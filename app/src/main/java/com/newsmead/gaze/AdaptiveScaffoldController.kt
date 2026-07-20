package com.newsmead.gaze

/** Manuscript-aligned scaffold levels. NONE represents stable, unsupported reading. */
enum class ScaffoldLevel { NONE, WORD, LINE, FOCUS, REENTRY }

data class ScaffoldState(
    val level: ScaffoldLevel = ScaffoldLevel.NONE,
    val currentLine: Int = -1,
    val wordStart: Int = TextTarget.NO_OFFSET,
    val wordEnd: Int = TextTarget.NO_OFFSET,
    val reentryTargetLine: Int = -1,
)

data class ScaffoldTransition(
    val from: ScaffoldLevel,
    val to: ScaffoldLevel,
    val timestampMs: Long,
    val reason: String,
    val stabilityIndex: Double,
    val confidence: Double,
    val currentLine: Int,
    val reentryTargetLine: Int,
    val recoveryLatencyMs: Long?,
)

data class ScaffoldUpdate(val state: ScaffoldState, val transition: ScaffoldTransition? = null)

/**
 * Maps the smoothed stability index onto one intervention at a time. Escalation
 * and withdrawal use different persistence intervals, preventing rapid visual
 * oscillation. Level 4 additionally requires evidence of loss of position.
 */
