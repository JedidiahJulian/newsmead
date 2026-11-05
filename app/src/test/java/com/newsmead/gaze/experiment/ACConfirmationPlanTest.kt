package com.newsmead.gaze.experiment

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ACConfirmationPlanTest {
    private fun plan() = ACConfirmationPlan.create(setupNotes = "Same chair and phone support", viewingDistanceCm = 40.0)
    private fun bound(plan: ACConfirmationPlan, index: Int = 0, reports: List<JSONObject> = emptyList(),
        collection: String = "fit$index") = plan.enter(index, "old$index", reports).bind(index, collection, "fp$index", reports)
    private fun report(plan: ACConfirmationPlan, index: Int, mode: String, start: String =
        if (mode == ExperimentFlow.READING) "2026-11-05T01:01:00.000Z" else "2026-11-05T01:00:00.000Z"): JSONObject {
        val step = plan.steps[index]
        return JSONObject().put("metadata", JSONObject().put("sessionId", plan.sessionId)
            .put("conditionId", step.conditionId).put("collectionId", step.boundCollectionId)
            .put("calibrationFingerprint", step.boundFingerprint).put("sourceMetadata", JSONObject(plan.sourceMetadata(index))
                .put("confirmation_boot_count", "42"))
            .put("outcome", "completed").put("startedAtUtc", start).put("endedAtUtc", "2026-11-05T01:00:10.000Z"))
            .put("result", JSONObject().put("mode", mode).put("rowsTruncated", false).put("validUniqueFreshCount", 10)
                .put("startMs", if (mode == ExperimentFlow.READING) 12000L else 1000L)
                .put("endMs", if (mode == ExperimentFlow.READING) 22000L else 11000L)
                .put("summary", JSONObject().put("missingHeldOutTargetIds", JSONArray())))
    }
    private fun pair(plan: ACConfirmationPlan, index: Int) = listOf(report(plan, index, ExperimentFlow.VALIDATION),
        report(plan, index, ExperimentFlow.READING))
    private fun live(plan: ACConfirmationPlan, index: Int = 0) = plan.steps[index].let {
        mapOf(it.conditionId to ACConfirmationPlan.Fit(it.boundCollectionId!!, it.boundFingerprint!!)) }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejected input") } catch (_: IllegalArgumentException) { }
    }

    @Test fun orderAndFreshEntryAndRetryAreExplicit() {
        val initial = plan()
        assertEquals(listOf("A", "C", "C", "A"), initial.steps.map { it.conditionId })
        assertEquals(listOf(1, 1, 2, 2), initial.steps.map { it.round })
        assertEquals(4, initial.steps.map { it.id }.toSet().size)
        assertEquals(ACConfirmationPlan.Stage.NEED_CALIBRATION, initial.action(emptyList(), emptyMap()).stage)
        rejects { initial.bind(0, "new", "fp", emptyList()) }
        val entered = initial.enter(0, "old", emptyList())
        rejects { entered.bind(0, "old", "fp", emptyList()) }
        assertEquals("old", entered.enter(0, "changed", emptyList()).steps[0].baselineCollectionId)
        val first = entered.bind(0, "fresh", "fp", emptyList())
        val retry = first.bind(0, "retry", "fp2", emptyList())
        rejects { retry.bind(0, "fresh", "fp", emptyList()) }
        rejects { retry.enter(1, null, emptyList()) }
    }

    @Test fun allHistoricalBlocksSurviveLaterStoreReplacementAndConsecutiveCNeedsNewFit() {
        var current = plan()
        val reports = mutableListOf<JSONObject>()
        for (index in 0..3) {
            current = bound(current, index, reports)
            assertEquals(ACConfirmationPlan.Stage.VALIDATION, current.action(reports, live(current, index)).stage)
            reports += report(current, index, ExperimentFlow.VALIDATION)
            assertEquals(ACConfirmationPlan.Stage.READING, current.action(reports, live(current, index)).stage)
            reports += report(current, index, ExperimentFlow.READING)
            if (index == 1) {
                val entered = current.enter(2, "fit1", reports)
                rejects { entered.bind(2, "fit1", "fp1", reports) }
            }
        }
        assertEquals(ACConfirmationPlan.Action(null, ACConfirmationPlan.Stage.COMPLETE), current.action(reports, emptyMap()))
        rejects { current.bind(3, "replacement", "fp", reports) }
    }

    @Test fun currentFitMismatchForcesCalibrationAndRetryInvalidatesOldReports() {
        val first = bound(plan())
        val reports = pair(first, 0).take(1)
        assertEquals(ACConfirmationPlan.Stage.NEED_CALIBRATION, first.action(reports,
            mapOf("A" to ACConfirmationPlan.Fit("fit0", "changed"))).stage)
        val retry = first.bind(0, "retry", "newfp", reports)
        assertEquals(ACConfirmationPlan.Stage.VALIDATION, retry.action(reports, live(retry)).stage)
    }

    @Test fun fractionalElapsedBoundaryDoesNotTruncateIntoACompletedBlock() {
        val current = bound(plan())
        val validation = report(current, 0, ExperimentFlow.VALIDATION)
        validation.getJSONObject("result").put("endMs", 11000.9)
        val earlyReading = report(current, 0, ExperimentFlow.READING)
        earlyReading.getJSONObject("result").put("startMs", 11000.1)
        assertEquals(ACConfirmationPlan.Stage.READING, current.action(listOf(validation, earlyReading), live(current)).stage)
        earlyReading.getJSONObject("result").put("startMs", 11000.9)
        assertEquals(1, current.action(listOf(validation, earlyReading), emptyMap()).index)
    }

    @Test fun wrongIdentityAndIncompleteAttemptsNeverComplete() {
        val current = bound(plan())
        val validation = report(current, 0, ExperimentFlow.VALIDATION)
        val reading = report(current, 0, ExperimentFlow.READING)
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONObject("metadata").put("sessionId", "other") },
            { it.getJSONObject("metadata").put("conditionId", "C") },
            { it.getJSONObject("metadata").put("collectionId", "other") },
            { it.getJSONObject("metadata").put("calibrationFingerprint", "other") },
            { it.getJSONObject("metadata").getJSONObject("sourceMetadata").put("confirmation_step_id", current.steps[1].id) },
            { it.getJSONObject("metadata").getJSONObject("sourceMetadata").put("confirmation_round", "2") },
            { it.getJSONObject("metadata").getJSONObject("sourceMetadata").put("confirmation_step_index", "1") },
            { it.getJSONObject("metadata").getJSONObject("sourceMetadata").put("confirmation_plan", "other") },
            { it.getJSONObject("metadata").put("outcome", "aborted") },
            { it.getJSONObject("result").put("rowsTruncated", true) },
            { it.getJSONObject("result").put("validUniqueFreshCount", 0) },
            { it.getJSONObject("result").getJSONObject("summary").put("missingHeldOutTargetIds", JSONArray().put("target")) })
        for (mutation in mutations) {
            val bad = JSONObject(validation.toString()).also(mutation)
            assertEquals(ACConfirmationPlan.Stage.VALIDATION, current.action(listOf(bad, reading), live(current)).stage)
        }
        assertEquals(ACConfirmationPlan.Stage.VALIDATION, current.action(listOf(JSONObject()), live(current)).stage)
    }

    @Test fun readingBeforeValidationCannotCompleteAndInvalidClockCannotComplete() {
        val current = bound(plan())
        val validation = report(current, 0, ExperimentFlow.VALIDATION)
        validation.getJSONObject("result").put("endMs", 11000L)
        val early = report(current, 0, ExperimentFlow.READING).also { it.getJSONObject("result").put("startMs", 10000L) }
        val bad = report(current, 0, ExperimentFlow.READING, "bad").also {
            it.getJSONObject("metadata").getJSONObject("sourceMetadata").put("confirmation_boot_count", "unavailable") }
        val rebooted = report(current, 0, ExperimentFlow.READING).also {
            it.getJSONObject("metadata").getJSONObject("sourceMetadata").put("confirmation_boot_count", "43") }
        for (reading in listOf(early, bad)) {
            assertEquals(ACConfirmationPlan.Stage.READING, current.action(listOf(validation, reading), live(current)).stage)
        }
        assertEquals(ACConfirmationPlan.Stage.NEED_CALIBRATION, current.action(listOf(validation, rebooted), live(current)).stage)
        val fallback = report(current, 0, ExperimentFlow.READING).also {
            it.getJSONObject("metadata").getJSONObject("sourceMetadata").put("confirmation_boot_count", "unavailable") }
        assertEquals(1, current.action(listOf(validation, fallback), emptyMap()).index)
        assertEquals(1, current.action(pair(current, 0), emptyMap()).index)
    }

    @Test fun serializationIsStrictAndPreservesRetryReservationsAndOptionalSelfReport() {
        val current = bound(plan()).bind(0, "retry", "newfp", emptyList())
        val restored = ACConfirmationPlan.fromJson(JSONObject(current.toJson().toString()))
        assertEquals(current.toJson().toString(), restored.toJson().toString())
        rejects { restored.bind(0, "fit0", "fp0", emptyList()) }
        assertNull(ACConfirmationPlan.fromJson(ACConfirmationPlan.create(setupNotes = "").toJson()).viewingDistanceCm)
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("version", "future") }, { it.put("extra", true) }, { it.remove("setupNotes") },
            { it.put("viewingDistanceCm", "40") }, { it.put("viewingDistanceCm", -1) },
            { it.put("sessionId", "bad") }, { it.put("setupNotes", "x".repeat(1001)) },
            { it.getJSONArray("steps").getJSONObject(1).put("conditionId", "A") },
            { it.getJSONArray("steps").getJSONObject(0).put("index", "0") },
            { it.getJSONArray("steps").getJSONObject(0).put("entered", "true") },
            { it.getJSONArray("steps").getJSONObject(1).put("id", current.steps[0].id) },
            { it.put("usedCollections", JSONArray()) })
        mutations.forEach { mutation -> rejects { ACConfirmationPlan.fromJson(JSONObject(current.toJson().toString()).also(mutation)) } }
        rejects { ACConfirmationPlan.create(setupNotes = "", viewingDistanceCm = Double.NaN) }
        rejects { ACConfirmationPlan.create(setupNotes = "", viewingDistanceCm = 0.0) }
    }
}
