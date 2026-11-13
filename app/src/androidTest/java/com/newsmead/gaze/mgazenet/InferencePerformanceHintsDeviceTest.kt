package com.newsmead.gaze.mgazenet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Camera-free API/membership/parity probe. Unsupported hinting is an explicit normal outcome. */
@RunWith(AndroidJUnit4::class)
class InferencePerformanceHintsDeviceTest {
    @Test fun probeOptionalHintsWithoutChangingModelMath() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("newsmeadPerfHintProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val summary = JSONObject().put("schema", "newsmead_inference_performance_hints_probe_v1")
            .put("device_model", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("threads", 4).put("model_sha256", MnnEstimator.MODEL_SHA).put("absolute_tolerance", 1e-5f)
            .put("criterion_source", "NativePipelineTest.modelHas258StableFeaturesAndRejectsBadInputBeforeJni")
            .put("limitation", "Synthetic feature parity and API lifecycle only; no accuracy, real-app gain or scheduler effect established. Unsupported sessions are reported, not treated as active")
        val executor = Executors.newSingleThreadExecutor()
        var failure: Throwable? = null
        var completed = false
        try {
            val future = executor.submit(Callable {
                check(OpenCVLoader.initLocal())
                val rows = JSONArray(); summary.put("fixtures", rows)
                val copiedInputs = mutableListOf<Preprocessor.Inputs>()
                val references = mutableListOf<FloatArray>()
                var model: MnnEstimator? = null
                try {
                    Preprocessor().use { prep ->
                        val created = MnnEstimator(context, 4); model = created
                        created.use { live ->
                            summary.put("runtime", live.runtimeDescription)
                                .put("initial", JSONObject(live.performanceHintsSnapshot()))
                            check(live.performanceHintsSnapshot()["active"] == false)
                            val fixtures = SyntheticFixtures.images()
                            fixtures.forEach { fixture ->
                                val prepared = prep.prepare(fixture.frame, fixture.crops)
                                val input = Preprocessor.Inputs(prepared.face.copyOf(), prepared.left.copyOf(),
                                    prepared.right.copyOf(), prepared.rect.copyOf())
                                copiedInputs.add(input)
                                references.add(live.infer(input))
                            }
                            live.setPerformanceHintsEnabled(true) // One support attempt; no unsupported-device retries.
                            val configured = live.performanceHintsSnapshot()
                            summary.put("configured", JSONObject(configured))
                            if (configured["active"] == true) check(configured["thread_count"] == 4)
                            copiedInputs.forEachIndexed { index, input ->
                                val outputs = mutableListOf<FloatArray>()
                                try {
                                    val actual = live.infer(input).also { outputs.add(it) }
                                    val repeat = live.infer(input).also { outputs.add(it) }
                                    val expected = references[index]
                                    check(expected.size == 258 && actual.size == expected.size && repeat.size == expected.size)
                                    var maximum = 0.0; var repeatMax = 0.0; var total = 0.0
                                    expected.indices.forEach { feature ->
                                        val delta = abs(expected[feature].toDouble() - actual[feature])
                                        maximum = maxOf(maximum, delta); total += delta
                                        repeatMax = maxOf(repeatMax, abs(actual[feature].toDouble() - repeat[feature]))
                                    }
                                    val passed = maximum <= 1e-5f && repeatMax <= 1e-5f
                                    rows.put(JSONObject().put("fixture", fixtures[index].name).put("feature_count", expected.size)
                                        .put("max_absolute_error", maximum).put("mean_absolute_error", total / expected.size)
                                        .put("repeat_max_absolute_error", repeatMax).put("passed", passed))
                                    check(passed) { "Hint probe feature parity exceeded unchanged tolerance" }
                                } finally { outputs.forEach { it.fill(0f) } }
                            }
                            var rejected = false
                            try { live.infer(copiedInputs.first().copy(face = FloatArray(3))).fill(0f) }
                            catch (_: IllegalArgumentException) { rejected = true }
                            check(rejected) { "Invalid model input was not rejected" }
                            summary.put("bad_input_original_error_preserved", rejected)
                            val measured = live.performanceHintsSnapshot()
                            summary.put("after_inference", JSONObject(measured))
                            val reports = measured["reports_succeeded"] as Long
                            val attempts = measured["report_attempts"] as Long
                            check(reports <= attempts)
                            if (configured["active"] == true && measured["api_errors"] == 0L)
                                check(attempts == 7L && reports == 7L) { "Expected one report for each of six valid and one rejected infer tasks" }
                            if (configured["active"] == false) check(attempts == 0L && reports == 0L)
                            if (reports > 0) check((measured["last_actual_duration_ns"] as Long) > 0L)
                            live.setPerformanceHintsEnabled(false)
                            val disabled = live.performanceHintsSnapshot()
                            summary.put("disabled", JSONObject(disabled))
                            check(disabled["active"] == false && disabled["session_close_verified"] == true)
                        }
                    }
                } finally {
                    references.forEach { it.fill(0f) }
                    copiedInputs.forEach { input -> listOf(input.face, input.left, input.right, input.rect).forEach { it.fill(0f) } }
                    model?.performanceHintsSnapshot()?.let { summary.put("closed", JSONObject(it)) }
                }
                val audit = checkNotNull(model).performanceHintsSnapshot()
                check(audit["closed"] == true && audit["session_close_verified"] == true)
                true
            })
            try { completed = future.get(60, TimeUnit.SECONDS) }
            catch (problem: ExecutionException) { throw (problem.cause ?: problem) }
            finally { if (!future.isDone) future.cancel(false) }
        } catch (problem: Throwable) {
            failure = problem
        } finally {
            executor.shutdown()
            var closed = try { executor.awaitTermination(5, TimeUnit.SECONDS) }
            catch (problem: InterruptedException) { if (failure == null) failure = problem; false }
            if (!closed) {
                executor.shutdownNow()
                closed = try { executor.awaitTermination(5, TimeUnit.SECONDS) }
                catch (problem: InterruptedException) { if (failure == null) failure = problem; false }
                if (failure == null) failure = IllegalStateException("Probe executor shutdown unverified")
            }
            // If native work is still running, avoid reading a concurrently mutated worker summary.
            val finalSummary = if (closed) summary else JSONObject()
                .put("schema", "newsmead_inference_performance_hints_probe_v1")
                .put("worker_summary_unavailable", "Executor still active; model/session cleanup unverified")
            failure?.let { finalSummary.put("failure", it.javaClass.simpleName) }
            finalSummary.put("executor_terminated", closed).put("measurement_completed", completed)
                .put("passed", completed && failure == null && closed)
            File(context.cacheDir, "newsmead-performance-hints-probe-summary.json").writeText(finalSummary.toString(2))
        }
        failure?.let { throw it }
        check(completed)
    }
}
