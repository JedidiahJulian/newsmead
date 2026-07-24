package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FixationWindowFilterTest {

    private val filter = FixationWindowFilter()

    /** Tight cluster at (x, y) with deterministic alternating jitter. */
    private fun steady(
        n: Int,
        startMs: Long,
        x: Float,
        y: Float,
        stepMs: Long = 33,
        jitter: Float = 0.001f,
    ): List<FixationWindowFilter.Sample> = (0 until n).map { i ->
        val sign = if (i % 2 == 0) 1f else -1f
        FixationWindowFilter.Sample(startMs + i * stepMs, x + sign * jitter, y + sign * jitter)
    }

    @Test
    fun acceptsSteadyFixation() {
        val result = filter.filter(steady(30, 0, 0.5f, 0.3f))
        assertEquals(FixationWindowFilter.Status.ACCEPTED, result.status)
        assertEquals(0.5f, result.medianX, 0.005f)
        assertEquals(0.3f, result.medianY, 0.005f)
        assertTrue(result.retainedCount >= 10)
        // Lead-in (first 120ms = 4 samples at 33ms) must have been discarded.
        assertTrue(result.retainedCount <= 26)
    }

    @Test
    fun rejectsSaccadeSpikeViaMad() {
        val samples = steady(30, 0, 0.5f, 0.3f).toMutableList()
        samples[15] = FixationWindowFilter.Sample(15 * 33L, 0.9f, 0.8f)
        val result = filter.filter(samples)
        assertEquals(FixationWindowFilter.Status.ACCEPTED, result.status)
        assertEquals(0.5f, result.medianX, 0.005f)
        assertEquals(0.3f, result.medianY, 0.005f)
        // 26 samples survive the lead-in trim; the spike is MAD-rejected.
        assertEquals(25, result.retainedCount)
    }

    @Test
    fun tooFewSamples() {
        val result = filter.filter(steady(5, 0, 0.5f, 0.3f))
        assertEquals(FixationWindowFilter.Status.TOO_FEW_SAMPLES, result.status)
    }

    @Test
    fun emptyInput() {
        val result = filter.filter(emptyList())
        assertEquals(FixationWindowFilter.Status.TOO_FEW_SAMPLES, result.status)
        assertEquals(0, result.rawCount)
    }

    @Test
    fun lowConfidenceOnContinuousDrift() {
        // x ramps 0.2 -> 0.8 over ~1s: no 300ms sub-window is stable either.
        val samples = (0 until 30).map { i ->
            FixationWindowFilter.Sample(i * 33L, 0.2f + 0.02f * i, 0.3f)
        }
        val result = filter.filter(samples)
        assertEquals(FixationWindowFilter.Status.LOW_CONFIDENCE, result.status)
    }

    @Test
    fun picksStablePlateauViaSubWindow() {
        // Two equal-size plateaus far apart: MAD rejects neither (deviations are
        // symmetric), the full window fails dispersion, and only a sub-window
        // inside one plateau qualifies.
        val a = steady(15, 0, 0.45f, 0.30f)
        val b = steady(15, 500, 0.55f, 0.30f)
        val result = filter.filter(a + b)
        assertEquals(FixationWindowFilter.Status.ACCEPTED, result.status)
        assertEquals(0.30f, result.medianY, 0.005f)
        val nearA = abs(result.medianX - 0.45f) < 0.01f
        val nearB = abs(result.medianX - 0.55f) < 0.01f
        assertTrue("median ${result.medianX} sits between plateaus", nearA || nearB)
        assertTrue(result.dispersionX <= 0.02f)
    }
}
