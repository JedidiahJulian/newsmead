package com.newsmead.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.newsmead.gaze.HybridEyeShadowBackend
import com.newsmead.gaze.ReusableArgbFrameTransformer
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Researcher-only candidate throughput screen. It never emits gaze and never reads or writes a
 * calibration. Launch explicitly through adb; it is not reachable from the participant UI.
 */
class HybridEyePerformanceActivity : AppCompatActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameTransformer = ReusableArgbFrameTransformer()
    private var cameraProvider: ProcessCameraProvider? = null
    private var backend: HybridEyeShadowBackend? = null
    private lateinit var statusText: TextView
    private var resultCount = 0L
    private var lastResultNs = 0L
    private var fps = 0f
    private var lastUiNs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        statusText = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            text = "Candidate-only performance preview\n\nWaiting for camera…"
        }
        setContentView(
            FrameLayout(this).apply {
                addView(
                    statusText,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
        )
        if (hasCameraPermission()) startCandidatePreview() else requestCameraPermission()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCandidatePreview()
        }
    }

    private fun startCandidatePreview() {
        backend = HybridEyeShadowBackend(applicationContext) { sample ->
            updateFps(sample.resultElapsedNs)
        }
        val future = ProcessCameraProvider.getInstance(applicationContext)
        future.addListener(
            {
                val provider = future.get()
                cameraProvider = provider
                bindCamera(provider)
            },
            ContextCompat.getMainExecutor(applicationContext),
        )
    }

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bindCamera(provider: ProcessCameraProvider) {
        val builder = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
        val analysis = builder.build().also { stream ->
            stream.setAnalyzer(cameraExecutor) { frame -> analyze(frame) }
        }
        provider.unbindAll()
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
    }

    private fun analyze(frame: ImageProxy) {
        try {
            val current = backend ?: return
            if (!current.canAcceptFrame()) {
                current.noteBusyFrameDropped()
                return
            }
            val transformed = frameTransformer.copyAndTransform(frame, frame.imageInfo.rotationDegrees)
            val ownedCopy = transformed.copy(Bitmap.Config.ARGB_8888, false)
            current.submit(
                bitmap = ownedCopy,
                referenceHorizontal = Float.NaN,
                referenceVertical = Float.NaN,
                captureTimestampNs = frame.imageInfo.timestamp,
            )
        } finally {
            frame.close()
        }
    }

    private fun updateFps(nowNs: Long) {
        resultCount += 1
        if (lastResultNs != 0L) {
            val delta = nowNs - lastResultNs
            if (delta > 0L) {
                val current = 1_000_000_000f / delta
                fps = if (fps == 0f) current else fps * 0.9f + current * 0.1f
            }
        }
        lastResultNs = nowNs
        if (lastUiNs == 0L || nowNs - lastUiNs >= UI_INTERVAL_NS) {
            lastUiNs = nowNs
            runOnUiThread {
                statusText.text = String.format(
                    Locale.US,
                    "Candidate-only performance preview\n\n%.1f FPS\n%d completed frames\n\nNo gaze output · no calibration writes",
                    fps,
                    resultCount,
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST,
        )
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        backend?.close()
        backend = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 4105
        private const val UI_INTERVAL_NS = 250_000_000L
    }
}
