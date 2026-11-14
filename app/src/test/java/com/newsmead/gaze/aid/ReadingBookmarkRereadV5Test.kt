package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Historical timed fixtures plus current progress checks; no human reading validation. */
class ReadingBookmarkRereadV5Test {
    private val geometry = ScanpathGeometry("g", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 200.0, 200.0), 20.0,
        (0..7).map { ScanpathLine(it * 10, it * 10 + 9, 0.0, 200.0,
            it * 20.0, it * 20.0 + 20.0) }, ScanpathTextDirection.LTR)
    private fun context(t: Double, line: Int = 3, exclusion: String? = null) =
        ReadingAidContext(1, "g", 8, line, null, null, t, t, t,
            validLiveInput = exclusion == null, geometry = geometry,
            rawSample = ScanpathSample(t, t, 100.0, 100.0, line, null, null,
                "g", exclusion == null, exclusion))
    private fun policy() = ReadingBookmarkPolicy(ScanpathConfig(), ReadingBookmarkPersistenceConfig(useProgressPersistence = false))
    private fun support(p: ReadingBookmarkPolicy, first: Double, last: Double, line: Int = 3,
                        id: String = "v$first"): ReadingBookmarkPolicy.Change {
        p.input(context(last, line), 250.0, 500.0)
        return p.support(ReadingAidContinuationEvidence("bookmark:$id", 1, line, first, last, last),
            context(last, line))
    }
    private fun visit(p: ReadingBookmarkPolicy, first: Int, line: Int): ReadingAidContinuationEvidence {
        val id = "visit:$first"
        for (t in first..first + 300 step 100) support(p, first.toDouble(), t.toDouble(), line, id)
        return ReadingAidContinuationEvidence(id, 1, line, first.toDouble(), first + 300.0, first + 300.0)
    }
    private fun witness(p: ReadingBookmarkPolicy): ReadingAidRereadEvidence {
        val visits = listOf(0, 1, 2, 3, 4, 0, 1, 2).mapIndexed { i, line -> visit(p, 100 + i * 400, line) }
        return ReadingAidRereadEvidence("r", visits, 0, 4, visits.last().lastCaptureMs)
    }
    private fun loss(p: ReadingBookmarkPolicy, start: Int): ReadingAidBookmarkEvidence? {
        var result: ReadingAidBookmarkEvidence? = null
        for (t in start..start + 500 step 100)
            result = p.input(context(t.toDouble(), exclusion = "no_face"), 250.0, 500.0).evidence ?: result
        return result
    }

    @Test fun briefStartupCannotArmButEstablishedReadingCan() {
        val brief = policy()
        support(brief, 0.0, 150.0)
        assertNull(loss(brief, 200))
        val established = policy()
        for (t in 100..1600 step 100) support(established, t - 100.0, t.toDouble())
        assertEquals("sustained_gaze_unavailable", loss(established, 1700)!!.departureReason)
    }

    @Test fun longAbsenceCannotConsumeRecoveryHold() {
        val p = policy()
        for (t in 100..1600 step 100) support(p, t - 100.0, t.toDouble())
        assertNotNull(loss(p, 1700))
        assertNull(p.input(context(10000.0, exclusion = "no_face"), 250.0, 500.0).clearReason)
        for (t in 10100..13000 step 100) assertNull(support(p, t - 100.0, t.toDouble()).clearReason)
        assertEquals("saved_line_return", support(p, 13000.0, 13100.0).clearReason)
    }

    @Test fun completeTrustedRereadFreezesBButEarlierReadingCanRelease() {
        val p = policy(); val e = witness(p)
        val bookmark = p.reread(e, context(e.confirmedDeliveryMs, 2)).evidence!!
        assertEquals(4, bookmark.source.line)
        assertEquals("bookmark:visit:1700", bookmark.source.evidenceId)
        assertEquals(0, bookmark.departureSupport!!.line)
        assertEquals(e, bookmark.reread)
        assertNull(p.reread(e, context(e.confirmedDeliveryMs, 2)).evidence)
        for (t in 3300..6200 step 100) assertNull(support(p, t - 100.0, t.toDouble(), 2).clearReason)
        assertEquals(4, p.savedLine)
        assertEquals("continued_reading_elsewhere", support(p, 6200.0, 6300.0, 2).clearReason)
    }

    @Test fun returnVisibilityLimitSurvivesNoisyInputAndDuplicateTicksWithoutRetriggering() {
        val p = policy()
        for (t in 100..1600 step 100) support(p, t - 100.0, t.toDouble())
        assertNotNull(loss(p, 1700))
        assertNull(p.input(context(10000.0, exclusion = "no_face"), 250.0, 500.0).clearReason)
        assertNull(p.auditSnapshot()["returned_at_ms"])
        assertNull(p.input(context(10100.0), 250.0, 500.0).clearReason)
        assertEquals(10100.0, p.auditSnapshot()["returned_at_ms"] as Double, 0.0)
        // Repeated/stale coordinates cannot earn recovery or restart the visibility budget.
        for (t in 10200..15000 step 100) assertNull(p.input(
            context(10100.0).copy(nowMs = t.toDouble()), 250.0, 500.0).clearReason)
        assertEquals("bookmark_return_visibility_limit", p.input(
            context(10100.0).copy(nowMs = 15100.0), 250.0, 500.0).clearReason)
        assertEquals(false, p.auditSnapshot()["shown"])
        assertNull(loss(p, 15200))
    }

    @Test fun crossingSavedRowNearRecoveryEndDoesNotAddAnotherRouteHold() {
        val p = policy(); val e = witness(p)
        assertNotNull(p.reread(e, context(e.confirmedDeliveryMs, 2)).evidence)
        for (t in 3300..6100 step 100) assertNull(support(p, t - 100.0, t.toDouble(), 2).clearReason)
        assertNull(support(p, 6100.0, 6200.0, 3).clearReason)
        assertEquals("saved_line_return", support(p, 6200.0, 6300.0, 4).clearReason)
    }

    @Test fun partialUntrustedOverlappingAndGeometryChangedWitnessesCannotTrigger() {
        val p = policy(); val e = witness(p)
        assertNull(p.reread(e.copy(visits = e.visits.take(6)), context(e.confirmedDeliveryMs, 2)).evidence)
        assertNull(p.reread(e.copy(visits = e.visits.mapIndexed { i, v ->
            if (i == 4) v.copy(evidenceId = "untrusted") else v }), context(e.confirmedDeliveryMs, 2)).evidence)
        assertNull(p.reread(e.copy(visits = e.visits.mapIndexed { i, v ->
            if (i == 5) v.copy(firstCaptureMs = e.visits[4].lastCaptureMs) else v }),
            context(e.confirmedDeliveryMs, 2)).evidence)
        p.interruptTransition()
        assertNull(p.reread(e, context(e.confirmedDeliveryMs, 2)).evidence)
        val changed = policy(); val fresh = witness(changed)
        assertNull(changed.reread(fresh, context(fresh.confirmedDeliveryMs, 2).copy(geometrySnapshotId = "new")).evidence)
    }

    @Test fun settlingPresencePreservesHistoryAndPresenceCreditButBoundariesBreakIt() {
        for (boundary in listOf("continuous", "missing", "mapper_rejected", "raw_gap")) {
            val p = policy()
            val visits = mutableListOf<ReadingAidContinuationEvidence>()
            for ((i, line) in listOf(0, 1, 2, 3, 4, 0, 1, 2).withIndex()) {
                val first = 100 + i * 700
                if (i > 0) {
                    val previousEnd = visits.last().lastCaptureMs.toInt()
                    for (t in previousEnd + 100 until first step 100) {
                        if (i == 1 && boundary == "raw_gap") continue
                        val missing = i == 1 && t == previousEnd + 100 && boundary == "missing"
                        p.input(context(t.toDouble(), line, if (missing) "no_face" else null), 250.0, 500.0)
                        if (i == 1 && t == previousEnd + 100 && boundary == "mapper_rejected") {
                            p.support(ReadingAidContinuationEvidence("bookmark:rejected", 1, line,
                                t.toDouble(), t.toDouble(), t.toDouble()),
                                context(t.toDouble(), line).copy(validLiveInput = false))
                        }
                    }
                }
                // A new precise visit starts after row settling. Its first point earns zero dwell.
                support(p, first.toDouble(), first.toDouble(), line, "visit:$first")
                if (i == 1 && boundary == "continuous")
                    assertEquals(700.0, p.auditSnapshot()["anchor_presence_credit_ms"] as Double, 0.0)
                for (t in first + 100..first + 300 step 100)
                    support(p, first.toDouble(), t.toDouble(), line, "visit:$first")
                visits.add(ReadingAidContinuationEvidence("visit:$first", 1, line,
                    first.toDouble(), first + 300.0, first + 300.0))
            }
            val e = ReadingAidRereadEvidence("bridged:$boundary", visits, 0, 4, visits.last().lastCaptureMs)
            val result = p.reread(e, context(e.confirmedDeliveryMs, 2)).evidence
            if (boundary == "continuous") assertNotNull(boundary, result) else assertNull(boundary, result)
        }
    }

    @Test fun fastRowsAndStationaryPresenceBothMatureWithoutLongRowSupport() {
        for (advancing in listOf(false, true)) {
            val p = policy()
            for (t in 100..2100 step 50) {
                val line = if (advancing) minOf(4, (t - 100) / 400) else 0
                p.input(context(t.toDouble(), line), 250.0, 500.0)
                val rowFirst = if (advancing) 100 + line * 400 else 100
                if (t - rowFirst >= 200) {
                    p.support(ReadingAidContinuationEvidence("bookmark:fast:$line", 1, line,
                        rowFirst + 200.0, t.toDouble(), t.toDouble()), context(t.toDouble(), line))
                }
            }
            assertEquals(2000.0, p.auditSnapshot()["anchor_presence_credit_ms"] as Double, 0.0)
            assertNotNull("advancing=$advancing", loss(p, 2200))
        }
    }

    @Test fun presenceCannotInventAnchorOrReuseAnchorAfterRawLineLeavesIt() {
        val noAnchor = policy()
        for (t in 100..1800 step 100) noAnchor.input(context(t.toDouble()), 250.0, 500.0)
        assertNull(loss(noAnchor, 1900))
        val departed = policy()
        for (t in 100..1800 step 100) support(departed, t - 100.0, t.toDouble())
        departed.input(context(1900.0, 4), 250.0, 500.0) // New row has no precise supported anchor yet.
        assertNull(loss(departed, 2000))
    }

    @Test fun fastForwardRecoveryUsesPresenceButClearsOnlyAtFreshSpatialSupport() {
        val p = policy()
        for (t in 100..1800 step 100) support(p, t - 100.0, t.toDouble(), 0)
        assertNotNull(loss(p, 1900))
        for (t in 2500..5600 step 50) {
            val line = minOf(7, (t - 2500) / 400)
            p.input(context(t.toDouble(), line), 250.0, 500.0)
        }
        assertEquals(3100.0, p.auditSnapshot()["recovery_presence_credit_ms"] as Double, 0.0)
        assertEquals(0, p.savedLine) // Input/timers alone never dismiss.
        assertEquals("continued_reading_elsewhere", p.support(
            ReadingAidContinuationEvidence("bookmark:recovered", 1, 7, 5500.0, 5600.0, 5600.0),
            context(5600.0, 7)).clearReason)
    }

    @Test fun agedUiTickBreaksPresenceButPreservesEstablishedDepartureReadiness() {
        val p = policy()
        for (t in 100..1800 step 100) support(p, t - 100.0, t.toDouble())
        p.input(context(1800.0).copy(nowMs = 2200.0), 250.0, 500.0)
        assertEquals(0.0, p.auditSnapshot()["anchor_presence_credit_ms"] as Double, 0.0)
        assertNotNull(loss(p, 2300))
    }

    @Test fun staleTicksRejectedMappingAndLayoutBreakPresenceMaturity() {
        for (boundary in listOf("stale", "rejected", "layout")) {
            val p = policy()
            for (t in 100..1400 step 100) support(p, t - 100.0, t.toDouble())
            when (boundary) {
                "stale" -> p.input(context(1400.0).copy(nowMs = 1700.0), 250.0, 500.0)
                "rejected" -> p.input(context(1500.0, exclusion = "unassigned_line"), 250.0, 500.0)
                else -> p.input(context(1500.0).copy(geometrySnapshotId = "other"), 250.0, 500.0)
            }
            for (t in 1800..2200 step 100) support(p, t - 100.0, t.toDouble())
            assertNull(boundary, loss(p, 2300))
        }
    }

    @Test fun duplicateSupportAndCaptureGapsCannotEarnMaturity() {
        val p = policy()
        repeat(20) { support(p, 0.0, 150.0) }
        assertNull(loss(p, 200))
        for (t in 1000..4000 step 500) support(p, t.toDouble(), t + 100.0)
        assertNull(loss(p, 4200))
    }
    private fun currentPolicy() = ReadingBookmarkPolicy(ScanpathConfig(), ReadingBookmarkPersistenceConfig())
    private fun established(p: ReadingBookmarkPolicy, line: Int = 3) {
        for (t in 100..1800 step 100) support(p, t - 100.0, t.toDouble(), line)
    }
    private fun currentBookmark(): ReadingBookmarkPolicy = currentPolicy().also {
        established(it); assertNotNull(loss(it, 1900))
    }

    @Test fun currentEarlierAndSameRowNeverExpireOrReleaseOnResidence() {
        for (line in listOf(0, 3)) {
            val p = currentBookmark()
            for (t in 2500..35000 step 100)
                assertNull(support(p, t - 100.0, t.toDouble(), line).clearReason)
            assertEquals(3, p.savedLine)
            assertEquals(true, p.auditSnapshot()["shown"])
            assertEquals(0.0, p.rereadProgress, 0.0)
        }
    }

    @Test fun currentSupportedAdjacentPassageClearsWithoutVisibilityHold() {
        val p = currentBookmark()
        assertNull(support(p, 2500.0, 2600.0, 3).clearReason)
        assertEquals("supported_forward_passage", support(p, 2700.0, 2800.0, 4).clearReason)
        assertEquals(false, p.auditSnapshot()["shown"])
    }

    @Test fun currentJumpAndStationaryLaterRowCannotProvePassage() {
        for (later in listOf(4, 5)) {
            val p = currentBookmark()
            for (t in 2500..35000 step 100)
                assertNull(support(p, t - 100.0, t.toDouble(), later).clearReason)
            assertEquals(3, p.savedLine)
            assertEquals("supported_forward_passage",
                support(p, 35100.0, 35200.0, later + 1).clearReason)
        }
    }

    @Test fun currentProgressIsMonotonicAcrossRereadingAndMissingness() {
        val p = currentBookmark()
        support(p, 2500.0, 2600.0, 0)
        assertEquals(0.0, p.rereadProgress, 0.0)
        support(p, 2700.0, 2800.0, 1)
        assertEquals(1.0 / 3.0, p.rereadProgress, 0.00001)
        support(p, 2900.0, 3000.0, 0)
        p.input(context(3100.0, exclusion = "no_face"), 250.0, 500.0)
        support(p, 3200.0, 3300.0, 2) // Jump after a boundary cannot earn progression.
        assertEquals(1.0 / 3.0, p.rereadProgress, 0.00001)
        assertNull(support(p, 3400.0, 3500.0, 3).clearReason)
        assertEquals(1.0, p.rereadProgress, 0.0)
        assertEquals(3, p.savedLine)
        assertEquals("supported_forward_passage", p.diagnosticRoute)
    }

    @Test fun currentBoundariesAndDuplicatesCannotBridgePassage() {
        for (boundary in listOf("missing", "rejected", "time", "gap", "duplicate")) {
            val p = currentBookmark()
            support(p, 2500.0, 2600.0, 3)
            when (boundary) {
                "missing" -> p.input(context(2700.0, exclusion = "no_face"), 250.0, 500.0)
                "rejected" -> p.support(ReadingAidContinuationEvidence("bad", 1, 3, 2600.0, 2700.0, 2700.0),
                    context(2700.0).copy(validLiveInput = false))
                "time" -> p.input(context(2500.0), 250.0, 500.0)
                "gap" -> Unit
                else -> assertNull(support(p, 2500.0, 2600.0, 4).clearReason)
            }
            val first = if (boundary == "gap") 3000.0 else 2800.0
            if (boundary != "duplicate") assertNull(support(p, first, first + 100.0, 4).clearReason)
            else assertEquals(3, p.savedLine)
        }
    }

    @Test fun currentBlinkLatchAndUnsettledRowPreserveFreshTrustworthySource() {
        val p = currentPolicy(); established(p)
        p.input(context(1850.0, exclusion = "blink"), 250.0, 500.0)
        p.input(context(1900.0, 4), 250.0, 500.0)
        val bookmark = loss(p, 2000)!!
        assertEquals(3, bookmark.source.line)
        assertEquals(1800.0, bookmark.source.lastCaptureMs, 0.0)
        val stale = currentPolicy(); established(stale)
        assertNull(loss(stale, 2200))
    }

    @Test fun currentBackwardDepartureNeedsForwardSupportAndStableBroadEarlierVisit() {
        val p = currentPolicy(); established(p, 3)
        support(p, 1900.0, 2000.0, 4, "frontier")
        assertNull(support(p, 2100.0, 2200.0, 0, "earlier").evidence)
        val bookmark = support(p, 2100.0, 2300.0, 0, "earlier").evidence!!
        assertEquals("supported_backward_reading", bookmark.departureReason)
        assertEquals(4, bookmark.source.line)
        assertEquals(0, bookmark.departureSupport!!.line)
        assertNull(support(p, 2100.0, 2400.0, 0, "earlier").evidence)
        val ordinary = currentPolicy(); established(ordinary, 0)
        for (line in 1..4) assertNull(support(ordinary, 1800.0 + line * 200,
            1900.0 + line * 200, line).evidence)
        val jitter = currentPolicy(); established(jitter, 3)
        support(jitter, 1900.0, 2000.0, 4)
        assertNull(support(jitter, 2100.0, 2300.0, 3).evidence)
        assertNull(support(jitter, 2100.0, 2400.0, 3).evidence)
    }

    @Test fun currentContinuousRawSettlingBridgesExactSupportForBackwardTriggerProgressAndPassage() {
        // Each exact support starts 350ms after the previous end; fresh raw input covers settling.
        fun settled(p: ReadingBookmarkPolicy, previousEnd: Int, line: Int,
                    boundary: String = "continuous"): ReadingBookmarkPolicy.Change {
            for (t in previousEnd + 50..previousEnd + 450 step 50) {
                if (boundary == "gap" && t < previousEnd + 450) continue
                p.input(context(t.toDouble(), line,
                    if (boundary == "missing" && t == previousEnd + 200) "no_face" else null),
                    250.0, 500.0)
            }
            return support(p, previousEnd + 350.0, previousEnd + 450.0, line,
                "settled:$previousEnd:$line")
        }
        for (boundary in listOf("continuous", "gap", "missing")) {
            val p = currentPolicy(); established(p, 3)
            assertNull(settled(p, 1800, 4, boundary).evidence)
            assertNull(settled(p, 2250, 0).evidence)
            p.input(context(2750.0, 0), 250.0, 500.0)
            val bookmark = support(p, 2600.0, 2800.0, 0, "settled:2250:0").evidence
            if (boundary != "continuous") {
                assertNull(boundary, bookmark)
                continue
            }
            assertEquals("supported_backward_reading", bookmark!!.departureReason)
            assertEquals(4, bookmark.source.line)
            // The trigger's earlier visit is retained as location, not pre-earned forward credit.
            support(p, 2600.0, 2850.0, 0, "settled:2250:0")
            var end = 2850
            for (line in 1..4) {
                assertNull(settled(p, end, line).clearReason)
                assertEquals(line / 4.0, p.rereadProgress, 0.0)
                assertEquals(4, p.savedLine)
                end += 450
            }
            assertEquals("supported_forward_passage", settled(p, end, 5).clearReason)
        }
        for (boundary in listOf("gap", "missing")) {
            val p = currentBookmark()
            support(p, 2500.0, 2600.0, 0)
            assertNull(settled(p, 2600, 1, boundary).clearReason)
            assertEquals(0.0, p.rereadProgress, 0.0)
            support(p, 3100.0, 3200.0, 3)
            assertNull(settled(p, 3200, 4, boundary).clearReason)
            assertEquals(3, p.savedLine)
        }
    }

    @Test fun currentConsecutiveCuesStartWithFreshProgressAndRereadOrigin() {
        val p = currentBookmark()
        support(p, 2500.0, 2600.0, 0)
        support(p, 2700.0, 2800.0, 1)
        assertEquals(1.0 / 3.0, p.rereadProgress, 0.00001)
        support(p, 2900.0, 3000.0, 3)
        assertEquals("supported_forward_passage", support(p, 3100.0, 3200.0, 4).clearReason)
        assertNotNull(loss(p, 3300))
        assertEquals(4, p.savedLine)
        assertEquals(0.0, p.rereadProgress, 0.0)
        support(p, 3900.0, 4000.0, 2)
        support(p, 4100.0, 4200.0, 3)
        assertEquals(0.5, p.rereadProgress, 0.0)
    }

    @Test fun currentRawMapperRejectionBreaksBackwardWitnessButPreservesArmedSource() {
        for (reason in listOf("unassigned_line", "unassigned_word", "ambiguous_line", "ambiguous_word")) {
            val p = currentPolicy(); established(p, 3)
            support(p, 1900.0, 2000.0, 4, "frontier")
            p.input(context(2050.0, 4, reason), 250.0, 500.0)
            assertEquals(reason, 4, p.savedLine)
            assertEquals(reason, true, p.auditSnapshot()["anchor_armed"])
            assertNull(reason, support(p, 2100.0, 2200.0, 0, "earlier").evidence)
            assertNull(reason, support(p, 2100.0, 2300.0, 0, "earlier").evidence)
        }
    }

}
