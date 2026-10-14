package com.newsmead.gaze.aid

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReadingAidBookmarkTraceTest {
    private val source = ReadingAidContinuationEvidence("source", 1, 3, 100.0, 200.0, 210.0)
    private fun action(evidence: ReadingAidBookmarkEvidence?) = ReadingAidAction(
        "bookmark", ReadingAidType.READING_BOOKMARK, "LAST_READ_LINE_BOOKMARK_V1", "TEST",
        ReadingAidAnchor(1, "g", 3), 1.0, null, null, 420.0, Double.MAX_VALUE,
        emptyList(), listOf("source"), 100.0, 400.0, 410.0,
        "A2_LAST_READ_LINE_BOOKMARK_V2", listOf("EB15", "CW12"), "experimental", true, "TEST",
        bookmarkEvidence = evidence)

    @Test fun outsideActionRetainsSourceAndDistinctDepartureBounds() {
        val json = action(ReadingAidBookmarkEvidence(source, "sustained_raw_outside_article",
            250.0, 400.0, 410.0, 4)).bookmarkEvidenceJson() as JSONObject
        assertEquals(3, json.getJSONObject("source").getInt("line"))
        assertEquals("source", json.getJSONObject("source").getString("evidence_id"))
        assertEquals(200.0, json.getJSONObject("source").getDouble("last_capture_ms"), 0.0)
        assertEquals(250.0, json.getDouble("departure_first_capture_ms"), 0.0)
        assertEquals(400.0, json.getDouble("departure_last_capture_ms"), 0.0)
        assertEquals(410.0, json.getDouble("departure_evidence_available_ms"), 0.0)
        assertEquals(4, json.getInt("departure_sample_count"))
        assertTrue(json.isNull("departure_support"))
    }

    @Test fun jumpActionRetainsLandingSupportWithoutReplacingSource() {
        val landing = ReadingAidContinuationEvidence("landing", 1, 0, 250.0, 400.0, 410.0)
        val json = action(ReadingAidBookmarkEvidence(source, "supported_non_normal_line_jump",
            250.0, 400.0, 410.0, 0, landing)).bookmarkEvidenceJson() as JSONObject
        assertEquals(3, json.getJSONObject("source").getInt("line"))
        assertEquals(0, json.getJSONObject("departure_support").getInt("line"))
        assertEquals("landing", json.getJSONObject("departure_support").getString("evidence_id"))
        assertEquals(410.0, json.getJSONObject("departure_support").getDouble("evidence_available_ms"), 0.0)
    }

    @Test fun LegacyActionDoesNotFabricateBookmarkEvidence() {
        assertSame(JSONObject.NULL, action(null).bookmarkEvidenceJson())
    }

    @Test fun unavailableDepartureSerializesExplicitNullCaptureAndDeliveryBasis() {
        val json = action(ReadingAidBookmarkEvidence(source, "sustained_gaze_unavailable",
            null, null, 750.0, 2, departureTimeBasis = "delivery_elapsed_time",
            departureFirstDeliveryMs = 250.0, departureLastDeliveryMs = 300.0))
            .bookmarkEvidenceJson() as JSONObject
        assertTrue(json.has("departure_first_capture_ms"))
        assertTrue(json.isNull("departure_first_capture_ms"))
        assertTrue(json.has("departure_last_capture_ms"))
        assertTrue(json.isNull("departure_last_capture_ms"))
        assertEquals("delivery_elapsed_time", json.getString("departure_time_basis"))
        assertEquals(250.0, json.getDouble("departure_first_delivery_ms"), 0.0)
        assertEquals(300.0, json.getDouble("departure_last_delivery_ms"), 0.0)
        assertEquals(750.0, json.getDouble("departure_evidence_available_ms"), 0.0)
        assertEquals(200.0, json.getJSONObject("source").getDouble("last_capture_ms"), 0.0)
    }

}
