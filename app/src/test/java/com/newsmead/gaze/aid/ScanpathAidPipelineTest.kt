package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** The real extractor/classifier feeds the policy; samples are synthetic assignments. */
class ScanpathAidPipelineTest {
    private class Pipeline {
        var context = ReadingAidContext(0, "g1", 2, null, null, null, 0.0, null, null, false)
        val policy = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype())
        val decisions = mutableListOf<ReadingAidDecision>()
        val patterns = mutableListOf<ScanpathEvent>()
        var latest: ScanpathFixation? = null
        val words = (0 until 8).map { WordRange(it * 5, it * 5 + 4) }
        val engine = ScanpathEngine(words,
            ScanpathConfig(returnSweepStartFraction = 0.25, returnSweepEndFraction = 0.75),
            onFixation = { latest = it }, onEvent = {
                patterns.add(it)
                decisions.addAll(policy.onEvent(it, latest, context))
            }, retainEvents = false)
        init {
            engine.updateGeometry(ScanpathGeometry("g1", 0.0, 0.0, ScanpathRect(0.0, 0.0, 100.0, 40.0),
                20.0, listOf(ScanpathLine(0, 20, 0.0, 100.0, 0.0, 20.0),
                    ScanpathLine(20, 40, 0.0, 100.0, 20.0, 40.0)), ScanpathTextDirection.LTR), 0.0)
        }
        fun sample(capture: Double, x: Double, word: Int, line: Int = 0, valid: Boolean = true) {
            val range = words[word]
            context = ReadingAidContext(0, "g1", 2, line, range.startUtf16, range.endUtf16,
                capture + 10, capture, capture + 10, valid)
            if (!valid) decisions.addAll(policy.onObservation(null, context))
            val accepted = engine.onSample(ScanpathSample(capture, capture + 10, x, line * 20.0 + 10,
                line, range.startUtf16, range.endUtf16, "g1", valid))
            decisions.addAll(policy.onObservation(if (accepted) engine.currentCandidateEvidence() else null, context))
        }
        fun hold(start: Double, x: Double, word: Int, line: Int = 0, span: Int = 250) {
            for (dt in 0..span step 50) sample(start + dt, x, word, line)
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

    @Test fun sweepChoosesTheObservedLandingLineAndNeverTheFollowingLine() {
        val p = Pipeline()
        p.hold(200.0, 90.0, 3, line = 0, span = 100)
        p.hold(350.0, 10.0, 4, line = 1, span = 100)
        p.sample(500.0, 40.0, 5, line = 1)
        assertTrue(p.patterns.any { it.patternId == "P09" && it.metrics["status"] == "observed" })
        val cue = p.decisions.single { it.status == ReadingAidDecisionStatus.CHOSEN &&
            it.action?.aidType == ReadingAidType.NEXT_LINE_START }.action!!
        assertEquals(1, cue.anchor.line)
        assertTrue(cue.eventIds.any { id -> p.patterns.any { it.eventId == id && it.patternId == "P09" } })
        assertEquals(listOf("W24", "S19"), cue.sourceIds)
    }
}
