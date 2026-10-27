package com.newsmead.gaze.mahalanobis

import kotlin.math.sqrt

/** Explicit terminal fit. Further samples cannot change either a successful or failed fit. */
class MahalanobisBaselineCollector(val config: MahalanobisConfig = MahalanobisConfig()) {
    private val retained = mutableListOf<RdfFeatureVector>()
    private var received = 0
    private var valid = 0
    private var lastTimestamp: Long? = null
    private var invalidInput = false
    private var frozen: BaselineFit? = null
    val status: BaselineStatus get() = frozen?.status ?: BaselineStatus.COLLECTING
    val coveredDurationMs: Long get() = coverageMs()
    val baselineVectors: List<RdfFeatureVector> get() = java.util.Collections.unmodifiableList(ArrayList(retained))

    fun add(vector: RdfFeatureVector): Boolean {
        if (frozen != null) return false
        received++
        if (!vector.values.isFinite() || !vector.validFraction.isFinite()) {
            invalidInput = true
            return false
        }
        val previous = lastTimestamp
        if (previous != null && vector.timestampMs <= previous) {
            invalidInput = true
            return false
        }
        lastTimestamp = vector.timestampMs
        if (!vector.isUsable(config)) return false
        valid++
        retained.add(vector)
        return true
    }

    fun freeze(): BaselineFit {
        frozen?.let { return it }
        val metadata = SamplingMetadata(received, valid, retained.size,
            retained.firstOrNull()?.timestampMs, retained.lastOrNull()?.timestampMs, coverageMs())
        fun fail(status: BaselineStatus) = BaselineFit(status, null, metadata).also { frozen = it }
        if (invalidInput) return fail(BaselineStatus.INVALID_INPUT)
        if (retained.size < config.minimumRetainedWindows) return fail(BaselineStatus.INSUFFICIENT_WINDOWS)
        if (metadata.coverageMs < config.minimumBaselineDurationMs) return fail(BaselineStatus.INSUFFICIENT_DURATION)

        // Multivariate Welford accumulation; n-1 gives the sample covariance.
        val mean = DoubleArray(3)
        val cross = Array(3) { DoubleArray(3) }
        retained.forEachIndexed { index, vector ->
            val delta = DoubleArray(3) { vector.values[it] - mean[it] }
            for (i in 0..2) mean[i] += delta[i] / (index + 1)
            for (i in 0..2) for (j in 0..2)
                cross[i][j] += delta[i] * (vector.values[j] - mean[j])
        }
        val covariance = Array(3) { i -> DoubleArray(3) { j ->
            (cross[i][j] + cross[j][i]) / (2.0 * (retained.size - 1))
        } }
        val regularized = Array(3) { i -> DoubleArray(3) { j ->
            covariance[i][j] + if (i == j) config.lambda else 0.0
        } }
        if (mean.any { !it.isFinite() } || regularized.any { row -> row.any { !it.isFinite() } })
            return fail(BaselineStatus.NUMERICAL_FAILURE)
        val originalFactor = cholesky(covariance)
        val factor = cholesky(regularized) ?: return fail(BaselineStatus.NUMERICAL_FAILURE)
        val ratio = diagonalRatio(factor) ?: return fail(BaselineStatus.NUMERICAL_FAILURE)
        val profile = FrozenMahalanobisProfile(config, RdfValues(mean[0], mean[1], mean[2]),
            Matrix3.from(covariance), Matrix3.from(regularized), factor, metadata,
            StabilityDiagnostic(ratio, originalFactor?.let(::diagonalRatio)),
            regularizationRequired = originalFactor == null)
        return BaselineFit(BaselineStatus.READY, profile, metadata).also { frozen = it }
    }

