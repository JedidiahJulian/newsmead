package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class NinePointAccuracySummaryTest {

    @Test
    fun buildPointResultComputesMedianEstimateAndCmErrors() {
        val pxPerCmX = 200.0
        val pxPerCmY = 100.0
        // Odd sample count (3 samples): median x offset = +100 px (+0.5 cm), median y offset = -50 px (-0.5 cm)
        val odd = NinePointAccuracySummary.buildPointResult(
            index = 1,
            targetScreenX = 216f,
            targetScreenY = 548f,
            targetLocalX = 216f,
            targetLocalY = 447f,
            samples = listOf(
                216f + 400f to 548f - 10f,
                216f + 100f to 548f - 50f,
                216f + 50f to 548f - 200f,
                Float.NaN to 100f,
            ),
            pxPerCmX = pxPerCmX,
            pxPerCmY = pxPerCmY,
        )
        assertEquals(3, odd.sampleCount)
        assertEquals(316f, odd.estimateLocalX!!, 0.001f)
        assertEquals(397f, odd.estimateLocalY!!, 0.001f)
        assertEquals(0.5, odd.dxCm!!, 1e-6)
        assertEquals(-0.5, odd.dyCm!!, 1e-6)
        assertEquals(hypot(0.5, -0.5), odd.radialCm!!, 1e-6)

        // Even sample count (4 samples): median averages middle two -> dx = 150 px (0.75 cm), dy = 200 px (2.0 cm)
        val even = NinePointAccuracySummary.buildPointResult(
            index = 2,
            targetScreenX = 540f,
            targetScreenY = 600f,
            targetLocalX = 540f,
            targetLocalY = 499f,
            samples = listOf(
                540f + 50f to 600f + 100f,
                540f + 100f to 600f + 150f,
                540f + 200f to 600f + 250f,
                540f + 900f to 600f + 400f,
            ),
            pxPerCmX = pxPerCmX,
            pxPerCmY = pxPerCmY,
        )
        assertEquals(4, even.sampleCount)
        assertEquals(690f, even.estimateLocalX!!, 0.001f)
        assertEquals(699f, even.estimateLocalY!!, 0.001f)
        assertEquals(0.75, even.dxCm!!, 1e-6)
        assertEquals(2.0, even.dyCm!!, 1e-6)
        assertEquals(hypot(0.75, 2.0), even.radialCm!!, 1e-6)
    }

    @Test
    fun buildPointResultLeavesUnmeasuredTargetsWithoutEstimate() {
        val missing = NinePointAccuracySummary.buildPointResult(
            index = 5,
            targetScreenX = 540f,
            targetScreenY = 1220f,
            targetLocalX = 540f,
            targetLocalY = 1119f,
            samples = listOf(Float.NaN to 10f, 10f to Float.POSITIVE_INFINITY),
            pxPerCmX = 160.0,
            pxPerCmY = 160.0,
        )
        assertEquals(5, missing.index)
        assertEquals(540f, missing.targetLocalX, 0f)
        assertEquals(1119f, missing.targetLocalY, 0f)
        assertEquals(0, missing.sampleCount)
        assertNull(missing.estimateLocalX)
        assertNull(missing.estimateLocalY)
        assertNull(missing.dxCm)
        assertNull(missing.dyCm)
        assertNull(missing.radialCm)
    }

    @Test
    fun summarizeHandlesCompletePartialAndEmptyRuns() {
        val px = 100.0
        val points = (1..9).map { idx ->
            if (idx == 9) {
                NinePointAccuracySummary.buildPointResult(
                    index = idx,
                    targetScreenX = 800f,
                    targetScreenY = 1800f,
                    targetLocalX = 800f,
                    targetLocalY = 1800f,
                    samples = emptyList(),
                    pxPerCmX = px,
                    pxPerCmY = px,
                )
            } else {
                // dx = 0, dy = idx * 10 px -> 0.1 * idx cm
                val dyPx = idx * 10f
                val s = 100f to (100f + dyPx)
                NinePointAccuracySummary.buildPointResult(
                    index = idx,
                    targetScreenX = 100f,
                    targetScreenY = 100f,
                    targetLocalX = 100f,
                    targetLocalY = 100f,
                    samples = listOf(s),
                    pxPerCmX = px,
                    pxPerCmY = px,
                )
            }
        }
        val partial = NinePointAccuracySummary.summarize(points)
        assertEquals(8, partial.measuredCount)
        assertEquals(9, partial.totalCount)
        // Sorted radial values across 1..8: 0.1..0.8 -> median = (0.4 + 0.5) / 2 = 0.45
        assertEquals(0.45, partial.median2dCm!!, 1e-6)
        assertEquals(8, partial.worstTargetIndex)
        assertEquals(0.8, partial.worst2dCm!!, 1e-6)
        assertEquals(0.45, partial.medianAbsVerticalCm!!, 1e-6)

        val empty = NinePointAccuracySummary.summarize(points.filter { it.sampleCount == 0 })
        assertEquals(0, empty.measuredCount)
        assertEquals(1, empty.totalCount)
        assertNull(empty.median2dCm)
        assertNull(empty.worstTargetIndex)
        assertNull(empty.worst2dCm)
        assertNull(empty.medianAbsVerticalCm)
    }

    @Test
    fun clampEstimateToBoundsHandlesInsideEdgesAndSmallViewports() {
        val inside = NinePointAccuracySummary.clampEstimateToBounds(500f, 800f, 1080f, 2340f, 24f)
        assertFalse(inside.clamped)
        assertEquals(500f, inside.x, 0f)
        assertEquals(800f, inside.y, 0f)

        val topLeftOut = NinePointAccuracySummary.clampEstimateToBounds(-10f, -50f, 1080f, 2340f, 24f)
        assertTrue(topLeftOut.clamped)
        assertEquals(24f, topLeftOut.x, 0f)
        assertEquals(24f, topLeftOut.y, 0f)

        val bottomRightOut = NinePointAccuracySummary.clampEstimateToBounds(1200f, 2500f, 1080f, 2340f, 24f)
        assertTrue(bottomRightOut.clamped)
        assertEquals(1056f, bottomRightOut.x, 0f)
        assertEquals(2316f, bottomRightOut.y, 0f)

        val zeroViewport = NinePointAccuracySummary.clampEstimateToBounds(50f, 50f, 0f, 0f, 24f)
        assertTrue(zeroViewport.clamped)
        assertEquals(0f, zeroViewport.x, 0f)
        assertEquals(0f, zeroViewport.y, 0f)

        val tinyViewport = NinePointAccuracySummary.clampEstimateToBounds(100f, 100f, 10f, 10f, 24f)
        assertTrue(tinyViewport.clamped)
        assertEquals(5f, tinyViewport.x, 0f)
        assertEquals(5f, tinyViewport.y, 0f)
    }

    @Test
    fun formattingPreservesUnitsDenominatorsAndDisclosures() {
        val pt = NinePointAccuracySummary.buildPointResult(
            index = 1,
            targetScreenX = 200f,
            targetScreenY = 400f,
            targetLocalX = 200f,
            targetLocalY = 400f,
            samples = listOf(250f to 350f),
            pxPerCmX = 100.0,
            pxPerCmY = 100.0,
        )
        val missing = NinePointAccuracySummary.buildPointResult(
            index = 2,
            targetScreenX = 500f,
            targetScreenY = 400f,
            targetLocalX = 500f,
            targetLocalY = 400f,
            samples = emptyList(),
            pxPerCmX = 100.0,
            pxPerCmY = 100.0,
        )
        val summary = NinePointAccuracySummary.summarize(listOf(pt, missing))
        assertEquals("Nine-point accuracy · 1/2 measured", NinePointAccuracySummary.formatHeadline(summary))
        val metrics = NinePointAccuracySummary.formatMetrics(summary)
        assertTrue(metrics.contains("Median 2D:"))
        assertTrue(metrics.contains("cm"))
        assertTrue(metrics.contains("(#1)"))

        val details = NinePointAccuracySummary.formatDetails(summary)
        assertTrue(details.contains("Measured targets: 1 of 2"))
        assertTrue(details.contains("Target 1: n=1, dx=0.500 cm, dy=-0.500 cm, 2D=0.707 cm"))
        assertTrue(details.contains("Target 2: no fresh coordinates"))
        assertTrue(details.contains("Eight targets were held out from calibration; the centre is a repeat."))
        assertTrue(details.contains("This check does not establish reading accuracy."))
    }
}
