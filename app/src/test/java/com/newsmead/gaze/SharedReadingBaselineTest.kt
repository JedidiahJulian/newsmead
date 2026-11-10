package com.newsmead.gaze

import org.junit.Assert.*
import org.junit.Test

class SharedReadingBaselineTest {
    @Test fun coordinateSourceSurvivesRdfFailureButNotParticipantCalibrationOrNewAttempt() {
        val attempt = ParticipantReadingBaseline.beginNewBaseline()
        ParticipantReadingBaseline.markCollecting(attempt, "cal")
        ParticipantReadingBaseline.markInvalid(attempt, "cal")
        SharedReadingBaseline.recorded(SharedReadingBaseline.Entry("pkg", "session", attempt, "person", "phone", "cal", "clock", 10.0, 100.0))
        assertNotNull(SharedReadingBaseline.current("cal", "person"))
        assertNull(SharedReadingBaseline.current("other", "person"))
        assertNull(SharedReadingBaseline.current("cal", "other"))
        assertNull(SharedReadingBaseline.current(null, "person"))
        assertNull(ParticipantReadingBaseline.current("cal"))
        assertEquals(SharedReadingBaseline.PENDING, SharedReadingBaseline.current("cal", "person")!!.fields()["status"])
        ParticipantReadingBaseline.beginNewBaseline()
        assertNull(SharedReadingBaseline.current("cal", "person"))
    }
}
