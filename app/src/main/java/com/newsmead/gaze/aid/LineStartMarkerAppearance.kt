package com.newsmead.gaze.aid

/** Cue appearance settings; strength and opacity do not encode reading difficulty. */
object LineStartMarkerAppearance {
    const val FORM_ID = "CONTINUATION_LINE_START_CUE_V1"
    const val LEGACY_BOOKMARK_FORM_ID = "LAST_READ_LINE_BOOKMARK_V1"
    const val BOOKMARK_FORM_ID = "LAST_READ_LINE_BOOKMARK_V3"
    const val BOOKMARK_DEFAULT_STRENGTH = 0.75
    /** Exploratory presentation floor; progress changes prominence, never the saved location. */
    const val BOOKMARK_DIMMED_STRENGTH = 0.40
    const val WIDTH_DP = 6f
    const val ALPHA = 240
    /** Current bookmark strength maps directly to opacity; legacy cues keep ALPHA. */
    const val BOOKMARK_ALPHA_MAX = 255

    fun bookmarkProgressFactor(progress: Double): Double {
        val p = progress.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        return 1.0 - p * (1.0 - BOOKMARK_DIMMED_STRENGTH / BOOKMARK_DEFAULT_STRENGTH)
    }

    fun bookmarkAlpha(strength: Double): Int =
        (BOOKMARK_ALPHA_MAX * (strength.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0)).toInt()
}
