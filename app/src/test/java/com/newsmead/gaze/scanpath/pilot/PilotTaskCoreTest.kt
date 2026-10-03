package com.newsmead.gaze.scanpath.pilot

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

class PilotTaskCoreTest {
    private val geometry = ScanpathGeometry("g", 80.0, 120.0,
        ScanpathRect(80.0, 120.0, 480.0, 240.0), 20.0,
        (0..2).map { ScanpathLine(it * 20, it * 20 + 19, 0.0, 400.0, it * 40.0, it * 40.0 + 20) },
        ScanpathTextDirection.LTR)
    private val words = (0..11).map { i -> PilotTarget("w$i", i / 4, i * 5, i * 5 + 4,
        ScanpathRect(90.0 + i % 4 * 100, 120.0 + i / 4 * 40,
            170.0 + i % 4 * 100, 140.0 + i / 4 * 40)) }
    private fun sample(t: Double?, w: PilotTarget = words[0], eligible: Boolean = true,
        line: Int? = w.line, start: Int? = w.wordStartUtf16, end: Int? = w.wordEndUtf16,
        snapshot: String? = "g", x: Double? = w.bounds.left + 1, delivery: Double? = t?.plus(10)) =
        ScanpathSample(t, delivery, x, w.bounds.top + 1, line, start, end, snapshot, eligible)
    private fun plan(vararg w: PilotTarget) = PilotTaskPlan("test", "test", "word", w.toList(), "prompt only")
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }

    @Test fun everyCatalogPlanUsesExistingWordsOrFullPhysicalRows() {
        PilotTasks.tasks.forEach { task ->
            val plan = PilotTasks.build(task.id, geometry, words)
            assertTrue(plan.targets.isNotEmpty())
            plan.targets.forEach { target ->
                if (target.wordStartUtf16 != null) assertTrue(target in words)
                else {
                    val row = geometry.lines[target.line]
                    assertEquals(ScanpathRect(80 + row.left, 120 + row.top, 80 + row.right, 120 + row.bottom), target.bounds)
                }
            }
        }
        assertEquals(listOf(0, 1), PilotTasks.build("next_line", geometry, words).targets.map { it.line })
        assertEquals(listOf(0, 2), PilotTasks.build("multiline", geometry, words).targets.map { it.line })
        assertEquals(listOf(words[0], words[1], words[2]), PilotTasks.build("forward_words", geometry, words).targets)
        assertEquals(listOf(words[2], words[1], words[0]), PilotTasks.build("backward_words", geometry, words).targets)
    }

    @Test fun rejectsClippingAmbiguityDuplicateRangesAndImpossibleDirection() {
        rejects { PilotTasks.build("word_hold", geometry, listOf(words[0].copy(bounds = words[0].bounds.copy(left = 79.0)))) }
        rejects { PilotTasks.build("word_hold", geometry, listOf(words[0], words[1].copy(id = words[0].id))) }
        rejects { PilotTasks.build("word_hold", geometry, listOf(words[0], words[1].copy(bounds = words[0].bounds))) }
        rejects { PilotTasks.build("word_hold", geometry, listOf(words[0], words[1].copy(wordStartUtf16 = 1))) }
        rejects { PilotTasks.build("next_line", geometry.copy(textDirection = ScanpathTextDirection.RTL), words) }
        rejects { PilotTasks.build("multiline", geometry.copy(visibleRectScreen = geometry.visibleRectScreen.copy(bottom = 180.0)), words.take(8)) }
        rejects { PilotTasks.build("line_hold", geometry.copy(lines = listOf(geometry.lines[0], geometry.lines[0])), emptyList()) }
        rejects { PilotTasks.build("word_hold", geometry.copy(textOriginX = Double.NaN), words) }
    }

    @Test fun translatedOriginPreservesExactSuppliedTargetsAndFullRows() {
        fun move(r: ScanpathRect) = ScanpathRect(r.left + 300, r.top + 500, r.right + 300, r.bottom + 500)
        val shifted = geometry.copy(textOriginX = 380.0, textOriginY = 620.0, visibleRectScreen = move(geometry.visibleRectScreen))
        val shiftedWords = words.map { it.copy(bounds = move(it.bounds)) }
        PilotTasks.tasks.forEach { task ->
            assertEquals(PilotTasks.build(task.id, geometry, words).targets.map { it.copy(bounds = move(it.bounds)) },
                PilotTasks.build(task.id, shifted, shiftedWords).targets)
        }
    }

    @Test fun blankParagraphRowsRetainPhysicalIndicesAndAreNeverTargets() {
        val withBlank = geometry.copy(lines = geometry.lines.toMutableList().apply {
            set(1, ScanpathLine(20, 20, 0.0, 0.0, 40.0, 60.0))
        })
        val visibleWords = words.filter { it.line != 1 }
        val hold = PilotTasks.build("line_hold", withBlank, visibleWords)
        assertTrue(hold.targets.all { it.line != 1 })
        assertEquals(0, PilotTasks.build("word_hold", withBlank, visibleWords).targets[0].line)
        rejects { PilotTasks.build("line_cycle", withBlank, visibleWords) }
        rejects { PilotTasks.build("multiline", withBlank, visibleWords) }
        rejects { PilotTasks.build("next_line", withBlank, visibleWords) }
        val inverted = withBlank.copy(lines = withBlank.lines.toMutableList().apply {
            set(1, withBlank.lines[1].copy(right = -1.0))
        })
        rejects { PilotTasks.build("line_hold", inverted, visibleWords) }
    }

    @Test fun delayedDeliveryUsesEarlierWindowAndOnsetBoundaryUsesNextWindow() {
        val score = PilotScore(plan(words[0], words[1]), "g", 0.0)
        score.showStep(0, 100.0)
        score.showStep(1, 200.0)
        score.record(sample(199.0, delivery = 250.0))
        score.record(sample(200.0, words[1], delivery = 260.0))
        score.record(sample(300.0, words[1])) // final end is exclusive
        val result = score.finish(300.0)
        assertEquals(listOf(1, 1), result.targets.map { it.intervalSamples })
        assertEquals(listOf(1, 1), result.targets.map { it.exactWord })
        assertEquals(1, result.outsideIntervals)
        assertEquals(2, result.wordOrder.matches)
    }

    @Test fun settleBoundaryIsInclusiveAndNoEligibleRatioIsUndefined() {
        val score = PilotScore(plan(words[0], words[1]), "g", 20.0)
        score.showStep(0, 100.0)
        score.record(sample(119.0))
        score.record(sample(120.0))
        score.showStep(1, 200.0)
        score.record(sample(220.0, words[1], eligible = false))
        val r = score.finish(250.0)
        assertEquals(2, r.targets[0].intervalSamples)
        assertEquals(1, r.targets[0].scorableSamples)
        assertEquals(1.0, r.targets[0].exactWordGivenEligible!!, 0.0)
        assertEquals(1, r.targets[1].scorableSamples)
        assertEquals(0, r.targets[1].eligibleSamples)
        assertNull(r.targets[1].exactWordGivenEligible)
        assertNull(r.targets[1].exactLineGivenEligible)
    }

    @Test fun laterSettlingKeepsTransitionsInOneContinuousOrderSegment() {
        val score = PilotScore(plan(words[0], words[1], words[2]), "g", 20.0)
        score.showStep(0, 0.0)
        score.record(sample(1.0, words[3])) // first settling exclusion applies to order too
        score.record(sample(20.0, words[0]))
        score.showStep(1, 30.0)
        score.record(sample(31.0, words[1])) // order included; agreement still settling
        score.record(sample(50.0, words[1]))
        score.showStep(2, 60.0)
        score.record(sample(61.0, words[2]))
        score.record(sample(80.0, words[2]))
        val r = score.finish(90.0)
        assertEquals(listOf(1, 1, 1), r.targets.map { it.scorableSamples })
        assertEquals(listOf(1, 1, 1), r.targets.map { it.exactWord })
        assertEquals(3, r.wordOrder.matches)
        assertEquals(3, r.wordOrder.maxMatchesInOneSegment)
        assertEquals(1, r.wordRunSegments.size)
        assertEquals(r.wordOrder.expected, r.wordRunSegments.single())
        assertEquals(1, r.lineRunSegments.size)
        assertEquals(1, r.lineOrder.recovered.size)
    }

    @Test fun descriptiveGlobalAlignmentAcrossBreaksHasOnlyPartialSingleSegmentAgreement() {
        val score = PilotScore(plan(words[0], words[4]), "g", 0.0)
        score.showStep(0, 0.0)
        score.record(sample(1.0, words[0]))
        score.record(sample(2.0, words[0], start = null, end = null))
        score.record(sample(3.0, words[4]))
        val r = score.finish(4.0)
        assertEquals(2, r.wordOrder.matches)
        assertEquals(1, r.wordOrder.maxMatchesInOneSegment)
        assertEquals(2, r.wordRunSegments.size)
        assertEquals(2, r.lineOrder.matches)
        assertEquals(2, r.lineOrder.maxMatchesInOneSegment)
        assertEquals(1, r.lineRunSegments.size)
    }

    @Test fun lineOnlyTaskHasDescriptiveWordsButNoWordReferenceScore() {
        val p = PilotTasks.build("line_hold", geometry, words)
        val score = PilotScore(p, "g", 0.0)
        score.showStep(0, 0.0)
        score.record(sample(1.0, words[0])); score.record(sample(2.0, words[1]))
        val r = score.finish(3.0)
        assertFalse(r.wordOrder.referenceAvailable)
        assertEquals(2, r.wordOrder.recovered.size)
        assertEquals(0, r.wordOrder.matches); assertEquals(0, r.wordOrder.misses)
        assertEquals(0, r.wordOrder.extras); assertEquals(0, r.wordOrder.editDistance)
        assertEquals(0, r.wordOrder.maxMatchesInOneSegment)
        assertTrue(r.lineOrder.referenceAvailable)
        assertEquals(1, r.lineOrder.matches)
        assertNull(r.targets[0].exactWordGivenEligible)
    }

    @Test fun abortedRunNeverCreditsUnshownTargetsDespiteDescriptiveGlobalAlignment() {
        val score = PilotScore(plan(words[0], words[1], words[2]), "g", 0.0)
        score.showStep(0, 0.0)
        score.record(sample(1.0, words[0]))
        score.record(sample(2.0, words[1])) // B is observed while cue A remains drawn
        score.record(sample(3.0, words[2])) // C is observed but its cue will never be drawn
        val r = score.finish(4.0)
        assertEquals(3, r.wordOrder.matches) // descriptive only, without prompted temporal matching
        assertEquals(3, r.plannedTargets)
        assertEquals(1, r.attemptedTargets)
        assertEquals(1, r.targets.count { it.scorableSamples > 0 })
        assertEquals(1, r.targets.count { it.exactWord > 0 })
        assertEquals(listOf(1, 0, 0), r.targets.map { it.exactWord })
        assertEquals(listOf(true, false, false), r.targets.map { it.attempted })
        assertEquals(listOf(3, 0, 0), r.targets.map { it.scorableSamples })
        assertNull(r.targets[1].exactWordGivenEligible)
        assertNull(r.targets[2].exactWordGivenEligible)
    }

    @Test fun invalidDuplicateAndBackwardsCapturesRemainExplicitExclusions() {
        val score = PilotScore(plan(words[0]), "g", 0.0)
        score.showStep(0, 100.0)
        score.record(sample(null)); score.record(sample(Double.NaN))
        score.record(sample(110.0)); score.record(sample(110.0)); score.record(sample(109.0))
        score.record(sample(120.0, x = Double.POSITIVE_INFINITY))
        score.record(sample(130.0, snapshot = "changed"))
        score.record(sample(140.0, eligible = false))
        val r = score.finish(200.0)
        assertEquals(8, r.totalSamples)
        assertEquals(1, r.nullCapture); assertEquals(1, r.nonfiniteCapture); assertEquals(2, r.outOfOrderCapture)
        assertEquals(1, r.invalidCoordinates); assertEquals(1, r.geometryMismatch); assertEquals(1, r.ineligible)
        assertEquals(4, r.targets[0].scorableSamples)
        assertEquals(1, r.targets[0].eligibleSamples)
    }

    @Test fun missingWordPreservesLineAndMissingnessBreaksWordRuns() {
        val score = PilotScore(plan(words[0]), "g", 0.0)
        score.showStep(0, 0.0)
        score.record(sample(1.0)); score.record(sample(2.0, start = null, end = null)); score.record(sample(3.0))
        val r = score.finish(4.0)
        assertEquals(3, r.targets[0].eligibleSamples)
        assertEquals(2, r.targets[0].wordAssigned)
        assertEquals(1, r.targets[0].unknownWord)
        assertEquals(3, r.targets[0].lineAssigned)
        assertEquals(3, r.targets[0].exactLine)
        assertEquals(2, r.wordOrder.recovered.size)
        assertEquals(1, r.lineOrder.recovered.size)
        assertEquals(2, r.wordRunSegments.size)
        assertEquals(1, r.lineRunSegments.size)
    }

    @Test fun longCaptureGapAndExplicitInvalidSampleSplitIndependentRuns() {
        val score = PilotScore(plan(words[0]), "g", 0.0, maxSequenceGapMs = 125.0)
        score.showStep(0, 0.0)
        score.record(sample(1.0)); score.record(sample(126.0)) // exactly gap setting preserves continuity
        score.record(sample(252.0)) // strictly over setting starts a fresh segment
        score.record(sample(253.0, eligible = false))
        score.record(sample(254.0))
        val r = score.finish(255.0)
        assertEquals(1, r.captureGapBreaks)
        assertEquals(3, r.wordRunSegments.size)
        assertEquals(3, r.lineRunSegments.size)
        assertTrue(r.wordRunSegments.all { it.size == 1 })
        assertEquals(3, r.wordOrder.recovered.size)
        assertEquals(4, r.targets[0].eligibleSamples)
    }

    @Test fun abortedUnshownTargetsRemainPlannedAndCapacityLossIsIncomplete() {
        val score = PilotScore(plan(words[0], words[1], words[2]), "g", 0.0, maxSamples = 1)
        score.showStep(0, 10.0)
        score.record(sample(11.0)); score.record(sample(12.0))
        val r = score.finish(20.0)
        assertEquals(listOf(true, false, false), r.targets.map { it.attempted })
        assertNull(r.targets[1].onsetMs); assertNull(r.targets[1].endMs)
        assertEquals(0, r.targets[1].intervalSamples)
        assertEquals(2, r.wordOrder.misses)
        assertEquals(1, r.droppedAtCapacity); assertTrue(r.incomplete)
        val untouched = PilotScore(plan(words[0]), "g", 0.0).finish(0.0)
        assertFalse(untouched.targets[0].attempted)
        assertNull(untouched.targets[0].exactWordGivenEligible)
    }

    @Test fun orderAlignmentCountsExtrasAndMissesFromObservedAssignments() {
        val score = PilotScore(plan(words[0], words[1], words[2]), "g", 0.0)
        score.showStep(0, 0.0)
        score.record(sample(1.0, words[0])); score.record(sample(2.0, words[3])); score.record(sample(3.0, words[2]))
        val r = score.finish(4.0)
        assertEquals(2, r.wordOrder.matches); assertEquals(1, r.wordOrder.misses); assertEquals(1, r.wordOrder.extras)
        assertEquals(2, r.wordOrder.editDistance)
    }

    @Test fun sameWordHoldsCannotValidatePhysiologicalRefixation() {
        fun recover(id: String): PilotScoreResult {
            val p = PilotTasks.build(id, geometry, words)
            val score = PilotScore(p, "g", 5.0)
            p.targets.forEachIndexed { i, target ->
                score.showStep(i, i * 10.0)
                score.record(sample(i * 10.0 + 1, target))
                score.record(sample(i * 10.0 + 6, target))
            }
            return score.finish(p.targets.size * 10.0)
        }
        assertEquals(recover("word_hold").wordOrder, recover("repeated_word_hold").wordOrder)
        assertTrue(PilotTasks.build("repeated_word_hold", geometry, words).referenceLimit.contains("cannot establish"))
    }

    @Test fun displayClockAndSettingsRejectInvalidOrRepeatedSteps() {
        rejects { PilotScore(plan(words[0]), "g", -1.0) }
        rejects { PilotScore(plan(words[0]), "g", Double.NaN) }
        val score = PilotScore(plan(words[0], words[1]), "g", 0.0)
        rejects { score.showStep(1, 1.0) }
        rejects { score.showStep(0, Double.NaN) }
        score.showStep(0, 10.0)
        rejects { score.showStep(0, 11.0) }
        rejects { score.showStep(1, 10.0) }
        rejects { score.showStep(1, 9.0) }
        rejects { score.finish(9.0) }
        score.finish(10.0)
    }
}
