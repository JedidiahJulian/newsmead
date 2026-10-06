package com.newsmead.gaze.mgazenet

import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.physicalDisplaySize

internal enum class MgazeNetStopAction { START_CLOSE, WAIT_FOR_CLOSE, ALREADY_CLOSED }

/** Keeps every terminal callback behind the one asynchronous source close. */
internal class MgazeNetStopCompletionQueue {
    private enum class State { OPEN, CLOSING, CLOSED }

    private var state = State.OPEN
    private val callbacks = ArrayList<() -> Unit>()

    @Synchronized
    fun request(callback: () -> Unit): MgazeNetStopAction = when (state) {
        State.OPEN -> {
            callbacks += callback
            state = State.CLOSING
            MgazeNetStopAction.START_CLOSE
        }
        State.CLOSING -> {
            callbacks += callback
            MgazeNetStopAction.WAIT_FOR_CLOSE
        }
        State.CLOSED -> MgazeNetStopAction.ALREADY_CLOSED
    }

    @Synchronized
    fun complete(): List<() -> Unit> {
        check(state == State.CLOSING) { "MGazeNet source close was not pending." }
        state = State.CLOSED
        return callbacks.toList().also { callbacks.clear() }
    }
}

/** Finite physical-screen measurements reach GazeProvider with no correction or replay. */
class MgazeNetGazeProvider(
    private val context: Context,
    private val view: View,
    private val smoothForReading: Boolean = false,
) : GazeProvider, DefaultLifecycleObserver {
    data class Observation(val captureMs: Double?, val deliveryElapsedNs: Long, val deliveryId: Long, val reason: String,
        val x: Float? = null, val y: Float? = null, val rawX: Float? = null, val rawY: Float? = null,
        val arrivals: Long? = null, val busyDrops: Long? = null) {
        val outputMs: Double get() = deliveryElapsedNs / 1e6
    }
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
    private var deliverySequence = 0L
    private val displaySize = Point()
    private val readingSmoother = MgazeNetReadingSmoother()
    private val stopCompletions = MgazeNetStopCompletionQueue()
    override fun setOnGaze(listener: GazeProvider.OnGaze) { this.listener = listener }
    override fun start(owner: LifecycleOwner) {
        check(source == null)
        val store = MgazeNetCalibrationStore(context)
        val saved = try { store.snapshot(view) } catch (_: Exception) {
            onFailure?.invoke("A fresh MGazeNet calibration is required."); return
        }
        this.owner = owner; owner.lifecycle.addObserver(this)
        lastCapture = -1.0; lastOutput = -1.0; fpsStart = MgazeNetCameraSource.now(); fpsCount = 0
        deliverySequence = 0L
        readingSmoother.reset()
        val identity = saved.identity
        val gate = MgazeNetOutputGate(identity.screenWidth,identity.screenHeight)
        source = MgazeNetCameraSource(context,owner,ready = {}, result = { frame ->
            val size = view.physicalDisplaySize(displaySize)
            if (size.x != identity.screenWidth || size.y != identity.screenHeight || view.display.rotation != identity.rotation) {
                fail("Screen geometry changed; recalibrate.")
            } else {
                val deliveryElapsedNs = SystemClock.elapsedRealtimeNanos()
                val now = deliveryElapsedNs / 1e6
                val deliveryId = ++deliverySequence
                val result = gate.evaluate(frame.captureMs,now,frame.eligible,frame.prediction,
                    if (frame.features == null) frame.reason else "eye_area")
                val reason = result.reason; val x = result.rawX; val y = result.rawY
                lastCapture = frame.captureMs; lastOutput = frame.outputMs
                fresh = reason == "coordinate"
                val captureMs = frame.captureMs.toLong()
                val outputX = if (fresh && smoothForReading) readingSmoother.filterX(x!!,captureMs) else x
                val outputY = if (fresh && smoothForReading) readingSmoother.filterY(y!!,captureMs) else y
                if (!fresh) readingSmoother.reset()
                onObservation?.invoke(Observation(frame.captureMs,deliveryElapsedNs,deliveryId,reason,if (fresh) outputX else null,if (fresh) outputY else null,
                    x,y,frame.arrivals,frame.busyDrops))
                if (fresh) listener?.onGaze(outputX!!,outputY!!)
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
                fresh = false
                readingSmoother.reset()
                onObservation?.invoke(Observation(lastCapture,SystemClock.elapsedRealtimeNanos(),++deliverySequence,"stale"))
            }
            handler.postDelayed(this,50)
        }
    }
    private fun fail(reason: String) { stopAndThen { onFailure?.invoke(reason) } }
    override fun onStop(owner: LifecycleOwner) { stop() }
    override fun stop() { stopAndThen {} }

    /** Runs [complete] only after the exact terminal source counters were observed. */
    fun stopAndThen(complete: () -> Unit) {
        when (stopCompletions.request(complete)) {
            MgazeNetStopAction.WAIT_FOR_CLOSE -> return
            MgazeNetStopAction.ALREADY_CLOSED -> {
                complete()
                return
            }
            MgazeNetStopAction.START_CLOSE -> Unit
        }
        handler.removeCallbacksAndMessages(null)
        owner?.lifecycle?.removeObserver(this); owner = null
        val closing = source; source = null; fresh = false; readingSmoother.reset()
        if (closing == null) {
            stopCompletions.complete().forEach { it() }
            return
        }
        closing.close { counts ->
            try {
                onObservation?.invoke(Observation(
                    null,SystemClock.elapsedRealtimeNanos(),++deliverySequence,"stopped",
                    arrivals = counts["analyzer_arrivals"] as? Long,
                    busyDrops = counts["observed_busy_drops"] as? Long,
                ))
            } finally {
                stopCompletions.complete().forEach { it() }
            }
        }
    }
    companion object {
        // Explicit operational expiry, NOT an accuracy threshold. No interpolation.
        const val MAX_OUTPUT_AGE_MS = MgazeNetOutputGate.EXPIRY_MS
    }
}
