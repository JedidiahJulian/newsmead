package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Deterministic capture-clock settings checks, not human reading validation. */
class ReadingBookmarkPersistenceV5Test {
    private val detector = ScanpathConfig()
    private val geometry = ScanpathGeometry("g", 100.0, 50.0,
        ScanpathRect(100.0, 50.0, 300.0, 250.0), 20.0,
        (0..7).map { ScanpathLine(it * 10, it * 10 + 9, 0.0, 200.0,
            it * 20.0, it * 20.0 + 20.0) }, ScanpathTextDirection.LTR)
    private fun policy(settings: ReadingBookmarkPersistenceConfig = ReadingBookmarkPersistenceConfig()) =
        // Seed persistence through the explicit legacy comparison route; live V5 disables it.
        ReadingBookmarkPolicy(detector, settings.copy(allowLineJumpTrigger = true))
    private fun context(t: Double, line: Int? = 3, exclusion: String? = null) =
        ReadingAidContext(1, "g", 8, line, null, null, t, t, t,
            validLiveInput = exclusion == null, geometry = geometry,
            rawSample = ScanpathSample(t, t, if (exclusion == "outside_visible_text") 350.0 else 150.0,
                100.0, line, null, null, "g", exclusion == null, exclusion))
    private fun evidence(first: Double, last: Double, line: Int = 3, segment: Int = 1) =
        ReadingAidContinuationEvidence("$segment:$first:$last:$line", segment, line, first, last, last)
    private fun support(p: ReadingBookmarkPolicy, first: Double, last: Double, line: Int = 3,
                        segment: Int = 1) = p.support(evidence(first, last, line, segment), context(last, line))
    private fun armJump(p: ReadingBookmarkPolicy): ReadingAidBookmarkEvidence {
        assertNull(support(p, 10.0, 110.0).evidence)
        return support(p, 200.0, 300.0, 0).evidence!!
    }
    private fun run(p: ReadingBookmarkPolicy, first: Int, last: Int, line: Int = 3): ReadingBookmarkPolicy.Change {
        var change = ReadingBookmarkPolicy.Change()
        for (t in first until last step 100) change = support(p, t.toDouble(), t + 100.0, line)
        return change
    }

    @Test fun defaultVisibilityAndReturnBothRequired() {
        val p = policy()
        val saved = armJump(p)
        assertEquals(3, saved.source.line)
        assertEquals(110.0, saved.source.lastCaptureMs, 0.0)
        assertNull(run(p, 300, 1300).clearReason) // 1000ms return; only 1000ms visible.
        assertNull(run(p, 1300, 1700).clearReason)
        assertEquals("saved_line_return", support(p, 1700.0, 1800.0).clearReason)
    }

    @Test fun elapsedVisibilityDoesNotReplaceSupportedReturnAndTimersCannotRelease() {
        val p = policy(); armJump(p)
        assertNull(p.input(context(2000.0), 250.0, 500.0).clearReason)
        assertNull(support(p, 2000.0, 2100.0).clearReason)
        assertNull(p.input(context(5000.0), 250.0, 500.0).clearReason)
        assertNull(support(p, 5000.0, 5100.0).clearReason)
        assertNull(run(p, 5100, 5900).clearReason)
        assertEquals("saved_line_return", support(p, 5900.0, 6000.0).clearReason)
    }

    @Test fun duplicatesAndOverlappingPrefixesOnlyCreditFreshTail() {
        val p = policy(ReadingBookmarkPersistenceConfig(minimumVisibleMs = 100.0,
            savedLineReturnMs = 1000.0))
        armJump(p)
        for (t in 400..1200 step 100) {
            // Each prefix reuses its first capture; duplicate deliveries add no time.
            val value = evidence(300.0, t.toDouble())
            assertNull(p.support(value, context(t.toDouble())).clearReason)
            assertNull(p.support(value, context(t.toDouble())).clearReason)
        }
        assertEquals("saved_line_return", p.support(evidence(300.0, 1300.0), context(1300.0)).clearReason)
    }

    @Test fun gapsMissingAndOversizedIntervalsBreakCredit() {
        val p = policy(ReadingBookmarkPersistenceConfig(minimumVisibleMs = 100.0,
            savedLineReturnMs = 500.0))
        armJump(p)
        assertNull(run(p, 300, 700).clearReason)
        assertNull(support(p, 800.0, 900.0).clearReason) // uncovered 100ms resets 400ms.
        assertNull(run(p, 900, 1200).clearReason)
        p.input(context(1250.0, null, "no_face"), 250.0, 500.0)
        assertNull(run(p, 1300, 1700).clearReason)
        assertNull(support(p, 1700.0, 2200.0).clearReason) // unsupported 500ms earns zero.
        assertNull(run(p, 2200, 2600).clearReason)
        assertEquals("saved_line_return", support(p, 2600.0, 2700.0).clearReason)
    }

