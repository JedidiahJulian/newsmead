package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic episode/timestamp contracts; no claim of phone accuracy or reading difficulty. */
class ReadingFocusRereadGateTest {
    private val config = ReadingAidPolicyConfig.evidenceAlignedPrototype()
    private val geometry = ScanpathGeometry("g", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 100.0, 240.0), 20.0,
        (0..11).map { ScanpathLine(it * 10, it * 10 + 9, 0.0, 100.0, it * 20.0, it * 20.0 + 20.0) })
    private fun policy() = ReadingFocusPolicy(config, ScanpathConfig()) {}
    private fun context(t: Double, size: Double = 22.0) =
        ReadingAidContext(1, "g", 12, 6, null, null, t, t, t,
            geometry = geometry, focusTextSizeDp = size)
    private fun witness(start: Double, id: String = "r:$start"): ReadingAidRereadEvidence {
        val visits = listOf(4, 7, 4, 5, 6).mapIndexed { i, line ->
            val first = start + i * 200.0
            ReadingAidContinuationEvidence("$id:$i", 1, line, first, first + 150.0, first + 150.0)
        }
        return ReadingAidRereadEvidence(id, visits, 4, 7, start + 950.0)
    }
    private fun add(p: ReadingFocusPolicy, start: Double, id: String = "r:$start"): ReadingAidFocusEvidence? {
        val r = witness(start, id)
        val c = context(r.confirmedDeliveryMs)
        p.support(r.visits.last(), c)
        return p.reread(r, c)
    }

    @Test fun oneCompletePrefixActivatesHalfAndAnotherDisjointEpisodeCanReachFull() {
        val p = policy()
        val first = add(p, 0.0)!!
        assertEquals(1, config.focusRereadMinSequences)
        assertEquals("A4_BROAD_REREADING_V5", first.ruleId)
        assertEquals(0.5, first.initialStrength, 0.0)
        assertEquals(1, first.requiredRereadSequences)
        assertEquals(1, first.rereads.size)
        assertEquals(1.0, add(p, 1000.0)!!.initialStrength, 0.0)
    }

    @Test fun duplicateRelabelledAndOverlappingCapturesCannotIncreaseStrength() {
        val p = policy()
        add(p, 0.0)
        repeat(4) {
            assertEquals(.5, add(p, 0.0)!!.initialStrength, 0.0)
            assertEquals(.5, add(p, 0.0, "relabel:$it")!!.initialStrength, 0.0)
        }
        assertEquals(.5, add(p, 800.0)!!.initialStrength, 0.0)
        assertEquals(1.0, add(p, 2000.0)!!.initialStrength, 0.0)
    }

    @Test fun partialOrInvalidWitnessAndTicksCannotCompleteAnEpisode() {
        for (kind in listOf("partial", "duplicate", "reversed")) {
            val p = policy()
            val r = witness(0.0)
            val visits = when (kind) {
                "partial" -> r.visits.dropLast(1)
                "duplicate" -> r.visits.toMutableList().apply { this[1] = this[0] }
                else -> r.visits.reversed()
            }
            assertNull(p.reread(r.copy(visits = visits), context(950.0)))
            repeat(10) { assertNull(p.currentEvidence(context(1000.0 + it))) }
            assertFalse(p.hasDemand)
        }
    }

    @Test fun fullCaptureSpanAtFifteenSecondsCountsButOneMillisecondOverDoesNot() {
        assertEquals(15000.0, config.focusRereadWindowMs, 0.0)
        for (extra in listOf(0.0, 1.0)) {
            val p = policy()
            // Long supported dwells separated by 50ms transitions isolate the whole-span boundary.
            val visits = listOf(4, 7, 4, 5, 6).mapIndexed { i, row ->
                val first = i * 3000.0
                val last = first + if (i == 4) 3000.0 + extra else 2950.0
                ReadingAidContinuationEvidence("boundary:$i", 1, row, first, last, last)
            }
            val end = visits.last().lastCaptureMs
            val r = ReadingAidRereadEvidence("boundary", visits, 4, 7, end)
            val c = context(end)
            p.support(visits.last(), c)
            val e = p.reread(r, c)
            if (extra == 0.0) {
                assertEquals(.5, e!!.initialStrength, 0.0)
                assertEquals(1, e.qualifiedEpisodes.size)
            } else {
                assertNull(e)
                assertFalse(p.hasDemand)
            }
        }
    }

    @Test fun responseExpiryStartsFreshEpisodeAtHalfEvenWithHistoricalEvidence() {
        val p = policy()
        add(p, 0.0); add(p, 1000.0)
        assertEquals(1.0, p.currentEvidence(context(1950.0))!!.initialStrength, 0.0)
        p.input(context(31000.0))
        assertFalse(p.hasDemand)
        assertEquals(.5, add(p, 32000.0)!!.initialStrength, 0.0)
    }

    @Test fun fontAndLayoutInvalidationRemoveRowWitnessesAndFreshEvidenceStartsHalf() {
        for (boundary in listOf("font", "layout", "reset")) {
            val p = policy()
            add(p, 0.0); add(p, 1000.0)
            when (boundary) {
                "font" -> p.currentEvidence(context(2000.0, size = 30.0))
                "layout" -> p.invalidateReread()
                else -> p.reset()
            }
            assertNull("Boundary $boundary", p.currentEvidence(context(2000.0)))
            assertEquals("Boundary $boundary", .5, add(p, 3000.0)!!.initialStrength, 0.0)
        }
    }
}
