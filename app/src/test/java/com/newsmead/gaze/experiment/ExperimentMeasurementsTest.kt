package com.newsmead.gaze.experiment

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class ExperimentMeasurementsTest {
    private fun target(id: String = "held", start: Double = 0.0, end: Double = 1000.0, held: Boolean = true) =
        TargetWindow(id, 0.0, 0.0, start, end, end + 100.0, held)
    private fun sample(t: Double, x: Double = 0.0, y: Double = 0.0, delivery: Double = t + 10) =
        ExperimentObservation(t, delivery, "coordinate", x, y, x * 2, y * 2)

    @Test fun sampleErrorsAreNotErrorOfMedianCoordinates() {
        val m = ExperimentMeasurements(0.0, "validation")
        m.registerTarget(target())
        m.observe(sample(10.0, -3.0, -4.0)); m.observe(sample(20.0, 3.0, 4.0))
        val r = m.finish(100.0)
        val raw = r.summary.rawHeldOut!!
        assertEquals(2, raw.n)
        assertEquals(5.0, raw.medianErrorPx!!, 0.0)
        assertEquals(5.0, raw.p95ErrorPx!!, 0.0)
        assertEquals(0.0, raw.dxBiasPx!!, 0.0)
        assertEquals(sqrt(50.0), raw.spatialSdPx!!, 1e-10)
        assertEquals(10.0, raw.rmsSuccessivePx!!, 0.0)
        assertEquals(10.0, r.summary.selectedHeldOut!!.medianErrorPx!!, 0.0)
    }

    @Test fun duplicateAndMissingnessBreakSuccessivePairsAndDenominatorExcludesWatchdogAndStop() {
        val m = ExperimentMeasurements(0.0, "validation")
        m.registerTarget(target())
        m.observe(sample(10.0)); m.observe(sample(10.0, delivery = 25.0))
        m.observe(sample(30.0, 5.0)); m.observe(ExperimentObservation(40.0, 50.0, "no_face"))
        m.observe(sample(60.0, 10.0))
        m.observe(ExperimentObservation(60.0, 70.0, "stale", watchdog = true))
        m.observe(ExperimentObservation(null, 80.0, "stopped", arrivals = 20, busyDrops = 7))
        val r = m.finish(100.0)
        assertEquals(5, r.observedDeliveredCount)
        assertEquals(3, r.validUniqueFreshCount)
        assertEquals(.6, r.summary.coverage!!, 0.0)
        assertEquals(0, r.targets.single().raw.successivePairs)
        assertEquals(20L, r.arrivalsTotal)
        assertEquals(7L, r.busyDropsTotal)
        assertEquals(1, r.invalidReasons["duplicate_or_regressed_capture"])
    }

    @Test fun centerRepeatAndAbsentTargetsNeverBecomeHeldOutSuccess() {
        val m = ExperimentMeasurements(0.0, "validation")
        m.registerTarget(target("held", 0.0, 100.0))
        m.registerTarget(target("missing", 100.0, 200.0))
        m.registerTarget(target("center", 200.0, 300.0, false))
        m.observe(sample(10.0, 3.0, 4.0)); m.observe(sample(210.0, 90.0, 0.0))
        val r = m.finish(310.0)
        assertEquals(5.0, r.summary.rawHeldOut!!.medianErrorPx!!, 0.0)
        assertEquals(listOf("missing"), r.summary.missingHeldOutTargetIds)
        assertEquals(0, r.targets[1].raw.n)
        assertNull(r.targets[1].raw.medianErrorPx)
        assertEquals(90.0, r.targets[2].raw.medianErrorPx!!, 0.0)
    }

    @Test fun targetMembershipUsesCaptureBoundsAndDeliveryDeadline() {
        val m = ExperimentMeasurements(0.0, "validation")
        m.registerTarget(TargetWindow("a", 0.0, 0.0, 10.0, 100.0, 120.0, true))
        m.observe(sample(10.0, delivery = 20.0))
        m.observe(sample(99.0, delivery = 121.0))
        m.observe(sample(100.0, delivery = 122.0))
        val r = m.finish(130.0)
        assertEquals(3, r.validUniqueFreshCount)
        assertEquals(1, r.targets.single().raw.n)
    }

    @Test fun rawAndSelectedMissingnessAreSeparate() {
        val m = ExperimentMeasurements(0.0, "validation")
        m.registerTarget(target())
        m.observe(sample(10.0).copy(selectedX = Double.NaN))
        val r = m.finish(30.0)
        assertEquals(1, r.targets.single().raw.n)
        assertEquals(0, r.targets.single().selected.n)
        assertNull(r.rows.single().observation.selectedX)
    }

    @Test fun malformedClocksAndCountersDoNotPoisonSubsequentSamples() {
        val m = ExperimentMeasurements(0.0, "reading")
        m.observe(sample(10.0).copy(arrivals = 5, busyDrops = 2))
        m.observe(sample(20.0).copy(deliveryMs = Double.NaN))
        m.observe(sample(30.0).copy(arrivals = 3, busyDrops = -1))
        m.observe(sample(40.0).copy(rawX = Double.POSITIVE_INFINITY))
        val r = m.finish(60.0)
        assertEquals(2, r.validUniqueFreshCount)
        assertEquals(4, r.observedDeliveredCount)
        assertEquals(1, r.rejectedClockCount)
        assertEquals(2, r.counterRegressions)
        assertEquals(5L, r.arrivalsTotal)
        assertEquals(2L, r.busyDropsTotal)
        assertNull(r.summary.rawHeldOut)
        assertNull(r.medianIntersampleMs)
    }

    @Test fun staleAndSilentExpiryProduceCensoredGaps() {
        val m = ExperimentMeasurements(0.0, "reading")
        m.observe(sample(10.0))
        m.observe(sample(600.0, delivery = 610.0))
        val r = m.finish(1200.0)
        assertEquals(3, r.gaps.size)
        assertTrue(r.gaps.first().leftCensored)
        assertEquals(510.0, r.gaps[1].startMs, 0.0)
        assertEquals(100.0, r.gaps[1].durationMs, 0.0)
        assertTrue(r.gaps.last().rightCensored)
        assertNull(r.medianIntersampleMs)
    }

    @Test fun largeTargetOffsetsDoNotInflatePooledWithinTargetJitter() {
        val m = ExperimentMeasurements(0.0, "validation")
        m.registerTarget(target("a", 0.0, 100.0))
        m.registerTarget(TargetWindow("b", 1000.0, 1000.0, 100.0, 200.0, 250.0, true))
        m.observe(sample(10.0, -1.0)); m.observe(sample(20.0, 1.0))
        m.observe(sample(110.0, 999.0, 1000.0)); m.observe(sample(120.0, 1001.0, 1000.0))
        val r = m.finish(250.0)
        assertEquals(sqrt(2.0), r.summary.rawHeldOut!!.spatialSdPx!!, 1e-10)
        assertEquals(2, r.summary.rawHeldOut!!.successivePairs)
    }

    @Test fun retentionCapIsExplicitAndMetricsDescribeRetainedRows() {
        val m = ExperimentMeasurements(0.0, "reading", maxRows = 2)
        m.observe(sample(10.0)); m.observe(sample(20.0)); m.observe(sample(30.0))
        m.observe(ExperimentObservation(null, 1000.0, "stopped", arrivals = 99, busyDrops = 7))
        val r = m.finish(100.0)
        assertTrue(r.rowsTruncated)
        assertEquals(2, r.omittedObservationCount)
        assertEquals(2, r.validUniqueFreshCount)
        assertEquals(99L, r.arrivalsTotal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlappingWindowsAreRejected() {
        val m = ExperimentMeasurements(0.0, "validation")
        m.registerTarget(target()); m.registerTarget(target("overlap"))
    }

    @Test fun exactExpiryBoundaryRemainsEligibleButBeyondItDoesNot() {
        val m = ExperimentMeasurements(0.0, "reading")
        m.observe(sample(10.0, delivery = 510.0))
        m.observe(sample(11.0, delivery = 512.0))
        val r = m.finish(520.0)
        assertEquals(1, r.validUniqueFreshCount)
        assertEquals(1, r.invalidReasons["stale"])
        assertEquals(501.0, r.p95AgeMs!!, 0.0)
    }

    @Test fun terminalShutdownCountersDoNotExtendMeasuredDurationOrGaps() {
        val m = ExperimentMeasurements(0.0, "reading")
        m.observe(sample(10.0, delivery = 10.0))
        m.observe(ExperimentObservation(null, 2000.0, "stopped", arrivals = 50, busyDrops = 4))
        val r = m.finish(1000.0)
        assertEquals(1.0, r.summary.freshHz!!, 0.0)
        assertEquals(1000.0, r.endMs, 0.0)
        assertEquals(50L, r.arrivalsTotal)
        assertEquals(2000.0, r.rows.last().observation.deliveryMs, 0.0)
        assertEquals(1000.0, r.gaps.last().endMs, 0.0)
        assertEquals(1, r.observedDeliveredCount)
    }

    @Test fun abortedBeforeUnregisteredTargetsStillListsExpectedMissingTargets() {
        val m = ExperimentMeasurements(0.0, "validation", expectedHeldOutTargetIds = setOf("0", "1", "2"))
        m.registerTarget(target("0"))
        m.observe(sample(10.0))
        assertEquals(listOf("1", "2"), m.finish(100.0).summary.missingHeldOutTargetIds)
    }
}
