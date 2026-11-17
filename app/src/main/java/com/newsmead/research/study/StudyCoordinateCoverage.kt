package com.newsmead.research.study

import com.newsmead.gaze.trajectory.CoordinateMahalanobis as Core
import org.json.JSONObject

/** Bounded live preview of coordinate-reference minimum data, never fit readiness or accuracy. */
class StudyCoordinateCoverage(private val startMs: Double, private val config: Core.Config = Core.Config()) {
    data class Snapshot(val coveredMs: Double, val requiredMs: Double, val weightedPointCount: Int,
        val issue: String?, val minimumDataReady: Boolean)
    private data class Row(val placement: Double?, val observation: Core.Observation?)
    private val rows = ArrayList<Row>()
    private val boundaries = ArrayList<Core.Boundary>()
    private var failure: String? = null
    init { require(startMs.isFinite() && startMs >= 0) }

    fun unavailable(reason: String) { failure = failure ?: reason }

    /** Any recording loss invalidates this attempt's preview, even if the writer keeps running. */
    fun recordingHealth(lostRecords: Long, writerFailure: String?) {
        if (lostRecords > 0) unavailable("recording_loss")
        if (writerFailure != null) unavailable("recording_failure")
    }

    /** Consumes the exact serialized payload; selected/display coordinates are never substituted. */
    fun observation(payload: JSONObject) {
        if (failure != null) return
        runCatching {
            check(rows.size < MAX_ROWS) { "observation_limit" }
            val capture = number(payload, "capture_ms")
            val delivery = number(payload, "delivery_ms")
            val placement = capture?.takeIf { it.isFinite() } ?: delivery?.takeIf { it.isFinite() }
            val c = payload.optJSONObject("coordinate_context")
            val observation = c?.let {
                val context = Core.Context(it.getString("participant_id"), it.getString("installation_id"),
                    it.getString("calibration_id"), it.getString("model_id"), it.getString("acquisition_id"),
                    it.getString("presentation_id"), it.getString("clock_domain_id"), it.getInt("screen_width_px"),
                    it.getInt("screen_height_px"), it.optInt("rotation_degrees", 0),
                    it.optString("coordinate_stream", Core.RAW_STREAM))
                check(context.coordinateStream == Core.RAW_STREAM) { "raw_stream_required" }
                Core.Observation(context, payload.getString("provider_epoch_id"), payload.getString("context_epoch_id"),
                    payload.getLong("delivery_id"), capture, delivery ?: Double.NaN,
                    number(payload, "raw_x_px"), number(payload, "raw_y_px"), payload.getString("reason"),
                    payload.getBoolean("timeout_watchdog"), payload.getBoolean("context_allowed"))
            }
            rows.add(Row(placement, observation))
        }.onFailure { unavailable("preview_payload_or_resource_failure") }
    }

    fun boundary(atMs: Double, reason: String) {
        if (failure != null) return
        runCatching {
            check(boundaries.size < MAX_BOUNDARIES)
            boundaries.add(Core.Boundary(atMs, reason))
        }.onFailure { unavailable("preview_boundary_or_resource_failure") }
    }

    fun snapshot(endMs: Double): Snapshot {
        fun result(covered: Double = 0.0, points: Int = 0, issue: String? = null) = Snapshot(
            covered, config.minReferenceCoverageMs, points, issue,
            issue == null && covered >= config.minReferenceCoverageMs && points >= config.minReferencePoints)
        failure?.let { return result(issue = it) }
        if (!endMs.isFinite() || endMs < startMs) return result(issue = "invalid_preview_bounds")
        if (endMs == startMs) return result(issue = "no_in_bound_observations")
        return runCatching {
            // Match study_package.reference_fit: finite placement segments the period; unplaceable
            // rows remain in arrival order as adjacency breaks. Late terminal rows earn no credit.
            val selected = rows.filter { it.placement == null || it.placement in startMs..endMs }
            if (selected.any { it.observation == null }) return result(issue = "context_unavailable")
            val observations = selected.map { it.observation!! }
            val context = observations.firstOrNull()?.context ?: return result(issue = "no_in_bound_observations")
            if (observations.any { it.context != context }) return result(issue = "multiple_reference_contexts")
            val stats = Core.referenceStats(context, observations, startMs, endMs, config, boundaries)
            result(stats.coveredMs, stats.weightedPointCount)
        }.getOrElse { unavailable("preview_statistics_failure"); result(issue = failure) }
    }

    private fun number(payload: JSONObject, key: String): Double? =
        if (payload.isNull(key)) null else payload.getDouble(key)

    companion object {
        // No recording data is dropped by these limits: only the optional preview becomes unavailable.
        private const val MAX_ROWS = 20_000
        private const val MAX_BOUNDARIES = 4_096
    }
}