    /** Union of valid observed windows: overlapping intervals count once. */
    private fun coverageMs(): Long {
        if (retained.isEmpty()) return 0
        val intervals = retained.sortedBy { it.windowStartMs }
        var start = intervals.first().windowStartMs
        var end = intervals.first().windowEndMs
        var covered = 0L
        for (vector in intervals.drop(1)) {
            if (vector.windowStartMs <= end) end = maxOf(end, vector.windowEndMs)
            else {
                covered += end - start
                start = vector.windowStartMs
                end = vector.windowEndMs
            }
        }
        return covered + end - start
    }
}

data class BaselineFit(val status: BaselineStatus, val profile: FrozenMahalanobisProfile?, val sampling: SamplingMetadata)

/** Constructed only after a usable covariance factorization; all fields are immutable. */
class FrozenMahalanobisProfile internal constructor(
    val config: MahalanobisConfig,
    val mean: RdfValues,
    val covariance: Matrix3,
    val regularizedCovariance: Matrix3,
    internal val cholesky: Matrix3,
    val sampling: SamplingMetadata,
    val stability: StabilityDiagnostic,
    /** True when unregularized covariance could not be Cholesky factored. */
    val regularizationRequired: Boolean,
) {
    val regularizationLambda: Double get() = config.lambda
    val regularizationApplied: Boolean get() = config.lambda > 0
    val validWindowCount: Int get() = sampling.retainedCount
    val isValid: Boolean get() = true
}

private fun diagonalRatio(factor: Matrix3): Double? {
    val diagonal = (0..2).map { factor[it, it] }
    return (diagonal.maxOrNull()!! / diagonal.minOrNull()!!).takeIf { it.isFinite() }
}

private fun cholesky(matrix: Array<DoubleArray>): Matrix3? {
    val lower = Array(3) { DoubleArray(3) }
    for (i in 0..2) for (j in 0..i) {
        var value = matrix[i][j]
        for (k in 0 until j) value -= lower[i][k] * lower[j][k]
        if (!value.isFinite()) return null
        lower[i][j] = if (i == j) {
            if (value <= 0) return null
            sqrt(value)
        } else value / lower[j][j]
        if (!lower[i][j].isFinite()) return null
    }
    return Matrix3.from(lower)
}

/** Only roundoff-sized negative squared distances may become zero. */
internal fun checkedMd2(value: Double): Double? = when {
    !value.isFinite() -> null
    value >= 0 -> value
    value >= -1e-10 -> 0.0
    else -> null
}

internal fun squaredDistance(delta: RdfValues, lower: Matrix3): Double? {
    // Sigma = L L^T. Solve L z = delta; MD2 = z^T z = delta^T Sigma^-1 delta.
    // This avoids both a raw inverse and cancellation from a final signed dot product.
    val whitened = DoubleArray(3)
    for (i in 0..2) {
        var value = delta[i]
        for (j in 0 until i) value -= lower[i, j] * whitened[j]
        whitened[i] = value / lower[i, i]
    }
    return checkedMd2(whitened.sumOf { it * it })
}

enum class ScoreStatus { AVAILABLE, BASELINE_UNAVAILABLE, INVALID_WINDOW, NUMERICAL_FAILURE }

data class MahalanobisScore(
    val vector: RdfFeatureVector,
    val status: ScoreStatus,
    val md2: Double?,
    val md: Double?,
    val delta: RdfValues?,
) {
    val scoreAvailable: Boolean get() = status == ScoreStatus.AVAILABLE
}

class MahalanobisScorer(private val profile: FrozenMahalanobisProfile?) {
    fun score(vector: RdfFeatureVector): MahalanobisScore {
        fun unavailable(status: ScoreStatus) = MahalanobisScore(vector, status, null, null, null)
        val model = profile ?: return unavailable(ScoreStatus.BASELINE_UNAVAILABLE)
        if (!vector.isUsable(model.config)) return unavailable(ScoreStatus.INVALID_WINDOW)
        val delta = vector.values - model.mean
        val md2 = squaredDistance(delta, model.cholesky) ?: return unavailable(ScoreStatus.NUMERICAL_FAILURE)
        return MahalanobisScore(vector, ScoreStatus.AVAILABLE, md2, sqrt(md2), delta)
    }
}
