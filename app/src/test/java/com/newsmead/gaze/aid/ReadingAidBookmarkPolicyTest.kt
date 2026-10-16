package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

class ReadingAidBookmarkPolicyTest {
    @Test fun startupTimerDoesNotBlockDelayedFirstVisitFromSavingBookmark() {
        val p = delayedStartupAndLoss(100.0)
        val cue = marker(p)!!
        assertEquals(3, cue.anchor.line)
        assertEquals(980.0, cue.bookmarkEvidence!!.source.firstCaptureMs, 0.0)
        assertEquals("sustained_gaze_unavailable", cue.bookmarkEvidence!!.departureReason)
    }

    @Test fun explicitBoundaryStillRejectsAVisitStartingBeforeThatBoundary() {
        assertNull(marker(delayedStartupAndLoss(1000.0)))
    }

    private fun delayedStartupAndLoss(boundaryMs: Double): ReadingAidPolicy {
        val p = policy(ReaderAidPreferences(lineGuide = false, focusRegion = false))
        val noInput = context(boundaryMs).copy(nowMs = boundaryMs, captureMs = null,
            evidenceAvailableMs = null, validLiveInput = false, rawSample = null)
        p.reset(noInput)
        p.tick(noInput.copy(nowMs = 1000.0))
        // Arrival1180 carries a first supported capture980 preceding the startup tick.
        p.onObservation(candidate(1100.0, start = 980.0).copy(evidenceAvailableMs = 1180.0),
            context(1100.0).copy(nowMs = 1180.0, evidenceAvailableMs = 1180.0))
        for (capture in 1200..1800 step 100) {
            val t = capture.toDouble()
            p.onObservation(null, context(t, null).copy(nowMs = t + 80,
                evidenceAvailableMs = t + 80, validLiveInput = false,
                rawSample = ScanpathSample(t, t + 80, null, null, null, null, null,
                    "g", false, "no_face")))
        }
        return p
    }

    @Test fun smoothedDepartureChecksSelectedPointWithoutRawFallback() {
        fun departure(p: ReadingAidPolicy, t: Double, rawX: Double, displayX: Double?) =
            p.onObservation(null, context(t, null).copy(validLiveInput = false,
                rawSample = ScanpathSample(t, t + 10, rawX, 100.0, null, null, null,
                    "g", false, "outside_visible_text", displayX, 100.0,
                    ScanpathCoordinateStream.ARTICLE_SMOOTHED)))
        val p = policy(); observe(p, 110.0)
        for (t in listOf(200.0, 250.0, 300.0)) departure(p, t, 350.0, 150.0)
        assertNull(marker(p))
        for (t in listOf(400.0, 450.0, 500.0)) departure(p, t, 350.0, null)
        assertNull(marker(p))
        for (t in listOf(600.0, 650.0, 700.0)) departure(p, t, 150.0, 350.0)
        assertEquals(3, marker(p)?.anchor?.line)
        assertEquals("sustained_smoothed_outside_article", marker(p)!!.bookmarkEvidence!!.departureReason)
    }

    private val geometry = ScanpathGeometry("g", 100.0, 50.0,
        ScanpathRect(100.0, 50.0, 300.0, 250.0), 20.0,
        (0..7).map { ScanpathLine(it * 10, it * 10 + 9, 0.0, 200.0,
            it * 20.0, it * 20.0 + 20.0) }, ScanpathTextDirection.LTR)
    private fun policy(prefs: ReaderAidPreferences = ReaderAidPreferences(lineGuide = false)) =
        ReadingAidPolicy(ReadingAidPolicyConfig.definitionPrototype(), prefs)
    private fun context(t: Double, line: Int? = 3) = ReadingAidContext(1, "g", 8,
        line, null, null, t + 10, t, t + 10, geometry = geometry, targetX = 150.0)
    private fun candidate(t: Double, line: Int = 3, start: Double = t - 100, segment: Int = 1) =
        ScanpathCandidateEvidence("candidate:$segment:$start", segment, start, t, t - start, 3,
            null, null, line, 0.0, 1.0, "g", t + 10, "trigger_observation_delivery")
    private fun observe(p: ReadingAidPolicy, t: Double, line: Int = 3) =
        p.onObservation(candidate(t, line), context(t, line))
    private fun marker(p: ReadingAidPolicy) = p.activeActions().singleOrNull {
        it.aidType == ReadingAidType.READING_BOOKMARK }
    private fun outside(p: ReadingAidPolicy, t: Double, exclusion: String = "outside_visible_text",
                        x: Double? = 350.0, allowed: Boolean = true) =
        p.onObservation(null, context(t, null).copy(validLiveInput = false,
            bookmarkInputAllowed = allowed,
            rawSample = ScanpathSample(t, t + 10, x, 100.0, null, null, null, "g", false, exclusion)))
    private fun armOutside(p: ReadingAidPolicy) {
        observe(p, 110.0)
        outside(p, 200.0); outside(p, 250.0); outside(p, 300.0)
    }

