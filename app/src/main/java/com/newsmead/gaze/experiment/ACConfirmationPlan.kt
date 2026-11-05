package com.newsmead.gaze.experiment

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.TimeZone

/** Resumable bookkeeping, not an accuracy criterion or a new measurement protocol. */
class ACConfirmationPlan private constructor(
    val sessionId: String,
    val setupNotes: String,
    val viewingDistanceCm: Double?,
    val steps: List<Step>,
    private val usedCollections: Set<String>
) {
    data class Step(val id: String, val index: Int, val round: Int, val conditionId: String,
        val entered: Boolean = false, val baselineCollectionId: String? = null,
        val boundCollectionId: String? = null, val boundFingerprint: String? = null)
    data class Fit(val collectionId: String, val fingerprint: String)
    enum class Stage { NEED_CALIBRATION, VALIDATION, READING, COMPLETE }
    data class Action(val index: Int?, val stage: Stage)

    init {
        require(ExperimentFlow.validSession(sessionId))
        require(setupNotes.length <= 1000)
        require(viewingDistanceCm == null || viewingDistanceCm.isFinite() && viewingDistanceCm > 0)
        require(steps.size == 4 && steps.map { it.id }.toSet().size == 4)
        steps.forEachIndexed { index, step ->
            require(ExperimentFlow.validSession(step.id) && step.index == index)
            require(step.round == index / 2 + 1 && step.conditionId == CONDITIONS[index])
            require(step.entered || step.baselineCollectionId == null && step.boundCollectionId == null)
            require((step.boundCollectionId == null) == (step.boundFingerprint == null))
            listOfNotNull(step.baselineCollectionId, step.boundCollectionId, step.boundFingerprint).forEach { bounded(it) }
            require(step.boundCollectionId == null || step.boundCollectionId != step.baselineCollectionId)
            require(step.boundCollectionId == null || step.boundCollectionId in usedCollections)
            if (index > 0 && step.entered) require(steps[index - 1].boundCollectionId != null)
        }
        val bound = steps.mapNotNull { it.boundCollectionId }
        require(bound.toSet().size == bound.size)
        require(usedCollections.size <= 1024)
        usedCollections.forEach { bounded(it) }
    }

    /** Capture the fit present BEFORE starting this block's new calibration, including on resume. */
    fun enter(index: Int, existingCollectionId: String?, reports: List<JSONObject>): ACConfirmationPlan {
        require(index == firstIncomplete(reports))
        val step = steps[index]
        if (step.entered) return this
        existingCollectionId?.let { bounded(it) }
        return replace(index, step.copy(entered = true, baselineCollectionId = existingCollectionId))
    }

    /** Retries can replace the current binding; prior generations remain reserved forever. */
    fun bind(index: Int, collectionId: String, fingerprint: String, reports: List<JSONObject>): ACConfirmationPlan {
        require(index == firstIncomplete(reports))
        bounded(collectionId); bounded(fingerprint)
        val step = steps[index]
        require(step.entered) { "Enter the block before collecting a fresh calibration" }
        require(collectionId != step.baselineCollectionId) { "The calibration predates this block" }
        if (collectionId == step.boundCollectionId && fingerprint == step.boundFingerprint) return this
        require(collectionId !in usedCollections) { "Every calibration generation must be new" }
        return replace(index, step.copy(boundCollectionId = collectionId, boundFingerprint = fingerprint),
            usedCollections + collectionId)
    }

    fun action(reports: List<JSONObject>, liveFits: Map<String, Fit>): Action {
        val index = firstIncomplete(reports) ?: return Action(null, Stage.COMPLETE)
        val step = steps[index]
        val fit = liveFits[step.conditionId]
        if (!step.entered || step.boundCollectionId == null || fit == null ||
            fit.collectionId != step.boundCollectionId || fit.fingerprint != step.boundFingerprint)
            return Action(index, Stage.NEED_CALIBRATION)
        if (reports.any { reading -> matches(step, ExperimentFlow.READING, reading) && reports.any { validation ->
                matches(step, ExperimentFlow.VALIDATION, validation) && runCatching {
                    val a = bootCount(reading)
                    val b = bootCount(validation)
                    a != null && b != null && a != b
                }.getOrDefault(false)
            } }) return Action(index, Stage.NEED_CALIBRATION)
        return Action(index, if (hasReport(step, ExperimentFlow.VALIDATION, reports)) Stage.READING else Stage.VALIDATION)
    }

    fun sourceMetadata(index: Int): Map<String, String> = steps[index].let { step -> mapOf(
        "confirmation_plan" to VERSION, "confirmation_step_id" to step.id,
        "confirmation_round" to step.round.toString(), "confirmation_step_index" to index.toString(),
        "confirmation_setup_notes" to setupNotes,
        "confirmation_viewing_distance_cm" to (viewingDistanceCm?.toString() ?: ""),
        "confirmation_viewing_distance_source" to "self_report", "confirmation_order" to "AC,CA") }

    private fun firstIncomplete(reports: List<JSONObject>): Int? = steps.indexOfFirst {
        !hasOrderedPair(it, reports)
    }.takeIf { it >= 0 }

    private fun hasOrderedPair(step: Step, reports: List<JSONObject>): Boolean = reports.any { reading ->
        matches(step, ExperimentFlow.READING, reading) && reports.any { validation ->
            matches(step, ExperimentFlow.VALIDATION, validation) && runCatching {
                val readingBoot = bootCount(reading)
                val validationBoot = bootCount(validation)
                if (readingBoot != null && validationBoot != null) {
                    val readingStart = reading.getJSONObject("result").getDouble("startMs")
                    val validationEnd = validation.getJSONObject("result").getDouble("endMs")
                    readingBoot == validationBoot && readingStart.isFinite() && validationEnd.isFinite() && validationEnd >= 0 && readingStart >= validationEnd
                } else utc(reading, "startedAtUtc") >= utc(validation, "endedAtUtc")
            }.getOrDefault(false)
        }
    }

    private fun bootCount(report: JSONObject): Long? {
        val value = report.getJSONObject("metadata").getJSONObject("sourceMetadata").opt("confirmation_boot_count")
        if (value == null || value == "unavailable") return null
        require(value is String && value.matches(Regex("0|[1-9][0-9]*")))
        return value.toLong().also { require(it >= 0) }
    }

    private fun utc(report: JSONObject, field: String): Long {
        val value = report.getJSONObject("metadata").getString(field)
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z")))
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC"); isLenient = false
        }
        val position = ParsePosition(0)
        val date = requireNotNull(format.parse(value, position))
        require(position.index == value.length)
        return date.time
    }

    private fun hasReport(step: Step, mode: String, reports: List<JSONObject>): Boolean {
        if (step.boundCollectionId == null) return false
        return reports.any { matches(step, mode, it) }
    }

    private fun matches(step: Step, mode: String, report: JSONObject): Boolean {
        if (step.boundCollectionId == null) return false
        return runCatching {
            val metadata = report.getJSONObject("metadata")
            val source = metadata.getJSONObject("sourceMetadata")
            metadata.getString("sessionId") == sessionId && metadata.getString("conditionId") == step.conditionId &&
                metadata.getString("collectionId") == step.boundCollectionId &&
                metadata.getString("calibrationFingerprint") == step.boundFingerprint &&
                sourceMetadata(step.index).all { (key, value) -> source.opt(key) == value } &&
                report.getJSONObject("result").getString("mode") == mode && ExperimentResultHistory.qualifies(report)
        }.getOrDefault(false)
    }

    private fun replace(index: Int, step: Step, used: Set<String> = usedCollections) = ACConfirmationPlan(
        sessionId, setupNotes, viewingDistanceCm, steps.mapIndexed { i, old -> if (i == index) step else old }, used)

    fun toJson(): JSONObject = JSONObject().put("version", VERSION).put("sessionId", sessionId)
        .put("setupNotes", setupNotes).put("viewingDistanceCm", viewingDistanceCm ?: JSONObject.NULL)
        .put("usedCollections", JSONArray(usedCollections.sorted())).put("steps", JSONArray(steps.map { step ->
            JSONObject().put("id", step.id).put("index", step.index).put("round", step.round)
                .put("conditionId", step.conditionId).put("entered", step.entered)
                .put("baselineCollectionId", step.baselineCollectionId ?: JSONObject.NULL)
                .put("boundCollectionId", step.boundCollectionId ?: JSONObject.NULL)
                .put("boundFingerprint", step.boundFingerprint ?: JSONObject.NULL)
        }))

    companion object {
        const val VERSION = "newsmead_gf2_ac_confirmation_v1"
        private val CONDITIONS = listOf("A", "C", "C", "A")
        fun create(sessionId: String = UUID.randomUUID().toString(), setupNotes: String,
            viewingDistanceCm: Double? = null) = ACConfirmationPlan(sessionId, setupNotes, viewingDistanceCm,
            CONDITIONS.mapIndexed { index, condition -> Step(UUID.randomUUID().toString(), index, index / 2 + 1, condition) }, emptySet())

        /** Fail closed on corrupt/unknown preferences; callers must explicitly offer a new plan. */
        fun fromJson(json: JSONObject): ACConfirmationPlan {
            keys(json, setOf("version", "sessionId", "setupNotes", "viewingDistanceCm", "usedCollections", "steps"))
            require(json.get("version") == VERSION)
            val distance = json.get("viewingDistanceCm").let {
                if (it == JSONObject.NULL) null else { require(it is Number); it.toDouble() }
            }
            val rawUsed = json.getJSONArray("usedCollections")
            require(rawUsed.length() <= 1024)
            val used = (0 until rawUsed.length()).map { string(rawUsed.get(it)) }
            require(used.toSet().size == used.size)
            val rawSteps = json.getJSONArray("steps")
            require(rawSteps.length() == 4)
            val steps = (0 until rawSteps.length()).map { index ->
                val step = rawSteps.getJSONObject(index)
                keys(step, setOf("id", "index", "round", "conditionId", "entered", "baselineCollectionId", "boundCollectionId", "boundFingerprint"))
                require(step.get("index") is Int && step.get("round") is Int && step.get("entered") is Boolean)
                Step(string(step.get("id")), step.getInt("index"), step.getInt("round"), string(step.get("conditionId")),
                    step.getBoolean("entered"), nullableString(step.get("baselineCollectionId")),
                    nullableString(step.get("boundCollectionId")), nullableString(step.get("boundFingerprint")))
            }
            return ACConfirmationPlan(string(json.get("sessionId")), string(json.get("setupNotes")), distance, steps, used.toSet())
        }
        private fun bounded(value: String) { require(value.isNotBlank() && value.length <= 512) }
        private fun string(value: Any): String { require(value is String); return value }
        private fun nullableString(value: Any): String? = if (value == JSONObject.NULL) null else string(value)
        private fun keys(json: JSONObject, expected: Set<String>) {
            require(json.keys().asSequence().toSet() == expected)
        }
    }
}
