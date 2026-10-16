package com.newsmead.gaze.scanpath

import org.junit.Assert.*
import org.junit.Test

class ScanpathMotionCompensationTest {
    private fun geometry(originY: Double = 0.0, id: String = "layout:1") = ScanpathGeometry(
        id, 0.0, originY, ScanpathRect(0.0, 0.0, 200.0, 1000.0), 20.0,
        listOf(ScanpathLine(0, 1, 0.0, 200.0, 0.0, 1000.0)), ScanpathTextDirection.LTR,
    )

    private fun sample(t: Double, screenY: Double = 300.0, originY: Double = 100.0) = ScanpathSample(
        captureMs = t, deliveryMs = t + 80.0, rawX = 17.0, rawY = 19.0,
        line = 0, wordStartUtf16 = 0, wordEndUtf16 = 1, geometrySnapshotId = "layout:1",
        displayX = 50.0, displayY = screenY,
        coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED,
        motionCompensation = ScanpathMotionCompensation(t, "layout:1", 0.0, originY, 50.0, screenY - originY),
    )

    private fun engine(config: ScanpathConfig = ScanpathConfig()) =
        ScanpathEngine(listOf(WordRange(0, 1)), config).also { it.updateGeometry(geometry(), 0.0) }

    @Test fun timelineUsesCaptureGeometryWithEightyMillisecondDeliveryLag() {
        val timeline = ScanpathGeometryTimeline()
        assertTrue(timeline.record(100.0, geometry(100.0)))
        assertTrue(timeline.record(140.0, geometry(90.0)))
        assertTrue(timeline.record(180.0, geometry(80.0)))
        val captured = timeline.at(100.0, 100.0)!!
        assertEquals(100.0, captured.atMs, 0.0)
        assertEquals(100.0, captured.geometry.textOriginY, 0.0)
        assertEquals(140.0, timeline.at(179.0)!!.atMs, 0.0)
        assertNull(timeline.at(99.0))
        assertNull(timeline.at(181.0))
    }

    @Test fun timelineEnforcesRecordOrderAgeRetentionAndReset() {
        val timeline = ScanpathGeometryTimeline(200.0)
        assertFalse(timeline.record(Double.NaN, geometry()))
        assertFalse(timeline.record(-1.0, geometry()))
        assertTrue(timeline.record(0.0, geometry(10.0)))
        assertTrue(timeline.record(200.0, geometry(20.0)))
        assertFalse(timeline.record(199.0, geometry()))
        assertTrue(timeline.record(200.0, geometry(30.0)))
        assertEquals(30.0, timeline.at(200.0)!!.geometry.textOriginY, 0.0)
        assertTrue(timeline.record(300.0, geometry()))
        assertNull(timeline.at(99.0))
        // A static floor remains causal for a recent capture; motion can impose max age.
        assertEquals(0.0, timeline.at(100.0)!!.atMs, 0.0)
        assertNotNull(timeline.at(100.0, 100.0))
        assertNull(timeline.at(100.0, 99.0))
        assertNull(timeline.at(Double.POSITIVE_INFINITY))
        assertNull(timeline.at(200.0, Double.NaN))
        assertNull(timeline.at(200.0, -1.0))
        timeline.clear()
        assertNull(timeline.at(200.0))
        assertTrue(timeline.record(1.0, geometry()))
    }

    @Test fun timelineBoundsResourceUseAndCopiesMutableLineLists() {
        val timeline = ScanpathGeometryTimeline(10000.0)
        val lines = geometry().lines.toMutableList()
        assertTrue(timeline.record(0.0, geometry().copy(lines = lines)))
        lines.clear()
        assertEquals(1, timeline.at(0.0)!!.geometry.lines.size)
        for (t in 1..4100) assertTrue(timeline.record(t.toDouble(), geometry()))
        assertNull(timeline.at(0.0))
        assertNotNull(timeline.at(4100.0))
    }

    @Test fun bodyPointPursuitStaysCompactWithoutChangingRawDisplayOrProcessing() {
        val e = engine(ScanpathConfig(wordResidenceMs = 250.0))
        for (i in 0..6) {
            val s = sample(100.0 + i * 50.0, 300.0 - i * 20.0, 100.0 - i * 20.0)
            assertTrue(e.onSample(s))
            assertEquals(17.0, s.rawX!!, 0.0)
            assertEquals(19.0, s.rawY!!, 0.0)
            assertEquals(s.displayY, s.processingY)
            assertEquals(200.0, s.analysisY!!, 0.0)
        }
        assertEquals(300.0, e.currentCandidateEvidence()!!.durationMs, 0.0)
        e.boundary("end", 480.0)
        assertEquals(1, e.fixations.size)
        assertEquals(200.0, e.fixations.single().y, 0.0)
        assertEquals(0.0, e.fixations.single().dispersionPx, 0.0)
        assertTrue(e.events.any { it.resolution == "word" && it.metrics["long_flag"] == true })
    }

