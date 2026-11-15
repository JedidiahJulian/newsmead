package com.newsmead.gaze

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

/**
 * Debug overlay for Stage 4: draws the current gaze point (given in full-screen
 * pixels) as a translucent dot on top of the article content. Purely visual —
 * it never consumes touch, so scrolling and the article controls still work.
 *
 * The point is stored in screen space and converted to this view's local space
 * at draw time via [getLocationOnScreen], so it stays correct wherever the
 * overlay sits in the layout.
 */
class GazeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    enum class CursorStyle { DOT, REGION }

    private val gazePaint = fillPaint(
        Color.argb(GAZE_DOT_ALPHA, GAZE_DOT_RED, GAZE_DOT_GREEN, GAZE_DOT_BLUE),
    )
    private val regionRadiusPx = dp(GAZE_REGION_RADIUS_DP)
    private val regionRingStrokePx = dp(GAZE_REGION_RING_STROKE_DP)
    private val regionOuterRadiusPx = (regionRadiusPx + dp(GAZE_REGION_HALO_WIDTH_DP)).coerceAtLeast(1f)
    private val regionMatrix = Matrix()
    private val regionShader = run {
        // Keep the center completely transparent so article text underneath remains untouched.
        val haloStartFrac = ((regionRadiusPx - regionRingStrokePx * 0.5f) / regionOuterRadiusPx).coerceIn(0f, 0.97f)
        val haloPeakFrac = (regionRadiusPx / regionOuterRadiusPx).coerceIn(haloStartFrac + 0.01f, 0.99f)
        RadialGradient(
            0f,
            0f,
            regionOuterRadiusPx,
            intArrayOf(
                Color.argb(0, GAZE_REGION_RED, GAZE_REGION_GREEN, GAZE_REGION_BLUE),
                Color.argb(0, GAZE_REGION_RED, GAZE_REGION_GREEN, GAZE_REGION_BLUE),
                Color.argb(GAZE_REGION_HALO_ALPHA, GAZE_REGION_RED, GAZE_REGION_GREEN, GAZE_REGION_BLUE),
                Color.argb(0, GAZE_REGION_RED, GAZE_REGION_GREEN, GAZE_REGION_BLUE),
            ),
            floatArrayOf(0f, haloStartFrac, haloPeakFrac, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val regionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = regionShader
    }
    private val regionRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(GAZE_REGION_RED, GAZE_REGION_GREEN, GAZE_REGION_BLUE)
        style = Paint.Style.STROKE
        strokeWidth = regionRingStrokePx
    }
    private val regionVisual = GazeRegionVisualState()
    private val wordPaint = fillPaint(Color.argb(82, 244, 211, 94))
    private val linePaint = fillPaint(Color.argb(58, 244, 211, 94))
    private val focusPaint = fillPaint(Color.argb(112, 0, 0, 0))
    private val reentryPaint = fillPaint(Color.argb(105, 44, 128, 220))
    private val reentryCuePaint = fillPaint(Color.argb(230, 44, 128, 220))
    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 229, 255)
        textSize = 36f
        typeface = Typeface.MONOSPACE
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 0, 229, 255)
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val radius = GAZE_DOT_RADIUS_PX
    private val cornerRadius = dp(5f)
    private val loc = IntArray(2)
    private val textLoc = IntArray(2)
    private val visibleTextRect = Rect()
    private val drawRect = RectF()
    private val cuePath = Path()

    private var hasPoint = false
    private var screenX = 0f
    private var screenY = 0f
    private var cursorStyle = CursorStyle.DOT
    private var fpsText: String? = null
    private var captionText: String? = null
    private var debugVisualsEnabled = false
    private var scaffoldTextView: TextView? = null
    private var requestedState = ScaffoldState()
    private var renderedState = ScaffoldState()
    private var scaffoldOpacity = 0f
    private var fadeAnimator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
    }

    /** Switch between pinpoint DOT (validation/comparison) and feathered REGION (article reading). */
    fun setCursorStyle(style: CursorStyle) {
        if (cursorStyle == style) return
        cursorStyle = style
        regionVisual.reset()
        postInvalidateOnAnimation()
    }

    /** Set the current gaze point in full-screen pixels. */
    fun setGazeScreen(x: Float, y: Float) {
        screenX = x
        screenY = y
        hasPoint = true
        if (cursorStyle == CursorStyle.REGION) {
            regionVisual.setTarget(x, y)
        }
        // Participant reading hides the diagnostic dot. Keep its latest position
        // without scheduling an otherwise empty draw for every camera result.
        if (debugVisualsEnabled || dotOnly || scaffoldOpacity > 0f) postInvalidateOnAnimation()
    }

    /** Clear only the debug gaze dot or fade out the gaze region when input is unavailable/stale. */
    fun clearGaze() {
        if (!hasPoint) return
        hasPoint = false
        if (cursorStyle == CursorStyle.REGION) {
            regionVisual.clear()
        }
        if (debugVisualsEnabled || dotOnly || scaffoldOpacity > 0f) postInvalidateOnAnimation()
    }

    fun setDebugVisualsEnabled(enabled: Boolean) {
        if (debugVisualsEnabled == enabled) return
        debugVisualsEnabled = enabled
        if (!enabled && cursorStyle == CursorStyle.REGION) {
            regionVisual.reset()
        }
        postInvalidateOnAnimation()
    }

    fun setScaffoldState(textView: TextView, state: ScaffoldState) {
        if (scaffoldTextView === textView && requestedState == state) return
        scaffoldTextView = textView
        val levelChanged = requestedState.level != state.level
        requestedState = state
        if (!levelChanged) {
            if (state.level != ScaffoldLevel.NONE) renderedState = state
            postInvalidateOnAnimation()
            return
        }

        fadeAnimator?.cancel()
        if (state.level == ScaffoldLevel.NONE) {
            fadeAnimator = animateOpacity(scaffoldOpacity, 0f) {
                if (requestedState.level == ScaffoldLevel.NONE) renderedState = requestedState
            }
        } else {
            renderedState = state
            scaffoldOpacity = 0f
            fadeAnimator = animateOpacity(0f, 1f)
        }
    }

    fun clearScaffold() {
        scaffoldTextView?.let { setScaffoldState(it, ScaffoldState()) }
    }

    /** Label the active level on screen during a demo recording; null hides it. */
    fun setScaffoldCaption(text: String?) {
        if (captionText == text) return
        captionText = text
        postInvalidateOnAnimation()
    }

    /** Show the tracker's current processing frame rate in the corner. */
    fun setFps(fps: Float) {
        val text = String.format(Locale.US, "%.0f fps", fps)
        if (fpsText == text) return
        fpsText = text
        if (debugVisualsEnabled && !dotOnly) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        fadeAnimator?.cancel()
        regionVisual.reset()
        super.onDetachedFromWindow()
    }

    private var dotOnly = false

    /** Researcher recording view: retain the gaze cursor, suppress all other overlays. */
    fun setDotOnly(enabled: Boolean) {
        if (dotOnly == enabled) return
        dotOnly = enabled
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!dotOnly) drawScaffold(canvas)
        // Above the article body, next to the FPS readout - never over the text.
        if (!dotOnly) captionText?.let { canvas.drawText(it, 24f, 104f, captionPaint) }
        if (!debugVisualsEnabled && !dotOnly) return
        if (!dotOnly) fpsText?.let { canvas.drawText(it, 24f, 60f, fpsPaint) }
        if (cursorStyle == CursorStyle.REGION) {
            drawRegion(canvas)
            return
        }
        if (!hasPoint) return
        getLocationOnScreen(loc)
        canvas.drawCircle(screenX - loc[0], screenY - loc[1], radius, gazePaint)
    }

    private fun drawRegion(canvas: Canvas) {
        val keepAnimating = regionVisual.step(System.nanoTime())
        if (regionVisual.visible) {
            getLocationOnScreen(loc)
            val drawX = regionVisual.renderX - loc[0]
            val drawY = regionVisual.renderY - loc[1]
            regionMatrix.setTranslate(drawX, drawY)
            regionShader.setLocalMatrix(regionMatrix)
            regionPaint.alpha = (255 * regionVisual.alpha).toInt().coerceIn(0, 255)
            regionRingPaint.alpha = (GAZE_REGION_RING_ALPHA * regionVisual.alpha).toInt().coerceIn(0, 255)
            canvas.drawCircle(drawX, drawY, regionOuterRadiusPx, regionPaint)
            canvas.drawCircle(drawX, drawY, regionRadiusPx, regionRingPaint)
        }
        if (keepAnimating) postInvalidateOnAnimation()
    }

    private fun drawScaffold(canvas: Canvas) {
        if (scaffoldOpacity <= 0f) return
        val textView = scaffoldTextView ?: return
        when (renderedState.level) {
            ScaffoldLevel.NONE -> Unit
            ScaffoldLevel.WORD -> drawWord(canvas, textView, renderedState)
            ScaffoldLevel.LINE -> drawLine(canvas, textView, renderedState.currentLine, linePaint)
            ScaffoldLevel.FOCUS -> drawFocusWindow(canvas, textView, renderedState.currentLine)
            ScaffoldLevel.REENTRY -> drawReentry(canvas, textView, renderedState.reentryTargetLine)
        }
    }

    private fun drawWord(canvas: Canvas, textView: TextView, state: ScaffoldState) {
        val layout = textView.layout ?: return
        if (state.wordStart < 0 || state.wordEnd <= state.wordStart ||
            state.wordStart >= layout.text.length || !prepareGeometry(textView)
        ) return

        val safeEnd = state.wordEnd.coerceAtMost(layout.text.length)
        val firstLine = layout.getLineForOffset(state.wordStart)
        val lastLine = layout.getLineForOffset((safeEnd - 1).coerceAtLeast(state.wordStart))
        setOpacity(wordPaint, WORD_ALPHA)
        for (line in firstLine..lastLine) {
            val start = maxOf(state.wordStart, layout.getLineStart(line))
            val end = minOf(safeEnd, layout.getLineEnd(line))
            val left = layout.getPrimaryHorizontal(start)
            val right = layout.getPrimaryHorizontal(end)
            if (setTextRect(textView, line, minOf(left, right), maxOf(left, right), dp(3f))) {
                canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, wordPaint)
            }
        }
    }

    private fun drawLine(canvas: Canvas, textView: TextView, line: Int, paint: Paint) {
        val layout = textView.layout ?: return
        if (line !in 0 until layout.lineCount || !prepareGeometry(textView)) return
        setOpacity(paint, if (paint === reentryPaint) REENTRY_ALPHA else LINE_ALPHA)
        val fullRight = (textView.width - textView.totalPaddingLeft - textView.totalPaddingRight).toFloat()
        if (setTextRect(textView, line, 0f, fullRight)) {
            canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, paint)
        }
    }

    private fun drawFocusWindow(canvas: Canvas, textView: TextView, centerLine: Int) {
        val layout = textView.layout ?: return
        if (centerLine !in 0 until layout.lineCount || !prepareGeometry(textView)) return
        val firstLine = (centerLine - FOCUS_RADIUS_LINES).coerceAtLeast(0)
        val lastLine = (centerLine + FOCUS_RADIUS_LINES).coerceAtMost(layout.lineCount - 1)
        val windowTop = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineTop(firstLine)
        val windowBottom = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineBottom(lastLine)
        val left = (visibleTextRect.left - loc[0]).toFloat()
        val right = (visibleTextRect.right - loc[0]).toFloat()
        val visibleTop = (visibleTextRect.top - loc[1]).toFloat()
        val visibleBottom = (visibleTextRect.bottom - loc[1]).toFloat()
        val focusTop = (windowTop.coerceIn(visibleTextRect.top, visibleTextRect.bottom) - loc[1]).toFloat()
        val focusBottom = (windowBottom.coerceIn(visibleTextRect.top, visibleTextRect.bottom) - loc[1]).toFloat()

        setOpacity(focusPaint, FOCUS_ALPHA)
        if (focusTop > visibleTop) canvas.drawRect(left, visibleTop, right, focusTop, focusPaint)
        if (focusBottom < visibleBottom) canvas.drawRect(left, focusBottom, right, visibleBottom, focusPaint)
    }

    private fun drawReentry(canvas: Canvas, textView: TextView, targetLine: Int) {
        val layout = textView.layout ?: return
        if (targetLine !in 0 until layout.lineCount || !prepareGeometry(textView)) return
        val targetTop = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineTop(targetLine)
        val targetBottom = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineBottom(targetLine)
        if (targetBottom > visibleTextRect.top && targetTop < visibleTextRect.bottom) {
            drawLine(canvas, textView, targetLine, reentryPaint)
            drawCue(
                canvas,
                pointsUp = targetLine < renderedState.currentLine,
                y = ((targetTop + targetBottom) / 2 - loc[1]).toFloat(),
            )
        } else {
            val pointsUp = targetBottom <= visibleTextRect.top
            val y = if (pointsUp) {
                (visibleTextRect.top - loc[1]).toFloat() + dp(22f)
            } else {
                (visibleTextRect.bottom - loc[1]).toFloat() - dp(22f)
            }
            drawCue(canvas, pointsUp, y)
        }
    }

    private fun drawCue(canvas: Canvas, pointsUp: Boolean, y: Float) {
        // Sits in the margin beside the text column so it never covers the words.
        // Narrow enough to fit the gutter, then made taller to stay readable.
        val columnLeft = (visibleTextRect.left - loc[0]).toFloat()
        val halfWidth = (columnLeft / 2f - dp(1f)).coerceIn(dp(4f), dp(9f))
        val centerX = (columnLeft - columnLeft / 2f).coerceAtLeast(halfWidth + dp(1f))
        val halfHeight = halfWidth * 1.6f
        cuePath.reset()
        if (pointsUp) {
            cuePath.moveTo(centerX, y - halfHeight)
            cuePath.lineTo(centerX - halfWidth, y + halfHeight)
            cuePath.lineTo(centerX + halfWidth, y + halfHeight)
        } else {
            cuePath.moveTo(centerX, y + halfHeight)
            cuePath.lineTo(centerX - halfWidth, y - halfHeight)
            cuePath.lineTo(centerX + halfWidth, y - halfHeight)
        }
        cuePath.close()
        setOpacity(reentryCuePaint, REENTRY_CUE_ALPHA)
        canvas.drawPath(cuePath, reentryCuePaint)
    }

    private fun prepareGeometry(textView: TextView): Boolean {
        if (!textView.getVisibleRectOnScreen(visibleTextRect, loc)) return false
        getLocationOnScreen(loc)
        textView.getLocationOnScreen(textLoc)
        return true
    }

    private fun setTextRect(
        textView: TextView,
        line: Int,
        contentLeft: Float,
        contentRight: Float,
        horizontalPad: Float = 0f,
    ): Boolean {
        val layout = textView.layout ?: return false
        val screenTop = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineTop(line)
        val screenBottom = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineBottom(line)
        val clippedTop = maxOf(screenTop, visibleTextRect.top)
        val clippedBottom = minOf(screenBottom, visibleTextRect.bottom)
        if (clippedTop >= clippedBottom) return false

        val screenLeft = textLoc[0] + textView.totalPaddingLeft + contentLeft - horizontalPad
        val screenRight = textLoc[0] + textView.totalPaddingLeft + contentRight + horizontalPad
        drawRect.set(
            maxOf(screenLeft, visibleTextRect.left.toFloat()) - loc[0],
            clippedTop.toFloat() - loc[1],
            minOf(screenRight, visibleTextRect.right.toFloat()) - loc[0],
            clippedBottom.toFloat() - loc[1],
        )
        return drawRect.left < drawRect.right
    }

    private fun animateOpacity(from: Float, to: Float, after: (() -> Unit)? = null): ValueAnimator =
        ValueAnimator.ofFloat(from, to).apply {
            duration = FADE_DURATION_MS
            addUpdateListener {
                scaffoldOpacity = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            if (after != null) {
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = after()
                })
            }
            start()
        }

    private fun setOpacity(paint: Paint, baseAlpha: Int) {
        paint.alpha = (baseAlpha * scaffoldOpacity).toInt().coerceIn(0, 255)
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        const val GAZE_DOT_RADIUS_PX = 18f
        const val GAZE_DOT_ALPHA = 180
        const val GAZE_DOT_RED = 255
        const val GAZE_DOT_GREEN = 64
        const val GAZE_DOT_BLUE = 0
        const val GAZE_REGION_RADIUS_DP = 26f
        const val GAZE_REGION_RING_STROKE_DP = 1.5f
        const val GAZE_REGION_RING_ALPHA = 135
        const val GAZE_REGION_HALO_WIDTH_DP = 3f
        const val GAZE_REGION_HALO_ALPHA = 50
        const val GAZE_REGION_RED = 120
        const val GAZE_REGION_GREEN = 144
        const val GAZE_REGION_BLUE = 156
        private const val WORD_ALPHA = 82
        private const val LINE_ALPHA = 58
        private const val FOCUS_ALPHA = 112
        private const val REENTRY_ALPHA = 105
        private const val REENTRY_CUE_ALPHA = 230
        private const val FOCUS_RADIUS_LINES = 2
        private const val FADE_DURATION_MS = 400L
    }
}

