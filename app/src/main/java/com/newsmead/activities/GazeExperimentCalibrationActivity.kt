package com.newsmead.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Gravity
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.newsmead.BuildConfig
import com.newsmead.databinding.ActivityGazeCalibrationBinding
import com.newsmead.gaze.experiment.ExperimentFlow
import com.newsmead.gaze.experiment.ACConfirmationLaunch
import com.newsmead.gaze.gazeCoordinateFrame
import com.newsmead.gaze.physicalDisplaySize
import com.newsmead.gaze.mgazenet.*

/** Isolated fitted experiment artifacts. Independent validation is a subsequent step. */
class GazeExperimentCalibrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGazeCalibrationBinding
    private lateinit var configuration: GazeExperimentConfiguration
    private var source: MgazeNetCameraSource? = null
    private var session: MgazeNetCalibrationSession? = null
    private var identity: CalibrationIdentity? = null
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var closing = false
    private var fitting = false
    private var started = 0.0
    private var permissionPending = false
    private var confirmation: Map<String, String>? = null
    private var setupCheck: CheckBox? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = permissionPending; permissionPending = false
        if (requested && granted && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) begin()
        else if (requested) binding.statusText.text = "Camera permission is needed. Press Start when ready."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = GazeExperimentConfiguration.fromId(intent.getStringExtra(ExperimentFlow.EXTRA_CONFIGURATION) ?: "")
        if (!BuildConfig.GAZE_EXPERIMENT_ENABLED || config == null ||
            !ExperimentFlow.validSession(intent.getStringExtra(ExperimentFlow.EXTRA_SESSION) ?: "")) { finish(); return }
        configuration = config
        confirmation = ACConfirmationLaunch.parse(intent)
        if (intent.hasExtra(ACConfirmationLaunch.EXTRA) && confirmation == null) { finish(); return }
        binding = ActivityGazeCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).hide(WindowInsetsCompat.Type.systemBars())
        binding.progressText.text = config.label
        binding.statusText.text = if (config.isNext)
            "Look at each target. One collection fits both B and C.\nValidation follows separately."
            else "Look at each target. This saves a separate experiment calibration.\nValidation follows separately."
        binding.startButton.text = "Start 13-point calibration"
        confirmation?.let { fields ->
            binding.progressText.text = "Round ${fields["confirmation_round"]} of 2 - ${config.label}"
            binding.statusText.text = "Fresh 13-point calibration for this block.\nKeep the same phone position, head position and lighting."
            setupCheck = CheckBox(this).apply {
                text = "My phone/head position, lighting and glasses match the recorded setup."
                textSize = 16f
                setPadding(32, 0, 32, 0)
                setOnCheckedChangeListener { _, checked -> binding.startButton.isEnabled = checked }
            }.also { check -> binding.root.addView(check, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
                bottomMargin = (resources.displayMetrics.density * 150).toInt()
            }) }
            binding.startButton.isEnabled = false
        }
        binding.startButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) begin()
            else { permissionPending = true; permission.launch(Manifest.permission.CAMERA) }
        }
    }

    private fun begin() {
        if (active || closing || fitting || binding.calibrationView.width == 0) return
        if (confirmation != null && setupCheck?.isChecked != true) return
        val store = MgazeNetCalibrationStore(this, configuration)
        identity = store.identity(binding.calibrationView)
        active = true; started = MgazeNetCameraSource.now()
        binding.root.keepScreenOn = true
        binding.startButton.visibility = View.GONE
        setupCheck?.visibility = View.GONE
        binding.progressText.text = "Preparing ${configuration.label}"
        source = MgazeNetCameraSource(this, this, ready = { metadata ->
            if (active) {
                identity = identity!!.copy(acquisition = metadata["acquisition"] as String)
                session = MgazeNetCalibrationSession(identity!!)
                countdown(3)
            }
        }, result = { frame ->
            if (active && !fitting) {
                if (!geometryMatches()) stop("Screen geometry changed. Calibration was not saved.")
                else session?.sample(frame.captureMs, frame.outputMs, frame.features, frame.leftArea, frame.rightArea, frame.posture)
            }
        }, failed = { stop(it) }, store = store).also { it.start() }
        handler.post(tick)
    }

    private fun countdown(n: Int) {
        if (!active) return
        binding.countdownText.visibility = if (n == 0) View.GONE else View.VISIBLE
        if (n == 0) { session!!.begin(); showTarget(); return }
        binding.countdownText.text = n.toString()
        handler.postDelayed({ countdown(n - 1) }, 1000)
    }
    private fun geometryMatches(): Boolean = identity?.let {
        val size = binding.calibrationView.physicalDisplaySize()
        size.x == it.screenWidth && size.y == it.screenHeight && binding.calibrationView.display.rotation == it.rotation &&
            binding.calibrationView.gazeCoordinateFrame() == it.viewport
    } == true
    private fun showTarget() {
        val s = session ?: return
        val point = s.identity.viewport.toLocal(s.target.x, s.target.y)
        binding.progressText.text = if (s.index < 0) "Practice" else "Target ${s.index + 1} of 13"
        binding.calibrationView.onNextTargetDraw { if (active) s.drawn(MgazeNetCameraSource.now()) }
        binding.calibrationView.showTarget(point.x, point.y, CalibrationIdentity.TARGET_CUE_MS)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active || fitting) return
            val now = MgazeNetCameraSource.now()
            if (!geometryMatches()) { stop("Screen geometry changed. Calibration was not saved."); return }
            if (now - started > 600000 || (session == null && now - started > 20000)) {
                stop("Calibration timed out. Calibration was not saved."); return
            }
            session?.let { s ->
                if (s.tick(now)) showTarget()
                if (s.phase == MgazeNetCalibrationSession.Phase.FAILED) {
                    stop("Not enough usable samples. Calibration was not saved."); return
                }
                if (s.phase == MgazeNetCalibrationSession.Phase.COLLECT) binding.statusText.text =
                    if (s.showPostureWarning(now)) "Hold the phone and your head steady"
                    else "${s.count} / ${MgazeNetCalibrationSession.SAMPLES_PER_TARGET} usable samples"
                if (s.phase == MgazeNetCalibrationSession.Phase.FITTING) {
                    fitting = true; binding.calibrationView.hideTarget()
                    binding.progressText.text = "Fitting calibration"
                    binding.statusText.text = "Keep this screen open."
                    source!!.pauseCapture()
                    val (features, labels) = s.takeTraining()
                    source!!.fit(features, labels) { fitted ->
                        s.fitted(fitted)
                        if (!fitted || !geometryMatches()) { stop("Calibration fit failed. Nothing was saved."); return@fit }
                        source!!.saveExperiment(identity!!) { saved ->
                            if (saved) {
                                setResult(RESULT_OK)
                                stop("Calibration fitted. Continue with independent validation.") { finish() }
                            } else stop("Calibration could not be saved. Previous experiment calibration was retained.")
                        }
                    }
                    return
                }
            }
            handler.postDelayed(this, 25)
        }
    }
    private fun stop(message: String, done: () -> Unit = {}) {
        if (!::binding.isInitialized || closing) return
        active = false; closing = true
        handler.removeCallbacksAndMessages(null)
        binding.root.keepScreenOn = false
        binding.calibrationView.hideTarget(); binding.countdownText.visibility = View.GONE
        binding.progressText.text = configuration.label
        binding.statusText.text = message
        session?.close(); session = null
        val previous = source; source = null
        val finished = {
            closing = false; fitting = false
            if (!isDestroyed) {
                setupCheck?.visibility = View.VISIBLE
                setupCheck?.isChecked = false
                binding.startButton.visibility = View.VISIBLE; binding.startButton.text = "Start again"; done()
            }
        }
        if (previous == null) finished() else previous.close { finished() }
    }
    override fun onStop() {
        permissionPending = false
        if (active || source != null) stop("Calibration interrupted. Start again when ready.")
        super.onStop()
    }
}
