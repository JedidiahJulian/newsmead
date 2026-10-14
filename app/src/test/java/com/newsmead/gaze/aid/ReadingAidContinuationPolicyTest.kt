package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

class ReadingAidContinuationPolicyTest {
    private val geometry = ScanpathGeometry("g", 100.0, 50.0,
        ScanpathRect(100.0, 50.0, 300.0, 250.0), 20.0,
        (0..7).map { ScanpathLine(it * 10, it * 10 + 9, 0.0, 200.0,
            it * 20.0, it * 20.0 + 20.0) }, ScanpathTextDirection.LTR)
    private fun policy(prefs: ReaderAidPreferences = ReaderAidPreferences(lineGuide = false)) =
        ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype(), prefs)
    private fun context(t: Double, line: Int = 3, x: Double = 120.0) =
        ReadingAidContext(1, "g", 8, line, null, null, t, t - 10, t,
            geometry = geometry, targetX = x)
    private fun candidate(t: Double, line: Int = 3, segment: Int = 1) =
        ScanpathCandidateEvidence("candidate:$segment:${t - 100}", segment,
            t - 100, t - 10, 90.0, 3, null, null, line, 0.0, 1.0,
            "g", t, "trigger_observation_delivery")
    private fun observe(p: ReadingAidPolicy, t: Double, line: Int = 3, x: Double = 120.0) =
        p.onObservation(candidate(t, line), context(t, line, x))
    private fun arm(p: ReadingAidPolicy) {
        observe(p, 110.0)
        observe(p, 230.0, x = 280.0)
    }
    private fun cue(p: ReadingAidPolicy) = p.activeActions().singleOrNull {
        it.aidType == ReadingAidType.NEXT_LINE_START }
    private fun mismatch(p: ReadingAidPolicy, line: Int = 0): ReadingAidAction {
        arm(p)
        observe(p, 350.0, line)
        return cue(p)!!
    }

    @Test fun adjacentArrivalConsumesExpectationWithoutCue() {
        val p = policy()
        arm(p)
        observe(p, 350.0, 4)
        assertNull(cue(p))
        observe(p, 470.0, 0)
        assertNull(cue(p))
    }

    @Test fun backwardAndForwardMismatchAnchorExpectedLineWithSeparateSupportBounds() {
        for (landing in listOf(0, 7)) {
            val p = policy()
            val a = mismatch(p, landing)
            assertEquals(4, a.anchor.line)
            assertEquals("A2_CONTINUATION_MISMATCH_V1", a.ruleId)
            assertEquals("CONTINUATION_LINE_START_CUE_V1", a.aidFormId)
            assertEquals(listOf("W24", "S19"), a.sourceIds)
            assertEquals(listOf(3, 3, landing), a.continuationEvidence.map { it.line })
            assertEquals(listOf(10.0, 130.0, 250.0), a.continuationEvidence.map { it.firstCaptureMs })
            assertEquals(listOf(100.0, 220.0, 340.0), a.continuationEvidence.map { it.lastCaptureMs })
            assertEquals(listOf(110.0, 230.0, 350.0), a.continuationEvidence.map { it.evidenceAvailableMs })
            assertEquals(a.evidenceIds, a.continuationEvidence.map { it.evidenceId })
        }
    }

    @Test fun cueStrengthIsFixedAndPositivePreferenceChangesPreserveIt() {
        val p = policy(ReaderAidPreferences(lineGuide = false, adaptiveStrength = false,
            manualStrength = 0.2, maxStrength = 0.1))
        val a = mismatch(p)
        assertEquals(1.0, a.strength, 0.0)
        p.updatePreferences(p.preferences.copy(manualStrength = 0.8, maxStrength = 0.05),
            context(360.0, 0))
        assertEquals(a.id, cue(p)?.id)
        p.updatePreferences(p.preferences.copy(maxStrength = 0.0), context(370.0, 0))
        assertNull(cue(p))
    }

    @Test fun zeroManualGlobalOffAndCueOptOutPreventActivation() {
        for (prefs in listOf(ReaderAidPreferences(enabled = false),
            ReaderAidPreferences(lineStartCue = false), ReaderAidPreferences(maxStrength = 0.0),
            ReaderAidPreferences(adaptiveStrength = false, manualStrength = 0.0))) {
            val p = policy(prefs.copy(lineGuide = false))
            arm(p)
            observe(p, 350.0, 0)
            assertNull(cue(p))
        }
    }

    @Test fun adaptiveCapAlsoKeepsFixedStrengthAndMissingTargetXCannotArm() {
        val capped = policy(ReaderAidPreferences(lineGuide = false, maxStrength = 0.15))
        assertEquals(1.0, mismatch(capped).strength, 0.0)
        val p = policy()
        p.onObservation(candidate(110.0), context(110.0).copy(targetX = null))
        observe(p, 230.0, x = 280.0)
        observe(p, 350.0, 0)
        assertNull(cue(p))
    }

    @Test fun lineBreakClearsExpectationAndMarkerWhileWordAmbiguityPreservesLineEvidence() {
        fun breakEvent(id: String, resolution: String, reason: String) = ScanpathEvent(
            id, "P13", resolution, emptyList(), 300.0, 300.0, 310.0,
            "trigger_observation_delivery", mapOf("reason" to reason))
        val p = policy()
        arm(p)
        p.onEvent(breakEvent("gap", "line", "sample_gap"), null, context(310.0))
        observe(p, 450.0, 0)
        assertNull(cue(p))
        val word = policy()
        arm(word)
        word.onEvent(breakEvent("word", "word", "unassigned_word"), null, context(310.0))
        observe(word, 450.0, 0)
        assertNotNull(cue(word))
        word.onEvent(breakEvent("scroll", "line", "geometry_changed"), null,
            context(460.0, 0))
        assertNull(cue(word))
    }

    @Test fun rawTargetWobbleAndSamePrefixDoNotClearButEstablishedArrivalDoes() {
        val p = policy()
        val a = mismatch(p)
        p.tick(context(360.0, 6))
        assertEquals(a.id, cue(p)?.id)
        val growing = candidate(350.0, 0).copy(endMs = 370.0,
            durationMs = 120.0, evidenceAvailableMs = 380.0)
        p.onObservation(growing, context(380.0, 0))
        assertEquals(a.id, cue(p)?.id)
        observe(p, 500.0, 4)
        assertNull(cue(p))
    }

    @Test fun validMajorityCandidateRawRowDisagreementPreservesMarkerAndExpectation() {
        val p = policy()
        val a = mismatch(p)
        val majority = candidate(350.0, 0).copy(endMs = 370.0, durationMs = 120.0,
            sampleCount = 6, lineSampleAgreement = 5.0 / 6.0, evidenceAvailableMs = 380.0)
        p.onObservation(majority, context(380.0, 1))
        assertEquals(a.id, cue(p)?.id)
        observe(p, 500.0, 4)
        assertNull(cue(p))
        val pending = policy()
        arm(pending)
        val sourceMajority = candidate(230.0).copy(endMs = 250.0, durationMs = 120.0,
            sampleCount = 6, lineSampleAgreement = 5.0 / 6.0, evidenceAvailableMs = 260.0)
        pending.onObservation(sourceMajority, context(260.0, 2, 280.0))
        observe(pending, 380.0, 0)
        assertEquals(4, cue(pending)?.anchor?.line)
    }

    @Test fun departedNonSourceFinalizationCannotConsumeCurrentExpectation() {
        val p = policy()
        arm(p)
        val older = ScanpathFixation(2, 1, 250.0, 340.0, 90.0, 3, 120.0, 60.0,
            0.0, null, null, 0.0, 0, 1.0, false, false, "g", 350.0,
            "trigger_observation_delivery")
        p.onEvent(ScanpathEvent("older-row", "P01", "line", listOf(2),
            250.0, 340.0, 350.0, "trigger_observation_delivery"), older, context(350.0, 3))
        assertNull(cue(p))
        observe(p, 470.0, 7)
        assertEquals(4, cue(p)?.anchor?.line)
    }

    @Test fun distinctContinuedReadingElsewhereClearsWithoutReactivation() {
        val p = policy()
        mismatch(p, 7)
        observe(p, 470.0, 7, 240.0)
        assertNull(cue(p))
        observe(p, 600.0, 0)
        assertNull(cue(p))
    }

    @Test fun middleOrEndOnlyAndMiddleDepartureDoNotArm() {
        for (fractions in listOf(listOf(200.0, 280.0), listOf(280.0), listOf(120.0, 200.0))) {
            val p = policy()
            fractions.forEachIndexed { i, x -> observe(p, 110.0 + 120.0 * i, x = x) }
            observe(p, 500.0, 0)
            assertNull(cue(p))
        }
    }

    @Test fun unsupportedGeometryClippedSourceOrExpectedLineAndLastLineAbstain() {
        val variants = listOf<ScanpathGeometry?>(null,
            geometry.copy(textDirection = ScanpathTextDirection.RTL),
            geometry.copy(textDirection = ScanpathTextDirection.UNKNOWN),
            geometry.copy(snapshotId = "other"),
            geometry.copy(visibleRectScreen = geometry.visibleRectScreen.copy(top = 115.0)),
            geometry.copy(visibleRectScreen = geometry.visibleRectScreen.copy(bottom = 145.0)))
        for (g in variants) {
            val p = policy()
            for ((t, line, x) in listOf(Triple(110.0, 3, 120.0),
                Triple(230.0, 3, 280.0), Triple(350.0, 0, 120.0)))
                p.onObservation(candidate(t, line), context(t, line, x).copy(geometry = g))
            assertNull(cue(p))
        }
        val p = policy()
        observe(p, 110.0, 7)
        observe(p, 230.0, 7, 280.0)
        observe(p, 350.0, 0)
        assertNull(cue(p))
    }

    @Test fun staleGapResetGenerationAndWindowCannotBridgeExpectation() {
        for (boundary in 0..5) {
            val p = policy()
            arm(p)
            when (boundary) {
                0 -> p.onObservation(candidate(230.0), context(500.0))
                1 -> p.onObservation(candidate(350.0, 0, 2), context(350.0, 0))
                2 -> p.reset(context(300.0))
                3 -> p.tick(context(300.0).copy(generation = 2))
                4 -> p.tick(context(300.0).copy(validLiveInput = false))
                5 -> p.tick(context(2500.0))
            }
            observe(p, if (boundary == 5) 2620.0 else 620.0, 0)
            assertNull(cue(p))
        }
    }

    @Test fun duplicateEvidenceAndTtlDoNotRearmSameDeparture() {
        val p = policy()
        val a = mismatch(p, 7)
        p.onObservation(candidate(350.0, 7), context(350.0, 7))
        assertEquals(a.id, cue(p)?.id)
        p.tick(context(1900.0, 7))
        assertNull(cue(p))
        observe(p, 2000.0, 0)
        assertNull(cue(p))
    }

    @Test fun slowSameLineReadingRetainsBeginningAndWindowRunsFromLastEndSupport() {
        val p = policy()
        observe(p, 110.0)
        observe(p, 1100.0, x = 180.0)
        observe(p, 2100.0, x = 200.0)
        observe(p, 3110.0, x = 280.0)
        observe(p, 3230.0, 0)
        assertEquals(4, cue(p)?.anchor?.line)
        val expired = policy()
        observe(expired, 110.0)
        observe(expired, 3110.0, x = 280.0)
        observe(expired, 5230.0, 0)
        assertNull(cue(expired))
    }

    @Test fun censoredFinalizedSourceCannotArmAfterReset() {
        for (left in listOf(false, true)) {
            val p = policy()
            p.reset(context(100.0))
            fun finalized(i: Int, t: Double, x: Double) {
                val f = ScanpathFixation(i, 1, t - 100, t - 10, 90.0, 3, x, 120.0,
                    0.0, null, null, 0.0, 3, 1.0, left, !left, "g", t,
                    "trigger_observation_delivery")
                p.onEvent(ScanpathEvent("censored:$i", "P01", "line", listOf(i),
                    f.startMs, f.endMs, t, "trigger_observation_delivery"), f, context(t))
            }
            finalized(0, 230.0, 120.0)
            finalized(1, 350.0, 280.0)
            observe(p, 470.0, 0)
            assertNull(cue(p))
        }
    }

    @Test fun firstCandidateProgressSurvivesItsLeftCensoredFinalization() {
        val p = policy()
        observe(p, 110.0)
        val f = ScanpathFixation(0, 1, 10.0, 100.0, 90.0, 3, 120.0, 120.0,
            0.0, null, null, 0.0, 3, 1.0, true, false, "g", 120.0,
            "trigger_observation_delivery")
        p.onEvent(ScanpathEvent("first-final", "P01", "line", listOf(0),
            f.startMs, f.endMs, 120.0, "trigger_observation_delivery"), f, context(120.0))
        observe(p, 230.0, x = 280.0)
        observe(p, 350.0, 0)
        val a = cue(p)!!
        assertEquals(4, a.anchor.line)
        assertEquals("candidate:1:10.0", a.continuationEvidence.first().evidenceId)
        assertFalse(a.evidenceIds.contains("fixation:0"))
    }

    @Test fun finalizedSourceUsesCentroidEvenAfterRawTargetMoves() {
        val p = policy()
        fun finalized(i: Int, t: Double, x: Double) {
            val f = ScanpathFixation(i, 1, t - 100, t - 10, 90.0, 3, x, 120.0,
                0.0, null, null, 0.0, 3, 1.0, false, false, "g", t,
                "trigger_observation_delivery")
            p.onEvent(ScanpathEvent("e$i", "P01", "line", listOf(i), f.startMs,
                f.endMs, t, "trigger_observation_delivery"), f, context(t, 0))
        }
        finalized(0, 110.0, 120.0)
        finalized(1, 230.0, 280.0)
        observe(p, 350.0, 0)
        val a = cue(p)!!
        assertEquals(4, a.anchor.line)
        assertEquals(listOf("fixation:0", "fixation:1", "candidate:1:250.0"), a.evidenceIds)
    }

    @Test fun candidateFinalizationOfLandingIsSameSupportAndDoesNotClearCue() {
        val p = policy()
        val a = mismatch(p)
        val f = ScanpathFixation(2, 1, 250.0, 340.0, 90.0, 3, 120.0, 60.0,
            0.0, null, null, 0.0, 0, 1.0, false, false, "g", 360.0,
            "trigger_observation_delivery")
        p.onEvent(ScanpathEvent("landing-final", "P01", "line", listOf(2),
            250.0, 340.0, 360.0, "trigger_observation_delivery"), f, context(360.0, 0))
        assertEquals(a.id, cue(p)?.id)
    }

    @Test fun defaultsAndProfileVersionsAreExplicit() {
        val c = ReadingAidPolicyConfig.definitionPrototype()
        assertEquals("DEFINITION_PROFILE_V4", c.configId)
        assertEquals("EXPERIMENTAL_DEFINITION_V4", c.conditionId)
        assertEquals(ReadingAidType.READING_BOOKMARK, c.lineStartAidType)
        assertEquals("EXPERIMENTAL_PROFILE_V2", ReadingAidPolicyConfig.experimentalPrototype().configId)
        assertEquals(0.25, c.continuationStartFraction, 0.0)
        assertEquals(0.75, c.continuationEndFraction, 0.0)
        assertEquals(2000.0, c.continuationWindowMs, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidContinuationFractionsRejected() {
        ReadingAidPolicyConfig.experimentalPrototype().copy(continuationStartFraction = 0.8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidContinuationWindowRejected() {
        ReadingAidPolicyConfig.experimentalPrototype().copy(continuationWindowMs = Double.NaN)
    }
}
