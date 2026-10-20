package com.newsmead.gaze.aid

/** Appearance-only direction from distinct supported rows, never screen motion or a diagnosis. */
internal class ReadingGuideDirectionTracker {
    data class State(val direction: ReadingGuideDirection, val visits: List<ReadingSupportedVisit>)

    private var key: Triple<Long, String, Int>? = null
    private var lastCapture = Double.NEGATIVE_INFINITY
    private var visits = emptyList<ReadingSupportedVisit>()
    private var step = 0
    private var direction = ReadingGuideDirection.BALANCED

    fun reset() {
        key = null; lastCapture = Double.NEGATIVE_INFINITY
        visits = emptyList(); step = 0; direction = ReadingGuideDirection.BALANCED
    }

    fun observe(visit: ReadingSupportedVisit): State {
        val next = Triple(visit.generation, visit.geometrySnapshotId, visit.segment)
        if (key != next) { reset(); key = next }
        if (visit.lastCaptureMs <= lastCapture) return State(direction, visits)
        lastCapture = visit.lastCaptureMs
        val previous = visits.lastOrNull()
        if (previous == null) visits = listOf(visit)
        else if (previous.band.representativeLine == visit.band.representativeLine) {
            visits = visits.dropLast(1) + visit
            // Only fresh same-row support can settle the band. Missing/unsettled
            // observations create a new visit/segment upstream; wall ticks cannot.
            if (visit.lastCaptureMs - visit.firstConfirmedCaptureMs >= ReadingLineGuideAppearance.SETTLE_SUPPORTED_MS)
                direction = ReadingGuideDirection.BALANCED
        } else {
            val nextStep = visit.band.representativeLine.compareTo(previous.band.representativeLine)
            if (nextStep != step) {
                visits = listOf(previous, visit)
                direction = ReadingGuideDirection.BALANCED
            } else {
                visits = (visits + visit).takeLast(3)
                direction = if (nextStep > 0) ReadingGuideDirection.DOWN else ReadingGuideDirection.UP
            }
            step = nextStep
        }
        return State(direction, visits)
    }
}
