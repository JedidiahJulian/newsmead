package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test

class GazeGeometryTest {
    private fun points() = MutableList(478) { GazeGeometry.Landmark(.5, .5) }.apply {
        this[0] = GazeGeometry.Landmark(.2, .2); this[1] = GazeGeometry.Landmark(.8, .8)
        this[33] = GazeGeometry.Landmark(.3, .4); this[133] = GazeGeometry.Landmark(.4, .4)
        this[362] = GazeGeometry.Landmark(.6, .4); this[263] = GazeGeometry.Landmark(.7, .4)
    }
    @Test fun cropCoordinatesPreserveUpstreamTensorIdentity() {
        val c = GazeGeometry.crops(points(), 100, 100)!!
        assertEquals(GazeGeometry.Box(20,20,60,60), c.face)
        assertEquals(GazeGeometry.Box(26,31,18,14), c.left)
        assertEquals(GazeGeometry.Box(56,31,18,14), c.right)
    }
    @Test fun primitiveCoordinatePathMatchesLandmarkPath() {
        val points = points()
        val x = DoubleArray(points.size) { points[it].x }
        val y = DoubleArray(points.size) { points[it].y }
        assertEquals(GazeGeometry.crops(points,100,100), GazeGeometry.crops(x, y, 100, 100))
        val px = DoubleArray(points.size) { 9999.0 }
        val py = DoubleArray(points.size) { -9999.0 }
        assertEquals(GazeGeometry.crops(points,100,100), GazeGeometry.crops(x, y, 100, 100, px, py))
        val shifted = points().apply { this[33] = GazeGeometry.Landmark(.31, .41) }
        val sx = DoubleArray(shifted.size) { shifted[it].x }
        val sy = DoubleArray(shifted.size) { shifted[it].y }
        assertEquals(GazeGeometry.crops(shifted,100,100), GazeGeometry.crops(sx, sy, 100, 100, px, py))
    }
    @Test fun halfPixelUsesEvenRounding() {
        val p = points(); p[33] = GazeGeometry.Landmark(.305, .4)
        assertEquals(GazeGeometry.crops(points(), 100, 100), GazeGeometry.crops(p, 100, 100))
    }
    @Test fun malformedLandmarksAreRejected() {
        assertNull(GazeGeometry.crops(points().take(467), 100, 100))
        assertNull(GazeGeometry.crops(points() + GazeGeometry.Landmark(.5, .5), 100, 100))
        assertNull(GazeGeometry.crops(points(), 0, 100))
        val p = points(); p[0] = GazeGeometry.Landmark(Double.NaN, .2)
        assertNull(GazeGeometry.crops(p, 100, 100))
        p[0] = GazeGeometry.Landmark(1e9, .2)
        assertNull(GazeGeometry.crops(p, 100, 100))
    }
    @Test fun compact468LandmarksMatch478WhenIrisIndicesAreInsideFaceBounds() {
        val full = points()
        val compact = full.take(468)
        assertEquals(GazeGeometry.crops(full, 100, 100), GazeGeometry.crops(compact, 100, 100))
        val fullX = DoubleArray(478) { full[it].x }
        val fullY = DoubleArray(478) { full[it].y }
        val compactX = fullX.copyOf(468)
        val compactY = fullY.copyOf(468)
        assertEquals(
            GazeGeometry.crops(fullX, fullY, 100, 100),
            GazeGeometry.crops(compactX, compactY, 100, 100, DoubleArray(468), DoubleArray(468)),
        )
    }
    @Test fun eyesTouchingBorderAreRejected() {
        val p = points(); p[33] = GazeGeometry.Landmark(.04, .4)
        assertNull(GazeGeometry.crops(p, 100, 100))
    }
    @Test fun lipsBelowFrameAreRejected() {
        val p = points()
        listOf(61,91,14,178,402,324,95).forEach { p[it] = GazeGeometry.Landmark(.5,1.0) }
        assertNull(GazeGeometry.crops(p, 100, 100))
    }
    @Test fun clippingUsesHalfOpenBoundsWithoutPadding() {
        assertEquals(GazeGeometry.Box(3,2,14,11), GazeGeometry.clip(GazeGeometry.Box(3,2,20,20),17,13))
        assertNull(GazeGeometry.clip(GazeGeometry.Box(-1,2,20,20),17,13))
        assertNull(GazeGeometry.clip(GazeGeometry.Box(17,2,1,1),17,13))
        assertNull(GazeGeometry.clip(GazeGeometry.Box(1,2,0,1),17,13))
    }
    @Test fun rectangleNormalizationKeepsOriginalUnclippedBox() {
        val c = GazeGeometry.Crops(GazeGeometry.Box(3,2,20,20),GazeGeometry.Box(1,1,8,4),GazeGeometry.Box(12,8,9,8),0.0,0.0)
        assertArrayEquals(floatArrayOf(20/17f,20/13f,3/17f,2/13f,8/17f,4/13f,1/17f,1/13f,9/17f,8/13f,12/17f,8/13f), c.rectangles(17,13), 1e-7f)
    }
}
