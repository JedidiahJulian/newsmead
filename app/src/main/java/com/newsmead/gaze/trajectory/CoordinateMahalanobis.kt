package com.newsmead.gaze.trajectory

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Pure descriptive coordinate distance. Pixels and elapsed milliseconds; no cognitive labels. */
object CoordinateMahalanobis {
    const val VERSION = "coordinate_md_v1"
    const val INPUT_INSTRUMENT = "newsmead_coordinate_md_input_v1"
    const val RAW_STREAM = "calibrated_raw_v1"
    const val SELECTED_STREAM = "article_smoothed_v1"

    enum class Status {
        READY, AVAILABLE, INSUFFICIENT_REFERENCE, REFERENCE_DEGENERATE,
        REFERENCE_ILL_CONDITIONED, REFERENCE_UNAVAILABLE, REFERENCE_MISMATCH, REFERENCE_BOUNDS_MISMATCH,
        REFERENCE_PERIOD, CONTEXT_MISMATCH, CONTEXT_EXCLUDED, SOURCE_UNAVAILABLE,
        WATCHDOG, INVALID_CLOCK, NON_MONOTONIC_CAPTURE, NON_INCREASING_DELIVERY,
        NONFINITE_COORDINATE, NUMERICAL_FAILURE, RECORDING_LOSS,
        CONTEXT_DISCONTINUITY, INSUFFICIENT_COVERAGE, NO_OBSERVED_INTERVALS
    }

    data class Context(
        val participantId: String, val installationId: String, val calibrationId: String,
        val modelId: String, val acquisitionId: String, val presentationId: String,
        val clockDomainId: String, val screenWidthPx: Int, val screenHeightPx: Int,
        val rotationDegrees: Int = 0, val coordinateStream: String = RAW_STREAM
    ) {
        init {
            require(listOf(participantId, installationId, calibrationId, modelId,
                acquisitionId, presentationId, clockDomainId).all { it.isNotBlank() })
            require(screenWidthPx > 0 && screenHeightPx > 0)
            require(rotationDegrees in listOf(0, 90, 180, 270))
            require(coordinateStream == RAW_STREAM || coordinateStream == SELECTED_STREAM)
        }
    }

    data class Config(
        val maxGapMs: Double = 250.0, val maxAgeMs: Double = 500.0,
        val minReferencePoints: Int = 3, val minReferenceCoverageMs: Double = 60_000.0,
        val minSummaryCoverageFraction: Double = 0.65, val relativeRidge: Double = 0.0,
        val maxConditionNumber: Double = 1e12
    ) {
        init {
            require(listOf(maxGapMs, maxAgeMs, maxConditionNumber).all { it.isFinite() && it > 0 })
            require(minReferencePoints >= 3 && maxConditionNumber >= 1)
            require(minReferenceCoverageMs.isFinite() && minReferenceCoverageMs >= 0)
            require(minSummaryCoverageFraction.isFinite() && minSummaryCoverageFraction in 0.0..1.0)
            require(relativeRidge.isFinite() && relativeRidge >= 0)
        }
    }

    data class Observation(
        val context: Context, val providerEpochId: String, val contextEpochId: String,
        val deliveryId: Long, val captureMs: Double?, val deliveryMs: Double,
        val xPx: Double?, val yPx: Double?, val reason: String = "coordinate",
        val watchdog: Boolean = false, val contextAllowed: Boolean = true
    ) {
        init {
            require(listOf(providerEpochId, contextEpochId, reason).all { it.isNotBlank() })
            require(deliveryId > 0)
        }
    }
    data class Boundary(val atMs: Double, val reason: String) {
        init { require(atMs.isFinite() && atMs >= 0 && reason.isNotBlank()) }
    }
    data class LossRange(val startMs: Double?, val endMs: Double?, val reason: String) {
        init {
            require(listOf(startMs, endMs).all { it == null || (it.isFinite() && it >= 0) })
            require(startMs == null || endMs == null || endMs >= startMs)
            require(reason.isNotBlank())
        }
        fun overlaps(startMs: Double, endMs: Double): Boolean =
            (this.startMs == null || this.startMs <= endMs) &&
                (this.endMs == null || this.endMs >= startMs)
    }
    data class Vec2(val x: Double, val y: Double)
    data class Cov2(val xx: Double, val xy: Double, val yy: Double)
    data class LowerFactor(val l00: Double, val l10: Double, val l11: Double)
    // Defensive copies prevent a caller's mutable list from changing a frozen profile.
    class Stats(
        val receivedCount: Int, val eligibleCount: Int, val weightedPointCount: Int,
        val intervalCount: Int, val coveredMs: Double, val weightSquaredSumMs2: Double,
        reasons: List<Pair<String, Int>>
    ) {
        val reasons: List<Pair<String, Int>> = java.util.Collections.unmodifiableList(reasons.toList())
    }
    class Profile internal constructor(
        val referenceId: String, val context: Context, val config: Config,
        val referenceStartMs: Double, val referenceEndMs: Double, val mean: Vec2,
        val covariance: Cov2, val scoringCovariance: Cov2, val ridgeAddedPx2: Double,
        val conditionNumber: Double, val factorScale: Double, val factor: LowerFactor,
        val unweightedSampleMean: Vec2, val unweightedSampleCovariance: Cov2,
        val stats: Stats, referenceDeliveryMaxima: List<Pair<String, Long>>,
        val version: String = VERSION
    ) {
        val referenceDeliveryMaxima: List<Pair<String, Long>> =
            java.util.Collections.unmodifiableList(referenceDeliveryMaxima.toList())
    }
    data class FitResult(val status: Status, val profile: Profile?, val stats: Stats)
    data class PointScore(
        val observation: Observation, val referenceId: String?, val status: Status,
        val md2: Double? = null, val md: Double? = null, val deltaPx: Vec2? = null
    )
    data class Summary(
        val startMs: Double, val endMs: Double, val referenceId: String?, val status: Status,
        val meanMd2: Double?, val coverageFraction: Double, val stats: Stats
    )

