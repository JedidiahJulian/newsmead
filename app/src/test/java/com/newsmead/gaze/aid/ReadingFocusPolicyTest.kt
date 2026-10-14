package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic policy contracts, not evidence of gaze accuracy or human aid benefit. */
class ReadingFocusPolicyTest {
    private val config = ReadingAidPolicyConfig.definitionPrototype()
    private val geometry = ScanpathGeometry("g", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 100.0, 100.0), 20.0,
        (0..3).map { ScanpathLine(it * 10, it * 10 + 9, 0.0, 100.0, it * 20.0, it * 20.0 + 20.0) })

    // Raw deliberately disagrees: only the declared selected stream may confirm presence.
    private fun c(delivery: Double, capture: Double? = delivery, y: Double? = 10.0,
                  reason: String? = null, eligible: Boolean = false, x: Double? = 10.0) =
        ReadingAidContext(1, "g", 4, null, null, null, delivery, capture, delivery,
            validLiveInput = eligible, geometry = geometry,
            rawSample = ScanpathSample(capture, delivery, 500.0, 500.0, null, null, null,
                "g", eligible, reason, displayX = x, displayY = y,
                coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED))
    private fun support(t: Double, line: Int = 0, first: Double = t - 20.0, segment: Int = 1) =
        ReadingAidContinuationEvidence("s:$t:$line", segment, line, first, t, t)
    private fun prime(p: ReadingFocusPolicy, start: Double = 0.0) {
        for (offset in listOf(0.0, 50.0, 100.0)) p.input(c(start + offset))
    }
    private fun missing(p: ReadingFocusPolicy, t: Double, reason: String = "no_face") =
        p.input(c(t, capture = null, reason = reason, x = null, y = null))
    private fun episode(p: ReadingFocusPolicy, start: Double) {
        missing(p, start); missing(p, start + 400.0, "stale")
        for ((index, offset) in listOf(500.0, 550.0, 600.0).withIndex())
            p.input(c(start + offset, y = index * 20.0 + 10.0))
    }
    private fun activated(p: ReadingFocusPolicy) {
        prime(p)
        for (t in listOf(200.0, 1000.0, 1800.0)) episode(p, t)
        assertTrue(p.hasDemand)
    }
    private fun evidence(p: ReadingFocusPolicy, t: Double = 2400.0,
                         preferences: ReaderAidPreferences = ReaderAidPreferences()) =
        p.support(support(t), c(t), preferences)
    private fun visit(p: ReadingFocusPolicy, t: Double, line: Int, segment: Int = 1,
                      first: Double = t - 20.0): ReadingAidFocusEvidence? {
        p.input(c(t, y = line * 20.0 + 10.0))
        return p.support(support(t, line, first, segment), c(t))
    }

    @Test fun movingUnassignedPresenceNeedsThreeSamplesAnd100CaptureMs() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        prime(p); missing(p, 200.0)
        p.input(c(700.0, capture = 680.0, y = 10.0))
        p.input(c(750.0, capture = 730.0, y = 70.0))
        assertTrue(completed.isEmpty())
        p.input(c(799.0, capture = 779.0, y = 30.0))
        assertTrue(completed.isEmpty())
        p.input(c(800.0, capture = 780.0, y = 50.0))
        val e = completed.single()
        assertNull(e.source); assertNull(e.recovery)
        assertEquals(700.0, e.firstRecoveryDeliveryMs, 0.0)
        assertEquals(800.0, e.completedDeliveryMs, 0.0)
        val r = e.presenceRecovery!!
        assertEquals(680.0, r.firstCaptureMs, 0.0)
        assertEquals(780.0, r.lastCaptureMs, 0.0)
        assertEquals(700.0, r.firstDeliveryMs, 0.0)
        assertEquals(800.0, r.confirmedDeliveryMs, 0.0)
        assertEquals(4, r.sampleCount)
        assertEquals("article_smoothed_v1", r.coordinateStream)
        assertEquals(1L, r.generation); assertEquals("g", r.geometrySnapshotId)
    }

    @Test fun twoSamplesSpanning100MsStillNeedAThirdFreshSample() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        prime(p); missing(p, 200.0)
        p.input(c(700.0)); p.input(c(800.0))
        assertTrue(completed.isEmpty())
        p.input(c(801.0))
        assertEquals(3, completed.single().presenceRecovery!!.sampleCount)
    }

    @Test fun duplicateTicksAndCaptureTimestampsCannotInventPresenceSamples() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        prime(p); missing(p, 200.0)
        val first = c(700.0)
        p.input(first)
        for (t in listOf(710.0, 720.0, 730.0)) p.input(first.copy(nowMs = t))
        p.input(c(740.0, capture = 700.0))
        p.input(c(750.0)); p.input(c(799.0, capture = 750.0))
        assertTrue(completed.isEmpty())
        p.input(c(800.0))
        assertEquals(3, completed.single().presenceRecovery!!.sampleCount)
    }

    @Test fun duplicateSelectedCaptureCannotPrematurelyEndLossTiming() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        prime(p); missing(p, 200.0)
        p.input(c(300.0, capture = 100.0))
        prime(p, 700.0)
        assertEquals(700.0, completed.single().firstRecoveryDeliveryMs, 0.0)
    }

    @Test fun captureGapAt125MsIsAllowedButLargerGapRestartsPresence() {
        for (gap in listOf(125.0, 125.001)) {
            val completed = mutableListOf<ReadingAidFocusEpisode>()
            val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
            prime(p); missing(p, 200.0)
            p.input(c(700.0)); p.input(c(700.0 + gap)); p.input(c(750.0 + gap))
            if (gap == 125.0) assertEquals(1, completed.size)
            else {
                assertTrue(completed.isEmpty())
                p.input(c(800.0 + gap))
                assertEquals(700.0 + gap, completed.single().presenceRecovery!!.firstCaptureMs, 0.0)
            }
        }
    }

    @Test fun selectedCoordinatesHaveNoRawFallbackAndStaleSamplesCannotConfirm() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        prime(p); missing(p, 200.0)
        for (t in listOf(700.0, 750.0, 800.0)) {
            val absent = c(t, x = null, y = null)
            p.input(absent.copy(rawSample = absent.rawSample!!.copy(rawX = 10.0, rawY = 10.0)))
        }
        for (t in listOf(850.0, 900.0, 950.0)) p.input(c(t, capture = t - 251.0))
        assertTrue(completed.isEmpty())
        prime(p, 1000.0)
        assertEquals(1, completed.size)
    }

    @Test fun oneTrackedBlipDoesNotSplitOngoingLossIntoMultipleEpisodes() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        prime(p); missing(p, 200.0)
        p.input(c(700.0)); missing(p, 750.0, "eye_area")
        prime(p, 1300.0)
        val e = completed.single()
        assertEquals(200.0, e.missingFirstDeliveryMs, 0.0)
        assertEquals(750.0, e.missingLastDeliveryMs, 0.0)
        assertEquals(700.0, e.firstRecoveryDeliveryMs, 0.0)
        assertEquals(listOf("no_face", "eye_area"), e.missingReasons)
        assertEquals(2, e.missingSampleCount)
        assertEquals("delivery_elapsed_time", e.timeBasis)
        assertNull(e.missingFirstCaptureMs); assertNull(e.missingLastCaptureMs)
    }

    @Test fun trackedOutsideReturnStopsLossClockAt500MsBoundary() {
        for (duration in listOf(499.999, 500.0)) {
            val completed = mutableListOf<ReadingAidFocusEpisode>()
            val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
            prime(p); missing(p, 200.0)
            p.input(c(200.0 + duration, x = 150.0, reason = "outside_visible_text"))
            prime(p, 1500.0)
            if (duration < 500.0) assertTrue(completed.isEmpty())
            else assertEquals(700.0, completed.single().firstRecoveryDeliveryMs, 0.0)
        }
    }

    @Test fun separatedShortLossesNeverCombineDespiteDelayedArticleReturn() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        prime(p)
        for (start in listOf(200.0, 1000.0, 1800.0, 2600.0)) {
            missing(p, start)
            p.input(c(start + 300.0, x = 150.0, reason = "outside_visible_text"))
            prime(p, start + 400.0)
        }
        assertTrue(completed.isEmpty()); assertFalse(p.hasDemand)
    }

    @Test fun threeEpisodesCreateDemandWithoutAnchorAndFourthRaisesStrength() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        activated(p)
        assertNull(p.currentEvidence(c(2400.0)))
        assertEquals("waiting_for_supported_anchor", p.diagnostics(2400.0)["reason"])
        val first = evidence(p)!!
        assertEquals(3, first.episodeCount); assertEquals(0.5, first.initialStrength, 0.0)
        assertEquals("A4_RETURN_PRESENCE_FOCUS_V2", first.ruleId)
        assertTrue(first.episodes.all { it.source == null && it.recovery == null && it.presenceRecovery != null })
        episode(p, 2600.0)
        val strong = evidence(p, 3200.0)!!
        assertEquals(4, strong.episodeCount); assertEquals(1.0, strong.initialStrength, 0.0)
        assertEquals(4, completed.map { it.episodeId }.distinct().size)
        assertEquals(first.support, strong.episodes.last().source)
    }

    @Test fun windowUsesConfirmationDeliveryAndIncludesExact30SecondBoundary() {
        for (extra in listOf(0.0, 0.001)) {
            val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
            prime(p); episode(p, 200.0); episode(p, 1000.0)
            // First completion800; third completion30800 (+extra).
            episode(p, 30200.0 + extra)
            assertEquals(if (extra == 0.0) 3 else 2,
                p.diagnostics(30800.0 + extra)["recent_episode_count"])
            assertEquals(extra == 0.0, p.hasDemand)
        }
    }

    @Test fun readingHoldFadeAndReaderCapsUseEligibleCaptureDeltas() {
        val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
        activated(p)
        evidence(p)
        assertNull(p.currentEvidence(c(2400.0), ReaderAidPreferences(maxStrength = 0.0)))
        assertNull(p.currentEvidence(c(2400.0), ReaderAidPreferences(
            adaptiveStrength = false, manualStrength = 0.0)))
        for (t in 2500..12400 step 100) {
            p.input(c(t.toDouble(), eligible = true))
            val e = evidence(p, t.toDouble())
            val recovery = t - 2400.0
            if (recovery == 3000.0) assertEquals(1.0, e!!.recoveryFactor, 0.0)
            if (recovery == 6500.0) {
                assertEquals(0.5, e!!.recoveryFactor, 1e-12)
                assertEquals(6500.0, e.observedRecoveryMs, 0.0)
                assertEquals(3000.0, e.recoveryHoldMs, 0.0)
                assertEquals(7000.0, e.recoveryFadeMs, 0.0)
                for (prefs in listOf(ReaderAidPreferences(maxStrength = 0.25),
                    ReaderAidPreferences(adaptiveStrength = false, manualStrength = 0.8))) {
                    val capped = p.currentEvidence(c(t.toDouble()), prefs)!!
                    val base = if (prefs.adaptiveStrength) minOf(prefs.maxStrength, capped.initialStrength)
                        else minOf(prefs.maxStrength, prefs.manualStrength)
                    assertEquals(if (prefs.adaptiveStrength) 0.125 else 0.4,
                        base * capped.recoveryFactor, 1e-12)
                }
            }
            if (recovery == 10000.0) assertNull(e)
        }
        assertFalse(p.hasDemand)
    }

    @Test fun cappedAndManualRoutesSelectFreshAlternationAfterInterruptionHasFaded() {
        for (prefs in listOf(ReaderAidPreferences(maxStrength = 0.25),
            ReaderAidPreferences(adaptiveStrength = false, manualStrength = 0.8))) {
            val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
            activated(p)
            for (t in 2500..8900 step 100) {
                p.input(c(t.toDouble(), eligible = true)); evidence(p, t.toDouble())
            }
            assertEquals(0.5, p.currentEvidence(c(8900.0))!!.recoveryFactor, 1e-12)
            p.breakLineSequence()
            for ((i, line) in listOf(0, 1, 0, 1).withIndex()) visit(p, 9000.0 + i * 100, line)
            val selected = p.currentEvidence(c(9300.0), prefs)!!
            assertEquals("A4_LINE_VISIT_ALTERNATION_FOCUS_V2", selected.ruleId)
            assertEquals(0.0, selected.observedRecoveryMs, 0.0)
            assertEquals(1, selected.alternations.size)
            assertTrue(selected.episodes.isEmpty())
        }
    }

    @Test fun outsideMissingUnsupportedAndLongGapsDoNotAdvanceReadingRecovery() {
        val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
        activated(p); evidence(p)
        p.input(c(2500.0, eligible = true))
        val before = p.currentEvidence(c(2500.0))!!.observedRecoveryMs
        p.input(c(2600.0, x = 150.0, reason = "outside_visible_text", eligible = true))
        p.input(c(2700.0, eligible = false))
        missing(p, 2800.0)
        p.input(c(2850.0, eligible = true)) // Short loss ends; no episode completed.
        p.input(c(5000.0, eligible = true))
        assertEquals(before, evidence(p, 5000.0)!!.observedRecoveryMs, 0.0)
        p.input(c(5100.0, eligible = true))
        assertEquals(before + 100.0, evidence(p, 5100.0)!!.observedRecoveryMs, 0.0)
    }

    @Test fun freshPresenceExtendsGraceButDuplicateTicksDoNotRenewIt() {
        val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
        activated(p); evidence(p)
        p.input(c(2500.0))
        assertTrue(p.displaySupported(3500.0))
        p.input(c(2500.0).copy(nowMs = 3500.0))
        assertFalse(p.displaySupported(3500.001))
    }

    @Test fun pauseRetainsHistoryButClearsPendingAnchorAndSequenceWhileResetClearsHistory() {
        val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
        prime(p); episode(p, 200.0); episode(p, 1000.0)
        visit(p, 1700.0, 0); visit(p, 1800.0, 1); visit(p, 1900.0, 0)
        missing(p, 2000.0); p.pause()
        prime(p, 2600.0)
        assertEquals(2, p.diagnostics(2700.0)["recent_episode_count"])
        assertFalse(p.hasDemand); assertNull(visit(p, 2800.0, 1))
        episode(p, 3000.0)
        assertTrue(p.hasDemand)
        p.pause()
        assertNull(p.currentEvidence(c(3600.0))); assertFalse(p.displaySupported(3600.0))
        assertEquals("waiting_for_supported_anchor", p.diagnostics(3600.0)["reason"])
        assertEquals(3, evidence(p, 3700.0)!!.episodeCount)
        p.reset()
        assertFalse(p.hasDemand)
        assertEquals(0, p.diagnostics(3700.0)["recent_episode_count"])
    }

    @Test fun fourSupportedVisitsIncludeOngoingFourthWithoutFinalizedCycleAndNeverOverlap() {
        val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
        assertNull(visit(p, 100.0, 0)); assertNull(visit(p, 200.0, 1)); assertNull(visit(p, 300.0, 0))
        val first = visit(p, 400.0, 1, first = 380.0)!!
        assertEquals("A4_LINE_VISIT_ALTERNATION_FOCUS_V2", first.ruleId)
        assertEquals(0.5, first.initialStrength, 0.0)
        assertTrue(first.cycleEventIds.isEmpty()); assertTrue(first.cycleFixationIndices.isEmpty())
        val a = first.alternations.single()
        assertEquals(listOf(0, 1, 0, 1), a.visits.map { it.line })
        assertEquals(400.0, a.confirmedDeliveryMs, 0.0)
        visit(p, 450.0, 1, first = 380.0) // Same ongoing visit extended.
        for ((t, line) in listOf(500.0 to 0, 600.0 to 1, 700.0 to 0)) visit(p, t, line)
        assertEquals(1, p.currentEvidence(c(700.0))!!.alternations.size)
        val second = visit(p, 800.0, 1)!!
        assertEquals(1.0, second.initialStrength, 0.0); assertEquals(2, second.alternations.size)
        assertTrue(second.alternations[0].visits.map { it.evidenceId }.toSet()
            .intersect(second.alternations[1].visits.map { it.evidenceId }.toSet()).isEmpty())
    }

    @Test fun alternationWindowIncludesEightSecondsButRejectsLongerAndOtherPatterns() {
        for (extra in listOf(0.0, 0.001)) {
            val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
            p.support(support(100.0, 0, first = 0.0), c(100.0))
            p.support(support(2000.0, 1), c(2000.0))
            p.support(support(4000.0, 0), c(4000.0))
            val e = p.support(support(8000.0 + extra, 1), c(8000.0 + extra))
            assertEquals(extra == 0.0, e != null)
        }
        val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
        for ((i, line) in listOf(0, 1, 2, 1).withIndex()) visit(p, 100.0 * (i + 1), line)
        assertFalse(p.hasDemand)
    }

    @Test fun unavailableOutsideCaptureGapSegmentAndPendingBoundariesPreventVisitBridging() {
        for (boundary in listOf("missing", "outside", "capture_gap", "segment", "pending", "null_sample")) {
            val p = ReadingFocusPolicy(config, ScanpathConfig()) {}
            visit(p, 100.0, 0); visit(p, 200.0, 1); visit(p, 300.0, 0)
            when (boundary) {
                "missing" -> missing(p, 350.0)
                "outside" -> p.input(c(350.0, x = 150.0, reason = "outside_visible_text"))
                "pending" -> p.breakLineSequence()
                "null_sample" -> p.input(c(350.0).copy(rawSample = null))
            }
            assertNull(visit(p, if (boundary == "capture_gap") 426.0 else 400.0,
                1, segment = if (boundary == "segment") 2 else 1))
            assertFalse("Boundary $boundary must break the sequence", p.hasDemand)
        }
    }

    @Test fun nullSampleClearsUnconfirmedPresenceWithoutErasingCompletedEpisodes() {
        val completed = mutableListOf<ReadingAidFocusEpisode>()
        val p = ReadingFocusPolicy(config, ScanpathConfig(), completed::add)
        prime(p); episode(p, 200.0)
        missing(p, 1000.0)
        p.input(c(1500.0)); p.input(c(1550.0))
        p.input(c(1575.0).copy(rawSample = null))
        p.input(c(1600.0))
        assertEquals(1, completed.size)
        p.input(c(1650.0)); p.input(c(1700.0))
        assertEquals(2, completed.size)
        assertEquals(1600.0, completed.last().presenceRecovery!!.firstCaptureMs, 0.0)
        assertEquals(2, p.diagnostics(1700.0)["recent_episode_count"])
    }
}
