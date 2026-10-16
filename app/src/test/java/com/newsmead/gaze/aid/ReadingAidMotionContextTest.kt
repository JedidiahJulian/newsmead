package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

class ReadingAidMotionContextTest {
    private val geometry = ScanpathGeometry("layout:1", 10.0, 70.0,
        ScanpathRect(10.0, 0.0, 300.0, 600.0), 30.0,
        listOf(ScanpathLine(0, 5, 0.0, 290.0, 0.0, 30.0)))
    private val sample = ScanpathSample(100.0, 180.0, 17.0, 19.0, 0, 0, 5, "layout:1",
        displayX = 60.0, displayY = 120.0, coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED,
        motionCompensation = ScanpathMotionCompensation(90.0, "layout:1", 10.0, 100.0, 50.0, 20.0))
    private fun context(value: ScanpathSample = sample, frame: ScanpathGeometry? = geometry) =
        ReadingAidContext(0, "layout:1", 1, 0, 0, 5, 180.0, 100.0, 180.0,
            geometry = frame, rawSample = value)

    @Test fun presentationFollowsCurrentTextButRetainsTheRecordedScreenPoint() {
        val c = context()
        val displayed = c.presentationSample!!
        assertEquals(60.0, displayed.processingX!!, 0.0)
        assertEquals(90.0, displayed.processingY!!, 0.0)
        assertNull(displayed.motionCompensation)
        assertSame(sample, c.rawSample)
        assertEquals(120.0, c.rawSample!!.processingY!!, 0.0)
        assertEquals(20.0, sample.analysisY!!, 0.0)
        assertEquals(19.0, requireNotNull(c.rawSample).rawY!!, 0.0)
    }

    @Test fun invalidOrReflowedProjectionAbstainsAndLegacySampleIsUnchanged() {
        assertNull(context(frame = null).presentationSample)
        assertNull(context(frame = geometry.copy(snapshotId = "layout:2")).presentationSample)
        assertNull(context(sample.copy(motionCompensation =
            sample.motionCompensation!!.copy(contentY = 21.0))).presentationSample)
        val legacy = sample.copy(motionCompensation = null)
        assertSame(legacy, context(legacy).presentationSample)
    }
}
