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
import android.util.Log
import android.util.TypedValue
import android.view.View
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
import com.newsmead.gaze.FixationWindowFilter
import com.newsmead.gaze.GazeMapper
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.LocalCalibratedGazeProvider
import com.newsmead.gaze.LocalGazeSources
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
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
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private lateinit var collector: CalibrationPointCollector

    private var lineHeightPx = 1f
    private var targets: List<PointF> = emptyList()
    private var pointIndex = 0
    private var attempts = 0
    private val observations = ArrayList<DriftCorrection.Observation>()

    // Drift correction the live pipeline currently runs with, plus the candidate
    // fitted from the latest measurement (pending researcher approval).
    private var activeCorrection: DriftCorrection? = null
    private var pendingCorrection: DriftCorrection? = null
    private var pendingPreMedianPx = 0f
    private var pendingPostMedianPx = 0f

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
        binding.accuracyButton.setOnClickListener { startMeasurement() }
        binding.btnApply.setOnClickListener { applyPendingCorrection() }
        binding.btnRevert.setOnClickListener { revertToCalibration() }
        binding.btnFullRecal.setOnClickListener { launchFullRecalibration() }
        binding.btnRemeasure.setOnClickListener { startMeasurement() }
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
        val mapper = try {
            GazeMapper(samples)
        } catch (e: Exception) {
            Log.e(TAG, "Calibration fit failed", e)
            binding.hintText.text = getString(com.newsmead.R.string.gaze_test_fit_failed)
            return
        }
        provider?.stop()
        val rawSource = LocalGazeSources.create(this)
        rawSource.setOnFps { fps -> runOnUiThread { binding.gazeDot.setFps(fps) } }
        activeCorrection = CalibrationStore.loadDriftCorrection(this)
        provider = LocalCalibratedGazeProvider(mapper, rawSource, activeCorrection).apply {
            setOnGaze { x, y -> runOnUiThread { onGaze(x, y) } }
            start(this@GazeTestActivity)
        }
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

    private fun startMeasurement() {
        val w = binding.calibrationView.width
        val h = binding.calibrationView.height
        if (w == 0 || h == 0) {
            binding.calibrationView.post { startMeasurement() }
            return
        }
        targets = computeTargets(w, h)
        observations.clear()
        pendingCorrection = null
        binding.accuracyButton.visibility = View.GONE
        binding.gatePanel.visibility = View.GONE
        binding.gazeDot.clearEstimate()
        runPoint(0)
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
        observations.add(DriftCorrection.Observation(result.medianX, result.medianY, p.x, p.y))
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
            showGate(canApply = false)
            return
        }

        val errorsPx = observations.map { hypot(it.predictedX - it.targetX, it.predictedY - it.targetY) }
        val medianPx = median(errorsPx)
        val p95Px = percentile(errorsPx, 0.95)
        val vertMedianPx = median(observations.map { abs(it.predictedY - it.targetY) })
        val medianLines = medianPx / lineHeightPx

        val newFit = DriftCorrection.fit(observations)
        val looPostPx = DriftCorrection.leaveOneOutMedianPx(observations)
        if (newFit != null && looPostPx != null) {
            pendingCorrection = activeCorrection?.let { DriftCorrection.compose(newFit, it) } ?: newFit
            pendingPreMedianPx = medianPx
            pendingPostMedianPx = looPostPx
        } else {
            pendingCorrection = null
        }

        Log.i(
            TAG,
            String.format(
                Locale.US,
                "ACCURACY n=%d median=%.0f px (%.2f lines) p95=%.0f px vert=%.0f px; est. corrected(LOO)=%.0f px; active=%b",
                observations.size, medianPx, medianLines, p95Px, vertMedianPx,
                looPostPx ?: Float.NaN, activeCorrection != null,
            ),
        )

        binding.hintText.text = ""
        binding.gateText.text = buildGateSummary(medianPx, medianLines, p95Px, vertMedianPx, looPostPx)
        binding.gateText.setTextColor(bandColor(medianLines))
        showGate(canApply = pendingCorrection != null)
    }

    private fun buildGateSummary(
        medianPx: Float,
        medianLines: Float,
        p95Px: Float,
        vertMedianPx: Float,
        looPostPx: Float?,
    ): String {
        val summary = getString(
            com.newsmead.R.string.gaze_recal_summary,
            medianPx, medianLines, p95Px, vertMedianPx,
        )
        val estimate = looPostPx?.let {
            "\n" + getString(com.newsmead.R.string.gaze_recal_estimate, it, it / lineHeightPx)
        } ?: ""
        val advice = getString(
            when {
                medianLines <= GREEN_LINES -> com.newsmead.R.string.gaze_recal_advice_good
                medianLines <= AMBER_LINES -> com.newsmead.R.string.gaze_recal_advice_ok
                else -> com.newsmead.R.string.gaze_recal_advice_poor
            },
        )
        return "$summary$estimate\n\n$advice"
    }

    private fun showGate(canApply: Boolean) {
        binding.btnApply.visibility = if (canApply) View.VISIBLE else View.GONE
        binding.btnRevert.visibility = if (activeCorrection != null) View.VISIBLE else View.GONE
        binding.btnFullRecal.visibility = View.VISIBLE
        binding.btnRemeasure.visibility = View.VISIBLE
        binding.gatePanel.visibility = View.VISIBLE
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

    private fun bandColor(lines: Float): Int = when {
        lines <= GREEN_LINES -> GREEN_COLOR
        lines <= AMBER_LINES -> AMBER_COLOR
        else -> RED_COLOR
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

    private fun percentile(values: List<Float>, p: Double): Float {
        val sorted = values.sorted()
        return sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
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
    }

    companion object {
        private const val TAG = "GazeStage3"
        private const val CONFIRM_MS = 250L
        private const val RETRY_PAUSE_MS = 500L
        private const val MAX_ATTEMPTS = 2
        private const val TONE_VOLUME = 60

        // Same line-height bands as the calibration gate (provisional).
        private const val GREEN_LINES = 3.0f
        private const val AMBER_LINES = 4.5f
        private const val ARTICLE_LINE_SPACING_MULT = 1.6f

        private val GREEN_COLOR = Color.parseColor("#2E7D32")
        private val AMBER_COLOR = Color.parseColor("#B26A00")
        private val RED_COLOR = Color.parseColor("#C62828")
    }
}
