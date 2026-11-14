package com.newsmead.gaze.aid

/** Smooths a requested opacity change; this clock never expires a bookmark or estimates reading. */
internal class ReadingBookmarkOpacityMotion {
    private var key: String? = null
    private var start = 0.0
    private var target = 0.0
    private var changedAt = 0.0

    fun clear() { key = null }

    fun move(nextKey: String, strength: Double, now: Double): Double {
        val next = strength.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        if (key != nextKey) {
            key = nextKey; start = next; target = next; changedAt = now
        } else if (next != target) {
            start = value(now); target = next; changedAt = now
        }
        return value(now)
    }

    fun isAnimating(now: Double) = key != null && start != target &&
        now - changedAt < ReadingLineGuideAppearance.TRANSITION_MS

    private fun value(now: Double): Double {
        val fraction = ((now - changedAt) / ReadingLineGuideAppearance.TRANSITION_MS).coerceIn(0.0, 1.0)
        val eased = fraction * fraction * (3.0 - 2.0 * fraction)
        return start + (target - start) * eased
    }
}
