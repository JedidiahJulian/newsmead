package com.newsmead.gaze.scanpath

import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable

/** Real elapsed times and supplied causal coordinates only; no provider, camera, or scrolling driver. */
@RunWith(AndroidJUnit4::class)
class ManualScrollScanpathSessionTest {
    @Test fun delayedPursuitRetainsIdentityAndRetargetsWordAndLineDuringManualMovement() {
        Fixture().use { f ->
            val before = f.id()
            f.onMain { f.session.manualScrollGesture(true) }
            assertEquals("Touch alone must preserve geometry", before, f.id())
            f.follow(4, 14)
            val movingId = f.id()
            assertNotEquals(before, movingId)
            f.onMain {
                assertEquals("geometry_unsettled", f.rows.first().exclusion)
                assertTrue(f.rows.last().eligible)
                assertEquals(4, f.rows.last().line)
                assertNotNull(f.session.engine.currentCandidateEvidence())
                assertEquals(1, f.rows.map { it.geometrySnapshotId }.distinct().size)
                val s = f.rows.last()
                assertEquals(s.displayX, s.processingX)
                assertEquals(-123.0, s.rawX!!, 0.0)
                assertEquals(s.processingY!! - s.motionCompensation!!.textOriginY, s.analysisY!!, 0.0)
            }
            f.follow(5, 8)
            f.onMain {
                assertEquals(movingId, f.snapshotId)
                assertTrue(f.rows.last().eligible)
                assertEquals(5, f.rows.last().line)
                val prefix = f.session.engine.currentCandidateEvidence()!!
                assertEquals(5, prefix.line)
                assertEquals(f.body.layout.getLineStart(5), prefix.wordStartUtf16)
            }
            f.onMain { f.session.manualScrollGesture(false) }
            f.follow(5, 4)
            assertEquals("Continuous post-UP movement retains provenance", movingId, f.id())
            Thread.sleep(180)
            f.onMain {
                f.move()
                assertNotEquals("Stable gap ends authorization for a later jump", movingId, f.snapshotId)
            }
        }
    }

    @Test fun fixedScreenCrossingChangingLinesDoesNotBecomeContentResidence() {
        Fixture().use { f ->
            f.onMain { f.session.manualScrollGesture(true) }
            // Establish movement/settle without feeding a synthetic residence prefix.
            repeat(6) { f.onMain { f.move() }; Thread.sleep(40) }
            lateinit var point: Capture
            f.onMain { point = f.capture(4) }
            repeat(6) {
                f.onMain {
                    // More than a line per translation changes text below the same screen point.
                    f.body.scrollBy(0, f.body.lineHeight + 2)
                    f.session.checkGeometry()
                    f.send(point.copy(atMs = now()))
                }
                Thread.sleep(40)
            }
            f.onMain {
                assertTrue(f.rows.all { it.eligible })
                assertTrue(f.rows.map { it.line }.distinct().size > 1)
                assertTrue(f.rows.map { it.analysisY }.distinct().size > 1)
                assertNull("Crossing text cannot establish a supported pending residence",
                    f.session.engine.currentCandidateEvidence())
            }
        }
    }

    @Test fun cancellationReflowConsultationAndExternalStopDiscardAuthorization() {
        Fixture().use { f ->
            for (reset in listOf<(Fixture) -> Unit>(
                { it.session.manualScrollGesture(false, cancelled = true) },
                {
                    it.body.setPadding(1, 0, 0, 0)
                    it.session.checkGeometry()
                    // TextView drops its Layout on padding changes. This detached host has
                    // no framework traversal, so explicitly finish reflow before translating.
                    it.layoutBody()
                },
                { it.session.setConsultation(true, now(), "fixture") },
                { it.session.autoScrollMotion(false, "external_scroll") },
                { it.session.boundary("reader_controls_opened") },
            )) {
                f.onMain {
                    f.session.manualScrollGesture(true)
                    f.move()
                    val initial = f.snapshotId
                    assertNotNull("Pre-reset movement must have measured geometry", initial)
                    reset(f)
                    f.session.checkGeometry()
                    val resetId = f.snapshotId
                    assertNotNull("Reset geometry must recover before the next translation", resetId)
                    f.move()
                    assertNotNull("Post-reset translation must have measured geometry", f.snapshotId)
                    assertNotEquals("Movement after reset must be uncontrolled", resetId, f.snapshotId)
                    assertNotEquals(initial, f.snapshotId)
                    f.send(f.capture(4))
                    assertFalse(f.rows.last().eligible)
                    assertNull(f.session.engine.currentCandidateEvidence())
                    f.session.setConsultation(false, now(), "fixture")
                }
            }
        }
    }

