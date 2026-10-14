package com.newsmead.gaze.scanpath

import org.junit.Assert.*
import org.junit.Test

class SmoothedScanpathCoreTest {
    private fun engine(): ScanpathEngine = ScanpathEngine(listOf(WordRange(0, 4))).also {
        it.updateGeometry(ScanpathGeometry("g", 0.0, 0.0,
            ScanpathRect(0.0, 0.0, 200.0, 100.0), 20.0,
            listOf(ScanpathLine(0, 4, 0.0, 200.0, 0.0, 20.0))), 0.0)
    }
    private fun sample(t: Double, raw: Double = 150.0, display: Double? = 10.0) =
        ScanpathSample(t, t + 10.0, raw, 80.0, 0, 0, 4, "g",
            displayX = display, displayY = 10.0,
            coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)

    @Test fun filteredGroupingCentroidAndDispersionPreserveOriginals() {
        val e = engine()
        val rows = listOf(sample(10.0, 150.0, 10.0), sample(60.0, 20.0, 12.0),
            sample(110.0, 190.0, 14.0))
        rows.forEach { assertTrue(e.onSample(it)) }
        assertEquals(3, e.currentCandidateEvidence()!!.sampleCount)
        e.boundary("end", 120.0)
        val f = e.fixations.single()
        assertEquals(12.0, f.x, 0.0)
        assertEquals(10.0, f.y, 0.0)
        assertEquals(4.0, f.dispersionPx, 0.0)
        assertEquals(0, f.wordStartUtf16)
        assertEquals(150.0, rows.first().rawX!!, 0.0)
        assertEquals(80.0, rows.first().rawY!!, 0.0)
    }

    @Test fun defaultRawAndMissingFilteredValuesNeverSubstituteEachOther() {
        val default = ScanpathSample(10.0, 20.0, 150.0, 80.0, 0, 0, 4, "g",
            displayX = 10.0, displayY = 10.0)
        assertEquals(ScanpathCoordinateStream.CALIBRATED_RAW, default.coordinateStream)
        assertEquals(150.0, default.processingX!!, 0.0)
        assertTrue(engine().onSample(default))
        for (missing in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            val e = engine()
            assertTrue(e.onSample(sample(10.0)))
            assertFalse(e.onSample(sample(60.0, display = missing)))
            assertNull(e.currentCandidateEvidence())
            assertTrue(e.events.any { it.metrics["reason"] == "invalid_sample" })
        }
    }

    @Test fun streamChangesSplitCandidatesAndPathContinuity() {
        val e = engine()
        for (t in listOf(10.0, 60.0, 110.0)) assertTrue(e.onSample(sample(t)))
        for (t in listOf(160.0, 210.0, 260.0)) assertTrue(e.onSample(sample(t).copy(
            coordinateStream = ScanpathCoordinateStream.CALIBRATED_RAW, rawX = 10.0, rawY = 10.0)))
        e.boundary("end", 270.0)
        assertEquals(2, e.fixations.size)
        assertNotEquals(e.fixations.first().segment, e.fixations.last().segment)
        assertTrue(e.fixations.first().rightCensored)
        assertTrue(e.fixations.last().leftCensored)
        assertTrue(e.events.any { it.metrics["reason"] == "coordinate_stream_change" })
        assertFalse(e.events.any { it.patternId == "P04" })
    }

    @Test fun selectedStreamKeepsCaptureOrderGapAndGeometryGuards() {
        val e = engine()
        assertTrue(e.onSample(sample(10.0)))
        assertFalse(e.onSample(sample(10.0)))
        assertTrue(e.events.any { it.metrics["reason"] == "non_monotonic_capture" })
        assertTrue(e.onSample(sample(60.0)))
        assertTrue(e.onSample(sample(250.0)))
        assertTrue(e.events.any { it.metrics["reason"] == "sample_gap" })
        assertFalse(e.onSample(sample(300.0).copy(geometrySnapshotId = "old")))
        assertNull(e.currentCandidateEvidence())
    }
}
