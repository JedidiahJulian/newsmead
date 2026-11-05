package com.newsmead.gaze.experiment

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class RunMetadata(
    val sessionId: String, val collectionId: String, val conditionId: String,
    val modelId: String, val calibrationFingerprint: String, val protocolId: String,
    val filterId: String, val startedAtUtc: String, val outcome: String,
    val geometry: Map<String, Double> = emptyMap(),
    val sourceMetadata: Map<String, String> = emptyMap(),
    val failureReason: String? = null,
    val endedAtUtc: String? = null,
)

/** Private experiment files, deliberately separate from global study recording. */
class ExperimentReportStore(private val directory: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "gaze-experiment/runs"))

    var lastListCorruptCount: Int = 0
        private set
    var lastListTruncated: Boolean = false
        private set

    @Synchronized
    fun save(metadata: RunMetadata, result: ExperimentResult): String {
        require(metadata.outcome in setOf("completed", "aborted", "failure"))
        require(listOf(metadata.sessionId, metadata.collectionId, metadata.conditionId, metadata.modelId,
            metadata.calibrationFingerprint, metadata.protocolId, metadata.filterId, metadata.startedAtUtc).all { it.isNotBlank() && it.length <= 2048 })
        require(metadata.geometry.size <= 64 && metadata.geometry.all { it.key.length <= 128 && it.value.isFinite() })
        require(metadata.sourceMetadata.size <= 64 && metadata.sourceMetadata.all { it.key.length <= 128 && it.value.length <= 4096 })
        require(metadata.failureReason == null || metadata.failureReason.length <= 4096)
        require(metadata.endedAtUtc == null || metadata.endedAtUtc.length <= 128)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create experiment report directory" }
        val id = UUID.randomUUID().toString()
        val report = JSONObject().put("schemaVersion", 1).put("reportId", id)
            .put("metadata", metadata.json()).put("result", result.json())
        validateReport(report, id)
        report.nullable("centimeters", ExperimentCentimeters.fromReport(report))
        val bytes = report.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_REPORT_BYTES) { "Report exceeds bounded file size" }
        val temporary = File(directory, "$id.tmp")
        val destination = File(directory, "$id.json")
        check(!destination.exists())
        try {
            temporary.outputStream().use { it.write(bytes); it.fd.sync() }
            check(temporary.renameTo(destination)) { "Could not finalize experiment report" }
        } finally { temporary.delete() }
        return id
    }

    /** Throws for malformed existing reports. Missing IDs return null. */
    fun read(id: String, centimeterFallback: PhysicalDisplayScale? = null): JSONObject? {
        require(UUID.fromString(id).toString() == id) { "Invalid report ID" }
        val file = File(directory, "$id.json")
        if (!file.exists()) return null
        require(file.length() in 1..MAX_REPORT_BYTES.toLong()) { "Invalid report size" }
        val report = JSONObject(file.readText(Charsets.UTF_8))
        validateReport(report, id)
        report.nullable("centimeters", ExperimentCentimeters.fromReport(report, centimeterFallback))
        return report
    }

    /** Corruption and resource omissions remain visible through the accompanying properties. */
    @Synchronized
    fun list(sessionId: String? = null, calibrationFingerprint: String? = null,
        summariesOnly: Boolean = false, centimeterFallback: PhysicalDisplayScale? = null): List<JSONObject> {
        lastListCorruptCount = 0
        val files = directory.listFiles { f -> f.isFile && f.extension == "json" }.orEmpty()
            .sortedByDescending { it.lastModified() }
        lastListTruncated = files.size > MAX_LIST_REPORTS
        var bytesRead = 0L
        return files.take(MAX_LIST_REPORTS).mapNotNull { file ->
            if (file.length() > MAX_LIST_BYTES - bytesRead) { lastListTruncated = true; return@mapNotNull null }
            bytesRead += file.length()
            try {
                val report = read(file.nameWithoutExtension, centimeterFallback) ?: return@mapNotNull null
                val metadata = report.getJSONObject("metadata")
                report.takeIf { (sessionId == null || metadata.getString("sessionId") == sessionId) &&
                    (calibrationFingerprint == null || metadata.getString("calibrationFingerprint") == calibrationFingerprint) }
                    ?.let { if (summariesOnly) summaryReport(it) else it }
            } catch (_: Exception) { lastListCorruptCount++; null }
        }
    }

    @Synchronized
    fun exportSession(sessionId: String, centimeterFallback: PhysicalDisplayScale? = null): JSONObject {
        val reports = list(sessionId, centimeterFallback = centimeterFallback)
        return JSONObject().put("schemaVersion", 1).put("sessionId", sessionId)
            .put("displaySpatialUnits", "cm")
            .put("corruptReportCount", lastListCorruptCount).put("listingTruncated", lastListTruncated)
            .put("reports", JSONArray(reports))
    }

    companion object {
        const val MAX_REPORT_BYTES = 48 * 1024 * 1024
        const val MAX_LIST_REPORTS = 200
        const val MAX_LIST_BYTES = 64L * 1024 * 1024
    }
}

