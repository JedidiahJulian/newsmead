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
import kotlin.math.abs

/** Isolated synthetic inference timing; numerical parity is reported, not required for timing. */
@RunWith(AndroidJUnit4::class)
class EstimatorThreadPerformanceDeviceTest {
    @Test(timeout = 60_000) fun compareWarmedFourAndTwoThreadInference() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("newsmeadThreadPerf") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val order = List(2) { listOf(4, 2, 2, 4) }.flatten()
        val samples = JSONArray()
        val summary = JSONObject().put("schema", "newsmead_thread_performance_v1")
            .put("device_model", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("model_sha256", MnnEstimator.MODEL_SHA).put("fixture", "SyntheticFixtures.images ramp")
            .put("thread_order", JSONArray(order)).put("warmup_calls_per_model", 5)
            .put("timed_calls_per_block", 12).put("blocks", samples)
            .put("thermal_status_initial", thermalStatus(context))
            .put("measurement", "infer(input) only, including its output copy; one preprocessed synthetic input reused. Output validation/wiping and JSON aggregation are outside per-call timers")
            .put("thread_cpu_scope", "Calling instrumentation thread only; excludes CPU time on native worker threads")
            .put("resource_context", "Both model sessions remain loaded; no affinity/frequency changes. Frequency, contention and scheduler state are uncontrolled")
            .put("limitation", "Isolated camera-free synthetic timing excludes camera, preprocessing, writer, UI and calibration. No real-app speed, actual gaze accuracy, human validity or default promotion established")
        var completed = false
        try {
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            Preprocessor().use { prep ->
                val fixture = SyntheticFixtures.images().first { it.name == "ramp" }
                val input = prep.prepare(fixture.frame, fixture.crops)
                MnnEstimator(context, 4).use { four -> MnnEstimator(context, 2).use { two ->
                    summary.put("four_thread_runtime", four.runtimeDescription)
                        .put("two_thread_runtime", two.runtimeDescription)
                    repeat(5) { four.infer(input).fill(0f); two.infer(input).fill(0f) }
                    val outputs = mutableListOf<FloatArray>()
                    try {
                        val expected = four.infer(input).also { outputs.add(it) }
                        val repeat = four.infer(input).also { outputs.add(it) }
                        val actual = two.infer(input).also { outputs.add(it) }
                        check(outputs.all { it.size == 258 && it.all { value -> value.isFinite() } })
                        var max = 0.0; var sum = 0.0; var repeatMax = 0.0
                        expected.indices.forEach { index ->
                            val delta = abs(expected[index].toDouble() - actual[index])
                            max = maxOf(max, delta); sum += delta
                            repeatMax = maxOf(repeatMax, abs(expected[index].toDouble() - repeat[index]))
                        }
                        summary.put("numerical_comparison", JSONObject().put("feature_count", expected.size)
                            .put("absolute_tolerance", 1e-5f)
                            .put("criterion_source", "NativePipelineTest.modelHas258StableFeaturesAndRejectsBadInputBeforeJni")
                            .put("max_absolute_error", max).put("mean_absolute_error", sum / expected.size)
                            .put("four_thread_repeat_max_absolute_error", repeatMax)
                            .put("four_thread_repeat_passed", repeatMax <= 1e-5f)
                            .put("two_four_parity_passed", max <= 1e-5f)
                            .put("existing_parity_criterion_passed", max <= 1e-5f && repeatMax <= 1e-5f)
                            .put("interpretation", "Reported separately; parity failure does not fail this performance-only diagnostic"))
                    } finally { outputs.forEach { it.fill(0f) } }
                    order.forEachIndexed { block, threads ->
                        val model = if (threads == 4) four else two
                        var wallSum = 0L; var cpuSum = 0L; var wallMax = 0L; var cpuMax = 0L
                        var wallMin = Long.MAX_VALUE; var cpuMin = Long.MAX_VALUE
                        repeat(12) {
                            val wallStart = SystemClock.elapsedRealtimeNanos()
                            val cpuStart = Debug.threadCpuTimeNanos()
                            val output = model.infer(input)
                            val cpuNs = Debug.threadCpuTimeNanos() - cpuStart
                            val wallNs = SystemClock.elapsedRealtimeNanos() - wallStart
                            try { check(output.size == 258 && output.all { it.isFinite() }) }
                            finally { output.fill(0f) }
                            wallSum += wallNs; cpuSum += cpuNs
                            wallMax = maxOf(wallMax, wallNs); cpuMax = maxOf(cpuMax, cpuNs)
                            wallMin = minOf(wallMin, wallNs); cpuMin = minOf(cpuMin, cpuNs)
                        }
                        samples.put(JSONObject().put("block", block).put("threads", threads).put("sample_count", 12)
                            .put("infer_wall_mean_ms", wallSum / 12e6).put("infer_wall_min_ms", wallMin / 1e6)
                            .put("infer_wall_max_ms", wallMax / 1e6).put("infer_thread_cpu_mean_ms", cpuSum / 12e6)
                            .put("infer_thread_cpu_min_ms", cpuMin / 1e6).put("infer_thread_cpu_max_ms", cpuMax / 1e6))
                    }
                } }
            }
            completed = true
        } catch (failure: Throwable) {
            summary.put("failure", failure.javaClass.simpleName)
            throw failure
        } finally {
            summary.put("measurement_completed", completed).put("thermal_status_final", thermalStatus(context))
                .put("completed_timed_samples", samples.length() * 12)
            File(context.cacheDir, "newsmead-thread-performance-summary.json").writeText(summary.toString(2))
        }
        check(completed) { "Timing measurement incomplete" }
    }

    private fun thermalStatus(context: Context): Any = if (android.os.Build.VERSION.SDK_INT >= 29)
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus ?: JSONObject.NULL
    else JSONObject.NULL
}
