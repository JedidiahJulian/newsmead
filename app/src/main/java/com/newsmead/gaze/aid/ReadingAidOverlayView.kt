package com.newsmead.gaze.aid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.util.AttributeSet
import android.widget.TextView
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathRect
import kotlin.math.abs
import kotlin.math.roundToInt

/** Independent adjustable aid forms. This view never consumes article touches. */
class ReadingAidOverlayView(
    context: Context,
    private val lineStartMarkerInGutter: Boolean = false,
    private val presentationClockMs: () -> Double = { SystemClock.elapsedRealtimeNanos() / 1e6 },
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val visible = Rect()
    private val focusVisible = Rect()
    private val origin = IntArray(2)
    private val textLocation = IntArray(2)
    private var body: TextView? = null
    private var geometry: ScanpathGeometry? = null
    private var generation = -1L
    private var actions = emptyList<ReadingAidAction>()
    private var guideSpan: GuideBackgroundSpan? = null
    private val receipts = mutableSetOf<String>()
    var onRender: (ReadingAidAction, Boolean, String, Double) -> Unit = { _, _, _, _ -> }
    var onFocusScrollRender: (ReadingAidAction, String, Double, Double, Double) -> Unit = { _, _, _, _, _ -> }
    var allowFocusScrollHold: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) clearFocusScrollHold()
        }
    private val focusMotion = ReadingFocusMotion()
    private val focusOnset = ReadingFocusOnset()
    private var lastFocus: ReadingAidAction? = null
    private var lastClearBand: ReadingFocusMotion.Band? = null
    private var bodyShape: List<Any?>? = null
    private data class FocusHold(val source: ReadingAidAction, val untilMs: Double,
        val band: ReadingFocusMotion.Band)
    private var focusHold: FocusHold? = null
    private val heldReceipts = mutableSetOf<String>()

    fun clearFocusScrollHold() {
        val changed = focusHold != null || lastFocus != null || lastClearBand != null || heldReceipts.isNotEmpty()
        val motionChanged = focusMotion.clear()
        val onsetChanged = focusOnset.clear()
        focusHold = null; lastFocus = null; lastClearBand = null
        heldReceipts.clear()
        if (changed || motionChanged || onsetChanged) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        clearFocusScrollHold()
        super.onDetachedFromWindow()
    }

    private fun shape(view: TextView): List<Any?> = listOf(view.text.toString(), view.width,
        view.textSize, view.textScaleX, view.typeface, view.letterSpacing, view.lineSpacingExtra,
        view.lineSpacingMultiplier, view.totalPaddingLeft, view.totalPaddingRight,
        view.totalPaddingTop, view.totalPaddingBottom, view.layout?.width, view.layout)

    init { isClickable = false; isFocusable = false }

    fun setActions(textView: TextView, snapshot: ScanpathGeometry?, epoch: Long,
                   value: List<ReadingAidAction>) {
        if (body === textView && geometry === snapshot && generation == epoch && actions == value) return
        val now = presentationClockMs()
        val nextShape = shape(textView)
        val before = geometry
        val translationCompatible = body === textView && generation == epoch &&
            before != null && snapshot != null && bodyShape == nextShape &&
            ReadingFocusMotion.compatibleTranslation(before, snapshot)
        val freshFocus = value.firstOrNull { it.aidType == ReadingAidType.FOCUS_REGION &&
            it.aidFormId == "CONTEXT_FOCUS_REGION_V4" && it.expiresMs > now &&
            it.anchor.generation == epoch && it.anchor.geometrySnapshotId == snapshot?.snapshotId }
        val nextStyle = freshFocus?.let { it.focusStyle ?: ReadingFocusStyle.FADE_TEXT }
        val previousFocusActive = actions.any { it.aidType == ReadingAidType.FOCUS_REGION &&
            it.aidFormId == "CONTEXT_FOCUS_REGION_V4" && it.expiresMs > now &&
            it.anchor.generation == generation && it.anchor.geometrySnapshotId == before?.snapshotId }
        if (!translationCompatible || (!previousFocusActive && focusHold?.let { it.untilMs > now } != true) ||
            (nextStyle != null && nextStyle != focusOnset.style)) clearFocusScrollHold()
        if (freshFocus != null) { focusHold = null; heldReceipts.clear(); focusOnset.select(nextStyle) }
        else if (translationCompatible && allowFocusScrollHold && ReadingFocusMotion.translated(before!!, snapshot!!)) {
            val existing = focusHold?.takeIf { it.untilMs > now }
            val source = existing?.source ?: lastFocus?.takeIf {
                actions.any { active ->
                    active.aidType == ReadingAidType.FOCUS_REGION &&
                        active.aidFormId == "CONTEXT_FOCUS_REGION_V4" && active.expiresMs > now &&
                        active.anchor.generation == generation &&
                        active.anchor.geometrySnapshotId == before.snapshotId
                }
            }
            val band = existing?.band ?: lastClearBand
            if (source != null && band != null) {
                focusHold = FocusHold(source, now + ReadingFocusMotion.SCROLL_HOLD_MS, band)
                focusMotion.pin(band)
            } else clearFocusScrollHold()
        }
        if (freshFocus == null && focusHold == null) clearFocusScrollHold()
        if (body !== textView) clearGuideSpan()
        body = textView; geometry = snapshot; generation = epoch; actions = value.toList()
        bodyShape = nextShape
        updateGuideSpan()
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
        val now = presentationClockMs()
        var presentingFocus = false
        val compatible = g != null && view.getVisibleRectOnScreen(visible) &&
            abs(textLocation[0] + view.totalPaddingLeft - view.scrollX - g.textOriginX) < 1 &&
            abs(textLocation[1] + view.totalPaddingTop - view.scrollY - g.textOriginY) < 1 &&
            layout.lineCount == g.lines.size
        for (action in actions.filter { it.aidType != ReadingAidType.WORD_MAGNIFIER && it.aidType != ReadingAidType.WORD_DEFINITION }) {
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
                    if (action.aidFormId != "CURRENT_LINE_HIGHLIGHT_GRAY_V1") {
                        paint.color = Color.argb((80 * action.strength).toInt(), 244, 211, 94)
                        canvas.drawRoundRect(RectF(left, top.toFloat() - origin[1], right,
                            bottom.toFloat() - origin[1]), dp(4f), dp(4f), paint)
                    } else if (!guideSpanDrawEligible(action)) {
                        receipt(action, false, "guide_background_unavailable", now); continue
                    }
                }
                ReadingAidType.CURRENT_SENTENCE -> if (!guideSpanDrawEligible(action)) {
                    receipt(action, false, "guide_background_unavailable", now); continue
                }
                ReadingAidType.NEXT_LINE_START, ReadingAidType.READING_BOOKMARK -> {
                    paint.color = Color.argb(LineStartMarkerAppearance.ALPHA, 44, 128, 220)
                    val textStart = (g.textOriginX + row.left - origin[0]).toFloat()
                    val markerWidth = dp(LineStartMarkerAppearance.WIDTH_DP)
                    val start = if (lineStartMarkerInGutter) textStart - dp(4f) - markerWidth else textStart
                    // Article padding reserves this space from inflation, in every aid condition.
                    // Withhold a clipped gutter marker rather than painting over the source text.
                    if (lineStartMarkerInGutter && (start < left || textStart - dp(4f) > right)) {
                        receipt(action, false, "line_start_gutter_not_visible", now); continue
                    }
                    canvas.drawRoundRect(RectF(start, top.toFloat() - origin[1],
                        (start + markerWidth).coerceAtMost(right),
                        bottom.toFloat() - origin[1]), dp(2f), dp(2f), paint)
                }
                ReadingAidType.FOCUS_REGION -> {
                    val v4 = action.aidFormId == "CONTEXT_FOCUS_REGION_V4"
                    if (v4 && !geometryMatchesLayout(view, g)) {
                        receipt(action, false, "geometry_not_visible_or_changed", now); continue
                    }
                    val fullWidth = v4 || action.aidFormId == "CONTEXT_FOCUS_REGION_V3"
                    if (fullWidth && !getVisibleRectOnScreen(focusVisible)) {
                        receipt(action, false, "focus_viewport_unavailable", now); continue
                    }
                    // Widen only the drawn veil; text geometry and gaze assignment keep their own bounds.
                    val focusLeft = if (fullWidth) focusVisible.left.toDouble() else visible.left.toDouble()
                    val focusRight = if (fullWidth) focusVisible.right.toDouble() else visible.right.toDouble()
                    val focus = ReadingFocusAppearance.resolve(g,
                        ScanpathRect(focusLeft, visible.top.toDouble(), focusRight, visible.bottom.toDouble()),
                        action.anchor.line, action.contextRadiusLines ?: 2, action.strength,
                        limitContextHeight = action.aidFormId == "CONTEXT_FOCUS_REGION_V2",
                        keepLineCount = fullWidth, occludeSurroundings = v4,
                        focusStyle = action.focusStyle ?: ReadingFocusStyle.FADE_TEXT)
                    val appearance = focus.appearance
                    if (appearance == null) {
                        receipt(action, false, focus.withheldReason!!, now); continue
                    }
                    val target = ReadingFocusMotion.Band(appearance.clearTop, appearance.clearBottom)
                    val band = if (v4) focusMotion.move(target,
                        ReadingFocusMotion.Band(top, bottom), now) else target
                    val style = action.focusStyle ?: ReadingFocusStyle.FADE_TEXT
                    if (v4) { presentingFocus = true; focusOnset.select(style) }
                    val alpha = if (v4) (appearance.alpha * focusOnset.fraction(now)).roundToInt() else appearance.alpha
                    val drawn = drawFocus(canvas, view, appearance, band, v4, style, alpha)
                    if (v4) {
                        if (drawn) { lastFocus = action; lastClearBand = band }
                        if (focusMotion.isMoving(now) || focusOnset.isRunning(now)) postInvalidateOnAnimation()
                    }
                    if (!drawn) {
                        receipt(action, false, if (alpha <= 0) "focus_onset_alpha_zero" else "focus_no_dimmed_area", now)
                        continue
                    }
                }
                ReadingAidType.WORD_MAGNIFIER, ReadingAidType.WORD_DEFINITION -> Unit
            }
            receipt(action, true, if (isBackgroundGuide(action)) "text_background_draw_proxy" else "canvas_draw_proxy", now)
        }
        val hold = focusHold
        if (!presentingFocus && hold == null) clearFocusScrollHold()
        if (hold != null && hold.untilMs <= now) { clearFocusScrollHold(); return }
        if (hold != null && compatible && g != null && bodyShape == shape(view) &&
            geometryMatchesLayout(view, g) &&
            abs(g.visibleRectScreen.top - visible.top) < 1 &&
            abs(g.visibleRectScreen.bottom - visible.bottom) < 1 &&
            abs(g.visibleRectScreen.left - visible.left) < 1 &&
            abs(g.visibleRectScreen.right - visible.right) < 1 && getVisibleRectOnScreen(focusVisible)) {
            val center = (hold.band.top + hold.band.bottom) / 2
            val anchor = g.lines.indices.filter { i -> g.textOriginY + g.lines[i].bottom > visible.top &&
                g.textOriginY + g.lines[i].top < visible.bottom }.minByOrNull { i ->
                abs(g.textOriginY + (g.lines[i].top + g.lines[i].bottom) / 2 - center)
            }
            if (anchor != null) {
                val appearance = ReadingFocusAppearance.resolve(g, ScanpathRect(
                    focusVisible.left.toDouble(), visible.top.toDouble(),
                    focusVisible.right.toDouble(), visible.bottom.toDouble()), anchor,
                    hold.source.contextRadiusLines ?: 2, hold.source.strength,
                    limitContextHeight = false, keepLineCount = true, occludeSurroundings = true,
                    focusStyle = hold.source.focusStyle ?: ReadingFocusStyle.FADE_TEXT).appearance
                if (appearance != null) {
                    // Presentation stays in screen space while the article passes through it.
                    // The nearby row above is only an availability check, never a gaze anchor.
                    val band = ReadingFocusMotion.fitPinnedBand(hold.band,
                        appearance.viewport.top, appearance.viewport.bottom)
                    val shadedHeight = (band.top - appearance.viewport.top) +
                        (appearance.viewport.bottom - band.bottom)
                    if (shadedHeight > 0) {
                        val drawn = drawFocus(canvas, view, appearance, band, true,
                            hold.source.focusStyle ?: ReadingFocusStyle.FADE_TEXT,
                            (appearance.alpha * focusOnset.fraction(now)).roundToInt())
                        if (drawn) lastClearBand = band
                        focusMotion.pin(band)
                        if (focusOnset.isRunning(now)) postInvalidateOnAnimation()
                        if (drawn && heldReceipts.add(g.snapshotId)) onFocusScrollRender(hold.source,
                            g.snapshotId, band.top, band.bottom, now)
                    }
                }
            }
            postInvalidateDelayed((hold.untilMs - now).toLong().coerceAtLeast(1))
        }
    }

    private fun geometryMatchesLayout(view: TextView, g: ScanpathGeometry): Boolean {
        val layout = view.layout ?: return false
        return layout.lineCount == g.lines.size && g.lines.indices.all { i ->
            val line = g.lines[i]
            layout.getLineStart(i) == line.startUtf16 && layout.getLineEnd(i) == line.endUtf16 &&
                abs(layout.getLineLeft(i) - line.left) < 1 && abs(layout.getLineRight(i) - line.right) < 1 &&
                abs(layout.getLineTop(i) - line.top) < 1 && abs(layout.getLineBottom(i) - line.bottom) < 1
        }
    }

    private fun drawFocus(canvas: Canvas, view: TextView, appearance: ReadingFocusAppearance.Appearance,
                          band: ReadingFocusMotion.Band, occluding: Boolean, style: ReadingFocusStyle,
                          alpha: Int = appearance.alpha): Boolean {
        val color = if (occluding && style == ReadingFocusStyle.FADE_TEXT) articleBackground(view) else Color.BLACK
        paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        val left = appearance.viewport.left.toFloat() - origin[0]
        val right = appearance.viewport.right.toFloat() - origin[0]
        val top = band.top.coerceIn(appearance.viewport.top, appearance.viewport.bottom).toFloat() - origin[1]
        val bottom = band.bottom.coerceIn(appearance.viewport.top, appearance.viewport.bottom).toFloat() - origin[1]
        if (alpha <= 0 || right <= left ||
            (top <= appearance.viewport.top - origin[1] && bottom >= appearance.viewport.bottom - origin[1])) return false
        canvas.drawRect(left, appearance.viewport.top.toFloat() - origin[1], right, top, paint)
        canvas.drawRect(left, bottom, right, appearance.viewport.bottom.toFloat() - origin[1], paint)
        return true
    }

    private fun articleBackground(view: TextView): Int {
        var ancestor: View? = view
        while (ancestor != null) {
            val background = ancestor.background as? ColorDrawable
            if (background != null && Color.alpha(background.color) == 255) return background.color
            ancestor = ancestor.parent as? View
        }
        val color = view.currentTextColor
        return if (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114 < 128000)
            Color.WHITE else Color.BLACK
    }

    private fun isBackgroundGuide(action: ReadingAidAction) =
        action.aidType == ReadingAidType.CURRENT_SENTENCE ||
            action.aidFormId == "CURRENT_LINE_HIGHLIGHT_GRAY_V1"

    private fun clearGuideSpan() {
        guideSpan?.let { (body?.text as? Spannable)?.removeSpan(it) }
        guideSpan = null
    }

    private fun updateGuideSpan() {
        clearGuideSpan()
        val view = body ?: return
        val g = geometry ?: return
        val action = actions.firstOrNull(::isBackgroundGuide) ?: return
        val row = g.lines.getOrNull(action.anchor.line) ?: return
        val start = action.guideRange?.startUtf16 ?: row.startUtf16
        val end = action.guideRange?.endUtf16 ?: row.endUtf16
        if (start < 0 || end <= start || end > view.text.length ||
            (action.aidType == ReadingAidType.CURRENT_SENTENCE && action.guideRange == null) ||
            !action.strength.isFinite() || action.strength <= 0) return
        // Appearance spans leave text, UTF-16 offsets, font metrics and wrapping intact.
        // Convert the buffer once; subsequent updates add/remove only our own span.
        if (view.text !is Spannable) view.setText(SpannableString(view.text), TextView.BufferType.SPANNABLE)
        val span = GuideBackgroundSpan(Color.argb((80 * action.strength.coerceIn(0.0, 1.0)).toInt(), 128, 128, 128)) {
            guideSpanDrawEligible(action)
        }
        guideSpan = span
        (view.text as Spannable).setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        view.invalidate()
    }

    private fun guideSpanDrawEligible(action: ReadingAidAction): Boolean {
        val view = body ?: return false
        val g = geometry ?: return false
        val layout = view.layout ?: return false
        val span = guideSpan ?: return false
        val text = view.text as? Spanned ?: return false
        val row = g.lines.getOrNull(action.anchor.line) ?: return false
        val start = action.guideRange?.startUtf16 ?: row.startUtf16
        val end = action.guideRange?.endUtf16 ?: row.endUtf16
        if (action.expiresMs <= presentationClockMs() ||
            action.anchor.generation != generation || action.anchor.geometrySnapshotId != g.snapshotId ||
            actions.none { it.id == action.id } || text.getSpanStart(span) != start || text.getSpanEnd(span) != end ||
            layout.lineCount != g.lines.size || !view.getVisibleRectOnScreen(visible)) return false
        view.getLocationOnScreen(textLocation)
        return abs(textLocation[0] + view.totalPaddingLeft - view.scrollX - g.textOriginX) < 1 &&
            abs(textLocation[1] + view.totalPaddingTop - view.scrollY - g.textOriginY) < 1
    }

    private class GuideBackgroundSpan(private val color: Int, private val eligible: () -> Boolean) :
        CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            if (eligible()) paint.bgColor = color
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
        // Total vertical padding includes centering offsets from the previous text layout.
        // Fit the new font against the reserved content box, including actual drawable padding.
        if (width <= 0 || paint.measureText(word) > width - totalPaddingLeft - totalPaddingRight ||
            paint.fontMetrics.let { it.bottom - it.top } > height - compoundPaddingTop - compoundPaddingBottom) {
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
