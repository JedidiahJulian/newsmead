package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MgazeNetSingleFlightGateTest {
    @Test fun admitsOnlyOneFrameUntilOwnerReleases() {
        val gate = MgazeNetSingleFlightGate()
        assertTrue(gate.tryAcquire())
        assertTrue(gate.isHeld())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertFalse(gate.isHeld())
        assertTrue(gate.tryAcquire())
        gate.release()
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsReleaseWithoutOwnership() {
        MgazeNetSingleFlightGate().release()
    }
}
