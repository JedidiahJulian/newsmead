package com.newsmead.gaze.mgazenet

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.ByteBuffer

/** Upright RGB without selfie mirroring. Media image wrappers may consume the returned bitmap. */
class CameraFrames : AutoCloseable {
    private var srcMat: Mat? = null
    private var rotatedMat: Mat? = null
    private var currentUprightMat: Mat? = null
    private var upright: Bitmap? = null
    private var lastRotation = 0
    private var pixels = IntArray(0)
    private var rgb = ByteArray(0)
    private var rgba = ByteArray(0)
    private var uprightRgba = ByteArray(0)

    internal val uprightMat: Mat
        get() = checkNotNull(currentUprightMat) { "No upright frame available" }

    fun copy(image: ImageProxy): Bitmap {
        copyMat(image)
        return uprightBitmap()
    }

    internal fun copyMat(image: ImageProxy): Mat {
        val plane = image.planes[0]
        require(plane.pixelStride == 4)
        return copyRgbaToMat(plane.buffer, image.width, image.height, plane.rowStride, image.imageInfo.rotationDegrees)
    }

    internal fun copyRgba(input: ByteBuffer, width: Int, height: Int, stride: Int, rotation: Int): Bitmap {
        copyRgbaToMat(input, width, height, stride, rotation)
        return uprightBitmap()
    }

    internal fun copyRgbaToMat(input: ByteBuffer, width: Int, height: Int, stride: Int, rotation: Int): Mat {
        require(width > 0 && height > 0 && stride >= width * 4 &&
            (rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270))
        val byteCount = width * height * 4
        if (rgba.size != byteCount) rgba = ByteArray(byteCount)
        val buffer = input.duplicate()
        if (stride == width * 4) {
            buffer.position(0)
            buffer.get(rgba, 0, byteCount)
        } else {
            // Last row may omit stride padding; copy only valid RGBA pixels explicitly.
            val rowBytes = width * 4
            for (y in 0 until height) {
                buffer.position(y * stride)
                buffer.get(rgba, y * rowBytes, rowBytes)
            }
        }
        val src = srcMat ?: Mat().also { srcMat = it }
        src.create(height, width, CvType.CV_8UC4)
        src.put(0, 0, rgba)
        val rotated = when (rotation) {
            0 -> src
            90 -> (rotatedMat ?: Mat().also { rotatedMat = it }).also { Core.rotate(src, it, Core.ROTATE_90_CLOCKWISE) }
            180 -> (rotatedMat ?: Mat().also { rotatedMat = it }).also { Core.rotate(src, it, Core.ROTATE_180) }
            else -> (rotatedMat ?: Mat().also { rotatedMat = it }).also { Core.rotate(src, it, Core.ROTATE_90_COUNTERCLOCKWISE) }
        }
        currentUprightMat = rotated
        lastRotation = rotation
        return rotated
    }

    internal fun uprightBitmap(): Bitmap {
        val rotated = uprightMat
        val w = rotated.cols()
        val h = rotated.rows()
        if (upright == null || upright!!.isRecycled || upright!!.width != w || upright!!.height != h) {
            if (upright?.isRecycled == false) upright?.recycle()
            upright = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val uprightByteCount = w * h * 4
        val bitmapBytes = if (lastRotation == 0) {
            rgba
        } else {
            if (uprightRgba.size != uprightByteCount) uprightRgba = ByteArray(uprightByteCount)
            rotated.get(0, 0, uprightRgba)
            uprightRgba
        }
        upright!!.copyPixelsFromBuffer(ByteBuffer.wrap(bitmapBytes, 0, uprightByteCount))
        return upright!!
    }

    fun rgb(bitmap: Bitmap): RgbFrame {
        val count = bitmap.width * bitmap.height
        if (pixels.size != count) pixels = IntArray(count)
        if (rgb.size != count * 3) rgb = ByteArray(count * 3)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            rgb[i * 3] = (pixels[i] ushr 16).toByte()
            rgb[i * 3 + 1] = (pixels[i] ushr 8).toByte()
            rgb[i * 3 + 2] = pixels[i].toByte()
        }
        return RgbFrame(bitmap.width, bitmap.height, rgb)
    }

    override fun close() {
        pixels.fill(0); rgb.fill(0); rgba.fill(0); uprightRgba.fill(0)
        currentUprightMat = null
        srcMat?.release(); srcMat = null
        rotatedMat?.release(); rotatedMat = null
        if (upright?.isRecycled == false) upright?.recycle()
        upright = null
    }
}
