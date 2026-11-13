package com.newsmead.research.study

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Camera-free compression comparison. Synthetic bytes exist only in memory.
 * Measures gzip CPU/wall/storage tradeoffs, not whole-app throughput or disk durability.
 */
@RunWith(AndroidJUnit4::class)
class StudyCompressionPerformanceDeviceTest {
    @Test(timeout = 10_000) fun compareDefaultAndBestSpeedWithExactDecodedParity() {
        val payload = syntheticJsonl()
        // Independent direct level-one stream checks that the production helper uses level one.
        val directLevelOne = compress(payload, "direct-level-1")
        assertArrayEquals(payload, decode(directLevelOne))
        listOf("default-level-6", "production-level-1").forEach { mode ->
            val warmup = compress(payload, mode)
            assertArrayEquals("Warmup decoded bytes: $mode", payload, decode(warmup))
            if (mode == "production-level-1") assertArrayEquals(directLevelOne, warmup)
        }
        val samples = JSONArray()
        val order = listOf("default-level-6", "production-level-1", "production-level-1", "default-level-6")
        order.forEachIndexed { index, mode ->
            val wallStart = SystemClock.elapsedRealtimeNanos()
            val cpuStart = Debug.threadCpuTimeNanos()
            val compressed = compress(payload, mode)
            val cpuNs = Debug.threadCpuTimeNanos() - cpuStart
            val wallNs = SystemClock.elapsedRealtimeNanos() - wallStart
            assertArrayEquals("Sample $index exact decoded bytes", payload, decode(compressed))
            if (mode == "production-level-1") assertArrayEquals("Production/direct level-one compressed parity", directLevelOne, compressed)
            samples.put(JSONObject().put("index", index).put("mode", mode)
                .put("thread_cpu_ms", cpuNs / 1e6).put("wall_ms", wallNs / 1e6)
                .put("uncompressed_bytes", payload.size).put("compressed_bytes", compressed.size)
                .put("exact_decoded_byte_equality", true)
                .put("production_matches_direct_level_one", if (mode == "production-level-1") true else JSONObject.NULL))
        }
        val summary = JSONObject().put("schema", "newsmead_compression_performance_v1")
            .put("device_model", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("payload", "Deterministic synthetic study-shaped JSONL; seed 20261112; no participant data")
            .put("measurement", "Compression, close/trailer and memory output copy only; excludes payload generation, decompression, JSON snapshotting and disk I/O")
            .put("gzip_buffer_bytes", 8192).put("sync_flush", true).put("flush_every_input_bytes", FLUSH_BYTES)
            .put("warmup_samples_per_mode", 1).put("order", JSONArray(order)).put("samples", samples)
            .put("interpretation", "Descriptive within-device CPU/wall/storage tradeoff; no speed assertion or whole-app improvement claim")
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "newsmead-compression-performance-summary.json").writeText(summary.toString(2))
    }

    private fun compress(payload: ByteArray, mode: String): ByteArray {
        val stored = ByteArrayOutputStream(payload.size / 2)
        val stream = when (mode) {
            "production-level-1" -> StudyBestSpeedGzipOutputStream(stored)
            "default-level-6" -> GZIPOutputStream(stored, 8192, true)
            "direct-level-1" -> object : GZIPOutputStream(stored, 8192, true) {
                init { def.setLevel(Deflater.BEST_SPEED) }
            }
            else -> error("Unknown diagnostic mode")
        }
        stream.use { output ->
            var offset = 0; var nextFlush = FLUSH_BYTES
            while (offset < payload.size) {
                // Small writes resemble individual JSONL records; no compression bypass.
                val count = minOf(1536, payload.size - offset)
                output.write(payload, offset, count); offset += count
                if (offset >= nextFlush) { output.flush(); nextFlush += FLUSH_BYTES }
            }
            output.flush()
        }
        return stored.toByteArray()
    }

    private fun decode(bytes: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun syntheticJsonl(): ByteArray {
        val random = Random(20261112L)
        val data = ByteArrayOutputStream(2_100_000)
        var seq = 0
        while (data.size() < 2_000_000) {
            val values = JSONArray(List(64) { random.nextInt(2_000_001) / 1000.0 })
            val kind = arrayOf("observation", "layout_snapshot", "rdf_tick", "scanpath_output")[seq % 4]
            val record = JSONObject().put("schema_version", StudyPackageWriter.SCHEMA)
                .put("package_id", "synthetic-compression-only").put("seq", seq)
                .put("kind", kind).put("recorded_elapsed_ms", seq * 83.25)
                .put("source_elapsed_ms", seq * 83.25 - 31.5)
                .put("payload", JSONObject().put("synthetic", true).put("reason", "synthetic-only")
                    .put("generation", seq / 40).put("values", values)
                    .put("text", "Synthetic article geometry and timing record. Caf\u00e9 \u65b0\u805e \ud83d\udcdd"))
            data.write((record.toString() + "\n").toByteArray(Charsets.UTF_8)); seq++
        }
        return data.toByteArray()
    }

    companion object { private const val FLUSH_BYTES = 262_144 }
}
