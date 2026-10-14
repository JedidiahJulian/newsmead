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

/** Synthetic existing provider outputs only; no camera or second smoothing pass. */
@RunWith(AndroidJUnit4::class)
class SmoothedScanpathSessionTest {
    @Test fun mappingUsesFilteredPointAndMissingnessConsultationAndScrollBreakContinuity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val host = FrameLayout(ctx)
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta"; textSize = 20f }
            host.addView(body, FrameLayout.LayoutParams(600, 800))
            host.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 600, 800)
            val rows = mutableListOf<ScanpathSample>()
            val accepted = mutableListOf<Boolean>()
            val session = ArticleScanpathSession(body, onInput = { _, s -> rows.add(s) },
                onAcceptance = { _, _, value -> accepted.add(value) },
                coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            val rawSession = ArticleScanpathSession(body)
            try {
                session.checkGeometry(); rawSession.checkGeometry()
                val g = ScanpathTextGeometryReader().read(body)!!
                fun point(offset: Int): Pair<Float, Float> {
                    val line = body.layout.getLineForOffset(offset)
                    val x = g.originX + (body.layout.getPrimaryHorizontal(offset) +
                        body.layout.getPrimaryHorizontal(offset + 1)) / 2.0
                    val y = g.originY + (body.layout.getLineTop(line) + body.layout.getLineBottom(line)) / 2.0
                    return x.toFloat() to y.toFloat()
                }
                val raw = point(1); val filtered = point(12)
                val base = SystemClock.elapsedRealtimeNanos() / 1e6 + 200.0
                fun observation(t: Double, x: Float? = filtered.first, y: Float? = filtered.second,
                                reason: String = "coordinate") = MgazeNetGazeProvider.Observation(
                    t, ((t + 10) * 1e6).toLong(), rows.size.toLong(), reason,
                    x, y, raw.first, raw.second)
                for (dt in listOf(0.0, 50.0, 100.0)) session.record(observation(base + dt))
                val s = rows.last()
                assertEquals(1, s.line)
                assertEquals(11, s.wordStartUtf16)
                assertEquals(16, s.wordEndUtf16)
                assertEquals(raw.first.toDouble(), s.rawX!!, 0.0)
                assertEquals(filtered.first.toDouble(), s.processingX!!, 0.0)
                assertEquals(1, session.engine.currentCandidateEvidence()!!.line)
                rawSession.record(observation(base))
                for (dt in listOf(50.0, 100.0)) rawSession.record(observation(base + dt))
                assertEquals(0, rawSession.engine.currentCandidateEvidence()!!.line)
                for ((i, value) in listOf<Float?>(null, Float.NaN).withIndex()) {
                    session.record(observation(base + 150 + i * 50, x = value))
                    assertFalse(accepted.last())
                    assertNull(rows.last().line)
                    assertNull(session.engine.currentCandidateEvidence())
                }
                session.record(observation(base + 250, reason = "stale"))
                assertFalse(accepted.last())
                session.record(observation(base + 275, x = -100f))
                assertEquals("outside_visible_text", rows.last().exclusion)
                assertFalse(accepted.last())
                session.setConsultation(true, base + 300, "fixture")
                session.record(observation(base + 350))
                assertEquals("definition_consultation", rows.last().exclusion)
                assertFalse(accepted.last())
                session.setConsultation(false, base + 400, "fixture")
                session.record(observation(base + 375))
                assertEquals("definition_boundary_late_capture", rows.last().exclusion)
                assertFalse(accepted.last())
                session.record(observation(base + 450))
                assertTrue(accepted.last())
                val oldId = rows.last().geometrySnapshotId
                body.scrollTo(0, 10)
                session.checkGeometry()
                session.record(observation(0.0))
                assertNotEquals(oldId, rows.last().geometrySnapshotId)
                assertEquals("geometry_unsettled", rows.last().exclusion)
                assertFalse(accepted.last())
            } finally { session.close("fixture"); rawSession.close("fixture") }
        }
    }
}
