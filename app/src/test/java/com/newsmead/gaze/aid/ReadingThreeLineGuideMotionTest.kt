package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathLine
import org.junit.Assert.*
import org.junit.Test

class ReadingThreeLineGuideMotionTest {
    private val rows = List(8) { i -> ScanpathLine(i * 2, i * 2 + 2,
        0.0, 100.0, i * 20.0, (i + 1) * 20.0) }
    private fun target(line: Int, above: Double = .04, below: Double = .04) =
        ReadingThreeLineGuideMotion.target(rows, line, above, .08, below)!!

    @Test fun repeatedCallbacksDoNotRestartPositionOrDirectionTransition() {
        val motion = ReadingThreeLineGuideMotion()
        motion.move(target(1), 0.0)
        motion.move(target(3, .03, .05), 10.0)
        val midway = motion.move(target(3, .03, .05), 135.0)
        assertEquals(40.0, midway.centreTop, 1e-9)
        assertEquals(.035, midway.aboveOpacity, 1e-9)
        assertEquals(.045, midway.belowOpacity, 1e-9)
        assertEquals(target(3, .03, .05), motion.move(target(3, .03, .05), 260.0))
        assertFalse(motion.isMoving(260.0))
    }

    @Test fun retargetStartsAtCurrentInterpolatedStateWithoutJump() {
        val motion = ReadingThreeLineGuideMotion()
        motion.move(target(1), 0.0)
        motion.move(target(5, .03, .05), 10.0)
        val before = motion.move(target(5, .03, .05), 135.0)
        val after = motion.move(target(2, .05, .03), 135.0)
        assertEquals(before, after)
        assertEquals(target(2, .05, .03), motion.move(target(2, .05, .03), 385.0))
    }

    @Test fun layoutOrArticleResetDiscardsStalePositionAndOpacity() {
        val motion = ReadingThreeLineGuideMotion()
        motion.move(target(1), 0.0)
        motion.move(target(6), 10.0)
        motion.clear()
        assertEquals(target(2, .05, .03), motion.move(target(2, .05, .03), 100.0))
        assertFalse(motion.isMoving(100.0))
    }

    @Test fun bodyCoordinatesTranslateExactlyWithoutRetargeting() {
        val motion = ReadingThreeLineGuideMotion()
        val band = motion.move(target(3), 0.0)
        val beforeScreenTop = 120.0 + band.top
        val after = motion.move(target(3), 100.0)
        assertEquals(band, after)
        assertEquals(-37.0, (83.0 + after.top) - beforeScreenTop, 1e-9)
        assertFalse(motion.isMoving(100.0))
    }

    @Test fun documentEdgesUseOnlyExistingRowsAndActualRowMetrics() {
        val first = target(0)
        assertEquals(first.top, first.centreTop, 0.0)
        assertEquals(0.0, first.aboveOpacity, 0.0)
        val last = target(rows.lastIndex)
        assertEquals(last.bottom, last.centreBottom, 0.0)
        assertEquals(0.0, last.belowOpacity, 0.0)
        val unequal = listOf(rows[0].copy(top = 0.0, bottom = 17.0),
            rows[1].copy(top = 17.0, bottom = 49.0), rows[2].copy(top = 49.0, bottom = 70.0))
        val actual = ReadingThreeLineGuideMotion.target(unequal, 1, .03, .08, .05)!!
        assertEquals(listOf(0.0, 17.0, 49.0, 70.0),
            listOf(actual.top, actual.centreTop, actual.centreBottom, actual.bottom))
        val one = ReadingThreeLineGuideMotion.target(unequal.take(1), 0, .05, .08, .03)!!
        assertEquals(0.0, one.aboveOpacity, 0.0)
        assertEquals(0.0, one.belowOpacity, 0.0)
    }

    @Test fun currentEntryLossAndReducedReaderCapImmediatelyScaleAllMovingAlphas() {
        val motion = ReadingThreeLineGuideMotion()
        motion.move(target(1, .03, .05), 0.0)
        motion.move(target(5, .05, .03), 10.0)
        val midway = motion.move(target(5, .05, .03), 135.0)
        val reduced = midway.capped(.02)
        assertEquals(.02, reduced.centreOpacity, 1e-9)
        assertEquals(midway.aboveOpacity / 4, reduced.aboveOpacity, 1e-9)
        assertEquals(midway.belowOpacity / 4, reduced.belowOpacity, 1e-9)
        val zero = midway.capped(0.0)
        assertEquals(0.0, zero.centreOpacity + zero.aboveOpacity + zero.belowOpacity, 0.0)
        assertEquals(0.0, midway.capped(Double.NaN).centreOpacity, 0.0)
    }

