package com.newsmead.gaze

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Local raw gaze source backed by CameraX and MediaPipe FaceLandmarker.
 *
 * It emits normalized iris-in-eye ratios. The values are deliberately raw
 * features, not screen coordinates; 16-point calibration maps them to screen px.
 */
class MediaPipeRawGazeSource(
    private val context: Context,
    private val modelAssetPath: String = MODEL_ASSET_PATH,
) : LocalRawGazeSource {

    private val appContext = context.applicationContext
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var listener: LocalRawGazeSource.OnRawGaze? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var stopped = true
    private var lastTimestampMs = 0L
    private var resultCount = 0
    private val lifecycleLock = Any()

    override fun setOnRawGaze(listener: LocalRawGazeSource.OnRawGaze) {
        this.listener = listener
    }

    override fun start(owner: LifecycleOwner) {
        if (!hasCameraPermission()) {
            requestCameraPermissionIfPossible()
            Log.w(TAG, "Camera permission missing; grant CAMERA permission and start gaze again")
            return
        }

        stopped = false
        try {
            faceLandmarker = createFaceLandmarker()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create FaceLandmarker. Ensure $modelAssetPath exists in app/src/main/assets", e)
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindCamera(provider, owner)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CameraX gaze source", e)
                stop()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    override fun stop() {
        synchronized(lifecycleLock) {
            stopped = true
            busy.set(false)
            cameraProvider?.unbindAll()
            cameraProvider = null
            faceLandmarker?.close()
            faceLandmarker = null
        }
    }

    fun shutdown() {
        stop()
        cameraExecutor.shutdown()
    }

    private fun createFaceLandmarker(): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> onFaceLandmarkerResult(result) }
            .setErrorListener { error ->
                busy.set(false)
                Log.e(TAG, "FaceLandmarker error", error)
            }
            .build()

        return FaceLandmarker.createFromOptions(appContext, options)
    }

    private fun bindCamera(provider: ProcessCameraProvider, owner: LifecycleOwner) {
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy -> analyze(imageProxy) }
            }

        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        Log.i(TAG, "Local MediaPipe raw gaze source started")
    }

    private fun analyze(imageProxy: ImageProxy) {
        val landmarker = synchronized(lifecycleLock) {
            if (stopped) null else faceLandmarker
        }
        if (landmarker == null) {
            imageProxy.close()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        try {
            if (stopped) return
            val bitmap = imageProxy.toBitmapArgb8888().rotate(imageProxy.imageInfo.rotationDegrees)
            val mpImage = BitmapImageBuilder(bitmap).build()
            landmarker.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Exception) {
            busy.set(false)
            Log.e(TAG, "Failed to analyze camera frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult) {
        busy.set(false)
        val face = result.faceLandmarks().firstOrNull()
        if (face == null) {
            Log.d(TAG, "No face landmarks")
            return
        }

        val raw = extractRawGaze(face) ?: return
        listener?.onRawGaze(raw.first, raw.second, System.currentTimeMillis())
    }

    private fun extractRawGaze(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Pair<Float, Float>? {
        if (landmarks.size <= MIN_REQUIRED_LANDMARK_INDEX) return null

        val right = eyeRatio(
            landmarks = landmarks,
            leftCorner = RIGHT_EYE_OUTER,
            rightCorner = RIGHT_EYE_INNER,
            upperLid = RIGHT_EYE_UPPER,
            lowerLid = RIGHT_EYE_LOWER,
            iris = RIGHT_IRIS,
        ) ?: return null

        val left = eyeRatio(
            landmarks = landmarks,
            leftCorner = LEFT_EYE_INNER,
            rightCorner = LEFT_EYE_OUTER,
            upperLid = LEFT_EYE_UPPER,
            lowerLid = LEFT_EYE_LOWER,
            iris = LEFT_IRIS,
        ) ?: return null

        return Pair((right.first + left.first) / 2f, (right.second + left.second) / 2f)
    }

    private fun eyeRatio(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        leftCorner: Int,
        rightCorner: Int,
        upperLid: Int,
        lowerLid: Int,
        iris: IntArray,
    ): Pair<Float, Float>? {
        val outer = landmarks[leftCorner]
        val inner = landmarks[rightCorner]
        val upper = landmarks[upperLid]
        val lower = landmarks[lowerLid]
        val irisX = iris.map { landmarks[it].x() }.average().toFloat()
        val irisY = iris.map { landmarks[it].y() }.average().toFloat()

        val minX = minOf(outer.x(), inner.x())
        val minY = minOf(upper.y(), lower.y())
        val eyeWidth = max(kotlin.math.abs(inner.x() - outer.x()), MIN_EYE_SIZE)
        val eyeHeight = max(kotlin.math.abs(lower.y() - upper.y()), MIN_EYE_SIZE)

        val ratioX = ((irisX - minX) / eyeWidth).coerceIn(-1f, 2f)
        val ratioY = ((irisY - minY) / eyeHeight).coerceIn(-1f, 2f)
        return Pair(ratioX, ratioY)
    }

    private fun ImageProxy.toBitmapArgb8888(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        buffer.rewind()

        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val rowStride = plane.rowStride
        val rowWidth = rowStride / pixelStride
        val rowBitmap = Bitmap.createBitmap(rowWidth, height, Bitmap.Config.ARGB_8888)
        rowBitmap.copyPixelsFromBuffer(buffer)
        return if (rowWidth == width) {
            rowBitmap
        } else {
            Bitmap.createBitmap(rowBitmap, 0, 0, width, height)
        }
    }

    private fun nextTimestampMs(): Long {
        val now = System.currentTimeMillis()
        val next = if (now <= lastTimestampMs) lastTimestampMs + 1 else now
        lastTimestampMs = next
        return next
    }

    private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return this
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermissionIfPossible() {
        val activity = context as? Activity ?: return
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    companion object {
        private const val TAG = "MediaPipeGaze"
        private const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val CAMERA_PERMISSION_REQUEST = 4104
        private const val MIN_EYE_SIZE = 1e-4f
        private const val RAW_LOG_INTERVAL = 15

        private const val RIGHT_EYE_OUTER = 33
        private const val RIGHT_EYE_INNER = 133
        private const val RIGHT_EYE_UPPER = 159
        private const val RIGHT_EYE_LOWER = 145
        private val RIGHT_IRIS = intArrayOf(469, 470, 471, 472)

        private const val LEFT_EYE_INNER = 362
        private const val LEFT_EYE_OUTER = 263
        private const val LEFT_EYE_UPPER = 386
        private const val LEFT_EYE_LOWER = 374
        private val LEFT_IRIS = intArrayOf(474, 475, 476, 477)

        private const val MIN_REQUIRED_LANDMARK_INDEX = 477
    }
}
