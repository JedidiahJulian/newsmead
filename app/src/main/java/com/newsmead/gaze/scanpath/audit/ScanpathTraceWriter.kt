package com.newsmead.gaze.scanpath.audit

import android.content.Context
import android.os.SystemClock
import com.newsmead.BuildConfig
import com.newsmead.gaze.aid.ReadingAidAction
import com.newsmead.gaze.aid.continuationEvidenceJson
import com.newsmead.gaze.aid.ReadingAidDecision
import com.newsmead.gaze.scanpath.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Typed input/evidence/action audit shared by replay and the separately versioned pilot. */
open class ScanpathTraceWriter(context: Context, prefix: String, header: JSONObject,
    private val recordLimit: Int = 20000, private val ordinalKey: String = "input_ordinal") {
    private val finalFile = File(context.noBackupFilesDir, "${prefix}_${UUID.randomUUID()}.jsonl").also {
        require(prefix.matches(Regex("[a-z_]+")))
        require(recordLimit >= 2)
    }
    private val partialFile = File(finalFile.absolutePath + ".partial")
    val file get() = if (closed && failure == null) finalFile else partialFile
    private val writer = partialFile.bufferedWriter(Charsets.UTF_8)
    private var sequence = 0
    private var closed = false
    var failure: String? = null
        private set

    init {
        append("header", header.put("version_name", BuildConfig.VERSION_NAME)
            .put("version_code", BuildConfig.VERSION_CODE).put("coordinate_units", "physical_screen_pixels")
            .put("offset_units", "UTF-16").put("time_units", "milliseconds")
            .put("record_limit", recordLimit).put("completion_requires", "final_jsonl_filename_and_complete_footer"))
    }

    fun append(kind: String, value: JSONObject) {
        if (closed || failure != null) return
        // Reserve one record for the termination summary, even if the data cap is reached.
        if (sequence >= recordLimit - 1) { failure = "record_limit"; return }
        write(kind, value)
    }

    private fun write(kind: String, value: JSONObject) {
        try {
            writer.append(JSONObject().put("sequence", ++sequence).put("kind", kind)
                .put("record_elapsed_ms", now()).put("detail", value).toString()).append('\n')
        } catch (e: Exception) { failure = e.javaClass.simpleName }
    }

    fun geometry(generation: Int, g: ScanpathGeometry?) = append("geometry", JSONObject()
        .put("generation", generation).put("snapshot_id", g?.snapshotId ?: JSONObject.NULL)
        .put("origin_x", g?.textOriginX ?: JSONObject.NULL).put("origin_y", g?.textOriginY ?: JSONObject.NULL)
        .put("line_height_px", g?.lineHeightPx ?: JSONObject.NULL).put("direction", g?.textDirection?.name ?: JSONObject.NULL)
        .put("visible", g?.visibleRectScreen?.let(::rect) ?: JSONObject.NULL)
        .put("lines", JSONArray(g?.lines?.map { line -> JSONObject()
            .put("start_utf16", line.startUtf16).put("end_utf16", line.endUtf16)
            .put("left", line.left).put("right", line.right).put("top", line.top).put("bottom", line.bottom)
        } ?: emptyList<JSONObject>())))

    fun sample(generation: Int, ordinal: Int, s: ScanpathSample) = append("sample", JSONObject()
        .put("generation", generation).put(ordinalKey, ordinal).put("capture_ms", finite(s.captureMs))
        .put("delivery_ms", finite(s.deliveryMs)).put("x", finite(s.rawX)).put("y", finite(s.rawY))
        .put("nonfinite_fields", JSONArray(listOf("capture_ms" to s.captureMs, "delivery_ms" to s.deliveryMs,
            "x" to s.rawX, "y" to s.rawY).filter { it.second != null && !it.second!!.isFinite() }.map { it.first }))
        .put("line", s.line ?: JSONObject.NULL).put("word_start_utf16", s.wordStartUtf16 ?: JSONObject.NULL)
        .put("word_end_utf16", s.wordEndUtf16 ?: JSONObject.NULL).put("geometry_snapshot_id", s.geometrySnapshotId ?: JSONObject.NULL)
        .put("eligible", s.eligible).put("exclusion", s.exclusion ?: JSONObject.NULL))

    fun fixation(generation: Int, f: ScanpathFixation) = append("fixation", JSONObject()
        .put("generation", generation).put("index", f.index).put("segment", f.segment)
        .put("start_ms", f.startMs).put("end_ms", f.endMs).put("duration_ms", f.durationMs)
        .put("sample_count", f.sampleCount).put("x", f.x).put("y", f.y).put("dispersion_px", f.dispersionPx)
        .put("line", f.line ?: JSONObject.NULL).put("word_start_utf16", f.wordStartUtf16 ?: JSONObject.NULL)
        .put("word_end_utf16", f.wordEndUtf16 ?: JSONObject.NULL).put("word_agreement", f.wordSampleAgreement)
        .put("line_agreement", f.lineSampleAgreement).put("left_censored", f.leftCensored).put("right_censored", f.rightCensored)
        .put("geometry_snapshot_id", f.geometrySnapshotId ?: JSONObject.NULL)
        .put("evidence_available_ms", f.evidenceAvailableMs ?: JSONObject.NULL).put("evidence_time_basis", f.evidenceTimeBasis))

    fun candidate(generation: Int, c: ScanpathCandidateEvidence?) {
        if (c == null) return
        append("candidate_prefix", JSONObject().put("generation", generation).put("id", c.candidateId)
            .put("segment", c.segment).put("start_ms", c.startMs).put("end_ms", c.endMs).put("duration_ms", c.durationMs)
            .put("sample_count", c.sampleCount).put("line", c.line ?: JSONObject.NULL)
            .put("word_start_utf16", c.wordStartUtf16 ?: JSONObject.NULL).put("word_end_utf16", c.wordEndUtf16 ?: JSONObject.NULL)
            .put("word_agreement", c.wordSampleAgreement).put("line_agreement", c.lineSampleAgreement)
            .put("geometry_snapshot_id", c.geometrySnapshotId ?: JSONObject.NULL)
            .put("evidence_available_ms", c.evidenceAvailableMs ?: JSONObject.NULL).put("evidence_time_basis", c.evidenceTimeBasis))
    }

    fun event(generation: Int, e: ScanpathEvent) = append("pattern", JSONObject()
        .put("generation", generation).put("id", e.eventId).put("pattern_id", e.patternId).put("resolution", e.resolution)
        .put("fixation_indices", JSONArray(e.fixationIndices)).put("start_ms", e.startMs ?: JSONObject.NULL)
        .put("end_ms", e.endMs ?: JSONObject.NULL).put("evidence_available_ms", e.evidenceAvailableMs ?: JSONObject.NULL)
        .put("evidence_time_basis", e.evidenceTimeBasis ?: JSONObject.NULL).put("metrics", JSONObject(e.metrics))
        .put("limitations", JSONArray(e.limitations)))

    fun decision(d: ReadingAidDecision) = append("decision", JSONObject().put("status", d.status.name)
        .put("reason", d.reason).put("decision_ms", d.nowMs).put("rule_id", d.ruleId ?: JSONObject.NULL)
        .put("event_ids", JSONArray(d.eventIds)).put("source_ids", JSONArray(d.sourceIds))
        .put("action", d.action?.let(::action) ?: JSONObject.NULL))

    fun render(a: ReadingAidAction, drawn: Boolean, reason: String, atMs: Double) = append("render", JSONObject()
        .put("action_id", a.id).put("drawn_proxy", drawn).put("reason", reason).put("render_ms", atMs)
        .put("verified_visibility", false))

    fun close(reason: String, summary: JSONObject) {
        if (closed) return
        try { writer.flush() } catch (e: Exception) { if (failure == null) failure = e.javaClass.simpleName }
        val problem = failure
        write("footer", summary.put("termination", reason).put("complete_trace", problem == null)
            .put("record_count_before_footer", sequence).put("trace_failure", problem ?: JSONObject.NULL))
        if (problem != null) failure = problem
        closed = true
        try { writer.close() } catch (e: Exception) { if (failure == null) failure = e.javaClass.simpleName }
        // A final name is published only after every write and close succeeds. A partial
        // file remains incomplete even if an I/O failure follows a complete-looking footer.
        if (failure == null && !partialFile.renameTo(finalFile)) failure = "finalize_failed"
    }

    private fun action(a: ReadingAidAction) = JSONObject().put("id", a.id).put("aid_type", a.aidType.name)
        .put("form_id", a.aidFormId).put("condition_id", a.conditionId).put("config_id", a.configId)
        .put("generation", a.anchor.generation).put("geometry_snapshot_id", a.anchor.geometrySnapshotId)
        .put("line", a.anchor.line).put("word_start_utf16", a.anchor.wordStartUtf16 ?: JSONObject.NULL)
        .put("word_end_utf16", a.anchor.wordEndUtf16 ?: JSONObject.NULL).put("strength", a.strength)
        .put("magnification_scale", a.magnificationScale ?: JSONObject.NULL).put("context_radius_lines", a.contextRadiusLines ?: JSONObject.NULL)
        .put("onset_ms", a.onsetMs).put("expires_ms", a.expiresMs).put("first_capture_ms", a.firstCaptureMs)
        .put("last_capture_ms", a.lastCaptureMs).put("evidence_available_ms", a.evidenceAvailableMs)
        .put("event_ids", JSONArray(a.eventIds)).put("evidence_ids", JSONArray(a.evidenceIds))
        .put("continuation_evidence", a.continuationEvidenceJson())
        .put("rule_id", a.ruleId).put("source_ids", JSONArray(a.sourceIds)).put("experimental", a.experimental)
        .put("evidence_relationship", a.evidenceRelationship)

    private fun rect(r: ScanpathRect) = JSONObject().put("left", r.left).put("right", r.right).put("top", r.top).put("bottom", r.bottom)
    private fun finite(value: Double?): Any = value?.takeIf { it.isFinite() } ?: JSONObject.NULL
    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e6
}
