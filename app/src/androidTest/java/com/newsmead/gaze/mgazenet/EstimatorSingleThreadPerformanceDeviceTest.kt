package com.newsmead.gaze.mgazenet

import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.os.PowerManager
import android.os.Process
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
import kotlin.math.abs

/** One CPU configuration per instrumentation process, so MNN's persistent worker pool is fresh. */
@RunWith(AndroidJUnit4::class)
class EstimatorSingleThreadPerformanceDeviceTest {
    @Test(timeout = 180_000) fun measureOneFreshCpuThreadConfiguration() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("newsmeadSingleThreadPerf") == "true")
        val threads = requireNotNull(args.getString("newsmeadSingleThreadPerfThreads")?.toIntOrNull())
        require(threads in 2..4) { "Thread count must be 2, 3, or 4" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val samples = JSONArray()
        val preprocessingSamples = JSONArray()
        val combinedSamples = JSONArray()
        val outputs = JSONArray()
        val summary = JSONObject()
            .put("schema", "newsmead_single_thread_performance_v2")
            .put("device_model", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("pid", Process.myPid())
            .put("process_start_ticks", processStartTicks())
            .put("requested_threads", threads)
            .put("model_sha256", MnnEstimator.MODEL_SHA)
            .put("warmup_calls", WARMUP_CALLS)
            .put("timed_calls", TIMED_CALLS)
            .put("thermal_status_initial", thermalStatus(context))
            .put("measurement", "Separate inference-only plus 640x480 Bitmap preprocessing and combined preprocessing+inference wall times")
            .put("thread_cpu_scope", "Calling instrumentation thread only; excludes native worker CPU time")
            .put("limitation", "Camera-free synthetic pipeline benchmark; excludes face detection, camera acquisition/copy, UI, writer and calibration and does not establish gaze accuracy or human validity")
        var completed = false
        try {
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            Preprocessor().use { prep ->
                val fixtures = SyntheticFixtures.images()
                val modelStart = SystemClock.elapsedRealtimeNanos()
                MnnEstimator(context, threads).use { model ->
                    summary.put("model_init_wall_ms", (SystemClock.elapsedRealtimeNanos() - modelStart) / 1e6)
                    val execution = model.executionSnapshot()
                    summary.put("execution", JSONObject(execution))
                    check(execution["requested_session_threads"] == threads)
                    check(execution["initial_process_pool_thread_count"] == threads)
                    check(execution["thread_count"] == threads)

                    val rampInput = prep.prepare(fixtures.first().frame, fixtures.first().crops)
                    repeat(WARMUP_CALLS) { model.infer(rampInput).fill(0f) }
                    fixtures.indices.forEach { index ->
                        val first = model.infer(prep.prepare(fixtures[index].frame, fixtures[index].crops))
                        val repeat = model.infer(prep.prepare(fixtures[index].frame, fixtures[index].crops))
                        try {
                            check(first.size == 258 && first.all(Float::isFinite))
                            check(repeat.size == 258 && repeat.all(Float::isFinite))
                            var repeatMax = 0.0
                            first.indices.forEach { i -> repeatMax = maxOf(repeatMax, abs(first[i] - repeat[i]).toDouble()) }
                            outputs.put(JSONObject().put("fixture", fixtures[index].name)
                                .put("repeat_max_absolute_error", repeatMax)
                                .put("values", JSONArray(first.toList())))
                        } finally {
                            first.fill(0f)
                            repeat.fill(0f)
                        }
                    }
                    prep.prepare(fixtures.first().frame, fixtures.first().crops)
                    repeat(TIMED_CALLS) {
                        val wallStart = SystemClock.elapsedRealtimeNanos()
                        val cpuStart = Debug.threadCpuTimeNanos()
                        val output = model.infer(rampInput)
                        val cpuNs = Debug.threadCpuTimeNanos() - cpuStart
                        val wallNs = SystemClock.elapsedRealtimeNanos() - wallStart
                        try { check(output.size == 258 && output.all(Float::isFinite)) }
                        finally { output.fill(0f) }
                        samples.put(JSONObject().put("wall_ms", wallNs / 1e6).put("calling_thread_cpu_ms", cpuNs / 1e6))
                    }

                    val bitmap = syntheticBitmap()
                    val crops = GazeGeometry.Crops(
                        GazeGeometry.Box(120, 70, 400, 360),
                        GazeGeometry.Box(190, 170, 90, 55),
                        GazeGeometry.Box(355, 170, 90, 55),
                        640.0, 480.0,
                    )
                    try {
                        repeat(4) { model.infer(prep.prepare(bitmap, crops)).fill(0f) }
                        repeat(PIPELINE_CALLS) {
                            val started = SystemClock.elapsedRealtimeNanos()
                            prep.prepare(bitmap, crops)
                            preprocessingSamples.put((SystemClock.elapsedRealtimeNanos() - started) / 1e6)
                        }
                        repeat(PIPELINE_CALLS) {
                            val started = SystemClock.elapsedRealtimeNanos()
                            val output = model.infer(prep.prepare(bitmap, crops))
                            val elapsed = SystemClock.elapsedRealtimeNanos() - started
                            try { check(output.size == 258 && output.all(Float::isFinite)) }
                            finally { output.fill(0f) }
                            combinedSamples.put(elapsed / 1e6)
                        }
                    } finally { bitmap.recycle() }
                }
            }
            completed = true
        } catch (failure: Throwable) {
            summary.put("failure", "${failure.javaClass.simpleName}: ${failure.message}")
            throw failure
        } finally {
            summary.put("outputs", outputs)
                .put("samples", samples)
                .put("preprocessing_source", "deterministic 640x480 ARGB bitmap with fixed valid face/eye crops")
                .put("preprocessing_samples_ms", preprocessingSamples)
                .put("combined_preprocessing_inference_samples_ms", combinedSamples)
                .put("completed_timed_samples", samples.length())
                .put("measurement_completed", completed)
                .put("thermal_status_final", thermalStatus(context))
            File(context.cacheDir, "newsmead-single-thread-performance-$threads.json").writeText(summary.toString(2))
        }
        check(completed && samples.length() == TIMED_CALLS &&
            preprocessingSamples.length() == PIPELINE_CALLS && combinedSamples.length() == PIPELINE_CALLS)
    }

    private fun syntheticBitmap(): Bitmap {
        val pixels = IntArray(640 * 480) { index ->
            val x = index % 640
            val y = index / 640
            val r = (x * 3 + y) and 255
            val g = (x + y * 5) and 255
            val b = (x * 7 + y * 11) and 255
            (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, 640, 480, Bitmap.Config.ARGB_8888).also { pixels.fill(0) }
    }

    private fun processStartTicks(): String {
        val stat = File("/proc/self/stat").readText()
        return stat.substring(stat.lastIndexOf(')') + 1).trim().split(Regex("\\s+"))[19]
    }

    private fun thermalStatus(context: Context): Any = if (android.os.Build.VERSION.SDK_INT >= 29)
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus ?: JSONObject.NULL
    else JSONObject.NULL

    companion object {
        private const val WARMUP_CALLS = 8
        private const val TIMED_CALLS = 48
        private const val PIPELINE_CALLS = 24
    }
}
