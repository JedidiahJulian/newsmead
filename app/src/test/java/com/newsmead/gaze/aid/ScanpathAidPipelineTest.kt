package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** The real extractor/classifier feeds the policy; samples are synthetic assignments. */
class ScanpathAidPipelineTest {
    private class Pipeline(config: ReadingAidPolicyConfig = ReadingAidPolicyConfig.experimentalPrototype()) {
        private val geometry = ScanpathGeometry("g1", 0.0, 0.0, ScanpathRect(0.0, 0.0, 100.0, 60.0),
            20.0, listOf(ScanpathLine(0, 20, 0.0, 100.0, 0.0, 20.0),
                ScanpathLine(20, 40, 0.0, 100.0, 20.0, 40.0),
                ScanpathLine(40, 60, 0.0, 100.0, 40.0, 60.0)), ScanpathTextDirection.LTR)
        var context = ReadingAidContext(0, "g1", 3, null, null, null, 0.0, null, null, false, geometry, null)
        val policy = ReadingAidPolicy(config,
            ReaderAidPreferences(wordDefinition = false))
        val decisions = mutableListOf<ReadingAidDecision>()
        val patterns = mutableListOf<ScanpathEvent>()
        var latest: ScanpathFixation? = null
        val words = (0 until 12).map { WordRange(it * 5, it * 5 + 4) }
        val engine = ScanpathEngine(words,
            ScanpathConfig(returnSweepStartFraction = 0.25, returnSweepEndFraction = 0.75),
            onFixation = { latest = it }, onEvent = {
                patterns.add(it)
                decisions.addAll(policy.onEvent(it, latest, context))
            }, retainEvents = false)
        init {
            engine.updateGeometry(geometry, 0.0)
        }
        fun sample(capture: Double, x: Double, word: Int, line: Int = 0, valid: Boolean = true,
                    rawY: Double = line * 20.0 + 10) {
            val range = words[word]
            val sample = ScanpathSample(capture, capture + 10, x, rawY,
                line, range.startUtf16, range.endUtf16, "g1", valid)
            context = ReadingAidContext(0, "g1", geometry.lines.size, line, range.startUtf16, range.endUtf16,
                capture + 10, capture, capture + 10, valid, geometry, x, rawSample = sample)
            if (!valid) decisions.addAll(policy.onObservation(null, context))
            val accepted = engine.onSample(sample)
            decisions.addAll(policy.onObservation(if (accepted) engine.currentCandidateEvidence() else null, context))
        }
        fun hold(start: Double, x: Double, word: Int, line: Int = 0, span: Int = 250,
                 rawY: Double = line * 20.0 + 10) {
            for (dt in 0..span step 50) sample(start + dt, x, word, line, rawY = rawY)
        }
    }

    @Test fun ongoingResidenceChangesMagnificationWithoutFinalizingAnotherFixation() {
        val p = Pipeline()
        p.hold(200.0, 10.0, 0)
        p.hold(500.0, 35.0, 0)
        p.hold(800.0, 60.0, 0)
        p.hold(1100.0, 85.0, 0, span = 300)
        assertEquals(3, p.engine.fixations.size)
        assertEquals(2, p.patterns.count { it.patternId == "P04" })
        val actions = p.decisions.filter { it.status == ReadingAidDecisionStatus.CHOSEN }
            .mapNotNull { it.action }.filter { it.aidType == ReadingAidType.WORD_MAGNIFIER }
        assertTrue(actions.size >= 2)
        assertTrue(actions.last().strength > actions.first().strength)
        // The first match uses finalized residence; the later increase uses an ongoing prefix.
        assertTrue(actions.first().evidenceIds.isEmpty())
        assertTrue(actions.all { it.eventIds.any { id ->
            p.patterns.any { event -> event.eventId == id && event.patternId == "P12" }
        } })
        assertTrue(actions.last().evidenceIds.any { it.startsWith("candidate:") })
        assertTrue(actions.all { it.lastCaptureMs <= it.evidenceAvailableMs && it.evidenceAvailableMs <= it.onsetMs })
        assertEquals(0, actions.last().anchor.wordStartUtf16)
        assertTrue(p.engine.events.isEmpty())
        // An invalid trigger must clear before the boundary flush can offer the old word again.
        p.sample(1450.0, 85.0, 0, valid = false)
        assertTrue(p.policy.activeActions().isEmpty())
        assertEquals(4, p.engine.fixations.size)
        assertTrue(p.engine.fixations.last().rightCensored)
    }

