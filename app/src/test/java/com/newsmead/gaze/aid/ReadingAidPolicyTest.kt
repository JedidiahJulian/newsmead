package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathFixation
import org.junit.Assert.*
import org.junit.Test

class ReadingAidPolicyTest {
    private fun context(t: Double, line: Int? = 0, start: Int? = 0, end: Int? = 2,
                        generation: Long = 1, geometry: String = "g1", valid: Boolean = true) =
        ReadingAidContext(generation, geometry, 3, line, start, end, t, t - 10, t, valid)

    private fun candidate(t: Double, start: Double = t - 100, segment: Int = 1,
                          line: Int = 0, wordStart: Int = 0, wordEnd: Int = 2,
                          id: String = "candidate:$t") = ScanpathCandidateEvidence(
        id, segment, start, t - 10, t - 10 - start, 3, wordStart, wordEnd, line,
        1.0, 1.0, "g1", t, "trigger_observation_delivery")

    private fun fixation(i: Int, t: Double, line: Int = 0, start: Int = 0,
                         end: Int = 2, segment: Int = 1) = ScanpathFixation(
        i, segment, t - 100, t - 10, 90.0, 3, 10.0, 10.0, 0.0,
        start, end, 1.0, line, 1.0, false, false, "g1", t,
        "trigger_observation_delivery")

    private fun longFixation(i: Int, t: Double) = fixation(i, t).copy(
        startMs = t - 260, durationMs = 250.0)

    private fun event(id: String, pattern: String, resolution: String, f: ScanpathFixation,
                      indices: List<Int> = listOf(f.index), metrics: Map<String, Any?> = emptyMap()) =
        ScanpathEvent(id, pattern, resolution, indices, f.startMs, f.endMs,
            f.evidenceAvailableMs, "trigger_observation_delivery", metrics)

    private fun chosen(ds: List<ReadingAidDecision>, type: ReadingAidType) =
        ds.singleOrNull { it.status == ReadingAidDecisionStatus.CHOSEN && it.action?.aidType == type }?.action

