package com.newsmead.gaze.aid

/** Pure policy values. Text offsets are UTF-16; all times are capture/delivery milliseconds. */
enum class ReadingAidType { CURRENT_LINE, NEXT_LINE_START, WORD_MAGNIFIER, FOCUS_REGION }
enum class ReadingAidDecisionStatus { CHOSEN, SUPPRESSED, CLEARED, EXPIRED }

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
    val configId: String
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
    val focusRegion: Boolean = false,
    val adaptiveStrength: Boolean = true,
    val maxStrength: Double = 1.0,
    val manualStrength: Double = 0.5
) {
    init { require(maxStrength.isFinite() && maxStrength in 0.0..1.0 && manualStrength.isFinite() && manualStrength in 0.0..1.0) }
}

/** The caller supplies only the latest eligible raw target for this capture/geometry. */
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
    val validLiveInput: Boolean = true
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
    val focusContextRadiusLines: Int = 2
) {
    init {
        require(configId.isNotBlank() && conditionId.isNotBlank())
        require(wordMinRefixations >= 1 && focusContextRadiusLines >= 1)
        require(listOf(wordMinObservedMs, wordSaturationMs, lineMinObservedMs, lineSaturationMs, cycleWindowMs,
            aidTtlMs, cooldownMs, maxEvidenceAgeMs, maxMagnificationScale).all { it.isFinite() && it > 0 })
        require(wordSaturationMs >= wordMinObservedMs && maxMagnificationScale >= 1.0)
        require(lineSaturationMs >= lineMinObservedMs)
        require(minAdaptiveStrength.isFinite() && minAdaptiveStrength in 0.0..1.0)
    }
    companion object {
        fun experimentalPrototype(conditionId: String = "EXPERIMENTAL_AIDS_V1") =
            ReadingAidPolicyConfig("EXPERIMENTAL_PROFILE_V1", conditionId, true)
        fun manualOnly(conditionId: String = "MANUAL_AIDS_V1") =
            ReadingAidPolicyConfig("MANUAL_ONLY_V1", conditionId, false)
    }
}
