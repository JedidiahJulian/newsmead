package com.newsmead.gaze

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent, numeric-only record of one nine-point diagnostic run. Camera
 * frames are never stored. The log keeps each observable pipeline stage so an
 * error can be localized without changing the gaze estimate delivered to the UI.
 */
class GazeAccuracySessionLog(
    context: Context,
    runLabel: String,
    screenWidthPx: Int,
    screenHeightPx: Int,
    densityDpi: Int,
    lineHeightPx: Float,
    calibrationPointCount: Int,
    activeCorrection: DriftCorrection?,
) {

    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    private val file = File(context.filesDir, "gaze_accuracy_session_$stamp.json")
    private val root = JSONObject()
    private val points = JSONArray()
    private var finished = false

    init {
        root.put("session_id", "gaze_accuracy_session_$stamp")
        root.put("run_label", runLabel)
        root.put("timestamp_start", isoTimestamp())
        root.put("device_model", Build.MODEL)
        root.put("device_manufacturer", Build.MANUFACTURER)
        root.put("android_version", Build.VERSION.RELEASE)
        root.put("screen_px", JSONArray().put(screenWidthPx).put(screenHeightPx))
        root.put("density_dpi", densityDpi)
        root.putNum("line_height_px", lineHeightPx)
        root.put("calibration_point_count", calibrationPointCount)
        root.put("drift_correction_active", activeCorrection != null)
        root.put("drift_correction", activeCorrection?.toJson() ?: JSONObject.NULL)
        root.put("camera_frames_retained", false)
        root.put(
            "processing_path",
            "raw_average -> median_7 -> one_euro -> quadratic_mapper -> optional_affine_correction",
        )
        root.put("points", points)
        flush()
        Log.i(TAG, "Started gaze accuracy diagnostic log ${file.absolutePath}")
    }

    fun logPoint(
        pointIndex: Int,
        attempt: Int,
        targetX: Float,
        targetY: Float,
        result: FixationWindowFilter.Result,
        pipelineSamples: List<LocalCalibratedGazeProvider.PipelineDiagnostics>,
        sourceEvents: List<LocalRawGazeSource.Diagnostics>,
    ) {
        val accepted = result.status == FixationWindowFilter.Status.ACCEPTED
        val dx = if (accepted) result.medianX - targetX else Float.NaN
        val dy = if (accepted) result.medianY - targetY else Float.NaN
        val error = if (accepted) kotlin.math.hypot(dx, dy) else Float.NaN
        points.put(
            JSONObject().apply {
                put("point_index", pointIndex)
                put("attempt", attempt)
                put("status", result.status.name)
                putNum("target_x", targetX)
                putNum("target_y", targetY)
                putNum("estimate_x", result.medianX)
                putNum("estimate_y", result.medianY)
                putNum("dx_px", dx)
                putNum("dy_px", dy)
                putNum("error_px", error)
                putNum("output_dispersion_x", result.dispersionX)
                putNum("output_dispersion_y", result.dispersionY)
                put("collector_raw_count", result.rawCount)
                put("collector_retained_count", result.retainedCount)
                put("pipeline_samples", JSONArray().apply {
                    pipelineSamples.forEach { put(it.toJson()) }
                })
                put("source_events", JSONArray().apply {
                    sourceEvents.forEach { put(it.toJson()) }
                })
            },
        )
        flush()
    }

    fun logSummary(
        acceptedPointCount: Int,
        medianPx: Float,
        p95Px: Float,
        verticalMedianPx: Float,
        estimatedCorrectedLooPx: Float?,
    ) {
        root.put(
            "summary",
            JSONObject().apply {
                put("accepted_point_count", acceptedPointCount)
                putNum("median_px", medianPx)
                putNum("p95_px", p95Px)
                putNum("vertical_median_px", verticalMedianPx)
                putNum("estimated_corrected_loo_px", estimatedCorrectedLooPx ?: Float.NaN)
            },
        )
        flush()
    }

    fun finish(outcome: String) {
        if (finished) return
        finished = true
        root.put("outcome", outcome)
        root.put("timestamp_end", isoTimestamp())
        flush()
        Log.i(TAG, "Finished gaze accuracy diagnostic log ${file.absolutePath}: $outcome")
    }

    fun fileName(): String = file.name

    private fun LocalCalibratedGazeProvider.PipelineDiagnostics.toJson(): JSONObject =
        JSONObject().apply {
            put("source_sequence", source?.sequence ?: JSONObject.NULL)
            put("output_timestamp_ms", outputTimestampMs)
            putNum("raw_x", rawX)
            putNum("raw_y", rawY)
            putNum("median_x", medianX)
            putNum("median_y", medianY)
            putNum("filtered_x", filteredX)
            putNum("filtered_y", filteredY)
            putNum("mapped_x", mappedX)
            putNum("mapped_y", mappedY)
            putNum("output_x", outputX)
            putNum("output_y", outputY)
            put("correction_active", correctionActive)
        }

    private fun LocalRawGazeSource.Diagnostics.toJson(): JSONObject =
        JSONObject().apply {
            put("sequence", sequence)
            put("outcome", outcome.name)
            put("capture_timestamp_ns", captureTimestampNs)
            put("submitted_elapsed_ns", submittedElapsedNs)
            put("result_elapsed_ns", resultElapsedNs)
            putNum(
                "submit_to_result_ms",
                if (submittedElapsedNs > 0L && resultElapsedNs >= submittedElapsedNs) {
                    (resultElapsedNs - submittedElapsedNs) / 1_000_000f
                } else {
                    Float.NaN
                },
            )
            put("frame_width", frameWidth)
            put("frame_height", frameHeight)
            put("rotation_degrees", rotationDegrees)
            putNum("eye_1_x", eye1X)
            putNum("eye_1_y", eye1Y)
            putNum("eye_2_x", eye2X)
            putNum("eye_2_y", eye2Y)
            putNum("gaze_x", gazeX)
            putNum("gaze_y", gazeY)
            putNum("eye_1_openness", eye1Openness)
            putNum("eye_2_openness", eye2Openness)
            put("busy_dropped_frames_cumulative", busyDroppedFrames)
        }

    private fun DriftCorrection.toJson(): JSONObject = JSONObject().apply {
        put("coeff_x", JSONArray().apply { coeffX.forEach { put(it) } })
        put("coeff_y", JSONArray().apply { coeffY.forEach { put(it) } })
    }

    private fun JSONObject.putNum(key: String, value: Float) {
        put(key, if (value.isFinite()) value.toDouble() else JSONObject.NULL)
    }

    private fun isoTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())

    private fun flush() {
        try {
            file.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${file.name}", e)
        }
    }

    companion object {
        private const val TAG = "GazeDiagnostics"
    }
}
