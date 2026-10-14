package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathLine
import com.newsmead.gaze.scanpath.ScanpathRect
import org.junit.Assert.*
import org.junit.Test

class ReadingFocusAppearanceTest {
    @Test fun backgroundMaskMakesOutsideTextFaintAtBothStrengthsAndFadesContinuously() {
        val g = geometry()
        fun mask(strength: Double, style: ReadingFocusStyle = ReadingFocusStyle.FADE_TEXT) = ReadingFocusAppearance.resolve(g, g.visibleRectScreen,
            4, 2, strength, limitContextHeight = false, keepLineCount = true,
            occludeSurroundings = true, focusStyle = style)
        assertEquals(230, mask(0.5).appearance!!.alpha)
        assertEquals(250, mask(1.0).appearance!!.alpha)
        assertEquals(115, mask(0.25).appearance!!.alpha)
        assertEquals(mask(0.5).appearance!!.clearTop, mask(1.0).appearance!!.clearTop, 0.0)
        assertEquals(mask(0.5).appearance!!.clearBottom, mask(1.0).appearance!!.clearBottom, 0.0)
        assertEquals("focus_alpha_zero", mask(0.0).withheldReason)
        assertEquals("focus_alpha_zero", mask(Double.NaN).withheldReason)
        assertEquals(102, mask(0.5, ReadingFocusStyle.DARK_SURROUNDINGS).appearance!!.alpha)
        assertEquals(153, mask(1.0, ReadingFocusStyle.DARK_SURROUNDINGS).appearance!!.alpha)
        assertEquals(51, mask(0.25, ReadingFocusStyle.DARK_SURROUNDINGS).appearance!!.alpha)
        assertEquals(mask(0.5).appearance!!.clearTop,
            mask(0.5, ReadingFocusStyle.DARK_SURROUNDINGS).appearance!!.clearTop, 0.0)
        assertEquals(mask(0.5).appearance!!.clearBottom,
            mask(0.5, ReadingFocusStyle.DARK_SURROUNDINGS).appearance!!.clearBottom, 0.0)
    }

    @Test fun currentProfileKeepsFiveLinesEvenAboveTheFormerViewportBudget() {
        assertEquals(2, ReadingAidPolicyConfig.definitionPrototype().focusContextRadiusLines)
        val g = geometry()
        val a = ReadingFocusAppearance.resolve(g, ScanpathRect(0.0, 100.0, 260.0, 240.0),
            3, 2, 0.5, limitContextHeight = false, keepLineCount = true).appearance!!
        assertEquals(120.0, a.clearTop, 0.0)
        assertEquals(220.0, a.clearBottom, 0.0)
        assertEquals(5 * g.lineHeightPx, a.clearBottom - a.clearTop, 0.0)
        assertEquals(0.0, a.viewport.left, 0.0)
        assertEquals(260.0, a.viewport.right, 0.0)
        assertEquals(10400.0, a.shadedAreaPx2, 0.0)
    }

    @Test fun fiveLineBandSlidesAtArticleAndVisibleViewportEdges() {
        val g = geometry()
        for ((anchor, viewport, expectedTop) in listOf(
            Triple(0, g.visibleRectScreen, 100.0),
            Triple(9, g.visibleRectScreen, 200.0),
            Triple(3, ScanpathRect(0.0, 160.0, 260.0, 300.0), 160.0),
            Triple(9, ScanpathRect(0.0, 160.0, 260.0, 300.0), 200.0))) {
            val a = ReadingFocusAppearance.resolve(g, viewport, anchor, 2, 1.0,
                limitContextHeight = false, keepLineCount = true).appearance!!
            assertEquals(expectedTop, a.clearTop, 0.0)
            assertEquals(100.0, a.clearBottom - a.clearTop, 0.0)
            assertTrue(a.clearTop <= g.textOriginY + g.lines[anchor].top)
            assertTrue(a.clearBottom >= g.textOriginY + g.lines[anchor].bottom)
        }
    }

    @Test fun fourVisibleRowsStayClearInsteadOfShrinkingToThreeForShade() {
        val g = geometry()
        val a = ReadingFocusAppearance.resolve(g, ScanpathRect(0.0, 100.0, 260.0, 180.0),
            1, 2, 1.0, limitContextHeight = false, keepLineCount = true)
        assertNull(a.appearance)
        assertEquals("focus_no_dimmed_area", a.withheldReason)
    }

    @Test fun fiveVisibleRowsAllowFourClearRowsToKeepSurroundingShade() {
        val g = geometry()
        val a = ReadingFocusAppearance.resolve(g, ScanpathRect(0.0, 100.0, 260.0, 200.0),
            2, 2, 1.0, limitContextHeight = false, keepLineCount = true).appearance!!
        assertEquals(80.0, a.clearBottom - a.clearTop, 0.0)
        assertTrue(a.clearTop <= 140.0 && a.clearBottom >= 160.0)
        assertEquals(5200.0, a.shadedAreaPx2, 0.0)
    }

    private fun geometry(count: Int = 10, originY: Double = 100.0) = ScanpathGeometry(
        "g", 30.0, originY, ScanpathRect(30.0, originY, 230.0, originY + count * 20), 20.0,
        List(count) { i -> ScanpathLine(i, i + 1, 0.0, 200.0, i * 20.0, (i + 1) * 20.0) })

