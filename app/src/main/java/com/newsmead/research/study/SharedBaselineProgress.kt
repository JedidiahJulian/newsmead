package com.newsmead.research.study

import java.util.Locale
import kotlin.math.floor

/** Minimum-data guidance only. Neither counter establishes a successful fit or gaze accuracy. */
internal data class SharedBaselineProgress(
    val rdfCoveredMs: Long,
    val rdfRequiredMs: Long,
    val retainedWindows: Int,
    val requiredWindows: Int,
    val coordinate: StudyCoordinateCoverage.Snapshot?
) {
    val rdfMinimumMet: Boolean get() = rdfCoveredMs >= rdfRequiredMs && retainedWindows >= requiredWindows
    val minimumsMet: Boolean get() = rdfMinimumMet && coordinate?.minimumDataReady == true
    private val waitingForGaze: Boolean get() = coordinate == null || coordinate.issue == "no_in_bound_observations"

    val footer: String get() = "R/D/F: ${seconds(rdfCoveredMs.toDouble())}/${seconds(rdfRequiredMs.toDouble())} s\n" +
        (if (waitingForGaze) "Gaze: waiting\n"
        else if (coordinate?.issue != null) "Gaze: unavailable\n"
        else if (coordinate == null) "Gaze: waiting\n"
        else "Gaze: ${seconds(coordinate.coveredMs)}/${seconds(coordinate.requiredMs)} s\n") +
        when {
            !waitingForGaze && coordinate?.issue != null -> "Check setup"
            minimumsMet -> "Minimums met"
            else -> "Keep reading"
        }

    val detail: String get() =
        "R/D/F coverage: ${seconds(rdfCoveredMs.toDouble())} of ${seconds(rdfRequiredMs.toDouble())} seconds; " +
            "$retainedWindows of $requiredWindows valid windows.\n" +
            if (waitingForGaze) "Waiting for usable gaze data."
            else if (coordinate == null || coordinate.issue != null) "Usable gaze time cannot be confirmed. Check the setup and collect a new baseline."
            else "Usable gaze time: ${seconds(coordinate.coveredMs)} of ${seconds(coordinate.requiredMs)} seconds."

    companion object {
        // Never round 59.999 seconds up to a displayed 60 before the threshold is met.
        fun seconds(milliseconds: Double): String {
            val tenths = floor(milliseconds.coerceAtLeast(0.0) / 100.0) / 10.0
            return if (tenths % 1.0 == 0.0) String.format(Locale.ROOT, "%.0f", tenths)
            else String.format(Locale.ROOT, "%.1f", tenths)
        }
    }
}
