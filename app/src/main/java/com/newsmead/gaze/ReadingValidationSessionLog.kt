package com.newsmead.gaze

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only synchronized trace for a structured reading validation run.
 * Each line is one JSON object. Camera images and raw face landmarks are never
 * retained, and detailed source telemetry remains disabled to avoid perturbing
 * the live gaze rate being evaluated.
 */
class ReadingValidationSessionLog(
    context: Context,
    runLabel: String,
    orderVariant: String,
    screenWidthPx: Int,
    screenHeightPx: Int,
    densityDpi: Int,
    rawFeatureMode: String,
    calibrationPointCount: Int,
    calibrationFingerprint: String?,
    driftCorrectionActive: Boolean,
    verticalAlignmentMode: ReadingVerticalAlignmentMode,
) {
    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    private val sessionId = "reading_validation_session_$stamp"
    private val file = File(context.filesDir, "$sessionId.jsonl")
    private val writer: BufferedWriter = file.bufferedWriter()
    private val startedAtMs = System.currentTimeMillis()
    private var finished = false

    init {
        write(
            JSONObject().apply {
                put("record_type", "session_start")
                put("schema_version", SCHEMA_VERSION)
                put("coordinate_space", GazeCoordinateContract.SPACE)
                put("calibration_sha256", calibrationFingerprint ?: JSONObject.NULL)
                put("protocol_version", ReadingValidationProtocol.VERSION)
                put("session_id", sessionId)
                put("run_label", runLabel)
                put("order_variant", orderVariant)
                put("timestamp_ms", startedAtMs)
                put("timestamp_iso", isoTimestamp(startedAtMs))
                put("device_model", Build.MODEL)
                put("device_manufacturer", Build.MANUFACTURER)
                put("android_version", Build.VERSION.RELEASE)
                put("screen_width_px", screenWidthPx)
                put("screen_height_px", screenHeightPx)
                put("density_dpi", densityDpi)
                put("raw_feature_mode", rawFeatureMode)
                put("calibration_point_count", calibrationPointCount)
                put("drift_correction_active", driftCorrectionActive)
                put("vertical_alignment_mode", verticalAlignmentMode.name.lowercase(Locale.ROOT))
                put("vertical_alignment_scope", "session_only_reading_surface_y_gain_bias")
                put("detailed_source_telemetry_enabled", false)
                put("camera_frames_retained", false)
                put("validation_scope", "known_target_spatial_accuracy_only")
                put(
                    "processing_path",
                    "calibrated_gaze -> optional_session_vertical_alignment -> line_aoi -> existing_target_stabilizer",
                )
            },
            flush = true,
        )
    }

    fun logCoordinateFrames(
        phase: ReadingValidationPhase,
        stepId: String,
        rootFrame: GazeCoordinateFrame,
        viewportFrame: GazeCoordinateFrame,
        textFrame: GazeCoordinateFrame,
    ) = event("coordinate_frames") {
        put("phase", phase.name)
        put("step_id", stepId)
        put("root_view_frame", rootFrame.toJson())
        put("viewport_frame", viewportFrame.toJson())
        put("text_view_frame", textFrame.toJson())
    }

    fun logLayout(
        textLength: Int,
        lineCount: Int,
        lineHeightPx: Float,
        textSizePx: Float,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ) = event("layout") {
        put("text_length", textLength)
        put("line_count", lineCount)
        putNum("line_height_px", lineHeightPx)
        putNum("text_size_px", textSizePx)
        put("viewport_width_px", viewportWidthPx)
        put("viewport_height_px", viewportHeightPx)
    }

    fun logProtocolState(
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
        instruction: String,
        expected: ReadingValidationCheckpoint? = null,
    ) = event("protocol_state", flush = true) {
        putContext(phase, stepId, state, expected)
        put("instruction", instruction)
    }

    fun logScroll(
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
        scrollY: Int,
        oldScrollY: Int,
        source: String,
    ) = event("scroll") {
        putContext(phase, stepId, state, null)
        put("scroll_y", scrollY)
        put("old_scroll_y", oldScrollY)
        put("delta_y", scrollY - oldScrollY)
        put("source", source)
    }

    fun logFps(
        fps: Float,
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
    ) = event("fps") {
        putContext(phase, stepId, state, null)
        putNum("fps", fps)
    }

    fun logVerticalReference(
        aggregate: ReadingVerticalReferenceAggregate,
    ) = event("vertical_alignment_reference", flush = true) {
        put("reference_id", aggregate.reference.id)
        putNum("target_x_screen_px", aggregate.reference.targetX)
        putNum("target_y_screen_px", aggregate.reference.targetY)
        put("sample_count", aggregate.sampleCount)
        putNum("observed_median_y_screen_px", aggregate.observedMedianY)
        putNum(
            "signed_error_before_px",
            aggregate.observedMedianY - aggregate.reference.targetY,
        )
    }

    fun logVerticalAlignmentFit(
        mode: ReadingVerticalAlignmentMode,
        fit: ReadingVerticalAlignmentFit,
        applied: Boolean,
    ) = event("vertical_alignment_fit", flush = true) {
        put("mode", mode.name.lowercase(Locale.ROOT))
        put("accepted", fit.accepted)
        put("applied", applied)
        put("reason", fit.reason)
        put("reference_count", fit.referenceCount)
        putNum("gain", fit.gain)
        putNum("intercept_px", fit.interceptPx)
        putNum("max_residual_px", fit.maxResidualPx)
        putNum("leave_one_out_max_px", fit.leaveOneOutMaxPx)
        putNum("median_before_px", fit.medianBeforePx)
        putNum("median_after_px", fit.medianAfterPx)
    }

    fun logGaze(
        timestampMs: Long,
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
        expected: ReadingValidationCheckpoint?,
        gazeX: Float,
        baseGazeY: Float,
        effectiveGazeY: Float,
        baseTarget: TextTarget,
        rawTarget: TextTarget,
        stableTarget: TextTarget,
        scrollY: Int,
        textTopOnScreen: Int,
    ) = event("gaze_sample", timestampMs) {
        putContext(phase, stepId, state, expected)
        putNum("gaze_x_screen_px", gazeX)
        putNum("gaze_y_screen_px", baseGazeY)
        putNum("effective_gaze_y_screen_px", effectiveGazeY)
        putNum("vertical_alignment_delta_px", effectiveGazeY - baseGazeY)
        putTarget("base_target", baseTarget)
        putTarget("raw_target", rawTarget)
        putTarget("stable_target", stableTarget)
        put("scroll_y", scrollY)
        put("text_top_screen_px", textTopOnScreen)
        put("line_count", stableTarget.lineCount.takeIf { it > 0 } ?: rawTarget.lineCount)
    }

    fun finish(outcome: String, summary: ReadingValidationMetrics.Summary) {
        if (finished) return
        event("session_end", flush = true) {
            put("outcome", outcome)
            put("timestamp_iso", isoTimestamp(System.currentTimeMillis()))
            put("measured_sample_count", summary.measuredSamples)
            put("valid_sample_count", summary.validSamples)
            put("completed_checkpoint_count", summary.checkpointCount)
            putNum("valid_fraction", summary.validFraction)
            putNum("exact_line_accuracy", summary.exactLineAccuracy)
            putNum("within_one_line_accuracy", summary.withinOneLineAccuracy)
            putNum("exact_word_accuracy", summary.exactWordAccuracy)
            put("word_measurement_sample_count", summary.wordMeasuredSamples)
            putNum("word_trial_exact_line_accuracy", summary.wordExactLineAccuracy)
            putNum("word_trial_within_one_line_accuracy", summary.wordWithinOneLineAccuracy)
            put("guided_line_measurement_sample_count", summary.lineMeasuredSamples)
            putNum("guided_line_exact_accuracy", summary.guidedLineExactAccuracy)
            putNum("guided_line_within_one_accuracy", summary.guidedLineWithinOneAccuracy)
            putNum("median_absolute_line_error", summary.medianAbsoluteLineError)
            putNum("p95_absolute_line_error", summary.p95AbsoluteLineError)
        }
        finished = true
        writer.close()
        Log.i(TAG, "Finished structured reading log ${file.absolutePath}: $outcome")
    }

    fun flush() {
        if (!finished) writer.flush()
    }

    fun fileName(): String = file.name

    private fun event(
        recordType: String,
        timestampMs: Long = System.currentTimeMillis(),
        flush: Boolean = false,
        block: JSONObject.() -> Unit,
    ) {
        if (finished) return
        write(JSONObject().apply {
            put("record_type", recordType)
            put("session_id", sessionId)
            put("timestamp_ms", timestampMs)
            put("elapsed_ms", (timestampMs - startedAtMs).coerceAtLeast(0L))
            block()
        }, flush)
    }

    private fun JSONObject.putContext(
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
        expected: ReadingValidationCheckpoint?,
    ) {
        put("phase", phase.name)
        put("step_id", stepId)
        put("trial_state", state.name)
        put("expected_checkpoint", expected?.id ?: JSONObject.NULL)
        put("expected_region", expected?.region ?: JSONObject.NULL)
        put("expected_line", expected?.lineIndex ?: JSONObject.NULL)
        put("expected_target_kind", expected?.targetKind?.name ?: JSONObject.NULL)
        put("expected_target_text", expected?.targetText ?: JSONObject.NULL)
        put("expected_target_start", expected?.targetStart ?: JSONObject.NULL)
        put("expected_target_end", expected?.targetEnd ?: JSONObject.NULL)
    }

    private fun JSONObject.putTarget(prefix: String, target: TextTarget) {
        put("${prefix}_valid", target.isValid)
        put("${prefix}_line", target.lineIndex)
        put("${prefix}_word_start", target.wordStart)
        put("${prefix}_word_end", target.wordEnd)
    }

    /** Capture/delivery and explicit gaps accompany coordinates; never contains model inputs. */
    fun logMgazeNetObservation(captureMs: Double?, outputMs: Double, reason: String,
        rawX: Float?, rawY: Float?, arrivals: Long?, busyDrops: Long?) {
        if (finished) return
        write(JSONObject().apply {
            put("record_type","mgazenet_source")
            putNum("capture_elapsed_ms",captureMs)
            putNum("output_elapsed_ms",outputMs)
            putNum("output_age_ms",captureMs?.let { outputMs-it })
            put("reason",reason)
            putNum("raw_screen_x",rawX); putNum("raw_screen_y",rawY)
            putNum("analyzer_arrivals",arrivals); putNum("observed_busy_drops",busyDrops)
            put("operational_expiry_ms",500)
            put("filter","none")
        })
    }

    private fun JSONObject.putNum(key: String, value: Number?) {
        val number = value?.toDouble()
        put(key, if (number != null && number.isFinite()) number else JSONObject.NULL)
    }

    private fun write(value: JSONObject, flush: Boolean = false) {
        try {
            writer.append(value.toString())
            writer.newLine()
            if (flush) writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${file.name}", e)
        }
    }

    private fun isoTimestamp(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(timestampMs))

    companion object {
        const val SCHEMA_VERSION = 6
        private const val TAG = "ReadingValidation"
    }
}