    @Test fun sustainedOutsideMarksSavedSourceWithCausalTimesAndFixedVisibility() {
        val p = policy(ReaderAidPreferences(lineGuide = false, maxStrength = 0.1,
            adaptiveStrength = false, manualStrength = 0.2))
        armOutside(p)
        val a = marker(p)!!
        assertEquals(3, a.anchor.line)
        assertEquals(1.0, a.strength, 0.0)
        assertEquals(Double.MAX_VALUE, a.expiresMs, 0.0)
        assertEquals("LAST_READ_LINE_BOOKMARK_V1", a.aidFormId)
        assertEquals("A2_LAST_READ_LINE_BOOKMARK_V2", a.ruleId)
        assertEquals(listOf("EB15", "CW12"), a.sourceIds)
        assertEquals(10.0, a.firstCaptureMs, 0.0)
        assertEquals(300.0, a.lastCaptureMs, 0.0)
        assertEquals(310.0, a.onsetMs, 0.0)
        assertEquals(310.0, a.evidenceAvailableMs, 0.0)
        assertEquals(110.0, a.bookmarkEvidence!!.source.lastCaptureMs, 0.0)
        assertEquals(200.0, a.bookmarkEvidence!!.departureFirstCaptureMs!!, 0.0)
        assertEquals(3, a.bookmarkEvidence!!.departureSampleCount)
        assertEquals("sustained_raw_outside_article", a.bookmarkEvidence!!.departureReason)
        p.tick(context(9000.0, null).copy(validLiveInput = false))
        assertEquals(a.id, marker(p)?.id)
    }

    @Test fun shortOutsideDuplicateInputAndTrackingLossDoNotTrigger() {
        val p = policy()
        observe(p, 110.0)
        outside(p, 200.0); outside(p, 220.0); outside(p, 240.0)
        assertNull(marker(p))
        repeat(5) { outside(p, 240.0) }
        assertNull(marker(p))
        outside(p, 250.0, "no_face", null)
        assertNull(marker(p))
        outside(p, 400.0); outside(p, 450.0); outside(p, 500.0)
        assertEquals(3, marker(p)?.anchor?.line)
    }

    @Test fun outsideGapOrCoordinatesInsideArticleCannotConfirmDeparture() {
        val p = policy()
        observe(p, 110.0)
        outside(p, 200.0); outside(p, 250.0); outside(p, 500.0)
        assertNull(marker(p))
        outside(p, 550.0, x = 150.0); outside(p, 600.0, x = 150.0)
        assertNull(marker(p))
        p.onObservation(null, context(1000.0, null).copy(validLiveInput = false,
            rawSample = ScanpathSample(650.0, 660.0, 350.0, 100.0,
                null, null, null, "g", false, "outside_visible_text")))
        assertNull(marker(p))
    }

    @Test fun sameLineAndNormalNextLineUpdateMemoryWithoutCue() {
        val p = policy()
        observe(p, 110.0); observe(p, 230.0); observe(p, 350.0, 4)
        assertNull(marker(p))
        outside(p, 400.0); outside(p, 450.0); outside(p, 500.0)
        assertEquals(4, marker(p)?.anchor?.line)
    }

