package com.newsmead.gaze.scanpath

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/** Synthetic raw prefixes frozen by tools/gaze/build_live_scanpath_parity_fixtures.py. */
class ScanpathParityTest {
    @Test fun rawPrefixesMatchPythonFixationsAndP01ThroughP12() {
        val input = checkNotNull(javaClass.getResourceAsStream("/scanpath/live-reference.tsv"))
        var engine: ScanpathEngine? = null
        var name = ""
        var checkpoints = 0
        var cases = 0
        input.bufferedReader().useLines { lines -> lines.forEach { row ->
            if (row.startsWith("#") || row.isBlank()) return@forEach
            val fields = row.split('\t')
            when (fields[0]) {
                "case" -> {
                    name = fields[1]
                    cases++
                    engine = ScanpathEngine((0 until 12).map { WordRange(it * 5, it * 5 + 4) },
                        configuration(fields[2]))
                }
                "geometry" -> checkNotNull(engine).updateGeometry(ScanpathGeometry(
                    fields[2], fields[4].toDouble(), 0.0,
                    ScanpathRect(fields[4].toDouble(), 0.0, fields[4].toDouble() + 100.0, 100.0),
                    20.0, (0 until 4).map { ScanpathLine(it * 15, it * 15 + 15,
                        0.0, 100.0, it * 20.0, it * 20.0 + 20.0) },
                    when (fields[3]) { "ltr" -> ScanpathTextDirection.LTR
                        "rtl" -> ScanpathTextDirection.RTL else -> null },
                ), fields[1].toDouble())
                "sample" -> checkNotNull(engine).onSample(ScanpathSample(
                    number(fields[1]), number(fields[2]), number(fields[3]), number(fields[4]),
                    integer(fields[5]), integer(fields[6]), integer(fields[7]), fields[8],
                    fields[9].toBoolean(),
                ))
                "boundary" -> checkNotNull(engine).boundary(fields[1], number(fields[2]))
                "check" -> {
                    checkpoints++
                    val current = checkNotNull(engine)
                    assertEquals("$name fixation prefix $checkpoints", fields[1],
                        canonical(current.fixations.map(::fixation)))
                    assertEquals("$name pattern prefix $checkpoints", fields[2],
                        canonical(current.events.filter { it.patternId != "P13" }.map(::event)))
                }
                "endcase" -> engine = null
                else -> error("Unknown fixture action ${fields[0]}")
            }
        } }
        assertEquals(13, cases)
        assertEquals(183, checkpoints)
    }

    private fun configuration(name: String) = when (name) {
        "default" -> ScanpathConfig()
        "declared_ltr" -> ScanpathConfig(returnSweepStartFraction = 0.25,
            returnSweepEndFraction = 0.75, textDirection = ScanpathTextDirection.LTR)
        "configured" -> ScanpathConfig(returnSweepStartFraction = 0.25,
            returnSweepEndFraction = 0.75, wordResidenceMs = 150.0, lineResidenceMs = 250.0,
            progressionWindowMs = 300.0, progressionMinObservedFraction = 0.5,
            wordProgressionMaxUnitsPerS = 2.0, lineProgressionMaxUnitsPerS = 1.0,
            cycleWindowMs = 2000.0)
        else -> error("Unknown fixture configuration $name")
    }
    private fun number(value: String) = value.takeUnless { it == "-" }?.toDouble()
    private fun integer(value: String) = value.takeUnless { it == "-" }?.toInt()

    private fun fixation(f: ScanpathFixation) = mapOf(
        "index" to f.index, "segment" to f.segment, "start_ms" to f.startMs, "end_ms" to f.endMs,
        "duration_ms" to f.durationMs, "sample_count" to f.sampleCount, "x" to f.x, "y" to f.y,
        "dispersion_px" to f.dispersionPx, "word_start" to f.wordStartUtf16, "word_end" to f.wordEndUtf16,
        "word_sample_agreement" to f.wordSampleAgreement, "line" to f.line,
        "line_sample_agreement" to f.lineSampleAgreement, "left_censored" to f.leftCensored,
        "right_censored" to f.rightCensored, "geometry_snapshot_id" to f.geometrySnapshotId,
        "evidence_available_ms" to f.evidenceAvailableMs, "evidence_time_basis" to f.evidenceTimeBasis,
    )
    private fun event(e: ScanpathEvent): Map<String, Any?> {
        val metrics = e.metrics.toMutableMap()
        (metrics["episode_id"] as? String)?.let {
            metrics["episode_id"] = it.replace(Regex("^(word|line)-\\d+-"), "$1-episode-")
        }
        return mapOf("pattern_id" to e.patternId, "resolution" to e.resolution,
            "fixation_indices" to e.fixationIndices, "start_ms" to e.startMs, "end_ms" to e.endMs,
            "evidence_available_ms" to e.evidenceAvailableMs, "evidence_time_basis" to e.evidenceTimeBasis,
            "metrics" to metrics, "limitations" to e.limitations)
    }

    private fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> BigDecimal(value.toString()).setScale(8, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
        is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
        is Map<*, *> -> value.entries.sortedBy { it.key as String }.joinToString(",", "{", "}") {
            canonical(it.key) + ":" + canonical(it.value)
        }
        is Iterable<*> -> value.joinToString(",", "[", "]", transform = ::canonical)
        else -> error("Unsupported fixture value $value")
    }
}
