package com.newsmead.gaze.aid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.gaze.scanpath.ScanpathTextGeometryReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native text background, in-memory pixels only; no camera or human-reference claim. */
@RunWith(AndroidJUnit4::class)
class ReadingGuideRenderingTest {
    private fun guide(generation: Long, id: String, line: Int, range: ReadingGuideRange, expires: Double) =
        ReadingAidAction("guide", ReadingAidType.CURRENT_SENTENCE, "CURRENT_SENTENCE_CONTEXT_HIGHLIGHT_V1",
            "fixture", ReadingAidAnchor(generation, id, line), .25, null, null,
            0.0, expires, emptyList(), emptyList(), 0.0, 0.0, 0.0,
            "fixture", emptyList(), "Synthetic rendering fixture", true, "fixture", guideRange = range)

    @Test fun grayContextPreservesGlyphColorTextAndWrappingThenClears() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.replayRoot)
                val body = activity.findViewById<TextView>(R.id.replayBody)
                val overlay = (0 until root.childCount).map(root::getChildAt).filterIsInstance<ReadingAidOverlayView>().single()
                val geometry = ScanpathTextGeometryReader().read(body)!!.forEngine("guide-render")
                val line = geometry.lines.indexOfFirst { it.endUtf16 > it.startUtf16 }
                val text = body.text.toString()
                val foreground = body.currentTextColor
                val range = ReadingGuideResolver().resolve(text, geometry, line)!!
                val action = guide(0, geometry.snapshotId, line, range, SystemClock.elapsedRealtimeNanos() / 1e6 + 5000)
                val baseline = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                val painted = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(baseline))
                    overlay.setActions(body, geometry, 0, listOf(action))
                    root.measure(android.view.View.MeasureSpec.makeMeasureSpec(root.width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(root.height, android.view.View.MeasureSpec.EXACTLY))
                    root.layout(root.left, root.top, root.right, root.bottom)
                    assertEquals(geometry.lines, ScanpathTextGeometryReader().read(body)!!.lines)
                    root.draw(Canvas(painted))
                    assertEquals(text, body.text.toString()); assertEquals(foreground, body.currentTextColor)
                    assertFalse("Context fill must be visible", baseline.sameAs(painted))
                    val origin = IntArray(2); root.getLocationOnScreen(origin)
                    val top = (geometry.textOriginY + geometry.lines[line].top - origin[1]).toInt().coerceAtLeast(0)
                    val bottom = (geometry.textOriginY + geometry.lines[line].bottom - origin[1]).toInt().coerceAtMost(root.height)
                    var solidGlyphPixels = 0
                    for (y in top until bottom) for (x in 0 until root.width) {
                        if (baseline.getPixel(x, y) == foreground) {
                            solidGlyphPixels++
                            assertEquals("Background must not tint solid glyph pixels", foreground, painted.getPixel(x, y))
                        }
                    }
                    assertTrue(solidGlyphPixels > 0)
                    overlay.setActions(body, geometry, 0, emptyList())
                    root.draw(Canvas(painted))
                    assertTrue("Disabling must remove only our background", baseline.sameAs(painted))
                } finally { baseline.recycle(); painted.recycle(); overlay.setActions(body, null, 0, emptyList()) }
            }
        }
    }

    @Test fun expiredAndUnavailableRangesNeverReportDrawn() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.replayRoot)
                val body = activity.findViewById<TextView>(R.id.replayBody)
                val overlay = (0 until root.childCount).map(root::getChildAt).filterIsInstance<ReadingAidOverlayView>().single()
                val g = ScanpathTextGeometryReader().read(body)!!.forEngine("range-expiry")
                val range = ReadingGuideResolver().resolve(body.text.toString(), g, 0)!!
                val receipts = mutableListOf<Boolean>()
                overlay.onRender = { _, drawn, _, _ -> receipts.add(drawn) }
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                try {
                    overlay.setActions(body, g, 0, listOf(guide(0, g.snapshotId, 0, range, 0.0)))
                    root.measure(android.view.View.MeasureSpec.makeMeasureSpec(root.width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(root.height, android.view.View.MeasureSpec.EXACTLY))
                    root.layout(root.left, root.top, root.right, root.bottom)
                    overlay.draw(Canvas(bitmap))
                    assertTrue("Expired action must produce a receipt", receipts.isNotEmpty())
                    assertFalse(receipts.any { it })
                    receipts.clear()
                    val invalid = range.copy(endUtf16 = body.text.length + 1)
                    overlay.setActions(body, g, 0, listOf(guide(0, g.snapshotId, 0, invalid,
                        SystemClock.elapsedRealtimeNanos() / 1e6 + 5000).copy(id = "invalid")))
                    overlay.draw(Canvas(bitmap))
                    assertTrue("Invalid action must produce a receipt", receipts.isNotEmpty())
                    assertFalse(receipts.any { it })
                } finally { bitmap.recycle(); overlay.setActions(body, null, 0, emptyList()) }
            }
        }
    }
}