/** UI history retains scalar summaries, not every saved coordinate row in memory. */
private fun summaryReport(report: JSONObject): JSONObject {
    val result = report.getJSONObject("result")
    val summary = JSONObject()
    listOf("mode", "summary", "rowsTruncated", "validUniqueFreshCount", "observedDeliveredCount", "startMs", "endMs")
        .forEach { summary.put(it, result.get(it)) }
    return JSONObject().put("schemaVersion", report.get("schemaVersion")).put("reportId", report.get("reportId"))
        .put("metadata", report.getJSONObject("metadata")).put("result", summary)
        .nullable("centimeters", report.optJSONObject("centimeters"))
}

/** Validate types without JSONObject's coercions, before a report reaches render/export callers. */
private fun validateReport(report: JSONObject, id: String) {
    require(report.number("schemaVersion") == 1.0 && report.text("reportId") == id)
    val metadata = report.getJSONObject("metadata")
    listOf("sessionId", "collectionId", "conditionId", "modelId", "calibrationFingerprint", "protocolId",
        "filterId", "startedAtUtc").forEach { metadata.text(it, 2048) }
    require(metadata.text("outcome") in setOf("completed", "aborted", "failure"))
    metadata.nullableText("failureReason", 4096)
    if (metadata.has("endedAtUtc")) metadata.nullableText("endedAtUtc", 128)
    metadata.getJSONObject("geometry").boundedFields(64) { key, value ->
        require(key.length <= 128 && value is Number && value.toDouble().isFinite())
    }
    metadata.getJSONObject("sourceMetadata").boundedFields(64) { key, value ->
        require(key.length <= 128 && value is String && value.length <= 4096)
    }
    val result = report.getJSONObject("result")
    require(result.text("mode") in setOf("validation", "reading"))
    val start = result.number("startMs")!!
    val end = result.number("endMs")!!
    require(start >= 0 && end >= start && result.number("maxFreshAgeMs")!! > 0)
    listOf("clock", "coordinates", "counterScope", "percentiles", "ageScope", "metricsScope").forEach { result.text(it) }
    val observed = result.count("observedDeliveredCount", 100_000)
    val valid = result.count("validUniqueFreshCount", observed)
    result.count("counterRegressions"); result.count("rejectedClockCount")
    val omitted = result.count("omittedObservationCount")
    require(result.flag("rowsTruncated") == (omitted > 0))
    listOf("arrivalsTotal", "busyDropsTotal").forEach { key -> result.number(key, true)?.let { require(it >= 0 && it % 1.0 == 0.0) } }
    listOf("medianIntersampleMs", "p95IntersampleMs", "medianAgeMs", "p95AgeMs").forEach { result.nonnegativeOrNull(it) }
    result.getJSONObject("invalidReasons").boundedFields(100_000) { key, _ -> result.getJSONObject("invalidReasons").count(key) }
    result.array("gaps", 100_001) { gap ->
        val g = gap as? JSONObject ?: error("Invalid gap")
        val gs = g.number("startMs")!!; val ge = g.number("endMs")!!
        require(gs >= start && ge >= gs && ge <= end && g.number("durationMs") == ge - gs)
        g.flag("leftCensored"); g.flag("rightCensored")
    }
    result.array("targets", 1000) { value ->
        val t = value as? JSONObject ?: error("Invalid target")
        t.text("id"); t.number("xPx"); t.number("yPx"); t.flag("heldOut")
        val ts = t.number("startInclusiveCaptureMs")!!; val te = t.number("endExclusiveCaptureMs")!!
        require(ts >= start && te > ts && t.number("deliveryDeadlineMs")!! >= te)
        validateSpatial(t.getJSONObject("raw")); validateSpatial(t.getJSONObject("selected"))
    }
    val summary = result.getJSONObject("summary")
    summary.nonnegativeOrNull("freshHz"); summary.nonnegativeOrNull("p95AgeMs")
    summary.number("coverage", true)?.let { require(it in 0.0..1.0) }
    listOf("rawHeldOut", "selectedHeldOut").forEach { key ->
        val value = summary.get(key)
        if (value !== JSONObject.NULL) validateSpatial(value as? JSONObject ?: error("Invalid spatial summary"))
    }
    summary.array("missingHeldOutTargetIds", 1000) { require(it is String && it.isNotBlank() && it.length <= 4096) }
    val rows = result.getJSONArray("rows")
    require(rows.length() in observed..100_000)
    var acceptedRows = 0
    var observedRows = 0
    result.array("rows", 100_000) { value ->
        val row = value as? JSONObject ?: error("Invalid observation")
        listOf("captureMs", "deliveryMs", "continuityBoundaryMs", "rawX", "rawY", "selectedX", "selectedY").forEach { row.number(it, true) }
        // Negative source counters are retained as rejected telemetry, rather than rewritten.
        listOf("arrivals", "busyDrops").forEach { key -> row.number(key, true)?.let { require(it % 1.0 == 0.0) } }
        val reason = row.text("reason")
        if (!row.flag("watchdog") && reason != "stopped") observedRows++
        if (row.flag("accepted")) acceptedRows++
        row.nullableText("rejection"); row.nullableText("targetId")
    }
    require(acceptedRows == valid && observedRows == observed)
}

