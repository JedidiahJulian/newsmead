package com.newsmead.research.study

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest

/** Synthetic encoding microbenchmark; the production writer is unchanged. */
@RunWith(AndroidJUnit4::class)
class StudyEncodingPerformanceDeviceTest {
    @Test(timeout = 15_000) fun compareReusableUtf8AdapterWithExactByteParity() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("newsmeadEncodingPerf") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val samples = JSONArray()
        val summary = JSONObject().put("schema", "newsmead_encoding_performance_v1")
            .put("device_model", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("measurement", "JSONObject.toString plus UTF-8 encoding, newline and memory SHA-256/counting sink writes; excludes fixture generation, parity checks, gzip, chunk rotation and disk I/O")
            .put("allocation_measurement", "ART process-wide art.gc.bytes-allocated delta, when available; includes concurrent process work and is not thread-local allocation attribution")
            .put("interpretation", "Exploratory encoding CPU/allocation comparison only; no hard speed gate, storage saving or whole-app gain assumed")
            .put("encoding_state", "One persistent UTF-8 OutputStreamWriter per block; flush before every buffer reset. Complete JSONObject text ends before newline; no surrogate may be carried across records")
            .put("retained_buffer_limit_bytes", 262_144).put("samples", samples)
        var passed = false
        try {
            val fixtures = fixtures()
            // Oversized parity-only record checks buffer shrinking without dominating timed study-shaped rows.
            val oversized = JSONObject().put("kind", "synthetic_oversized").put("text", "x".repeat(270_000))
            val parityRows = fixtures + oversized + fixtures
            val paritySink = ByteArrayOutputStream()
            val parityAdapter = Adapter()
            parityAdapter.use { adapter -> parityRows.forEachIndexed { index, record ->
                val expected = baseline(record)
                paritySink.reset(); adapter.encode(record, paritySink)
                val actual = paritySink.toByteArray()
                assertArrayEquals("Exact UTF-8 row parity $index", expected, actual)
                check(actual.last() == 10.toByte() && actual.count { it == 10.toByte() } == 1)
            } }
            summary.put("exact_per_record_byte_parity", true).put("parity_record_count", parityRows.size)
                .put("parity_includes_oversized_reset", true)
            val bytesPerSweep = fixtures.sumOf { baseline(it).size.toLong() }
            val sweeps = ((2_097_152 + bytesPerSweep - 1) / bytesPerSweep).toInt()
            summary.put("timed_fixture_count", fixtures.size).put("sweeps_per_block", sweeps)
                .put("records_per_block", sweeps * fixtures.size).put("bytes_per_block", sweeps * bytesPerSweep)
            val expected = runBlock("baseline", fixtures, sweeps)
            val warmCandidate = runBlock("candidate", fixtures, sweeps)
            checkParity(expected, warmCandidate)
            val order = List(2) { listOf("baseline", "candidate", "candidate", "baseline") }.flatten()
            summary.put("warmup_blocks_per_mode", 1).put("order", JSONArray(order))
            order.forEachIndexed { index, mode ->
                val sample = runBlock(mode, fixtures, sweeps)
                samples.put(sample.put("index", index).put("mode", mode))
                checkParity(expected, sample)
            }
            passed = true
        } catch (failure: Throwable) {
            summary.put("failure", failure.javaClass.simpleName)
            throw failure
        } finally {
            summary.put("passed", passed)
            File(context.cacheDir, "newsmead-encoding-performance-summary.json").writeText(summary.toString(2))
        }
    }

    private fun baseline(record: JSONObject) = (record.toString() + "\n").toByteArray(Charsets.UTF_8)

    private class ReusableBuffer : ByteArrayOutputStream(4096) {
        override fun reset() { super.reset(); if (buf.size > 262_144) buf = ByteArray(4096) }
    }

    private class Adapter : AutoCloseable {
        private val buffer = ReusableBuffer()
        private val writer = OutputStreamWriter(buffer, Charsets.UTF_8)
        fun encode(record: JSONObject, sink: OutputStream) {
            writer.write(record.toString()); writer.write(10); writer.flush()
            buffer.writeTo(sink)
            buffer.reset()
        }
        override fun close() { writer.close(); buffer.reset() }
    }

    private class CountingSink : OutputStream() {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        override fun write(value: Int) { digest.update(value.toByte()); bytes++ }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            digest.update(buffer, offset, length); bytes += length
        }
    }

    private fun allocatedBytes() = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()

    private fun runBlock(mode: String, records: List<JSONObject>, sweeps: Int): JSONObject {
        val sink = CountingSink()
        val adapter = if (mode == "candidate") Adapter() else null
        val allocatedBefore = allocatedBytes()
        val wallStart = SystemClock.elapsedRealtimeNanos()
        val cpuStart = Debug.threadCpuTimeNanos()
        try {
            repeat(sweeps) { records.forEach { record ->
                if (adapter == null) sink.write(baseline(record)) else adapter.encode(record, sink)
            } }
        } finally { adapter?.close() }
        val cpuNs = Debug.threadCpuTimeNanos() - cpuStart
        val wallNs = SystemClock.elapsedRealtimeNanos() - wallStart
        val allocatedAfter = allocatedBytes()
        val allocation = if (allocatedBefore != null && allocatedAfter != null && allocatedAfter >= allocatedBefore)
            allocatedAfter - allocatedBefore else JSONObject.NULL
        return JSONObject().put("thread_cpu_ms", cpuNs / 1e6).put("wall_ms", wallNs / 1e6)
            .put("process_allocated_bytes_delta", allocation).put("encoded_bytes", sink.bytes)
            .put("sha256", sink.digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
    }

    private fun checkParity(expected: JSONObject, actual: JSONObject) {
        check(expected.getLong("encoded_bytes") == actual.getLong("encoded_bytes")) { "Encoded byte count mismatch" }
        check(expected.getString("sha256") == actual.getString("sha256")) { "Encoded digest mismatch" }
        actual.put("byte_count_and_digest_match", true)
    }

    private fun fixtures(): List<JSONObject> {
        val text = "Synthetic study row: café 中文 😀 \u2028\u2029 \" \\ \b\t\n\u0000 orphan-high:\uD800 orphan-low:\uDC00. "
        return List(8) { index ->
            JSONObject().put("schema_version", StudyPackageWriter.SCHEMA).put("seq", index)
                .put("kind", arrayOf("observation", "layout_snapshot", "scanpath_output", "aid_decision")[index % 4])
                .put("source_elapsed_ms", if (index % 2 == 0) JSONObject.NULL else index * 83.25)
                .put("payload", JSONObject().put("synthetic", true).put("eligible", index % 2 == 0)
                    .put("missing", JSONObject.NULL).put("text", text.repeat(16 + index))
                    .put("numbers", JSONArray(listOf(0, -0.0, -0.0f, 1.25f, 1e-20, 1e20, Long.MAX_VALUE)))
                    .put("empty", JSONArray()).put("nested", JSONObject().put("reason", "synthetic-only")))
        }
    }
}
