package com.newsmead.gaze.scanpath.replay

import com.newsmead.gaze.scanpath.*
import kotlin.math.max
import kotlin.math.min

/** UTF-16 range and physical line index; bounds are in screen pixels. */
data class ReplayWord(val range: WordRange, val line: Int, val bounds: ScanpathRect)
data class ReplayPoint(val offsetMs: Double, val x: Double?, val y: Double?, val eligible: Boolean = true,
    val sourceReason: String? = null)
data class ReplayScenario(val id: String, val label: String, val description: String,
    val expectedPatternIds: Set<String>, val expectedAidTypes: Set<String>)

/** Scripted observations, not labelled engine inputs or evidence of human reading validity. */
object ScanpathReplayScenarios {
    val scenarios = listOf(
        ReplayScenario("navigation", "Forward and backward navigation", "Word advances, a jump and a return to the observed frontier.",
            setOf("P01", "P02", "P03", "P07", "P10"), setOf("CURRENT_LINE")),
        ReplayScenario("return_sweep", "Observed return sweep", "Right end of one LTR line to the left start of the next visible line.",
            setOf("P05", "P09"), setOf("CURRENT_LINE")),
        ReplayScenario("continuation_return", "Unexpected return", "Beginning-to-end source progress, followed by an earlier-line landing.",
            setOf("P08"), setOf("CURRENT_LINE", "NEXT_LINE_START")),
        ReplayScenario("continuation_skip", "Unexpected skip", "Beginning-to-end source progress, followed by a nonadjacent later-line landing.",
            setOf("P06"), setOf("CURRENT_LINE", "NEXT_LINE_START")),
        ReplayScenario("bookmark_return", "Backward jump without departure", "Stable source line, earlier-line landing, then return. Current assistance should not create a bookmark from this alone; older profiles retain their comparison trigger.",
            setOf("P08"), setOf("CURRENT_LINE", "READING_BOOKMARK")),
        ReplayScenario("bookmark_skip", "Forward jump without departure", "Stable source line, nonadjacent later-line landing, then return. Current assistance should not create a bookmark from this alone; older profiles retain their comparison trigger.",
            setOf("P06"), setOf("CURRENT_LINE", "READING_BOOKMARK")),
        ReplayScenario("bookmark_lookaway", "Bookmark after looking away", "Stable reading line, sustained coordinates outside the article, then return to the saved line.",
            setOf("P13"), setOf("CURRENT_LINE", "READING_BOOKMARK")),
        ReplayScenario("bookmark_tracking_loss", "Bookmark when tracking disappears", "Stable reading line, no-face callbacks with missing coordinates for 900 ms, then return to the saved line.",
            setOf("P13"), setOf("CURRENT_LINE", "READING_BOOKMARK")),
        ReplayScenario("repeated_interruptions", "Repeated tracking interruptions", "Four distinct no-face gaps with missing coordinates, each followed by supported reading on the saved line; Controls select focus.",
            setOf("P13"), setOf("CURRENT_LINE", "READING_BOOKMARK", "FOCUS_REGION")),
        ReplayScenario("brief_interruptions", "Brief tracking interruptions", "Three no-face gaps shorter than 500 ms, each followed by fresh supported reading on the same line.",
            setOf("P13"), setOf("CURRENT_LINE")),
        ReplayScenario("interruption_place_search", "Return while searching for place", "Three no-face gaps followed by moving coordinates across visible lines; article presence activates focus before a final stable hold.",
            setOf("P13"), setOf("CURRENT_LINE", "FOCUS_REGION")),
        ReplayScenario("line_alternation", "Supported line alternation", "Four supported A–B–A–B visits over more than two seconds, ending during the ongoing fourth visit; Controls select focus.",
            setOf("P08"), setOf("CURRENT_LINE", "FOCUS_REGION")),
        ReplayScenario("broad_reread", "Broad passage rereading", "Stabilized A → later B → A → next row → continued forward rereading. Online prefix; no difficulty diagnosis.",
            setOf("P08"), setOf("CURRENT_LINE", "FOCUS_REGION")),
        ReplayScenario("word_refixations", "Word refixations and residence", "Separated positions inside one wide word, followed by ongoing observed residence.",
            setOf("P04", "P12"), setOf("CURRENT_LINE", "WORD_MAGNIFIER")),
        ReplayScenario("line_cycle", "Line cycle and residence", "A–B–A–B lines, then word advances on B with recent residence and slow frontier progression; Controls select focus.",
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
                    return row.endUtf16 > row.startUtf16 &&
                        valid(ScanpathRect(geometry.textOriginX + row.left, geometry.textOriginY + row.top,
                        geometry.textOriginX + row.right, geometry.textOriginY + row.bottom)) &&
                        geometry.textOriginX + row.left >= v.left && geometry.textOriginX + row.right <= v.right &&
                        geometry.textOriginY + row.top >= v.top && geometry.textOriginY + row.bottom <= v.bottom
                }
                val trios = visibleLines.windowed(3).filter {
                    it[1] == it[0] + 1 && it[2] == it[1] + 1 && it.all(::fullyVisible)
                }
                if (trios.isEmpty()) impossible("need three fully visible physical lines")
                fun fraction(w: ReplayWord): Double {
                    val row = geometry.lines[w.line]
                    return (center(w).first - geometry.textOriginX - row.left) / (row.right - row.left)
                }
                // Only source progress needs quarter-line endpoints. Stable landing and
                // expected-line arrival may use any real word center, including a one-word row.
                val trace = trios.firstNotNullOfOrNull { (first, middle, last) ->
                    val source = if (id == "continuation_return") middle else first
                    val destination = if (id == "continuation_return") first else last
                    val expected = if (id == "continuation_return") last else middle
                    val sourceWords = usable.filter { it.line == source }
                    sourceWords.filter { fraction(it) <= 0.25 }.firstNotNullOfOrNull { start ->
                        sourceWords.filter { fraction(it) >= 0.75 && separated(center(start), center(it)) }
                            .firstNotNullOfOrNull { end ->
                                usable.filter { it.line == destination && separated(center(end), center(it)) }
                                    .firstNotNullOfOrNull { landing ->
                                        usable.firstOrNull { it.line == expected && separated(center(landing), center(it)) }
                                            ?.let { arrival -> listOf(start, end, landing, arrival) }
                                    }
                            }
                    }
                } ?: impossible("need contiguous visible lines with separated source word centers at <=25% and >=75% and supported landing/arrival centers")
                trace.forEachIndexed { index, word -> hold(center(word), if (index == 3) 100 else 250) }
            }
            "bookmark_return", "bookmark_skip", "bookmark_lookaway", "bookmark_tracking_loss",
            "repeated_interruptions", "brief_interruptions" -> {
                if (geometry.textDirection != ScanpathTextDirection.LTR)
                    impossible("reading bookmark requires verified LTR geometry")
                val visibleRows = usable.groupBy { it.line }.filter { (line, _) ->
                    val row = geometry.lines[line]
                    geometry.textOriginX + row.left >= v.left && geometry.textOriginX + row.right <= v.right &&
                        geometry.textOriginY + row.top >= v.top && geometry.textOriginY + row.bottom <= v.bottom
                }.toSortedMap()
                if (id == "repeated_interruptions" || id == "brief_interruptions") {
                    val source = visibleRows.values.firstOrNull()?.firstOrNull()
                        ?: impossible("need one fully visible source line")
                    hold(center(source))
                    val gaps = if (id == "repeated_interruptions") listOf(650, 750, 850, 650)
                        else listOf(300, 300, 300)
                    gaps.forEach { span ->
                        for (dt in 0..span step 50)
                            out.add(ReplayPoint(time + dt, null, null, false, "no_face"))
                        time += span + 50
                        hold(center(source), 350)
                    }
                } else if (id == "bookmark_tracking_loss") {
                    val source = visibleRows.values.firstOrNull()?.firstOrNull()
                        ?: impossible("need one fully visible source line")
                    hold(center(source))
                    for (dt in 0..900 step 50)
                        out.add(ReplayPoint(time + dt, null, null, false, "no_face"))
                    time += 950
                    hold(center(source), 350)
                } else if (id == "bookmark_lookaway") {
                    val source = visibleRows.values.firstOrNull()?.firstOrNull()
                        ?: impossible("need one fully visible source line")
                    hold(center(source))
                    val outside = (v.left + v.right) / 2 to v.top - max(2.0, geometry.lineHeightPx)
                    for (dt in 0..650 step 50)
                        out.add(ReplayPoint(time + dt, outside.first, outside.second, false))
                    time += 700
                    hold(center(source), 350)
                } else {
                    val pair = visibleRows.keys.firstNotNullOfOrNull { source ->
                        val destination = visibleRows.keys.firstOrNull {
                            if (id == "bookmark_return") it < source else it > source + 1
                        }
                        destination?.let { visibleRows.getValue(source).first() to visibleRows.getValue(it).first() }
                    } ?: impossible("need fully visible source and non-normal destination lines")
                    hold(center(pair.first)); hold(center(pair.second)); hold(center(pair.first), 350)
                }
            }
            "interruption_place_search" -> {
                val visibleRows = usable.groupBy { it.line }.filter { (line, _) ->
                    val row = geometry.lines[line]
                    geometry.textOriginX + row.left >= v.left && geometry.textOriginX + row.right <= v.right &&
                        geometry.textOriginY + row.top >= v.top && geometry.textOriginY + row.bottom <= v.bottom
                }.toSortedMap()
                val path = visibleRows.values.firstNotNullOfOrNull { source ->
                    val search = visibleRows.values.filter { it.first().line != source.first().line }
                        .flatMap { it }.firstNotNullOfOrNull { a ->
                            visibleRows.values.flatMap { it }.firstOrNull { b ->
                                b.line != source.first().line && b.line != a.line && separated(center(a), center(b))
                            }?.let { a to it }
                        }
                    search?.let { Triple(source.first(), it.first, it.second) }
                } ?: impossible("need a fully visible source line and two separated physical search lines")
                hold(center(path.first))
                repeat(3) {
                    for (dt in 0..650 step 50)
                        out.add(ReplayPoint(time + dt, null, null, false, "no_face"))
                    time += 700
                    // Every adjacent point exceeds detector dispersion: no stable recovery prefix.
                    for (dt in 0..300 step 50) {
                        val p = center(if ((dt / 50) % 2 == 0) path.second else path.third)
                        out.add(ReplayPoint(time + dt, p.first, p.second))
                    }
                    time += 350
                }
                hold(center(path.first), 350)
            }
            "broad_reread" -> {
                val rows = usable.groupBy { it.line }.filter { (line, _) ->
                    val row = geometry.lines[line]
                    geometry.textOriginY + row.top >= v.top && geometry.textOriginY + row.bottom <= v.bottom
                }.toSortedMap()
                val group = rows.keys.toList().windowed(4).firstOrNull { it.last() - it.first() == 3 }
                    ?: impossible("need four consecutive fully visible rows for broad rereading")
                listOf(group[0], group[3], group[0], group[1], group[2]).forEach {
                    hold(center(rows.getValue(it).first()), 600)
                }
            }
            "line_alternation" -> {
                val visible = usable.filter { w ->
                    val row = geometry.lines[w.line]
                    geometry.textOriginX + row.left >= v.left && geometry.textOriginX + row.right <= v.right &&
                        geometry.textOriginY + row.top >= v.top && geometry.textOriginY + row.bottom <= v.bottom
                }
                val pair = visible.firstNotNullOfOrNull { a ->
                    visible.firstOrNull { b -> b.line == a.line + 1 && separated(center(a), center(b)) }
                        ?.let { a to it }
                } ?: impossible("need adjacent fully visible physical lines with separated word centers")
                hold(center(pair.first), 750)
                hold(center(pair.second), 750)
                hold(center(pair.first), 750)
                // Establish the fourth visit without finalizing it or flushing the engine.
                hold(center(pair.second), 200)
            }
            "word_refixations" -> {
                val w = usable.filter { it.bounds.right - it.bounds.left >= 3 * threshold }
                    .maxByOrNull { it.bounds.right - it.bounds.left }
                    ?: impossible("need a visible word at least three dispersion thresholds wide")
                val y = center(w).second
                val a = w.bounds.left + threshold * 0.1 to y
                val b = (w.bounds.left + w.bounds.right) / 2 to y
                val c = w.bounds.right - threshold * 0.1 to y
                hold(a); hold(b); hold(c); hold(a, 1450)
            }
            "line_cycle" -> {
                val pair = usable.firstNotNullOfOrNull { a ->
                    val bs = usable.filter { it.line == a.line + 1 }
                    bs.firstNotNullOfOrNull { b -> bs.firstOrNull { c ->
                        separated(center(a), center(b)) && separated(center(b), center(c))
                    }?.let { listOf(a, b, it) } }
                } ?: impossible("need adjacent visible lines and two separated words on the second line")
                val (a, b, c) = pair.map(::center)
                // Exceed the 1500 ms definition setting while the legacy comparison's
                // finalized line residence remains inside its separate 2000 ms cycle window.
                hold(a); hold(b); hold(a); hold(b); hold(c, 1750); hold(b); hold(c, 100)
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