    @Test fun malformedTargetsWithholdAndDefensiveCapsBoundAppearance() {
        assertNull(ReadingThreeLineGuideMotion.target(rows, -1, .03, .08, .05))
        assertNull(ReadingThreeLineGuideMotion.target(rows, rows.size, .03, .08, .05))
        assertNull(ReadingThreeLineGuideMotion.target(rows, 0, Double.NaN, .08, .05))
        assertNull(ReadingThreeLineGuideMotion.target(listOf(rows[0].copy(bottom = 0.0)), 0, .03, .08, .05))
        val capped = ReadingThreeLineGuideMotion.target(rows, 2, 1.0, 1.0, 1.0)!!
        assertEquals(.09, capped.centreOpacity, 0.0)
        assertEquals(.09, capped.aboveOpacity, 0.0)
        assertEquals(.09, capped.belowOpacity, 0.0)
    }

    @Test fun directionalBandPositionsGazeRowAndInterpolatesSettlingWithoutJumping() {
        fun directional(line: Int, direction: ReadingGuideDirection) =
            ReadingThreeLineGuideMotion.target(rows, line, .09, .09, .09, direction)!!
        val down = directional(3, ReadingGuideDirection.DOWN)
        val settled = directional(3, ReadingGuideDirection.BALANCED)
        val up = directional(3, ReadingGuideDirection.UP)
        assertEquals(listOf(60.0, 60.0, 80.0, 120.0), down.bounds())
        assertEquals(listOf(40.0, 60.0, 80.0, 100.0), settled.bounds())
        assertEquals(listOf(20.0, 60.0, 80.0, 80.0), up.bounds())
        val motion = ReadingThreeLineGuideMotion()
        motion.move(down, 0.0)
        assertEquals(down, motion.move(settled, 100.0))
        val halfway = motion.move(settled, 225.0)
        assertEquals(50.0, halfway.top, 0.0)
        assertEquals(110.0, halfway.bottom, 0.0)
        assertEquals(60.0, halfway.bottom - halfway.top, 0.0)
        assertEquals(settled, motion.move(settled, 350.0))
        assertEquals(0.0, directional(0, ReadingGuideDirection.UP).top, 0.0)
        assertEquals(20.0, directional(0, ReadingGuideDirection.UP).bottom, 0.0)
        assertEquals(140.0, directional(7, ReadingGuideDirection.DOWN).top, 0.0)
        assertEquals(160.0, directional(7, ReadingGuideDirection.DOWN).bottom, 0.0)
    }
    @Test fun freshSingleLineOpacityUpdatesDoNotRestartPositionAndHonorSearchTarget() {
        val motion = ReadingThreeLineGuideMotion()
        fun single(row: Int, alpha: Double) = ReadingThreeLineGuideMotion.singleLineTarget(rows, row, alpha)!!
        motion.move(single(1, .08), 0.0, animateOpacity = false)
        motion.move(single(5, .08), 10.0, animateOpacity = false)
        for (now in listOf(35.0, 60.0, 85.0, 110.0, 135.0, 160.0, 185.0, 210.0, 235.0)) {
            val alpha = .08 + (now - 10.0) / 250.0 * .11
            val band = motion.move(single(5, alpha), now, animateOpacity = false)
            val t = (now - 10.0) / 250.0
            val eased = t * t * (3 - 2 * t)
            assertEquals(20.0 + 80.0 * eased, band.centreTop, 1e-9)
            assertEquals(alpha, band.centreOpacity, 1e-9)
            assertEquals(band.top, band.centreTop, 0.0)
            assertEquals(band.bottom, band.centreBottom, 0.0)
            assertEquals(20.0, band.bottom - band.top, 1e-9)
        }
        val final = motion.move(single(5, .19), 260.0, animateOpacity = false)
        assertEquals(single(5, .19), final)
        assertFalse(motion.isMoving(260.0))
        assertEquals(.19, single(5, 1.0).centreOpacity, 0.0)
        assertEquals(.03, final.capped(.03).centreOpacity, 1e-9)
    }

    @Test fun alphaOnlyDirectionalRetargetDoesNotDelayArrivalAtTargetRow() {
        val motion = ReadingThreeLineGuideMotion()
        motion.move(target(1), 0.0)
        motion.move(target(5), 10.0)
        motion.move(target(5, .05, .03), 135.0)
        val arrived = motion.move(target(5, .05, .03), 260.0)
        assertEquals(target(5).bounds(), arrived.bounds())
        assertTrue("Opacity may still finish independently", motion.isMoving(260.0))
        assertEquals(target(5, .05, .03), motion.move(target(5, .05, .03), 385.0))
    }

    @Test fun singleLineRejectsMissingInvalidAndDegenerateRows() {
        assertNull(ReadingThreeLineGuideMotion.singleLineTarget(rows, -1, .13))
        assertNull(ReadingThreeLineGuideMotion.singleLineTarget(rows, rows.size, .13))
        assertNull(ReadingThreeLineGuideMotion.singleLineTarget(rows, 1, Double.NaN))
        assertNull(ReadingThreeLineGuideMotion.singleLineTarget(listOf(rows[0].copy(top = Double.NaN)), 0, .13))
        assertNull(ReadingThreeLineGuideMotion.singleLineTarget(listOf(rows[0].copy(bottom = 0.0)), 0, .13))
    }

}
