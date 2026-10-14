package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathRect
import kotlin.math.roundToInt

/** Focus geometry in screen pixels. The anchor itself is never clipped to the context budget. */
internal object ReadingFocusAppearance {
    const val MAX_CONTEXT_HEIGHT_FRACTION = 0.6
    const val BASE_BACKGROUND_OPACITY = 0.90
    const val MAX_BACKGROUND_OPACITY = 0.98
    const val BASE_DARK_OPACITY = 0.40
    const val MAX_DARK_OPACITY = 0.60

    data class Appearance(
        val viewport: ScanpathRect,
        val clearTop: Double,
        val clearBottom: Double,
        val contextRadiusLines: Int,
        val alpha: Int,
    ) {
        val shadedAreaPx2: Double get() = (viewport.right - viewport.left) *
            ((clearTop - viewport.top) + (viewport.bottom - clearBottom))
    }

    data class Resolution(val appearance: Appearance?, val withheldReason: String?)

    fun resolve(geometry: ScanpathGeometry, viewport: ScanpathRect, anchorLine: Int,
                preferredRadiusLines: Int, strength: Double,
                limitContextHeight: Boolean = true,
                keepLineCount: Boolean = false,
                occludeSurroundings: Boolean = false,
                focusStyle: ReadingFocusStyle = ReadingFocusStyle.FADE_TEXT): Resolution {
        fun withheld(reason: String) = Resolution(null, reason)
        if (!strength.isFinite()) return withheld("focus_alpha_zero")
        val boundedStrength = strength.coerceIn(0.0, 1.0)
        // Each V4 style has its own opacity. Recovery below 50% fades continuously
        // to zero; historical forms retain their original black veil.
        val baseOpacity = if (focusStyle == ReadingFocusStyle.DARK_SURROUNDINGS) BASE_DARK_OPACITY else BASE_BACKGROUND_OPACITY
        val maxOpacity = if (focusStyle == ReadingFocusStyle.DARK_SURROUNDINGS) MAX_DARK_OPACITY else MAX_BACKGROUND_OPACITY
        val opacity = if (boundedStrength <= 0.5) baseOpacity * boundedStrength / 0.5
            else baseOpacity + (maxOpacity - baseOpacity) *
                (boundedStrength - 0.5) / 0.5
        val alpha = if (occludeSurroundings) (255 * opacity).roundToInt()
            else (40 * boundedStrength).toInt()
        if (alpha <= 0) return withheld("focus_alpha_zero")
        if (listOf(viewport.left, viewport.top, viewport.right, viewport.bottom).any { !it.isFinite() } ||
            viewport.right <= viewport.left || viewport.bottom <= viewport.top ||
            !geometry.textOriginY.isFinite()) return withheld("focus_geometry_unavailable")
        val anchor = geometry.lines.getOrNull(anchorLine) ?: return withheld("line_unavailable")
        if (!anchor.top.isFinite() || !anchor.bottom.isFinite() || anchor.bottom <= anchor.top)
            return withheld("focus_geometry_unavailable")
        val anchorTop = (geometry.textOriginY + anchor.top).coerceAtLeast(viewport.top)
        val anchorBottom = (geometry.textOriginY + anchor.bottom).coerceAtMost(viewport.bottom)
        if (anchorTop >= anchorBottom) return withheld("anchor_not_visible")
        var radius = preferredRadiusLines.coerceIn(0, geometry.lines.lastIndex)
        if (keepLineCount) {
            val visibleLines = geometry.lines.indices.filter { i ->
                val row = geometry.lines[i]
                row.top.isFinite() && row.bottom.isFinite() && row.bottom > row.top &&
                    geometry.textOriginY + row.bottom > viewport.top && geometry.textOriginY + row.top < viewport.bottom
            }
            if (visibleLines.isEmpty()) return withheld("anchor_not_visible")
            var count = minOf(2 * radius + 1, visibleLines.last() - visibleLines.first() + 1)
            fun band(lineCount: Int): Appearance {
                // Slide at either edge to retain the requested available rows.
                val firstIndex = (anchorLine - radius).coerceIn(visibleLines.first(), visibleLines.last() - lineCount + 1)
                val lastIndex = firstIndex + lineCount - 1
                val top = (geometry.textOriginY + geometry.lines[firstIndex].top).coerceIn(viewport.top, viewport.bottom)
                val bottom = (geometry.textOriginY + geometry.lines[lastIndex].bottom).coerceIn(viewport.top, viewport.bottom)
                return Appearance(viewport, minOf(anchorTop, top), maxOf(anchorBottom, bottom), radius, alpha)
            }
            var appearance = band(count)
            // The owner allows four or five rows. Never collapse to three just to create shading.
            if (appearance.shadedAreaPx2 <= 0 && count == 5) {
                count = 4
                appearance = band(count)
            }
            return if (appearance.shadedAreaPx2 > 0) Resolution(appearance, null)
            else withheld("focus_no_dimmed_area")
        }
        while (true) {
            val first = geometry.lines[(anchorLine - radius).coerceAtLeast(0)]
            val last = geometry.lines[(anchorLine + radius).coerceAtMost(geometry.lines.lastIndex)]
            if (!first.top.isFinite() || !last.bottom.isFinite())
                return withheld("focus_geometry_unavailable")
            val top = minOf(anchorTop, (geometry.textOriginY + first.top)
                .coerceIn(viewport.top, viewport.bottom))
            val bottom = maxOf(anchorBottom, (geometry.textOriginY + last.bottom)
                .coerceIn(viewport.top, viewport.bottom))
            if (!limitContextHeight || radius == 0 || bottom - top <=
                (viewport.bottom - viewport.top) * MAX_CONTEXT_HEIGHT_FRACTION) {
                val appearance = Appearance(viewport, top, bottom, radius, alpha)
                return if (appearance.shadedAreaPx2 > 0) Resolution(appearance, null)
                else withheld("focus_no_dimmed_area")
            }
            radius--
        }
    }
}
