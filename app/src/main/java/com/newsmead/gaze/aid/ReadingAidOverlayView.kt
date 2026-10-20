package com.newsmead.gaze.aid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.Editable
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
    private var lineBackground: ReadingLineGuideBackground? = null
    private var lineAction: ReadingAidAction? = null
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class GradientKey(val start: Double, val end: Double, val color: Int,
        val fadeTop: Boolean, val fadeBottom: Boolean, val fadeFraction: Float)
    private val gradients = mutableMapOf<GradientKey, Shader>()
    private var threeLineValidatedRows: List<com.newsmead.gaze.scanpath.ScanpathLine>? = null
    private var guideTextChanged = false
    private var guideInvalidatedRows: List<com.newsmead.gaze.scanpath.ScanpathLine>? = null
    private var guideDrawShape: List<Any?>? = null
    private val guideTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            guideTextChanged = true
            guideInvalidatedRows = geometry?.lines
            threeLineValidatedLayout = null
            threeLineMotion.clear()
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }
    private val threeLineMotion = ReadingThreeLineGuideMotion()
    private var threeLineDrawn = false
    private var threeLineValidatedLayout: Layout? = null
    internal var lineGuideLayoutValidationCount = 0
        private set
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
        clearGuideSpan()
        threeLineMotion.clear()
        super.onDetachedFromWindow()
    }

    private fun shape(view: TextView): List<Any?> = listOf(view.text.toString(), view.width,
        view.textSize, view.textScaleX, view.typeface, view.letterSpacing, view.lineSpacingExtra,
        view.lineSpacingMultiplier, view.totalPaddingLeft, view.totalPaddingRight,
        view.totalPaddingTop, view.totalPaddingBottom, view.layout?.width, view.layout)

    private fun drawShape(view: TextView): List<Any?> = listOf(view.text, view.width,
        view.textSize, view.textScaleX, view.typeface, view.letterSpacing, view.lineSpacingExtra,
        view.lineSpacingMultiplier, view.totalPaddingLeft, view.totalPaddingRight,
        view.totalPaddingTop, view.totalPaddingBottom, view.layout?.width)

    init { isClickable = false; isFocusable = false }

    fun setActions(textView: TextView, snapshot: ScanpathGeometry?, epoch: Long,
                   value: List<ReadingAidAction>) {
        if (body === textView && geometry === snapshot && generation == epoch && actions == value) {
            if (lineAction != null) ensureLineBackground(textView)
            return
        }
        val now = presentationClockMs()
        val nextShape = shape(textView)
        val before = geometry
        val translationCompatible = body === textView && generation == epoch &&
            before != null && snapshot != null && bodyShape == nextShape &&
            ReadingFocusMotion.compatibleTranslation(before, snapshot)
        val oldLineStyle = actions.firstOrNull(::isAnimatedLineGuide)?.aidFormId
        val newLineStyle = value.firstOrNull(::isAnimatedLineGuide)?.aidFormId
        // An equivalent Layout replacement while converting the text buffer is harmless.
        // Actual reflow changes the body shape or physical row metrics and resets motion.
        val threeLineCompatible = body === textView && generation == epoch && before != null && snapshot != null &&
            bodyShape?.dropLast(1) == nextShape.dropLast(1) &&
            ReadingFocusMotion.compatibleTranslation(before, snapshot)
        if (!threeLineCompatible || oldLineStyle != newLineStyle) threeLineMotion.clear()
        val freshFocus = value.firstOrNull { it.aidType == ReadingAidType.FOCUS_REGION &&
            it.aidFormId == "CONTEXT_FOCUS_REGION_V4" && it.expiresMs > now &&
            it.anchor.generation == epoch && it.anchor.geometrySnapshotId == snapshot?.snapshotId }
        val nextStyle = freshFocus?.let { it.focusStyle ?: ReadingFocusStyle.FADE_TEXT }
        val previousFocusActive = actions.any { it.aidType == ReadingAidType.FOCUS_REGION &&
            it.aidFormId == "CONTEXT_FOCUS_REGION_V4" && it.expiresMs > now &&
            it.anchor.generation == generation && it.anchor.geometrySnapshotId == before?.snapshotId }
        if (!translationCompatible || (!previousFocusActive && focusHold?.let { it.untilMs > now } != true) ||
            (nextStyle != null && nextStyle != focusOnset.style)) clearFocusScrollHold()
        if (freshFocus != null) {
            focusHold = null; heldReceipts.clear()
            focusOnset.animateAllStyles = freshFocus.configId in setOf("EVIDENCE_ALIGNED_V5", "DEMO_EVIDENCE_ALIGNED_V5")
            focusOnset.select(nextStyle)
        }
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
            if (isBackgroundGuide(action) && action.guideEvidence?.let {
                    (255 * it.opacityAt(now)).roundToInt() <= 0 } == true) {
                animateGuide(action, view, now)
                receipt(action, false, "guide_animation_alpha_zero", now)
                continue
            }
            when (action.aidType) {
                ReadingAidType.CURRENT_LINE -> {
                    if (isAnimatedLineGuide(action)) {
                        if (!threeLineBackgroundDrawEligible(action)) {
                            receipt(action, false, "guide_background_unavailable", now); continue
                        }
                        if (!threeLineDrawn) receipt(action, false, "guide_background_not_drawn", now)
                        // Positive receipts originate only from the actual text-background callback.
                        continue
                    } else if (action.aidFormId != "CURRENT_LINE_HIGHLIGHT_GRAY_V1") {
                        paint.color = Color.argb((80 * action.strength).toInt(), 244, 211, 94)
                        canvas.drawRoundRect(RectF(left, top.toFloat() - origin[1], right,
                            bottom.toFloat() - origin[1]), dp(4f), dp(4f), paint)
                    } else if (!guideSpanDrawEligible(action)) {
                        receipt(action, false, "guide_background_unavailable", now); continue
                    }
                    animateGuide(action, view, now)
                }
                ReadingAidType.CURRENT_SENTENCE -> if (!guideSpanDrawEligible(action)) {
                    receipt(action, false, "guide_background_unavailable", now); continue
                } else animateGuide(action, view, now)
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
                    // Keep the entire reread passage plus a neighbouring row clear, even
                    // during movement. Offscreen passage rows do not expand the viewport.
                    val target = ReadingFocusContext.protect(
                        ReadingFocusMotion.Band(appearance.clearTop, appearance.clearBottom), g,
                        action.focusEvidence?.reread, appearance.viewport.top, appearance.viewport.bottom)
                    val anchorBand = ReadingFocusContext.protect(ReadingFocusMotion.Band(top, bottom), g,
                        action.focusEvidence?.reread, appearance.viewport.top, appearance.viewport.bottom)
                    val band = if (v4) focusMotion.move(target, anchorBand, now) else target
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
                    val pinned = ReadingFocusMotion.fitPinnedBand(hold.band,
                        appearance.viewport.top, appearance.viewport.bottom)
                    val band = ReadingFocusContext.protect(pinned, g, hold.source.focusEvidence?.reread,
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
            val drawable = ancestor.background
            val background = (if (drawable is ReadingLineGuideBackground) drawable.base else drawable) as? ColorDrawable
            if (background != null && Color.alpha(background.color) == 255) return background.color
            ancestor = ancestor.parent as? View
        }
        val color = view.currentTextColor
        return if (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114 < 128000)
            Color.WHITE else Color.BLACK
    }

    private fun isBackgroundGuide(action: ReadingAidAction) =
        action.aidType == ReadingAidType.CURRENT_SENTENCE ||
            action.aidFormId == "CURRENT_LINE_HIGHLIGHT_GRAY_V1" || isThreeLineGuide(action)

    private fun isThreeLineGuide(action: ReadingAidAction) =
        action.aidType == ReadingAidType.CURRENT_LINE && action.aidFormId == "CURRENT_THREE_LINE_GUIDE_GRAY_V1"

    private fun isAnimatedLineGuide(action: ReadingAidAction) = isThreeLineGuide(action) ||
        (action.aidType == ReadingAidType.CURRENT_LINE &&
            action.aidFormId == "CURRENT_LINE_HIGHLIGHT_GRAY_V1" && action.guideEvidence != null)

    private fun lineGuideTarget(action: ReadingAidAction): ReadingThreeLineGuideMotion.Appearance? {
        val g = geometry ?: return null
        val e = action.guideEvidence ?: return null
        if (!isThreeLineGuide(action)) return ReadingThreeLineGuideMotion.singleLineTarget(g.lines, action.anchor.line, e.opacity)
        val three = e.threeLine ?: return null
        return ReadingThreeLineGuideMotion.target(g.lines, action.anchor.line,
            three.aboveOpacity, e.opacity, three.belowOpacity, three.direction)
    }

    private fun clearLineBackground() {
        val view = body
        val wrapper = lineBackground
        if (wrapper != null) {
            wrapper.release()
            if (view?.background === wrapper) {
                val left = view.paddingLeft; val top = view.paddingTop
                val right = view.paddingRight; val bottom = view.paddingBottom
                view.background = wrapper.base
                view.setPadding(left, top, right, bottom)
            }
            view?.removeTextChangedListener(guideTextWatcher)
        }
        lineBackground = null
        lineAction = null
        threeLineDrawn = false
        threeLineValidatedLayout = null
        threeLineValidatedRows = null
        guideDrawShape = null
        guideTextChanged = false
        guideInvalidatedRows = null
        gradients.clear()
    }

    private fun ensureLineBackground(view: TextView) {
        if (lineBackground != null && view.background === lineBackground) return
        lineBackground?.release()
        view.removeTextChangedListener(guideTextWatcher)
        val wrapper = ReadingLineGuideBackground(view.background) { canvas ->
            lineAction?.let { drawThreeLineBackground(canvas, view, it) }
        }
        lineBackground = wrapper
        // A Drawable must preserve the body's current padding, including an external theme update.
        val left = view.paddingLeft; val top = view.paddingTop
        val right = view.paddingRight; val bottom = view.paddingBottom
        view.background = wrapper
        view.setPadding(left, top, right, bottom)
        wrapper.attach()
        view.addTextChangedListener(guideTextWatcher)
    }

    private fun clearGuideSpan() {
        guideSpan?.let { (body?.text as? Spannable)?.removeSpan(it) }
        guideSpan = null
        clearLineBackground()
    }

    private fun updateGuideSpan() {
        val view = body ?: return
        val g = geometry
        val action = actions.firstOrNull(::isBackgroundGuide)
        if (action != null && g != null && isAnimatedLineGuide(action)) {
            guideSpan?.let { (view.text as? Spannable)?.removeSpan(it) }
            guideSpan = null
            val evidence = action.guideEvidence ?: return
            if (!action.strength.isFinite() || action.strength <= 0) { clearLineBackground(); return }
            val target = lineGuideTarget(action) ?: run { clearLineBackground(); return }
            ensureLineBackground(view)
            lineAction = action
            if (guideInvalidatedRows !== g.lines)
                guideTextChanged = false
            guideDrawShape = drawShape(view)
            threeLineDrawn = false
            threeLineMotion.move(target, presentationClockMs(),
                evidence.threeLine?.transitionMs ?: ReadingThreeLineGuideMotion.TRANSITION_MS,
                animateOpacity = isThreeLineGuide(action))
            lineBackground?.invalidateSelf()
            return
        }
        clearGuideSpan()
        if (g == null || action == null) return
        val row = g.lines.getOrNull(action.anchor.line) ?: return
        val start = action.guideRange?.startUtf16 ?: row.startUtf16
        val end = action.guideRange?.endUtf16 ?: row.endUtf16
        if (start < 0 || end <= start || end > view.text.length ||
            (action.aidType == ReadingAidType.CURRENT_SENTENCE && action.guideRange == null) ||
            !action.strength.isFinite() || action.strength <= 0) return
        if (view.text !is Spannable) view.setText(SpannableString(view.text), TextView.BufferType.SPANNABLE)
        val span = GuideBackgroundSpan({
            val alpha = action.guideEvidence?.let { (255 * it.opacityAt(presentationClockMs())).roundToInt() }
                ?: (80 * action.strength.coerceIn(0.0, 1.0)).toInt()
            Color.argb(alpha, 128, 128, 128)
        }) { guideSpanDrawEligible(action) }
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

    private fun animateGuide(action: ReadingAidAction, view: TextView, now: Double) {
        val e = action.guideEvidence ?: return
        if (isAnimatedLineGuide(action)) return
        if ((isAnimatedLineGuide(action) && threeLineMotion.isMoving(now)) ||
            now - e.presentationStartedMs < e.entryMs || now > e.lastSupportedDeliveryMs + e.lossGraceMs) {
            view.postInvalidateOnAnimation()
            postInvalidateOnAnimation()
        } else postInvalidateDelayed((e.lastSupportedDeliveryMs + e.lossGraceMs - now).toLong().coerceAtLeast(1L))
    }

    private fun threeLineBackgroundDrawEligible(action: ReadingAidAction): Boolean {
        val view = body ?: return false
        val g = geometry ?: return false
        val wrapper = lineBackground ?: return false
        if (view.background !== wrapper || lineAction !== action) return false
        val layout = view.layout ?: return false
        if (guideTextChanged || guideDrawShape != drawShape(view) || layout.lineCount != g.lines.size) {
            threeLineMotion.clear()
            return false
        }
        if (action.expiresMs <= presentationClockMs() || action.anchor.generation != generation ||
            action.anchor.geometrySnapshotId != g.snapshotId || actions.none { it.id == action.id } ||
            !view.getVisibleRectOnScreen(visible)) return false
        // Snapshot IDs change with captures/scrolling. Validate row metrics only when
        // the Layout or immutable row list changes, including equal-count reflows.
        if (threeLineValidatedLayout !== layout || threeLineValidatedRows !== g.lines) {
            lineGuideLayoutValidationCount++
            if (!geometryMatchesLayout(view, g)) { threeLineMotion.clear(); return false }
            threeLineValidatedLayout = layout
            threeLineValidatedRows = g.lines
        }
        view.getLocationOnScreen(textLocation)
        return abs(textLocation[0] + view.totalPaddingLeft - view.scrollX - g.textOriginX) < 1 &&
            abs(textLocation[1] + view.totalPaddingTop - view.scrollY - g.textOriginY) < 1
    }

    private fun drawThreeLineBackground(canvas: Canvas, view: TextView, action: ReadingAidAction) {
        if (!threeLineBackgroundDrawEligible(action)) return
        val evidence = action.guideEvidence ?: return
        val g = geometry ?: return
        val layout = view.layout ?: return
        val now = presentationClockMs()
        val target = lineGuideTarget(action) ?: return
        val band = threeLineMotion.move(target, now,
            evidence.threeLine?.transitionMs ?: ReadingThreeLineGuideMotion.TRANSITION_MS,
            animateOpacity = isThreeLineGuide(action)).capped(evidence.opacityAt(now))
        val top = maxOf(band.top, visible.top - g.textOriginY)
        val bottom = minOf(band.bottom, visible.bottom - g.textOriginY)
        val x1 = maxOf(0.0, visible.left - g.textOriginX).toFloat()
        val x2 = minOf(layout.width.toDouble(), visible.right - g.textOriginX).toFloat()
        val e = action.guideEvidence ?: return
        val remaining = action.expiresMs - now
        if (remaining > 0) {
            val animating = threeLineMotion.isMoving(now) || now - e.presentationStartedMs < e.entryMs ||
                now > e.lastSupportedDeliveryMs + e.lossGraceMs
            val delay = if (animating) 16L else
                (e.lastSupportedDeliveryMs + e.lossGraceMs - now).toLong().coerceAtLeast(1L)
            lineBackground?.requestNextDraw(minOf(delay, remaining.toLong().coerceAtLeast(1L)))
        }
        if (top >= bottom || x1 >= x2 || (255 * band.centreOpacity).roundToInt() <= 0) return
        val saved = canvas.save()
        // View.draw compensates its background for internal scrolling. The band's
        // coordinates are Layout coordinates, so restore the text padding/scroll offset.
        canvas.translate((view.totalPaddingLeft - view.scrollX).toFloat(),
            (view.totalPaddingTop - view.scrollY).toFloat())
        canvas.clipRect(x1, top.toFloat(), x2, bottom.toFloat())
        if (gradients.size > 12) gradients.clear()
        var drawn = false
        fun section(start: Double, end: Double, alpha: Double, fadeTop: Boolean = false,
                    fadeBottom: Boolean = false, fadeFraction: Float = .12f) {
            val a = (255 * alpha).roundToInt()
            if (start >= end || end <= top || start >= bottom || a <= 0) return
            val sectionTop = maxOf(start, top).toFloat()
            val sectionBottom = minOf(end, bottom).toFloat()
            val fadeSpan = fadeFraction * (end - start)
            val peakFraction = minOf(
                if (fadeTop) ((sectionBottom - start) / fadeSpan).coerceIn(0.0, 1.0) else 1.0,
                if (fadeBottom) ((end - sectionTop) / fadeSpan).coerceIn(0.0, 1.0) else 1.0)
            if ((a * peakFraction).roundToInt() <= 0) return
            // Paint alpha also modulates shader alpha. Keep gradient stops opaque
            // at their plateau and apply the requested opacity exactly once here.
            val color = Color.rgb(128, 128, 128)
            val transparent = Color.argb(0, 128, 128, 128)
            backgroundPaint.color = color
            backgroundPaint.alpha = a
            val key = GradientKey(start, end, color, fadeTop, fadeBottom, fadeFraction)
            backgroundPaint.shader = if (!fadeTop && !fadeBottom) null else gradients.getOrPut(key) { when {
                fadeTop && fadeBottom -> LinearGradient(0f, start.toFloat(), 0f, end.toFloat(),
                    intArrayOf(transparent, color, color, transparent),
                    floatArrayOf(0f, fadeFraction, 1f - fadeFraction, 1f), Shader.TileMode.CLAMP)
                fadeTop -> LinearGradient(0f, start.toFloat(), 0f, end.toFloat(),
                    intArrayOf(transparent, color, color), floatArrayOf(0f, fadeFraction, 1f), Shader.TileMode.CLAMP)
                fadeBottom -> LinearGradient(0f, start.toFloat(), 0f, end.toFloat(),
                    intArrayOf(color, color, transparent), floatArrayOf(0f, 1f - fadeFraction, 1f), Shader.TileMode.CLAMP)
                else -> error("A gradient requires a fading edge")
            } }
            if (!canvas.quickReject(x1, sectionTop, x2, sectionBottom, Canvas.EdgeType.AA)) {
                canvas.drawRect(x1, sectionTop, x2, sectionBottom, backgroundPaint)
                drawn = true
            }
        }
        // One fill avoids row stripes and fractional-pixel seams while the band moves.
        section(band.top, band.bottom, band.centreOpacity, fadeTop = true, fadeBottom = true,
            fadeFraction = if (isThreeLineGuide(action)) ReadingLineGuideAppearance.THREE_EDGE_FADE_FRACTION else .12f)
        canvas.restoreToCount(saved)
        if (drawn) {
            threeLineDrawn = true
            receipt(action, true, if (isThreeLineGuide(action)) "three_line_text_background_draw" else "single_line_text_background_draw", now)
        }

    }

    private class GuideBackgroundSpan(private val color: () -> Int, private val eligible: () -> Boolean) :
        CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            if (eligible()) paint.bgColor = color()
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
