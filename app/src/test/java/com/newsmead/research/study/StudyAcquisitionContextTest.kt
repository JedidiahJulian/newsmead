package com.newsmead.research.study

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyAcquisitionContextTest {
    private fun metadata() = JSONObject("""{
        "model_sha256":"model-a", "pixel_contract":"rgba-upright-nchw", "runtime":"cpu2",
        "camera_source_shape_rotation":[640,480,90], "requested_localizer":null,
        "reference_calibration_runtime":"calibration-cpu2", "prefer_stable_frame_rate":true,
        "camera_ae_target_fps_request":[30,30], "preprocessor_slot_count":2,
        "output_max_age_ms":650.0, "smooth_for_reading":true, "reject_blinks":false,
        "smoothing_min_cutoff":1.0, "smoothing_beta":0.05,
        "performance_hints":{"requested":false,"active":false,"status":"disabled_by_default","target_duration_ns":33333333,"thread_count":2,
            "requested_session_threads":2,"initial_process_pool_thread_count":2,
            "owner_tid":100,"thread_ids":[100,101],"membership_status":"first_native_creation_exact_1_new_same_comm"},
        "mnn_execution":{"backend":"CPU","runtime":"cpu2","requested":false,"active":false,"status":"disabled_by_default","thread_count":2,
            "owner_tid":100,"thread_ids":[100,101]}
    }""")

    @Test fun nativeOperationalTelemetryDoesNotChangeIdentity() {
        val original = metadata()
        val rawBefore = original.toString()
        val changed = metadata()
        for (branch in listOf("performance_hints", "mnn_execution")) {
            changed.getJSONObject(branch).apply {
                put("owner_tid", 999); put("thread_ids", JSONArray("[999,101]"))
                put("membership_status", "unchanged_cached_native_identities")
                put("closed", true)
                put("report_attempts", 20); put("reports_succeeded", 19)
                put("last_actual_duration_ns", 22); put("max_actual_duration_ns", 30)
                put("total_actual_duration_ns", 250); put("session_close_verified", false)
                put("sessions_created", 3); put("sessions_closed", 2)
                put("api_errors", 1); put("last_api_error", "close_error:Exception")
                put("native_close_verified", true)
            }
        }
        assertEquals(StudyAcquisitionContext.id(original), StudyAcquisitionContext.id(changed))
        assertEquals(rawBefore, original.toString())
        assertEquals(100, original.getJSONObject("performance_hints").getInt("owner_tid"))
    }

    @Test fun controlsAndUnknownFieldsRemainCompatibilitySensitive() {
        val originalId = StudyAcquisitionContext.id(metadata())
        val topLevelChanges = mapOf<String, Any>(
            "model_sha256" to "model-b", "pixel_contract" to "other-preprocessing",
            "runtime" to "cpu4", "requested_localizer" to "CPU",
            "reference_calibration_runtime" to "other-calibration-runtime",
            "camera_source_shape_rotation" to JSONArray("[480,640,90]"),
            "prefer_stable_frame_rate" to false, "camera_ae_target_fps_request" to JSONArray("[15,30]"),
            "preprocessor_slot_count" to 3, "output_max_age_ms" to 500.0,
            "smooth_for_reading" to false, "reject_blinks" to true,
            "smoothing_min_cutoff" to 2.0, "smoothing_beta" to 0.1,
            "new_control" to true, "owner_tid" to 999,
        )
        topLevelChanges.forEach { (key, value) ->
            assertNotEquals(key, originalId, StudyAcquisitionContext.id(metadata().put(key, value)))
        }
        for (branch in listOf("performance_hints", "mnn_execution")) {
            for ((key, value) in mapOf("requested" to true, "active" to true, "status" to "unsupported_api_error",
                "target_duration_ns" to 100,
                "thread_count" to 4, "requested_session_threads" to 4,
                "initial_process_pool_thread_count" to 4, "backend" to "OPENCL",
                "actual_main_backend" to 3, "gpu_mode" to 16, "precision" to "fp16",
                "copy_layout" to "other", "new_control" to true)) {
                val changed = metadata()
                changed.getJSONObject(branch).put(key, value)
                assertNotEquals("$branch.$key", originalId, StudyAcquisitionContext.id(changed))
            }
        }
    }

    @Test fun canonicalizesNestedObjectKeysWithoutMutationAndPreservesArrayOrder() {
        val first = JSONObject("""{"z":[{"b":2,"a":1},3],"a":{"z":null,"a":"quoted\""}}""")
        val second = JSONObject("""{"a":{"a":"quoted\"","z":null},"z":[{"a":1,"b":2},3]}""")
        val before = first.toString()
        val id = StudyAcquisitionContext.id(first)
        assertEquals(id, StudyAcquisitionContext.id(second))
        assertEquals(before, first.toString())
        assertTrue(id.startsWith("${StudyAcquisitionContext.VERSION}:"))
        second.put("z", JSONArray("[3,{\"a\":1,\"b\":2}]"))
        assertNotEquals(id, StudyAcquisitionContext.id(second))
    }

    @Test fun telemetryNamesOutsideImmediateKnownBranchesAreNotDiscarded() {
        val original = metadata()
        original.getJSONObject("mnn_execution").put("future_control", JSONObject().put("status", "a"))
        val changed = JSONObject(original.toString())
        changed.getJSONObject("mnn_execution").getJSONObject("future_control").put("status", "b")
        assertNotEquals(StudyAcquisitionContext.id(original), StudyAcquisitionContext.id(changed))
    }

    @Test fun missingControlRemainsDistinctFromExplicitNull() {
        val original = metadata()
        val missing = metadata().apply { remove("requested_localizer") }
        assertNotEquals(StudyAcquisitionContext.id(original), StudyAcquisitionContext.id(missing))
    }
}
