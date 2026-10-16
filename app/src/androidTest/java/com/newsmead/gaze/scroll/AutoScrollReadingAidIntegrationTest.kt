package com.newsmead.gaze.scroll

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnPreDraw
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.fragments.article.ArticleTypography
import com.newsmead.gaze.aid.*
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.physicalDisplaySize
import com.newsmead.gaze.scanpath.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Real attached scrolling with supplied causal coordinates; no camera or human validity claim. */
@RunWith(AndroidJUnit4::class)
class AutoScrollReadingAidIntegrationTest {
    @Test fun lineAndSentenceGuidesRenderDuringActualMotionWithStableSourceOffsets() {
        AutoScrollAidFixture(ReaderAidPreferences(autoScroll = true, wordDefinition = false,
            focusRegion = false)).use { f ->
            f.follow(650, f.a)
            f.onMain {
                f.assertMovingRendered(ReadingAidType.CURRENT_LINE)
                assertEquals(f.a.line, f.runtime.activeActions().single {
                    it.aidType == ReadingAidType.CURRENT_LINE }.anchor.line)
                f.runtime.updatePreferences(f.runtime.preferences.copy(guideStyle = ReadingGuideStyle.SENTENCE))
            }
            f.follow(650, f.a)
            f.onMain {
                f.assertMovingRendered(ReadingAidType.CURRENT_SENTENCE)
                val sentence = f.runtime.activeActions().single { it.aidType == ReadingAidType.CURRENT_SENTENCE }
                assertEquals(f.a.line, sentence.anchor.line)
                assertNotNull(sentence.guideRange)
                assertTrue(f.a.start in sentence.guideRange!!.startUtf16 until sentence.guideRange!!.endUtf16)
                f.assertStableTarget(f.a)
                f.assertTraceCoordinates()
                f.assertOriginalText()
            }
        }
    }

    @Test fun sustainedWordPursuitOffersLookupFromRealPrefixAndStopsNavigation() {
        AutoScrollAidFixture(ReaderAidPreferences(autoScroll = true, lineGuide = false,
            focusRegion = false)).use { f ->
            f.follow(2150, f.a)
            f.awaitDraw()
            f.onMain {
                assertTrue("No definition choice while navigation was moving",
                    f.choices.any { it.first.aidType == ReadingAidType.WORD_DEFINITION && it.second })
                assertTrue("No real prefix reached the configured word duration", f.prefixes.any {
                    it.wordStartUtf16 == f.a.start && it.durationMs >= f.runtime.config.definitionMinObservedMs })
                assertTrue("Lookup offer did not reach attached layout", f.offerEvents.contains("shown"))
                assertTrue(f.popup.isOfferShowing)
                assertFalse(f.popup.isShowing)
                assertFalse("Lookup offer must block navigation", f.controller.isMoving)
                assertTrue(f.ends.any { it.optString("reason") == "lookup_offer" })
                assertTrue(f.renders.any { it.action.aidType == ReadingAidType.WORD_DEFINITION && it.drawn })
                assertFalse(f.offerEvents.contains("accepted"))
                assertEquals(0, f.lookupRequests)
                f.assertStableTarget(f.a)
                f.assertOriginalText()
            }
        }
    }

