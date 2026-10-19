package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathConfig
import com.newsmead.gaze.scanpath.ScanpathCoordinateStream

/** Engineering persistence settings; no reading-benefit or accuracy claim. */
internal data class ReadingBookmarkPersistenceConfig(
    val minimumVisibleMs: Double = 1500.0,
    val savedLineReturnMs: Double = 1000.0,
    val elsewhereReadingMs: Double = 1500.0,
    val maxAgeMs: Double = 30000.0,
    /** Live V5 uses departure evidence; older comparison tests may enable this explicitly. */
    val allowLineJumpTrigger: Boolean = false
) {
    init {
        require(listOf(minimumVisibleMs, savedLineReturnMs, elsewhereReadingMs, maxAgeMs)
            .all { it.isFinite() && it > 0.0 })
        require(maxAgeMs >= minimumVisibleMs)
    }
}

/** Bounded location memory. Input breaks are observable missingness, never attention labels. */
internal class ReadingBookmarkPolicy(
    private val detector: ScanpathConfig,
    private val persistence: ReadingBookmarkPersistenceConfig? = null
) {
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
    private var shownAt: Double? = null
    private var contextKey: Pair<Long, String>? = null
    private var releasePrevious: ReadingAidContinuationEvidence? = null
    private var releaseCreditMs = 0.0
    private var releaseIsReturn: Boolean? = null
    // Raw article presence can preserve a chain while its next row settles; it earns no time.
    private var presenceFirst: Double? = null
    private var presenceLast: Double? = null
    private val maximumSupportIntervalMs = 250.0
    private fun supportedMappingExclusion(reason: String?) = reason == null || reason in
        setOf("unassigned_line", "unassigned_word", "ambiguous_line", "ambiguous_word")
    val savedLine: Int? get() = source?.line

    fun reset() {
        source = null; comparison = null; landing = null; shown = false
        shownAt = null; contextKey = null; clearRelease()
        clearPresence()
        lastSupport = Double.NEGATIVE_INFINITY; lastInput = Double.NEGATIVE_INFINITY
        clearOutside(); clearLoss()
        lastNow = Double.NEGATIVE_INFINITY; lastDelivery = Double.NEGATIVE_INFINITY
    }

    private fun clearLoss() { lossFirstDelivery = null; lossLastDelivery = null; lossCount = 0 }

    private fun clearOutside() { outsideFirst = null; outsideLast = null; outsideCount = 0 }

    /** Preserve the saved location/cue, but never infer a transition across missing input. */
    fun interruptTransition() { comparison = null; landing = null; clearRelease(); clearPresence() }

    /** A different coarse row without mapper agreement cannot become a precise saved line. */
    fun withholdUncertainSource() {
        if (persistence == null || shown) return
        source = null; comparison = null; landing = null
        lastSupport = Double.NEGATIVE_INFINITY
        clearOutside(); clearLoss(); clearRelease(); clearPresence()
    }

    private fun clearPresence() { presenceFirst = null; presenceLast = null }

    private fun clearRelease() {
        releasePrevious = null; releaseCreditMs = 0.0; releaseIsReturn = null
    }

    private fun persistenceBoundary(c: ReadingAidContext): Change? {
        val key = c.generation to c.geometrySnapshotId
        if (!c.bookmarkInputAllowed || (contextKey != null && contextKey != key)) {
            val hadCue = shown
            reset()
            contextKey = key
            return Change(clearReason = if (hadCue) "bookmark_geometry_or_input_excluded" else null)
        }
        contextKey = key
        if (shown && shownAt?.let { c.nowMs.isFinite() && c.nowMs - it >= persistence!!.maxAgeMs } == true) {
            reset(); contextKey = key
            return Change(clearReason = "bookmark_max_age")
        }
        return null
    }

    /** Fresh stabilized row support, independent of word or horizontal row-zone assignment. */
    fun support(value: ReadingAidContinuationEvidence, c: ReadingAidContext): Change {
        if (persistence == null) return support(value)
        persistenceBoundary(c)?.let { return it }
        val valid = c.nowMs.isFinite() && c.nowMs >= 0.0 && c.validLiveInput &&
            value.line in 0 until c.lineCount && value.firstCaptureMs.isFinite() &&
            value.lastCaptureMs.isFinite() && value.evidenceAvailableMs.isFinite() &&
            value.firstCaptureMs >= 0.0 && value.lastCaptureMs >= value.firstCaptureMs &&
            value.evidenceAvailableMs >= value.lastCaptureMs && c.nowMs >= value.evidenceAvailableMs &&
            c.nowMs - value.lastCaptureMs <= maximumSupportIntervalMs &&
            c.nowMs >= lastNow &&
            c.presentationSample?.let { supportedMappingExclusion(it.exclusion) &&
                it.geometrySnapshotId == c.geometrySnapshotId && it.captureMs == value.lastCaptureMs &&
                it.deliveryMs?.let { delivery -> delivery.isFinite() &&
                    delivery >= value.lastCaptureMs && delivery <= c.nowMs } == true } != false
        if (!valid) { interruptTransition(); return Change() }
        if (value.lastCaptureMs <= lastSupport) return Change()
        if (!shown) {
            val change = support(value)
            if (change.evidence != null) { shownAt = c.nowMs; clearRelease() }
            return change
        }
        lastSupport = value.lastCaptureMs
        clearOutside(); clearLoss()
        val prior = source ?: return Change()
        val returning = value.line == prior.line
        val previous = releasePrevious
        val directlyCovered = previous != null && value.firstCaptureMs <= previous.lastCaptureMs &&
            value.lastCaptureMs - previous.lastCaptureMs <= maximumSupportIntervalMs
        val presenceBridge = previous != null && presenceFirst?.let { it <= previous.lastCaptureMs } == true &&
            presenceLast == value.lastCaptureMs
        val sameChain = previous != null && previous.segment == value.segment &&
            releaseIsReturn == returning && (directlyCovered || presenceBridge) &&
            (returning || value.line == previous.line || value.line == previous.line + 1)
        val intervalStart = if (sameChain) maxOf(value.firstCaptureMs, previous!!.lastCaptureMs)
            else value.firstCaptureMs
        val intervalMs = value.lastCaptureMs - intervalStart
        // Neither uncovered capture gaps nor repeated/overlapping portions earn credit.
        if (!sameChain) releaseCreditMs = 0.0
        if (intervalMs in 0.0..maximumSupportIntervalMs) releaseCreditMs += intervalMs
        else releaseCreditMs = 0.0
        releasePrevious = value; releaseIsReturn = returning
        val required = if (returning) persistence.savedLineReturnMs else persistence.elsewhereReadingMs
        if (releaseCreditMs >= required && c.nowMs - (shownAt ?: c.nowMs) >= persistence.minimumVisibleMs) {
            shown = false; shownAt = null; landing = null; source = value; comparison = value
            clearRelease()
            return Change(clearReason = if (returning) "saved_line_return" else "continued_reading_elsewhere")
        }
        return Change()
    }

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
        if ((persistence == null || persistence.allowLineJumpTrigger) &&
            uninterrupted && prior != null && value.firstCaptureMs > prior.lastCaptureMs &&
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
        if (persistence != null) { shownAt = nowMs; clearRelease() }
        return Change(ReadingAidBookmarkEvidence(prior, "sustained_gaze_unavailable",
            null, null, nowMs, lossCount, departureTimeBasis = "delivery_elapsed_time",
            departureFirstDeliveryMs = first, departureLastDeliveryMs = lossLastDelivery))
    }

    fun input(c: ReadingAidContext, maxAgeMs: Double, lossMs: Double): Change {
        if (persistence != null) {
            persistenceBoundary(c)?.let { return it }
            val sample = c.presentationSample
            val rect = c.geometry?.takeIf { it.snapshotId == c.geometrySnapshotId }?.visibleRectScreen
            val t = sample?.captureMs
            val x = sample?.processingX; val y = sample?.processingY
            if (!c.nowMs.isFinite() || c.nowMs < lastNow ||
                sample == null || !supportedMappingExclusion(sample.exclusion) ||
                sample.geometrySnapshotId != c.geometrySnapshotId ||
                x?.isFinite() != true || y?.isFinite() != true || rect == null ||
                x !in rect.left..rect.right || y !in rect.top..rect.bottom ||
                sample.captureMs?.let { it.isFinite() && it >= 0 && it <= c.nowMs &&
                    c.nowMs - it <= maximumSupportIntervalMs } != true ||
                sample.deliveryMs?.let { it.isFinite() && it >= (sample.captureMs ?: Double.POSITIVE_INFINITY) &&
                    it <= c.nowMs && it >= lastDelivery } != true ||
                (t != null && t < lastInput)) {
                clearRelease(); clearPresence()
            } else if (t != presenceLast) {
                // A duplicate is neutral only after freshness and validity have been checked.
                if (presenceLast == null || t!! - presenceLast!! > maximumSupportIntervalMs) {
                    clearRelease(); presenceFirst = t
                }
                presenceLast = t
            }
        }
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
            outsideCount < detector.minFixationSamples || t - outsideFirst!! <
                (if (persistence != null) lossMs else detector.minFixationMs))
            return Change()
        shown = true; landing = null
        if (persistence != null) { shownAt = c.nowMs; clearRelease() }
        val reason = if (s.coordinateStream == ScanpathCoordinateStream.CALIBRATED_RAW)
            "sustained_raw_outside_article" else "sustained_smoothed_outside_article"
        return Change(ReadingAidBookmarkEvidence(prior, reason,
            outsideFirst!!, t, d, outsideCount))
    }
}
