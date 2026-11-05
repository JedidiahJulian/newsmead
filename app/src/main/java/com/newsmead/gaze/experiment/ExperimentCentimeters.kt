package com.newsmead.gaze.experiment

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.sqrt

/** Physical-screen axis scales; Android density/dp is not a physical length scale. */
data class PhysicalDisplayScale(
    val pixelsPerCmX: Double, val pixelsPerCmY: Double,
    val screenWidthPx: Int, val screenHeightPx: Int, val rotation: Int, val source: String,
) {
    init {
        require(pixelsPerCmX.isFinite() && pixelsPerCmX > 0 && pixelsPerCmY.isFinite() && pixelsPerCmY > 0)
        require(screenWidthPx > 0 && screenHeightPx > 0 && rotation in 0..3 && source.isNotBlank())
    }
}

/** Derived units only: retained pixel rows, target membership and acceptance remain authoritative. */
object ExperimentCentimeters {
    fun fromResult(result: ExperimentResult, scale: PhysicalDisplayScale): JSONObject = calculate(
        result.mode, result.maxFreshAgeMs, result.targets.map { it.window }, result.rows.map { row ->
            val o = row.observation
            Row(row.accepted, row.targetId, o.captureMs, o.deliveryMs, o.rawX, o.rawY, o.selectedX, o.selectedY)
        }, scale,
    )

    /** Caller must validate the full report schema first. Fallback is for local private legacy reports only. */
    fun fromReport(report: JSONObject, fallback: PhysicalDisplayScale? = null): JSONObject? {
        val metadata = report.getJSONObject("metadata")
        val geometry = metadata.getJSONObject("geometry")
        val sourceMetadata = metadata.getJSONObject("sourceMetadata")
        val recorded = geometry.has("pixelsPerCmX") || geometry.has("pixelsPerCmY") ||
            sourceMetadata.has("physical_scale_source")
        val scale = if (recorded) {
            val x = geometry.strictNumber("pixelsPerCmX") ?: return null
            val y = geometry.strictNumber("pixelsPerCmY") ?: return null
            val width = geometry.strictInt("screenWidthPx") ?: return null
            val height = geometry.strictInt("screenHeightPx") ?: return null
            val rotation = geometry.strictInt("rotation") ?: return null
            val source = sourceMetadata.opt("physical_scale_source") as? String ?: return null
            try { PhysicalDisplayScale(x, y, width, height, rotation, source) }
            catch (_: IllegalArgumentException) { return null }
        } else {
            val candidate = fallback ?: return null
            if (geometry.strictInt("screenWidthPx") != candidate.screenWidthPx ||
                geometry.strictInt("screenHeightPx") != candidate.screenHeightPx ||
                geometry.strictInt("rotation") != candidate.rotation) return null
            candidate.copy(source = "current_device_display_metrics_for_legacy_local_report")
        }
        val result = report.getJSONObject("result")
        val targets = result.getJSONArray("targets").objects().map {
            TargetWindow(it.getString("id"), it.getDouble("xPx"), it.getDouble("yPx"),
                it.getDouble("startInclusiveCaptureMs"), it.getDouble("endExclusiveCaptureMs"),
                it.getDouble("deliveryDeadlineMs"), it.getBoolean("heldOut"))
        }
        val rows = result.getJSONArray("rows").objects().map {
            Row(it.getBoolean("accepted"), it.opt("targetId") as? String,
                it.strictNumber("captureMs"),
                it.strictNumber("continuityBoundaryMs") ?: it.strictNumber("deliveryMs"),
                it.strictNumber("rawX"), it.strictNumber("rawY"),
                it.strictNumber("selectedX"), it.strictNumber("selectedY"))
        }
        return try { calculate(result.getString("mode"), result.getDouble("maxFreshAgeMs"), targets, rows, scale) }
        catch (_: IllegalArgumentException) { null }
    }

    private data class Row(val accepted: Boolean, val targetId: String?, val capture: Double?,
        val delivery: Double?, val rawX: Double?, val rawY: Double?, val selectedX: Double?, val selectedY: Double?)

