package com.newsmead.activities

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import com.newsmead.BuildConfig
import com.newsmead.R
import com.newsmead.data.StudyConfig
import com.newsmead.databinding.ActivityScanpathPilotBinding
import com.newsmead.gaze.aid.*
import com.newsmead.gaze.mgazenet.CalibrationIdentity
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.scanpath.*
import com.newsmead.gaze.scanpath.audit.ScanpathTraceWriter
import com.newsmead.gaze.scanpath.pilot.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

enum class PilotReadingCondition { NO_AIDS, SHADOW, DISPLAY }
enum class PilotAllocation { PRACTICE, DEVELOPMENT, EVALUATION }
data class PilotRunSettings(val linkedSet: String = "practice", val protocol: String = "UNFROZEN_PRACTICE",
    val allocation: PilotAllocation = PilotAllocation.PRACTICE, val stepMs: Long = 2000,
    val settlingMs: Double = 300.0, val readingCondition: PilotReadingCondition = PilotReadingCondition.SHADOW,
    val preferences: ReaderAidPreferences = ReaderAidPreferences()) {
    init {
        require(linkedSet.matches(Regex("[A-Za-z0-9_-]{1,64}")) && protocol.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(stepMs in 250..10000 && settlingMs.isFinite() && settlingMs >= 0 && settlingMs < stepMs)
        require(allocation == PilotAllocation.PRACTICE || protocol != "UNFROZEN_PRACTICE")
    }
}
data class PilotRunResult(val taskId: String, val reason: String, val score: PilotScoreResult?,
    val observations: Int, val engineAccepted: Int, val patterns: Set<String>, val chosenAids: Set<String>,
    val drawnAids: Set<String>, val trace: File, val traceFailure: String?)

/** Explicit-start live instrument. Software-only input is available to debug tests, never via intent. */
class ScanpathPilotActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanpathPilotBinding
    private lateinit var overlay: ReadingAidOverlayView
    private lateinit var definitionPopup: ReadingAidDefinitionPopup
    private var consultationAnchor: ReadingAidAnchor? = null
    private lateinit var prompt: PromptView
    private val handler = Handler(Looper.getMainLooper())
    private var settings = PilotRunSettings()
    private var runtime: ArticleReadingAidRuntime? = null
    private var session: ArticleScanpathSession? = null
    private var provider: MgazeNetGazeProvider? = null
    private var log: ScanpathTraceWriter? = null
    private var scorer: PilotScore? = null
    private var plan: PilotTaskPlan? = null
    private var geometry: ScanpathGeometry? = null
    private var plannedFrame: ScanpathGeometry? = null
    private var pendingStop: String? = null
    private var selectedTask = "word_hold"
    private var synthetic = false
    private var preparing = false
    private var running = false
    private var closing = false
    private var waitingForInput = false
    private var token = 0
    private var observations = 0
    private var accepted = 0
    private var terminalDeliveries = 0
    private var lastGeneration = 0
    private var step = -1
    private var stopMs = 0.0
    private var stopReason = ""
    private var skipReport = false
    private var startFingerprint: String? = null
    private var pendingScore: PilotScoreResult? = null
    private var reportPending = false
    private var reportDialog: AlertDialog? = null
    private val patternCounts = linkedMapOf<String, Int>()
    private val chosen = linkedSetOf<String>()
    private val drawn = linkedSetOf<String>()
    private data class Passage(val id: String, val label: String, val body: String, val origin: String)
    private var passages = emptyList<Passage>()
    var lastResult: PilotRunResult? = null
        private set
    val isRunning get() = running || preparing || closing
    val activeTarget: PilotTarget? get() = plan?.targets?.getOrNull(step)
    val currentStep get() = step
    private val preDraw = ViewTreeObserver.OnPreDrawListener { session?.checkGeometry(); runtime?.tick(); true }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) binding.root.post {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startLiveRun()
        } else binding.pilotStatus.text = "Camera permission was declined; no recording started."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanpathPilotBinding.inflate(layoutInflater)
        setContentView(binding.root)
        passages = listOf(Passage("software_fixture", "Practice text", ScanpathReplayActivity.REPLAY_TEXT, "synthetic_fixture")) +
            JSONObject(assets.open(StudyConfig.ARTICLES_ASSET).bufferedReader().use { it.readText() }).getJSONArray("articles").let { rows ->
                (0 until rows.length()).map { i -> rows.getJSONObject(i).let { a ->
                    Passage("study_${a.getInt("article_id")}", a.getString("title"), a.getString("body"), "original_bundled_study_prose")
                } }
            }
        binding.pilotBody.textLocale = Locale.ENGLISH
        binding.pilotBody.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP)
        overlay = ReadingAidOverlayView(this, lineStartMarkerInGutter = true)
        definitionPopup = ReadingAidDefinitionPopup(this, binding.pilotViewport, binding.pilotBody)
        prompt = PromptView(this) { index, at -> promptDrawn(index, at) }
        binding.pilotViewport.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        binding.pilotViewport.addView(prompt, FrameLayout.LayoutParams(-1, -1))
        binding.pilotTask.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            PilotTasks.tasks.map { it.label } + "Ordinary reading")
        binding.pilotPassage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, passages.map { it.label })
        binding.pilotPassage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isRunning) { binding.pilotBody.text = passages[position].body; binding.pilotScroll.scrollTo(0, 0) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.pilotBody.text = passages.first().body
        binding.pilotBack.setOnClickListener { finish() }
        binding.pilotStart.setOnClickListener { startLiveRun() }
        binding.pilotStop.setOnClickListener { stopRun("reader_stop") }
        binding.pilotSettings.setOnClickListener { showSettings() }
        binding.pilotTrace.setOnClickListener { showTrace() }
        controls(false)
    }

    fun startLiveRun() {
        if (isRunning) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.CAMERA); return
        }
        val issue = MgazeNetCalibrationStore(this).compatibilityIssue(binding.pilotBody)
        if (issue != null) { binding.pilotStatus.text = "$issue\nUse Home's calibration screen, then return."; return }
        val id = PilotTasks.tasks.getOrNull(binding.pilotTask.selectedItemPosition)?.id ?: "ordinary_reading"
        begin(id, false)
    }

    /** Tests explicitly label every trace synthetic and skip permission, calibration and provider. */
    fun startSyntheticCheck(id: String, value: PilotRunSettings = PilotRunSettings(stepMs = 450, settlingMs = 100.0)) {
        check(BuildConfig.DEBUG)
        check(!isRunning)
        settings = value.copy(allocation = PilotAllocation.PRACTICE, linkedSet = "software_check", protocol = "SOFTWARE_ONLY")
        begin(id, true)
    }
    fun deliverSyntheticObservation(value: MgazeNetGazeProvider.Observation) {
        check(BuildConfig.DEBUG && synthetic && (running || closing))
        receive(value)
    }

    private fun begin(id: String, softwareOnly: Boolean) {
        saveReport("not_assessed", "")
        observations = 0; accepted = 0; terminalDeliveries = 0; lastGeneration = 0; step = -1
        patternCounts.clear(); chosen.clear(); drawn.clear()
        geometry = null; plannedFrame = null; scorer = null; plan = null; pendingScore = null; pendingStop = null
        skipReport = false
        selectedTask = id; synthetic = softwareOnly; preparing = true; lastResult = null
        definitionPopup.close()
        definitionPopup = ReadingAidDefinitionPopup(this, binding.pilotViewport, binding.pilotBody,
            DictionaryDefinitionRepository(this, allowNetwork = !softwareOnly),
            unavailableMessageRes = if (softwareOnly) R.string.definition_offline_unavailable else R.string.definition_unavailable)
        consultationAnchor = null
        startFingerprint = if (synthetic) null else MgazeNetCalibrationStore(this).fingerprint()
        val mine = ++token
        binding.pilotStatus.text = "Preparing ${if (softwareOnly) "software check" else "live task"}…"
        controls(true)
        binding.root.doOnPreDraw {
            if (mine != token || !preparing || isFinishing) return@doOnPreDraw
            try { prepare(mine) } catch (e: Exception) {
                if (log == null) log = ScanpathTraceWriter(this, "scanpath_pilot", header(null, JSONObject())
                    .put("setup_failure", e.message ?: e.javaClass.simpleName), 100000)
                stopRun("setup_failed")
            }
        }
    }

    private fun prepare(mine: Int) {
        val frame = checkNotNull(ScanpathTextGeometryReader().read(binding.pilotBody)) { "Text layout unavailable." }
        plannedFrame = frame.forEngine("planning")
        val mapper = ScanpathWordMapper(binding.pilotBody.text.toString(), binding.pilotBody)
        val layout = binding.pilotBody.layout
        val words = mapper.ranges.mapNotNull { w ->
            val line = layout.getLineForOffset(w.startUtf16)
            if (layout.getLineForOffset(w.endUtf16 - 1) != line) return@mapNotNull null
            val row = frame.lines[line]
            val left = frame.originX + layout.getPrimaryHorizontal(w.startUtf16)
            val end = frame.originX + layout.getPrimaryHorizontal(w.endUtf16 - 1) +
                binding.pilotBody.paint.measureText(binding.pilotBody.text.subSequence(w.endUtf16 - 1, w.endUtf16).toString()) * 0.4
            val rect = ScanpathRect(left, frame.originY + row.top + 1, minOf(end, frame.originX + row.right), frame.originY + row.bottom - 1)
            if (rect.right <= rect.left || rect.left < frame.visible.left || rect.right > frame.visible.right ||
                rect.top < frame.visible.top || rect.bottom > frame.visible.bottom) null
            else PilotTarget("word:${w.startUtf16}:${w.endUtf16}", line, w.startUtf16, w.endUtf16, rect)
        }
        plan = if (selectedTask == "ordinary_reading") null else PilotTasks.build(selectedTask, plannedFrame!!, words)
        val reading = plan == null
        val condition = if (reading) settings.readingCondition else PilotReadingCondition.NO_AIDS
        val prefs = if (condition == PilotReadingCondition.NO_AIDS) settings.preferences.copy(enabled = false) else settings.preferences
        runtime = ArticleReadingAidRuntime(binding.pilotBody, binding.pilotWordDock, overlay,
            ReadingAidPolicyConfig.definitionPrototype().copy(conditionId = "PILOT_DEFINITION_${condition.name}_V2"), prefs,
            if (synthetic) ReadingAidInputSource.SYNTHETIC_REPLAY else ReadingAidInputSource.LIVE_MGAZENET,
            onDecision = { d -> log?.decision(d); if (d.status == ReadingAidDecisionStatus.CHOSEN) d.action?.let { chosen.add(it.aidType.name) } },
            onRenderReceipt = { a, visible, reason, at -> log?.render(a, visible, reason, at); if (visible) drawn.add(a.aidType.name) },
            renderAids = condition == PilotReadingCondition.DISPLAY,
            definitionPopup = definitionPopup,
            onConsultationChanged = { active, at, reason ->
                if (active) consultationAnchor = definitionPopup.action?.anchor
                log?.append("definition_consultation", JSONObject().put("active", active)
                    .put("elapsed_ms", at).put("reason", reason)
                    .put("word_start_utf16", consultationAnchor?.wordStartUtf16 ?: JSONObject.NULL)
                    .put("word_end_utf16", consultationAnchor?.wordEndUtf16 ?: JSONObject.NULL))
                session?.setConsultation(active, at, reason)
                if (!active) consultationAnchor = null
            })
        log = ScanpathTraceWriter(this, "scanpath_pilot", header(plan, runtime!!.configurationSnapshot()
            .put("dictionary_mode", if (synthetic) "bundled_only" else "bundled_then_online")
            .put("ui_layout_id", "CONTEXT_DEFINITION_GUTTER_V1").put("reserved_word_strip_dp", 0)), 100000)
        session = ArticleScanpathSession(binding.pilotBody, runtime!!.scanpathConfig,
            onGeometry = { generation, g ->
                log?.geometry(generation, g); runtime?.geometry(generation, g); geometry = g; lastGeneration = generation
                if (running && plan != null && (g == null || g.copy(snapshotId = "planning") != plannedFrame || generation != 0))
                    {
                        pendingStop = "geometry_changed"
                        prompt.clear()
                        handler.post { if (running && pendingStop != null) stopRun("geometry_changed") }
                    }
            }, onInput = { generation, sample ->
                log?.sample(generation, observations, sample); scorer?.record(sample); runtime?.input(generation, sample)
            }, onCandidate = { generation, c -> log?.candidate(generation, c); runtime?.candidate(generation, c) },
            onFixation = { generation, f -> log?.fixation(generation, f); runtime?.fixation(generation, f) },
            onEvent = { generation, e ->
                log?.event(generation, e); patternCounts[e.patternId] = (patternCounts[e.patternId] ?: 0) + 1; runtime?.event(generation, e)
            }, onAcceptance = { generation, _, ok ->
                if (ok) accepted++
                log?.append("input_acceptance", JSONObject().put("generation", generation).put("input_ordinal", observations).put("engine_accepted", ok))
            })
        session!!.checkGeometry()
        plan?.let { scorer = PilotScore(it, checkNotNull(geometry).snapshotId, settings.settlingMs,
            maxSequenceGapMs = runtime!!.scanpathConfig.maxSampleGapMs) }
        binding.root.viewTreeObserver.addOnPreDrawListener(preDraw)
        preparing = false; running = true; waitingForInput = true; binding.root.keepScreenOn = true
        binding.pilotStatus.text = "Waiting for raw gaze…\n${if (plan == null) condition.name else "Prompted targets; aids off"}"
        handler.postDelayed({ if (token == mine && running && waitingForInput) stopRun("no_coordinate_input") }, 20000)
        handler.postDelayed({ if (token == mine && running) stopRun("resource_time_limit") }, 600000)
        if (synthetic) beginTimeline() else {
            provider = MgazeNetGazeProvider(this, binding.pilotBody, smoothForReading = false).also { p ->
                p.onObservation = ::receive
                p.onFailure = { reason -> log?.append("provider_failure", JSONObject().put("reason", reason)); stopRun("provider_failed") }
                p.start(this)
            }
        }
    }

    private fun receive(o: MgazeNetGazeProvider.Observation) {
        if (!running && !closing) return
        observations++
        log?.append("raw_observation", JSONObject().put("input_ordinal", observations).put("delivery_id", o.deliveryId)
            .put("capture_ms", finite(o.captureMs)).put("delivery_ms", o.outputMs).put("reason", o.reason)
            .put("raw_x", finite(o.rawX?.toDouble())).put("raw_y", finite(o.rawY?.toDouble()))
            .put("provider_x", finite(o.x?.toDouble())).put("provider_y", finite(o.y?.toDouble()))
            .put("arrivals", o.arrivals ?: JSONObject.NULL).put("busy_drops", o.busyDrops ?: JSONObject.NULL)
            .put("phase", if (closing) "terminal_delivery" else if (waitingForInput) "initialization" else "task"))
        if (closing) {
            // Retain trailing callbacks as unknown, rather than remapping a paused/reflowed view.
            terminalDeliveries++
            val s = ScanpathSample(o.captureMs, o.outputMs, o.rawX?.toDouble(), o.rawY?.toDouble(), null, null, null,
                geometry?.snapshotId, false, "terminal_delivery")
            log?.sample(lastGeneration, observations, s); scorer?.record(s)
        } else {
            session?.record(o)
            if (waitingForInput && o.reason == "coordinate") beginTimeline()
            val reason = pendingStop ?: log?.failure?.let { "trace_failure" }
            if (reason != null) stopRun(reason)
        }
    }

    private fun beginTimeline() {
        if (!running || !waitingForInput) return
        waitingForInput = false
        log?.append("task_ready", JSONObject().put("elapsed_ms", now()))
        if (plan == null) binding.pilotStatus.text = "Read at your own pace. Stop when finished.\n${settings.readingCondition} · raw gaze · hidden dot"
        else showStep(0)
    }
    private fun showStep(index: Int) {
        if (!running) return
        val task = checkNotNull(plan)
        if (index >= task.targets.size) { stopRun("completed"); return }
        step = index
        val requestMs = now()
        log?.append("prompt_requested", JSONObject().put("index", index).put("request_ms", requestMs))
        prompt.show(index, task.targets[index])
        binding.pilotStatus.text = "Follow the blue ${if (task.kind == "word") "word" else "line"}.\nTarget ${index + 1}/${task.targets.size} · keep looking until it changes."
        val mine = token
        handler.postDelayed({ if (mine == token && running && step == index && !prompt.hasDrawn) stopRun("prompt_not_drawn") }, 2000)
    }
    private fun promptDrawn(index: Int, at: Double) {
        if (!running || index != step) return
        scorer?.showStep(index, at)
        log?.append("prompt_onset_proxy", JSONObject().put("index", index).put("onset_ms", at)
            .put("target", PilotRecordJson.target(checkNotNull(activeTarget))).put("verified_visibility", false))
        val mine = token
        handler.postDelayed({ if (mine == token && running) {
            session?.checkGeometry()
            val problem = pendingStop ?: log?.failure?.let { "trace_failure" }
            if (problem != null) stopRun(problem) else showStep(index + 1)
        } }, settings.stepMs)
    }

    fun stopRun(reason: String) {
        if (closing) { if (reason.startsWith("activity")) skipReport = true; return }
        if (!running && !preparing) return
        token++; running = false; preparing = false; waitingForInput = false; closing = true
        stopReason = reason; stopMs = now(); skipReport = reason.startsWith("activity")
        handler.removeCallbacksAndMessages(null)
        binding.root.keepScreenOn = false
        binding.root.viewTreeObserver.removeOnPreDrawListener(preDraw)
        prompt.clear()
        runtime?.suspend(reason); session?.close(reason); runtime?.close(reason); session = null; runtime = null
        log?.append("task_end", JSONObject().put("elapsed_ms", stopMs).put("reason", reason))
        binding.pilotStatus.text = "Stopping camera and retaining terminal counters…"
        binding.pilotStop.isEnabled = false
        val p = provider
        provider = null
        if (p == null) sourceClosed() else p.stopAndThen { sourceClosed() }
    }
    private fun sourceClosed() {
        if (!closing) return
        closing = false
        pendingScore = scorer?.finish(stopMs); scorer = null
        log?.append("source_closed", JSONObject().put("elapsed_ms", now()).put("terminal_deliveries", terminalDeliveries))
        reportPending = true
        if (synthetic || skipReport || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) saveReport("not_assessed", "")
        else showReport()
    }
    private fun showReport() {
        val choices = arrayOf("Not assessed", "Followed the instructions", "Could not follow", "Unsure")
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val category = Spinner(this).apply { adapter = ArrayAdapter(this@ScanpathPilotActivity, android.R.layout.simple_spinner_dropdown_item, choices); panel.addView(this) }
        val note = EditText(this).apply { hint = "Optional adherence / interruption note"; maxLines = 3; panel.addView(this) }
        binding.pilotStatus.text = "Task finished. Record adherence before viewing scores."
        reportDialog = AlertDialog.Builder(this).setTitle("Task adherence report").setView(panel).setCancelable(false)
            .setPositiveButton("Save") { _, _ -> saveReport(arrayOf("not_assessed", "reported_followed", "reported_not_followed", "unsure")[category.selectedItemPosition], note.text.toString().take(2000)) }
            .setNegativeButton("Skip") { _, _ -> saveReport("not_assessed", "") }.show()
    }
    private fun saveReport(category: String, note: String) {
        if (!reportPending) return
        reportPending = false; reportDialog?.dismiss(); reportDialog = null
        val recorder = log ?: return
        recorder.append("compliance_report", JSONObject().put("category", category).put("note", note)
            .put("reported_ms", now()).put("reference_kind", "self_or_operator_report_not_verified_eye_position"))
        val endFingerprint = if (synthetic) null else MgazeNetCalibrationStore(this).fingerprint()
        val summary = JSONObject().put("task_id", selectedTask).put("observations", observations).put("engine_accepted_samples", accepted)
            .put("terminal_deliveries", terminalDeliveries).put("score", pendingScore?.let(PilotRecordJson::score) ?: JSONObject.NULL)
            .put("pattern_record_counts", JSONObject().apply { patternCounts.forEach { (id, n) -> put(id, n) } })
            .put("chosen_aids", JSONArray(chosen.toList())).put("drawn_proxy_aids", JSONArray(drawn.toList()))
            .put("compliance", category).put("end_calibration_sha256", endFingerprint ?: JSONObject.NULL)
            .put("calibration_changed", if (synthetic) JSONObject.NULL else startFingerprint != endFingerprint)
        recorder.close(stopReason, summary)
        lastResult = PilotRunResult(selectedTask, stopReason, pendingScore, observations, accepted, patternCounts.keys.toSet(),
            chosen.toSet(), drawn.toSet(), recorder.file, recorder.failure)
        log = null; plan = null; pendingScore = null
        controls(false)
        binding.pilotStatus.text = "$selectedTask: $stopReason\nRaw callbacks $observations · engine accepted $accepted\n${patternCounts.keys.joinToString(" ")}\nTrace ${recorder.failure ?: "saved privately"}"
    }

    private fun header(task: PilotTaskPlan?, configuration: JSONObject): JSONObject {
        val passage = passages[binding.pilotPassage.selectedItemPosition.coerceAtLeast(0)]
        val values = JSONObject().put("linked_set", settings.linkedSet).put("protocol", settings.protocol).put("allocation", settings.allocation.name)
            .put("step_duration_ms", settings.stepMs).put("scoring_settling_ms", settings.settlingMs).put("reading_condition", settings.readingCondition.name)
        return JSONObject().put("schema_version", 1).put("instrument", "newsmead_scanpath_pilot_v1")
            .put("input_kind", if (synthetic) "synthetic_pilot_check" else if (selectedTask == "ordinary_reading") "live_reading" else "live_prompted")
            .put("camera_frames_retained", false).put("human_reference", false).put("reference_kind", "intended_targets_conditional_on_compliance")
            .put("capture_time_basis", if (synthetic) "synthetic_elapsed_realtime_test" else "camera_timestamp_ns_div_1e6_elapsed_realtime")
            .put("delivery_time_basis", "actual_elapsed_realtime_callback").put("prompt_time_basis", "canvas_draw_proxy_elapsed_realtime")
            .put("raw_scoring", true).put("smoothing", false).put("alignment_correction", false).put("gaze_dot_visible", false)
            .put("terminal_delivery_rule", "retained_unknown_no_post_stop_mapping").put("reserved_word_strip_dp", 0)
            .put("ui_layout_id", "CONTEXT_DEFINITION_GUTTER_V1")
            .put("dictionary_mode", if (synthetic) "bundled_only" else "bundled_then_online")
            .put("task_id", selectedTask).put("plan", task?.let(PilotRecordJson::plan) ?: JSONObject.NULL)
            .put("run_settings", values).put("run_settings_sha256", hash(values.toString()))
            .put("configuration", configuration).put("passage_id", passage.id).put("passage_origin", passage.origin)
            .put("body_text", binding.pilotBody.text.toString()).put("body_sha256", hash(binding.pilotBody.text.toString()))
            .put("font_dp", StudyConfig.ARTICLE_FONT_SIZE_DP).put("locale", "en").put("model_sha256", if (synthetic) JSONObject.NULL else CalibrationIdentity.MODEL)
            .put("model_pipeline", if (synthetic) "synthetic_coordinates" else CalibrationIdentity.PIPELINE)
            .put("start_calibration_sha256", startFingerprint ?: JSONObject.NULL)
            .put("resource_timeout_ms", 600000).put("score_sample_limit", 20000)
    }
    private fun controls(active: Boolean) {
        binding.pilotTask.isEnabled = !active; binding.pilotPassage.isEnabled = !active
        binding.pilotStart.isEnabled = !active; binding.pilotSettings.isEnabled = !active
        binding.pilotStop.isEnabled = active; binding.pilotTrace.isEnabled = !active && lastResult != null
    }
    private fun showTrace() {
        val r = lastResult ?: return
        AlertDialog.Builder(this).setTitle("Private pilot trace").setMessage("${r.trace.absolutePath}\nStatus: ${r.traceFailure ?: "written"}")
            .setPositiveButton("Copy path") { _, _ -> (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Pilot trace", r.trace.absolutePath)) }.setNegativeButton(android.R.string.ok, null).show()
    }
    private fun showSettings() {
        if (isRunning) return
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        fun field(label: String, text: String) = EditText(this).apply { hint = label; setText(text); panel.addView(this) }
        panel.addView(TextView(this).apply { text = "Pseudonymous codes only. Practice defaults are engineering settings. Prompted tasks have aids off; reading condition applies to Ordinary reading." })
        val linked = field("Linked session-set code", settings.linkedSet)
        val protocol = field("Protocol / plan code", settings.protocol)
        fun <T : Enum<T>> choice(values: Array<T>, selected: T) = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScanpathPilotActivity, android.R.layout.simple_spinner_dropdown_item, values.map { it.name }); setSelection(values.indexOf(selected)); panel.addView(this)
        }
        val allocation = choice(PilotAllocation.values(), settings.allocation)
        val condition = choice(PilotReadingCondition.values(), settings.readingCondition)
        val duration = field("Target duration, ms", settings.stepMs.toString())
        val settle = field("Excluded settling after cue, ms", settings.settlingMs.toString())
        fun check(label: Int, selected: Boolean) = CheckBox(this).apply { setText(label); isChecked = selected; panel.addView(this) }
        val p = settings.preferences
        val enabled = check(R.string.reading_aids_enabled, p.enabled)
        val line = check(R.string.reading_aids_line, p.lineGuide)
        val start = check(R.string.reading_aids_start, p.lineStartCue)
        val word = check(R.string.reading_aids_word_definition, p.wordDefinition)
        val focus = check(R.string.reading_aids_focus, p.focusRegion)
        val adaptive = check(R.string.reading_aids_adaptive, p.adaptiveStrength)
        val strength = field("Aid strength, 0–100% (continuation marker stays fixed)", (p.maxStrength * 100).toInt().toString())
        val scroll = ScrollView(this).apply { addView(panel) }
        val dialog = AlertDialog.Builder(this).setTitle("Pilot settings").setView(scroll).setPositiveButton("Apply", null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val amount = strength.text.toString().toDouble() / 100
                require(amount.isFinite() && amount in 0.0..1.0)
                settings = PilotRunSettings(linked.text.toString(), protocol.text.toString(), PilotAllocation.values()[allocation.selectedItemPosition],
                    duration.text.toString().toLong(), settle.text.toString().toDouble(), PilotReadingCondition.values()[condition.selectedItemPosition],
                    ReaderAidPreferences(enabled = enabled.isChecked, lineGuide = line.isChecked, lineStartCue = start.isChecked,
                        wordMagnifier = false, wordDefinition = word.isChecked, focusRegion = focus.isChecked,
                        adaptiveStrength = adaptive.isChecked, maxStrength = amount, manualStrength = amount))
                dialog.dismiss()
            } catch (_: Exception) { Toast.makeText(this, "Use valid codes, a named plan outside practice, and a settling interval shorter than the target duration.", Toast.LENGTH_LONG).show() }
        } }; dialog.show()
    }
    override fun onPause() { stopRun("activity_paused"); saveReport("not_assessed", ""); super.onPause() }
    override fun onDestroy() {
        stopRun("activity_destroyed"); saveReport("not_assessed", "")
        if (::definitionPopup.isInitialized) definitionPopup.close()
        super.onDestroy()
    }

    private class PromptView(context: Context, private val receipt: (Int, Double) -> Unit) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val origin = IntArray(2)
        private var target: PilotTarget? = null
        private var index = -1
        var hasDrawn = false
            private set
        init { isClickable = false; isFocusable = false }
        fun show(n: Int, value: PilotTarget) { index = n; target = value; hasDrawn = false; invalidate() }
        fun clear() { target = null; invalidate() }
        override fun onDraw(canvas: Canvas) {
            val t = target ?: return
            getLocationOnScreen(origin)
            val r = t.bounds
            paint.style = Paint.Style.FILL; paint.color = Color.argb(32, 60, 170, 255)
            canvas.drawRect((r.left - origin[0]).toFloat(), (r.top - origin[1]).toFloat(), (r.right - origin[0]).toFloat(), (r.bottom - origin[1]).toFloat(), paint)
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(50, 155, 245); paint.strokeWidth = 2 * resources.displayMetrics.density
            canvas.drawRect((r.left - origin[0]).toFloat(), (r.top - origin[1]).toFloat(), (r.right - origin[0]).toFloat(), (r.bottom - origin[1]).toFloat(), paint)
            if (!hasDrawn) { hasDrawn = true; receipt(index, now()) }
        }
    }
    companion object {
        private fun now() = SystemClock.elapsedRealtimeNanos() / 1e6
        private fun finite(v: Double?): Any = v?.takeIf { it.isFinite() } ?: JSONObject.NULL
        private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
