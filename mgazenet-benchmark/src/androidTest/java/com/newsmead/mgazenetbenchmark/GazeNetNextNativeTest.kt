package com.newsmead.mgazenetbenchmark

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/** Camera-free checks only. Building this APK does not execute Android inference. */
class GazeNetNextNativeTest {
    private fun reference(context: Context): JSONObject {
        assumeTrue("Build with -Pgazefollower2CandidateDir to include the candidate",
            context.assets.list(GazeNetNextCandidate.ASSET_DIRECTORY)?.contains("candidate.json") == true)
        val bytes = context.assets.open("${GazeNetNextCandidate.ASSET_DIRECTORY}/candidate.json").use { it.readBytes() }
        check(MnnEstimator.sha256(bytes) == GazeNetNextCandidate.REFERENCE_SHA)
        return JSONObject(bytes.toString(Charsets.UTF_8)).also {
            check(it.getString("outcome") == "host_parity_passed")
            check(it.getString("source_sha256") == GazeNetNextCandidate.ONNX_SHA)
            check(it.getString("model_sha256") == GazeNetNextCandidate.MNN_SHA)
            check(it.getString("model_id") == GazeNetNextCandidate.ID)
        }
    }

    private fun tensor(context: Context, descriptor: JSONObject): FloatArray {
        val name = descriptor.getString("file")
        require(name.matches(Regex("(ramp|clipped|channels)_(face|left|right|rect)\\.f32")))
        val bytes = context.assets.open("${GazeNetNextCandidate.ASSET_DIRECTORY}/$name").use { it.readBytes() }
        check(MnnEstimator.sha256(bytes) == descriptor.getString("sha256"))
        val count = descriptor.getInt("count")
        check(bytes.size == count * 4)
        return FloatArray(count).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
    }

    private fun inputs(context: Context, fixture: JSONObject) = Preprocessor.Inputs(
        tensor(context, fixture.getJSONObject("face")), tensor(context, fixture.getJSONObject("left")),
        tensor(context, fixture.getJSONObject("right")), tensor(context, fixture.getJSONObject("rect")))

    @Test fun nativeMatchesPinnedOnnxSyntheticReference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = reference(context)
        val checks = JSONArray()
        val report = JSONObject().put("mode", "gazefollower2_android_synthetic_parity")
            .put("model_id", GazeNetNextCandidate.ID).put("model_sha256", GazeNetNextCandidate.MNN_SHA)
            .put("reference_sha256", GazeNetNextCandidate.REFERENCE_SHA).put("device_model", Build.MODEL)
            .put("runtime", MnnEstimator.RUNTIME).put("human_accuracy_established", false)
            .put("outcome", "failed").put("checks", checks)
        try {
            val tolerances = reference.getJSONObject("tolerances")
            val atol = tolerances.getDouble("atol")
            val rtol = tolerances.getDouble("rtol")
            val fixtures = reference.getJSONArray("fixtures")
            check(fixtures.length() == 3)
            var allPassed = true
            MnnEstimator(context, spec = GazeNetNextCandidate.spec).use { model ->
                for (i in 0 until fixtures.length()) {
                    val fixture = fixtures.getJSONObject(i)
                    val input = inputs(context, fixture)
                    val actual = model.infer(input)
                    val again = model.infer(input)
                    val expected = fixture.getJSONArray("onnx_output")
                    check(expected.length() == 258 && actual.size == 258)
                    var maximum = 0.0
                    var passed = true
                    var repeated = true
                    actual.indices.forEach { j ->
                        val gold = expected.getDouble(j)
                        check(gold.isFinite())
                        val error = abs(actual[j].toDouble() - gold)
                        maximum = max(maximum, error)
                        passed = passed && actual[j].isFinite() && error <= atol + rtol * abs(gold)
                        repeated = repeated && abs(actual[j] - again[j]) <= 1e-5f
                    }
                    allPassed = allPassed && passed && repeated
                    checks.put(JSONObject().put("name", fixture.getString("name")).put("pass", passed)
                        .put("repeat_pass", repeated).put("max_abs", maximum).put("atol", atol).put("rtol", rtol)
                        .put("output", JSONArray(actual.map { it.toDouble() })))
                }
            }
            report.put("outcome", if (allPassed) "android_synthetic_parity_passed" else "android_synthetic_parity_failed")
            assertTrue("Android/ONNX synthetic parity failed; inspect emitted report", allPassed)
        } catch (error: Throwable) {
            report.put("error", error.toString())
            throw error
        } finally {
            val directory = File(context.filesDir, "gazefollower2-synthetic/${SystemClock.elapsedRealtimeNanos()}")
            check(directory.mkdirs())
            val file = File(directory, "report.json")
            file.writeText(report.toString(2))
            instrumentation.sendStatus(0, Bundle().apply { putString("gazefollower2_report", file.absolutePath) })
        }
    }

    @Test fun candidateRejectsMalformedTensors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = reference(context).getJSONArray("fixtures").getJSONObject(0)
        val input = inputs(context, fixture)
        MnnEstimator(context, spec = GazeNetNextCandidate.spec).use { model ->
            val invalid = listOf(input.copy(face = FloatArray(3)),
                input.copy(right = input.right.copyOf().also { it[0] = Float.NaN }),
                input.copy(rect = input.rect.copyOf().also { it[0] = Float.POSITIVE_INFINITY }))
            invalid.forEach { values ->
                try { model.infer(values); fail("Malformed tensor was accepted") }
                catch (_: IllegalArgumentException) { }
            }
            assertEquals(258, model.infer(input).size)
        }
    }
}
