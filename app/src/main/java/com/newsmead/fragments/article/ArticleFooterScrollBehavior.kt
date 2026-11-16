package com.newsmead.fragments.article

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/** Slides the article bottom bar off-screen on downward scroll without leaving a layout gap. */
internal class ArticleFooterScrollBehavior(
    private val scrollView: NestedScrollView,
    private val bottomBar: View,
    // True for armed auto mode (enabled and not reader-paused), including gaze stops.
    private val isAutoScrolling: () -> Boolean = { false },
) {
    private val threshold = ViewConfiguration.get(bottomBar.context).scaledTouchSlop
    private var hidden = false
    private var animator: ValueAnimator? = null
    private var pendingDelta = 0
    private var pendingPreparation: View.OnLayoutChangeListener? = null

    init {
        scrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (isAutoScrolling()) {
                    pendingDelta = 0
                    return@OnScrollChangeListener
                }
                if (scrollY <= 0) {
                    pendingDelta = 0
                    setHidden(false)
                    return@OnScrollChangeListener
                }
                val dy = scrollY - oldScrollY
                if (dy == 0) return@OnScrollChangeListener
                if ((dy > 0) != (pendingDelta > 0)) pendingDelta = 0
                pendingDelta += dy
                if (pendingDelta > threshold && !hidden) {
                    pendingDelta = 0
                    setHidden(true)
                } else if (pendingDelta < -threshold && hidden) {
                    pendingDelta = 0
                    setHidden(false)
                }
            }
        )
    }

    /** Reclaim the footer region before accepting settled auto-scroll input. */
    fun prepareForAutoScroll() {
        pendingDelta = 0
        clearPendingPreparation()
        if (bottomBar.height > 0) {
            setHidden(true)
        } else {
            // Enable may precede the fragment's first layout. A reader action or
            // detach must cancel this deferred preparation rather than hide later.
            val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                clearPendingPreparation()
                if (isAutoScrolling()) setHidden(true)
            }
            pendingPreparation = listener
            bottomBar.addOnLayoutChangeListener(listener)
        }
    }

    fun show() {
        clearPendingPreparation()
        pendingDelta = 0
        setHidden(false)
    }

    private fun clearPendingPreparation() {
        pendingPreparation?.let(bottomBar::removeOnLayoutChangeListener)
        pendingPreparation = null
    }

    fun setHidden(shouldHide: Boolean, animate: Boolean = true) {
        if (hidden == shouldHide) return
        val height = bottomBar.height
        if (height <= 0) return
        val params = bottomBar.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        hidden = shouldHide
        animator?.cancel()
        val targetMargin = if (shouldHide) -height else 0
        if (!shouldHide) bottomBar.visibility = View.VISIBLE
        if (!animate || params.bottomMargin == targetMargin) {
            params.bottomMargin = targetMargin
            bottomBar.layoutParams = params
            if (shouldHide) bottomBar.visibility = View.INVISIBLE
            return
        }
        animator = ValueAnimator.ofInt(params.bottomMargin, targetMargin).apply {
            duration = 180L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val p = bottomBar.layoutParams as? ViewGroup.MarginLayoutParams ?: return@addUpdateListener
                p.bottomMargin = anim.animatedValue as Int
                bottomBar.layoutParams = p
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (hidden) bottomBar.visibility = View.INVISIBLE
                }
            })
            start()
        }
    }

    fun detach() {
        clearPendingPreparation()
        animator?.cancel()
        animator = null
        scrollView.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
    }
}
