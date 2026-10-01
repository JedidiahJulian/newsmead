package com.newsmead.gaze.aid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathConfig
import com.newsmead.gaze.scanpath.ScanpathEngine
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathFixation
import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathSample
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
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
) {
    private val policy = ReadingAidPolicy(config, preferences)
    val preferences get() = policy.preferences
    val scanpathConfig = ScanpathConfig(returnSweepStartFraction = 0.25, returnSweepEndFraction = 0.75,
        wordResidenceMs = config.wordMinObservedMs, lineResidenceMs = config.lineMinObservedMs,
        cycleWindowMs = config.cycleWindowMs, progressionWindowMs = 1000.0,
        lineProgressionMaxUnitsPerS = 0.5, progressionMinObservedFraction = 0.5)
    private val trace = ReadingAidTrace()
    private val calibrationFingerprint = if (inputSource == ReadingAidInputSource.LIVE_MGAZENET)
        MgazeNetCalibrationStore(body.context).fingerprint() else null
    private val handler = Handler(Looper.getMainLooper())
    private var snapshot: ScanpathGeometry? = null
    private var latestFixation: ScanpathFixation? = null
    private var latestCapture = Double.NEGATIVE_INFINITY
    private var suspended = false
    private var closed = false
    private var context = ReadingAidContext(0, "", 0, null, null, null,
        elapsedMs(), null, null, false)
    private val pulse = object : Runnable {
        override fun run() {
            if (closed) return
            tick()
            handler.postDelayed(this, 100)
        }
    }

    init {
        overlay.onRender = { action, drawn, reason, at ->
            trace.render(action, drawn, reason, at)
            onRenderReceipt(action, drawn, reason, at)
        }
        wordDock.onRender = overlay.onRender
        trace.record("started", elapsedMs(), configuration())
        updateDockVisibility()
        handler.post(pulse)
    }

    fun geometry(generation: Int, value: ScanpathGeometry?) {
        checkMain()
        if (closed) return
        snapshot = value
        if (context.generation != generation.toLong()) latestFixation = null
        context = context.copy(generation = generation.toLong(), geometrySnapshotId = value?.snapshotId ?: "",
            lineCount = value?.lines?.size ?: 0, targetLine = null, targetWordStartUtf16 = null,
            targetWordEndUtf16 = null, nowMs = elapsedMs(), validLiveInput = false)
        trace.record("geometry", context.nowMs, JSONObject().put("generation", generation)
            .put("geometry_snapshot_id", value?.snapshotId ?: JSONObject.NULL)
            .put("body_sha256", sha256(body.text.toString())).put("line_count", value?.lines?.size ?: 0))
        decisions(policy.reset(context))
    }

    fun input(generation: Int, sample: ScanpathSample) {
        checkMain()
        if (closed) return
        val now = elapsedMs()
        val capture = sample.captureMs
        val delivery = sample.deliveryMs
        val ordered = capture != null && capture.isFinite() && capture > latestCapture
        if (capture != null && capture.isFinite()) latestCapture = maxOf(latestCapture, capture)
        val valid = !suspended && sample.eligible && ordered && delivery != null && delivery.isFinite() &&
            delivery >= capture!! && delivery <= now && sample.geometrySnapshotId == snapshot?.snapshotId
        context = ReadingAidContext(generation.toLong(), snapshot?.snapshotId ?: "",
            snapshot?.lines?.size ?: 0, sample.line, sample.wordStartUtf16, sample.wordEndUtf16,
            now, capture, delivery, valid)
        // Clear before an invalid observation can flush the earlier candidate and its events.
        if (!valid) decisions(policy.onObservation(null, context))
    }

    fun fixation(generation: Int, value: ScanpathFixation) {
        trace.fixation(generation, value)
        if (!closed && generation.toLong() == context.generation) latestFixation = value
    }

    fun event(generation: Int, value: ScanpathEvent) {
        checkMain()
        if (closed || suspended || generation.toLong() != context.generation) return
        trace.event(generation, value)
        context = context.copy(nowMs = elapsedMs())
        decisions(policy.onEvent(value, latestFixation, context))
    }

    fun candidate(generation: Int, value: ScanpathCandidateEvidence?) {
        checkMain()
        if (closed || suspended || generation.toLong() != context.generation) return
        trace.candidate(generation, value)
        context = context.copy(nowMs = elapsedMs())
        decisions(policy.onObservation(value, context))
    }

    fun updatePreferences(value: ReaderAidPreferences) {
        checkMain()
        context = context.copy(nowMs = elapsedMs())
        decisions(policy.updatePreferences(value, context))
        trace.record("preferences", context.nowMs, preferenceJson())
        updateDockVisibility()
    }

    fun suspend(reason: String) {
        checkMain()
        if (closed) return
        suspended = true
        context = context.copy(nowMs = elapsedMs(), validLiveInput = false)
        decisions(policy.reset(context))
        trace.record("suspended", context.nowMs, JSONObject().put("reason", reason))
        updateDockVisibility()
    }

    fun resume() {
        checkMain()
        if (closed) return
        suspended = false
        context = context.copy(nowMs = elapsedMs(), validLiveInput = false)
        decisions(policy.reset(context))
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
    fun activeActions(): List<ReadingAidAction> = policy.activeActions()

    private fun decisions(values: List<ReadingAidDecision>) {
        trace.context(context)
        values.forEach { trace.decision(it); onDecision(it) }
        val actions = if (closed || suspended) emptyList() else policy.activeActions()
        overlay.setActions(body, snapshot, context.generation, actions)
        wordDock.showAction(actions.firstOrNull { it.aidType == ReadingAidType.WORD_MAGNIFIER }, body)
    }

    private fun updateDockVisibility() {
        wordDock.visibility = if (!closed && !suspended && preferences.enabled && preferences.wordMagnifier)
            View.VISIBLE else View.GONE
    }

    private fun preferenceJson() = JSONObject().put("enabled", preferences.enabled)
        .put("line_guide", preferences.lineGuide).put("line_start_cue", preferences.lineStartCue)
        .put("word_magnifier", preferences.wordMagnifier).put("focus_region", preferences.focusRegion)
        .put("adaptive_strength", preferences.adaptiveStrength).put("max_strength", preferences.maxStrength)
        .put("manual_strength", preferences.manualStrength)

    private fun configuration(): JSONObject {
        val values = JSONObject().put("config_id", config.configId).put("condition_id", config.conditionId)
            .put("automatic_rules_enabled", config.automaticRulesEnabled).put("word_min_refixations", config.wordMinRefixations)
            .put("word_min_observed_ms", config.wordMinObservedMs).put("word_saturation_ms", config.wordSaturationMs)
            .put("line_min_observed_ms", config.lineMinObservedMs).put("line_saturation_ms", config.lineSaturationMs)
            .put("cycle_window_ms", config.cycleWindowMs)
            .put("aid_ttl_ms", config.aidTtlMs).put("cooldown_ms", config.cooldownMs)
            .put("max_evidence_age_ms", config.maxEvidenceAgeMs).put("min_adaptive_strength", config.minAdaptiveStrength)
            .put("max_magnification_scale", config.maxMagnificationScale).put("focus_context_radius_lines", config.focusContextRadiusLines)
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
        return JSONObject().put("policy_version", 1).put("extractor_version", ScanpathEngine.EXTRACTOR_VERSION)
            .put("classifier_version", ScanpathEngine.CLASSIFIER_VERSION)
            .put("provisional_evidence_version", ScanpathEngine.PROVISIONAL_EVIDENCE_VERSION)
            .put("input_source", inputSource.name)
            .put("model", if (inputSource == ReadingAidInputSource.LIVE_MGAZENET) "mgazenet" else "synthetic_coordinates")
            .put("settings", values).put("settings_sha256", digest)
            .put("calibration_sha256", calibrationFingerprint ?: JSONObject.NULL)
            .put("body_sha256", sha256(body.text.toString()))
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
