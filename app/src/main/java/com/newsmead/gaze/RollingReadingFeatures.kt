package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.*
import java.util.ArrayDeque

/** Time window over existing line-event counters. All times are elapsed capture milliseconds. */
class RollingReadingFeatures(private val config: MahalanobisConfig = MahalanobisConfig()) {
    private data class Counters(val at: Long, val regressions: Int, val stays: Int)
    private data class DwellInterval(val start: Long, val end: Long)
    private data class Quality(val at: Long, val valid: Boolean)
    private val counters = ArrayDeque<Counters>()
    private val quality = ArrayDeque<Quality>()
    private val dwellIntervals = ArrayDeque<DwellInterval>()
    private var regressions = 0
    private var stays = 0
    private var lastTick: Long? = null
    private var lastInput: Long? = null
    private var discontinuityAt: Long? = null

    fun regression() { regressions++ }
    fun dwell(startMs: Long, endMs: Long) {
        if (endMs - startMs >= ReadingStateInferencer.DEFAULT_MIN_DWELL_MS)
            dwellIntervals.addLast(DwellInterval(startMs, endMs))
    }
    fun fixation() { stays++ }

    fun recordQuality(atMs: Long, valid: Boolean) {
        val previous = lastInput
        if (previous != null && atMs <= previous) return
        if (previous != null && atMs - previous > config.maxGapMs) discontinuityAt = atMs
        lastInput = atMs
        quality.addLast(Quality(atMs, valid))
        trimQuality(atMs)
    }

    /** A tick schedules an update; the feature denominator remains the full rolling window. */
    fun tick(atMs: Long, ongoingDwellStartMs: Long? = null, ongoingDwellEndMs: Long? = null): RdfFeatureVector? {
        val previous = lastTick
        if (previous != null && atMs - previous < config.updateIntervalMs) return null
        if (previous != null && atMs - previous > config.maxGapMs) discontinuityAt = atMs
        lastTick = atMs
        counters.addLast(Counters(atMs, regressions, stays))
        while (counters.size > 2) {
            val iterator = counters.iterator()
            iterator.next()
            if (iterator.next().at > atMs - config.windowMs) break
            counters.removeFirst()
        }
        trimQuality(atMs)
        val first = counters.first
        val span = atMs - first.at
        while (dwellIntervals.isNotEmpty() && dwellIntervals.first.end <= first.at)
            dwellIntervals.removeFirst()
        fun overlap(start: Long, end: Long) = (minOf(end, atMs) - maxOf(start, first.at)).coerceAtLeast(0L)
        var observedDwellMs = dwellIntervals.sumOf { overlap(it.start, it.end) }
        if (ongoingDwellStartMs != null && ongoingDwellEndMs != null &&
            ongoingDwellEndMs - ongoingDwellStartMs >= ReadingStateInferencer.DEFAULT_MIN_DWELL_MS)
            observedDwellMs += overlap(ongoingDwellStartMs, ongoingDwellEndMs)
        val validCount = quality.count { it.valid }
        val fraction = if (quality.isEmpty()) 0.0 else validCount.toDouble() / quality.size
        val fresh = lastInput?.let { atMs - it in 0..config.maxGapMs } == true
        val state = when {
            span < config.windowMs -> FeatureQuality.INSUFFICIENT
            span > config.windowMs + config.maxWindowOverhangMs -> FeatureQuality.DISCONTINUITY
            discontinuityAt?.let { it > first.at } == true -> FeatureQuality.DISCONTINUITY
            !fresh || quality.peekLast()?.valid != true || fraction < config.minimumValidFraction -> FeatureQuality.MISSING
            else -> FeatureQuality.VALID
        }
        val values = if (span > 0) RdfValues(
            (regressions - first.regressions) * 60_000.0 / span,
            (observedDwellMs.toDouble() / span).coerceIn(0.0, 1.0),
            (stays - first.stays) * 60_000.0 / span,
        ) else RdfValues(0.0, 0.0, 0.0)
        return RdfFeatureVector(atMs, first.at, atMs, values, state, fraction, validCount, quality.size)
    }

    fun reset() {
        counters.clear(); quality.clear(); dwellIntervals.clear(); regressions = 0; stays = 0
        lastTick = null; lastInput = null; discontinuityAt = null
    }

    private fun trimQuality(atMs: Long) {
        while (quality.isNotEmpty() && quality.first.at < atMs - config.windowMs) quality.removeFirst()
    }
}
