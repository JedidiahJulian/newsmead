package com.newsmead.gaze.aid

import org.json.JSONArray
import org.json.JSONObject

/** Numeric support only; the expected line is separately recorded in the action anchor. */
internal fun ReadingAidAction.continuationEvidenceJson() = JSONArray(continuationEvidence.map {
    JSONObject().put("evidence_id", it.evidenceId).put("segment", it.segment).put("line", it.line)
        .put("first_capture_ms", it.firstCaptureMs).put("last_capture_ms", it.lastCaptureMs)
        .put("evidence_available_ms", it.evidenceAvailableMs)
})
