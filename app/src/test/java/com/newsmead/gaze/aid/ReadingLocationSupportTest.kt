package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

class ReadingLocationSupportTest {
    private val measured = ReadingLocationUncertainty(2.0, 2.0, "independent_placement_check")
    private val geometry = ScanpathGeometry("rows", 10.0, 70.0,
        ScanpathRect(10.0, 70.0, 310.0, 670.0), 30.0,
        (0 until 20).map { ScanpathLine(it * 10, it * 10 + 8, 0.0, 300.0, it * 30.0, it * 30.0 + 30) })
    private fun context(t: Double, line: Int, exclusion: String? = null, delivery: Double = t,
        now: Double = delivery, frame: ScanpathGeometry = geometry): ReadingAidContext {
        val y = frame.textOriginY + line * 30 + 15
        val s = ScanpathSample(t, delivery, 999.0, 999.0, line, line * 10, line * 10 + 5,
            frame.snapshotId, exclusion = exclusion, displayX = 60.0, displayY = y,
            coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
        return ReadingAidContext(1, frame.snapshotId, 20, line, line * 10, line * 10 + 5,
            now, t, delivery, geometry = frame, rawSample = s)
    }
    private fun bounds(line: Int) = ScanpathRect(30.0, 70.0 + line * 30, 90.0, 100.0 + line * 30)
    private fun visit(h: ReadingLocationSupport, start: Double, line: Int,
        u: ReadingLocationUncertainty? = measured): ReadingLocationUpdate {
        var result = ReadingLocationUpdate(null, false)
        var reread: ReadingSupportedReread? = null
        repeat(5) {
            result = h.update(context(start + it * 100, line), u)
            result.reread?.let { reread = it }
        }
        return result.copy(reread = reread)
    }

    @Test fun stabilizesSelectedScreenCoordinatesAndKeepsCaptureBounds() {
        val h = ReadingLocationSupport()
        assertNull(h.update(context(0.0, 4), measured).current)
        assertNull(h.update(context(100.0, 4), measured).current)
        val v = h.update(context(200.0, 4, delivery = 220.0), measured).current!!
        assertEquals(4, v.band.representativeLine) // Raw coordinates intentionally outside article.
        assertEquals(0.0, v.firstCaptureMs, 0.0)
        assertEquals(200.0, v.lastCaptureMs, 0.0)
        assertEquals(220.0, v.evidenceAvailableMs, 0.0)
    }

    @Test fun duplicateCallbacksCannotAccumulateResidenceOrChangeVisit() {
        val h = ReadingLocationSupport()
        h.update(context(0.0, 4), measured)
        val duplicate = h.update(context(0.0, 4, delivery = 50.0), measured)
        assertFalse(duplicate.fresh); assertNull(duplicate.current)
        h.update(context(100.0, 4), measured)
        val v = h.update(context(200.0, 4), measured).current!!
        val tick = h.update(context(200.0, 4, now = 600.0), measured)
        assertFalse(tick.fresh); assertEquals(v, tick.current)
    }

    @Test fun longGapAndBlinkBreakContiguousSupport() {
        val h = ReadingLocationSupport()
        val first = visit(h, 0.0, 4).current!!
        assertNull(h.update(context(700.0, 4), measured).current)
        h.update(context(800.0, 4), measured)
        val afterGap = h.update(context(900.0, 4), measured).current!!
        assertTrue(afterGap.segment > first.segment)
        assertEquals(700.0, afterGap.firstCaptureMs, 0.0)
        assertNull(h.update(context(1000.0, 4, "blink"), measured).current)
        assertNull(h.update(context(1100.0, 4), measured).current)
    }

    @Test fun exact250msGapCountsButStaleAndFutureSamplesDoNot() {
        val h = ReadingLocationSupport()
        h.update(context(0.0, 4), measured)
        assertNotNull(h.update(context(250.0, 4), measured).current)
        assertNull(h.update(context(300.0, 4, now = 551.0), measured).current)
        assertNull(h.update(context(600.0, 4, delivery = 599.0), measured).current)
    }

    @Test fun unknownErrorHasNeighborBandAndNoMeasuredDistinguishability() {
        val h = ReadingLocationSupport()
        val a = visit(h, 0.0, 4, null).current!!.band
        val b = visit(h, 500.0, 7, null).current!!.band
        assertEquals(3, a.firstLine); assertEquals(5, a.lastLine)
        assertFalse(a.measured); assertFalse(a.measurablyDistinctFrom(b))
        assertFalse(a.overlaps(b))
    }

    @Test fun measuredRadiusOverlapsAllRowsItsIntervalTouches() {
        val h = ReadingLocationSupport()
        val v = visit(h, 0.0, 4, ReadingLocationUncertainty(0.0, 20.0, "measured")).current!!
        assertEquals(3, v.band.firstLine); assertEquals(5, v.band.lastLine)
        assertTrue(v.band.measured)
    }

    @Test fun fiveVisitsRequireReturnAndContinuedForwardWithinFrontier() {
        val h = ReadingLocationSupport()
        val results = listOf(4, 7, 4, 5, 6).mapIndexed { i, row -> visit(h, i * 500.0, row) }
        assertTrue(results.take(4).all { it.reread == null })
        val reread = results.last().reread!!
        assertEquals(listOf(4, 7, 4, 5, 6), reread.visits.map { it.band.representativeLine })
        assertTrue(reread.measuredDistinguishability)
    }

    @Test fun unknownBandsAllowBroadDepartureWithExplicitUnknownAdjacentProgress() {
        val h = ReadingLocationSupport()
        val result = listOf(4, 7, 4, 5, 6).mapIndexed { i, row -> visit(h, i * 500.0, row, null) }.last()
        assertNotNull(result.reread)
        assertFalse(result.reread!!.measuredDistinguishability)
        assertTrue(result.searchWitnesses.isEmpty())
    }

    @Test fun adjacentNoiseAndOvershootingFrontierCannotBecomeReread() {
        listOf(listOf(4, 5, 4, 5, 6), listOf(4, 7, 4, 6, 8), listOf(4, 7, 5, 6, 7)).forEach { route ->
            val h = ReadingLocationSupport()
            val results = route.mapIndexed { i, row -> visit(h, i * 500.0, row, null) }
            assertTrue(results.all { it.reread == null })
        }
    }

    @Test fun measuredSearchRequiresThreeDistinctIntervals() {
        val h = ReadingLocationSupport()
        val result = listOf(5, 7, 6).mapIndexed { i, row -> visit(h, i * 500.0, row) }.last()
        assertEquals(listOf(5, 7, 6), result.searchWitnesses.map { it.band.representativeLine })
        val broad = ReadingLocationSupport()
        val uncertain = ReadingLocationUncertainty(2.0, 20.0, "measured")
        assertTrue(listOf(5, 7, 6).mapIndexed { i, row -> visit(broad, i * 500.0, row, uncertain) }
            .last().searchWitnesses.isEmpty())
    }

    @Test fun rereadWindowIsBoundedAndReorderedCapturesCannotRecoverOldEvidence() {
        val bounded = ReadingLocationSupport(rereadWindowMs = 1000.0)
        assertTrue(listOf(4, 7, 4, 5, 6).mapIndexed { i, row -> visit(bounded, i * 500.0, row) }
            .all { it.reread == null })
        val h = ReadingLocationSupport()
        visit(h, 0.0, 4)
        assertNull(h.update(context(300.0, 4, delivery = 500.0), measured).current)
        assertFalse(h.update(context(400.0, 4, delivery = 550.0), measured).fresh)
        assertNull(h.update(context(600.0, 4), measured).current)
    }

    @Test fun projectedAutoScrollCoordinatesAreUsedWithoutChangingRecordedSample() {
        val h = ReadingLocationSupport()
        var last: ReadingLocationUpdate? = null
        repeat(3) {
            val c = context(it * 100.0, 4)
            val original = c.rawSample!!.copy(displayY = 600.0,
                motionCompensation = ScanpathMotionCompensation(it * 100.0, "rows", 10.0, 465.0, 50.0, 135.0))
            last = h.update(c.copy(rawSample = original), measured)
            assertEquals(600.0, original.displayY!!, 0.0)
        }
        assertEquals(4, last!!.current!!.band.representativeLine)
    }

    @Test fun translatingViewportKeepsMedianAndMeasuredIntervalsInContentFrame() {
        val h = ReadingLocationSupport()
        var result: ReadingLocationUpdate? = null
        repeat(5) { i ->
            val shifted = geometry.copy(textOriginY = 70.0 - i * 25.0)
            result = h.update(context(i * 100.0, 8, frame = shifted), measured)
        }
        val v = result!!.current!!
        assertEquals(8, v.band.representativeLine)
        assertEquals(0.0, v.firstCaptureMs, 0.0)
        assertEquals(253.0, v.band.intervalTopPx!!, 0.0)
        assertEquals(257.0, v.band.intervalBottomPx!!, 0.0)
    }

    @Test fun geometryChangesAndExtractorOnlyExclusionsHaveDifferentBoundaries() {
        val h = ReadingLocationSupport()
        val original = visit(h, 0.0, 4).current!!
        val unassigned = h.update(context(500.0, 4, "unassigned_word").copy(validLiveInput = false), measured).current!!
        assertEquals(original.id, unassigned.id)
        assertNull(h.update(context(600.0, 4, frame = geometry.copy(snapshotId = "reflow")), measured).current)
    }

    @Test fun mappingOnlyWordRequiresBoundsAndExplicitOptInThenOffersOnce() {
        val default = ReadingLocationSupport()
        repeat(16) { assertNull(default.update(context(it * 100.0, 4), wordBounds = bounds(4)).wordResidence) }
        val h = ReadingLocationSupport(allowMappingOnlyWordResidence = true)
        val offers = (0..15).map { h.update(context(it * 100.0, 4), wordBounds = bounds(4)).wordResidence }
            .filterNotNull()
        assertEquals(1, offers.size)
        assertEquals(1000.0, offers.single().lastCaptureMs - offers.single().firstCaptureMs, 0.0)
        assertEquals("mapping_only_unvalidated", offers.single().confidenceProvenance)
        h.update(context(1600.0, 4, "blink"), wordBounds = bounds(4))
        repeat(16) { assertNull(h.update(context(1700.0 + it * 100, 4), wordBounds = bounds(4)).wordResidence) }
        val noBounds = ReadingLocationSupport(allowMappingOnlyWordResidence = true)
        repeat(16) { assertNull(noBounds.update(context(it * 100.0, 4)).wordResidence) }
    }

    @Test fun measuredWordRequiresEntireErrorBoxWithinWord() {
        val h = ReadingLocationSupport()
        val tooWide = ReadingLocationUncertainty(40.0, 2.0, "measured")
        repeat(16) { assertNull(h.update(context(it * 100.0, 4), tooWide, bounds(4)).wordResidence) }
        val bounded = ReadingLocationSupport()
        val offers = (0..15).map { bounded.update(context(it * 100.0, 4), measured, bounds(4)).wordResidence }
            .filterNotNull()
        assertEquals(1, offers.size)
        assertEquals(measured.provenance, offers.single().confidenceProvenance)
    }

    @Test fun wordRangeMustBelongToItsExactSourceRow() {
        val h = ReadingLocationSupport(allowMappingOnlyWordResidence = true)
        repeat(16) {
            val c = context(it * 100.0, 4)
            assertNull(h.update(c.copy(rawSample = c.rawSample!!.copy(wordEndUtf16 = 55)),
                wordBounds = bounds(4)).wordResidence)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMeasuredRadiusIsRejected() { ReadingLocationUncertainty(Double.NaN, 0.0, "bad") }
}
