package com.newsmead.gaze

import java.util.ArrayDeque
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Produces the manuscript's smoothed, participant-relative stability signal.
 * The existing [ReadingStateInferencer.RsiScore] remains the cumulative session
 * report; adaptation is based on deltas inside a recent sliding window so that
 * support can also be withdrawn after recovery.
 */
class WindowedStabilityEstimator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val baselineDurationMs: Long = DEFAULT_BASELINE_DURATION_MS,
    private val metricUpdateIntervalMs: Long = DEFAULT_METRIC_UPDATE_INTERVAL_MS,
    private val minimumMetricSpanMs: Long = DEFAULT_MINIMUM_METRIC_SPAN_MS,
    private val smoothingTimeConstantMs: Long = DEFAULT_SMOOTHING_TIME_CONSTANT_MS,
    initialBaseline: BaselineProfile? = null,
) {
    private val suppliedBaseline = initialBaseline
    private val scores = ArrayDeque<CumulativeSample>()
    private val gazeValidity = ArrayDeque<ValiditySample>()
    private val regressionStats = RunningStats()
    private val dwellStats = RunningStats()
    private val fixationStats = RunningStats()

    private var baselineStartedAtMs: Long? = null
    private var baseline: BaselineProfile? = initialBaseline
    private var lastMetricAtMs = Long.MIN_VALUE
    private var lastSmoothedAtMs: Long? = null
    private var smoothedIndex = 0.0
    private var lastSnapshot = StabilitySnapshot.EMPTY.copy(baselineReady = initialBaseline != null)

    fun recordGazeSample(valid: Boolean, timestampMs: Long) {
        gazeValidity.addLast(ValiditySample(timestampMs, valid))
        trimValidity(timestampMs)
    }

    fun onCumulativeScore(score: ReadingStateInferencer.RsiScore): StabilitySnapshot {
        val sample = CumulativeSample(
            timestampMs = score.timestampMs,
            fixationCount = score.fixationCount,
            totalDwellMs = score.totalDwellMs,
            regressionCount = score.regressionCount,
        )
        if (baseline == null && baselineStartedAtMs == null) {
            baselineStartedAtMs = sample.timestampMs
        }
        if (scores.peekLast()?.let {
                sample.fixationCount < it.fixationCount ||
                    sample.totalDwellMs < it.totalDwellMs ||
                    sample.regressionCount < it.regressionCount
            } == true
        ) {
            scores.clear()
        }
        scores.addLast(sample)
        trimScores(sample.timestampMs)

        if (lastMetricAtMs != Long.MIN_VALUE &&
            sample.timestampMs - lastMetricAtMs < metricUpdateIntervalMs
        ) return currentSnapshot(sample.timestampMs)

        val first = scores.peekFirst() ?: return currentSnapshot(sample.timestampMs)
        val elapsedMs = sample.timestampMs - first.timestampMs
        if (elapsedMs < minimumMetricSpanMs) return currentSnapshot(sample.timestampMs)
        lastMetricAtMs = sample.timestampMs

        val minutes = elapsedMs / 60_000.0
        val metrics = ReadingMetrics(
            regressionRatePerMinute = (sample.regressionCount - first.regressionCount) / minutes,
            dwellRatio = ((sample.totalDwellMs - first.totalDwellMs).toDouble() / elapsedMs)
                .coerceIn(0.0, 1.0),
            fixationRatePerMinute = (sample.fixationCount - first.fixationCount) / minutes,
        )

        val activeBaseline = baseline
        if (activeBaseline == null) {
            collectBaseline(metrics, sample.timestampMs)
            lastSnapshot = StabilitySnapshot(
                rawIndex = 0.0,
                smoothedIndex = 0.0,
                confidence = confidence(sample.timestampMs),
                baselineReady = baseline != null,
                metrics = metrics,
                timestampMs = sample.timestampMs,
            )
            return lastSnapshot
        }

        val rawIndex = (
            ReadingStateInferencer.REGRESSION_WEIGHT * positiveZ(
                metrics.regressionRatePerMinute,
                activeBaseline.regressionRatePerMinute,
                MIN_REGRESSION_STD,
            ) +
                ReadingStateInferencer.DWELL_WEIGHT * positiveZ(
                    metrics.dwellRatio,
                    activeBaseline.dwellRatio,
                    MIN_DWELL_STD,
                ) +
                ReadingStateInferencer.FIXATION_WEIGHT * positiveZ(
                    metrics.fixationRatePerMinute,
                    activeBaseline.fixationRatePerMinute,
                    MIN_FIXATION_STD,
                )
            ).coerceIn(0.0, MAX_INDEX)

        val previousAt = lastSmoothedAtMs
        val alpha = if (previousAt == null) 1.0 else {
            val deltaMs = (sample.timestampMs - previousAt).coerceAtLeast(0L)
            1.0 - exp(-deltaMs.toDouble() / smoothingTimeConstantMs.coerceAtLeast(1L))
        }
        smoothedIndex += alpha * (rawIndex - smoothedIndex)
        lastSmoothedAtMs = sample.timestampMs
        lastSnapshot = StabilitySnapshot(
            rawIndex = rawIndex,
            smoothedIndex = smoothedIndex,
            confidence = confidence(sample.timestampMs),
            baselineReady = true,
            metrics = metrics,
            timestampMs = sample.timestampMs,
        )
        return lastSnapshot
    }

    fun currentSnapshot(timestampMs: Long): StabilitySnapshot {
        trimValidity(timestampMs)
        return lastSnapshot.copy(confidence = confidence(timestampMs), timestampMs = timestampMs)
    }

    fun baselineProfile(): BaselineProfile? = baseline

    fun reset() {
        scores.clear()
        gazeValidity.clear()
        regressionStats.reset()
        dwellStats.reset()
        fixationStats.reset()
        baselineStartedAtMs = null
        baseline = suppliedBaseline
        lastMetricAtMs = Long.MIN_VALUE
        lastSmoothedAtMs = null
        smoothedIndex = 0.0
        lastSnapshot = StabilitySnapshot.EMPTY.copy(baselineReady = suppliedBaseline != null)
    }

    private fun collectBaseline(metrics: ReadingMetrics, timestampMs: Long) {
        val startedAt = baselineStartedAtMs ?: timestampMs.also { baselineStartedAtMs = it }
        regressionStats.add(metrics.regressionRatePerMinute)
        dwellStats.add(metrics.dwellRatio)
        fixationStats.add(metrics.fixationRatePerMinute)
        if (timestampMs - startedAt < baselineDurationMs || regressionStats.count < MIN_BASELINE_SAMPLES) return
        baseline = BaselineProfile(
            regressionRatePerMinute = regressionStats.toMetric(MIN_REGRESSION_STD),
            dwellRatio = dwellStats.toMetric(MIN_DWELL_STD),
            fixationRatePerMinute = fixationStats.toMetric(MIN_FIXATION_STD),
        )
    }

    private fun positiveZ(value: Double, baselineMetric: BaselineMetric, floor: Double): Double =
        max(0.0, (value - baselineMetric.mean) / max(baselineMetric.standardDeviation, floor))

    private fun trimScores(timestampMs: Long) {
        // Retain one sample immediately before the window boundary so cumulative
        // counter deltas can still be calculated at the left edge.
        while (scores.size > 2) {
            val iterator = scores.iterator()
            iterator.next()
            val second = iterator.next()
            if (timestampMs - second.timestampMs <= windowMs) break
            scores.removeFirst()
        }
    }

    private fun trimValidity(timestampMs: Long) {
        while (gazeValidity.isNotEmpty()) {
            val first = gazeValidity.peekFirst() ?: break
            if (timestampMs - first.timestampMs <= windowMs) break
            gazeValidity.removeFirst()
        }
    }

    private fun confidence(timestampMs: Long): Double {
        trimValidity(timestampMs)
        if (gazeValidity.isEmpty()) return 0.0
        return gazeValidity.count { it.valid }.toDouble() / gazeValidity.size
    }

    data class ReadingMetrics(
        val regressionRatePerMinute: Double,
        val dwellRatio: Double,
        val fixationRatePerMinute: Double,
    ) {
        companion object {
            val EMPTY = ReadingMetrics(0.0, 0.0, 0.0)
        }
    }

    data class BaselineMetric(val mean: Double, val standardDeviation: Double)

    data class BaselineProfile(
        val regressionRatePerMinute: BaselineMetric,
        val dwellRatio: BaselineMetric,
        val fixationRatePerMinute: BaselineMetric,
    )

    data class StabilitySnapshot(
        val rawIndex: Double,
        val smoothedIndex: Double,
        val confidence: Double,
        val baselineReady: Boolean,
        val metrics: ReadingMetrics,
        val timestampMs: Long,
    ) {
        companion object {
            val EMPTY = StabilitySnapshot(0.0, 0.0, 0.0, false, ReadingMetrics.EMPTY, 0L)
        }
    }

    private data class CumulativeSample(
        val timestampMs: Long,
        val fixationCount: Int,
        val totalDwellMs: Long,
        val regressionCount: Int,
    )

    private data class ValiditySample(val timestampMs: Long, val valid: Boolean)

    private class RunningStats {
        var count = 0
            private set
        private var mean = 0.0
        private var sumSquaredDelta = 0.0

        fun add(value: Double) {
            count += 1
            val delta = value - mean
            mean += delta / count
            sumSquaredDelta += delta * (value - mean)
        }

        fun reset() {
            count = 0
            mean = 0.0
            sumSquaredDelta = 0.0
        }

        fun toMetric(floor: Double): BaselineMetric {
            val variance = if (count > 1) sumSquaredDelta / (count - 1) else 0.0
            return BaselineMetric(mean, max(sqrt(variance), floor))
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 10_000L
        const val DEFAULT_BASELINE_DURATION_MS = 20_000L
        const val DEFAULT_METRIC_UPDATE_INTERVAL_MS = 500L
        const val DEFAULT_MINIMUM_METRIC_SPAN_MS = 2_000L
        const val DEFAULT_SMOOTHING_TIME_CONSTANT_MS = 2_000L
        const val MIN_BASELINE_SAMPLES = 8
        const val MIN_REGRESSION_STD = 1.0
        const val MIN_DWELL_STD = 0.05
        const val MIN_FIXATION_STD = 5.0
        const val MAX_INDEX = 4.0
    }
}
