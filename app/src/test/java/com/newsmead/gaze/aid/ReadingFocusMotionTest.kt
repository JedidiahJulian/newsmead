package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathLine
import com.newsmead.gaze.scanpath.ScanpathRect
import org.junit.Assert.*
import org.junit.Test

class ReadingFocusMotionTest {
    private fun band(top: Double, bottom: Double) = ReadingFocusMotion.Band(top, bottom)

    @Test fun heldBandStaysInScreenSpaceAndOnlyFitsChangedViewport() {
        val held = band(100.0, 200.0)
        assertEquals(held, ReadingFocusMotion.fitPinnedBand(held, 0.0, 300.0))
        assertEquals(band(120.0, 220.0), ReadingFocusMotion.fitPinnedBand(held, 120.0, 300.0))
        assertEquals(band(80.0, 180.0), ReadingFocusMotion.fitPinnedBand(held, 0.0, 180.0))
        assertEquals(band(120.0, 180.0), ReadingFocusMotion.fitPinnedBand(held, 120.0, 180.0))
    }

    @Test fun clearingEmptyMotionIsIdempotent() {
        val motion = ReadingFocusMotion()
        assertFalse(motion.clear())
        motion.pin(band(20.0, 120.0))
        assertTrue(motion.clear())
        assertFalse(motion.clear())
    }

    @Test fun largeJumpImmediatelyUncoversFreshAnchorThenContractsSmoothly() {
        val motion = ReadingFocusMotion()
        motion.move(band(20.0, 120.0), band(60.0, 80.0), 0.0)
        val start = motion.move(band(180.0, 280.0), band(220.0, 240.0), 10.0)
        assertEquals(band(20.0, 240.0), start)
        val midway = motion.move(band(180.0, 280.0), band(220.0, 240.0), 100.0)
        assertEquals(band(100.0, 260.0), midway)
        assertTrue(midway.top <= 220.0 && midway.bottom >= 240.0)
        assertEquals(band(180.0, 280.0), motion.move(band(180.0, 280.0), band(220.0, 240.0), 190.0))
        assertFalse(motion.isMoving(190.0))
    }

    @Test fun repeatedSameBoundsDoNotRestartTransitionAndPinnedHoldIsStable() {
        val motion = ReadingFocusMotion()
        motion.pin(band(0.0, 100.0))
        motion.move(band(40.0, 140.0), band(60.0, 80.0), 0.0)
        motion.move(band(40.0, 140.0), band(60.0, 80.0), 90.0)
        assertEquals(band(40.0, 140.0), motion.move(band(40.0, 140.0), band(60.0, 80.0), 180.0))
        motion.pin(band(20.0, 120.0))
        assertEquals(band(20.0, 120.0), motion.move(band(20.0, 120.0), band(40.0, 60.0), 500.0))
    }

    @Test fun translationAllowsVerticalClippingButRejectsReflowOrHorizontalChanges() {
        val g = ScanpathGeometry("g1", 20.0, 100.0,
            ScanpathRect(20.0, 100.0, 220.0, 300.0), 20.0,
            List(10) { i -> ScanpathLine(i * 2, i * 2 + 2, 0.0, 200.0, i * 20.0, (i + 1) * 20.0) })
        val next = g.copy(snapshotId = "g2", textOriginY = 80.0,
            visibleRectScreen = g.visibleRectScreen.copy(bottom = 280.0))
        assertTrue(ReadingFocusMotion.compatibleTranslation(g, next))
        assertTrue(ReadingFocusMotion.translated(g, next))
        assertFalse(ReadingFocusMotion.translated(g, g.copy(snapshotId = "tick")))
        assertFalse(ReadingFocusMotion.compatibleTranslation(g, next.copy(textOriginX = 21.0)))
        assertFalse(ReadingFocusMotion.compatibleTranslation(g, next.copy(lineHeightPx = 21.0)))
        assertFalse(ReadingFocusMotion.compatibleTranslation(g, next.copy(lines = next.lines.mapIndexed { i, row ->
            if (i == 0) row.copy(endUtf16 = 1) else row
        })))
        assertFalse(ReadingFocusMotion.compatibleTranslation(g, next.copy(textOriginY = Double.NaN)))
    }
}
