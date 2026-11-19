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

/** Camera-free opt-in comparison of CPU2 normal (FP32) vs low (FP16/ARM82) inference. */
@RunWith(AndroidJUnit4::class)
class EstimatorPrecisionPerformanceDeviceTest {
    @Test(timeout = 60_000) fun compareNormalAndLowPrecisionCpu2Inference() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("newsmeadPrecisionPerf") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val order = List(2) { listOf("normal", "low", "low", "normal") }.flatten()
        val blocks = JSONArray()
        val fixtureDeltas = JSONArray()
        val summary = JSONObject()
            .put("schema", "newsmead_precision_performance_v1")
            .put("device_model", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("threads", 2)
            .put("model_sha256", MnnEstimator.MODEL_SHA)
            .put("order", JSONArray(order))
            .put("warmup_calls_per_model", WARMUP)
            .put("timed_calls_per_block", CALLS_PER_BLOCK)
            .put("thermal_status_initial", thermalStatus(context))
            .put("fixture_deltas", fixtureDeltas)
            .put("blocks", blocks)
            .put("limitation", "Isolated camera-free synthetic timing and feature delta only; no full-app FPS, human accuracy or calibration compatibility established")
        var completed = false
        try {
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            Preprocessor().use { prep ->
                val fixtures = SyntheticFixtures.images()
                val inputs = fixtures.map { f ->
                    prep.prepare(f.frame, f.crops).let {
                        Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
                    }
                }
                MnnEstimator(context, 2, precision = MnnEstimator.Precision.NORMAL).use { normal ->
                    MnnEstimator(context, 2, precision = MnnEstimator.Precision.LOW).use { low ->
                        summary.put("normal_runtime", normal.runtimeDescription)
                            .put("low_runtime", low.runtimeDescription)
                            .put("normal_main_backend", normal.executionSnapshot()["actual_main_backend"])
                            .put("low_main_backend", low.executionSnapshot()["actual_main_backend"])
                        repeat(WARMUP) {
                            normal.infer(inputs[0]).fill(0f)
                            low.infer(inputs[0]).fill(0f)
                        }
                        fixtures.forEachIndexed { index, fixture ->
                            val expected = normal.infer(inputs[index])
                            val actual = low.infer(inputs[index])
                            try {
                                check(expected.size == 258 && actual.size == 258)
                                var maxAbs = 0.0
                                var sumAbs = 0.0
                                expected.indices.forEach { i ->
                                    val delta = abs(expected[i].toDouble() - actual[i])
                                    maxAbs = maxOf(maxAbs, delta)
                                    sumAbs += delta
                                }
                                fixtureDeltas.put(
                                    JSONObject()
                                        .put("fixture", fixture.name)
                                        .put("feature_count", expected.size)
                                        .put("max_absolute_error", maxAbs)
                                        .put("mean_absolute_error", sumAbs / expected.size)
                                )
                            } finally {
                                expected.fill(0f)
                                actual.fill(0f)
                            }
                        }
                        var badInputRejected = true
                        for (model in listOf(normal, low)) {
                            val rejected = runCatching { model.infer(inputs[0].copy(face = FloatArray(3))).fill(0f) }
                                .exceptionOrNull() is IllegalArgumentException
                            badInputRejected = badInputRejected && rejected
                        }
                        summary.put("bad_input_rejected", badInputRejected)
                        check(badInputRejected) { "Bad input was not rejected before native execution" }

                        val timedInput = inputs[0]
                        order.forEachIndexed { blockIndex, mode ->
                            val model = if (mode == "normal") normal else low
                            var wallSum = 0L; var cpuSum = 0L
                            var wallMin = Long.MAX_VALUE; var wallMax = 0L
                            var cpuMin = Long.MAX_VALUE; var cpuMax = 0L
                            repeat(CALLS_PER_BLOCK) {
                                val wallStart = SystemClock.elapsedRealtimeNanos()
                                val cpuStart = Debug.threadCpuTimeNanos()
                                val out = model.infer(timedInput)
                                val cpuNs = Debug.threadCpuTimeNanos() - cpuStart
                                val wallNs = SystemClock.elapsedRealtimeNanos() - wallStart
                                try {
                                    check(out.size == 258 && out.all { it.isFinite() })
                                } finally {
                                    out.fill(0f)
                                }
                                wallSum += wallNs; cpuSum += cpuNs
                                wallMin = minOf(wallMin, wallNs); wallMax = maxOf(wallMax, wallNs)
                                cpuMin = minOf(cpuMin, cpuNs); cpuMax = maxOf(cpuMax, cpuNs)
                            }
                            blocks.put(
                                JSONObject()
                                    .put("block", blockIndex)
                                    .put("precision", mode)
                                    .put("sample_count", CALLS_PER_BLOCK)
                                    .put("infer_wall_mean_ms", wallSum / (CALLS_PER_BLOCK * 1e6))
                                    .put("infer_wall_min_ms", wallMin / 1e6)
                                    .put("infer_wall_max_ms", wallMax / 1e6)
                                    .put("infer_thread_cpu_mean_ms", cpuSum / (CALLS_PER_BLOCK * 1e6))
                                    .put("infer_thread_cpu_min_ms", cpuMin / 1e6)
                                    .put("infer_thread_cpu_max_ms", cpuMax / 1e6)
                            )
                        }
                    }
                }
            }
            completed = true
        } catch (failure: Throwable) {
            summary.put("failure", failure.javaClass.simpleName)
            throw failure
        } finally {
            summary.put("measurement_completed", completed)
                .put("thermal_status_final", thermalStatus(context))
                .put("completed_timed_samples", blocks.length() * CALLS_PER_BLOCK)
            File(context.cacheDir, "newsmead-precision-performance-summary.json").writeText(summary.toString(2))
        }
        check(completed) { "Precision performance measurement incomplete" }
    }

    private fun thermalStatus(context: Context): Any = if (android.os.Build.VERSION.SDK_INT >= 29)
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus ?: JSONObject.NULL
    else JSONObject.NULL

    companion object {
        private const val WARMUP = 5
        private const val CALLS_PER_BLOCK = 20
    }
}
