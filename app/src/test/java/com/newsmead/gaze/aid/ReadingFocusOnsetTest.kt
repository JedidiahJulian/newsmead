package com.newsmead.gaze.aid

import org.junit.Assert.*
import org.junit.Test

class ReadingFocusOnsetTest {
    @Test fun continuousPresentationKeepsPhaseAcrossRefreshAndStopsRequestingFrames() {
        val onset = ReadingFocusOnset()
        onset.select(ReadingFocusStyle.DARK_SURROUNDINGS)
        assertEquals(0.0, onset.fraction(1000.0), 0.0)
        assertTrue(onset.isRunning(1000.0))
        assertFalse(onset.select(ReadingFocusStyle.DARK_SURROUNDINGS))
        assertEquals(0.15625, onset.fraction(1100.0), 0.0)
        assertFalse(onset.select(ReadingFocusStyle.DARK_SURROUNDINGS))
        assertEquals(0.5, onset.fraction(1200.0), 0.0)
        assertEquals(1.0, onset.fraction(1400.0), 0.0)
        assertFalse(onset.isRunning(1400.0))
        assertEquals(1.0, onset.fraction(10000.0), 0.0)
        assertFalse(onset.isRunning(10000.0))
    }

    @Test fun removalAndStyleChangeResetWhileRepeatedClearsAreIdempotent() {
        val onset = ReadingFocusOnset()
        assertFalse(onset.clear())
        onset.select(ReadingFocusStyle.DARK_SURROUNDINGS)
        onset.fraction(0.0)
        assertTrue(onset.clear())
        assertFalse(onset.clear())
        assertFalse(onset.isRunning(100.0))
        onset.select(ReadingFocusStyle.DARK_SURROUNDINGS)
        assertEquals(0.0, onset.fraction(100.0), 0.0)
        onset.select(ReadingFocusStyle.FADE_TEXT)
        assertEquals(1.0, onset.fraction(200.0), 0.0)
        assertFalse(onset.isRunning(200.0))
        onset.select(ReadingFocusStyle.DARK_SURROUNDINGS)
        assertEquals(0.0, onset.fraction(300.0), 0.0)
        assertEquals(0.5, onset.fraction(500.0), 0.0)
    }
}
