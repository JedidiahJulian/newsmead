package com.newsmead.gaze

/** A recorded coordinate reference, independent of whether the R/D/F fit succeeds.
 * The coordinate fit is deliberately deferred to the strict offline importer.
 * Like the R/D/F session, this link is never restored after process death.
 */
object SharedReadingBaseline {
    const val PROTOCOL = com.newsmead.research.study.StudyBaselineProtocol.VERSION
    const val SURFACE = com.newsmead.research.study.StudyBaselineProtocol.SURFACE
    const val PENDING = com.newsmead.research.study.StudyBaselineProtocol.PENDING
    const val ARGUMENT = "shared_reading_baseline"

    data class Entry(val sourcePackageId: String, val sourceSessionId: String,
        val baselineAttemptId: String, val participantId: String, val installationId: String,
        val calibrationId: String, val clockDomainId: String, val startMs: Double, val endMs: Double) {
        init {
            require(listOf(sourcePackageId, sourceSessionId, baselineAttemptId, participantId,
                installationId, calibrationId, clockDomainId).all { it.isNotBlank() })
            require(startMs.isFinite() && endMs.isFinite() && startMs >= 0 && endMs > startMs)
        }
        fun fields(): Map<String, Any?> = linkedMapOf("protocol" to PROTOCOL,
            "source_package_id" to sourcePackageId, "source_session_id" to sourceSessionId,
            "baseline_attempt_id" to baselineAttemptId, "participant_id" to participantId,
            "installation_id" to installationId, "calibration_id" to calibrationId,
            "clock_domain_id" to clockDomainId, "start_ms" to startMs, "end_ms" to endMs,
            "status" to PENDING)
    }
    private var entry: Entry? = null
    fun recorded(value: Entry) {
        check(ParticipantReadingBaseline.isCurrentSession(value.baselineAttemptId))
        entry = value
    }
    fun current(calibrationId: String?, participantId: String?): Entry? = entry?.takeIf {
        calibrationId != null && participantId != null && it.calibrationId == calibrationId &&
            it.participantId == participantId && ParticipantReadingBaseline.isCurrentSession(it.baselineAttemptId)
    }
}
