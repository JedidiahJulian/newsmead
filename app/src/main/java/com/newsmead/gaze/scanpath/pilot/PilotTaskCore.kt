package com.newsmead.gaze.scanpath.pilot

import com.newsmead.gaze.scanpath.*

/** Bounds are physical screen pixels; nullable word offsets are Android UTF-16. */
data class PilotTarget(val id: String, val line: Int, val wordStartUtf16: Int?,
    val wordEndUtf16: Int?, val bounds: ScanpathRect)
data class PilotTaskPlan(val id: String, val label: String, val kind: String,
    val targets: List<PilotTarget>, val referenceLimit: String)
data class PilotTaskDescriptor(val id: String, val label: String)

/** Instructions are target references, never physiological fixation or difficulty labels. */
object PilotTasks {
    val tasks = listOf(
        PilotTaskDescriptor("word_hold", "Hold one word"),
        PilotTaskDescriptor("line_hold", "Hold one line"),
        PilotTaskDescriptor("forward_words", "Forward adjacent words"),
        PilotTaskDescriptor("backward_words", "Backward adjacent words"),
        PilotTaskDescriptor("nonadjacent_words", "Nonadjacent words"),
        PilotTaskDescriptor("next_line", "End of line to next line start"),
        PilotTaskDescriptor("earlier_line", "Return to an earlier line"),
        PilotTaskDescriptor("multiline", "Advance across multiple lines"),
        PilotTaskDescriptor("line_cycle", "Alternate two lines"),
        PilotTaskDescriptor("repeated_word_hold", "Repeated cue on the same word"))

