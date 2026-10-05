package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MahalanobisScorerTest {
    private fun vector(r: Double, d: Double, f: Double) =
        WindowedStabilityEstimator.ReadingMetrics(r, d, f)

    @Test
    fun signedLowDeviationRemainsUnusualButFailsPositiveGate() {
        val baseline = (0 until 20).map { i ->
            vector(4.0 + (i % 3), 0.30 + (i % 3) * 0.01, 55.0 + (i % 3) * 2)
        }
        val profile = MahalanobisScorer.fit(baseline)
        val score = MahalanobisScorer.score(vector(0.0, 0.02, 20.0), profile)

        assertTrue(score.md2 > profile.thresholds.p99)
        assertEquals(MahalanobisScorer.Direction.BELOW_BASELINE, score.directionR)
        assertEquals(MahalanobisScorer.Direction.BELOW_BASELINE, score.directionD)
        assertEquals(MahalanobisScorer.Direction.BELOW_BASELINE, score.directionF)
        assertFalse(score.positiveDirectionEligible)
        assertEquals(score.md2, score.md * score.md, 1e-9)
    }

    @Test
    fun covarianceDistinguishesJointAndOpposingPatterns() {
        val baseline = (0 until 30).map { i ->
            val r = 2.0 + i % 10
            vector(r, 0.10 + r * 0.02, 30.0 + r * 4.0)
        }
        val profile = MahalanobisScorer.fit(baseline)
        val matching = MahalanobisScorer.score(vector(15.0, 0.40, 90.0), profile)
        val opposing = MahalanobisScorer.score(vector(15.0, 0.10, 30.0), profile)

        assertTrue(profile.covariance[1] > 0.0)
        assertTrue(opposing.md2 > matching.md2)
        assertTrue(profile.precision.all(Double::isFinite))
        assertTrue(profile.thresholds.p90 <= profile.thresholds.p95)
        assertTrue(profile.thresholds.p95 <= profile.thresholds.p99)
    }

    @Test
    fun constantBaselineDoesNotFlagItsOwnMean() {
        val normal = vector(0.0, 0.2, 60.0)
        val profile = MahalanobisScorer.fit(List(12) { normal })
        val score = MahalanobisScorer.score(normal, profile)

        assertEquals(0.0, score.md2, 1e-20)
        assertEquals(MahalanobisScorer.PercentileBand.BELOW_P90, score.band)
    }
}
