package com.newsmead.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.newsmead.databinding.ActivityGazeTestBinding
import com.newsmead.gaze.GazeCoordinateFrame
import com.newsmead.gaze.gazeCoordinateFrame
import com.newsmead.gaze.mgazenet.*

/** Explicit-start raw MGazeNet check. No affine fit, correction, calibration write or quality gate. */
class GazeTestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGazeTestBinding
    private var provider: MgazeNetGazeProvider? = null
    private val handler = Handler(Looper.getMainLooper())
    private var frame: GazeCoordinateFrame? = null
    private var target = -1
    private var onset = Double.NaN
    private val samples = ArrayList<Pair<Float,Float>>()
    private val summaries = ArrayList<String>()
    private val targetResults = ArrayList<TargetResult>()
    private var permissionPending = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGazeTestBinding.inflate(layoutInflater); setContentView(binding.root)
        binding.root.keepScreenOn = false
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,binding.root).hide(WindowInsetsCompat.Type.systemBars())
        binding.hintText.text = "MGazeNet accuracy check. Eight targets are new and the centre is repeated. Results do not correct gaze."
        binding.accuracyButton.visibility = View.VISIBLE; binding.accuracyButton.text = "Start MGazeNet check"
        binding.accuracyButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionPending = true; ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),43)
            } else start()
        }
        binding.btnFullRecal.setOnClickListener { startActivity(Intent(this,GazeCalibrationActivity::class.java)); finish() }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if (code == 43 && permissionPending) {
            permissionPending = false
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) start()
        }
    }
    private fun start() {
        if (provider != null) return
        val issue = MgazeNetCalibrationStore(this).compatibilityIssue(binding.root)
        if (issue != null) { end(issue); return }
        frame = binding.calibrationView.gazeCoordinateFrame()
        binding.accuracyButton.visibility = View.GONE; binding.root.keepScreenOn = true
        val p = MgazeNetGazeProvider(this,binding.root)
        p.onFailure = { end(it) }
        p.onObservation = { observation ->
            if (frame != binding.calibrationView.gazeCoordinateFrame()) end("Target geometry changed; check stopped.")
            else if (target == -1 && observation.reason == "coordinate") { target = 0; showTarget() }
            else if (target in MgazeNetAccuracyProtocol.TARGET_FRACTIONS.indices &&
                observation.reason == "coordinate" && onset.isFinite() &&
                observation.captureMs != null && observation.captureMs >= onset+3000 &&
                observation.captureMs < onset+5500 && observation.outputMs < onset+5750) {
                samples.add(observation.x!! to observation.y!!)
            }
        }
        // No estimated dot is shown while measuring instructed targets.
        p.setOnGaze { _,_ -> }
        provider = p; p.start(this)
        handler.postDelayed({ if (provider != null && target == -1) end("No fresh gaze output within 20 seconds. Check stopped.") },20000)
    }
    private fun targetPoint() = MgazeNetAccuracyProtocol.target(frame!!,target)
    private fun showTarget() {
        samples.clear(); onset = Double.NaN
        val p = targetPoint(); val local = frame!!.toLocal(p.x,p.y)
        binding.hintText.text = "Target ${target+1} of 9"
        binding.calibrationView.onNextTargetDraw {
            onset = MgazeNetCameraSource.now()
            handler.postDelayed({ finishTarget() },5750)
        }
        binding.calibrationView.showTarget(local.x,local.y,1800)
    }
    private fun finishTarget() {
        if (provider == null || target !in MgazeNetAccuracyProtocol.TARGET_FRACTIONS.indices) return
        val p = targetPoint()
        summaries.add(if (samples.isEmpty()) "Target ${target+1}: no fresh coordinates" else {
            val dx = median(samples.map { it.first-p.x }); val dy = median(samples.map { it.second-p.y })
            val radial = kotlin.math.hypot(dx,dy)
            targetResults.add(TargetResult(target+1,radial,kotlin.math.abs(dy)))
            "Target ${target+1}: n=${samples.size}, dx=${"%.1f".format(dx)}, " +
                "dy=${"%.1f".format(dy)}, 2D=${"%.1f".format(radial)} px"
        })
        target++
        if (target == MgazeNetAccuracyProtocol.TARGET_FRACTIONS.size) end(resultSummary())
        else showTarget()
    }
    private fun resultSummary(): String {
        val metrics = if (targetResults.isEmpty()) "No targets produced usable coordinates." else {
            val median2d = median(targetResults.map { it.radial })
            val medianVertical = median(targetResults.map { it.absDy })
            val worst = targetResults.maxBy { it.radial }
            "Measured targets: ${targetResults.size} of ${MgazeNetAccuracyProtocol.TARGET_FRACTIONS.size}\n" +
                "Median 2D target error: ${"%.1f".format(median2d)} px\n" +
                "Worst target: ${worst.index} at ${"%.1f".format(worst.radial)} px\n" +
                "Median |vertical bias|: ${"%.1f".format(medianVertical)} px"
        }
        return "Nine-point result\n$metrics\n\n${summaries.joinToString("\n")}\n\n" +
            "Eight targets were held out from calibration; the centre is a repeat. " +
            "Raw MGazeNet with 500 ms operational expiry. No accuracy pass or correction. " +
            "This check does not establish reading accuracy."
    }
    private fun median(values: List<Float>): Float = values.sorted().let {
        (it[(it.size-1)/2]+it[it.size/2])/2
    }
    private fun end(message: String) {
        handler.removeCallbacksAndMessages(null); provider?.stop(); provider = null
        binding.root.keepScreenOn = false; binding.calibrationView.hideTarget()
        binding.gateText.text = message; binding.gatePanel.visibility = View.VISIBLE
        binding.btnFullRecal.visibility = View.VISIBLE
        binding.accuracyButton.visibility = View.GONE
    }
    override fun onStop() { permissionPending = false; if (provider != null) end("Check interrupted."); super.onStop() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); provider?.stop(); super.onDestroy() }

    private data class TargetResult(val index: Int, val radial: Float, val absDy: Float)
}
