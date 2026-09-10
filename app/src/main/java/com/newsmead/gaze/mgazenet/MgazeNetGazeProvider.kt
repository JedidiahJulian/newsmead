package com.newsmead.gaze.mgazenet

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.physicalDisplaySize

/** Only unfiltered finite physical-screen measurements reach GazeProvider. No correction or replay. */
class MgazeNetGazeProvider(private val context: Context, private val view: View) : GazeProvider, DefaultLifecycleObserver {
    data class Observation(val captureMs: Double?, val outputMs: Double, val reason: String,
        val x: Float? = null, val y: Float? = null, val rawX: Float? = null, val rawY: Float? = null,
        val arrivals: Long? = null, val busyDrops: Long? = null)
    private var listener: GazeProvider.OnGaze? = null
    private var source: MgazeNetCameraSource? = null
    private var owner: LifecycleOwner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastCapture = -1.0
    private var lastOutput = -1.0
    private var fresh = false
    var onObservation: ((Observation) -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    var onFps: ((Float) -> Unit)? = null
    private var fpsStart = 0.0
    private var fpsCount = 0
    override fun setOnGaze(listener: GazeProvider.OnGaze) { this.listener = listener }
    override fun start(owner: LifecycleOwner) {
        check(source == null)
        val store = MgazeNetCalibrationStore(context)
        val saved = try { store.snapshot(view) } catch (_: Exception) {
            onFailure?.invoke("A fresh MGazeNet calibration is required."); return
        }
        this.owner = owner; owner.lifecycle.addObserver(this)
        lastCapture = -1.0; lastOutput = -1.0; fpsStart = MgazeNetCameraSource.now(); fpsCount = 0
        val identity = saved.identity
        val gate = MgazeNetOutputGate(identity.screenWidth,identity.screenHeight)
        source = MgazeNetCameraSource(context,owner,ready = {}, result = { frame ->
            val size = view.physicalDisplaySize()
            if (size.x != identity.screenWidth || size.y != identity.screenHeight || view.display.rotation != identity.rotation) {
                fail("Screen geometry changed; recalibrate.")
            } else {
                val now = MgazeNetCameraSource.now()
                val result = gate.evaluate(frame.captureMs,now,frame.eligible,frame.prediction,
                    if (frame.features == null) frame.reason else "eye_area")
                val reason = result.reason; val x = result.rawX; val y = result.rawY
                lastCapture = frame.captureMs; lastOutput = frame.outputMs
                fresh = reason == "coordinate"
                onObservation?.invoke(Observation(frame.captureMs,now,reason,if (fresh) x else null,if (fresh) y else null,
                    x,y,frame.arrivals,frame.busyDrops))
                if (fresh) listener?.onGaze(x!!,y!!)
                fpsCount++
                if (now-fpsStart >= 1000) { onFps?.invoke((fpsCount*1000/(now-fpsStart)).toFloat()); fpsStart=now; fpsCount=0 }
            }
        }, failed = ::fail, store = store, saved = saved).also { it.start() }
        handler.post(watchdog)
    }
    private val watchdog = object : Runnable {
        override fun run() {
            if (source == null) return
            val now = MgazeNetCameraSource.now()
            if (lastOutput < 0 && now - fpsStart > 20000) { fail("MGazeNet initialization timed out."); return }
            if (fresh && now-lastCapture > MAX_OUTPUT_AGE_MS) {
                fresh = false; onObservation?.invoke(Observation(lastCapture,now,"stale"))
            }
            handler.postDelayed(this,50)
        }
    }
    private fun fail(reason: String) { stop(); onFailure?.invoke(reason) }
    override fun onStop(owner: LifecycleOwner) { stop() }
    override fun stop() {
        handler.removeCallbacksAndMessages(null)
        owner?.lifecycle?.removeObserver(this); owner = null
        val closing = source; source = null; fresh = false
        closing?.close { counts -> onObservation?.invoke(Observation(null,MgazeNetCameraSource.now(),
            "stopped;arrivals=${counts["analyzer_arrivals"]};busy_drops=${counts["observed_busy_drops"]}")) }
    }
    companion object {
        // Explicit operational expiry, NOT an accuracy threshold. No interpolation or smoothing.
        const val MAX_OUTPUT_AGE_MS = MgazeNetOutputGate.EXPIRY_MS
    }
}
