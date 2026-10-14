package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathGeometry
import java.text.BreakIterator
import java.util.Locale

/** Text-only units cached for the latest text; offsets always refer to the original UTF-16 string. */
class ReadingGuideResolver {
    private data class Paragraph(val range: ReadingGuideRange, val sentences: List<ReadingGuideRange>)
    private var cachedText: String? = null
    private var paragraphs = emptyList<Paragraph>()

    fun resolve(text: String, geometry: ScanpathGeometry?, line: Int?): ReadingGuideRange? {
        val g = geometry ?: return null
        val row = g.lines.getOrNull(line ?: return null) ?: return null
        if (g.snapshotId.isBlank() || text.isBlank() ||
            !listOf(g.textOriginX, g.textOriginY, g.lineHeightPx).all { it.isFinite() } ||
            g.lineHeightPx <= 0 ||
            g.lines.any { it.startUtf16 < 0 || it.endUtf16 < it.startUtf16 ||
                it.endUtf16 > text.length || splitsSurrogate(text, it.startUtf16) ||
                splitsSurrogate(text, it.endUtf16) } ||
            g.lines.zipWithNext().any { (a, b) -> a.endUtf16 > b.startUtf16 } ||
            !listOf(row.left, row.right, row.top, row.bottom).all { it.isFinite() } ||
            row.right <= row.left || row.bottom <= row.top) return null
        val selected = trimmed(text, row.startUtf16, row.endUtf16, ReadingGuideUnit.PARAGRAPH)
            ?: return null
        if (cachedText != text) {
            paragraphs = segment(text)
            cachedText = text
        }
        val overlapping = paragraphs.filter { intersects(it.range, selected) }
        val paragraph = overlapping.singleOrNull() ?: return null
        if (selected.startUtf16 < paragraph.range.startUtf16 ||
            selected.endUtf16 > paragraph.range.endUtf16) return null
        val sentences = paragraph.sentences.filter { intersects(it, selected) }
        val result = when (sentences.size) {
            0 -> null
            1 -> sentences.single()
            else -> paragraph.range
        } ?: return null
        return result.takeIf { it.startUtf16 >= g.lines.first().startUtf16 &&
            it.endUtf16 <= g.lines.last().endUtf16 }
    }

    private fun segment(text: String): List<Paragraph> {
        val out = mutableListOf<Paragraph>()
        var start = 0
        // A single line break may be author wrapping; blank lines are paragraph boundaries.
        val separators = Regex("""(?:\r\n|\r(?!\n)|(?<!\r)\n)[\t \x0C]*(?:\r\n|\r(?!\n)|(?<!\r)\n)+""")
        fun add(end: Int) {
            val range = trimmed(text, start, end, ReadingGuideUnit.PARAGRAPH) ?: return
            val iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH)
            iterator.setText(text.substring(range.startUtf16, range.endUtf16))
            val sentences = mutableListOf<ReadingGuideRange>()
            var first = iterator.first()
            var next = iterator.next()
            while (next != BreakIterator.DONE) {
                trimmed(text, range.startUtf16 + first, range.startUtf16 + next,
                    ReadingGuideUnit.SENTENCE)?.let { sentences.add(it) }
                first = next
                next = iterator.next()
            }
            out.add(Paragraph(range, sentences))
        }
        for (separator in separators.findAll(text)) {
            add(separator.range.first)
            start = separator.range.last + 1
        }
        add(text.length)
        return out
    }

    private fun trimmed(text: String, first: Int, last: Int, unit: ReadingGuideUnit): ReadingGuideRange? {
        var start = first
        var end = last
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return if (start == end) null else ReadingGuideRange(start, end, unit)
    }

    private fun intersects(a: ReadingGuideRange, b: ReadingGuideRange) =
        a.startUtf16 < b.endUtf16 && b.startUtf16 < a.endUtf16

    private fun splitsSurrogate(text: String, offset: Int) = offset > 0 && offset < text.length &&
        text[offset - 1].isHighSurrogate() && text[offset].isLowSurrogate()
}
