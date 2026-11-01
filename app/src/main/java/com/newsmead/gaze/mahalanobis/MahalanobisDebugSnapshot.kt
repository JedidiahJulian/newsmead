package com.newsmead.gaze.mahalanobis

import com.newsmead.gaze.ParticipantBaselineStatus
import java.util.Locale

/** Development-only runtime view; never drives Scanpath, aids, or participant output. */
data class MahalanobisDebugSnapshot(
    val timestampMs: Long,
    val regressionRatePerMinute: Double?,
    val dwellFraction: Double?,
    val fixationRatePerMinute: Double?,
    val baselineMeanRegressionRatePerMinute: Double?,
    val baselineMeanDwellFraction: Double?,
    val baselineMeanFixationRatePerMinute: Double?,
    val deltaRegressionRatePerMinute: Double?,
    val deltaDwellFraction: Double?,
    val deltaFixationRatePerMinute: Double?,
    val mahalanobisMD2: Double?,
    val mahalanobisDistance: Double?,
    val baselineStatus: ParticipantBaselineStatus,
    val windowQuality: FeatureQuality,
    val validSampleFraction: Double,
    val observedCoverageFraction: Double,
    val scoreStatus: ScoreStatus,
    val scoreAvailable: Boolean,
) {
    fun toDebugLog(): String = String.format(
        Locale.US,
        "window=%s validSamples=%.3f observedCoverage=%.3f baseline=%s " +
            "R=%s D=%s F=%s meanR=%s meanD=%s meanF=%s " +
            "deltaR=%s deltaD=%s deltaF=%s MD2=%s MD=%s score=%s available=%s",
        windowQuality,
        validSampleFraction,
        observedCoverageFraction,
        baselineStatus,
        regressionRatePerMinute.debugValue(),
        dwellFraction.debugValue(),
        fixationRatePerMinute.debugValue(),
        baselineMeanRegressionRatePerMinute.debugValue(),
        baselineMeanDwellFraction.debugValue(),
        baselineMeanFixationRatePerMinute.debugValue(),
        deltaRegressionRatePerMinute.debugValue(),
        deltaDwellFraction.debugValue(),
        deltaFixationRatePerMinute.debugValue(),
        mahalanobisMD2.debugValue(),
        mahalanobisDistance.debugValue(),
        scoreStatus,
        scoreAvailable,
    )

    companion object {
        fun from(
            score: MahalanobisScore,
            profile: FrozenMahalanobisProfile?,
            baselineStatus: ParticipantBaselineStatus,
            config: MahalanobisConfig,
        ): MahalanobisDebugSnapshot {
            val featureValues = score.vector.values.takeIf {
                score.vector.isUsable(config)
            }
            return MahalanobisDebugSnapshot(
                timestampMs = score.vector.timestampMs,
                regressionRatePerMinute = featureValues?.regressionRatePerMinute,
                dwellFraction = featureValues?.dwellFraction,
                fixationRatePerMinute = featureValues?.fixationRatePerMinute,
                baselineMeanRegressionRatePerMinute = profile?.mean?.regressionRatePerMinute,
                baselineMeanDwellFraction = profile?.mean?.dwellFraction,
                baselineMeanFixationRatePerMinute = profile?.mean?.fixationRatePerMinute,
                deltaRegressionRatePerMinute = score.delta?.regressionRatePerMinute,
                deltaDwellFraction = score.delta?.dwellFraction,
                deltaFixationRatePerMinute = score.delta?.fixationRatePerMinute,
                mahalanobisMD2 = score.md2,
                mahalanobisDistance = score.md,
                baselineStatus = baselineStatus,
                windowQuality = score.vector.quality,
                validSampleFraction = score.vector.validFraction,
                observedCoverageFraction = score.vector.observedCoverageFraction,
                scoreStatus = score.status,
                scoreAvailable = score.scoreAvailable,
            )
        }
    }
}

private fun Double?.debugValue(): String = this?.let { String.format(Locale.US, "%.6f", it) } ?: "unavailable"
