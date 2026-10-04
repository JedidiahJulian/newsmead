package com.newsmead.gaze.aid.demo

import com.newsmead.gaze.aid.ReadingAidType
import org.junit.Assert.*
import org.junit.Test

class ReadingAidDemoPlanTest {
    @Test fun cycleContainsEachDisplayAndPairedStrengthsInOrder() {
        val stages = ReadingAidDemoPlan.stages
        assertEquals(listOf(null, ReadingAidType.CURRENT_LINE, ReadingAidType.NEXT_LINE_START,
            ReadingAidType.WORD_DEFINITION,
            ReadingAidType.FOCUS_REGION, ReadingAidType.FOCUS_REGION), stages.map { it.type })
        assertEquals(listOf(0.0, 0.6, 1.0, 1.0, 0.25, 1.0), stages.map { it.strength })
        assertEquals(4000L, ReadingAidDemoPlan.STAGE_MS)
    }

    @Test fun navigationWrapsBothDirections() {
        assertEquals(0, ReadingAidDemoPlan.move(5, 1))
        assertEquals(5, ReadingAidDemoPlan.move(0, -1))
        assertEquals(0, ReadingAidDemoPlan.move(0, 6))
        assertEquals(0, ReadingAidDemoPlan.move(0, -6))
    }
}
