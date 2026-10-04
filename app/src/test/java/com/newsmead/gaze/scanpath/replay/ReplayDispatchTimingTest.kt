package com.newsmead.gaze.scanpath.replay

import org.junit.Assert.*
import org.junit.Test

class ReplayDispatchTimingTest {
    @Test fun fractionalEarlyCallbacksRetryBeforeDelivery() {
        val capture = 161929788.0
        listOf(0.07, 0.09078601002693176, 0.12).forEach { earlyMs ->
            val timing = ReplayDispatchTiming.assess(capture, capture - earlyMs)
            assertEquals(1L, timing.retryDelayMs)
            assertFalse(timing.tooLate)
        }
        assertEquals(3L, ReplayDispatchTiming.assess(capture, capture - 2.01).retryDelayMs)
    }

    @Test fun captureDeadlineAndHundredMillisecondBoundaryRemainEligible() {
        listOf(0.0, 0.001, 99.999, 100.0).forEach { lagMs ->
            assertEquals(ReplayDispatchTiming(0L, false), ReplayDispatchTiming.assess(1000.0, 1000.0 + lagMs))
        }
    }

    @Test fun retryUsesActualNewDeliveryAndStillRejectsExcessiveLateness() {
        val capture = 1000.0
        assertEquals(1L, ReplayDispatchTiming.assess(capture, 999.9).retryDelayMs)
        assertEquals(ReplayDispatchTiming(0L, false), ReplayDispatchTiming.assess(capture, 1000.9))
        assertEquals(ReplayDispatchTiming(0L, true), ReplayDispatchTiming.assess(capture, 1100.001))
    }
}
