package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathSample

/** Pure policy values. Text offsets are UTF-16; all times are capture/delivery milliseconds. */
enum class ReadingAidType { CURRENT_LINE, CURRENT_SENTENCE, NEXT_LINE_START, READING_BOOKMARK, WORD_MAGNIFIER, FOCUS_REGION, WORD_DEFINITION }
enum class ReadingGuideStyle { LINE, SENTENCE }
enum class ReadingFocusStyle { FADE_TEXT, DARK_SURROUNDINGS }
enum class ReadingGuideUnit { SENTENCE, PARAGRAPH }
/** Original text offsets, separate from word assignment. */
data class ReadingGuideRange(val startUtf16: Int, val endUtf16: Int, val unit: ReadingGuideUnit)

enum class ReadingAidDecisionStatus { CHOSEN, SUPPRESSED, CLEARED, EXPIRED }
enum class ReadingAidInputSource { LIVE_MGAZENET, SYNTHETIC_REPLAY }

data class ReadingAidAnchor(
    val generation: Long,
    val geometrySnapshotId: String,
    val line: Int,
    val wordStartUtf16: Int? = null,
    val wordEndUtf16: Int? = null
)

data class ReadingAidAction(
    val id: String,
    val aidType: ReadingAidType,
    val aidFormId: String,
    val conditionId: String,
    val anchor: ReadingAidAnchor,
    val strength: Double,
    val magnificationScale: Double?,
    val contextRadiusLines: Int?,
    val onsetMs: Double,
    val expiresMs: Double,
    val eventIds: List<String>,
    val evidenceIds: List<String>,
    val firstCaptureMs: Double,
    val lastCaptureMs: Double,
    val evidenceAvailableMs: Double,
    val ruleId: String,
    val sourceIds: List<String>,
    val evidenceRelationship: String,
    val experimental: Boolean,
    val configId: String,
    val continuationEvidence: List<ReadingAidContinuationEvidence> = emptyList(),
    val bookmarkEvidence: ReadingAidBookmarkEvidence? = null,
    val guideRange: ReadingGuideRange? = null,
    val focusEvidence: ReadingAidFocusEvidence? = null,
    val focusStyle: ReadingFocusStyle? = null
)

/** A supported reading location and the later observable departure; no attention diagnosis. */
data class ReadingAidBookmarkEvidence(
    val source: ReadingAidContinuationEvidence,
    val departureReason: String,
    val departureFirstCaptureMs: Double?,
    val departureLastCaptureMs: Double?,
    val departureEvidenceAvailableMs: Double,
    val departureSampleCount: Int,
    val departureSupport: ReadingAidContinuationEvidence? = null,
    val departureTimeBasis: String = "capture",
    val departureFirstDeliveryMs: Double? = null,
    val departureLastDeliveryMs: Double? = null
)

/** Separate source/landing support bounds; candidate IDs do not imply finalized fixations. */
data class ReadingAidContinuationEvidence(
    val evidenceId: String, val segment: Int, val line: Int,
    val firstCaptureMs: Double, val lastCaptureMs: Double, val evidenceAvailableMs: Double
)

data class ReadingAidDecision(
    val status: ReadingAidDecisionStatus,
    val action: ReadingAidAction?,
    val reason: String,
    val nowMs: Double,
    val eventIds: List<String> = emptyList(),
    val ruleId: String? = null,
    val sourceIds: List<String> = emptyList()
)

data class ReaderAidPreferences(
    val enabled: Boolean = true,
    val lineGuide: Boolean = true,
    val lineStartCue: Boolean = true,
    val wordMagnifier: Boolean = true,
    val focusRegion: Boolean = true,
    val adaptiveStrength: Boolean = true,
    val maxStrength: Double = 1.0,
    val manualStrength: Double = 0.5,
    val wordDefinition: Boolean = true,
    val guideStyle: ReadingGuideStyle = ReadingGuideStyle.LINE,
    val focusStyle: ReadingFocusStyle = ReadingFocusStyle.FADE_TEXT,
    val autoScroll: Boolean = false
) {
    init { require(maxStrength.isFinite() && maxStrength in 0.0..1.0 && manualStrength.isFinite() && manualStrength in 0.0..1.0) }
}

