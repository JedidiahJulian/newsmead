package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathConfig
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathFixation
import com.newsmead.gaze.scanpath.ScanpathTextDirection
import kotlin.math.max
import kotlin.math.min

/** Causal policy for observable targets. Rules are experimental mappings, not difficulty estimates. */
class ReadingAidPolicy(
    val config: ReadingAidPolicyConfig = ReadingAidPolicyConfig.manualOnly(),
    preferences: ReaderAidPreferences = ReaderAidPreferences(),
    bookmarkDetectorConfig: ScanpathConfig = ScanpathConfig(),
    onFocusEpisode: (ReadingAidFocusEpisode) -> Unit = {}
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
    private val bookmark = ReadingBookmarkPolicy(bookmarkDetectorConfig)
    private val focus = ReadingFocusPolicy(config, bookmarkDetectorConfig, onFocusEpisode)

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
            (!value.adaptiveStrength && value.manualStrength == 0.0)) { resetContinuation(); bookmark.reset() }
        for ((type, action) in active.toMap()) {
            if (!allowed(type) || value.maxStrength == 0.0 ||
                (!value.adaptiveStrength && value.manualStrength == 0.0))
                clear(type, context.nowMs, "reader_disabled_or_zero_strength", out)
            else if (type !in listOf(ReadingAidType.NEXT_LINE_START, ReadingAidType.READING_BOOKMARK) &&
                (action.strength > value.maxStrength || !value.adaptiveStrength)) {
                clear(type, context.nowMs, "reader_strength_changed", out)
            }
        }
        return out
    }

    fun tick(context: ReadingAidContext): List<ReadingAidDecision> = prepare(context)

    fun reset(context: ReadingAidContext, preserveCompletedFocus: Boolean = false): List<ReadingAidDecision> {
        val out = mutableListOf<ReadingAidDecision>()
        val keepFocus = preserveCompletedFocus && currentGuideProfile() && epoch?.first == context.generation
        if (keepFocus) focus.pause()
        clearAll(context.nowMs, "explicit_reset", out)
        resetEvidence(preserveFocus = keepFocus)
        continuationFloor = context.captureMs ?: context.nowMs
        epoch = context.generation to context.geometrySnapshotId
        return out
    }

    fun focusDiagnostics(nowMs: Double): Map<String, Any> = focus.diagnostics(nowMs)

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
                val autoScroll = reason == "auto_scroll_start" || reason == "auto_scroll_end"
                val keepBookmark = config.lineStartAidType == ReadingAidType.READING_BOOKMARK &&
                    reason in listOf("invalid_sample", "sample_gap", "unassigned_line", "non_monotonic_capture")
                // Preserve display briefly while the engine still breaks both sequence paths.
                val keepFocus = reason in listOf("invalid_sample", "sample_gap", "unassigned_line",
                    "non_monotonic_capture", "unclassified_fixation_gap", "geometry_change", "layout_unavailable") ||
                    reason.startsWith("definition_") || autoScroll
                if (reason == "geometry_change" || reason == "layout_unavailable" || reason.startsWith("definition_") || autoScroll)
                    focus.pause()
                else if (reason != "unclassified_fixation_gap") focus.breakLineSequence()
                val holdFocus = keepFocus && retainFocus(context)
                if (keepBookmark) clearDynamic(context.nowMs, reason, out, holdFocus)
                else clearAll(context.nowMs, reason, out, holdFocus)
                resetEvidence(keepBookmark, preserveFocus = keepFocus)
                continuationFloor = context.captureMs ?: context.nowMs
            }
            return out
        }
        if (!valid(context)) return suppress("invalid_live_input", event, context, out)
        if (!fresh(event.endMs, event.evidenceAvailableMs, event.evidenceTimeBasis, context)) {
            resetContinuation()
            clear(ReadingAidType.CURRENT_SENTENCE, context.nowMs, "late_or_unavailable_evidence", out)
            return suppress("late_or_unavailable_evidence", event, context, out)
        }
        val f = latestFinalizedFixation
        if (f == null || f.geometrySnapshotId != context.geometrySnapshotId ||
            f.index !in event.fixationIndices || f.endMs > context.captureMs!! ||
            f.evidenceAvailableMs == null || f.evidenceAvailableMs > context.nowMs) {
            resetContinuation()
            clear(ReadingAidType.CURRENT_SENTENCE, context.nowMs, "unsupported_or_stale_fixation", out)
            return suppress("unsupported_or_stale_fixation", event, context, out)
        }
        if (segment != null && f.segment != segment) {
            clearDynamic(context.nowMs, "segment_changed", out, retainFocus(context))
            resetEvidence(config.lineStartAidType == ReadingAidType.READING_BOOKMARK, preserveFocus = true)
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
        if (f.line != null && f.line == context.targetLine && !f.leftCensored && !f.rightCensored)
            focusSupport(ReadingAidContinuationEvidence("fixation:${f.index}", f.segment, f.line,
                f.startMs, f.endMs, f.evidenceAvailableMs!!), context, out)
        if (f.line != null && f.line == context.targetLine) chooseGuide(
            f.line, f.startMs, f.endMs, f.evidenceAvailableMs!!,
            listOf(event.eventId), listOf("fixation:${f.index}"), context, out)
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
                // Current assistance uses its own supported visits, including the ongoing fourth.
                // Finalized fast P11 remains a descriptive scanpath event, without double counting.
                if (!currentGuideProfile()) {
                    cycle = Cycle(event.eventId, f.endMs, event.evidenceAvailableMs!!)
                    maybeFocus(context, out)
                }
            }
        }
        return out
    }

    fun onObservation(candidate: ScanpathCandidateEvidence?, context: ReadingAidContext): List<ReadingAidDecision> {
        val out = prepare(context).toMutableList()
        if (!valid(context)) return out
        if (candidate != null && segment != null && candidate.segment != segment) {
            clearDynamic(context.nowMs, "segment_changed", out, retainFocus(context))
            resetEvidence(config.lineStartAidType == ReadingAidType.READING_BOOKMARK, preserveFocus = true)
        }
        if (candidate != null && candidate.geometrySnapshotId == context.geometrySnapshotId &&
            candidate.line == context.targetLine && (segment == null || candidate.segment == segment) &&
            fresh(candidate.endMs, candidate.evidenceAvailableMs, candidate.evidenceTimeBasis, context)) {
            if (segment == null) segment = candidate.segment
            continuationSupport(candidate.candidateId, candidate.segment, candidate.line,
                candidate.startMs, candidate.endMs, candidate.evidenceAvailableMs!!,
                context.targetX, context, out)
            val line = candidate.line
            if (line != null) chooseGuide(line, candidate.startMs, candidate.endMs,
                candidate.evidenceAvailableMs!!, emptyList(), listOf(candidate.candidateId), context, out)
            maybeWord(context, candidate, out)
            if (line != null) focusSupport(ReadingAidContinuationEvidence(candidate.candidateId,
                candidate.segment, line, candidate.startMs, candidate.endMs,
                candidate.evidenceAvailableMs!!), context, out)
        } else if (candidate != null) {
            // A valid majority prefix may disagree with one latest selected row assignment.
            // Withhold gaze-following aids while retaining supported continuation state.
            if (candidate.geometrySnapshotId != context.geometrySnapshotId || candidate.line == null ||
                !fullyVisible(candidate.line, context) ||
                (segment != null && candidate.segment != segment) ||
                !fresh(candidate.endMs, candidate.evidenceAvailableMs, candidate.evidenceTimeBasis, context)) {
                resetContinuation()
                clear(ReadingAidType.NEXT_LINE_START, context.nowMs, "candidate_anchor_unavailable", out)
            }
            clear(ReadingAidType.CURRENT_LINE, context.nowMs, "candidate_anchor_unavailable", out)
            clear(ReadingAidType.CURRENT_SENTENCE, context.nowMs, "candidate_anchor_unavailable", out)
            clear(ReadingAidType.WORD_MAGNIFIER, context.nowMs, "candidate_anchor_unavailable", out)
        }
        if (candidate == null) clear(ReadingAidType.CURRENT_SENTENCE, context.nowMs,
            "candidate_anchor_unavailable", out)
        return out
    }

    private fun currentGuideProfile() = config.configId == "DEFINITION_PROFILE_V4"

    /** Range and physical source line must agree; word assignment and horizontal gaze are unused. */
    private fun guideRangeValid(c: ReadingAidContext): Boolean {
        val range = c.guideRange ?: return false
        val g = c.geometry ?: return false
        val line = c.targetLine ?: return false
        val row = g.lines.getOrNull(line) ?: return false
        return g.snapshotId == c.geometrySnapshotId && g.lines.size == c.lineCount &&
            g.lines.all { it.startUtf16 >= 0 && it.endUtf16 >= it.startUtf16 } &&
            g.lines.zipWithNext().all { (a, b) -> a.endUtf16 <= b.startUtf16 } &&
            range.startUtf16 >= (g.lines.firstOrNull()?.startUtf16 ?: 0) &&
            range.endUtf16 > range.startUtf16 &&
            range.endUtf16 <= (g.lines.maxOfOrNull { it.endUtf16 } ?: 0) &&
            row.startUtf16 >= 0 && row.endUtf16 > row.startUtf16 &&
            range.startUtf16 < row.endUtf16 && range.endUtf16 > row.startUtf16 &&
            listOf(row.left, row.right, row.top, row.bottom).all { it.isFinite() } &&
            row.right > row.left && row.bottom > row.top
    }

    private fun chooseGuide(line: Int, first: Double, last: Double, available: Double,
        events: List<String>, evidence: List<String>, c: ReadingAidContext,
        out: MutableList<ReadingAidDecision>) {
        if (!preferences.lineGuide) return
        val sentence = currentGuideProfile() && preferences.guideStyle == ReadingGuideStyle.SENTENCE
        if (sentence && !guideRangeValid(c)) return
        val type = if (sentence) ReadingAidType.CURRENT_SENTENCE else ReadingAidType.CURRENT_LINE
        clear(if (sentence) ReadingAidType.CURRENT_LINE else ReadingAidType.CURRENT_SENTENCE,
            c.nowMs, "guide_style_changed", out, false)
        choose(type, anchor(c, line), config.minAdaptiveStrength, first, last, available,
            events, evidence,
            if (sentence) "A1_RELIABLE_SENTENCE_CONTEXT_V1" else "A1_RELIABLE_LINE_V1",
            if (sentence) listOf("V26") else if (currentGuideProfile()) listOf("W24") else listOf("W24", "N23"),
            if (sentence) "V26 desktop hover sentence/paragraph forms improved self-reported attention; no significant speed or comprehension gain. Automatic phone selection and difficulty dose are unvalidated."
            else if (currentGuideProfile()) "W24 gaze-following line form; automatic phone benefit, gray appearance and difficulty dose are unvalidated."
            else "W24 gaze-following line form; N23 mouse-controlled ruler form; live phone benefit is untested.",
            c, out, guideRange = if (sentence) c.guideRange else null)
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
        if (config.lineStartAidType == ReadingAidType.READING_BOOKMARK) {
            val geometry = c.geometry ?: return
            if (config.automaticRulesEnabled && allowed(ReadingAidType.READING_BOOKMARK) &&
                line != null && line == c.targetLine && fullyVisible(line, c) &&
                first.isFinite() && last.isFinite() && first <= last && first > continuationFloor &&
                c.captureMs!! - last in 0.0..config.maxEvidenceAgeMs && x?.isFinite() == true &&
                x - geometry.textOriginX in geometry.lines[line].left..geometry.lines[line].right) {
                applyBookmark(bookmark.support(ReadingAidContinuationEvidence(id, supportSegment,
                    line, first, last, available)), c, out)
            }
            return
        }
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
        // expectation unless its landing agrees with the latest selected target.
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
            "H03/M25 gaze-triggered translation precedents; English definitions, the 1500 ms setting and phone benefit remain experimental.",
            c, out)
    }

    private fun cFocusAllowed(c: ReadingAidContext) = c.focusInputAllowed &&
        config.automaticRulesEnabled && allowed(ReadingAidType.FOCUS_REGION) && preferences.maxStrength > 0 &&
        (preferences.adaptiveStrength || preferences.manualStrength > 0)

    private fun focusSupport(value: ReadingAidContinuationEvidence, c: ReadingAidContext,
                             out: MutableList<ReadingAidDecision>) {
        if (!currentGuideProfile() || !cFocusAllowed(c) || !fullyVisible(value.line, c) ||
            !value.firstCaptureMs.isFinite() || !value.lastCaptureMs.isFinite() ||
            value.firstCaptureMs < 0 || value.firstCaptureMs > value.lastCaptureMs ||
            c.captureMs!! - value.lastCaptureMs !in 0.0..config.maxEvidenceAgeMs) return
        val row = c.geometry!!.lines[value.line]
        val x = c.targetX ?: return
        // Focus follows coarse physical-line support, including line-only mapper targets.
        val article = c.geometry.visibleRectScreen
        if (!x.isFinite() || x !in article.left..article.right) return
        c.presentationSample?.let { sample ->
            val y = sample.processingY
            if (!sample.eligible || sample.geometrySnapshotId != c.geometrySnapshotId ||
                sample.processingX?.isFinite() != true || y?.isFinite() != true ||
                sample.processingX!! !in article.left..article.right ||
                y - c.geometry.textOriginY !in row.top..row.bottom) return
        }
        val evidence = focus.support(value, c, preferences)
        if (focus.displaySupported(c.nowMs)) applyFocus(evidence, c, out)
    }

    private fun applyFocus(e: ReadingAidFocusEvidence?, c: ReadingAidContext,
                           out: MutableList<ReadingAidDecision>) {
        if (e == null) {
            if (focus.hasDemand && retainFocus(c)) return
            clear(ReadingAidType.FOCUS_REGION, c.nowMs, "focus_recovery_complete", out, false)
            return
        }
        val strength = e.initialStrength * e.recoveryFactor
        val supports = e.episodes.flatMap { listOfNotNull(it.source, it.recovery) } +
            e.alternations.flatMap { it.visits } + e.support
        val presence = e.episodes.mapNotNull { it.presenceRecovery }
        choose(ReadingAidType.FOCUS_REGION, anchor(c, e.support.line), strength,
            (supports.map { it.firstCaptureMs } + presence.map { it.firstCaptureMs }).minOrNull()!!,
            (supports.map { it.lastCaptureMs } + presence.map { it.lastCaptureMs }).maxOrNull()!!,
            maxOf(e.support.evidenceAvailableMs, presence.maxOfOrNull { it.confirmedDeliveryMs } ?: 0.0,
                e.alternations.maxOfOrNull { it.confirmedDeliveryMs } ?: 0.0), e.cycleEventIds,
            e.episodes.map { it.episodeId } + supports.map { it.evidenceId },
            e.ruleId, listOf("EB15", "N23", "S14"),
            "Observable interruption/line-alternation assistance; trigger, frequency dose and phone benefit are experimental, without an attention diagnosis.",
            c, out, focusEvidence = e)
    }

    private fun maybeFocus(c: ReadingAidContext, out: MutableList<ReadingAidDecision>) {
        if (currentGuideProfile() || !config.automaticRulesEnabled || !preferences.focusRegion) return
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

    private fun retainFocus(c: ReadingAidContext): Boolean = currentGuideProfile() &&
        cFocusAllowed(c) && continuationGeometryValid(c) && focus.displaySupported(c.nowMs) &&
        focus.currentEvidence(c, preferences) != null &&
        active[ReadingAidType.FOCUS_REGION]?.let { fullyVisible(it.anchor.line, c) } == true

    private fun prepare(c: ReadingAidContext): List<ReadingAidDecision> {
        val out = mutableListOf<ReadingAidDecision>()
        val next = c.generation to c.geometrySnapshotId
        if (epoch != null && epoch != next) {
            val sameArticle = epoch!!.first == next.first
            if (sameArticle) focus.pause()
            clearAll(c.nowMs, "generation_or_geometry_changed", out)
            resetEvidence(preserveFocus = sameArticle)
            continuationFloor = c.captureMs ?: c.nowMs
            seenEvents.clear()
        }
        epoch = next
        if (currentGuideProfile()) {
            if (!config.automaticRulesEnabled || !allowed(ReadingAidType.FOCUS_REGION) ||
                preferences.maxStrength == 0.0 || (!preferences.adaptiveStrength && preferences.manualStrength == 0.0)) {
                focus.reset()
                clear(ReadingAidType.FOCUS_REGION, c.nowMs, "focus_input_excluded", out, false)
            } else if (!c.focusInputAllowed || !continuationGeometryValid(c)) {
                focus.pause()
                clear(ReadingAidType.FOCUS_REGION, c.nowMs, "focus_operational_pause", out, false)
            } else {
                focus.input(c)
                if (!focus.hasDemand || !focus.displaySupported(c.nowMs)) clear(ReadingAidType.FOCUS_REGION, c.nowMs,
                    "focus_witnesses_expired_or_recovery_complete", out, false)
                else {
                    val evidence = focus.currentEvidence(c, preferences)?.takeIf { fullyVisible(it.support.line, c) }
                    if (evidence == null) clear(ReadingAidType.FOCUS_REGION, c.nowMs, "focus_anchor_unavailable", out, false)
                    else applyFocus(evidence, c, out)
                }
            }
        }
        if (!continuationGeometryValid(c) || !c.bookmarkInputAllowed ||
            bookmark.savedLine?.let { !fullyVisible(it, c) } == true ||
            active[ReadingAidType.READING_BOOKMARK]?.let { !fullyVisible(it.anchor.line, c) } == true) {
            bookmark.reset()
            clear(ReadingAidType.READING_BOOKMARK, c.nowMs, "bookmark_geometry_or_input_excluded", out)
        } else if (config.automaticRulesEnabled && config.lineStartAidType == ReadingAidType.READING_BOOKMARK &&
            allowed(ReadingAidType.READING_BOOKMARK) && preferences.maxStrength > 0 &&
            (preferences.adaptiveStrength || preferences.manualStrength > 0)) {
            applyBookmark(bookmark.input(c, config.maxEvidenceAgeMs, config.bookmarkTrackingLossMs), c, out)
        }
        continuationEnd?.let {
            if (c.captureMs == null || c.captureMs - it.evidence.lastCaptureMs > config.continuationWindowMs)
                resetContinuation()
        }
        if (!continuationGeometryValid(c) || active[ReadingAidType.NEXT_LINE_START]?.let {
                !fullyVisible(it.anchor.line, c) } == true) {
            resetContinuation()
            clear(ReadingAidType.NEXT_LINE_START, c.nowMs, "continuation_geometry_unavailable", out)
        }
        for (type in listOf(ReadingAidType.CURRENT_LINE, ReadingAidType.CURRENT_SENTENCE)) {
            val guide = active[type] ?: continue
            if (!allowed(type) || (type == ReadingAidType.CURRENT_SENTENCE &&
                    (!guideRangeValid(c) || guide.guideRange != c.guideRange)))
                clear(type, c.nowMs, "guide_style_or_context_changed", out)
        }
        for ((type, action) in active.toMap()) {
            if (c.nowMs >= action.expiresMs && !(currentGuideProfile() && type == ReadingAidType.FOCUS_REGION)) expire(type, c.nowMs, out)
        }
        if (!valid(c)) {
            clearDynamic(c.nowMs, "invalid_live_input", out, retainFocus(c))
            resetEvidence(config.lineStartAidType == ReadingAidType.READING_BOOKMARK, preserveFocus = true)
            // A timer with no observation has no new capture boundary. Advancing
            // to wall time would reject an entire first prefix delivered with lag.
            c.captureMs?.takeIf { it.isFinite() && it >= 0 }?.let {
                continuationFloor = maxOf(continuationFloor, it)
            }
        } else {
            val word = active[ReadingAidType.WORD_MAGNIFIER]
            if (word != null && (word.anchor.line != c.targetLine ||
                word.anchor.wordStartUtf16 != c.targetWordStartUtf16 ||
                word.anchor.wordEndUtf16 != c.targetWordEndUtf16))
                clear(ReadingAidType.WORD_MAGNIFIER, c.nowMs, "word_target_moved", out)
            val following = if (currentGuideProfile()) listOf(ReadingAidType.CURRENT_LINE)
                else listOf(ReadingAidType.CURRENT_LINE, ReadingAidType.FOCUS_REGION)
            for (type in following) {
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
        ReadingAidType.CURRENT_LINE -> preferences.lineGuide &&
            (!currentGuideProfile() || preferences.guideStyle == ReadingGuideStyle.LINE)
        ReadingAidType.CURRENT_SENTENCE -> preferences.lineGuide && currentGuideProfile() &&
            preferences.guideStyle == ReadingGuideStyle.SENTENCE
        ReadingAidType.NEXT_LINE_START, ReadingAidType.READING_BOOKMARK -> preferences.lineStartCue
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
                       continuationEvidence: List<ReadingAidContinuationEvidence> = emptyList(),
                       bookmarkEvidence: ReadingAidBookmarkEvidence? = null,
                       guideRange: ReadingGuideRange? = null,
                       focusEvidence: ReadingAidFocusEvidence? = null) {
        if (!allowed(type) || preferences.maxStrength == 0.0 ||
            (!preferences.adaptiveStrength && preferences.manualStrength == 0.0)) return
        if (type != ReadingAidType.READING_BOOKMARK && !(currentGuideProfile() && type == ReadingAidType.FOCUS_REGION) && c.nowMs < (cooldownUntil[type] ?: Double.NEGATIVE_INFINITY)) {
            out.add(ReadingAidDecision(ReadingAidDecisionStatus.SUPPRESSED, null, "cooldown", c.nowMs,
                events, rule, sources))
            return
        }
        val intrusiveWordActive = active.containsKey(ReadingAidType.WORD_MAGNIFIER) ||
            (!currentGuideProfile() && active.containsKey(ReadingAidType.WORD_DEFINITION))
        if (type == ReadingAidType.FOCUS_REGION && intrusiveWordActive) {
            out.add(ReadingAidDecision(ReadingAidDecisionStatus.SUPPRESSED, null,
                "word_magnifier_priority", c.nowMs, events, rule, sources))
            return
        }
        if ((type == ReadingAidType.WORD_MAGNIFIER ||
                (type == ReadingAidType.WORD_DEFINITION && !currentGuideProfile())) && active.containsKey(ReadingAidType.FOCUS_REGION))
            clear(ReadingAidType.FOCUS_REGION, c.nowMs, "word_magnifier_priority", out)
        val strength = if (type in listOf(ReadingAidType.NEXT_LINE_START, ReadingAidType.READING_BOOKMARK)) 1.0
            else if (focusEvidence != null) min(preferences.maxStrength,
                if (preferences.adaptiveStrength) focusEvidence.initialStrength else preferences.manualStrength) *
                focusEvidence.recoveryFactor
            else if (preferences.adaptiveStrength) min(preferences.maxStrength, targetStrength)
            else min(preferences.maxStrength, preferences.manualStrength)
        if (strength <= 0) return
        val focusStyle = preferences.focusStyle.takeIf { currentGuideProfile() && type == ReadingAidType.FOCUS_REGION }
        val old = active[type]
        if (old != null && old.strength == strength && old.focusStyle == focusStyle &&
            (old.anchor == anchor || (type == ReadingAidType.CURRENT_SENTENCE &&
                old.anchor.generation == anchor.generation &&
                old.anchor.geometrySnapshotId == anchor.geometrySnapshotId &&
                old.guideRange == guideRange)) && old.focusEvidence == focusEvidence) return
        if (old != null) clear(type, c.nowMs, "replaced", out, applyCooldown = false)
        val action = ReadingAidAction("aid:${++serial}", type,
            when (type) {
                ReadingAidType.CURRENT_LINE -> if (currentGuideProfile()) "CURRENT_LINE_HIGHLIGHT_GRAY_V1"
                    else "CURRENT_LINE_HIGHLIGHT_V1"
                ReadingAidType.CURRENT_SENTENCE -> "CURRENT_SENTENCE_CONTEXT_HIGHLIGHT_V1"
                ReadingAidType.NEXT_LINE_START -> "CONTINUATION_LINE_START_CUE_V1"
                ReadingAidType.READING_BOOKMARK -> "LAST_READ_LINE_BOOKMARK_V1"
                ReadingAidType.WORD_MAGNIFIER -> "CONTEXT_WORD_MAGNIFIER_V1"
                ReadingAidType.WORD_DEFINITION -> if (currentGuideProfile()) "CONTEXT_WORD_LOOKUP_OFFER_V1" else "CONTEXT_WORD_DEFINITION_V1"
                ReadingAidType.FOCUS_REGION -> if (currentGuideProfile()) "CONTEXT_FOCUS_REGION_V4" else "CONTEXT_FOCUS_REGION_V1"
            }, config.conditionId, anchor, strength,
            if (type == ReadingAidType.WORD_MAGNIFIER) 1.0 +
                (config.maxMagnificationScale - 1.0) * strength else null,
            if (type == ReadingAidType.FOCUS_REGION) config.focusContextRadiusLines else null,
            c.nowMs, if (type == ReadingAidType.READING_BOOKMARK || focusEvidence != null) Double.MAX_VALUE else c.nowMs + config.aidTtlMs, events, evidence,
            first, last, available, rule, sources, relationship, true, config.configId, continuationEvidence, bookmarkEvidence, guideRange, focusEvidence,
            focusStyle = focusStyle)
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
        if (applyCooldown && type !in listOf(ReadingAidType.CURRENT_LINE, ReadingAidType.CURRENT_SENTENCE))
            cooldownUntil[type] = now + config.cooldownMs
        out.add(ReadingAidDecision(ReadingAidDecisionStatus.CLEARED, prior, reason, now,
            prior.eventIds, prior.ruleId, prior.sourceIds))
    }

    private fun expire(type: ReadingAidType, now: Double, out: MutableList<ReadingAidDecision>) {
        val prior = active.remove(type) ?: return
        if (type !in listOf(ReadingAidType.CURRENT_LINE, ReadingAidType.CURRENT_SENTENCE)) cooldownUntil[type] = now + config.cooldownMs
        out.add(ReadingAidDecision(ReadingAidDecisionStatus.EXPIRED, prior, "ttl_expired", now,
            prior.eventIds, prior.ruleId, prior.sourceIds))
    }

    private fun clearAll(now: Double, reason: String, out: MutableList<ReadingAidDecision>,
                         preserveFocus: Boolean = false) {
        for (type in active.keys.toList()) if (!preserveFocus || type != ReadingAidType.FOCUS_REGION)
            clear(type, now, reason, out, applyCooldown = false)
        cooldownUntil.clear()
    }

    private fun applyBookmark(change: ReadingBookmarkPolicy.Change, c: ReadingAidContext,
                              out: MutableList<ReadingAidDecision>) {
        change.clearReason?.let { clear(ReadingAidType.READING_BOOKMARK, c.nowMs, it, out, false) }
        change.evidence?.let { e ->
            choose(ReadingAidType.READING_BOOKMARK, anchor(c, e.source.line), 1.0,
                e.source.firstCaptureMs, e.departureLastCaptureMs ?: e.source.lastCaptureMs,
                max(e.source.evidenceAvailableMs, e.departureEvidenceAvailableMs),
                emptyList(), listOfNotNull(e.source.evidenceId, e.departureSupport?.evidenceId),
                "A2_LAST_READ_LINE_BOOKMARK_V2", listOf("EB15", "CW12"),
                "EB15/CW12 known-interruption last-location cue precedents; autonomous outside-article, unavailable-gaze and line-jump triggers on phones are experimental and benefit is unvalidated.",
                c, out, bookmarkEvidence = e)
        }
    }

    private fun clearDynamic(now: Double, reason: String, out: MutableList<ReadingAidDecision>,
                             preserveFocus: Boolean = false) {
        for (type in active.keys.toList()) if (type != ReadingAidType.READING_BOOKMARK &&
            (!preserveFocus || type != ReadingAidType.FOCUS_REGION))
            clear(type, now, reason, out, false)
        cooldownUntil.clear()
    }

    private fun resetEvidence(preserveBookmark: Boolean = false, preserveFocus: Boolean = false) {
        if (preserveFocus) focus.interruptReading() else focus.reset()
        if (!preserveBookmark) bookmark.reset() else bookmark.interruptTransition()
        wordVisit = null; cycle = null; lineResidence = null; segment = null
        resetContinuation()
        markerLanding = null
        lastContinuationCapture = Double.NEGATIVE_INFINITY
    }
}
