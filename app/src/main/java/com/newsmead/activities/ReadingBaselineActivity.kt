package com.newsmead.activities

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
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.newsmead.data.StudyConfig
import com.newsmead.gaze.ParticipantReadingBaseline
import com.newsmead.gaze.ReadingFeaturePipeline
import com.newsmead.gaze.mahalanobis.MahalanobisBaselineCollector
import com.newsmead.gaze.mahalanobis.MahalanobisConfig
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.reading.ArticleReadingSession
import com.newsmead.gaze.reading.ReadingCoordinateStream

/** Explicit unassisted reading; model inputs stay in memory and no aid runtime is attached. */
class ReadingBaselineActivity : AppCompatActivity() {
    private val config = MahalanobisConfig()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var body: TextView
    private lateinit var instructions: TextView
    private lateinit var participant: EditText
    private lateinit var status: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    private var pipeline = ReadingFeaturePipeline(config)
    private var collector = MahalanobisBaselineCollector(config)
    private var adapter: ArticleReadingSession? = null
    private var provider: MgazeNetGazeProvider? = null
    private var running = false
    private var stopping = false
    private var resumed = false
    private var destroyed = false
    private var frozen = false
    private var pendingPermission = false
    private var participantCode = ""
    private var fingerprint = ""
    private var sessionId = ""
    private var startedAt = 0L
    private var lineCount = 0
    private var vectors = 0
    private var validVectors = 0
    private var latestWindowSummary = "Waiting for gaze samples"
    private val intervalExclusions = linkedMapOf<String, Int>()
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingPermission
        pendingPermission = false
        if (requested && granted && resumed && !destroyed) beginAttempt()
        else if (requested) status.text = "Camera permission is required. Press Start when ready."
    }
    private val preDraw = ViewTreeObserver.OnPreDrawListener { adapter?.checkGeometry(); true }
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            collectTick()
            status.text = "Valid $validVectors/$vectors (need ${config.minimumRetainedWindows}); ${(now() - startedAt) / 1000}s\n" +
                "Covered ${collector.coveredDurationMs / 1000}s/${config.minimumBaselineDurationMs / 1000}s\n" +
                latestWindowSummary
            handler.postDelayed(this, config.updateIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(MaterialButton(this).apply { text = "Back"; setOnClickListener { finish() } })
        root.addView(TextView(this).apply { text = "Unassisted reading baseline"; textSize = 22f })
        instructions = TextView(this).apply {
            text = "Complete normal eye-tracker calibration first. Read naturally and scroll by hand. A ${config.windowMs / 1000}-second window must fill first; then collect at least ${config.minimumRetainedWindows} valid windows and ${config.minimumBaselineDurationMs / 1000} seconds of covered valid reading. A new baseline is required after app restart, participant change, or recalibration."
        }
        root.addView(instructions)
        participant = EditText(this).apply { hint = "Participant code"; isSingleLine = true }
        root.addView(participant)
        status = TextView(this).apply { minLines = 3; maxLines = 3 }
        root.addView(status)
        startButton = MaterialButton(this).apply { text = "Start"; setOnClickListener { requestStart() } }
        finishButton = MaterialButton(this).apply { text = "Finish and freeze"; setOnClickListener { finishAttempt() } }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(startButton); addView(finishButton)
        })
        body = TextView(this).apply {
            text = BASELINE_PASSAGE
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP)
            setPadding(dp(8), dp(8), dp(8), dp(24))
        }
        val scroll = NestedScrollView(this).apply { addView(body) }
        scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY ->
            if (running && y != oldY) adapter?.checkGeometry()
        })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        body.viewTreeObserver.addOnPreDrawListener(preDraw)
        updateButtons()
        root.post { refreshReadyState() }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        root.post { refreshReadyState() }
    }

    private fun refreshReadyState() {
        if (running || stopping || destroyed) return
        val store = MgazeNetCalibrationStore(this)
        val existing = ParticipantReadingBaseline.current(store.fingerprint())
        frozen = existing != null
        if (existing != null) {
            participant.setText(existing.participantCode)
            status.text = "Baseline frozen for this session. Return to article reading, or explicitly start a new session."
        } else status.text = store.compatibilityIssue(root) ?: "Ready. Enter a participant code and press Start to use the camera."
        updateButtons()
    }

    private fun updateButtons() {
        if (destroyed) return
        instructions.visibility = if (running) View.GONE else View.VISIBLE
        participant.visibility = if (running) View.GONE else View.VISIBLE
        startButton.visibility = if (running) View.GONE else View.VISIBLE
        finishButton.visibility = if (running) View.VISIBLE else View.GONE
        participant.isEnabled = !running && !stopping && !frozen
        startButton.text = if (frozen) "Start new session" else "Start / retry baseline"
        startButton.isEnabled = !running && !stopping && MgazeNetCalibrationStore(this).compatibilityIssue(root) == null
        finishButton.isEnabled = running && !stopping
    }

    private fun requestStart() {
        if (!resumed || running || stopping) return
        if (frozen) {
            frozen = false
            ParticipantReadingBaseline.beginNewBaseline()
            participant.setText("")
            status.text = "Enter the participant code and press Start for the new session."
            updateButtons()
            return
        }
        if (participant.text.toString().trim().isEmpty()) { participant.error = "Enter a participant code"; return }
        val issue = MgazeNetCalibrationStore(this).compatibilityIssue(root)
        if (issue != null) { status.text = issue; updateButtons(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingPermission = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else beginAttempt()
    }

    private fun beginAttempt() {
        if (!resumed || destroyed || running || stopping || frozen) return
        val store = MgazeNetCalibrationStore(this)
        val issue = store.compatibilityIssue(root)
        val calibration = store.fingerprint()
        participantCode = participant.text.toString().trim()
        if (issue != null || calibration == null || participantCode.isEmpty()) {
            status.text = issue ?: "Participant code and compatible calibration are required."
            return
        }
        fingerprint = calibration
        sessionId = ParticipantReadingBaseline.beginNewBaseline()
        participant.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(participant.windowToken, 0)
        pipeline = ReadingFeaturePipeline(config)
        collector = MahalanobisBaselineCollector(config)
        vectors = 0; validVectors = 0
        latestWindowSummary = "Waiting for gaze samples"
        intervalExclusions.clear()
        lineCount = 0
        startedAt = now()
        running = true
        adapter = ArticleReadingSession(body,
            coordinateStream = ReadingCoordinateStream.ARTICLE_SMOOTHED,
            motionCompensatedAids = false,
            onGeometry = { _, geometry -> lineCount = geometry?.lines?.size ?: 0 },
            onInput = { _, sample ->
                if (!sample.eligible) {
                    val reason = sample.exclusion ?: "unknown"
                    intervalExclusions[reason] = (intervalExclusions[reason] ?: 0) + 1
                }
                val capture = sample.captureMs
                if (running && capture != null && capture.isFinite() && capture >= startedAt && capture < Long.MAX_VALUE.toDouble())
                    pipeline.sample(sample.line, lineCount, sample.eligible, capture.toLong())
            },
            onBoundary = { reason ->
                when (reason) {
                    "invalid_sample", "non_monotonic_capture" -> Unit
                    "reading_scroll" -> pipeline.breakContinuity()
                    else -> pipeline.boundary()
                }
            })
        adapter?.checkGeometry()
        pipeline.tick(startedAt)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val camera = MgazeNetGazeProvider(this, body, smoothForReading = true, rejectBlinks = true)
        provider = camera
        camera.onObservation = { observation -> if (running && provider === camera) adapter?.record(observation) }
        camera.onFailure = { reason ->
            if (provider === camera && !destroyed)
                stopCollection { status.text = "Baseline stopped: $reason. Press Start to retry." }
        }
        camera.setOnGaze { _, _ -> }
        updateButtons()
        handler.post(tick)
        runCatching { camera.start(this) }.onFailure { error ->
            stopCollection { status.text = "Camera could not start: ${error.message}. Press Start to retry." }
        }
    }

    private fun collectTick() {
        pipeline.tick(now())?.let { vector ->
            collector.add(vector)
            vectors++
            if (vector.isUsable(config)) validVectors++
            val reason = intervalExclusions.maxByOrNull { it.value }?.key
            latestWindowSummary = "${vector.quality}: ${(vector.validFraction * 100).toInt()}% valid gaze" +
                (reason?.let { "; $it" } ?: "")
            intervalExclusions.clear()
        }
    }

    private fun finishAttempt() {
        if (!running || stopping) return
        collectTick()
        stopCollection {
            val store = MgazeNetCalibrationStore(this)
            if (!ParticipantReadingBaseline.isCurrentSession(sessionId) || store.fingerprint() != fingerprint || store.compatibilityIssue(root) != null)
                status.text = "Baseline rejected: session or calibration changed. Press Start to retry."
            else {
                val fit = collector.freeze()
                val profile = fit.profile
                if (profile == null) status.text = "Baseline invalid: ${fit.status}. No profile saved. Press Start to retry."
                else {
                    ParticipantReadingBaseline.freeze(ParticipantReadingBaseline.Entry(participantCode, sessionId, fingerprint, profile))
                    frozen = true
                    status.text = "Baseline frozen. Return to article reading in this session."
                }
            }
        }
    }

    /** Provider ownership is retained until asynchronous camera close completes. */
    private fun stopCollection(after: () -> Unit = {}) {
        if (stopping) return
        running = false
        handler.removeCallbacks(tick)
        adapter?.close("baseline_stopped"); adapter = null
        pipeline.breakContinuity()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val closing = provider
        stopping = closing != null
        updateButtons()
        val complete = {
            if (provider === closing) provider = null
            stopping = false
            if (!destroyed) { after(); updateButtons() }
        }
        if (closing == null) complete() else closing.stopAndThen { runOnUiThread { complete() } }
    }

    override fun onPause() {
        resumed = false
        if (running) stopCollection { status.text = "Baseline interrupted. Press Start to retry; paused time is excluded." }
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        if (body.viewTreeObserver.isAlive) body.viewTreeObserver.removeOnPreDrawListener(preDraw)
        stopCollection()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        // Same fixed provisional reading material as the working Mahalanobis branch.
        private val BASELINE_PASSAGE = """
            On quiet mornings, Lina walked to the community library before the streets became busy. She liked the wide windows, the wooden tables, and the patient rhythm of pages turning nearby. The familiar room made it easier to settle into a story without rushing.

            One week, the librarian displayed a report about gardens built above crowded buildings. These rooftop spaces reduced heat, absorbed rainwater, and gave residents a place to grow herbs. Volunteers recorded which plants survived strong sunlight and which needed shade during the afternoon.

            Interpreting the results required care because the buildings differed in height, wind exposure, and access to water. A plant that struggled on one roof sometimes flourished only two streets away. The researchers therefore compared repeated observations instead of trusting a single measurement.

            Lina reread that explanation and noticed how easily one dramatic result could hide the larger pattern. She wrote a short note in the margin, then continued to a diagram showing the temperature changes across several months. The evidence became clearer when she connected the sentences to the chart.

            By noon, the room had grown brighter and more readers had arrived. Lina closed the report, returned it to the desk, and carried the main lesson home: careful conclusions depend on consistent observations gathered under different conditions.
        """.trimIndent()
    }
}
