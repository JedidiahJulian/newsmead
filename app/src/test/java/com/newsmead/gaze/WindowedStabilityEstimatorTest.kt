package com.newsmead.gaze

import org.junit.Assert.assertTrue
import org.junit.Test

class WindowedStabilityEstimatorTest {

    @Test
    fun recentInstabilityRisesThenFallsOutOfWindow() {
        val baseline = WindowedStabilityEstimator.BaselineProfile(
            regressionRatePerMinute = WindowedStabilityEstimator.BaselineMetric(0.0, 1.0),
            dwellRatio = WindowedStabilityEstimator.BaselineMetric(0.20, 0.05),
            fixationRatePerMinute = WindowedStabilityEstimator.BaselineMetric(60.0, 10.0),
        )
        val estimator = WindowedStabilityEstimator(
            windowMs = 10_000,
            metricUpdateIntervalMs = 0,
            minimumMetricSpanMs = 1_000,
            smoothingTimeConstantMs = 1,
            initialBaseline = baseline,
        )
        estimator.recordGazeSample(true, 0)
        estimator.onCumulativeScore(score(0, fixations = 0, dwellMs = 0, regressions = 0))
        estimator.recordGazeSample(true, 10_000)
        val elevated = estimator.onCumulativeScore(
            score(10_000, fixations = 20, dwellMs = 7_000, regressions = 5)
        )

        estimator.recordGazeSample(true, 20_001)
        val recovered = estimator.onCumulativeScore(
            score(20_001, fixations = 30, dwellMs = 8_000, regressions = 5)
        )

        assertTrue(elevated.rawIndex >= 2.0)
        assertTrue(recovered.rawIndex < elevated.rawIndex)
        assertTrue(recovered.smoothedIndex < elevated.smoothedIndex)
    }

    @Test
    fun baselineMustCompleteBeforeAdaptationIsReady() {
        val estimator = WindowedStabilityEstimator(
            windowMs = 10_000,
            baselineDurationMs = 5_000,
            metricUpdateIntervalMs = 0,
            minimumMetricSpanMs = 1_000,
        )
        estimator.recordGazeSample(true, 0)
        estimator.onCumulativeScore(score(0, 0, 0, 0))
        estimator.recordGazeSample(true, 2_000)
        val snapshot = estimator.onCumulativeScore(score(2_000, 2, 500, 0))

        assertTrue(!snapshot.baselineReady)
    }

    private fun score(
        timestampMs: Long,
        fixations: Int,
        dwellMs: Long,
        regressions: Int,
    ) = ReadingStateInferencer.RsiScore(
        score = 0.0,
        regressionComponent = 0.0,
        dwellComponent = 0.0,
        fixationComponent = 0.0,
        fixationCount = fixations,
        totalDwellMs = dwellMs,
        regressionCount = regressions,
        elapsedMs = timestampMs.coerceAtLeast(1),
        timestampMs = timestampMs,
    )
}
