package com.newsmead.gaze.mahalanobis

/** Feature order and units: regressions/minute, dwell fraction, line stays/minute. */
data class RdfValues(
    val regressionRatePerMinute: Double,
    val dwellFraction: Double,
    val fixationRatePerMinute: Double,
) {
    operator fun get(index: Int): Double = when (index) {
        0 -> regressionRatePerMinute
        1 -> dwellFraction
        2 -> fixationRatePerMinute
        else -> error("RDF index out of range")
    }
    operator fun minus(other: RdfValues) = RdfValues(this[0] - other[0], this[1] - other[1], this[2] - other[2])
    fun isFinite() = (0..2).all { this[it].isFinite() }
}

enum class FeatureQuality { VALID, MISSING, INSUFFICIENT, DISCONTINUITY }

/** All three features describe this same capture-time interval. */
data class RdfFeatureVector(
    val timestampMs: Long,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val values: RdfValues,
    val quality: FeatureQuality = FeatureQuality.VALID,
    val validFraction: Double = 1.0,
    val validSampleCount: Int? = null,
    val totalSampleCount: Int? = null,
) {
    fun isUsable(config: MahalanobisConfig): Boolean = quality == FeatureQuality.VALID &&
        timestampMs >= 0 && windowStartMs >= 0 && windowEndMs == timestampMs &&
        windowEndMs >= windowStartMs && windowEndMs - windowStartMs >= config.windowMs &&
        windowEndMs - windowStartMs - config.windowMs <= config.maxWindowOverhangMs &&
        validFraction.isFinite() && validFraction in config.minimumValidFraction..1.0 &&
        ((validSampleCount == null && totalSampleCount == null) ||
            (validSampleCount != null && totalSampleCount != null && totalSampleCount > 0 &&
                validSampleCount in 0..totalSampleCount &&
                kotlin.math.abs(validFraction - validSampleCount.toDouble() / totalSampleCount) <= 1e-9)) &&
        values.isFinite() && values.regressionRatePerMinute >= 0 &&
        values.dwellFraction in 0.0..1.0 && values.fixationRatePerMinute >= 0
}

/** Operational defaults; sample count and duration are not claims of human validity. */
data class MahalanobisConfig(
    val windowMs: Long = 10_000,
    val updateIntervalMs: Long = 500,
    val maxWindowOverhangMs: Long = updateIntervalMs,
    val minimumBaselineDurationMs: Long = 60_000,
    val minimumRetainedWindows: Int = 30,
    val lambda: Double = 1e-6,
    val maxGapMs: Long = 1_000,
    val minimumValidFraction: Double = 0.65,
) {
    init {
        require(windowMs > 0 && updateIntervalMs > 0 && maxWindowOverhangMs >= 0)
        require(minimumBaselineDurationMs >= 0 && minimumRetainedWindows >= 2)
        require(lambda.isFinite() && lambda >= 0 && maxGapMs > 0)
        require(minimumValidFraction.isFinite() && minimumValidFraction in 0.0..1.0)
    }
}

/** Immutable storage: arrays never escape a frozen profile. */
data class Matrix3(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double,
    val m20: Double, val m21: Double, val m22: Double,
) {
    operator fun get(row: Int, col: Int): Double = when (row * 3 + col) {
        0 -> m00; 1 -> m01; 2 -> m02
        3 -> m10; 4 -> m11; 5 -> m12
        6 -> m20; 7 -> m21; 8 -> m22
        else -> error("Matrix index out of range")
    }
    companion object {
        internal fun from(rows: Array<DoubleArray>) = Matrix3(
            rows[0][0], rows[0][1], rows[0][2], rows[1][0], rows[1][1], rows[1][2],
            rows[2][0], rows[2][1], rows[2][2],
        )
    }
}

enum class BaselineStatus { COLLECTING, READY, INSUFFICIENT_WINDOWS, INSUFFICIENT_DURATION, INVALID_INPUT, NUMERICAL_FAILURE }

data class SamplingMetadata(
    val receivedCount: Int,
    val validCount: Int,
    val retainedCount: Int,
    val firstTimestampMs: Long?,
    val lastTimestampMs: Long?,
    val coverageMs: Long,
) {
    val invalidCount: Int get() = receivedCount - validCount
}

/** Diagonal ratio is a factor diagnostic, not a covariance condition number. */
data class StabilityDiagnostic(
    val choleskyDiagonalRatio: Double,
    val unregularizedCholeskyDiagonalRatio: Double?,
)