    @Test fun causalGeometryAndFreshnessGuardsStillAbstain() {
        Fixture().use { f ->
            f.onMain { f.session.manualScrollGesture(true) }
            f.follow(4, 12)
            lateinit var old: Capture
            f.onMain { old = f.capture(4) }
            Thread.sleep(280)
            f.onMain {
                f.send(old, old.atMs)
                assertEquals("scroll_input_stale", f.rows.last().exclusion)
                f.send(f.capture(4).copy(atMs = now() + 100.0))
                assertFalse(f.rows.last().eligible)
            }
            Thread.sleep(180)
            f.onMain {
                // No geometry observation for >100 ms before this past capture.
                f.send(f.capture(4).copy(atMs = now() - 40.0))
                assertEquals("scroll_geometry_unavailable", f.rows.last().exclusion)
                f.session.manualScrollGesture(false, cancelled = true)
                f.body.scrollBy(0, 2)
                f.session.checkGeometry()
                f.send(old)
                assertFalse("A pre-boundary capture must not use new geometry", f.rows.last().eligible)
            }
        }
    }

    @Test fun defaultAdapterDoesNotAuthorizeManualTranslation() {
        Fixture(manual = false).use { f ->
            f.onMain {
                f.session.manualScrollGesture(true)
                val first = f.snapshotId
                f.move()
                val second = f.snapshotId
                f.move()
                assertNotEquals(first, second)
                assertNotEquals(second, f.snapshotId)
                f.send(f.capture(4))
                assertEquals("geometry_unsettled", f.rows.last().exclusion)
            }
        }
    }

    private data class Capture(val atMs: Double, val x: Float, val y: Float)

    private class Fixture(manual: Boolean = true) : Closeable {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var body: TextView
        private lateinit var host: FrameLayout
        lateinit var session: ArticleScanpathSession
        val rows = mutableListOf<ScanpathSample>()
        var snapshotId: String? = null
        init {
            onMain {
                host = FrameLayout(instrumentation.targetContext)
                body = TextView(instrumentation.targetContext).apply {
                    text = (0..29).joinToString("\n") { "Alpha beta gamma delta" }
                    textSize = 20f
                }
                host.addView(body, FrameLayout.LayoutParams(600, 800))
                layoutBody()
                session = ArticleScanpathSession(body,
                    onGeometry = { _, g -> snapshotId = g?.snapshotId },
                    onInput = { _, s -> rows.add(s) },
                    coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED,
                    motionCompensatedAids = true, manualScrollAids = manual)
                session.checkGeometry()
            }
        }
        fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun layoutBody() {
            host.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 600, 800)
            assertNotNull("Fixture traversal must restore the TextView Layout", body.layout)
        }
        fun id(): String? { var result: String? = null; onMain { result = snapshotId }; return result }
        fun move() { body.scrollBy(0, 2); session.checkGeometry() }
        fun capture(line: Int): Capture {
            val geometry = ScanpathTextGeometryReader().read(body)!!
            val offset = body.layout.getLineStart(line) + 1
            return Capture(now(),
                (geometry.originX + (body.layout.getPrimaryHorizontal(offset) +
                    body.layout.getPrimaryHorizontal(offset + 1)) / 2).toFloat(),
                (geometry.originY + (body.layout.getLineTop(line) + body.layout.getLineBottom(line)) / 2).toFloat())
        }
        fun send(capture: Capture, outputMs: Double = now()) {
            session.record(MgazeNetGazeProvider.Observation(capture.atMs, (outputMs * 1e6).toLong(),
                rows.size.toLong(), "coordinate", capture.x, capture.y, -123f, -456f))
        }
        fun follow(line: Int, frames: Int) {
            val captures = mutableListOf<Capture>()
            repeat(frames) { frame ->
                onMain {
                    move()
                    captures.add(capture(line))
                    if (frame >= 2) send(captures[frame - 2])
                }
                Thread.sleep(40)
            }
        }
        override fun close() = onMain { session.close("fixture") }
    }

    companion object { private fun now() = SystemClock.elapsedRealtimeNanos() / 1e6 }
}
