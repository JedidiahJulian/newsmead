package com.newsmead.activities

import android.Manifest
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
import com.newsmead.databinding.ActivityGazeCalibrationBinding
import com.newsmead.gaze.gazeCoordinateFrame
import com.newsmead.gaze.physicalDisplaySize
import com.newsmead.gaze.mgazenet.*

/** MGazeNet-only calibration. Setup performs no camera/native/store mutation. */
class GazeCalibrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGazeCalibrationBinding
    private val handler = Handler(Looper.getMainLooper())
    private var source: MgazeNetCameraSource? = null
    private var session: MgazeNetCalibrationSession? = null
    private var identity: CalibrationIdentity? = null
    private var active = false
    private var fitting = false
    private var startedAt = 0.0
    private var verification = -1
    private var checkStart = Double.NaN
    private val errors = ArrayList<Float>()
    private val summaries = ArrayList<String>()
    private var permissionPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGazeCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        binding.root.keepScreenOn = false
        binding.progressText.text = "MGazeNet calibration. Start opens the camera; follow each target until it advances."
        binding.startButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionPending = true
                ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),42)
            } else begin()
        }
        binding.btnAccept.setOnClickListener { save() }
        binding.btnRedoWorst.visibility = View.GONE
        binding.btnRedoAll.visibility = View.GONE
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if (code == 42 && permissionPending) {
            permissionPending = false
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin()
        }
    }
    private fun begin() {
        if (active || binding.calibrationView.width == 0) return
        val store = MgazeNetCalibrationStore(this)
        identity = store.identity(binding.calibrationView)
        active = true; startedAt = MgazeNetCameraSource.now()
        binding.root.keepScreenOn = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.startButton.visibility = View.GONE
        binding.progressText.text = "Preparing MGazeNet…"
        source = MgazeNetCameraSource(this,this,ready = { metadata ->
            identity = identity!!.copy(acquisition = metadata["acquisition"] as String)
            val s = MgazeNetCalibrationSession(identity!!); session = s
            countdown(3) { s.begin(); showTarget() }
        }, result = { frame ->
            if (active) {
                if (!geometryMatches()) stop("Screen geometry changed. Calibration was not saved.")
                else if (verification >= 0) observeCheck(frame)
                else session?.sample(frame.captureMs,frame.outputMs,frame.features,frame.leftArea,frame.rightArea)
            }
        },failed = { stop(it) },store = store).also { it.start() }
        handler.post(tick)
    }
    private fun countdown(n: Int, done: () -> Unit) {
        if (!active) return
        if (n == 0) { binding.countdownText.visibility = View.GONE; done(); return }
        binding.countdownText.visibility = View.VISIBLE; binding.countdownText.text = n.toString()
        handler.postDelayed({ countdown(n-1,done) },1000)
    }
    private fun geometryMatches() = identity?.let {
        val size = binding.calibrationView.physicalDisplaySize()
        size.x == it.screenWidth && size.y == it.screenHeight && binding.calibrationView.display.rotation == it.rotation &&
            binding.calibrationView.gazeCoordinateFrame() == it.viewport
    } == true
    private fun showTarget() {
        val s = session ?: return
        val local = s.identity.viewport.toLocal(s.target.x,s.target.y)
        binding.progressText.text = if (s.index < 0) "Practice" else "Target ${s.index+1} of 16"
        binding.calibrationView.onNextTargetDraw {
            handler.postDelayed({ if (active) s.drawn(MgazeNetCameraSource.now()) },1800)
        }
        binding.calibrationView.showTarget(local.x,local.y,1800)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val now = MgazeNetCameraSource.now()
            if (!geometryMatches()) { stop("Screen geometry changed. Nothing was saved."); return }
            if (now-startedAt > 600000 || (session == null && now-startedAt > 20000)) {
                stop("Calibration timed out. Nothing was saved."); return
            }
            val s = session
            if (s != null && verification < 0) {
                if (s.tick(now)) showTarget()
                if (s.phase == MgazeNetCalibrationSession.Phase.FAILED) { stop("Target capture timed out. Nothing was saved."); return }
                if (s.phase == MgazeNetCalibrationSession.Phase.COLLECT) binding.statusText.text = "${s.count} / 45 samples"
                if (s.phase == MgazeNetCalibrationSession.Phase.FITTING && !fitting) {
                    fitting = true; binding.calibrationView.hideTarget(); binding.progressText.text = "Fitting calibration…"
                    val (features,labels) = s.takeTraining()
                    source!!.fit(features,labels) { ok ->
                        s.fitted(ok)
                        if (!ok) stop("Calibration fit failed. Nothing was saved.")
                        else { verification = 0; showCheck() }
                    }
                }
            } else if (verification in 0..5 && checkStart.isFinite() && now >= checkStart+5750) {
                val label = if (verification == 0) "Near-centre repeat" else "Validation $verification"
                summaries.add(if (errors.isEmpty()) "$label: no coordinates" else {
                    val sorted = errors.sorted(); val median = (sorted[(sorted.size-1)/2]+sorted[sorted.size/2])/2
                    "$label: ${errors.size} coordinates; median |Y error| ${"%.1f".format(median)} px"
                })
                verification++
                if (verification == 6) finishChecks() else showCheck()
            }
            handler.postDelayed(this,50)
        }
    }
    private fun checkTarget() = identity!!.let { id ->
        if (verification == 0) id.targets[5] else {
            val f = listOf(.5f to .5f,.25f to .25f,.75f to .25f,.25f to .75f,.75f to .75f)[verification-1]
            id.viewport.toScreen(f.first*id.viewport.width,f.second*id.viewport.height)
        }
    }
    private fun showCheck() {
        errors.clear(); checkStart = Double.NaN
        val p = checkTarget(); val local = identity!!.viewport.toLocal(p.x,p.y)
        binding.progressText.text = if (verification == 0) "Near-centre repeat" else "Validation $verification of 5"
        binding.statusText.text = "Keep looking at the target"
        binding.calibrationView.onNextTargetDraw { if (active) checkStart = MgazeNetCameraSource.now() }
        binding.calibrationView.showTarget(local.x,local.y,1800)
    }
    private fun observeCheck(frame: MgazeNetCameraSource.Frame) {
        if (verification !in 0..5 || !checkStart.isFinite() || frame.captureMs < checkStart+3000 ||
            frame.captureMs >= checkStart+5500 || frame.outputMs >= checkStart+5750 || !frame.eligible) return
        val p = frame.prediction ?: return
        val dy = kotlin.math.abs(p[1]*identity!!.screenHeight-checkTarget().y)
        if (dy.isFinite()) errors.add(dy)
    }
    private fun finishChecks() {
        source?.pauseCapture()
        binding.root.keepScreenOn = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.calibrationView.hideTarget(); binding.statusText.text = ""
        binding.gatePanel.visibility = View.VISIBLE
        binding.gateText.text = summaries.joinToString("\n") +
            "\n\nDescriptive checks only; no accuracy pass is assigned. Save retains two local models. No correction is applied."
        binding.btnAccept.text = "Save MGazeNet calibration"
    }
    private fun save() {
        if (!active || verification != 6 || !geometryMatches()) return
        binding.btnAccept.isEnabled = false
        source!!.save(identity!!) { success ->
            stop(if (success) "MGazeNet calibration saved." else "Calibration could not be saved.")
            if (success) finish()
        }
    }
    private fun stop(message: String) {
        active = false; handler.removeCallbacksAndMessages(null)
        source?.close {}; source = null; session?.close()
        binding.calibrationView.hideTarget(); binding.countdownText.visibility = View.GONE
        binding.root.keepScreenOn = false; window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.progressText.text = message
    }
    override fun onStop() { permissionPending = false; if (active) stop("Calibration interrupted. Close and reopen to begin again."); super.onStop() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); source?.close {}; session?.close(); super.onDestroy() }
}
