package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Full selected-sample -> aid contracts. Synthetic inputs do not validate human benefit. */
class ReadingAidEvidenceV5Test {
    private val geometry = ScanpathGeometry("g", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 400.0, 700.0), 50.0,
        (0..13).map { ScanpathLine(it * 100, it * 100 + 99, 0.0, 400.0, it * 50.0, it * 50.0 + 50) },
        ScanpathTextDirection.LTR)
    private class Session(val policy: ReadingAidPolicy = ReadingAidPolicy(ReadingAidPolicyConfig.evidenceAlignedPrototype())) {
        var time = 0.0
        var latest: ReadingAidContext? = null
    }
    private fun context(t: Double, line: Int, reason: String? = null, uncertainty: ReadingLocationUncertainty? = null): ReadingAidContext {
        val tracked = reason == null
        val sample = ScanpathSample(t, t, if (tracked) 40.0 else null,
            if (tracked) line * 50.0 + 25 else null, line, line * 100, line * 100 + 8,
            "g", tracked, reason)
        return ReadingAidContext(1, "g", 14, line, line * 100, line * 100 + 8, t, t, t,
            tracked, geometry, 40.0, sample, locationUncertainty = uncertainty,
            targetWordBounds = ScanpathRect(10.0, line * 50.0, 90.0, line * 50.0 + 50))
    }
    private fun Session.sample(line: Int, dt: Int = 50, reason: String? = null,
        uncertainty: ReadingLocationUncertainty? = null) {
        time += dt
        latest = context(time, line, reason, uncertainty)
        policy.onObservation(null, latest!!)
    }
    private fun Session.hold(line: Int, ms: Int = 600, dt: Int = 50) {
        repeat(ms / dt) { sample(line, dt) }
    }
    private fun Session.action(type: ReadingAidType) = policy.activeActions().firstOrNull { it.aidType == type }

    @Test fun ordinaryReadingGetsOneLineWithoutScanpathEventAndHasLossGrace() {
        val s = Session(); s.hold(5, 1800)
        val guide = s.action(ReadingAidType.CURRENT_LINE)!!
        val e = guide.guideEvidence!!
        assertEquals(4, e.firstLine); assertEquals(6, e.lastLine)
        assertFalse(e.measuredUncertainty)
        // Internal uncertainty is retained; it is not painted as a three-row guide.
        assertEquals(ReadingGuideRange(500, 599, ReadingGuideUnit.LINE), guide.guideRange)
        assertEquals(.13, e.opacity, .0001)
        assertEquals("A1_SUPPORTED_LOCATION_GUIDE_V5", guide.ruleId)
        val last = s.latest!!
        s.policy.tick(last.copy(nowMs = s.time + 750))
        assertNotNull(s.action(ReadingAidType.CURRENT_LINE))
        assertEquals(.065, e.opacityAt(s.time + 900), .0001)
        s.policy.tick(last.copy(nowMs = s.time + 1050))
        assertNull(s.action(ReadingAidType.CURRENT_LINE))
    }

    @Test fun measuredUncertaintyEnablesOneRowAndPairwiseSeparateSearch() {
        val s = Session()
        val u = ReadingLocationUncertainty(2.0, 2.0, "synthetic_measured_fixture_not_phone_validation")
        listOf(5, 7, 6).forEach { line -> repeat(12) { s.sample(line, uncertainty = u) } }
        val guide = s.action(ReadingAidType.CURRENT_LINE)!!
        assertEquals(6, guide.guideEvidence!!.firstLine)
        assertEquals(6, guide.guideEvidence!!.lastLine)
        assertEquals(.19, guide.guideEvidence!!.opacity, .0001)
        assertEquals(3, guide.guideEvidence!!.searchEvidenceIds.size)
        assertEquals("A1_MEASURED_SEPARATE_SEARCH_V5", guide.ruleId)
    }

    @Test fun wordOfferNeedsFreshSupportedSecondAndDoesNotRepeatAfterExpiryOrLoss() {
        val s = Session(); s.hold(5, 1100)
        assertNull(s.action(ReadingAidType.WORD_DEFINITION))
        repeat(20) { s.policy.onObservation(null, s.latest!!) }
        assertNull(s.action(ReadingAidType.WORD_DEFINITION))
        s.hold(5, 200)
        val offer = s.action(ReadingAidType.WORD_DEFINITION)!!
        assertEquals("CONTEXT_WORD_LOOKUP_OFFER_V1", offer.aidFormId)
        assertEquals("mapping_only_unvalidated", offer.wordConfidence)
        assertTrue(offer.lastCaptureMs - offer.firstCaptureMs >= 1000.0)
        s.hold(5, 2000)
        assertNull(s.action(ReadingAidType.WORD_DEFINITION))
        repeat(15) { s.sample(5, reason = "no_face") }
        s.hold(5, 1600)
        assertNull(s.action(ReadingAidType.WORD_DEFINITION))
    }

    @Test fun oneCompleteBroadRereadActivatesDistinctRouteWithoutExactWordIndex() {
        val s = Session()
        listOf(4, 7, 4, 5).forEach { s.hold(it) }
        assertNull("The second forward reread step is still missing", s.action(ReadingAidType.FOCUS_REGION))
        s.hold(6)
        val focus = s.action(ReadingAidType.FOCUS_REGION)!!
        assertEquals("A4_BROAD_REREADING_V5", focus.ruleId)
        assertEquals(listOf(4, 7, 4, 5, 6), focus.focusEvidence!!.reread!!.visits.map { it.line })
        assertEquals(.5, focus.strength, .0001)
        assertEquals(1, focus.focusEvidence!!.rereads.size)
        assertEquals(1, focus.focusEvidence!!.requiredRereadSequences)
        assertEquals(15000.0, focus.focusEvidence!!.rereadWindowMs, 0.0)
        assertTrue(focus.focusEvidence!!.reread!!.firstLine <= 4)
        assertTrue(focus.focusEvidence!!.reread!!.lastLine >= 7)
    }

    @Test fun ordinaryForwardPassageWithBroadReturnCanActivateFocus() {
        val s = Session()
        val route = listOf(4, 5, 6, 7, 4, 5, 6)
        route.dropLast(1).forEach { s.hold(it) }
        assertNull(s.action(ReadingAidType.FOCUS_REGION))
        s.hold(route.last())
        val focus = s.action(ReadingAidType.FOCUS_REGION)!!
        assertEquals("A4_BROAD_REREADING_V5", focus.ruleId)
        assertEquals(1, focus.focusEvidence!!.rereads.size)
        assertTrue(focus.focusEvidence!!.rereads.all { r -> r.visits.map { it.line } == route })
        assertEquals(4, focus.focusEvidence!!.reread!!.firstLine)
        assertEquals(7, focus.focusEvidence!!.reread!!.lastLine)
    }

    @Test fun selectedSampleBroadPrefixWorksAtFiveFifteenAndThirtyHz() {
        for (hz in listOf(5, 15, 30)) {
            val s = Session()
            for (row in listOf(4, 5, 6, 7, 4, 5, 6)) {
                repeat(hz) {
                    s.time += 1000.0 / hz
                    s.latest = context(s.time, row)
                    s.policy.onObservation(null, s.latest!!)
                }
            }
            val focus = s.action(ReadingAidType.FOCUS_REGION)
            assertNotNull("Sampling rate $hz Hz", focus)
            assertEquals(.5, focus!!.strength, .0001)
            assertEquals(1, focus.focusEvidence!!.rereads.size)
            assertEquals(listOf(4, 5, 6, 7, 4, 5, 6),
                focus.focusEvidence!!.reread!!.visits.map { it.line })
        }
    }

    @Test fun unknownAdjacentAlternationsStayOffWhileBroadAnchorsActivate() {
        for (other in listOf(5, 6, 7)) {
            val s = Session()
            listOf(4, other, 4, other).forEach { s.hold(it) }
            if (other < 7) assertNull(s.action(ReadingAidType.FOCUS_REGION))
            else {
                val focus = s.action(ReadingAidType.FOCUS_REGION)!!
                assertEquals("A4_FONT_SCALED_LINE_VISITS_V3", focus.ruleId)
                assertEquals(.5, focus.strength, .0001)
            }
        }
    }

    @Test fun slowPhoneInputRetainsInterruptionRecoveryAndRepeatedRevisitRoutes() {
        val interrupted = Session(); interrupted.hold(4, 800, 200)
        repeat(3) {
            repeat(4) { interrupted.sample(4, 200, "no_face") }
            interrupted.hold(4, 800, 200)
        }
        assertEquals("A4_RETURN_PRESENCE_FOCUS_V2", interrupted.action(ReadingAidType.FOCUS_REGION)!!.ruleId)
        repeat(3) { additional ->
            repeat(4) { interrupted.sample(4, 200, "no_face") }
            interrupted.hold(4, 800, 200)
            val e = interrupted.action(ReadingAidType.FOCUS_REGION)!!.focusEvidence!!
            assertEquals(if (additional < 2) .5 else 1.0, e.initialStrength, 0.0)
            assertEquals(if (additional < 2) 1 else 2, e.qualifiedEpisodes.size)
        }
        val revisits = Session()
        listOf(4, 7, 4, 7).forEach { revisits.hold(it, 800, 200) }
        assertEquals("A4_FONT_SCALED_LINE_VISITS_V3", revisits.action(ReadingAidType.FOCUS_REGION)!!.ruleId)
        assertNull("Within-article revisits are not departures", revisits.action(ReadingAidType.READING_BOOKMARK))
    }

    @Test fun allV5FocusStylesEaseInWithoutRefreshRestart() {
        val onset = ReadingFocusOnset().apply { animateAllStyles = true }
        onset.select(ReadingFocusStyle.FADE_TEXT)
        assertEquals(0.0, onset.fraction(1000.0), 0.0)
        assertEquals(.5, onset.fraction(1200.0), .0001)
        assertFalse(onset.select(ReadingFocusStyle.FADE_TEXT))
        assertEquals(1.0, onset.fraction(1400.0), 0.0)
    }

    @Test fun guideEntryAndExitHaveDefiniteOpacityAndDoNotCreditMissingTime() {
        val e = ReadingAidGuideEvidence(4, 6, "unknown", false, 1000.0, 1000.0, .08)
        assertEquals(0.0, e.opacityAt(1000.0), 0.0)
        assertEquals(.04, e.opacityAt(1075.0), .0001)
        assertEquals(.08, e.opacityAt(1750.0), .0001)
        assertEquals(.04, e.opacityAt(1900.0), .0001)
        assertEquals(0.0, e.opacityAt(2050.0), 0.0)
    }

    @Test fun translatedRereadPassageRemainsClearIncludingDuringPinnedScrollHold() {
        val r = ReadingAidRereadEvidence("r", emptyList(), 4, 7, 1000.0)
        val pinned = ReadingFocusMotion.Band(250.0, 500.0)
        val current = ReadingFocusContext.protect(pinned, geometry, r, 0.0, 700.0)
        assertEquals(150.0, current.top, 0.0)
        assertEquals(500.0, current.bottom, 0.0)
        val shifted = ReadingFocusContext.protect(pinned, geometry.copy(textOriginY = -100.0), r, 0.0, 700.0)
        assertEquals(50.0, shifted.top, 0.0)
        assertEquals(500.0, shifted.bottom, 0.0)
        assertEquals(pinned, ReadingFocusContext.protect(pinned, geometry, null, 0.0, 700.0))
    }

    @Test fun smoothedSelectedPathDrivesBookmarkEvenWhenUnselectedRawCoordinatesAreOutside() {
        val s = Session()
        fun selected(line: Int) {
            s.time += 200
            val c = context(s.time, line)
            s.latest = c.copy(rawSample = c.rawSample!!.copy(coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED,
                rawX = -1000.0, rawY = -1000.0, displayX = 40.0, displayY = line * 50.0 + 25))
            s.policy.onObservation(null, s.latest!!)
        }
        repeat(4) { selected(4) }; repeat(4) { selected(7) }
        assertNull(s.action(ReadingAidType.READING_BOOKMARK))
        repeat(4) {
            s.time += 200
            val c = context(s.time, 7, "no_face")
            s.latest = c.copy(rawSample = c.rawSample!!.copy(coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED))
            s.policy.onObservation(null, s.latest!!)
        }
        assertNotNull(s.action(ReadingAidType.READING_BOOKMARK))
        assertEquals(7, s.action(ReadingAidType.READING_BOOKMARK)!!.anchor.line)
        repeat(12) { selected(7) }
        assertNull(s.action(ReadingAidType.READING_BOOKMARK))
    }

    @Test fun lineWobbleAndNonAdjacentJumpsNeverTriggerDepartureBookmark() {
        val s = Session()
        listOf(4, 5, 4, 6, 4, 8, 4).forEach { s.hold(it, 700) }
        assertNull(s.action(ReadingAidType.READING_BOOKMARK))
    }

    @Test fun outsideDepartureNeeds500msAndFreezesTheLastSupportedLine() {
        val s = Session(); s.hold(4, 800)
        fun outside() {
            s.time += 50
            val c = context(s.time, 4)
            s.latest = c.copy(validLiveInput = false, targetLine = null, targetWordStartUtf16 = null,
                targetWordEndUtf16 = null, targetX = 450.0, targetWordBounds = null,
                rawSample = c.rawSample!!.copy(rawX = 450.0, line = null, wordStartUtf16 = null,
                    wordEndUtf16 = null, eligible = false, exclusion = "outside_visible_text"))
            s.policy.onObservation(null, s.latest!!)
        }
        repeat(6) { outside() }
        assertNull("A 250ms outside run must not trigger", s.action(ReadingAidType.READING_BOOKMARK))
        repeat(5) { outside() }
        val bookmark = s.action(ReadingAidType.READING_BOOKMARK)!!
        assertEquals(4, bookmark.anchor.line)
        assertEquals("sustained_raw_outside_article", bookmark.bookmarkEvidence!!.departureReason)
        assertTrue(bookmark.bookmarkEvidence!!.departureLastCaptureMs!! -
            bookmark.bookmarkEvidence!!.departureFirstCaptureMs!! >= 500.0)
        assertNull(bookmark.guideRange)
    }

    @Test fun ambiguousDifferentRowWithholdsPreciseBookmarkInsteadOfReusingWrongOldLine() {
        val s = Session(); s.hold(4, 800)
        repeat(12) {
            s.time += 50
            val c = context(s.time, 7)
            s.latest = c.copy(validLiveInput = false, targetLine = null, targetWordStartUtf16 = null,
                targetWordEndUtf16 = null, targetWordBounds = null,
                rawSample = c.rawSample!!.copy(line = null, wordStartUtf16 = null, wordEndUtf16 = null,
                    eligible = false, exclusion = "unassigned_line"))
            s.policy.onObservation(null, s.latest!!)
        }
        repeat(12) { s.sample(7, reason = "no_face") }
        assertNull(s.action(ReadingAidType.READING_BOOKMARK))
    }

    @Test fun rejectedMapperObservationsCannotPayDownBookmarkReturnTime() {
        val s = Session(); s.hold(4, 800)
        repeat(12) { s.sample(4, reason = "no_face") }
        assertNotNull(s.action(ReadingAidType.READING_BOOKMARK))
        repeat(60) { i ->
            s.time += 50
            val c = context(s.time, 4)
            s.latest = if (i % 2 == 0) c else c.copy(validLiveInput = false,
                rawSample = c.rawSample!!.copy(eligible = false, exclusion = "unassigned_line"))
            s.policy.onObservation(null, s.latest!!)
        }
        assertNotNull("Alternating rejected samples cannot count as 1s accepted return", s.action(ReadingAidType.READING_BOOKMARK))
        s.hold(4, 1200)
        assertNull(s.action(ReadingAidType.READING_BOOKMARK))
    }

    @Test fun guideOpacityHonoursReaderCapAndGeometryChangeInvalidatesAllAnchors() {
        val s = Session(ReadingAidPolicy(ReadingAidPolicyConfig.evidenceAlignedPrototype(),
            ReaderAidPreferences(maxStrength = .5)))
        s.hold(4, 1800)
        assertEquals(.095, s.action(ReadingAidType.CURRENT_LINE)!!.guideEvidence!!.opacity, .0001)
        val c = s.latest!!
        s.policy.tick(c.copy(geometrySnapshotId = "changed", geometry = geometry.copy(snapshotId = "changed")))
        assertTrue(s.policy.activeActions().isEmpty())
    }
}
