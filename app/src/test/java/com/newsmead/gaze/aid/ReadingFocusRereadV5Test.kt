package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic controller contracts; these do not establish gaze accuracy or reader benefit. */
class ReadingFocusRereadV5Test {
    private val config = ReadingAidPolicyConfig.definitionPrototype().copy(evidenceAlignedV5 = true)
    private val geometry = ScanpathGeometry("g", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 100.0, 240.0), 20.0,
        (0..11).map { ScanpathLine(it * 10, it * 10 + 9, 0.0, 100.0, it * 20.0, it * 20.0 + 20.0) })

    private fun policy(v5: Boolean = true) = ReadingFocusPolicy(
        config.copy(evidenceAlignedV5 = v5), ScanpathConfig()) {}
    private fun context(t: Double, reason: String? = null, generation: Long = 1,
                        geometryId: String = "g") =
        ReadingAidContext(generation, geometryId, 12, 6, null, null, t, t, t,
            geometry = geometry.copy(snapshotId = geometryId),
            rawSample = ScanpathSample(t, t, 10.0, 130.0, 6, null, null,
                geometryId, reason == null, reason))
    private fun support(t: Double, first: Double = 1000.0, line: Int = 6, segment: Int = 1) =
        ReadingAidContinuationEvidence("s:$t:$line:$segment", segment, line, first, t, t)
    private fun witness(id: String = "r1", start: Double = 0.0): ReadingAidRereadEvidence {
        val visits = listOf(4, 7, 4, 5, 6).mapIndexed { i, line ->
            val end = start + (i + 1) * 200.0
            ReadingAidContinuationEvidence("$id:$i", 1, line, end - 150.0, end, end)
        }
        return ReadingAidRereadEvidence(id, visits, 4, 7, start + 1000.0)
    }
    private fun activate(p: ReadingFocusPolicy): ReadingAidFocusEvidence {
        p.support(support(1000.0), context(1000.0))
        return p.reread(witness(), context(1000.0))!!
    }
    private fun recover(p: ReadingFocusPolicy, end: Int) {
        for (t in 1100..end step 100) p.support(support(t.toDouble()), context(t.toDouble()))
    }

    @Test fun freshSequenceCanActivateAfterTheEarlierResponseFinishedFading() {
        val p = policy(); activate(p); recover(p, 11000)
        assertNull(p.currentEvidence(context(11000.0)))
        p.support(support(13000.0, first = 12000.0), context(13000.0))
        val e = p.reread(witness("r2", 12000.0), context(13000.0))!!
        assertEquals(0.0, e.observedRecoveryMs, 0.0)
        assertTrue(e.initialStrength > 0.0)
    }

    @Test fun broadRereadingIsOptInAndKeepsItsCompleteRangeAndWitness() {
        val p = policy()
        val e = activate(p)
        assertEquals("A4_BROAD_REREADING_V5", e.ruleId)
        assertEquals(0.5, e.initialStrength, 0.0)
        assertEquals(witness(), e.reread)
        assertEquals(4, e.reread!!.firstLine)
        assertEquals(7, e.reread!!.lastLine)
        assertTrue(e.episodes.isEmpty())
        assertTrue(e.alternations.isEmpty())
        val legacy = policy(false)
        legacy.support(support(1000.0), context(1000.0))
        assertNull(legacy.reread(witness(), context(1000.0)))
        assertFalse(legacy.hasDemand)
    }

    @Test fun duplicatesTicksAndRawEligibleInputEarnNoRecovery() {
        val p = policy()
        activate(p)
        for (t in 1100..1400 step 100) p.input(context(t.toDouble()))
        assertEquals(0.0, p.currentEvidence(context(1400.0))!!.observedRecoveryMs, 0.0)
        p.support(support(1400.0), context(1400.0)) // A 400 ms support gap earns nothing.
        val e = p.support(support(1500.0), context(1500.0))!!
        assertEquals(100.0, e.observedRecoveryMs, 0.0)
        repeat(5) { p.support(support(1500.0), context(1500.0)) }
        assertEquals(100.0, p.currentEvidence(context(1700.0))!!.observedRecoveryMs, 0.0)
    }

    @Test fun supportGapMissingSegmentAndPauseCannotBridgeRecovery() {
        for (boundary in listOf("gap", "missing", "segment", "pause", "epoch")) {
            val p = policy()
            activate(p)
            p.support(support(1100.0), context(1100.0))
            when (boundary) {
                "missing" -> p.input(context(1150.0, reason = "no_face"))
                "pause", "epoch" -> p.pause()
            }
            val time = if (boundary == "gap") 1400.0 else 1200.0
            val ctx = if (boundary == "epoch") context(time, generation = 2, geometryId = "g2") else context(time)
            if (boundary == "pause" || boundary == "epoch") {
                assertTrue(p.hasDemand)
                assertNull(p.currentEvidence(ctx))
            }
            val segment = if (boundary == "segment") 2 else 1
            val e = p.support(support(time, segment = segment), ctx)!!
            assertEquals("Boundary $boundary", 100.0, e.observedRecoveryMs, 0.0)
            assertEquals("A4_BROAD_REREADING_V5", e.ruleId)
        }
    }

    @Test fun tailsAt250msCountButLongerTailsAndUnobservedVisitGapsDoNot() {
        val p = policy()
        activate(p)
        assertEquals(250.0, p.support(support(1250.0), context(1250.0))!!.observedRecoveryMs, 0.0)
        assertEquals(250.0, p.support(support(1500.001), context(1500.001))!!.observedRecoveryMs, 0.0)
        val e = p.support(support(1600.0, first = 1580.0), context(1600.0))!!
        assertEquals(270.0, e.observedRecoveryMs, 0.0)
    }

    @Test fun fiveHzAndExtractorOnlyBreaksRetainQualifiedSupportRecovery() {
        val p = policy()
        activate(p)
        for (t in 1200..2000 step 200) {
            val ctx = context(t.toDouble())
            p.input(ctx)
            p.support(support(t.toDouble()), ctx)
        }
        assertEquals(1000.0, p.currentEvidence(context(2000.0))!!.observedRecoveryMs, 0.0)
        val ctx = context(2200.0, reason = "insufficient_samples")
        p.input(ctx)
        // A qualified aid-local visit can survive a finalized-detector-only exclusion.
        assertEquals(1200.0, p.support(support(2200.0), ctx)!!.observedRecoveryMs, 0.0)
    }

    @Test fun holdAndFadeUseSupportedTimeAndRefreshPreservesRecoveryAndActivationAge() {
        val p = policy()
        activate(p)
        recover(p, 4000)
        assertEquals(1.0, p.currentEvidence(context(4000.0))!!.recoveryFactor, 0.0)
        recoverFrom(p, 4100, 5000)
        val before = p.currentEvidence(context(5000.0))!!
        assertEquals(4000.0, before.observedRecoveryMs, 0.0)
        assertEquals(6.0 / 7.0, before.recoveryFactor, 1e-12)
        val refresh = p.reread(witness("r2", 4000.0), context(5000.0))!!
        assertEquals(1.0, refresh.initialStrength, 0.0)
        assertEquals(before.observedRecoveryMs, refresh.observedRecoveryMs, 0.0)
        assertEquals(before.recoveryFactor, refresh.recoveryFactor, 0.0)
        assertEquals(refresh, p.reread(witness("r2", 4000.0), context(5000.0)))
        // A new ID attached to the same captures is not a distinct second sequence.
        assertEquals(refresh, p.reread(witness("r3", 4000.0), context(5000.0)))
        p.input(context(31001.0).copy(rawSample = null))
        assertFalse(p.hasDemand) // Original activation at 1000, rather than refreshed at 5000.
    }

    private fun recoverFrom(p: ReadingFocusPolicy, start: Int, end: Int) {
        for (t in start..end step 100) p.support(support(t.toDouble()), context(t.toDouble()))
    }

    @Test fun userCapsAndManualStrengthApplyThroughoutFadeAndResetClearsDemand() {
        val p = policy()
        activate(p)
        val capped = p.currentEvidence(context(1000.0), ReaderAidPreferences(maxStrength = 0.2))!!
        assertEquals(0.2, minOf(0.2, capped.initialStrength) * capped.recoveryFactor, 0.0)
        assertNull(p.currentEvidence(context(1000.0), ReaderAidPreferences(maxStrength = 0.0)))
        assertNull(p.currentEvidence(context(1000.0),
            ReaderAidPreferences(adaptiveStrength = false, manualStrength = 0.0)))
        recover(p, 11000)
        assertNull(p.currentEvidence(context(11000.0)))
        assertFalse(p.hasDemand)
        p.reset()
        assertNull(p.currentEvidence(context(11000.0)))
        assertFalse(p.hasDemand)
    }

    @Test fun interruptionRouteRemainsAvailableWhenRereadRouteIsSelectedThenTypographyChanges() {
        val p = ReadingFocusPolicy(config.copy(focusInterruptionMinEpisodes = 1), ScanpathConfig()) {}
        for (t in listOf(0.0, 50.0, 100.0)) p.input(context(t))
        p.input(context(200.0, reason = "no_face"))
        p.input(context(700.0, reason = "no_face"))
        for (t in listOf(800.0, 850.0, 900.0)) p.input(context(t))
        p.support(support(900.0, first = 880.0), context(900.0))
        assertEquals("A4_RETURN_PRESENCE_FOCUS_V2", p.currentEvidence(context(900.0))!!.ruleId)
        p.support(support(1900.0), context(1900.0))
        p.reread(witness("r1", 900.0), context(1900.0))
        p.support(support(3000.0), context(3000.0))
        val reread = p.reread(witness("r2", 2000.0), context(3000.0))!!
        assertEquals("A4_BROAD_REREADING_V5", reread.ruleId)
        // Font changes invalidate line-based witnesses while presence episodes remain valid.
        val retained = p.currentEvidence(context(3000.0).copy(focusTextSizeDp = 30.0))!!
        assertEquals("A4_RETURN_PRESENCE_FOCUS_V2", retained.ruleId)
        assertEquals(1, retained.episodes.size)
        assertNull(retained.reread)
    }

    @Test fun alternationStillActivatesAndV5RefreshKeepsItsSupportedRecovery() {
        for (v5 in listOf(false, true)) {
            val p = policy(v5)
            for ((i, line) in listOf(4, 5, 4, 5).withIndex()) {
                val t = 100.0 * (i + 1)
                p.support(support(t, first = t - 20.0, line = line), context(t))
            }
            assertEquals("A4_FONT_SCALED_LINE_VISITS_V3", p.currentEvidence(context(400.0))!!.ruleId)
            p.support(support(500.0, first = 380.0, line = 5), context(500.0))
            val recovery = p.currentEvidence(context(500.0))!!.observedRecoveryMs
            for ((i, line) in listOf(4, 5, 4, 5).withIndex()) {
                val t = 600.0 + 100.0 * i
                p.support(support(t, first = t - 20.0, line = line), context(t))
            }
            val e = p.currentEvidence(context(900.0))!!
            assertEquals(1.0, e.initialStrength, 0.0)
            assertEquals(2, e.alternations.size)
            assertEquals(if (v5) recovery + 80.0 else 0.0, e.observedRecoveryMs, 0.0)
        }
    }
}
