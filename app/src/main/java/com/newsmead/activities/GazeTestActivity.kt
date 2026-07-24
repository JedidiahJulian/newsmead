package com.newsmead.activities

import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.newsmead.databinding.ActivityGazeTestBinding
import com.newsmead.gaze.CalibrationStore
import com.newsmead.gaze.DriftCorrection
import com.newsmead.gaze.GazeMapper
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.LocalCalibratedGazeProvider
import com.newsmead.gaze.LocalGazeSources
import com.newsmead.gaze.LocalRawGazeSource
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Gaze accuracy test, ported from the prototype's Stage 3. Loads the saved
 * 16-point calibration, runs the live calibrated gaze pipeline through
 * [GazeProvider], and renders a dot at the estimate. The accuracy harness cycles
 * a 3x3 target grid (at 20/50/80%, deliberately offset from the calibration
 * dots) and reports the median gaze error in px and cm - the vertical median is
 * the line-level decision number. Requires calibration to have been run first.
 */
class GazeTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGazeTestBinding
    private var provider: GazeProvider? = null
    private val handler = Handler(Looper.getMainLooper())

    private var testPoints: List<PointF> = emptyList()
    private var testIndex = 0
    private var collecting = false
    private val bufX = ArrayList<Float>()
    private val bufY = ArrayList<Float>()
    private val errors = ArrayList<FloatArray>() // per point [dx, dy] in px
    private val observations = ArrayList<DriftCorrection.Observation>()

    // Drift correction: what the pipeline ran with during this test, and the
    // candidate fitted from the latest run (pending researcher approval).
    private var activeCorrection: DriftCorrection? = null
    private var pendingCorrection: DriftCorrection? = null
    private var pendingPreMedianPx = 0f
    private var pendingPostMedianPx = 0f
    @Volatile private var latestBlinkStats: LocalRawGazeSource.BlinkStats? = null
    private var testStartBlinkStats: LocalRawGazeSource.BlinkStats? = null
    private var pointStartBlinkStats: LocalRawGazeSource.BlinkStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGazeTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableImmersiveMode()
        binding.accuracyButton.setOnClickListener { startAccuracyTest() }
        binding.applyCorrectionButton.setOnClickListener { applyPendingCorrection() }
        startGaze()
    }

    private fun startGaze() {
        val samples = CalibrationStore.load(this)
        if (samples == null) {
            binding.hintText.text = getString(com.newsmead.R.string.gaze_test_no_calibration)
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
        rawSource.setOnBlinkStats { stats -> latestBlinkStats = stats }
        activeCorrection = CalibrationStore.loadDriftCorrection(this)
        provider = LocalCalibratedGazeProvider(mapper, rawSource, activeCorrection).apply {
            setOnGaze { x, y -> runOnUiThread { onGaze(x, y) } }
            start(this@GazeTestActivity)
        }
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
        if (collecting) {
            bufX.add(x)
            bufY.add(y)
        }
    }

    // --- Accuracy harness -------------------------------------------------

    private fun startAccuracyTest() {
        val view = binding.gazeDot
        testPoints = computeTestPoints(view.width, view.height)
        testIndex = 0
        errors.clear()
        observations.clear()
        pendingCorrection = null
        binding.applyCorrectionButton.visibility = View.GONE
        testStartBlinkStats = latestBlinkStats
        binding.accuracyButton.visibility = View.GONE
        showTestPoint(0)
    }

    /** Test grid at 20/50/80% - distinct from the calibration dots. */
    private fun computeTestPoints(w: Int, h: Int): List<PointF> {
        val xs = floatArrayOf(w * 0.2f, w * 0.5f, w * 0.8f)
        val ys = floatArrayOf(h * 0.2f, h * 0.5f, h * 0.8f)
        return buildList { for (y in ys) for (x in xs) add(PointF(x, y)) }
    }

    private fun showTestPoint(index: Int) {
        testIndex = index
        collecting = false
        bufX.clear()
        bufY.clear()
        val p = testPoints[index]
        binding.gazeDot.setTarget(p.x, p.y)
        binding.gazeDot.clearEstimate()
        binding.hintText.text =
            getString(com.newsmead.R.string.gaze_test_accuracy_progress, index + 1, testPoints.size)
        handler.postDelayed({ startCollectPoint() }, SETTLE_MS)
    }

    private fun startCollectPoint() {
        bufX.clear()
        bufY.clear()
        pointStartBlinkStats = latestBlinkStats
        collecting = true
        handler.postDelayed({ finishPoint() }, COLLECT_MS)
    }

    private fun finishPoint() {
        collecting = false
        if (bufX.size < MIN_SAMPLES) {
            Log.w(TAG, "Point ${testIndex + 1}: only ${bufX.size} samples, re-collecting")
            handler.postDelayed({ showTestPoint(testIndex) }, RETRY_PAUSE_MS)
            return
        }
        val gx = median(bufX)
        val gy = median(bufY)
        val p = testPoints[testIndex]
        val dx = gx - p.x
        val dy = gy - p.y
        errors.add(floatArrayOf(dx, dy))
        observations.add(DriftCorrection.Observation(gx, gy, p.x, p.y))
        val pointStats = blinkDelta(pointStartBlinkStats, latestBlinkStats)
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "point %d/%d: target=(%.0f, %.0f) est=(%.0f, %.0f) err=%.0f px dx=%.0f dy=%.0f samples=%d emitted=%d blinkDrop=%d noFace=%d open=%.3f blink=%b th=%.3f/%.3f",
                testIndex + 1,
                testPoints.size,
                p.x,
                p.y,
                gx,
                gy,
                hypot(dx, dy),
                dx,
                dy,
                bufX.size,
                pointStats.emittedSamples,
                pointStats.blinkDroppedFrames,
                pointStats.noFaceFrames,
                pointStats.lastOpenness,
                pointStats.blink,
                pointStats.closeThreshold,
                pointStats.openThreshold,
            ),
        )
        // Show this point's error on screen and mark where the estimate landed,
        // then pause so it's readable before advancing to the next target.
        binding.gazeDot.setEstimate(gx, gy)
        binding.hintText.text = getString(
            com.newsmead.R.string.gaze_test_point_result,
            testIndex + 1, testPoints.size, hypot(dx, dy), abs(dy),
        )
        val next = testIndex + 1
        handler.postDelayed({
            if (next < testPoints.size) showTestPoint(next) else finishTest()
        }, POINT_RESULT_MS)
    }

    private fun finishTest() {
        binding.gazeDot.clearTarget()
        val dm = resources.displayMetrics
        val pxPerCmX = dm.xdpi / 2.54f
        val pxPerCmY = dm.ydpi / 2.54f

        val errPx = errors.map { hypot(it[0], it[1]) }
        val errCm = errors.map { hypot(it[0] / pxPerCmX, it[1] / pxPerCmY) }
        val vertPx = errors.map { abs(it[1]) }
        val vertCm = errors.map { abs(it[1]) / pxPerCmY }

        val medPx = median(errPx)
        val medCm = median(errCm)
        val medVertPx = median(vertPx)
        val medVertCm = median(vertCm)
        val runStats = blinkDelta(testStartBlinkStats, latestBlinkStats)

        Log.i(
            TAG,
            String.format(
                Locale.US,
                "ACCURACY (n=%d): median err=%.0f px / %.2f cm; vertical median=%.0f px / %.2f cm; emitted=%d blinkDrop=%d noFace=%d open=%.3f blink=%b th=%.3f/%.3f; xdpi=%.0f ydpi=%.0f",
                errors.size,
                medPx,
                medCm,
                medVertPx,
                medVertCm,
                runStats.emittedSamples,
                runStats.blinkDroppedFrames,
                runStats.noFaceFrames,
                runStats.lastOpenness,
                runStats.blink,
                runStats.closeThreshold,
                runStats.openThreshold,
                dm.xdpi,
                dm.ydpi,
            ),
        )
        binding.hintText.text = getString(
            com.newsmead.R.string.gaze_test_accuracy_result, medPx, medCm, medVertPx, medVertCm,
        )
        binding.accuracyButton.visibility = View.VISIBLE
        offerDriftCorrection(medPx)
    }

    /**
     * Fit an affine drift correction from this run's (predicted, truth) pairs
     * and offer it to the researcher. The test ran through the pipeline with
     * [activeCorrection] applied, so the new fit maps corrected->truth and the
     * stored candidate is the composition of the two. The estimate shown is
     * in-sample; re-run the test after applying to verify independently.
     */
    private fun offerDriftCorrection(preMedianPx: Float) {
        val newFit = DriftCorrection.fit(observations) ?: return
        val postMedianPx = DriftCorrection.medianResidualPx(newFit, observations)
        pendingCorrection = activeCorrection?.let { DriftCorrection.compose(newFit, it) } ?: newFit
        pendingPreMedianPx = preMedianPx
        pendingPostMedianPx = postMedianPx
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "DRIFT FIT: median %.0f px -> est. %.0f px (in-sample, n=%d, composed=%b)",
                preMedianPx, postMedianPx, observations.size, activeCorrection != null,
            ),
        )
        binding.applyCorrectionButton.text =
            getString(com.newsmead.R.string.gaze_test_apply_correction, preMedianPx, postMedianPx)
        binding.applyCorrectionButton.visibility = View.VISIBLE
    }

    private fun applyPendingCorrection() {
        val correction = pendingCorrection ?: return
        CalibrationStore.saveDriftCorrection(this, correction, pendingPreMedianPx, pendingPostMedianPx)
        pendingCorrection = null
        binding.applyCorrectionButton.visibility = View.GONE
        binding.hintText.text = getString(com.newsmead.R.string.gaze_test_correction_applied)
        // Restart the pipeline so the live dot immediately uses the correction.
        startGaze()
    }

    private fun blinkDelta(
        start: LocalRawGazeSource.BlinkStats?,
        end: LocalRawGazeSource.BlinkStats?,
    ): LocalRawGazeSource.BlinkStats {
        val current = end ?: return LocalRawGazeSource.BlinkStats(
            totalResults = 0,
            emittedSamples = 0,
            blinkDroppedFrames = 0,
            noFaceFrames = 0,
            lastOpenness = Float.NaN,
            blink = false,
            closeThreshold = 0f,
            openThreshold = 0f,
        )
        return LocalRawGazeSource.BlinkStats(
            totalResults = current.totalResults - (start?.totalResults ?: current.totalResults),
            emittedSamples = current.emittedSamples - (start?.emittedSamples ?: current.emittedSamples),
            blinkDroppedFrames = current.blinkDroppedFrames - (start?.blinkDroppedFrames ?: current.blinkDroppedFrames),
            noFaceFrames = current.noFaceFrames - (start?.noFaceFrames ?: current.noFaceFrames),
            lastOpenness = current.lastOpenness,
            blink = current.blink,
            closeThreshold = current.closeThreshold,
            openThreshold = current.openThreshold,
        )
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    // ----------------------------------------------------------------------

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
        provider?.stop()
    }

    companion object {
        private const val TAG = "GazeStage3"
        private const val SETTLE_MS = 800L
        private const val COLLECT_MS = 1200L
        private const val RETRY_PAUSE_MS = 400L
        private const val POINT_RESULT_MS = 900L
        private const val MIN_SAMPLES = 10
    }
}
