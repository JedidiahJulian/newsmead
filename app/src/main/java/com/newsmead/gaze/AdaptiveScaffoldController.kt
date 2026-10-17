package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.MahalanobisScore
import com.newsmead.gaze.mahalanobis.ScoreStatus
import com.newsmead.gaze.mahalanobis.TemporalResult

enum class ScaffoldLevel { NONE, WORD, LINE, FOCUS, REENTRY }
data class ScaffoldState(val level: ScaffoldLevel = ScaffoldLevel.NONE, val currentLine: Int = -1,
    val wordStart: Int = TextTarget.NO_OFFSET, val wordEnd: Int = TextTarget.NO_OFFSET,
    val reentryTargetLine: Int = -1)
data class ScaffoldTransition(val from: ScaffoldLevel, val to: ScaffoldLevel, val timestampMs: Long,
    val reason: String, val md2: Double?, val confidence: Double, val currentLine: Int,
    val reentryTargetLine: Int)
data class ScaffoldUpdate(val state: ScaffoldState, val transition: ScaffoldTransition? = null)

/** Renderer targets plus independent displaced-return evidence. Persistence belongs to
 * the temporal detector; MD2 does not select unvalidated severity tiers. */
class AdaptiveScaffoldController(
    private val supportLevel: ScaffoldLevel = ScaffoldLevel.LINE,
    private val lossOffTextMs: Long = 800,
    private val minimumLossLineJump: Int = 2,
    private val reentryEvidenceMs: Long = 10_000,
) {
    private var level = ScaffoldLevel.NONE
    private var offTextSinceMs: Long? = null
    private var lastStableLine = -1
    private var reentryTarget = -1
    private var evidenceUntil = -1L

    fun onGazeTarget(target: TextTarget, timestampMs: Long) {
        if (!target.isValid) {
            if (offTextSinceMs == null) offTextSinceMs = timestampMs
        } else {
            val away = offTextSinceMs
            if (away != null && timestampMs - away >= lossOffTextMs && lastStableLine >= 0 &&
                kotlin.math.abs(target.lineIndex - lastStableLine) >= minimumLossLineJump) {
                reentryTarget = lastStableLine
                evidenceUntil = timestampMs + reentryEvidenceMs
            }
            offTextSinceMs = null
        }
    }

    fun update(score: MahalanobisScore, temporal: TemporalResult, target: TextTarget,
        enabled: Boolean = true): ScaffoldUpdate {
        val usable = score.status == ScoreStatus.AVAILABLE && target.isValid
        if (usable && !temporal.active && temporal.high == false) lastStableLine = target.lineIndex
        val desired = when {
            !enabled || !usable || !temporal.active -> ScaffoldLevel.NONE
            reentryTarget >= 0 && score.vector.timestampMs <= evidenceUntil -> ScaffoldLevel.REENTRY
            else -> supportLevel
        }
        val previous = level
        level = desired
        val state = ScaffoldState(level, target.lineIndex, target.wordStart, target.wordEnd,
            if (level == ScaffoldLevel.REENTRY) reentryTarget else -1)
        return ScaffoldUpdate(state, if (previous == level) null else ScaffoldTransition(previous, level,
            score.vector.timestampMs, if (level == ScaffoldLevel.NONE) "recovered_or_unavailable" else
                "confirmed_baseline_deviation", score.md2, score.vector.validFraction, target.lineIndex,
            state.reentryTargetLine))
    }

    fun reset(): ScaffoldUpdate {
        level = ScaffoldLevel.NONE; offTextSinceMs = null; lastStableLine = -1
        reentryTarget = -1; evidenceUntil = -1
        return ScaffoldUpdate(ScaffoldState())
    }
}
