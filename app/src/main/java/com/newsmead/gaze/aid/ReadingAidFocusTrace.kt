package com.newsmead.gaze.aid

import org.json.JSONArray
import org.json.JSONObject

internal fun ReadingAidAction.focusEvidenceJson(): Any {
    val e = focusEvidence ?: return JSONObject.NULL
    return JSONObject().put("rule_id", e.ruleId).put("episode_count", e.episodeCount)
        .put("cycle_count", e.cycleCount).put("window_ms", e.windowMs)
        .put("initial_strength", e.initialStrength).put("observed_recovery_ms", e.observedRecoveryMs)
        .put("text_size_dp", e.textSizeDp).put("required_alternations", e.requiredAlternations)
        .put("minimum_line_visit_ms", e.minimumLineVisitMs)
        .put("recovery_hold_ms", e.recoveryHoldMs).put("recovery_fade_ms", e.recoveryFadeMs)
        .put("recovery_time_basis", "eligible_ordered_selected_capture_deltas")
        .put("episodes", JSONArray().apply { e.episodes.forEach { put(it.focusEpisodeJson()) } })
        .put("cycle_event_ids", JSONArray(e.cycleEventIds))
        .put("cycle_fixation_indices", JSONArray().apply { e.cycleFixationIndices.forEach { put(JSONArray(it)) } })
        .put("line_visit_alternations", JSONArray().apply { e.alternations.forEach { a ->
            put(JSONObject().put("evidence_id", a.evidenceId).put("generation", a.generation)
                .put("geometry_snapshot_id", a.geometrySnapshotId).put("confirmed_delivery_ms", a.confirmedDeliveryMs)
                .put("kind", "aid_supported_line_visits_not_finalized_p11")
                .put("visits", JSONArray().apply { a.visits.forEach { put(it.focusSupportJson()) } }))
        } })
        .put("support", e.support.focusSupportJson())
}

internal fun ReadingAidFocusEpisode.focusEpisodeJson(): JSONObject = JSONObject()
    .put("episode_id", episodeId).put("generation", generation).put("geometry_snapshot_id", geometrySnapshotId)
    .put("source", source?.focusSupportJson() ?: JSONObject.NULL)
    .put("recovery", recovery?.focusSupportJson() ?: JSONObject.NULL)
    .put("completed_delivery_ms", completedDeliveryMs)
    .put("presence_recovery", presenceRecovery?.let { p -> JSONObject()
        .put("evidence_id", p.evidenceId).put("generation", p.generation)
        .put("geometry_snapshot_id", p.geometrySnapshotId).put("coordinate_stream", p.coordinateStream)
        .put("first_capture_ms", p.firstCaptureMs).put("last_capture_ms", p.lastCaptureMs)
        .put("first_delivery_ms", p.firstDeliveryMs).put("confirmed_delivery_ms", p.confirmedDeliveryMs)
        .put("sample_count", p.sampleCount).put("requires_line_or_word_assignment", false)
    } ?: JSONObject.NULL)
    .put("missing_first_delivery_ms", missingFirstDeliveryMs).put("missing_last_delivery_ms", missingLastDeliveryMs)
    .put("missing_first_capture_ms", missingFirstCaptureMs ?: JSONObject.NULL)
    .put("missing_last_capture_ms", missingLastCaptureMs ?: JSONObject.NULL)
    .put("first_recovery_delivery_ms", firstRecoveryDeliveryMs)
    .put("qualifying_loss_elapsed_ms", firstRecoveryDeliveryMs - missingFirstDeliveryMs)
    .put("missing_reasons", JSONArray(missingReasons)).put("missing_sample_count", missingSampleCount)
    .put("time_basis", timeBasis)

private fun ReadingAidContinuationEvidence.focusSupportJson() = JSONObject()
    .put("evidence_id", evidenceId).put("segment", segment).put("line", line)
    .put("first_capture_ms", firstCaptureMs).put("last_capture_ms", lastCaptureMs)
    .put("evidence_available_ms", evidenceAvailableMs)
