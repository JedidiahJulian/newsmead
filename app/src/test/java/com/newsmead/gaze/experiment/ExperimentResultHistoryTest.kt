package com.newsmead.gaze.experiment

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExperimentResultHistoryTest {
    private fun report(id: String, condition: String = "A", session: String = "first", fingerprint: String = "fit1",
        collection: String = "shared1", outcome: String = "completed", mode: String = "validation", missing: Int = 0,
        valid: Int = 10, truncated: Boolean = false) = JSONObject().put("reportId", id)
        .put("metadata", JSONObject().put("conditionId", condition).put("sessionId", session)
            .put("calibrationFingerprint", fingerprint).put("collectionId", collection).put("outcome", outcome))
        .put("result", JSONObject().put("mode", mode).put("rowsTruncated", truncated).put("validUniqueFreshCount", valid)
            .put("summary", JSONObject().put("missingHeldOutTargetIds", JSONArray((0 until missing).map { it.toString() }))))

    @Test fun recalibratingNextPairKeepsBothOldResultsVisibleWithoutGrantingCurrentCompletion() {
        val a = report("a")
        val b = report("b", "B")
        val c = report("c", "C")
        val reports = listOf(c, b, a)
        val displayed = ExperimentResultHistory.scope(reports, ExperimentResultHistory.ALL, "first")
        assertSame(a, ExperimentResultHistory.latest(displayed, "A", "validation"))
        assertSame(b, ExperimentResultHistory.latest(displayed, "B", "validation"))
        assertSame(c, ExperimentResultHistory.latest(displayed, "C", "validation"))
        assertTrue(ExperimentResultHistory.current(reports, "first", "B", "fit2", "shared2").isEmpty())
        assertTrue(ExperimentResultHistory.current(reports, "first", "C", "fit2", "shared2").isEmpty())
        assertEquals(listOf(a), ExperimentResultHistory.current(reports, "first", "A", "fit1", "shared1"))
    }
    @Test fun startingANewComparisonDoesNotErasePreviousComparisonHistory() {
        val old = report("old", session = "first")
        val recent = report("recent", condition = "C", session = "second")
        val reports = listOf(recent, old)
        assertEquals(reports, ExperimentResultHistory.scope(reports, ExperimentResultHistory.ALL, "second"))
        assertEquals(listOf(recent), ExperimentResultHistory.scope(reports, ExperimentResultHistory.CURRENT, "second"))
        assertEquals(listOf(old), ExperimentResultHistory.scope(reports, "first", "second"))
        assertTrue(ExperimentResultHistory.current(reports, "second", "A", "fit1", "shared1").isEmpty())
    }
    @Test fun latestFailedOrIncompleteAttemptsRemainVisibleAndEarlierAttemptsRemainAccessible() {
        val completed = report("complete")
        val failed = report("failed", outcome = "failure", valid = 0, missing = 8)
        val reading = report("read", mode = "reading")
        val reports = listOf(reading, failed, completed)
        assertSame(failed, ExperimentResultHistory.latest(reports, "A", "validation"))
        assertSame(reading, ExperimentResultHistory.latest(reports, "A", "reading"))
        assertFalse(ExperimentResultHistory.qualifies(failed))
        assertTrue(ExperimentResultHistory.qualifies(completed))
        assertEquals(3, ExperimentResultHistory.scope(reports, "first", "first").size)
        assertFalse(ExperimentResultHistory.qualifies(report("incomplete", missing = 1)))
        assertFalse(ExperimentResultHistory.qualifies(report("truncated", truncated = true)))
        assertFalse(ExperimentResultHistory.qualifies(report("aborted", outcome = "aborted")))
    }
    @Test fun eligibilityRejectsOtherConditionsCollectionsAndMissingIdentity() {
        val a = report("a")
        val otherCollection = report("other", collection = "shared2")
        val otherCondition = report("b", condition = "B")
        val reports = listOf(otherCollection, otherCondition, a)
        assertEquals(listOf(a), ExperimentResultHistory.current(reports, "first", "A", "fit1", "shared1"))
        assertTrue(ExperimentResultHistory.current(reports, "first", "A", null, "shared1").isEmpty())
        assertTrue(ExperimentResultHistory.current(reports, "first", "A", "fit1", null).isEmpty())
    }
}