    fun build(id: String, geometry: ScanpathGeometry, words: List<PilotTarget>): PilotTaskPlan {
        val descriptor = tasks.singleOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown pilot task: $id")
        fun impossible(detail: String): Nothing = throw IllegalArgumentException("Pilot $id cannot use this geometry: $detail")
        fun valid(r: ScanpathRect) = listOf(r.left, r.top, r.right, r.bottom).all { it.isFinite() } &&
            r.right > r.left && r.bottom > r.top
        fun inside(a: ScanpathRect, b: ScanpathRect) = a.left >= b.left && a.right <= b.right &&
            a.top >= b.top && a.bottom <= b.bottom
        fun overlaps(a: ScanpathRect, b: ScanpathRect) = a.left < b.right && b.left < a.right &&
            a.top < b.bottom && b.top < a.bottom
        val visible = geometry.visibleRectScreen
        if (!valid(visible) || !geometry.textOriginX.isFinite() || !geometry.textOriginY.isFinite() ||
            !geometry.lineHeightPx.isFinite() || geometry.lineHeightPx <= 0) impossible("invalid screen geometry")
        val rows = geometry.lines.map { row -> ScanpathRect(geometry.textOriginX + row.left,
            geometry.textOriginY + row.top, geometry.textOriginX + row.right, geometry.textOriginY + row.bottom) }
        // TextView retains blank paragraph rows. Preserve their physical indices, but never target them.
        if (rows.any { r -> listOf(r.left, r.top, r.right, r.bottom).any { !it.isFinite() } ||
                r.right < r.left || r.bottom < r.top } ||
            geometry.lines.any { it.startUtf16 < 0 || it.endUtf16 < it.startUtf16 })
            impossible("invalid physical line")
        if (rows.indices.any { i -> (i + 1 until rows.size).any { j ->
                valid(rows[i]) && valid(rows[j]) && overlaps(rows[i], rows[j]) } })
            impossible("ambiguous physical lines")
        if (words.map { it.id }.distinct().size != words.size || words.any { it.id.isBlank() })
            impossible("word IDs must be unique and nonblank")
        val sorted = words.sortedBy { it.wordStartUtf16 }
        sorted.forEach { word ->
            val row = geometry.lines.getOrNull(word.line) ?: impossible("word has no physical line")
            val start = word.wordStartUtf16 ?: impossible("word has no UTF-16 start")
            val end = word.wordEndUtf16 ?: impossible("word has no UTF-16 end")
            if (start < row.startUtf16 || end > row.endUtf16 || end <= start || !valid(word.bounds) ||
                !inside(word.bounds, visible) || !inside(word.bounds, rows[word.line]))
                impossible("word must have a valid range and wholly visible screen bounds")
        }
        if (sorted.zipWithNext().any { (a, b) -> a.wordEndUtf16!! > b.wordStartUtf16!! } ||
            sorted.indices.any { i -> (i + 1 until sorted.size).any { j -> overlaps(sorted[i].bounds, sorted[j].bounds) } })
            impossible("overlapping or ambiguous words")
        // Largest full rows/words avoid tiny edge targets without modifying supplied bounds.
        val fullLines = rows.indices.filter { valid(rows[it]) &&
            geometry.lines[it].endUtf16 > geometry.lines[it].startUtf16 && inside(rows[it], visible) }
        fun lineTarget(i: Int) = PilotTarget("line:$i", i, null, null, rows[i])
        val grouped = sorted.groupBy { it.line }
        fun area(t: PilotTarget) = (t.bounds.right - t.bounds.left) * (t.bounds.bottom - t.bounds.top)
        fun robustWord() = sorted.maxByOrNull(::area) ?: impossible("need a wholly visible word")
        fun consecutive(n: Int): List<PilotTarget> = grouped.values.flatMap { it.windowed(n) }
            .maxByOrNull { group -> group.minOf(::area) } ?: impossible("need $n adjacent visible words on one line")
        val selected = when (id) {
            "word_hold" -> listOf(robustWord())
            "repeated_word_hold" -> List(3) { robustWord() }
            "line_hold" -> listOf(lineTarget(fullLines.maxByOrNull { rows[it].right - rows[it].left }
                ?: impossible("need a wholly visible full line")))
            "forward_words" -> consecutive(3)
            "backward_words" -> consecutive(3).reversed()
            "nonadjacent_words" -> consecutive(3).let { listOf(it.first(), it.last(), it.first()) }
            "next_line" -> {
                if (geometry.textDirection != ScanpathTextDirection.LTR) impossible("end/start task requires verified LTR")
                val pair = grouped.keys.sorted().firstNotNullOfOrNull { i ->
                    val a = grouped[i]?.lastOrNull()
                    val b = grouped[i + 1]?.firstOrNull()
                    if (a == null || b == null) null else {
                        fun fraction(w: PilotTarget): Double =
                            ((w.bounds.left + w.bounds.right) / 2 - rows[w.line].left) /
                                (rows[w.line].right - rows[w.line].left)
                        if (fraction(a) >= 0.75 && fraction(b) <= 0.25) listOf(a, b) else null
                    }
                } ?: impossible("need end/start words at 75%/25% on adjacent LTR lines")
                pair
            }
            "earlier_line", "multiline", "line_cycle" -> {
                val n = if (id == "multiline") 3 else 2
                val lines = fullLines.windowed(n).firstOrNull { group -> group.zipWithNext().all { (a, b) -> b == a + 1 } }
                    ?: impossible("need $n adjacent wholly visible full lines")
                when (id) {
                    "earlier_line" -> lines.reversed().map(::lineTarget)
                    "multiline" -> listOf(lines.first(), lines.last()).map(::lineTarget)
                    else -> listOf(lines[0], lines[1], lines[0], lines[1]).map(::lineTarget)
                }
            }
            else -> impossible("unsupported task")
        }
        val limit = if (id == "repeated_word_hold")
            "Negative control: repeated same-word cues cannot establish distinct physiological refixations (P04)."
        else "Prompted target agreement only; instructions do not establish physiological fixations, natural reading, or difficulty."
        return PilotTaskPlan(id, descriptor.label, if (selected.all { it.wordStartUtf16 == null }) "line" else "word", selected, limit)
    }
}

/**
 * intervalSamples: usable capture clocks in [onset,end); scorableSamples: after settling.
 * eligibleSamples additionally requires the sample eligible flag, finite selected processing XY and the initial snapshot.
 * Assignment/match/unknown counts use eligibleSamples; nullable rates retain empty denominators.
 * unknownWord/unknownLine mean eligible but unassigned at that resolution, not ineligible samples.
 * Clock-invalid samples cannot enter a target denominator and remain in the global exclusions.
 */
data class PilotTargetScore(val index: Int, val target: PilotTarget, val attempted: Boolean,
    val onsetMs: Double?, val endMs: Double?, val intervalSamples: Int, val scorableSamples: Int,
    val eligibleSamples: Int, val wordAssigned: Int, val lineAssigned: Int,
    val exactWord: Int, val exactLine: Int, val unknownWord: Int, val unknownLine: Int) {
    val exactWordGivenEligible: Double? get() = if (target.wordStartUtf16 == null || eligibleSamples == 0) null else exactWord.toDouble() / eligibleSamples
    val exactLineGivenEligible: Double? get() = if (eligibleSamples == 0) null else exactLine.toDouble() / eligibleSamples
}
/**
 * Descriptive global minimum insertion/deletion alignment (LCS) ignores both continuity breaks
 * and prompted capture windows; it does not establish temporal target recovery or event precision.
 * Substitutions count as one miss plus one extra. maxMatchesInOneSegment describes alignment
 * within one continuous resolution-specific segment, also without temporal matching.
 * When no reference exists, observed runs remain descriptive and score counts are unavailable
 * (stored as zero; consumers must check referenceAvailable before reporting numeric scores).
 */
