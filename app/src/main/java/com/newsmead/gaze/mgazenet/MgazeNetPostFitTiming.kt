package com.newsmead.gaze.mgazenet

/** Post-fit validation uses capture time; output may arrive shortly after the last capture. */
object MgazeNetPostFitTiming {
    const val SETTLE_MS = 3_000.0
    const val CAPTURE_END_MS = 7_000.0
    const val OUTPUT_GRACE_MS = 250.0

    fun accepts(checkStartMs: Double, captureMs: Double, outputMs: Double): Boolean =
        checkStartMs.isFinite() && captureMs.isFinite() && outputMs.isFinite() &&
            captureMs >= checkStartMs + SETTLE_MS &&
            captureMs < checkStartMs + CAPTURE_END_MS &&
            outputMs < checkStartMs + CAPTURE_END_MS + OUTPUT_GRACE_MS

    fun finished(checkStartMs: Double, nowMs: Double): Boolean =
        checkStartMs.isFinite() && nowMs >= checkStartMs + CAPTURE_END_MS + OUTPUT_GRACE_MS
}
