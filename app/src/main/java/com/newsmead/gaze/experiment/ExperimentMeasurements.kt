package com.newsmead.gaze.experiment

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt

/** All clocks are elapsed-realtime milliseconds; coordinates are physical-screen pixels. */
data class ExperimentObservation(
    val captureMs: Double?, val deliveryMs: Double, val reason: String,
    val rawX: Double? = null, val rawY: Double? = null,
    val selectedX: Double? = null, val selectedY: Double? = null,
    val arrivals: Long? = null, val busyDrops: Long? = null, val watchdog: Boolean = false,
)

data class TargetWindow(
    val id: String, val xPx: Double, val yPx: Double,
    val startInclusiveCaptureMs: Double, val endExclusiveCaptureMs: Double,
    val deliveryDeadlineMs: Double, val heldOut: Boolean,
)

data class SpatialMetrics(
    val n: Int, val dxBiasPx: Double?, val dyBiasPx: Double?,
    val medianErrorPx: Double?, val p95ErrorPx: Double?,
    /** Sample SD in two dimensions, pooled within targets for an aggregate. */
    val spatialSdPx: Double?, val rmsSuccessivePx: Double?, val successivePairs: Int,
)
data class TargetMetrics(val window: TargetWindow, val raw: SpatialMetrics, val selected: SpatialMetrics)
data class OutputGap(val startMs: Double, val endMs: Double, val leftCensored: Boolean, val rightCensored: Boolean) {
    val durationMs get() = endMs - startMs
}
data class ExperimentSummary(
    val freshHz: Double?, val p95AgeMs: Double?, val coverage: Double?,
    val rawHeldOut: SpatialMetrics?, val selectedHeldOut: SpatialMetrics?,
    val missingHeldOutTargetIds: List<String>,
)
data class ExperimentRow(
    val observation: ExperimentObservation, val accepted: Boolean, val rejection: String?, val targetId: String?,
    /** Null only when the original delivery clock was nonfinite; never export a repaired clock as original. */
    val originalDeliveryMs: Double? = observation.deliveryMs,
)
data class ExperimentResult(
    val mode: String, val startMs: Double, val endMs: Double, val maxFreshAgeMs: Double,
    val observedDeliveredCount: Int, val validUniqueFreshCount: Int,
    val invalidReasons: Map<String, Int>, val arrivalsTotal: Long?, val busyDropsTotal: Long?,
    val counterRegressions: Int, val rejectedClockCount: Int,
    val medianIntersampleMs: Double?, val p95IntersampleMs: Double?,
    val medianAgeMs: Double?, val p95AgeMs: Double?, val gaps: List<OutputGap>,
    val targets: List<TargetMetrics>, val summary: ExperimentSummary,
    val rows: List<ExperimentRow>, val rowsTruncated: Boolean, val omittedObservationCount: Int,
)

