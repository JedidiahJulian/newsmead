package com.newsmead.gaze.scanpath.replay

import com.newsmead.gaze.aid.*
import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Coordinates are mapped through fixture rectangles, then the real engine and policy run. */
class ScanpathReplayScenariosTest {
    private val geometry = ScanpathGeometry("screen", 80.0, 120.0,
        ScanpathRect(80.0, 120.0, 480.0, 240.0), 20.0,
        (0..2).map { ScanpathLine(it * 20, it * 20 + 19, 0.0, 400.0, it * 40.0, it * 40.0 + 20) },
        ScanpathTextDirection.LTR)
    private val words = (0..11).map { i ->
        val line = i / 4
        ReplayWord(WordRange(i * 5, i * 5 + 4), line,
            ScanpathRect(90.0 + i % 4 * 100, 120.0 + line * 40,
                170.0 + i % 4 * 100, 140.0 + line * 40))
    }
    private class Result {
        val events = mutableListOf<ScanpathEvent>()
        val decisions = mutableListOf<ReadingAidDecision>()
        val active = mutableListOf<List<ReadingAidAction>>()
        lateinit var engine: ScanpathEngine
        val actions get() = decisions.filter { it.status == ReadingAidDecisionStatus.CHOSEN }.mapNotNull { it.action }
    }
    private fun run(id: String, focus: Boolean = true): Result {
        val result = Result()
        val profile = ReadingAidPolicyConfig.experimentalPrototype()
        val policy = ReadingAidPolicy(profile, ReaderAidPreferences(focusRegion = focus))
        var context = ReadingAidContext(0, geometry.snapshotId, geometry.lines.size, null, null, null, 0.0, null, null, false)
        var latest: ScanpathFixation? = null
        val engine = ScanpathEngine(words.map { it.range }, ScanpathConfig(
            returnSweepStartFraction = 0.25, returnSweepEndFraction = 0.75,
            wordResidenceMs = profile.wordMinObservedMs, lineResidenceMs = profile.lineMinObservedMs,
            cycleWindowMs = 2000.0, progressionWindowMs = 1000.0,
            lineProgressionMaxUnitsPerS = 0.5, progressionMinObservedFraction = 0.5),
            onFixation = { latest = it }, onEvent = {
                result.events.add(it)
                result.decisions.addAll(policy.onEvent(it, latest, context))
            })
        result.engine = engine
        engine.updateGeometry(geometry, 0.0)
        ScanpathReplayScenarios.build(id, geometry, words).forEach { point ->
            val x = point.x
            val y = point.y
            val matches = words.filter { x != null && y != null &&
                x > it.bounds.left && x < it.bounds.right &&
                y > it.bounds.top && y < it.bounds.bottom }
            val word = matches.singleOrNull()
            val line = geometry.lines.indices.singleOrNull { i ->
                val row = geometry.lines[i]
                x != null && y != null && x - geometry.textOriginX in row.left..row.right &&
                    y - geometry.textOriginY in row.top..row.bottom
            }
            assertTrue(!point.eligible || word != null)
            val capture = 10000 + point.offsetMs
            val delivery = capture + 10
            context = ReadingAidContext(0, geometry.snapshotId, geometry.lines.size, line,
                word?.range?.startUtf16, word?.range?.endUtf16, delivery, capture, delivery, point.eligible)
            if (!point.eligible) result.decisions.addAll(policy.onObservation(null, context))
            val accepted = engine.onSample(ScanpathSample(capture, delivery, x, y, line,
                word?.range?.startUtf16, word?.range?.endUtf16, geometry.snapshotId, point.eligible))
            result.decisions.addAll(policy.onObservation(if (accepted) engine.currentCandidateEvidence() else null, context))
            result.active.add(policy.activeActions())
        }
        return result
    }

    @Test fun everyScenarioProducesItsDeclaredPatternsAndAidsFromRawCoordinates() {
        ScanpathReplayScenarios.scenarios.forEach { scenario ->
            val points = ScanpathReplayScenarios.build(scenario.id, geometry, words)
            assertTrue(points.first().offsetMs >= 200)
            assertTrue(points.zipWithNext().all { (a, b) -> b.offsetMs - a.offsetMs in 1.0..50.0 })
            val result = run(scenario.id)
            assertTrue("${scenario.id}: patterns ${result.events.map { it.patternId }}",
                result.events.map { it.patternId }.toSet().containsAll(scenario.expectedPatternIds))
            assertTrue("${scenario.id}: aids ${result.actions.map { it.aidType }}",
                result.actions.map { it.aidType.name }.toSet().containsAll(scenario.expectedAidTypes))
            assertTrue(result.actions.all { it.lastCaptureMs <= it.evidenceAvailableMs && it.evidenceAvailableMs <= it.onsetMs })
        }
    }

