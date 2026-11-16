package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MgazeNetStartupTimeoutPolicyTest {
    @Test fun slowModelInitializationDoesNotConsumeTheFirstOutputWindow() {
        assertNull(MgazeNetStartupTimeoutPolicy.expired(1_000.0, -1.0, -1.0, 21_500.0))
        assertNull(MgazeNetStartupTimeoutPolicy.expired(1_000.0, 22_000.0, -1.0, 41_999.0))
        assertEquals(MgazeNetStartupTimeout.FIRST_OUTPUT,
            MgazeNetStartupTimeoutPolicy.expired(1_000.0, 22_000.0, -1.0, 42_001.0))
    }

    @Test fun initializationThatNeverBindsStillTimesOut() {
        assertNull(MgazeNetStartupTimeoutPolicy.expired(1_000.0, -1.0, -1.0, 61_000.0))
        assertEquals(MgazeNetStartupTimeout.INITIALIZATION,
            MgazeNetStartupTimeoutPolicy.expired(1_000.0, -1.0, -1.0, 61_001.0))
    }

    @Test fun anyDeliveredOutputDisablesBothStartupTimeouts() {
        assertNull(MgazeNetStartupTimeoutPolicy.expired(1_000.0, -1.0, 1_500.0, 100_000.0))
        assertNull(MgazeNetStartupTimeoutPolicy.expired(1_000.0, 2_000.0, 2_500.0, 100_000.0))
    }

    @Test fun regressedOrInvalidObservationClocksDoNotExpire() {
        assertNull(MgazeNetStartupTimeoutPolicy.expired(1_000.0, -1.0, -1.0, 500.0))
        assertNull(MgazeNetStartupTimeoutPolicy.expired(1_000.0, 2_000.0, -1.0, 1_500.0))
        assertNull(MgazeNetStartupTimeoutPolicy.expired(1_000.0, 2_000.0, -1.0, Double.NaN))
    }
}
