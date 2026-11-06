package com.newsmead.research.study

import org.json.JSONObject

/** Versioned collection declaration, not a metric definition or validity claim. */
object StudyBaselineProtocol {
    const val VERSION = "shared_reading_baseline_v1"
    const val SURFACE = "article_reading_surface_v1"
    const val PENDING = "RECORDED_PENDING_OFFLINE_FIT"
    fun declaration(attempt: String, participant: String, calibration: String): JSONObject {
        require(listOf(attempt, participant, calibration).all { it.isNotBlank() })
        return JSONObject().put("baseline_protocol", VERSION).put("baseline_attempt_id", attempt)
            .put("baseline_participant_code", participant).put("calibration_id", calibration)
            .put("coordinate_reference_declared", true).put("coordinate_reference_contract", SURFACE)
    }
}
