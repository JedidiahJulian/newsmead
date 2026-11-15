package com.newsmead.gaze

import org.junit.Assert.*
import org.junit.Test

class GazeRegionVisualStateTest {
    @Test fun initialTargetSnapsWithoutOriginFlyInAndConverges() {
        val state = GazeRegionVisualState()
        assertFalse(state.visible)

        state.setTarget(320f, 640f)
        assertTrue(state.hasRenderPoint)
        assertEquals(320f, state.renderX, 0f)
        assertEquals(640f, state.renderY, 0f)

        var nowNs = 1_000_000_000L
        var keepAnimating = state.step(nowNs)
        assertTrue(keepAnimating)
        assertTrue(state.visible)
        assertTrue(state.alpha in 0.05f..0.99f)

        repeat(40) {
            nowNs += 16_000_000L
            keepAnimating = state.step(nowNs)
        }
        assertFalse(keepAnimating)
        assertEquals(1f, state.alpha, 0f)
        assertEquals(320f, state.renderX, 0f)
        assertEquals(640f, state.renderY, 0f)
    }

    @Test fun positionGlidesSmoothlyAcrossFramesWithoutMutatingTarget() {
        val state = GazeRegionVisualState()
        var nowNs = 1_000_000_000L
        state.setTarget(100f, 200f)
        repeat(40) {
            nowNs += 16_000_000L
            state.step(nowNs)
        }

        state.setTarget(400f, 500f)
        nowNs += 16_000_000L
        val keepAnimating = state.step(nowNs)
        assertTrue(keepAnimating)
        assertEquals(400f, state.targetX, 0f)
        assertEquals(500f, state.targetY, 0f)
        assertTrue(state.renderX in 110f..390f)
        assertTrue(state.renderY in 210f..490f)

        repeat(60) {
            nowNs += 16_000_000L
            state.step(nowNs)
        }
        assertEquals(400f, state.renderX, 0f)
        assertEquals(500f, state.renderY, 0f)
    }

    @Test fun clearFadesOutAndNextTargetSnapsFresh() {
        val state = GazeRegionVisualState()
        var nowNs = 1_000_000_000L
        state.setTarget(200f, 300f)
        repeat(40) {
            nowNs += 16_000_000L
            state.step(nowNs)
        }
        assertTrue(state.visible)

        state.clear()
        nowNs += 16_000_000L
        assertTrue(state.step(nowNs))
        assertTrue(state.alpha < 1f)

        repeat(60) {
            nowNs += 16_000_000L
            state.step(nowNs)
        }
        assertFalse(state.visible)
        assertFalse(state.hasRenderPoint)
        assertEquals(0f, state.alpha, 0f)

        state.setTarget(800f, 1200f)
        assertEquals(800f, state.renderX, 0f)
        assertEquals(1200f, state.renderY, 0f)
    }

    @Test fun dotAndRegionConstantsMatchSpec() {
        assertEquals(18f, GazeOverlayView.GAZE_DOT_RADIUS_PX, 0f)
        assertEquals(180, GazeOverlayView.GAZE_DOT_ALPHA)
        assertEquals(255, GazeOverlayView.GAZE_DOT_RED)
        assertEquals(64, GazeOverlayView.GAZE_DOT_GREEN)
        assertEquals(0, GazeOverlayView.GAZE_DOT_BLUE)

        assertEquals(26f, GazeOverlayView.GAZE_REGION_RADIUS_DP, 0f)
        assertEquals(1.5f, GazeOverlayView.GAZE_REGION_RING_STROKE_DP, 0f)
        assertEquals(135, GazeOverlayView.GAZE_REGION_RING_ALPHA)
        assertEquals(3f, GazeOverlayView.GAZE_REGION_HALO_WIDTH_DP, 0f)
        assertEquals(50, GazeOverlayView.GAZE_REGION_HALO_ALPHA)
        assertEquals(120, GazeOverlayView.GAZE_REGION_RED)
        assertEquals(144, GazeOverlayView.GAZE_REGION_GREEN)
        assertEquals(156, GazeOverlayView.GAZE_REGION_BLUE)
    }
}
