package com.newsmead.gaze.aid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.widget.TextView
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.scanpath.ScanpathGeometry
import kotlin.math.abs

/** Independent adjustable aid forms. This view never consumes article touches. */
class ReadingAidOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val visible = Rect()
    private val origin = IntArray(2)
    private val textLocation = IntArray(2)
    private var body: TextView? = null
    private var geometry: ScanpathGeometry? = null
    private var generation = -1L
    private var actions = emptyList<ReadingAidAction>()
    private val receipts = mutableSetOf<String>()
    var onRender: (ReadingAidAction, Boolean, String, Double) -> Unit = { _, _, _, _ -> }

    init { isClickable = false; isFocusable = false }

    fun setActions(textView: TextView, snapshot: ScanpathGeometry?, epoch: Long,
                   value: List<ReadingAidAction>) {
        if (body === textView && geometry === snapshot && generation == epoch && actions == value) return
        body = textView; geometry = snapshot; generation = epoch; actions = value.toList()
        val activeIds = actions.map { it.id }.toSet()
        receipts.removeAll { it.substringBefore('|') !in activeIds }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val view = body ?: return
        val layout = view.layout ?: return
        getLocationOnScreen(origin)
        view.getLocationOnScreen(textLocation)
        val g = geometry
        val now = SystemClock.elapsedRealtimeNanos() / 1e6
        val compatible = g != null && view.getVisibleRectOnScreen(visible) &&
            abs(textLocation[0] + view.totalPaddingLeft - view.scrollX - g.textOriginX) < 1 &&
            abs(textLocation[1] + view.totalPaddingTop - view.scrollY - g.textOriginY) < 1 &&
            layout.lineCount == g.lines.size
        for (action in actions.filter { it.aidType != ReadingAidType.WORD_MAGNIFIER }) {
            val reason = when {
                action.expiresMs <= now -> "expired_before_draw"
                action.anchor.generation != generation || action.anchor.geometrySnapshotId != g?.snapshotId -> "stale_anchor"
                !compatible -> "geometry_not_visible_or_changed"
                action.anchor.line !in 0 until layout.lineCount -> "line_unavailable"
                else -> null
            }
            if (reason != null) { receipt(action, false, reason, now); continue }
            val row = g!!.lines[action.anchor.line]
            val top = (g.textOriginY + row.top).coerceAtLeast(visible.top.toDouble())
            val bottom = (g.textOriginY + row.bottom).coerceAtMost(visible.bottom.toDouble())
            if (top >= bottom) { receipt(action, false, "anchor_not_visible", now); continue }
            val left = visible.left.toFloat() - origin[0]
            val right = visible.right.toFloat() - origin[0]
            when (action.aidType) {
                ReadingAidType.CURRENT_LINE -> {
                    paint.color = Color.argb((80 * action.strength).toInt(), 244, 211, 94)
                    canvas.drawRoundRect(RectF(left, top.toFloat() - origin[1], right,
                        bottom.toFloat() - origin[1]), dp(4f), dp(4f), paint)
                }
                ReadingAidType.NEXT_LINE_START -> {
                    paint.color = Color.argb((240 * action.strength).toInt(), 44, 128, 220)
                    val start = (g.textOriginX + row.left - origin[0]).toFloat()
                    canvas.drawRoundRect(RectF(start, top.toFloat() - origin[1],
                        (start + dp((3 + 5 * action.strength).toFloat())).coerceAtMost(right),
                        bottom.toFloat() - origin[1]), dp(2f), dp(2f), paint)
                }
                ReadingAidType.FOCUS_REGION -> {
                    val radius = action.contextRadiusLines ?: 2
                    val first = g.lines[(action.anchor.line - radius).coerceAtLeast(0)]
                    val last = g.lines[(action.anchor.line + radius).coerceAtMost(g.lines.lastIndex)]
                    val windowTop = (g.textOriginY + first.top).coerceIn(visible.top.toDouble(), visible.bottom.toDouble()).toFloat() - origin[1]
                    val windowBottom = (g.textOriginY + last.bottom).coerceIn(visible.top.toDouble(), visible.bottom.toDouble()).toFloat() - origin[1]
                    // A light veil retains readable surrounding text; the span never narrows with strength.
                    paint.color = Color.argb((40 * action.strength).toInt(), 0, 0, 0)
                    canvas.drawRect(left, visible.top.toFloat() - origin[1], right, windowTop, paint)
                    canvas.drawRect(left, windowBottom, right, visible.bottom.toFloat() - origin[1], paint)
                }
                ReadingAidType.WORD_MAGNIFIER -> Unit
            }
            receipt(action, true, "canvas_draw_proxy", now)
        }
    }

    private fun receipt(action: ReadingAidAction, drawn: Boolean, reason: String, now: Double) {
        if (receipts.add("${action.id}|$drawn|$reason")) onRender(action, drawn, reason, now)
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
}

/** Reserved non-text strip: magnification never covers the source paragraph. */
class ReadingAidWordView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyle: Int = 0) : AppCompatTextView(context, attrs, defStyle) {
    var action: ReadingAidAction? = null
    var onRender: (ReadingAidAction, Boolean, String, Double) -> Unit = { _, _, _, _ -> }
    private val receipts = mutableSetOf<String>()
    private var lastWidth = -1
    private var lastHeight = -1

    fun showAction(value: ReadingAidAction?, source: TextView) {
        if (action == value && currentTextColor == source.currentTextColor &&
            width == lastWidth && height == lastHeight) return
        action = value
        lastWidth = width; lastHeight = height
        receipts.clear()
        text = ""
        contentDescription = null
        if (value == null) return
        val start = value.anchor.wordStartUtf16
        val end = value.anchor.wordEndUtf16
        val now = SystemClock.elapsedRealtimeNanos() / 1e6
        if (start == null || end == null || start < 0 || end > source.text.length || end <= start) {
            receipt(value, false, "word_range_unavailable", now); return
        }
        typeface = source.typeface
        setTextColor(source.currentTextColor)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
            source.textSize * (value.magnificationScale ?: 1.0).toFloat())
        val word = source.text.subSequence(start, end).toString()
        if (width <= 0 || paint.measureText(word) > width - totalPaddingLeft - totalPaddingRight ||
            paint.fontMetrics.let { it.bottom - it.top } > height - totalPaddingTop - totalPaddingBottom) {
            receipt(value, false, "magnified_word_does_not_fit", now); return
        }
        text = word
        contentDescription = word
    }

    override fun onDraw(canvas: Canvas) {
        val current = action
        val now = SystemClock.elapsedRealtimeNanos() / 1e6
        if (current != null && current.expiresMs <= now) {
            receipt(current, false, "expired_before_draw", now); return
        }
        super.onDraw(canvas)
        if (current != null && text.isNotEmpty()) receipt(current, true, "text_draw_proxy", now)
    }

    private fun receipt(value: ReadingAidAction, drawn: Boolean, reason: String, now: Double) {
        if (receipts.add("$drawn|$reason")) onRender(value, drawn, reason, now)
    }
}
