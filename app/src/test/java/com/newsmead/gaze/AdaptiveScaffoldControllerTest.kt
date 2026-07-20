package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveScaffoldControllerTest {

    @Test
    fun escalatesOneLevelAfterPersistentInstability() {
        val controller = AdaptiveScaffoldController(
            escalationPersistenceMs = 100,
            recoveryPersistenceMs = 200,
        )
        val target = TextTarget(8, 30, 20, 25)

        assertEquals(ScaffoldLevel.NONE, controller.update(snapshot(1.2), target, 0).state.level)
        val word = controller.update(snapshot(1.2), target, 100)

        assertEquals(ScaffoldLevel.WORD, word.state.level)
        assertEquals(ScaffoldLevel.WORD, word.transition?.to)
    }

    @Test
    fun withdrawsOnlyAfterLongerRecoveryPersistence() {
        val controller = AdaptiveScaffoldController(
            escalationPersistenceMs = 100,
            recoveryPersistenceMs = 300,
        )
        val target = TextTarget(8, 30, 20, 25)
        controller.update(snapshot(1.2), target, 0)
        controller.update(snapshot(1.2), target, 100)

        assertEquals(ScaffoldLevel.WORD, controller.update(snapshot(0.0), target, 200).state.level)
        assertEquals(ScaffoldLevel.WORD, controller.update(snapshot(0.0), target, 499).state.level)
        val recovered = controller.update(snapshot(0.0), target, 500)

        assertEquals(ScaffoldLevel.NONE, recovered.state.level)
        assertEquals(400L, recovered.transition?.recoveryLatencyMs)
    }

    @Test
    fun lowConfidenceClearsSupportImmediately() {
        val controller = AdaptiveScaffoldController(escalationPersistenceMs = 0)
        val target = TextTarget(8, 30, 20, 25)
        controller.update(snapshot(1.2), target, 0)
        controller.update(snapshot(1.2), target, 0)

        val update = controller.update(snapshot(index = 3.0, confidence = 0.2), target, 1)

        assertEquals(ScaffoldLevel.NONE, update.state.level)
        assertEquals("low gaze confidence", update.transition?.reason)
    }

    @Test
    fun veryHighIndexWithoutLossEvidenceStopsAtFocus() {
        val controller = AdaptiveScaffoldController(escalationPersistenceMs = 0)
        val target = TextTarget(8, 30)
        var update = controller.update(snapshot(3.0), target, 0)
        repeat(8) { update = controller.update(snapshot(3.0), target, it.toLong()) }

        assertEquals(ScaffoldLevel.FOCUS, update.state.level)
        assertNull(update.transition)
    }

    @Test
    fun lossOfPositionEvidenceAllowsReentryLevel() {
        val controller = AdaptiveScaffoldController(
            escalationPersistenceMs = 0,
            lossOffTextMs = 500,
            reentryEvidenceMs = 10_000,
        )
        controller.update(snapshot(0.0), TextTarget(10, 30), 0)
        controller.onGazeTarget(TextTarget.INVALID, 100)
        val displaced = TextTarget(5, 30)
        controller.onGazeTarget(displaced, 700)

        var update = controller.update(snapshot(3.0), displaced, 700)
        repeat(8) { update = controller.update(snapshot(3.0), displaced, 701 + it.toLong()) }

        assertEquals(ScaffoldLevel.REENTRY, update.state.level)
        assertEquals(10, update.state.reentryTargetLine)
    }

    private fun snapshot(
        index: Double,
        confidence: Double = 1.0,
    ) = WindowedStabilityEstimator.StabilitySnapshot(
        rawIndex = index,
        smoothedIndex = index,
        confidence = confidence,
        baselineReady = true,
        metrics = WindowedStabilityEstimator.ReadingMetrics.EMPTY,
        timestampMs = 0,
    )
}
