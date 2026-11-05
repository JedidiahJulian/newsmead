package com.newsmead.gaze.mgazenet

/** Explicit experiment only; null retains the legacy model, identity and storage. */
enum class GazeExperimentConfiguration(val id: String, val label: String, val modelSha: String,
    val mappingId: String, val isNext: Boolean) {
    CURRENT_SVR("A", "A - Current + SVR", CalibrationIdentity.MODEL, CalibrationIdentity.SVR, false),
    NEXT_SVR("B", "B - Next + SVR", "4547cc4685a7176aad75c50a25dc9a3368d1dd1fafe0d8ea5c32421af93a49b0", CalibrationIdentity.SVR, true),
    NEXT_RIDGE("C", "C - Next + ridge", "4547cc4685a7176aad75c50a25dc9a3368d1dd1fafe0d8ea5c32421af93a49b0", "native_ridge_centered_cholesky_v1_lambda3", true);
    companion object {
        const val NEXT_MODEL_SHA = "4547cc4685a7176aad75c50a25dc9a3368d1dd1fafe0d8ea5c32421af93a49b0"
        fun fromId(id: String): GazeExperimentConfiguration? = entries.firstOrNull { it.id == id }
    }
}
