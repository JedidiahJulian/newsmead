package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Selected samples -> actual AUTO policy. Synthetic contracts do not establish human validity. */
class ReadingBookmarkHighlightContractTest {
    private val geometry = ScanpathGeometry("g", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 400.0, 700.0), 50.0,
        (0..13).map { ScanpathLine(it * 100, it * 100 + 99, 0.0, 400.0,
            it * 50.0, it * 50.0 + 50.0) }, ScanpathTextDirection.LTR)

    private inner class Replay(val name: String, val latency: Double = 0.0) {
        var capture = 0.0
        var latest: ReadingAidContext? = null
        var audit: Map<String, Any?> = emptyMap()
        var first: ReadingAidAction? = null
        var departureDelivery: Double? = null
        val policy = ReadingAidPolicy(ReadingAidPolicyConfig.evidenceAlignedPrototype(),
            onAudit = { kind, _, value -> if (kind == "support_revision") audit = value })

        fun send(c: ReadingAidContext) {
            latest = c
            policy.onObservation(null, c)
            if (first == null) first = bookmark()
        }

        fun sample(row: Int = 4, reason: String? = null, mappedRow: Int? = row,
                   dt: Int = 50, snapshot: String = "g") {
            capture += dt
            val delivery = capture + latency
            val missing = reason in setOf("no_face", "blink", "eye_state_unavailable")
            val outside = reason == "outside_visible_text"
            val eligible = reason == null
            val x = if (missing) null else if (outside) 450.0 else 40.0
            val y = if (missing) null else row * 50.0 + 25.0
            val sample = ScanpathSample(capture, delivery, x, y,
                if (eligible) mappedRow else null,
                if (eligible) mappedRow?.times(100) else null,
                if (eligible) mappedRow?.times(100)?.plus(8) else null,
                snapshot, eligible, reason)
            send(ReadingAidContext(1, snapshot, 14, if (eligible) mappedRow else null,
                sample.wordStartUtf16, sample.wordEndUtf16, delivery, capture, delivery,
                eligible, geometry.copy(snapshotId = snapshot), x, sample))
        }

        fun hold(row: Int, ms: Int = 600, reason: String? = null, mappedRow: Int? = row) {
            require(ms % 50 == 0)
            repeat(ms / 50) { sample(row, reason, mappedRow) }
        }

        fun away(ms: Int, reason: String = "no_face") {
            if (departureDelivery == null) departureDelivery = capture + 50.0 + latency
            hold(4, ms, reason)
        }

        fun duplicate(count: Int = 20) {
            val c = latest!!
            repeat(count) { send(c) }
        }

        fun bookmark() = policy.activeActions().firstOrNull { it.aidType == ReadingAidType.READING_BOOKMARK }
        fun guide() = policy.activeActions().firstOrNull { it.aidType == ReadingAidType.CURRENT_LINE }
        fun state() = audit["bookmark"] as? Map<*, *>
        fun report(): ReadingAidAction? {
            val action = bookmark()
            val onset = first?.onsetMs
            println("BOOKMARK_CONTRACT case=$name trigger=${first?.bookmarkEvidence?.departureReason ?: "none"}" +
                " source=${first?.anchor?.line} onset_ms=$onset" +
                " departure_latency_ms=${onset?.let { o -> departureDelivery?.let { o - it } }}" +
                " pipeline_latency_ms=$latency current_source=${action?.anchor?.line}")
            return action
        }
    }

    @Test fun stabilizedHighlightOverridesMapperDisagreementAndExtractionOnlyExclusions() {
        for (reason in listOf(null, "unassigned_line", "unassigned_word", "ambiguous_line", "ambiguous_word")) {
            val s = Replay("highlight_${reason ?: "mapper_mismatch"}", 300.0)
            s.hold(7, 2000, reason, mappedRow = if (reason == null) 4 else null)
            assertEquals("Fixture must establish the visible highlight", 7, s.guide()?.anchor?.line)
            s.away(600)
            val cue = s.report()
            assertNotNull("Stabilized highlight must supply the bookmark source: $reason", cue)
            assertEquals(7, cue!!.anchor.line)
            assertEquals("sustained_gaze_unavailable", cue.bookmarkEvidence!!.departureReason)
        }
    }

    @Test fun captureTimeArmsAcrossNormalVisitNullTransitionsWithPipelineLatency() {
        val s = Replay("normal_transition_300ms", 300.0)
        listOf(4, 5, 6, 7, 8, 9).forEach { s.hold(it, 300) }
        assertEquals(9, s.guide()?.anchor?.line)
        s.away(500)
        assertNull("Only 450ms from first away capture", s.bookmark())
        s.away(50)
        val cue = s.report()
        assertNotNull(cue)
        assertEquals(9, cue!!.anchor.line)
        assertEquals(500.0, cue.onsetMs - s.departureDelivery!!, .001)
    }

    @Test fun repeatedUiCallbacksAndBlinkNeverPayPartialArmingDebt() {
        val s = Replay("partial_arm_blink_duplicates", 300.0)
        s.hold(4, 1100)
        s.duplicate(100)
        assertNull(s.bookmark())
        s.hold(4, 200, "blink")
        s.hold(4, 350)
        assertEquals(1350.0, s.state()!!["anchor_credit_ms"] as Double, .001)
        assertEquals(false, s.state()!!["anchor_armed"])
        s.hold(4, 200)
        assertEquals(true, s.state()!!["anchor_armed"])
        s.away(550)
        assertNotNull(s.report())
    }

    @Test fun shortUncertaintyPausesArmingWithoutCreditingUnknownInterval() {
        val s = Replay("partial_arm_uncertainty")
        s.hold(4, 1100)
        // This is valid selected input, but its new row has no stabilized visit yet.
        s.hold(5, 100)
        s.hold(5, 300)
        assertNull(s.bookmark())
        assertEquals(false, s.state()!!["anchor_armed"])
        s.hold(5, 300)
        s.away(550)
        assertNotNull(s.report())
    }

    @Test fun armedAdjacentForwardStepAllowsSettledThreeRowBackreading() {
        val s = Replay("adjacent_forward_backread", 300.0)
        s.hold(4, 1800)
        listOf(5, 6, 7).forEach { s.hold(it, 400) }
        s.hold(4, 150)
        assertNull("An unsettled return cannot trigger", s.bookmark())
        s.hold(4, 300)
        val cue = s.report()
        assertNotNull(cue)
        assertEquals(7, cue!!.anchor.line)
        assertEquals("supported_backward_reading", cue.bookmarkEvidence!!.departureReason)
        assertEquals(4, cue.bookmarkEvidence!!.departureSupport!!.line)
    }

    @Test fun largerForwardJumpRequiresAdjacentContinuationBeforeFrontierPromotion() {
        val unconfirmed = Replay("large_jump_without_continuation")
        unconfirmed.hold(4, 1800)
        listOf(5, 9, 6).forEach { unconfirmed.hold(it, 600) }
        assertNull(unconfirmed.report())
        val confirmed = Replay("large_jump_with_continuation")
        confirmed.hold(4, 1800)
        listOf(5, 9, 10, 6).forEach { confirmed.hold(it, 600) }
        val cue = confirmed.report()
        assertNotNull(cue)
        assertEquals(10, cue!!.anchor.line)
        assertEquals("supported_backward_reading", cue.bookmarkEvidence!!.departureReason)
    }

    @Test fun wobbleAndPureBlinksNeverCreateDeparture() {
        val wobble = Replay("wobble")
        listOf(4, 5, 4, 6, 4, 8, 4).forEach { wobble.hold(it, 700) }
        assertNull(wobble.report())
        val blink = Replay("pure_blinks")
        blink.hold(4, 1800)
        blink.away(1500, "blink")
        assertNull(blink.report())
    }

    @Test fun outsideAndNoFaceAccumulateOnlyAwayEvidenceAcrossShortBlink() {
        for (blinkMs in listOf(200, 350)) {
        val s = Replay("outside_blink_${blinkMs}_no_face", 300.0)
        s.hold(4, 1800)
        s.away(300, "outside_visible_text")
        s.away(blinkMs, "blink")
        s.away(200)
        assertNull("Blink duration cannot satisfy the 500ms departure threshold", s.bookmark())
        s.away(150)
        val cue = s.report()
        assertNotNull(cue)
        assertEquals(4, cue!!.anchor.line)
        assertTrue("Blink pauses departure credit", cue.onsetMs - s.departureDelivery!! >= 500.0 + blinkMs)
        }
    }

    @Test fun confirmedReadingResetsSeparatedShortLookaways() {
        val s = Replay("separated_short_lookaways")
        s.hold(4, 1800)
        repeat(3) {
            s.away(300, if (it % 2 == 0) "outside_visible_text" else "no_face")
            assertNull(s.bookmark())
            s.hold(4, 400)
        }
        assertNull(s.report())
        s.departureDelivery = null
        s.away(550)
        assertNotNull(s.report())
    }

    @Test fun geometryAndInvalidTimestampBoundariesCannotReuseArmedSource() {
        for (boundary in listOf("geometry", "capture_regression", "pipeline_age")) {
            val s = Replay("boundary_$boundary")
            s.hold(4, 1800)
            val c = s.latest!!
            when (boundary) {
                "geometry" -> s.sample(snapshot = "changed")
                "capture_regression" -> s.send(c.copy(captureMs = 100.0,
                    rawSample = c.rawSample!!.copy(captureMs = 100.0)))
                else -> s.send(c.copy(nowMs = c.nowMs + 700.0,
                    evidenceAvailableMs = c.nowMs + 700.0,
                    rawSample = c.rawSample!!.copy(deliveryMs = c.nowMs + 700.0)))
            }
            // Keep the next input monotone in delivery; no new supported reading can re-arm.
            if (boundary == "pipeline_age") s.capture += 700.0
            if (boundary == "geometry") {
                repeat(12) { s.sample(reason = "no_face", snapshot = "changed") }
            } else s.away(600)
            assertNull(s.report())
        }
    }

    @Test fun briefGeometrySettlingPausesPartialArmingWithZeroCredit() {
        val s = Replay("brief_geometry_pause", 300.0)
        s.hold(4, 1100)
        s.hold(4, 200, "geometry_unsettled")
        s.hold(4, 350)
        assertEquals(1350.0, s.state()!!["anchor_credit_ms"] as Double, .001)
        assertEquals(false, s.state()!!["anchor_armed"])
        s.hold(4, 200)
        s.away(550)
        assertNotNull(s.report())
    }

    @Test fun unsettledCoordinatesPreserveLastConfirmedPlaceAfterGuideFades() {
        val s = Replay("sustained_unsettled_source")
        s.hold(4, 1800)
        repeat(24) { s.sample(if (it % 2 == 0) 6 else 7) }
        assertNull(s.guide())
        assertNull("Fresh unsettled gaze alone cannot trigger", s.bookmark())
        s.away(600)
        assertEquals("Use the remembered confirmed line", 4, s.report()?.anchor?.line)
    }

    @Test fun repeatedOutsideCaptureWithNewDeliveryCannotEarnDepartureTime() {
        val s = Replay("repeated_outside_capture")
        s.hold(4, 1800)
        s.sample(reason = "outside_visible_text")
        val first = s.latest!!
        for (delta in 50..500 step 50) {
            val delivery = first.nowMs + delta
            s.send(first.copy(nowMs = delivery, evidenceAvailableMs = delivery,
                rawSample = first.rawSample!!.copy(deliveryMs = delivery)))
        }
        assertEquals(0.0, s.state()!!["away_credit_ms"] as Double, .001)
        assertNull(s.report())
    }

    @Test fun noNewGazeAndOneShotStaleNotificationStillProduceBookmark() {
        val s = Replay("one_shot_watchdog", 300.0)
        s.hold(4, 1800)
        val last = s.latest!!
        // The real provider emits stale once at capture age650ms, then only UI pulses remain.
        for (age in 100..300 step 100) s.send(last.copy(nowMs = last.nowMs + age))
        val staleDelivery = last.captureMs!! + 650.0
        val stale = last.copy(nowMs = staleDelivery, validLiveInput = false,
            rawSample = last.rawSample!!.copy(deliveryMs = staleDelivery, eligible = false,
                exclusion = "stale", rawX = null, rawY = null, displayX = null, displayY = null))
        s.send(stale)
        for (age in 400..700 step 100) {
            s.send(stale.copy(nowMs = last.nowMs + age))
            assertNull("Short absence cannot trigger", s.bookmark())
        }
        s.send(stale.copy(nowMs = last.nowMs + 800))
        val cue = s.report()
        assertNotNull("No new gaze must let the unavailable timer finish", cue)
        assertEquals(4, cue!!.anchor.line)
        assertEquals("sustained_gaze_unavailable", cue.bookmarkEvidence!!.departureReason)
        assertNull(cue.bookmarkEvidence!!.departureFirstDeliveryMs)
        assertEquals(last.nowMs + 250, cue.bookmarkEvidence!!.departureFirstElapsedMs!!, .001)
        assertEquals(550.0, cue.bookmarkEvidence!!.departureLastElapsedMs!! -
            cue.bookmarkEvidence!!.departureFirstElapsedMs!!, .001)
    }

    @Test fun silentCoordinateStreamHandsOffFromHighlightToBookmark() {
        val s = Replay("coordinate_stream_silence", 300.0)
        s.hold(4, 1800)
        val last = s.latest!!
        for (age in 100..1400 step 100) s.send(last.copy(nowMs = last.nowMs + age))
        assertNull("Highlight has finished its grace and exit", s.guide())
        assertEquals("The saved place survives the vanished highlight", 4, s.report()?.anchor?.line)
    }
}