    private fun finite(value: Double?): Boolean = value != null && value.isFinite()
    private class Validator(
        val context: Context, val config: Config, maxima: List<Pair<String, Long>> = emptyList()
    ) {
        val maxima = maxima.toMap().toMutableMap()
        var captureFloor: Double? = null
        fun accept(o: Observation): Status {
            if (o.context != context) return Status.CONTEXT_MISMATCH
            val old = maxima[o.providerEpochId]
            if (old != null && o.deliveryId <= old) return Status.NON_INCREASING_DELIVERY
            maxima[o.providerEpochId] = o.deliveryId
            if (o.watchdog) return Status.WATCHDOG
            val t = o.captureMs
            if (!finite(t) || !o.deliveryMs.isFinite() || t!! < 0 || o.deliveryMs < t)
                return Status.INVALID_CLOCK
            if (o.reason != "coordinate") {
                captureFloor = maxOf(captureFloor ?: 0.0, t)
                return Status.SOURCE_UNAVAILABLE
            }
            if (captureFloor != null && t <= captureFloor!!) return Status.NON_MONOTONIC_CAPTURE
            captureFloor = t
            if (o.deliveryMs - t > config.maxAgeMs) return Status.SOURCE_UNAVAILABLE
            if (!o.contextAllowed) return Status.CONTEXT_EXCLUDED
            if (!finite(o.xPx) || !finite(o.yPx)) return Status.NONFINITE_COORDINATE
            return Status.AVAILABLE
        }
    }
    private fun bounds(start: Double, end: Double) {
        require(start.isFinite() && end.isFinite() && start >= 0 && end > start)
    }
    private fun weights(
        observations: List<Observation>, statuses: List<Status>, start: Double, end: Double,
        config: Config, boundaries: List<Boundary>
    ): Pair<DoubleArray, Int> {
        val weights = DoubleArray(observations.size)
        var intervals = 0
        var previous: Int? = null
        observations.forEachIndexed { i, o ->
            val t = o.captureMs
            if (statuses[i] != Status.AVAILABLE || t == null || t !in start..end) {
                previous = null
            } else {
                previous?.let { j ->
                    val p = observations[j]
                    val pt = p.captureMs!!
                    val dt = t - pt
                    if (p.providerEpochId == o.providerEpochId && p.contextEpochId == o.contextEpochId &&
                        dt > 0 && dt <= config.maxGapMs && boundaries.none { pt < it.atMs && it.atMs <= t }) {
                        weights[j] += dt / 2
                        weights[i] += dt / 2
                        intervals++
                    }
                }
                previous = i
            }
        }
        return weights to intervals
    }
    // Compensated accumulation limits rounding without raw-square subtraction.
    private fun sum(values: Iterable<Double>): Double {
        var total = 0.0
        var correction = 0.0
        for (v in values) {
            // Current callers sum nonnegative quantities; preserve an overflowed
            // squared-weight diagnostic as infinity rather than creating NaN.
            if (!v.isFinite()) return v
            val next = total + v
            correction += if (abs(total) >= abs(v)) (total - next) + v else (v - next) + total
            total = next
        }
        return total + correction
    }
    private fun stats(statuses: List<Status>, weights: DoubleArray, intervals: Int): Stats = Stats(
        statuses.size, statuses.count { it == Status.AVAILABLE }, weights.count { it > 0 },
        intervals, sum(weights.asIterable()), sum(weights.map { it * it }),
        statuses.groupingBy { it.name }.eachCount().toSortedMap().toList()
    )
    private fun moments(observations: List<Observation>, weights: DoubleArray, sample: Boolean = false): Pair<Vec2, Cov2> {
        var total = 0.0; var mx = 0.0; var my = 0.0
        var cxx = 0.0; var cxy = 0.0; var cyy = 0.0
        observations.forEachIndexed { i, o ->
            val w = weights[i]
            if (w > 0) {
                val updated = total + w
                val dx = o.xPx!! - mx; val dy = o.yPx!! - my
                val nx = mx + dx * (w / updated); val ny = my + dy * (w / updated)
                cxx += w * dx * (o.xPx - nx)
                cxy += w * (dx * (o.yPx - ny) + dy * (o.xPx - nx)) / 2
                cyy += w * dy * (o.yPx - ny)
                total = updated; mx = nx; my = ny
            }
        }
        val denominator = if (sample) total - 1 else total
        check(denominator > 0)
        val cov = Cov2(cxx / denominator, cxy / denominator, cyy / denominator)
        check(listOf(mx, my, cov.xx, cov.xy, cov.yy).all { it.isFinite() })
        return Vec2(mx, my) to cov
    }
    private data class FactorResult(
        val status: Status, val cov: Cov2? = null, val ridge: Double = 0.0,
        val condition: Double = 0.0, val scale: Double = 0.0, val lower: LowerFactor? = null
    )
    private fun factor(covariance: Cov2, config: Config): FactorResult {
        val trace = covariance.xx + covariance.yy
        if (!trace.isFinite()) return FactorResult(Status.NUMERICAL_FAILURE)
        if (trace <= 0) return FactorResult(Status.REFERENCE_DEGENERATE)
        val ridge = config.relativeRidge * (trace / 2)
        val cov = Cov2(covariance.xx + ridge, covariance.xy, covariance.yy + ridge)
        val scale = maxOf(abs(cov.xx), abs(cov.xy), abs(cov.yy))
        if (!listOf(ridge, cov.xx, cov.xy, cov.yy, scale).all { it.isFinite() } || scale <= 0)
            return FactorResult(Status.NUMERICAL_FAILURE)
        val a = cov.xx / scale; val b = cov.xy / scale; val d = cov.yy / scale
        if (a <= 0) return FactorResult(Status.REFERENCE_DEGENERATE)
        val l00 = sqrt(a); val l10 = b / l00
        val residual = d - l10 * l10
        if (residual <= 0) return FactorResult(Status.REFERENCE_DEGENERATE)
        val l11 = sqrt(residual)
        val largest = (a + d) / 2 + hypot((a - d) / 2, b)
        val smallest = (a * residual) / largest
        if (smallest <= 0) return FactorResult(Status.REFERENCE_DEGENERATE)
        val condition = largest / smallest
        if (!condition.isFinite() || condition > config.maxConditionNumber)
            return FactorResult(Status.REFERENCE_ILL_CONDITIONED)
        return FactorResult(Status.READY, cov, ridge, condition, scale, LowerFactor(l00, l10, l11))
    }

