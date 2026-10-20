package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathLine

/** Appearance only. Bounds stay in body-content coordinates; scrolling adds no motion. */
internal class ReadingThreeLineGuideMotion {
    data class Appearance(
        val top: Double, val centreTop: Double, val centreBottom: Double, val bottom: Double,
        val aboveOpacity: Double, val centreOpacity: Double, val belowOpacity: Double,
    ) {
        /** Entry, loss and reader caps apply to the whole band in the same ratio. */
        fun capped(centreCap: Double): Appearance {
            val factor = if (centreOpacity > 0 && centreCap.isFinite())
                (centreCap / centreOpacity).coerceIn(0.0, 1.0) else 0.0
            return copy(aboveOpacity = aboveOpacity * factor, centreOpacity = centreOpacity * factor,
                belowOpacity = belowOpacity * factor)
        }
        internal fun bounds() = listOf(top, centreTop, centreBottom, bottom)
        internal fun alphas() = listOf(aboveOpacity, centreOpacity, belowOpacity)
    }

    companion object {
        const val TRANSITION_MS = ReadingLineGuideAppearance.TRANSITION_MS

        fun target(rows: List<ScanpathLine>, centre: Int, above: Double, middle: Double,
                   below: Double, direction: ReadingGuideDirection = ReadingGuideDirection.BALANCED): Appearance? {
            val row = rows.getOrNull(centre) ?: return null
            val range = ReadingLineGuideAppearance.rows(centre, rows.lastIndex, direction)
            val top = rows[range.first].top
            val bottom = rows[range.last].bottom
            if (listOf(top, row.top, row.bottom, bottom, above, middle, below).any { !it.isFinite() } ||
                top > row.top || row.top >= row.bottom || row.bottom > bottom) return null
            val alpha = middle.coerceIn(0.0, ReadingLineGuideAppearance.THREE_CENTER_OPACITY)
            return Appearance(top, row.top, row.bottom, bottom,
                if (range.first == centre) 0.0 else above.coerceIn(0.0, alpha), alpha,
                if (range.last == centre) 0.0 else below.coerceIn(0.0, alpha))
        }

        fun singleLineTarget(rows: List<ScanpathLine>, centre: Int, opacity: Double): Appearance? {
            val row = rows.getOrNull(centre) ?: return null
            if (!row.top.isFinite() || !row.bottom.isFinite() || row.top >= row.bottom || !opacity.isFinite()) return null
            return Appearance(row.top, row.top, row.bottom, row.bottom, 0.0, opacity.coerceIn(0.0, .19), 0.0)
        }
    }

    private var from: Appearance? = null
    private var target: Appearance? = null
    private var positionStartedMs = 0.0
    private var alphaStartedMs = 0.0
    private var positionDurationMs = TRANSITION_MS
    private var alphaDurationMs = TRANSITION_MS

    fun clear() { from = null; target = null }

    fun move(next: Appearance, now: Double, transitionMs: Double = TRANSITION_MS,
             animateOpacity: Boolean = true): Appearance {
        val current = at(now) ?: next
        val previous = target
        val duration = transitionMs.takeIf { it.isFinite() && it > 0 } ?: TRANSITION_MS
        // Fresh support can change opacity on every callback. Only changed bounds restart motion.
        if (previous == null || previous.bounds() != next.bounds()) {
            from = (from ?: current).copy(top = current.top, centreTop = current.centreTop,
                centreBottom = current.centreBottom, bottom = current.bottom)
            positionStartedMs = now
            positionDurationMs = duration
        }
        if (previous == null || previous.alphas() != next.alphas()) {
            from = (from ?: current).copy(aboveOpacity = current.aboveOpacity,
                centreOpacity = current.centreOpacity, belowOpacity = current.belowOpacity)
            alphaStartedMs = now
            alphaDurationMs = if (animateOpacity) duration else 0.0
        }
        target = next
        return at(now)!!
    }

    fun isMoving(now: Double): Boolean {
        val start = from ?: return false
        val end = target ?: return false
        return (start.bounds() != end.bounds() && now - positionStartedMs < positionDurationMs) ||
            (start.alphas() != end.alphas() && now - alphaStartedMs < alphaDurationMs)
    }

    private fun at(now: Double): Appearance? {
        val end = target ?: return null
        val start = from ?: return end
        fun fraction(started: Double, duration: Double): Double {
            val t = if (duration <= 0) 1.0 else ((now - started) / duration).coerceIn(0.0, 1.0)
            return t * t * (3 - 2 * t)
        }
        val position = fraction(positionStartedMs, positionDurationMs)
        val alpha = fraction(alphaStartedMs, alphaDurationMs)
        fun mix(a: Double, b: Double, t: Double) = if (t >= 1.0) b else if (t <= 0.0) a else a + (b - a) * t
        return Appearance(mix(start.top, end.top, position), mix(start.centreTop, end.centreTop, position),
            mix(start.centreBottom, end.centreBottom, position), mix(start.bottom, end.bottom, position),
            mix(start.aboveOpacity, end.aboveOpacity, alpha), mix(start.centreOpacity, end.centreOpacity, alpha),
            mix(start.belowOpacity, end.belowOpacity, alpha))
    }
}
