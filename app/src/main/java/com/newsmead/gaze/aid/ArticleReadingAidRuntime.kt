package com.newsmead.gaze.aid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathConfig
import com.newsmead.gaze.scanpath.ScanpathCoordinateStream
import com.newsmead.gaze.scanpath.ScanpathEngine
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathFixation
import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathSample
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.mgazenet.MgazeNetReadingSmoother
import com.newsmead.gaze.mgazenet.MgazeNetBlinkGate
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Main-thread integration. The bounded trace is exported only by a reader's menu action. */
class ArticleReadingAidRuntime(
    private val body: TextView,
    private val wordDock: ReadingAidWordView,
    private val overlay: ReadingAidOverlayView,
    val config: ReadingAidPolicyConfig = ReadingAidPolicyConfig.experimentalPrototype(),
    preferences: ReaderAidPreferences = ReaderAidPreferences(),
    private val inputSource: ReadingAidInputSource = ReadingAidInputSource.LIVE_MGAZENET,
    private val onDecision: (ReadingAidDecision) -> Unit = {},
    private val onRenderReceipt: (ReadingAidAction, Boolean, String, Double) -> Unit = { _, _, _, _ -> },
    private val renderAids: Boolean = true,
    private val definitionPopup: ReadingAidDefinitionPopup? = null,
    private val onConsultationChanged: (Boolean, Double, String) -> Unit = { _, _, _ -> },
    val coordinateStream: ScanpathCoordinateStream = ScanpathCoordinateStream.CALIBRATED_RAW,
    private val onFocusEpisode: (ReadingAidFocusEpisode) -> Unit = {},
    private val onDefinitionOffer: (ReadingAidAction, String, Double) -> Unit = { _, _, _ -> },
    private val blinkFilterEnabled: Boolean = false,
    private val motionCompensatedAids: Boolean = false,
    /** Optional separately measured screen-pixel error bound for this calibration/device. */
    private val measuredLocationUncertainty: ReadingLocationUncertainty? = null,
) {
    val scanpathConfig = ScanpathConfig(returnSweepStartFraction = 0.25, returnSweepEndFraction = 0.75,
        wordResidenceMs = config.wordMinObservedMs, lineResidenceMs = config.lineMinObservedMs,
        cycleWindowMs = config.cycleWindowMs, progressionWindowMs = 1000.0,
        lineProgressionMaxUnitsPerS = 0.5, progressionMinObservedFraction = 0.5)
    private val trace = ReadingAidTrace()
    private val policy = ReadingAidPolicy(config, preferences, scanpathConfig,
        onFocusEpisode = { episode -> trace.focusEpisode(episode); onFocusEpisode(episode) })
    val preferences get() = policy.preferences
    private val calibrationFingerprint = if (inputSource == ReadingAidInputSource.LIVE_MGAZENET)
        MgazeNetCalibrationStore(body.context).fingerprint() else null
    private val handler = Handler(Looper.getMainLooper())
    private var snapshot: ScanpathGeometry? = null
    private var latestFixation: ScanpathFixation? = null
    private var latestCapture = Double.NEGATIVE_INFINITY
    private var suspended = false
    private var closed = false
    private var consulting = false
    private var preserveFocusOnResume = false
    private var lastFocusStatus: String? = null
    private var consultationAnchor: ReadingAidAnchor? = null
    private var dismissedDefinitionAnchor: ReadingAidAnchor? = null
    private var unsupportedDefinitionActionId: String? = null
    private var context = ReadingAidContext(0, "", 0, null, null, null,
        elapsedMs(), null, null, false, focusTextSizeDp = renderedFontDp())
    private val guideResolver = ReadingGuideResolver()
    private val pulse = object : Runnable {
        override fun run() {
            if (closed) return
            tick()
            handler.postDelayed(this, 100)
        }
    }

    init {
        require(!motionCompensatedAids || coordinateStream == ScanpathCoordinateStream.ARTICLE_SMOOTHED)
        overlay.onFocusScrollRender = { source, displayedSnapshot, top, bottom, at ->
            // Presentation continuity is not new gaze support or a detector decision.
            trace.record("focus_scroll_presentation", at, JSONObject()
                .put("source_action_id", source.id)
                .put("source_geometry_snapshot_id", source.anchor.geometrySnapshotId)
                .put("presentation_geometry_snapshot_id", displayedSnapshot)
                .put("focus_style", source.focusStyle?.name ?: JSONObject.NULL)
                .put("clear_top_screen_px", top).put("clear_bottom_screen_px", bottom)
                .put("position_basis", "viewport_hold_during_translation")
                .put("new_gaze_evidence", false))
        }
        overlay.onRender = { action, drawn, reason, at ->
            trace.render(action, drawn, reason, at)
            onRenderReceipt(action, drawn, reason, at)
        }
        wordDock.onRender = overlay.onRender
        definitionPopup?.onRender = overlay.onRender
        definitionPopup?.onOfferEvent = offerEvent@{ action, event, at ->
            if (closed) return@offerEvent
            if (event == "expired") dismissedDefinitionAnchor = action.anchor
            trace.record("definition_offer", at, JSONObject().put("event", event)
                .put("action_id", action.id).put("at_ms", at)
                .put("word_start_utf16", action.anchor.wordStartUtf16 ?: JSONObject.NULL)
                .put("word_end_utf16", action.anchor.wordEndUtf16 ?: JSONObject.NULL)
                .put("user_requested_panel", event == "accepted"))
            onDefinitionOffer(action, event, at)
        }
        definitionPopup?.onConsultationChanged = { active, at, reason ->
            overlay.clearFocusScrollHold()
            consulting = active
            if (active) consultationAnchor = definitionPopup?.action?.anchor
            else { dismissedDefinitionAnchor = consultationAnchor; consultationAnchor = null }
            context = context.copy(nowMs = at, validLiveInput = false, rawSample = null,
                focusInputAllowed = false, bookmarkInputAllowed = false)
            latestFixation = null
            trace.record("definition_consultation", at, JSONObject().put("active", active).put("reason", reason)
                .put("anchor_start_utf16", (consultationAnchor ?: dismissedDefinitionAnchor)?.wordStartUtf16 ?: JSONObject.NULL)
                .put("anchor_end_utf16", (consultationAnchor ?: dismissedDefinitionAnchor)?.wordEndUtf16 ?: JSONObject.NULL))
            // Clear article interpretation without dismissing the consultation itself.
            val resets = policy.reset(context, preserveCompletedFocus = true)
            trace.context(context)
            resets.forEach { trace.decision(it); onDecision(it) }
            recordFocusStatus()
            overlay.setActions(body, snapshot, context.generation, emptyList())
            wordDock.showAction(null, body)
            onConsultationChanged(active, at, reason)
        }
        trace.record("started", elapsedMs(), configuration())
        updateDockVisibility()
        handler.post(pulse)
    }

    fun geometry(generation: Int, value: ScanpathGeometry?) {
        checkMain()
        if (closed) return
        val previous = snapshot
        if (motionCompensatedAids && previous != null && value != null &&
            previous.snapshotId == value.snapshotId && context.generation == generation.toLong() &&
            ReadingFocusMotion.compatibleTranslation(previous, value)) {
            // Same content layout, new display position. Preserve accumulated evidence
            // and anchor IDs while rendering each aid against the current screen frame.
            snapshot = value
            context = context.copy(geometry = value, nowMs = elapsedMs(),
                targetX = context.rawSample?.motionCompensation?.let { it.contentX + value.textOriginX }
                    ?: context.targetX)
            decisions(emptyList())
            return
        }
        definitionPopup?.clear("geometry_changed")
        dismissedDefinitionAnchor = null
        snapshot = value
        if (context.generation != generation.toLong()) latestFixation = null
        context = context.copy(generation = generation.toLong(), geometrySnapshotId = value?.snapshotId ?: "",
            lineCount = value?.lines?.size ?: 0, targetLine = null, targetWordStartUtf16 = null,
            targetWordEndUtf16 = null, nowMs = elapsedMs(), validLiveInput = false,
            geometry = value, targetX = null, guideRange = null, focusTextSizeDp = renderedFontDp())
        trace.record("geometry", context.nowMs, JSONObject().put("generation", generation)
            .put("geometry_snapshot_id", value?.snapshotId ?: JSONObject.NULL)
            .put("body_sha256", sha256(body.text.toString())).put("line_count", value?.lines?.size ?: 0)
            .put("text_size_px", body.textSize).put("line_height_px", body.lineHeight)
            .put("line_spacing_extra_px", body.lineSpacingExtra)
            .put("line_spacing_multiplier", body.lineSpacingMultiplier)
            .put("focus_text_size_dp", context.focusTextSizeDp))
        decisions(policy.reset(context, preserveCompletedFocus = true))
    }

    fun input(generation: Int, sample: ScanpathSample) {
        checkMain()
        if (closed) return
        val now = elapsedMs()
        val capture = sample.captureMs
        val delivery = sample.deliveryMs
        val ordered = capture != null && capture.isFinite() && capture > latestCapture
        if (capture != null && capture.isFinite()) latestCapture = maxOf(latestCapture, capture)
        val matchingStream = sample.coordinateStream == coordinateStream
        val matchingAnalysis = (sample.motionCompensation?.let {
            motionCompensatedAids && it.validFor(sample) } ?: true) &&
            (!motionCompensatedAids || !sample.eligible || sample.motionCompensation != null)
        val eyeExcluded = sample.exclusion == "blink" || sample.exclusion == "eye_state_unavailable"
        val selectedPointValid = sample.processingX?.isFinite() == true && sample.processingY?.isFinite() == true
        val valid = !suspended && !consulting && matchingStream && matchingAnalysis && !eyeExcluded && selectedPointValid &&
            sample.eligible && ordered && delivery != null && delivery.isFinite() &&
            delivery >= capture!! && delivery <= now && sample.geometrySnapshotId == snapshot?.snapshotId
        val interpretationAllowed = !suspended && !consulting && matchingStream && matchingAnalysis &&
            sample.geometrySnapshotId == snapshot?.snapshotId &&
            (sample.exclusion == null || sample.exclusion == "outside_visible_text" ||
                sample.exclusion !in listOf("layout_unavailable", "geometry_unsettled") &&
                !sample.exclusion.startsWith("definition_") &&
                listOf("scroll", "calibration", "control", "pause", "restart", "stop", "camera", "provider_failed", "geometry", "text_change")
                    .none { sample.exclusion.contains(it) })
        // Only translation/unsettled geometry may retain the focus presentation.
        // Menus, consultation, calibration and provider/screen stops must remove it.
        if (!matchingStream || !matchingAnalysis || sample.exclusion?.let { reason ->
                reason != "geometry_unsettled" && reason != "scroll" &&
                    (reason == "layout_unavailable" || reason.startsWith("definition_") ||
                        listOf("calibration", "control", "pause", "restart", "stop", "camera", "provider_failed", "text_change")
                            .any { reason.contains(it) })
            } == true) overlay.clearFocusScrollHold()
        context = ReadingAidContext(generation.toLong(), snapshot?.snapshotId ?: "",
            snapshot?.lines?.size ?: 0, sample.line, sample.wordStartUtf16, sample.wordEndUtf16,
            now, capture, delivery, valid, geometry = snapshot,
            targetX = sample.motionCompensation?.let { it.contentX + (snapshot?.textOriginX ?: 0.0) }
                ?: sample.processingX,
            rawSample = sample, bookmarkInputAllowed = interpretationAllowed,
            guideRange = if (valid) guideResolver.resolve(body.text.toString(), snapshot, sample.line) else null,
            focusInputAllowed = interpretationAllowed, focusTextSizeDp = renderedFontDp(),
            locationUncertainty = measuredLocationUncertainty,
            targetWordBounds = wordBounds(sample.line, sample.wordStartUtf16, sample.wordEndUtf16))
        if (valid) definitionPopup?.observeReadingTarget(generation.toLong(), sample.wordStartUtf16, sample.wordEndUtf16)
        val dismissed = dismissedDefinitionAnchor
        if (valid && dismissed != null && (dismissed.generation != generation.toLong() ||
            dismissed.wordStartUtf16 != sample.wordStartUtf16 || dismissed.wordEndUtf16 != sample.wordEndUtf16))
            dismissedDefinitionAnchor = null
        if (sample.exclusion?.startsWith("definition_") == true) trace.record("consultation_input", now,
            JSONObject().put("capture_ms", capture ?: JSONObject.NULL).put("delivery_ms", delivery ?: JSONObject.NULL)
                .put("raw_x_px", sample.rawX?.takeIf { it.isFinite() } ?: JSONObject.NULL)
                .put("raw_y_px", sample.rawY?.takeIf { it.isFinite() } ?: JSONObject.NULL)
                .put("coordinate_stream", sample.coordinateStream.id)
                .put("processing_x_px", sample.processingX?.takeIf { it.isFinite() } ?: JSONObject.NULL)
                .put("processing_y_px", sample.processingY?.takeIf { it.isFinite() } ?: JSONObject.NULL)
                .put("exclusion", sample.exclusion).put("article_eligible", false))
        // Clear before an invalid observation can flush the earlier candidate and its events.
        if (!valid || config.evidenceAlignedV5) decisions(policy.onObservation(null, context))
    }

    fun fixation(generation: Int, value: ScanpathFixation) {
        trace.fixation(generation, value)
        if (!closed && !consulting && generation.toLong() == context.generation) latestFixation = value
    }

    fun event(generation: Int, value: ScanpathEvent) {
        checkMain()
        if (closed || suspended || consulting || generation.toLong() != context.generation) return
        trace.event(generation, value)
        context = context.copy(nowMs = elapsedMs())
        decisions(policy.onEvent(value, latestFixation, context))
    }

    fun candidate(generation: Int, value: ScanpathCandidateEvidence?) {
        checkMain()
        if (closed || suspended || consulting || generation.toLong() != context.generation) return
        trace.candidate(generation, value)
        context = context.copy(nowMs = elapsedMs())
        decisions(policy.onObservation(value, context))
    }

    fun updatePreferences(value: ReaderAidPreferences) {
        checkMain()
        overlay.clearFocusScrollHold()
        if (!value.enabled || !value.wordDefinition || value.maxStrength == 0.0 ||
            (!value.adaptiveStrength && value.manualStrength == 0.0)) definitionPopup?.clear("reader_controls_changed")
        context = context.copy(nowMs = elapsedMs())
        decisions(policy.updatePreferences(value, context))
        trace.record("preferences", context.nowMs, preferenceJson())
        updateDockVisibility()
    }

    fun suspend(reason: String) {
        checkMain()
        if (closed) return
        suspended = true
        overlay.clearFocusScrollHold()
        preserveFocusOnResume = reason == "reader_controls"
        definitionPopup?.clear(reason)
        context = context.copy(nowMs = elapsedMs(), validLiveInput = false, rawSample = null,
            focusInputAllowed = false, bookmarkInputAllowed = false)
        decisions(policy.reset(context, preserveCompletedFocus = preserveFocusOnResume))
        trace.record("suspended", context.nowMs, JSONObject().put("reason", reason))
        updateDockVisibility()
    }

    fun resume() {
        checkMain()
        if (closed) return
        suspended = false
        context = context.copy(nowMs = elapsedMs(), validLiveInput = false)
        decisions(policy.reset(context, preserveCompletedFocus = preserveFocusOnResume))
        preserveFocusOnResume = false
        updateDockVisibility()
    }

    fun close(reason: String) {
        if (closed) return
        suspend(reason)
        closed = true
        handler.removeCallbacksAndMessages(null)
    }

    fun tick() {
        checkMain()
        if (closed) return
        context = context.copy(nowMs = elapsedMs())
        decisions(policy.tick(context))
    }

    fun export(context: Context): File = trace.export(context, configuration())
    fun configurationSnapshot(): JSONObject = configuration()
    /** Explicit navigation is neither reader regression nor new evidence of distraction. */
    fun autoScrollBoundary(moving: Boolean, atMs: Double, detail: JSONObject) {
        checkMain()
        if (closed) return
        if (motionCompensatedAids) {
            autoScrollEvent(if (moving) "start" else "end", atMs,
                JSONObject(detail.toString()).put("aid_input_mode", "auto_scroll_content_frame_v1"))
            return
        }
        context = context.copy(nowMs = atMs, validLiveInput = false, rawSample = null,
            focusInputAllowed = false, bookmarkInputAllowed = false)
        latestFixation = null
        decisions(policy.reset(context, preserveCompletedFocus = true))
        autoScrollEvent(if (moving) "start" else "end", atMs, detail)
    }
    fun autoScrollEvent(event: String, atMs: Double, detail: JSONObject) {
        checkMain()
        if (!closed) trace.record("auto_scroll_$event", atMs, detail)
    }
    private fun renderedFontDp(): Double =
        body.textSize.toDouble() / body.resources.displayMetrics.density.toDouble()

    /** Layout measurement occurs at input, never from an appearance-span draw callback. */
    private fun wordBounds(line: Int?, start: Int?, end: Int?): com.newsmead.gaze.scanpath.ScanpathRect? {
        val g = snapshot ?: return null
        val layout = body.layout ?: return null
        if (line == null || start == null || end == null || start < 0 || end <= start ||
            end > body.text.length || line !in g.lines.indices ||
            layout.getLineForOffset(start) != line || layout.getLineForOffset(end - 1) != line) return null
        val a = layout.getPrimaryHorizontal(start).toDouble() + g.textOriginX
        val b = layout.getPrimaryHorizontal(end).toDouble() + g.textOriginX
        val row = g.lines[line]
        return com.newsmead.gaze.scanpath.ScanpathRect(minOf(a, b), g.textOriginY + row.top,
            maxOf(a, b), g.textOriginY + row.bottom)
    }
    fun activeActions(): List<ReadingAidAction> = policy.activeActions()

    private fun decisions(values: List<ReadingAidDecision>) {
        trace.context(context)
        values.forEach { trace.decision(it); onDecision(it) }
        recordFocusStatus()
        val actions = if (closed || suspended || consulting || !renderAids) emptyList() else policy.activeActions()
        overlay.allowFocusScrollHold = (config.configId == "DEFINITION_PROFILE_V4" || config.evidenceAlignedV5) && renderAids &&
            !closed && !suspended && !consulting && preferences.enabled && preferences.focusRegion &&
            preferences.maxStrength > 0.0 && (preferences.adaptiveStrength || preferences.manualStrength > 0.0)
        overlay.setActions(body, snapshot, context.generation, actions)
        wordDock.showAction(actions.firstOrNull { it.aidType == ReadingAidType.WORD_MAGNIFIER }, body)
        if (!consulting) {
            val definition = actions.firstOrNull { it.aidType == ReadingAidType.WORD_DEFINITION }
                ?.takeUnless { it.anchor == dismissedDefinitionAnchor }
            if (definition != null && definitionPopup?.languageSupported == false) {
                if (unsupportedDefinitionActionId != definition.id) {
                    unsupportedDefinitionActionId = definition.id
                    trace.render(definition, false, "dictionary_language_unsupported", context.nowMs)
                    onRenderReceipt(definition, false, "dictionary_language_unsupported", context.nowMs)
                }
            } else definitionPopup?.offerAction(definition)
        }
    }

    private fun recordFocusStatus() {
        val detail = JSONObject(policy.focusDiagnostics(context.nowMs))
        val signature = detail.toString()
        if (signature != lastFocusStatus) {
            lastFocusStatus = signature
            trace.record("focus_activation_state", context.nowMs, detail)
        }
    }

    private fun updateDockVisibility() {
        wordDock.visibility = if (config.wordAidType == ReadingAidType.WORD_MAGNIFIER && renderAids && !closed && !suspended && preferences.enabled && preferences.wordMagnifier)
            View.VISIBLE else View.GONE
    }

    private fun preferenceJson() = JSONObject().put("enabled", preferences.enabled)
        .put("auto_scroll", preferences.autoScroll)
        .put("line_guide", preferences.lineGuide).put("line_start_cue", preferences.lineStartCue)
        .put("guide_style", preferences.guideStyle.name)
        .put("word_magnifier", preferences.wordMagnifier).put("focus_region", preferences.focusRegion)
        .put("focus_style", preferences.focusStyle.name)
        .put("word_definition", preferences.wordDefinition)
        .put("adaptive_strength", preferences.adaptiveStrength).put("max_strength", preferences.maxStrength)
        .put("manual_strength", preferences.manualStrength)

    private fun configuration(): JSONObject {
        val values = JSONObject().put("config_id", config.configId).put("condition_id", config.conditionId)
            .put("coordinate_stream", coordinateStream.id)
            .put("processing_pipeline_version", "SELECTED_PROVIDER_COORDINATES_V1")
            .put("motion_compensated_aids", motionCompensatedAids)
            .put("aid_analysis_frame", if (motionCompensatedAids) "auto_scroll_content_frame_v1" else "screen")
            .put("motion_cluster_interpretation", if (motionCompensatedAids)
                "experimental_content_residence_proxy_not_physiological_fixation" else JSONObject.NULL)
            .put("motion_geometry_max_age_ms", if (motionCompensatedAids) 100 else JSONObject.NULL)
            .put("blink_filter_enabled", blinkFilterEnabled)
            .put("blink_filter_version", if (blinkFilterEnabled) MgazeNetBlinkGate.VERSION else JSONObject.NULL)
            .put("blink_close_aspect_ratio", if (blinkFilterEnabled) MgazeNetBlinkGate.CLOSE_THRESHOLD else JSONObject.NULL)
            .put("blink_reopen_aspect_ratio", if (blinkFilterEnabled) MgazeNetBlinkGate.OPEN_THRESHOLD else JSONObject.NULL)
            .put("blink_reopen_fresh_frames", if (blinkFilterEnabled) MgazeNetBlinkGate.REOPEN_FRAMES else JSONObject.NULL)
            .put("blink_geometry", if (blinkFilterEnabled) "each_eye_perpendicular_lid_gap_divided_by_pixel_corner_width" else JSONObject.NULL)
            .put("blink_reason_means", if (blinkFilterEnabled) "closure_or_reopening_or_initial_opening_guard_not_blink_event_count" else JSONObject.NULL)
            .put("smoothing", coordinateStream == ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            .put("smoothing_filter", if (coordinateStream == ScanpathCoordinateStream.ARTICLE_SMOOTHED) "one_euro" else JSONObject.NULL)
            .put("smoothing_min_cutoff", if (coordinateStream == ScanpathCoordinateStream.ARTICLE_SMOOTHED) MgazeNetReadingSmoother.MIN_CUTOFF else JSONObject.NULL)
            .put("smoothing_beta", if (coordinateStream == ScanpathCoordinateStream.ARTICLE_SMOOTHED) MgazeNetReadingSmoother.BETA else JSONObject.NULL)
            .put("automatic_rules_enabled", config.automaticRulesEnabled).put("word_min_refixations", config.wordMinRefixations)
            .put("word_aid_type", config.wordAidType.name).put("definition_min_observed_ms", config.definitionMinObservedMs)
            .put("definition_presentation_mode", "above_word_offer_then_user_requested_panel")
            .put("definition_offer_ttl_ms", ReadingAidDefinitionPopup.OFFER_TTL_MS)
            .put("definition_offer_style", ReadingAidDefinitionOffer.STYLE_ID)
            .put("definition_offer_tip_gap_dp", ReadingAidDefinitionOffer.TIP_GAP_DP)
            .put("definition_offer_vertical_anchor", "word_ink_top_from_layout_baseline")
            .put("definition_offer_opens_panel_automatically", false)
            .put("definition_offer_pauses_measurement", false)
            .put("definition_render_receipts", "lookup_offer_and_definition_panel_are_separate_phases")
            .put("dictionary_adapter_version", DictionaryDefinitionRepository.ONLINE_ROUTE_VERSION)
            .put("word_min_observed_ms", config.wordMinObservedMs).put("word_saturation_ms", config.wordSaturationMs)
            .put("line_min_observed_ms", config.lineMinObservedMs).put("line_saturation_ms", config.lineSaturationMs)
            .put("cycle_window_ms", config.cycleWindowMs)
            .put("aid_ttl_ms", config.aidTtlMs).put("cooldown_ms", config.cooldownMs)
            .put("max_evidence_age_ms", config.maxEvidenceAgeMs).put("min_adaptive_strength", config.minAdaptiveStrength)
            .put("guide_selector_version", if (config.evidenceAlignedV5) "STABILIZED_SINGLE_LINE_V5" else "TEXT_LINE_CONTEXT_V1")
            .put("guide_ambiguity_fallback", if (config.evidenceAlignedV5) "single_stabilized_estimated_row_unvalidated" else "containing_paragraph_or_abstain")
            .put("guide_measured_uncertainty_available", context.locationUncertainty != null)
            .put("guide_color_rgb", if (config.configId == "DEFINITION_PROFILE_V4" || config.evidenceAlignedV5) "#808080" else "#F4D35E")
            .put("guide_alpha_scale", if (config.evidenceAlignedV5) 255 else 80)
            .put("guide_opacities", if (config.evidenceAlignedV5) org.json.JSONArray(listOf(.08, .13, .19)) else JSONObject.NULL)
            .put("guide_entry_ms", if (config.evidenceAlignedV5) 150 else JSONObject.NULL)
            .put("guide_loss_grace_ms", if (config.evidenceAlignedV5) 750 else JSONObject.NULL)
            .put("guide_exit_ms", if (config.evidenceAlignedV5) 300 else JSONObject.NULL)
            .put("word_confidence_basis", if (config.evidenceAlignedV5) "consistent_exact_mapping_and_word_bounds_not_validated_gaze_accuracy" else "extractor_prefix")
            .put("guide_layer", if (config.configId == "DEFINITION_PROFILE_V4" || config.evidenceAlignedV5) "text_background_span" else "canvas_overlay")
            .put("max_magnification_scale", config.maxMagnificationScale).put("focus_context_radius_lines", config.focusContextRadiusLines)
            .put("continuation_start_fraction", config.continuationStartFraction)
            .put("continuation_end_fraction", config.continuationEndFraction)
            .put("continuation_window_ms", config.continuationWindowMs)
            .put("line_start_aid_type", config.lineStartAidType.name)
            .put("line_start_marker_rule", if (config.lineStartAidType == ReadingAidType.READING_BOOKMARK)
                "A2_LAST_READ_LINE_BOOKMARK_V2" else "A2_CONTINUATION_MISMATCH_V1")
            .put("line_start_marker_form", if (config.lineStartAidType == ReadingAidType.READING_BOOKMARK)
                "LAST_READ_LINE_BOOKMARK_V1" else LineStartMarkerAppearance.FORM_ID)
            .put("bookmark_outside_min_ms", if (config.evidenceAlignedV5) config.bookmarkTrackingLossMs else scanpathConfig.minFixationMs)
            .put("bookmark_outside_min_samples", scanpathConfig.minFixationSamples)
            .put("bookmark_outside_max_gap_ms", if (config.evidenceAlignedV5) 250.0 else scanpathConfig.maxSampleGapMs)
            .put("bookmark_expiry_mode", "until_supported_reading_or_invalidation")
            .put("bookmark_min_visible_ms", if (config.evidenceAlignedV5) 1500 else JSONObject.NULL)
            .put("bookmark_return_support_ms", if (config.evidenceAlignedV5) 1000 else JSONObject.NULL)
            .put("bookmark_elsewhere_support_ms", if (config.evidenceAlignedV5) 1500 else JSONObject.NULL)
            .put("bookmark_max_age_ms", if (config.evidenceAlignedV5) 30000 else JSONObject.NULL)
            .put("bookmark_outside_geometry", "outside_visible_article_rect")
            .put("bookmark_tracking_loss_ms", config.bookmarkTrackingLossMs)
            .put("bookmark_tracking_loss_time_basis", "delivery_elapsed_time")
            .put("bookmark_tracking_loss_interpretation", "unavailable_gaze_not_attention_diagnosis")
            .put("bookmark_line_jump_trigger", !config.evidenceAlignedV5)
            .put("bookmark_source_requires_mapper_agreement", config.evidenceAlignedV5)
            .put("focus_rule_version", if (config.evidenceAlignedV5) "BROAD_REREAD_PLUS_MIGRATION_ROUTES_V5" else "FONT_AWARE_LINE_VISITS_V5_UNCHANGED_RETURN_PRESENCE")
            .put("focus_broad_reread_order", if (config.evidenceAlignedV5) "A_B_A_C_D:B>A;A<C<D<=B" else JSONObject.NULL)
            .put("focus_broad_reread_window_ms", if (config.evidenceAlignedV5) 15000 else JSONObject.NULL)
            .put("focus_font_rule_version", ReadingFocusTypography.VERSION)
            .put("focus_text_size_dp", renderedFontDp())
            .put("focus_font_size_basis", "rendered_font_pixels_divided_by_display_density_including_system_font_scale")
            .put("focus_font_rules", org.json.JSONArray(listOf(
                ReadingFocusTypography.COMPACT, ReadingFocusTypography.SMALL, ReadingFocusTypography.STANDARD).map {
                JSONObject().put("id", it.id).put("required_alternations", it.requiredAlternations)
                    .put("minimum_line_visit_ms", maxOf(scanpathConfig.minFixationMs, it.extraMinVisitMs))
            }))
            .put("focus_interruption_min_episodes", config.focusInterruptionMinEpisodes)
            .put("focus_interruption_window_ms", config.focusInterruptionWindowMs)
            .put("focus_loss_min_ms", config.focusLossMinMs)
            .put("focus_recovery_fade_ms", config.focusRecoveryFadeMs)
            .put("focus_recovery_hold_ms", config.focusRecoveryHoldMs)
            .put("focus_tracking_grace_ms", config.focusTrackingGraceMs)
            .put("focus_grace_time_basis", "last_supported_or_article_presence_delivery_elapsed_time")
            .put("focus_interruption_count_unit", "qualified_loss_and_confirmed_article_presence_return")
            .put("focus_return_presence_ms", config.focusReturnPresenceMs)
            .put("focus_return_min_samples", config.focusReturnMinSamples)
            .put("focus_return_max_gap_ms", if (config.evidenceAlignedV5) 250.0 else scanpathConfig.maxSampleGapMs)
            .put("focus_return_requires_line_or_word", false)
            .put("focus_base_strength", ReadingAidPolicyConfig.FOCUS_STRENGTH_STEP)
            .put("focus_strength_per_extra_episode", ReadingAidPolicyConfig.FOCUS_STRENGTH_STEP)
            .put("focus_activation_strengths", org.json.JSONArray(listOf(ReadingAidPolicyConfig.FOCUS_STRENGTH_STEP, 1.0)))
            .put("focus_strength_schedule", "two_activation_strengths_with_reading_time_hold_then_fade")
            .put("focus_history_break_policy", "same_article_geometry_consultation_preserves_completed_summaries_clears_pending_and_anchors")
            .put("focus_support_geometry", "visible_article_x_and_supported_line_y_without_word_assignment")
            .put("focus_alternation_window_ms", config.focusAlternationWindowMs)
            .put("focus_alternation_count_unit", "four_nonoverlapping_supported_line_visits_including_ongoing_fourth")
            .put("focus_clear_window_max_viewport_fraction", JSONObject.NULL)
            .put("focus_clear_window_lines", 2 * config.focusContextRadiusLines + 1)
            .put("focus_clear_window_min_lines", if (config.configId == "DEFINITION_PROFILE_V4" || config.evidenceAlignedV5) 4 else JSONObject.NULL)
            .put("focus_clear_window_mode", if (config.configId == "DEFINITION_PROFILE_V4" || config.evidenceAlignedV5)
                "five_preferred_four_if_five_leaves_no_surroundings" else "legacy_centered_radius")
            .put("focus_veil_horizontal_bounds", if (config.configId == "DEFINITION_PROFILE_V4" || config.evidenceAlignedV5)
                "full_visible_overlay_width" else "visible_text_bounds")
            .put("focus_form", if (config.configId == "DEFINITION_PROFILE_V4" || config.evidenceAlignedV5) "CONTEXT_FOCUS_REGION_V4" else "CONTEXT_FOCUS_REGION_V1")
            .put("focus_mask_color", if (config.configId == "DEFINITION_PROFILE_V4" || config.evidenceAlignedV5) "per_action_focus_style" else "#000000")
            .put("focus_appearance_selector_version", "FOCUS_MASK_STYLE_V4_VISIBLE_SURROUNDINGS")
            .put("focus_max_surrounding_opacity", ReadingFocusAppearance.MAX_SURROUNDING_OPACITY)
            .put("focus_dark_onset_ms", ReadingFocusOnset.ONSET_MS)
            .put("focus_dark_onset_curve", "smoothstep_presentation_only")
            .put("focus_default_style", ReadingFocusStyle.FADE_TEXT.name)
            .put("focus_mask_opacity_at_half_strength", if (preferences.focusStyle == ReadingFocusStyle.DARK_SURROUNDINGS)
                ReadingFocusAppearance.BASE_DARK_OPACITY else ReadingFocusAppearance.BASE_BACKGROUND_OPACITY)
            .put("focus_mask_opacity_at_full_strength", if (preferences.focusStyle == ReadingFocusStyle.DARK_SURROUNDINGS)
                ReadingFocusAppearance.MAX_DARK_OPACITY else ReadingFocusAppearance.MAX_BACKGROUND_OPACITY)
            .put("focus_mask_opacities_by_style", JSONObject()
                .put(ReadingFocusStyle.FADE_TEXT.name, JSONObject()
                    .put("half_strength", ReadingFocusAppearance.BASE_BACKGROUND_OPACITY)
                    .put("full_strength", ReadingFocusAppearance.MAX_BACKGROUND_OPACITY))
                .put(ReadingFocusStyle.DARK_SURROUNDINGS.name, JSONObject()
                    .put("half_strength", ReadingFocusAppearance.BASE_DARK_OPACITY)
                    .put("full_strength", ReadingFocusAppearance.MAX_DARK_OPACITY)))
            .put("focus_motion_ms", ReadingFocusMotion.TRANSITION_MS)
            .put("focus_scroll_presentation_grace_ms", ReadingFocusMotion.SCROLL_HOLD_MS)
            .put("focus_scroll_position_basis", "viewport_hold_during_translation_not_new_gaze_evidence")
            .put("focus_steady_reading_time_basis", if (config.evidenceAlignedV5) "fresh_stabilized_supported_capture_tails_max_gap_250ms" else "eligible_ordered_capture_deltas_within_max_sample_gap")
            .put("focus_all_styles_onset_ms", if (config.evidenceAlignedV5) ReadingFocusOnset.ONSET_MS else JSONObject.NULL)
            .put("focus_interpretation", "experimental_support_rule_not_distraction_probability")
            .put("line_start_marker_width_dp", LineStartMarkerAppearance.WIDTH_DP.toDouble())
            .put("line_start_marker_alpha", LineStartMarkerAppearance.ALPHA)
            .put("line_start_marker_strength_mode", "fixed")
            .put("return_sweep_start_fraction", scanpathConfig.returnSweepStartFraction)
            .put("return_sweep_end_fraction", scanpathConfig.returnSweepEndFraction)
            .put("progression_window_ms", scanpathConfig.progressionWindowMs)
            .put("line_progression_max_units_per_s", scanpathConfig.lineProgressionMaxUnitsPerS)
            .put("progression_min_observed_fraction", scanpathConfig.progressionMinObservedFraction)
            .put("dispersion_lines", scanpathConfig.dispersionLines).put("min_fixation_ms", scanpathConfig.minFixationMs)
            .put("min_fixation_samples", scanpathConfig.minFixationSamples).put("max_pending_samples", scanpathConfig.maxPendingSamples)
            .put("max_sample_gap_ms", scanpathConfig.maxSampleGapMs).put("max_transition_gap_ms", scanpathConfig.maxTransitionGapMs)
            .put("assignment_agreement", scanpathConfig.assignmentAgreement)
        val digest = MessageDigest.getInstance("SHA-256").digest(values.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return JSONObject().put("policy_version", 16).put("extractor_version", ScanpathEngine.EXTRACTOR_VERSION)
            .put("classifier_version", ScanpathEngine.CLASSIFIER_VERSION)
            .put("provisional_evidence_version", ScanpathEngine.PROVISIONAL_EVIDENCE_VERSION)
            .put("input_source", inputSource.name)
            .put("aid_rendering_enabled", renderAids)
            .put("model", if (inputSource == ReadingAidInputSource.LIVE_MGAZENET) "mgazenet" else "synthetic_coordinates")
            .put("settings", values).put("settings_sha256", digest)
            .put("calibration_sha256", calibrationFingerprint ?: JSONObject.NULL)
            .put("body_sha256", sha256(body.text.toString()))
            .put("typography", JSONObject().put("text_size_px", body.textSize)
                .put("line_height_px", body.lineHeight).put("line_spacing_extra_px", body.lineSpacingExtra)
                .put("line_spacing_multiplier", body.lineSpacingMultiplier)
                .put("system_font_scale", body.resources.configuration.fontScale))
            .put("max_history_fixations", com.newsmead.gaze.scanpath.ArticleScanpathSession.MAX_HISTORY_FIXATIONS)
            .put("preferences", preferenceJson()).put("experimental", true)
    }

    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
    companion object {
        private fun elapsedMs() = SystemClock.elapsedRealtimeNanos() / 1e6
        private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
