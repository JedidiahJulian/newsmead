package com.newsmead.research.study

import org.json.JSONArray
import org.json.JSONObject

/** Forward-only acquisition identity; raw provider metadata is still recorded in full.
 *
 * Only known operational fields in the two native diagnostic snapshots are omitted.
 * Unknown fields remain compatibility-sensitive, including inside those snapshots.
 * Changes to this projection require a new version; older identities are not upgraded.
 */
object StudyAcquisitionContext {
    const val VERSION = "study-acquisition-v1"

    private val diagnosticBranches = setOf("performance_hints", "mnn_execution")
    private val operationalFields = setOf(
        "closed", "membership_status", "owner_tid", "thread_ids",
        "report_attempts", "reports_succeeded", "last_actual_duration_ns",
        "max_actual_duration_ns", "total_actual_duration_ns", "session_close_verified",
        "sessions_created", "sessions_closed", "api_errors", "last_api_error",
        "native_close_verified",
    )

    fun id(metadata: JSONObject): String = "$VERSION:${StudyJson.hash(canonical(metadata, root = true))}"

    // Serialize directly rather than modifying the provider's JSONObject or relying on its key order.
    private fun canonical(value: Any?, root: Boolean = false, diagnostic: Boolean = false): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted()
            .filterNot { diagnostic && it in operationalFields }
            .joinToString(",", "{", "}") { key ->
                JSONObject.quote(key) + ":" + canonical(value.get(key), diagnostic = root && key in diagnosticBranches)
            }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        is Number -> JSONObject.numberToString(value)
        is Boolean -> value.toString()
        else -> error("Unsupported acquisition metadata value ${value.javaClass.name}")
    }
}
