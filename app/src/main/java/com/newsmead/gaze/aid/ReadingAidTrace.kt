package com.newsmead.gaze.aid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathFixation

/** Bounded in-memory numeric/action journal; disk export requires the explicit menu action. */
class ReadingAidTrace(private val capacity: Int = 2048) {
    private val records = ArrayDeque<JSONObject>()
    private var sequence = 0L
    private var dropped = 0L
    private val facts = linkedMapOf<String, JSONObject>()
    private var evictedFacts = 0L
    private var incompleteSupportRecords = 0L
    private var liveContext = JSONObject()
    fun context(value: ReadingAidContext) {
        liveContext = JSONObject().put("generation", value.generation)
            .put("geometry_snapshot_id", value.geometrySnapshotId).put("line_count", value.lineCount)
            .put("line", value.targetLine ?: JSONObject.NULL)
            .put("word_start_utf16", value.targetWordStartUtf16 ?: JSONObject.NULL)
            .put("word_end_utf16", value.targetWordEndUtf16 ?: JSONObject.NULL)
            .put("capture_ms", value.captureMs ?: JSONObject.NULL)
            .put("delivery_ms", value.evidenceAvailableMs ?: JSONObject.NULL)
            .put("valid_live_input", value.validLiveInput)
            .put("focus_input_allowed", value.focusInputAllowed)
            .put("focus_text_size_dp", value.focusTextSizeDp.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("coordinate_stream", value.rawSample?.coordinateStream?.id ?: JSONObject.NULL)
            .put("processing_x_px", value.rawSample?.processingX?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("processing_y_px", value.rawSample?.processingY?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("raw_x_px", value.rawSample?.rawX?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("raw_y_px", value.rawSample?.rawY?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("analysis_x_content_px", value.rawSample?.motionCompensation?.contentX?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("analysis_y_content_px", value.rawSample?.motionCompensation?.contentY?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("analysis_frame", value.rawSample?.motionCompensation?.modeId ?: "screen")
            .put("capture_geometry_at_ms", value.rawSample?.motionCompensation?.geometryTimeMs?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("capture_text_origin_x_px", value.rawSample?.motionCompensation?.textOriginX?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("capture_text_origin_y_px", value.rawSample?.motionCompensation?.textOriginY?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            .put("presentation_text_origin_x_px", value.geometry?.textOriginX ?: JSONObject.NULL)
            .put("presentation_text_origin_y_px", value.geometry?.textOriginY ?: JSONObject.NULL)
    }
    fun event(generation: Int, e: ScanpathEvent) {
        remember(generation, e.eventId, JSONObject().put("kind", "finalized_pattern")
            .put("event_id", e.eventId).put("pattern_id", e.patternId).put("resolution", e.resolution)
            .put("fixation_indices", JSONArray(e.fixationIndices)).put("start_ms", e.startMs ?: JSONObject.NULL)
            .put("end_ms", e.endMs ?: JSONObject.NULL).put("evidence_available_ms", e.evidenceAvailableMs ?: JSONObject.NULL)
            .put("evidence_time_basis", e.evidenceTimeBasis ?: JSONObject.NULL).put("metrics", JSONObject(e.metrics)))
    }
    fun fixation(generation: Int, f: ScanpathFixation) {
        remember(generation, "fixation:${f.index}", JSONObject().put("kind", "finalized_candidate")
            .put("index", f.index).put("segment", f.segment).put("geometry_snapshot_id", f.geometrySnapshotId)
            .put("start_ms", f.startMs).put("end_ms", f.endMs).put("observed_span_ms", f.durationMs)
            .put("sample_count", f.sampleCount).put("word_start_utf16", f.wordStartUtf16 ?: JSONObject.NULL)
            .put("word_end_utf16", f.wordEndUtf16 ?: JSONObject.NULL).put("line", f.line ?: JSONObject.NULL)
            .put("word_sample_agreement", f.wordSampleAgreement).put("line_sample_agreement", f.lineSampleAgreement)
            .put("left_censored", f.leftCensored).put("right_censored", f.rightCensored)
            .put("evidence_available_ms", f.evidenceAvailableMs ?: JSONObject.NULL).put("evidence_time_basis", f.evidenceTimeBasis))
    }
    fun candidate(generation: Int, c: ScanpathCandidateEvidence?) {
        if (c == null) return
        remember(generation, c.candidateId, JSONObject().put("kind", "provisional_prefix")
            .put("candidate_id", c.candidateId).put("segment", c.segment)
            .put("geometry_snapshot_id", c.geometrySnapshotId).put("start_ms", c.startMs).put("end_ms", c.endMs)
            .put("observed_span_ms", c.durationMs).put("sample_count", c.sampleCount)
            .put("word_start_utf16", c.wordStartUtf16 ?: JSONObject.NULL)
            .put("word_end_utf16", c.wordEndUtf16 ?: JSONObject.NULL).put("line", c.line ?: JSONObject.NULL)
            .put("word_sample_agreement", c.wordSampleAgreement).put("line_sample_agreement", c.lineSampleAgreement)
            .put("evidence_available_ms", c.evidenceAvailableMs ?: JSONObject.NULL)
            .put("evidence_time_basis", c.evidenceTimeBasis))
    }
    fun focusEpisode(episode: ReadingAidFocusEpisode) {
        val detail = episode.focusEpisodeJson()
        remember(episode.generation.toInt(), episode.episodeId,
            JSONObject(detail.toString()).put("kind", "focus_interruption_episode"))
        record("focus_interruption_episode", episode.completedDeliveryMs, detail)
    }
    private fun remember(generation: Int, id: String, fact: JSONObject) {
        val key = "$generation/$id"
        facts.remove(key)
        if (facts.size == 512) { facts.remove(facts.keys.first()); evictedFacts++ }
        facts[key] = fact.put("generation", generation)
    }
    fun record(kind: String, nowMs: Double, detail: JSONObject) {
        if (records.size == capacity) { records.removeFirst(); dropped++ }
        records.addLast(JSONObject().put("sequence", ++sequence).put("kind", kind)
            .put("elapsed_ms", nowMs).put("detail", detail))
    }
    fun decision(value: ReadingAidDecision) = record("decision", value.nowMs, JSONObject()
        .put("status", value.status.name).put("reason", value.reason)
        .put("rule_id", value.ruleId ?: JSONObject.NULL).put("sources", JSONArray(value.sourceIds))
        .put("event_ids", JSONArray(value.eventIds)).put("input", liveContext)
        .put("support", support(value.action?.anchor?.generation ?: liveContext.optLong("generation"),
            value.eventIds + (value.action?.evidenceIds ?: emptyList())))
        .put("action", value.action?.let(::action) ?: JSONObject.NULL))
    fun render(value: ReadingAidAction, drawn: Boolean, reason: String, nowMs: Double) =
        record("render", nowMs, JSONObject().put("action_id", value.id).put("drawn_proxy", drawn)
            .put("reason", reason).put("presentation_phase", when {
                reason.startsWith("definition_offer_") -> "lookup_offer"
                reason.startsWith("definition_") -> "definition_panel"
                else -> JSONObject.NULL
            }).put("verified_visibility", false))

    /** App-private numeric export includes gaze coordinates; no word text, camera frames or upload. */
    fun export(context: Context, configuration: JSONObject): File {
        val file = File(context.noBackupFilesDir, "scanpath_aids_${UUID.randomUUID()}.json")
        file.writeText(JSONObject().put("schema_version", 2).put("kind", "scanpath_aid_trace")
            .put("configuration", configuration).put("retained_records", records.size)
            .put("dropped_records", dropped).put("complete_trace", dropped == 0L && incompleteSupportRecords == 0L)
            .put("source_facts_evicted", evictedFacts)
            .put("incomplete_support_records", incompleteSupportRecords)
            .put("camera_frames_retained", false).put("records", JSONArray(records.toList())).toString(2))
        return file
    }

    private fun action(a: ReadingAidAction) = JSONObject()
        .put("action_id", a.id).put("aid_type", a.aidType.name).put("aid_form_id", a.aidFormId)
        .put("condition_id", a.conditionId).put("config_id", a.configId)
        .put("generation", a.anchor.generation).put("geometry_snapshot_id", a.anchor.geometrySnapshotId)
        .put("line", a.anchor.line).put("word_start_utf16", a.anchor.wordStartUtf16 ?: JSONObject.NULL)
        .put("word_end_utf16", a.anchor.wordEndUtf16 ?: JSONObject.NULL)
        .put("strength", a.strength).put("magnification_scale", a.magnificationScale ?: JSONObject.NULL)
        .put("context_radius_lines", a.contextRadiusLines ?: JSONObject.NULL)
        .put("focus_style", a.focusStyle?.name ?: JSONObject.NULL)
        .put("onset_ms", a.onsetMs).put("expires_ms", a.expiresMs)
        .put("first_capture_ms", a.firstCaptureMs).put("last_capture_ms", a.lastCaptureMs)
        .put("evidence_available_ms", a.evidenceAvailableMs)
        .put("event_ids", JSONArray(a.eventIds)).put("evidence_ids", JSONArray(a.evidenceIds))
        .put("continuation_evidence", a.continuationEvidenceJson())
        .put("bookmark_evidence", a.bookmarkEvidenceJson())
        .put("focus_evidence", a.focusEvidenceJson())
        .put("guide_range", a.guideRangeJson())
        .put("rule_id", a.ruleId).put("sources", JSONArray(a.sourceIds))
        .put("evidence_relationship", a.evidenceRelationship).put("experimental", a.experimental)

    private fun support(generation: Long, ids: List<String>): JSONObject {
        val available = ids.distinct().mapNotNull { facts["$generation/$it"] }
        val missing = ids.distinct().filter { "$generation/$it" !in facts }
        if (missing.isNotEmpty()) incompleteSupportRecords++
        return JSONObject().put("facts", JSONArray(available))
            .put("complete_support", missing.isEmpty()).put("missing_ids", JSONArray(missing))
    }
}
