package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic selected-stream observations, with the live runtime's detector and callback order. */
class FocusLiveContinuityTest {
    private class LivePipeline(shortRows: Boolean = false) {
        private val config = ReadingAidPolicyConfig.definitionPrototype()
        private val detector = ScanpathConfig(returnSweepStartFraction = 0.25, returnSweepEndFraction = 0.75,
            wordResidenceMs = config.wordMinObservedMs, lineResidenceMs = config.lineMinObservedMs,
            cycleWindowMs = config.cycleWindowMs, progressionWindowMs = 1000.0,
            lineProgressionMaxUnitsPerS = 0.5, progressionMinObservedFraction = 0.5)
        private var geometry = ScanpathGeometry("g1", 0.0, 0.0, ScanpathRect(0.0, 0.0, 100.0, 60.0),
            20.0, (0..2).map { ScanpathLine(it * 20, it * 20 + 20, 0.0, if (shortRows) 40.0 else 100.0,
                it * 20.0, it * 20.0 + 20.0) }, ScanpathTextDirection.LTR)
        val episodes = mutableListOf<ReadingAidFocusEpisode>()
        val policy = ReadingAidPolicy(config, ReaderAidPreferences(wordDefinition = false), detector,
            onFocusEpisode = episodes::add)
        val patterns = mutableListOf<ScanpathEvent>()
        val gapActions = mutableListOf<List<ReadingAidAction>>()
        private var context = ReadingAidContext(0, "g1", 3, null, null, null, 0.0, null, null,
            false, geometry)
        private var latest: ScanpathFixation? = null
        private val engine = ScanpathEngine((0..11).map { WordRange(it * 5, it * 5 + 4) }, detector,
            onFixation = { latest = it }, onEvent = { event ->
                patterns.add(event)
                policy.onEvent(event, latest, context)
                if (event.patternId == "P13" && event.metrics["reason"] == "unclassified_fixation_gap")
                    gapActions.add(policy.activeActions())
            }, retainEvents = false)

        init { engine.updateGeometry(geometry, 0.0) }

        fun sample(t: Double, x: Double = 10.0, line: Int = 0, missing: Boolean = false,
                   selectedY: Double = line * 20.0 + 10) {
            val word = (line * 4 + if (x >= 50) 2 else 0).takeIf {
                x in geometry.lines[line].left..geometry.lines[line].right }
            val sample = ScanpathSample(t, t + 10, null, null,
                line.takeUnless { missing }, word?.times(5).takeUnless { missing },
                word?.let { it * 5 + 4 }.takeUnless { missing }, geometry.snapshotId, !missing,
                if (missing) "no_face" else null, x.takeUnless { missing },
                selectedY.takeUnless { missing }, ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            context = ReadingAidContext(0, geometry.snapshotId, 3, sample.line, sample.wordStartUtf16,
                sample.wordEndUtf16, t + 10, t, t + 10, !missing, geometry, sample.processingX,
                rawSample = sample)
            // Runtime.input clears invalid input before engine.onSample flushes old evidence.
            if (missing) policy.onObservation(null, context)
            val accepted = engine.onSample(sample)
            policy.onObservation(if (accepted) engine.currentCandidateEvidence() else null, context)
        }

        fun hold(t: Double, x: Double = 10.0, line: Int = 0) {
            for (dt in 0..100 step 50) sample(t + dt, x, line)
        }

        fun episode(t: Double, x: Double = 10.0) {
            sample(t, missing = true)
            sample(t + 500, missing = true)
            hold(t + 600, x)
        }

        /** Unestablished movement keeps sample gaps at 50 ms but separates fixations by 200 ms. */
        fun unclassifiedGap(t: Double) {
            sample(t, 70.0)
            sample(t + 50, 10.0)
            sample(t + 100, 70.0)
            hold(t + 150, 40.0)
            sample(t + 300, 70.0)
        }

        fun boundary(geometryChange: Boolean) {
            if (geometryChange) {
                geometry = geometry.copy(snapshotId = "g2")
                context = context.copy(geometrySnapshotId = "g2", geometry = geometry, validLiveInput = false)
                policy.reset(context, preserveCompletedFocus = true) // Same-article scroll, fresh anchors required.
                engine.updateGeometry(geometry, context.nowMs)
            } else engine.boundary("pause", context.nowMs)
        }

        fun focus() = policy.activeActions().singleOrNull { it.aidType == ReadingAidType.FOCUS_REGION }

        fun autoScroll(t: Double) {
            for ((reason, at) in listOf("auto_scroll_start" to t, "auto_scroll_end" to t + 400)) {
                context = context.copy(nowMs = at, validLiveInput = false, rawSample = null,
                    focusInputAllowed = false, bookmarkInputAllowed = false)
                policy.reset(context, preserveCompletedFocus = true) // Runtime navigation boundary.
                engine.boundary(reason, at) // Session callback emits P13 to the same policy.
            }
        }
    }

