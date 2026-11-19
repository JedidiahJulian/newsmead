package com.newsmead.gaze.mgazenet

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class RgbFrame(val width: Int, val height: Int, val rgb: ByteArray) {
    init { require(width > 0 && height > 0 && rgb.size == width * height * 3) }
}

/** GazeFollower ordering: uint8 linear resize, float /255, right-eye flip.
 * RGB is packed to NCHW for the stock MNN CPU Session JNI, not NHWC Module API.
 */
class Preprocessor : AutoCloseable {
    data class Inputs(val face: FloatArray, val left: FloatArray, val right: FloatArray, val rect: FloatArray)
    private val rgba = Mat()
    private val source = Mat()
    private val roiRgb = Mat()
    private val resized = Mat()
    private val face = FloatArray(3 * 224 * 224)
    private val left = FloatArray(3 * 112 * 112)
    private val right = FloatArray(3 * 112 * 112)
    private val rect = FloatArray(12)
    private val faceBytes = ByteArray(face.size)
    private val eyeBytes = ByteArray(left.size)

    /** Returned arrays are reused; caller must finish inference before the next call. */
    fun prepare(frame: RgbFrame, crops: GazeGeometry.Crops): Inputs {
        source.create(frame.height, frame.width, CvType.CV_8UC3)
        source.put(0, 0, frame.rgb)
        return prepareSource(frame.width, frame.height, crops)
    }

    /** Direct Mat path used by the camera pipeline; skips copying Bitmap back into a Mat. */
    internal fun prepare(rgbaMat: Mat, crops: GazeGeometry.Crops): Inputs {
        require(!rgbaMat.empty() && rgbaMat.type() == CvType.CV_8UC4 && rgbaMat.cols() > 0 && rgbaMat.rows() > 0)
        return prepareFromRgbaMat(rgbaMat, crops)
    }

    /** Bitmap path used by callers/tests; converts only the 3 clipped ROIs to RGB. */
    fun prepare(bitmap: Bitmap, crops: GazeGeometry.Crops): Inputs {
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)
        Utils.bitmapToMat(bitmap, rgba)
        return prepareFromRgbaMat(rgba, crops)
    }

    private fun prepareFromRgbaMat(rgbaMat: Mat, crops: GazeGeometry.Crops): Inputs {
        val w = rgbaMat.cols()
        val h = rgbaMat.rows()
        patchFromRgbaMat(rgbaMat, w, h, crops.face, 224, false, face, faceBytes)
        patchFromRgbaMat(rgbaMat, w, h, crops.left, 112, false, left, eyeBytes)
        patchFromRgbaMat(rgbaMat, w, h, crops.right, 112, true, right, eyeBytes)
        return Inputs(face, left, right, crops.rectangles(w, h, rect))
    }

    private fun prepareSource(width: Int, height: Int, crops: GazeGeometry.Crops): Inputs {
        patch(width, height, crops.face, 224, false, face, faceBytes)
        patch(width, height, crops.left, 112, false, left, eyeBytes)
        patch(width, height, crops.right, 112, true, right, eyeBytes)
        return Inputs(face, left, right, crops.rectangles(width, height, rect))
    }

    private fun patchFromRgbaMat(
        rgbaMat: Mat,
        width: Int,
        height: Int,
        box: GazeGeometry.Box,
        edge: Int,
        flip: Boolean,
        out: FloatArray,
        bytes: ByteArray,
    ) {
        val b = requireNotNull(GazeGeometry.clip(box, width, height)) { "Invalid crop" }
        val rgbaRoi = rgbaMat.submat(b.y, b.y + b.height, b.x, b.x + b.width)
        try {
            Imgproc.cvtColor(rgbaRoi, roiRgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.resize(roiRgb, resized, targetSize(edge), 0.0, 0.0, Imgproc.INTER_LINEAR)
        } finally {
            rgbaRoi.release()
        }
        resized.get(0, 0, bytes)
        packNchw(edge, flip, out, bytes)
    }

    private fun patch(width: Int, height: Int, box: GazeGeometry.Box, edge: Int, flip: Boolean, out: FloatArray, bytes: ByteArray) {
        val b = requireNotNull(GazeGeometry.clip(box, width, height)) { "Invalid crop" }
        val roi = source.submat(b.y, b.y + b.height, b.x, b.x + b.width)
        try { Imgproc.resize(roi, resized, targetSize(edge), 0.0, 0.0, Imgproc.INTER_LINEAR) }
        finally { roi.release() }
        resized.get(0, 0, bytes)
        packNchw(edge, flip, out, bytes)
    }

    private fun packNchw(edge: Int, flip: Boolean, out: FloatArray, bytes: ByteArray) {
        val planeSize = edge * edge
        val plane2 = planeSize * 2
        if (!flip) {
            var src = 0
            var dst = 0
            while (dst < planeSize) {
                out[dst] = NORMALIZED[bytes[src].toInt() and 255]
                out[planeSize + dst] = NORMALIZED[bytes[src + 1].toInt() and 255]
                out[plane2 + dst] = NORMALIZED[bytes[src + 2].toInt() and 255]
                src += 3
                dst++
            }
        } else {
            val rowBytes = edge * 3
            var rowDst = 0
            var rowSrcEnd = rowBytes - 3
            for (y in 0 until edge) {
                var dst = rowDst
                var src = rowSrcEnd
                val dstEnd = rowDst + edge
                while (dst < dstEnd) {
                    out[dst] = NORMALIZED[bytes[src].toInt() and 255]
                    out[planeSize + dst] = NORMALIZED[bytes[src + 1].toInt() and 255]
                    out[plane2 + dst] = NORMALIZED[bytes[src + 2].toInt() and 255]
                    dst++
                    src -= 3
                }
                rowDst += edge
                rowSrcEnd += rowBytes
            }
        }
    }

    private fun targetSize(edge: Int): Size = if (edge == 224) SIZE_224 else SIZE_112

    override fun close() {
        face.fill(0f); left.fill(0f); right.fill(0f); rect.fill(0f); faceBytes.fill(0); eyeBytes.fill(0)
        rgba.release(); source.release(); roiRgb.release(); resized.release()
    }

    companion object {
        private val SIZE_224 = Size(224.0, 224.0)
        private val SIZE_112 = Size(112.0, 112.0)
        private val NORMALIZED = FloatArray(256) { it / 255f }
    }
}
