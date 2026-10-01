package com.newsmead.gaze.aid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Idle synthetic view checks only; never creates a provider/camera or recording. */
@RunWith(AndroidJUnit4::class)
class ReadingAidRenderingTest {
    private fun action(expires: Double) = ReadingAidAction("a1", ReadingAidType.WORD_MAGNIFIER,
        "CONTEXT_WORD_MAGNIFIER_V1", "synthetic", ReadingAidAnchor(0, "g1", 0, 0, 7),
        1.0, 1.5, null, 0.0, expires, emptyList(), listOf("candidate:1"), 0.0, 100.0, 110.0,
        "synthetic", listOf("W24"), "software check", true, "synthetic")

    @Test fun magnificationUsesItsReservedViewAndReportsADrawProxy() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = TextView(context).apply { text = "example paragraph"; textSize = 20f }
            val dock = ReadingAidWordView(context)
            dock.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
            dock.layout(0, 0, 1000, 300)
            val receipt = mutableListOf<Pair<Boolean, String>>()
            dock.onRender = { _, drawn, reason, _ -> receipt.add(drawn to reason) }
            dock.showAction(action(SystemClock.elapsedRealtimeNanos() / 1e6 + 1500), source)
            assertEquals("example", dock.text.toString())
            assertEquals(source.textSize * 1.5f, dock.textSize, 0.01f)
            assertEquals("example paragraph", source.text.toString())
            val bitmap = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
            try { dock.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
            assertTrue(receipt.contains(true to "text_draw_proxy"))
        }
    }

    @Test fun anExpiredMagnifierNeverReportsADrawnFrame() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = TextView(context).apply { text = "example paragraph"; textSize = 20f }
            val dock = ReadingAidWordView(context)
            dock.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
            dock.layout(0, 0, 1000, 300)
            val receipt = mutableListOf<Pair<Boolean, String>>()
            dock.onRender = { _, drawn, reason, _ -> receipt.add(drawn to reason) }
            dock.showAction(action(0.0), source)
            val bitmap = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
            try { dock.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
            assertTrue(receipt.contains(false to "expired_before_draw"))
            assertFalse(receipt.any { it.first })
        }
    }
}