    @Test fun actualLineAlternationEnablesFocusDuringContinuousMotion() {
        AutoScrollAidFixture(ReaderAidPreferences(autoScroll = true, lineGuide = false,
            wordDefinition = false)).use { f ->
            f.onMain {
                val renderedDp = f.runtime.configurationSnapshot().getJSONObject("settings")
                    .getDouble("focus_text_size_dp")
                assertTrue("Fixture must isolate the STANDARD font tier; rendered dp=$renderedDp",
                    renderedDp >= 22.0)
                assertEquals("Fixture must require one alternation", 1,
                    ReadingFocusTypography.rule(renderedDp).requiredAlternations)
            }
            // Warm-up C avoids relying on an initial left-censored A. Each visit is
            // made of actual 40 ms observations; C finally flushes the last B.
            f.follow(320, f.c)
            for (target in listOf(f.a, f.b, f.a, f.b, f.c)) f.follow(240, target)
            f.onMain {
                val diagnostics = f.focusDiagnostics()
                assertTrue("Engine did not observe the physical-line cycle; $diagnostics", f.events.any {
                    it.patternId == "P11" && it.resolution == "line" })
                assertTrue("No active focus action; $diagnostics", f.runtime.activeActions().any {
                    it.aidType == ReadingAidType.FOCUS_REGION })
                val focus = f.runtime.activeActions().single { it.aidType == ReadingAidType.FOCUS_REGION }
                assertNotNull("Focus action lacks supported evidence; $diagnostics", focus.focusEvidence)
                assertTrue("No real supported ABAB sequence; $diagnostics",
                    focus.focusEvidence!!.alternations.isNotEmpty())
                assertTrue(f.samples.filter { it.eligible }.mapNotNull { it.line }.containsAll(
                    listOf(f.a.line, f.b.line, f.c.line)))
                f.assertMovingRendered(ReadingAidType.FOCUS_REGION)
                f.assertOriginalText()
            }
        }
    }

    @Test fun supportedReadingThenTrackingLossShowsBookmarkWithAutoScrollStillEnabled() {
        AutoScrollAidFixture(ReaderAidPreferences(autoScroll = true, lineGuide = false,
            focusRegion = false, wordDefinition = false)).use { f ->
            f.follow(650, f.a)
            f.onMain {
                assertTrue(f.controller.isMoving)
                assertTrue(f.prefixes.any { it.line == f.a.line })
            }
            f.missing(750)
            f.awaitDraw()
            f.onMain {
                assertTrue(f.controller.enabled)
                assertTrue(f.runtime.preferences.autoScroll)
                assertFalse("Missing gaze must stop actual motion", f.controller.isMoving)
                val bookmark = f.runtime.activeActions().single { it.aidType == ReadingAidType.READING_BOOKMARK }
                assertEquals(f.a.line, bookmark.anchor.line)
                val evidence = bookmark.bookmarkEvidence!!
                assertEquals("sustained_gaze_unavailable", evidence.departureReason)
                assertTrue(bookmark.evidenceAvailableMs - evidence.departureFirstDeliveryMs!! >=
                    f.runtime.config.bookmarkTrackingLossMs)
                assertTrue(f.renders.any { it.action.aidType == ReadingAidType.READING_BOOKMARK && it.drawn })
                f.assertOriginalText()
            }
        }
    }

    @Test fun externalMoveInvalidatesEvenWhenSessionAlreadyObservedItsTranslation() {
        AutoScrollAidFixture(ReaderAidPreferences(autoScroll = true, wordDefinition = false,
            focusRegion = false)).use { f ->
            f.follow(650, f.a)
            f.onMain { f.assertExternalBoundaryAfterPrematureGeometryObservation() }
        }
    }
}

/** All mutable state and runtime calls belong to main; only bounded waits run off main. */
private class AutoScrollAidFixture(preferences: ReaderAidPreferences) : Closeable {
    data class Target(val line: Int, val start: Int, val end: Int, val x: Double, val y: Double)
    data class Frame(val atMs: Double, val geometry: ScanpathGeometry)
    data class Render(val action: ReadingAidAction, val drawn: Boolean, val moving: Boolean, val reason: String)

