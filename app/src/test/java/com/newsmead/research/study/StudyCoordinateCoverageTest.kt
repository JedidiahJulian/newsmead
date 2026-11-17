package com.newsmead.research.study

import com.newsmead.gaze.trajectory.CoordinateMahalanobis as Core
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StudyCoordinateCoverageTest {
    private val context = Core.Context("p", "i", "c", "m", "a", "presentation", "elapsed", 1080, 2400)
    private fun row(id: Long, t: Double) = Core.Observation(context, "provider", "context", id, t, t + 10,
        100.0 + id % 3, 200.0 + id % 2)
    private fun payload(o: Core.Observation): JSONObject {
        val c = o.context
        val ctx = JSONObject().put("participant_id", c.participantId).put("installation_id", c.installationId)
            .put("calibration_id", c.calibrationId).put("model_id", c.modelId).put("acquisition_id", c.acquisitionId)
            .put("presentation_id", c.presentationId).put("clock_domain_id", c.clockDomainId)
            .put("screen_width_px", c.screenWidthPx).put("screen_height_px", c.screenHeightPx)
            .put("rotation_degrees", c.rotationDegrees).put("coordinate_stream", c.coordinateStream)
        return JSONObject().put("coordinate_context", ctx).put("provider_epoch_id", o.providerEpochId)
            .put("context_epoch_id", o.contextEpochId).put("delivery_id", o.deliveryId)
            .put("capture_ms", o.captureMs ?: JSONObject.NULL).put("delivery_ms", o.deliveryMs)
            .put("raw_x_px", o.xPx ?: JSONObject.NULL).put("raw_y_px", o.yPx ?: JSONObject.NULL)
            .put("selected_x_px", 999.0).put("selected_y_px", 999.0)
            .put("reason", o.reason).put("timeout_watchdog", o.watchdog).put("context_allowed", o.contextAllowed)
    }
    private fun preview(rows: List<Core.Observation>, end: Double,
        cfg: Core.Config = Core.Config(), boundaries: List<Core.Boundary> = emptyList()): StudyCoordinateCoverage.Snapshot {
        val monitor = StudyCoordinateCoverage(0.0, cfg)
        rows.forEach { monitor.observation(payload(it)) }
        boundaries.forEach { monitor.boundary(it.atMs, it.reason) }
        return monitor.snapshot(end)
    }
    private fun assertParity(rows: List<Core.Observation>, end: Double, boundaries: List<Core.Boundary> = emptyList()) {
        val cfg = Core.Config(minReferenceCoverageMs = 0.0)
        val fit = Core.fitReference("ref", context, rows, 0.0, end, cfg, boundaries)
        val snapshot = preview(rows, end, cfg, boundaries)
        assertNull(snapshot.issue)
        assertEquals(fit.stats.coveredMs, snapshot.coveredMs, 0.0)
        assertEquals(fit.stats.weightedPointCount, snapshot.weightedPointCount)
    }

    @Test fun elapsedRdfTimeCannotStandInForCoordinateCoverage() {
        val rows = (0..576).map { row(it + 1L, it * 100.0) } + row(578, 57624.855821)
        val snapshot = preview(rows, 66000.0)
        assertEquals(57624.855821, snapshot.coveredMs, 1e-8)
        assertEquals(60000.0, snapshot.requiredMs, 0.0)
        assertFalse(snapshot.minimumDataReady)
    }

    @Test fun exactThresholdAndJustBelowUseUnroundedCoverage() {
        val rows = (0..600).map { row(it + 1L, it * 100.0) }
        assertTrue(preview(rows, 66000.0).minimumDataReady)
        val below = rows.dropLast(1) + rows.last().copy(captureMs = 59999.999, deliveryMs = 60010.0)
        assertFalse(preview(below, 66000.0).minimumDataReady)
    }

    @Test fun validatorAndAdjacencyFailuresAgreeWithFitStats() {
        val base = (0..8).map { row(it + 1L, it * 100.0) }
        val replacements = listOf(
            base[3].copy(captureMs = null), base[3].copy(xPx = null), base[3].copy(yPx = null),
            base[3].copy(watchdog = true), base[3].copy(reason = "blink"),
            base[3].copy(deliveryMs = 801.0), base[3].copy(deliveryMs = 299.0),
            base[3].copy(captureMs = 200.0), base[3].copy(deliveryId = 3),
            base[3].copy(contextAllowed = false), base[3].copy(providerEpochId = "new-provider"),
            base[3].copy(contextEpochId = "new-context"))
        replacements.forEach { replacement -> assertParity(base.toMutableList().also { it[3] = replacement }, 1000.0) }
        assertParity(base, 1000.0, listOf(Core.Boundary(250.0, "transition")))
        assertParity(listOf(row(1, 0.0), row(2, 250.0), row(3, 500.001), row(4, 700.0)), 1000.0)
        assertParity(base.toMutableList().also { it[3] = it[3].copy(deliveryMs = 800.0) }, 1000.0)
    }

    @Test fun boundsFilterBeforeContextChecksAndValidation() {
        val monitor = StudyCoordinateCoverage(100.0, Core.Config(minReferenceCoverageMs = 100.0))
        monitor.observation(payload(row(1, 0.0)).put("coordinate_context", JSONObject.NULL))
        monitor.observation(payload(row(2, 100.0)))
        monitor.observation(payload(row(3, 200.0)))
        monitor.observation(payload(row(4, 300.0)))
        monitor.observation(payload(row(5, 400.0)).put("coordinate_context", JSONObject.NULL))
        val snapshot = monitor.snapshot(300.0)
        assertNull(snapshot.issue)
        assertEquals(200.0, snapshot.coveredMs, 0.0)
        assertTrue(snapshot.minimumDataReady)
        assertEquals("context_unavailable", monitor.snapshot(500.0).issue)
    }

    @Test fun unavailableAndChangedContextAreExplicitAndAttemptsAreIndependent() {
        val monitor = StudyCoordinateCoverage(0.0)
        monitor.observation(payload(row(1, 100.0)).put("coordinate_context", JSONObject.NULL))
        assertEquals("context_unavailable", monitor.snapshot(1000.0).issue)
        val changed = preview(listOf(row(1, 0.0), row(2, 100.0).copy(context = context.copy(rotationDegrees = 90))), 1000.0)
        assertEquals("multiple_reference_contexts", changed.issue)
        assertFalse(changed.minimumDataReady)
        val fresh = StudyCoordinateCoverage(0.0)
        assertEquals(0.0, fresh.snapshot(1000.0).coveredMs, 0.0)
        fresh.observation(payload(row(1, 0.0)))
        fresh.observation(payload(row(2, 100.0)))
        fresh.observation(payload(row(3, 200.0)))
        assertNull(fresh.snapshot(1000.0).issue)
        assertEquals(200.0, fresh.snapshot(1000.0).coveredMs, 0.0)
    }

    @Test fun unplaceableClockIsRetainedAsAdjacencyBreak() {
        val monitor = StudyCoordinateCoverage(0.0)
        monitor.observation(payload(row(1, 0.0)))
        monitor.observation(payload(row(2, 100.0)).put("capture_ms", JSONObject.NULL).put("delivery_ms", JSONObject.NULL))
        monitor.observation(payload(row(3, 200.0)))
        assertEquals(0.0, monitor.snapshot(300.0).coveredMs, 0.0)
    }

    @Test fun malformedPayloadAndResourceLimitsOnlyDisablePreview() {
        val monitor = StudyCoordinateCoverage(0.0)
        monitor.observation(JSONObject().put("coordinate_context", JSONObject().put("participant_id", "p")))
        assertNotNull(monitor.snapshot(1000.0).issue)
        val capped = StudyCoordinateCoverage(0.0)
        repeat(4097) { capped.boundary(it.toDouble(), "test") }
        assertNotNull(capped.snapshot(5000.0).issue)
        assertFalse(capped.snapshot(5000.0).minimumDataReady)
    }

    @Test fun writerOverflowPermanentlyDisablesOtherwiseReadyPreviewWithoutWriterFailure() {
        for (rejectedKind in listOf("rejected", "rdf_baseline_fit", "shared_baseline_result")) {
        val monitor = StudyCoordinateCoverage(0.0, Core.Config(minReferenceCoverageMs = 200.0))
        (0..2).forEach { monitor.observation(payload(row(it + 1L, it * 100.0))) }
        assertTrue(monitor.snapshot(1000.0).minimumDataReady)
        val gate = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val identity = JSONObject().apply {
            for (key in listOf("study_id", "participant_id", "installation_id", "run_id", "session_id", "clock_domain_id")) put(key, key)
        }
        val root = Files.createTempDirectory("coordinate-coverage-writer-test").toFile().also { it.deleteOnExit() }
        val writer = StudyPackageWriter(root, identity, { 1.0 }, queueCapacity = 1,
            onState = { if (it == "recording") { entered.countDown(); gate.await(5, TimeUnit.SECONDS) } })
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(writer.record("one", JSONObject(), 10.0))
            val accepted = writer.record(rejectedKind, JSONObject(), 20.0)
            assertFalse("Rejected $rejectedKind must not be published as accepted", accepted)
            assertNull(writer.failure)
            val health = writer.health()
            assertEquals(1L, health.getLong("lost_records"))
            monitor.recordingHealth(health.getLong("lost_records"), writer.failure)
            assertEquals("recording_loss", monitor.snapshot(1000.0).issue)
            assertFalse(monitor.snapshot(1000.0).minimumDataReady)
            // A later healthy report must never clear the attempt's latched loss.
            monitor.recordingHealth(0L, null)
            monitor.observation(payload(row(4, 300.0)))
            assertEquals("recording_loss", monitor.snapshot(1000.0).issue)
            assertFalse(monitor.snapshot(1000.0).minimumDataReady)
        } finally {
            writer.finish("test_done", 1000.0)
            gate.countDown()
            assertTrue(writer.awaitClosed(5000))
        }
        }
    }
}
