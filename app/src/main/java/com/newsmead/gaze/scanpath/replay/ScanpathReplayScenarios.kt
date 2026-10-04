package com.newsmead.gaze.scanpath.replay

import com.newsmead.gaze.scanpath.*
import kotlin.math.max
import kotlin.math.min

/** UTF-16 range and physical line index; bounds are in screen pixels. */
data class ReplayWord(val range: WordRange, val line: Int, val bounds: ScanpathRect)
data class ReplayPoint(val offsetMs: Double, val x: Double?, val y: Double?, val eligible: Boolean = true)
data class ReplayScenario(val id: String, val label: String, val description: String,
    val expectedPatternIds: Set<String>, val expectedAidTypes: Set<String>)

/** Scripted observations, not labelled engine inputs or evidence of human reading validity. */
object ScanpathReplayScenarios {
    val scenarios = listOf(
        ReplayScenario("navigation", "Forward and backward navigation", "Word advances, a jump and a return to the observed frontier.",
            setOf("P01", "P02", "P03", "P07", "P10"), setOf("CURRENT_LINE")),
        ReplayScenario("return_sweep", "Observed return sweep", "Right end of one LTR line to the left start of the next visible line.",
            setOf("P05", "P09"), setOf("CURRENT_LINE")),
        ReplayScenario("continuation_return", "Unexpected return", "Source line begins and ends at its right edge, then lands on an earlier line instead of the expected next line.",
            setOf("P08"), setOf("CURRENT_LINE", "NEXT_LINE_START")),
        ReplayScenario("continuation_skip", "Unexpected skip", "Source line begins and ends at its right edge, then lands beyond the expected next line.",
            setOf("P06"), setOf("CURRENT_LINE", "NEXT_LINE_START")),
        ReplayScenario("word_refixations", "Word refixations and residence", "Separated positions inside one wide word, followed by ongoing observed residence.",
            setOf("P04", "P12"), setOf("CURRENT_LINE", "WORD_MAGNIFIER")),
        ReplayScenario("line_cycle", "Line cycle and residence", "A–B–A–B lines, then word advances on B with recent residence and slow frontier progression; focus requires opt-in.",
            setOf("P08", "P10", "P11", "P12"), setOf("CURRENT_LINE", "FOCUS_REGION")),
        ReplayScenario("loss_recovery", "Tracking loss and recovery", "An established line, explicit missing observations, then fresh line evidence.",
            setOf("P13"), setOf("CURRENT_LINE"))
    )

