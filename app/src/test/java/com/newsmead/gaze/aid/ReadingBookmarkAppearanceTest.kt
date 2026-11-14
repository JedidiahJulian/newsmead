package com.newsmead.gaze.aid

import org.junit.Assert.*
import org.junit.Test

class ReadingBookmarkAppearanceTest {
    @Test fun opacityRespectsStrengthAndRejectsNonFiniteValues() {
        assertEquals(191, LineStartMarkerAppearance.bookmarkAlpha(LineStartMarkerAppearance.BOOKMARK_DEFAULT_STRENGTH))
        assertEquals(127, LineStartMarkerAppearance.bookmarkAlpha(.5))
        assertEquals(25, LineStartMarkerAppearance.bookmarkAlpha(.1))
        assertEquals(0, LineStartMarkerAppearance.bookmarkAlpha(0.0))
        assertEquals(0, LineStartMarkerAppearance.bookmarkAlpha(Double.NaN))
        assertEquals(255, LineStartMarkerAppearance.bookmarkAlpha(2.0))
        assertEquals(0, LineStartMarkerAppearance.bookmarkAlpha(-1.0))
        assertEquals(0.40, LineStartMarkerAppearance.BOOKMARK_DEFAULT_STRENGTH *
            LineStartMarkerAppearance.bookmarkProgressFactor(1.0), 0.00001)
        assertTrue(LineStartMarkerAppearance.bookmarkProgressFactor(.5) >
            LineStartMarkerAppearance.bookmarkProgressFactor(1.0))
        val motion = ReadingBookmarkOpacityMotion()
        assertEquals(.75, motion.move("cue", .75, 0.0), 0.0)
        assertEquals(.75, motion.move("cue", .40, 100.0), 0.0)
        assertTrue(motion.isAnimating(200.0))
        val intermediate = motion.move("cue", .40, 200.0)
        assertTrue(intermediate in .40.. .75)
        assertEquals(.40, motion.move("cue", .40, 10000.0), 0.00001)
        assertEquals(.75, motion.move("new_cue", .75, 10100.0), 0.0)
    }
}
