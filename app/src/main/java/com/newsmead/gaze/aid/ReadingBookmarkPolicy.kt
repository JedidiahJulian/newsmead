package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathConfig
import com.newsmead.gaze.scanpath.ScanpathCoordinateStream

/** Bounded location memory. Input breaks are observable missingness, never attention labels. */
internal class ReadingBookmarkPolicy(private val detector: ScanpathConfig) {
    data class Change(val evidence: ReadingAidBookmarkEvidence? = null, val clearReason: String? = null)
    private var source: ReadingAidContinuationEvidence? = null
    private var comparison: ReadingAidContinuationEvidence? = null
    private var landing: ReadingAidContinuationEvidence? = null
    private var shown = false
    private var lastSupport = Double.NEGATIVE_INFINITY
    private var lastInput = Double.NEGATIVE_INFINITY
    private var outsideFirst: Double? = null
    private var outsideLast: Double? = null
    private var outsideCount = 0
    private var lossFirstDelivery: Double? = null
    private var lossLastDelivery: Double? = null
    private var lossCount = 0
    private var lastNow = Double.NEGATIVE_INFINITY
    private var lastDelivery = Double.NEGATIVE_INFINITY
    val savedLine: Int? get() = source?.line

    fun reset() {
        source = null; comparison = null; landing = null; shown = false
        lastSupport = Double.NEGATIVE_INFINITY; lastInput = Double.NEGATIVE_INFINITY
        clearOutside(); clearLoss()
        lastNow = Double.NEGATIVE_INFINITY; lastDelivery = Double.NEGATIVE_INFINITY
    }

    private fun clearLoss() { lossFirstDelivery = null; lossLastDelivery = null; lossCount = 0 }

    private fun clearOutside() { outsideFirst = null; outsideLast = null; outsideCount = 0 }

    /** Preserve the saved location/cue, but never infer a transition across missing input. */
    fun interruptTransition() { comparison = null; landing = null }

    fun support(value: ReadingAidContinuationEvidence): Change {
        if (value.lastCaptureMs <= lastSupport) return Change()
        lastSupport = value.lastCaptureMs
        clearOutside(); clearLoss()
        val prior = source
        // A changing majority within one prefix cannot establish a separate landing.
        if (prior != null && value.line != prior.line && value.firstCaptureMs <= prior.lastCaptureMs)
            return Change()
        val previous = comparison
        val uninterrupted = previous != null && value.segment == previous.segment &&
            (value.firstCaptureMs == previous.firstCaptureMs ||
                value.firstCaptureMs - previous.lastCaptureMs in 0.0..detector.maxTransitionGapMs)
        comparison = value
        if (shown && prior != null) {
            val resumed = value.line == prior.line
            val continued = uninterrupted && landing?.let { (value.segment to value.firstCaptureMs) !=
                (it.segment to it.firstCaptureMs) && value.firstCaptureMs > it.lastCaptureMs } == true
            if (resumed || continued) {
                shown = false; landing = null; source = value
                return Change(clearReason = if (resumed) "saved_line_return" else "continued_reading_elsewhere")
            }
            if (!uninterrupted || landing == null) landing = value
            return Change()
        }
        if (uninterrupted && prior != null && value.firstCaptureMs > prior.lastCaptureMs &&
            value.line != prior.line && value.line != prior.line + 1) {
            shown = true; landing = value
            return Change(ReadingAidBookmarkEvidence(prior, "supported_non_normal_line_jump",
                value.firstCaptureMs, value.lastCaptureMs, value.evidenceAvailableMs, 0, value))
        }
        source = value
        return Change()
    }

    private fun confirmLoss(nowMs: Double, lossMs: Double): Change {
        val first = lossFirstDelivery ?: return Change()
        val prior = source ?: return Change()
        if (shown || nowMs - first < lossMs) return Change()
        shown = true; landing = null
        return Change(ReadingAidBookmarkEvidence(prior, "sustained_gaze_unavailable",
            null, null, nowMs, lossCount, departureTimeBasis = "delivery_elapsed_time",
            departureFirstDeliveryMs = first, departureLastDeliveryMs = lossLastDelivery))
    }

    fun input(c: ReadingAidContext, maxAgeMs: Double, lossMs: Double): Change {
        if (!c.nowMs.isFinite() || c.nowMs < 0 || c.nowMs < lastNow) { clearLoss(); clearOutside(); return Change() }
        lastNow = c.nowMs
        val s = c.presentationSample ?: return confirmLoss(c.nowMs, lossMs)
        val delivery = s.deliveryMs
        val capture = s.captureMs
        val clockValid = delivery?.isFinite() == true && delivery >= 0 && delivery <= c.nowMs &&
            delivery >= lastDelivery && (capture == null || (capture.isFinite() && capture >= 0 && capture <= delivery))
        val missing = s.exclusion in setOf("no_face", "invalid_crops", "eye_area", "stale",
            "invalid_prediction", "coordinate_unavailable")
        // Loss duration belongs to delivery/elapsed time. The watchdog repeats its last capture.
        if (missing && c.bookmarkInputAllowed &&
            s.geometrySnapshotId == c.geometrySnapshotId && clockValid) {
            val repeated = lossFirstDelivery != null && delivery == lossLastDelivery
            if (!repeated && c.nowMs - delivery!! > maxAgeMs) {
                clearLoss(); clearOutside(); return Change()
            }
            lastDelivery = delivery!!
            clearOutside(); interruptTransition()
            if (source != null && delivery >= source!!.evidenceAvailableMs) {
                if (lossFirstDelivery == null) lossFirstDelivery = delivery
                if (!repeated) { lossLastDelivery = delivery; lossCount++ }
                return confirmLoss(c.nowMs, lossMs)
            }
            clearLoss(); return Change()
        }
        clearLoss()
        if (!clockValid || s.exclusion == "invalid_clock") { clearOutside(); return Change() }
        lastDelivery = delivery!!
        val t = s.captureMs
        // Repeated policy calls for one input cannot count it twice or erase its run.
        if (t?.isFinite() == true && t == lastInput) return Change()
        val ordered = t?.isFinite() == true && t > lastInput
        if (t?.isFinite() == true) lastInput = maxOf(lastInput, t)
        val d = s.deliveryMs
        val g = c.geometry
        val v = g?.visibleRectScreen
        val x = s.processingX; val y = s.processingY
        val outside = ordered && c.bookmarkInputAllowed &&
            s.exclusion == "outside_visible_text" && s.geometrySnapshotId == c.geometrySnapshotId &&
            d?.isFinite() == true && t!! <= d && d <= c.nowMs && c.nowMs - t <= maxAgeMs &&
            x?.isFinite() == true && y?.isFinite() == true && v != null &&
            (x < v.left || x > v.right || y < v.top || y > v.bottom)
        if (!outside) { clearOutside(); return Change() }
        t!!; d!!
        if (outsideLast == null || t - outsideLast!! > detector.maxSampleGapMs) {
            outsideFirst = t; outsideCount = 0
        }
        outsideLast = t; outsideCount++
        val prior = source ?: return Change()
        if (shown || t <= prior.lastCaptureMs || outsideFirst!! <= prior.lastCaptureMs ||
            outsideCount < detector.minFixationSamples || t - outsideFirst!! < detector.minFixationMs)
            return Change()
        shown = true; landing = null
        val reason = if (s.coordinateStream == ScanpathCoordinateStream.CALIBRATED_RAW)
            "sustained_raw_outside_article" else "sustained_smoothed_outside_article"
        return Change(ReadingAidBookmarkEvidence(prior, reason,
            outsideFirst!!, t, d, outsideCount))
    }
}
