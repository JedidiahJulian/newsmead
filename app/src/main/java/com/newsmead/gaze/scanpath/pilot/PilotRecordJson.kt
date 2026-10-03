package com.newsmead.gaze.scanpath.pilot

import com.newsmead.gaze.scanpath.ScanpathRect
import org.json.JSONArray
import org.json.JSONObject

/** Explicit counts and null ratios; no inference of eye-position truth or difficulty. */
object PilotRecordJson {
    fun rect(r: ScanpathRect) = JSONObject().put("left", r.left).put("top", r.top).put("right", r.right).put("bottom", r.bottom)
    fun target(t: PilotTarget) = JSONObject().put("id", t.id).put("line", t.line)
        .put("word_start_utf16", t.wordStartUtf16 ?: JSONObject.NULL).put("word_end_utf16", t.wordEndUtf16 ?: JSONObject.NULL)
        .put("bounds_screen", rect(t.bounds))
    fun plan(p: PilotTaskPlan) = JSONObject().put("id", p.id).put("kind", p.kind).put("label", p.label)
        .put("reference_limit", p.referenceLimit).put("targets", JSONArray(p.targets.map(::target)))
    fun score(s: PilotScoreResult): JSONObject = JSONObject().put("total_samples", s.totalSamples)
        .put("null_capture", s.nullCapture).put("nonfinite_capture", s.nonfiniteCapture).put("out_of_order_capture", s.outOfOrderCapture)
        .put("invalid_coordinates", s.invalidCoordinates).put("ineligible", s.ineligible).put("geometry_mismatch", s.geometryMismatch)
        .put("dropped_at_capacity", s.droppedAtCapacity).put("outside_intervals", s.outsideIntervals).put("incomplete", s.incomplete)
        .put("capture_gap_breaks", s.captureGapBreaks).put("word_run_segments", JSONArray(s.wordRunSegments))
        .put("line_run_segments", JSONArray(s.lineRunSegments))
        .put("descriptive_word_alignment", order(s.wordOrder)).put("descriptive_line_alignment", order(s.lineOrder))
        .put("prompted_opportunities", JSONObject().put("planned_targets", s.targets.size)
            .put("drawn_targets", s.targets.count { it.attempted }).put("targets_with_scorable_samples", s.targets.count { it.scorableSamples > 0 })
            .put("word_references_planned", s.targets.count { it.target.wordStartUtf16 != null })
            .put("targets_with_exact_word_sample", s.targets.count { it.exactWord > 0 })
            .put("targets_with_exact_line_sample", s.targets.count { it.exactLine > 0 })
            .put("reference_kind", "drawn_target_capture_windows_not_physiological_events"))
        .put("targets", JSONArray(s.targets.map { t -> JSONObject().put("index", t.index).put("target", target(t.target))
            .put("attempted", t.attempted).put("onset_ms", t.onsetMs ?: JSONObject.NULL).put("end_ms", t.endMs ?: JSONObject.NULL)
            .put("interval_samples", t.intervalSamples).put("scorable_samples", t.scorableSamples).put("eligible_samples", t.eligibleSamples)
            .put("word_assigned", t.wordAssigned).put("line_assigned", t.lineAssigned).put("exact_word", t.exactWord).put("exact_line", t.exactLine)
            .put("unknown_word_given_eligible_count", t.unknownWord).put("unknown_line_given_eligible_count", t.unknownLine)
            .put("word_unavailable_scorable_count", t.scorableSamples - t.eligibleSamples + t.unknownWord)
            .put("line_unavailable_scorable_count", t.scorableSamples - t.eligibleSamples + t.unknownLine)
            .put("exact_word_given_eligible", t.exactWordGivenEligible ?: JSONObject.NULL)
            .put("exact_line_given_eligible", t.exactLineGivenEligible ?: JSONObject.NULL) }))
    private fun order(s: PilotOrderScore) = JSONObject().put("expected", JSONArray(s.expected)).put("observed", JSONArray(s.recovered))
        .put("reference_available", s.referenceAvailable).put("score", if (!s.referenceAvailable) JSONObject.NULL else
            JSONObject().put("matches", s.matches).put("misses", s.misses).put("extras", s.extras).put("edit_distance", s.editDistance)
                .put("max_matches_in_one_segment", s.maxMatchesInOneSegment))
        .put("global_alignment_ignores_breaks", true).put("temporal_matching", false).put("event_precision", JSONObject.NULL)
}