/** The caller supplies the latest eligible target from its declared stream for this capture/geometry. */
data class ReadingAidContext(
    val generation: Long,
    val geometrySnapshotId: String,
    val lineCount: Int,
    val targetLine: Int?,
    val targetWordStartUtf16: Int?,
    val targetWordEndUtf16: Int?,
    val nowMs: Double,
    val captureMs: Double?,
    val evidenceAvailableMs: Double?,
    val validLiveInput: Boolean = true,
    val geometry: ScanpathGeometry? = null,
    val targetX: Double? = null,
    val rawSample: ScanpathSample? = null,
    val bookmarkInputAllowed: Boolean = true,
    val guideRange: ReadingGuideRange? = null,
    val focusInputAllowed: Boolean = true,
    /** Rendered font pixels divided by display density; includes Android font scaling. */
    val focusTextSizeDp: Double = 22.0
)

/** Engineering prototype settings, not paper thresholds or calibrated difficulty cutoffs. */
data class ReadingAidPolicyConfig(
    val configId: String,
    val conditionId: String,
    val automaticRulesEnabled: Boolean,
    val wordMinRefixations: Int = 2,
    val wordMinObservedMs: Double = 750.0,
    val wordSaturationMs: Double = 1500.0,
    val lineMinObservedMs: Double = 1000.0,
    val lineSaturationMs: Double = 2000.0,
    val cycleWindowMs: Double = 2000.0,
    val aidTtlMs: Double = 1500.0,
    val cooldownMs: Double = 2000.0,
    val maxEvidenceAgeMs: Double = 250.0,
    val minAdaptiveStrength: Double = 0.25,
    val maxMagnificationScale: Double = 1.5,
    val focusContextRadiusLines: Int = 2,
    val wordAidType: ReadingAidType = ReadingAidType.WORD_MAGNIFIER,
    val definitionMinObservedMs: Double = 1500.0,
    val continuationStartFraction: Double = 0.25,
    val continuationEndFraction: Double = 0.75,
    val continuationWindowMs: Double = 2000.0,
    val lineStartAidType: ReadingAidType = ReadingAidType.NEXT_LINE_START,
    val bookmarkTrackingLossMs: Double = 500.0,
    val focusInterruptionMinEpisodes: Int = 3,
    val focusInterruptionWindowMs: Double = 30000.0,
    val focusLossMinMs: Double = 500.0,
    val focusRecoveryFadeMs: Double = 7000.0,
    val focusRecoveryHoldMs: Double = 3000.0,
    val focusTrackingGraceMs: Double = 1000.0,
    val focusReturnPresenceMs: Double = 100.0,
    val focusReturnMinSamples: Int = 3,
    val focusAlternationWindowMs: Double = 8000.0
) {
    init {
        require(configId.isNotBlank() && conditionId.isNotBlank())
        require(wordAidType in listOf(ReadingAidType.WORD_MAGNIFIER, ReadingAidType.WORD_DEFINITION))
        require(lineStartAidType in listOf(ReadingAidType.NEXT_LINE_START, ReadingAidType.READING_BOOKMARK))
        require(definitionMinObservedMs.isFinite() && definitionMinObservedMs > 0)
        require(continuationStartFraction.isFinite() && continuationEndFraction.isFinite() &&
            continuationStartFraction >= 0 && continuationStartFraction < continuationEndFraction &&
            continuationEndFraction <= 1)
        require(continuationWindowMs.isFinite() && continuationWindowMs > 0)
        require(focusInterruptionMinEpisodes >= 1)
        require(focusReturnPresenceMs.isFinite() && focusReturnPresenceMs > 0 && focusReturnMinSamples >= 2)
        require(focusAlternationWindowMs.isFinite() && focusAlternationWindowMs > 0)
        require(listOf(focusInterruptionWindowMs, focusLossMinMs, focusRecoveryFadeMs).all { it.isFinite() && it > 0 })
        require(listOf(focusRecoveryHoldMs, focusTrackingGraceMs).all { it.isFinite() && it >= 0 })
        require(bookmarkTrackingLossMs.isFinite() && bookmarkTrackingLossMs > 0)
        require(wordMinRefixations >= 1 && focusContextRadiusLines >= 1)
        require(listOf(wordMinObservedMs, wordSaturationMs, lineMinObservedMs, lineSaturationMs, cycleWindowMs,
            aidTtlMs, cooldownMs, maxEvidenceAgeMs, maxMagnificationScale).all { it.isFinite() && it > 0 })
        require(wordSaturationMs >= wordMinObservedMs && maxMagnificationScale >= 1.0)
        require(lineSaturationMs >= lineMinObservedMs)
        require(minAdaptiveStrength.isFinite() && minAdaptiveStrength in 0.0..1.0)
    }
    companion object {
        // Two initial focus strengths for the current short-article prototype.
        const val FOCUS_STRENGTH_STEP = 0.5
        fun definitionPrototype() = ReadingAidPolicyConfig("DEFINITION_PROFILE_V4", "EXPERIMENTAL_DEFINITION_V4",
            true, wordAidType = ReadingAidType.WORD_DEFINITION, lineStartAidType = ReadingAidType.READING_BOOKMARK,
            focusContextRadiusLines = 2)
        fun experimentalPrototype(conditionId: String = "EXPERIMENTAL_AIDS_V1") =
            ReadingAidPolicyConfig("EXPERIMENTAL_PROFILE_V2", conditionId, true)
        fun manualOnly(conditionId: String = "MANUAL_AIDS_V1") =
            ReadingAidPolicyConfig("MANUAL_ONLY_V1", conditionId, false)
    }
}