    @Test fun automaticScrollPreservesCompletedEpisodesWithoutJoiningReadingAcrossMotion() {
        val p = LivePipeline()
        p.hold(200.0)
        p.episode(400.0)
        p.episode(1200.0)
        assertEquals(2, p.episodes.size)
        p.autoScroll(2000.0)
        assertTrue(p.patterns.any { it.patternId == "P13" && it.metrics["reason"] == "auto_scroll_start" })
        assertTrue(p.patterns.any { it.patternId == "P13" && it.metrics["reason"] == "auto_scroll_end" })
        p.hold(2600.0)
        p.episode(2800.0)
        assertEquals(3, p.episodes.size)
        assertEquals(3, p.focus()!!.focusEvidence!!.episodeCount)
        assertFalse(p.patterns.any { it.patternId == "P11" })
    }

    @Test fun completedEpisodesSurviveRealWordAndLineUnclassifiedGap() {
        val p = LivePipeline()
        p.hold(200.0)
        p.episode(400.0)
        p.episode(1200.0)
        assertEquals(2, p.episodes.size)
        p.unclassifiedGap(1950.0)
        assertEquals(setOf("word", "line"), p.patterns.filter {
            it.patternId == "P13" && it.metrics["reason"] == "unclassified_fixation_gap"
        }.map { it.resolution }.toSet())
        assertTrue(p.gapActions.all { actions -> actions.none { it.aidType == ReadingAidType.FOCUS_REGION } })
        p.episode(2350.0)
        assertEquals(3, p.episodes.size)
        // Before the fix the third recovery has only one retained witness and no focus action.
        assertEquals(3, p.focus()!!.focusEvidence!!.episodeCount)
        assertEquals(0.5, p.focus()!!.strength, 0.0)
        assertFalse(p.patterns.any { it.patternId == "P11" })
    }

    @Test fun completedCycleRemainsVisibleThroughBriefGapAndUsesFreshSupport() {
        val p = LivePipeline()
        p.hold(200.0, line = 0)
        p.hold(350.0, line = 1)
        p.hold(500.0, line = 0)
        p.hold(650.0, line = 1)
        p.hold(800.0)
        val cycleIds = p.focus()!!.focusEvidence!!.alternations.map { it.evidenceId }
        assertEquals(1, cycleIds.size)
        p.unclassifiedGap(950.0)
        assertEquals(2, p.gapActions.size)
        assertTrue(p.gapActions.all { actions -> actions.any { it.aidType == ReadingAidType.FOCUS_REGION } })
        // The helper also establishes a post-gap fixation (1100..1200) and flushes it
        // at1250, so restored focus must cite that fresh support, not the old cycle anchor.
        val resumed = p.focus()!!.focusEvidence!!
        assertEquals(cycleIds, resumed.alternations.map { it.evidenceId })
        assertEquals(1100.0, resumed.support.firstCaptureMs, 0.0)
        assertEquals(1200.0, resumed.support.lastCaptureMs, 0.0)
        p.sample(1300.0, 70.0)
        p.sample(1350.0, 70.0)
        assertEquals(cycleIds, p.focus()!!.focusEvidence!!.alternations.map { it.evidenceId })
        // Departing the fifth visit can finalize another overlapping PRE-gap cycle.
        // Check capture support relative to the actual gap, not callback/event counts.
        val firstGapStart = p.patterns.filter { it.patternId == "P13" &&
            it.metrics["reason"] == "unclassified_fixation_gap" }
            .minOf { (it.metrics["at_ms"] as Number).toDouble() }
        assertTrue(p.patterns.filter { it.patternId == "P11" && it.resolution == "line" }
            .all { it.endMs!! < firstGapStart })
    }

