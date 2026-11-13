package com.newsmead.gaze.mgazenet

import android.content.Context
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Isolated debug execution only. Reflection keeps the optional debug bridge out of release linkage. */
internal class ExperimentalGpuSession(context: Context, threads: Int, model: ByteArray) : AutoCloseable {
    private val owner = Thread.currentThread()
    private val api: Api
    private var handle = 0L
    private var closed = false
    private var nativeCloseVerified = false
    private val actualMainBackend: Int

    init {
        SameModelExecutionTrial.requireBackendAuthorized(context, threads, "OPENCL")
        api = bridge(context.classLoader)
        try {
            invoke(api.load)
            handle = invoke(api.create, model) as Long
            check(handle != 0L) { "Experimental OpenCL bridge returned an invalid handle" }
            val actual = invoke(api.backend, handle) as Int
            check(actual == 3) { "UnsupportedOpenCL: main backend is $actual; no silent CPU fallback" }
            actualMainBackend = actual
        } catch (problem: Throwable) {
            try { close() } catch (cleanup: Throwable) { problem.addSuppressed(cleanup) }
            throw problem
        }
    }

    fun infer(input: Preprocessor.Inputs): FloatArray {
        checkOpen()
        require(input.face.size == 3 * 224 * 224 && FloatInputValidation.allFinite(input.face))
        require(input.left.size == 3 * 112 * 112 && FloatInputValidation.allFinite(input.left))
        require(input.right.size == 3 * 112 * 112 && FloatInputValidation.allFinite(input.right))
        require(input.rect.size == 12 && FloatInputValidation.allFinite(input.rect))
        val output = invoke(api.run, handle, input.face, input.left, input.right, input.rect) as? FloatArray
            ?: error("Experimental OpenCL bridge returned no features")
        try {
            check(output.size == 258 && FloatInputValidation.allFinite(output)) { "Non-finite or malformed OpenCL model output" }
            return output
        } catch (problem: Throwable) { output.fill(0f); throw problem }
    }

    fun snapshot(): Map<String, Any?> = linkedMapOf(
        "backend" to "OPENCL", "actual_main_backend" to actualMainBackend, "runtime" to RUNTIME,
        "gpu_mode" to 16, "precision" to "Precision_Normal (fp16 storage/fp32 compute)",
        "copy_layout" to "CAFFE float32 NCHW map/unmap; output wait MAP_TENSOR_READ,true",
        "cpu_fallback_limitation" to "Main pipeline backend only; per-operator CPU fallback not excluded; CPU fallback pool/thread count unobserved",
        "closed" to closed, "native_close_verified" to nativeCloseVerified,
    )

    fun performanceHintsSnapshot(): Map<String, Any?> = linkedMapOf(
        "requested" to false, "active" to false, "status" to "unavailable_for_experimental_opencl",
        "thread_count" to null, "requested_session_threads" to null,
        "initial_process_pool_thread_count" to null, "closed" to closed,
        "membership_status" to "not_observed_for_opencl",
    )

    override fun close() {
        checkOwner()
        if (closed) return
        closed = true
        val releasing = handle
        handle = 0L // At most one native close attempt, even if it throws.
        if (releasing != 0L) {
            invoke(api.close, releasing)
            nativeCloseVerified = true
        }
    }

    private fun checkOwner() { check(Thread.currentThread() === owner) { "Experimental OpenCL calls require the model owner thread" } }
    private fun checkOpen() { checkOwner(); check(!closed && handle != 0L) { "Experimental OpenCL model is closed" } }

    private data class Api(val load: Method, val create: Method, val run: Method, val backend: Method, val close: Method)

    companion object {
        const val RUNTIME = "MNN 3.6.1 OpenCL main backend 3, FAST mode 16, Precision_Normal (fp16 storage/fp32 compute); per-operator CPU fallback not excluded, CPU fallback pool/thread count unobserved"
        @Volatile private var cachedApi: Api? = null
        @Synchronized private fun bridge(loader: ClassLoader): Api {
            cachedApi?.let { return it }
            val type = try {
                Class.forName("com.newsmead.gaze.mgazenet.MnnGpuProbeNative", true, loader)
            } catch (missing: ClassNotFoundException) {
                throw IllegalStateException("Experimental OpenCL requires the optional debug GPU probe bridge", missing)
            }
            return Api(type.getMethod("load"), type.getMethod("nativeCreate", ByteArray::class.java),
                type.getMethod("nativeRun", java.lang.Long.TYPE, FloatArray::class.java,
                    FloatArray::class.java, FloatArray::class.java, FloatArray::class.java),
                type.getMethod("nativeMainBackend", java.lang.Long.TYPE),
                type.getMethod("nativeClose", java.lang.Long.TYPE)).also { cachedApi = it }
        }
        private fun invoke(method: Method, vararg arguments: Any): Any? = try {
            method.invoke(null, *arguments)
        } catch (wrapped: InvocationTargetException) { throw (wrapped.targetException ?: wrapped) }
    }
}