/** One completed observable unavailable/recovery episode, never a physiological diagnosis. */
data class ReadingAidFocusEpisode(
    val episodeId: String,
    val source: ReadingAidContinuationEvidence?,
    val recovery: ReadingAidContinuationEvidence?,
    val missingFirstDeliveryMs: Double,
    val missingLastDeliveryMs: Double,
    val missingFirstCaptureMs: Double?,
    val missingLastCaptureMs: Double?,
    val missingReasons: List<String>,
    val missingSampleCount: Int,
    val timeBasis: String = "delivery_elapsed_time",
    val generation: Long,
    val geometrySnapshotId: String,
    val firstRecoveryDeliveryMs: Double = recovery?.evidenceAvailableMs ?: Double.NaN,
    val presenceRecovery: ReadingAidArticlePresence? = null,
    val completedDeliveryMs: Double = presenceRecovery?.confirmedDeliveryMs ?: recovery!!.evidenceAvailableMs
)

/** Presence anywhere in the visible article, with no fixation, word or line assignment. */
data class ReadingAidArticlePresence(
    val evidenceId: String, val generation: Long, val geometrySnapshotId: String,
    val firstCaptureMs: Double, val lastCaptureMs: Double,
    val firstDeliveryMs: Double, val confirmedDeliveryMs: Double, val sampleCount: Int,
    val coordinateStream: String
)

/** Aid-specific supported visits; this is not a finalized P11 scanpath event. */
data class ReadingAidFocusAlternation(
    val evidenceId: String, val visits: List<ReadingAidContinuationEvidence>,
    val geometrySnapshotId: String, val generation: Long, val confirmedDeliveryMs: Double
)

data class ReadingAidFocusEvidence(
    val episodes: List<ReadingAidFocusEpisode> = emptyList(),
    val cycleEventIds: List<String> = emptyList(),
    val cycleFixationIndices: List<List<Int>> = emptyList(),
    val initialStrength: Double,
    val observedRecoveryMs: Double,
    val recoveryFadeMs: Double,
    val windowMs: Double,
    val support: ReadingAidContinuationEvidence,
    val ruleId: String,
    val recoveryHoldMs: Double = 0.0,
    val alternations: List<ReadingAidFocusAlternation> = emptyList(),
    val textSizeDp: Double = 22.0,
    val requiredAlternations: Int = 1,
    val minimumLineVisitMs: Double = 0.0
) {
    val episodeCount: Int get() = episodes.size
    val cycleCount: Int get() = cycleEventIds.size + alternations.size
    val interruptionWindowMs: Double get() = windowMs
    val recoveryFactor: Double get() = focusRecoveryFactor(observedRecoveryMs, recoveryHoldMs, recoveryFadeMs)
}

/** Reading-time modulation only; retained drawing during missing input contributes no time. */
internal fun focusRecoveryFactor(observedMs: Double, holdMs: Double, fadeMs: Double): Double =
    (1.0 - (observedMs - holdMs).coerceAtLeast(0.0) / fadeMs).coerceIn(0.0, 1.0)
