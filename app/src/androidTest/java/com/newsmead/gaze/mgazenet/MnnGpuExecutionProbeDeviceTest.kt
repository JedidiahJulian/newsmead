package com.newsmead.gaze.mgazenet

import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import kotlin.math.abs

/** Camera-free support/repeatability probe; no CPU reference session or calibration access. */
@RunWith(AndroidJUnit4::class)
class MnnGpuExecutionProbeDeviceTest {
    @Test fun probeExplicitOpenClExecutionOnSyntheticFixtures() {
        assumeTrue("Explicit GPU probe opt-in required",
            InstrumentationRegistry.getArguments().getString("newsmeadMnnGpuProbe") == "true")
        assumeTrue("Debug probe only", BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = JSONArray()
        val summary = JSONObject().put("schema", "newsmead_mnn_gpu_probe_v1")
            .put("mnn_commit", "d407447ed56c4121a11ccbd266dc184ca1ead0c2")
            .put("device_model", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("pid", Process.myPid()).put("model_sha256", MnnEstimator.MODEL_SHA)
            .put("requested_backend", "OpenCL (3)").put("precision", "Precision_Normal: GPU fp16 storage/fp32 compute; not CPU float32 parity")
            .put("mode", "MNN_GPU_TUNING_FAST")
            .put("backup_backend", "ScheduleConfig default CPU; not controlled; per-operator CPU fallback is not excluded")
            .put("backend_scope", "getSessionInfo BACKENDS[0] reports main pipeline forward type only, not every operator")
            .put("copy_layout", "Tensor map/unmap CAFFE float32 NCHW; output wait MAP_TENSOR_READ,true then map/read/unmap")
            .put("measurement", "SystemClock.elapsedRealtimeNanos around nativeRun only after warmup; includes JNI/input validation/temporary copies/upload/inference/synchronization/output copy; excludes preprocessing/output validation/wiping")
            .put("repeatability_criterion", "Same GPU session/device repeat maximum absolute error <=1e-5; not CPU/GPU numerical parity")
            .put("warmup_calls_per_fixture", WARMUP).put("timed_calls_per_fixture", REPEATS)
            .put("fixtures", fixtures)
            .put("limitation", "Synthetic camera-free support probe only; no gaze accuracy, human validity, full-app FPS, calibration compatibility or adoption claim")
        var handle = 0L
        var loaded = false
        var completed = false
        var closed = false
        var failure: Throwable? = null
        var bytes: ByteArray? = null
        try {
            summary.put("process_start_identity", processStartIdentity())
            bytes = context.assets.open("base.mnn").use { it.readBytes() }
            check(MnnEstimator.sha256(bytes) == MnnEstimator.MODEL_SHA) { "Model hash mismatch" }
            MnnGpuProbeNative.load(); loaded = true
            handle = MnnGpuProbeNative.nativeCreate(bytes)
            check(handle != 0L) { "GPU probe returned invalid handle" }
            val backend = MnnGpuProbeNative.nativeMainBackend(handle)
            summary.put("actual_main_backend", backend)
            check(backend == 3) { "UnsupportedOpenCL: main backend is $backend; CPU fallback cannot pass" }
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            val images = SyntheticFixtures.images()
            try {
                Preprocessor().use { prep ->
                    images.forEachIndexed { index, fixture ->
                        val input = prep.prepare(fixture.frame, fixture.crops)
                        if (index == 0) {
                            val invalid = FloatArray(1)
                            try {
                                val invalidFailure = runCatching {
                                    MnnGpuProbeNative.nativeRun(handle, invalid, input.left, input.right, input.rect).fill(0f)
                                }.exceptionOrNull()
                                check(invalidFailure is IllegalArgumentException) { "Invalid input length was not rejected before inference: $invalidFailure" }
                                summary.put("invalid_length_rejected", true)
                            } finally { invalid.fill(0f) }
                        }
                        repeat(WARMUP) { run(handle, input).fill(0f) }
                        val expected = run(handle, input)
                        try {
                            var maxDifference = 0.0
                            var sumNs = 0L; var minNs = Long.MAX_VALUE; var maxNs = 0L
                            repeat(REPEATS) {
                                val started = SystemClock.elapsedRealtimeNanos()
                                val actual = MnnGpuProbeNative.nativeRun(handle, input.face, input.left, input.right, input.rect)
                                val elapsed = SystemClock.elapsedRealtimeNanos() - started
                                try {
                                    validate(actual)
                                    expected.indices.forEach { i ->
                                        maxDifference = maxOf(maxDifference, abs(expected[i].toDouble() - actual[i]))
                                    }
                                } finally { actual.fill(0f) }
                                check(elapsed >= 0L)
                                sumNs += elapsed; minNs = minOf(minNs, elapsed); maxNs = maxOf(maxNs, elapsed)
                            }
                            val passed = maxDifference.isFinite() && maxDifference <= 1e-5
                            fixtures.put(JSONObject().put("fixture", fixture.name).put("feature_count", expected.size)
                                .put("repeat_max_absolute_error", maxDifference).put("repeat_passed", passed)
                                .put("timed_calls", REPEATS).put("native_call_mean_ms", sumNs / (REPEATS * 1e6))
                                .put("native_call_min_ms", minNs / 1e6).put("native_call_max_ms", maxNs / 1e6))
                            check(passed) { "GPU same-session repeatability exceeds unchanged 1e-5 criterion for ${fixture.name}" }
                        } finally { expected.fill(0f) }
                    }
                }
            } finally { images.forEach { it.frame.rgb.fill(0) } }
            check(fixtures.length() == 3)
            completed = true
        } catch (problem: Throwable) {
            failure = problem
            summary.put("failure_class", problem.javaClass.name).put("failure_message", problem.message ?: JSONObject.NULL)
                .put("unsupported_or_failed", true)
        } finally {
            bytes?.fill(0)
            if (handle != 0L && loaded) {
                try {
                    MnnGpuProbeNative.nativeClose(handle)
                    MnnGpuProbeNative.nativeClose(handle) // Idempotence also verifies the registry removed ownership.
                    val afterClose = runCatching { MnnGpuProbeNative.nativeMainBackend(handle) }.exceptionOrNull()
                    check(afterClose is IllegalStateException) { "Closed handle remains accessible" }
                    closed = true
                } catch (cleanup: Throwable) {
                    summary.put("cleanup_failure_class", cleanup.javaClass.name).put("cleanup_failure_message", cleanup.message ?: JSONObject.NULL)
                    failure?.addSuppressed(cleanup) ?: run { failure = cleanup }
                }
            } else closed = handle == 0L
            summary.put("measurement_completed", completed).put("native_close_verified", closed)
                .put("successful", completed && closed && failure == null)
            File(context.cacheDir, "newsmead-mnn-gpu-probe-summary.json").writeText(summary.toString(2))
        }
        failure?.let { throw it }
        check(completed && closed)
    }

    private fun run(handle: Long, input: Preprocessor.Inputs): FloatArray =
        MnnGpuProbeNative.nativeRun(handle, input.face, input.left, input.right, input.rect).also { output ->
            try { validate(output) } catch (problem: Throwable) { output.fill(0f); throw problem }
        }

    private fun validate(output: FloatArray) {
        check(output.size == 258 && output.all { it.isFinite() }) { "GPU output must contain 258 finite features" }
    }

    private fun processStartIdentity(): String {
        val stat = File("/proc/self/stat").readText()
        val start = stat.substring(stat.lastIndexOf(')') + 1).trim().split(Regex("\\s+"))[19]
        check(start.toLongOrNull() != null)
        val boot = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("unavailable")
        return "pid=${Process.myPid()};start_ticks=$start;boot_id=$boot"
    }

    companion object {
        private const val WARMUP = 3
        private const val REPEATS = 5
    }
}
