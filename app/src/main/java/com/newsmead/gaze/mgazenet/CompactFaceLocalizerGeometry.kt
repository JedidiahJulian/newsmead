package com.newsmead.gaze.mgazenet

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure crop, affine-matrix, and projection geometry for the compact 468-point TFLite localizer.
 * Separate from OpenCV/TFLite runtime so exact coordinate transforms can be tested on JVM.
 */
internal object CompactFaceLocalizerGeometry {
    data class FaceCrop(
        val centerX: Float,
        val centerY: Float,
        val sidePx: Float,
        val rotationDegrees: Float,
    )

    fun sigmoid(logit: Float): Float {
        if (!logit.isFinite()) return Float.NaN
        return (1.0 / (1.0 + exp(-logit.toDouble()))).toFloat()
    }

    fun faceCropFromDetection(
        rightEyeX: Float,
        rightEyeY: Float,
        leftEyeX: Float,
        leftEyeY: Float,
        boxLeft: Float,
        boxTop: Float,
        boxRight: Float,
        boxBottom: Float,
    ): FaceCrop? {
        if (!rightEyeX.isFinite() || !rightEyeY.isFinite() ||
            !leftEyeX.isFinite() || !leftEyeY.isFinite() ||
            !boxLeft.isFinite() || !boxTop.isFinite() ||
            !boxRight.isFinite() || !boxBottom.isFinite()
        ) return null
        val width = boxRight - boxLeft
        val height = boxBottom - boxTop
        if (width <= 0f || height <= 0f) return null
        if (hypot((leftEyeX - rightEyeX).toDouble(), (leftEyeY - rightEyeY).toDouble()) < MIN_INTER_EYE_PX) {
            return null
        }
        val screenLeftX: Float
        val screenLeftY: Float
        val screenRightX: Float
        val screenRightY: Float
        if (rightEyeX <= leftEyeX) {
            screenLeftX = rightEyeX; screenLeftY = rightEyeY
            screenRightX = leftEyeX; screenRightY = leftEyeY
        } else {
            screenLeftX = leftEyeX; screenLeftY = leftEyeY
            screenRightX = rightEyeX; screenRightY = rightEyeY
        }
        val rotationDegrees = Math.toDegrees(
            atan2((screenRightY - screenLeftY).toDouble(), (screenRightX - screenLeftX).toDouble()),
        ).toFloat()
        if (!rotationDegrees.isFinite()) return null
        return FaceCrop(
            centerX = (boxLeft + boxRight) / 2f,
            centerY = (boxTop + boxBottom) / 2f,
            sidePx = max(width, height) * FACE_ROI_SCALE,
            rotationDegrees = rotationDegrees,
        )
    }

    /**
     * Forward 2x3 row-major matrix for OpenCV warpAffine mapping source pixel coordinates
     * to the 192x192 rotated/scaled face crop expected by face_landmark.tflite.
     */
    fun affineMatrixValues(crop: FaceCrop): DoubleArray {
        require(crop.sidePx.isFinite() && crop.sidePx > 0f)
        require(crop.centerX.isFinite() && crop.centerY.isFinite() && crop.rotationDegrees.isFinite())
        val scale = FACE_MODEL_EDGE.toDouble() / crop.sidePx.toDouble()
        val rad = Math.toRadians(crop.rotationDegrees.toDouble())
        val c = cos(rad)
        val s = sin(rad)
        val half = FACE_MODEL_EDGE / 2.0
        val m00 = scale * c
        val m01 = scale * s
        val m02 = half - scale * (c * crop.centerX + s * crop.centerY)
        val m10 = -scale * s
        val m11 = scale * c
        val m12 = half - scale * (-s * crop.centerX + c * crop.centerY)
        return doubleArrayOf(m00, m01, m02, m10, m11, m12)
    }

    /**
     * Exact inverse of [affineMatrixValues], mapping 192x192 face-tensor landmark coordinates
     * to normalized [0, 1] coordinates in the upright camera frame.
     */
    fun projectLandmarks(
        rawModelLandmarks: FloatArray,
        crop: FaceCrop,
        imageWidth: Int,
        imageHeight: Int,
        outNormX: DoubleArray,
        outNormY: DoubleArray,
    ): Boolean {
        if (rawModelLandmarks.size < FACE_LANDMARK_FLOATS ||
            outNormX.size != FACE_LANDMARK_COUNT || outNormY.size != FACE_LANDMARK_COUNT ||
            imageWidth <= 0 || imageHeight <= 0 ||
            !crop.sidePx.isFinite() || crop.sidePx <= 0f ||
            !crop.centerX.isFinite() || !crop.centerY.isFinite() || !crop.rotationDegrees.isFinite()
        ) return false
        val rad = Math.toRadians(crop.rotationDegrees.toDouble())
        val c = cos(rad)
        val s = sin(rad)
        val half = FACE_MODEL_EDGE / 2.0
        val invScale = crop.sidePx.toDouble() / FACE_MODEL_EDGE.toDouble()
        for (i in 0 until FACE_LANDMARK_COUNT) {
            val offset = i * LANDMARK_DIMS
            val modelX = rawModelLandmarks[offset]
            val modelY = rawModelLandmarks[offset + 1]
            if (!modelX.isFinite() || !modelY.isFinite()) return false
            val localX = modelX.toDouble() - half
            val localY = modelY.toDouble() - half
            val px = crop.centerX.toDouble() + (c * localX - s * localY) * invScale
            val py = crop.centerY.toDouble() + (s * localX + c * localY) * invScale
            if (!px.isFinite() || !py.isFinite()) return false
            outNormX[i] = px / imageWidth
            outNormY[i] = py / imageHeight
        }
        return true
    }

