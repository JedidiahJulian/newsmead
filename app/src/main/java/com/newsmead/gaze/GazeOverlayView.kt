package com.newsmead.gaze

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 64, 0)
        style = Paint.Style.FILL
    }
    private val radius = 18f
    private val loc = IntArray(2)

    private var hasPoint = false
    private var screenX = 0f
    private var screenY = 0f

    init {
        isClickable = false
        isFocusable = false
    }

    /** Set the current gaze point in full-screen pixels. */
    fun setGazeScreen(x: Float, y: Float) {
        screenX = x
        screenY = y
        hasPoint = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasPoint) return
        getLocationOnScreen(loc)
        canvas.drawCircle(screenX - loc[0], screenY - loc[1], radius, paint)
    }
}
