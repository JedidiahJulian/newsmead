package com.newsmead.gaze.experiment

/** Pure navigation contract. Each new comparison rotates through all six orders. */
object ExperimentFlow {
    const val EXTRA_CONFIGURATION = "gaze_experiment_configuration"
    const val EXTRA_SESSION = "gaze_experiment_session"
    const val EXTRA_MODE = "gaze_experiment_mode"
    const val VALIDATION = "validation"
    const val READING = "reading"
    const val PROTOCOL = "newsmead_gf2_experiment_v1"
    const val FILTER = "raw_no_smoothing_eye_area_gate_v1"
    val orders = listOf(listOf("A", "B", "C"), listOf("B", "C", "A"), listOf("C", "A", "B"),
        listOf("A", "C", "B"), listOf("C", "B", "A"), listOf("B", "A", "C"))
    fun order(index: Int) = orders[Math.floorMod(index, orders.size)]
    fun collectionGroup(id: String): String = when (id) { "A" -> "current"; "B", "C" -> "next"; else -> error("Unknown configuration") }
    fun validSession(id: String) = runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false)
}
