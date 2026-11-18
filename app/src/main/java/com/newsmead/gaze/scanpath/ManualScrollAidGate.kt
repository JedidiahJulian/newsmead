package com.newsmead.gaze.scanpath

/** Gesture provenance only; this gate never drives scrolling or authorizes layout changes. */
internal class ManualScrollAidGate(private val stableGapMs: Double) {
    enum class Translation { UNCONTROLLED, FIRST, CONTINUING }

    private var touching = false
    private var lastTranslationMs: Double? = null

    init { require(stableGapMs.isFinite() && stableGapMs > 0) }

    fun gesture(active: Boolean, nowMs: Double, cancelled: Boolean = false) {
        if (cancelled || !nowMs.isFinite()) { reset(); return }
        if (active) {
            // Duplicate DOWN does not discard a moving gesture's provenance.
            if (!touching) { reset(); touching = true }
        } else {
            touching = false
            expire(nowMs)
        }
    }

    fun observe(nowMs: Double, translated: Boolean, compatible: Boolean = true): Translation {
        if (!nowMs.isFinite() || !compatible ||
            lastTranslationMs?.let { nowMs < it } == true) {
            reset()
            return Translation.UNCONTROLLED
        }
        expire(nowMs)
        if (!translated || (!touching && lastTranslationMs == null)) return Translation.UNCONTROLLED
        val result = if (lastTranslationMs == null) Translation.FIRST else Translation.CONTINUING
        lastTranslationMs = nowMs
        return result
    }

    /** Returns whether movement had started, so a touch-only cancellation need not reset evidence. */
    fun reset(): Boolean {
        val moved = lastTranslationMs != null
        touching = false
        lastTranslationMs = null
        return moved
    }

    private fun expire(nowMs: Double) {
        if (!touching && lastTranslationMs?.let { nowMs - it >= stableGapMs } == true) reset()
    }
}
