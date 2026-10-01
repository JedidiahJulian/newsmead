package com.newsmead.gaze.scanpath

import org.junit.Assert.*
import org.junit.Test

class ScanpathEngineTest {
    @Test fun provisionalEvidenceIsIndependentOfFinalizedFixationsAndCaptureOrderSurvivesRotation() {
        val e = ScanpathEngine(words)
        e.updateGeometry(geometry(), 0.0)
        assertTrue(e.onSample(ScanpathSample(10.0, 20.0, 10.0, 10.0, 0, 0, 1, "geometry:1")))
        assertNull(e.currentCandidateEvidence())
        assertTrue(e.onSample(ScanpathSample(60.0, 70.0, 10.0, 10.0, 0, null, null, "geometry:1")))
        assertTrue(e.onSample(ScanpathSample(110.0, 120.0, 10.0, 10.0, 0, 0, 1, "geometry:1")))
        val prefix = e.currentCandidateEvidence()!!
        assertEquals(100.0, prefix.durationMs, 0.0)
        assertEquals(120.0, prefix.evidenceAvailableMs!!, 0.0)
        assertNull(prefix.wordStartUtf16)
        assertEquals(0, prefix.line)
        assertTrue(e.fixations.isEmpty())
        assertTrue(e.events.none { it.patternId == "P04" || it.patternId == "P12" })
        assertEquals(prefix, e.currentCandidateEvidence())
        val rotated = ScanpathEngine(words, initialCaptureMs = e.latestObservedCaptureMs)
        rotated.updateGeometry(geometry(), 125.0)
        assertFalse(rotated.onSample(ScanpathSample(110.0, 130.0, 10.0, 10.0, 0, 0, 1, "geometry:1")))
        assertTrue(rotated.onSample(ScanpathSample(140.0, 150.0, 10.0, 10.0, 0, 0, 1, "geometry:1")))
        assertTrue(rotated.events.any { it.metrics["reason"] == "non_monotonic_capture" })
    }
    private val words = (0 until 8).map { WordRange(it * 2, it * 2 + 1) }
    private fun geometry(direction: ScanpathTextDirection? = ScanpathTextDirection.LTR,
                         id: String = "geometry:1") = ScanpathGeometry(
        id, 0.0, 0.0, ScanpathRect(0.0, 0.0, 100.0, 60.0), 20.0,
        listOf(ScanpathLine(0, 8, 0.0, 100.0, 0.0, 20.0),
               ScanpathLine(8, 16, 0.0, 100.0, 20.0, 40.0)), direction)

    private fun fixation(e: ScanpathEngine, start: Double, x: Double, y: Double,
                         word: Int?, line: Int?, id: String = "geometry:1") {
        for (dt in listOf(0.0, 50.0, 100.0)) {
            val range = word?.let { words[it] }
            assertTrue(e.onSample(ScanpathSample(start + dt, start + dt + 10,
                x, y, line, range?.startUtf16, range?.endUtf16, id)))
        }
    }

    @Test fun wordAmbiguityPreservesLineContinuityAndDefaultFlagsAbstain() {
        val e = ScanpathEngine(words)
        e.updateGeometry(geometry(), 0.0)
        fixation(e, 10.0, 10.0, 10.0, 0, 0)
        fixation(e, 140.0, 30.0, 10.0, null, 0)
        fixation(e, 270.0, 50.0, 30.0, 4, 1)
        e.boundary("session_end", 380.0)
        assertEquals(3, e.fixations.size)
        assertTrue(e.events.any { it.patternId == "P13" && it.resolution == "word" &&
            it.metrics["reason"] == "unassigned_word" })
        assertTrue(e.events.any { it.patternId == "P05" && it.resolution == "line" })
        assertFalse(e.events.any { it.patternId == "P01" || it.patternId == "P02" })
        val residence = e.events.first { it.patternId == "P12" && it.metrics["kind"] == "residence_update" }
        assertNull(residence.metrics["long_flag"])
        assertNull(e.events.first { it.patternId == "P12" && it.metrics["kind"] == "frontier_progression" }.metrics["slow_flag"])
    }

    @Test fun cycleUsesCollapsedVisitsAndRevisitSupport() {
        val e = ScanpathEngine(words)
        e.updateGeometry(geometry(), 0.0)
        listOf(0, 0, 1, 0, 1).forEachIndexed { i, w ->
            fixation(e, 10.0 + 130 * i, listOf(10.0, 25.0, 50.0, 10.0, 50.0)[i], 10.0, w, 0)
        }
        e.boundary("session_end", 640.0)
        val cycle = e.events.single { it.patternId == "P11" && it.resolution == "word" }
        assertEquals(listOf(0, 2, 3, 4), cycle.fixationIndices)
        val phases = e.events.filter { it.patternId == "P10" && it.resolution == "word" }
        assertTrue(phases.any { it.metrics["phase"] == "revisit" })
        assertTrue(phases.any { it.metrics["phase"] == "frontier_reached" })
        assertTrue(phases.first().fixationIndices.containsAll(listOf(0, 2, 3)))
    }

    @Test fun sweepRequiresSettingsAndVerifiedLtrGeometry() {
        fun sweep(direction: ScanpathTextDirection?, cfg: ScanpathConfig): ScanpathEvent {
            val e = ScanpathEngine(words, cfg)
            e.updateGeometry(geometry(direction), 0.0)
            fixation(e, 10.0, 90.0, 10.0, 0, 0)
            fixation(e, 140.0, 10.0, 30.0, 4, 1)
            e.boundary("end", 250.0)
            return e.events.single { it.patternId == "P09" }
        }
        val configured = ScanpathConfig(returnSweepStartFraction = .2, returnSweepEndFraction = .8)
        assertEquals("observed", sweep(ScanpathTextDirection.LTR, configured).metrics["status"])
        assertEquals("not_configured", sweep(ScanpathTextDirection.LTR, ScanpathConfig()).metrics["reason"])
        assertEquals("direction_unknown", sweep(null, configured).metrics["reason"])
        assertEquals("direction_unsupported", sweep(ScanpathTextDirection.RTL,
            configured.copy(textDirection = ScanpathTextDirection.LTR)).metrics["reason"])
    }

