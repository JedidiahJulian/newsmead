package com.newsmead.gaze.mgazenet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.research.study.AppCpuPlacement
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import kotlin.math.abs

/** Camera-free synthetic feature parity for one unchanged four-thread model session. */
@RunWith(AndroidJUnit4::class)
class AppCpuPlacementParityDeviceTest {
    @Test fun compareSameFourThreadModelBeforeAndDuringF0Placement() {
        assumeTrue("Explicit CPU placement parity opt-in required",
            InstrumentationRegistry.getArguments().getString("newsmeadCpuPlacementParity") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val results = JSONArray()
        val summary = JSONObject().put("schema", "newsmead_cpu_placement_parity_v1")
            .put("device_model", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("pid", android.os.Process.myPid()).put("model_sha256", MnnEstimator.MODEL_SHA)
            .put("reference_threads", 4).put("candidate_threads", 4)
            .put("same_model_session", true).put("requested_candidate_mask", "f0")
            .put("absolute_tolerance", TOLERANCE)
            .put("criterion_source", "NativePipelineTest.modelHas258StableFeaturesAndRejectsBadInputBeforeJni")
            .put("fixture_source", "SyntheticFixtures.images (ramp, clipped, channels); public synthetic inputs")
            .put("frequency_resource_context", "Whole-app process CPU placement only; CPU frequency, governor, thermal state and competing work are unmeasured and uncontrolled")
            .put("limitation", "Synthetic feature parity only; no actual gaze accuracy, human validity or performance benefit established")
            .put("fixtures", results)
        var passed = true
        var allRestored = true
        var maximum = 0.0
        var repeatMaximum = 0.0
        var total = 0.0
        var featureCount = 0
        var failure: Throwable? = null
        try {
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            Preprocessor().use { prep ->
                // Construct once before any placement change; retain the same native session/kernel setup.
                MnnEstimator(context, 4).use { model ->
                    summary.put("runtime", model.runtimeDescription)
                    SyntheticFixtures.images().forEach { fixture ->
                        val record = JSONObject().put("fixture", fixture.name)
                        results.put(record)
                        val placement = JSONObject()
                        record.put("cpu_placement", placement)
                        val lease = AppCpuPlacement.Lease("f0", placement)
                        val outputs = mutableListOf<FloatArray>()
                        var fixtureFailure: Throwable? = null
                        try {
                            val input = prep.prepare(fixture.frame, fixture.crops)
                            val expected = model.infer(input).also { outputs.add(it) }
                            val repeat = model.infer(input).also { outputs.add(it) }
                            lease.apply()
                            val actual = model.infer(input).also { outputs.add(it) }
                            check(expected.size == 258 && repeat.size == expected.size && actual.size == expected.size)
                            check(outputs.all { array -> array.all { it.isFinite() } })
                            var fixtureMaximum = 0.0
                            var fixtureTotal = 0.0
                            var repeatMax = 0.0
                            expected.indices.forEach { index ->
                                val difference = abs(expected[index].toDouble() - actual[index])
                                fixtureMaximum = maxOf(fixtureMaximum, difference)
                                fixtureTotal += difference
                                repeatMax = maxOf(repeatMax, abs(expected[index].toDouble() - repeat[index]))
                            }
                            val fixturePassed = fixtureMaximum <= TOLERANCE && repeatMax <= TOLERANCE
                            passed = passed && fixturePassed
                            maximum = maxOf(maximum, fixtureMaximum)
                            repeatMaximum = maxOf(repeatMaximum, repeatMax)
                            total += fixtureTotal
                            featureCount += expected.size
                            record.put("feature_count", expected.size).put("max_absolute_error", fixtureMaximum)
                                .put("mean_absolute_error", fixtureTotal / expected.size)
                                .put("four_thread_repeat_max_absolute_error", repeatMax).put("passed", fixturePassed)
                        } catch (problem: Throwable) {
                            fixtureFailure = problem
                            record.put("failure", problem.javaClass.simpleName).put("passed", false)
                        } finally {
                            // Restore before the next fixture; never run another command after a blocker.
                            val restoreFailure = lease.restore()
                            val restored = restoreFailure == null && placement.optBoolean("restoration_successful", false)
                            allRestored = allRestored && restored
                            outputs.forEach { it.fill(0f) }
                            if (restoreFailure != null) {
                                if (fixtureFailure == null) fixtureFailure = restoreFailure
                                else fixtureFailure?.addSuppressed(restoreFailure)
                            }
                        }
                        fixtureFailure?.let { throw it }
                    }
                }
            }
        } catch (problem: Throwable) {
            passed = false
            failure = problem
            summary.put("failure", problem.javaClass.simpleName)
        } finally {
            passed = passed && featureCount == 3 * 258 && results.length() == 3 && allRestored && failure == null
            summary.put("feature_count", featureCount).put("max_absolute_error", maximum)
                .put("mean_absolute_error", if (featureCount > 0) total / featureCount else JSONObject.NULL)
                .put("four_thread_repeat_max_absolute_error", repeatMaximum)
                .put("all_restorations_verified", allRestored).put("passed", passed)
            File(context.cacheDir, "newsmead-cpu-placement-parity-summary.json").writeText(summary.toString(2))
        }
        assertTrue("CPU placement fixture parity/restoration failed; inspect aggregate summary", passed && allRestored)
    }

    companion object { private const val TOLERANCE = 1e-5f }
}
