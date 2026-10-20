package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathRect

/** Explicit externally measured error radii in screen pixels; never estimated from recent jitter. */
data class ReadingLocationUncertainty(
    val horizontalRadiusPx: Double, val verticalRadiusPx: Double, val provenance: String
) {
    init {
        require(horizontalRadiusPx.isFinite() && horizontalRadiusPx >= 0)
        require(verticalRadiusPx.isFinite() && verticalRadiusPx >= 0 && provenance.isNotBlank())
    }
}

data class ReadingSupportedRowBand(
    val firstLine: Int, val lastLine: Int, val representativeLine: Int,
    val measured: Boolean, val provenance: String,
    /** Body-content Y interval, comparable through viewport translation within one layout epoch. */
    val intervalTopPx: Double? = null, val intervalBottomPx: Double? = null
) {
    fun overlaps(other: ReadingSupportedRowBand) = firstLine <= other.lastLine && other.firstLine <= lastLine
    /** Unknown bands may support coarse movement, but never measured distinguishability. */
    fun measurablyDistinctFrom(other: ReadingSupportedRowBand): Boolean = measured && other.measured &&
        !overlaps(other) && intervalTopPx != null && intervalBottomPx != null &&
        other.intervalTopPx != null && other.intervalBottomPx != null &&
        (intervalBottomPx < other.intervalTopPx || other.intervalBottomPx < intervalTopPx)
}

data class ReadingSupportedVisit(
    val id: String, val segment: Int, val generation: Long, val geometrySnapshotId: String,
    val band: ReadingSupportedRowBand, val firstCaptureMs: Double, val lastCaptureMs: Double,
    val evidenceAvailableMs: Double,
    val firstConfirmedCaptureMs: Double = lastCaptureMs,
    val firstConfirmedDeliveryMs: Double = evidenceAvailableMs
)

data class ReadingSupportedReread(
    val visits: List<ReadingSupportedVisit>, val measuredDistinguishability: Boolean
)

data class ReadingSupportedWordResidence(
    val line: Int, val startUtf16: Int, val endUtf16: Int,
    val firstCaptureMs: Double, val lastCaptureMs: Double, val evidenceAvailableMs: Double,
    val confidenceProvenance: String
)

data class ReadingLocationUpdate(
    val current: ReadingSupportedVisit?, val fresh: Boolean,
    val reread: ReadingSupportedReread? = null,
    val wordResidence: ReadingSupportedWordResidence? = null,
    val searchWitnesses: List<ReadingSupportedVisit> = emptyList()
)

