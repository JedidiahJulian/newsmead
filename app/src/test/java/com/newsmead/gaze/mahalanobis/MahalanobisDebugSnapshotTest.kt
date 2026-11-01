package com.newsmead.gaze.mahalanobis

import com.newsmead.gaze.ParticipantBaselineStatus
import org.junit.Assert.*
import org.junit.Test

class MahalanobisDebugSnapshotTest {
    private val config = MahalanobisConfig(minimumRetainedWindows = 2,
        minimumBaselineDurationMs = 0)

    private fun vector(value: RdfValues, index: Int = 0) = RdfFeatureVector(
        timestampMs = 10_000L + index * 500L,
        windowStartMs = index * 500L,
        windowEndMs = 10_000L + index * 500L,
        values = value,
    )

    private fun profile(): FrozenMahalanobisProfile {
        val collector = MahalanobisBaselineCollector(config)
        collector.add(vector(RdfValues(9.0, .4, 19.0)))
        collector.add(vector(RdfValues(11.0, .6, 21.0), 1))
        return collector.freeze().profile!!
    }

    @Test fun availableSnapshotExposesFeaturesMeansDeltasAndDistances() {
        val profile = profile()
        val score = MahalanobisScorer(profile).score(vector(RdfValues(12.0, .7, 22.0)))
        val snapshot = MahalanobisDebugSnapshot.from(score, profile,
            ParticipantBaselineStatus.BASELINE_VALID, config)

        assertEquals(12.0, snapshot.regressionRatePerMinute!!, 0.0)
        assertEquals(profile.mean.dwellFraction, snapshot.baselineMeanDwellFraction!!, 0.0)
        assertEquals(score.delta!!.fixationRatePerMinute,
            snapshot.deltaFixationRatePerMinute!!, 0.0)
        assertEquals(score.md2, snapshot.mahalanobisMD2)
        assertEquals(score.md, snapshot.mahalanobisDistance)
        assertEquals(ParticipantBaselineStatus.BASELINE_VALID, snapshot.baselineStatus)
        assertTrue(snapshot.scoreAvailable)
    }

    @Test fun invalidWindowKeepsFeaturesAndScoreUnavailableInDebugSnapshot() {
        val profile = profile()
        val invalid = vector(profile.mean).copy(
            quality = FeatureQuality.MISSING,
            observedCoverageFraction = 0.1,
        )
        val score = MahalanobisScorer(profile).score(invalid)
        val snapshot = MahalanobisDebugSnapshot.from(score, profile,
            ParticipantBaselineStatus.BASELINE_VALID, config)

        assertNull(snapshot.regressionRatePerMinute)
        assertNull(snapshot.dwellFraction)
        assertNull(snapshot.fixationRatePerMinute)
        assertNull(snapshot.mahalanobisMD2)
        assertNull(snapshot.mahalanobisDistance)
        assertEquals(FeatureQuality.MISSING, snapshot.windowQuality)
        assertEquals(0.1, snapshot.observedCoverageFraction, 0.0)
        assertFalse(snapshot.scoreAvailable)
    }

    @Test fun fullValidityCheckKeepsUnderObservedFeaturesUnavailable() {
        val profile = profile()
        val underObserved = vector(profile.mean).copy(observedCoverageFraction = 0.1)
        val score = MahalanobisScorer(profile).score(underObserved)
        val snapshot = MahalanobisDebugSnapshot.from(score, profile,
            ParticipantBaselineStatus.BASELINE_VALID, config)

        assertEquals(FeatureQuality.VALID, snapshot.windowQuality)
        assertEquals(ScoreStatus.INVALID_WINDOW, snapshot.scoreStatus)
        assertNull(snapshot.regressionRatePerMinute)
        assertNull(snapshot.dwellFraction)
        assertNull(snapshot.fixationRatePerMinute)
        assertFalse(snapshot.scoreAvailable)
    }
}