    @Test fun normalSweepDoesNotTriggerContinuationCue() {
        val p = Pipeline()
        p.hold(200.0, 90.0, 3, line = 0, span = 100)
        p.hold(350.0, 10.0, 4, line = 1, span = 100)
        p.sample(500.0, 40.0, 5, line = 1)
        assertTrue(p.patterns.any { it.patternId == "P09" && it.metrics["status"] == "observed" })
        assertTrue(p.decisions.none { it.status == ReadingAidDecisionStatus.CHOSEN &&
            it.action?.aidType == ReadingAidType.NEXT_LINE_START })
    }

    @Test fun boundaryRowWobblePreservesExistingContinuationMarker() {
        val p = Pipeline()
        p.hold(200.0, 10.0, 4, line = 1, rawY = 39.0)
        p.hold(500.0, 90.0, 7, line = 1, rawY = 39.0)
        p.hold(800.0, 10.0, 0, line = 0, rawY = 19.0)
        val marker = p.policy.activeActions().single { it.aidType == ReadingAidType.NEXT_LINE_START }
        assertEquals(2, marker.anchor.line)
        val chosenCount = p.decisions.count { it.status == ReadingAidDecisionStatus.CHOSEN &&
            it.action?.aidType == ReadingAidType.NEXT_LINE_START }

        // Five supported row-0 samples plus one 2 px boundary wobble assigned to row 1.
        p.hold(1100.0, 10.0, 0, line = 0, span = 200, rawY = 19.0)
        p.sample(1350.0, 10.0, 4, line = 1, rawY = 21.0)
        val candidate = p.engine.currentCandidateEvidence()!!
        assertEquals(0, candidate.line)
        assertTrue(candidate.lineSampleAgreement >= 0.8)
        assertTrue(p.decisions.count { it.status == ReadingAidDecisionStatus.CHOSEN &&
            it.action?.aidType == ReadingAidType.NEXT_LINE_START } == chosenCount)
        val stillActive = p.policy.activeActions().single { it.aidType == ReadingAidType.NEXT_LINE_START }
        assertEquals(marker.id, stillActive.id)
        assertEquals(2, stillActive.anchor.line)
        assertFalse(p.decisions.any { it.status == ReadingAidDecisionStatus.CLEARED &&
            it.action?.aidType == ReadingAidType.NEXT_LINE_START })
    }
    @Test fun definitionProfileAssistsOnOngoingFourthVisitBeforeFinalizedFastPattern() {
        val p = Pipeline(ReadingAidPolicyConfig.definitionPrototype())
        p.hold(200.0, 10.0, 0, line = 0)
        p.hold(500.0, 10.0, 4, line = 1)
        p.hold(800.0, 10.0, 0, line = 0)
        p.hold(1100.0, 10.0, 4, line = 1)
        assertFalse(p.patterns.any { it.patternId == "P11" && it.resolution == "line" })
        val early = p.policy.activeActions().single { it.aidType == ReadingAidType.FOCUS_REGION }
        assertEquals(1, early.anchor.line)
        assertEquals(1, early.focusEvidence!!.alternations.size)
        assertTrue(early.eventIds.isEmpty())
        p.sample(1400.0, 10.0, 0, line = 0)
        val cycle = p.patterns.single { it.patternId == "P11" && it.resolution == "line" }
        assertEquals(1, p.latest!!.line)
        assertEquals(0, p.context.targetLine)
        assertTrue(p.policy.activeActions().any { it.aidType == ReadingAidType.FOCUS_REGION })
        p.sample(1450.0, 10.0, 0, line = 0)
        p.sample(1500.0, 10.0, 0, line = 0)
        val focus = p.policy.activeActions().single { it.aidType == ReadingAidType.FOCUS_REGION }
        assertEquals(0, focus.anchor.line)
        assertTrue(focus.eventIds.isEmpty())
        assertEquals("A4_FONT_SCALED_LINE_VISITS_V3", focus.ruleId)
        assertEquals(0.5, focus.strength, 0.0)
        assertEquals(1, focus.focusEvidence!!.cycleCount)
        assertFalse(p.patterns.any { it.patternId == "P12" && it.metrics["slow_flag"] == true })
    }

}
