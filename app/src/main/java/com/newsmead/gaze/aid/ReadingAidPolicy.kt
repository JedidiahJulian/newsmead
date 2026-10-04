package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathFixation
import com.newsmead.gaze.scanpath.ScanpathTextDirection
import kotlin.math.max
import kotlin.math.min

/** Causal policy for observable targets. Rules are experimental mappings, not difficulty estimates. */
class ReadingAidPolicy(
    val config: ReadingAidPolicyConfig = ReadingAidPolicyConfig.manualOnly(),
    preferences: ReaderAidPreferences = ReaderAidPreferences()
) {
    var preferences: ReaderAidPreferences = preferences
        private set
    private val active = mutableMapOf<ReadingAidType, ReadingAidAction>()
    private val cooldownUntil = mutableMapOf<ReadingAidType, Double>()
    private val seenEvents = mutableSetOf<String>()
    private var serial = 0L
    private var epoch: Pair<Long, String>? = null
    private var segment: Int? = null
    private var wordVisit: WordVisit? = null
    private var cycle: Cycle? = null
    private var lineResidence: LineResidence? = null
    private var continuationStart: ContinuationSupport? = null
    private var continuationEnd: ContinuationSupport? = null
    private var markerLanding: ContinuationSupport? = null
    private var lastContinuationCapture = Double.NEGATIVE_INFINITY
    private var continuationFloor = Double.NEGATIVE_INFINITY

    private data class ContinuationSupport(val evidence: ReadingAidContinuationEvidence,
        val fraction: Double) {
        val key get() = evidence.segment to evidence.firstCaptureMs
    }

    private data class WordVisit(
        val segment: Int, val line: Int, val start: Int, val end: Int,
        val firstMs: Double, val observedMs: Double, val fixationIndices: List<Int>,
        val lastFinalEndMs: Double, val lastEventId: String, val availableMs: Double,
        val refixationEventIds: List<String>
    )
    private data class Cycle(val eventId: String, val endMs: Double, val availableMs: Double)
    private data class LineResidence(val eventId: String, val line: Int, val observedMs: Double,
                                     val slow: Boolean, val slowEventId: String?,
                                     val endMs: Double, val availableMs: Double)

    fun activeActions(): List<ReadingAidAction> = active.values.sortedBy { it.aidType.name }

    fun updatePreferences(value: ReaderAidPreferences, context: ReadingAidContext): List<ReadingAidDecision> {
        preferences = value
        val out = prepare(context).toMutableList()
        if (!allowed(ReadingAidType.NEXT_LINE_START) || value.maxStrength == 0.0 ||
            (!value.adaptiveStrength && value.manualStrength == 0.0)) resetContinuation()
        for ((type, action) in active.toMap()) {
            if (!allowed(type) || value.maxStrength == 0.0 ||
                (!value.adaptiveStrength && value.manualStrength == 0.0))
                clear(type, context.nowMs, "reader_disabled_or_zero_strength", out)
            else if (type != ReadingAidType.NEXT_LINE_START &&
                (action.strength > value.maxStrength || !value.adaptiveStrength)) {
                clear(type, context.nowMs, "reader_strength_changed", out)
            }
        }
        return out
    }

    fun tick(context: ReadingAidContext): List<ReadingAidDecision> = prepare(context)

    fun reset(context: ReadingAidContext): List<ReadingAidDecision> {
        val out = mutableListOf<ReadingAidDecision>()
        clearAll(context.nowMs, "explicit_reset", out)
        resetEvidence()
        continuationFloor = context.captureMs ?: context.nowMs
        epoch = context.generation to context.geometrySnapshotId
        return out
    }

    fun onEvent(event: ScanpathEvent, latestFinalizedFixation: ScanpathFixation?,
                context: ReadingAidContext): List<ReadingAidDecision> {
        val out = prepare(context).toMutableList()
        if (event.eventId in seenEvents) return out
        seenEvents.add(event.eventId)
        if (event.patternId == "P13") {
            val reason = event.metrics["reason"] as? String ?: "unknown_break"
            if (event.resolution == "word" && reason == "unassigned_word") {
                wordVisit = null
                clear(ReadingAidType.WORD_MAGNIFIER, context.nowMs, reason, out)
            } else {
                clearAll(context.nowMs, reason, out)
                resetEvidence()
                continuationFloor = context.captureMs ?: context.nowMs
            }
            return out
        }
        if (!valid(context)) return suppress("invalid_live_input", event, context, out)
        if (!fresh(event.endMs, event.evidenceAvailableMs, event.evidenceTimeBasis, context)) {
            resetContinuation()
            return suppress("late_or_unavailable_evidence", event, context, out)
        }
        val f = latestFinalizedFixation
        if (f == null || f.geometrySnapshotId != context.geometrySnapshotId ||
            f.index !in event.fixationIndices || f.endMs > context.captureMs!! ||
            f.evidenceAvailableMs == null || f.evidenceAvailableMs > context.nowMs) {
            resetContinuation()
            return suppress("unsupported_or_stale_fixation", event, context, out)
        }
        if (segment != null && f.segment != segment) {
            clearAll(context.nowMs, "segment_changed", out)
            resetEvidence()
        }
        segment = f.segment
        val supportRow = context.geometry?.lines?.getOrNull(f.line ?: -1)
        if (!f.leftCensored && !f.rightCensored && supportRow != null && f.y.isFinite() &&
            f.y - context.geometry!!.textOriginY in supportRow.top..supportRow.bottom &&
            fresh(f.endMs, f.evidenceAvailableMs, f.evidenceTimeBasis, context))
            continuationSupport("fixation:${f.index}", f.segment, f.line, f.startMs,
                f.endMs, f.evidenceAvailableMs!!, f.x, context, out)
        else if (!(f.leftCensored && !f.rightCensored && continuationStart?.let {
                it.key == (f.segment to f.startMs) && it.evidence.line == f.line &&
                    f.endMs >= it.evidence.lastCaptureMs &&
                    context.captureMs!! - f.endMs in 0.0..config.maxEvidenceAgeMs
            } == true)) resetContinuation()
        if (f.line != null && f.line == context.targetLine && preferences.lineGuide) choose(
            ReadingAidType.CURRENT_LINE, anchor(context, f.line), config.minAdaptiveStrength,
            f.startMs, f.endMs, f.evidenceAvailableMs,
            listOf(event.eventId), listOf("fixation:${f.index}"), "A1_RELIABLE_LINE_V1",
            listOf("W24", "N23"),
            "W24 gaze-following line form; N23 mouse-controlled ruler form; live phone benefit is untested.",
            context, out)
        when {
            event.patternId == "P04" && event.resolution == "word" -> {
                val visit = wordVisit
                if (visit != null && visit.segment == f.segment && visit.line == f.line &&
                    visit.start == f.wordStartUtf16 && visit.end == f.wordEndUtf16)
                    wordVisit = visit.copy(refixationEventIds = visit.refixationEventIds + event.eventId)
            }
            event.patternId == "P12" && event.resolution == "word" &&
                event.metrics["kind"] == "residence_update" -> {
                val observed = (event.metrics["observed_span_ms"] as? Number)?.toDouble()
                val wordStart = f.wordStartUtf16
                val wordEnd = f.wordEndUtf16
                if (observed != null && observed.isFinite() && observed >= 0 &&
                    wordStart != null && wordEnd != null && f.line != null &&
                    context.targetLine == f.line && context.targetWordStartUtf16 == wordStart &&
                    context.targetWordEndUtf16 == wordEnd) {
                    val prior = wordVisit?.takeIf { it.segment == f.segment &&
                        it.line == f.line && it.start == wordStart && it.end == wordEnd &&
                        it.fixationIndices.firstOrNull() == event.fixationIndices.firstOrNull() }
                    wordVisit = WordVisit(f.segment, f.line, wordStart, wordEnd,
                        event.startMs ?: f.startMs, observed, event.fixationIndices,
                        f.endMs, event.eventId, event.evidenceAvailableMs!!,
                        prior?.refixationEventIds ?: emptyList())
                    maybeWord(context, null, out)
                } else {
                    wordVisit = null
                    clear(ReadingAidType.WORD_MAGNIFIER, context.nowMs, "word_anchor_unavailable", out)
                }
            }
            event.patternId == "P12" && event.resolution == "line" &&
                event.metrics["kind"] == "residence_update" -> {
                val observed = (event.metrics["observed_span_ms"] as? Number)?.toDouble()
                if (observed != null && observed.isFinite() && f.line != null) {
                    lineResidence = LineResidence(event.eventId, f.line, observed,
                        false, null, f.endMs, event.evidenceAvailableMs!!)
                    maybeFocus(context, out)
                }
            }
            event.patternId == "P12" && event.resolution == "line" &&
                event.metrics["kind"] == "frontier_progression" -> {
                val slow = event.metrics["slow_flag"] == true
                val prior = lineResidence
                if (prior != null && prior.line == f.line)
                    lineResidence = prior.copy(slow = slow,
                        slowEventId = if (slow) event.eventId else null, endMs = f.endMs,
                        availableMs = event.evidenceAvailableMs!!)
                maybeFocus(context, out)
            }
            event.patternId == "P11" && event.resolution == "line" -> {
                cycle = Cycle(event.eventId, f.endMs, event.evidenceAvailableMs!!)
                maybeFocus(context, out)
            }
        }
        return out
    }

    fun onObservation(candidate: ScanpathCandidateEvidence?, context: ReadingAidContext): List<ReadingAidDecision> {
        val out = prepare(context).toMutableList()
        if (!valid(context)) return out
        if (candidate != null && segment != null && candidate.segment != segment) {
            clearAll(context.nowMs, "segment_changed", out)
            resetEvidence()
        }
        if (candidate != null && candidate.geometrySnapshotId == context.geometrySnapshotId &&
            candidate.line == context.targetLine && (segment == null || candidate.segment == segment) &&
            fresh(candidate.endMs, candidate.evidenceAvailableMs, candidate.evidenceTimeBasis, context)) {
            if (segment == null) segment = candidate.segment
            continuationSupport(candidate.candidateId, candidate.segment, candidate.line,
                candidate.startMs, candidate.endMs, candidate.evidenceAvailableMs!!,
                context.targetX, context, out)
            val line = candidate.line
            if (line != null && preferences.lineGuide) choose(
                ReadingAidType.CURRENT_LINE, anchor(context, line), config.minAdaptiveStrength,
                candidate.startMs, candidate.endMs, candidate.evidenceAvailableMs!!,
                emptyList(), listOf(candidate.candidateId), "A1_RELIABLE_LINE_V1",
                listOf("W24", "N23"),
                "W24 gaze-following line form; N23 mouse-controlled ruler form; live phone benefit is untested.",
                context, out)
            maybeWord(context, candidate, out)
        } else if (candidate != null) {
            // A valid majority prefix may disagree with one latest raw row assignment.
            // Withhold gaze-following aids while retaining supported continuation state.
            if (candidate.geometrySnapshotId != context.geometrySnapshotId || candidate.line == null ||
                !fullyVisible(candidate.line, context) ||
                (segment != null && candidate.segment != segment) ||
                !fresh(candidate.endMs, candidate.evidenceAvailableMs, candidate.evidenceTimeBasis, context)) {
                resetContinuation()
                clear(ReadingAidType.NEXT_LINE_START, context.nowMs, "candidate_anchor_unavailable", out)
            }
            clear(ReadingAidType.CURRENT_LINE, context.nowMs, "candidate_anchor_unavailable", out)
            clear(ReadingAidType.WORD_MAGNIFIER, context.nowMs, "candidate_anchor_unavailable", out)
        }
        return out
    }

    private fun continuationGeometryValid(c: ReadingAidContext): Boolean {
        val g = c.geometry ?: return false
        val v = g.visibleRectScreen
        return g.snapshotId == c.geometrySnapshotId && g.lines.size == c.lineCount &&
            g.textDirection == ScanpathTextDirection.LTR &&
            listOf(g.textOriginX, g.textOriginY, v.left, v.top, v.right, v.bottom).all { it.isFinite() } &&
            v.right > v.left && v.bottom > v.top
    }

    private fun fullyVisible(line: Int, c: ReadingAidContext): Boolean {
        if (!continuationGeometryValid(c)) return false
        val g = c.geometry!!
        val row = g.lines.getOrNull(line) ?: return false
        val v = g.visibleRectScreen
        return row.endUtf16 > row.startUtf16 &&
            listOf(row.left, row.right, row.top, row.bottom).all { it.isFinite() } &&
            row.right > row.left && row.bottom > row.top &&
            row.left + g.textOriginX >= v.left && row.right + g.textOriginX <= v.right &&
            row.top + g.textOriginY >= v.top && row.bottom + g.textOriginY <= v.bottom
    }

    private fun resetContinuation() {
        continuationStart = null
        continuationEnd = null
    }

    /** Geometry supports an expected physical line, without asserting comprehension or difficulty. */
    private fun continuationSupport(id: String, supportSegment: Int, line: Int?, first: Double,
        last: Double, available: Double, x: Double?, c: ReadingAidContext,
        out: MutableList<ReadingAidDecision>) {
        if (!config.automaticRulesEnabled || !allowed(ReadingAidType.NEXT_LINE_START)) {
            resetContinuation()
            return
        }
        if (line == null || !fullyVisible(line, c) || x?.isFinite() != true ||
            !first.isFinite() || !last.isFinite() || first > last || first <= continuationFloor ||
            c.captureMs!! - last !in 0.0..config.maxEvidenceAgeMs) {
            resetContinuation()
            return
        }
        val g = c.geometry!!
        val row = g.lines[line]
        val localX = x - g.textOriginX
        if (localX !in row.left..row.right) {
            resetContinuation()
            return
        }
        // Finalization may describe an older departed row. It cannot consume a current
        // expectation unless its landing agrees with the latest raw target.
        if (continuationStart?.let { it.evidence.line != line } == true && line != c.targetLine) return
        // Ignore older repeats while allowing a growing established candidate prefix.
        if (last <= lastContinuationCapture) return
        lastContinuationCapture = last
        val support = ContinuationSupport(ReadingAidContinuationEvidence(id, supportSegment, line,
            first, last, available), (localX - row.left) / (row.right - row.left))
        val marker = active[ReadingAidType.NEXT_LINE_START]
        if (marker != null && line == c.targetLine) {
            if (line == marker.anchor.line) {
                clear(ReadingAidType.NEXT_LINE_START, c.nowMs, "expected_line_arrival", out)
                markerLanding = null
            } else if (markerLanding?.let { support.key != it.key &&
                    first > it.evidence.lastCaptureMs } == true) {
                clear(ReadingAidType.NEXT_LINE_START, c.nowMs, "continued_reading_elsewhere", out)
                markerLanding = null
            }
        }
        val start = continuationStart
        val end = continuationEnd
        if (start != null && start.evidence.line != line) {
            // A departure consumes this expectation exactly once, including a normal arrival.
            resetContinuation()
            if (end != null && line == c.targetLine && first > end.evidence.lastCaptureMs &&
                last - end.evidence.lastCaptureMs <= config.continuationWindowMs &&
                fullyVisible(start.evidence.line + 1, c) && line != start.evidence.line + 1) {
                val evidence = listOf(start.evidence, end.evidence, support.evidence).distinct()
                choose(ReadingAidType.NEXT_LINE_START, anchor(c, start.evidence.line + 1), 1.0,
                    start.evidence.firstCaptureMs, last, evidence.maxOf { it.evidenceAvailableMs },
                    emptyList(), evidence.map { it.evidenceId }, "A2_CONTINUATION_MISMATCH_V1",
                    listOf("W24", "S19"),
                    "Expected-next-line assistance hypothesis; W24/S19 are form/navigation precedents only; this trigger and phone benefit are unvalidated.",
                    c, out, evidence)
                if (active[ReadingAidType.NEXT_LINE_START] != null) markerLanding = support
            }
        }
        if (line >= c.lineCount - 1 || !fullyVisible(line + 1, c)) {
            resetContinuation()
            return
        }
        if (continuationStart == null && support.fraction <= config.continuationStartFraction) {
            continuationStart = support
        } else {
            val source = continuationStart
            if (source != null && source.evidence.line == line &&
                last > source.evidence.lastCaptureMs && support.fraction >= config.continuationEndFraction)
                continuationEnd = support
        }
    }

    private fun maybeWord(c: ReadingAidContext, candidate: ScanpathCandidateEvidence?, out: MutableList<ReadingAidDecision>) {
        if (config.wordAidType == ReadingAidType.WORD_DEFINITION) {
            maybeDefinition(c, candidate, out)
            return
        }
        val visit = wordVisit ?: return
        if (!config.automaticRulesEnabled || !preferences.wordMagnifier ||
            visit.line != c.targetLine || visit.start != c.targetWordStartUtf16 ||
            visit.end != c.targetWordEndUtf16) {
            clear(ReadingAidType.WORD_MAGNIFIER, c.nowMs, "word_target_moved_or_disabled", out)
            return
        }
        val prefix = candidate?.takeIf {
            it.segment == visit.segment && it.geometrySnapshotId == c.geometrySnapshotId &&
                it.line == visit.line && it.wordStartUtf16 == visit.start &&
                it.wordEndUtf16 == visit.end && it.startMs > visit.lastFinalEndMs &&
                fresh(it.endMs, it.evidenceAvailableMs, it.evidenceTimeBasis, c)
        }
        val observed = visit.observedMs + (prefix?.durationMs ?: 0.0)
        // A compact ongoing prefix adds observed time; it is not a finalized refixation/P04.
        val refixations = max(0, visit.fixationIndices.distinct().size - 1)
        if (refixations < config.wordMinRefixations || observed < config.wordMinObservedMs) return
        val target = interpolatedStrength(observed, config.wordMinObservedMs,
            config.wordSaturationMs)
        choose(ReadingAidType.WORD_MAGNIFIER,
            anchor(c, visit.line, visit.start, visit.end), target, visit.firstMs,
            prefix?.endMs ?: visit.lastFinalEndMs, prefix?.evidenceAvailableMs ?: visit.availableMs,
            visit.refixationEventIds + visit.lastEventId,
            listOfNotNull(prefix?.candidateId), "A3_WITHIN_VISIT_WORD_V1",
            listOf("W24", "S14"),
            "W24 duration/refixation-triggered word support precedent, without significant word outcome; S14 supports preserving readable context.",
            c, out)
    }

    private fun maybeDefinition(c: ReadingAidContext, candidate: ScanpathCandidateEvidence?, out: MutableList<ReadingAidDecision>) {
        if (!config.automaticRulesEnabled || !preferences.wordDefinition) return
        val visit = wordVisit?.takeIf { it.line == c.targetLine && it.start == c.targetWordStartUtf16 &&
            it.end == c.targetWordEndUtf16 }
        val prefix = candidate?.takeIf { it.wordStartUtf16 == c.targetWordStartUtf16 &&
            it.wordEndUtf16 == c.targetWordEndUtf16 && it.wordStartUtf16 != null &&
            (visit == null || it.startMs > visit.lastFinalEndMs) }
        val observed = (visit?.observedMs ?: 0.0) + (prefix?.durationMs ?: 0.0)
        if (observed < config.definitionMinObservedMs || (visit == null && prefix == null)) return
        choose(ReadingAidType.WORD_DEFINITION,
            anchor(c, c.targetLine!!, c.targetWordStartUtf16, c.targetWordEndUtf16), 1.0,
            visit?.firstMs ?: prefix!!.startMs, prefix?.endMs ?: visit!!.lastFinalEndMs,
            prefix?.evidenceAvailableMs ?: visit!!.availableMs,
            listOfNotNull(visit?.lastEventId), listOfNotNull(prefix?.candidateId), "A5_WORD_RESIDENCE_DEFINITION_V1",
            listOf("H03", "M25"),
            "H03/M25 gaze-triggered translation precedents; English definitions, the 800 ms setting and phone benefit remain experimental.",
            c, out)
    }

    private fun maybeFocus(c: ReadingAidContext, out: MutableList<ReadingAidDecision>) {
        if (!config.automaticRulesEnabled || !preferences.focusRegion) return
        val recentCycle = cycle ?: return
        val residence = lineResidence ?: return
        if (residence.line != c.targetLine || residence.observedMs < config.lineMinObservedMs ||
            !residence.slow || residence.slowEventId == null || c.captureMs == null ||
            recentCycle.endMs > residence.endMs ||
            c.captureMs - residence.endMs !in 0.0..config.maxEvidenceAgeMs ||
            c.captureMs - recentCycle.endMs !in 0.0..config.cycleWindowMs ||
            (active.containsKey(ReadingAidType.WORD_MAGNIFIER) || active.containsKey(ReadingAidType.WORD_DEFINITION))) return
        // Experimental stimulus modulation from observed line residence, not a difficulty score.
        val target = interpolatedStrength(residence.observedMs, config.lineMinObservedMs,
            config.lineSaturationMs)
        choose(ReadingAidType.FOCUS_REGION, anchor(c, residence.line), target,
            recentCycle.endMs, residence.endMs, max(recentCycle.availableMs, residence.availableMs),
            listOf(recentCycle.eventId, residence.eventId, residence.slowEventId!!),
            emptyList(), "A4_CYCLE_SLOW_LINE_V1",
            listOf("N23", "S14"),
            "N23 compares ruler forms, not this trigger; S14 cautions against hiding earlier text. Mapping is experimental.",
            c, out)
    }

    /** Exact configured endpoints; equal limits are an immediate step once activation is met. */
    private fun interpolatedStrength(observedMs: Double, minimumMs: Double,
                                     saturationMs: Double): Double {
        val ratio = if (saturationMs == minimumMs) 1.0 else
            ((observedMs - minimumMs) / (saturationMs - minimumMs)).coerceIn(0.0, 1.0)
        return config.minAdaptiveStrength + (1.0 - config.minAdaptiveStrength) * ratio
    }

    private fun prepare(c: ReadingAidContext): List<ReadingAidDecision> {
        val out = mutableListOf<ReadingAidDecision>()
        val next = c.generation to c.geometrySnapshotId
        if (epoch != null && epoch != next) {
            clearAll(c.nowMs, "generation_or_geometry_changed", out)
            resetEvidence()
            continuationFloor = c.captureMs ?: c.nowMs
            seenEvents.clear()
        }
        epoch = next
        continuationEnd?.let {
            if (c.captureMs == null || c.captureMs - it.evidence.lastCaptureMs > config.continuationWindowMs)
                resetContinuation()
        }
        if (!continuationGeometryValid(c) || active[ReadingAidType.NEXT_LINE_START]?.let {
                !fullyVisible(it.anchor.line, c) } == true) {
            resetContinuation()
            clear(ReadingAidType.NEXT_LINE_START, c.nowMs, "continuation_geometry_unavailable", out)
        }
        for ((type, action) in active.toMap()) {
            if (c.nowMs >= action.expiresMs) expire(type, c.nowMs, out)
        }
        if (!valid(c)) {
            clearAll(c.nowMs, "invalid_live_input", out)
            resetEvidence()
            continuationFloor = c.captureMs ?: c.nowMs
        } else {
            val word = active[ReadingAidType.WORD_MAGNIFIER]
            if (word != null && (word.anchor.line != c.targetLine ||
                word.anchor.wordStartUtf16 != c.targetWordStartUtf16 ||
                word.anchor.wordEndUtf16 != c.targetWordEndUtf16))
                clear(ReadingAidType.WORD_MAGNIFIER, c.nowMs, "word_target_moved", out)
            for (type in listOf(ReadingAidType.CURRENT_LINE, ReadingAidType.FOCUS_REGION)) {
                if (active[type]?.anchor?.line != null && active[type]?.anchor?.line != c.targetLine)
                    clear(type, c.nowMs, "line_target_moved", out)
            }
        }
        return out
    }

    private fun valid(c: ReadingAidContext): Boolean = c.validLiveInput &&
        c.geometrySnapshotId.isNotBlank() && c.lineCount > 0 && c.nowMs.isFinite() &&
        c.captureMs?.isFinite() == true && c.captureMs <= c.nowMs &&
        c.nowMs - c.captureMs <= config.maxEvidenceAgeMs &&
        c.evidenceAvailableMs?.isFinite() == true &&
        c.evidenceAvailableMs >= c.captureMs && c.evidenceAvailableMs <= c.nowMs &&
        c.targetLine != null && c.targetLine in 0 until c.lineCount &&
        ((c.targetWordStartUtf16 == null && c.targetWordEndUtf16 == null) ||
            (c.targetWordStartUtf16 != null && c.targetWordEndUtf16 != null &&
                c.targetWordStartUtf16 >= 0 && c.targetWordEndUtf16 > c.targetWordStartUtf16))

    private fun fresh(end: Double?, available: Double?, basis: String?, c: ReadingAidContext): Boolean =
        end?.isFinite() == true && available?.isFinite() == true &&
            basis == "trigger_observation_delivery" && end <= c.captureMs!! &&
            available >= end && available <= c.evidenceAvailableMs!! && available <= c.nowMs &&
            c.nowMs - available <= config.maxEvidenceAgeMs

    private fun allowed(type: ReadingAidType): Boolean = preferences.enabled && when (type) {
        ReadingAidType.CURRENT_LINE -> preferences.lineGuide
        ReadingAidType.NEXT_LINE_START -> preferences.lineStartCue
        ReadingAidType.WORD_MAGNIFIER -> preferences.wordMagnifier
        ReadingAidType.WORD_DEFINITION -> preferences.wordDefinition
        ReadingAidType.FOCUS_REGION -> preferences.focusRegion
    }

    private fun anchor(c: ReadingAidContext, line: Int, start: Int? = null, end: Int? = null) =
        ReadingAidAnchor(c.generation, c.geometrySnapshotId, line, start, end)

    private fun choose(type: ReadingAidType, anchor: ReadingAidAnchor, targetStrength: Double,
                       first: Double, last: Double, available: Double,
                       events: List<String>, evidence: List<String>, rule: String,
                       sources: List<String>, relationship: String,
                       c: ReadingAidContext, out: MutableList<ReadingAidDecision>,
                       continuationEvidence: List<ReadingAidContinuationEvidence> = emptyList()) {
        if (!allowed(type) || preferences.maxStrength == 0.0 ||
            (!preferences.adaptiveStrength && preferences.manualStrength == 0.0)) return
        if (c.nowMs < (cooldownUntil[type] ?: Double.NEGATIVE_INFINITY)) {
            out.add(ReadingAidDecision(ReadingAidDecisionStatus.SUPPRESSED, null, "cooldown", c.nowMs,
                events, rule, sources))
            return
        }
        if (type == ReadingAidType.FOCUS_REGION && (active.containsKey(ReadingAidType.WORD_MAGNIFIER) || active.containsKey(ReadingAidType.WORD_DEFINITION))) {
            out.add(ReadingAidDecision(ReadingAidDecisionStatus.SUPPRESSED, null,
                "word_magnifier_priority", c.nowMs, events, rule, sources))
            return
        }
        if (type in listOf(ReadingAidType.WORD_MAGNIFIER, ReadingAidType.WORD_DEFINITION) && active.containsKey(ReadingAidType.FOCUS_REGION))
            clear(ReadingAidType.FOCUS_REGION, c.nowMs, "word_magnifier_priority", out)
        val strength = if (type == ReadingAidType.NEXT_LINE_START) 1.0
            else if (preferences.adaptiveStrength) min(preferences.maxStrength, targetStrength)
            else min(preferences.maxStrength, preferences.manualStrength)
        if (strength <= 0) return
        val old = active[type]
        if (old != null && old.anchor == anchor && old.strength == strength) return
        if (old != null) clear(type, c.nowMs, "replaced", out, applyCooldown = false)
        val action = ReadingAidAction("aid:${++serial}", type,
            when (type) {
                ReadingAidType.CURRENT_LINE -> "CURRENT_LINE_HIGHLIGHT_V1"
                ReadingAidType.NEXT_LINE_START -> "CONTINUATION_LINE_START_CUE_V1"
                ReadingAidType.WORD_MAGNIFIER -> "CONTEXT_WORD_MAGNIFIER_V1"
                ReadingAidType.WORD_DEFINITION -> "CONTEXT_WORD_DEFINITION_V1"
                ReadingAidType.FOCUS_REGION -> "CONTEXT_FOCUS_REGION_V1"
            }, config.conditionId, anchor, strength,
            if (type == ReadingAidType.WORD_MAGNIFIER) 1.0 +
                (config.maxMagnificationScale - 1.0) * strength else null,
            if (type == ReadingAidType.FOCUS_REGION) config.focusContextRadiusLines else null,
            c.nowMs, c.nowMs + config.aidTtlMs, events, evidence,
            first, last, available, rule, sources, relationship, true, config.configId, continuationEvidence)
        active[type] = action
        out.add(ReadingAidDecision(ReadingAidDecisionStatus.CHOSEN, action, "rule_matched", c.nowMs,
            events, rule, sources))
    }

    private fun suppress(reason: String, e: ScanpathEvent, c: ReadingAidContext,
                         out: MutableList<ReadingAidDecision>): List<ReadingAidDecision> {
        out.add(ReadingAidDecision(ReadingAidDecisionStatus.SUPPRESSED, null, reason,
            c.nowMs, listOf(e.eventId), e.patternId))
        return out
    }

    private fun clear(type: ReadingAidType, now: Double, reason: String,
                      out: MutableList<ReadingAidDecision>, applyCooldown: Boolean = true) {
        val prior = active.remove(type) ?: return
        if (applyCooldown && type != ReadingAidType.CURRENT_LINE)
            cooldownUntil[type] = now + config.cooldownMs
        out.add(ReadingAidDecision(ReadingAidDecisionStatus.CLEARED, prior, reason, now,
            prior.eventIds, prior.ruleId, prior.sourceIds))
    }

    private fun expire(type: ReadingAidType, now: Double, out: MutableList<ReadingAidDecision>) {
        val prior = active.remove(type) ?: return
        if (type != ReadingAidType.CURRENT_LINE) cooldownUntil[type] = now + config.cooldownMs
        out.add(ReadingAidDecision(ReadingAidDecisionStatus.EXPIRED, prior, "ttl_expired", now,
            prior.eventIds, prior.ruleId, prior.sourceIds))
    }

    private fun clearAll(now: Double, reason: String, out: MutableList<ReadingAidDecision>) {
        for (type in active.keys.toList()) clear(type, now, reason, out, applyCooldown = false)
        cooldownUntil.clear()
    }

    private fun resetEvidence() {
        wordVisit = null; cycle = null; lineResidence = null; segment = null
        resetContinuation()
        markerLanding = null
        lastContinuationCapture = Double.NEGATIVE_INFINITY
    }
}