private fun validateSpatial(value: JSONObject) {
    value.count("n", 100_000); value.count("successivePairs", 100_000)
    value.number("dxBiasPx", true); value.number("dyBiasPx", true)
    listOf("medianErrorPx", "p95ErrorPx", "spatialSdPx", "rmsSuccessivePx").forEach { value.nonnegativeOrNull(it) }
}

private fun JSONObject.text(key: String, max: Int = 4096): String = (get(key) as? String)
    ?.also { require(it.isNotBlank() && it.length <= max) } ?: error("Invalid string: $key")
private fun JSONObject.nullableText(key: String, max: Int = 4096) {
    val value = get(key)
    require(value === JSONObject.NULL || (value is String && value.length <= max)) { "Invalid nullable string: $key" }
}
private fun JSONObject.number(key: String, nullable: Boolean = false): Double? {
    val value = get(key)
    if (nullable && value === JSONObject.NULL) return null
    require(value is Number && value.toDouble().isFinite()) { "Invalid number: $key" }
    return value.toDouble()
}
private fun JSONObject.nonnegativeOrNull(key: String) { number(key, true)?.let { require(it >= 0) } }
private fun JSONObject.count(key: String, max: Int = Int.MAX_VALUE): Int {
    val value = number(key)!!
    require(value >= 0 && value <= max && value % 1.0 == 0.0) { "Invalid count: $key" }
    return value.toInt()
}
private fun JSONObject.flag(key: String): Boolean = get(key) as? Boolean ?: error("Invalid boolean: $key")
private fun JSONObject.array(key: String, max: Int, validate: (Any) -> Unit) {
    val values = getJSONArray(key)
    require(values.length() <= max)
    for (i in 0 until values.length()) validate(values.get(i))
}
private fun JSONObject.boundedFields(max: Int, validate: (String, Any) -> Unit) {
    require(length() <= max)
    val names = keys()
    while (names.hasNext()) { val key = names.next(); validate(key, get(key)) }
}

