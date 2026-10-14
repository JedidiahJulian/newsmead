package com.newsmead.gaze.aid.demo

import com.newsmead.gaze.aid.ReadingAidType

/** Presentation settings only: no observations, detector events, or policy decisions. */
object ReadingAidDemoPlan {
    const val STAGE_MS = 4000L
    data class Stage(val type: ReadingAidType?, val strength: Double)
    val legacyMagnifierStages = listOf(
        Stage(null, 0.0),
        Stage(ReadingAidType.CURRENT_LINE, 0.6),
        Stage(ReadingAidType.NEXT_LINE_START, 0.6),
        Stage(ReadingAidType.WORD_MAGNIFIER, 0.25),
        Stage(ReadingAidType.WORD_MAGNIFIER, 1.0),
        Stage(ReadingAidType.FOCUS_REGION, 0.25),
        Stage(ReadingAidType.FOCUS_REGION, 1.0),
    )
    val stages = listOf(
        Stage(null, 0.0),
        Stage(ReadingAidType.CURRENT_LINE, 0.25),
        Stage(ReadingAidType.READING_BOOKMARK, 1.0),
        Stage(ReadingAidType.WORD_DEFINITION, 1.0),
        Stage(ReadingAidType.FOCUS_REGION, 0.5),
        Stage(ReadingAidType.FOCUS_REGION, 1.0),
    )
    fun move(index: Int, delta: Int, count: Int = stages.size): Int = Math.floorMod(index + delta, count)
}
