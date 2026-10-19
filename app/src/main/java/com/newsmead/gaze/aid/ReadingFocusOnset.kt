package com.newsmead.gaze.aid

/** One continuous presentation, independent of policy action IDs and capture time. */
internal class ReadingFocusOnset {
    companion object { const val ONSET_MS = 400.0 }
    var animateAllStyles: Boolean = false

    var style: ReadingFocusStyle? = null
        private set
    private var startedMs: Double? = null

    fun select(next: ReadingFocusStyle?): Boolean {
        if (style == next) return false
        style = next
        startedMs = null
        return true
    }

    fun clear(): Boolean = select(null)

    fun fraction(now: Double): Double {
        if (style == null || (!animateAllStyles && style != ReadingFocusStyle.DARK_SURROUNDINGS)) return 1.0
        val start = startedMs ?: now.also { startedMs = it }
        val t = ((now - start) / ONSET_MS).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    fun isRunning(now: Double): Boolean = (style == ReadingFocusStyle.DARK_SURROUNDINGS || (animateAllStyles && style != null)) &&
        startedMs?.let { now - it < ONSET_MS } == true
}
