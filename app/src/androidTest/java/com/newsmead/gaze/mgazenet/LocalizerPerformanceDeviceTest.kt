package com.newsmead.gaze.mgazenet

import android.content.Context
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import java.nio.ByteBuffer

/** Camera-free opt-in timing of CompactFaceLocalizer tracked ROI pass and BlazeFace detector pass. */
@RunWith(AndroidJUnit4::class)
class LocalizerPerformanceDeviceTest {
    @Test(timeout = 60_000) fun measureCompactTrackedPassAndDetectorPass() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("newsmeadLocalizerPerf") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val order = List(2) { listOf("tracked", "detector", "detector", "tracked") }.flatten()
        val blocks = JSONArray()
        val summary = JSONObject()
            .put("schema", "newsmead_localizer_performance_v1")
            .put("device_model", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("detector_sha256", CalibrationIdentity.DETECTOR)
            .put("localizer_sha256", CalibrationIdentity.LOCALIZER)
            .put("order", JSONArray(order))
            .put("warmup_calls", WARMUP)
            .put("timed_calls_per_block", CALLS_PER_BLOCK)
            .put("thermal_status_initial", thermalStatus(context))
            .put("blocks", blocks)
            .put("limitation", "Isolated camera-free synthetic 480x640 timing only; no camera, MNN gaze, or human accuracy measured")
        var completed = false
        try {
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            val width = 480
            val height = 640
            val raw = ByteArray(width * height * 4)
            for (i in 0 until width * height) {
                raw[i * 4] = ((i * 7) and 255).toByte()
                raw[i * 4 + 1] = ((i * 13 + 31) and 255).toByte()
                raw[i * 4 + 2] = ((i * 29 + 97) and 255).toByte()
                raw[i * 4 + 3] = 255.toByte()
            }
            val normX = DoubleArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT)
            val normY = DoubleArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT)
            val crop = CompactFaceLocalizerGeometry.FaceCrop(240f, 320f, 220f, 0f)
            CameraFrames().use { frames ->
                val uprightMat = frames.copyRgbaToMat(ByteBuffer.wrap(raw), width, height, width * 4, 0)
                CompactFaceLocalizer(context).use { localizer ->
                    repeat(WARMUP) {
                        val presence = localizer.benchmarkTrackedPass(uprightMat, crop, normX, normY)
                        check(presence.isFinite() && normX.all { it.isFinite() } && normY.all { it.isFinite() })
                        localizer.benchmarkDetectorPass(frames.uprightBitmap())
                    }
                    order.forEachIndexed { blockIndex, mode ->
                        var wallSum = 0L; var cpuSum = 0L
                        var wallMin = Long.MAX_VALUE; var wallMax = 0L
                        var cpuMin = Long.MAX_VALUE; var cpuMax = 0L
                        repeat(CALLS_PER_BLOCK) {
                            if (mode == "tracked") {
                                val wallStart = SystemClock.elapsedRealtimeNanos()
                                val cpuStart = Debug.threadCpuTimeNanos()
                                val presence = localizer.benchmarkTrackedPass(uprightMat, crop, normX, normY)
                                val cpuNs = Debug.threadCpuTimeNanos() - cpuStart
                                val wallNs = SystemClock.elapsedRealtimeNanos() - wallStart
                                check(presence.isFinite() && normX.all { it.isFinite() } && normY.all { it.isFinite() })
                                wallSum += wallNs; cpuSum += cpuNs
                                wallMin = minOf(wallMin, wallNs); wallMax = maxOf(wallMax, wallNs)
                                cpuMin = minOf(cpuMin, cpuNs); cpuMax = maxOf(cpuMax, cpuNs)
                            } else {
                                val detectorBitmap = frames.uprightBitmap()
                                val wallStart = SystemClock.elapsedRealtimeNanos()
                                val cpuStart = Debug.threadCpuTimeNanos()
                                localizer.benchmarkDetectorPass(detectorBitmap)
                                val cpuNs = Debug.threadCpuTimeNanos() - cpuStart
                                val wallNs = SystemClock.elapsedRealtimeNanos() - wallStart
                                wallSum += wallNs; cpuSum += cpuNs
                                wallMin = minOf(wallMin, wallNs); wallMax = maxOf(wallMax, wallNs)
                                cpuMin = minOf(cpuMin, cpuNs); cpuMax = maxOf(cpuMax, cpuNs)
                            }
                        }
                        blocks.put(
                            JSONObject()
                                .put("block", blockIndex)
                                .put("stage", mode)
                                .put("sample_count", CALLS_PER_BLOCK)
                                .put("wall_mean_ms", wallSum / (CALLS_PER_BLOCK * 1e6))
                                .put("wall_min_ms", wallMin / 1e6)
                                .put("wall_max_ms", wallMax / 1e6)
                                .put("thread_cpu_mean_ms", cpuSum / (CALLS_PER_BLOCK * 1e6))
                                .put("thread_cpu_min_ms", cpuMin / 1e6)
                                .put("thread_cpu_max_ms", cpuMax / 1e6),
                        )
                    }
                }
            }
            completed = true
        } catch (failure: Throwable) {
            summary.put("failure", failure.javaClass.simpleName + ": " + failure.message)
            throw failure
        } finally {
            summary.put("measurement_completed", completed)
                .put("thermal_status_final", thermalStatus(context))
                .put("completed_timed_samples", blocks.length() * CALLS_PER_BLOCK)
            File(context.cacheDir, "newsmead-localizer-performance-summary.json").writeText(summary.toString(2))
        }
        check(completed) { "Localizer performance measurement incomplete" }
    }

    private fun thermalStatus(context: Context): Any = if (android.os.Build.VERSION.SDK_INT >= 29) {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus ?: JSONObject.NULL
    } else JSONObject.NULL

    companion object {
        private const val WARMUP = 5
        private const val CALLS_PER_BLOCK = 20
    }
}
