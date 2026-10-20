package com.newsmead.gaze.aid

/** Trial presentation targets, separate from uncertainty, detector support and confidence. */
internal object ReadingLineGuideAppearance {
    const val THREE_CENTER_OPACITY = .09
    const val THREE_EDGE_FADE_FRACTION = .06f
    const val SETTLE_SUPPORTED_MS = 900.0
    const val TRANSITION_MS = 250.0

    fun threeLine(direction: ReadingGuideDirection, centerOpacity: Double,
                  support: List<ReadingAidContinuationEvidence> = emptyList()): ReadingThreeLineGuideEvidence {
        val opacity = centerOpacity.coerceIn(0.0, THREE_CENTER_OPACITY)
        return ReadingThreeLineGuideEvidence(direction,
            if (direction == ReadingGuideDirection.DOWN) 0.0 else opacity,
            if (direction == ReadingGuideDirection.UP) 0.0 else opacity,
            TRANSITION_MS, support)
    }

    /** Keep the supported row at the requested slot; clip missing article-edge rows. */
    fun rows(line: Int, lastLine: Int, direction: ReadingGuideDirection): IntRange = when (direction) {
        ReadingGuideDirection.DOWN -> line..minOf(lastLine, line + 2)
        ReadingGuideDirection.UP -> maxOf(0, line - 2)..line
        ReadingGuideDirection.BALANCED -> maxOf(0, line - 1)..minOf(lastLine, line + 1)
    }
}