    @Test fun invalidClockAndStaleSupportBreakReleaseChain() {
        val p = policy(ReadingBookmarkPersistenceConfig(minimumVisibleMs = 100.0,
            savedLineReturnMs = 500.0))
        armJump(p)
        assertNull(run(p, 300, 700).clearReason)
        val invalid = context(750.0)
        p.input(invalid.copy(rawSample = invalid.rawSample!!.copy(deliveryMs = 800.0)), 250.0, 500.0)
        assertNull(run(p, 700, 1100).clearReason)
        assertNull(p.support(evidence(1100.0, 1200.0), context(1200.0).copy(nowMs = 1500.0)).clearReason)
        assertNull(run(p, 1500, 1900).clearReason)
        assertEquals("saved_line_return", support(p, 1900.0, 2000.0).clearReason)
    }

    @Test fun forwardAdjacentElsewhereReadingReleasesAfter1500ms() {
        val p = policy(); armJump(p)
        assertNull(run(p, 300, 800, 0).clearReason)
        assertNull(run(p, 800, 1300, 1).clearReason)
        assertNull(run(p, 1300, 1700, 2).clearReason)
        assertEquals("continued_reading_elsewhere", support(p, 1700.0, 1800.0, 2).clearReason)
    }

    @Test fun fiveHzRowSettlingPreservesCreditWithoutCreditingTransition() {
        val p = policy(ReadingBookmarkPersistenceConfig(minimumVisibleMs = 100.0,
            elsewhereReadingMs = 800.0))
        armJump(p)
        p.input(context(400.0, 0), 250.0, 500.0)
        assertNull(support(p, 300.0, 400.0, 0).clearReason) // 100ms supported.
        p.input(context(600.0, 1), 250.0, 500.0) // New row is still settling.
        p.input(context(800.0, 1), 250.0, 500.0)
        assertNull(support(p, 600.0, 800.0, 1).clearReason) // 300ms, not 500ms.
        p.input(context(1000.0, 1), 250.0, 500.0)
        assertNull(support(p, 600.0, 1000.0, 1).clearReason)
        p.input(context(1200.0, 2), 250.0, 500.0)
        p.input(context(1400.0, 2), 250.0, 500.0)
        assertNull(support(p, 1200.0, 1400.0, 2).clearReason) // 700ms, not 1100ms.
        p.input(context(1600.0, 2), 250.0, 500.0)
        assertEquals("continued_reading_elsewhere", support(p, 1200.0, 1600.0, 2).clearReason)
    }

    @Test fun extractorOnlyUnknownWordAndLineAllowCoarseSupportBridge() {
        for (exclusion in listOf("unassigned_word", "unassigned_line", "ambiguous_word", "ambiguous_line")) {
            val p = policy(ReadingBookmarkPersistenceConfig(minimumVisibleMs = 100.0,
                elsewhereReadingMs = 300.0))
            armJump(p)
            val first = context(400.0, 0, exclusion).copy(validLiveInput = true)
            p.input(first, 250.0, 500.0)
            assertNull(p.support(evidence(300.0, 400.0, 0), first).clearReason)
            p.input(context(600.0, null, exclusion), 250.0, 500.0)
            val next = context(800.0, null, exclusion).copy(validLiveInput = true)
            p.input(next, 250.0, 500.0)
            assertEquals(exclusion, "continued_reading_elsewhere",
                p.support(evidence(600.0, 800.0, 1), next).clearReason)
        }
    }

    @Test fun rawBridgeRejectsMissingOutsideBlinkStaleAndUnorderedInputs() {
        for (boundary in listOf("missing", "outside", "blink", "stale_tick", "backwards", "gap")) {
            val p = policy(ReadingBookmarkPersistenceConfig(minimumVisibleMs = 100.0,
                elsewhereReadingMs = 300.0))
            armJump(p)
            p.input(context(400.0, 0), 250.0, 500.0)
            assertNull(support(p, 300.0, 400.0, 0).clearReason)
            val invalid = when (boundary) {
                "missing" -> context(500.0, null, "no_face")
                "outside" -> context(500.0).let { it.copy(rawSample = it.rawSample!!.copy(rawX = 350.0)) }
                "blink" -> context(500.0, null, "blink")
                "stale_tick" -> context(400.0).copy(nowMs = 700.0)
                "backwards" -> context(390.0).copy(nowMs = 500.0)
                else -> context(700.0)
            }
            p.input(invalid, 250.0, 500.0)
            p.input(context(800.0, 1), 250.0, 500.0)
            assertNull(boundary, support(p, 600.0, 800.0, 1).clearReason)
        }
    }

