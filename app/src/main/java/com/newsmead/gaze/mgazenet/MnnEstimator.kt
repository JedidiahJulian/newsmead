package com.newsmead.gaze.mgazenet

import android.content.Context
import com.taobao.android.mnn.MNNNetNative as Native
import java.security.MessageDigest

/** Thread confined. Ordinary CPU execution plus explicitly isolated debug OpenCL exploration. */
class MnnEstimator(
    context: Context,
    private val threads: Int = DEFAULT_CPU_THREADS,
    experimentalBackend: String? = null,
    val precision: Precision = DEFAULT_CPU_PRECISION,
) : AutoCloseable {
    enum class Precision(val nativeMode: Int, val token: String, val label: String) {
        NORMAL(MnnCpuPrecisionNative.PRECISION_NORMAL, "normal", "default precision (not upstream Module low precision)"),
        LOW(MnnCpuPrecisionNative.PRECISION_LOW, "low", "low precision (FP16 / ARM82 when supported)"),
    }

    val executionBackend = if (experimentalBackend == "OPENCL") "OPENCL" else "CPU"
    val runtimeDescription = if (executionBackend == "OPENCL") ExperimentalGpuSession.RUNTIME
        else "MNN 3.6.1 CPU Session, $threads threads, ${precision.label}"
    private var gpu: ExperimentalGpuSession? = null
    private var cpuHandle = 0L
    private var actualMainBackend: Int? = null
    private var net = 0L
    private val session: Long
    private val inputs: LongArray
    private val output: Long
    private val values = FloatArray(258)
    private val performanceHints = InferencePerformanceHints(context.applicationContext)

    init {
        require(threads in 1..8)
        SameModelExecutionTrial.requireBackendAuthorized(context, threads, experimentalBackend)
        val bytes = context.assets.open("base.mnn").use { it.readBytes() }
        if (executionBackend == "OPENCL") {
            try {
                check(sha256(bytes) == MODEL_SHA) { "Model hash mismatch" }
                session = 0L; inputs = LongArray(0); output = 0L
                gpu = ExperimentalGpuSession(context, threads, bytes)
            } finally { bytes.fill(0) }
        } else if (precision == Precision.LOW) {
            try {
                check(sha256(bytes) == MODEL_SHA) { "Model hash mismatch" }
                session = 0L; inputs = LongArray(0); output = 0L
                val createdHandle = performanceHints.construct(threads) {
                    MnnCpuPrecisionNative.nativeCreate(bytes, threads, precision.nativeMode)
                }
                check(createdHandle != 0L) { "MNN CPU precision session failed" }
                cpuHandle = createdHandle
                actualMainBackend = MnnCpuPrecisionNative.nativeMainBackend(createdHandle)
            } catch (error: Throwable) {
                try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
                throw error
            } finally { bytes.fill(0) }
        } else {
            check(sha256(bytes) == MODEL_SHA) { "Model hash mismatch" }
            try {
                val handles = performanceHints.construct(threads) {
                    net = Native.nativeCreateNetFromBuffer(bytes)
                    check(net != 0L) { "MNN model load failed" }
                    val createdSession = Native.nativeCreateSession(net, 0, threads, null, null)
                    check(createdSession != 0L) { "MNN CPU session failed" }
                    val createdInputs = arrayOf("face", "left", "right", "rect").map { name ->
                        Native.nativeGetSessionInput(net, createdSession, name).also { check(it != 0L) { "Missing $name" } }
                    }.toLongArray()
                    val shapes = arrayOf(intArrayOf(1,3,224,224), intArrayOf(1,3,112,112), intArrayOf(1,3,112,112), intArrayOf(1,12))
                    createdInputs.indices.forEach { check(Native.nativeTensorGetDimensions(createdInputs[it]).contentEquals(shapes[it])) { "Unexpected input layout" } }
                    val createdOutput = Native.nativeGetSessionOutput(net, createdSession, "output_0")
                    check(createdOutput != 0L && Native.nativeTensorGetData(createdOutput, null) == 258) { "Unexpected output shape" }
                    Handles(createdSession, createdInputs, createdOutput)
                }
                session = handles.session; inputs = handles.inputs; output = handles.output
                actualMainBackend = 0
            } catch (error: Throwable) {
                try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
                throw error
            }
        }
    }

    fun infer(input: Preprocessor.Inputs): FloatArray {
        gpu?.let { return it.infer(input) }
        val hintStarted = performanceHints.beginTask()
        try {
            if (precision == Precision.LOW) {
                check(cpuHandle != 0L) { "MNN CPU precision session is closed" }
                validateInput(input.face, FACE_SIZE)
                validateInput(input.left, EYE_SIZE)
                validateInput(input.right, EYE_SIZE)
                validateInput(input.rect, RECT_SIZE)
                check(MnnCpuPrecisionNative.nativeRun(cpuHandle, input.face, input.left, input.right, input.rect, values) == 0) {
                    "MNN inference failed"
                }
            } else {
                check(net != 0L)
                setInput(FACE_INPUT, input.face, FACE_SIZE)
                setInput(LEFT_INPUT, input.left, EYE_SIZE)
                setInput(RIGHT_INPUT, input.right, EYE_SIZE)
                setInput(RECT_INPUT, input.rect, RECT_SIZE)
                check(Native.nativeRunSession(net, session) == 0) { "MNN inference failed" }
                check(Native.nativeTensorGetData(output, values) != 0)
            }
            check(FloatInputValidation.allFinite(values)) { "Non-finite model output" }
            return values.copyOf().also { values.fill(0f) }
        } finally { if (hintStarted != 0L) performanceHints.endTask(hintStarted) }
    }

    private fun validateInput(values: FloatArray, expectedSize: Int) {
        // JNI writes directly into native CPU tensors; reject malformed data before native code.
        require(values.size == expectedSize && FloatInputValidation.allFinite(values))
    }

    private fun setInput(index: Int, values: FloatArray, expectedSize: Int) {
        validateInput(values, expectedSize)
        Native.nativeSetInputFloatData(net, inputs[index], values)
    }

    /** Owner-thread opt-in only. Default remains disabled, including ordinary diagnostics. */
    fun setPerformanceHintsEnabled(enabled: Boolean) {
        if (executionBackend == "OPENCL") {
            check(!enabled) { "Performance hints are unavailable for experimental OpenCL execution" }
            return
        }
        performanceHints.setEnabled(enabled)
    }
    fun performanceHintsSnapshot(): Map<String, Any?> = gpu?.performanceHintsSnapshot() ?: performanceHints.snapshot()
    fun executionSnapshot(): Map<String, Any?> = gpu?.snapshot() ?: (performanceHintsSnapshot() +
        mapOf(
            "backend" to "CPU",
            "runtime" to runtimeDescription,
            "precision" to precision.token,
            "actual_main_backend" to actualMainBackend,
        ))

    override fun close() {
        if (executionBackend == "OPENCL") {
            try { gpu?.close() } finally { values.fill(0f) }
            return
        }
        try { performanceHints.close() }
        finally {
            values.fill(0f)
            if (cpuHandle != 0L) {
                val handle = cpuHandle
                cpuHandle = 0L
                MnnCpuPrecisionNative.nativeClose(handle)
            }
            if (net != 0L) { Native.nativeReleaseNet(net); net = 0L }
        }
    }
    private data class Handles(val session: Long, val inputs: LongArray, val output: Long)
    companion object {
        const val DEFAULT_CPU_THREADS = 2
        val DEFAULT_CPU_PRECISION = Precision.LOW
        const val DEFAULT_CPU_PRECISION_TOKEN = "low"
        private const val FACE_INPUT = 0
        private const val LEFT_INPUT = 1
        private const val RIGHT_INPUT = 2
        private const val RECT_INPUT = 3
        private const val FACE_SIZE = 3 * 224 * 224
        private const val EYE_SIZE = 3 * 112 * 112
        private const val RECT_SIZE = 12
        const val MODEL_SHA = "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96"
        const val RUNTIME = "MNN 3.6.1 CPU Session, $DEFAULT_CPU_THREADS threads, low precision (FP16 / ARM82 when supported)"
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
