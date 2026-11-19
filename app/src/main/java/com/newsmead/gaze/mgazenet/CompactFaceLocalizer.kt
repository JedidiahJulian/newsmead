package com.newsmead.gaze.mgazenet

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compact 468-point CPU TFLite localizer using BlazeFace bootstrap/recovery and
 * previous-landmark ROI tracking on the upright OpenCV RGBA Mat without per-frame Bitmap copies.
 */
internal class CompactFaceLocalizer(context: Context) : AutoCloseable {
    data class Result(val found: Boolean, val detectorRan: Boolean, val presence: Float)

    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)

    private val detector: FaceDetector
    private val interpreter: Interpreter
    private val landmarkBuffer: ByteBuffer
    private val landmarkFloatView: FloatBuffer
    private val presenceBuffer: ByteBuffer
    private val faceOutputs: Map<Int, Any>

    private val affineMat = Mat(2, 3, CvType.CV_64F)
    private val cropRgba = Mat()
    private val cropRgb = Mat()
    private val cropFloat = Mat()
    private val cropSize = Size(
        CompactFaceLocalizerGeometry.FACE_MODEL_EDGE.toDouble(),
        CompactFaceLocalizerGeometry.FACE_MODEL_EDGE.toDouble(),
    )
    private val faceFloats = FloatArray(
        CompactFaceLocalizerGeometry.FACE_MODEL_EDGE *
            CompactFaceLocalizerGeometry.FACE_MODEL_EDGE * 3,
    )
    private val faceInput: ByteBuffer = ByteBuffer.allocateDirect(faceFloats.size * 4)
        .order(ByteOrder.nativeOrder())
    private val faceFloatInput: FloatBuffer = faceInput.asFloatBuffer()
    private val rawLandmarks = FloatArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_FLOATS)
    private var trackedCrop: CompactFaceLocalizerGeometry.FaceCrop? = null

    init {
        var createdDetector: FaceDetector? = null
        var createdInterpreter: Interpreter? = null
        try {
            val detectorBytes = appContext.assets.open(FACE_DETECTOR_ASSET).use { it.readBytes() }
            check(CalibrationIdentity.hash(detectorBytes) == CalibrationIdentity.DETECTOR) {
                "Unexpected BlazeFace detector asset hash"
            }
            val landmarkBytes = appContext.assets.open(FACE_LANDMARK_ASSET).use { it.readBytes() }
            check(CalibrationIdentity.hash(landmarkBytes) == CalibrationIdentity.LOCALIZER) {
                "Unexpected compact face landmark asset hash"
            }

            createdDetector = FaceDetector.createFromOptions(
                appContext,
                FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(FACE_DETECTOR_ASSET)
                            .setDelegate(Delegate.CPU)
                            .build(),
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setMinDetectionConfidence(CompactFaceLocalizerGeometry.MIN_DETECTION_CONFIDENCE)
                    .build(),
            )

            val modelBuffer = ByteBuffer.allocateDirect(landmarkBytes.size)
                .order(ByteOrder.nativeOrder())
                .put(landmarkBytes)
            modelBuffer.rewind()
            createdInterpreter = Interpreter(
                modelBuffer,
                Interpreter.Options().setNumThreads(INFERENCE_THREADS),
            )
            val inputTensor = createdInterpreter.getInputTensor(0)
            check(
                inputTensor.dataType() == DataType.FLOAT32 &&
                    inputTensor.shape().contentEquals(
                        intArrayOf(
                            1,
                            CompactFaceLocalizerGeometry.FACE_MODEL_EDGE,
                            CompactFaceLocalizerGeometry.FACE_MODEL_EDGE,
                            3,
                        ),
                    ),
            ) { "Unexpected compact face landmark input tensor contract" }

            val landmarkIndex = outputIndex(createdInterpreter, CompactFaceLocalizerGeometry.FACE_LANDMARK_FLOATS)
            val presenceIndex = outputIndex(createdInterpreter, 1)
            landmarkBuffer = outputBuffer(createdInterpreter, landmarkIndex)
            landmarkFloatView = landmarkBuffer.asFloatBuffer()
            presenceBuffer = outputBuffer(createdInterpreter, presenceIndex)
            faceOutputs = mapOf(
                landmarkIndex to landmarkBuffer,
                presenceIndex to presenceBuffer,
            )
            detector = createdDetector
            interpreter = createdInterpreter
        } catch (failure: Throwable) {
            runCatching { createdDetector?.close() }
            runCatching { createdInterpreter?.close() }
            affineMat.release()
            cropRgba.release()
            cropRgb.release()
            cropFloat.release()
            throw failure
        }
    }

    fun detect(
        uprightRgba: Mat,
        bitmapSupplier: () -> Bitmap,
        outNormX: DoubleArray,
        outNormY: DoubleArray,
    ): Result {
        check(!closed.get()) { "CompactFaceLocalizer is closed" }
        require(uprightRgba.type() == CvType.CV_8UC4 && uprightRgba.cols() > 0 && uprightRgba.rows() > 0)
        require(
            outNormX.size == CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT &&
                outNormY.size == CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT,
        )

        val currentTracked = trackedCrop
        if (currentTracked != null) {
            val presence = inferOnCrop(uprightRgba, currentTracked, outNormX, outNormY)
            if (presence.isFinite() && presence >= CompactFaceLocalizerGeometry.FACE_PRESENCE_THRESHOLD) {
                val nextCrop = CompactFaceLocalizerGeometry.faceCropFromLandmarks(
                    outNormX, outNormY, uprightRgba.cols(), uprightRgba.rows(),
                )
                if (nextCrop != null) {
                    trackedCrop = nextCrop
                    return Result(found = true, detectorRan = false, presence = presence)
                }
            }
            trackedCrop = null
        }

        val bitmap = bitmapSupplier()
        val detectedCrop = detectFaceCrop(bitmap)
            ?: return Result(found = false, detectorRan = true, presence = 0f)
        val presence = inferOnCrop(uprightRgba, detectedCrop, outNormX, outNormY)
        if (!presence.isFinite() || presence < CompactFaceLocalizerGeometry.FACE_PRESENCE_THRESHOLD) {
            trackedCrop = null
            return Result(found = false, detectorRan = true, presence = presence)
        }
        trackedCrop = CompactFaceLocalizerGeometry.faceCropFromLandmarks(
            outNormX, outNormY, uprightRgba.cols(), uprightRgba.rows(),
        ) ?: detectedCrop
        return Result(found = true, detectorRan = true, presence = presence)
    }

    internal fun benchmarkTrackedPass(
        uprightRgba: Mat,
        crop: CompactFaceLocalizerGeometry.FaceCrop,
        outNormX: DoubleArray,
        outNormY: DoubleArray,
    ): Float {
        check(!closed.get()) { "CompactFaceLocalizer is closed" }
        trackedCrop = null
        val presence = inferOnCrop(uprightRgba, crop, outNormX, outNormY)
        if (presence.isFinite()) {
            CompactFaceLocalizerGeometry.faceCropFromLandmarks(
                outNormX, outNormY, uprightRgba.cols(), uprightRgba.rows(),
            )
        }
        return presence
    }

    internal fun benchmarkDetectorPass(bitmap: Bitmap): Boolean {
        check(!closed.get()) { "CompactFaceLocalizer is closed" }
        trackedCrop = null
        return detectFaceCrop(bitmap) != null
    }

    private fun detectFaceCrop(bitmap: Bitmap): CompactFaceLocalizerGeometry.FaceCrop? {
        require(!bitmap.isRecycled) { "Bitmap passed to FaceDetector must not be recycled" }
        val mp = BitmapImageBuilder(bitmap).build()
        val detection = try {
            detector.detect(mp).detections().firstOrNull()
        } finally {
            mp.close()
        } ?: return null
        val keypoints = detection.keypoints().orElse(emptyList())
        if (keypoints.size < REQUIRED_FACE_KEYPOINTS) return null
        val rightEye = keypoints[RIGHT_EYE_KEYPOINT]
        val leftEye = keypoints[LEFT_EYE_KEYPOINT]
        val box = detection.boundingBox()
        return CompactFaceLocalizerGeometry.faceCropFromDetection(
            rightEyeX = rightEye.x() * bitmap.width,
            rightEyeY = rightEye.y() * bitmap.height,
            leftEyeX = leftEye.x() * bitmap.width,
            leftEyeY = leftEye.y() * bitmap.height,
            boxLeft = box.left,
            boxTop = box.top,
            boxRight = box.right,
            boxBottom = box.bottom,
        )
    }

    private fun inferOnCrop(
        uprightRgba: Mat,
        crop: CompactFaceLocalizerGeometry.FaceCrop,
        outNormX: DoubleArray,
        outNormY: DoubleArray,
    ): Float {
        val matrixValues = CompactFaceLocalizerGeometry.affineMatrixValues(crop)
        affineMat.put(0, 0, *matrixValues)
        Imgproc.warpAffine(
            uprightRgba,
            cropRgba,
            affineMat,
            cropSize,
            Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT,
            BLACK_RGBA,
        )
        Imgproc.cvtColor(cropRgba, cropRgb, Imgproc.COLOR_RGBA2RGB)
        cropRgb.convertTo(cropFloat, CvType.CV_32FC3, 1.0 / 255.0)
        cropFloat.get(0, 0, faceFloats)
        faceFloatInput.clear()
        faceFloatInput.put(faceFloats)
        faceInput.rewind()

        landmarkBuffer.rewind()
        presenceBuffer.rewind()
        interpreter.runForMultipleInputsOutputs(arrayOf(faceInput), faceOutputs)
        landmarkBuffer.rewind()
        landmarkFloatView.clear()
        landmarkFloatView.get(rawLandmarks)

        val presence = CompactFaceLocalizerGeometry.sigmoid(presenceBuffer.getFloat(0))
        if (!presence.isFinite()) return Float.NaN
        if (!CompactFaceLocalizerGeometry.projectLandmarks(
                rawLandmarks, crop, uprightRgba.cols(), uprightRgba.rows(), outNormX, outNormY,
            )
        ) {
            return Float.NaN
        }
        return presence
    }

    private fun outputIndex(interpreter: Interpreter, elements: Int): Int =
        (0 until interpreter.outputTensorCount).firstOrNull {
            interpreter.getOutputTensor(it).numElements() == elements
        } ?: error("Model output with $elements elements not found")

    private fun outputBuffer(interpreter: Interpreter, index: Int): ByteBuffer =
        ByteBuffer.allocateDirect(interpreter.getOutputTensor(index).numBytes())
            .order(ByteOrder.nativeOrder())

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        trackedCrop = null
        faceFloats.fill(0f)
        rawLandmarks.fill(0f)
        runCatching { detector.close() }
        runCatching { interpreter.close() }
        affineMat.release()
        cropRgba.release()
        cropRgb.release()
        cropFloat.release()
    }

    companion object {
        private const val FACE_DETECTOR_ASSET = "blaze_face_short_range.tflite"
        private const val FACE_LANDMARK_ASSET = "face_landmark.tflite"
        private const val INFERENCE_THREADS = 2
        private const val RIGHT_EYE_KEYPOINT = 0
        private const val LEFT_EYE_KEYPOINT = 1
        private const val REQUIRED_FACE_KEYPOINTS = 2
        private val BLACK_RGBA = Scalar(0.0, 0.0, 0.0, 255.0)
    }
}
