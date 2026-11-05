package com.newsmead.gaze.experiment

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class ExperimentReportStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val directory get() = temporary.root
    private fun metadata(session: String = "session", calibration: String = "calibration", outcome: String = "completed") =
        RunMetadata(session, "collection", "A", "model", calibration, "protocol", "raw", "2026-11-05T00:00:00Z", outcome,
            mapOf("widthPx" to 1080.0), mapOf("runtime" to "MNN"), endedAtUtc = "2026-11-05T00:01:00Z")
    private fun result(withSample: Boolean = true): ExperimentResult {
        val m = ExperimentMeasurements(0.0, "validation", expectedHeldOutTargetIds = setOf("held", "missing"))
        m.registerTarget(TargetWindow("held", 100.0, 200.0, 0.0, 100.0, 120.0, true))
        if (withSample) m.observe(ExperimentObservation(10.0, 20.0, "coordinate", 103.0, 204.0, 106.0, 208.0))
        return m.finish(150.0)
    }
    private fun change(id: String, edit: (JSONObject) -> Unit) {
        val file = File(directory, "$id.json")
        val report = JSONObject(file.readText())
        edit(report)
        file.writeText(report.toString())
    }

    @Test fun roundTripPreservesMetadataCoordinateSeparationAndNullableMetrics() {
        val store = ExperimentReportStore(directory)
        val id = store.save(metadata(), result())
        val report = store.read(id)!!
        assertEquals(id, report.getString("reportId"))
        val metadata = report.getJSONObject("metadata")
        assertEquals("collection", metadata.getString("collectionId"))
        assertEquals(1080.0, metadata.getJSONObject("geometry").getDouble("widthPx"), 0.0)
        assertEquals("MNN", metadata.getJSONObject("sourceMetadata").getString("runtime"))
        val result = report.getJSONObject("result")
        val row = result.getJSONArray("rows").getJSONObject(0)
        assertEquals(103.0, row.getDouble("rawX"), 0.0)
        assertEquals(106.0, row.getDouble("selectedX"), 0.0)
        assertEquals(10.0, row.getDouble("captureMs"), 0.0)
        assertEquals(20.0, row.getDouble("deliveryMs"), 0.0)
        val raw = result.getJSONObject("summary").getJSONObject("rawHeldOut")
        assertEquals(5.0, raw.getDouble("medianErrorPx"), 0.0)
        assertTrue(raw.isNull("spatialSdPx"))
        assertTrue(raw.isNull("rmsSuccessivePx"))
        val emptyId = store.save(metadata(outcome = "aborted"), result(false))
        val empty = store.read(emptyId)!!.getJSONObject("result").getJSONObject("summary")
        assertTrue(empty.isNull("coverage"))
        assertTrue(empty.getJSONObject("rawHeldOut").isNull("medianErrorPx"))
        assertEquals(2, empty.getJSONArray("missingHeldOutTargetIds").length())
    }

    @Test fun repeatedSavesAreDistinctImmutableAndFilterBySessionAndCalibration() {
        val store = ExperimentReportStore(directory)
        val first = store.save(metadata(), result())
        val firstBytes = File(directory, "$first.json").readBytes()
        val second = store.save(metadata(), result())
        store.save(metadata(session = "other"), result())
        store.save(metadata(calibration = "other"), result())
        assertNotEquals(first, second)
        assertArrayEquals(firstBytes, File(directory, "$first.json").readBytes())
        assertEquals(2, store.list("session", "calibration").size)
        assertEquals(3, store.list("session").size)
        assertEquals(4, store.list().size)
        assertEquals(0, store.lastListCorruptCount)
        assertNull(store.read(UUID.randomUUID().toString()))
    }

    @Test fun historySummariesPreserveIdentitiesAndPartialMetricsWithoutRetainingCoordinateRows() {
        val store = ExperimentReportStore(directory)
        val first = store.save(metadata(session = "old", outcome = "aborted"), result())
        val second = store.save(metadata(session = "new", calibration = "fit2"), result(false))
        val summaries = store.list(summariesOnly = true)
        assertEquals(setOf(first, second), summaries.map { it.getString("reportId") }.toSet())
        val old = summaries.single { it.getString("reportId") == first }
        assertEquals("old", old.getJSONObject("metadata").getString("sessionId"))
        assertEquals("aborted", old.getJSONObject("metadata").getString("outcome"))
        assertEquals(5.0, old.getJSONObject("result").getJSONObject("summary").getJSONObject("rawHeldOut").getDouble("medianErrorPx"), 0.0)
        assertFalse(old.getJSONObject("result").has("rows"))
        assertFalse(old.getJSONObject("result").has("targets"))
        assertEquals(1, store.list(sessionId = "old", summariesOnly = true).size)
        assertEquals(1, store.read(first)!!.getJSONObject("result").getJSONArray("rows").length())
        assertEquals(1, store.exportSession("old").getJSONArray("reports").getJSONObject(0)
            .getJSONObject("result").getJSONArray("rows").length())
    }

    @Test fun savedScaleProducesCentimeterHistoryAndExportWhileKeepingPixelMeasurements() {
        val store = ExperimentReportStore(directory)
        val scaled = metadata().copy(geometry = mapOf("screenWidthPx" to 1080.0, "screenHeightPx" to 2340.0,
            "rotation" to 0.0, "pixelsPerCmX" to 3.0, "pixelsPerCmY" to 4.0),
            sourceMetadata = mapOf("physical_scale_source" to "recorded_test_scale"))
        val id = store.save(scaled, result())
        fun error(report: JSONObject) = report.getJSONObject("centimeters").getJSONObject("summary")
            .getJSONObject("rawHeldOut").getDouble("medianErrorCm")
        assertEquals(kotlin.math.sqrt(2.0), error(store.read(id)!!), 1e-12)
        val summary = store.list(summariesOnly = true).single()
        assertEquals(kotlin.math.sqrt(2.0), error(summary), 1e-12)
        assertEquals("recorded_test_scale", summary.getJSONObject("centimeters").getString("scaleSource"))
        val exported = store.exportSession("session").getJSONArray("reports").getJSONObject(0)
        assertEquals(kotlin.math.sqrt(2.0), error(exported), 1e-12)
        assertEquals(5.0, exported.getJSONObject("result").getJSONObject("summary").getJSONObject("rawHeldOut").getDouble("medianErrorPx"), 0.0)
        assertEquals(103.0, exported.getJSONObject("result").getJSONArray("rows").getJSONObject(0).getDouble("rawX"), 0.0)
        change(id) { it.getJSONObject("centimeters").getJSONObject("summary").getJSONObject("rawHeldOut").put("medianErrorCm", 99.0) }
        assertEquals(kotlin.math.sqrt(2.0), error(store.read(id)!!), 1e-12)
    }

    @Test fun legacyConversionIsGeometryBoundAndDoesNotRewriteSavedPixelReport() {
        val store = ExperimentReportStore(directory)
        val id = store.save(metadata().copy(geometry = mapOf("screenWidthPx" to 1080.0, "screenHeightPx" to 2340.0, "rotation" to 0.0)), result())
        val file = File(directory, "$id.json")
        val original = file.readBytes()
        assertTrue(store.read(id)!!.isNull("centimeters"))
        val scale = PhysicalDisplayScale(3.0, 4.0, 1080, 2340, 0, "current_display")
        val summary = store.list(summariesOnly = true, centimeterFallback = scale).single()
        assertEquals("current_device_display_metrics_for_legacy_local_report", summary.getJSONObject("centimeters").getString("scaleSource"))
        assertEquals("cm", store.exportSession("session", scale).getString("displaySpatialUnits"))
        assertTrue(store.list(summariesOnly = true, centimeterFallback = scale.copy(screenWidthPx = 720)).single().isNull("centimeters"))
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun readingReportsKeepAbsentAccuracyAndOriginalTerminalClock() {
        val store = ExperimentReportStore(directory)
        val m = ExperimentMeasurements(0.0, "reading")
        m.observe(ExperimentObservation(10.0, 20.0, "coordinate", 1.0, 2.0))
        m.observe(ExperimentObservation(null, 1000.0, "stopped", arrivals = 7, busyDrops = 2))
        val id = store.save(metadata(), m.finish(100.0))
        val result = store.read(id)!!.getJSONObject("result")
        assertEquals(100.0, result.getDouble("endMs"), 0.0)
        assertEquals(1000.0, result.getJSONArray("rows").getJSONObject(1).getDouble("deliveryMs"), 0.0)
        assertEquals(7L, result.getLong("arrivalsTotal"))
        assertTrue(result.getJSONObject("summary").isNull("rawHeldOut"))
        assertTrue(result.getJSONObject("summary").isNull("selectedHeldOut"))
    }

    @Test fun preEndTimestampReportsRemainReadableAndExportable() {
        val store = ExperimentReportStore(directory)
        val id = store.save(metadata(), result())
        change(id) { it.getJSONObject("metadata").remove("endedAtUtc") }
        assertNotNull(store.read(id))
        assertEquals(1, store.list().size)
        assertEquals(0, store.lastListCorruptCount)
        assertEquals(1, store.exportSession("session").getJSONArray("reports").length())
        change(id) { it.getJSONObject("metadata").put("endedAtUtc", 42) }
        assertTrue(store.list().isEmpty())
        assertEquals(1, store.lastListCorruptCount)
    }

    @Test fun malformedSemanticReportsAreCountedBeforeRendering() {
        val store = ExperimentReportStore(directory)
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONObject("metadata").remove("conditionId") },
            { it.getJSONObject("metadata").remove("collectionId") },
            { it.getJSONObject("result").remove("mode") },
            { it.getJSONObject("result").put("rowsTruncated", "false") },
            { it.getJSONObject("result").put("validUniqueFreshCount", "1") },
            { it.getJSONObject("result").getJSONObject("summary").remove("missingHeldOutTargetIds") },
            { it.getJSONObject("result").getJSONObject("summary").put("rawHeldOut", "broken") },
            { it.getJSONObject("result").getJSONObject("summary").getJSONObject("rawHeldOut").remove("medianErrorPx") },
        )
        mutations.forEach { mutation ->
            val id = store.save(metadata(), result())
            change(id, mutation)
        }
        store.save(metadata(), result())
        assertEquals(1, store.list().size)
        assertEquals(mutations.size, store.lastListCorruptCount)
        assertFalse(store.lastListTruncated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun explicitReadRejectsMalformedNumericTypes() {
        val store = ExperimentReportStore(directory)
        val id = store.save(metadata(), result())
        change(id) { it.getJSONObject("result").getJSONObject("summary").put("coverage", "0.5") }
        store.read(id)
    }

    @Test fun combinedExportKeepsAllOutcomesAndExposesCorruption() {
        val store = ExperimentReportStore(directory)
        listOf("completed", "aborted", "failure").forEach { store.save(metadata(outcome = it), result()) }
        store.save(metadata(session = "other"), result())
        val corrupt = store.save(metadata(), result())
        change(corrupt) { it.remove("result") }
        val export = store.exportSession("session")
        assertEquals("session", export.getString("sessionId"))
        assertEquals(3, export.getJSONArray("reports").length())
        assertEquals(1, export.getInt("corruptReportCount"))
        assertFalse(export.getBoolean("listingTruncated"))
    }

    @Test fun fileCountBoundIsVisibleToListingAndCombinedExport() {
        val store = ExperimentReportStore(directory)
        val first = store.save(metadata(), result(false))
        val template = File(directory, "$first.json").readText()
        repeat(ExperimentReportStore.MAX_LIST_REPORTS) {
            val id = UUID.randomUUID().toString()
            File(directory, "$id.json").writeText(JSONObject(template).put("reportId", id).toString())
        }
        assertEquals(ExperimentReportStore.MAX_LIST_REPORTS, store.list().size)
        assertTrue(store.lastListTruncated)
        assertEquals(0, store.lastListCorruptCount)
        val export = store.exportSession("session")
        assertEquals(ExperimentReportStore.MAX_LIST_REPORTS, export.getJSONArray("reports").length())
        assertTrue(export.getBoolean("listingTruncated"))
    }
}