    fun build(id: String, geometry: ScanpathGeometry, words: List<ReplayWord>): List<ReplayPoint> {
        require(scenarios.any { it.id == id }) { "Unknown replay scenario: $id" }
        fun impossible(detail: String): Nothing = throw IllegalArgumentException("Replay $id cannot use this geometry: $detail")
        val v = geometry.visibleRectScreen
        fun valid(r: ScanpathRect) = listOf(r.left, r.top, r.right, r.bottom).all { it.isFinite() } &&
            r.right > r.left && r.bottom > r.top
        if (!valid(v) || !geometry.lineHeightPx.isFinite() || geometry.lineHeightPx <= 0 ||
            !geometry.textOriginX.isFinite() || !geometry.textOriginY.isFinite()) impossible("invalid screen geometry")
        val threshold = 0.5 * geometry.lineHeightPx
        val sorted = words.sortedBy { it.range.startUtf16 }
        if (sorted.isEmpty() || sorted.zipWithNext().any { (a, b) -> a.range.endUtf16 > b.range.startUtf16 })
            impossible("missing or overlapping UTF-16 word ranges")
        val usable = sorted.mapNotNull { w ->
            val row = geometry.lines.getOrNull(w.line) ?: impossible("word has no physical line")
            if (!valid(w.bounds) || w.range.startUtf16 < row.startUtf16 ||
                w.range.endUtf16 > row.endUtf16 || w.range.endUtf16 <= w.range.startUtf16)
                impossible("invalid word range or screen rectangle")
            val clipped = ScanpathRect(max(w.bounds.left, max(v.left, geometry.textOriginX + row.left)),
                max(w.bounds.top, max(v.top, geometry.textOriginY + row.top)),
                min(w.bounds.right, min(v.right, geometry.textOriginX + row.right)),
                min(w.bounds.bottom, min(v.bottom, geometry.textOriginY + row.bottom)))
            if (valid(clipped)) w.copy(bounds = clipped) else null
        }
        fun center(w: ReplayWord) = (w.bounds.left + w.bounds.right) / 2 to (w.bounds.top + w.bounds.bottom) / 2
        fun separated(a: Pair<Double, Double>, b: Pair<Double, Double>) =
            kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second) > threshold
        val out = mutableListOf<ReplayPoint>()
        var time = 200.0
        fun hold(p: Pair<Double, Double>, spanMs: Int = 250) {
            for (dt in 0..spanMs step 50) out.add(ReplayPoint(time + dt, p.first, p.second))
            time += spanMs + 50
        }
        when (id) {
            "navigation" -> {
                val row = usable.groupBy { it.line }.values.flatMap { it.windowed(4) }.firstOrNull { group ->
                    group.zipWithNext().all { (a, b) ->
                        sorted.indexOfFirst { it.range == b.range } == sorted.indexOfFirst { it.range == a.range } + 1 &&
                            separated(center(a), center(b)) }
                } ?: impossible("need four consecutive visible words on one line with distinct fixation positions")
                listOf(0, 1, 3, 0, 3, 1).forEach { hold(center(row[it])) }
            }
            "return_sweep" -> {
                if (geometry.textDirection != ScanpathTextDirection.LTR) impossible("return sweep requires verified LTR geometry")
                fun fraction(w: ReplayWord): Double {
                    val row = geometry.lines[w.line]
                    return (center(w).first - geometry.textOriginX - row.left) / (row.right - row.left)
                }
                val pair = usable.firstNotNullOfOrNull { end ->
                    if (fraction(end) < 0.75) null else usable.firstOrNull { start ->
                        start.line == end.line + 1 && fraction(start) <= 0.25 &&
                            center(end).first > center(start).first && separated(center(end), center(start))
                    }?.let { end to it }
                } ?: impossible("need visible word centers at >=75% and <=25% on adjacent LTR lines")
                val final = usable.firstOrNull { it.line == pair.second.line && separated(center(it), center(pair.second)) }
                    ?: impossible("landing line needs another separated visible word to finalize its candidate")
                hold(center(pair.first)); hold(center(pair.second)); hold(center(final), 100)
            }
            "continuation_return", "continuation_skip" -> {
                if (geometry.textDirection != ScanpathTextDirection.LTR)
                    impossible("continuation mismatch requires verified LTR geometry")
                val visibleLines = usable.map { it.line }.distinct().sorted()
                if (visibleLines.size < 3) impossible("need three fully visible physical lines")
                fun fullyVisible(line: Int): Boolean {
                    val row = geometry.lines.getOrNull(line) ?: return false
                    return valid(ScanpathRect(geometry.textOriginX + row.left, geometry.textOriginY + row.top,
                        geometry.textOriginX + row.right, geometry.textOriginY + row.bottom)) &&
                        geometry.textOriginX + row.left >= v.left && geometry.textOriginX + row.right <= v.right &&
                        geometry.textOriginY + row.top >= v.top && geometry.textOriginY + row.bottom <= v.bottom
                }
                val trio = visibleLines.windowed(3).firstOrNull {
                    it[1] == it[0] + 1 && it[2] == it[1] + 1 && it.all(::fullyVisible)
                }
                    ?: impossible("need three fully visible physical lines")
                val (first, middle, last) = trio
                fun edge(line: Int, start: Boolean): ReplayWord? = usable.filter { it.line == line }
                    .firstOrNull { w ->
                        val row = geometry.lines[w.line]
                        val f = (center(w).first - geometry.textOriginX - row.left) / (row.right - row.left)
                        if (start) f <= 0.25 else f >= 0.75
                    }
                val sourceStart = edge(if (id == "continuation_return") middle else first, true)
                    ?: impossible("source line needs a visible word center at <=25%")
                val sourceEnd = edge(sourceStart.line, false)
                    ?: impossible("source line needs a visible word center at >=75%")
                val destinationLine = if (id == "continuation_return") first else last
                val destination = edge(destinationLine, true)
                    ?: impossible("destination line needs a visible word center at <=25%")
                val expected = if (id == "continuation_return") last else middle
                val expectedStart = edge(expected, true)
                    ?: impossible("expected line needs a visible word center at <=25%")
                hold(center(sourceStart)); hold(center(sourceEnd)); hold(center(destination)); hold(center(expectedStart), 100)
            }
            "word_refixations" -> {
                val w = usable.filter { it.bounds.right - it.bounds.left >= 3 * threshold }
                    .maxByOrNull { it.bounds.right - it.bounds.left }
                    ?: impossible("need a visible word at least three dispersion thresholds wide")
                val y = center(w).second
                val a = w.bounds.left + threshold * 0.1 to y
                val b = (w.bounds.left + w.bounds.right) / 2 to y
                val c = w.bounds.right - threshold * 0.1 to y
                hold(a); hold(b); hold(c); hold(a, 650)
            }
            "line_cycle" -> {
                val pair = usable.firstNotNullOfOrNull { a ->
                    val bs = usable.filter { it.line == a.line + 1 }
                    bs.firstNotNullOfOrNull { b -> bs.firstOrNull { c ->
                        separated(center(a), center(b)) && separated(center(b), center(c))
                    }?.let { listOf(a, b, it) } }
                } ?: impossible("need adjacent visible lines and two separated words on the second line")
                val (a, b, c) = pair.map(::center)
                hold(a); hold(b); hold(a); hold(b); hold(c, 1250); hold(b); hold(c, 100)
            }
            "loss_recovery" -> {
                val p = center(usable.firstOrNull() ?: impossible("need a visible word"))
                hold(p)
                repeat(8) { out.add(ReplayPoint(time, null, null, false)); time += 50 }
                hold(p, 400)
            }
        }
        // Refuse coordinates that an actual rectangle mapper could assign ambiguously.
        out.filter { it.eligible }.forEach { p ->
            val x = p.x ?: impossible("eligible coordinate has no x")
            val y = p.y ?: impossible("eligible coordinate has no y")
            val matches = sorted.filter { w -> x > w.bounds.left && x < w.bounds.right &&
                y > w.bounds.top && y < w.bounds.bottom }
            val lines = geometry.lines.indices.filter { i ->
                val row = geometry.lines[i]
                x - geometry.textOriginX in row.left..row.right &&
                    y - geometry.textOriginY in row.top..row.bottom
            }
            if (matches.size != 1 || lines.size != 1 || matches.single().line != lines.single())
                impossible("scripted coordinate has ambiguous word or physical line assignment")
        }
        return out.toList()
    }
}
