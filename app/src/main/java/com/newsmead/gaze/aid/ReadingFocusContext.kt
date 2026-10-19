package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathGeometry

/** The reread passage and a neighbour stay legible during tracking and scroll holds. */
internal object ReadingFocusContext {
    fun protect(band: ReadingFocusMotion.Band, geometry: ScanpathGeometry,
        reread: ReadingAidRereadEvidence?, viewportTop: Double, viewportBottom: Double): ReadingFocusMotion.Band {
        if (reread == null) return band
        val first = geometry.lines.getOrNull((reread.firstLine - 1).coerceAtLeast(0)) ?: return band
        val last = geometry.lines.getOrNull((reread.lastLine + 1).coerceAtMost(geometry.lines.lastIndex)) ?: return band
        return ReadingFocusMotion.Band(minOf(band.top,
            (geometry.textOriginY + first.top).coerceIn(viewportTop, viewportBottom)),
            maxOf(band.bottom, (geometry.textOriginY + last.bottom).coerceIn(viewportTop, viewportBottom)))
    }
}
