package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathConfig
import org.junit.Assert.*
import org.junit.Test

/** Synthetic qualification and cross-route accounting contracts, not human validity evidence. */
class ReadingFocusSupportedEpisodeTest {
    private fun policy() = ReadingFocusPolicy(ReadingAidPolicyConfig.evidenceAlignedPrototype(), ScanpathConfig()) {}
    private fun context(t: Double, size: Double = 22.0) =
        ReadingAidContext(1, "g", 12, 7, null, null, t, t, t, focusTextSizeDp = size)
    private fun visit(first: Double, row: Int, duration: Double = 150.0) =
        ReadingAidContinuationEvidence("v:$first:$row", 1, row, first, first + duration, first + duration)
    private fun broad(p: ReadingFocusPolicy): ReadingAidFocusEvidence {
        val visits = listOf(4, 7, 4, 5, 6).mapIndexed { i, row -> visit(i * 200.0, row) }
        val c = context(950.0)
        p.support(visits.last(), c)
        return p.reread(ReadingAidRereadEvidence("broad", visits, 4, 7, 950.0), c)!!
    }
    private fun abab(p: ReadingFocusPolicy, first: Double, other: Int = 7,
        measured: Boolean = false, size: Double = 22.0): ReadingAidFocusEvidence? {
        var evidence: ReadingAidFocusEvidence? = null
        listOf(4, other, 4, other).forEachIndexed { i, row ->
            val v = visit(first + i * 300.0, row, 250.0)
            val band = if (measured) ReadingSupportedRowBand(row, row, row, true,
                "synthetic_measured_intervals", row * 20.0 + 8, row * 20.0 + 12) else null
            evidence = p.support(v, context(v.lastCaptureMs, size), ReaderAidPreferences(), band)
        }
        return evidence
    }

    @Test fun globalLedgerRejectsCrossRouteOverlapAndCreditsFreshDifferentRoute() {
        val p = policy()
        val first = broad(p)
        assertEquals(.5, first.initialStrength, 0.0)
        assertEquals(1, first.qualifiedEpisodes.size)
        val overlap = abab(p, 900.0)!!
        assertEquals(.5, overlap.initialStrength, 0.0)
        assertEquals(1, overlap.qualifiedEpisodes.size)
        val second = abab(p, 2200.0)!!
        assertEquals(1.0, second.initialStrength, 0.0)
        assertEquals(2, second.qualifiedEpisodes.size)
        assertEquals(listOf("A4_BROAD_REREADING_V5", "A4_FONT_SCALED_LINE_VISITS_V3"),
            second.qualifiedEpisodes.map { it.routeId })
        val (a, b) = second.qualifiedEpisodes
        assertTrue(b.firstObservedMs > a.lastCaptureMs)
        assertTrue(a.confirmedDeliveryMs >= a.lastCaptureMs)
        assertTrue(b.confirmedDeliveryMs >= b.lastCaptureMs)
        assertTrue(second.qualifiedEpisodes.all { it.spatialProvenance.isNotBlank() && it.timeBasis.isNotBlank() })
    }

    @Test fun measuredNonoverlapAllowsAdjacentAnchorsButUnknownBandsDoNot() {
        assertNull(abab(policy(), 0.0, other = 5))
        val measured = abab(policy(), 0.0, other = 5, measured = true)!!
        assertEquals(.5, measured.initialStrength, 0.0)
        assertEquals(1, measured.qualifiedEpisodes.size)
        assertTrue(measured.qualifiedEpisodes.single().alternations.single().anchorBands.all { it.measured })
        val overlapping = policy()
        listOf(4, 5, 4, 5).forEachIndexed { i, row ->
            val v = visit(i * 300.0, row, 250.0)
            val band = ReadingSupportedRowBand(4, 5, row, true, "synthetic_overlapping_intervals", 80.0, 120.0)
            assertNull(overlapping.support(v, context(v.lastCaptureMs), ReaderAidPreferences(), band))
        }
    }

    @Test fun fontScaledAlternationsNeedACompleteNewEpisodeBeforeEscalating() {
        for ((size, required) in listOf(22.0 to 1, 18.0 to 2, 14.0 to 3)) {
            val p = policy()
            var t = 0.0
            repeat(2) { episode ->
                repeat(required) { index ->
                    val e = abab(p, t, size = size)
                    if (index < required - 1) {
                        if (episode == 0) assertNull("Font $size incomplete episode", e)
                        else {
                            assertEquals(.5, e!!.initialStrength, 0.0)
                            assertEquals(1, e.qualifiedEpisodes.size)
                        }
                    } else {
                        assertEquals(if (episode == 0) .5 else 1.0, e!!.initialStrength, 0.0)
                        assertEquals(episode + 1, e.qualifiedEpisodes.size)
                    }
                    t += 1400.0
                }
                // Resume on a fresh segment so same-row boundaries cannot merge episodes.
                p.pause()
            }
        }
    }
}