    /** Derives the next frame's eye-aligned 1.5x ROI directly from the previous 468 landmarks. */
    fun faceCropFromLandmarks(
        normX: DoubleArray,
        normY: DoubleArray,
        imageWidth: Int,
        imageHeight: Int,
    ): FaceCrop? {
        if (normX.size != FACE_LANDMARK_COUNT || normY.size != FACE_LANDMARK_COUNT ||
            imageWidth <= 0 || imageHeight <= 0
        ) return null
        val rx33 = normX[RIGHT_EYE_OUTER_CORNER] * imageWidth
        val ry33 = normY[RIGHT_EYE_OUTER_CORNER] * imageHeight
        val rx263 = normX[LEFT_EYE_OUTER_CORNER] * imageWidth
        val ry263 = normY[LEFT_EYE_OUTER_CORNER] * imageHeight
        if (!rx33.isFinite() || !ry33.isFinite() || !rx263.isFinite() || !ry263.isFinite()) return null
        if (hypot(rx263 - rx33, ry263 - ry33) < MIN_INTER_EYE_PX) return null

        val screenLeftX: Double
        val screenLeftY: Double
        val screenRightX: Double
        val screenRightY: Double
        if (rx33 <= rx263) {
            screenLeftX = rx33; screenLeftY = ry33
            screenRightX = rx263; screenRightY = ry263
        } else {
            screenLeftX = rx263; screenLeftY = ry263
            screenRightX = rx33; screenRightY = ry33
        }
        val rad = atan2(screenRightY - screenLeftY, screenRightX - screenLeftX)
        val rotationDegrees = Math.toDegrees(rad).toFloat()
        if (!rotationDegrees.isFinite()) return null
        val c = cos(rad)
        val s = sin(rad)

        var minRx = Double.POSITIVE_INFINITY
        var maxRx = Double.NEGATIVE_INFINITY
        var minRy = Double.POSITIVE_INFINITY
        var maxRy = Double.NEGATIVE_INFINITY
        for (i in 0 until FACE_LANDMARK_COUNT) {
            val nx = normX[i]
            val ny = normY[i]
            if (!nx.isFinite() || !ny.isFinite()) return null
            val px = nx * imageWidth
            val py = ny * imageHeight
            val rx = c * px + s * py
            val ry = -s * px + c * py
            if (!rx.isFinite() || !ry.isFinite()) return null
            if (rx < minRx) minRx = rx
            if (rx > maxRx) maxRx = rx
            if (ry < minRy) minRy = ry
            if (ry > maxRy) maxRy = ry
        }
        val span = max(maxRx - minRx, maxRy - minRy)
        val sidePx = (span * FACE_ROI_SCALE).toFloat()
        val midRx = (minRx + maxRx) / 2.0
        val midRy = (minRy + maxRy) / 2.0
        val cx = (c * midRx - s * midRy).toFloat()
        val cy = (s * midRx + c * midRy).toFloat()
        val maxImageSide = max(imageWidth, imageHeight)
        if (!cx.isFinite() || !cy.isFinite() || !sidePx.isFinite()) return null
        if (sidePx < MIN_TRACKED_SIDE_PX || sidePx > maxImageSide * 2.5f) return null
        if (cx < -imageWidth * 0.1f || cx > imageWidth * 1.1f ||
            cy < -imageHeight * 0.1f || cy > imageHeight * 1.1f
        ) return null
        return FaceCrop(cx, cy, sidePx, rotationDegrees)
    }

    const val FACE_LANDMARK_COUNT = 468
    const val LANDMARK_DIMS = 3
    const val FACE_LANDMARK_FLOATS = FACE_LANDMARK_COUNT * LANDMARK_DIMS
    const val FACE_MODEL_EDGE = 192
    const val FACE_ROI_SCALE = 1.5f
    const val FACE_PRESENCE_THRESHOLD = 0.5f
    const val MIN_DETECTION_CONFIDENCE = 0.5f
    const val MIN_INTER_EYE_PX = 8f
    const val MIN_TRACKED_SIDE_PX = 24f
    const val RIGHT_EYE_OUTER_CORNER = 33
    const val LEFT_EYE_OUTER_CORNER = 263
}
