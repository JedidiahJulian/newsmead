package com.newsmead.gaze

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.newsmead.gaze.mgazenet.NinePointAccuracySummary
import java.util.Locale
import kotlin.math.hypot

/**
 * Full-screen overlay drawn after the nine-point accuracy check completes:
 * plots each of the 9 target coordinates as a red dot, each measured median
 * gaze estimate as a blue dot, and connecting distance vectors with labels.
 */
class NinePointAccuracyOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val pillRect = RectF()
    private var results: List<NinePointAccuracySummary.PointResult> = emptyList()

    private val targetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TARGET_RED_COLOR
        style = Paint.Style.FILL
    }
    private val estimateFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ESTIMATE_BLUE_COLOR
        style = Paint.Style.FILL
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val clampedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ESTIMATE_BLUE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = CLAMPED_RING_STROKE_DP * density
    }
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CONNECTOR_BLUE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = CONNECTOR_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    private val indexTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = INDEX_TEXT_SP * scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val pillFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PILL_FILL_COLOR
        style = Paint.Style.FILL
    }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PILL_STROKE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = PILL_STROKE_DP * density
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LABEL_TEXT_COLOR
        textSize = LABEL_TEXT_SP * scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val noDataTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NO_DATA_TEXT_COLOR
        textSize = LABEL_TEXT_SP * scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    fun setResults(points: List<NinePointAccuracySummary.PointResult>) {
        results = points.toList()
        invalidate()
    }

    fun clearResults() {
        if (results.isEmpty()) return
        results = emptyList()
        invalidate()
    }

    internal fun currentResults(): List<NinePointAccuracySummary.PointResult> = results

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (results.isEmpty() || width <= 0 || height <= 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val edgePadPx = ESTIMATE_EDGE_PAD_DP * density

        val clampedEstimates = results.map { point ->
            val ex = point.estimateLocalX
            val ey = point.estimateLocalY
            if (ex != null && ey != null && ex.isFinite() && ey.isFinite()) {
                NinePointAccuracySummary.clampEstimateToBounds(ex, ey, viewWidth, viewHeight, edgePadPx)
            } else {
                null
            }
        }

        // Pass 1: Distance connector lines between target and clamped estimate.
        for (i in results.indices) {
            val point = results[i]
            val clamped = clampedEstimates[i] ?: continue
            canvas.drawLine(point.targetLocalX, point.targetLocalY, clamped.x, clamped.y, connectorPaint)
        }

        // Pass 2: Distance / missing-data pills so dots stay on top in Pass 3.
        val labelEdgePadPx = LABEL_EDGE_PAD_DP * density
        val nearThresholdPx = NEAR_THRESHOLD_DP * density
        val offsetBelowPx = LABEL_OFFSET_BELOW_DP * density
        for (i in results.indices) {
            val point = results[i]
            val clamped = clampedEstimates[i]
            val radialCm = point.radialCm
            if (clamped != null && radialCm != null && radialCm.isFinite()) {
                val tx = point.targetLocalX
                val ty = point.targetLocalY
                val ex = clamped.x
                val ey = clamped.y
                val distPx = hypot(ex - tx, ey - ty)
                val anchorX = (tx + ex) / 2f
                val anchorY = if (distPx < nearThresholdPx) {
                    maxOf(ty, ey) + offsetBelowPx
                } else {
                    (ty + ey) / 2f
                }
                val text = String.format(Locale.US, "%.2f cm", radialCm)
                drawPillLabel(canvas, text, anchorX, anchorY, viewWidth, viewHeight, labelEdgePadPx, labelTextPaint)
            } else {
                val anchorY = point.targetLocalY + offsetBelowPx
                drawPillLabel(canvas, "no data", point.targetLocalX, anchorY, viewWidth, viewHeight, labelEdgePadPx, noDataTextPaint)
            }
        }

        // Pass 3: All 9 red target dots (with centered 1..9 index) and measured blue estimate dots.
        val haloPx = HALO_WIDTH_DP * density
        val targetRadiusPx = TARGET_RADIUS_DP * density
        val estRadiusPx = ESTIMATE_RADIUS_DP * density
        val clampedRingRadiusPx = CLAMPED_RING_RADIUS_DP * density
        val indexBaselineOffset = -(indexTextPaint.ascent() + indexTextPaint.descent()) / 2f

        for (i in results.indices) {
            val point = results[i]
            val tx = point.targetLocalX
            val ty = point.targetLocalY
            canvas.drawCircle(tx, ty, targetRadiusPx + haloPx, haloPaint)
            canvas.drawCircle(tx, ty, targetRadiusPx, targetFillPaint)
            canvas.drawText(point.index.toString(), tx, ty + indexBaselineOffset, indexTextPaint)

            val clamped = clampedEstimates[i] ?: continue
            canvas.drawCircle(clamped.x, clamped.y, estRadiusPx + haloPx, haloPaint)
            canvas.drawCircle(clamped.x, clamped.y, estRadiusPx, estimateFillPaint)
            if (clamped.clamped) {
                canvas.drawCircle(clamped.x, clamped.y, clampedRingRadiusPx, clampedRingPaint)
            }
        }
    }

    private fun drawPillLabel(
        canvas: Canvas,
        text: String,
        anchorX: Float,
        anchorY: Float,
        viewWidth: Float,
        viewHeight: Float,
        edgePadPx: Float,
        textPaint: Paint,
    ) {
        val textWidth = textPaint.measureText(text)
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val pillWidth = textWidth + 2f * PILL_H_PADDING_DP * density
        val pillHeight = textHeight + 2f * PILL_V_PADDING_DP * density
        val maxLeft = maxOf(edgePadPx, viewWidth - pillWidth - edgePadPx)
        val maxTop = maxOf(edgePadPx, viewHeight - pillHeight - edgePadPx)
        val left = (anchorX - pillWidth / 2f).coerceIn(edgePadPx, maxLeft)
        val top = (anchorY - pillHeight / 2f).coerceIn(edgePadPx, maxTop)
        pillRect.set(left, top, left + pillWidth, top + pillHeight)
        val cornerRadius = pillHeight / 2f
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillFillPaint)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillStrokePaint)
        val textY = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, pillRect.centerX(), textY, textPaint)
    }

    companion object {
        internal val TARGET_RED_COLOR = Color.parseColor("#E53935")
        internal val ESTIMATE_BLUE_COLOR = Color.parseColor("#1E88E5")
        internal val CONNECTOR_BLUE_COLOR = Color.parseColor("#B31976D2")
        private val PILL_FILL_COLOR = Color.parseColor("#E8FFFFFF")
        private val PILL_STROKE_COLOR = Color.parseColor("#CFD8DC")
        private val LABEL_TEXT_COLOR = Color.parseColor("#1A1A1A")
        private val NO_DATA_TEXT_COLOR = Color.parseColor("#666666")

        private const val TARGET_RADIUS_DP = 7.5f
        private const val ESTIMATE_RADIUS_DP = 6.5f
        private const val HALO_WIDTH_DP = 2f
        private const val CLAMPED_RING_RADIUS_DP = 10.5f
        private const val CLAMPED_RING_STROKE_DP = 1.5f
        private const val CONNECTOR_STROKE_DP = 2.2f
        private const val INDEX_TEXT_SP = 9f
        private const val LABEL_TEXT_SP = 10.5f
        private const val PILL_H_PADDING_DP = 5f
        private const val PILL_V_PADDING_DP = 2f
        private const val PILL_STROKE_DP = 0.75f
        private const val ESTIMATE_EDGE_PAD_DP = 12f
        private const val LABEL_EDGE_PAD_DP = 8f
        private const val NEAR_THRESHOLD_DP = 26f
        private const val LABEL_OFFSET_BELOW_DP = 18f
    }
}
