package com.newsmead.gaze.mgazenet

/** Post-fit validation uses capture time; output may arrive shortly after the last capture. */
object MgazeNetPostFitTiming {
    const val SETTLE_MS = 3_000.0
    const val CAPTURE_END_MS = 7_000.0
    const val EXTENDED_CAPTURE_END_MS = 11_000.0
    const val COMPARISON_SETTLE_MS = 3_000.0
    const val COMPARISON_CAPTURE_END_MS = 5_500.0
    const val OUTPUT_GRACE_MS = 250.0
    const val MAX_ATTEMPTS = 2

    enum class Decision { WAIT, EXTEND, COMPLETE, RETRY, FAILED }

    fun settleMs(allowLocalRetry: Boolean): Double =
        if (allowLocalRetry) SETTLE_MS else COMPARISON_SETTLE_MS

    fun accepts(checkStartMs: Double, captureMs: Double, outputMs: Double,
        allowLocalRetry: Boolean = false): Boolean {
        val settle = settleMs(allowLocalRetry)
        val endMs = if (allowLocalRetry) EXTENDED_CAPTURE_END_MS else COMPARISON_CAPTURE_END_MS
        return checkStartMs.isFinite() && captureMs.isFinite() && outputMs.isFinite() &&
            captureMs >= checkStartMs + settle &&
            captureMs < checkStartMs + endMs &&
            outputMs < checkStartMs + endMs + OUTPUT_GRACE_MS
    }

    fun finished(checkStartMs: Double, nowMs: Double): Boolean =
        checkStartMs.isFinite() && nowMs >= checkStartMs + CAPTURE_END_MS + OUTPUT_GRACE_MS

    /** Comparison runs retain their fixed check; ordinary runs may extend/retry for sample count only. */
    fun decision(checkStartMs: Double, nowMs: Double, usableSamples: Int, attempt: Int,
        allowLocalRetry: Boolean): Decision {
        require(usableSamples >= 0 && attempt in 1..MAX_ATTEMPTS)
        if (!checkStartMs.isFinite() || !nowMs.isFinite()) return Decision.WAIT
        if (!allowLocalRetry) return if (nowMs >= checkStartMs + COMPARISON_CAPTURE_END_MS + OUTPUT_GRACE_MS)
            Decision.COMPLETE else Decision.WAIT
        if (!finished(checkStartMs, nowMs)) return Decision.WAIT
        if (usableSamples >= MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK) return Decision.COMPLETE
        if (nowMs < checkStartMs + EXTENDED_CAPTURE_END_MS + OUTPUT_GRACE_MS) return Decision.EXTEND
        return if (attempt < MAX_ATTEMPTS) Decision.RETRY else Decision.FAILED
    }

    /** Read-only completion fraction in 0..1 aligned with [decision] completion. */
    fun progress(checkStartMs: Double, nowMs: Double, usableSamples: Int,
        allowLocalRetry: Boolean): Float {
        val settle = settleMs(allowLocalRetry)
        if (!checkStartMs.isFinite() || !nowMs.isFinite() || nowMs < checkStartMs + settle) return 0f
        val endWithGrace = (if (allowLocalRetry) CAPTURE_END_MS else COMPARISON_CAPTURE_END_MS) + OUTPUT_GRACE_MS
        val timeFraction = ((nowMs - (checkStartMs + settle)) / (endWithGrace - settle)).coerceIn(0.0, 1.0)
        if (!allowLocalRetry) return timeFraction.toFloat()
        val sampleFraction = (usableSamples.toDouble() / MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK)
            .coerceIn(0.0, 1.0)
        return minOf(timeFraction, sampleFraction).toFloat()
    }
}
