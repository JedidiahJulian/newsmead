package com.newsmead.gaze

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.mgazenet.NinePointAccuracySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class NinePointAccuracyOverlayTest {

    @Test
    fun rendersAllNineRedTargetsMeasuredBlueEstimatesAndClearsCleanly() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val overlay = NinePointAccuracyOverlayView(context)
            val width = 1080
            val height = 2340
            overlay.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
            )
            overlay.layout(0, 0, width, height)

            val density = context.resources.displayMetrics.density
            val edgePadPx = 12f * density
            val xs = listOf(216f, 540f, 864f)
            val ys = listOf(468f, 1170f, 1872f)
            val targets = ys.flatMap { y -> xs.map { x -> x to y } }

            // 7 normal in-bounds points, point 8 has an off-screen estimate, point 9 is unmeasured.
            val results = targets.mapIndexed { i, (tx, ty) ->
                val idx = i + 1
                when (idx) {
                    8 -> NinePointAccuracySummary.PointResult(
                        index = idx,
                        targetLocalX = tx,
                        targetLocalY = ty,
                        estimateLocalX = 1450f,
                        estimateLocalY = -120f,
                        sampleCount = 12,
                        dxCm = 2.0,
                        dyCm = -3.0,
                        radialCm = 3.606,
                    )
                    9 -> NinePointAccuracySummary.PointResult(
                        index = idx,
                        targetLocalX = tx,
                        targetLocalY = ty,
                        estimateLocalX = null,
                        estimateLocalY = null,
                        sampleCount = 0,
                        dxCm = null,
                        dyCm = null,
                        radialCm = null,
                    )
                    else -> NinePointAccuracySummary.PointResult(
                        index = idx,
                        targetLocalX = tx,
                        targetLocalY = ty,
                        estimateLocalX = tx + 70f,
                        estimateLocalY = ty - 90f,
                        sampleCount = 20,
                        dxCm = 0.4,
                        dyCm = -0.5,
                        radialCm = 0.64,
                    )
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                overlay.setResults(results)
                assertEquals(9, overlay.currentResults().size)
                bitmap.eraseColor(Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))

                val redRadius = ceil(8f * density).toInt()
                val blueRadius = ceil(7f * density).toInt()
                for (pt in results) {
                    assertTrue(
                        "Expected red target dot around target ${pt.index}",
                        hasColorNear(
                            bitmap = bitmap,
                            cx = pt.targetLocalX.toInt(),
                            cy = pt.targetLocalY.toInt(),
                            radius = redRadius,
                            targetColor = NinePointAccuracyOverlayView.TARGET_RED_COLOR,
                        ),
                    )
                    val ex = pt.estimateLocalX
                    val ey = pt.estimateLocalY
                    if (ex != null && ey != null) {
                        val clamped = NinePointAccuracySummary.clampEstimateToBounds(
                            ex,
                            ey,
                            width.toFloat(),
                            height.toFloat(),
                            edgePadPx,
                        )
                        assertTrue(
                            "Expected blue estimate dot around target ${pt.index}",
                            hasColorNear(
                                bitmap = bitmap,
                                cx = clamped.x.toInt(),
                                cy = clamped.y.toInt(),
                                radius = blueRadius,
                                targetColor = NinePointAccuracyOverlayView.ESTIMATE_BLUE_COLOR,
                            ),
                        )
                    }
                }

                overlay.clearResults()
                assertTrue(overlay.currentResults().isEmpty())
                bitmap.eraseColor(Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                for (pt in results) {
                    assertEquals(0, Color.alpha(bitmap.getPixel(pt.targetLocalX.toInt(), pt.targetLocalY.toInt())))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun hasColorNear(
        bitmap: Bitmap,
        cx: Int,
        cy: Int,
        radius: Int,
        targetColor: Int,
    ): Boolean {
        val tr = Color.red(targetColor)
        val tg = Color.green(targetColor)
        val tb = Color.blue(targetColor)
        val minX = (cx - radius).coerceIn(0, bitmap.width - 1)
        val maxX = (cx + radius).coerceIn(0, bitmap.width - 1)
        val minY = (cy - radius).coerceIn(0, bitmap.height - 1)
        val maxY = (cy + radius).coerceIn(0, bitmap.height - 1)
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 200 &&
                    kotlin.math.abs(Color.red(pixel) - tr) <= 20 &&
                    kotlin.math.abs(Color.green(pixel) - tg) <= 20 &&
                    kotlin.math.abs(Color.blue(pixel) - tb) <= 20
                ) {
                    return true
                }
            }
        }
        return false
    }
}
