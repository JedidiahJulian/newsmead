package com.newsmead.gaze.aid

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Rect
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.SystemClock

/** Keeps the body's own background below the guide and both below TextView glyphs. */
internal class ReadingLineGuideBackground(
    val base: Drawable?,
    private val drawGuide: (Canvas) -> Unit,
) : Drawable(), Drawable.Callback {
    private val scheduled = mutableSetOf<Runnable>()
    private val nextDraw = Runnable { invalidateSelf() }

    fun requestNextDraw(delayMs: Long) {
        unscheduleSelf(nextDraw)
        scheduleSelf(nextDraw, SystemClock.uptimeMillis() + delayMs.coerceAtLeast(1))
    }

    fun attach() { base?.callback = this }
    fun release() {
        unscheduleSelf(nextDraw)
        scheduled.toList().forEach(::unscheduleSelf)
        scheduled.clear()
        if (base?.callback === this) base.callback = null
    }
    override fun draw(canvas: Canvas) { base?.draw(canvas); drawGuide(canvas) }
    override fun onBoundsChange(bounds: Rect) { base?.bounds = bounds }
    override fun setAlpha(alpha: Int) { base?.alpha = alpha }
    override fun getAlpha(): Int = base?.alpha ?: 255
    override fun setColorFilter(filter: ColorFilter?) { base?.colorFilter = filter }
    override fun getColorFilter(): ColorFilter? = base?.colorFilter
    override fun getOutline(outline: Outline) { base?.getOutline(outline) ?: outline.setEmpty() }
    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean =
        base?.setLayoutDirection(layoutDirection) ?: false
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun isStateful(): Boolean = base?.isStateful ?: false
    override fun onStateChange(state: IntArray): Boolean = base?.setState(state) ?: false
    override fun onLevelChange(level: Int): Boolean = base?.setLevel(level) ?: false
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        return (base?.setVisible(visible, restart) ?: false) || changed
    }
    override fun getPadding(padding: Rect): Boolean = base?.getPadding(padding) ?: run { padding.setEmpty(); false }
    override fun getIntrinsicWidth(): Int = base?.intrinsicWidth ?: -1
    override fun getIntrinsicHeight(): Int = base?.intrinsicHeight ?: -1
    override fun getMinimumWidth(): Int = base?.minimumWidth ?: 0
    override fun getMinimumHeight(): Int = base?.minimumHeight ?: 0
    override fun jumpToCurrentState() { base?.jumpToCurrentState() }
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, whenMs: Long) {
        scheduled.add(what); scheduleSelf(what, whenMs)
    }
    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        scheduled.remove(what); unscheduleSelf(what)
    }
}