    private fun appearance(g: ScanpathGeometry, viewport: ScanpathRect, line: Int, radius: Int = 2) =
        ReadingFocusAppearance.resolve(g, viewport, line, radius, 0.5).appearance!!

    @Test fun shortViewportShrinksContextAndLeavesPositiveShade() {
        val a = appearance(geometry(), ScanpathRect(30.0, 100.0, 230.0, 200.0), 2)
        assertEquals(1, a.contextRadiusLines)
        assertEquals(120.0, a.clearTop, 0.0)
        assertEquals(180.0, a.clearBottom, 0.0)
        assertEquals(8000.0, a.shadedAreaPx2, 0.0)
        assertEquals(20, a.alpha)
    }

    @Test fun preferredContextIsRetainedWhenItFitsAndStrengthDoesNotChangeGeometry() {
        val g = geometry()
        val viewport = g.visibleRectScreen
        val a = appearance(g, viewport, 4)
        assertEquals(2, a.contextRadiusLines)
        assertEquals(140.0, a.clearTop, 0.0)
        assertEquals(240.0, a.clearBottom, 0.0)
        val strong = ReadingFocusAppearance.resolve(g, viewport, 4, 2, 1.0).appearance!!
        assertEquals(a.clearTop, strong.clearTop, 0.0)
        assertEquals(a.clearBottom, strong.clearBottom, 0.0)
        assertEquals(40, strong.alpha)
    }

    @Test fun partiallyVisibleAnchorAtEitherViewportEdgeStaysEntirelyClear() {
        val g = geometry()
        val viewport = ScanpathRect(30.0, 150.0, 230.0, 230.0)
        val top = appearance(g, viewport, 2)
        assertEquals(150.0, top.clearTop, 0.0)
        assertTrue(top.clearBottom >= 160.0)
        val bottom = appearance(g, viewport, 6)
        assertEquals(230.0, bottom.clearBottom, 0.0)
        assertTrue(bottom.clearTop <= 220.0)
        assertTrue(top.shadedAreaPx2 > 0)
        assertTrue(bottom.shadedAreaPx2 > 0)
    }

    @Test fun oversizedAnchorOverridesBudgetButKeepsSurroundingsDimmed() {
        val g = geometry(1).copy(lines = listOf(ScanpathLine(0, 1, 0.0, 200.0, 10.0, 90.0)))
        val a = appearance(g, ScanpathRect(30.0, 100.0, 230.0, 200.0), 0)
        assertEquals(0, a.contextRadiusLines)
        assertEquals(110.0, a.clearTop, 0.0)
        assertEquals(190.0, a.clearBottom, 0.0)
        assertEquals(4000.0, a.shadedAreaPx2, 0.0)
    }

    @Test fun emptySurroundingsAndInvisibleAlphaAreExplicitlyWithheld() {
        val g = geometry(1)
        assertEquals("focus_no_dimmed_area", ReadingFocusAppearance.resolve(
            g, g.visibleRectScreen, 0, 2, 1.0).withheldReason)
        for (strength in listOf(0.0, 0.01, Double.NaN)) {
            val result = ReadingFocusAppearance.resolve(geometry(), geometry().visibleRectScreen, 2, 1, strength)
            assertNull(result.appearance)
            assertEquals("focus_alpha_zero", result.withheldReason)
        }
    }

    @Test fun invalidEmptyAndOffscreenGeometryCannotReportShade() {
        val g = geometry()
        assertEquals("anchor_not_visible", ReadingFocusAppearance.resolve(
            g, ScanpathRect(30.0, 100.0, 230.0, 120.0), 2, 1, 1.0).withheldReason)
        assertEquals("line_unavailable", ReadingFocusAppearance.resolve(
            g.copy(lines = emptyList()), g.visibleRectScreen, 0, 1, 1.0).withheldReason)
        assertEquals("focus_geometry_unavailable", ReadingFocusAppearance.resolve(
            g, ScanpathRect(30.0, 100.0, 30.0, 200.0), 2, 1, 1.0).withheldReason)
    }

    @Test fun negativeRadiusAndLargeRadiusAreBoundedByAvailableRows() {
        val g = geometry()
        assertEquals(0, appearance(g, g.visibleRectScreen, 2, -1).contextRadiusLines)
        val a = appearance(g, g.visibleRectScreen, 0, Int.MAX_VALUE)
        assertTrue(a.clearBottom - a.clearTop <= 120.0)
        assertTrue(a.clearTop <= 100.0 && a.clearBottom >= 120.0)
    }

    @Test fun legacyContextKeepsPreferredRadiusButCannotClaimAnEmptyDraw() {
        val g = geometry()
        val viewport = ScanpathRect(30.0, 100.0, 230.0, 220.0)
        val legacy = ReadingFocusAppearance.resolve(g, viewport, 2, 2, 1.0,
            limitContextHeight = false).appearance!!
        assertEquals(2, legacy.contextRadiusLines)
        assertEquals(4000.0, legacy.shadedAreaPx2, 0.0)
        assertEquals("focus_no_dimmed_area", ReadingFocusAppearance.resolve(g,
            ScanpathRect(30.0, 100.0, 230.0, 200.0), 2, 2, 1.0,
            limitContextHeight = false).withheldReason)
    }
}
