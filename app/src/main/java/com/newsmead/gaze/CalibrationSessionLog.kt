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
 * Incremental per-session calibration log (docs/calibration-design.md §6).
 * Flushed to disk after every mutation, so a crash or abort mid-session still
 * leaves analyzable data. Written alongside (never instead of) the
 * calibration_16point.csv that the mapper consumes.
 */
class CalibrationSessionLog(
    context: Context,
    screenWidthPx: Int,
    screenHeightPx: Int,
    densityDpi: Int,
    orderSeed: Long,
) {

    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private val file = File(context.filesDir, "calibration_session_$stamp.json")
    private val root = JSONObject()
    private val points = JSONArray()
    private var validation = JSONArray()
    private val flags = JSONArray()

    init {
        root.put("session_id", "calibration_session_$stamp")
        root.put(
            "timestamp_start",
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()),
        )
        root.put("device_model", Build.MODEL)
        root.put("screen_px", JSONArray().put(screenWidthPx).put(screenHeightPx))
        root.put("density_dpi", densityDpi)
        root.put("order_seed", orderSeed)
        root.put("points", points)
        root.put("validation", validation)
        root.put("session_quality_flags", flags)
        flush()
    }

    fun logPoint(
        pointId: Int,
        screenX: Float,
        screenY: Float,
        presentationIndex: Int,
        practice: Boolean,
        driftRepeat: Boolean,
        recaptured: Boolean,
        excluded: Boolean,
        rawSampleCount: Int,
        retainedSampleCount: Int,
        blinkDropped: Long,
        noFace: Long,
        featureX: Float,
        featureY: Float,
        dispersionX: Float,
        dispersionY: Float,
    ) {
        points.put(
            JSONObject().apply {
                put("point_id", pointId)
                putNum("screen_x", screenX)
                putNum("screen_y", screenY)
                put("presentation_index", presentationIndex)
                put("practice", practice)
                put("drift_repeat", driftRepeat)
                put("recaptured", recaptured)
                put("excluded", excluded)
                put("raw_sample_count", rawSampleCount)
                put("retained_sample_count", retainedSampleCount)
                put("blink_dropped", blinkDropped)
                put("no_face", noFace)
                put("aggregated_feature", featureArray(featureX, featureY))
                put("dispersion_feature", featureArray(dispersionX, dispersionY))
            },
        )
        flush()
    }

    fun logDrift(first: FloatArray?, second: FloatArray?, deltaPxPostFit: Float, flagged: Boolean) {
        root.put(
            "drift_check",
            JSONObject().apply {
                put("first_pass_feature", first?.let { featureArray(it[0], it[1]) } ?: JSONObject.NULL)
                put("second_pass_feature", second?.let { featureArray(it[0], it[1]) } ?: JSONObject.NULL)
                putNum("delta_px_postfit", deltaPxPostFit)
                put("flagged_high_drift", flagged)
            },
        )
        flush()
    }

    fun logFit(
        pointsUsed: Int,
        looMedianPx: Float,
        looP95Px: Float,
        looMedianLines: Float,
        worstPointIds: List<Int>,
    ) {
        root.put(
            "fit",
            JSONObject().apply {
                put("points_used", pointsUsed)
                putNum("loo_median_px", looMedianPx)
                putNum("loo_p95_px", looP95Px)
                putNum("loo_median_lines", looMedianLines)
                put("worst_point_ids", JSONArray(worstPointIds))
            },
        )
        flush()
    }

    fun addValidationPoint(screenX: Float, screenY: Float, errorPx: Float, errorLines: Float) {
        validation.put(
            JSONObject().apply {
                putNum("screen_x", screenX)
                putNum("screen_y", screenY)
                putNum("error_px", errorPx)
                putNum("error_lines", errorLines)
            },
        )
        flush()
    }

    /** Clears validation entries for a fresh round after a redo. */
    fun resetValidation() {
        validation = JSONArray()
        root.put("validation", validation)
        flush()
    }

    fun addFlag(flag: String) {
        flags.put(flag)
        flush()
    }

    fun finish(outcome: String) {
        root.put("outcome", outcome)
        root.put(
            "timestamp_end",
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()),
        )
        flush()
    }

    private fun featureArray(x: Float, y: Float): JSONArray =
        JSONArray().apply {
            put(if (x.isFinite()) x.toDouble() else JSONObject.NULL)
            put(if (y.isFinite()) y.toDouble() else JSONObject.NULL)
        }

    /** JSONObject.put(double) throws on NaN/Infinity; store null instead. */
    private fun JSONObject.putNum(key: String, value: Float) {
        put(key, if (value.isFinite()) value.toDouble() else JSONObject.NULL)
    }

    private fun flush() {
        try {
            file.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write session log ${file.name}", e)
        }
    }

    companion object {
        private const val TAG = "GazeCalib"
    }
}