    @Test fun backwardForwardAndLastLineDeparturesMarkSourceWithoutPredictingNext() {
        for ((source, dest) in listOf(3 to 0, 3 to 7, 7 to 0)) {
            val p = policy()
            observe(p, 110.0, source); observe(p, 230.0, dest)
            val a = marker(p)!!
            assertEquals(source, a.anchor.line)
            assertEquals(dest, a.bookmarkEvidence!!.departureSupport!!.line)
            assertEquals("supported_non_normal_line_jump", a.bookmarkEvidence!!.departureReason)
        }
    }

    @Test fun sourceReturnClearsAndIntentionalContinuedReadingElsewhereClears() {
        val returned = policy(); armOutside(returned)
        val decisions = observe(returned, 500.0)
        assertNull(marker(returned))
        assertTrue(decisions.any { it.reason == "saved_line_return" })
        val elsewhere = policy()
        observe(elsewhere, 110.0); observe(elsewhere, 230.0, 0)
        val a = marker(elsewhere)!!
        elsewhere.onObservation(candidate(280.0, 0, 130.0), context(280.0, 0))
        assertEquals(a.id, marker(elsewhere)?.id)
        assertTrue(observe(elsewhere, 420.0, 0).any { it.reason == "continued_reading_elsewhere" })
        assertNull(marker(elsewhere))
    }

    @Test fun outsideCueAllowsSearchLandingThenContinuedReadingClears() {
        val p = policy(); armOutside(p)
        observe(p, 500.0, 0)
        assertNotNull(marker(p))
        observe(p, 650.0, 0)
        assertNull(marker(p))
    }

    @Test fun rawWobbleStaleEvidenceAndDepartedFinalizationCannotReplaceMemory() {
        val p = policy(); observe(p, 110.0)
        p.onObservation(candidate(230.0, 3), context(230.0, 0))
        p.onObservation(candidate(110.0, 7), context(400.0, 7))
        val f = ScanpathFixation(0, 1, 410.0, 510.0, 100.0, 3, 150.0, 60.0,
            0.0, null, null, 0.0, 0, 1.0, false, false, "g", 520.0,
            "trigger_observation_delivery")
        p.onEvent(ScanpathEvent("departed", "P01", "line", listOf(0),
            410.0, 510.0, 520.0, "trigger_observation_delivery"), f, context(510.0, 3))
        outside(p, 550.0); outside(p, 600.0); outside(p, 650.0)
        assertEquals(3, marker(p)?.anchor?.line)
    }

    @Test fun censoredFinalizationsCannotCreateSavedLocation() {
        for ((left, right) in listOf(true to false, false to true)) {
            val p = policy()
            val f = ScanpathFixation(0, 1, 10.0, 110.0, 100.0, 3, 150.0, 120.0,
                0.0, null, null, 0.0, 3, 1.0, left, right, "g", 120.0,
                "trigger_observation_delivery")
            p.onEvent(ScanpathEvent("censored", "P01", "line", listOf(0),
                10.0, 110.0, 120.0, "trigger_observation_delivery"), f, context(110.0))
            outside(p, 200.0); outside(p, 250.0); outside(p, 300.0)
            assertNull(marker(p))
        }
    }

    @Test fun freshUncensoredFinalizationCanSaveLineAndPrefixRelabelCannotJump() {
        val p = policy()
        val f = ScanpathFixation(0, 1, 10.0, 110.0, 100.0, 3, 150.0, 120.0,
            0.0, null, null, 0.0, 3, 1.0, false, false, "g", 120.0,
            "trigger_observation_delivery")
        p.onEvent(ScanpathEvent("supported", "P01", "line", listOf(0),
            10.0, 110.0, 120.0, "trigger_observation_delivery"), f, context(110.0))
        p.onObservation(candidate(150.0, 0, 10.0), context(150.0, 0))
        assertNull(marker(p))
        outside(p, 200.0); outside(p, 250.0); outside(p, 300.0)
        assertEquals(3, marker(p)?.anchor?.line)
    }

