package com.newsmead.gaze.mahalanobis

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class MahalanobisCoreTest {
    private val config = MahalanobisConfig(minimumRetainedWindows = 2, minimumBaselineDurationMs = 0)
    private fun vector(values: RdfValues, index: Int = 0): RdfFeatureVector {
        val end = 10_000L + index * 500L
        return RdfFeatureVector(end, end - 10_000, end, values)
    }
    private fun fit(values: List<RdfValues>, settings: MahalanobisConfig = config): FrozenMahalanobisProfile {
        val collector = MahalanobisBaselineCollector(settings)
        values.forEachIndexed { index, value -> assertTrue(collector.add(vector(value, index))) }
        val result = collector.freeze()
        assertEquals(BaselineStatus.READY, result.status)
        return result.profile!!
    }
    private fun independentBaseline(): List<RdfValues> = listOf(
        RdfValues(9.0, .5, 20.0), RdfValues(11.0, .5, 20.0),
        RdfValues(10.0, .4, 20.0), RdfValues(10.0, .6, 20.0),
        RdfValues(10.0, .5, 18.0), RdfValues(10.0, .5, 22.0),
    )
    private fun md2(profile: FrozenMahalanobisProfile, value: RdfValues): Double {
        val score = MahalanobisScorer(profile).score(vector(value))
        assertTrue(score.scoreAvailable)
        assertEquals(sqrt(score.md2!!), score.md!!, 1e-12)
        return score.md2!!
    }

    @Test fun baselineCenterHasZeroDistance() {
        val profile = fit(independentBaseline())
        val score = MahalanobisScorer(profile).score(vector(profile.mean))
        assertEquals(0.0, score.md2!!, 1e-12)
        assertEquals(0.0, score.md!!, 1e-12)
        assertEquals(RdfValues(0.0, 0.0, 0.0), score.delta)
    }

    @Test fun distanceIncreasesAlongSameDirection() {
        val profile = fit(independentBaseline())
        val values = listOf(0.0, .5, 1.0, 2.0).map {
            md2(profile, RdfValues(10 + it, .5 + .01 * it, 20 + it))
        }
        assertTrue(values.zipWithNext().all { it.second > it.first })
        assertEquals(4 * values[2], values[3], 1e-9)
    }

    @Test fun differentRawScalesFollowBaselineVariance() {
        val profile = fit(listOf(
            RdfValues(19.0, .5, 10_000.0), RdfValues(21.0, .5, 10_000.0),
            RdfValues(20.0, .49, 10_000.0), RdfValues(20.0, .51, 10_000.0),
            RdfValues(20.0, .5, 9_000.0), RdfValues(20.0, .5, 11_000.0),
        ), config.copy(lambda = 0.0))
        val regression = md2(profile, RdfValues(21.0, .5, 10_000.0))
        val dwell = md2(profile, RdfValues(20.0, .51, 10_000.0))
        val fixation = md2(profile, RdfValues(20.0, .5, 11_000.0))
        assertEquals(regression, dwell, 1e-9)
        assertEquals(regression, fixation, 1e-9)
    }

    @Test fun correlatedRelationshipIsLessUnusualThanOpposingCombination() {
        val baseline = (-4..4).flatMap { i -> listOf(-1, 1).map { jitter ->
            RdfValues(10.0 + i, .5 + i * .03 + jitter * .001, 20.0 + jitter)
        } }
        val profile = fit(baseline)
        assertTrue(profile.covariance[0, 1] > 0)
        val along = md2(profile, RdfValues(12.0, .56, 20.0))
        val against = md2(profile, RdfValues(12.0, .44, 20.0))
        assertTrue(against > 100 * along)
    }

    @Test fun regularizationStabilizesSingularAndZeroVarianceCovariance() {
        val profile = fit(List(6) { RdfValues(10.0, .5, 20.0) })
        assertTrue(profile.regularizationRequired)
        assertTrue(profile.regularizationApplied)
        assertEquals(config.lambda, profile.regularizationLambda, 0.0)
        assertEquals(0.0, profile.covariance[1, 1], 0.0)
        assertEquals(config.lambda, profile.regularizedCovariance[1, 1], 0.0)
        assertTrue(profile.stability.choleskyDiagonalRatio.isFinite())
        assertNull(profile.stability.unregularizedCholeskyDiagonalRatio)
        assertTrue(md2(profile, RdfValues(11.0, .6, 21.0)).isFinite())

        val correlated = fit((0..5).map { RdfValues(10.0 + it, .3 + it * .01, 20.0 + it * 2) })
        assertTrue(md2(correlated, RdfValues(12.0, .32, 25.0)).isFinite())
    }

    @Test fun insufficientOrFailedBaselineDoesNotProduceFakeZero() {
        val collector = MahalanobisBaselineCollector(config)
        assertEquals(BaselineStatus.COLLECTING, collector.status)
        collector.add(vector(RdfValues(10.0, .5, 20.0)))
        val fit = collector.freeze()
        assertEquals(BaselineStatus.INSUFFICIENT_WINDOWS, fit.status)
        val score = MahalanobisScorer(fit.profile).score(vector(RdfValues(10.0, .5, 20.0)))
        assertFalse(score.scoreAvailable)
        assertNull(score.md2)
        assertNull(score.md)

        val singular = MahalanobisBaselineCollector(config.copy(lambda = 0.0))
        repeat(3) { singular.add(vector(RdfValues(10.0, .5, 20.0), it)) }
        assertEquals(BaselineStatus.NUMERICAL_FAILURE, singular.freeze().status)
    }

    @Test fun nonFiniteAndInvalidLiveValuesAreUnavailable() {
        val profile = fit(independentBaseline())
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val score = MahalanobisScorer(profile).score(vector(RdfValues(invalid, .5, 20.0)))
            assertEquals(ScoreStatus.INVALID_WINDOW, score.status)
            assertFalse(score.scoreAvailable)
            assertNull(score.md2)
            val collector = MahalanobisBaselineCollector(config)
            collector.add(vector(RdfValues(invalid, .5, 20.0)))
            assertEquals(BaselineStatus.INVALID_INPUT, collector.freeze().status)
        }
        val partial = vector(profile.mean).copy(windowStartMs = 5_000)
        assertFalse(MahalanobisScorer(profile).score(partial).scoreAvailable)
    }

    @Test fun finalizedBaselineNeverChangesDuringSubsequentReading() {
        val collector = MahalanobisBaselineCollector(config)
        independentBaseline().forEachIndexed { i, value -> collector.add(vector(value, i)) }
        val fit = collector.freeze()
        val profile = fit.profile!!
        val originalMean = profile.mean
        val originalCovariance = profile.covariance
        val originalRegularized = profile.regularizedCovariance
        repeat(20) {
            assertFalse(collector.add(vector(RdfValues(100.0 + it, .9, 300.0), it + 10)))
            MahalanobisScorer(profile).score(vector(RdfValues(100.0 + it, .9, 300.0)))
        }
        assertSame(fit, collector.freeze())
        assertEquals(originalMean, profile.mean)
        assertEquals(originalCovariance, profile.covariance)
        assertEquals(originalRegularized, profile.regularizedCovariance)
        assertEquals(6, profile.validWindowCount)
    }

    @Test fun positiveAndNegativeDeviationsHaveSameDistance() {
        val profile = fit(independentBaseline())
        val positive = MahalanobisScorer(profile).score(vector(RdfValues(15.0, .7, 25.0)))
        val negative = MahalanobisScorer(profile).score(vector(RdfValues(5.0, .3, 15.0)))
        assertTrue(positive.md2!! > 10)
        assertEquals(positive.md2!!, negative.md2!!, 1e-9)
        assertEquals(5.0, positive.delta!![0], 0.0)
        assertEquals(-5.0, negative.delta!![0], 0.0)
        assertTrue(negative.delta!![1] < 0)
    }

    @Test fun numericalPrecisionAllowsOnlyTinyNegativeRoundoff() {
        assertEquals(0.0, checkedMd2(-1e-12)!!, 0.0)
        assertNull(checkedMd2(-1e-4))
        assertNull(checkedMd2(Double.NaN))
        assertNull(checkedMd2(Double.POSITIVE_INFINITY))
        assertEquals(1e-12, checkedMd2(1e-12)!!, 0.0)
    }

    @Test fun sampleCovarianceUsesNMinusOneAndRetainsOffDiagonalTerms() {
        val profile = fit(listOf(RdfValues(1.0, .2, 2.0), RdfValues(3.0, .4, 6.0)))
        assertEquals(RdfValues(2.0, .3, 4.0).regressionRatePerMinute, profile.mean[0], 0.0)
        assertEquals(2.0, profile.covariance[0, 0], 1e-12)
        assertEquals(.2, profile.covariance[0, 1], 1e-12)
        assertEquals(4.0, profile.covariance[0, 2], 1e-12)
        assertEquals(profile.covariance[0, 1], profile.covariance[1, 0], 0.0)
    }

    @Test fun coveredDurationCountsOverlapOnceAndExcludesGaps() {
        val collector = MahalanobisBaselineCollector(config.copy(minimumBaselineDurationMs = 25_000))
        collector.add(vector(RdfValues(10.0, .5, 20.0)))
        collector.add(vector(RdfValues(11.0, .6, 21.0), 1))
        collector.add(vector(RdfValues(12.0, .7, 22.0), 80))
        assertEquals(20_500, collector.coveredDurationMs)
        assertEquals(BaselineStatus.INSUFFICIENT_DURATION, collector.freeze().status)
    }
}
