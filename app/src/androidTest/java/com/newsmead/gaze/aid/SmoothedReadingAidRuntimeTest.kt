package com.newsmead.gaze.aid

import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Camera-free supplied evidence verifies routing, not actual fixation or reading difficulty. */
@RunWith(AndroidJUnit4::class)
class SmoothedReadingAidRuntimeTest {
    @Test fun activeFocusSurvivesScrollAsPresentationWithoutInventingNewPolicySupport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var runtime: ArticleReadingAidRuntime
        lateinit var overlay: ReadingAidOverlayView
        lateinit var body: TextView
        lateinit var geometry: ScanpathGeometry
        var heldDraws = 0
        fun now() = SystemClock.elapsedRealtimeNanos() / 1e6
        instrumentation.runOnMainSync {
            val ctx = instrumentation.targetContext
            val root = FrameLayout(ctx).apply { setBackgroundColor(Color.WHITE) }
            body = TextView(ctx).apply {
                text = (1..24).joinToString("\n") { "Reading line $it" }
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 36f)
            }
            overlay = ReadingAidOverlayView(ctx)
            root.addView(body, FrameLayout.LayoutParams(600, 400))
            root.addView(overlay, FrameLayout.LayoutParams(600, 400))
            root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 600, 400)
            runtime = ArticleReadingAidRuntime(body, ReadingAidWordView(ctx), overlay,
                config = ReadingAidPolicyConfig.definitionPrototype(),
                preferences = ReaderAidPreferences(lineGuide = false, wordDefinition = false),
                inputSource = ReadingAidInputSource.SYNTHETIC_REPLAY,
                coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            val recorder = overlay.onFocusScrollRender
            overlay.onFocusScrollRender = { source, snapshot, top, bottom, at ->
                recorder(source, snapshot, top, bottom, at)
                heldDraws++
                assertEquals("scroll-after", snapshot)
                assertEquals("scroll-before", source.anchor.geometrySnapshotId)
            }
            geometry = ScanpathTextGeometryReader().read(body)!!.forEngine("scroll-before")
            runtime.geometry(0, geometry)
        }
        fun emit(visit: Int, line: Int, supported: Boolean) = instrumentation.runOnMainSync {
                    val at = now() - 2
                    val row = geometry.lines[line]
                    runtime.input(0, ScanpathSample(at, at + 1, null, null, line, null, null,
                        geometry.snapshotId, displayX = geometry.textOriginX + (row.left + row.right) / 2,
                        displayY = geometry.textOriginY + (row.top + row.bottom) / 2,
                        coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED))
                    runtime.candidate(0, if (supported) ScanpathCandidateEvidence("scroll-visit:$visit", 1,
                        at - 100, at, 100.0, 3, null, null, line, 0.0, 1.0,
                        geometry.snapshotId, at + 1, "trigger_observation_delivery") else null)
        }
        try {
            // Four nonoverlapping supported visits with intermediate selected samples.
            // Keep the capture stream continuous rather than joining visits across a >125ms gap.
            Thread.sleep(140)
            for ((visit, line) in listOf(2, 3, 2, 3).withIndex()) {
                if (visit > 0) {
                    Thread.sleep(65)
                    emit(visit, line, false)
                    Thread.sleep(65)
                }
                emit(visit, line, true)
            }
            instrumentation.runOnMainSync {
                assertTrue(runtime.activeActions().any { it.aidType == ReadingAidType.FOCUS_REGION })
                overlay.draw(Canvas(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)))
                // A detached parent scroller has no window transform. TextView's own
                // scroll offsets supply an actual translated geometry in this fixture.
                body.scrollTo(0, 55)
                val moved = ScanpathTextGeometryReader().read(body)!!.forEngine("scroll-after")
                assertNotEquals(geometry.textOriginY, moved.textOriginY)
                runtime.geometry(0, moved)
                assertTrue(runtime.activeActions().none { it.aidType == ReadingAidType.FOCUS_REGION })
                overlay.draw(Canvas(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)))
                assertEquals(1, heldDraws)
                // A settings pause must clear presentation as well as detector actions.
                runtime.suspend("reader_controls")
                overlay.draw(Canvas(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)))
                assertEquals(1, heldDraws)
                assertFalse(overlay.allowFocusScrollHold)
            }
        } finally { instrumentation.runOnMainSync { runtime.close("fixture") } }
    }

    @Test fun movingReturnsSurviveConsultationAndScrollButWaitForNewGeometryAnchor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var runtime: ArticleReadingAidRuntime
        lateinit var popup: ReadingAidDefinitionPopup
        lateinit var geometry: ScanpathGeometry
        val episodes = mutableListOf<ReadingAidFocusEpisode>()
        fun now() = SystemClock.elapsedRealtimeNanos() / 1e6
        instrumentation.runOnMainSync {
            val ctx = instrumentation.targetContext
            val host = FrameLayout(ctx)
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta\nThird reading line"; textSize = 20f }
            host.addView(body, FrameLayout.LayoutParams(600, 800))
            host.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 600, 800)
            geometry = ScanpathTextGeometryReader().read(body)!!.forEngine("return-fixture")
            popup = ReadingAidDefinitionPopup(ctx, host, body)
            runtime = ArticleReadingAidRuntime(body, ReadingAidWordView(ctx), ReadingAidOverlayView(ctx),
                config = ReadingAidPolicyConfig.definitionPrototype(),
                preferences = ReaderAidPreferences(lineGuide = false, wordDefinition = false),
                inputSource = ReadingAidInputSource.SYNTHETIC_REPLAY, renderAids = false,
                definitionPopup = popup, coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED,
                onFocusEpisode = episodes::add)
            runtime.geometry(0, geometry)
        }
        // Supplied unmapped article coordinates intentionally do not offer a fixation candidate.
        fun emit(rowIndex: Int = 0, missing: Boolean = false, supported: Boolean = false) {
            instrumentation.runOnMainSync {
                val row = geometry.lines[rowIndex]
                val at = now() - 2.0
                val sample = ScanpathSample(at, at + 1, null, null,
                    rowIndex.takeIf { supported }, null, null, geometry.snapshotId, supported,
                    if (missing) "no_face" else if (!supported) "outside_visible_text" else null,
                    displayX = if (missing) null else geometry.textOriginX + (row.left + row.right) / 2,
                    displayY = if (missing) null else geometry.textOriginY + (row.top + row.bottom) / 2,
                    coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
                runtime.input(0, sample)
                if (supported) runtime.candidate(0, ScanpathCandidateEvidence("source:$at", 1,
                    at - 100, at, 100.0, 3, null, null, rowIndex, 0.0, 1.0,
                    geometry.snapshotId, at + 1, "trigger_observation_delivery"))
            }
        }
        fun episode() {
            emit(missing = true)
            Thread.sleep(550)
            for (index in 0..2) {
                if (index > 0) Thread.sleep(60)
                emit(rowIndex = index % 2)
            }
        }
        try {
            Thread.sleep(130)
            emit(supported = true)
            episode(); episode()
            instrumentation.runOnMainSync {
                assertEquals(2, episodes.size)
                popup.onConsultationChanged(true, now(), "supplied_fixture")
            }
            emit(missing = true) // Excluded consultation input must not create an episode.
            instrumentation.runOnMainSync {
                popup.onConsultationChanged(false, now(), "supplied_fixture")
                geometry = geometry.copy(snapshotId = "return-fixture-scrolled")
                runtime.geometry(0, geometry)
            }
            // Presence arms again without inventing a new supported line after scroll.
            for (index in 0..2) {
                if (index > 0) Thread.sleep(60)
                emit(rowIndex = index % 2)
            }
            episode()
            instrumentation.runOnMainSync {
                assertEquals(3, episodes.size)
                assertTrue(episodes.all { it.recovery == null && it.presenceRecovery != null })
                assertTrue(runtime.activeActions().none { it.aidType == ReadingAidType.FOCUS_REGION })
            }
            emit(rowIndex = 1, supported = true)
            instrumentation.runOnMainSync {
                val focus = runtime.activeActions().single { it.aidType == ReadingAidType.FOCUS_REGION }
                assertEquals(3, focus.focusEvidence!!.episodeCount)
                assertEquals(0.5, focus.strength, 0.0)
                assertEquals("return-fixture-scrolled", focus.anchor.geometrySnapshotId)
                assertEquals(1, focus.anchor.line)
            }
        } finally { instrumentation.runOnMainSync { runtime.close("fixture"); popup.close() } }
    }

    @Test fun oneMissingCallbackWithRepeatedCaptureCanMatureThroughRuntimeTicks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var runtime: ArticleReadingAidRuntime? = null
        var geometry: ScanpathGeometry? = null
        instrumentation.runOnMainSync {
            val ctx = instrumentation.targetContext
            val root = FrameLayout(ctx)
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta"; textSize = 20f }
            root.addView(body, FrameLayout.LayoutParams(600, 800))
            root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 600, 800)
            geometry = ScanpathTextGeometryReader().read(body)!!.forEngine("loss-fixture")
            runtime = ArticleReadingAidRuntime(body, ReadingAidWordView(ctx), ReadingAidOverlayView(ctx),
                config = ReadingAidPolicyConfig.definitionPrototype(),
                preferences = ReaderAidPreferences(lineGuide = false, wordDefinition = false),
                inputSource = ReadingAidInputSource.SYNTHETIC_REPLAY, renderAids = false,
                coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            runtime!!.geometry(0, geometry)
            val g = geometry!!
            val row = g.lines[1]
            val at = SystemClock.elapsedRealtimeNanos() / 1e6
            runtime!!.input(0, ScanpathSample(at, at, null, null, 1, null, null, g.snapshotId,
                displayX = g.textOriginX + (row.left + row.right) / 2,
                displayY = g.textOriginY + (row.top + row.bottom) / 2,
                coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED))
        }
        try {
            // Establish supplied support strictly after the geometry-reset floor.
            Thread.sleep(180)
            instrumentation.runOnMainSync {
                val g = geometry!!
                val row = g.lines[1]
                val at = SystemClock.elapsedRealtimeNanos() / 1e6 - 2.0
                val x = g.textOriginX + (row.left + row.right) / 2
                val y = g.textOriginY + (row.top + row.bottom) / 2
                val source = ScanpathSample(at, at + 1, null, null, 1, null, null, g.snapshotId,
                    displayX = x, displayY = y, coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
                runtime!!.input(0, source)
                runtime!!.candidate(0, ScanpathCandidateEvidence("loss-source", 0, at - 100, at,
                    100.0, 3, null, null, 1, 0.0, 1.0, g.snapshotId, at + 1,
                    "trigger_observation_delivery"))
                // The live watchdog reports the previous capture, rather than inventing a new one.
                val delivered = SystemClock.elapsedRealtimeNanos() / 1e6
                runtime!!.input(0, source.copy(deliveryMs = delivered, line = null,
                    eligible = false, exclusion = "stale", displayX = null, displayY = null))
                assertTrue(runtime!!.activeActions().isEmpty())
            }
            Thread.sleep(700)
            instrumentation.runOnMainSync {
                val marker = runtime!!.activeActions().single { it.aidType == ReadingAidType.READING_BOOKMARK }
                assertEquals(1, marker.anchor.line)
                val e = marker.bookmarkEvidence!!
                assertEquals("sustained_gaze_unavailable", e.departureReason)
                assertNull(e.departureLastCaptureMs)
                assertTrue(marker.evidenceAvailableMs - e.departureFirstDeliveryMs!! >= 500.0)
            }
        } finally { instrumentation.runOnMainSync { runtime?.close("fixture") } }
    }

    @Test fun selectedContextOffersAidsAndUnavailableOrWrongStreamCannotReuseRawPoint() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val root = FrameLayout(ctx)
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta"; textSize = 20f }
            root.addView(body, FrameLayout.LayoutParams(600, 800))
            root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 600, 800)
            val runtime = ArticleReadingAidRuntime(body, ReadingAidWordView(ctx), ReadingAidOverlayView(ctx),
                config = ReadingAidPolicyConfig.definitionPrototype().copy(conditionId = "SMOOTHED_RUNTIME_FIXTURE_V1"),
                inputSource = ReadingAidInputSource.SYNTHETIC_REPLAY, renderAids = false,
                coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            try {
                val g = ScanpathTextGeometryReader().read(body)!!.forEngine("fixture")
                runtime.geometry(0, g)
                val row = g.lines[1]
                val x = g.textOriginX + body.layout.getPrimaryHorizontal(12)
                val y = g.textOriginY + (row.top + row.bottom) / 2
                val t = SystemClock.elapsedRealtimeNanos() / 1e6 - 5.0
                val selected = ScanpathSample(t, t + 1, -100.0, -100.0, 1, 11, 16, g.snapshotId,
                    displayX = x, displayY = y, coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
                fun offer(sample: ScanpathSample) {
                    runtime.input(0, sample)
                    val at = sample.captureMs!!
                    runtime.candidate(0, ScanpathCandidateEvidence("prefix:$at", 0, at - 1500, at, 1500.0,
                        20, 11, 16, 1, 1.0, 1.0, g.snapshotId, sample.deliveryMs,
                        "trigger_observation_delivery"))
                }
                offer(selected)
                val actions = runtime.activeActions()
                assertTrue(actions.any { it.aidType == ReadingAidType.CURRENT_SENTENCE && it.anchor.line == 1 })
                assertTrue(actions.any { it.aidType == ReadingAidType.WORD_DEFINITION &&
                    it.anchor.line == 1 && it.anchor.wordStartUtf16 == 11 })
                val settings = runtime.configurationSnapshot().getJSONObject("settings")
                assertEquals("article_smoothed_v1", settings.getString("coordinate_stream"))
                assertTrue(settings.getBoolean("smoothing"))
                // Valid originals must not rescue absent filtered coordinates.
                offer(selected.copy(captureMs = t + 1, deliveryMs = t + 2, rawX = x, rawY = y,
                    displayX = null, displayY = null))
                assertTrue(runtime.activeActions().isEmpty())
                // A raw declaration cannot be silently accepted by a smoothed runtime.
                offer(selected.copy(captureMs = t + 2, deliveryMs = t + 3, rawX = x, rawY = y,
                    coordinateStream = ScanpathCoordinateStream.CALIBRATED_RAW))
                assertTrue(runtime.activeActions().isEmpty())
            } finally { runtime.close("fixture") }
        }
    }
}
