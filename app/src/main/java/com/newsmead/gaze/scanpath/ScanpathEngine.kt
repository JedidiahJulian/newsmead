package com.newsmead.gaze.scanpath

import kotlin.math.max

/**
 * Incremental, capture ordered descriptions of provisional gaze fixations on a declared stream.
 * `retainEvents=false` delivers events only by callback; fixations remain indexed for support
 * and grow linearly within an engine generation. The Android adapter caps a generation.
 * Full P12 support lists cost O(prefix length) per event.
 * This is not a difficulty detector.
 */
class ScanpathEngine(
    wordRanges: List<WordRange>,
    val config: ScanpathConfig = ScanpathConfig(),
    private val onFixation: (ScanpathFixation) -> Unit = {},
    private val onEvent: (ScanpathEvent) -> Unit = {},
    private val retainEvents: Boolean = true,
    initialCaptureMs: Double? = null,
) {
    companion object {
        const val EXTRACTOR_VERSION = 1
        const val CLASSIFIER_VERSION = 1
        const val PROVISIONAL_EVIDENCE_VERSION = 1
    }
    private val words = wordRanges.toList()
    private val wordOrdinals = words.withIndex().associate { it.value to it.index }
    private val geometry = mutableMapOf<String, ScanpathGeometry>()
    private val usedGeometryIds = mutableSetOf<String>()
    internal val retainedGeometrySnapshotCount: Int get() = geometry.size
    private var currentGeometry: ScanpathGeometry? = null
    private val pending = mutableListOf<ScanpathSample>()
    private val retainedFixations = mutableListOf<ScanpathFixation>()
    private val retainedEvents = mutableListOf<ScanpathEvent>()
    private var emittedEventCount = 0
    val fixations: List<ScanpathFixation> get() = retainedFixations.toList()
    val events: List<ScanpathEvent> get() = retainedEvents.toList()
    val latestFixation: ScanpathFixation? get() = retainedFixations.lastOrNull()
    val fixationCount: Int get() = retainedFixations.size
    val latestObservedCaptureMs: Double? get() = latestCaptureMs
    private var segment = 0
    private var leftCensored = true
    private var lastAcceptedMs: Double? = null
    private var latestCaptureMs: Double? = initialCaptureMs
    private var coordinateStream: ScanpathCoordinateStream? = null
    private val states = mapOf("word" to PathState(), "line" to PathState())

    init {
        require(initialCaptureMs == null || initialCaptureMs.isFinite())
        require(words.all { it.startUtf16 >= 0 && it.endUtf16 > it.startUtf16 })
        require(words.zipWithNext().all { (a, b) -> a.endUtf16 <= b.startUtf16 })
    }

    fun updateGeometry(snapshot: ScanpathGeometry, atMs: Double?) {
        require(snapshot.snapshotId.isNotBlank() && snapshot.snapshotId !in usedGeometryIds)
        require(snapshot.lineHeightPx.isFinite() && snapshot.lineHeightPx > 0)
        boundary("geometry_change", atMs)
        // The boundary finalized old candidates and reset both path histories. Prior
        // geometry is no longer needed to classify any future P01–P12 observation.
        geometry.clear()
        usedGeometryIds.add(snapshot.snapshotId)
        geometry[snapshot.snapshotId] = snapshot.copy(lines = snapshot.lines.toList())
        currentGeometry = geometry[snapshot.snapshotId]
    }

    /** Returns whether the selected observation entered the fixation detector. */
    fun onSample(sample: ScanpathSample): Boolean {
        val t = sample.captureMs
        val valid = sample.eligible && t != null && t.isFinite() &&
            sample.processingX?.isFinite() == true && sample.processingY?.isFinite() == true &&
            currentGeometry != null && sample.geometrySnapshotId == currentGeometry?.snapshotId
        if (!valid) {
            boundary("invalid_sample", t, availability(sample).first, availability(sample).second)
            noteCapture(t)
            return false
        }
        t!!
        val (available, basis) = availability(sample)
        if (latestCaptureMs != null && t <= latestCaptureMs!!) {
            boundary("non_monotonic_capture", t, available, basis)
            return false
        }
        noteCapture(t)
        if (coordinateStream != null && sample.coordinateStream != coordinateStream) {
            boundary("coordinate_stream_change", t, available, basis)
        }
        coordinateStream = sample.coordinateStream
        val last = lastAcceptedMs
        if (last != null && t - last > config.maxSampleGapMs) {
            boundary("sample_gap", t, available, basis)
        }
        lastAcceptedMs = t
        val candidate = pending + sample
        val threshold = config.dispersionLines * currentGeometry!!.lineHeightPx
        if (pending.isNotEmpty() && dispersion(candidate) > threshold) {
            if (established()) {
                emitFixation(false, available, basis)
                pending.clear()
                leftCensored = false
            } else {
                while (pending.isNotEmpty() && dispersion(pending + sample) > threshold) {
                    pending.removeAt(0)
                    leftCensored = false
                }
            }
        }
        if (pending.size >= config.maxPendingSamples) {
            boundary("sample_buffer_limit", t, available, basis)
            lastAcceptedMs = t
        }
        pending.add(sample)
        return true
    }

    /** Explicit boundary flushes a right-censored candidate and both independent paths. */
    fun boundary(reason: String, atMs: Double?) {
        val available = atMs?.takeIf { it.isFinite() }
        boundary(reason, atMs, available, if (available == null) "unavailable" else "boundary_record_time")
    }

    private fun boundary(reason: String, atMs: Double?, available: Double?, basis: String) {
        val safeTime = atMs?.takeIf { it.isFinite() }
        val safeAvailable = available?.takeIf { it.isFinite() }
        emitFixation(true, safeAvailable, basis)
        for (resolution in listOf("word", "line")) {
            val st = states.getValue(resolution)
            emit("P13", resolution, emptyList(),
                mapOf("reason" to reason, "terminal" to true, "from_segment" to segment,
                    "to_segment" to segment + 1, "at_ms" to safeTime),
                boundaryMs = safeTime, boundaryAvailable = safeAvailable, boundaryBasis = basis)
            // An initial geometry boundary precedes all path groups.
            if (st.previous != null) st.reset()
        }
        pending.clear()
        lastAcceptedMs = null
        segment++
        leftCensored = true
    }

    private fun noteCapture(t: Double?) {
        if (t != null && t.isFinite()) latestCaptureMs = max(latestCaptureMs ?: t, t)
    }

    private fun availability(sample: ScanpathSample): Pair<Double?, String> {
        val capture = sample.captureMs?.takeIf { it.isFinite() }
        val delivery = sample.deliveryMs?.takeIf { it.isFinite() }
        return if (capture != null && delivery != null && delivery >= capture)
            delivery to "trigger_observation_delivery"
        else if (capture != null) capture to "trigger_capture_lower_bound"
        else null to "unavailable"
    }

    private fun established(): Boolean = pending.size >= config.minFixationSamples &&
        pending.last().captureMs!! - pending.first().captureMs!! >= config.minFixationMs

    /** Read-only causal prefix for timely aid policy; never appends a fixation or pattern. */
    fun currentCandidateEvidence(): ScanpathCandidateEvidence? {
        if (!established()) return null
        val (word, wordAgreement) = majority(pending.map { WordRange(it.wordStartUtf16 ?: -1, it.wordEndUtf16 ?: -1) })
        val (line, lineAgreement) = majority(pending.map { it.line })
        val assignedWord = word.startUtf16 >= 0 && word.endUtf16 > word.startUtf16 &&
            wordAgreement >= config.assignmentAgreement
        val start = pending.first().captureMs!!
        val end = pending.last().captureMs!!
        val (available, basis) = availability(pending.last())
        return ScanpathCandidateEvidence(
            "candidate:$segment:$start", segment, start, end, end - start, pending.size,
            word.startUtf16.takeIf { assignedWord }, word.endUtf16.takeIf { assignedWord },
            line?.takeIf { it >= 0 && lineAgreement >= config.assignmentAgreement },
            wordAgreement, lineAgreement, pending.first().geometrySnapshotId, available, basis,
        )
    }

    private fun dispersion(samples: List<ScanpathSample>): Double =
        samples.maxOf { it.processingX!! } - samples.minOf { it.processingX!! } +
            samples.maxOf { it.processingY!! } - samples.minOf { it.processingY!! }

    private fun <T> majority(values: List<T>): Pair<T, Double> {
        val counts = linkedMapOf<T, Int>()
        values.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val winner = counts.maxByOrNull { it.value }!!
        return winner.key to winner.value.toDouble() / values.size
    }

    private fun emitFixation(rightCensored: Boolean, available: Double?, basis: String) {
        if (!established()) return
        val (word, wordAgreement) = majority(pending.map { WordRange(it.wordStartUtf16 ?: -1, it.wordEndUtf16 ?: -1) })
        val (lineValue, lineAgreement) = majority(pending.map { it.line })
        val wordAssigned = word.startUtf16 >= 0 && word.endUtf16 > word.startUtf16 &&
            wordAgreement >= config.assignmentAgreement
        val lineAssigned = lineValue != null && lineValue >= 0 && lineAgreement >= config.assignmentAgreement
        val start = pending.first().captureMs!!
        val end = pending.last().captureMs!!
        val evidence = available?.takeIf { it.isFinite() && it >= end }
        val fixation = ScanpathFixation(
            retainedFixations.size, segment, start, end, end - start, pending.size,
            pending.map { it.processingX!! }.average(), pending.map { it.processingY!! }.average(),
            dispersion(pending), if (wordAssigned) word.startUtf16 else null,
            if (wordAssigned) word.endUtf16 else null, wordAgreement,
            if (lineAssigned) lineValue else null, lineAgreement,
            leftCensored, rightCensored, pending.first().geometrySnapshotId,
            evidence, if (evidence != null) basis else "unavailable"
        )
        retainedFixations.add(fixation)
        onFixation(fixation)
        classify(fixation)
    }

    private data class Visit(val ordinal: Int, val firstIndex: Int, val startMs: Double,
                             val frontierAt: Int, val observedBeforeMs: Double)
    private data class Episode(val id: String, val revisit: Int, val frontier: Int,
                               val firstIndex: Int, val support: List<Int>)
    private class PathState {
        var previous: Int? = null
        val visits = mutableListOf<Visit>()
        val lastFour = ArrayDeque<Visit>()
        val seen = mutableMapOf<Int, Int>()
        var frontier: Int? = null
        var frontierSupport: Int? = null
        var episode: Episode? = null
        var residenceOrdinal: Int? = null
        val residenceIndices = mutableListOf<Int>()
        var residenceStartMs = 0.0
        var residenceObservedMs = 0.0
        var residenceLeftCensored = false
        var observedTotalMs = 0.0
        var group = 0
        fun reset() {
            previous = null; visits.clear(); lastFour.clear(); seen.clear()
            frontier = null; frontierSupport = null; episode = null
            residenceOrdinal = null; residenceIndices.clear(); residenceObservedMs = 0.0
            observedTotalMs = 0.0; group++
        }
    }

    private fun classify(f: ScanpathFixation) {
        val word = if (f.wordStartUtf16 != null && f.wordEndUtf16 != null)
            wordOrdinals[WordRange(f.wordStartUtf16, f.wordEndUtf16)] else null
        for ((resolution, ordinal) in listOf("word" to word, "line" to f.line)) {
            val st = states.getValue(resolution)
            val prevIndex = st.previous
            if (prevIndex != null) {
                val prev = retainedFixations[prevIndex]
                if (f.segment != prev.segment || f.geometrySnapshotId != prev.geometrySnapshotId ||
                    f.startMs - prev.endMs > config.maxTransitionGapMs) {
                    val reason = when {
                        f.segment != prev.segment -> "segment_change_unknown"
                        f.geometrySnapshotId != prev.geometrySnapshotId -> "geometry_change"
                        else -> "unclassified_fixation_gap"
                    }
                    emit("P13", resolution, listOf(prevIndex, f.index),
                        mapOf("reason" to reason, "from_fixation_index" to prevIndex,
                            "to_fixation_index" to f.index, "at_ms" to f.startMs), boundaryMs = f.startMs)
                    st.reset()
                }
            }
            if (ordinal == null || ordinal < 0) {
                if (st.previous != null || f.index == 0) {
                    emit("P13", resolution, listOfNotNull(st.previous, f.index),
                        mapOf("reason" to "unassigned_$resolution", "at_ms" to f.startMs),
                        boundaryMs = f.startMs)
                    st.reset()
                }
                continue
            }
            classifyAssigned(resolution, ordinal, f, st)
        }
    }

    private fun classifyAssigned(resolution: String, ordinal: Int, f: ScanpathFixation, st: PathState) {
        val prevIndex = st.previous
        val previousOrdinal = st.visits.lastOrNull()?.ordinal
        if (prevIndex != null && previousOrdinal != null) {
            val prev = retainedFixations[prevIndex]
            val delta = ordinal - previousOrdinal
            if (resolution == "word") {
                val pid = when { delta == 1 -> "P01"; delta > 1 -> "P02"; delta < 0 -> "P03"; else -> "P04" }
                emit(pid, resolution, listOf(prevIndex, f.index),
                    mapOf("from_ordinal" to previousOrdinal, "to_ordinal" to ordinal,
                        "delta_words" to delta, "from_line" to prev.line, "to_line" to f.line),
                    if (pid == "P02") listOf("intervening_text_not_observed") else emptyList())
                if (delta < 0 && prev.line != null && prev.line == f.line) {
                    emit("P07", resolution, listOf(prevIndex, f.index),
                        mapOf("delta_words" to delta, "line" to f.line))
                }
            } else if (delta != 0) {
                val pid = when { delta == 1 -> "P05"; delta > 1 -> "P06"; else -> "P08" }
                emit(pid, resolution, listOf(prevIndex, f.index),
                    mapOf("from_line" to previousOrdinal, "to_line" to ordinal, "delta_lines" to delta),
                    if (pid == "P06") listOf("intervening_text_not_observed") else emptyList())
                if (delta == 1) emitSweep(prev, f)
            }
        }
        if (st.visits.isEmpty() || ordinal != st.visits.last().ordinal) {
            val oldFrontier = st.frontier
            val visit = Visit(ordinal, f.index, f.startMs, max(oldFrontier ?: ordinal, ordinal), st.observedTotalMs)
            st.visits.add(visit)
            st.lastFour.addLast(visit)
            if (st.lastFour.size > 4) st.lastFour.removeFirst()
            val episode = st.episode
            if (episode != null) {
                val phase = when {
                    ordinal >= episode.frontier -> "frontier_reached"
                    previousOrdinal != null && ordinal > previousOrdinal -> "approaching_frontier"
                    else -> "backward_within_episode"
                }
                emit("P10", resolution, episode.support + (episode.firstIndex..f.index).toList(),
                    mapOf("phase" to phase, "previous_ordinal" to previousOrdinal,
                        "episode_id" to episode.id, "revisited_ordinal" to episode.revisit,
                        "remembered_frontier" to episode.frontier, "current_ordinal" to ordinal))
                if (ordinal >= episode.frontier) st.episode = null
            } else if (ordinal in st.seen) {
                val support = listOfNotNull(st.seen[ordinal], st.frontierSupport, f.index)
                val newEpisode = Episode("$resolution-${st.group}-${f.index}", ordinal,
                    oldFrontier ?: ordinal, f.index, support)
                st.episode = newEpisode
                emit("P10", resolution, support,
                    mapOf("phase" to "revisit", "episode_id" to newEpisode.id,
                        "revisited_ordinal" to ordinal, "remembered_frontier" to oldFrontier))
            }
            st.seen.putIfAbsent(ordinal, f.index)
            val four = st.lastFour.toList()
            if (four.size == 4 && four[0].ordinal == four[2].ordinal &&
                four[1].ordinal == four[3].ordinal && four[0].ordinal != four[1].ordinal) {
                val span = f.endMs - four[0].startMs
                if (config.cycleWindowMs == null || span <= config.cycleWindowMs) {
                    emit("P11", resolution, four.map { it.firstIndex },
                        mapOf("kind" to "alternation_abab", "ordinals" to four.map { it.ordinal },
                            "elapsed_ms" to span, "cycle_window_ms" to config.cycleWindowMs),
                        listOf("structural_cycle_only"))
                }
            }
        }
        st.previous = f.index
        updateResidence(resolution, ordinal, f, st)
        updateProgression(resolution, ordinal, f, st)
    }

    private fun updateResidence(resolution: String, ordinal: Int, f: ScanpathFixation, st: PathState) {
        if (st.residenceOrdinal != ordinal) {
            st.residenceOrdinal = ordinal
            st.residenceIndices.clear()
            st.residenceStartMs = f.startMs
            st.residenceObservedMs = 0.0
            st.residenceLeftCensored = f.leftCensored
        }
        st.residenceIndices.add(f.index)
        st.residenceObservedMs += f.durationMs
        val elapsed = f.endMs - st.residenceStartMs
        val limit = if (resolution == "word") config.wordResidenceMs else config.lineResidenceMs
        val crossed = limit?.let { st.residenceObservedMs >= it }
        emit("P12", resolution, st.residenceIndices,
            mapOf("kind" to "residence_update", "ordinal" to ordinal,
                "observed_span_ms" to st.residenceObservedMs, "elapsed_ms" to elapsed,
                "observed_fraction" to if (elapsed > 0) st.residenceObservedMs / elapsed else null,
                "left_censored" to st.residenceLeftCensored, "right_censored" to f.rightCensored,
                "observed_threshold_crossed" to crossed, "long_flag" to if (crossed == true) true else null),
            listOf("candidate_fixation_spans_only", "lower_bound_if_censored"))
    }

    private fun updateProgression(resolution: String, ordinal: Int, f: ScanpathFixation, st: PathState) {
        val old = st.frontier
        st.frontier = if (old == null) ordinal else max(old, ordinal)
        if (old == null || ordinal > old) st.frontierSupport = f.index
        val window = config.progressionWindowMs
        val anchor = if (window == null) st.visits.first() else
            st.visits.lastOrNull { f.endMs - it.startMs >= window } ?: st.visits.first()
        val observed = st.observedTotalMs - anchor.observedBeforeMs + f.durationMs
        val elapsed = f.endMs - anchor.startMs
        val fraction = if (elapsed > 0) observed / elapsed else null
        val gain = st.frontier!! - anchor.frontierAt
        val rate = if (elapsed > 0) 1000 * gain / elapsed else null
        val limit = if (resolution == "word") config.wordProgressionMaxUnitsPerS else config.lineProgressionMaxUnitsPerS
        val slow = if (limit != null && window != null && elapsed >= window && fraction != null &&
            fraction >= config.progressionMinObservedFraction!!) rate!! <= limit else null
        val metrics = mutableMapOf<String, Any?>(
            "kind" to "frontier_progression", "ordinal" to ordinal,
            "frontier_before" to old, "frontier_after" to st.frontier,
            "window_baseline_frontier" to anchor.frontierAt, "window_start_ms" to anchor.startMs,
            "observed_span_ms" to observed, "elapsed_ms" to elapsed, "observed_fraction" to fraction,
            "frontier_gain_units" to gain,
            "units" to if (resolution == "word") "word_ordinals" else "physical_lines",
            "frontier_gain_units_per_s" to rate, "slow_flag" to slow)
        if (resolution == "word") metrics["frontier_text_utf16_end"] = words[st.frontier!!].endUtf16
        emit("P12", resolution, (anchor.firstIndex..f.index).toList(), metrics,
            listOf("observed_frontier_only", "elapsed_time_contains_unobserved_gaps"))
        st.observedTotalMs += f.durationMs
        // Keep the first anchor and the most recent visit eligible to become a window anchor.
        if (window != null && st.visits.size > 2) {
            val newestEligible = st.visits.indexOfLast { f.endMs - it.startMs >= window }
            if (newestEligible > 1) st.visits.subList(1, newestEligible).clear()
        } else if (window == null && st.visits.size > 1) {
            st.visits.subList(1, st.visits.size - 1).clear()
        }
    }

    private fun emitSweep(prev: ScanpathFixation, f: ScanpathFixation) {
        val from = lineFraction(prev)
        val to = lineFraction(f)
        val reason = when {
            config.returnSweepEndFraction == null -> "not_configured"
            from.second != null -> from.second
            to.second != null -> to.second
            prev.x <= f.x -> "not_leftward"
            else -> null
        }
        val status = when {
            reason != null -> "abstain"
            from.first!! >= config.returnSweepEndFraction!! && to.first!! <= config.returnSweepStartFraction!! -> "observed"
            else -> "not_matched"
        }
        emit("P09", "line", listOf(prev.index, f.index),
            mapOf("status" to status, "reason" to reason,
                "text_direction_requirement" to "ltr",
                "configured_text_direction" to config.textDirection?.name?.lowercase(),
                "direction_provenance" to "geometry_or_explicit_study_condition",
                "from_end_fraction" to from.first, "to_start_fraction" to to.first,
                "delta_x_px" to f.x - prev.x),
            listOf("candidate_geometry_only", "undershoot_possible"))
    }

    private fun lineFraction(f: ScanpathFixation): Pair<Double?, String?> {
        val g = geometry[f.geometrySnapshotId] ?: return null to "geometry_missing"
        val direction = g.textDirection ?: config.textDirection
        if (direction == null) return null to "direction_unknown"
        if (direction != ScanpathTextDirection.LTR) return null to "direction_unsupported"
        val v = g.visibleRectScreen
        if (!listOf(v.left, v.top, v.right, v.bottom).all { it.isFinite() } ||
            v.right <= v.left || v.bottom <= v.top) return null to "visible_rect_missing"
        if (f.x !in v.left..v.right || f.y !in v.top..v.bottom) return null to "outside_visible_rect"
        val row = g.lines.getOrNull(f.line ?: -1) ?: return null to "line_geometry_missing"
        if (row.endUtf16 <= row.startUtf16 || row.right <= row.left || row.bottom <= row.top ||
            !listOf(row.left, row.right, row.top, row.bottom, g.textOriginX, g.textOriginY).all { it.isFinite() })
            return null to "line_geometry_invalid"
        val x = f.x - g.textOriginX
        val y = f.y - g.textOriginY
        if (x !in row.left..row.right || y !in row.top..row.bottom) return null to "outside_line_geometry"
        return (x - row.left) / (row.right - row.left) to null
    }

    private fun emit(pid: String, resolution: String, indices: List<Int>,
                     metrics: Map<String, Any?> = emptyMap(), limitations: List<String> = emptyList(),
                     boundaryMs: Double? = null, boundaryAvailable: Double? = null,
                     boundaryBasis: String? = null) {
        val support = indices.distinct().sorted()
        val source = support.map { retainedFixations[it] }
        val evidence = if (source.isEmpty()) boundaryAvailable else
            source.map { it.evidenceAvailableMs }.takeIf { it.all { t -> t != null } }?.maxOf { it!! }
        val basis = if (source.isEmpty()) boundaryBasis else
            source.map { it.evidenceTimeBasis }.distinct().singleOrNull()
        val event = ScanpathEvent(
            "pattern:${++emittedEventCount}", pid, resolution, support,
            if (source.isEmpty()) boundaryMs else source.minOf { it.startMs },
            boundaryMs ?: source.maxOfOrNull { it.endMs }, evidence, basis, metrics.toMap(), limitations.toList())
        if (retainEvents) retainedEvents.add(event)
        onEvent(event)
    }
}
