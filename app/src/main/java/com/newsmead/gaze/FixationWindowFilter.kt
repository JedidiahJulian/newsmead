package com.newsmead.gaze

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Rejection + aggregation for one calibration point's SAMPLE window
 * (docs/calibration-design.md §4). Operates purely on timestamped raw gaze
 * feature samples (normalized iris-in-eye ratios, NOT pixels), so thresholds
 * can be tuned and re-run against logged data on the JVM without re-collecting.
 *
 * Pipeline: drop the lead-in, reject per-axis MAD outliers, then gate on
 * dispersion — accepting the full window, else the best contiguous sub-window,
 * else flagging the point low-confidence.
 */
class FixationWindowFilter(
    private val leadInMs: Long = LEAD_IN_MS,
    private val madK: Float = MAD_K,
    private val maxDispersion: Float = MAX_DISPERSION,
    private val minSubWindowMs: Long = MIN_SUB_WINDOW_MS,
    private val minSamples: Int = MIN_SAMPLES,
) {

    data class Sample(val timestampMs: Long, val x: Float, val y: Float)

    enum class Status { ACCEPTED, LOW_CONFIDENCE, TOO_FEW_SAMPLES }

    data class Result(
        val status: Status,
        val medianX: Float,
        val medianY: Float,
        val dispersionX: Float,
        val dispersionY: Float,
        val rawCount: Int,
        val retainedCount: Int,
    )

    fun filter(samples: List<Sample>): Result {
        val ordered = samples.sortedBy { it.timestampMs }
        val rawCount = ordered.size
        if (ordered.isEmpty()) {
            return Result(Status.TOO_FEW_SAMPLES, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0, 0)
        }

        // Unconditionally discard the lead-in: residual settle after the SETTLE state.
        val startMs = ordered.first().timestampMs
        val trimmed = ordered.filter { it.timestampMs - startMs >= leadInMs }
        if (trimmed.size < minSamples) return diagnostic(Status.TOO_FEW_SAMPLES, trimmed, rawCount)

        // Per-axis MAD outlier rejection: removes the stray saccade/twitch without
        // assuming Gaussian noise.
        val retained = rejectMadOutliers(trimmed)
        if (retained.size < minSamples) return diagnostic(Status.TOO_FEW_SAMPLES, retained, rawCount)

        val dx = standardDeviation(retained.map { it.x })
        val dy = standardDeviation(retained.map { it.y })
        if (dx <= maxDispersion && dy <= maxDispersion) {
            return Result(
                Status.ACCEPTED,
                median(retained.map { it.x }), median(retained.map { it.y }),
                dx, dy, rawCount, retained.size,
            )
        }

        // Full window too spread: look for the best contiguous stable sub-window.
        bestSubWindow(retained)?.let { sub ->
            return Result(
                Status.ACCEPTED,
                median(sub.map { it.x }), median(sub.map { it.y }),
                standardDeviation(sub.map { it.x }), standardDeviation(sub.map { it.y }),
                rawCount, sub.size,
            )
        }
        return diagnostic(Status.LOW_CONFIDENCE, retained, rawCount)
    }

    /** Result carrying whatever medians/dispersions are computable, for logging. */
    private fun diagnostic(status: Status, samples: List<Sample>, rawCount: Int): Result {
        if (samples.isEmpty()) {
            return Result(status, Float.NaN, Float.NaN, Float.NaN, Float.NaN, rawCount, 0)
        }
        return Result(
            status,
            median(samples.map { it.x }), median(samples.map { it.y }),
            standardDeviation(samples.map { it.x }), standardDeviation(samples.map { it.y }),
            rawCount, samples.size,
        )
    }

    /** Drop samples farther than madK * MAD from the median on either axis. */
    private fun rejectMadOutliers(samples: List<Sample>): List<Sample> {
        val medX = median(samples.map { it.x })
        val medY = median(samples.map { it.y })
        val madX = median(samples.map { abs(it.x - medX) })
        val madY = median(samples.map { abs(it.y - medY) })
        return samples.filter { s ->
            (madX < MAD_EPSILON || abs(s.x - medX) <= madK * madX) &&
                (madY < MAD_EPSILON || abs(s.y - medY) <= madK * madY)
        }
    }

    /**
     * Best (lowest worst-axis dispersion) contiguous sub-window spanning at least
     * [minSubWindowMs] with at least [minSamples] samples and both axes within
     * [maxDispersion]. Null if no sub-window qualifies. O(n^3) is fine at the
     * ~45-sample scale of one SAMPLE window.
     */
    private fun bestSubWindow(samples: List<Sample>): List<Sample>? {
        var best: List<Sample>? = null
        var bestSpread = Float.MAX_VALUE
        for (i in samples.indices) {
            for (j in i + 1 until samples.size) {
                if (samples[j].timestampMs - samples[i].timestampMs < minSubWindowMs) continue
                val window = samples.subList(i, j + 1)
                if (window.size < minSamples) continue
                val dx = standardDeviation(window.map { it.x })
                val dy = standardDeviation(window.map { it.y })
                if (dx > maxDispersion || dy > maxDispersion) continue
                val spread = max(dx, dy)
                if (spread < bestSpread) {
                    bestSpread = spread
                    best = window
                }
            }
        }
        return best
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    private fun standardDeviation(values: List<Float>): Float {
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance).toFloat()
    }

    companion object {
        // Starting values per docs/calibration-design.md §4; provisional pending pilot.
        private const val LEAD_IN_MS = 120L
        private const val MAD_K = 2.5f
        private const val MAX_DISPERSION = 0.02f
        private const val MIN_SUB_WINDOW_MS = 300L
        private const val MIN_SAMPLES = 10
        private const val MAD_EPSILON = 1e-6f
    }
}
