package com.newsmead.research.study

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Toast
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import com.newsmead.activities.ReadingBaselineActivity
import com.newsmead.data.StudyConfig
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.fragments.article.ArticleTypography
import com.newsmead.gaze.ParticipantReadingBaseline
import com.newsmead.gaze.SharedReadingBaseline
import com.newsmead.gaze.ReadingFeaturePipeline
import com.newsmead.gaze.mahalanobis.MahalanobisBaselineCollector
import com.newsmead.gaze.mahalanobis.MahalanobisConfig
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.physicalDisplaySize
import com.newsmead.gaze.reading.ArticleReadingSession
import com.newsmead.gaze.reading.ReadingCoordinateStream
import com.newsmead.models.Article

/** Explicit unassisted capture on the real article layout. No aid runtime, dot,
 * TTS, Firebase access or camera exists before Start or after an interruption.
 * Only the existing R/D/F profile is fitted here; coordinate fitting is offline.
 */
class SharedBaselineReader(private val fragment: Fragment, private val binding: FragmentArticleBinding) {
    private val context get() = binding.root.context
    private val config = MahalanobisConfig()
    private val handler = Handler(Looper.getMainLooper())
    private var pipeline = ReadingFeaturePipeline(config)
    private var collector = MahalanobisBaselineCollector(config)
    private var adapter: ArticleReadingSession? = null
    private var provider: MgazeNetGazeProvider? = null
    private var visit: StudyVisit? = null
    private var attempt = ""
    private var calibration = ""
    private var participant = ""
    private var running = false
    private var stopping = false
    private var closed = false
    private var lineCount = 0
    private var vectors = 0
    private var validVectors = 0
    private var dialog: AlertDialog? = null
    private lateinit var progress: TextView
    private var lastResult = "Researcher: start a Reading run in Study recording before using Start on this screen."
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (running) {
            visit?.presentation(binding.tvArticleText, StudyJson.obj("diagnostic_dot" to false), binding.nsvArticleText)
            adapter?.checkGeometry()
        }
        true
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            collectTick()
            progress.text = "R/D/F: $validVectors/${config.minimumRetainedWindows}\nCovered: ${collector.coveredDurationMs / 1000}/${config.minimumBaselineDurationMs / 1000}s\nRaw ref: recording"
            binding.btnArticleReadingAids.contentDescription = "Finish baseline. $validVectors of $vectors valid R/D/F windows; ${collector.coveredDurationMs / 1000} seconds covered."
            handler.postDelayed(this, config.updateIntervalMs)
        }
    }

    fun bind() {
        SharedStudyToolbar.holdExpanded(binding)
        binding.tvArticleHeadline.text = "Reading baseline"
        binding.tvSource.text = "NewsMead"
        binding.tvCategory.text = "Unassisted reading"
        binding.tvArticleMinRead.text = "Read naturally"
        binding.tvArticleText.text = ReadingBaselineActivity.BASELINE_PASSAGE
        binding.ivArticleFullImage.visibility = View.GONE
        binding.btnReadFullArticle.visibility = View.GONE
        binding.divider.visibility = View.GONE
        binding.btnArticleRecommendations.visibility = View.GONE
        binding.tvArticleRecommended.visibility = View.GONE
        binding.rvArticleRecommended.visibility = View.GONE
        binding.btnArticleBack.setOnClickListener { fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        listOf(binding.btnReadAloudArticle, binding.btnTranslateArticle, binding.btnArticleShare,
            binding.btnSaveList, binding.btnArticleTextSmaller, binding.btnArticleTextLarger,
            binding.btnArticleClrLight, binding.btnArticleClrDark, binding.btnArticleClrSepia).forEach { it.isEnabled = false }
        // Reuse the existing fixed-height footer area; status never resizes the reading viewport.
        val statusArea = binding.btnArticleClrLight.parent as LinearLayout
        statusArea.removeAllViews()
        progress = TextView(context).apply {
            text = "Baseline\nReady to start"
            textSize = 12f; maxLines = 3; gravity = Gravity.CENTER_VERTICAL
            setTextColor(binding.tvArticleText.currentTextColor)
        }
        statusArea.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        if (StudyConfig.LOCK_ARTICLE_FONT_SIZE) {
            binding.tvArticleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP)
            binding.llArticleBottomButtons.visibility = View.GONE
        } else {
            val size = context.getSharedPreferences("article_reading_preferences", Context.MODE_PRIVATE)
                .getFloat("text_size_sp", ArticleTypography.DEFAULT_SIZE_SP)
            ArticleTypography.apply(binding.tvArticleText, size)
            binding.llArticleBottomButtons.visibility = View.VISIBLE
        }
        binding.btnArticleReadingAids.text = "Start"
        binding.btnArticleReadingAids.contentDescription = "Start shared reading baseline"
        binding.btnArticleReadingAids.setOnClickListener { if (running) finish(true, "shared_baseline_finished") else instructions() }
        binding.btnRunGazeTest.setOnClickListener { instructions() }
        binding.root.viewTreeObserver.addOnPreDrawListener(preDraw)
        binding.root.doOnPreDraw {
            binding.nsvArticleText.scrollTo(0, binding.tvArticleText.top)
        }
    }

    private fun instructions() {
        if (closed || stopping || running || dialog != null) return
        var startRequested = false
        val run = StudyCollection.run
        val ready = run != null && run.phase == "reading"
        dialog = AlertDialog.Builder(context).setTitle("Before baseline reading")
            .setMessage(if (ready) "For the reader:\nPlease read this passage as you normally would and scroll when needed. You may ask for a break or stop at any time.\n\nFor the researcher:\nParticipant code: ${run!!.participant}\nUse this person's saved calibration. This is a separate setup reading with visual aids off. Wait for at least ${config.minimumBaselineDurationMs / 1000} seconds covered and ${config.minimumRetainedWindows} valid windows, then tap Finish and check the result.\n\nR/D/F is the behaviour reference. The coordinate reference is recorded now and fitted after transfer; recording it does not mean that fit is ready.\n\n$lastResult"
                else "Start a Reading run in Study recording first. Enter the participant code and condition, choose Reading, and tap Start run. Then return here and use Start.")
            .setNegativeButton(android.R.string.cancel, null)
            .apply { if (ready) setPositiveButton("Start") { _, _ -> startRequested = true } }
            .create().also { d ->
                d.setOnDismissListener {
                    dialog = null
                    if (startRequested) binding.root.post { if (fragment.isResumed && !closed) start() }
                }
                d.show()
            }
    }

    private fun start() {
        if (running || stopping || closed || !fragment.isResumed) return
        if (!StudyConfig.GAZE_ENABLED || StudyConfig.GAZE_TOUCH_VALIDATION) {
            message("Shared baseline requires the live gaze-tracking build."); return
        }
        val run = StudyCollection.run ?: return
        if (run.phase != "reading") return
        val store = MgazeNetCalibrationStore(context)
        val issue = store.compatibilityIssue(binding.root)
        val fingerprint = store.fingerprint()
        if (issue != null || fingerprint == null) { message(issue ?: "Calibrate the gaze tracker first."); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            message("Camera permission is required. Complete gaze calibration first."); return
        }
        participant = run.participant
        calibration = fingerprint
        attempt = ParticipantReadingBaseline.beginNewBaseline()
        ParticipantReadingBaseline.markCollecting(attempt, calibration)
        pipeline = ReadingFeaturePipeline(config)
        collector = MahalanobisBaselineCollector(config)
        vectors = 0; validVectors = 0; lineCount = 0
        val recording = StudyCollection.open(context, "reading_baseline",
            StudyBaselineProtocol.declaration(attempt, participant, calibration).put("rdf_config", StudyJson.value(config))) ?: return
        visit = recording
        recording.presentation(binding.tvArticleText, StudyJson.obj("diagnostic_dot" to false), binding.nsvArticleText)
        running = true
        adapter = ArticleReadingSession(binding.tvArticleText,
            coordinateStream = ReadingCoordinateStream.ARTICLE_SMOOTHED, motionCompensatedAids = false,
            onGeometry = { generation, geometry ->
                lineCount = geometry?.lines?.size ?: 0
                recording.record("layout_snapshot", StudyJson.obj("producer" to "rdf", "generation" to generation, "geometry" to geometry), StudyVisit.now())
            }, onInput = { generation, sample ->
                recording.record("rdf_input", StudyJson.obj("generation" to generation, "sample" to sample), sample.captureMs)
                sample.captureMs?.takeIf { it.isFinite() && it >= recording.startMs && it < Long.MAX_VALUE.toDouble() }
                    ?.let { pipeline.sample(sample.line, lineCount, sample.eligible, it.toLong()) }
            }, onBoundary = { reason ->
                recording.record("rdf_boundary", StudyJson.obj("reason" to reason), StudyVisit.now())
                if (StudyCaptureContext.presentationBoundary(reason)) recording.boundary(reason)
                when (reason) {
                    "invalid_sample", "non_monotonic_capture" -> Unit
                    "reading_scroll" -> pipeline.breakContinuity()
                    else -> pipeline.boundary()
                }
            })
        adapter?.checkGeometry()
        val started = SystemClock.elapsedRealtime()
        pipeline.tick(started)
        recording.record("rdf_schedule_tick", StudyJson.obj("tick_ms" to started), started.toDouble())
        val camera = MgazeNetGazeProvider(context, binding.tvArticleText, smoothForReading = true,
            rejectBlinks = true, preferStableFrameRate = true, maxOutputAgeMs = StudyConfig.READING_MAX_OUTPUT_AGE_MS)
        provider = camera
        val epoch = recording.providerStarted(calibration)
        val screen = binding.root.physicalDisplaySize()
        val rotation = binding.root.display.rotation * 90
        camera.onMetadata = { recording.metadata(epoch, it) }
        camera.onObservation = { observation ->
            recording.observation(epoch, observation, screen.x, screen.y, rotation)
            if (running && provider === camera) adapter?.record(observation)
        }
        camera.onFailure = { reason -> if (provider === camera) { message(reason); finish(false, "provider_failed") } }
        camera.setOnGaze { _, _ -> }
        binding.btnArticleReadingAids.text = "Finish"
        binding.btnRunGazeTest.isEnabled = false
        fragment.activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.post(tick)
        runCatching { camera.start(fragment.viewLifecycleOwner) }.onFailure { message(it.message ?: "Camera failed"); finish(false, "provider_failed") }
    }

    private fun collectTick() {
        val at = SystemClock.elapsedRealtime()
        visit?.record("rdf_schedule_tick", StudyJson.obj("tick_ms" to at), at.toDouble())
        pipeline.tick(at)?.let { vector ->
            val retained = collector.add(vector)
            vectors++; if (vector.isUsable(config)) validVectors++
            visit?.record("rdf_baseline_window", StudyJson.obj("feature_vector" to vector,
                "retained" to retained, "baseline_attempt_id" to attempt), at.toDouble())
        }
    }

    fun interrupt(reason: String) {
        dialog?.dismiss()
        if (running) finish(false, reason)
    }

    private fun finish(requestedCompletion: Boolean, reason: String) {
        if (!running || stopping) return
        collectTick()
        running = false; stopping = true
        handler.removeCallbacks(tick)
        val recording = visit ?: return
        recording.beginResult()
        recording.finish(reason)
        adapter?.close(reason); adapter = null
        val camera = provider
        val attemptId = attempt; val fingerprint = calibration; val person = participant
        fragment.activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val complete = {
            var rdfReady = false
            var coordinateRecorded = false
            try {
                val valid = requestedCompletion && recording.acceptsResults && ParticipantReadingBaseline.isCurrentSession(attemptId) &&
                    MgazeNetCalibrationStore(context).fingerprint() == fingerprint && StudyCollection.run?.id == recording.run.id
                val fit = if (valid) collector.freeze() else null
                if (fit != null) recording.record("rdf_baseline_fit", StudyJson.obj("fit" to fit,
                    "baseline_attempt_id" to attemptId, "baseline_participant_code" to person, "config" to config), StudyVisit.now())
                val profile = fit?.profile
                rdfReady = profile != null
                coordinateRecorded = valid
                if (valid && profile != null) ParticipantReadingBaseline.freeze(
                    ParticipantReadingBaseline.Entry(person, attemptId, fingerprint, profile))
                else if (valid) ParticipantReadingBaseline.markInvalid(attemptId, fingerprint)
                else ParticipantReadingBaseline.abandonCollecting(attemptId, fingerprint)
                val end = recording.endMs!!
                recording.record("shared_baseline_result", StudyJson.obj("baseline_attempt_id" to attemptId,
                    "completed" to valid, "coordinate_reference_status" to if (valid) SharedReadingBaseline.PENDING else "INTERRUPTED",
                    "rdf_status" to (fit?.status?.name ?: "INTERRUPTED"), "start_ms" to recording.startMs, "end_ms" to end), StudyVisit.now())
                if (valid) SharedReadingBaseline.recorded(SharedReadingBaseline.Entry(recording.packageId, recording.id,
                    attemptId, person, recording.run.installation, fingerprint, recording.run.clockDomain, recording.startMs, end))
                lastResult = if (valid) {
                    "Baseline recording saved. Behaviour reference (R/D/F): ${fit!!.status}. Coordinate reference: recorded, to be checked after transfer. " +
                        if (rdfReady) "Return Home and open the assigned article when ready." else "R/D/F is not ready. Check the setup before another baseline attempt."
                } else "Baseline interrupted. Check the setup and use Start for a new attempt."
            } finally {
                recording.endResult(); provider = null; visit = null; stopping = false
                if (!closed) {
                    binding.btnArticleReadingAids.text = "Start"
                    progress.text = "R/D/F: ${if (rdfReady) "ready" else "not ready"}\nRaw ref: ${if (coordinateRecorded) "fit pending" else "interrupted"}"
                    binding.btnRunGazeTest.isEnabled = true
                    message(lastResult)
                }
            }
        }
        if (camera == null) complete() else camera.stopAndThen { handler.post { complete() } }
    }

    fun close() {
        closed = true
        interrupt("baseline_view_destroyed")
        handler.removeCallbacks(tick)
        if (binding.root.viewTreeObserver.isAlive) binding.root.viewTreeObserver.removeOnPreDrawListener(preDraw)
    }
    private fun message(text: String) { if (!closed) Toast.makeText(context, text, Toast.LENGTH_LONG).show() }

    companion object {
        fun arguments() = Bundle().apply {
            putBoolean(SharedReadingBaseline.ARGUMENT, true)
            putParcelable("articleItem", Article("NewsMead", "", "Reading baseline", "", "",
                ReadingBaselineActivity.BASELINE_PASSAGE, "Baseline", "English", "", "", "shared-reading-baseline"))
        }
    }
}