private fun JSONObject.nullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
private fun RunMetadata.json() = JSONObject().put("sessionId", sessionId).put("collectionId", collectionId)
    .put("conditionId", conditionId).put("modelId", modelId).put("calibrationFingerprint", calibrationFingerprint)
    .put("protocolId", protocolId).put("filterId", filterId).put("startedAtUtc", startedAtUtc)
    .put("outcome", outcome).put("geometry", JSONObject(geometry)).put("sourceMetadata", JSONObject(sourceMetadata))
    .nullable("failureReason", failureReason).nullable("endedAtUtc", endedAtUtc)

private fun SpatialMetrics.json() = JSONObject().put("n", n).nullable("dxBiasPx", dxBiasPx)
    .nullable("dyBiasPx", dyBiasPx).nullable("medianErrorPx", medianErrorPx).nullable("p95ErrorPx", p95ErrorPx)
    .nullable("spatialSdPx", spatialSdPx).nullable("rmsSuccessivePx", rmsSuccessivePx).put("successivePairs", successivePairs)

private fun ExperimentResult.json(): JSONObject {
    val targetRows = targets.map { t ->
        val w = t.window
        JSONObject().put("id", w.id).put("xPx", w.xPx).put("yPx", w.yPx)
            .put("startInclusiveCaptureMs", w.startInclusiveCaptureMs).put("endExclusiveCaptureMs", w.endExclusiveCaptureMs)
            .put("deliveryDeadlineMs", w.deliveryDeadlineMs).put("heldOut", w.heldOut)
            .put("raw", t.raw.json()).put("selected", t.selected.json())
    }
    val observations = rows.map { row ->
        val o = row.observation
        JSONObject().nullable("captureMs", o.captureMs).nullable("deliveryMs", row.originalDeliveryMs).put("reason", o.reason)
            .nullable("continuityBoundaryMs", o.deliveryMs.takeIf { row.rejection == "invalid_delivery_clock" })
            .nullable("rawX", o.rawX).nullable("rawY", o.rawY).nullable("selectedX", o.selectedX).nullable("selectedY", o.selectedY)
            .nullable("arrivals", o.arrivals).nullable("busyDrops", o.busyDrops).put("watchdog", o.watchdog)
            .put("accepted", row.accepted).nullable("rejection", row.rejection).nullable("targetId", row.targetId)
    }
    return JSONObject().put("mode", mode).put("startMs", startMs).put("endMs", endMs).put("maxFreshAgeMs", maxFreshAgeMs)
        .put("clock", "elapsed_realtime_ms").put("coordinates", "physical_screen_px")
        .put("observedDeliveredCount", observedDeliveredCount).put("validUniqueFreshCount", validUniqueFreshCount)
        .put("invalidReasons", JSONObject(invalidReasons)).nullable("arrivalsTotal", arrivalsTotal).nullable("busyDropsTotal", busyDropsTotal)
        .put("counterRegressions", counterRegressions).put("rejectedClockCount", rejectedClockCount)
        .put("counterScope", "observed_source_cumulative_totals; CameraX_undelivered_unknown")
        .put("percentiles", "median_central_average;p95_nearest_rank")
        .put("ageScope", "real_deliveries_with_usable_clocks_including_invalid_and_stale")
        .nullable("medianIntersampleMs", medianIntersampleMs).nullable("p95IntersampleMs", p95IntersampleMs)
        .nullable("medianAgeMs", medianAgeMs).nullable("p95AgeMs", p95AgeMs)
        .put("gaps", JSONArray(gaps.map { JSONObject().put("startMs", it.startMs).put("endMs", it.endMs)
            .put("durationMs", it.durationMs).put("leftCensored", it.leftCensored).put("rightCensored", it.rightCensored) }))
        .put("targets", JSONArray(targetRows)).put("summary", JSONObject()
            .nullable("freshHz", summary.freshHz).nullable("p95AgeMs", summary.p95AgeMs).nullable("coverage", summary.coverage)
            .nullable("rawHeldOut", summary.rawHeldOut?.json()).nullable("selectedHeldOut", summary.selectedHeldOut?.json())
            .put("missingHeldOutTargetIds", JSONArray(summary.missingHeldOutTargetIds)))
        .put("rowsTruncated", rowsTruncated).put("omittedObservationCount", omittedObservationCount)
        .put("metricsScope", "retained_observations_only;source_counters_include_omitted_observations")
        .put("rows", JSONArray(observations))
}