    @Test fun sweepIsObservedAndCuesItsLandingLine() {
        val result = run("return_sweep")
        val sweep = result.events.single { it.patternId == "P09" }
        assertEquals("observed", sweep.metrics["status"])
        val cue = result.actions.single { it.aidType == ReadingAidType.NEXT_LINE_START }
        assertEquals(1, cue.anchor.line)
        assertTrue(sweep.eventId in cue.eventIds)
    }

    @Test fun distinctSameWordFixationsPrecedeIncreasingMagnifierStrength() {
        val result = run("word_refixations")
        assertEquals(3, result.engine.fixationCount)
        assertEquals(2, result.events.count { it.patternId == "P04" })
        val fs = result.engine.fixations
        assertTrue(fs.zipWithNext().all { (a, b) -> a.wordStartUtf16 == b.wordStartUtf16 &&
            kotlin.math.abs(a.x - b.x) > geometry.lineHeightPx * 0.5 })
        val aids = result.actions.filter { it.aidType == ReadingAidType.WORD_MAGNIFIER }
        assertTrue(aids.size >= 2)
        assertTrue(aids.last().strength > aids.first().strength)
        assertTrue(aids.last().evidenceIds.any { it.startsWith("candidate:") })
        assertTrue(result.events.none { it.patternId == "P11" })
    }

    @Test fun focusRequiresOptInAndActualRecentResidenceAndSlowProgression() {
        val result = run("line_cycle")
        val aid = result.actions.first { it.aidType == ReadingAidType.FOCUS_REGION }
        val support = result.events.filter { it.eventId in aid.eventIds }
        assertTrue(support.any { it.patternId == "P11" && it.resolution == "line" })
        assertTrue(support.any { it.metrics["kind"] == "residence_update" &&
            (it.metrics["observed_span_ms"] as Number).toDouble() >= 1000 })
        assertTrue(support.any { it.metrics["slow_flag"] == true })
        assertTrue(run("line_cycle", focus = false).actions.none { it.aidType == ReadingAidType.FOCUS_REGION })
    }

    @Test fun lossClearsBeforeFreshEvidenceReacquiresLineWithoutBridging() {
        val points = ScanpathReplayScenarios.build("loss_recovery", geometry, words)
        val result = run("loss_recovery")
        val loss = points.indexOfFirst { !it.eligible }
        assertTrue(result.active[loss - 1].isNotEmpty())
        assertTrue(result.active[loss].isEmpty())
        assertTrue(result.active.last().any { it.aidType == ReadingAidType.CURRENT_LINE })
        assertTrue(result.events.any { it.patternId == "P13" && it.metrics["reason"] == "invalid_sample" })
        assertTrue(result.events.none { it.patternId == "P04" })
    }

    @Test fun impossibleGeometryExplainsTheMissingRequirement() {
        fun rejects(id: String, g: ScanpathGeometry = geometry, w: List<ReplayWord> = words, detail: String) {
            try { ScanpathReplayScenarios.build(id, g, w); fail("Expected rejection") }
            catch (e: IllegalArgumentException) { assertTrue(e.message.orEmpty().contains(detail)) }
        }
        rejects("word_refixations", w = words.map { it.copy(bounds = it.bounds.copy(right = it.bounds.left + 20)) }, detail = "three dispersion")
        rejects("return_sweep", geometry.copy(textDirection = ScanpathTextDirection.RTL), detail = "LTR")
        rejects("navigation", w = words.take(2), detail = "four consecutive")
        rejects("line_cycle", w = words.take(4), detail = "adjacent")
        rejects("loss_recovery", geometry.copy(visibleRectScreen = ScanpathRect(0.0, 0.0, 10.0, 10.0)), detail = "visible word")
        rejects("loss_recovery", w = words.mapIndexed { i, w -> if (i == 1) w.copy(bounds = words[0].bounds) else w }, detail = "ambiguous")
        rejects("unknown", detail = "Unknown replay scenario")
    }

    @Test fun translatedScreenOriginTranslatesCoordinatesAndKeepsTimes() {
        val dx = 300.0
        val dy = 500.0
        fun translate(r: ScanpathRect) = ScanpathRect(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
        val moved = geometry.copy(textOriginX = geometry.textOriginX + dx, textOriginY = geometry.textOriginY + dy,
            visibleRectScreen = translate(geometry.visibleRectScreen))
        val movedWords = words.map { it.copy(bounds = translate(it.bounds)) }
        ScanpathReplayScenarios.scenarios.forEach { scenario ->
            val original = ScanpathReplayScenarios.build(scenario.id, geometry, words)
            val shifted = ScanpathReplayScenarios.build(scenario.id, moved, movedWords)
            assertEquals(original.map { it.copy(x = it.x?.plus(dx), y = it.y?.plus(dy)) }, shifted)
            assertTrue(shifted.last().offsetMs < 30000)
        }
    }
}
