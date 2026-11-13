package com.newsmead.gaze.mgazenet

import android.content.Context
import android.os.Bundle
import com.newsmead.BuildConfig
import com.newsmead.research.study.StudyCollection

/** Explicit isolated diagnostic only; never a persisted runtime preference. */
internal object SameModelExecutionTrial {
    const val THREADS = "newsmeadExperimentalMnnThreads"
    const val LOCALIZER = "newsmeadExperimentalLocalizer"
    const val BACKEND = "newsmeadExperimentalMnnBackend"
    const val OPT_IN = "newsmeadSameModelExecutionTrial"
    private val experimentalCpuAcquisition = Regex("[0-9]+x[0-9]+:(0|90|180|270):CPU_experimental")
    fun requested(context: Context, extras: Bundle?): Int? {
        val present = extras?.containsKey(THREADS) == true
        val optedIn = extras?.getBoolean(OPT_IN, false) == true
        require(present == optedIn) { "Execution trial requires explicit opt-in and thread count" }
        val threads = if (present) extras!!.getInt(THREADS) else null
        val localizer = if (extras?.containsKey(LOCALIZER) == true) {
            requireNotNull(extras.getString(LOCALIZER)) { "Explicit localizer must be CPU or GPU" }
        } else null
        val backend = if (extras?.containsKey(BACKEND) == true) {
            requireNotNull(extras.getString(BACKEND)) { "Explicit gaze backend must be CPU or OPENCL" }
        } else null
        requireAuthorized(context, threads, localizer, backend)
        return threads
    }
    fun requestedLocalizer(context: Context, extras: Bundle?): String? {
        requested(context, extras)
        return extras?.getString(LOCALIZER)
    }
    fun requestedBackend(context: Context, extras: Bundle?): String? {
        requested(context, extras)
        return extras?.getString(BACKEND)
    }
    fun requireBackendAuthorized(context: Context, threads: Int?, backend: String?) {
        if (backend != null) requireAuthorized(context, threads, backend = backend)
    }

    fun requireAuthorized(context: Context, threads: Int?, localizer: String? = null, backend: String? = null) {
        if (localizer != null) {
            require(localizer == "CPU" || localizer == "GPU") { "Explicit localizer must be CPU or GPU" }
            require(threads == 2) { "Explicit localizer requires the CPU2 execution trial" }
        }
        if (backend != null) {
            require(backend == "CPU" || backend == "OPENCL") { "Explicit gaze backend must be CPU or OPENCL" }
            require(threads == 2) { "Explicit gaze backend requires the isolated two-thread reference configuration" }
        }
        if (threads == null) return
        require(threads in 1..4) { "Execution trial supports only CPU1, CPU2, CPU3 or CPU4" }
        check(BuildConfig.DEBUG && StudyCollection.isIsolatedLivePerformanceStorage(context)) {
            "Execution trial requires debug isolated live-performance storage before capture"
        }
    }
    /** Caller must reauthorize first; only this exact GPU-reference exception is experimental. */
    fun acquisitionMatches(reference: String, actual: String, localizer: String?): Boolean =
        reference == actual || (localizer == "CPU" &&
            experimentalCpuAcquisition.matches(actual) &&
            reference == actual.removeSuffix("CPU_experimental") + "GPU")
}