    fun fitReference(
        referenceId: String, context: Context, observations: List<Observation>,
        startMs: Double, endMs: Double, config: Config = Config(),
        boundaries: List<Boundary> = emptyList(), losses: List<LossRange> = emptyList()
    ): FitResult {
        bounds(startMs, endMs)
        require(referenceId.isNotBlank())
        val validator = Validator(context, config)
        val statuses = observations.map { validator.accept(it) }
        val (weights, intervals) = weights(observations, statuses, startMs, endMs, config, boundaries)
        val stats = stats(statuses, weights, intervals)
        fun fail(status: Status) = FitResult(status, null, stats)
        // Reject accidental reference/evaluation pooling rather than freezing
        // delivery maxima from observations outside the declared reference.
        if (observations.any {
                val at = if (finite(it.captureMs)) it.captureMs else it.deliveryMs.takeIf { t -> t.isFinite() }
                at != null && at !in startMs..endMs
            }) return fail(Status.REFERENCE_BOUNDS_MISMATCH)
        if (Status.CONTEXT_MISMATCH in statuses) return fail(Status.REFERENCE_MISMATCH)
        if (losses.any { it.overlaps(startMs, endMs) }) return fail(Status.RECORDING_LOSS)
        if (stats.weightedPointCount < config.minReferencePoints || stats.coveredMs < config.minReferenceCoverageMs)
            return fail(Status.INSUFFICIENT_REFERENCE)
        val weighted: Pair<Vec2, Cov2>
        val unweighted: Pair<Vec2, Cov2>
        try {
            weighted = moments(observations, weights)
            unweighted = moments(observations, DoubleArray(weights.size) { if (weights[it] > 0) 1.0 else 0.0 }, true)
        } catch (_: IllegalStateException) { return fail(Status.NUMERICAL_FAILURE) }
        val f = factor(weighted.second, config)
        if (f.status != Status.READY) return fail(f.status)
        return FitResult(Status.READY, Profile(referenceId, context, config, startMs, endMs,
            weighted.first, weighted.second, f.cov!!, f.ridge, f.condition, f.scale, f.lower!!,
            unweighted.first, unweighted.second, stats, validator.maxima.toSortedMap().toList()), stats)
    }

