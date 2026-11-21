package com.newsmead.gaze.mgazenet

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/** Pure computation and formatting for the explicit-start nine-point MGazeNet accuracy check. */
object NinePointAccuracySummary {

    data class PointResult(
        val index: Int,
        val targetLocalX: Float,
        val targetLocalY: Float,
        val estimateLocalX: Float?,
        val estimateLocalY: Float?,
        val sampleCount: Int,
        val dxCm: Double?,
        val dyCm: Double?,
        val radialCm: Double?,
    )

    data class Summary(
        val points: List<PointResult>,
        val measuredCount: Int,
        val totalCount: Int,
        val median2dCm: Double?,
        val worstTargetIndex: Int?,
        val worst2dCm: Double?,
        val medianAbsVerticalCm: Double?,
    )

    data class ClampedPoint(
        val x: Float,
        val y: Float,
        val clamped: Boolean,
    )

    fun buildPointResult(
        index: Int,
        targetScreenX: Float,
        targetScreenY: Float,
        targetLocalX: Float,
        targetLocalY: Float,
        samples: List<Pair<Float, Float>>,
        pxPerCmX: Double,
        pxPerCmY: Double,
    ): PointResult {
        require(pxPerCmX.isFinite() && pxPerCmX > 0.0) { "pxPerCmX must be positive and finite" }
        require(pxPerCmY.isFinite() && pxPerCmY > 0.0) { "pxPerCmY must be positive and finite" }
        val valid = samples.filter { (x, y) -> x.isFinite() && y.isFinite() }
        if (valid.isEmpty() || !targetScreenX.isFinite() || !targetScreenY.isFinite() ||
            !targetLocalX.isFinite() || !targetLocalY.isFinite()
        ) {
            return PointResult(
                index = index,
                targetLocalX = targetLocalX,
                targetLocalY = targetLocalY,
                estimateLocalX = null,
                estimateLocalY = null,
                sampleCount = 0,
                dxCm = null,
                dyCm = null,
                radialCm = null,
            )
        }
        val dxPx = medianFloat(valid.map { it.first - targetScreenX })
        val dyPx = medianFloat(valid.map { it.second - targetScreenY })
        val dxCm = dxPx / pxPerCmX
        val dyCm = dyPx / pxPerCmY
        val radialCm = hypot(dxCm, dyCm)
        return PointResult(
            index = index,
            targetLocalX = targetLocalX,
            targetLocalY = targetLocalY,
            estimateLocalX = targetLocalX + dxPx,
            estimateLocalY = targetLocalY + dyPx,
            sampleCount = valid.size,
            dxCm = dxCm,
            dyCm = dyCm,
            radialCm = radialCm,
        )
    }

    fun summarize(points: List<PointResult>): Summary {
        val measured = points.filter {
            it.radialCm != null && it.dyCm != null && it.radialCm.isFinite() && it.dyCm.isFinite()
        }
        if (measured.isEmpty()) {
            return Summary(
                points = points.toList(),
                measuredCount = 0,
                totalCount = points.size,
                median2dCm = null,
                worstTargetIndex = null,
                worst2dCm = null,
                medianAbsVerticalCm = null,
            )
        }
        val median2dCm = medianDouble(measured.map { it.radialCm!! })
        val worst = measured.maxBy { it.radialCm!! }
        val medianAbsVerticalCm = medianDouble(measured.map { abs(it.dyCm!!) })
        return Summary(
            points = points.toList(),
            measuredCount = measured.size,
            totalCount = points.size,
            median2dCm = median2dCm,
            worstTargetIndex = worst.index,
            worst2dCm = worst.radialCm,
            medianAbsVerticalCm = medianAbsVerticalCm,
        )
    }

    fun clampEstimateToBounds(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        paddingPx: Float,
    ): ClampedPoint {
        val maxX = maxOf(0f, width)
        val maxY = maxOf(0f, height)
        val nonNegPad = if (paddingPx.isFinite()) maxOf(0f, paddingPx) else 0f
        val padX = nonNegPad.coerceIn(0f, maxX / 2f)
        val padY = nonNegPad.coerceIn(0f, maxY / 2f)
        val cx = if (x.isFinite()) x.coerceIn(padX, maxX - padX) else padX
        val cy = if (y.isFinite()) y.coerceIn(padY, maxY - padY) else padY
        return ClampedPoint(x = cx, y = cy, clamped = !x.isFinite() || !y.isFinite() || cx != x || cy != y)
    }

    fun formatHeadline(summary: Summary): String =
        "Nine-point accuracy · ${summary.measuredCount}/${summary.totalCount} measured"

    fun formatMetrics(summary: Summary): String {
        val median2d = summary.median2dCm
        val worst2d = summary.worst2dCm
        val worstIdx = summary.worstTargetIndex
        val medianVert = summary.medianAbsVerticalCm
        if (summary.measuredCount == 0 || median2d == null || worst2d == null || worstIdx == null || medianVert == null) {
            return "No targets produced usable coordinates."
        }
        return String.format(
            Locale.US,
            "Median 2D: %.2f cm  ·  Worst: %.2f cm (#%d)\nMedian |vertical bias|: %.2f cm",
            median2d,
            worst2d,
            worstIdx,
            medianVert,
        )
    }

    fun formatDetails(summary: Summary): String {
        val median2d = summary.median2dCm
        val worst2d = summary.worst2dCm
        val worstIdx = summary.worstTargetIndex
        val medianVert = summary.medianAbsVerticalCm
        val metrics = if (summary.measuredCount == 0 || median2d == null || worst2d == null || worstIdx == null || medianVert == null) {
            "No targets produced usable coordinates."
        } else {
            "Measured targets: ${summary.measuredCount} of ${summary.totalCount}\n" +
                String.format(Locale.US, "Median 2D target error: %.3f cm\n", median2d) +
                String.format(Locale.US, "Worst target: %d at %.3f cm\n", worstIdx, worst2d) +
                String.format(Locale.US, "Median |vertical bias|: %.3f cm", medianVert)
        }
        val rows = summary.points.joinToString("\n") { point ->
            val dxCm = point.dxCm
            val dyCm = point.dyCm
            val radialCm = point.radialCm
            if (point.sampleCount == 0 || dxCm == null || dyCm == null || radialCm == null) {
                "Target ${point.index}: no fresh coordinates"
            } else {
                String.format(
                    Locale.US,
                    "Target %d: n=%d, dx=%.3f cm, dy=%.3f cm, 2D=%.3f cm",
                    point.index,
                    point.sampleCount,
                    dxCm,
                    dyCm,
                    radialCm,
                )
            }
        }
        return "Nine-point result\n$metrics\n\n$rows\n\n" +
            "Eight targets were held out from calibration; the centre is a repeat. " +
            "Raw MGazeNet with 500 ms operational expiry. No accuracy pass or correction. " +
            "This check does not establish reading accuracy."
    }

    private fun medianFloat(values: List<Float>): Float = values.sorted().let {
        (it[(it.size - 1) / 2] + it[it.size / 2]) / 2f
    }

    private fun medianDouble(values: List<Double>): Double = values.sorted().let {
        (it[(it.size - 1) / 2] + it[it.size / 2]) / 2.0
    }
}