    @Test fun detectorSettingsDetermineOutsideSupportRatherThanAidTtl() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.definitionPrototype(),
            ReaderAidPreferences(lineGuide = false), ScanpathConfig(minFixationMs = 200.0,
                minFixationSamples = 4, maxSampleGapMs = 80.0))
        observe(p, 110.0)
        outside(p, 200.0); outside(p, 260.0); outside(p, 320.0); outside(p, 380.0)
        assertNull(marker(p))
        outside(p, 440.0)
        assertEquals(3, marker(p)?.anchor?.line)
    }

    @Test fun ordinaryBreakRetainsBookmarkButGeometryConsultationAndResetInvalidate() {
        val p = policy(); armOutside(p)
        val a = marker(p)!!
        p.onEvent(ScanpathEvent("break", "P13", "line", emptyList(), 350.0, 350.0,
            360.0, "trigger_observation_delivery", mapOf("reason" to "invalid_sample")),
            null, context(350.0, null).copy(validLiveInput = false))
        assertEquals(a.id, marker(p)?.id)
        for (boundary in 0..3) {
            val other = policy(); armOutside(other)
            when (boundary) {
                0 -> other.tick(context(400.0).copy(generation = 2))
                1 -> other.tick(context(400.0).copy(geometry = geometry.copy(textDirection = ScanpathTextDirection.RTL)))
                2 -> outside(other, 400.0, "definition_consultation", allowed = false)
                3 -> other.reset(context(400.0))
            }
            assertNull(marker(other))
            outside(other, 500.0); outside(other, 550.0); outside(other, 600.0)
            assertNull(marker(other))
        }
    }

    @Test fun offZeroAndOptOutClearMemoryWhilePositiveStrengthPreservesStaticBookmark() {
        val p = policy(); armOutside(p)
        val id = marker(p)!!.id
        p.updatePreferences(p.preferences.copy(maxStrength = 0.1), context(400.0, null))
        assertEquals(id, marker(p)?.id)
        for (prefs in listOf(ReaderAidPreferences(enabled = false), ReaderAidPreferences(lineStartCue = false),
            ReaderAidPreferences(maxStrength = 0.0), ReaderAidPreferences(adaptiveStrength = false, manualStrength = 0.0))) {
            val other = policy(); armOutside(other)
            other.updatePreferences(prefs, context(400.0, null))
            assertNull(marker(other))
            other.updatePreferences(ReaderAidPreferences(), context(450.0))
            outside(other, 500.0); outside(other, 550.0); outside(other, 600.0)
            assertNull(marker(other))
        }
    }

    private fun trackingBreak(p: ReadingAidPolicy, reason: String, t: Double) {
        p.onEvent(ScanpathEvent("break:$reason", "P13", "line", emptyList(), t, t,
            t + 10, "trigger_observation_delivery", mapOf("reason" to reason)),
            null, context(t, null).copy(validLiveInput = false))
    }

    @Test fun nonadjacentRecoveryReseedsAfterTrackingBreakWithoutInferringJump() {
        for (reason in listOf("invalid_sample", "sample_gap", "non_monotonic_capture")) {
            val p = policy(); observe(p, 110.0)
            trackingBreak(p, reason, 160.0)
            p.onObservation(candidate(300.0, 0, segment = 2), context(300.0, 0))
            assertNull(marker(p))
            p.onObservation(candidate(420.0, 6, segment = 2), context(420.0, 6))
            assertEquals(0, marker(p)?.anchor?.line)
        }
    }

    @Test fun heldBookmarkSurvivesFirstRecoveryElsewhereThenSupportedContinuationClears() {
        for (reason in listOf("invalid_sample", "sample_gap", "non_monotonic_capture")) {
            val p = policy(); observe(p, 110.0); observe(p, 230.0, 0)
            val id = marker(p)!!.id
            trackingBreak(p, reason, 260.0)
            assertEquals(id, marker(p)?.id)
            p.onObservation(candidate(450.0, 6, segment = 2), context(450.0, 6))
            assertEquals(id, marker(p)?.id)
            p.onObservation(candidate(600.0, 6, segment = 2), context(600.0, 6))
            assertNull(marker(p))
        }
    }

    @Test fun supportedSourceReturnStillClearsOnFirstRecoveryPrefix() {
        val p = policy(); observe(p, 110.0); observe(p, 230.0, 0)
        trackingBreak(p, "invalid_sample", 260.0)
        val decisions = p.onObservation(candidate(450.0, 3, segment = 2), context(450.0, 3))
        assertTrue(decisions.any { it.reason == "saved_line_return" })
        assertNull(marker(p))
    }

    @Test fun excessiveTransitionGapOrChangedSegmentReseedsWithoutJumpOrClear() {
        for (newSegment in listOf(1, 2)) {
            val p = policy(); observe(p, 110.0)
            p.onObservation(candidate(500.0, 0, segment = newSegment), context(500.0, 0))
            assertNull(marker(p))
            p.onObservation(candidate(620.0, 6, segment = newSegment), context(620.0, 6))
            val id = marker(p)!!.id
            p.onObservation(candidate(1000.0, 7, segment = newSegment), context(1000.0, 7))
            assertEquals(id, marker(p)?.id)
            p.onObservation(candidate(1120.0, 7, segment = newSegment), context(1120.0, 7))
            assertNull(marker(p))
        }
    }

    private fun unavailable(now: Double, delivery: Double = now, capture: Double? = delivery,
                            reason: String = "no_face",
                            stream: ScanpathCoordinateStream = ScanpathCoordinateStream.CALIBRATED_RAW) =
        context(now - 10, null).copy(nowMs = now, captureMs = capture,
            evidenceAvailableMs = delivery, validLiveInput = false,
            rawSample = ScanpathSample(capture, delivery, null, null, null, null, null,
                "g", false, reason, coordinateStream = stream))

    @Test fun lossConfirmsAt500DeliveryElapsedWithNullDepartureCaptureBounds() {
        for (stream in ScanpathCoordinateStream.values()) {
            val p = policy(); observe(p, 110.0)
            val loss = unavailable(200.0, capture = 110.0, reason = "stale", stream = stream)
            p.onObservation(null, loss)
            p.tick(loss.copy(nowMs = 699.0))
            assertNull(marker(p))
            p.tick(loss.copy(nowMs = 700.0))
            val a = marker(p)!!; val e = a.bookmarkEvidence!!
            assertEquals("sustained_gaze_unavailable", e.departureReason)
            assertNull(e.departureFirstCaptureMs); assertNull(e.departureLastCaptureMs)
            assertEquals(110.0, a.lastCaptureMs, 0.0)
            assertEquals(700.0, a.onsetMs, 0.0)
            assertEquals(700.0, a.evidenceAvailableMs, 0.0)
            assertEquals("delivery_elapsed_time", e.departureTimeBasis)
            assertEquals(200.0, e.departureFirstDeliveryMs!!, 0.0)
            assertEquals(200.0, e.departureLastDeliveryMs!!, 0.0)
            assertEquals(1, e.departureSampleCount)
        }
    }

    @Test fun pendingLossMaturesWithoutCallbacksAndInvalidSampleBreakDoesNotEraseIt() {
        val p = policy(); observe(p, 110.0)
        p.onObservation(null, unavailable(200.0))
        trackingBreak(p, "invalid_sample", 240.0)
        p.tick(unavailable(699.0).copy(rawSample = null))
        assertNull(marker(p))
        p.tick(unavailable(700.0).copy(rawSample = null))
        assertNotNull(marker(p))
    }

    @Test fun usableSelectedPointCancelsBeforeCandidateThenLossRearms() {
        for (stream in ScanpathCoordinateStream.values()) {
            val p = policy(); observe(p, 110.0)
            p.onObservation(null, unavailable(200.0, stream = stream))
            val point = ScanpathSample(690.0, 700.0, 150.0, 100.0, 3, null, null,
                "g", true, null, 150.0, 100.0, stream)
            p.onObservation(null, context(690.0).copy(rawSample = point))
            p.tick(context(750.0, null).copy(validLiveInput = false, rawSample = null))
            assertNull(marker(p))
            val next = unavailable(800.0, stream = stream)
            p.onObservation(null, next)
            p.tick(next.copy(nowMs = 1299.0)); assertNull(marker(p))
            p.tick(next.copy(nowMs = 1300.0)); assertNotNull(marker(p))
        }
    }

    @Test fun onlyExplicitProviderMissingReasonsCanArmAndNoSourceCannotArm() {
        for (reason in listOf("no_face", "invalid_crops", "eye_area", "stale",
            "invalid_prediction", "coordinate_unavailable")) {
            val p = policy(); observe(p, 110.0)
            val loss = unavailable(200.0, reason = reason)
            p.onObservation(null, loss); p.tick(loss.copy(nowMs = 700.0))
            assertNotNull(reason, marker(p))
            val empty = policy(); empty.onObservation(null, loss)
            empty.tick(loss.copy(nowMs = 700.0)); assertNull(marker(empty))
        }
        for (reason in listOf("sample_gap", "invalid_sample", "invalid_clock", "paused",
            "stopped", "definition_consultation", "controls_open", "geometry_unsettled")) {
            val p = policy(); observe(p, 110.0)
            val input = unavailable(200.0, reason = reason)
            p.onObservation(null, input); p.tick(input.copy(nowMs = 700.0))
            assertNull(reason, marker(p))
        }
    }

    @Test fun excludedAndInvalidClocksCannotArmOrCompletePendingLoss() {
        val base = unavailable(200.0)
        for (bad in listOf(base.copy(bookmarkInputAllowed = false),
            base.copy(rawSample = base.rawSample!!.copy(deliveryMs = 201.0)),
            base.copy(rawSample = base.rawSample!!.copy(deliveryMs = Double.NaN)),
            base.copy(rawSample = base.rawSample!!.copy(captureMs = 201.0)),
            base.copy(rawSample = base.rawSample!!.copy(captureMs = -1.0)),
            base.copy(rawSample = base.rawSample!!.copy(geometrySnapshotId = "other")),
            base.copy(nowMs = 500.0), base.copy(nowMs = Double.NaN))) {
            val p = policy(); observe(p, 110.0)
            p.onObservation(null, bad)
            p.tick(unavailable(900.0).copy(rawSample = null))
            assertNull(marker(p))
        }
        val p = policy(); observe(p, 110.0)
        p.onObservation(null, base)
        p.tick(base.copy(nowMs = 190.0))
        p.tick(unavailable(900.0).copy(rawSample = null)); assertNull(marker(p))
    }

    @Test fun heldLossCueReturnsOrContinuesElsewhereWithoutGapJump() {
        for (line in listOf(3, 0)) {
            val p = policy(); observe(p, 110.0)
            val loss = unavailable(200.0)
            p.onObservation(null, loss); p.tick(loss.copy(nowMs = 700.0))
            val id = marker(p)!!.id
            p.onObservation(candidate(800.0, line, segment = 2), context(800.0, line))
            if (line == 3) assertNull(marker(p)) else {
                assertEquals(id, marker(p)?.id)
                p.onObservation(candidate(950.0, line, segment = 2), context(950.0, line))
                assertNull(marker(p))
            }
        }
        val p = policy(); observe(p, 110.0)
        p.onObservation(null, unavailable(200.0))
        p.onObservation(candidate(400.0, 0, segment = 2), context(400.0, 0))
        assertNull(marker(p))
    }

    @Test fun lossSettingsRequireFinitePositiveDurationAndResetClearsPending() {
        for (ms in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            try {
                ReadingAidPolicyConfig.definitionPrototype().copy(bookmarkTrackingLossMs = ms)
                fail("accepted invalid tracking loss duration")
            } catch (_: IllegalArgumentException) { }
        }
        val p = policy(); observe(p, 110.0)
        p.onObservation(null, unavailable(200.0)); p.reset(context(300.0))
        p.tick(unavailable(900.0).copy(rawSample = null)); assertNull(marker(p))
    }


    @Test fun providerStaleRetainedRawDoesNotBecomeUsableAndDeliveryUpdatesKeepRun() {
        for (stream in ScanpathCoordinateStream.values()) {
            val p = policy(); observe(p, 110.0)
            val first = unavailable(200.0, capture = 110.0, reason = "stale", stream = stream)
                .let { it.copy(rawSample = it.rawSample!!.copy(rawX = 150.0, rawY = 100.0)) }
            p.onObservation(null, first)
            val later = first.copy(nowMs = 500.0,
                rawSample = first.rawSample!!.copy(deliveryMs = 500.0))
            p.onObservation(null, later)
            p.tick(later.copy(nowMs = 700.0))
            val e = marker(p)!!.bookmarkEvidence!!
            assertEquals(200.0, e.departureFirstDeliveryMs!!, 0.0)
            assertEquals(500.0, e.departureLastDeliveryMs!!, 0.0)
            assertEquals(2, e.departureSampleCount)
        }
    }

}
