package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.MahalanobisBaselineCollector
import com.newsmead.gaze.mahalanobis.MahalanobisConfig
import com.newsmead.gaze.mahalanobis.RdfFeatureVector
import com.newsmead.gaze.mahalanobis.RdfValues
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ParticipantReadingBaselineTest {
    @After fun clearSession() { ParticipantReadingBaseline.beginNewBaseline() }

    private fun profile() = MahalanobisBaselineCollector(MahalanobisConfig(
        windowMs = 1, minimumRetainedWindows = 2, minimumBaselineDurationMs = 0,
    )).apply {
        add(RdfFeatureVector(1, 0, 1, RdfValues(1.0, .2, 2.0)))
        add(RdfFeatureVector(2, 1, 2, RdfValues(2.0, .3, 3.0)))
    }.freeze().profile!!

    @Test fun profileRequiresMatchingCalibrationAndActiveSession() {
        val session = ParticipantReadingBaseline.beginNewBaseline()
        val entry = ParticipantReadingBaseline.Entry("P01", session, "calibration-A", profile())
        ParticipantReadingBaseline.freeze(entry)
        assertSame(entry, ParticipantReadingBaseline.current("calibration-A"))
        assertNull(ParticipantReadingBaseline.current("calibration-B"))
        assertNull(ParticipantReadingBaseline.current(null))
        ParticipantReadingBaseline.beginNewBaseline()
        assertNull(ParticipantReadingBaseline.current("calibration-A"))
        assertFalse(ParticipantReadingBaseline.isCurrentSession(session))
    }

    @Test fun frozenProfileCannotBeReplacedWithoutExplicitNewSession() {
        val session = ParticipantReadingBaseline.beginNewBaseline()
        val entry = ParticipantReadingBaseline.Entry("P01", session, "calibration-A", profile())
        ParticipantReadingBaseline.freeze(entry)
        assertThrows(IllegalStateException::class.java) { ParticipantReadingBaseline.freeze(entry) }
        assertSame(entry.profile, ParticipantReadingBaseline.current("calibration-A")!!.profile)
    }

    @Test fun abandonedAttemptCannotFreezeIntoNewParticipantSession() {
        val abandoned = ParticipantReadingBaseline.beginNewBaseline()
        val entry = ParticipantReadingBaseline.Entry("P01", abandoned, "calibration-A", profile())
        ParticipantReadingBaseline.beginNewBaseline()
        assertThrows(IllegalStateException::class.java) { ParticipantReadingBaseline.freeze(entry) }
        assertNull(ParticipantReadingBaseline.current("calibration-A"))
    }
}
