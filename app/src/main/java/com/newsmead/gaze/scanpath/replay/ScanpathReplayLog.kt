package com.newsmead.gaze.scanpath.replay

import android.content.Context
import com.newsmead.gaze.scanpath.ScanpathCoordinateStream
import com.newsmead.gaze.scanpath.audit.ScanpathTraceWriter
import org.json.JSONArray
import org.json.JSONObject

/** Post-filter synthetic trajectories exercise software behavior, not tracking accuracy. */
class ScanpathReplayLog(context: Context, scenario: ReplayScenario, body: String,
    configuration: JSONObject, plan: List<ReplayPoint>, originElapsedMs: Double,
    recordLimit: Int = 20000) : ScanpathTraceWriter(context, "scanpath_replay",
    JSONObject().put("schema_version", 2).put("input_kind", "synthetic_replay")
        .put("coordinate_stream", ScanpathCoordinateStream.ARTICLE_SMOOTHED.id)
        .put("input_phase", "post_filter_synthetic_trajectory").put("smoothing_applied_to_synthetic_input", false)
        .put("raw_scoring", false).put("processed_scoring", true)
        .put("camera_frames_retained", false).put("human_reference", false)
        .put("scenario_id", scenario.id).put("body_text", body).put("configuration", configuration)
        .put("expected_patterns", JSONArray(scenario.expectedPatternIds.toList()))
        .put("expected_aids", JSONArray(scenario.expectedAidTypes.toList()))
        .put("origin_elapsed_ms", originElapsedMs)
        .put("capture_time_basis", "scheduled_synthetic_elapsed_realtime")
        .put("delivery_time_basis", "actual_elapsed_realtime_callback")
        .put("max_delivery_lag_ms", 100).put("plan", JSONArray(plan.map {
            JSONObject().put("offset_ms", it.offsetMs).put("x", it.x ?: JSONObject.NULL)
                .put("y", it.y ?: JSONObject.NULL).put("eligible", it.eligible)
                .put("source_reason", it.sourceReason ?: JSONObject.NULL)
        })), recordLimit, "plan_ordinal")
