package com.newsmead.gaze.experiment

import android.content.Intent
import org.json.JSONObject

/** Explicit per-block tags, fixed before a camera session starts. */
object ACConfirmationLaunch {
    const val EXTRA = "gaze_ac_confirmation"
    fun put(intent: Intent, plan: ACConfirmationPlan, index: Int): Intent {
        val fields = plan.sourceMetadata(index) + ("confirmation_session_id" to plan.sessionId)
        return intent.putExtra(EXTRA, JSONObject(fields).toString())
    }
    fun parse(intent: Intent): Map<String, String>? = runCatching {
        if (!intent.hasExtra(EXTRA)) return null
        val data = JSONObject(requireNotNull(intent.getStringExtra(EXTRA)))
        require(data.length() in 9..16)
        val fields = data.keys().asSequence().associateWith { key ->
            require(key.length <= 128)
            (data.get(key) as? String)?.also { require(it.length <= 1000) } ?: error("Invalid confirmation field")
        }
        require(fields["confirmation_plan"] == ACConfirmationPlan.VERSION)
        val index = requireNotNull(fields["confirmation_step_index"]?.toIntOrNull())
        require(index in 0..3 && fields["confirmation_round"] == (index / 2 + 1).toString())
        require(fields["confirmation_order"] == "AC,CA")
        require(ExperimentFlow.validSession(fields["confirmation_step_id"] ?: ""))
        require(ExperimentFlow.validSession(fields["confirmation_session_id"] ?: ""))
        require(fields["confirmation_session_id"] == intent.getStringExtra(ExperimentFlow.EXTRA_SESSION))
        require(intent.getStringExtra(ExperimentFlow.EXTRA_CONFIGURATION) == listOf("A", "C", "C", "A")[index])
        require(fields.containsKey("confirmation_setup_notes") && fields["confirmation_viewing_distance_source"] == "self_report")
        val distance = requireNotNull(fields["confirmation_viewing_distance_cm"])
        if (distance.isNotEmpty()) require(distance.toDouble().let { it.isFinite() && it > 0 })
        fields
    }.getOrNull()
}
