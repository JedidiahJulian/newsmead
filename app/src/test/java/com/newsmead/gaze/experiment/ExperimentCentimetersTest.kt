package com.newsmead.gaze.experiment

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class ExperimentCentimetersTest {
    private val scale = PhysicalDisplayScale(3.0, 4.0, 1080, 2400, 0, "android_display_metrics_xdpi_ydpi_v1")
    private fun target(id: String = "a", start: Double = 0.0, end: Double = 1000.0,
        held: Boolean = true, x: Double = 0.0, y: Double = 0.0) =
        TargetWindow(id, x, y, start, end, end + 100, held)
    private fun sample(t: Double, x: Double, y: Double, delivery: Double = t + 1) =
        ExperimentObservation(t, delivery, "coordinate", x, y, x * 2, y * 2)
    private fun summary(value: JSONObject, selected: Boolean = false) = value.getJSONObject("summary")
        .getJSONObject(if (selected) "selectedHeldOut" else "rawHeldOut")
    private fun raw(value: JSONObject, index: Int) = value.getJSONArray("targets").getJSONObject(index).getJSONObject("raw")
    private fun assertMetric(expected: Double, value: JSONObject, key: String) =
        assertEquals(expected, value.getDouble(key), 1e-10)

    @Test fun independentAxesConvertSamplesBeforeErrorAndJitter() {
        val m = ExperimentMeasurements(0.0, "validation"); m.registerTarget(target())
        m.observe(sample(10.0, -3.0, -4.0)); m.observe(sample(20.0, 3.0, 4.0))
        val result = m.finish(30.0)
        val cm = ExperimentCentimeters.fromResult(result, scale)
        val raw = summary(cm)
        assertEquals(2, raw.getInt("n")); assertEquals(1, raw.getInt("successivePairs"))
        assertMetric(sqrt(2.0), raw, "medianErrorCm"); assertMetric(sqrt(2.0), raw, "p95ErrorCm")
        assertMetric(2.0, raw, "spatialSdCm"); assertMetric(sqrt(8.0), raw, "rmsSuccessiveCm")
        assertMetric(0.0, raw, "dxBiasCm"); assertMetric(0.0, raw, "dyBiasCm")
        assertMetric(sqrt(8.0), summary(cm, true), "medianErrorCm")
        assertEquals(5.0, result.summary.rawHeldOut!!.medianErrorPx!!, 0.0)
    }

    @Test fun anisotropyChangesRadialOrderAndKeepsMedianAndNearestRankConventions() {
        val m = ExperimentMeasurements(0.0, "validation"); m.registerTarget(target())
        listOf(3.0 to 0.0, 0.0 to 4.0, 0.0 to 6.0, 7.0 to 0.0).forEachIndexed { i, p ->
            m.observe(sample(10.0 + i * 10, p.first, p.second))
        }
        val value = summary(ExperimentCentimeters.fromResult(m.finish(50.0),
            scale.copy(pixelsPerCmX = 1.0, pixelsPerCmY = 10.0)))
        // Pixel radii 3,4,6,7 become cm radii 3,.4,.6,7.
        assertMetric(1.8, value, "medianErrorCm"); assertMetric(7.0, value, "p95ErrorCm")
    }

    @Test fun pooledWithinTargetDegreesExcludeCenterOffsetsAndPreserveMissingTargets() {
        val m = ExperimentMeasurements(0.0, "validation")
        m.registerTarget(target("a", 0.0, 100.0)); m.registerTarget(target("b", 100.0, 200.0, x = 3000.0))
        m.registerTarget(target("missing", 200.0, 300.0)); m.registerTarget(target("center", 300.0, 400.0, false))
        m.observe(sample(10.0, -3.0, 0.0)); m.observe(sample(20.0, 3.0, 0.0))
        m.observe(sample(110.0, 2997.0, 0.0)); m.observe(sample(120.0, 3003.0, 0.0))
        m.observe(sample(310.0, 900.0, 0.0))
        val cm = ExperimentCentimeters.fromResult(m.finish(420.0), scale)
        assertEquals(4, summary(cm).getInt("n")); assertEquals(2, summary(cm).getInt("successivePairs"))
        assertMetric(sqrt(2.0), summary(cm), "spatialSdCm"); assertMetric(2.0, summary(cm), "rmsSuccessiveCm")
        assertMetric(sqrt(2.0), raw(cm, 1), "spatialSdCm")
        assertEquals(0, raw(cm, 2).getInt("n")); assertTrue(raw(cm, 2).isNull("medianErrorCm"))
        assertMetric(300.0, raw(cm, 3), "medianErrorCm")
    }

    @Test fun invalidRowsMissingSelectedAndExpiryBreakPairsWithoutDroppingAcceptedRaw() {
        val m = ExperimentMeasurements(0.0, "validation"); m.registerTarget(target(end = 2000.0))
        m.observe(sample(10.0, 0.0, 0.0)); m.observe(sample(20.0, 3.0, 0.0).copy(selectedY = null))
        m.observe(sample(30.0, 6.0, 0.0)); m.observe(ExperimentObservation(40.0, 41.0, "no_face"))
        m.observe(sample(50.0, 9.0, 0.0)); m.observe(sample(550.0, 12.0, 0.0, 550.0))
        m.observe(sample(1050.0, 15.0, 0.0, 1051.0))
        val r = m.finish(1100.0); val cm = ExperimentCentimeters.fromResult(r, scale)
        assertEquals(r.summary.rawHeldOut!!.n, summary(cm).getInt("n"))
        assertEquals(3, summary(cm).getInt("successivePairs")) // 10->20,20->30,50->550 (inclusive)
        assertEquals(5, summary(cm, true).getInt("n")); assertEquals(1, summary(cm, true).getInt("successivePairs"))
        assertMetric(1.0, summary(cm), "rmsSuccessiveCm")
    }

    @Test fun reportAndResultAgreeWithoutMutationAndRecordedScaleWins() {
        val m = ExperimentMeasurements(0.0, "validation"); m.registerTarget(target())
        m.observe(sample(10.0, 3.0, 4.0)); m.observe(sample(20.0, 6.0, 8.0))
        val result = m.finish(30.0); val report = report(result, true); val before = report.toString()
        val actual = ExperimentCentimeters.fromReport(report, scale.copy(pixelsPerCmX = 100.0))!!
        assertEquals(ExperimentCentimeters.fromResult(result, scale).toString(), actual.toString())
        assertEquals(before, report.toString()); assertEquals(scale.source, actual.getString("scaleSource"))
    }

    @Test fun legacyFallbackRequiresExactGeometryAndLabelsItsOrigin() {
        val m = ExperimentMeasurements(0.0, "reading"); m.observe(sample(10.0, 3.0, 4.0))
        val report = report(m.finish(30.0), false)
        assertNull(ExperimentCentimeters.fromReport(report))
        assertNull(ExperimentCentimeters.fromReport(report, scale.copy(screenWidthPx = 1079)))
        assertNull(ExperimentCentimeters.fromReport(report, scale.copy(screenHeightPx = 2399)))
        assertNull(ExperimentCentimeters.fromReport(report, scale.copy(rotation = 1)))
        val cm = ExperimentCentimeters.fromReport(report, scale)!!
        assertEquals("current_device_display_metrics_for_legacy_local_report", cm.getString("scaleSource"))
        assertTrue(cm.getJSONObject("summary").isNull("rawHeldOut"))
        assertTrue(cm.getJSONObject("summary").isNull("selectedHeldOut"))
    }

    @Test fun partialInvalidAndOverflowingRecordedScalesCannotUseFallback() {
        val m = ExperimentMeasurements(0.0, "validation"); m.registerTarget(target())
        m.observe(sample(10.0, 3.0, 4.0)); val r = m.finish(30.0)
        val partial = report(r, false); partial.getJSONObject("metadata").getJSONObject("geometry").put("pixelsPerCmX", 3.0)
        assertNull(ExperimentCentimeters.fromReport(partial, scale))
        for (invalid in listOf(0.0, -1.0, "3", JSONObject.NULL, Double.MIN_VALUE)) {
            val report = report(r, true)
            report.getJSONObject("metadata").getJSONObject("geometry").put("pixelsPerCmX", invalid)
            assertNull(ExperimentCentimeters.fromReport(report, scale))
        }
    }

    @Test(expected = IllegalArgumentException::class) fun scaleRejectsNonfiniteAxes() {
        scale.copy(pixelsPerCmY = Double.NaN)
    }

    /** Minimal retained-field projection: converter's caller owns full schema validation. */
    private fun report(result: ExperimentResult, recorded: Boolean): JSONObject {
        val geometry = JSONObject().put("screenWidthPx", 1080).put("screenHeightPx", 2400).put("rotation", 0)
        val source = JSONObject()
        if (recorded) {
            geometry.put("pixelsPerCmX", scale.pixelsPerCmX).put("pixelsPerCmY", scale.pixelsPerCmY)
            source.put("physical_scale_source", scale.source)
        }
        return JSONObject().put("metadata", JSONObject().put("geometry", geometry).put("sourceMetadata", source))
            .put("result", JSONObject().put("mode", result.mode).put("maxFreshAgeMs", result.maxFreshAgeMs)
                .put("targets", JSONArray(result.targets.map { t -> val w = t.window
                    JSONObject().put("id", w.id).put("xPx", w.xPx).put("yPx", w.yPx).put("heldOut", w.heldOut)
                        .put("startInclusiveCaptureMs", w.startInclusiveCaptureMs)
                        .put("endExclusiveCaptureMs", w.endExclusiveCaptureMs).put("deliveryDeadlineMs", w.deliveryDeadlineMs)
                })).put("rows", JSONArray(result.rows.map { r -> val o = r.observation
                    JSONObject().put("accepted", r.accepted).put("targetId", r.targetId ?: JSONObject.NULL)
                        .put("captureMs", o.captureMs ?: JSONObject.NULL).put("deliveryMs", r.originalDeliveryMs ?: JSONObject.NULL)
                        .put("continuityBoundaryMs", if (r.rejection == "invalid_delivery_clock") o.deliveryMs else JSONObject.NULL)
                        .put("rawX", o.rawX ?: JSONObject.NULL).put("rawY", o.rawY ?: JSONObject.NULL)
                        .put("selectedX", o.selectedX ?: JSONObject.NULL).put("selectedY", o.selectedY ?: JSONObject.NULL)
                })))
    }
}