    @Test fun duplicateInputTicksPreserveButNeverAddSupportedCredit() {
        val p = policy(ReadingBookmarkPersistenceConfig(minimumVisibleMs = 100.0,
            elsewhereReadingMs = 300.0))
        armJump(p)
        p.input(context(400.0, 0), 250.0, 500.0)
        assertNull(support(p, 300.0, 400.0, 0).clearReason)
        repeat(3) { assertNull(p.input(context(400.0, 0), 250.0, 500.0).clearReason) }
        p.input(context(600.0, 1), 250.0, 500.0)
        p.input(context(800.0, 1), 250.0, 500.0)
        assertEquals("continued_reading_elsewhere", support(p, 600.0, 800.0, 1).clearReason)
    }

    @Test fun elsewhereReversalsJumpsAndSegmentChangesRestartCredit() {
        val p = policy(ReadingBookmarkPersistenceConfig(minimumVisibleMs = 100.0,
            elsewhereReadingMs = 500.0))
        armJump(p)
        assertNull(run(p, 300, 700, 1).clearReason)
        assertNull(support(p, 700.0, 800.0, 0).clearReason) // reversal
        assertNull(run(p, 800, 1100, 0).clearReason)
        assertNull(support(p, 1100.0, 1200.0, 2).clearReason) // two-row jump
        assertNull(run(p, 1200, 1500, 2).clearReason)
        assertNull(support(p, 1500.0, 1600.0, 2, 2).clearReason) // new segment
        assertNull(p.support(evidence(1600.0, 1900.0, 2, 2), context(1900.0, 2)).clearReason)
        assertNull(support(p, 1900.0, 2000.0, 2, 2).clearReason)
        assertNull(support(p, 2000.0, 2100.0, 2, 2).clearReason)
        assertNull(support(p, 2100.0, 2200.0, 2, 2).clearReason)
        assertNull(support(p, 2200.0, 2300.0, 2, 2).clearReason)
        assertEquals("continued_reading_elsewhere", support(p, 2300.0, 2400.0, 2, 2).clearReason)
    }

    @Test fun geometryGenerationResetAndAgeInvalidateSavedCue() {
        val p = policy(); armJump(p)
        assertEquals("bookmark_geometry_or_input_excluded",
            p.input(context(400.0).copy(geometrySnapshotId = "new"), 250.0, 500.0).clearReason)
        assertNull(p.savedLine)
        p.reset(); armJump(p)
        assertEquals("bookmark_geometry_or_input_excluded",
            p.support(evidence(300.0, 400.0), context(400.0).copy(generation = 2)).clearReason)
        assertNull(p.savedLine)
        p.reset(); armJump(p)
        assertEquals("bookmark_max_age", p.input(context(30300.0), 250.0, 500.0).clearReason)
        assertNull(p.savedLine)
        p.reset(); assertNull(p.savedLine)
    }

    @Test fun outsideAndUnavailableKeepTheirDistinctDepartureEvidence() {
        val outside = policy(); support(outside, 10.0, 110.0)
        var departure: ReadingAidBookmarkEvidence? = null
        for (t in 200..700 step 50)
            departure = outside.input(context(t.toDouble(), null, "outside_visible_text"), 250.0, 500.0).evidence ?: departure
        assertEquals("sustained_raw_outside_article", departure!!.departureReason)
        assertEquals("capture", departure.departureTimeBasis)
        val missing = policy(); support(missing, 10.0, 110.0)
        for (t in 200..700 step 100)
            departure = missing.input(context(t.toDouble(), null, "no_face"), 250.0, 500.0).evidence ?: departure
        assertEquals("sustained_gaze_unavailable", departure!!.departureReason)
        assertEquals("delivery_elapsed_time", departure.departureTimeBasis)
        assertNull(run(missing, 700, 1100).clearReason)
        missing.input(context(1150.0, null, "outside_visible_text"), 250.0, 500.0)
        assertNull(run(missing, 1200, 2100).clearReason)
        assertEquals("saved_line_return", support(missing, 2100.0, 2200.0).clearReason)
    }

    @Test fun nullPersistenceRetainsImmediateLegacyReturn() {
        val p = ReadingBookmarkPolicy(detector)
        p.support(evidence(10.0, 110.0))
        assertNotNull(p.support(evidence(200.0, 300.0, 0)).evidence)
        assertEquals("saved_line_return", p.support(evidence(300.0, 400.0)).clearReason)
    }
}
