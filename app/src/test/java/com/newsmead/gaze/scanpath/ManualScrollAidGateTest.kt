package com.newsmead.gaze.scanpath

import com.newsmead.gaze.scanpath.ManualScrollAidGate.Translation.*
import org.junit.Assert.*
import org.junit.Test

class ManualScrollAidGateTest {
    @Test fun onlyExplicitGestureAuthorizesTranslations() {
        val gate = ManualScrollAidGate(150.0)
        assertEquals(UNCONTROLLED, gate.observe(0.0, true))
        gate.gesture(true, 10.0)
        assertEquals(UNCONTROLLED, gate.observe(20.0, false))
        assertEquals(FIRST, gate.observe(30.0, true))
        assertEquals(CONTINUING, gate.observe(1000.0, true)) // Finger still down.
    }

    @Test fun touchWithoutMovementCannotAuthorizePostUpJump() {
        val gate = ManualScrollAidGate(150.0)
        gate.gesture(true, 0.0)
        gate.gesture(false, 50.0)
        assertEquals(UNCONTROLLED, gate.observe(51.0, true))
    }

    @Test fun flingRequiresContinuousTranslationsAndExpiresAtStableGap() {
        val gate = ManualScrollAidGate(150.0)
        gate.gesture(true, 0.0)
        assertEquals(FIRST, gate.observe(10.0, true))
        gate.gesture(false, 20.0)
        assertEquals(CONTINUING, gate.observe(159.0, true))
        assertEquals(CONTINUING, gate.observe(200.0, true))
        gate.observe(349.0, false)
        assertEquals(UNCONTROLLED, gate.observe(350.0, true))
        assertEquals(UNCONTROLLED, gate.observe(351.0, true))
    }

    @Test fun holdingStillBeforeUpDoesNotCreateAFreshFlingLease() {
        val gate = ManualScrollAidGate(150.0)
        gate.gesture(true, 0.0)
        gate.observe(10.0, true)
        gate.gesture(false, 200.0)
        assertEquals(UNCONTROLLED, gate.observe(201.0, true))
    }

    @Test fun cancelAndExternalResetRequireNewGesture() {
        val gate = ManualScrollAidGate(150.0)
        gate.gesture(true, 0.0)
        gate.observe(10.0, true)
        gate.gesture(false, 20.0, cancelled = true)
        assertEquals(UNCONTROLLED, gate.observe(21.0, true))
        gate.gesture(true, 30.0)
        assertEquals(FIRST, gate.observe(31.0, true))
        assertTrue(gate.reset())
        assertEquals(UNCONTROLLED, gate.observe(32.0, true))
        assertFalse(gate.reset())
    }

    @Test fun incompatibleGeometryCancelsEvenWhileFingerRemainsDown() {
        val gate = ManualScrollAidGate(150.0)
        gate.gesture(true, 0.0)
        gate.observe(10.0, true)
        assertEquals(UNCONTROLLED, gate.observe(20.0, true, compatible = false))
        assertEquals(UNCONTROLLED, gate.observe(30.0, true))
        gate.gesture(false, 40.0)
        gate.gesture(true, 50.0)
        assertEquals(FIRST, gate.observe(60.0, true))
    }

    @Test fun newDownAfterUpStartsNewBoundaryEvenBeforeFlingExpires() {
        val gate = ManualScrollAidGate(150.0)
        gate.gesture(true, 0.0)
        gate.observe(10.0, true)
        gate.gesture(true, 11.0) // Duplicate DOWN is harmless.
        assertEquals(CONTINUING, gate.observe(12.0, true))
        gate.gesture(false, 20.0)
        gate.gesture(true, 30.0)
        assertEquals(FIRST, gate.observe(31.0, true))
    }

    @Test fun malformedAndBackwardTimesFailClosed() {
        val gate = ManualScrollAidGate(150.0)
        gate.gesture(true, 0.0)
        gate.observe(10.0, true)
        assertEquals(UNCONTROLLED, gate.observe(9.0, true))
        gate.gesture(true, Double.NaN)
        assertEquals(UNCONTROLLED, gate.observe(20.0, true))
    }
}