    private val scenario = ActivityScenario.launch(ScanpathReplayActivity::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val reader = ScanpathTextGeometryReader()
    private val frames = ArrayDeque<Frame>()
    private var task: Runnable? = null
    private var listening = false
    private var ordinal = 0L
    private var displayGeometry: ScanpathGeometry? = null
    lateinit var binding: FragmentArticleBinding
        private set
    lateinit var runtime: ArticleReadingAidRuntime
        private set
    lateinit var session: ArticleScanpathSession
        private set
    lateinit var controller: ArticleAutoScrollController
        private set
    lateinit var popup: ReadingAidDefinitionPopup
        private set
    private lateinit var overlay: ReadingAidOverlayView
    lateinit var a: Target
        private set
    lateinit var b: Target
        private set
    lateinit var c: Target
        private set
    var lookupRequests = 0
        private set
    val samples = mutableListOf<ScanpathSample>()
    val prefixes = mutableListOf<ScanpathCandidateEvidence>()
    val events = mutableListOf<ScanpathEvent>()
    val choices = mutableListOf<Pair<ReadingAidAction, Boolean>>()
    val renders = mutableListOf<Render>()
    val offerEvents = mutableListOf<String>()
    val ends = mutableListOf<JSONObject>()
    private val observations = mutableMapOf<Double, MgazeNetGazeProvider.Observation>()
    private val scrollPositions = mutableListOf<Int>()
    private val originalText = (1..180).joinToString("\n") { "Readers follow words." }
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        controller.onPreDraw()
        session.checkGeometry()
        runtime.tick()
        rememberFrame()
        // Retain actual static geometry as well as moving frames for delayed captures.
        binding.root.postInvalidateOnAnimation()
        true
    }

