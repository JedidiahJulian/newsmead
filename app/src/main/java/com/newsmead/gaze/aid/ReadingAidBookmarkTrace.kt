package com.newsmead.gaze.aid

import org.json.JSONObject

/** The saved source and the departure have separate capture/delivery bounds. */
internal fun ReadingAidAction.bookmarkEvidenceJson(): Any {
    val evidence = bookmarkEvidence ?: return JSONObject.NULL
    return JSONObject().put("source", evidence.source.bookmarkSupportJson())
        .put("departure_reason", evidence.departureReason)
        .put("departure_first_capture_ms", evidence.departureFirstCaptureMs ?: JSONObject.NULL)
        .put("departure_last_capture_ms", evidence.departureLastCaptureMs ?: JSONObject.NULL)
        .put("departure_evidence_available_ms", evidence.departureEvidenceAvailableMs)
        .put("departure_sample_count", evidence.departureSampleCount)
        .put("departure_time_basis", evidence.departureTimeBasis)
        .put("departure_first_delivery_ms", evidence.departureFirstDeliveryMs ?: JSONObject.NULL)
        .put("departure_last_delivery_ms", evidence.departureLastDeliveryMs ?: JSONObject.NULL)
        .put("departure_support", evidence.departureSupport?.bookmarkSupportJson() ?: JSONObject.NULL)
}

private fun ReadingAidContinuationEvidence.bookmarkSupportJson() = JSONObject()
    .put("evidence_id", evidenceId).put("segment", segment).put("line", line)
    .put("first_capture_ms", firstCaptureMs).put("last_capture_ms", lastCaptureMs)
    .put("evidence_available_ms", evidenceAvailableMs)