/** Aid-local engineering support, independent of frozen fixation extraction and its segments. */
class ReadingLocationSupport(
    private val minDwellMs: Double = 150.0,
    private val maxGapMs: Double = 250.0,
    private val rereadWindowMs: Double = 15_000.0,
    private val allowMappingOnlyWordResidence: Boolean = false
) {
    init {
        require(listOf(minDwellMs, maxGapMs, rereadWindowMs).all { it.isFinite() && it > 0 })
        require(minDwellMs >= 150.0 && maxGapMs <= 250.0)
    }
    private data class Point(val capture: Double, val y: Double)
    private data class Word(val line: Int, val start: Int, val end: Int)
    private val points = ArrayDeque<Point>()
    private val visits = ArrayDeque<ReadingSupportedVisit>()
    private var epoch: Pair<Long, String>? = null
    private var lastCapture = Double.NEGATIVE_INFINITY
    private var seenCapture = Double.NEGATIVE_INFINITY
    private var lastDelivery = Double.NEGATIVE_INFINITY
    private var lastNow = Double.NEGATIVE_INFINITY
    private var candidate: ReadingSupportedRowBand? = null
    private var candidateFirst = 0.0
    private var candidateAligned = false
    private var current: ReadingSupportedVisit? = null
    private var segment = 0
    private var serial = 0L
    private var word: Word? = null
    private var wordFirst = 0.0
    private var offeredWord: Word? = null

    /** Operational/missing boundaries reset duration, while an unresolved word remains suppressed. */
    fun interrupt() {
        points.clear(); visits.clear(); candidate = null; candidateAligned = false; current = null; word = null
        lastCapture = Double.NEGATIVE_INFINITY; segment++
    }

    fun reset() {
        interrupt(); epoch = null; offeredWord = null
        seenCapture = Double.NEGATIVE_INFINITY
        lastDelivery = Double.NEGATIVE_INFINITY; lastNow = Double.NEGATIVE_INFINITY
    }

    /** wordBounds, when provided, is the exact mapped word's screen-pixel rectangle. */
    fun update(c: ReadingAidContext, uncertainty: ReadingLocationUncertainty? = null,
        wordBounds: ScanpathRect? = null): ReadingLocationUpdate {
        if (!c.nowMs.isFinite() || c.nowMs < 0 || c.nowMs < lastNow) {
            reset(); return ReadingLocationUpdate(null, false)
        }
        lastNow = c.nowMs
        val nextEpoch = c.generation to c.geometrySnapshotId
        if (epoch != nextEpoch) { reset(); epoch = nextEpoch; lastNow = c.nowMs }
        val s = c.presentationSample
        val g = c.geometry
        if (!c.focusInputAllowed || !c.bookmarkInputAllowed || s == null || g == null ||
            g.snapshotId != c.geometrySnapshotId || s.geometrySnapshotId != c.geometrySnapshotId) {
            interrupt(); return ReadingLocationUpdate(null, false)
        }
        val t = s.captureMs; val d = s.deliveryMs
        if (t == null || d == null || !t.isFinite() || !d.isFinite() || t < 0 || t > d || d > c.nowMs || d < lastDelivery) {
            interrupt(); return ReadingLocationUpdate(null, false)
        }
        val x = s.processingX; val y = s.processingY
        val extractionOnly = s.exclusion in setOf("unassigned_line", "unassigned_word", "ambiguous_line", "ambiguous_word")
        val validPoint = x != null && y != null && x.isFinite() && y.isFinite() &&
            x in g.visibleRectScreen.left..g.visibleRectScreen.right &&
            y in g.visibleRectScreen.top..g.visibleRectScreen.bottom &&
            (s.exclusion == null || extractionOnly)
        if (!validPoint || t < seenCapture) {
            seenCapture = maxOf(seenCapture, t)
            lastDelivery = d; interrupt(); return ReadingLocationUpdate(null, false)
        }
        // Repeated delivery/capture callbacks cannot add dwell or erase a supported visit.
        if (t == seenCapture) { lastDelivery = d; return ReadingLocationUpdate(current, false) }
        seenCapture = t
        if (c.nowMs - t > 250.0 || c.nowMs - d > 250.0) {
            lastDelivery = d; interrupt(); return ReadingLocationUpdate(null, false)
        }
        if (lastCapture.isFinite() && t - lastCapture > maxGapMs) interrupt()
        lastCapture = t; lastDelivery = d
        if (g.lines.isEmpty() || !g.textOriginY.isFinite() || !g.textOriginX.isFinite() ||
            g.lines.any { row -> !listOf(row.top, row.bottom, row.left, row.right).all { it.isFinite() } ||
                row.top >= row.bottom || row.left > row.right }) {
            interrupt(); return ReadingLocationUpdate(null, false)
        }
        points.addLast(Point(t, y!! - g.textOriginY))
        while (points.size > 1 && t - points.first().capture > minDwellMs) points.removeFirst()
        val sortedY = points.map { it.y }.sorted()
        val medianY = if (sortedY.size % 2 == 1) sortedY[sortedY.size / 2]
            else (sortedY[sortedY.size / 2 - 1] + sortedY[sortedY.size / 2]) / 2
        val representative = g.lines.indices.minByOrNull {
            kotlin.math.abs((g.lines[it].top + g.lines[it].bottom) / 2 - medianY)
        }!!
        val rawLine = g.lines.indices.minByOrNull {
            kotlin.math.abs(g.textOriginY + (g.lines[it].top + g.lines[it].bottom) / 2 - y)
        }!!
        // A median crossing alone cannot count earlier samples as residence on the new row.
        val band = if (uncertainty == null) ReadingSupportedRowBand(
            maxOf(0, representative - 1), minOf(g.lines.lastIndex, representative + 1), representative,
            false, "neighboring_rows_uncertainty_unknown") else {
            val top = medianY - uncertainty.verticalRadiusPx
            val bottom = medianY + uncertainty.verticalRadiusPx
            val rows = g.lines.indices.filter {
                g.lines[it].top <= bottom && g.lines[it].bottom >= top
            }
            if (rows.isEmpty()) { interrupt(); return ReadingLocationUpdate(null, false) }
            ReadingSupportedRowBand(rows.first(), rows.last(), representative, true, uncertainty.provenance, top, bottom)
        }
        val aligned = rawLine == representative
        if (candidate?.let { sameBand(it, band) } != true || !aligned || !candidateAligned) {
            candidate = band; candidateFirst = t
        }
        candidateAligned = aligned
        var reread: ReadingSupportedReread? = null
        if (rawLine == representative && t - candidateFirst >= minDwellMs) {
            val previous = current
            if (previous == null || !sameBand(previous.band, band)) {
                current = ReadingSupportedVisit("location:${++serial}", segment, c.generation, c.geometrySnapshotId,
                    band, candidateFirst, t, d)
                // Re-establishing the same row after unsettled samples is not a
                // new movement. Replace its support, without bridging the gap.
                if (visits.lastOrNull()?.band?.representativeLine == band.representativeLine) visits.removeLast()
                visits.addLast(current!!)
                while (visits.size > 128 || (visits.size > 1 &&
                        t - visits.first().firstCaptureMs > rereadWindowMs)) visits.removeFirst()
                reread = detectReread()
            } else {
                current = previous.copy(band = band, lastCaptureMs = t, evidenceAvailableMs = d)
                if (visits.lastOrNull()?.id == previous.id) { visits.removeLast(); visits.addLast(current!!) }
            }
        } else {
            // Unsettled observations must not extend the previous visit through a transition.
            current = null
        }
        val sourceRow = s.line?.let { g.lines.getOrNull(it) }
        val exactWordEligible = s.eligible && s.exclusion == null && c.validLiveInput && sourceRow != null &&
            s.wordStartUtf16?.let { it >= sourceRow.startUtf16 } == true &&
            s.wordEndUtf16?.let { it <= sourceRow.endUtf16 } == true
        val residence = updateWord(s.line, s.wordStartUtf16, s.wordEndUtf16, rawLine, x!!, y,
            t, d, uncertainty, wordBounds, g.visibleRectScreen, current != null && exactWordEligible)
        val search = if (current != null && visits.lastOrNull()?.id == current?.id) detectSearch() else emptyList()
        return ReadingLocationUpdate(current, true, reread, residence, search)
    }

    private fun sameBand(a: ReadingSupportedRowBand, b: ReadingSupportedRowBand) =
        a.firstLine == b.firstLine && a.lastLine == b.lastLine && a.representativeLine == b.representativeLine &&
            a.measured == b.measured && a.provenance == b.provenance

    private fun detectReread(): ReadingSupportedReread? {
        if (visits.size < 5) return null
        val v = visits.toList()
        // First-pass forward rows may occur between A and B. After the broad
        // return, require at least two actual forward row changes within B.
        for (returned in v.lastIndex - 2 downTo 2) {
            val b = v[returned - 1].band
            val r = v[returned].band
            val tail = v.subList(returned, v.size)
            if (r.lastLine >= b.firstLine || v.last().band.representativeLine > b.representativeLine ||
                tail.zipWithNext().any { (from, to) ->
                    from.band.representativeLine >= to.band.representativeLine }) continue
            for (start in returned - 2 downTo 0) {
                val a = v[start].band
                if (a.lastLine >= b.firstLine || !a.overlaps(r)) continue
                val firstPass = v.subList(start, returned)
                if (firstPass.zipWithNext().any { (from, to) ->
                        from.band.representativeLine >= to.band.representativeLine }) continue
                val witness = v.subList(start, v.size)
                if (witness.last().lastCaptureMs - witness.first().firstCaptureMs > rereadWindowMs) continue
                return ReadingSupportedReread(witness, witness.zipWithNext().all { (from, to) ->
                    from.band.measurablyDistinctFrom(to.band) })
            }
        }
        return null
    }

    private fun detectSearch(): List<ReadingSupportedVisit> {
        if (visits.size < 3) return emptyList()
        val v = visits.toList().takeLast(3); val a = v[0].band; val b = v[1].band; val c = v[2].band
        return if (v.last().lastCaptureMs - v.first().firstCaptureMs <= 8000.0 &&
            a.representativeLine < b.representativeLine && c.representativeLine < b.representativeLine &&
            c.representativeLine >= a.representativeLine && a.measurablyDistinctFrom(b) &&
            b.measurablyDistinctFrom(c) && a.measurablyDistinctFrom(c)) v else emptyList()
    }

    private fun updateWord(line: Int?, start: Int?, end: Int?, rawLine: Int, x: Double, y: Double,
        t: Double, d: Double, uncertainty: ReadingLocationUncertainty?, bounds: ScanpathRect?,
        article: ScanpathRect, supported: Boolean
    ): ReadingSupportedWordResidence? {
        val mapped = if (line != null && line == rawLine && start != null && end != null && start >= 0 && end > start)
            Word(line, start, end) else null
        val validBounds = bounds != null &&
            listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all { it.isFinite() } &&
            bounds.left < bounds.right && bounds.top < bounds.bottom &&
            bounds.left >= article.left && bounds.right <= article.right &&
            bounds.top >= article.top && bounds.bottom <= article.bottom &&
            x in bounds.left..bounds.right && y in bounds.top..bounds.bottom
        val measuredInside = uncertainty != null && validBounds && bounds != null &&
            x - uncertainty.horizontalRadiusPx >= bounds.left && x + uncertainty.horizontalRadiusPx <= bounds.right &&
            y - uncertainty.verticalRadiusPx >= bounds.top && y + uncertainty.verticalRadiusPx <= bounds.bottom
        if (!supported || mapped == null || !(measuredInside ||
                (uncertainty == null && allowMappingOnlyWordResidence && validBounds))) {
            word = null; return null
        }
        if (word != mapped) {
            word = mapped; wordFirst = t
            if (offeredWord != mapped) offeredWord = null
        }
        if (t - wordFirst < 1000.0 || offeredWord == mapped) return null
        offeredWord = mapped
        return ReadingSupportedWordResidence(mapped.line, mapped.start, mapped.end, wordFirst, t, d,
            if (measuredInside) uncertainty!!.provenance else "mapping_only_unvalidated")
    }
}