    private fun calculate(mode: String, freshness: Double, windows: List<TargetWindow>, rows: List<Row>,
        scale: PhysicalDisplayScale): JSONObject {
        val held = windows.filter { it.heldOut }
        val hasSummary = mode == "validation" && held.isNotEmpty()
        return JSONObject().put("method", "newsmead_gaze_cm_spatial_v1").put("units", "cm")
            .put("pixelsPerCmX", scale.pixelsPerCmX).put("pixelsPerCmY", scale.pixelsPerCmY)
            .put("scaleSource", scale.source).put("summary", JSONObject()
                .nullable("rawHeldOut", if (hasSummary) spatial(held, rows, false, freshness, scale) else null)
                .nullable("selectedHeldOut", if (hasSummary) spatial(held, rows, true, freshness, scale) else null))
            .put("targets", JSONArray(windows.map { window -> JSONObject().put("id", window.id)
                .put("raw", spatial(listOf(window), rows, false, freshness, scale))
                .put("selected", spatial(listOf(window), rows, true, freshness, scale)) }))
    }

    private fun spatial(windows: List<TargetWindow>, rows: List<Row>, selected: Boolean,
        freshness: Double, scale: PhysicalDisplayScale): JSONObject {
        val errors = mutableListOf<Double>(); val dxs = mutableListOf<Double>(); val dys = mutableListOf<Double>()
        var withinSquared = 0.0; var degrees = 0; var pairSquared = 0.0; var pairs = 0
        for (window in windows) {
            val localX = mutableListOf<Double>(); val localY = mutableListOf<Double>()
            var previous: Row? = null
            for (row in rows) {
                val x = if (selected) row.selectedX else row.rawX
                val y = if (selected) row.selectedY else row.rawY
                if (!row.accepted || row.targetId != window.id || x == null || y == null) {
                    previous = null; continue
                }
                val dx = (x - window.xPx) / scale.pixelsPerCmX
                val dy = (y - window.yPx) / scale.pixelsPerCmY
                errors += hypot(dx, dy); dxs += dx; dys += dy
                // Target-relative coordinates avoid cancellation from unrelated screen origins.
                localX += dx; localY += dy
                previous?.let { p ->
                    if (row.delivery!! <= p.capture!! + freshness) {
                        val px = if (selected) p.selectedX!! else p.rawX!!
                        val py = if (selected) p.selectedY!! else p.rawY!!
                        val sx = (x - px) / scale.pixelsPerCmX; val sy = (y - py) / scale.pixelsPerCmY
                        pairSquared += sx * sx + sy * sy; pairs++
                    }
                }
                previous = row
            }
            if (localX.size > 1) {
                val mx = localX.average(); val my = localY.average()
                withinSquared += localX.indices.sumOf {
                    val dx = localX[it] - mx; val dy = localY[it] - my; dx * dx + dy * dy
                }
                degrees += localX.size - 1
            }
        }
        return JSONObject().put("n", errors.size).nullable("dxBiasCm", dxs.takeIf { it.isNotEmpty() }?.average())
            .nullable("dyBiasCm", dys.takeIf { it.isNotEmpty() }?.average())
            .nullable("medianErrorCm", ExperimentMeasurements.percentile(errors, .5))
            .nullable("p95ErrorCm", ExperimentMeasurements.percentile(errors, .95))
            .nullable("spatialSdCm", if (degrees > 0) sqrt(withinSquared / degrees) else null)
            .nullable("rmsSuccessiveCm", if (pairs > 0) sqrt(pairSquared / pairs) else null)
            .put("successivePairs", pairs)
    }

    private fun JSONObject.strictNumber(key: String): Double? = (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun JSONObject.strictInt(key: String): Int? = strictNumber(key)?.takeIf {
        it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() && it % 1.0 == 0.0
    }?.toInt()
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.nullable(key: String, value: Any?): JSONObject {
        require(value !is Double || value.isFinite()) { "Nonfinite centimeter metric: $key" }
        return put(key, value ?: JSONObject.NULL)
    }
}
