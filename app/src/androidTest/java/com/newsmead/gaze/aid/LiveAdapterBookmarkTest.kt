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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual rendered text and the live motion-enabled adapter; synthetic observations, camera off. */
@RunWith(AndroidJUnit4::class)
class LiveAdapterBookmarkTest {
    @Test fun liveAdapterLookawayAndBackreadingRetainDimAndClearOnlyAfterPassage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { screen ->
            lateinit var runtime: ArticleReadingAidRuntime
            lateinit var session: ArticleScanpathSession
            lateinit var overlay: ReadingAidOverlayView
            var frame: ScanpathGeometry? = null
            val draws = mutableSetOf<String>()
            var sequence = 0L
            fun now() = SystemClock.elapsedRealtimeNanos() / 1e6
            instrumentation.waitForIdleSync()
            var ready = false
            val layoutDeadline = SystemClock.elapsedRealtime() + 3000
            while (!ready && SystemClock.elapsedRealtime() < layoutDeadline) {
                screen.onActivity { ready = it.findViewById<TextView>(R.id.replayBody).let { body ->
                    body.layout != null && body.width > 0 && body.isShown } }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("Synthetic reading surface is not visible", ready)
            screen.onActivity { activity ->
                val body = activity.findViewById<TextView>(R.id.replayBody)
                val viewport = activity.findViewById<FrameLayout>(R.id.replayViewport)
                overlay = ReadingAidOverlayView(activity, lineStartMarkerInGutter = true)
                viewport.addView(overlay, FrameLayout.LayoutParams(-1, -1))
                runtime = ArticleReadingAidRuntime(body, activity.findViewById(R.id.replayWordDock), overlay,
                    config = ReadingAidPolicyConfig.evidenceAlignedPrototype(),
                    preferences = ReaderAidPreferences(lineGuide = false, focusRegion = false, wordDefinition = false),
                    inputSource = ReadingAidInputSource.SYNTHETIC_REPLAY,
                    coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED,
                    blinkFilterEnabled = true, motionCompensatedAids = true,
                    onRenderReceipt = { action, drawn, _, _ -> if (drawn) draws.add(action.id) })
                session = ArticleScanpathSession(body, config = runtime.scanpathConfig,
                    coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED, motionCompensatedAids = true,
                    onGeometry = { generation, geometry -> frame = geometry; runtime.geometry(generation, geometry) },
                    onInput = { generation, sample -> runtime.input(generation, sample) },
                    onCandidate = { generation, candidate -> runtime.candidate(generation, candidate) },
                    onEvent = { generation, event -> runtime.event(generation, event) },
                    onFixation = { generation, fixation -> runtime.fixation(generation, fixation) })
                session.checkGeometry()
            }
            val visible = mutableListOf<Int>()
            screen.onActivity {
                val g = checkNotNull(frame)
                visible.addAll(g.lines.indices.filter { line ->
                    val row = g.lines[line]; val v = g.visibleRectScreen
                    row.top + g.textOriginY >= v.top && row.bottom + g.textOriginY <= v.bottom && row.right > row.left
                }.take(6))
            }
            fun hold(line: Int?, ms: Int, reason: String = "coordinate") {
                val until = SystemClock.elapsedRealtime() + ms
                while (SystemClock.elapsedRealtime() < until) {
                    screen.onActivity {
                        val g = checkNotNull(frame)
                        val row = line?.let { g.lines[it] }
                        val x = row?.let { (g.textOriginX + (it.left + it.right) / 2).toFloat() }
                        val y = row?.let { (g.textOriginY + (it.top + it.bottom) / 2).toFloat() }
                        session.record(MgazeNetGazeProvider.Observation(now() - 5,
                            SystemClock.elapsedRealtimeNanos(), ++sequence, reason,
                            x = x, y = y, rawX = x, rawY = y, blinkFilterApplied = true))
                    }
                    SystemClock.sleep(50)
                }
            }
            fun bookmark(): ReadingAidAction? {
                var action: ReadingAidAction? = null
                screen.onActivity { action = runtime.activeActions().firstOrNull { a -> a.aidType == ReadingAidType.READING_BOOKMARK } }
                return action
            }
            try {
                assertEquals("Need six actual visible rows", 6, visible.size)
                visible.take(4).forEach { hold(it, 500) }
                hold(null, 50, "blink")
                hold(visible[3], 300)
                hold(null, 700, "no_face")
                val away = checkNotNull(bookmark()) { "Live adapter failed to activate lookaway bookmark" }
                assertEquals(visible[3], away.anchor.line)
                assertEquals("sustained_gaze_unavailable", away.bookmarkEvidence!!.departureReason)
                assertEquals(.75, away.strength, .00001)
                hold(visible[0], 600); hold(visible[1], 600); hold(visible[2], 600)
                val dimmed = checkNotNull(bookmark())
                assertEquals(away.id, dimmed.id)
                assertTrue(dimmed.strength < away.strength)
                assertTrue(dimmed.strength >= LineStartMarkerAppearance.BOOKMARK_DIMMED_STRENGTH - .00001)
                hold(visible[3], 600)
                assertEquals(.40, bookmark()!!.strength, .00001)
                hold(visible[4], 600)
                assertNull("Forward passage must release", bookmark())
                hold(visible[5], 600); hold(visible[1], 700)
                val backread = checkNotNull(bookmark()) { "Live adapter failed to activate supported backreading" }
                assertEquals(visible[5], backread.anchor.line)
                assertEquals("supported_backward_reading", backread.bookmarkEvidence!!.departureReason)
                assertEquals(.75, backread.strength, .00001)
                hold(visible[2], 700)
                screen.onActivity { activity ->
                    assertTrue("Bookmark must actually draw", draws.contains(backread.id))
                    val view = activity.window.decorView
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    // Small synthetic-screen artifact only; no camera image or participant recording.
                    val file = File(activity.cacheDir, "bookmark-live-adapter.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            } finally {
                screen.onActivity { session.close("fixture_complete"); runtime.close("fixture_complete")
                    (overlay.parent as? FrameLayout)?.removeView(overlay) }
            }
        }
    }
}
