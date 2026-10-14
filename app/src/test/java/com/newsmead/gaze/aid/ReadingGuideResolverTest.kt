package com.newsmead.gaze.aid
import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

class ReadingGuideResolverTest {
    private fun geometry(vararg ranges: Pair<Int, Int>) = ScanpathGeometry("g", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 200.0, 200.0), 20.0,
        ranges.mapIndexed { i, (start, end) -> ScanpathLine(start, end, 0.0, 180.0,
            i * 20.0, i * 20.0 + 20.0) })

    @Test fun wrappedSentenceHasOneStableOriginalRange() {
        val text = "A long sentence wraps here. Next one."
        val g = geometry(0 to 10, 10 to 27, 27 to text.length)
        val r = ReadingGuideResolver()
        val expected = ReadingGuideRange(0, 27, ReadingGuideUnit.SENTENCE)
        assertEquals(expected, r.resolve(text, g, 0))
        assertEquals(expected, r.resolve(text, g, 1))
    }
    @Test fun sharedLineFallsBackToOnlyContainingParagraph() {
        val text = "One. Two.\r\n\r\nThree."
        val r = ReadingGuideResolver()
        assertEquals(ReadingGuideRange(0, 9, ReadingGuideUnit.PARAGRAPH),
            r.resolve(text, geometry(0 to 9), 0))
        assertNull(r.resolve(text, geometry(0 to text.length), 0))
        assertNull(r.resolve(text, geometry(9 to 13), 0))
    }
    @Test fun unicodeOffsetsWhitespaceAndCacheReplacement() {
        val text = "  \uD83D\uDE00 reads.   "
        val r = ReadingGuideResolver()
        assertEquals(ReadingGuideRange(2, 11, ReadingGuideUnit.SENTENCE),
            r.resolve(text, geometry(0 to text.length), 0))
        assertEquals(ReadingGuideRange(0, 4, ReadingGuideUnit.SENTENCE),
            r.resolve("New.", geometry(0 to 4), 0))
        assertNull(r.resolve(text, geometry(3 to text.length), 0))
    }
    @Test fun blankInvalidAndOverlappingGeometryAbstain() {
        val r = ReadingGuideResolver()
        assertNull(r.resolve("   ", geometry(0 to 3), 0))
        assertNull(r.resolve("One.", geometry(0 to 5), 0))
        assertNull(r.resolve("One.", geometry(0 to 4), 1))
        assertNull(r.resolve("One.", null, 0))
        assertNull(r.resolve("One. Two.", geometry(0 to 6, 5 to 9), 0))
    }
    @Test fun singleCrLfDoesNotCreateParagraphBoundary() {
        val text = "One\r\nwraps. Two."
        assertEquals(ReadingGuideUnit.SENTENCE,
            ReadingGuideResolver().resolve(text, geometry(0 to 5, 5 to text.length), 0)?.unit)
    }
}
