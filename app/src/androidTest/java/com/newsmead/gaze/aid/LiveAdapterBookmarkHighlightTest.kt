package com.newsmead.gaze.aid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.scanpath.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Camera-free rendered replay -> motion-enabled live adapter -> AUTO runtime contracts. */
@RunWith(AndroidJUnit4::class)
class LiveAdapterBookmarkHighlightTest {
    private inner class Fixture(
        val screen: ActivityScenario<ScanpathReplayActivity>, val name: String
    ) {
        lateinit var runtime: ArticleReadingAidRuntime
        lateinit var session: ArticleScanpathSession
        lateinit var overlay: ReadingAidOverlayView
        var frame: ScanpathGeometry? = null
        val visible = mutableListOf<Int>()
        val draws = mutableSetOf<String>()
        val summary = JSONObject().put("journey", name).put("result", "failed")
            .put("input", "synthetic_observations_camera_off")
            .put("requested_capture_delivery_latency_ms", 5.0)
        val stages = JSONArray()
        var sequence = 0L
        var firstUnavailableDelivery: Double? = null
        var firstUnavailableCapture: Double? = null
        var latestSample: ScanpathSample? = null
        private val instrumentation = InstrumentationRegistry.getInstrumentation()

        fun start() {
            instrumentation.waitForIdleSync()
            var ready = false
            val deadline = SystemClock.elapsedRealtime() + 3000
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                screen.onActivity { activity ->
                    ready = activity.findViewById<TextView>(R.id.replayBody).let {
                        it.layout != null && it.width > 0 && it.isShown
                    }
                }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("Synthetic reading surface must be visible", ready)
            screen.onActivity { activity ->
                val body = activity.findViewById<TextView>(R.id.replayBody)
                val viewport = activity.findViewById<FrameLayout>(R.id.replayViewport)
                overlay = ReadingAidOverlayView(activity, lineStartMarkerInGutter = true)
                viewport.addView(overlay, FrameLayout.LayoutParams(-1, -1))
                runtime = ArticleReadingAidRuntime(body, activity.findViewById(R.id.replayWordDock), overlay,
                    config = ReadingAidPolicyConfig.evidenceAlignedPrototype(),
                    preferences = ReaderAidPreferences(focusRegion = false, wordDefinition = false),
                    inputSource = ReadingAidInputSource.SYNTHETIC_REPLAY,
                    coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED,
                    blinkFilterEnabled = true, motionCompensatedAids = true,
                    onRenderReceipt = { action, drawn, _, _ -> if (drawn) draws.add(action.id) })
                session = ArticleScanpathSession(body, config = runtime.scanpathConfig,
                    coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED, motionCompensatedAids = true,
                    onGeometry = { generation, geometry -> frame = geometry; runtime.geometry(generation, geometry) },
                    onInput = { generation, sample -> latestSample = sample; runtime.input(generation, sample) },
                    onCandidate = { generation, candidate -> runtime.candidate(generation, candidate) },
                    onEvent = { generation, event -> runtime.event(generation, event) },
                    onFixation = { generation, fixation -> runtime.fixation(generation, fixation) })
                session.checkGeometry()
                val g = checkNotNull(frame)
                visible.addAll(g.lines.indices.filter { line ->
                    val row = g.lines[line]; val v = g.visibleRectScreen
                    row.top + g.textOriginY >= v.top && row.bottom + g.textOriginY <= v.bottom &&
                        row.left + g.textOriginX >= v.left && row.right + g.textOriginX <= v.right &&
                        row.right > row.left
                }.take(6))
                val configuration = runtime.configurationSnapshot()
                summary.put("configuration", configuration).put("visible_rows", JSONArray(visible))
                assertEquals(ReadingAidsMode.AUTO, runtime.preferences.mode)
                assertEquals(20, configuration.getInt("policy_version"))
                assertEquals("A2_LAST_READ_LINE_BOOKMARK_V9",
                    configuration.getJSONObject("settings").getString("line_start_marker_rule"))
                assertFalse(configuration.getJSONObject("settings").getBoolean("bookmark_source_requires_mapper_agreement"))
                assertTrue(configuration.getJSONObject("settings").isNull("bookmark_source_max_age_at_departure_ms"))
                assertTrue(configuration.getJSONObject("settings").getBoolean("motion_compensated_aids"))
                assertEquals("SYNTHETIC_REPLAY", configuration.getString("input_source"))
            }
            assertEquals("Need six actual visible rows", 6, visible.size)
            // Establish capture-time geometry before the short arming journey starts.
            SystemClock.sleep(200)
        }

        fun hold(line: Int?, ms: Int, reason: String = "coordinate", outside: Boolean = false) {
            val deadline = SystemClock.elapsedRealtime() + ms
            while (SystemClock.elapsedRealtime() < deadline) {
                screen.onActivity {
                    val g = checkNotNull(frame)
                    val row = line?.let { g.lines[it] }
                    val x = if (outside) (g.visibleRectScreen.right + 100.0).toFloat()
                        else row?.let { (g.textOriginX + (it.left + it.right) / 2).toFloat() }
                    val y = row?.let { (g.textOriginY + (it.top + it.bottom) / 2).toFloat() }
                    val deliveryNs = SystemClock.elapsedRealtimeNanos()
                    val delivery = deliveryNs / 1e6
                    if (reason == "no_face" && firstUnavailableDelivery == null) {
                        firstUnavailableDelivery = delivery
                        firstUnavailableCapture = delivery - 5.0
                    }
                    session.record(MgazeNetGazeProvider.Observation(delivery - 5.0,
                        deliveryNs, ++sequence, reason, x = x, y = y, rawX = x, rawY = y,
                        blinkFilterApplied = true))
                }
                SystemClock.sleep(50)
            }
        }

        fun bookmark(): ReadingAidAction? {
            var action: ReadingAidAction? = null
            screen.onActivity {
                action = runtime.activeActions().firstOrNull { it.aidType == ReadingAidType.READING_BOOKMARK }
            }
            return action
        }

        fun record(stage: String, action: ReadingAidAction? = bookmark()) {
            screen.onActivity {
                stages.put(JSONObject().put("stage", stage)
                    .put("source_line", action?.anchor?.line ?: JSONObject.NULL)
                    .put("onset_ms", action?.onsetMs ?: JSONObject.NULL)
                    .put("trigger", action?.bookmarkEvidence?.departureReason ?: JSONObject.NULL)
                    .put("strength", action?.strength ?: JSONObject.NULL)
                    .put("drawn", action?.let { draws.contains(it.id) } ?: false)
                    .put("last_sample_capture_ms", latestSample?.captureMs ?: JSONObject.NULL)
                    .put("last_sample_delivery_ms", latestSample?.deliveryMs ?: JSONObject.NULL)
                    .put("last_mapper_line", latestSample?.line ?: JSONObject.NULL)
                    .put("last_mapper_exclusion", latestSample?.exclusion ?: JSONObject.NULL))
            }
        }

        fun captureSyntheticScreen(stage: String, action: ReadingAidAction? = null) {
            screen.onActivity { activity ->
                val view = activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    val folder = File(activity.cacheDir, "bookmark-highlight-check").apply { mkdirs() }
                    File(folder, "$name-$stage.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { bitmap.recycle() }
                if (action != null) assertTrue("Bookmark must actually draw", draws.contains(action.id))
            }
        }

        fun close() {
            screen.onActivity { activity ->
                summary.put("stages", stages)
                    .put("first_no_face_capture_ms", firstUnavailableCapture ?: JSONObject.NULL)
                    .put("first_no_face_delivery_ms", firstUnavailableDelivery ?: JSONObject.NULL)
                val folder = File(activity.cacheDir, "bookmark-highlight-check").apply { mkdirs() }
                File(folder, "$name.json").writeText(summary.toString(2))
                if (::session.isInitialized) session.close("highlight_fixture_complete")
                if (::runtime.isInitialized) runtime.close("highlight_fixture_complete")
                if (::overlay.isInitialized) (overlay.parent as? FrameLayout)?.removeView(overlay)
            }
        }
    }

    private fun journey(name: String, run: Fixture.() -> Unit) {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { screen ->
            val fixture = Fixture(screen, name)
            try {
                fixture.start()
                fixture.run()
                fixture.summary.put("result", "passed")
            } finally { fixture.close() }
        }
    }

    @Test fun partialArmingSurvivesShortBlinkThenRereadDimsAndForwardPassageClears() =
        journey("partial-arm-blink") {
            hold(visible[0], 550); hold(visible[1], 550)
            hold(null, 200, "blink")
            record("partial_arm_blink")
            assertNull("A blink cannot arm or trigger a departure", bookmark())
            hold(visible[2], 450); hold(visible[3], 450)
            assertNull("Normal forward reading cannot trigger", bookmark())
            hold(null, 700, "no_face")
            val cue = bookmark()
            record("lookaway", cue)
            assertNotNull("Partial capture-time arming must survive the short blink", cue)
            val saved = checkNotNull(cue)
            assertEquals(visible[3], saved.anchor.line)
            assertEquals("A2_LAST_READ_LINE_BOOKMARK_V9", saved.ruleId)
            assertEquals("sustained_gaze_unavailable", saved.bookmarkEvidence!!.departureReason)
            val latency = saved.onsetMs - checkNotNull(firstUnavailableDelivery)
            summary.put("source_line", saved.anchor.line).put("onset_ms", saved.onsetMs)
                .put("departure_delivery_latency_ms", latency)
                .put("source_capture_ms", saved.bookmarkEvidence!!.source.lastCaptureMs)
            assertTrue("Unavailable input must supply at least 500ms departure evidence", latency >= 500.0)
            captureSyntheticScreen("cue", saved)
            record("cue_rendered", saved)
            hold(visible[0], 600); hold(visible[1], 600); hold(visible[2], 600)
            val dimmed = checkNotNull(bookmark())
            record("earlier_reread", dimmed)
            assertEquals(saved.id, dimmed.id)
            assertTrue(dimmed.strength < saved.strength)
            assertTrue(dimmed.strength >= LineStartMarkerAppearance.BOOKMARK_DIMMED_STRENGTH - .00001)
            hold(visible[3], 600)
            assertNotNull("Residence on the saved row retains the cue", bookmark())
            hold(visible[4], 600)
            record("forward_passage")
            assertNull("Supported adjacent passage past the saved row clears", bookmark())
        }

    @Test fun mapperWobbleSeparatedOutsideGlancesAndBlinkOnlyDoNotCue() =
        journey("no-false-cue") {
            hold(visible[0], 1800)
            listOf(0, 1, 0, 2, 0, 4, 0).forEach { index ->
                hold(visible[index], 600)
                record("wobble_$index")
                assertNull("Actual mapped row wobble cannot trigger", bookmark())
            }
            repeat(3) { index ->
                hold(visible[0], 250, outside = true)
                record("short_outside_$index")
                assertNull("Separated short glances cannot accumulate departure credit", bookmark())
                hold(visible[0], 400)
            }
            hold(null, 700, "blink")
            record("blink_only")
            assertNull("Blink-only absence cannot trigger", bookmark())
            captureSyntheticScreen("clear")
        }

    @Test fun noNewGazeHandsOffFromExpiredHighlightToSavedBookmark() =
        journey("silent-stream-handoff") {
            hold(visible[3], 2000)
            record("last_gaze")
            SystemClock.sleep(1400)
            val saved = checkNotNull(bookmark()) { "No fresh gaze must finish the bookmark timer" }
            assertEquals(visible[3], saved.anchor.line)
            assertEquals("sustained_gaze_unavailable", saved.bookmarkEvidence!!.departureReason)
            screen.onActivity {
                assertTrue("Highlight has expired while the saved place persists", runtime.activeActions().none {
                    it.aidType == ReadingAidType.CURRENT_LINE })
            }
            record("highlight_expired_bookmark_visible", saved)
            captureSyntheticScreen("handoff", saved)
            hold(visible[3], 500); hold(visible[4], 500)
            assertNull(bookmark())
        }

    @Test fun oneShotStaleCallbackCanFinishOnUiPulses() =
        journey("one-shot-watchdog") {
            hold(visible[3], 2000)
            val lastCapture = checkNotNull(latestSample?.captureMs)
            SystemClock.sleep(620)
            screen.onActivity {
                session.record(MgazeNetGazeProvider.Observation(lastCapture,
                    SystemClock.elapsedRealtimeNanos(), ++sequence, "stale", timeoutWatchdog = true))
            }
            SystemClock.sleep(700)
            val saved = checkNotNull(bookmark()) { "One watchdog callback must be sufficient" }
            assertEquals(visible[3], saved.anchor.line)
            record("watchdog_bookmark", saved)
            captureSyntheticScreen("cue", saved)
        }

    @Test fun lookawayAfterHighlightAlreadyFadedUsesLastConfirmedLine() =
        journey("lookaway-after-highlight-faded") {
            hold(visible[3], 2000)
            repeat(26) { hold(visible[if (it % 2 == 0) 1 else 2], 50) }
            screen.onActivity {
                assertTrue("No new row has settled", runtime.activeActions().none {
                    it.aidType == ReadingAidType.CURRENT_LINE })
            }
            assertNull("Unsettled in-text gaze alone cannot trigger", bookmark())
            record("highlight_already_gone")
            hold(visible[3], 700, outside = true)
            val saved = checkNotNull(bookmark()) { "Lookaway must recover the last confirmed place" }
            assertEquals(visible[3], saved.anchor.line)
            record("remembered_line_bookmark", saved)
            captureSyntheticScreen("cue", saved)
        }
}
