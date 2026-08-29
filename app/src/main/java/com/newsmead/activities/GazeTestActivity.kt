package com.newsmead.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.newsmead.data.StudyConfig
import com.newsmead.databinding.ActivityGazeTestBinding
import com.newsmead.gaze.CalibrationPointCollector
import com.newsmead.gaze.CalibrationStore
import com.newsmead.gaze.CalibrationView
import com.newsmead.gaze.DriftCorrection
import com.newsmead.gaze.DetailedTelemetryMode
import com.newsmead.gaze.FixationWindowFilter
import com.newsmead.gaze.FpsSummaryAccumulator
import com.newsmead.gaze.GazeMapper
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.GazeAccuracySessionLog
import com.newsmead.gaze.LocalCalibratedGazeProvider
import com.newsmead.gaze.LocalGazeSources
import com.newsmead.gaze.LocalRawGazeSource
import com.newsmead.gaze.ReadingSpatialMetrics
import com.newsmead.gaze.PostureProfile
import com.newsmead.gaze.PostureSummaryAccumulator
import java.util.Locale
import kotlin.math.hypot

/**
 * Gaze accuracy check + drift re-calibration (docs/calibration-design.md §7.2).
 *
 * Runs the live calibrated pipeline, then measures accuracy over a 3x3 grid
 * using the SHARED capture engine (CalibrationPointCollector -> CalibrationView +
 * FixationWindowFilter), identical to full calibration. Because drift from
 * posture shift is mostly bias/gain, it fits an affine screen-space correction on
 * top of the untouched 16-point map, reports an honest leave-one-out estimate of
 * the corrected error (in px and line-heights), and lets the researcher Apply it,
 * Revert to the base calibration, run a Full recalibration, or Measure again.
 * The affine correction and the raw calibration are never conflated.
 */
class GazeTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGazeTestBinding
    private var provider: GazeProvider? = null
    private var localProvider: LocalCalibratedGazeProvider? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private lateinit var collector: CalibrationPointCollector

    private var lineHeightPx = 1f
    private var targets: List<PointF> = emptyList()
    private var pointIndex = 0
    private var attempts = 0
    private val observations = ArrayList<DriftCorrection.Observation>()
    private val observationTargetIndices = ArrayList<Int>()

    // Drift correction the live pipeline currently runs with, plus the candidate
    // fitted from the latest measurement (pending researcher approval).
    private var activeCorrection: DriftCorrection? = null
    private var pendingCorrection: DriftCorrection? = null
    private var pendingPreMedianPx = 0f
    private var pendingPostMedianPx = 0f
    private var calibrationPointCount = 0
    private var postureProfile: PostureProfile? = null

    private var accuracySessionLog: GazeAccuracySessionLog? = null
    @Volatile private var sampleWindowActive = false
    private val pointPipelineSamples = ArrayList<LocalCalibratedGazeProvider.PipelineDiagnostics>()
    private val pointSourceEvents = ArrayList<LocalRawGazeSource.Diagnostics>()
    private val pointFps = FpsSummaryAccumulator()
    private val runFps = FpsSummaryAccumulator()
    private val pointPosture = PostureSummaryAccumulator()
    private val runPosture = PostureSummaryAccumulator()
    private var telemetryMode = DetailedTelemetryMode.ON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGazeTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableImmersiveMode()
        lineHeightPx = computeLineHeightPx()
        tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
        } catch (e: RuntimeException) {
            Log.w(TAG, "ToneGenerator unavailable; runs without audio cues", e)
            null
        }
        collector = CalibrationPointCollector(binding.calibrationView, tone)
        collector.setOnSampleWindowChanged { active ->
            sampleWindowActive = active
            if (active) {
                pointPipelineSamples.clear()
                pointSourceEvents.clear()
                pointFps.reset()
                pointPosture.reset()
            }
        }
        binding.accuracyButton.setOnClickListener { requestMeasurementLabel() }
        binding.btnApply.setOnClickListener { applyPendingCorrection() }
        binding.btnRevert.setOnClickListener { revertToCalibration() }
        binding.btnFullRecal.setOnClickListener { launchFullRecalibration() }
        binding.btnRemeasure.setOnClickListener { requestMeasurementLabel() }
        startGaze()
    }

    private fun startGaze() {
        val samples = CalibrationStore.load(this)
        if (samples == null) {
            binding.hintText.text = getString(com.newsmead.R.string.gaze_test_no_calibration)
            binding.btnFullRecal.visibility = View.VISIBLE
            binding.gatePanel.visibility = View.VISIBLE
            return
        }
        calibrationPointCount = samples.size
        postureProfile = PostureProfile.fromCalibration(samples)
        val mapper = try {
            GazeMapper(samples)
        } catch (e: Exception) {
            Log.e(TAG, "Calibration fit failed", e)
            binding.hintText.text = getString(com.newsmead.R.string.gaze_test_fit_failed)
            return
        }
        provider?.stop()
        val rawSource = LocalGazeSources.create(this)
        rawSource.setOnFps { fps ->
            runOnUiThread {
                binding.gazeDot.setFps(fps)
                if (sampleWindowActive) {
                    pointFps.add(fps)
                    runFps.add(fps)
                }
            }
        }
        activeCorrection = CalibrationStore.loadDriftCorrection(this)
        val localProvider = LocalCalibratedGazeProvider(
            mapper,
            rawSource,
            activeCorrection,
            postureProfile,
        ).apply {
            setOnGaze { x, y -> runOnUiThread { onGaze(x, y) } }
            setOnPostureAssessment { assessment ->
                if (sampleWindowActive) {
                    pointPosture.add(assessment)
                    runPosture.add(assessment)
                }
            }
            start(this@GazeTestActivity)
        }
        this.localProvider = localProvider
        provider = localProvider
        binding.hintText.text = getString(com.newsmead.R.string.gaze_test_hint)
        binding.accuracyButton.visibility = View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startGaze()
        }
    }

    private fun onGaze(x: Float, y: Float) {
        binding.gazeDot.setGaze(x, y)
        // Feed the shared collector the MAPPED screen px (not raw features): the
        // drift correction is affine in screen space, so we measure there.
        collector.onRawSample(x, y, System.currentTimeMillis())
    }

    // --- Measurement pass (shared capture engine) --------------------------

    private fun requestMeasurementLabel() {
        val input = EditText(this).apply {
            hint = "e.g. seated_normal-light_no-glasses_run-1"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val detailed = CheckBox(this).apply {
            text = "Detailed per-frame telemetry ON"
            isChecked = telemetryMode.enabled
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(input)
            addView(detailed)
        }
        AlertDialog.Builder(this)
            .setTitle("Accuracy run setup")
            .setMessage("Describe the run and select the same telemetry mode used for its calibration.")
            .setView(container)
            .setPositiveButton("Start") { _, _ ->
                telemetryMode = if (detailed.isChecked) {
                    DetailedTelemetryMode.ON
                } else {
                    DetailedTelemetryMode.OFF
                }
                startMeasurement(input.text.toString().trim().ifEmpty { "unlabelled" })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startMeasurement(runLabel: String) {
        val w = binding.calibrationView.width
        val h = binding.calibrationView.height
        if (w == 0 || h == 0) {
            binding.calibrationView.post { startMeasurement(runLabel) }
            return
        }
        accuracySessionLog?.finish("restarted", runFps.snapshot(), runPosture.snapshot())
        configureDetailedTelemetry()
        runFps.reset()
        runPosture.reset()
        accuracySessionLog = GazeAccuracySessionLog(
            context = this,
            runLabel = runLabel,
            telemetryMode = telemetryMode,
            screenWidthPx = w,
            screenHeightPx = h,
            densityDpi = resources.displayMetrics.densityDpi,
            lineHeightPx = lineHeightPx,
            calibrationPointCount = calibrationPointCount,
            activeCorrection = activeCorrection,
            postureProfile = postureProfile,
        )
        targets = computeTargets(w, h)
        observations.clear()
        observationTargetIndices.clear()
        pendingCorrection = null
        binding.accuracyButton.visibility = View.GONE
        binding.gatePanel.visibility = View.GONE
        binding.gazeDot.clearEstimate()
        startPreRunCountdown { runPoint(0) }
    }

    /** 3x3 grid at 20/50/80% - deliberately offset from the 16 calibration dots. */
    private fun computeTargets(w: Int, h: Int): List<PointF> {
        val xs = floatArrayOf(w * 0.2f, w * 0.5f, w * 0.8f)
        val ys = floatArrayOf(h * 0.2f, h * 0.5f, h * 0.8f)
        return buildList { for (y in ys) for (x in xs) add(PointF(x, y)) }
    }

    private fun runPoint(index: Int) {
        pointIndex = index
        attempts = 0
        captureCurrent()
    }

    private fun captureCurrent() {
        val p = targets[pointIndex]
        binding.hintText.text =
            getString(com.newsmead.R.string.gaze_test_accuracy_progress, pointIndex + 1, targets.size)
        updateMiniMap()
        collector.capture(p.x, p.y) { result ->
            if (result.status == FixationWindowFilter.Status.ACCEPTED) {
                onPointCaptured(result)
            } else {
                onPointFailed(result)
            }
        }
    }

    private fun onPointCaptured(result: FixationWindowFilter.Result) {
        val p = targets[pointIndex]
        accuracySessionLog?.logPoint(
            pointIndex = pointIndex,
            attempt = attempts + 1,
            targetX = p.x,
            targetY = p.y,
            result = result,
            fpsSummary = pointFps.snapshot(),
            pipelineSamples = pointPipelineSamples.toList(),
            sourceEvents = pointSourceEvents.toList(),
            postureSummary = pointPosture.snapshot(),
        )
        observations.add(DriftCorrection.Observation(result.medianX, result.medianY, p.x, p.y))
        observationTargetIndices.add(pointIndex)
        val err = hypot(result.medianX - p.x, result.medianY - p.y)
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "point %d/%d target=(%.0f, %.0f) est=(%.0f, %.0f) err=%.0f px dy=%.0f retained=%d",
                pointIndex + 1, targets.size, p.x, p.y, result.medianX, result.medianY,
                err, result.medianY - p.y, result.retainedCount,
            ),
        )
        handler.postDelayed({ advance() }, CONFIRM_MS)
    }

    private fun onPointFailed(result: FixationWindowFilter.Result) {
        val p = targets[pointIndex]
        accuracySessionLog?.logPoint(
            pointIndex = pointIndex,
            attempt = attempts + 1,
            targetX = p.x,
            targetY = p.y,
            result = result,
            fpsSummary = pointFps.snapshot(),
            pipelineSamples = pointPipelineSamples.toList(),
            sourceEvents = pointSourceEvents.toList(),
            postureSummary = pointPosture.snapshot(),
        )
        attempts++
        Log.w(TAG, "point ${pointIndex + 1} attempt $attempts failed: ${result.status} raw=${result.rawCount}")
        if (attempts < MAX_ATTEMPTS) {
            handler.postDelayed({ captureCurrent() }, RETRY_PAUSE_MS)
        } else {
            Log.w(TAG, "point ${pointIndex + 1} skipped after $MAX_ATTEMPTS attempts")
            handler.postDelayed({ advance() }, RETRY_PAUSE_MS)
        }
    }

    private fun advance() {
        val next = pointIndex + 1
        if (next < targets.size) runPoint(next) else finishMeasurement()
    }

    private fun updateMiniMap() {
        val states = targets.indices.map { i ->
            when {
                i == pointIndex -> CalibrationView.MiniDotState.CURRENT
                i < pointIndex -> CalibrationView.MiniDotState.DONE
                else -> CalibrationView.MiniDotState.PENDING
            }
        }
        binding.calibrationView.setMiniMap(states)
    }

    // --- Result + remedy gate ----------------------------------------------

    private fun finishMeasurement() {
        binding.calibrationView.hideTarget()
        binding.calibrationView.setMiniMap(emptyList())
        if (observations.size < DriftCorrection.MIN_OBSERVATIONS) {
            binding.hintText.text = getString(com.newsmead.R.string.gaze_recal_insufficient)
            accuracySessionLog?.finish("insufficient_points", runFps.snapshot(), runPosture.snapshot())
            showGate(canApply = false)
            return
        }

        val readingSummary = ReadingSpatialMetrics.summarize(
            observations.mapIndexed { index, observation ->
                val targetIndex = observationTargetIndices[index]
                ReadingSpatialMetrics.Observation(
                    id = targetIndex,
                    label = targetLabel(targetIndex),
                    targetX = observation.targetX,
                    targetY = observation.targetY,
                    predictedX = observation.predictedX,
                    predictedY = observation.predictedY,
                )
            },
            lineHeightPx,
        ) ?: run {
            accuracySessionLog?.finish("summary_failed", runFps.snapshot(), runPosture.snapshot())
            showGate(canApply = false)
            return
        }

        val newFit = DriftCorrection.fit(observations)
        val looPostPx = DriftCorrection.leaveOneOutMedianPx(observations)
        if (newFit != null && looPostPx != null) {
            pendingCorrection = activeCorrection?.let { DriftCorrection.compose(newFit, it) } ?: newFit
            pendingPreMedianPx = readingSummary.medianErrorPx
            pendingPostMedianPx = looPostPx
        } else {
            pendingCorrection = null
        }

        accuracySessionLog?.logSummary(
            summary = readingSummary,
            lineHeightPx = lineHeightPx,
            estimatedCorrectedLooPx = looPostPx,
        )
        accuracySessionLog?.finish("completed", runFps.snapshot(), runPosture.snapshot())

        Log.i(
            TAG,
            String.format(
                Locale.US,
                "ACCURACY n=%d vertical median/p95/max=%.2f/%.2f/%.2f lines within1.2=%d/%d; " +
                    "2D median/p95/max=%.0f/%.0f/%.0f px; est. corrected(LOO)=%.0f px; active=%b",
                observations.size,
                readingSummary.medianVerticalPx / lineHeightPx,
                readingSummary.p95VerticalPx / lineHeightPx,
                readingSummary.maxVerticalPx / lineHeightPx,
                readingSummary.withinReference, readingSummary.points.size,
                readingSummary.medianErrorPx, readingSummary.p95ErrorPx, readingSummary.maxErrorPx,
                looPostPx ?: Float.NaN, activeCorrection != null,
            ),
        )

        binding.hintText.text = ""
        binding.gateText.text = buildGateSummary(readingSummary, looPostPx) +
            "\n\nDiagnostic log: ${accuracySessionLog?.fileName() ?: "unavailable"}"
        binding.gateText.setTextColor(
            if (readingSummary.meetsProvisionalReference) GREEN_COLOR else AMBER_COLOR,
        )
        showGate(canApply = pendingCorrection != null)
    }

    private fun buildGateSummary(
        summary: ReadingSpatialMetrics.Summary,
        looPostPx: Float?,
    ): String = buildString {
        appendLine("9-point live-pipeline spatial check")
        appendLine(String.format(
            Locale.US,
            "Vertical median %.1f · P95 %.1f · max %.1f lines",
            summary.medianVerticalPx / lineHeightPx,
            summary.p95VerticalPx / lineHeightPx,
            summary.maxVerticalPx / lineHeightPx,
        ))
        appendLine("Within 0.5/1.0/1.2 lines: ${summary.withinHalfLine}/${summary.points.size} · " +
            "${summary.withinOneLine}/${summary.points.size} · ${summary.withinReference}/${summary.points.size}")
        appendLine(String.format(
            Locale.US,
            "2-D median/P95/max: %.0f/%.0f/%.0f px",
            summary.medianErrorPx, summary.p95ErrorPx, summary.maxErrorPx,
        ))
        appendLine()
        appendLine("Individual targets (signed vertical; + is below target)")
        summary.points.sortedBy { it.id }.forEach { point ->
            appendLine(String.format(
                Locale.US,
                "P%d %-13s dx %+.0f px · dy %+.1f lines · total %.0f px",
                point.id + 1, point.label, point.dxPx, point.dyPx / lineHeightPx, point.errorPx,
            ))
        }
        appendLine("Worst: P${summary.worstVertical.id + 1} ${summary.worstVertical.label} " +
            String.format(Locale.US, "at %.1f vertical lines", summary.worstVertical.verticalLines))
        appendLine("Rows (top/middle/bottom): ${axisSummary(summary, true)}")
        appendLine("Columns (left/center/right): ${axisSummary(summary, false)}")
        looPostPx?.let {
            appendLine()
            appendLine(String.format(
                Locale.US,
                "Global affine candidate: estimated leave-one-out 2-D median %.0f px (%.1f lines).",
                it, it / lineHeightPx,
            ))
            appendLine("This estimate is not a verified correction and may not repair a one-sided or local failure.")
        }
        appendLine()
        appendLine(
            if (summary.meetsProvisionalReference) {
                "Meets the provisional spatial reference: every measured target is within 1.2 vertical lines."
            } else {
                "Does not meet the provisional spatial reference; inspect the target and region pattern above."
            },
        )
        append("This check does not establish reading-line compatibility; that requires direct reading validation.")
    }

    private fun showGate(canApply: Boolean) {
        binding.btnApply.visibility = if (canApply) View.VISIBLE else View.GONE
        binding.btnRevert.visibility = if (activeCorrection != null) View.VISIBLE else View.GONE
        binding.btnFullRecal.visibility = View.VISIBLE
        binding.btnRemeasure.visibility = View.VISIBLE
        binding.gatePanel.visibility = View.VISIBLE
    }

    private fun configureDetailedTelemetry() {
        val current = localProvider ?: return
        if (telemetryMode.enabled) {
            current.setOnSourceDiagnostics { diagnostics ->
                runOnUiThread {
                    if (sampleWindowActive) pointSourceEvents.add(diagnostics)
                }
            }
            current.setOnDiagnostics { diagnostics ->
                runOnUiThread {
                    if (sampleWindowActive) pointPipelineSamples.add(diagnostics)
                }
            }
        } else {
            current.setOnSourceDiagnostics(null)
            current.setOnDiagnostics(null)
        }
    }

    private fun applyPendingCorrection() {
        val correction = pendingCorrection ?: return
        CalibrationStore.saveDriftCorrection(this, correction, pendingPreMedianPx, pendingPostMedianPx)
        CalibrationStore.appendRecalibrationHistory(this, "applied", pendingPreMedianPx, pendingPostMedianPx)
        pendingCorrection = null
        binding.gatePanel.visibility = View.GONE
        binding.hintText.text = getString(com.newsmead.R.string.gaze_test_correction_applied)
        startGaze() // restart so the live dot immediately uses the correction
    }

    private fun revertToCalibration() {
        CalibrationStore.clearDriftCorrection(this)
        CalibrationStore.appendRecalibrationHistory(this, "reverted", pendingPreMedianPx, Float.NaN)
        pendingCorrection = null
        binding.gatePanel.visibility = View.GONE
        binding.hintText.text = getString(com.newsmead.R.string.gaze_recal_reverted)
        startGaze()
    }

    private fun launchFullRecalibration() {
        provider?.stop()
        provider = null
        startActivity(Intent(this, GazeCalibrationActivity::class.java))
        finish()
    }

    // --- Helpers -----------------------------------------------------------

    private fun startPreRunCountdown(onFinished: () -> Unit) {
        var value = COUNTDOWN_START
        binding.countdownText.visibility = View.VISIBLE
        val tick = object : Runnable {
            override fun run() {
                if (value == 0) {
                    binding.countdownText.visibility = View.GONE
                    onFinished()
                    return
                }
                binding.countdownText.text = value.toString()
                value--
                handler.postDelayed(this, COUNTDOWN_STEP_MS)
            }
        }
        handler.post(tick)
    }

    private fun targetLabel(index: Int): String {
        val rows = arrayOf("top", "middle", "bottom")
        val cols = arrayOf("left", "center", "right")
        return "${rows[index / 3]}-${cols[index % 3]}"
    }

    private fun axisSummary(summary: ReadingSpatialMetrics.Summary, rows: Boolean): String =
        (0..2).joinToString(" / ") { axis ->
            val values = summary.points
                .filter { if (rows) it.id / 3 == axis else it.id % 3 == axis }
                .map { it.verticalLines }
            if (values.isEmpty()) "—" else String.format(Locale.US, "%.1f", median(values))
        }

    /** See GazeCalibrationActivity.computeLineHeightPx - same article geometry. */
    private fun computeLineHeightPx(): Float {
        val dm = resources.displayMetrics
        val paint = TextPaint()
        paint.textSize = StudyConfig.ARTICLE_FONT_SIZE_DP * dm.density
        val fm = paint.fontMetrics
        val extraPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, dm)
        return (fm.descent - fm.ascent) * ARTICLE_LINE_SPACING_MULT + extraPx
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        collector.cancel()
        provider?.stop()
        tone?.release()
        accuracySessionLog?.finish("aborted", runFps.snapshot(), runPosture.snapshot())
    }

    companion object {
        private const val TAG = "GazeStage3"
        private const val CONFIRM_MS = 250L
        private const val RETRY_PAUSE_MS = 500L
        private const val MAX_ATTEMPTS = 2
        private const val TONE_VOLUME = 60
        private const val COUNTDOWN_START = 3
        private const val COUNTDOWN_STEP_MS = 1_000L

        private const val ARTICLE_LINE_SPACING_MULT = 1.6f

        private val GREEN_COLOR = Color.parseColor("#2E7D32")
        private val AMBER_COLOR = Color.parseColor("#B26A00")
    }
}
