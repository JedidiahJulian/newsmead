package com.newsmead.gaze.scanpath.replay

import kotlin.math.ceil

/** Compares the unchanged synthetic capture deadline with actual callback delivery. */
data class ReplayDispatchTiming(val retryDelayMs: Long, val tooLate: Boolean) {
    companion object {
        fun assess(captureMs: Double, deliveryMs: Double): ReplayDispatchTiming {
            val lagMs = deliveryMs - captureMs
            return ReplayDispatchTiming(
                if (lagMs < 0.0) ceil(-lagMs).toLong() else 0L,
                lagMs > 100.0
            )
        }
    }
}
