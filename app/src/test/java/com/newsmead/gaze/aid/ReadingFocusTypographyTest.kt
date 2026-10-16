package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic ordered capture/delivery inputs; no human difficulty or gaze accuracy claims. */
class ReadingFocusTypographyTest {
    private class Fixture(var sizeDp: Double) {
        private val geometry = ScanpathGeometry("g", 0.0, 0.0,
            ScanpathRect(0.0, 0.0, 100.0, 160.0), 20.0,
            List(8) { i -> ScanpathLine(i * 10, i * 10 + 9, 0.0, 100.0, i * 20.0, (i + 1) * 20.0) })
        val policy = ReadingFocusPolicy(ReadingAidPolicyConfig.definitionPrototype(), ScanpathConfig()) {}
        var now = 0.0
        private fun context(line: Int = 0, missing: Boolean = false) = ReadingAidContext(
            0, "g", 8, line.takeUnless { missing }, null, null, now,
            now.takeUnless { missing }, now, !missing, geometry,
            rawSample = ScanpathSample(now.takeUnless { missing }, now, null, null,
                line.takeUnless { missing }, null, null, "g", !missing,
                if (missing) "no_face" else null, 20.0.takeUnless { missing },
                (line * 20.0 + 10.0).takeUnless { missing }, ScanpathCoordinateStream.ARTICLE_SMOOTHED),
            focusTextSizeDp = sizeDp)

        fun input(at: Double, line: Int = 0, missing: Boolean = false) {
            now = at
            policy.input(context(line, missing))
        }
        fun visit(line: Int, spanMs: Int): ReadingAidFocusEvidence? {
            val first = now + 40.0
            for (offset in 0..spanMs step 40) {
                input(first + offset, line)
                if (offset >= 100) policy.support(ReadingAidContinuationEvidence(
                    "visit:$first:$line", 1, line, first, now, now), context(line))
            }
            return policy.currentEvidence(context(line))
        }
        fun cycle(spanMs: Int): ReadingAidFocusEvidence? {
            var evidence: ReadingAidFocusEvidence? = null
            for (line in listOf(0, 1, 0, 1)) evidence = visit(line, spanMs)
            return evidence
        }
        fun episode() {
            val start = now + 40.0
            input(start, missing = true)
            input(start + 400.0, missing = true)
            for (offset in listOf(500.0, 550.0, 600.0)) input(start + offset)
            policy.support(ReadingAidContinuationEvidence("anchor:$now", 1, 0, now - 100.0, now, now), context())
        }
        fun evidence() = policy.currentEvidence(context())
    }

    @Test fun smallerFontsNeedMoreNonoverlappingCyclesAndLongerVisits() {
        for ((size, required, span) in listOf(Triple(14.0, 3, 240), Triple(16.0, 3, 240),
            Triple(18.0, 2, 160), Triple(20.0, 2, 160), Triple(22.0, 1, 120), Triple(60.0, 1, 120))) {
            val f = Fixture(size)
            repeat(required - 1) { assertNull("$size before activation", f.cycle(span)) }
            val initial = checkNotNull(f.cycle(span))
            assertEquals("$size initial strength", 0.5, initial.initialStrength, 0.0)
            assertEquals(required, initial.alternations.size)
            assertEquals(required, initial.requiredAlternations)
            assertEquals(if (required == 1) 100.0 else span.toDouble(), initial.minimumLineVisitMs, 0.0)
            val stronger = checkNotNull(f.cycle(span))
            assertEquals(1.0, stronger.initialStrength, 0.0)
            assertEquals(required + 1, stronger.alternations.size)
            val visits = stronger.alternations.flatMap { a -> a.visits.map { it.evidenceId } }
            assertEquals(visits.size, visits.distinct().size)
        }
    }

    @Test fun rapidBriefAlternationCannotAccumulateSmallFontCycles() {
        for ((size, tooBrief) in listOf(14.0 to 200, 18.0 to 120)) {
            val f = Fixture(size)
            repeat(6) { assertNull(f.cycle(tooBrief)) }
            assertFalse(f.policy.hasDemand)
            assertEquals(0, f.policy.diagnostics(f.now)["recent_alternation_count"])
        }
    }

    @Test fun resizingAlsoWithinOneTierDiscardsLineHistoryAndResponse() {
        val f = Fixture(22.0)
        assertNotNull(f.cycle(120))
        f.sizeDp = 20.0
        f.input(f.now + 40.0)
        assertFalse(f.policy.hasDemand)
        assertNull(f.cycle(160))
        f.sizeDp = 18.0 // Same tier; old physical line history must still be discarded.
        f.input(f.now + 40.0)
        assertNull(f.cycle(160))
        assertEquals(0.5, checkNotNull(f.cycle(160)).initialStrength, 0.0)
    }

    @Test fun anInterveningEstablishedShortVisitCannotBeDeletedToInventABAB() {
        val f = Fixture(14.0)
        repeat(6) {
            assertNull(f.visit(0, 240))
            assertNull(f.visit(1, 240))
            assertNull(f.visit(2, 200)) // Detector-established, below the 240 ms focus requirement.
        }
        assertFalse(f.policy.hasDemand)
        assertEquals(0, f.policy.diagnostics(f.now)["recent_alternation_count"])
    }

    @Test fun lookAwayStillNeedsThreeEpisodesAtEverySizeAndSurvivesResize() {
        for (size in listOf(14.0, 18.0, 22.0, 60.0)) {
            val f = Fixture(size)
            for (t in listOf(0.0, 50.0, 100.0)) f.input(t)
            repeat(2) { f.episode(); assertFalse(f.policy.hasDemand) }
            f.sizeDp = 16.0 // Completed episodes must survive a font change.
            f.episode()
            val e = checkNotNull(f.evidence())
            assertEquals(3, e.episodeCount)
            assertEquals(0.5, e.initialStrength, 0.0)
            assertEquals("A4_RETURN_PRESENCE_FOCUS_V2", e.ruleId)
        }
    }

    @Test fun tierBoundariesAllowPixelRoundoffAndInvalidSizeUsesStrictRule() {
        assertEquals(ReadingFocusTypography.COMPACT, ReadingFocusTypography.rule(17.99))
        assertEquals(ReadingFocusTypography.SMALL, ReadingFocusTypography.rule(17.9999))
        assertEquals(ReadingFocusTypography.SMALL, ReadingFocusTypography.rule(21.99))
        assertEquals(ReadingFocusTypography.STANDARD, ReadingFocusTypography.rule(21.9999))
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertEquals(ReadingFocusTypography.COMPACT, ReadingFocusTypography.rule(bad))
    }
}