data class PilotOrderScore(val expected: List<String>, val recovered: List<String>,
    val matches: Int, val misses: Int, val extras: Int, val maxMatchesInOneSegment: Int) {
    val referenceAvailable: Boolean get() = expected.isNotEmpty()
    val editDistance: Int get() = misses + extras
}
data class PilotScoreResult(val targets: List<PilotTargetScore>, val totalSamples: Int,
    val nullCapture: Int, val nonfiniteCapture: Int, val outOfOrderCapture: Int,
    val invalidCoordinates: Int, val ineligible: Int, val geometryMismatch: Int,
    val droppedAtCapacity: Int, val outsideIntervals: Int,
    val wordOrder: PilotOrderScore, val lineOrder: PilotOrderScore,
    val captureGapBreaks: Int, val wordRunSegments: List<List<String>>,
    val lineRunSegments: List<List<String>>) {
    val plannedTargets: Int get() = targets.size
    val attemptedTargets: Int get() = targets.count { it.attempted }
    /** Capacity loss makes observed denominators/order incomplete; never interpret as a full run. */
    val incomplete: Boolean get() = droppedAtCapacity > 0
}

/**
 * Uses sample eligibility and the selected processing stream, never engine acceptance. Onsets and captures share elapsed ms.
 * Retains at most maxSamples observations; capacity losses remain explicit and break runs.
 * Call record for all callbacks, including delayed ones, before finish. The onset log is causal,
 * but assignment is deferred so a delayed callback still belongs to its earlier half-open window.
 * Sequence references collapse adjacent identical targets: this instrument cannot count refixations.
 * Ordered selected-stream runs start after the first settling period only. Later per-target settling periods
 * exclude agreement samples but retain observed transitions descriptively. Global order alignment
 * has no temporal matching; only target rows represent actual drawn capture-time opportunities.
 * maxSequenceGapMs is an explicit engineering continuity setting, not a validated cutoff.
 */
