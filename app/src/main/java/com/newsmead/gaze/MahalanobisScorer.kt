package com.newsmead.gaze

import kotlin.math.max
import kotlin.math.sqrt

enum class ScaffoldScoringMethod { LEGACY_WEIGHTED_AII, MAHALANOBIS }

enum class MahalanobisDirectionPolicy { STANDARD_TWO_SIDED, POSITIVE_DIRECTION_GATE }

/** The same recent R/D/F vectors used by weighted AII, scored against their joint baseline. */
object MahalanobisScorer {
    enum class Direction { ABOVE_BASELINE, BELOW_BASELINE, NEAR_BASELINE }
    enum class PercentileBand { BELOW_P90, P90_TO_P95, P95_TO_P99, P99_OR_ABOVE }

    data class Thresholds(val p90: Double, val p95: Double, val p99: Double)

    /** Matrices are row-major in R, D, F order. No percentage weights are implied. */
    data class Profile(
        val mean: WindowedStabilityEstimator.ReadingMetrics,
        val covariance: List<Double>,
        val regularizedCovariance: List<Double>,
        val precision: List<Double>,
        val thresholds: Thresholds,
        val sampleCount: Int,
        val offDiagonalShrinkage: Double,
    )

    data class Score(
        val md2: Double,
        val md: Double,
        val band: PercentileBand,
        val deltaR: Double,
        val deltaD: Double,
        val deltaF: Double,
        val directionR: Direction,
        val directionD: Direction,
        val directionF: Direction,
    ) {
        val positiveDirectionEligible: Boolean
            get() = directionR == Direction.ABOVE_BASELINE ||
                directionD == Direction.ABOVE_BASELINE || directionF == Direction.ABOVE_BASELINE
    }

    fun fit(
        samples: List<WindowedStabilityEstimator.ReadingMetrics>,
        offDiagonalShrinkage: Double = DEFAULT_OFF_DIAGONAL_SHRINKAGE,
    ): Profile {
        require(samples.size >= WindowedStabilityEstimator.MIN_BASELINE_SAMPLES)
        require(offDiagonalShrinkage in 0.0..1.0)
        val rows = samples.map { it.values() }
        require(rows.all { row -> row.all(Double::isFinite) })
        val mean = DoubleArray(3) { i -> rows.sumOf { it[i] } / rows.size }
        val covariance = DoubleArray(9)
        for (row in rows) for (i in 0..2) for (j in 0..2) {
            covariance[i * 3 + j] += (row[i] - mean[i]) * (row[j] - mean[j])
        }
        for (i in covariance.indices) covariance[i] = covariance[i] / (rows.size - 1).toDouble()

        val floors = doubleArrayOf(
            WindowedStabilityEstimator.MIN_REGRESSION_STD,
            WindowedStabilityEstimator.MIN_DWELL_STD,
            WindowedStabilityEstimator.MIN_FIXATION_STD,
        )
        val regularized = DoubleArray(9) { index ->
            val i = index / 3
            val j = index % 3
            if (i == j) max(covariance[index], floors[i] * floors[i])
            else covariance[index] * (1.0 - offDiagonalShrinkage)
        }
        val precision = inversePositiveDefinite3(regularized)
        val distances = rows.map { distanceSquared(it, mean, precision) }.sorted()
        return Profile(
            mean = WindowedStabilityEstimator.ReadingMetrics(mean[0], mean[1], mean[2]),
            covariance = covariance.toList(),
            regularizedCovariance = regularized.toList(),
            precision = precision.toList(),
            thresholds = Thresholds(
                percentile(distances, 0.90), percentile(distances, 0.95), percentile(distances, 0.99),
            ),
            sampleCount = rows.size,
            offDiagonalShrinkage = offDiagonalShrinkage,
        )
    }

    fun score(
        metrics: WindowedStabilityEstimator.ReadingMetrics,
        profile: Profile,
        directionDeadbandZ: Double = DEFAULT_DIRECTION_DEADBAND_Z,
    ): Score {
        require(directionDeadbandZ >= 0.0 && directionDeadbandZ.isFinite())
        val values = metrics.values()
        require(values.all(Double::isFinite))
        val mean = profile.mean.values()
        val delta = DoubleArray(3) { values[it] - mean[it] }
        val md2 = distanceSquared(values, mean, profile.precision.toDoubleArray())
        val thresholds = profile.thresholds
        val band = when {
            md2 <= thresholds.p90 -> PercentileBand.BELOW_P90
            md2 <= thresholds.p95 -> PercentileBand.P90_TO_P95
            md2 <= thresholds.p99 -> PercentileBand.P95_TO_P99
            else -> PercentileBand.P99_OR_ABOVE
        }
        val directions = Array(3) { i ->
            val sd = sqrt(profile.regularizedCovariance[i * 3 + i])
            val boundary = directionDeadbandZ * sd
            when {
                delta[i] > boundary -> Direction.ABOVE_BASELINE
                delta[i] < -boundary -> Direction.BELOW_BASELINE
                else -> Direction.NEAR_BASELINE
            }
        }
        return Score(md2, sqrt(md2), band, delta[0], delta[1], delta[2],
            directions[0], directions[1], directions[2])
    }

    private fun WindowedStabilityEstimator.ReadingMetrics.values() =
        doubleArrayOf(regressionRatePerMinute, dwellRatio, fixationRatePerMinute)

    private fun distanceSquared(values: DoubleArray, mean: DoubleArray, precision: DoubleArray): Double {
        val delta = DoubleArray(3) { values[it] - mean[it] }
        var result = 0.0
        for (i in 0..2) for (j in 0..2) result += delta[i] * precision[i * 3 + j] * delta[j]
        return max(0.0, result) // Only suppress negative floating-point roundoff.
    }

    private fun percentile(sorted: List<Double>, fraction: Double): Double {
        val position = fraction * (sorted.size - 1)
        val lower = position.toInt()
        val upper = minOf(lower + 1, sorted.lastIndex)
        return sorted[lower] + (position - lower) * (sorted[upper] - sorted[lower])
    }

    private fun inversePositiveDefinite3(matrix: DoubleArray): DoubleArray {
        // Cholesky decomposition avoids a fragile determinant-based inverse.
        val l = DoubleArray(9)
        for (i in 0..2) for (j in 0..i) {
            var value = matrix[i * 3 + j]
            for (k in 0 until j) value -= l[i * 3 + k] * l[j * 3 + k]
            l[i * 3 + j] = if (i == j) {
                require(value > 0.0 && value.isFinite()) { "Invalid baseline covariance" }
                sqrt(value)
            } else value / l[j * 3 + j]
        }
        val inverse = DoubleArray(9)
        for (column in 0..2) {
            val y = DoubleArray(3)
            for (i in 0..2) {
                var value = if (i == column) 1.0 else 0.0
                for (k in 0 until i) value -= l[i * 3 + k] * y[k]
                y[i] = value / l[i * 3 + i]
            }
            for (i in 2 downTo 0) {
                var value = y[i]
                for (k in i + 1..2) value -= l[k * 3 + i] * inverse[k * 3 + column]
                inverse[i * 3 + column] = value / l[i * 3 + i]
            }
        }
        return inverse
    }

    const val DEFAULT_OFF_DIAGONAL_SHRINKAGE = 0.10
    const val DEFAULT_DIRECTION_DEADBAND_Z = 0.50
}
