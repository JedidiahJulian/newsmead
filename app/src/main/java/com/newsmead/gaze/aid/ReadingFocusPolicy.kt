package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathConfig

/** Online broad rereading prefix supplied by the aid-local stabilized-location detector. */
data class ReadingAidRereadEvidence(
    val evidenceId: String, val visits: List<ReadingAidContinuationEvidence>,
    val firstLine: Int, val lastLine: Int, val confirmedDeliveryMs: Double,
    val measuredDistinguishability: Boolean = false
)

/** Presence, supported line visits and display anchors have separate lifetimes. No attention diagnosis. */
internal class ReadingFocusPolicy(
    private val config: ReadingAidPolicyConfig,
    private val detector: ScanpathConfig,
    private val onEpisode: (ReadingAidFocusEpisode) -> Unit
) {
    private data class Loss(val source: ReadingAidContinuationEvidence?, val firstDelivery: Double,
        var lastDelivery: Double, val firstCapture: Double?, var lastCapture: Double?,
        val reasons: MutableSet<String>, var count: Int, var firstRecoveryDelivery: Double? = null)
    private data class Presence(val firstCapture: Double, var lastCapture: Double,
        val firstDelivery: Double, var lastDelivery: Double, var count: Int)
    private data class Demand(val initialStrength: Double, var recoveryMs: Double, val activatedMs: Double,
        val episodes: List<ReadingAidFocusEpisode> = emptyList(),
        val alternations: List<ReadingAidFocusAlternation> = emptyList(),
        val reread: ReadingAidRereadEvidence? = null,
        val rereads: List<ReadingAidRereadEvidence> = emptyList(),
        val qualified: List<ReadingAidFocusQualification> = emptyList())
    private val episodes = mutableListOf<ReadingAidFocusEpisode>()
    private val alternations = mutableListOf<ReadingAidFocusAlternation>()
    private val visits = mutableListOf<ReadingAidContinuationEvidence>()
    private val visitBands = mutableListOf<ReadingSupportedRowBand>()
    private var lastVisit: ReadingAidContinuationEvidence? = null
    private var observedLine: Int? = null
    private var observedSegment: Int? = null
    private var observedLineQualified = false
    private var source: ReadingAidContinuationEvidence? = null
    private var sourceEpoch: Pair<Long, String>? = null
    private var loss: Loss? = null
    private var presence: Presence? = null
    private var armed = false
    private var lastNow = Double.NEGATIVE_INFINITY
    private var lastDelivery = Double.NEGATIVE_INFINITY
    private var lastInputCapture = Double.NEGATIVE_INFINITY
    private var lastCapture: Double? = null
    private var lastSupportedCapture = Double.NEGATIVE_INFINITY
    private var lastSupportedDelivery = Double.NEGATIVE_INFINITY
    private var lastPresenceDelivery = Double.NEGATIVE_INFINITY
    private var interruptionDemand: Demand? = null
    private var alternationDemand: Demand? = null
    private var rereadDemand: Demand? = null
    private var lastReread: ReadingAidRereadEvidence? = null
    private val rereads = mutableListOf<ReadingAidRereadEvidence>()
    private var rereadEpoch: Pair<Long, String>? = null
    private var episodeDemand: Demand? = null
    private var lastCreditedEndCapture = Double.NEGATIVE_INFINITY
    private var supportEpoch: Pair<Long, String>? = null
    private var recoveryTail: ReadingAidContinuationEvidence? = null
    private var recoveryEpoch: Pair<Long, String>? = null
    private var serial = 0L
    private var textSizeDp = 22.0
    private var typography = ReadingFocusTypography.STANDARD
    var witnessesExpired: Boolean = false
        private set

    fun reset() {
        episodes.clear(); alternations.clear(); interruptionDemand = null; alternationDemand = null
        episodeDemand = null; lastCreditedEndCapture = Double.NEGATIVE_INFINITY; supportEpoch = null
        invalidateReread()
        pause()
        lastNow = Double.NEGATIVE_INFINITY; lastDelivery = Double.NEGATIVE_INFINITY
        lastInputCapture = Double.NEGATIVE_INFINITY; witnessesExpired = false
    }

    /** Same-article operational pause: retain completed summaries, discard pending evidence and anchors. */
    fun pause() {
        rereads.clear(); rereadEpoch = null
        source = null; sourceEpoch = null; loss = null; presence = null; armed = false
        recoveryTail = null; recoveryEpoch = null
        lastCapture = null; lastSupportedCapture = Double.NEGATIVE_INFINITY
        lastSupportedDelivery = Double.NEGATIVE_INFINITY; lastPresenceDelivery = Double.NEGATIVE_INFINITY
        breakLineSequence()
    }

    fun breakLineSequence() {
        visits.clear(); visitBands.clear(); lastVisit = null
        observedLine = null; observedSegment = null; observedLineQualified = false
    }
    fun interruptReading() { lastCapture = null }
    /** Row indices in a reread witness cannot survive text reflow. */
    fun invalidateReread() {
        rereadDemand = null; lastReread = null; rereads.clear(); rereadEpoch = null
        recoveryTail = null; recoveryEpoch = null
        discardRowQualifications()
    }
    private fun breakRecoverySupport() {
        if (config.evidenceAlignedV5) { recoveryTail = null; recoveryEpoch = null }
    }

    val hasDemand: Boolean get() = listOfNotNull(interruptionDemand, alternationDemand, rereadDemand, episodeDemand).any {
        it.initialStrength > 0 && it.recoveryMs < config.focusRecoveryHoldMs + config.focusRecoveryFadeMs
    }

    /** Fresh presence can retain a known anchor while the reader searches among lines. Ticks cannot renew it. */
    fun displaySupported(nowMs: Double): Boolean = nowMs.isFinite() &&
        nowMs - maxOf(lastSupportedDelivery, lastPresenceDelivery) in 0.0..config.focusTrackingGraceMs

    fun input(c: ReadingAidContext) {
        selectTypography(c.focusTextSizeDp)
        witnessesExpired = false
        if (!c.nowMs.isFinite() || c.nowMs < 0 || c.nowMs < lastNow) { reset(); return }
        lastNow = c.nowMs
        episodes.removeAll { c.nowMs - it.completedDeliveryMs > config.focusInterruptionWindowMs }
        alternations.removeAll { c.nowMs - it.confirmedDeliveryMs > config.focusInterruptionWindowMs }
        rereads.removeAll { c.nowMs - it.confirmedDeliveryMs > config.focusRereadWindowMs }
        // Once activated, the response owns its witnesses until recovery or its own bounded expiry.
        if (interruptionDemand?.let { c.nowMs - it.activatedMs > config.focusInterruptionWindowMs } == true) {
            interruptionDemand = null; witnessesExpired = true
        }
        if (alternationDemand?.let { c.nowMs - it.activatedMs > config.focusInterruptionWindowMs } == true) {
            alternationDemand = null; witnessesExpired = true
        }
        if (rereadDemand?.let { c.nowMs - it.activatedMs > config.focusInterruptionWindowMs } == true) {
            rereadDemand = null; witnessesExpired = true
        }
        if (episodeDemand?.let { c.nowMs - it.activatedMs > config.focusInterruptionWindowMs } == true) {
            episodeDemand = null; witnessesExpired = true
        }
        val s = c.presentationSample ?: run {
            presence = null; interruptReading(); breakRecoverySupport(); breakLineSequence(); return
        }
        val d = s.deliveryMs; val t = s.captureMs
        val clocks = d?.isFinite() == true && d >= 0 && d <= c.nowMs && d >= lastDelivery &&
            (t == null || (t.isFinite() && t >= 0 && t <= d))
        if (!clocks || s.exclusion == "invalid_clock") { reset(); return }
        if (c.nowMs - d!! > config.maxEvidenceAgeMs) {
            interruptReading(); breakRecoverySupport(); return
        }
        val freshDelivery = d > lastDelivery
        lastDelivery = d
        if (s.geometrySnapshotId != c.geometrySnapshotId) { pause(); return }
        val missing = s.exclusion in setOf("no_face", "invalid_crops", "eye_area", "stale",
            "invalid_prediction", "coordinate_unavailable")
        if (missing) {
            breakRecoverySupport()
            interruptReading(); presence = null; breakLineSequence()
            if (!freshDelivery) return
            val pending = loss
            if (pending == null && armed) {
                if (source != null && d < source!!.evidenceAvailableMs) return
                loss = Loss(source, d, d, t, t, mutableSetOf(s.exclusion!!), 1)
                armed = false
            } else if (pending != null && d > pending.lastDelivery) {
                pending.lastDelivery = d; pending.lastCapture = t
                pending.reasons.add(s.exclusion!!); pending.count++
            }
            return
        }
        if (s.exclusion == "blink" || s.exclusion == "eye_state_unavailable") {
            breakRecoverySupport()
            // Eye closure is not evidence of looking away. It must not extend a
            // preceding brief tracking glitch into a qualifying >=500 ms loss.
            val pending = loss
            if (pending != null && pending.firstRecoveryDelivery == null &&
                pending.lastDelivery - pending.firstDelivery < config.focusLossMinMs) loss = null
            interruptReading(); presence = null; breakLineSequence()
            return
        }
        val selected = s.processingX?.isFinite() == true && s.processingY?.isFinite() == true
        // Repeated timer/context callbacks are neutral; they cannot add samples or break a live visit.
        if (!freshDelivery || t == lastInputCapture) return
        if (t != null && t < lastInputCapture) {
            presence = null; interruptReading(); breakRecoverySupport(); breakLineSequence(); return
        }
        val extractionOnly = config.evidenceAlignedV5 && s.exclusion in setOf(
            "unassigned_line", "unassigned_word", "ambiguous_line", "ambiguous_word")
        val tracked = selected && t != null && t > lastInputCapture &&
            c.nowMs - t in 0.0..config.maxEvidenceAgeMs &&
            (s.exclusion == null || s.exclusion == "outside_visible_text" || extractionOnly)
        val pending = loss
        if (pending != null && pending.firstRecoveryDelivery == null && tracked && freshDelivery &&
            t!! > maxOf(pending.source?.lastCaptureMs ?: Double.NEGATIVE_INFINITY,
                pending.lastCapture ?: Double.NEGATIVE_INFINITY)) {
            if (d - pending.firstDelivery < config.focusLossMinMs) loss = null
            else pending.firstRecoveryDelivery = d
        }
        val g = c.geometry
        val article = g?.visibleRectScreen
        // Deliberately independent of sample.eligible, mapped line/word, dispersion and fixation establishment.
        val inside = tracked && g?.snapshotId == c.geometrySnapshotId && article != null &&
            s.processingX!! in article.left..article.right && s.processingY!! in article.top..article.bottom
        if (config.evidenceAlignedV5 && (!selected || t == null ||
                c.nowMs - t !in 0.0..config.maxEvidenceAgeMs ||
                article == null || g?.snapshotId != c.geometrySnapshotId ||
                s.processingX!! !in article.left..article.right || s.processingY!! !in article.top..article.bottom ||
                recoveryTail?.let { t - it.lastCaptureMs > 250.0 } == true)) breakRecoverySupport()
        if (!inside) { presence = null; interruptReading(); breakLineSequence(); return }
        if (!freshDelivery || t!! <= lastInputCapture) return
        if (lastInputCapture.isFinite() && t - lastInputCapture > detector.maxSampleGapMs) {
            presence = null; interruptReading(); breakLineSequence()
        }
        lastInputCapture = t
        lastPresenceDelivery = d
        val prior = presence
        if (prior == null) presence = Presence(t, t, d, d, 1)
        else { prior.lastCapture = t; prior.lastDelivery = d; prior.count++ }
        val observed = presence!!
        if (observed.lastCapture - observed.firstCapture >= config.focusReturnPresenceMs &&
            observed.count >= config.focusReturnMinSamples) {
            armed = true
            val qualified = loss
            if (qualified?.firstRecoveryDelivery != null) {
                val recovery = ReadingAidArticlePresence("focus_presence:" + (++serial), c.generation,
                    c.geometrySnapshotId, observed.firstCapture, t, observed.firstDelivery, d,
                    observed.count, s.coordinateStream.id)
                val episode = ReadingAidFocusEpisode("focus_episode:" + (++serial), qualified.source, null,
                    qualified.firstDelivery, qualified.lastDelivery, qualified.firstCapture, qualified.lastCapture,
                    qualified.reasons.toList(), qualified.count, generation = c.generation,
                    geometrySnapshotId = c.geometrySnapshotId, firstRecoveryDeliveryMs = qualified.firstRecoveryDelivery!!,
                    presenceRecovery = recovery, completedDeliveryMs = d)
                episodes.add(episode); if (episodes.size > 512) episodes.removeAt(0)
                onEpisode(episode)
                if (config.evidenceAlignedV5) {
                    val fresh = episodes.filter { it.missingFirstDeliveryMs > lastCreditedEndCapture }
                        .takeLast(config.focusInterruptionMinEpisodes)
                    if (fresh.size == config.focusInterruptionMinEpisodes) {
                        val last = fresh.last().presenceRecovery!!
                        credit(ReadingAidFocusQualification("focus_qualified:" + (++serial),
                            "A4_RETURN_PRESENCE_FOCUS_V2", fresh.first().missingFirstDeliveryMs,
                            last.lastCaptureMs, d, episodes = fresh,
                            timeBasis = "unavailable_delivery_boundaries_and_fresh_return_captures"), c)
                    }
                } else if (episodes.size >= config.focusInterruptionMinEpisodes)
                    interruptionDemand = refresh(interruptionDemand,
                        Demand(strength(episodes.size - config.focusInterruptionMinEpisodes + 1),
                            0.0, d, episodes = episodes.toList()))
                loss = null
                lastCapture = t
                return
            }
        }
        val previous = lastCapture
        if (!config.evidenceAlignedV5 && previous != null && t > previous && t - previous <= detector.maxSampleGapMs && loss == null &&
            s.eligible && c.validLiveInput) {
            interruptionDemand?.let { it.recoveryMs += t - previous }
            alternationDemand?.let { it.recoveryMs += t - previous }
        }
        lastCapture = if (s.eligible && c.validLiveInput && loss == null) t else null
    }

    fun support(value: ReadingAidContinuationEvidence, c: ReadingAidContext,
                preferences: ReaderAidPreferences = ReaderAidPreferences(),
                rowBand: ReadingSupportedRowBand? = null): ReadingAidFocusEvidence? {
        selectTypography(c.focusTextSizeDp)
        if (config.evidenceAlignedV5) {
            val nextEpoch = c.generation to c.geometrySnapshotId
            if (supportEpoch != null && supportEpoch != nextEpoch) {
                invalidateReread(); breakLineSequence(); alternations.clear()
            }
            supportEpoch = nextEpoch
        }
        if (config.evidenceAlignedV5 && !validSupport(value, c)) {
            recoveryTail = null; recoveryEpoch = null
            return currentEvidence(c, preferences)
        }
        if (value.lastCaptureMs < lastSupportedCapture ||
            (value.lastCaptureMs == lastSupportedCapture && value.line != source?.line)) return null
        if (value.lastCaptureMs > lastSupportedCapture && value.evidenceAvailableMs > lastSupportedDelivery) {
            if (config.evidenceAlignedV5) recover(value, c)
            lastSupportedCapture = value.lastCaptureMs; lastSupportedDelivery = value.evidenceAvailableMs
            source = value; sourceEpoch = c.generation to c.geometrySnapshotId
            // Established source support can arm a future loss, but never completes a pending return.
            if (loss == null) armed = true
            observeVisit(value, c, rowBand)
        }
        return currentEvidence(c, preferences)
    }

    private fun validSupport(v: ReadingAidContinuationEvidence, c: ReadingAidContext) =
        c.validLiveInput && c.focusInputAllowed && c.nowMs.isFinite() &&
            listOf(v.firstCaptureMs, v.lastCaptureMs, v.evidenceAvailableMs).all { it.isFinite() && it >= 0 } &&
            v.firstCaptureMs <= v.lastCaptureMs && v.lastCaptureMs <= v.evidenceAvailableMs &&
            c.nowMs - v.evidenceAvailableMs in 0.0..config.maxEvidenceAgeMs &&
            c.nowMs - v.lastCaptureMs in 0.0..config.maxEvidenceAgeMs && v.line in 0 until c.lineCount

    /** Only fresh, disjoint supported capture tails pay down a response. */
    private fun recover(v: ReadingAidContinuationEvidence, c: ReadingAidContext) {
        val prior = recoveryTail
        val epoch = c.generation to c.geometrySnapshotId
        if (prior != null && recoveryEpoch == epoch && prior.segment == v.segment &&
            v.lastCaptureMs - prior.lastCaptureMs in 0.0..250.0 && loss == null) {
            val start = maxOf(prior.lastCaptureMs, v.firstCaptureMs)
            val credit = (v.lastCaptureMs - start).coerceAtLeast(0.0)
            listOfNotNull(interruptionDemand, alternationDemand, rereadDemand, episodeDemand).forEach {
                // A newly confirmed response cannot receive time preceding its confirmation.
                it.recoveryMs += minOf(credit, (v.lastCaptureMs - it.activatedMs).coerceAtLeast(0.0))
            }
        }
        recoveryTail = v; recoveryEpoch = epoch
    }

    private fun refresh(previous: Demand?, next: Demand): Demand =
        if (config.evidenceAlignedV5 && previous != null &&
            previous.recoveryMs < config.focusRecoveryHoldMs + config.focusRecoveryFadeMs &&
            next.activatedMs - previous.activatedMs <= config.focusInterruptionWindowMs) next.copy(
            initialStrength = maxOf(previous.initialStrength, next.initialStrength),
            recoveryMs = previous.recoveryMs, activatedMs = previous.activatedMs) else next

    fun reread(e: ReadingAidRereadEvidence, c: ReadingAidContext): ReadingAidFocusEvidence? {
        if (!config.evidenceAlignedV5) return currentEvidence(c)
        selectTypography(c.focusTextSizeDp)
        if (e.visits.size < 5 || e.firstLine !in 0 until c.lineCount ||
            e.lastLine !in e.firstLine until c.lineCount || !e.confirmedDeliveryMs.isFinite() ||
            c.nowMs - e.confirmedDeliveryMs !in 0.0..config.maxEvidenceAgeMs ||
            e.visits.any { !validSupport(it, c.copy(nowMs = it.evidenceAvailableMs)) } ||
            e.visits.last().evidenceAvailableMs > e.confirmedDeliveryMs ||
            e.visits.zipWithNext().any { (a, b) -> a.lastCaptureMs >= b.firstCaptureMs } ||
            e.visits.map { it.evidenceId }.distinct().size != e.visits.size) return currentEvidence(c)
        val nextEpoch = c.generation to c.geometrySnapshotId
        if (rereadEpoch != nextEpoch) {
            rereads.clear(); lastReread = null; rereadEpoch = nextEpoch
        }
        val prior = lastReread
        if (prior?.evidenceId == e.evidenceId || (prior != null &&
                (e.visits.first().firstCaptureMs <= prior.visits.last().lastCaptureMs ||
                    e.confirmedDeliveryMs <= prior.confirmedDeliveryMs))) return currentEvidence(c)
        lastReread = e
        // The entire capture span must fit, not just the three confirmation times.
        val end = e.visits.last().lastCaptureMs
        rereads.removeAll { end - it.visits.first().firstCaptureMs > config.focusRereadWindowMs }
        if (end - e.visits.first().firstCaptureMs <= config.focusRereadWindowMs) rereads.add(e)
        val fresh = rereads.filter { it.visits.first().firstCaptureMs > lastCreditedEndCapture }
            .takeLast(config.focusRereadMinSequences)
        if (fresh.size == config.focusRereadMinSequences)
            credit(ReadingAidFocusQualification("focus_qualified:" + (++serial),
                "A4_BROAD_REREADING_V5", fresh.first().visits.first().firstCaptureMs,
                fresh.last().visits.last().lastCaptureMs, e.confirmedDeliveryMs, rereads = fresh,
                spatialProvenance = if (fresh.all { it.measuredDistinguishability })
                    "measured_distinguishability" else "coarse_neighbor_guard_accuracy_unknown"), c)
        return currentEvidence(c)
    }

    private fun observeVisit(value: ReadingAidContinuationEvidence, c: ReadingAidContext,
                             rowBand: ReadingSupportedRowBand?) {
        if (observedSegment != null && observedSegment != value.segment) breakLineSequence()
        if (observedLine != value.line) {
            // A completed short detour is still part of the observed order. It
            // must break pending ABAB, rather than disappear from the sequence.
            if (observedLine != null && !observedLineQualified) breakLineSequence()
            observedLine = value.line
            observedSegment = value.segment
            observedLineQualified = false
        }
        // Support already passes the detector's minimum. Smaller letters add a
        // longer same-visit requirement before this aid counts a line transition.
        if (value.lastCaptureMs - value.firstCaptureMs < typography.extraMinVisitMs) return
        val previous = lastVisit
        if (previous != null && previous.segment != value.segment) breakLineSequence()
        val prior = lastVisit
        if (prior != null && prior.firstCaptureMs == value.firstCaptureMs && prior.line != value.line) return
        observedLineQualified = true
        if (prior?.line == value.line) return
        lastVisit = value
        visits.add(value)
        visitBands.add(rowBand ?: ReadingSupportedRowBand((value.line - 1).coerceAtLeast(0),
            (value.line + 1).coerceAtMost(c.lineCount - 1), value.line, false,
            "neighboring_rows_uncertainty_unknown"))
        if (visits.size > 4) { visits.removeAt(0); visitBands.removeAt(0) }
        if (visits.size != 4) return
        val v = visits.toList()
        if (v[0].line != v[2].line || v[1].line != v[3].line || v[0].line == v[1].line ||
            value.lastCaptureMs - v[0].firstCaptureMs > config.focusAlternationWindowMs) return
        if (config.evidenceAlignedV5 && listOf(0, 2).any { a -> listOf(1, 3).any { b ->
                val left = visitBands[a]; val right = visitBands[b]
                if (left.measured && right.measured) !left.measurablyDistinctFrom(right) else left.overlaps(right)
            } }) return
        val alternation = ReadingAidFocusAlternation("focus_alternation:" + (++serial), v,
            c.geometrySnapshotId, c.generation, value.evidenceAvailableMs,
            if (config.evidenceAlignedV5) visitBands.toList() else emptyList())
        alternations.add(alternation); if (alternations.size > 512) alternations.removeAt(0)
        if (config.evidenceAlignedV5) {
            val fresh = alternations.filter { it.visits.first().firstCaptureMs > lastCreditedEndCapture }
                .takeLast(typography.requiredAlternations)
            if (fresh.size == typography.requiredAlternations)
                credit(ReadingAidFocusQualification("focus_qualified:" + (++serial),
                    "A4_FONT_SCALED_LINE_VISITS_V3", fresh.first().visits.first().firstCaptureMs,
                    value.lastCaptureMs, value.evidenceAvailableMs, alternations = fresh,
                    spatialProvenance = if (fresh.all { a -> a.anchorBands.all { it.measured } })
                        "measured_nonoverlapping_bands" else "coarse_neighbor_guard_accuracy_unknown"), c)
        } else if (alternations.size >= typography.requiredAlternations)
            alternationDemand = refresh(alternationDemand,
                Demand(strength(alternations.size - typography.requiredAlternations + 1),
                    0.0, value.evidenceAvailableMs, alternations = alternations.toList()))
        // Each strength-increasing sequence needs four new, nonoverlapping visits.
        visits.clear(); visitBands.clear()
    }

    private fun strength(count: Int) = (count * ReadingAidPolicyConfig.FOCUS_STRENGTH_STEP).coerceIn(0.0, 1.0)

    private fun episodeAlive(value: Demand, now: Double) =
        value.recoveryMs < config.focusRecoveryHoldMs + config.focusRecoveryFadeMs &&
            now - value.activatedMs in 0.0..config.focusInterruptionWindowMs

    /** One ledger across routes: an old suffix or second label cannot raise the dose. */
    private fun credit(value: ReadingAidFocusQualification, c: ReadingAidContext) {
        if (!listOf(value.firstObservedMs, value.lastCaptureMs, value.confirmedDeliveryMs)
                .all { it.isFinite() && it >= 0.0 } ||
            value.firstObservedMs > value.lastCaptureMs || value.lastCaptureMs > value.confirmedDeliveryMs ||
            c.nowMs - value.confirmedDeliveryMs !in 0.0..config.maxEvidenceAgeMs ||
            value.firstObservedMs <= lastCreditedEndCapture) return
        val previous = episodeDemand?.takeIf { episodeAlive(it, c.nowMs) }
        val qualified = (previous?.qualified.orEmpty() + value).takeLast(2)
        episodeDemand = qualifiedDemand(qualified,
            if (previous == null) 0.5 else 1.0,
            previous?.recoveryMs ?: 0.0, previous?.activatedMs ?: value.confirmedDeliveryMs)
        lastCreditedEndCapture = value.lastCaptureMs
    }

    private fun qualifiedDemand(values: List<ReadingAidFocusQualification>, strength: Double,
                                recoveryMs: Double, activatedMs: Double): Demand =
        Demand(strength, recoveryMs, activatedMs,
            episodes = values.flatMap { it.episodes },
            alternations = values.flatMap { it.alternations },
            reread = values.last().rereads.lastOrNull(),
            rereads = values.flatMap { it.rereads }, qualified = values)

    /** Font/layout changes invalidate row witnesses, while qualified absence/recovery is independent. */
    private fun discardRowQualifications() {
        val previous = episodeDemand ?: return
        val retained = previous.qualified.filter { it.routeId == "A4_RETURN_PRESENCE_FOCUS_V2" }
        episodeDemand = if (retained.isEmpty()) null else qualifiedDemand(retained,
            strength(retained.size), previous.recoveryMs, previous.activatedMs)
    }

    private fun selectTypography(sizeDp: Double) {
        val normalized = sizeDp.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        if (kotlin.math.abs(normalized - textSizeDp) <= 0.001) return
        textSizeDp = normalized
        typography = ReadingFocusTypography.rule(normalized)
        // Line numbers and qualifying spans change after resizing. Confirmed
        // look-away episodes and their response are independent of font size.
        breakLineSequence()
        alternations.clear()
        alternationDemand = null
        if (config.evidenceAlignedV5) {
            invalidateReread()
        }
    }

    fun currentEvidence(c: ReadingAidContext,
                        preferences: ReaderAidPreferences = ReaderAidPreferences()): ReadingAidFocusEvidence? {
        selectTypography(c.focusTextSizeDp)
        val value = source ?: return null
        if (sourceEpoch != (c.generation to c.geometrySnapshotId) ||
            c.nowMs - value.evidenceAvailableMs !in 0.0..config.focusInterruptionWindowMs) return null
        if (config.evidenceAlignedV5) {
            val demand = episodeDemand?.takeIf { episodeAlive(it, c.nowMs) } ?: return null
            val effective = minOf(preferences.maxStrength,
                if (preferences.adaptiveStrength) demand.initialStrength else preferences.manualStrength) *
                focusRecoveryFactor(demand.recoveryMs, config.focusRecoveryHoldMs, config.focusRecoveryFadeMs)
            if (effective <= 0.0) return null
            return ReadingAidFocusEvidence(demand.episodes, initialStrength = demand.initialStrength,
                observedRecoveryMs = demand.recoveryMs, recoveryFadeMs = config.focusRecoveryFadeMs,
                windowMs = config.focusInterruptionWindowMs, support = value,
                ruleId = demand.qualified.last().routeId,
                recoveryHoldMs = config.focusRecoveryHoldMs, alternations = demand.alternations,
                textSizeDp = textSizeDp, requiredAlternations = typography.requiredAlternations,
                minimumLineVisitMs = maxOf(150.0, typography.extraMinVisitMs), reread = demand.reread,
                rereads = demand.rereads, requiredRereadSequences = config.focusRereadMinSequences,
                rereadWindowMs = config.focusRereadWindowMs, qualifiedEpisodes = demand.qualified)
        }
        fun effective(demand: Demand?) = demand?.let {
            minOf(preferences.maxStrength, if (preferences.adaptiveStrength) it.initialStrength else preferences.manualStrength) *
                focusRecoveryFactor(it.recoveryMs, config.focusRecoveryHoldMs, config.focusRecoveryFadeMs)
        } ?: 0.0
        val a = effective(interruptionDemand); val b = effective(alternationDemand)
        val r = effective(rereadDemand)
        if (maxOf(a, b, r) <= 0) return null
        val interruption = a > b || (a == b &&
            (interruptionDemand?.recoveryMs ?: Double.POSITIVE_INFINITY) <=
            (alternationDemand?.recoveryMs ?: Double.POSITIVE_INFINITY))
        val rereading = r > maxOf(a, b)
        val demand = (if (rereading) rereadDemand else if (interruption) interruptionDemand else alternationDemand)!!
        return ReadingAidFocusEvidence(demand.episodes, initialStrength = demand.initialStrength,
            observedRecoveryMs = demand.recoveryMs, recoveryFadeMs = config.focusRecoveryFadeMs,
            windowMs = config.focusInterruptionWindowMs, support = value,
            ruleId = if (rereading) "A4_BROAD_REREADING_V5" else if (interruption) "A4_RETURN_PRESENCE_FOCUS_V2" else "A4_FONT_SCALED_LINE_VISITS_V3",
            recoveryHoldMs = config.focusRecoveryHoldMs, alternations = demand.alternations,
            textSizeDp = textSizeDp, requiredAlternations = typography.requiredAlternations,
            minimumLineVisitMs = maxOf(detector.minFixationMs, typography.extraMinVisitMs), reread = demand.reread,
            rereads = demand.rereads, requiredRereadSequences = config.focusRereadMinSequences,
            rereadWindowMs = config.focusRereadWindowMs)
    }

    fun diagnostics(now: Double): Map<String, Any> = mapOf(
        "reason" to when {
            hasDemand && (source == null || now - source!!.evidenceAvailableMs !in 0.0..config.focusInterruptionWindowMs) -> "waiting_for_supported_anchor"
            hasDemand && !displaySupported(now) -> "waiting_for_fresh_article_presence"
            hasDemand -> "response_active"
            loss?.firstRecoveryDelivery != null -> "confirming_article_return"
            loss != null -> "unavailable_episode_pending"
            else -> "collecting_recent_episodes_or_visits"
        },
        "recent_episode_count" to episodes.size, "recent_alternation_count" to alternations.size,
        "qualified_focus_episode_count" to (episodeDemand?.takeIf { episodeAlive(it, now) }?.qualified?.size ?: 0),
        "last_credited_capture_ms" to lastCreditedEndCapture.takeIf { it.isFinite() }.let { it ?: "none" },
        "recent_reread_sequence_count" to rereads.count { now - it.confirmedDeliveryMs in 0.0..config.focusRereadWindowMs },
        "required_reread_sequences" to config.focusRereadMinSequences,
        "reread_window_ms" to config.focusRereadWindowMs,
        "text_size_dp" to textSizeDp, "font_rule" to typography.id,
        "required_alternations" to typography.requiredAlternations,
        "minimum_line_visit_ms" to maxOf(detector.minFixationMs, typography.extraMinVisitMs),
        "return_sample_count" to (presence?.count?.coerceAtMost(config.focusReturnMinSamples) ?: 0),
        "has_demand" to hasDemand,
        "has_anchor" to (source != null && now - source!!.evidenceAvailableMs in 0.0..config.focusInterruptionWindowMs))
}