class PilotScore(private val plan: PilotTaskPlan, private val geometrySnapshotId: String,
    val settlingMs: Double, val maxSamples: Int = 20000, val maxSequenceGapMs: Double = 125.0) {
    private data class Observation(val sample: ScanpathSample, val usableClock: Boolean)
    private val observations = ArrayList<Observation>()
    private val onsets = mutableListOf<Pair<Int, Double>>()
    private var lastCapture: Double? = null
    private var finished = false
    private var total = 0
    private var nullCapture = 0
    private var nonfiniteCapture = 0
    private var outOfOrder = 0
    private var invalidCoordinates = 0
    private var ineligible = 0
    private var geometryMismatch = 0
    private var dropped = 0

    init {
        require(settlingMs.isFinite() && settlingMs >= 0) { "settlingMs must be finite and nonnegative" }
        require(maxSequenceGapMs.isFinite() && maxSequenceGapMs > 0)
        require(maxSamples > 0 && plan.targets.isNotEmpty())
    }

    fun showStep(index: Int, onsetMs: Double) {
        check(!finished)
        require(index in plan.targets.indices && index == (onsets.lastOrNull()?.first?.plus(1) ?: 0)) {
            "Show each step once in planned order"
        }
        require(onsetMs.isFinite() && onsetMs >= 0 && (onsets.lastOrNull()?.second?.let { onsetMs > it } ?: true)) {
            "Display onset must be finite, nonnegative and strictly increasing"
        }
        onsets.add(index to onsetMs)
    }

    fun record(sample: ScanpathSample) {
        check(!finished)
        total++
        val t = sample.captureMs
        val usableClock = when {
            t == null -> { nullCapture++; false }
            !t.isFinite() -> { nonfiniteCapture++; false }
            t < 0 || lastCapture?.let { t <= it } == true -> { outOfOrder++; false }
            else -> { lastCapture = t; true }
        }
        if (sample.processingX?.isFinite() != true || sample.processingY?.isFinite() != true) invalidCoordinates++
        if (!sample.eligible) ineligible++
        if (sample.geometrySnapshotId != geometrySnapshotId) geometryMismatch++
        if (observations.size < maxSamples) observations.add(Observation(sample, usableClock)) else dropped++
    }

    fun finish(endMs: Double): PilotScoreResult {
        check(!finished)
        require(endMs.isFinite() && endMs >= 0 && (onsets.lastOrNull()?.second?.let { endMs >= it } ?: true))
        finished = true
        val counts = Array(plan.targets.size) { IntArray(9) }
        val wordRuns = mutableListOf<String>()
        val lineRuns = mutableListOf<String>()
        val wordSegments = mutableListOf<MutableList<String>>()
        val lineSegments = mutableListOf<MutableList<String>>()
        var previousWord: String? = null
        var previousLine: String? = null
        var previousCapture: Double? = null
        var gapBreaks = 0
        var outside = 0
        observations.forEach { observation ->
            val s = observation.sample
            val capture = s.captureMs
            if (observation.usableClock && capture != null) {
                if (previousCapture?.let { capture - it > maxSequenceGapMs } == true) {
                    previousWord = null; previousLine = null; gapBreaks++
                }
                previousCapture = capture
            }
            val window = if (observation.usableClock && capture != null) onsets.indexOfLast {
                capture >= it.second && capture < endMs
            } else -1
            if (window < 0) {
                if (observation.usableClock) outside++
                previousWord = null; previousLine = null
                return@forEach
            }
            val (index, onset) = onsets[window]
            val c = counts[index]
            c[0]++ // all clock-usable samples in the displayed interval
            val agreementScorable = capture!! - onset >= settlingMs
            if (agreementScorable) c[1]++ // capture-time scorable, before selected-stream eligibility exclusions
            if (!s.eligible || s.processingX?.isFinite() != true || s.processingY?.isFinite() != true ||
                s.geometrySnapshotId != geometrySnapshotId) {
                previousWord = null; previousLine = null
                return@forEach
            }
            val target = plan.targets[index]
            val word = wordKey(s.wordStartUtf16, s.wordEndUtf16)
            val line = s.line?.takeIf { it >= 0 }?.let { "line:$it" }
            if (agreementScorable) {
                c[2]++
                if (word == null) c[7]++ else {
                    c[3]++
                    if (s.wordStartUtf16 == target.wordStartUtf16 && s.wordEndUtf16 == target.wordEndUtf16) c[5]++
                }
                if (line == null) c[8]++ else {
                    c[4]++
                    if (s.line == target.line) c[6]++
                }
            }
            // Only the initial settling period excludes ordered samples from the selected processing stream.
            if (capture - onsets.first().second < settlingMs) return@forEach
            if (word != null) {
                if (word != previousWord) {
                    wordRuns.add(word)
                    if (previousWord == null) wordSegments.add(mutableListOf())
                    wordSegments.last().add(word)
                }
            }
            if (line != null) {
                if (line != previousLine) {
                    lineRuns.add(line)
                    if (previousLine == null) lineSegments.add(mutableListOf())
                    lineSegments.last().add(line)
                }
            }
            previousWord = word; previousLine = line
        }
        val scores = plan.targets.mapIndexed { i, target ->
            val shown = onsets.indexOfFirst { it.first == i }
            val c = counts[i]
            PilotTargetScore(i, target, shown >= 0, onsets.getOrNull(shown)?.second,
                if (shown < 0) null else onsets.getOrNull(shown + 1)?.second ?: endMs,
                c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8])
        }
        fun collapse(keys: List<String>) = keys.filterIndexed { i, key -> i == 0 || keys[i - 1] != key }
        val wordReference = collapse(plan.targets.mapNotNull { wordKey(it.wordStartUtf16, it.wordEndUtf16) })
        val lineReference = collapse(plan.targets.map { "line:${it.line}" })
        return PilotScoreResult(scores, total, nullCapture, nonfiniteCapture, outOfOrder,
            invalidCoordinates, ineligible, geometryMismatch, dropped, outside,
            align(wordReference, wordRuns, wordSegments), align(lineReference, lineRuns, lineSegments), gapBreaks,
            wordSegments.map { it.toList() }, lineSegments.map { it.toList() })
    }

    private fun wordKey(start: Int?, end: Int?): String? =
        if (start != null && end != null && start >= 0 && end > start) "word:$start:$end" else null

    private fun align(expected: List<String>, recovered: List<String>, segments: List<List<String>>): PilotOrderScore {
        if (expected.isEmpty()) return PilotOrderScore(expected, recovered.toList(), 0, 0, 0, 0)
        val matches = lcsLength(expected, recovered)
        return PilotOrderScore(expected, recovered.toList(), matches, expected.size - matches,
            recovered.size - matches, segments.maxOfOrNull { lcsLength(expected, it) } ?: 0)
    }

    private fun lcsLength(expected: List<String>, recovered: List<String>): Int {
        // One row of DP keeps memory bounded independently of the sample/run count.
        val row = IntArray(expected.size + 1)
        recovered.forEach { actual ->
            var diagonal = 0
            expected.forEachIndexed { i, wanted ->
                val old = row[i + 1]
                row[i + 1] = if (wanted == actual) diagonal + 1 else maxOf(row[i + 1], row[i])
                diagonal = old
            }
        }
        return row.last()
    }
}
