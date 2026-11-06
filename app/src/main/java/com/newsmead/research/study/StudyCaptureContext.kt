package com.newsmead.research.study

import org.json.JSONObject
import java.util.TreeMap
import java.util.UUID

/** Capture-time floor lookup. A later presentation must never relabel an earlier capture. */
class StudyCaptureContext(startMs: Double) {
    data class State(val epoch: String, val allowed: Boolean, val presentationId: String?, val settings: JSONObject)
    private val timeline = TreeMap<Double, State>().apply {
        put(startMs,State(UUID.randomUUID().toString(),true,null,JSONObject()))
    }
    fun change(atMs: Double, allowed: Boolean?, presentationId: String?, settings: JSONObject): State {
        require(atMs.isFinite() && atMs >= timeline.lastKey())
        val next = State(UUID.randomUUID().toString(),allowed ?: timeline.lastEntry().value.allowed,presentationId,settings)
        timeline[atMs] = next
        while (timeline.size > 4096 && timeline.firstKey() < atMs - 5000) timeline.pollFirstEntry()
        return next
    }
    fun at(captureMs: Double): State? = if (captureMs.isFinite()) timeline.floorEntry(captureMs)?.value else null
    companion object {
        fun presentationBoundary(reason: String) = reason !in setOf("invalid_sample","non_monotonic_capture")
    }
}

/** Short atomic reservation; waiting for writer close occurs outside the reservation monitor. */
class StudyMaintenanceGate {
    private var activeRun = false
    private var maintenance = false
    @Synchronized fun startRun() { check(!maintenance) { "Wait for transfer preparation or recovery." }; activeRun = true }
    @Synchronized fun endRun() { activeRun = false }
    @Synchronized fun claim() { check(!activeRun && !maintenance) { "Stop recording before recovery or transfer." }; maintenance = true }
    @Synchronized fun release() { maintenance = false }
}
