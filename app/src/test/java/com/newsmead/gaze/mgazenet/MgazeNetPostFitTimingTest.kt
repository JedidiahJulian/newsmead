package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test

class MgazeNetPostFitTimingTest {
    @Test fun ordinaryPostFitGetsTwoSecondsAfter1200MsSettleWithoutRelaxingQualityGate() {
        val start = 1_000.0
        val captures = (0..13).map { start + MgazeNetPostFitTiming.SETTLE_MS + it * 140.0 }
        val inOrdinaryWindow = captures.count { it < start + MgazeNetPostFitTiming.CAPTURE_END_MS }
        assertTrue(inOrdinaryWindow >= MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK)
        val retained = captures.count { MgazeNetPostFitTiming.accepts(start, it, it + 100.0, true) }
        assertEquals(14, retained)
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 1_199.0, start + 1_299.0, true))
        assertTrue(MgazeNetPostFitTiming.accepts(start, start + 3_199.0, start + 3_299.0, true))
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 7_200.0, start + 7_300.0, true))
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 7_199.0, start + 7_450.0, true))
        assertFalse(MgazeNetPostFitTiming.finished(start, start + 3_449.0))
        assertTrue(MgazeNetPostFitTiming.finished(start, start + 3_450.0))
    }

    @Test fun ordinaryCheckExtendsAndRetriesOnlyForInsufficientSamples() {
        val start = 1_000.0
        val timing = MgazeNetPostFitTiming
        assertEquals(MgazeNetPostFitTiming.Decision.WAIT, timing.decision(start, start + 3_449.0, 10, 1, true))
        assertEquals(MgazeNetPostFitTiming.Decision.EXTEND, timing.decision(start, start + 3_450.0, 10, 1, true))
        assertTrue(timing.accepts(start, start + 5_000.0, start + 5_100.0, true))
        assertEquals(MgazeNetPostFitTiming.Decision.COMPLETE, timing.decision(start, start + 5_100.0, 12, 1, true))
        assertFalse(timing.accepts(start, start + 7_200.0, start + 7_300.0, true))
        assertEquals(MgazeNetPostFitTiming.Decision.RETRY, timing.decision(start, start + 7_450.0, 11, 1, true))
        assertEquals(MgazeNetPostFitTiming.Decision.FAILED, timing.decision(start, start + 7_450.0, 11, 2, true))
    }

    @Test fun comparisonCheckKeepsTheExistingFixedWindowAndQualityDecision() {
        val start = 1_000.0
        val timing = MgazeNetPostFitTiming
        assertFalse(timing.accepts(start, start + 2_999.0, start + 3_099.0, false))
        assertTrue(timing.accepts(start, start + 3_000.0, start + 3_100.0, false))
        assertEquals(MgazeNetPostFitTiming.Decision.WAIT, timing.decision(start, start + 5_749.0, 10, 1, false))
        assertEquals(MgazeNetPostFitTiming.Decision.COMPLETE, timing.decision(start, start + 5_750.0, 10, 1, false))
        assertFalse(timing.accepts(start, start + 5_500.0, start + 5_600.0, false))
        assertFalse(timing.accepts(start, start + 5_499.0, start + 5_750.0, false))
        assertTrue(timing.accepts(start, start + 5_499.0, start + 5_749.0, false))
    }

    @Test fun postFitProgressAlignsWithCompletionAndRespectsSampleShortfall() {
        val start = 1_000.0
        val timing = MgazeNetPostFitTiming
        assertEquals(0f, timing.progress(start, start + 1_199.0, 12, true), 0f)
        assertEquals(0.5f, timing.progress(start, start + 2_325.0, 12, true), 0.001f)
        assertEquals(1f, timing.progress(start, start + 3_450.0, 12, true), 0f)
        assertEquals(8f / 12f, timing.progress(start, start + 3_450.0, 8, true), 0.001f)
        assertEquals(0f, timing.progress(start, start + 2_999.0, 12, false), 0f)
        assertEquals(1f, timing.progress(start, start + 5_750.0, 10, false), 0f)
    }
}