    @Test fun duplicateCaptureAndBoundaryAreImmediateAndDoNotBridge() {
        val e = ScanpathEngine(words)
        e.updateGeometry(geometry(), 0.0)
        fixation(e, 10.0, 10.0, 10.0, 0, 0)
        assertFalse(e.onSample(ScanpathSample(110.0, 120.0, 10.0, 10.0, 0, 0, 1, "geometry:1")))
        assertEquals(1, e.fixations.size)
        assertEquals("non_monotonic_capture", e.events.last().metrics["reason"])
        fixation(e, 140.0, 30.0, 10.0, 1, 0)
        e.boundary("end", 250.0)
        assertFalse(e.events.any { it.patternId == "P01" })
        assertTrue(e.fixations.all { it.rightCensored })
    }

    @Test fun observedResidenceExcludesGapsAndCensoringDoesNotProveShortVisit() {
        val e = ScanpathEngine(words, ScanpathConfig(wordResidenceMs = 250.0))
        e.updateGeometry(geometry(), 0.0)
        fixation(e, 10.0, 10.0, 10.0, 0, 0)
        fixation(e, 140.0, 11.0, 10.0, 0, 0)
        e.boundary("end", 250.0)
        val residence = e.events.last { it.patternId == "P12" && it.resolution == "word" &&
            it.metrics["kind"] == "residence_update" }
        assertEquals(230.0, residence.metrics["observed_span_ms"])
        assertEquals(false, residence.metrics["observed_threshold_crossed"])
        assertNull(residence.metrics["long_flag"])
        assertEquals(true, residence.metrics["right_censored"])
    }

    @Test fun pendingCapBreaksCausallyAndCallbackOnlyModeKeepsNoEvents() {
        val callbacks = mutableListOf<ScanpathEvent>()
        val e = ScanpathEngine(words, ScanpathConfig(maxPendingSamples = 3),
            onEvent = { callbacks.add(it) }, retainEvents = false)
        e.updateGeometry(geometry(), 0.0)
        fixation(e, 10.0, 10.0, 10.0, 0, 0)
        assertTrue(e.onSample(ScanpathSample(140.0, 150.0, 10.0, 10.0, 0, 0, 1, "geometry:1")))
        assertEquals(1, e.fixations.size)
        assertEquals(true, e.fixations.single().rightCensored)
        assertTrue(callbacks.any { it.patternId == "P13" && it.metrics["reason"] == "sample_buffer_limit" })
        assertTrue(e.events.isEmpty())
        assertTrue(e.onSample(ScanpathSample(190.0, 200.0, 10.0, 10.0, 0, 0, 1, "geometry:1")))
        assertTrue(e.onSample(ScanpathSample(240.0, 250.0, 10.0, 10.0, 0, 0, 1, "geometry:1")))
        e.boundary("end", 250.0)
        assertEquals(2, e.fixations.size)
        assertFalse(callbacks.any { it.patternId == "P04" })
        assertEquals(callbacks.mapIndexed { i, _ -> "pattern:${i + 1}" }, callbacks.map { it.eventId })
    }

    @Test fun sampleGapAndGeometryChangeNeverBridgeWordMovements() {
        val e = ScanpathEngine(words)
        e.updateGeometry(geometry(), 0.0)
        fixation(e, 10.0, 10.0, 10.0, 0, 0)
        fixation(e, 400.0, 30.0, 10.0, 1, 0)
        e.updateGeometry(geometry(id = "geometry:2"), 510.0)
        fixation(e, 530.0, 50.0, 10.0, 2, 0, "geometry:2")
        e.boundary("end", 640.0)
        assertEquals(3, e.fixations.size)
        assertFalse(e.events.any { it.patternId == "P01" })
        assertTrue(e.events.any { it.patternId == "P13" && it.metrics["reason"] == "sample_gap" })
        assertTrue(e.events.any { it.patternId == "P13" && it.metrics["reason"] == "geometry_change" })
        assertEquals(listOf("geometry:1", "geometry:1", "geometry:2"),
            e.fixations.map { it.geometrySnapshotId })
    }

    @Test fun oldGeometryIsReleasedAfterItsSweepEventIsFinalized() {
        val e = ScanpathEngine(words, ScanpathConfig(
            returnSweepStartFraction = .2, returnSweepEndFraction = .8))
        e.updateGeometry(geometry(), 0.0)
        fixation(e, 10.0, 90.0, 10.0, 0, 0)
        fixation(e, 140.0, 10.0, 30.0, 4, 1)
        // updateGeometry flushes the second fixation before releasing the old snapshot.
        e.updateGeometry(geometry(id = "geometry:2"), 250.0)
        val sweep = e.events.single { it.patternId == "P09" }
        assertEquals("observed", sweep.metrics["status"])
        assertEquals(listOf(0, 1), sweep.fixationIndices)
        assertEquals(1, e.retainedGeometrySnapshotCount)
        assertTrue(e.events.contains(sweep))
        try {
            e.updateGeometry(geometry(id = "geometry:1"), 260.0)
            fail("Snapshot IDs must remain unique after releasing geometry")
        } catch (_: IllegalArgumentException) {
            assertEquals(1, e.retainedGeometrySnapshotCount)
        }
    }
}
