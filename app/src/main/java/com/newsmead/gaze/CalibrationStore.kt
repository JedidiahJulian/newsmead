package com.newsmead.gaze

import android.content.Context
import android.util.Log
import java.io.File

/** One calibration sample: known screen point (px) to measured local gaze feature. */
data class CalibrationSample(
    val screenX: Float,
    val screenY: Float,
    val gazeX: Float,
    val gazeY: Float,
    val eye1X: Float = Float.NaN,
    val eye1Y: Float = Float.NaN,
    val eye2X: Float = Float.NaN,
    val eye2Y: Float = Float.NaN,
) {
    val hasPerEye: Boolean
        get() = eye1X.isFinite() && eye1Y.isFinite() && eye2X.isFinite() && eye2Y.isFinite()
}

internal object CalibrationCsvCodec {
    const val HEADER = "screen_x,screen_y,gaze_x,gaze_y,eye1_x,eye1_y,eye2_x,eye2_y"

    fun encode(sample: CalibrationSample): String =
        "${sample.screenX},${sample.screenY},${sample.gazeX},${sample.gazeY}," +
            "${sample.eye1X},${sample.eye1Y},${sample.eye2X},${sample.eye2Y}"

    /** Accept both legacy four-column and per-eye eight-column calibrations. */
    fun decode(line: String): CalibrationSample {
        val values = line.split(',')
        require(values.size == 4 || values.size == 8) { "Expected 4 or 8 calibration columns" }
        return CalibrationSample(
            screenX = values[0].toFloat(),
            screenY = values[1].toFloat(),
            gazeX = values[2].toFloat(),
            gazeY = values[3].toFloat(),
            eye1X = values.getOrNull(4)?.toFloat() ?: Float.NaN,
            eye1Y = values.getOrNull(5)?.toFloat() ?: Float.NaN,
            eye2X = values.getOrNull(6)?.toFloat() ?: Float.NaN,
            eye2Y = values.getOrNull(7)?.toFloat() ?: Float.NaN,
        )
    }
}

/** Reads/writes the calibration pairs as a CSV in app-private storage. */
object CalibrationStore {

    private const val TAG = "GazeCalib"
    private const val CSV_NAME = "calibration_16point.csv"
    private const val LEGACY_WIFI_CSV_NAME = "calibration_wifi.csv"
    private const val DRIFT_CSV_NAME = "drift_correction.csv"
    private const val DRIFT_HEADER = "cx0,cx1,cx2,cy0,cy1,cy2,timestamp_ms,pre_median_px,post_median_px"
    private const val RECAL_LOG_NAME = "recalibration_log.csv"
    private const val RECAL_LOG_HEADER = "timestamp_ms,outcome,pre_median_px,post_median_px"

    fun save(context: Context, samples: List<CalibrationSample>) {
        val file = File(context.filesDir, CSV_NAME)
        val text = buildString {
            append(CalibrationCsvCodec.HEADER).append('\n')
            samples.forEach { append(CalibrationCsvCodec.encode(it)).append('\n') }
        }
        file.writeText(text)
        Log.i(TAG, "Wrote ${samples.size} calibration pairs to ${file.absolutePath}")
        // A fresh full calibration supersedes any drift correction fitted on top
        // of the previous one.
        clearDriftCorrection(context)
    }

    /** Returns the saved samples, or null if the file is missing/unparseable. */
    fun load(context: Context): List<CalibrationSample>? {
        val file = File(context.filesDir, CSV_NAME)
        if (!file.exists()) return null
        return read(file)
    }

    // --- Drift correction (fitted from the gaze accuracy test) --------------

    fun saveDriftCorrection(
        context: Context,
        correction: DriftCorrection,
        preMedianPx: Float,
        postMedianPx: Float,
    ) {
        val file = File(context.filesDir, DRIFT_CSV_NAME)
        val cx = correction.coeffX
        val cy = correction.coeffY
        file.writeText(
            DRIFT_HEADER + '\n' +
                "${cx[0]},${cx[1]},${cx[2]},${cy[0]},${cy[1]},${cy[2]}," +
                "${System.currentTimeMillis()},$preMedianPx,$postMedianPx\n",
        )
        Log.i(
            TAG,
            "Saved drift correction (median $preMedianPx px -> est. $postMedianPx px) to ${file.absolutePath}",
        )
    }

    /** Returns the saved drift correction, or null if absent/unparseable. */
    fun loadDriftCorrection(context: Context): DriftCorrection? {
        val file = File(context.filesDir, DRIFT_CSV_NAME)
        if (!file.exists()) return null
        return try {
            val parts = file.readLines()[1].split(",")
            DriftCorrection(
                doubleArrayOf(parts[0].toDouble(), parts[1].toDouble(), parts[2].toDouble()),
                doubleArrayOf(parts[3].toDouble(), parts[4].toDouble(), parts[5].toDouble()),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ${file.name}", e)
            null
        }
    }

    fun clearDriftCorrection(context: Context) {
        val file = File(context.filesDir, DRIFT_CSV_NAME)
        if (file.exists() && file.delete()) {
            Log.i(TAG, "Cleared drift correction (superseded)")
        }
    }

    /**
     * Append-only audit trail of re-calibration actions (apply/revert), one line
     * per action, for thesis data-quality reporting. Separate from the single
     * active drift_correction.csv so history is never overwritten.
     */
    fun appendRecalibrationHistory(
        context: Context,
        outcome: String,
        preMedianPx: Float,
        postMedianPx: Float,
    ) {
        val file = File(context.filesDir, RECAL_LOG_NAME)
        try {
            if (!file.exists()) file.appendText(RECAL_LOG_HEADER + '\n')
            file.appendText("${System.currentTimeMillis()},$outcome,$preMedianPx,$postMedianPx\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append $RECAL_LOG_NAME", e)
        }
    }

    private fun read(file: File): List<CalibrationSample>? {
        if (!file.exists()) return null
        return try {
            file.readLines()
                .drop(1)
                .filter { it.isNotBlank() }
                .map(CalibrationCsvCodec::decode)
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ${file.name}", e)
            null
        }
    }
}
