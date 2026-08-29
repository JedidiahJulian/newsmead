package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationCsvCodecTest {

    @Test
    fun readsLegacyFourColumnCalibration() {
        val sample = CalibrationCsvCodec.decode("100.0,200.0,0.4,0.6")

        assertEquals(100f, sample.screenX)
        assertEquals(0.4f, sample.gazeX)
        assertFalse(sample.hasPerEye)
    }

    @Test
    fun roundTripsPerEyeCalibration() {
        val original = CalibrationSample(
            screenX = 100f,
            screenY = 200f,
            gazeX = 0.4f,
            gazeY = 0.6f,
            eye1X = 0.35f,
            eye1Y = 0.55f,
            eye2X = 0.45f,
            eye2Y = 0.65f,
        )

        val decoded = CalibrationCsvCodec.decode(CalibrationCsvCodec.encode(original))

        assertEquals(original, decoded)
        assertTrue(decoded.hasPerEye)
    }
}
