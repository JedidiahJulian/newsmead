package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CompactFaceLocalizerGeometryTest {
    @Test fun affineForwardAndProjectLandmarksInvertAcrossRotationsAndScales() {
        val imageWidth = 480
        val imageHeight = 640
        val testPointsPx = listOf(
            240.0 to 320.0,
            190.0 to 270.0,
            290.0 to 285.0,
            225.0 to 385.0,
            175.0 to 310.0,
        )
        for (angle in listOf(-45f, 0f, 30f, 85f)) {
            val crop = CompactFaceLocalizerGeometry.FaceCrop(
                centerX = 235f,
                centerY = 315f,
                sidePx = 240f,
                rotationDegrees = angle,
            )
            val m = CompactFaceLocalizerGeometry.affineMatrixValues(crop)
            val raw = FloatArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_FLOATS)
            for (i in 0 until CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT) {
                val (px, py) = testPointsPx[i % testPointsPx.size]
                val qx = m[0] * px + m[1] * py + m[2]
                val qy = m[3] * px + m[4] * py + m[5]
                raw[i * 3] = qx.toFloat()
                raw[i * 3 + 1] = qy.toFloat()
                raw[i * 3 + 2] = 0f
            }
            val outX = DoubleArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT)
            val outY = DoubleArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT)
            assertTrue(
                CompactFaceLocalizerGeometry.projectLandmarks(
                    raw, crop, imageWidth, imageHeight, outX, outY,
                ),
            )
            for (i in 0 until CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT) {
                val (expectedPx, expectedPy) = testPointsPx[i % testPointsPx.size]
                assertEquals("angle=$angle i=$i px", expectedPx, outX[i] * imageWidth, 1e-3)
                assertEquals("angle=$angle i=$i py", expectedPy, outY[i] * imageHeight, 1e-3)
            }
        }
    }

    @Test fun faceCropFromDetectionOrdersEyesAndRejectsDegenerateInputs() {
        val crop = CompactFaceLocalizerGeometry.faceCropFromDetection(
            rightEyeX = 180f,
            rightEyeY = 210f,
            leftEyeX = 280f,
            leftEyeY = 260f,
            boxLeft = 140f,
            boxTop = 160f,
            boxRight = 320f,
            boxBottom = 380f,
        )!!
        assertEquals(230f, crop.centerX, 1e-5f)
        assertEquals(270f, crop.centerY, 1e-5f)
        assertEquals(220f * 1.5f, crop.sidePx, 1e-5f)
        val expectedAngle = Math.toDegrees(kotlin.math.atan2(50.0, 100.0)).toFloat()
        assertEquals(expectedAngle, crop.rotationDegrees, 1e-4f)

        // Swapping right/left arguments must preserve orientation instead of flipping 180 degrees.
        val swapped = CompactFaceLocalizerGeometry.faceCropFromDetection(
            rightEyeX = 280f,
            rightEyeY = 260f,
            leftEyeX = 180f,
            leftEyeY = 210f,
            boxLeft = 140f,
            boxTop = 160f,
            boxRight = 320f,
            boxBottom = 380f,
        )!!
        assertEquals(crop.rotationDegrees, swapped.rotationDegrees, 1e-4f)

        // Reject degenerate/non-finite inputs.
        assertNull(CompactFaceLocalizerGeometry.faceCropFromDetection(Float.NaN, 210f, 280f, 260f, 140f, 160f, 320f, 380f))
        assertNull(CompactFaceLocalizerGeometry.faceCropFromDetection(180f, 210f, 183f, 212f, 140f, 160f, 320f, 380f))
        assertNull(CompactFaceLocalizerGeometry.faceCropFromDetection(180f, 210f, 280f, 260f, 320f, 160f, 320f, 380f))
        assertNull(CompactFaceLocalizerGeometry.faceCropFromDetection(180f, 210f, 280f, 260f, 140f, 380f, 320f, 380f))
    }

    @Test fun faceCropFromLandmarksRecoversCenterSpanRollAndRejectsInvalid() {
        val w = 480
        val h = 640
        for (angleDeg in listOf(-25.0, 0.0, 30.0)) {
            val rad = Math.toRadians(angleDeg)
            val c = cos(rad)
            val s = sin(rad)
            val cx = 250.0
            val cy = 310.0
            fun normPoint(localX: Double, localY: Double): Pair<Double, Double> {
                val px = cx + c * localX - s * localY
                val py = cy + s * localX + c * localY
                return (px / w) to (py / h)
            }
            val nx = DoubleArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT) { cx / w }
            val ny = DoubleArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT) { cy / h }
            // Symmetric bounding points in the face-aligned frame: width 160, height 200.
            listOf(
                0 to (-80.0 to 0.0),
                1 to (80.0 to 0.0),
                2 to (0.0 to -100.0),
                3 to (0.0 to 100.0),
                CompactFaceLocalizerGeometry.RIGHT_EYE_OUTER_CORNER to (-50.0 to -30.0),
                CompactFaceLocalizerGeometry.LEFT_EYE_OUTER_CORNER to (50.0 to -30.0),
            ).forEach { (idx, pt) ->
                val (x, y) = normPoint(pt.first, pt.second)
                nx[idx] = x
                ny[idx] = y
            }
            val crop = CompactFaceLocalizerGeometry.faceCropFromLandmarks(nx, ny, w, h)!!
            assertEquals("angle=$angleDeg cx", cx.toFloat(), crop.centerX, 1e-2f)
            assertEquals("angle=$angleDeg cy", cy.toFloat(), crop.centerY, 1e-2f)
            assertEquals("angle=$angleDeg side", 200f * 1.5f, crop.sidePx, 1e-2f)
            assertEquals("angle=$angleDeg roll", angleDeg.toFloat(), crop.rotationDegrees, 1e-2f)

            // Reject invalid variants derived from this face.
            assertNull(CompactFaceLocalizerGeometry.faceCropFromLandmarks(nx.copyOf(467), ny.copyOf(467), w, h))
            assertNull(CompactFaceLocalizerGeometry.faceCropFromLandmarks(
                nx.copyOf().also { it[10] = Double.NaN }, ny, w, h,
            ))
            // Collapsed eyes.
            assertNull(CompactFaceLocalizerGeometry.faceCropFromLandmarks(
                nx.copyOf().also { it[263] = it[33] },
                ny.copyOf().also { it[263] = it[33] },
                w,
                h,
            ))
            // Off-screen center (> 1.1 * width).
            assertNull(CompactFaceLocalizerGeometry.faceCropFromLandmarks(
                DoubleArray(468) { nx[it] + 1.0 }, ny, w, h,
            ))
        }

        // Tiny tracked side (< 24 px) rejected.
        val tinyX = DoubleArray(468) { 0.5 }
        val tinyY = DoubleArray(468) { 0.5 }
        tinyX[33] = (240.0 - 5.0) / w
        tinyX[263] = (240.0 + 5.0) / w
        assertNull(CompactFaceLocalizerGeometry.faceCropFromLandmarks(tinyX, tinyY, w, h))

        // Oversized span (> 2.5 * maxImageSide) rejected.
        val hugeX = DoubleArray(468) { 0.5 }
        val hugeY = DoubleArray(468) { 0.5 }
        hugeX[33] = 0.2
        hugeX[263] = 0.8
        hugeY[0] = -2.0
        hugeY[1] = 2.0
        assertNull(CompactFaceLocalizerGeometry.faceCropFromLandmarks(hugeX, hugeY, w, h))
    }

    @Test fun sigmoidMapsLogitsAndRejectsNonFinite() {
        assertEquals(0.5f, CompactFaceLocalizerGeometry.sigmoid(0f), 1e-6f)
        assertTrue(CompactFaceLocalizerGeometry.sigmoid(10f) in 0.99f..1.0f)
        assertTrue(CompactFaceLocalizerGeometry.sigmoid(-10f) in 0.0f..0.01f)
        assertTrue(abs(CompactFaceLocalizerGeometry.sigmoid(1000f) - 1f) < 1e-6f)
        assertTrue(CompactFaceLocalizerGeometry.sigmoid(Float.NaN).isNaN())
        assertTrue(CompactFaceLocalizerGeometry.sigmoid(Float.POSITIVE_INFINITY).isNaN())
    }
}
