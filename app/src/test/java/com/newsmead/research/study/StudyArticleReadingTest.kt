package com.newsmead.research.study

import org.junit.Assert.*
import org.junit.Test

class StudyArticleReadingTest {
    @Test fun sharedReadingMetadataCarriesRequiredCalibrationAndContract() {
        val shared = StudyArticleReading().nextSegment("article", "calibration", true)
        assertEquals("calibration", shared.getString("calibration_id"))
        assertEquals(StudyBaselineProtocol.SURFACE, shared.getString("coordinate_reference_contract"))
        assertFalse(StudyArticleReading().nextSegment("article").has("coordinate_reference_contract"))
        for (missing in listOf<String?>(null, "", " ")) {
            try { StudyArticleReading().nextSegment("article", missing, true); fail("Missing calibration must reject") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun providerSegmentsShareReadingAndLinkEarlierSession() {
        val reading = StudyArticleReading()
        val first = reading.nextSegment("article-A")
        assertTrue(first.isNull("previous_reading_segment_session_id"))
        reading.opened("session-1")
        val second = reading.nextSegment("article-A")
        assertEquals(first.getString("article_reading_id"), second.getString("article_reading_id"))
        assertEquals(1, first.getInt("reading_segment_index"))
        assertEquals(2, second.getInt("reading_segment_index"))
        assertEquals("session-1", second.getString("previous_reading_segment_session_id"))
    }

    @Test fun reopeningSameContentHasNewReadingAndUnavailableArticleIdsStayExplicit() {
        val first = StudyArticleReading().nextSegment("article-A")
        val reopened = StudyArticleReading().nextSegment("article-A")
        assertEquals(first.getString("article_id"), reopened.getString("article_id"))
        assertNotEquals(first.getString("article_reading_id"), reopened.getString("article_reading_id"))
        listOf<String?>(null, "", "0").forEach { missing ->
            val metadata = StudyArticleReading().nextSegment(missing)
            assertTrue(metadata.isNull("article_id"))
            assertEquals("unavailable", metadata.getString("article_id_status"))
        }
    }
}
