package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.FrozenMahalanobisProfile
import java.util.UUID

/** In-memory participant session; never persisted or restored after process death. */
object ParticipantReadingBaseline {
    data class Entry(val participantCode: String, val sessionId: String,
        val calibrationFingerprint: String, val profile: FrozenMahalanobisProfile)

    private var activeSessionId: String? = null
    private var entry: Entry? = null

    /** Called only after the participant explicitly starts a new baseline attempt. */
    fun beginNewBaseline(): String = UUID.randomUUID().toString().also {
        activeSessionId = it
        entry = null
    }

    fun freeze(value: Entry) {
        check(value.sessionId == activeSessionId) { "Baseline session changed" }
        check(entry == null) { "Baseline already frozen for this session" }
        entry = value
    }

    fun current(calibrationFingerprint: String?): Entry? = entry?.takeIf {
        calibrationFingerprint != null && it.calibrationFingerprint == calibrationFingerprint &&
            it.sessionId == activeSessionId
    }

    fun isCurrentSession(sessionId: String): Boolean = activeSessionId == sessionId
}
