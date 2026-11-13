package com.newsmead.gaze.mgazenet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import kotlin.math.abs

/** Camera-free exploratory thread comparison using existing public synthetic images/crops.
 * The 1e-5 absolute tolerance is NativePipelineTest's existing feature repeatability criterion;
 * passing it establishes fixture parity only, not gaze accuracy or human validity.
 */
@RunWith(AndroidJUnit4::class)
class EstimatorThreadParityDeviceTest {
    @Test fun compareExplicitFourAndCandidateThreadsOnExistingSyntheticFixtures() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit exploratory thread-parity opt-in required",
            args.getString("newsmeadThreadParity") == "true")
        val requestedCandidate = args.getString("newsmeadThreadParityCandidate") ?: "2"
        require(requestedCandidate == "2" || requestedCandidate == "3") {
            "newsmeadThreadParityCandidate must be 2 or 3"
        }
        val candidateThreads = requestedCandidate.toInt()
        check(OpenCVLoader.initLocal())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val results = JSONArray()
        var passed = true
        var maximum = 0.0; var total = 0.0; var features = 0
        Preprocessor().use { prep ->
            MnnEstimator(context, 4).use { reference ->
                MnnEstimator(context, candidateThreads).use { candidate ->
                    SyntheticFixtures.images().forEach { fixture ->
                        val input = prep.prepare(fixture.frame, fixture.crops)
                        val expected = reference.infer(input)
                        val repeat = reference.infer(input)
                        val actual = candidate.infer(input)
                        try {
                            check(expected.size == 258 && repeat.size == expected.size && actual.size == expected.size)
                            check(expected.all { it.isFinite() } && repeat.all { it.isFinite() } && actual.all { it.isFinite() }) {
                                "Non-finite feature in CPU4/CPU$candidateThreads comparison"
                            }
                            var fixtureMax = 0.0; var fixtureTotal = 0.0; var repeatMax = 0.0
                            expected.indices.forEach { index ->
                                val difference = abs(expected[index].toDouble() - actual[index])
                                fixtureMax = maxOf(fixtureMax, difference); fixtureTotal += difference
                                repeatMax = maxOf(repeatMax, abs(expected[index].toDouble() - repeat[index]))
                            }
                            val fixturePassed = fixtureMax.isFinite() && fixtureTotal.isFinite() && repeatMax.isFinite() &&
                                fixtureMax <= TOLERANCE && repeatMax <= TOLERANCE
                            passed = passed && fixturePassed
                            maximum = maxOf(maximum, fixtureMax); total += fixtureTotal; features += expected.size
                            results.put(JSONObject().put("fixture", fixture.name).put("feature_count", expected.size)
                                .put("max_absolute_error", fixtureMax).put("mean_absolute_error", fixtureTotal / expected.size)
                                .put("four_thread_repeat_max_absolute_error", repeatMax).put("passed", fixturePassed))
                        } finally { expected.fill(0f); repeat.fill(0f); actual.fill(0f) }
                    }
                }
            }
        }
        passed = passed && features > 0 && maximum.isFinite() && total.isFinite()
        val summary = JSONObject().put("schema", "newsmead_thread_parity_v1")
            .put("device_model", android.os.Build.MODEL).put("reference_threads", 4).put("candidate_threads", candidateThreads)
            .put("model_sha256", MnnEstimator.MODEL_SHA).put("absolute_tolerance", TOLERANCE)
            .put("criterion_source", "NativePipelineTest.modelHas258StableFeaturesAndRejectsBadInputBeforeJni")
            .put("fixture_source", "SyntheticFixtures.images (ramp, clipped, channels); no private data")
            .put("max_absolute_error", maximum).put("mean_absolute_error", total / features)
            .put("feature_count", features).put("passed", passed).put("fixtures", results)
            .put("native_pool_limitation", "CPU4 reference is constructed first in this test, then CPU$candidateThreads; both sessions share the process native pool (first CPU4 pool in a fresh process). Earlier process construction may predate this test. This is not a fresh-pool performance comparison")
            .put("limitation", "Exploratory feature parity only; a parity failure is not a model accuracy failure. No calibration, accuracy, runtime benefit or default promotion established")
        val summaryName = if (candidateThreads == 2) "newsmead-thread-parity-summary.json"
            else "newsmead-thread-parity-cpu3-summary.json"
        File(context.cacheDir, summaryName).writeText(summary.toString(2))
        assertTrue("CPU$candidateThreads/CPU4 fixture parity exceeds existing 1e-5 repeatability tolerance; inspect $summaryName", passed)
    }
    companion object { private const val TOLERANCE = 1e-5f }
}
