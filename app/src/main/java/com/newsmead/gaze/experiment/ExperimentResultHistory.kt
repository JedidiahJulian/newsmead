package com.newsmead.gaze.experiment

import org.json.JSONObject

/** Display history and workflow eligibility have deliberately different scopes. */
object ExperimentResultHistory {
    const val ALL = "all"
    const val CURRENT = "current"

    /** Input order is newest first, as returned by ExperimentReportStore.list. */
    fun scope(reports: List<JSONObject>, scope: String, currentSession: String): List<JSONObject> =
        if (scope == ALL) reports else reports.filter {
            it.getJSONObject("metadata").getString("sessionId") == if (scope == CURRENT) currentSession else scope
        }

    fun current(reports: List<JSONObject>, session: String, condition: String,
        fingerprint: String?, collection: String?): List<JSONObject> {
        if (fingerprint == null || collection == null) return emptyList()
        return reports.filter { report -> report.getJSONObject("metadata").let {
            it.getString("sessionId") == session && it.getString("conditionId") == condition &&
                it.getString("calibrationFingerprint") == fingerprint && it.getString("collectionId") == collection
        } }
    }

    fun latest(reports: List<JSONObject>, condition: String, mode: String): JSONObject? = reports.firstOrNull {
        it.getJSONObject("metadata").getString("conditionId") == condition && it.getJSONObject("result").getString("mode") == mode
    }

    fun qualifies(report: JSONObject): Boolean = report.getJSONObject("result").let { result ->
        report.getJSONObject("metadata").getString("outcome") == "completed" && !result.getBoolean("rowsTruncated") &&
            result.getInt("validUniqueFreshCount") > 0 && (result.getString("mode") != ExperimentFlow.VALIDATION ||
                result.getJSONObject("summary").getJSONArray("missingHeldOutTargetIds").length() == 0)
    }
}
