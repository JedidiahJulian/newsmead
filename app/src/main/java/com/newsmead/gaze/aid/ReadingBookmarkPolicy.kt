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
    val allowLineJumpTrigger: Boolean = false,
    /** Compatibility name: fresh eligible in-article presence, not attentive reading. */
    val minimumAnchorSupportMs: Double = 1500.0,
    /** Compatibility name: post-trigger mapped presence; clearing also requires fresh spatial support. */
    val minimumRecoverySupportMs: Double = 3000.0,
    /** Once fresh article input returns, a cue cannot linger through noisy recovery indefinitely. */
    val maximumVisibleAfterReturnMs: Double = 5000.0,
    /** Historical comparison only; production retains location until supported passage. */
    val useProgressPersistence: Boolean = true
) {
    init {
        require(listOf(minimumVisibleMs, savedLineReturnMs, elsewhereReadingMs, maxAgeMs, maximumVisibleAfterReturnMs)
            .all { it.isFinite() && it > 0.0 })
        require(listOf(minimumAnchorSupportMs, minimumRecoverySupportMs).all { it.isFinite() && it >= 0.0 })
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
    private var returnedAt: Double? = null
    private var contextKey: Pair<Long, String>? = null
    private var releasePrevious: ReadingAidContinuationEvidence? = null
    private var releaseCreditMs = 0.0
    private var releaseIsReturn: Boolean? = null
    // Presence preserves spatial history through settling; its capture deltas have a separate UI clock.
    private var presenceFirst: Double? = null
    private var presenceLast: Double? = null
    private val maximumSupportIntervalMs = 250.0
    private data class Trusted(val value: ReadingAidContinuationEvidence, val mature: Boolean)
    private val trusted = linkedMapOf<String, Trusted>()
    private var anchorPrevious: ReadingAidContinuationEvidence? = null
    private var anchorCreditMs = 0.0
    private var anchorArmed = false
    private var maturityPresenceLast: Double? = null
    private var recoveryPresenceLast: Double? = null
    private var recoveryPresenceLine: Int? = null
    private var lastInsideLine: Int? = null
    private var recoveryPrevious: ReadingAidContinuationEvidence? = null
    private var recoveryCreditMs = 0.0
    private var recoveryFloor = Double.NEGATIVE_INFINITY
    private var rereadBookmark = false
    private var lastRereadEnd = Double.NEGATIVE_INFINITY
    private var forwardSource: ReadingAidContinuationEvidence? = null
    private var backwardCandidate: ReadingAidContinuationEvidence? = null
    private var rereadStartLine: Int? = null
    private var furthestRereadLine: Int? = null
    var rereadProgress: Double = 0.0
        private set
    val diagnosticRoute: String? get() = if (shown && persistence?.useProgressPersistence == true)
        "supported_forward_passage" else null
    private val progressMode: Boolean get() = persistence?.useProgressPersistence == true
    private fun resetCueProgress(startLine: Int? = null) {
        rereadStartLine = startLine; furthestRereadLine = startLine; rereadProgress = 0.0
    }
    private fun supportedMappingExclusion(reason: String?) = reason == null || reason in
        setOf("unassigned_line", "unassigned_word", "ambiguous_line", "ambiguous_word")
    val savedLine: Int? get() = source?.line

    fun auditSnapshot(): Map<String, Any?> = mapOf("reread_progress" to rereadProgress, "route" to diagnosticRoute, "source" to source, "comparison" to comparison,
        "landing" to landing, "shown" to shown, "shown_at_ms" to shownAt, "returned_at_ms" to returnedAt,
        "outside_first_ms" to outsideFirst, "outside_last_ms" to outsideLast, "outside_count" to outsideCount,
        "loss_first_delivery_ms" to lossFirstDelivery, "loss_last_delivery_ms" to lossLastDelivery,
        "loss_count" to lossCount, "release_credit_ms" to releaseCreditMs,
        "release_previous" to releasePrevious, "release_is_return" to releaseIsReturn,
        "presence_first_ms" to presenceFirst, "presence_last_ms" to presenceLast,
        "anchor_credit_ms" to anchorCreditMs, "anchor_presence_credit_ms" to anchorCreditMs,
        "anchor_armed" to anchorArmed, "last_inside_line" to lastInsideLine,
        "recovery_credit_ms" to recoveryCreditMs, "recovery_presence_credit_ms" to recoveryCreditMs, "reread_bookmark" to rereadBookmark)

    fun reset() {
        source = null; comparison = null; landing = null; shown = false
        forwardSource = null; backwardCandidate = null
        rereadStartLine = null; furthestRereadLine = null; rereadProgress = 0.0
        shownAt = null; returnedAt = null; contextKey = null; clearRelease()
        clearPresence()
        trusted.clear(); anchorPrevious = null; anchorCreditMs = 0.0; anchorArmed = false
        maturityPresenceLast = null; lastInsideLine = null
        recoveryFloor = Double.NEGATIVE_INFINITY; rereadBookmark = false; lastRereadEnd = Double.NEGATIVE_INFINITY
        lastSupport = Double.NEGATIVE_INFINITY; lastInput = Double.NEGATIVE_INFINITY
        clearOutside(); clearLoss()
        lastNow = Double.NEGATIVE_INFINITY; lastDelivery = Double.NEGATIVE_INFINITY
    }

    private fun clearLoss() { lossFirstDelivery = null; lossLastDelivery = null; lossCount = 0 }

    private fun clearOutside() { outsideFirst = null; outsideLast = null; outsideCount = 0 }

    /** Preserve the saved location/cue, but never infer a transition across missing input. */
    fun interruptTransition() {
        comparison = null; landing = null; clearRelease(); clearPresence()
        forwardSource = null; backwardCandidate = null
        trusted.clear(); anchorPrevious = null; anchorCreditMs = 0.0; maturityPresenceLast = null
    }

    /** A different coarse row without mapper agreement cannot become a precise saved line. */
    fun withholdUncertainSource() {
        if (persistence == null || shown) return
        source = null; comparison = null; landing = null
        maturityPresenceLast = null; lastInsideLine = null
        trusted.clear(); anchorPrevious = null; anchorCreditMs = 0.0; anchorArmed = false
        lastSupport = Double.NEGATIVE_INFINITY
        clearOutside(); clearLoss(); clearRelease(); clearPresence()
    }

    private fun clearPresence() { presenceFirst = null; presenceLast = null }

    private fun clearRelease() {
        releasePrevious = null; releaseCreditMs = 0.0; releaseIsReturn = null
        recoveryPrevious = null; recoveryCreditMs = 0.0
        recoveryPresenceLast = null; recoveryPresenceLine = null
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
        if (!progressMode && shown && shownAt?.let { c.nowMs.isFinite() && c.nowMs - it >= persistence!!.maxAgeMs } == true) {
            reset(); contextKey = key
            return Change(clearReason = "bookmark_max_age")
        }
        if (!progressMode && shown && returnedAt?.let { c.nowMs.isFinite() &&
                c.nowMs - it >= persistence!!.maximumVisibleAfterReturnMs } == true) {
            reset(); contextKey = key
            return Change(clearReason = "bookmark_return_visibility_limit")
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
            (!progressMode || c.presentationSample?.let {
                it.eligible && it.exclusion == null && it.line == value.line
            } == true) &&
            c.presentationSample?.let { supportedMappingExclusion(it.exclusion) &&
                it.geometrySnapshotId == c.geometrySnapshotId && it.captureMs == value.lastCaptureMs &&
                it.deliveryMs?.let { delivery -> delivery.isFinite() &&
                    delivery >= value.lastCaptureMs && delivery <= c.nowMs } == true } != false
        if (!valid) { interruptTransition(); if (!progressMode) anchorArmed = false; return Change() }
        if (value.lastCaptureMs <= lastSupport) return Change()
        if (!shown) {
            if (source?.let { value.line != it.line && value.firstCaptureMs <= it.lastCaptureMs } == true) {
                lastSupport = value.lastCaptureMs
                interruptTransition()
                return Change()
            }
            val previous = anchorPrevious
            val bridge = previous != null && presenceFirst?.let { it <= previous.lastCaptureMs } == true &&
                presenceLast == value.lastCaptureMs
            // Maturity is a presence clock; spatial stabilization still gates this exact anchor.
            anchorArmed = (progressMode && anchorArmed) || anchorCreditMs >= persistence.minimumAnchorSupportMs
            if (previous != null && (previous.segment != value.segment ||
                    (value.firstCaptureMs - previous.lastCaptureMs > maximumSupportIntervalMs && !bridge))) {
                trusted.clear(); forwardSource = null; backwardCandidate = null
            }
            anchorPrevious = value
            if (value.evidenceId.startsWith("bookmark:")) {
                trusted[value.evidenceId] = Trusted(value, anchorArmed)
                while (trusted.size > 64) trusted.remove(trusted.keys.first())
            }
            if (progressMode) {
                supportedBackwardDeparture(value, c, previous)?.let { return it }
            }
            val change = support(value)
            if (change.evidence != null) { shownAt = c.nowMs; recoveryFloor = value.lastCaptureMs; clearRelease() }
            return change
        }
        lastSupport = value.lastCaptureMs
        clearOutside(); clearLoss()
        val prior = source ?: return Change()
        if (progressMode) return progressSupport(value, prior)
        val returning = if (rereadBookmark) value.line >= prior.line else value.line == prior.line
        // Zero-hold comparison fixtures retain the older supported-tail route clocks.
        if (persistence.minimumRecoverySupportMs == 0.0) {
            val recovery = recoveryPrevious
            val recoveryBridge = recovery != null && presenceFirst?.let { it <= recovery.lastCaptureMs } == true &&
                presenceLast == value.lastCaptureMs
            val recoveryChain = recovery != null && recovery.segment == value.segment &&
                value.line in recovery.line..recovery.line + 1 &&
                ((value.firstCaptureMs <= recovery.lastCaptureMs &&
                    value.lastCaptureMs - recovery.lastCaptureMs <= maximumSupportIntervalMs) || recoveryBridge)
            val recoveryStart = if (recoveryChain) maxOf(value.firstCaptureMs, recovery!!.lastCaptureMs) else value.firstCaptureMs
            val recoveryInterval = value.lastCaptureMs - maxOf(recoveryStart, recoveryFloor)
            if (!recoveryChain || (rereadBookmark && !returning)) recoveryCreditMs = 0.0
            if ((!rereadBookmark || returning) && recoveryInterval in 0.0..maximumSupportIntervalMs)
                recoveryCreditMs += recoveryInterval
            else recoveryCreditMs = 0.0
            recoveryPrevious = value
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
            val intervalMs = value.lastCaptureMs - maxOf(intervalStart, recoveryFloor)
            // Neither uncovered capture gaps nor repeated/overlapping portions earn credit.
            if (!sameChain) releaseCreditMs = 0.0
            if (intervalMs in 0.0..maximumSupportIntervalMs) releaseCreditMs += intervalMs
            else releaseCreditMs = 0.0
            releasePrevious = value; releaseIsReturn = returning
        }
        val required = if (returning) persistence.savedLineReturnMs else persistence.elsewhereReadingMs
        val freshPreciseRelease = persistence.minimumRecoverySupportMs == 0.0 ||
            c.presentationSample?.let { it.eligible && it.exclusion == null && it.line == value.line &&
                it.captureMs == value.lastCaptureMs && recoveryPresenceLast == value.lastCaptureMs } == true
        // Current recovery can cross the saved row without starting an extra route hold.
        val routeRecovered = persistence.minimumRecoverySupportMs > 0.0 ||
            (releaseIsReturn == returning && releaseCreditMs >= required)
        if (freshPreciseRelease && routeRecovered &&
            recoveryCreditMs >= persistence.minimumRecoverySupportMs && c.nowMs - (shownAt ?: c.nowMs) >= persistence.minimumVisibleMs) {
            shown = false; shownAt = null; returnedAt = null; landing = null; source = value; comparison = value
            clearRelease(); rereadBookmark = false; anchorPrevious = null; anchorCreditMs = 0.0; anchorArmed = false
            trusted.clear()
            return Change(clearReason = if (returning) "saved_line_return" else "continued_reading_elsewhere")
        }
        return Change()
    }

    /** Exact adjacent support is required: neither a raw row nor a jumped-to row proves passage. */
    private fun adjacent(a: ReadingAidContinuationEvidence?, b: ReadingAidContinuationEvidence): Boolean =
        a != null && a.segment == b.segment && b.line == a.line + 1 &&
            b.firstCaptureMs > a.lastCaptureMs &&
            (b.firstCaptureMs - a.lastCaptureMs <= maximumSupportIntervalMs ||
                (presenceFirst?.let { it <= a.lastCaptureMs } == true && presenceLast == b.lastCaptureMs))

    private fun progressSupport(value: ReadingAidContinuationEvidence,
                                saved: ReadingAidContinuationEvidence): Change {
        val previous = releasePrevious
        val forward = adjacent(previous, value)
        if (rereadStartLine == null && value.line <= saved.line) {
            rereadStartLine = value.line; furthestRereadLine = value.line
        }
        if (forward && rereadStartLine != null && value.line <= saved.line) {
            furthestRereadLine = maxOf(furthestRereadLine ?: value.line, value.line)
            val distance = saved.line - rereadStartLine!!
            if (distance > 0) rereadProgress = maxOf(rereadProgress,
                ((furthestRereadLine!! - rereadStartLine!!).toDouble() / distance).coerceIn(0.0, 1.0))
        }
        releasePrevious = value
        if (forward && previous!!.line >= saved.line) {
            shown = false; shownAt = null; returnedAt = null; landing = null
            resetCueProgress()
            source = value; comparison = value; clearRelease()
            rereadBookmark = false; anchorPrevious = null; forwardSource = null; backwardCandidate = null
            trusted.clear()
            return Change(clearReason = "supported_forward_passage")
        }
        return Change()
    }

    private fun supportedBackwardDeparture(value: ReadingAidContinuationEvidence, c: ReadingAidContext,
                                           previous: ReadingAidContinuationEvidence?): Change? {
        val frontier = forwardSource
        if (frontier != null && value.segment == frontier.segment && value.line <= frontier.line - 3 &&
            value.firstCaptureMs > frontier.lastCaptureMs) {
            val candidate = backwardCandidate
            backwardCandidate = value
            if (candidate != null && candidate.segment == value.segment && candidate.line == value.line &&
                candidate.firstCaptureMs == value.firstCaptureMs &&
                value.lastCaptureMs > candidate.lastCaptureMs &&
                value.lastCaptureMs - candidate.lastCaptureMs <= maximumSupportIntervalMs &&
                value.lastCaptureMs - value.firstCaptureMs >= detector.minFixationMs && anchorArmed) {
                source = frontier; lastSupport = value.lastCaptureMs; shown = true; shownAt = c.nowMs
                returnedAt = c.nowMs; rereadBookmark = true; recoveryFloor = value.lastCaptureMs
                resetCueProgress(value.line)
                lastRereadEnd = value.lastCaptureMs
                clearOutside(); clearLoss(); clearRelease()
                return Change(ReadingAidBookmarkEvidence(frontier, "supported_backward_reading",
                    value.firstCaptureMs, value.lastCaptureMs, value.evidenceAvailableMs, 0,
                    departureSupport = value))
            }
            return null
        }
        backwardCandidate = null
        if (adjacent(previous, value)) forwardSource = value
        else if (frontier != null && value.line == frontier.line && value.segment == frontier.segment)
            forwardSource = value
        else if (previous != null && value.line != previous.line) forwardSource = null
        return null
    }

    /** Shared coarse witness may identify a departure, but only stored exact support anchors it. */
    fun reread(e: ReadingAidRereadEvidence, c: ReadingAidContext): Change {
        val settings = persistence ?: return Change()
        persistenceBoundary(c)?.let { return it }
        if (shown || !c.validLiveInput || !c.nowMs.isFinite() || c.nowMs < lastNow ||
            !e.confirmedDeliveryMs.isFinite() || c.nowMs - e.confirmedDeliveryMs !in 0.0..maximumSupportIntervalMs ||
            e.visits.size < 5 || e.firstLine !in 0 until c.lineCount ||
            e.lastLine !in e.firstLine until c.lineCount) return Change()
        val visits = e.visits
        val current = c.presentationSample
        if (current == null || !current.eligible || current.exclusion != null ||
            current.geometrySnapshotId != c.geometrySnapshotId ||
            current.captureMs != visits.last().lastCaptureMs || current.line != visits.last().line ||
            current.deliveryMs?.let { it.isFinite() && it >= visits.last().lastCaptureMs && it <= c.nowMs } != true)
            return Change()
        if (visits.first().firstCaptureMs <= lastRereadEnd ||
            visits.map { it.evidenceId }.distinct().size != visits.size ||
            visits.any { it.segment != visits.first().segment || it.line !in e.firstLine..e.lastLine ||
                !it.firstCaptureMs.isFinite() || !it.lastCaptureMs.isFinite() ||
                !it.evidenceAvailableMs.isFinite() || it.firstCaptureMs < 0.0 ||
                it.lastCaptureMs < it.firstCaptureMs || it.evidenceAvailableMs < it.lastCaptureMs ||
                it.evidenceAvailableMs > e.confirmedDeliveryMs } ||
            visits.zipWithNext().any { (a, b) -> a.lastCaptureMs >= b.firstCaptureMs } ||
            visits.last().lastCaptureMs != lastSupport ||
            c.nowMs - visits.last().lastCaptureMs !in 0.0..maximumSupportIntervalMs ||
            (!progressMode && visits.last().lastCaptureMs - visits.first().firstCaptureMs > settings.maxAgeMs)) return Change()
        fun exact(v: ReadingAidContinuationEvidence): Trusted? = trusted["bookmark:${v.evidenceId}"]?.takeIf {
            it.value.segment == v.segment && it.value.line == v.line &&
                it.value.firstCaptureMs >= v.firstCaptureMs && it.value.lastCaptureMs <= v.lastCaptureMs &&
                it.value.lastCaptureMs >= v.firstCaptureMs && it.value.evidenceAvailableMs <= e.confirmedDeliveryMs
        }
        // A complete precise history also prevents a witness spanning a rejected mapper interval.
        if (visits.any { exact(it) == null }) return Change()
        for (returned in visits.lastIndex - 2 downTo 2) {
            val frontier = visits[returned - 1]
            val back = visits[returned]
            val separation = if (e.measuredDistinguishability) 1 else 3
            if (frontier.line - back.line < separation || kotlin.math.abs(back.line - visits.first().line) > (if (e.measuredDistinguishability) 0 else 2) ||
                visits.last().line > frontier.line ||
                visits.subList(0, returned).zipWithNext().any { (a, b) -> a.line >= b.line } ||
                visits.subList(returned, visits.size).zipWithNext().any { (a, b) -> a.line >= b.line }) continue
            val precise = exact(frontier) ?: continue
            if (!precise.mature) continue
            source = precise.value; shown = true; shownAt = c.nowMs; returnedAt = c.nowMs; landing = null
            recoveryFloor = visits.last().lastCaptureMs
            rereadBookmark = true; lastRereadEnd = visits.last().lastCaptureMs
            if (progressMode) resetCueProgress(back.line)
            clearOutside(); clearLoss(); clearRelease()
            return Change(ReadingAidBookmarkEvidence(precise.value, "supported_broad_rereading",
                back.firstCaptureMs, back.lastCaptureMs, e.confirmedDeliveryMs, 0,
                departureSupport = back, reread = e))
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
        if (shown || (persistence != null && (!anchorArmed ||
                (!progressMode && persistence.minimumAnchorSupportMs > 0.0 && lastInsideLine != prior.line))) || nowMs - first < lossMs) return Change()
        shown = true; landing = null
        if (progressMode) resetCueProgress()
        if (persistence != null) { shownAt = nowMs; recoveryFloor = nowMs; clearRelease() }
        return Change(ReadingAidBookmarkEvidence(prior, "sustained_gaze_unavailable",
            null, null, nowMs, lossCount, departureTimeBasis = "delivery_elapsed_time",
            departureFirstDeliveryMs = first, departureLastDeliveryMs = lossLastDelivery))
    }

    /** UI residence only. Each eligible capture interval is counted once, regardless of row dwell. */
    private fun observeEligiblePresence(c: ReadingAidContext) {
        val settings = persistence ?: return
        val s = c.presentationSample
        val t = s?.captureMs
        val d = s?.deliveryMs
        val rect = c.geometry?.takeIf { it.snapshotId == c.geometrySnapshotId }?.visibleRectScreen
        val line = s?.line
        val x = s?.processingX; val y = s?.processingY
        val valid = s != null && s.eligible && s.exclusion == null && line != null &&
            line in 0 until c.lineCount && s.geometrySnapshotId == c.geometrySnapshotId &&
            c.nowMs.isFinite() && c.nowMs >= lastNow && t != null && t.isFinite() && t >= 0.0 &&
            c.nowMs - t in 0.0..maximumSupportIntervalMs && t >= lastInput &&
            d != null && d.isFinite() && d >= t && d <= c.nowMs && d >= lastDelivery &&
            rect != null && x?.isFinite() == true && y?.isFinite() == true &&
            x in rect.left..rect.right && y in rect.top..rect.bottom
        if (!valid) {
            maturityPresenceLast = null; anchorCreditMs = 0.0
            if (settings.minimumRecoverySupportMs > 0.0) clearRelease()
            // A UI tick can age the last coordinate before the provider reports its loss.
            // It breaks clocks/history, but cannot erase an already established departure latch.
            val agedRetainedCapture = s != null && s.eligible && s.exclusion == null &&
                s.geometrySnapshotId == c.geometrySnapshotId && t != null && t.isFinite() &&
                t == lastInput && c.nowMs.isFinite() && c.nowMs >= lastNow &&
                c.nowMs - t > maximumSupportIntervalMs && d != null && d.isFinite() &&
                d >= t && d <= c.nowMs && d >= lastDelivery
            if (!progressMode && !agedRetainedCapture && s?.exclusion !in setOf("no_face", "invalid_crops", "eye_area", "stale",
                    "invalid_prediction", "coordinate_unavailable", "outside_visible_text")) anchorArmed = false
            return
        }
        t!!; line!!
        val previous = maturityPresenceLast
        if (previous != t) {
            val delta = previous?.let { t - it }
            if (delta != null && delta in 0.0..maximumSupportIntervalMs) anchorCreditMs += delta
            else anchorCreditMs = 0.0
            maturityPresenceLast = t
            anchorArmed = (progressMode && anchorArmed) || anchorCreditMs >= settings.minimumAnchorSupportMs
        }
        lastInsideLine = line
        if (!shown) return
        if (returnedAt == null) returnedAt = c.nowMs
        if (settings.minimumRecoverySupportMs == 0.0) return
        val prior = source ?: return
        val previousCapture = recoveryPresenceLast
        if (previousCapture == t) return
        val previousLine = recoveryPresenceLine
        val delta = previousCapture?.let { t - it }
        val chain = delta != null && delta in 0.0..maximumSupportIntervalMs &&
            previousLine != null && line in previousLine..previousLine + 1
        val returning = if (rereadBookmark) line >= prior.line else line == prior.line
        if (!chain) recoveryCreditMs = 0.0
        if (!chain || releaseIsReturn != returning) releaseCreditMs = 0.0
        if (chain) {
            val credited = (t - maxOf(previousCapture!!, recoveryFloor)).coerceAtLeast(0.0)
            recoveryCreditMs += credited
            // A new route starts at this capture; its preceding interval belonged to the old route.
            if (releaseIsReturn == returning) releaseCreditMs += credited
        }
        releaseIsReturn = returning
        recoveryPresenceLast = t; recoveryPresenceLine = line
    }

    fun input(c: ReadingAidContext, maxAgeMs: Double, lossMs: Double): Change {
        if (persistence != null) {
            persistenceBoundary(c)?.let { return it }
            val sample = c.presentationSample
            val rect = c.geometry?.takeIf { it.snapshotId == c.geometrySnapshotId }?.visibleRectScreen
            val t = sample?.captureMs
            val x = sample?.processingX; val y = sample?.processingY
            if (!c.nowMs.isFinite() || c.nowMs < lastNow ||
                sample == null || (progressMode && sample.exclusion != null) ||
                !supportedMappingExclusion(sample.exclusion) ||
                sample.geometrySnapshotId != c.geometrySnapshotId ||
                x?.isFinite() != true || y?.isFinite() != true || rect == null ||
                x !in rect.left..rect.right || y !in rect.top..rect.bottom ||
                sample.captureMs?.let { it.isFinite() && it >= 0 && it <= c.nowMs &&
                    c.nowMs - it <= maximumSupportIntervalMs } != true ||
                sample.deliveryMs?.let { it.isFinite() && it >= (sample.captureMs ?: Double.POSITIVE_INFINITY) &&
                    it <= c.nowMs && it >= lastDelivery } != true ||
                (t != null && t < lastInput)) {
                interruptTransition()
            } else if (t != presenceLast) {
                // A duplicate is neutral only after freshness and validity have been checked.
                if (presenceLast == null || t!! - presenceLast!! > maximumSupportIntervalMs) {
                    clearRelease(); trusted.clear(); anchorPrevious = null; anchorCreditMs = 0.0
                    forwardSource = null; backwardCandidate = null
                    presenceFirst = t
                }
                presenceLast = t
            }
            observeEligiblePresence(c)
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
                if (lossFirstDelivery == null) {
                    if (progressMode && delivery - source!!.evidenceAvailableMs !in 0.0..maxAgeMs) return Change()
                    lossFirstDelivery = delivery
                }
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
            if (progressMode && source?.let { t - it.lastCaptureMs !in 0.0..maxAgeMs } != false) {
                clearOutside(); return Change()
            }
        }
        outsideLast = t; outsideCount++
        val prior = source ?: return Change()
        if (shown || (persistence != null && (!anchorArmed ||
                (!progressMode && persistence.minimumAnchorSupportMs > 0.0 && lastInsideLine != prior.line))) || t <= prior.lastCaptureMs || outsideFirst!! <= prior.lastCaptureMs ||
            outsideCount < detector.minFixationSamples || t - outsideFirst!! <
                (if (persistence != null) lossMs else detector.minFixationMs))
            return Change()
        shown = true; landing = null
        if (progressMode) resetCueProgress()
        if (persistence != null) { shownAt = c.nowMs; recoveryFloor = t; clearRelease() }
        val reason = if (s.coordinateStream == ScanpathCoordinateStream.CALIBRATED_RAW)
            "sustained_raw_outside_article" else "sustained_smoothed_outside_article"
        return Change(ReadingAidBookmarkEvidence(prior, reason,
            outsideFirst!!, t, d, outsideCount))
    }
}