/** Bounded descriptive accumulator. Once full, metrics cover retained observations only. */
class ExperimentMeasurements(
    private val startMs: Double, private val mode: String,
    private val maxRows: Int = 100_000, private val maxFreshAgeMs: Double = 500.0,
    private val expectedHeldOutTargetIds: Set<String> = emptySet(),
) {
    private val targets = mutableListOf<TargetWindow>()
    private val rows = mutableListOf<ExperimentRow>()
    private var omitted = 0
    private var lastDelivery = startMs
    private var lastMeasurementDelivery = startMs
    private var lastCapture = Double.NEGATIVE_INFINITY
    private var arrivals: Long? = null
    private var drops: Long? = null
    private var counterRegressions = 0
    private var rejectedClocks = 0
    private var finished = false

    init {
        require(startMs.isFinite() && startMs >= 0)
        require(mode == "validation" || mode == "reading")
        require(maxRows in 1..100_000 && maxFreshAgeMs.isFinite() && maxFreshAgeMs > 0)
        require(expectedHeldOutTargetIds.size <= 1000 && expectedHeldOutTargetIds.all { it.isNotBlank() })
    }

    fun registerTarget(window: TargetWindow) {
        check(!finished)
        require(mode == "validation")
        require(targets.size < 1000)
        require(window.id.isNotBlank() && targets.none { it.id == window.id })
        require(listOf(window.xPx, window.yPx, window.startInclusiveCaptureMs,
            window.endExclusiveCaptureMs, window.deliveryDeadlineMs).all { it.isFinite() })
        require(window.startInclusiveCaptureMs >= startMs && window.endExclusiveCaptureMs > window.startInclusiveCaptureMs)
        require(window.deliveryDeadlineMs >= window.endExclusiveCaptureMs)
        require(targets.none { window.startInclusiveCaptureMs < it.endExclusiveCaptureMs &&
            it.startInclusiveCaptureMs < window.endExclusiveCaptureMs }) { "Ambiguous target windows" }
        targets += window
    }

    fun observe(observation: ExperimentObservation) {
        check(!finished)
        val o = observation
        if (rows.size >= maxRows) {
            omitted++
            if (o.deliveryMs.isFinite() && o.deliveryMs >= lastDelivery) {
                arrivals = updateCounter(o.arrivals, arrivals)
                drops = updateCounter(o.busyDrops, drops)
            }
            return
        }
        if (!o.deliveryMs.isFinite() || o.deliveryMs < startMs || o.deliveryMs < lastDelivery) {
            rejectedClocks++
            // Never retain nonfinite values in JSON or let malformed clocks poison ordering.
            rows += ExperimentRow(o.copy(deliveryMs = lastDelivery,
                captureMs = o.captureMs?.takeIf { it.isFinite() },
                rawX = o.rawX?.takeIf { it.isFinite() }, rawY = o.rawY?.takeIf { it.isFinite() },
                selectedX = o.selectedX?.takeIf { it.isFinite() }, selectedY = o.selectedY?.takeIf { it.isFinite() }),
                false, "invalid_delivery_clock", null, o.deliveryMs.takeIf { it.isFinite() })
            return
        }
        lastDelivery = o.deliveryMs
        if (o.reason != "stopped") lastMeasurementDelivery = o.deliveryMs
        arrivals = updateCounter(o.arrivals, arrivals)
        drops = updateCounter(o.busyDrops, drops)
        val real = !o.watchdog && o.reason != "stopped"
        val capture = o.captureMs
        val rejection = when {
            !real -> if (o.watchdog) "watchdog" else "stopped"
            capture == null || !capture.isFinite() || capture < startMs || capture > o.deliveryMs -> "invalid_clock"
            capture <= lastCapture -> "duplicate_or_regressed_capture"
            o.reason != "coordinate" -> o.reason
            o.deliveryMs - capture > maxFreshAgeMs -> "stale"
            o.rawX?.isFinite() != true || o.rawY?.isFinite() != true -> "invalid_raw_coordinate"
            else -> null
        }
        if (real && capture != null && capture.isFinite() && capture >= startMs && capture <= o.deliveryMs) {
            lastCapture = maxOf(lastCapture, capture)
        }
        val target = if (rejection == null) targets.singleOrNull {
            capture!! >= it.startInclusiveCaptureMs && capture < it.endExclusiveCaptureMs && o.deliveryMs <= it.deliveryDeadlineMs
        } else null
        // Sanitize optional malformed fields without promoting them to valid samples.
        rows += ExperimentRow(o.copy(captureMs = capture?.takeIf { it.isFinite() },
            rawX = o.rawX?.takeIf { it.isFinite() }, rawY = o.rawY?.takeIf { it.isFinite() },
            selectedX = o.selectedX?.takeIf { it.isFinite() }, selectedY = o.selectedY?.takeIf { it.isFinite() }),
            rejection == null, rejection, target?.id)
    }

    private fun updateCounter(value: Long?, previous: Long?): Long? {
        if (value == null) return previous
        if (value < 0 || (previous != null && value < previous)) { counterRegressions++; return previous }
        return value
    }

    fun finish(endMs: Double): ExperimentResult {
        check(!finished)
        // Source shutdown may report terminal counters after the measured collection boundary.
        require(endMs.isFinite() && endMs >= lastMeasurementDelivery)
        finished = true
        val real = rows.filter { !it.observation.watchdog && it.observation.reason != "stopped" }
        val valid = rows.filter { it.accepted }
        // Latency describes all real deliveries with usable clocks, including stale/invalid outputs.
        val ages = real.filter { it.rejection != "invalid_delivery_clock" }.mapNotNull {
            val capture = it.observation.captureMs
            if (capture != null && capture >= startMs && capture <= it.observation.deliveryMs)
                it.observation.deliveryMs - capture else null
        }
        val intervals = mutableListOf<Double>()
        val gaps = mutableListOf<OutputGap>()
        var gapStart: Double? = startMs
        var leftCensored = true
        var expiry: Double? = null
        var previous: ExperimentRow? = null
        for (row in rows) {
            if (row.observation.reason == "stopped") continue
            val now = row.observation.deliveryMs
            if (expiry != null && now > expiry && gapStart == null) { gapStart = expiry; leftCensored = false }
            if (row.accepted) {
                if (gapStart != null) {
                    if (now > gapStart) gaps += OutputGap(gapStart, now, leftCensored, false)
                    gapStart = null
                }
                if (previous?.accepted == true && expiry != null && now <= expiry) {
                    intervals += row.observation.captureMs!! - previous.observation.captureMs!!
                }
                expiry = row.observation.captureMs!! + maxFreshAgeMs
            } else if (row.observation.reason != "stopped") {
                if (gapStart == null) { gapStart = now; leftCensored = false }
                expiry = null
            }
            previous = row
        }
        if (gapStart == null && expiry != null && endMs > expiry) { gapStart = expiry; leftCensored = false }
        if (gapStart != null && endMs > gapStart) gaps += OutputGap(gapStart, endMs, leftCensored, true)
        val perTarget = targets.map { TargetMetrics(it, spatial(listOf(it), false), spatial(listOf(it), true)) }
        val heldOut = targets.filter { it.heldOut }
        val duration = endMs - startMs
        val summary = ExperimentSummary(if (duration > 0) valid.size * 1000.0 / duration else null,
            percentile(ages, .95), if (real.isNotEmpty()) valid.size.toDouble() / real.size else null,
            if (mode == "validation" && heldOut.isNotEmpty()) spatial(heldOut, false) else null,
            if (mode == "validation" && heldOut.isNotEmpty()) spatial(heldOut, true) else null,
            (expectedHeldOutTargetIds + heldOut.map { it.id }).filter { id ->
                perTarget.none { it.window.id == id && it.raw.n > 0 }
            }.sorted())
        return ExperimentResult(mode, startMs, endMs, maxFreshAgeMs, real.size, valid.size,
            real.filter { !it.accepted }.groupingBy { it.rejection!! }.eachCount(), arrivals, drops,
            counterRegressions, rejectedClocks, percentile(intervals, .5), percentile(intervals, .95),
            percentile(ages, .5), percentile(ages, .95), gaps.toList(), perTarget, summary,
            rows.toList(), omitted > 0, omitted)
    }

    private fun spatial(windows: List<TargetWindow>, selected: Boolean): SpatialMetrics {
        val errors = mutableListOf<Double>()
        val dxs = mutableListOf<Double>()
        val dys = mutableListOf<Double>()
        var withinSquared = 0.0
        var degrees = 0
        var pairSquared = 0.0
        var pairs = 0
        for (window in windows) {
            val localX = mutableListOf<Double>()
            val localY = mutableListOf<Double>()
            var previous: ExperimentRow? = null
            for (row in rows) {
                val o = row.observation
                val x = if (selected) o.selectedX else o.rawX
                val y = if (selected) o.selectedY else o.rawY
                if (!row.accepted || row.targetId != window.id || x == null || y == null) { previous = null; continue }
                val dx = x - window.xPx; val dy = y - window.yPx
                errors += hypot(dx, dy); dxs += dx; dys += dy; localX += x; localY += y
                previous?.let { p ->
                    if (o.deliveryMs <= p.observation.captureMs!! + maxFreshAgeMs) {
                        val px = if (selected) p.observation.selectedX!! else p.observation.rawX!!
                        val py = if (selected) p.observation.selectedY!! else p.observation.rawY!!
                        pairSquared += (x - px) * (x - px) + (y - py) * (y - py); pairs++
                    }
                }
                previous = row
            }
            if (localX.size > 1) {
                val mx = localX.average(); val my = localY.average()
                withinSquared += localX.indices.sumOf { (localX[it] - mx) * (localX[it] - mx) + (localY[it] - my) * (localY[it] - my) }
                degrees += localX.size - 1
            }
        }
        return SpatialMetrics(errors.size, dxs.takeIf { it.isNotEmpty() }?.average(), dys.takeIf { it.isNotEmpty() }?.average(),
            percentile(errors, .5), percentile(errors, .95), if (degrees > 0) sqrt(withinSquared / degrees) else null,
            if (pairs > 0) sqrt(pairSquared / pairs) else null, pairs)
    }

    companion object {
        /** Nearest-rank percentiles, with conventional average of the central pair for median. */
        internal fun percentile(values: List<Double>, p: Double): Double? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            if (p == .5 && sorted.size % 2 == 0) return sorted[sorted.size / 2 - 1] / 2 + sorted[sorted.size / 2] / 2
            return sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
        }
    }
}
