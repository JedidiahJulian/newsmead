package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.FrozenMahalanobisProfile
import java.util.UUID

enum class ParticipantBaselineStatus {
    BASELINE_NOT_READY,
    BASELINE_COLLECTING,
    BASELINE_VALID,
    BASELINE_INVALID,
}

/** In-memory participant session; never persisted or restored after process death. */
object ParticipantReadingBaseline {
    data class Entry(val participantCode: String, val sessionId: String,
        val calibrationFingerprint: String, val profile: FrozenMahalanobisProfile)

    private var activeSessionId: String? = null
    private var entry: Entry? = null
    private var status = ParticipantBaselineStatus.BASELINE_NOT_READY
    private var statusCalibrationFingerprint: String? = null

    /** Called only after the participant explicitly starts a new baseline attempt. */
    fun beginNewBaseline(): String = UUID.randomUUID().toString().also {
        activeSessionId = it
        entry = null
        status = ParticipantBaselineStatus.BASELINE_NOT_READY
        statusCalibrationFingerprint = null
    }

    fun markCollecting(sessionId: String, calibrationFingerprint: String) {
        check(sessionId == activeSessionId) { "Baseline session changed" }
        check(entry == null) { "Baseline already frozen for this session" }
        status = ParticipantBaselineStatus.BASELINE_COLLECTING
        statusCalibrationFingerprint = calibrationFingerprint
    }

    fun markInvalid(sessionId: String, calibrationFingerprint: String) {
        check(sessionId == activeSessionId) { "Baseline session changed" }
        check(entry == null) { "Baseline already frozen for this session" }
        status = ParticipantBaselineStatus.BASELINE_INVALID
        statusCalibrationFingerprint = calibrationFingerprint
    }

    /** Ignore stale asynchronous shutdowns from an older baseline attempt. */
    fun abandonCollecting(sessionId: String, calibrationFingerprint: String) {
        if (sessionId != activeSessionId || entry != null ||
            status != ParticipantBaselineStatus.BASELINE_COLLECTING ||
            calibrationFingerprint != statusCalibrationFingerprint) return
        status = ParticipantBaselineStatus.BASELINE_NOT_READY
        statusCalibrationFingerprint = null
    }

    fun freeze(value: Entry) {
        check(value.sessionId == activeSessionId) { "Baseline session changed" }
        check(entry == null) { "Baseline already frozen for this session" }
        entry = value
        status = ParticipantBaselineStatus.BASELINE_VALID
        statusCalibrationFingerprint = value.calibrationFingerprint
    }

    fun current(calibrationFingerprint: String?): Entry? = entry?.takeIf {
        calibrationFingerprint != null && it.calibrationFingerprint == calibrationFingerprint &&
            it.sessionId == activeSessionId
    }

    fun status(calibrationFingerprint: String?): ParticipantBaselineStatus =
        if (calibrationFingerprint != null && calibrationFingerprint == statusCalibrationFingerprint)
            status else ParticipantBaselineStatus.BASELINE_NOT_READY

    fun isCurrentSession(sessionId: String): Boolean = activeSessionId == sessionId
}
