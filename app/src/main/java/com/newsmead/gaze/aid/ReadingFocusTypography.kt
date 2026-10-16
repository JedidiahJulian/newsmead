package com.newsmead.gaze.aid

/** Trial safeguards for smaller rendered letters; not calibrated difficulty thresholds. */
internal object ReadingFocusTypography {
    const val VERSION = "RENDERED_SIZE_LINE_VISITS_V1"
    data class Rule(val id: String, val requiredAlternations: Int, val extraMinVisitMs: Double)
    val STANDARD = Rule("STANDARD_22_DP_AND_UP", 1, 0.0)
    val SMALL = Rule("SMALL_18_TO_BELOW_22_DP", 2, 160.0)
    val COMPACT = Rule("COMPACT_BELOW_18_DP", 3, 240.0)

    // The adapter supplies font pixels / display density, including system font scaling.
    // A tiny tolerance avoids changing tiers because of float conversion roundoff.
    fun rule(textSizeDp: Double): Rule = when {
        !textSizeDp.isFinite() || textSizeDp <= 0.0 -> COMPACT
        textSizeDp < 18.0 - 0.001 -> COMPACT
        textSizeDp < 22.0 - 0.001 -> SMALL
        else -> STANDARD
    }
}
