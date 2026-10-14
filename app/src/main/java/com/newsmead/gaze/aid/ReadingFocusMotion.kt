package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathGeometry

/** Presentation time only: never changes an action, anchor, or scanpath observation. */
internal class ReadingFocusMotion {
    companion object {
        const val TRANSITION_MS = 180.0
        const val SCROLL_HOLD_MS = 1500.0

        fun fitPinnedBand(band: Band, viewportTop: Double, viewportBottom: Double): Band {
            val height = (band.bottom - band.top).coerceIn(0.0, viewportBottom - viewportTop)
            val top = band.top.coerceIn(viewportTop, viewportBottom - height)
            return Band(top, top + height)
        }

        fun compatibleTranslation(before: ScanpathGeometry, after: ScanpathGeometry): Boolean =
            before.textOriginY.isFinite() && after.textOriginY.isFinite() &&
                before.textOriginX == after.textOriginX && before.lines == after.lines &&
                before.lineHeightPx == after.lineHeightPx && before.textDirection == after.textDirection &&
                before.visibleRectScreen.left == after.visibleRectScreen.left &&
                before.visibleRectScreen.right == after.visibleRectScreen.right

        fun translated(before: ScanpathGeometry, after: ScanpathGeometry): Boolean =
            before.textOriginY != after.textOriginY ||
                before.visibleRectScreen.top != after.visibleRectScreen.top ||
                before.visibleRectScreen.bottom != after.visibleRectScreen.bottom
    }

    data class Band(val top: Double, val bottom: Double)
    private var from: Band? = null
    private var target: Band? = null
    private var startedMs = 0.0

    fun clear(): Boolean {
        val changed = from != null || target != null
        from = null; target = null
        return changed
    }
    fun pin(band: Band) { from = band; target = band }

    fun move(targetBand: Band, anchor: Band, now: Double): Band {
        if (target != targetBand) {
            val current = at(now) ?: targetBand
            // Expand immediately when necessary; the fresh physical anchor is never veiled.
            from = Band(minOf(current.top, anchor.top), maxOf(current.bottom, anchor.bottom))
            target = targetBand
            startedMs = now
        }
        val band = at(now)!!
        return Band(minOf(band.top, anchor.top), maxOf(band.bottom, anchor.bottom))
    }

    fun isMoving(now: Double) = from != target && now - startedMs < TRANSITION_MS

    private fun at(now: Double): Band? {
        val end = target ?: return null
        val start = from ?: return end
        val t = ((now - startedMs) / TRANSITION_MS).coerceIn(0.0, 1.0)
        val eased = t * t * (3 - 2 * t)
        return Band(start.top + (end.top - start.top) * eased,
            start.bottom + (end.bottom - start.bottom) * eased)
    }
}
