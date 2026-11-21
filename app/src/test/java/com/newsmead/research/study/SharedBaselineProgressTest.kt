package com.newsmead.research.study

import org.junit.Assert.*
import org.junit.Test

class SharedBaselineProgressTest {
    private fun state(rdfMs: Long = 66_000, windows: Int = 86,
        coordinateMs: Double = 57_624.855821, issue: String? = null) = SharedBaselineProgress(
        rdfMs, 60_000, windows, 30,
        StudyCoordinateCoverage.Snapshot(coordinateMs, 60_000.0, 1302, issue,
            issue == null && coordinateMs >= 60_000.0))

    @Test fun passingRdfMinimumDoesNotAutomaticallyCompleteWithShortCoordinateCoverage() {
        val progress = state()
        assertTrue(progress.rdfMinimumMet)
        assertFalse(progress.minimumsMet)
        assertEquals("R/D/F: 66/60 s\nGaze: 57.6/60 s", progress.footer)
        assertTrue(progress.detail.contains("86 of 30 valid windows"))
    }

    @Test fun bothCoverageAndWindowMinimumsAreRequiredForAutomaticCompletion() {
        assertFalse(state(rdfMs = 59_999, coordinateMs = 60_100.0).minimumsMet)
        assertFalse(state(windows = 29, coordinateMs = 60_100.0).minimumsMet)
        assertFalse(state(coordinateMs = 59_999.999).minimumsMet)
        assertTrue(state(rdfMs = 60_000, windows = 30, coordinateMs = 60_000.0).minimumsMet)
        assertTrue(state(coordinateMs = 60_100.0).footer.endsWith("Gaze: 60.1/60 s"))
    }

    @Test fun unavailablePreviewNeverAutomaticallyCompletes() {
        assertFalse(state(coordinateMs = 70_000.0, issue = "context_unavailable").minimumsMet)
        assertTrue(state(issue = "context_unavailable").footer.endsWith("Gaze: unavailable"))
        val absent = SharedBaselineProgress(70_000, 60_000, 100, 60, null)
        assertFalse(absent.minimumsMet)
        assertTrue(absent.footer.contains("Gaze: waiting"))
    }

    @Test fun displayFloorsTimeSoBelowThresholdNeverLooksComplete() {
        assertEquals("59.9", SharedBaselineProgress.seconds(59_999.999))
        assertEquals("60", SharedBaselineProgress.seconds(60_000.0))
        assertTrue(state(coordinateMs = 59_999.999).footer.contains("Gaze: 59.9/60 s"))
    }
}
