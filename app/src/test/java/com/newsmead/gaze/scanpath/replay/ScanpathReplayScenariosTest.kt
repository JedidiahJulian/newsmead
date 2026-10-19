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
        val candidates = mutableListOf<ScanpathCandidateEvidence?>()
        val samples = mutableListOf<ScanpathSample>()
        lateinit var engine: ScanpathEngine
        val actions get() = decisions.filter { it.status == ReadingAidDecisionStatus.CHOSEN }.mapNotNull { it.action }
    }
    private fun run(id: String, focus: Boolean = true, scriptedPoints: List<ReplayPoint>? = null,
                    continuationStrengthProbe: Boolean = false,
                    fixtureGeometry: ScanpathGeometry = geometry,
                    fixtureWords: List<ReplayWord> = words,
                    currentBookmarkProfile: Boolean = id.startsWith("bookmark_") ||
                        id in setOf("repeated_interruptions", "brief_interruptions",
                            "interruption_place_search", "line_alternation")): Result {
        val geometry = fixtureGeometry
        val words = fixtureWords
        val result = Result()
        val profile = if (id == "broad_reread") ReadingAidPolicyConfig.evidenceAlignedPrototype()
            else if (currentBookmarkProfile) ReadingAidPolicyConfig.definitionPrototype()
            else ReadingAidPolicyConfig.experimentalPrototype()
        val policy = ReadingAidPolicy(profile, ReaderAidPreferences(focusRegion = focus,
            wordDefinition = false, adaptiveStrength = !continuationStrengthProbe,
            manualStrength = if (continuationStrengthProbe) 0.2 else 0.5,
            maxStrength = if (continuationStrengthProbe) 0.1 else 1.0))
        var context = ReadingAidContext(0, geometry.snapshotId, geometry.lines.size, null, null, null, 0.0, null, null, false,
            geometry, null)
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
        (scriptedPoints ?: ScanpathReplayScenarios.build(id, geometry, words)).forEach { point ->
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
            val selectedReplay = id in setOf("interruption_place_search", "line_alternation")
            val sample = ScanpathSample(capture, delivery,
                if (selectedReplay) null else x, if (selectedReplay) null else y, line,
                word?.range?.startUtf16, word?.range?.endUtf16, geometry.snapshotId, point.eligible,
                if (point.eligible) null else point.sourceReason ?: if (x != null && y != null) "outside_visible_text" else "synthetic_loss",
                displayX = if (selectedReplay) x else null, displayY = if (selectedReplay) y else null,
                coordinateStream = if (selectedReplay) ScanpathCoordinateStream.ARTICLE_SMOOTHED else ScanpathCoordinateStream.CALIBRATED_RAW)
            result.samples.add(sample)
            context = ReadingAidContext(0, geometry.snapshotId, geometry.lines.size, line,
                word?.range?.startUtf16, word?.range?.endUtf16, delivery, capture, delivery, point.eligible,
                geometry, x, rawSample = sample)
            if (!point.eligible) result.decisions.addAll(policy.onObservation(null, context))
            val accepted = engine.onSample(sample)
            val candidate = if (accepted) engine.currentCandidateEvidence() else null
            result.candidates.add(candidate)
            result.decisions.addAll(policy.onObservation(candidate, context))
            result.active.add(policy.activeActions())
        }
        return result
    }

    @Test fun everyScenarioProducesItsDeclaredPatternsAndAidsFromSelectedCoordinates() {
        // The historical three-row fixture cannot contain the new four-row V5 route.
        ScanpathReplayScenarios.scenarios.filter { it.id != "broad_reread" }.forEach { scenario ->
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

    @Test fun broadRereadScenarioRunsThroughCurrentLocationAndFocusPolicy() {
        val g = geometry.copy(visibleRectScreen = geometry.visibleRectScreen.copy(bottom = 360.0),
            lines = (0..5).map { ScanpathLine(it * 20, it * 20 + 19, 0.0, 400.0,
                it * 40.0, it * 40.0 + 20) })
        val w = (0..23).map { i -> ReplayWord(WordRange(i * 5, i * 5 + 4), i / 4,
            ScanpathRect(90.0 + i % 4 * 100, 120.0 + i / 4 * 40,
                170.0 + i % 4 * 100, 140.0 + i / 4 * 40)) }
        val result = run("broad_reread", fixtureGeometry = g, fixtureWords = w)
        val focus = result.actions.first { it.aidType == ReadingAidType.FOCUS_REGION }
        assertEquals("A4_BROAD_REREADING_V5", focus.ruleId)
        assertEquals(listOf(0, 3, 0, 1, 2), focus.focusEvidence!!.reread!!.visits.map { it.line })
        assertTrue(result.events.any { it.patternId == "P08" })
        val dx = 100.0; val dy = 200.0
        val shiftedGeometry = g.copy(textOriginX = g.textOriginX + dx, textOriginY = g.textOriginY + dy,
            visibleRectScreen = g.visibleRectScreen.let { ScanpathRect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) })
        val shiftedWords = w.map { it.copy(bounds = it.bounds.let { b ->
            ScanpathRect(b.left + dx, b.top + dy, b.right + dx, b.bottom + dy) }) }
        assertEquals(ScanpathReplayScenarios.build("broad_reread", g, w).map {
            it.copy(x = it.x?.plus(dx), y = it.y?.plus(dy)) },
            ScanpathReplayScenarios.build("broad_reread", shiftedGeometry, shiftedWords))
    }

    @Test fun normalSweepIsObservedWithoutContinuationCue() {
        val result = run("return_sweep")
        val sweep = result.events.single { it.patternId == "P09" }
        assertEquals("observed", sweep.metrics["status"])
        assertTrue(result.actions.none { it.aidType == ReadingAidType.NEXT_LINE_START })
    }

    @Test fun currentBookmarkScenariosRememberObservedSourceAndClearOnReturn() {
        listOf("bookmark_return" to 1, "bookmark_skip" to 0, "bookmark_lookaway" to 0,
            "bookmark_tracking_loss" to 0).forEach { (id, source) ->
            val result = run(id, continuationStrengthProbe = true)
            val bookmark = result.actions.single { it.aidType == ReadingAidType.READING_BOOKMARK }
            assertEquals(source, bookmark.anchor.line)
            assertEquals("LAST_READ_LINE_BOOKMARK_V1", bookmark.aidFormId)
            assertEquals(1.0, bookmark.strength, 0.0)
            assertTrue(bookmark.evidenceIds.isNotEmpty())
            assertTrue(bookmark.lastCaptureMs <= bookmark.evidenceAvailableMs)
            assertTrue(bookmark.evidenceAvailableMs <= bookmark.onsetMs)
            assertTrue(result.decisions.any { it.status == ReadingAidDecisionStatus.CLEARED &&
                it.action?.id == bookmark.id })
            assertTrue(result.active.last().none { it.aidType == ReadingAidType.READING_BOOKMARK })
            assertTrue(result.actions.none { it.aidType == ReadingAidType.NEXT_LINE_START })
        }
    }

    @Test fun currentNormalSweepAndBriefUnclassifiedLossNeedNoBookmark() {
        for (id in listOf("return_sweep", "loss_recovery")) {
            val result = run(id, currentBookmarkProfile = true)
            assertTrue(result.actions.none { it.aidType == ReadingAidType.READING_BOOKMARK })
        }
    }

    @Test fun missingCoordinateReplayMarksSavedLineWithDeliveryEvidenceThenClearsOnReturn() {
        val points = ScanpathReplayScenarios.build("bookmark_tracking_loss", geometry, words)
        val result = run("bookmark_tracking_loss")
        val bookmark = result.actions.single { it.aidType == ReadingAidType.READING_BOOKMARK }
        val e = bookmark.bookmarkEvidence!!
        assertEquals("sustained_gaze_unavailable", e.departureReason)
        assertEquals("delivery_elapsed_time", e.departureTimeBasis)
        assertNull(e.departureFirstCaptureMs)
        assertNull(e.departureLastCaptureMs)
        assertEquals(e.source.lastCaptureMs, bookmark.lastCaptureMs, 0.0)
        assertTrue(bookmark.evidenceAvailableMs - e.departureFirstDeliveryMs!! >= 500.0)
        assertTrue(points.filter { it.sourceReason == "no_face" }.all { it.x == null && it.y == null })
        assertTrue(result.active.last().none { it.aidType == ReadingAidType.READING_BOOKMARK })
        assertTrue(result.actions.none { it.aidType == ReadingAidType.FOCUS_REGION })
    }

    @Test fun repeatedInterruptionFixtureContainsDistinctNullCoordinateGapsAndShortSupportedReturns() {
        for (id in listOf("repeated_interruptions", "brief_interruptions")) {
            val points = ScanpathReplayScenarios.build(id, geometry, words)
            val starts = points.indices.filter { !points[it].eligible && (it == 0 || points[it - 1].eligible) }
            assertEquals(if (id == "repeated_interruptions") 4 else 3, starts.size)
            starts.forEach { start ->
                val end = points.indices.first { it >= start && points[it].eligible }
                val gap = points.subList(start, end)
                assertTrue(gap.all { it.sourceReason == "no_face" && it.x == null && it.y == null })
                val span = gap.last().offsetMs - gap.first().offsetMs
                if (id == "repeated_interruptions") assertTrue(span >= 500) else assertTrue(span < 500)
                val returnEnd = points.indices.firstOrNull { it > end && !points[it].eligible } ?: points.size
                assertEquals(350.0, points[returnEnd - 1].offsetMs - points[end].offsetMs, 0.0)
            }
            assertTrue(points.last().offsetMs - points.first().offsetMs < 30000)
            val wordResidence = run(id).events.filter { it.patternId == "P12" && it.resolution == "word" &&
                it.metrics["kind"] == "residence_update" }
            assertTrue(wordResidence.all { (it.metrics["observed_span_ms"] as Number).toDouble() <
                ReadingAidPolicyConfig.definitionPrototype().wordMinObservedMs })
        }
        val brief = run("brief_interruptions")
        assertTrue(brief.actions.none { it.aidType == ReadingAidType.FOCUS_REGION ||
            it.aidType == ReadingAidType.READING_BOOKMARK })
    }

    @Test fun repeatedInterruptionsCountCompletedEpisodesAndStartFocusOnThirdReturn() {
        val result = run("repeated_interruptions")
        val focus = result.actions.filter { it.aidType == ReadingAidType.FOCUS_REGION }
        val first = focus.first()
        val initial = checkNotNull(first.focusEvidence)
        assertEquals(3, initial.episodes.size)
        assertEquals(0.5, first.strength, 0.0)
        val fourth = focus.first { it.focusEvidence!!.episodes.size == 4 }
        assertEquals(1.0, fourth.strength, 0.0)
        assertEquals(4, fourth.focusEvidence!!.episodes.map { it.episodeId }.distinct().size)
        val points = ScanpathReplayScenarios.build("repeated_interruptions", geometry, words)
        val thirdReturn = points.indices.filter { points[it].eligible && it > 0 && !points[it - 1].eligible }[2]
        assertTrue(first.onsetMs >= 10000 + points[thirdReturn].offsetMs)
        focus.forEach { action ->
            val evidence = checkNotNull(action.focusEvidence)
            assertTrue(evidence.episodes.size >= 3)
            evidence.episodes.forEach { episode ->
                assertEquals("delivery_elapsed_time", episode.timeBasis)
                assertEquals(episode.missingFirstDeliveryMs - 10.0, episode.missingFirstCaptureMs!!, 0.0)
                assertEquals(episode.missingLastDeliveryMs - 10.0, episode.missingLastCaptureMs!!, 0.0)
                assertEquals(listOf("no_face"), episode.missingReasons)
                assertTrue(episode.missingSampleCount > 1)
                assertTrue(episode.missingLastDeliveryMs - episode.missingFirstDeliveryMs >= 500)
                assertTrue(checkNotNull(episode.source).evidenceAvailableMs <= episode.missingFirstDeliveryMs)
                val presence = checkNotNull(episode.presenceRecovery)
                assertNull(episode.recovery)
                assertTrue(episode.missingLastDeliveryMs < presence.firstCaptureMs)
                assertTrue(presence.lastCaptureMs <= presence.confirmedDeliveryMs)
                assertTrue(presence.sampleCount >= 3)
                assertTrue(presence.lastCaptureMs - presence.firstCaptureMs >= 100)
                assertEquals(presence.confirmedDeliveryMs, episode.completedDeliveryMs, 0.0)
                assertTrue(episode.completedDeliveryMs <= action.onsetMs)
            }
        }
        assertTrue(run("repeated_interruptions", focus = false).actions.none {
            it.aidType == ReadingAidType.FOCUS_REGION
        })
    }

    @Test fun articleSearchCompletesThirdReturnBeforeAnyStableRecoveryAndUsesKnownVisibleAnchor() {
        val id = "interruption_place_search"
        val points = ScanpathReplayScenarios.build(id, geometry, words)
        val starts = points.indices.filter { !points[it].eligible && (it == 0 || points[it - 1].eligible) }
        val returns = points.indices.filter { it > 0 && points[it].eligible && !points[it - 1].eligible }
        assertEquals(3, starts.size)
        assertEquals(3, returns.size)
        val result = run(id)
        starts.zip(returns).forEach { (start, end) ->
            val gap = points.subList(start, end)
            assertTrue(gap.all { !it.eligible && it.x == null && it.y == null && it.sourceReason == "no_face" })
            assertEquals(650.0, gap.last().offsetMs - gap.first().offsetMs, 0.0)
            val search = points.subList(end, end + 7)
            assertEquals(300.0, search.last().offsetMs - search.first().offsetMs, 0.0)
            assertTrue(search.zipWithNext().all { (a, b) ->
                b.offsetMs - a.offsetMs == 50.0 &&
                    kotlin.math.abs(a.x!! - b.x!!) + kotlin.math.abs(a.y!! - b.y!!) > geometry.lineHeightPx * 0.5
            })
            assertTrue(result.candidates.subList(end, end + 7).all { it == null })
        }
        val third = returns[2]
        val finalHold = third + 7
        val first = result.actions.first { it.aidType == ReadingAidType.FOCUS_REGION }
        val evidence = checkNotNull(first.focusEvidence)
        assertEquals(3, evidence.episodeCount)
        assertEquals(0.5, first.strength, 0.0)
        assertEquals(10000 + points[third + 2].offsetMs + 10, first.onsetMs, 0.0)
        assertTrue(first.onsetMs < 10000 + points[finalHold].offsetMs)
        assertTrue(result.active.take(third + 2).all { actions -> actions.none { it.aidType == ReadingAidType.FOCUS_REGION } })
        assertEquals(0, first.anchor.line)
        assertEquals(geometry.snapshotId, first.anchor.geometrySnapshotId)
        assertTrue(evidence.support.lastCaptureMs < 10000 + points[starts.first()].offsetMs)
        assertTrue(result.candidates[finalHold + 2] != null)
        evidence.episodes.forEach { episode ->
            assertNull(episode.recovery)
            val presence = checkNotNull(episode.presenceRecovery)
            assertEquals(3, presence.sampleCount)
            assertEquals(100.0, presence.lastCaptureMs - presence.firstCaptureMs, 0.0)
            assertEquals(ScanpathCoordinateStream.ARTICLE_SMOOTHED.id, presence.coordinateStream)
        }
        assertTrue(result.events.none { it.patternId == "P11" })
    }

    @Test fun ongoingFourthSupportedVisitActivatesWithoutFinalizedFastP11() {
        val id = "line_alternation"
        val points = ScanpathReplayScenarios.build(id, geometry, words)
        val result = run(id)
        val first = result.actions.first { it.aidType == ReadingAidType.FOCUS_REGION }
        val e = checkNotNull(first.focusEvidence)
        assertEquals(0.5, first.strength, 0.0)
        assertEquals("A4_FONT_SCALED_LINE_VISITS_V3", e.ruleId)
        assertTrue(e.episodes.isEmpty())
        assertTrue(e.cycleEventIds.isEmpty())
        val alternation = e.alternations.single()
        assertEquals(listOf(0, 1, 0, 1), alternation.visits.map { it.line })
        val span = alternation.visits.last().lastCaptureMs - alternation.visits.first().firstCaptureMs
        assertTrue(span > 2000 && span < 8000)
        assertTrue(alternation.visits.last().evidenceId.startsWith("candidate:"))
        assertEquals(3, result.engine.fixationCount)
        val ongoing = checkNotNull(result.engine.currentCandidateEvidence())
        assertEquals(ongoing.startMs, alternation.visits.last().firstCaptureMs, 0.0)
        assertEquals(10000 + points.last().offsetMs, ongoing.endMs, 0.0)
        assertTrue(first.onsetMs <= ongoing.endMs + 10)
        assertTrue(result.events.none { it.patternId == "P11" })
        assertTrue(run(id, focus = false).actions.none { it.aidType == ReadingAidType.FOCUS_REGION })
    }

    @Test fun newFocusFixturesProduceIdenticalCameraFreeSelectedCoordinateTraces() {
        for (id in listOf("interruption_place_search", "line_alternation")) {
            assertEquals(ScanpathReplayScenarios.build(id, geometry, words),
                ScanpathReplayScenarios.build(id, geometry, words))
            val a = run(id)
            val b = run(id)
            assertEquals(a.events, b.events)
            assertEquals(a.decisions, b.decisions)
            assertEquals(a.active, b.active)
            assertEquals(a.candidates, b.candidates)
            assertEquals(a.samples, b.samples)
            assertTrue(a.samples.all { it.coordinateStream == ScanpathCoordinateStream.ARTICLE_SMOOTHED &&
                it.rawX == null && it.rawY == null })
            assertTrue(a.samples.filter { it.eligible }.all { it.displayX != null && it.displayY != null })
        }
    }

    @Test fun unexpectedContinuationDirectionsCueExpectedPhysicalLine() {
        listOf("continuation_return" to (2 to "P08"), "continuation_skip" to (1 to "P06")).forEach { (id, expected) ->
            val result = run(id, continuationStrengthProbe = true)
            val cue = result.actions.single { it.aidType == ReadingAidType.NEXT_LINE_START }
            assertEquals(expected.first, cue.anchor.line)
            assertEquals(1.0, cue.strength, 0.0)
            assertTrue(cue.evidenceIds.size >= 3)
            assertTrue(cue.firstCaptureMs <= cue.lastCaptureMs)
            assertTrue(cue.lastCaptureMs <= cue.evidenceAvailableMs)
            assertTrue(cue.evidenceAvailableMs <= cue.onsetMs)
            assertEquals("A2_CONTINUATION_MISMATCH_V1", cue.ruleId)
            assertEquals("CONTINUATION_LINE_START_CUE_V1", cue.aidFormId)
            assertTrue(result.events.any { it.patternId == expected.second })
            assertTrue(result.decisions.any { it.status == ReadingAidDecisionStatus.CLEARED &&
                it.reason == "expected_line_arrival" })
        }
    }

    /** Paragraph-final rows have one word; only the progress source spans the column. */
    private fun continuationFixture(sourceLine: Int, lineCount: Int = 3): Pair<ScanpathGeometry, List<ReplayWord>> {
        val rows = (0 until lineCount).map { line ->
            ScanpathLine(line * 20, line * 20 + 19, 0.0,
                if (line == sourceLine) 400.0 else 80.0, line * 40.0, line * 40.0 + 20)
        }
        val g = geometry.copy(lines = rows,
            visibleRectScreen = geometry.visibleRectScreen.copy(bottom = 120.0 + lineCount * 40))
        val w = rows.indices.flatMap { line ->
            if (line == sourceLine) listOf(
                ReplayWord(WordRange(line * 20, line * 20 + 4), line,
                    ScanpathRect(90.0, 120.0 + line * 40, 170.0, 140.0 + line * 40)),
                ReplayWord(WordRange(line * 20 + 5, line * 20 + 9), line,
                    ScanpathRect(390.0, 120.0 + line * 40, 470.0, 140.0 + line * 40)))
            else listOf(ReplayWord(WordRange(line * 20, line * 20 + 4), line,
                ScanpathRect(90.0, 120.0 + line * 40, 150.0, 140.0 + line * 40)))
        }
        return g to w
    }

    private fun assertContinuation(id: String, g: ScanpathGeometry, w: List<ReplayWord>, source: Int) {
        val points = ScanpathReplayScenarios.build(id, g, w)
        // Endpoint selection must not change capture intervals or extend holds to manufacture support.
        assertEquals(ScanpathReplayScenarios.build(id, geometry, words).map { it.offsetMs }, points.map { it.offsetMs })
        val result = run(id, continuationStrengthProbe = true, fixtureGeometry = g, fixtureWords = w)
        val cue = result.actions.single { it.aidType == ReadingAidType.NEXT_LINE_START }
        assertEquals(source + 1, cue.anchor.line)
        assertTrue(result.events.any { it.patternId == if (id == "continuation_return") "P08" else "P06" })
        assertTrue(result.decisions.any { it.status == ReadingAidDecisionStatus.CLEARED &&
            it.reason == "expected_line_arrival" })
        assertTrue(cue.evidenceIds.size >= 3)
        assertTrue(cue.firstCaptureMs <= cue.lastCaptureMs && cue.lastCaptureMs <= cue.evidenceAvailableMs &&
            cue.evidenceAvailableMs <= cue.onsetMs)
        val row = g.lines[source]
        fun fraction(p: ReplayPoint) = (p.x!! - g.textOriginX - row.left) / (row.right - row.left)
        assertTrue(fraction(points[0]) <= 0.25)
        assertTrue(fraction(points[6]) >= 0.75)
        // Both non-source rows have a single real center at 50%, outside the first quarter.
        assertEquals(120.0, points[12].x!!, 0.0)
        assertEquals(120.0, points[18].x!!, 0.0)
    }

    @Test fun continuationSupportsOneWordLandingAndExpectedRowsThroughEngineAndPolicy() {
        listOf("continuation_return" to 1, "continuation_skip" to 0).forEach { (id, source) ->
            val (g, w) = continuationFixture(source)
            assertContinuation(id, g, w, source)
        }
    }

    @Test fun continuationSelectsLaterContiguousRowsWhenFirstSourceLacksProgressEndpoints() {
        listOf("continuation_return" to 2, "continuation_skip" to 1).forEach { (id, source) ->
            val (g, w) = continuationFixture(source, lineCount = 4)
            assertContinuation(id, g, w, source)
        }
    }

    @Test fun continuationAbstainsWithoutSourceEndpointsOrFullyVisibleSuccessor() {
        listOf("continuation_return" to 1, "continuation_skip" to 0).forEach { (id, source) ->
            val (g, w) = continuationFixture(source)
            val (shortRows, shortWords) = continuationFixture(sourceLine = -1)
            listOf(Triple(shortRows, shortWords, "source word centers"),
                Triple(g.copy(visibleRectScreen = g.visibleRectScreen.copy(bottom = 210.0)), w,
                    "fully visible physical lines")).forEach { (invalid, fixture, reason) ->
                try { ScanpathReplayScenarios.build(id, invalid, fixture); fail("Expected geometry rejection") }
                catch (e: IllegalArgumentException) { assertTrue(e.message.orEmpty().contains(reason)) }
            }
        }
    }

    @Test fun missingnessAfterSourceEndPreventsContinuationCue() {
        val points = ScanpathReplayScenarios.build("continuation_skip", geometry, words).toMutableList()
        points.add(12, ReplayPoint(points[11].offsetMs + 50, null, null, eligible = false))
        for (i in 13 until points.size) points[i] = points[i].copy(offsetMs = points[i].offsetMs + 50)
        assertTrue(run("continuation_skip", scriptedPoints = points, continuationStrengthProbe = true).actions.none {
            it.aidType == ReadingAidType.NEXT_LINE_START
        })
    }

    @Test fun continuedReadingElsewhereClearsMismatchCue() {
        val points = ScanpathReplayScenarios.build("continuation_skip", geometry, words).take(18).toMutableList()
        val other = words.first { it.line == 0 }
        val x = (other.bounds.left + other.bounds.right) / 2
        val y = (other.bounds.top + other.bounds.bottom) / 2
        val start = points.last().offsetMs + 50
        for (dt in 0..250 step 50) points.add(ReplayPoint(start + dt, x, y))
        val result = run("continuation_skip", scriptedPoints = points, continuationStrengthProbe = true)
        assertTrue(result.actions.any { it.aidType == ReadingAidType.NEXT_LINE_START })
        assertTrue(result.decisions.any { it.status == ReadingAidDecisionStatus.CLEARED &&
            it.reason == "continued_reading_elsewhere" })
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
        rejects("line_alternation", w = words.take(4), detail = "adjacent fully visible")
        rejects("interruption_place_search", w = words.take(8), detail = "two separated physical search lines")
        rejects("line_alternation", geometry.copy(visibleRectScreen = geometry.visibleRectScreen.copy(bottom = 170.0)),
            detail = "fully visible")
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
        ScanpathReplayScenarios.scenarios.filter { it.id != "broad_reread" }.forEach { scenario ->
            val original = ScanpathReplayScenarios.build(scenario.id, geometry, words)
            val shifted = ScanpathReplayScenarios.build(scenario.id, moved, movedWords)
            assertEquals(original.map { it.copy(x = it.x?.plus(dx), y = it.y?.plus(dy)) }, shifted)
            assertTrue(shifted.last().offsetMs < 30000)
        }
    }
}
