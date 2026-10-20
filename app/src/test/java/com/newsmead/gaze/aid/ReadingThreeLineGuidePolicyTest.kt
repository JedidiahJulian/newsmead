package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Selected synthetic inputs establish presentation contracts, not phone row accuracy. */
class ReadingThreeLineGuidePolicyTest {
    private val geometry = ScanpathGeometry("g", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 400.0, 700.0), 50.0,
        (0..13).map { ScanpathLine(it * 100, it * 100 + 99, 0.0, 400.0,
            it * 50.0, it * 50.0 + 50) }, ScanpathTextDirection.LTR)

    private inner class Session {
        val policy = ReadingAidPolicy(ReadingAidPolicyConfig.evidenceAlignedPrototype(),
            ReaderAidPreferences(guideStyle = ReadingGuideStyle.THREE_LINE,
                wordDefinition = false, focusRegion = false, lineStartCue = false))
        var frame = geometry
        var time = 0.0
        lateinit var latest: ReadingAidContext
        fun sample(line: Int, reason: String? = null) {
            time += 50.0
            val row = frame.lines[line]
            val y = frame.textOriginY + (row.top + row.bottom) / 2
            val sample = ScanpathSample(time, time, if (reason == null) 390.0 else null,
                if (reason == null) 690.0 else null, line, row.startUtf16, row.startUtf16 + 5,
                frame.snapshotId, reason == null, reason,
                displayX = if (reason == null) 40.0 else null,
                displayY = if (reason == null) y else null,
                coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            latest = ReadingAidContext(1, frame.snapshotId, frame.lines.size, line,
                row.startUtf16, row.startUtf16 + 5, time, time, time, reason == null,
                frame, 40.0, sample, locationUncertainty = ReadingLocationUncertainty(1.0, 1.0,
                    "synthetic_fixture_not_phone_accuracy"))
            policy.onObservation(null, latest)
        }
        fun hold(line: Int, samples: Int = 12) { repeat(samples) { sample(line) } }
        fun guide() = policy.activeActions().single { it.aidType == ReadingAidType.CURRENT_LINE }
        fun band() = guide().guideEvidence!!.threeLine!!
    }

    @Test fun stationaryThreeLinePresentationIsSeparateFromMeasuredUncertainty() {
        val s = Session(); s.hold(6, 40)
        val guide = s.guide()
        assertEquals(6, guide.anchor.line)
        assertEquals("CURRENT_THREE_LINE_GUIDE_GRAY_V1", guide.aidFormId)
        assertEquals("A1_SUPPORTED_THREE_LINE_GUIDE_V1", guide.ruleId)
        assertEquals(ReadingGuideRange(500, 799, ReadingGuideUnit.READING_BAND), guide.guideRange)
        assertEquals(6, guide.guideEvidence!!.firstLine)
        assertEquals(6, guide.guideEvidence!!.lastLine)
        assertTrue(guide.guideEvidence!!.measuredUncertainty)
        assertEquals(.09, guide.guideEvidence!!.opacity, .000001)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        assertEquals(.09, s.band().aboveOpacity, .000001)
        assertEquals(.09, s.band().belowOpacity, .000001)
        assertEquals(250.0, s.band().transitionMs, 0.0)
    }

    @Test fun directionPlacesSupportedRowAtTopMiddleOrBottomWithoutOpacityStripes() {
        val s = Session(); s.hold(4); s.hold(5)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        s.hold(6)
        assertEquals(ReadingGuideDirection.DOWN, s.band().direction)
        assertEquals(0.0, s.band().aboveOpacity, .000001)
        assertEquals(.09, s.band().belowOpacity, .000001)
        assertEquals(ReadingGuideRange(600, 899, ReadingGuideUnit.READING_BAND), s.guide().guideRange)
        assertEquals(listOf(4, 5, 6), s.band().directionSupport.map { it.line })
        assertTrue(s.band().directionSupport.all { it.evidenceId in s.guide().evidenceIds })
        s.hold(5)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        assertEquals(ReadingGuideRange(400, 699, ReadingGuideUnit.READING_BAND), s.guide().guideRange)
        s.hold(4)
        assertEquals(ReadingGuideDirection.UP, s.band().direction)
        assertEquals(.09, s.band().aboveOpacity, .000001)
        assertEquals(0.0, s.band().belowOpacity, .000001)
        assertEquals(ReadingGuideRange(200, 499, ReadingGuideUnit.READING_BAND), s.guide().guideRange)
    }

    @Test fun settlingNeedsFreshSameRowSupportAndProgressionCanResumeAfterIt() {
        val s = Session(); listOf(4, 5, 6).forEach { s.hold(it, 8) }
        assertEquals(ReadingGuideDirection.DOWN, s.band().direction)
        repeat(40) { s.policy.onObservation(null, s.latest) }
        assertEquals(ReadingGuideDirection.DOWN, s.band().direction)
        s.hold(6, 10)
        assertEquals(ReadingGuideDirection.DOWN, s.band().direction)
        s.hold(6, 10)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        assertEquals(ReadingGuideRange(500, 799, ReadingGuideUnit.READING_BAND), s.guide().guideRange)
        s.hold(7, 8)
        assertEquals(ReadingGuideDirection.DOWN, s.band().direction)
        assertEquals(ReadingGuideRange(700, 999, ReadingGuideUnit.READING_BAND), s.guide().guideRange)
    }

    @Test fun repeatedCallbacksAndUnsettledReversalCannotManufactureAnotherChange() {
        val s = Session(); s.hold(4); s.hold(5)
        repeat(20) { s.policy.onObservation(null, s.latest) }
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        s.sample(6); s.sample(6)
        assertEquals(5, s.guide().anchor.line)
        s.hold(5)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        s.hold(6)
        assertEquals(ReadingGuideDirection.DOWN, s.band().direction)
        assertEquals(listOf(4, 5, 6), s.band().directionSupport.map { it.line })
    }

    @Test fun missingTrackingBreaksDirectionHistoryWithoutBackfillingCredit() {
        val s = Session(); listOf(4, 5, 6).forEach { s.hold(it) }
        val lastSupported = s.guide().lastCaptureMs
        s.sample(6, "no_face")
        assertEquals(lastSupported, s.guide().lastCaptureMs, 0.0)
        s.hold(6)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        assertEquals(listOf(6), s.band().directionSupport.map { it.line })
    }

    @Test fun contentTranslationDoesNotReverseDirectionAndReflowResetsIt() {
        val s = Session(); listOf(4, 5, 6).forEach { s.hold(it) }
        s.frame = s.frame.copy(textOriginY = -100.0)
        s.hold(6, 4)
        assertEquals(ReadingGuideDirection.DOWN, s.band().direction)
        assertEquals(listOf(4, 5, 6), s.band().directionSupport.map { it.line })
        s.frame = s.frame.copy(snapshotId = "reflow", lines = s.frame.lines.map {
            it.copy(top = it.top * 1.25, bottom = it.bottom * 1.25)
        })
        s.hold(4)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        assertEquals(listOf(4), s.band().directionSupport.map { it.line })
        assertEquals("reflow", s.guide().anchor.geometrySnapshotId)
    }

    @Test fun readerCapScalesCenterAndBothNeighborsTogether() {
        val s = Session(); listOf(4, 5, 6).forEach { s.hold(it) }
        s.policy.updatePreferences(s.policy.preferences.copy(maxStrength = .1), s.latest)
        s.hold(6, 2)
        assertEquals(.019, s.guide().guideEvidence!!.opacity, .000001)
        assertEquals(0.0, s.band().aboveOpacity, .000001)
        assertEquals(.019, s.band().belowOpacity, .000001)
        s.policy.updatePreferences(s.policy.preferences.copy(maxStrength = 1.0,
            adaptiveStrength = false, manualStrength = .1), s.latest)
        s.hold(6, 2)
        assertEquals(.019, s.guide().guideEvidence!!.opacity, .000001)
        assertTrue(s.guide().strength <= .1)
        s.hold(6, 20)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
        assertEquals(.019, s.band().aboveOpacity, .000001)
        assertEquals(.019, s.band().belowOpacity, .000001)
    }

    @Test fun styleSwitchClearsOldFormAndBookmarkChoiceRemainsIndependent() {
        val s = Session(); s.hold(4)
        s.policy.updatePreferences(s.policy.preferences.copy(guideStyle = ReadingGuideStyle.LINE), s.latest)
        assertFalse(s.policy.activeActions().any { it.aidType == ReadingAidType.CURRENT_LINE })
        s.hold(4)
        assertEquals("CURRENT_LINE_HIGHLIGHT_GRAY_V1", s.guide().aidFormId)
        assertNull(s.guide().guideEvidence!!.threeLine)
        assertEquals(ReadingGuideUnit.LINE, s.guide().guideRange!!.unit)
        assertFalse(s.policy.preferences.lineStartCue)
        s.policy.updatePreferences(s.policy.preferences.copy(guideStyle = ReadingGuideStyle.THREE_LINE), s.latest)
        s.hold(4)
        assertEquals(ReadingGuideDirection.BALANCED, s.band().direction)
    }

    @Test fun documentEdgesClipNeighborsWithoutMovingEstimatedCenter() {
        val s = Session(); s.hold(0)
        assertEquals(0, s.guide().anchor.line)
        assertEquals(ReadingGuideRange(0, 199, ReadingGuideUnit.READING_BAND), s.guide().guideRange)
        s.hold(13)
        assertEquals(13, s.guide().anchor.line)
        assertEquals(ReadingGuideRange(1200, 1399, ReadingGuideUnit.READING_BAND), s.guide().guideRange)
    }

    @Test fun threeLineGuideKeepsExistingTrackingLossGraceAndExit() {
        val s = Session(); s.hold(6)
        val guide = s.guide()
        val e = guide.guideEvidence!!
        s.policy.tick(s.latest.copy(nowMs = s.time + 750))
        assertEquals(guide.id, s.guide().id)
        assertEquals(.045, e.opacityAt(s.time + 900), .000001)
        s.policy.tick(s.latest.copy(nowMs = s.time + 1050))
        assertFalse(s.policy.activeActions().any { it.aidType == ReadingAidType.CURRENT_LINE })
    }
}