    /** Profile stays frozen; only delivery/capture validation state advances. */
    class CoordinateScorer(val profile: Profile?, val context: Context, config: Config? = null) {
        val config: Config = profile?.config ?: config ?: Config()
        private val validator = Validator(context, this.config,
            if (profile != null && profile.context == context) profile.referenceDeliveryMaxima else emptyList())
        fun score(observation: Observation): PointScore {
            val p = profile
            fun result(status: Status) = PointScore(observation, p?.referenceId, status)
            val status = validator.accept(observation)
            if (status != Status.AVAILABLE) return result(status)
            if (p == null) return result(Status.REFERENCE_UNAVAILABLE)
            if (p.context != context) return result(Status.REFERENCE_MISMATCH)
            if (observation.captureMs!! <= p.referenceEndMs) return result(Status.REFERENCE_PERIOD)
            val delta = Vec2(observation.xPx!! - p.mean.x, observation.yPx!! - p.mean.y)
            val rootScale = sqrt(p.factorScale)
            val zx = (delta.x / rootScale) / p.factor.l00
            val zy = (delta.y / rootScale - p.factor.l10 * zx) / p.factor.l11
            val md2 = zx * zx + zy * zy
            if (!listOf(delta.x, delta.y, md2).all { it.isFinite() }) return result(Status.NUMERICAL_FAILURE)
            return PointScore(observation, p.referenceId, Status.AVAILABLE, md2, sqrt(md2), delta)
        }
    }
    fun scorePoints(profile: Profile?, context: Context, observations: List<Observation>, config: Config? = null): List<PointScore> {
        val scorer = CoordinateScorer(profile, context, config)
        return observations.map { scorer.score(it) }
    }

    /** Re-score ordered observations; invalid/null-clock rows remain adjacency breaks. */
    fun summarize(
        profile: Profile?, context: Context, observations: List<Observation>, startMs: Double, endMs: Double,
        boundaries: List<Boundary> = emptyList(), losses: List<LossRange> = emptyList(), config: Config? = null
    ): Summary {
        bounds(startMs, endMs)
        val cfg = profile?.config ?: config ?: Config()
        val scores = scorePoints(profile, context, observations, cfg)
        val statuses = scores.map { it.status }
        val (weights, intervals) = weights(observations, statuses, startMs, endMs, cfg, boundaries)
        val selected = observations.indices.filter {
            val o = observations[it]
            val at = if (finite(o.captureMs)) o.captureMs!! else o.deliveryMs
            at.isFinite() && at in startMs..endMs
        }
        val stats = stats(selected.map { statuses[it] }, selected.map { weights[it] }.toDoubleArray(), intervals)
        val coverage = stats.coveredMs / (endMs - startMs)
        fun result(status: Status, mean: Double? = null) =
            Summary(startMs, endMs, profile?.referenceId, status, mean, coverage, stats)
        if (losses.any { it.overlaps(startMs, endMs) }) return result(Status.RECORDING_LOSS)
        if (profile == null) return result(Status.REFERENCE_UNAVAILABLE)
        if (profile.context != context || selected.any { observations[it].context != context })
            return result(Status.REFERENCE_MISMATCH)
        val epochs = selected.map { observations[it].let { o -> o.providerEpochId to o.contextEpochId } }.toSet()
        if (epochs.size > 1 || boundaries.any { startMs < it.atMs && it.atMs < endMs })
            return result(Status.CONTEXT_DISCONTINUITY)
        if (stats.coveredMs <= 0) return result(Status.NO_OBSERVED_INTERVALS)
        if (coverage < cfg.minSummaryCoverageFraction) return result(Status.INSUFFICIENT_COVERAGE)
        val mean = sum(weights.indices.filter { weights[it] > 0 }.map { weights[it] / stats.coveredMs * scores[it].md2!! })
        return if (mean.isFinite()) result(Status.AVAILABLE, mean) else result(Status.NUMERICAL_FAILURE)
    }
}