    init {
        try {
            onMain { activity ->
                binding = FragmentArticleBinding.inflate(activity.layoutInflater)
                binding.tvArticleText.text = originalText
                ArticleTypography.apply(binding.tvArticleText, 22f)
                // Isolate STANDARD-tier activation with margin above its 22 dp boundary,
                // independent of system font scaling; retain the article's spacing rule.
                binding.tvArticleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
                binding.tvArticleText.setLineSpacing(
                    binding.tvArticleText.textSize / ArticleTypography.DEFAULT_SIZE_SP,
                    ArticleTypography.LINE_SPACING_MULTIPLIER)
                activity.setContentView(binding.root)
                overlay = ReadingAidOverlayView(activity, lineStartMarkerInGutter = true)
                (binding.root as ViewGroup).addView(overlay, ConstraintLayout.LayoutParams(0, 0).apply {
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                })
                overlay.translationZ = 1000f
                popup = ReadingAidDefinitionPopup(activity, binding.root as ViewGroup, binding.tvArticleText,
                    repository = object : WordDefinitionRepository {
                        override fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest {
                            lookupRequests++
                            callback(null)
                            return DefinitionRequest {}
                        }
                    })
                runtime = ArticleReadingAidRuntime(binding.tvArticleText, binding.readingAidWordDock, overlay,
                    config = ReadingAidPolicyConfig.definitionPrototype()
                        .copy(conditionId = "SMOOTHED_ARTICLE_CONTENT_AIDS_V11"),
                    preferences = preferences, inputSource = ReadingAidInputSource.SYNTHETIC_REPLAY,
                    coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED,
                    motionCompensatedAids = true, definitionPopup = popup,
                    onDecision = { decision ->
                        if (decision.status == ReadingAidDecisionStatus.CHOSEN) decision.action?.let {
                            choices += it to (::controller.isInitialized && controller.isMoving)
                        }
                    },
                    onRenderReceipt = { action, drawn, reason, _ ->
                        renders += Render(action, drawn, ::controller.isInitialized && controller.isMoving, reason)
                    },
                    onDefinitionOffer = { _, event, _ -> offerEvents += event },
                    onConsultationChanged = { active, at, reason ->
                        controller.interrupt("definition_consultation")
                        session.setConsultation(active, at, reason)
                    })
                session = ArticleScanpathSession(binding.tvArticleText, config = runtime.scanpathConfig,
                    coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED, motionCompensatedAids = true,
                    onGeometry = { generation, geometry ->
                        displayGeometry = geometry
                        runtime.geometry(generation, geometry)
                    },
                    onInput = { generation, sample -> samples += sample; runtime.input(generation, sample) },
                    onFixation = { generation, fixation -> runtime.fixation(generation, fixation) },
                    onEvent = { generation, event -> events += event; runtime.event(generation, event) },
                    onCandidate = { generation, candidate ->
                        candidate?.let(prefixes::add)
                        runtime.candidate(generation, candidate)
                    })
                controller = ArticleAutoScrollController(binding.tvArticleText, binding.nsvArticleText,
                    binding.bottomAppBar, binding.articleAutoScrollTail,
                    blocked = { when {
                        popup.isShowing -> "definition_open"
                        popup.isOfferShowing -> "lookup_offer"
                        else -> null
                    } },
                    onBoundary = { moving, at, detail ->
                        session.autoScrollMotion(moving, detail.optString("reason"))
                        runtime.autoScrollBoundary(moving, at, detail)
                        if (!moving) ends += JSONObject(detail.toString())
                    },
                    onJournal = { kind, at, detail -> runtime.autoScrollEvent(kind, at, detail) },
                    onState = { _, _ -> })
                binding.root.viewTreeObserver.addOnPreDrawListener(preDraw)
                listening = true
            }
            awaitDraw()
            onMain { binding.nsvArticleText.scrollTo(0, binding.tvArticleText.top) }
            awaitDraw()
            onMain { controller.setEnabled(true) }
            awaitDraw()
            delay(350)
            onMain {
                val g = reader.read(binding.tvArticleText)!!.forEngine("fixture")
                val h = binding.tvArticleText.physicalDisplaySize().y
                val base = g.lines.indices.firstOrNull { index ->
                    val row = g.lines[index]
                    g.textOriginY + row.top >= h * .55 && g.textOriginY + row.bottom < h * .65
                } ?: error("Attached article has no wholly visible lower target row")
                a = target(base)
                b = target(base + 1)
                c = target(base + 2)
                listOf(a, b, c).forEach { assertVisible(it, g) }
                scrollPositions.clear()
                samples.clear()
            }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun onMain(block: (ScanpathReplayActivity) -> Unit) { scenario.onActivity { block(it) } }
    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e6

    private fun rememberFrame() {
        reader.read(binding.tvArticleText)?.let { geometry ->
            frames.addLast(Frame(now(), geometry.forEngine("fixture")))
            while (frames.size > 200) frames.removeFirst()
        }
        val position = binding.nsvArticleText.scrollY
        if (scrollPositions.lastOrNull() != position) scrollPositions += position
    }

    private fun target(line: Int): Target {
        val layout = binding.tvArticleText.layout
        val start = originalText.indexOf("follow", layout.getLineStart(line))
        check(start >= layout.getLineStart(line) && start + 6 <= layout.getLineEnd(line)) {
            "Synthetic sentence wrapped; requested line $line has no complete target token"
        }
        return Target(line, start, start + 6,
            (layout.getPrimaryHorizontal(start) + layout.getPrimaryHorizontal(start + 6)) / 2.0,
            (layout.getLineTop(line) + layout.getLineBottom(line)) / 2.0)
    }

    private fun assertVisible(target: Target, g: ScanpathGeometry) {
        val row = g.lines[target.line]
        val y = g.textOriginY + target.y
        val x = g.textOriginX + target.x
        val screenHeight = binding.tvArticleText.physicalDisplaySize().y
        assertTrue("Historical target line ${target.line} left the article viewport",
            g.textOriginY + row.top >= g.visibleRectScreen.top &&
                g.textOriginY + row.bottom < g.visibleRectScreen.bottom &&
                x >= g.visibleRectScreen.left && x < g.visibleRectScreen.right)
        assertTrue("Pursued target is no longer in the controller's lower region: y=$y",
            y > .40 * screenHeight)
    }

    private fun emit(target: Target?, missing: Boolean) {
        val capture = now() - 80.0
        val point = if (target != null) {
            val historical = frames.lastOrNull { it.atMs <= capture }
                ?: error("No causal attached geometry for delayed capture")
            assertVisible(target, historical.geometry)
            (historical.geometry.textOriginX + target.x).toFloat() to
                (historical.geometry.textOriginY + target.y).toFloat()
        } else null
        val observation = MgazeNetGazeProvider.Observation(capture, SystemClock.elapsedRealtimeNanos(),
            ++ordinal, if (missing) "no_face" else "coordinate", point?.first, point?.second,
            point?.first?.plus(3f), point?.second?.plus(4f))
        observations[capture] = observation
        controller.onPreDraw()
        session.record(observation)
        controller.onObservation(observation)
        binding.root.postInvalidateOnAnimation()
    }

    fun follow(durationMs: Long, target: Target) = phase(durationMs) { emit(target, false) }
    fun missing(durationMs: Long) = phase(durationMs) { emit(null, true) }

    private fun phase(durationMs: Long, emit: () -> Unit) {
        val finished = CountDownLatch(1)
        var failure: Throwable? = null
        onMain {
            check(task == null)
            val deadline = SystemClock.elapsedRealtime() + durationMs
            task = object : Runnable {
                override fun run() {
                    try {
                        if (SystemClock.elapsedRealtime() >= deadline) {
                            task = null
                            finished.countDown()
                        } else {
                            emit()
                            handler.postDelayed(this, 40)
                        }
                    } catch (caught: Throwable) {
                        failure = caught
                        task = null
                        finished.countDown()
                    }
                }
            }
            handler.post(task!!)
        }
        assertTrue("Bounded source phase did not complete", finished.await(durationMs + 3000, TimeUnit.MILLISECONDS))
        failure?.let { throw it }
    }

    fun awaitDraw() {
        val finished = CountDownLatch(1)
        onMain {
            binding.root.doOnPreDraw { finished.countDown() }
            binding.root.requestLayout()
            binding.root.invalidate()
        }
        assertTrue("Attached layout did not draw", finished.await(3, TimeUnit.SECONDS))
    }

    private fun delay(durationMs: Long) {
        val finished = CountDownLatch(1)
        onMain { handler.postDelayed({ finished.countDown() }, durationMs) }
        assertTrue("Bounded UI delay failed", finished.await(durationMs + 3000, TimeUnit.MILLISECONDS))
    }

    fun assertMovingRendered(type: ReadingAidType) {
        val diagnostics = focusDiagnostics()
        assertTrue("No actual continuous scroll changes; $diagnostics", scrollPositions.distinct().size >= 3)
        assertTrue("Controller stopped before $type assertion; $diagnostics", controller.isMoving)
        assertTrue("No active $type; $diagnostics", runtime.activeActions().any { it.aidType == type })
        assertTrue("No attached draw receipt for $type during motion; $diagnostics", renders.any {
            it.action.aidType == type && it.drawn && it.moving })
    }

    fun focusDiagnostics(): String = "sample_exclusions=" + samples.groupingBy { it.exclusion ?: "eligible" }.eachCount() +
        "; P13_reasons=" + events.filter { it.patternId == "P13" }.map { it.metrics["reason"] } +
        "; active=" + runtime.activeActions().map { it.aidType } +
        "; chosen=" + choices.map { it.first.aidType } +
        "; focus_renders=" + renders.filter { it.action.aidType == ReadingAidType.FOCUS_REGION }
            .map { "drawn=${it.drawn},moving=${it.moving},reason=${it.reason}" }

    fun assertStableTarget(target: Target) {
        val accepted = samples.filter { it.eligible }
        assertTrue("No eligible motion samples", accepted.isNotEmpty())
        for (sample in accepted) {
            assertEquals(target.line, sample.line)
            assertEquals(target.start, sample.wordStartUtf16)
            assertEquals(target.end, sample.wordEndUtf16)
            val original = observations[sample.captureMs]!!
            assertEquals(original.x!!.toDouble(), sample.processingX!!, 0.0)
            assertEquals(original.y!!.toDouble(), sample.processingY!!, 0.0)
            assertEquals(original.rawX!!.toDouble(), sample.rawX!!, 0.0)
            assertEquals(original.rawY!!.toDouble(), sample.rawY!!, 0.0)
            assertTrue(sample.motionCompensation!!.validFor(sample))
            assertTrue(sample.motionCompensation!!.geometryTimeMs <= sample.captureMs!!)
        }
        assertTrue("Analysis and selected screen frame were not distinguished", accepted.any {
            abs(it.processingY!! - it.analysisY!!) > 1.0 })
    }

    fun assertTraceCoordinates() {
        val file = runtime.export(binding.root.context)
        try {
            val trace = JSONObject(file.readText())
            val settings = trace.getJSONObject("configuration").getJSONObject("settings")
            assertTrue(settings.getBoolean("motion_compensated_aids"))
            assertEquals("SMOOTHED_ARTICLE_CONTENT_AIDS_V11", settings.getString("condition_id"))
            val records = trace.getJSONArray("records")
            val inputs = (0 until records.length()).map { records.getJSONObject(it) }
                .filter { it.getString("kind") == "decision" }
                .map { it.getJSONObject("detail").getJSONObject("input") }
                .filter { it.optBoolean("valid_live_input") && it.optString("analysis_frame") ==
                    ScanpathMotionCompensation.MODE_ID }
            assertTrue("Trace lacks causal content-frame input", inputs.isNotEmpty())
            for (input in inputs) {
                val original = observations[input.getDouble("capture_ms")]!!
                assertEquals(original.x!!.toDouble(), input.getDouble("processing_x_px"), 0.0)
                assertEquals(original.y!!.toDouble(), input.getDouble("processing_y_px"), 0.0)
                assertEquals(input.getDouble("processing_y_px") - input.getDouble("capture_text_origin_y_px"),
                    input.getDouble("analysis_y_content_px"), 0.0)
                assertTrue(input.getDouble("capture_geometry_at_ms") <= input.getDouble("capture_ms"))
            }
        } finally { assertTrue("Synthetic private trace was not removed", file.delete()) }
    }

    fun assertOriginalText() { assertEquals(originalText, binding.tvArticleText.text.toString()) }

    fun assertExternalBoundaryAfterPrematureGeometryObservation() {
        assertTrue(controller.isMoving)
        assertNotNull(session.engine.currentCandidateEvidence())
        val priorId = displayGeometry!!.snapshotId
        val priorY = binding.nsvArticleText.scrollY
        binding.nsvArticleText.scrollTo(0, priorY + 24)
        assertTrue("External programmatic scroll did not move the attached view",
            binding.nsvArticleText.scrollY > priorY)
        // Deliberately reproduce the old ordering: the session sees this external
        // translation while its controlled-motion flag is still true.
        session.checkGeometry()
        assertEquals(priorId, displayGeometry!!.snapshotId)
        session.autoScrollMotion(false, "external_scroll")
        assertNotEquals(priorId, displayGeometry!!.snapshotId)
        assertNull(session.engine.currentCandidateEvidence())
        assertTrue(events.any { it.metrics["reason"] == "uncontrolled_scroll" })
        val geometry = displayGeometry!!
        val observation = MgazeNetGazeProvider.Observation(now(), SystemClock.elapsedRealtimeNanos(),
            ++ordinal, "coordinate", (geometry.textOriginX + a.x).toFloat(),
            (geometry.textOriginY + a.y).toFloat())
        session.record(observation)
        assertFalse("Capture immediately after external motion must abstain", samples.last().eligible)
        assertEquals("geometry_unsettled", samples.last().exclusion)
        assertNull(session.engine.currentCandidateEvidence())
        // The actual controller observes and halts its unexpected scroll during cleanup.
        controller.onPreDraw()
    }

    override fun close() {
        try {
            onMain {
                handler.removeCallbacksAndMessages(null)
                task = null
                if (::binding.isInitialized && listening && binding.root.viewTreeObserver.isAlive)
                    binding.root.viewTreeObserver.removeOnPreDrawListener(preDraw)
                listening = false
                if (::controller.isInitialized) controller.close()
                if (::session.isInitialized) session.close("auto_scroll_aid_fixture")
                if (::runtime.isInitialized) runtime.close("auto_scroll_aid_fixture")
                if (::popup.isInitialized) popup.close()
            }
        } finally { scenario.close() }
    }
}