/**
 * Display-only exponential glide and fade state for [GazeOverlayView.CursorStyle.REGION].
 * Never mutates recorded coordinates, scanpath state, or aid triggers.
 */
internal class GazeRegionVisualState(
    private val glideRateHz: Float = 14f,
    private val fadeInRateHz: Float = 12f,
    private val fadeOutRateHz: Float = 8f,
) {
    var hasTarget = false; private set
    var hasRenderPoint = false; private set
    var targetX = 0f; private set
    var targetY = 0f; private set
    var renderX = 0f; private set
    var renderY = 0f; private set
    var alpha = 0f; private set
    private var lastStepNs = 0L

    val visible: Boolean get() = hasRenderPoint && alpha > MIN_VISIBLE_ALPHA

    fun setTarget(x: Float, y: Float) {
        targetX = x
        targetY = y
        hasTarget = true
        if (!hasRenderPoint) {
            renderX = x
            renderY = y
            hasRenderPoint = true
        }
    }

    fun clear() {
        hasTarget = false
    }

    fun reset() {
        hasTarget = false
        hasRenderPoint = false
        alpha = 0f
        lastStepNs = 0L
    }

    /** Advances glide/fade for one display frame; returns true while further animation frames are needed. */
    fun step(nowNs: Long): Boolean {
        val dtSec = if (lastStepNs == 0L || nowNs <= lastStepNs) {
            DEFAULT_FRAME_DT_SEC
        } else {
            ((nowNs - lastStepNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        }
        lastStepNs = nowNs

        val goalAlpha = if (hasTarget) 1f else 0f
        val alphaRate = if (hasTarget) fadeInRateHz else fadeOutRateHz
        val alphaFactor = 1f - exp(-dtSec * alphaRate)
        alpha += (goalAlpha - alpha) * alphaFactor
        if (abs(goalAlpha - alpha) <= SNAP_ALPHA_EPS) {
            alpha = goalAlpha
        }

        if (!hasTarget && alpha <= MIN_VISIBLE_ALPHA) {
            alpha = 0f
            hasRenderPoint = false
            lastStepNs = 0L
            return false
        }

        if (hasTarget && hasRenderPoint) {
            val posFactor = 1f - exp(-dtSec * glideRateHz)
            renderX += (targetX - renderX) * posFactor
            renderY += (targetY - renderY) * posFactor
            if (abs(targetX - renderX) <= SNAP_PX_EPS) renderX = targetX
            if (abs(targetY - renderY) <= SNAP_PX_EPS) renderY = targetY
        }

        val alphaMoving = abs(goalAlpha - alpha) > SNAP_ALPHA_EPS
        val posMoving = hasTarget && (abs(targetX - renderX) > SNAP_PX_EPS || abs(targetY - renderY) > SNAP_PX_EPS)
        return alphaMoving || posMoving
    }

    private companion object {
        const val DEFAULT_FRAME_DT_SEC = 1f / 60f
        const val MIN_VISIBLE_ALPHA = 0.01f
        const val SNAP_ALPHA_EPS = 0.01f
        const val SNAP_PX_EPS = 0.5f
    }
}
