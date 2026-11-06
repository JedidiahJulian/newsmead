package com.newsmead.research.study

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.aid.ReadingAidContext
import com.newsmead.gaze.aid.ReadingAidPolicy
import com.newsmead.gaze.aid.ReadingAidPolicyConfig
import com.newsmead.gaze.aid.ReaderAidPreferences
import com.newsmead.gaze.scanpath.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Camera-free synthetic main-thread load. Measures transport/policy work, not rendered FPS or accuracy. */
@RunWith(AndroidJUnit4::class)
class StudyAuditPerformanceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val handler = Handler(Looper.getMainLooper())
    private fun nowNs() = SystemClock.elapsedRealtimeNanos()
    private fun nowMs() = nowNs() / 1e6
    private val geometry = ScanpathGeometry("synthetic-400-lines", 0.0, 0.0,
        ScanpathRect(0.0, 0.0, 1080.0, 1920.0), 40.0,
        List(400) { ScanpathLine(it * 80, (it + 1) * 80, 20.0, 1000.0, it * 40.0, (it + 1) * 40.0) },
        ScanpathTextDirection.LTR)

    private fun context(at: Double, tick: Long): ReadingAidContext {
        // Repeated visible-line visits exercise native support history without inventing camera data.
        val line = ((tick / 45) % 12).toInt()
        val x = 30.0 + (tick % 30) * 30.0
        val sample = ScanpathSample(at, at, x, line * 40.0 + 20.0, line, line * 80, line * 80 + 4,
            geometry.snapshotId, displayX = x, displayY = line * 40.0 + 20.0,
            coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
        return ReadingAidContext(1, geometry.snapshotId, 400, line, line * 80, line * 80 + 4,
            at, at, at, geometry = geometry, targetX = x, rawSample = sample)
    }

    @Test(timeout = 240_000) fun pacedMainThreadAuditWriterEndurance() {
        val root = File(instrumentation.targetContext.cacheDir, "study-audit-performance-${UUID.randomUUID()}")
        check(root.mkdirs())
        val identity = StudyJson.obj("study_id" to "synthetic-performance", "participant_id" to "synthetic-only",
            "installation_id" to "synthetic-only", "run_id" to UUID.randomUUID().toString(),
            "session_id" to UUID.randomUUID().toString(), "clock_domain_id" to "synthetic-device-elapsed")
        val coordinateContext = StudyJson.obj("participant_id" to "synthetic-only", "installation_id" to "synthetic-only",
            "calibration_id" to "synthetic-calibration", "model_id" to "synthetic-model",
            "acquisition_id" to "synthetic-acquisition", "presentation_id" to "synthetic-400-lines",
            "clock_domain_id" to identity.getString("clock_domain_id"), "screen_width_px" to 1080,
            "screen_height_px" to 1920, "rotation_degrees" to 0, "coordinate_stream" to "calibrated_raw_v1")
        val writer = StudyPackageWriter(root, identity, ::nowMs)
        val encoder = StudyAuditEncoder()
        val sink: (String, JSONObject, Double) -> Boolean = { kind, payload, at -> writer.record(kind, payload, at) }
        val complete = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val timings = Array(3) { mutableListOf<Pair<Double, Double>>() }
        val heaps = JSONArray()
        var ticks = 0L; var missedSlots = 0L; var nextHeapMs = 0.0
        var startedNs = 0L; var startedUptime = 0L; var slot = 0L
        var micro = JSONObject()
        lateinit var policy: ReadingAidPolicy
        lateinit var callback: Runnable
        callback = Runnable {
            try {
                val begin = nowNs()
                val elapsed = (begin - startedNs) / 1e6
                if (elapsed >= DURATION_MS) { complete.countDown(); return@Runnable }
                val scheduledNs = startedNs + slot * PERIOD_NS
                val at = begin / 1e6
                val c = context(at, ticks)
                val sample = requireNotNull(c.rawSample)
                check(writer.record("gaze_observation", StudyJson.obj("provider_epoch_id" to "synthetic-epoch",
                    "delivery_id" to ticks + 1, "capture_ms" to at, "delivery_ms" to at,
                    "raw_x_px" to sample.rawX, "raw_y_px" to sample.rawY,
                    "selected_x_px" to sample.displayX, "selected_y_px" to sample.displayY,
                    "reason" to "coordinate", "timeout_watchdog" to false, "synthetic" to true,
                    "coordinate_context" to coordinateContext, "context_epoch_id" to "synthetic-context",
                    "context_allowed" to true, "delivery_elapsed_ns" to begin), at))
                check(writer.record("scanpath_input", StudyJson.obj("generation" to 1, "sample" to c.rawSample), at))
                check(writer.record("rdf_input", StudyJson.obj("generation" to 1, "sample" to c.rawSample), at))
                policy.onObservation(null, c).forEach { decision ->
                    check(writer.record("aid_decision", StudyJson.obj("decision" to decision), decision.nowMs))
                }
                if (elapsed >= nextHeapMs) {
                    val runtime = Runtime.getRuntime()
                    heaps.put(StudyJson.obj("elapsed_ms" to elapsed, "used_java_heap_bytes" to
                        (runtime.totalMemory() - runtime.freeMemory()), "writer_health" to writer.health()))
                    nextHeapMs += 5000.0
                }
                val end = nowNs()
                timings[(elapsed / 60000).toInt().coerceAtMost(2)].add(
                    ((begin - scheduledNs) / 1e6) to ((end - begin) / 1e6))
                ticks++
                // Late callbacks retain actual source time. Missed scheduling slots are counted, never replayed.
                val next = maxOf(slot + 1, (end - startedNs) / PERIOD_NS + 1)
                missedSlots += next - slot - 1; slot = next
                handler.postAtTime(callback, startedUptime + (slot * PERIOD_NS + 999_999) / 1_000_000)
            } catch (error: Throwable) { failure.set(error); complete.countDown() }
        }
        try {
            instrumentation.runOnMainSync {
                micro = microbenchmark()
                policy = ReadingAidPolicy(ReadingAidPolicyConfig.evidenceAlignedPrototype(), ReaderAidPreferences(),
                    onAudit = { kind, at, detail -> encoder.record(kind, at, detail, sink) })
                startedNs = nowNs(); startedUptime = SystemClock.uptimeMillis()
                check(writer.record("session_start", StudyJson.obj("start_ms" to startedNs / 1e6,
                    "phase" to "reading", "collector_version" to "study_collection_v1.1",
                    "metadata" to mapOf("fixture" to "camera_free_audit_endurance_v1")), startedNs / 1e6))
                handler.post(callback)
            }
            val waitDeadline = nowNs() + 185_000_000_000L
            while (complete.count > 0 && nowNs() < waitDeadline) complete.await(5, TimeUnit.SECONDS)
            assertEquals("Main-thread fixture did not terminate", 0L, complete.count)
        } finally {
            handler.removeCallbacks(callback)
            val ended = nowMs()
            writer.record("session_end", StudyJson.obj("end_ms" to ended, "reason" to "synthetic_performance_complete"), ended)
            writer.finish("synthetic_performance_complete", ended, failure.get() != null || complete.count != 0L)
        }
        assertTrue("Writer close exceeded bound", writer.awaitClosed(5000))
        val result = StudyJson.obj("fixture" to "camera_free_audit_endurance_v1",
            "package_path" to writer.packageDirectory.absolutePath, "requested_duration_ms" to DURATION_MS,
            "target_callback_hz" to 30, "actual_ticks" to ticks, "missed_schedule_slots" to missedSlots,
            "duration_ms" to ((nowNs() - startedNs) / 1e6), "heap_samples" to heaps,
            "writer_health" to writer.health(), "microbenchmark" to micro,
            "failure" to failure.get()?.toString(), "scientific_validation" to false,
            "scope" to "synthetic policy/serialization/writer main-thread work; no camera, inference, view rendering or participant data")
        val periods = JSONArray()
        timings.forEachIndexed { index, values -> periods.put(StudyJson.obj("start_ms" to index * 60000,
            "end_ms" to (index + 1) * 60000, "callbacks" to values.size,
            "callback_lateness_ms" to stats(values.map { it.first }), "work_ms" to stats(values.map { it.second }))) }
        result.put("periods", periods)
        val resultFile = File(root, "result.json")
        resultFile.writeText(result.toString(2))
        Log.i("StudyAuditPerformance", "RESULT=${resultFile.absolutePath} PACKAGE=${writer.packageDirectory.absolutePath}")
        failure.get()?.let { throw AssertionError("Synthetic callback failed", it) }
        assertNull(writer.failure)
        result.put("integrity", verify(writer.packageDirectory, ticks))
        resultFile.writeText(result.toString(2))
        assertEquals(0L, writer.health().getLong("lost_records"))
    }

    private fun microbenchmark(): JSONObject {
        val at = nowMs(); val detail = mapOf("input_context" to context(at, 0), "components" to
            listOf(mapOf("component" to "focus", "status" to "evaluated", "active_action_ids" to emptyList<String>())))
        fun legacy(sink: (String, JSONObject, Double) -> Boolean) {
            listOf("support_revision", "aid_evaluation").forEach { sink(it, StudyJson.value(detail) as JSONObject, at) }
        }
        fun projected(encoder: StudyAuditEncoder, sink: (String, JSONObject, Double) -> Boolean) {
            listOf("support_revision", "aid_evaluation").forEach { encoder.record(it, at, detail, sink) }
        }
        val warm = StudyAuditEncoder()
        val discard: (String, JSONObject, Double) -> Boolean = { _, json, _ -> json.toString().toByteArray(Charsets.UTF_8); true }
        repeat(10) { legacy(discard); projected(warm, discard) }
        var oldBytes = 0L; var newBytes = 0L; var oldNs = 0L; var newNs = 0L
        val encoder = StudyAuditEncoder()
        val oldSink: (String, JSONObject, Double) -> Boolean = { _, json, _ -> oldBytes += json.toString().toByteArray(Charsets.UTF_8).size; true }
        val newSink: (String, JSONObject, Double) -> Boolean = { _, json, _ -> newBytes += json.toString().toByteArray(Charsets.UTF_8).size; true }
        repeat(100) { index ->
            fun old() { val begin = nowNs(); legacy(oldSink); oldNs += nowNs() - begin }
            fun new() { val begin = nowNs(); projected(encoder, newSink); newNs += nowNs() - begin }
            if (index % 2 == 0) { old(); new() } else { new(); old() }
        }
        return StudyJson.obj("audit_pairs" to 100, "warmup_pairs_per_path" to 10,
            "legacy_ms" to oldNs / 1e6, "projected_ms" to newNs / 1e6,
            "legacy_payload_utf8_bytes" to oldBytes, "projected_payload_utf8_bytes" to newBytes,
            "projected_includes_initial_layout" to true,
            "scope" to "main-thread encode plus JSON UTF-8 serialization; shared warmed reflection cache; excludes writer envelope/deep-copy/disk")
    }

    private fun stats(values: List<Double>): JSONObject {
        if (values.isEmpty()) return StudyJson.obj("count" to 0)
        val sorted = values.sorted()
        return StudyJson.obj("count" to values.size, "mean" to values.average(),
            "p50" to sorted[(sorted.size - 1) / 2], "p95" to sorted[((sorted.size - 1) * .95).toInt()],
            "max" to sorted.last())
    }

    private fun verify(directory: File, ticks: Long): JSONObject {
        val manifest = JSONObject(File(directory, "manifest.json").readText())
        assertTrue(manifest.getBoolean("content_complete"))
        val layouts = mutableSetOf<String>(); var rows = 0L; var refs = 0L; var observations = 0L
        val kinds = mutableMapOf<String, Long>()
        val chunks = manifest.getJSONArray("chunks")
        repeat(chunks.length()) { index ->
            val chunk = chunks.getJSONObject(index); val file = File(directory, chunk.getString("file"))
            assertEquals(chunk.getLong("byte_count"), file.length())
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) {
                val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
            } }
            assertEquals(chunk.getString("sha256"), digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
            var chunkRows = 0L
            StudyChunkIO.open(file, chunk).bufferedReader().useLines { lines -> lines.forEach { text ->
                val row = JSONObject(text); assertEquals(rows, row.getLong("seq")); rows++; chunkRows++
                val kind = row.getString("kind"); val payload = row.getJSONObject("payload")
                kinds[kind] = (kinds[kind] ?: 0) + 1
                if (kind == "layout_snapshot" && payload.optString("producer") == StudyAuditEncoder.PRODUCER) {
                    layouts.add("${payload.getLong("generation")}/${payload.getJSONObject("geometry").getString("snapshot_id")}/${payload.getLong("layout_revision")}")
                }
                payload.optJSONObject("input_context")?.optJSONObject("geometry_ref")?.let { ref ->
                    assertEquals(StudyAuditEncoder.PRODUCER, ref.getString("producer"))
                    assertTrue("Unresolved earlier layout", layouts.contains("${ref.getLong("generation")}/${ref.getString("geometry_snapshot_id")}/${ref.getLong("layout_revision")}")); refs++
                    assertEquals(payload.getJSONObject("input_context").getDouble("now_ms"), row.getDouble("source_elapsed_ms"), 0.0)
                }
                if (kind == "gaze_observation") {
                    observations++; assertEquals(observations, payload.getLong("delivery_id"))
                    assertEquals(payload.getDouble("capture_ms"), row.getDouble("source_elapsed_ms"), 0.0)
                }
            }
            }
            assertEquals(chunk.getLong("record_count"), chunkRows)
        }
        assertEquals(manifest.getLong("record_count"), rows); assertEquals(ticks, observations)
        assertEquals(ticks, kinds["scanpath_input"] ?: 0L); assertEquals(ticks, kinds["rdf_input"] ?: 0L)
        assertEquals(ticks, kinds["aid_evaluation"] ?: 0L); assertTrue((kinds["support_revision"] ?: 0) > 0)
        assertTrue(refs > 0); assertEquals(1, layouts.size)
        return StudyJson.obj("records" to rows, "resolved_refs" to refs, "layout_keys" to layouts.toList(),
            "kind_counts" to kinds, "hashes_counts_sequence_sources_pass" to true)
    }

    companion object {
        private const val DURATION_MS = 180_000.0
        private const val PERIOD_NS = 1_000_000_000L / 30
    }
}
