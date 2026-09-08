package com.newsmead.mgazenetbenchmark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.UUID

/** Separate research session. Opening this screen never starts a camera or calibration. */
class AccuracyActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private var targetView: AccuracyTargetView? = null
    private var source: AccuracyCameraSource? = null
    private var session: AccuracySession? = null
    private var pipeline: Map<String,Any?> = emptyMap()
    private var visible = false
    private var closing = false
    private var fitting = false
    private var pendingStart = false
    private var label = ""
    private var age = 0.0
    private var openedAt = 0.0
    private var runId = ""
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingStart = false
        if (granted && visible) begin() else { status.text = "Camera not started."; startButton.isEnabled = true }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (closing || source == null) return
            val now = AccuracyCameraSource.now()
            if ((session == null && now-openedAt > 20_000) || now-openedAt > 600_000) {
                finishRun("session_deadline",true); return
            }
            session?.tick(now); updateState()
            if (!closing) handler.postDelayed(this,50)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,48,24,24) }
        status = TextView(this).apply {
            textSize = 18f
            text = "Accuracy research setup\n\nThis separate session uses its own calibration and held-out targets. Images and personal calibration models are not saved. No reading assistance is changed.\n\nOnly start when the research procedure is agreed."
        }
        val runLabel = EditText(this).apply { hint = "Research run label"; setSingleLine() }
        val freshness = EditText(this).apply {
            hint = "Analysis age limit in ms (from study protocol)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        startButton = Button(this).apply {
            text = "Start separate calibration and accuracy check"
            setOnClickListener {
                if (source != null || pendingStart) return@setOnClickListener
                label = runLabel.text.toString().trim(); age = freshness.text.toString().toDoubleOrNull() ?: Double.NaN
                if (label.isBlank() || label.length > 120 || !age.isFinite() || age <= 0 || age > 60_000) {
                    status.text = "Enter a run label and the analysis age limit specified by the protocol. No default accuracy threshold is selected."
                    return@setOnClickListener
                }
                isEnabled = false
                if (ContextCompat.checkSelfPermission(this@AccuracyActivity,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) begin()
                else { pendingStart = true; permission.launch(Manifest.permission.CAMERA) }
            }
        }
        root.addView(status); root.addView(runLabel); root.addView(freshness); root.addView(startButton)
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(root)
    }
    private fun begin() {
        if (!visible || source != null) return
        try { check(OpenCVLoader.initLocal()) } catch (e: Throwable) { status.text = "Cannot start: $e"; startButton.isEnabled = true; return }
        closing = false; openedAt = AccuracyCameraSource.now(); runId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { textSize = 18f; setPadding(20,12,20,0); text = "Preparing camera. Follow the red target when it appears." }
        root.addView(status,LinearLayout.LayoutParams(-1,(96*resources.displayMetrics.density).toInt()))
        targetView = AccuracyTargetView(this).also { view ->
            root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
            view.onPresented = { token, time -> session?.presented(token,time) }
            view.onGeometry = { geometry ->
                if (!closing && session != null && session!!.layout != geometry) finishRun("screen_layout_changed",true)
            }
        }
        root.addView(Button(this).apply { text = "Stop and return"; setOnClickListener {
            if (closing) finish() else finishRun("user_stopped",false)
        } })
        setContentView(root)
        source = AccuracyCameraSource(applicationContext,this,{ metadata ->
            if (!closing) {
                try {
                    pipeline = metadata
                    session = AccuracySession(targetView!!.snapshot(),age,label)
                    updateState()
                } catch (e: Throwable) { finishRun("invalid_target_layout: $e",true) }
            }
        },{ frame ->
            session?.frame(frame)
            frame.features?.fill(0f)
            if (!closing) updateState()
        },{ error -> finishRun(error,true) }).also { it.start() }
        handler.post(ticker)
    }
    private fun updateState() {
        val current = session ?: return
        if (closing) return
        targetView?.target = current.target
        when (current.phase) {
            AccuracySession.Phase.CALIBRATION -> {
                val point = current.fitPoints.first { it.id == current.target!!.id }
                status.text = if (point.practice) "Practice: look at the red target.\n${point.accepted} / 45 samples"
                    else "Calibration: look at the red target.\nPoint ${current.fitPoints.indexOf(point)} of 13 · ${point.accepted} / 45 samples"
            }
            AccuracySession.Phase.FITTING -> if (!fitting) {
                fitting = true; status.text = "Preparing your separate calibration…"
                source!!.fit(current.training()) { success ->
                    current.fitted(success,AccuracyCameraSource.now()); if (!closing) updateState()
                }
            }
            AccuracySession.Phase.VALIDATION -> status.text = "Accuracy check: keep looking at the red target.\nTarget ${current.target!!.testIndex!!+1} / ${current.blocks.size}"
            AccuracySession.Phase.COMPLETE -> finishRun("complete",false)
            else -> finishRun(current.failure ?: "session_failed",true)
        }
    }
    private fun finishRun(reason: String, failed: Boolean) {
        if (closing || source == null) return
        closing = true; handler.removeCallbacks(ticker)
        if (failed) session?.fail(reason,AccuracyCameraSource.now()) else session?.stop(reason,AccuracyCameraSource.now())
        targetView?.target = null; status.text = "Closing camera and saving the numeric record…"
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        source!!.close { counters ->
            val current = session
            val payload = current?.let {
                AccuracyReport.payload(it,runId,"${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}",pipeline).toMutableMap()
            } ?: linkedMapOf<String,Any?>("schema" to "mgazenet_accuracy_partial_v1","outcome" to "failed",
                "session_id" to runId,"failure" to reason,"blocks" to emptyList<Any>(),"camera_frames_retained" to false)
            payload["camera_counters"] = counters
            Thread {
                val message = try {
                    val file = AccuracyReport.writeNew(File(filesDir,"accuracy/$runId"),payload)
                    "${if (current?.phase == AccuracySession.Phase.COMPLETE) "Measurement complete" else "Session incomplete"}.\nNumeric record saved: ${file.name}\nNo accuracy pass or reading claim has been assigned. You may return."
                } catch (e: Throwable) { "Could not save the numeric record: $e" }
                runOnUiThread { status.text = message }
            }.start()
        }
    }
    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() {
        visible = false
        if (!pendingStart) finishRun("activity_backgrounded",false)
        super.onStop()
    }
    override fun onDestroy() { handler.removeCallbacks(ticker); super.onDestroy() }
}
