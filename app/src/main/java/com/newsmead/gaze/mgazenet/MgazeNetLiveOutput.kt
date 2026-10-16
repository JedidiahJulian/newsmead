package com.newsmead.gaze.mgazenet

/** Shared output for the reading dot, observations and aid input; no replay of a hidden point. */
internal class MgazeNetLiveOutput(width: Int, height: Int,
    private val smoothForReading: Boolean, private val rejectBlinks: Boolean) {
    data class Result(val reason: String, val rawX: Float?, val rawY: Float?,
        val x: Float?, val y: Float?) {
        val emitted get() = reason == "coordinate"
    }
    private val outputGate = MgazeNetOutputGate(width, height)
    private val blinkGate = MgazeNetBlinkGate()
    private val smoother = MgazeNetReadingSmoother()

    fun evaluate(captureMs: Double, deliveryMs: Double, eligible: Boolean,
                 prediction: FloatArray?, sourceReason: String,
                 eyes: MgazeNetEyelidGeometry.Sample?): Result {
        val gated = outputGate.evaluate(captureMs, deliveryMs, eligible, prediction, sourceReason)
        // A duplicate/expired frame cannot count toward reopening after a reset.
        if (gated.reason == "invalid_clock" || gated.reason == "stale") {
            invalidate()
            return Result(gated.reason, gated.rawX, gated.rawY, null, null)
        }
        // A very small eye area may itself be a measured closure. Preserve no-face/
        // crop failures, while identifying known closed lids as blink rather than look-away.
        val checkEyes = rejectBlinks && (eligible || (sourceReason == "eye_area" && eyes != null))
        val eyeReason = if (checkEyes) blinkGate.evaluate(captureMs, eyes) else null
        if (rejectBlinks && !checkEyes) blinkGate.reset()
        val reason = eyeReason ?: gated.reason
        if (reason != "coordinate") {
            smoother.reset()
            return Result(reason, gated.rawX, gated.rawY, null, null)
        }
        val x = if (smoothForReading) smoother.filterX(gated.rawX!!, captureMs.toLong()) else gated.rawX
        val y = if (smoothForReading) smoother.filterY(gated.rawY!!, captureMs.toLong()) else gated.rawY
        return Result(reason, gated.rawX, gated.rawY, x, y)
    }

    /** Expiry resets temporal filters, while the output clock still rejects repeated frames. */
    fun invalidate() { smoother.reset(); blinkGate.reset() }
}
