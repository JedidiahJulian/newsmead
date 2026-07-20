package com.newsmead.gaze

import android.graphics.Rect
import android.widget.TextView
import java.text.BreakIterator

/**
 * Stage 4 AOI mapping: resolves a full-screen gaze/touch y-coordinate to the
 * article body's text-line index.
 *
 * Scroll-awareness is free: the body [TextView]'s on-screen position (from
 * [getLocationOnScreen]) already moves as the article scrolls, so
 * `screenY - textViewTop` is the offset into the text [android.text.Layout]
 * regardless of scroll position — no separate scroll-offset tracking needed.
 * The font size is locked (see StudyConfig.ARTICLE_FONT_SIZE_DP), so line
 * heights and therefore these bands are stable within a session.
 */
class LineAoiMapper(private val textView: TextView) {

    private val loc = IntArray(2)
    private val visibleRect = Rect()
    private var cachedText = ""
    private var wordIterator: BreakIterator? = null

    /** @return the 0-based line index under [screenY], or -1 if outside the text. */
    fun lineAt(screenY: Float): Int {
        val layout = textView.layout ?: return -1
        if (!textView.getGlobalVisibleRect(visibleRect) ||
            screenY < visibleRect.top || screenY >= visibleRect.bottom
        ) return -1
        textView.getLocationOnScreen(loc)
        val localY = screenY - loc[1] - textView.totalPaddingTop
        if (localY < 0f || localY >= layout.height.toFloat()) return -1
        return layout.getLineForVertical(localY.toInt())
    }

    /** Resolve both the robust line target and, when possible, a word range. */
    fun targetAt(screenX: Float, screenY: Float): TextTarget {
        val layout = textView.layout ?: return TextTarget.INVALID
        val line = lineAt(screenY)
        if (line !in 0 until layout.lineCount) return TextTarget.INVALID

        textView.getLocationOnScreen(loc)
        val localX = screenX - loc[0] - textView.totalPaddingLeft
        val lineLeft = layout.getLineLeft(line)
        val lineRight = layout.getLineRight(line)
        if (localX < minOf(lineLeft, lineRight) || localX > maxOf(lineLeft, lineRight)) {
            return TextTarget(line, layout.lineCount)
        }

        val text = textView.text.toString()
        if (text.isEmpty()) return TextTarget(line, layout.lineCount)
        val offset = layout.getOffsetForHorizontal(line, localX)
            .coerceIn(layout.getLineStart(line), (layout.getLineEnd(line) - 1).coerceAtLeast(0))
        if (offset !in text.indices || !text[offset].isLetterOrDigit()) {
            return TextTarget(line, layout.lineCount)
        }

        if (cachedText != text || wordIterator == null) {
            cachedText = text
            wordIterator = BreakIterator.getWordInstance(textView.textLocale).apply { setText(text) }
        }
        val iterator = wordIterator ?: return TextTarget(line, layout.lineCount)
        val start = if (iterator.isBoundary(offset)) offset else iterator.preceding(offset + 1)
        val end = iterator.following(offset)
        if (start == BreakIterator.DONE || end == BreakIterator.DONE || start >= end ||
            text.substring(start, end).none { it.isLetterOrDigit() }
        ) return TextTarget(line, layout.lineCount)

        return TextTarget(
            lineIndex = line,
            lineCount = layout.lineCount,
            wordStart = start,
            wordEnd = end,
        )
    }

    /** Total number of laid-out lines in the body, or 0 before layout. */
    val lineCount: Int get() = textView.layout?.lineCount ?: 0
}
