package com.newsmead.gaze.aid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.TextView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.scanpath.ScanpathTextGeometryReader
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

    @Test fun articleLineStartMarkerDrawsInPaddingWithoutCoveringTheText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val density = context.resources.displayMetrics.density
            val width = (320 * density).toInt()
            val height = (120 * density).toInt()
            val root = FrameLayout(context)
            val source = TextView(context).apply {
                text = "example article line"
                textSize = 20f
                setPadding((16 * density).toInt(), 0, 0, 0)
            }
            val overlay = ReadingAidOverlayView(context, lineStartMarkerInGutter = true)
            root.addView(source, FrameLayout.LayoutParams(width, height))
            root.addView(overlay, FrameLayout.LayoutParams(width, height))
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val geometry = ScanpathTextGeometryReader().read(source)!!.forEngine("g1")
            val marker = action(SystemClock.elapsedRealtimeNanos() / 1e6 + 5000).copy(
                aidType = ReadingAidType.NEXT_LINE_START,
                aidFormId = LineStartMarkerAppearance.FORM_ID,
                magnificationScale = null,
            )
            val receipts = mutableListOf<Pair<Boolean, String>>()
            overlay.onRender = { _, drawn, reason, _ -> receipts.add(drawn to reason) }
            overlay.setActions(source, geometry, 0, listOf(marker))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                overlay.draw(Canvas(bitmap))
                var lastPaintedX = -1
                for (y in 0 until height) for (x in 0 until width) {
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) != 0) lastPaintedX = maxOf(lastPaintedX, x)
                }
                assertTrue("The marker must actually draw", lastPaintedX >= 0)
                assertTrue("Marker pixels must clear the first letter", lastPaintedX < source.totalPaddingLeft - 3 * density)
                assertTrue(receipts.contains(true to "canvas_draw_proxy"))
                assertEquals("example article line", source.text.toString())
                val alternate = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    overlay.setActions(source, geometry, 0, listOf(marker.copy(id = "low_strength", strength = 0.1)))
                    overlay.draw(Canvas(alternate))
                    assertTrue("Marker appearance must be independent of strength", bitmap.sameAs(alternate))
                } finally { alternate.recycle() }
            } finally { bitmap.recycle() }
        }
    }

    @Test fun centeredStripFitsLowThenHighStrengthButWithholdsOversizedWords() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val density = context.resources.displayMetrics.density
            val width = (320 * density).toInt()
            val height = (68 * density).toInt()
            val source = TextView(context).apply {
                text = "example paragraph"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 22f)
            }
            val dock = ReadingAidWordView(context).apply {
                layoutParams = ViewGroup.LayoutParams(width, height)
                gravity = Gravity.CENTER
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            }
            fun layoutDock() {
                dock.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                dock.layout(0, 0, width, height)
            }
            layoutDock()
            val receipts = mutableListOf<Pair<Boolean, String>>()
            dock.onRender = { _, drawn, reason, _ -> receipts.add(drawn to reason) }
            val base = action(SystemClock.elapsedRealtimeNanos() / 1e6 + 5000)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                dock.showAction(base.copy(id = "low", strength = 0.25, magnificationScale = 1.125), source)
                layoutDock()
                assertEquals("example", dock.text.toString())
                dock.draw(Canvas(bitmap))
                assertTrue(receipts.contains(true to "text_draw_proxy"))
                receipts.clear()
                dock.showAction(base.copy(id = "high"), source)
                layoutDock()
                assertEquals("example", dock.text.toString())
                assertEquals(source.textSize * 1.5f, dock.textSize, 0.01f)
                assertEquals(height, dock.height)
                dock.draw(Canvas(bitmap))
                assertTrue(receipts.contains(true to "text_draw_proxy"))
                receipts.clear()
                val oversized = "W".repeat(100)
                source.text = oversized
                dock.showAction(base.copy(id = "too_wide", anchor = base.anchor.copy(wordEndUtf16 = oversized.length)), source)
                assertTrue(dock.text.isEmpty())
                assertTrue(receipts.contains(false to "magnified_word_does_not_fit"))
                receipts.clear()
                source.text = "example"
                source.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 100f)
                dock.showAction(base.copy(id = "too_tall"), source)
                assertTrue(dock.text.isEmpty())
                assertTrue(receipts.contains(false to "magnified_word_does_not_fit"))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun magnificationUsesItsReservedViewAndReportsADrawProxy() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = TextView(context).apply { text = "example paragraph"; textSize = 20f }
            val dock = ReadingAidWordView(context).apply {
                layoutParams = ViewGroup.LayoutParams(1000, 300)
            }
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
            val dock = ReadingAidWordView(context).apply {
                layoutParams = ViewGroup.LayoutParams(1000, 300)
            }
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
