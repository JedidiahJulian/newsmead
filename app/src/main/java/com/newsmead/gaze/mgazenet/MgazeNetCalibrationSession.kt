package com.newsmead.gaze.mgazenet

/** Target collection only. Never encodes, logs or exports personal features. Main-thread confined. */
class MgazeNetCalibrationSession(val identity: CalibrationIdentity) : AutoCloseable {
    enum class Phase { READY, WAIT_DRAW, COLLECT, POST, FITTING, COMPLETE, FAILED }
    var phase = Phase.READY; private set
    var index = -1; private set // -1 is practice; 0..15 are fit targets
    var count = 0; private set
    var rejected = 0; private set
    private var onset = Double.NaN
    private var lastCapture = -1.0
    private var postUntil = Double.NaN
    private val rows = ArrayList<FloatArray>()
    private val labels = ArrayList<FloatArray>()
    val target get() = if (index < 0) identity.viewport.toScreen(identity.viewport.width/2f,identity.viewport.height/2f)
        else identity.targets[index]
    fun begin() { check(phase == Phase.READY); phase = Phase.WAIT_DRAW }
    fun drawn(now: Double) {
        if (phase != Phase.WAIT_DRAW) return
        require(now.isFinite()); onset = now; count = 0; phase = Phase.COLLECT
    }
    fun sample(capture: Double, output: Double, features: FloatArray?, left: Double, right: Double) {
        if (phase != Phase.COLLECT || capture < onset + SETTLE_MS || capture >= onset + TARGET_TIMEOUT_MS) return
        if (!capture.isFinite() || !output.isFinite() || output < capture || capture <= lastCapture || output >= onset + TARGET_TIMEOUT_MS) {
            rejected++; return
        }
        lastCapture = capture
        if (features == null || features.size != 258 || !features.all(Float::isFinite) ||
            !left.isFinite() || !right.isFinite() || left <= 10 || right <= 10) { rejected++; return }
        if (index >= 0) {
            rows.add(features.copyOf())
            labels.add(floatArrayOf(target.x/identity.screenWidth,target.y/identity.screenHeight))
        }
        count++
        if (count == 45) { postUntil = output + 500; phase = Phase.POST }
    }
    /** Returns true when a different target must be drawn. */
    fun tick(now: Double): Boolean {
        if (phase == Phase.COLLECT && now >= onset + TARGET_TIMEOUT_MS) { close(); return false }
        if (phase == Phase.POST && now >= postUntil) {
            if (index == 15) { phase = Phase.FITTING; return false }
            index++; phase = Phase.WAIT_DRAW; return true
        }
        return false
    }
    /** Transfers ownership to the worker; cancellation no longer races its training arrays. */
    fun takeTraining(): Pair<Array<FloatArray>,Array<FloatArray>> {
        check(phase == Phase.FITTING && rows.size == 720 && labels.size == 720)
        return (rows.toTypedArray() to labels.toTypedArray()).also { rows.clear(); labels.clear() }
    }
    fun fitted(success: Boolean) { check(phase == Phase.FITTING); phase = if (success) Phase.COMPLETE else Phase.FAILED }
    override fun close() {
        rows.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) }; rows.clear(); labels.clear()
        if (phase != Phase.COMPLETE) phase = Phase.FAILED
    }
    companion object { const val SETTLE_MS = 1500; const val TARGET_TIMEOUT_MS = 30000 }
}
