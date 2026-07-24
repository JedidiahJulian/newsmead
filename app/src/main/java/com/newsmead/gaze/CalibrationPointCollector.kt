package com.newsmead.gaze

import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Shared per-point capture engine for both full 16-point calibration and the
 * re-calibration / accuracy screen (docs/calibration-design.md §7.2).
 *
 * Drives one target through APPEAR -> HOLD -> SETTLE -> SAMPLE (adaptive) ->
 * CONFIRM on the supplied [CalibrationView], filtering the collected samples with
 * [FixationWindowFilter], and reports the [FixationWindowFilter.Result]. The host
 * owns sequencing, retry/exclusion policy, quality gating and logging; this owns
 * only the capture mechanics, so both screens share identical sampling instead of
 * duplicating (and silently diverging in) it.
 */
class CalibrationPointCollector(
    private val view: CalibrationView,
    private val tone: ToneGenerator?,
    private val filter: FixationWindowFilter = FixationWindowFilter(),
) {

    private val handler = Handler(Looper.getMainLooper())
    private val buffer = ArrayList<FixationWindowFilter.Sample>()
    private var collecting = false
    private var sampleStartMs = 0L
    private var onDone: ((FixationWindowFilter.Result) -> Unit)? = null

    /** Forward every raw gaze sample here; buffered only during the SAMPLE window. */
    fun onRawSample(x: Float, y: Float, timestampMs: Long) {
        if (collecting) buffer.add(FixationWindowFilter.Sample(timestampMs, x, y))
    }

    /**
     * Capture the target at [x],[y]. [onDone] receives the filtered result: on
     * ACCEPTED a confirm flash + tone have already fired; on a failure status the
     * host decides whether to re-capture. One attempt per call.
     */
    fun capture(x: Float, y: Float, onDone: (FixationWindowFilter.Result) -> Unit) {
        this.onDone = onDone
        collecting = false
        buffer.clear()
        view.showTarget(x, y)
        // APPEAR (animation) + HOLD_ATTENTION (older-adult saccadic latency).
        handler.postDelayed({ settle() }, CalibrationView.APPEAR_MS + HOLD_MS)
    }

    /** Cancel any in-flight capture (on stop / redo / activity teardown). */
    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        collecting = false
        onDone = null
    }

    /** Static, unsampled: overshoot/correction saccades land here. */
    private fun settle() {
        handler.postDelayed({ startSample() }, SETTLE_MS)
    }

    private fun startSample() {
        buffer.clear()
        collecting = true
        sampleStartMs = SystemClock.uptimeMillis()
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, TICK_TONE_MS)
        handler.postDelayed({ check() }, BASE_SAMPLE_MS)
    }

    /** Accept as soon as the filter is satisfied; else extend to the timeout. */
    private fun check() {
        val result = filter.filter(buffer.toList())
        if (result.status == FixationWindowFilter.Status.ACCEPTED) {
            collecting = false
            view.flashConfirm()
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, ACK_TONE_MS)
            onDone?.invoke(result)
            return
        }
        if (SystemClock.uptimeMillis() - sampleStartMs < SAMPLE_TIMEOUT_MS) {
            handler.postDelayed({ check() }, SAMPLE_RECHECK_MS)
        } else {
            collecting = false
            onDone?.invoke(result)
        }
    }

    companion object {
        // Per-point state timings (docs/calibration-design.md §2.3).
        private const val HOLD_MS = 500L
        private const val SETTLE_MS = 400L
        private const val BASE_SAMPLE_MS = 700L
        private const val SAMPLE_RECHECK_MS = 250L
        private const val SAMPLE_TIMEOUT_MS = 1500L
        private const val TICK_TONE_MS = 50
        private const val ACK_TONE_MS = 100
    }
}
