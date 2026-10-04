package com.newsmead.gaze.aid

import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Scripted coordinates only. Exercises consultation opened inside finalization callbacks. */
@RunWith(AndroidJUnit4::class)
class DefinitionConsultationSessionTest {
    @Test fun finalizedResidenceOpeningDoesNotReenterOrBridgeConsultation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val host = FrameLayout(ctx)
            val body = TextView(ctx).apply { text = "keyboard another\nSecond article line"; textSize = 16f }
            host.addView(body, FrameLayout.LayoutParams(1000, 1000))
            host.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 1000, 1000)
            val events = mutableListOf<ScanpathEvent>()
            val inputs = mutableListOf<ScanpathSample>()
            val accepted = mutableListOf<Boolean>()
            var openingTime = 0.0
            var opened = false
            lateinit var session: ArticleScanpathSession
            session = ArticleScanpathSession(body, ScanpathConfig(wordResidenceMs = 800.0),
                onEvent = { _, event ->
                    events.add(event)
                    if (!opened && event.patternId == "P12" && event.resolution == "word" &&
                        event.metrics["kind"] == "residence_update") {
                        opened = true
                        session.setConsultation(true, openingTime, "scripted_event_open")
                    }
                }, onInput = { _, value -> inputs.add(value) }, onAcceptance = { _, _, value -> accepted.add(value) })
            try {
                session.checkGeometry()
                val frame = ScanpathTextGeometryReader().read(body)!!
                val left = (frame.originX + body.layout.getPrimaryHorizontal(0) + 1).toFloat()
                val right = (frame.originX + body.layout.getPrimaryHorizontal(8) - 1).toFloat()
                val y = (frame.originY + body.layout.getLineBottom(0) / 2.0).toFloat()
                assertTrue(right - left > frame.lineHeightPx * 0.5)
                val base = SystemClock.elapsedRealtimeNanos() / 1e6 + 200.0
                fun record(t: Double, x: Float = left) = session.record(MgazeNetGazeProvider.Observation(
                    t, ((t + 10) * 1e6).toLong(), inputs.size.toLong(), "coordinate", rawX = x, rawY = y))
                repeat(9) { record(base + it * 100) }
                openingTime = base + 900
                record(openingTime, right)
                assertTrue(opened)
                assertEquals("No duplicate fixation from reentrant flush", 1, session.engine.fixationCount)
                assertEquals(1, events.count { it.patternId == "P12" && it.resolution == "word" && it.metrics["kind"] == "residence_update" })
                record(base + 1000)
                assertEquals("definition_consultation", inputs.last().exclusion)
                assertEquals(left.toDouble(), inputs.last().rawX!!, 0.0)
                assertFalse(accepted.last())
                session.setConsultation(false, base + 1100, "scripted_dismiss")
                record(base + 1050)
                assertEquals("definition_boundary_late_capture", inputs.last().exclusion)
                record(base + 1200, right)
                assertTrue(accepted.last())
                record(base + 1300, right)
                record(base + 1400, right)
                session.engine.boundary("scripted_finalize", base + 1450)
                val first = session.engine.fixations.first()
                val resumed = session.engine.fixations.last()
                assertEquals(2, session.engine.fixationCount)
                assertNotEquals(first.segment, resumed.segment)
                assertTrue(resumed.leftCensored)
                assertFalse(events.any { it.patternId == "P04" })
            } finally { session.close("fixture_closed") }
        }
    }
}
