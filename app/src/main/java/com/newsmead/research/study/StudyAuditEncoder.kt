package com.newsmead.research.study

import com.newsmead.gaze.aid.ReadingAidContext
import com.newsmead.gaze.scanpath.ScanpathGeometry
import org.json.JSONObject

/** Preserve native audit state while recording a shared article layout once per change.
 * Called on the producer thread; the sink must snapshot JSON before returning.
 */
class StudyAuditEncoder {
    private var lastGeometry: ScanpathGeometry? = null
    private var lastGeneration: Long? = null
    private var lastRevision: Long? = null
    private var nextRevision = 0L

    fun record(kind: String, atMs: Double, detail: Map<String, Any?>,
        sink: (String, JSONObject, Double) -> Boolean) {
        val context = detail["input_context"] as? ReadingAidContext
        if (context == null) {
            sink(kind, StudyJson.value(detail) as JSONObject, atMs)
            return
        }
        val geometry = context.geometry
        var revision = lastRevision
        if (geometry != null && (geometry !== lastGeometry || context.generation != lastGeneration)) {
            // Controlled scrolling can reuse the native ID with a different screen origin.
            // Reserve a unique revision even if the sink rejects this snapshot.
            revision = ++nextRevision
            // A rejected layout cannot become the cache entry: retry on the next audit.
            if (sink("layout_snapshot", StudyJson.obj("producer" to PRODUCER,
                    "generation" to context.generation, "layout_revision" to revision,
                    "geometry" to geometry), atMs)) {
                lastGeometry = geometry
                lastGeneration = context.generation
                lastRevision = revision
            }
        }
        // Copying the context changes the serialization input only; the policy keeps its native object.
        val input = StudyJson.value(context.copy(geometry = null)) as JSONObject
        input.remove("geometry")
        input.put("geometry_ref", geometry?.let { StudyJson.obj("producer" to PRODUCER,
            "generation" to context.generation, "geometry_snapshot_id" to it.snapshotId,
            "layout_revision" to revision) } ?: JSONObject.NULL)
        val payload = JSONObject()
        detail.forEach { (key, value) -> payload.put(key, if (key == "input_context") input else StudyJson.value(value)) }
        payload.put("audit_payload_version", VERSION)
        sink(kind, payload, atMs)
    }

    companion object {
        const val VERSION = "aid_geometry_ref_v1"
        const val PRODUCER = "aid_audit"
    }
}
