package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test

class MgazeNetPostFitTimingTest {
    @Test fun slowButEligibleFramesGetFourSecondsWithoutRelaxingTheQualityGate() {
        val start = 1_000.0
        val captures = (0..15).map { start + MgazeNetPostFitTiming.SETTLE_MS + it * 250.0 }
        assertEquals(10, captures.count { it < start + 5_500.0 })
        val retained = captures.count { MgazeNetPostFitTiming.accepts(start, it, it + 100.0) }
        assertEquals(16, retained)
        assertTrue(retained >= MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK)
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 2_999.0, start + 3_099.0))
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 7_000.0, start + 7_100.0))
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 6_999.0, start + 7_250.0))
        assertFalse(MgazeNetPostFitTiming.finished(start, start + 7_249.0))
        assertTrue(MgazeNetPostFitTiming.finished(start, start + 7_250.0))
    }
}