    @Test fun explicitPauseResetsHistoryButSameArticleGeometryRetainsCompletedEpisodes() {
        for (geometryChange in listOf(false, true)) {
            val p = LivePipeline()
            p.hold(200.0)
            p.episode(400.0)
            p.episode(1200.0)
            p.boundary(geometryChange)
            p.hold(2000.0)
            p.episode(2200.0)
            assertEquals(3, p.episodes.size)
            if (geometryChange) assertEquals(3, p.focus()!!.focusEvidence!!.episodeCount)
            else assertNull(p.focus())
        }
    }

    @Test fun continuousTrackedMotionKeepsAidVisitsWhileFinalizedP11StillBreaks() {
        val p = LivePipeline()
        p.hold(200.0, line = 0)
        p.hold(350.0, line = 1)
        p.sample(500.0, 70.0)
        p.sample(550.0, 10.0)
        p.sample(600.0, 70.0)
        p.hold(650.0, 40.0)
        p.hold(800.0, 70.0, line = 1)
        p.hold(950.0, 10.0)
        p.sample(1100.0, 70.0, line = 1)
        assertEquals(setOf("word", "line"), p.patterns.filter {
            it.patternId == "P13" && it.metrics["reason"] == "unclassified_fixation_gap"
        }.map { it.resolution }.toSet())
        // Finalized P11 breaks, while the separate aid route observes supported visits
        // through continuously available selected samples between those visits.
        assertFalse(p.patterns.any { it.patternId == "P11" && it.resolution == "line" })
        assertEquals(1, p.focus()!!.focusEvidence!!.alternations.size)
        assertTrue(p.focus()!!.eventIds.isEmpty())
    }

    @Test fun shortRowLineOnlyRecoveriesSupportFocusInsideArticleWidth() {
        val p = LivePipeline(shortRows = true)
        p.hold(200.0, 70.0)
        p.episode(400.0, 70.0)
        p.episode(1200.0, 70.0)
        p.episode(2000.0, 70.0)
        assertEquals(3, p.focus()!!.focusEvidence!!.episodeCount)
        assertNull(p.focus()!!.anchor.wordStartUtf16)
        assertTrue(p.policy.activeActions().any { it.aidType == ReadingAidType.CURRENT_LINE })
    }

    @Test fun shortRowFinalizedCycleSupportsFocusButInvalidArticleOrRowCoordinatesDoNot() {
        val p = LivePipeline(shortRows = true)
        p.hold(200.0, 70.0, 0)
        p.hold(350.0, 70.0, 1)
        p.hold(500.0, 70.0, 0)
        p.hold(650.0, 70.0, 1)
        p.hold(800.0, 70.0, 0)
        assertEquals(1, p.focus()!!.focusEvidence!!.cycleCount)
        // Deliberately inconsistent mapper assignments must not provide fresh focus support.
        p.sample(950.0, 110.0, 1)
        p.sample(1000.0, 110.0, 1)
        p.sample(1050.0, 110.0, 1)
        val held = p.focus()!!
        assertTrue(held.focusEvidence!!.support.lastCaptureMs < 950.0)
        p.sample(1100.0, 70.0, 0, selectedY = 35.0)
        p.sample(1150.0, 70.0, 0, selectedY = 35.0)
        p.sample(1200.0, 70.0, 0, selectedY = 35.0)
        assertEquals(held.focusEvidence!!.support, p.focus()!!.focusEvidence!!.support)
        p.sample(2301.0, 110.0, 1)
        assertNull(p.focus())
    }
    @Test fun activeCycleFocusClearsImmediatelyOnOperationalAndGeometryBoundaries() {
        for (geometryChange in listOf(false, true)) {
            val p = LivePipeline()
            p.hold(200.0, line = 0)
            p.hold(350.0, line = 1)
            p.hold(500.0, line = 0)
            p.hold(650.0, line = 1)
            p.hold(800.0, line = 0)
            assertNotNull(p.focus())
            p.boundary(geometryChange)
            assertNull(p.focus())
            p.hold(950.0)
            if (geometryChange) assertNotNull(p.focus()) else assertNull(p.focus())
        }
    }

}