    @Test fun lineGuideTracksReliableCandidateAndInvalidInputClearsImmediately() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        assertNotNull(chosen(p.onObservation(candidate(110.0), context(110.0)), ReadingAidType.CURRENT_LINE))
        assertTrue(p.onObservation(candidate(120.0), context(120.0)).isEmpty())
        val moved = p.onObservation(candidate(240.0, line = 1), context(240.0, 1, 4, 6))
        assertTrue(moved.any { it.status == ReadingAidDecisionStatus.CLEARED })
        assertEquals(1, chosen(moved, ReadingAidType.CURRENT_LINE)?.anchor?.line)
        assertTrue(p.onObservation(null, context(250.0, 1, 4, 6, valid = false))
            .any { it.status == ReadingAidDecisionStatus.CLEARED && it.reason == "invalid_live_input" })
        assertTrue(p.activeActions().isEmpty())
    }

    @Test fun sweepCueIsReactiveLandingAndEventDeduplicates() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        val f = fixation(2, 210.0, line = 1, start = 4, end = 6)
        val e = event("p09", "P09", "line", f, listOf(1, 2), mapOf("status" to "observed"))
        val c = context(210.0, 1, 4, 6)
        val action = chosen(p.onEvent(e, f, c), ReadingAidType.NEXT_LINE_START)!!
        assertEquals(1, action.anchor.line)
        assertEquals(listOf("W24", "S19"), action.sourceIds)
        assertTrue(p.onEvent(e, f, c).isEmpty())
        assertTrue(p.onEvent(e.copy(eventId = "p09b", evidenceAvailableMs = null), f, c)
            .any { it.reason == "late_or_unavailable_evidence" })
    }

    @Test fun wordSupportUsesOnlyCurrentVisitAndProvisionalPrefix() {
        val two = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        val f0 = longFixation(0, 310.0)
        val f1 = longFixation(1, 570.0)
        two.onEvent(event("two-w0", "P12", "word", f0,
            metrics = mapOf("kind" to "residence_update", "observed_span_ms" to 250.0)),
            f0, context(310.0))
        two.onEvent(event("two-refix", "P04", "word", f1, listOf(0, 1)), f1, context(570.0))
        two.onEvent(event("two-w1", "P12", "word", f1, listOf(0, 1),
            mapOf("kind" to "residence_update", "observed_span_ms" to 500.0)),
            f1, context(570.0))
        assertNull(chosen(two.onObservation(candidate(850.0, start = 565.0), context(850.0)),
            ReadingAidType.WORD_MAGNIFIER))

        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        val f2 = longFixation(2, 830.0)
        p.onEvent(event("w0", "P12", "word", f0,
            metrics = mapOf("kind" to "residence_update", "observed_span_ms" to 250.0)),
            f0, context(310.0))
        p.onEvent(event("refix1", "P04", "word", f1, listOf(0, 1)), f1, context(570.0))
        p.onEvent(event("w1", "P12", "word", f1, listOf(0, 1),
            mapOf("kind" to "residence_update", "observed_span_ms" to 500.0)),
            f1, context(570.0))
        p.onEvent(event("refix2", "P04", "word", f2, listOf(1, 2)), f2, context(830.0))
        val base = chosen(p.onEvent(event("w2", "P12", "word", f2, listOf(0, 1, 2),
            mapOf("kind" to "residence_update", "observed_span_ms" to 750.0)),
            f2, context(830.0)), ReadingAidType.WORD_MAGNIFIER)!!
        val prefix = candidate(950.0, start = 825.0)
        val stronger = chosen(p.onObservation(prefix, context(950.0)), ReadingAidType.WORD_MAGNIFIER)!!
        assertTrue(stronger.strength > base.strength)
        assertEquals(listOf(prefix.candidateId), stronger.evidenceIds)
        assertEquals(listOf("refix1", "refix2", "w2"), stronger.eventIds)
        assertEquals(0, stronger.anchor.wordStartUtf16)
        val other = fixation(3, 1080.0, start = 4, end = 6)
        p.onEvent(event("w3", "P12", "word", other, listOf(3),
            mapOf("kind" to "residence_update", "observed_span_ms" to 90.0)), other,
            context(1080.0, start = 4, end = 6))
        assertFalse(p.activeActions().any { it.aidType == ReadingAidType.WORD_MAGNIFIER })
    }

    @Test fun wordAmbiguityPreservesLineWhileGlobalBreakClearsAll() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        p.onObservation(candidate(110.0), context(110.0))
        val f = fixation(0, 210.0)
        val wordBreak = event("break-word", "P13", "word", f,
            metrics = mapOf("reason" to "unassigned_word"))
        p.onEvent(wordBreak, f, context(210.0, start = null, end = null))
        assertTrue(p.activeActions().any { it.aidType == ReadingAidType.CURRENT_LINE })
        val global = event("break-all", "P13", "line", f,
            metrics = mapOf("reason" to "invalid_sample"))
        assertTrue(p.onEvent(global, f, context(220.0, start = null, end = null))
            .any { it.status == ReadingAidDecisionStatus.CLEARED })
        assertTrue(p.activeActions().isEmpty())
    }

    @Test fun readerLimitsEpochExpiryAndCycleAlone() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype(),
            ReaderAidPreferences(focusRegion = true, adaptiveStrength = false,
                manualStrength = 0.7, maxStrength = 0.4))
        val line = chosen(p.onObservation(candidate(110.0), context(110.0)), ReadingAidType.CURRENT_LINE)!!
        assertEquals(0.4, line.strength, 0.0)
        val f = fixation(1, 210.0)
        val cycle = event("cycle", "P11", "line", f, metrics = mapOf("kind" to "alternation_abab"))
        assertNull(chosen(p.onEvent(cycle, f, context(210.0)), ReadingAidType.FOCUS_REGION))
        assertTrue(p.tick(context(1700.0)).any { it.status == ReadingAidDecisionStatus.EXPIRED })
        p.onObservation(candidate(1800.0), context(1800.0))
        assertTrue(p.tick(context(1810.0, generation = 2))
            .any { it.reason == "generation_or_geometry_changed" })
        assertTrue(p.activeActions().isEmpty())
    }

    @Test fun focusRequiresReaderOptInObservedResidenceAndConfiguredSlowProgression() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype(),
            ReaderAidPreferences(focusRegion = true))
        val f0 = fixation(0, 210.0)
        p.onEvent(event("cycle", "P11", "line", f0), f0, context(210.0))
        val f1 = fixation(1, 1210.0)
        p.onEvent(event("residence", "P12", "line", f1,
            metrics = mapOf("kind" to "residence_update", "observed_span_ms" to 1100.0)),
            f1, context(1210.0))
        assertFalse(p.activeActions().any { it.aidType == ReadingAidType.FOCUS_REGION })
        val slow = event("slow", "P12", "line", f1,
            metrics = mapOf("kind" to "frontier_progression", "slow_flag" to true))
        val action = chosen(p.onEvent(slow, f1, context(1210.0)), ReadingAidType.FOCUS_REGION)
        assertNotNull(action)
        assertEquals(2, action!!.contextRadiusLines)
        assertEquals(listOf("cycle", "residence", "slow"), action.eventIds)
        assertEquals(listOf("N23", "S14"), action.sourceIds)
        assertTrue(action.experimental)
        val f2 = fixation(2, 1470.0)
        p.onEvent(event("residence-more", "P12", "line", f2,
            metrics = mapOf("kind" to "residence_update", "observed_span_ms" to 1600.0)),
            f2, context(1470.0))
        val stronger = chosen(p.onEvent(event("slow-more", "P12", "line", f2,
            metrics = mapOf("kind" to "frontier_progression", "slow_flag" to true)),
            f2, context(1470.0)), ReadingAidType.FOCUS_REGION)!!
        assertTrue(stronger.strength > action.strength)
        assertEquals(listOf("cycle", "residence-more", "slow-more"), stronger.eventIds)
        p.tick(context(3000.0))
        val next = fixation(3, 3300.0)
        p.onEvent(event("cycle2", "P11", "line", next), next, context(3300.0))
        p.onEvent(event("residence2", "P12", "line", next,
            metrics = mapOf("kind" to "residence_update", "observed_span_ms" to 1200.0)),
            next, context(3300.0))
        val blocked = p.onEvent(event("slow2", "P12", "line", next,
            metrics = mapOf("kind" to "frontier_progression", "slow_flag" to true)),
            next, context(3300.0))
        assertTrue(blocked.any { it.status == ReadingAidDecisionStatus.SUPPRESSED && it.reason == "cooldown" })
    }

    @Test fun staleOrUnavailableEvidenceAndProfileOffCannotChooseAutomaticAction() {
        val manual = ReadingAidPolicy()
        val f = fixation(0, 210.0, line = 1, start = 4, end = 6)
        val e = event("sweep", "P09", "line", f, metrics = mapOf("status" to "observed"))
        assertNull(chosen(manual.onEvent(e, f, context(210.0, 1, 4, 6)),
            ReadingAidType.NEXT_LINE_START))
        val automatic = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        assertTrue(automatic.onEvent(e, f, context(600.0, 1, 4, 6))
            .any { it.reason == "late_or_unavailable_evidence" })
        val unavailable = e.copy(eventId = "unavailable", evidenceAvailableMs = null)
        assertTrue(automatic.onEvent(unavailable, f, context(210.0, 1, 4, 6))
            .any { it.reason == "late_or_unavailable_evidence" })
        assertTrue(automatic.activeActions().isEmpty())
    }

    @Test fun wordResolutionBreakClearsMagnifierButNotLineGuide() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        p.onObservation(candidate(110.0), context(110.0))
        val f = fixation(2, 510.0)
        p.onEvent(event("residence", "P12", "word", f, listOf(0, 1, 2),
            mapOf("kind" to "residence_update", "observed_span_ms" to 800.0)),
            f, context(510.0))
        assertTrue(p.activeActions().any { it.aidType == ReadingAidType.WORD_MAGNIFIER })
        p.onEvent(event("ambiguous", "P13", "word", f,
            metrics = mapOf("reason" to "unassigned_word")), f,
            context(520.0, start = null, end = null))
        assertTrue(p.activeActions().any { it.aidType == ReadingAidType.CURRENT_LINE })
        assertFalse(p.activeActions().any { it.aidType == ReadingAidType.WORD_MAGNIFIER })
    }

    @Test fun repeatedBreakCannotClearNewAidWithinSameEpoch() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        p.onObservation(candidate(110.0), context(110.0))
        val f = fixation(0, 210.0)
        val e = event("once-only", "P13", "line", f,
            metrics = mapOf("reason" to "invalid_sample"))
        p.onEvent(e, f, context(210.0))
        assertTrue(p.activeActions().isEmpty())
        p.onObservation(candidate(320.0), context(320.0))
        assertTrue(p.activeActions().any { it.aidType == ReadingAidType.CURRENT_LINE })
        assertTrue(p.onEvent(e, f, context(320.0)).isEmpty())
        assertTrue(p.activeActions().any { it.aidType == ReadingAidType.CURRENT_LINE })
    }

    @Test fun zeroManualOrMaximumStrengthClearsVisibleAid() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        p.onObservation(candidate(110.0), context(110.0))
        assertTrue(p.activeActions().isNotEmpty())
        assertTrue(p.updatePreferences(ReaderAidPreferences(adaptiveStrength = false,
            manualStrength = 0.0), context(120.0))
            .any { it.status == ReadingAidDecisionStatus.CLEARED })
        assertTrue(p.activeActions().isEmpty())
        assertNull(chosen(p.onObservation(candidate(130.0), context(130.0)), ReadingAidType.CURRENT_LINE))
        p.updatePreferences(ReaderAidPreferences(), context(140.0))
        p.onObservation(candidate(150.0), context(150.0))
        assertTrue(p.updatePreferences(ReaderAidPreferences(maxStrength = 0.0), context(160.0))
            .any { it.status == ReadingAidDecisionStatus.CLEARED })
        assertTrue(p.activeActions().isEmpty())
    }

    @Test fun deliveryBeforeCaptureInvalidatesLiveInput() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        p.onObservation(candidate(110.0), context(110.0))
        val reversed = context(120.0).copy(evidenceAvailableMs = 109.0)
        assertTrue(p.onObservation(null, reversed).any {
            it.status == ReadingAidDecisionStatus.CLEARED && it.reason == "invalid_live_input"
        })
        assertTrue(p.activeActions().isEmpty())
    }

    @Test fun focusStrengthRespectsManualSettingAndOptOut() {
        fun feed(policy: ReadingAidPolicy): List<ReadingAidAction> {
            val f0 = fixation(0, 210.0)
            val f1 = fixation(1, 1210.0)
            val f2 = fixation(2, 1470.0)
            policy.onEvent(event("cycle", "P11", "line", f0), f0, context(210.0))
            policy.onEvent(event("residence", "P12", "line", f1,
                metrics = mapOf("kind" to "residence_update", "observed_span_ms" to 1100.0)),
                f1, context(1210.0))
            val first = chosen(policy.onEvent(event("slow", "P12", "line", f1,
                metrics = mapOf("kind" to "frontier_progression", "slow_flag" to true)),
                f1, context(1210.0)), ReadingAidType.FOCUS_REGION)
            policy.onEvent(event("residence-more", "P12", "line", f2,
                metrics = mapOf("kind" to "residence_update", "observed_span_ms" to 1600.0)),
                f2, context(1470.0))
            val second = chosen(policy.onEvent(event("slow-more", "P12", "line", f2,
                metrics = mapOf("kind" to "frontier_progression", "slow_flag" to true)),
                f2, context(1470.0)), ReadingAidType.FOCUS_REGION)
            return listOfNotNull(first, second)
        }
        val manual = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype(),
            ReaderAidPreferences(focusRegion = true, adaptiveStrength = false,
                manualStrength = 0.4, maxStrength = 0.8))
        val actions = feed(manual)
        assertEquals(1, actions.size)
        assertEquals(0.4, actions.single().strength, 0.0)

        val optedOut = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype(),
            ReaderAidPreferences(focusRegion = false))
        assertTrue(feed(optedOut).isEmpty())
        assertFalse(optedOut.activeActions().any { it.aidType == ReadingAidType.FOCUS_REGION })
    }

    @Test(expected = IllegalArgumentException::class)
    fun lineSaturationCannotPrecedeActivation() {
        ReadingAidPolicyConfig.experimentalPrototype().copy(lineSaturationMs = 999.0)
    }

    @Test fun wordSaturationEndpointsAreExactAndReaderCapStillApplies() {
        fun strength(observed: Double, saturation: Double, cap: Double): Double {
            val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype()
                .copy(wordSaturationMs = saturation), ReaderAidPreferences(maxStrength = cap))
            val f = fixation(2, 510.0)
            val residence = event("word-endpoint", "P12", "word", f, listOf(0, 1, 2),
                mapOf("kind" to "residence_update", "observed_span_ms" to observed))
            return chosen(p.onEvent(residence, f, context(510.0)),
                ReadingAidType.WORD_MAGNIFIER)!!.strength
        }
        assertEquals(0.6, strength(750.0, 750.0, 0.6), 1e-12)
        assertEquals(0.25, strength(750.0, 750.25, 0.8), 1e-12)
        assertEquals(0.7, strength(750.25, 750.25, 0.7), 1e-12)
    }

    @Test fun focusSaturationEndpointsAreExactAndReaderCapStillApplies() {
        fun strength(observed: Double, saturation: Double, cap: Double): Double {
            val p = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype()
                .copy(lineSaturationMs = saturation),
                ReaderAidPreferences(focusRegion = true, maxStrength = cap))
            val f0 = fixation(0, 210.0)
            val f1 = fixation(1, 1210.0)
            p.onEvent(event("cycle-endpoint", "P11", "line", f0), f0, context(210.0))
            p.onEvent(event("line-endpoint", "P12", "line", f1,
                metrics = mapOf("kind" to "residence_update", "observed_span_ms" to observed)),
                f1, context(1210.0))
            val slow = event("slow-endpoint", "P12", "line", f1,
                metrics = mapOf("kind" to "frontier_progression", "slow_flag" to true))
            return chosen(p.onEvent(slow, f1, context(1210.0)),
                ReadingAidType.FOCUS_REGION)!!.strength
        }
        assertEquals(0.6, strength(1000.0, 1000.0, 0.6), 1e-12)
        assertEquals(0.25, strength(1000.0, 1000.25, 0.8), 1e-12)
        assertEquals(0.7, strength(1000.25, 1000.25, 0.7), 1e-12)
    }
}
