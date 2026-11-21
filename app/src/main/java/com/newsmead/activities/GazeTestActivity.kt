package com.newsmead.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.newsmead.databinding.ActivityGazeTestBinding
import com.newsmead.gaze.GazeCoordinateFrame
import com.newsmead.gaze.gazeCoordinateFrame
import com.newsmead.gaze.mgazenet.CalibrationIdentity
import com.newsmead.gaze.mgazenet.MgazeNetAccuracyProtocol
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.mgazenet.MgazeNetCameraSource
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.mgazenet.MgazeNetPostFitTiming
import com.newsmead.gaze.mgazenet.NinePointAccuracySummary

/** Explicit-start raw MGazeNet check. No affine fit, correction, calibration write or quality gate. */
class GazeTestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGazeTestBinding
    private var provider: MgazeNetGazeProvider? = null
    private var closingProvider = false
    private val handler = Handler(Looper.getMainLooper())
    private var frame: GazeCoordinateFrame? = null
    private var target = -1
    private var onset = Double.NaN
    private var countdownStarted = false
    private var detailsExpanded = false
    private val samples = ArrayList<Pair<Float, Float>>()
    private val pointResults = ArrayList<NinePointAccuracySummary.PointResult>()
    private var permissionPending = false
    private var pxPerCmX = Double.NaN
    private var pxPerCmY = Double.NaN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGazeTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.keepScreenOn = false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        binding.hintText.text =
            "MGazeNet accuracy check. Eight targets are new and the centre is repeated. Results do not correct gaze."
        binding.accuracyButton.visibility = View.VISIBLE
        binding.accuracyButton.text = "Start MGazeNet check"
        binding.accuracyButton.setOnClickListener { requestStart() }
        binding.btnRemeasure.setOnClickListener { requestStart() }
        binding.btnFullRecal.setOnClickListener {
            startActivity(Intent(this, GazeCalibrationActivity::class.java))
            finish()
        }
        binding.btnExit.setOnClickListener { finish() }
        binding.btnToggleDetails.setOnClickListener {
            detailsExpanded = !detailsExpanded
            binding.gatePanel.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
            binding.btnToggleDetails.text = if (detailsExpanded) "Hide details" else "Details"
        }
    }

    private fun requestStart() {
        if (provider != null || closingProvider) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionPending = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        } else {
            start()
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == CAMERA_PERMISSION_REQUEST && permissionPending) {
            permissionPending = false
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) start()
        }
    }

    private fun start() {
        if (provider != null || closingProvider) return
        if (binding.calibrationView.width == 0 || binding.calibrationView.height == 0) {
            binding.calibrationView.post { start() }
            return
        }
        val issue = MgazeNetCalibrationStore(this).compatibilityIssue(binding.root)
        if (issue != null) {
            endError(issue)
            return
        }
        val metrics = resources.displayMetrics
        if (!metrics.xdpi.isFinite() || metrics.xdpi <= 0f || !metrics.ydpi.isFinite() || metrics.ydpi <= 0f) {
            endError("Physical display dimensions are unavailable; accuracy check stopped.")
            return
        }
        pxPerCmX = metrics.xdpi / 2.54
        pxPerCmY = metrics.ydpi / 2.54
        frame = binding.calibrationView.gazeCoordinateFrame()

        target = -1
        onset = Double.NaN
        countdownStarted = false
        detailsExpanded = false
        samples.clear()
        pointResults.clear()

        binding.accuracyResultOverlay.clearResults()
        binding.resultSummaryCard.visibility = View.GONE
        binding.resultActionsBar.visibility = View.GONE
        binding.gatePanel.visibility = View.GONE
        binding.countdownText.visibility = View.GONE
        binding.accuracyButton.visibility = View.GONE
        binding.hintText.visibility = View.VISIBLE
        binding.hintText.text = "Preparing MGazeNet…"
        binding.root.keepScreenOn = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val p = MgazeNetGazeProvider(this, binding.root)
        p.onFailure = { endError(it) }
        p.onObservation = { observation ->
            if (provider === p) {
                if (frame != binding.calibrationView.gazeCoordinateFrame()) {
                    endError("Target geometry changed; check stopped.")
                } else if (target == -1 && !countdownStarted && observation.reason == "coordinate") {
                    countdownStarted = true
                    binding.hintText.text = "Hold steady and follow each target"
                    countdown(3) {
                        if (provider === p) {
                            target = 0
                            showTarget()
                        }
                    }
                } else if (target in MgazeNetAccuracyProtocol.TARGET_FRACTIONS.indices &&
                    observation.reason == "coordinate" && onset.isFinite() &&
                    observation.captureMs != null &&
                    MgazeNetPostFitTiming.accepts(onset, observation.captureMs, observation.outputMs, allowLocalRetry = false)
                ) {
                    val gx = observation.x
                    val gy = observation.y
                    if (gx != null && gy != null) {
                        samples.add(gx to gy)
                    }
                }
            }
        }
        // No estimated dot is shown while measuring instructed targets.
        p.setOnGaze { _, _ -> }
        provider = p
        p.start(this)
        handler.post(progressTick)
        handler.postDelayed(
            {
                if (provider === p && target == -1 && !countdownStarted) {
                    endError("No fresh gaze output within 20 seconds. Check stopped.")
                }
            },
            START_TIMEOUT_MS,
        )
    }

    private fun countdown(n: Int, done: () -> Unit) {
        if (provider == null) return
        if (n == 0) {
            binding.countdownText.visibility = View.GONE
            done()
            return
        }
        binding.countdownText.visibility = View.VISIBLE
        binding.countdownText.text = n.toString()
        handler.postDelayed({ countdown(n - 1, done) }, COUNTDOWN_STEP_MS)
    }

    private val progressTick = object : Runnable {
        override fun run() {
            if (provider == null) return
            if (target in MgazeNetAccuracyProtocol.TARGET_FRACTIONS.indices && onset.isFinite()) {
                binding.calibrationView.setTargetProgress(
                    MgazeNetPostFitTiming.progress(
                        checkStartMs = onset,
                        nowMs = MgazeNetCameraSource.now(),
                        usableSamples = 0,
                        allowLocalRetry = false,
                    ),
                )
            }
            handler.postDelayed(this, PROGRESS_TICK_MS)
        }
    }

    private fun targetPoint() = MgazeNetAccuracyProtocol.target(frame!!, target)

    private fun showTarget() {
        samples.clear()
        onset = Double.NaN
        val p = targetPoint()
        val local = frame!!.toLocal(p.x, p.y)
        binding.hintText.visibility = View.VISIBLE
        binding.hintText.text = "Target ${target + 1} of ${MgazeNetAccuracyProtocol.TARGET_FRACTIONS.size}"
        binding.calibrationView.onNextTargetDraw {
            if (provider != null) {
                onset = MgazeNetCameraSource.now()
                handler.postDelayed({ finishTarget() }, TARGET_DURATION_MS)
            }
        }
        binding.calibrationView.showTarget(local.x, local.y, CalibrationIdentity.TARGET_CUE_MS)
    }

    private fun finishTarget() {
        if (provider == null || target !in MgazeNetAccuracyProtocol.TARGET_FRACTIONS.indices) return
        binding.calibrationView.setTargetProgress(1f)
        val p = targetPoint()
        val local = frame!!.toLocal(p.x, p.y)
        pointResults.add(
            NinePointAccuracySummary.buildPointResult(
                index = target + 1,
                targetScreenX = p.x,
                targetScreenY = p.y,
                targetLocalX = local.x,
                targetLocalY = local.y,
                samples = samples,
                pxPerCmX = pxPerCmX,
                pxPerCmY = pxPerCmY,
            ),
        )
        target++
        if (target == MgazeNetAccuracyProtocol.TARGET_FRACTIONS.size) {
            showCompletedResults()
        } else {
            showTarget()
        }
    }

    private fun showCompletedResults() {
        stopActiveRun()
        val summary = NinePointAccuracySummary.summarize(pointResults)
        binding.hintText.visibility = View.GONE
        binding.accuracyButton.visibility = View.GONE
        binding.accuracyResultOverlay.setResults(summary.points)
        binding.resultSummaryTitle.text = NinePointAccuracySummary.formatHeadline(summary)
        binding.resultSummaryMetrics.text = NinePointAccuracySummary.formatMetrics(summary)
        binding.resultSummaryCard.visibility = View.VISIBLE
        binding.gateText.text = NinePointAccuracySummary.formatDetails(summary)
        detailsExpanded = false
        binding.gatePanel.visibility = View.GONE
        binding.resultActionsBar.visibility = View.VISIBLE
        binding.btnToggleDetails.visibility = View.VISIBLE
        binding.btnToggleDetails.text = "Details"
        binding.btnRemeasure.visibility = View.VISIBLE
        binding.btnRemeasure.text = "Test again"
        binding.btnFullRecal.visibility = View.VISIBLE
        binding.btnFullRecal.text = "Recalibrate"
        binding.btnExit.visibility = View.VISIBLE
    }

    private fun endError(message: String) {
        stopActiveRun()
        binding.accuracyResultOverlay.clearResults()
        binding.resultSummaryCard.visibility = View.GONE
        binding.accuracyButton.visibility = View.GONE
        binding.hintText.visibility = View.VISIBLE
        binding.gateText.text = message
        detailsExpanded = false
        binding.gatePanel.visibility = View.VISIBLE
        binding.resultActionsBar.visibility = View.VISIBLE
        binding.btnToggleDetails.visibility = View.GONE
        binding.btnRemeasure.visibility = View.VISIBLE
        binding.btnRemeasure.text = "Try again"
        binding.btnFullRecal.visibility = View.VISIBLE
        binding.btnFullRecal.text = "Recalibrate"
        binding.btnExit.visibility = View.VISIBLE
    }

    private fun stopActiveRun() {
        handler.removeCallbacksAndMessages(null)
        val activeProvider = provider
        provider = null
        if (activeProvider != null) {
            closingProvider = true
            binding.btnRemeasure.isEnabled = false
            activeProvider.stopAndThen {
                runOnUiThread {
                    closingProvider = false
                    if (!isFinishing && !isDestroyed) {
                        binding.btnRemeasure.isEnabled = true
                    }
                }
            }
        }
        binding.root.keepScreenOn = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.calibrationView.hideTarget()
        binding.countdownText.visibility = View.GONE
    }

    override fun onStop() {
        permissionPending = false
        if (provider != null) endError("Check interrupted.")
        super.onStop()
    }

    override fun onDestroy() {
        stopActiveRun()
        super.onDestroy()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 43
        private const val START_TIMEOUT_MS = 20_000L
        private const val COUNTDOWN_STEP_MS = 1_000L
        private const val PROGRESS_TICK_MS = 50L
        private const val TARGET_DURATION_MS =
            (MgazeNetPostFitTiming.COMPARISON_CAPTURE_END_MS + MgazeNetPostFitTiming.OUTPUT_GRACE_MS).toLong()
    }
}