class AdaptiveScaffoldController(
    private val wordThreshold: Double = DEFAULT_WORD_THRESHOLD,
    private val lineThreshold: Double = DEFAULT_LINE_THRESHOLD,
    private val focusThreshold: Double = DEFAULT_FOCUS_THRESHOLD,
    private val reentryThreshold: Double = DEFAULT_REENTRY_THRESHOLD,
    private val minimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
    private val escalationPersistenceMs: Long = DEFAULT_ESCALATION_PERSISTENCE_MS,
    private val recoveryPersistenceMs: Long = DEFAULT_RECOVERY_PERSISTENCE_MS,
    private val lossOffTextMs: Long = DEFAULT_LOSS_OFF_TEXT_MS,
    private val minimumLossLineJump: Int = DEFAULT_MINIMUM_LOSS_LINE_JUMP,
    private val reentryEvidenceMs: Long = DEFAULT_REENTRY_EVIDENCE_MS,
    private val forcedLevel: ScaffoldLevel? = null,
) {
    private var currentLevel = ScaffoldLevel.NONE
    private var candidateLevel = ScaffoldLevel.NONE
    private var candidateSinceMs = 0L
    private var offTextSinceMs: Long? = null
    private var lastStableLine = -1
    private var reentryTargetLine = -1
    private var reentryEvidenceUntilMs = 0L
    private var instabilityStartedAtMs: Long? = null

    fun onGazeTarget(target: TextTarget, timestampMs: Long) {
        if (!target.isValid) {
            if (offTextSinceMs == null) offTextSinceMs = timestampMs
            return
        }
        val offTextAt = offTextSinceMs
        if (offTextAt != null && timestampMs - offTextAt >= lossOffTextMs &&
            lastStableLine >= 0 && kotlin.math.abs(target.lineIndex - lastStableLine) >= minimumLossLineJump
        ) {
            reentryTargetLine = lastStableLine
            reentryEvidenceUntilMs = timestampMs + reentryEvidenceMs
        }
        offTextSinceMs = null
    }

    fun update(
        snapshot: WindowedStabilityEstimator.StabilitySnapshot,
        target: TextTarget,
        timestampMs: Long,
    ): ScaffoldUpdate {
        val forced = forcedLevel
        if (forced != null) {
            return changeImmediately(forced, snapshot, target, timestampMs, "forced study level")
        }

        if (!snapshot.baselineReady) {
            return changeImmediately(ScaffoldLevel.NONE, snapshot, target, timestampMs, "baseline collection")
        }
        if (snapshot.confidence < minimumConfidence) {
            return changeImmediately(ScaffoldLevel.NONE, snapshot, target, timestampMs, "low gaze confidence")
        }

        if (target.isValid && snapshot.smoothedIndex < wordThreshold) {
            lastStableLine = target.lineIndex
        }

        val desired = desiredLevel(snapshot.smoothedIndex, timestampMs)
        if (desired == currentLevel) {
            candidateLevel = currentLevel
            candidateSinceMs = timestampMs
            return ScaffoldUpdate(stateFor(target))
        }

        if (candidateLevel != desired) {
            candidateLevel = desired
            candidateSinceMs = timestampMs
            return ScaffoldUpdate(stateFor(target))
        }

        val escalating = desired.ordinal > currentLevel.ordinal
        val requiredMs = if (escalating) escalationPersistenceMs else recoveryPersistenceMs
        if (timestampMs - candidateSinceMs < requiredMs) return ScaffoldUpdate(stateFor(target))

        val next = if (escalating) {
            ScaffoldLevel.entries[currentLevel.ordinal + 1].coerceAtMost(desired)
        } else {
            ScaffoldLevel.entries[currentLevel.ordinal - 1].coerceAtLeast(desired)
        }
        return transitionTo(next, snapshot, target, timestampMs, if (escalating) "persistent instability" else "stable recovery")
    }

    fun reset(): ScaffoldUpdate {
        currentLevel = ScaffoldLevel.NONE
        candidateLevel = ScaffoldLevel.NONE
        candidateSinceMs = 0L
        offTextSinceMs = null
        lastStableLine = -1
        reentryTargetLine = -1
        reentryEvidenceUntilMs = 0L
        instabilityStartedAtMs = null
        return ScaffoldUpdate(ScaffoldState())
    }

    private fun desiredLevel(index: Double, timestampMs: Long): ScaffoldLevel = when {
        index < wordThreshold -> ScaffoldLevel.NONE
        index < lineThreshold -> ScaffoldLevel.WORD
        index < focusThreshold -> ScaffoldLevel.LINE
        index < reentryThreshold -> ScaffoldLevel.FOCUS
        timestampMs <= reentryEvidenceUntilMs && reentryTargetLine >= 0 -> ScaffoldLevel.REENTRY
        else -> ScaffoldLevel.FOCUS
    }

    private fun transitionTo(
        next: ScaffoldLevel,
        snapshot: WindowedStabilityEstimator.StabilitySnapshot,
        target: TextTarget,
        timestampMs: Long,
        reason: String,
    ): ScaffoldUpdate {
        val previous = currentLevel
        currentLevel = next
        candidateLevel = next
        candidateSinceMs = timestampMs
        if (previous == ScaffoldLevel.NONE && next != ScaffoldLevel.NONE) instabilityStartedAtMs = timestampMs
        val recoveryLatency = if (next == ScaffoldLevel.NONE) {
            instabilityStartedAtMs?.let { timestampMs - it }
        } else null
        if (next == ScaffoldLevel.NONE) instabilityStartedAtMs = null
        val state = stateFor(target)
        return ScaffoldUpdate(
            state,
            ScaffoldTransition(
                from = previous,
                to = next,
                timestampMs = timestampMs,
                reason = reason,
                stabilityIndex = snapshot.smoothedIndex,
                confidence = snapshot.confidence,
                currentLine = target.lineIndex,
                reentryTargetLine = state.reentryTargetLine,
                recoveryLatencyMs = recoveryLatency,
            ),
        )
    }

    private fun changeImmediately(
        next: ScaffoldLevel,
        snapshot: WindowedStabilityEstimator.StabilitySnapshot,
        target: TextTarget,
        timestampMs: Long,
        reason: String,
    ): ScaffoldUpdate {
        candidateLevel = next
        candidateSinceMs = timestampMs
        return if (next == currentLevel) ScaffoldUpdate(stateFor(target))
        else transitionTo(next, snapshot, target, timestampMs, reason)
    }

    private fun stateFor(target: TextTarget): ScaffoldState = ScaffoldState(
        level = currentLevel,
        currentLine = target.lineIndex,
        wordStart = if (target.hasWord) target.wordStart else TextTarget.NO_OFFSET,
        wordEnd = if (target.hasWord) target.wordEnd else TextTarget.NO_OFFSET,
        reentryTargetLine = if (currentLevel == ScaffoldLevel.REENTRY) {
            reentryTargetLine.takeIf { it >= 0 } ?: target.lineIndex
        } else {
            -1
        },
    )

    private fun ScaffoldLevel.coerceAtMost(other: ScaffoldLevel): ScaffoldLevel =
        if (ordinal <= other.ordinal) this else other

    private fun ScaffoldLevel.coerceAtLeast(other: ScaffoldLevel): ScaffoldLevel =
        if (ordinal >= other.ordinal) this else other

    companion object {
        const val DEFAULT_WORD_THRESHOLD = 1.0
        const val DEFAULT_LINE_THRESHOLD = 1.5
        const val DEFAULT_FOCUS_THRESHOLD = 2.0
        const val DEFAULT_REENTRY_THRESHOLD = 2.5
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.65
        const val DEFAULT_ESCALATION_PERSISTENCE_MS = 1_500L
        const val DEFAULT_RECOVERY_PERSISTENCE_MS = 3_500L
        const val DEFAULT_LOSS_OFF_TEXT_MS = 800L
        const val DEFAULT_MINIMUM_LOSS_LINE_JUMP = 2
        const val DEFAULT_REENTRY_EVIDENCE_MS = 10_000L
    }
}