    @Test fun fixedScreenPointCrossingContentDoesNotBecomeLongWordResidence() {
        val e = engine(ScanpathConfig(wordResidenceMs = 250.0))
        // Conservative fixture: even if assignment incorrectly repeats one word, the
        // moving content coordinate cannot form an established compact candidate.
        for (i in 0..6) assertTrue(e.onSample(sample(100.0 + i * 50.0, 300.0, 100.0 - i * 20.0)))
        assertNull(e.currentCandidateEvidence())
        e.boundary("end", 480.0)
        assertTrue(e.fixations.isEmpty())
        assertFalse(e.events.any { it.metrics["long_flag"] == true })
    }

    @Test fun absentMetadataKeepsDeclaredStreamCoordinatesAndDefaultEvents() {
        val raw = engine()
        val smoothed = engine()
        for (i in 0..2) {
            val s = sample(100.0 + i * 50.0).copy(motionCompensation = null)
            assertEquals(s.processingX, s.analysisX)
            assertEquals(s.processingY, s.analysisY)
            assertTrue(smoothed.onSample(s))
            val r = s.copy(coordinateStream = ScanpathCoordinateStream.CALIBRATED_RAW)
            assertEquals(17.0, r.analysisX!!, 0.0)
            assertEquals(19.0, r.analysisY!!, 0.0)
            assertTrue(raw.onSample(r))
        }
        raw.boundary("end", 280.0)
        smoothed.boundary("end", 280.0)
        assertEquals(19.0, raw.fixations.single().y, 0.0)
        assertEquals(300.0, smoothed.fixations.single().y, 0.0)
        assertEquals(raw.events.map { it.patternId to it.metrics }, smoothed.events.map { it.patternId to it.metrics })
    }

    @Test fun malformedMetadataUsesInvalidBoundaryAndPreservesCaptureFloor() {
        val base = sample(300.0)
        val m = base.motionCompensation!!
        val bad = listOf(
            base.copy(coordinateStream = ScanpathCoordinateStream.CALIBRATED_RAW),
            base.copy(motionCompensation = m.copy(modeId = "unknown")),
            base.copy(motionCompensation = m.copy(geometrySnapshotId = "other")),
            base.copy(motionCompensation = m.copy(geometryTimeMs = 301.0)),
            base.copy(motionCompensation = m.copy(geometryTimeMs = -1.0)),
            base.copy(motionCompensation = m.copy(contentY = 201.0)),
            base.copy(motionCompensation = m.copy(textOriginX = Double.NaN)),
            base.copy(displayY = Double.POSITIVE_INFINITY),
            base.copy(motionCompensation = m.copy(contentX = Double.NaN)),
        )
        for (invalid in bad) {
            val e = engine()
            for (t in listOf(100.0, 150.0, 200.0)) assertTrue(e.onSample(sample(t)))
            assertFalse(invalid.motionCompensation!!.validFor(invalid))
            assertNull(invalid.analysisY)
            assertFalse(e.onSample(invalid))
            assertEquals("invalid_sample", e.events.last().metrics["reason"])
            assertTrue(e.fixations.single().rightCensored)
            assertEquals(300.0, e.latestObservedCaptureMs!!, 0.0)
            assertFalse(e.onSample(sample(250.0)))
            assertEquals(300.0, e.latestObservedCaptureMs!!, 0.0)
            assertTrue(e.onSample(sample(350.0)))
        }
    }

    @Test fun frameSwitchGapAndLayoutChangeDoNotBridgeCandidates() {
        val e = engine()
        for (t in listOf(100.0, 150.0, 200.0)) assertTrue(e.onSample(sample(t)))
        assertTrue(e.onSample(sample(250.0).copy(motionCompensation = null)))
        assertEquals("analysis_frame_change", e.events.last().metrics["reason"])
        assertNull(e.currentCandidateEvidence())
        assertTrue(e.onSample(sample(300.0)))
        assertEquals("analysis_frame_change", e.events.last().metrics["reason"])
        assertTrue(e.onSample(sample(500.0)))
        assertEquals("sample_gap", e.events.last().metrics["reason"])
        assertNull(e.currentCandidateEvidence())
        e.updateGeometry(geometry(id = "layout:2"), 510.0)
        assertFalse(e.onSample(sample(550.0)))
        assertEquals("invalid_sample", e.events.last().metrics["reason"])
        assertFalse(e.events.any { it.patternId == "P04" })
    }

    @Test fun compensatedAmbiguousWordStillPreservesMissingAssignment() {
        val e = engine()
        assertTrue(e.onSample(sample(100.0)))
        assertTrue(e.onSample(sample(150.0).copy(wordStartUtf16 = null, wordEndUtf16 = null)))
        assertTrue(e.onSample(sample(200.0)))
        assertNull(e.currentCandidateEvidence()!!.wordStartUtf16)
        e.boundary("end", 280.0)
        assertNull(e.fixations.single().wordStartUtf16)
        assertEquals(0, e.fixations.single().line)
        assertTrue(e.events.any { it.metrics["reason"] == "unassigned_word" })
    }
}
