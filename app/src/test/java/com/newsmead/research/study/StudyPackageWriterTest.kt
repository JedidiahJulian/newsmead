package com.newsmead.research.study

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StudyPackageWriterTest {
    private fun identity() = JSONObject().apply {
        for (key in listOf("study_id", "participant_id", "installation_id", "run_id", "session_id", "clock_domain_id")) put(key, key)
    }
    private fun root(): File = Files.createTempDirectory("study-writer-test").toFile().also { it.deleteOnExit() }
    private fun manifest(w: StudyPackageWriter) = JSONObject(File(w.packageDirectory, "manifest.json").readText())
    private fun rows(w: StudyPackageWriter): List<JSONObject> {
        val chunks = manifest(w).getJSONArray("chunks")
        return (0 until chunks.length()).flatMap { i -> StudyChunkIO.open(File(w.packageDirectory, chunks.getJSONObject(i).getString("file")), chunks.getJSONObject(i))
            .bufferedReader().use { it.readLines().map { text -> JSONObject(text) } } }
    }

    @Test fun normalChunkHashesCountsAndTimes() {
        val w = StudyPackageWriter(root(), identity(), { 50.0 }, maxChunkBytes = 1000)
        for (i in 0..9) assertTrue(w.record("sample", JSONObject().put("x", i), 20.0 + i))
        w.finish("done", 60.0)
        assertTrue(w.awaitClosed(5000)); assertNull(w.failure)
        val m = manifest(w); assertTrue(m.getBoolean("content_complete"))
        val all = rows(w); assertEquals(13, all.size); assertEquals(13L, m.getLong("record_count"))
        assertEquals("package_start", all.first().getString("kind")); assertEquals("package_end", all.last().getString("kind"))
        assertEquals(20.0, all[1].getDouble("source_elapsed_ms"), 0.0)
        assertEquals(50.0, all[1].getDouble("recorded_elapsed_ms"), 0.0)
        val chunks = m.getJSONArray("chunks"); assertTrue(chunks.length() > 1)
        for (i in 0 until chunks.length()) {
            val c = chunks.getJSONObject(i); val bytes = File(w.packageDirectory, c.getString("file")).readBytes()
            assertEquals(bytes.size.toLong(), c.getLong("byte_count"))
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(hash, c.getString("sha256"))
            val decoded = StudyChunkIO.open(File(w.packageDirectory, c.getString("file")), c).use { it.readBytes() }
            assertEquals(StudyChunkIO.GZIP_ENCODING, c.getString("encoding"))
            assertEquals(decoded.size.toLong(), c.getLong("uncompressed_byte_count"))
            val records = decoded.toString(Charsets.UTF_8).trim().lines().map { JSONObject(it) }
            assertEquals(records.size.toLong(), c.getLong("record_count"))
            assertEquals(records.first().getLong("seq"), c.getLong("first_seq"))
            assertEquals(records.last().getLong("seq"), c.getLong("last_seq"))
        }
        assertEquals(all.last().getLong("seq"), w.health().getLong("flushed_through_seq"))
    }

    @Test fun copiedPayloadAndIdentityAreIsolated() {
        val gate = CountDownLatch(1); val entered = CountDownLatch(1); val id = identity()
        val w = StudyPackageWriter(root(), id, { 1.0 }, onState = { if (it == "recording") { entered.countDown(); gate.await(5, TimeUnit.SECONDS) } })
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val child = JSONObject().put("value", "before")
        val payload = JSONObject().put("nested", JSONArray().put(child))
        assertTrue(w.record("test", payload, Double.NaN))
        child.put("value", "after"); id.put("study_id", "changed")
        w.finish("done", 2.0); gate.countDown(); assertTrue(w.awaitClosed(5000))
        val row = rows(w)[1]
        assertEquals("before", row.getJSONObject("payload").getJSONArray("nested").getJSONObject(0).getString("value"))
        assertEquals("study_id", row.getString("study_id")); assertTrue(row.isNull("source_elapsed_ms"))
    }

    @Test fun overflowHasReservedTerminalAndLossRange() {
        val gate = CountDownLatch(1); val entered = CountDownLatch(1)
        val w = StudyPackageWriter(root(), identity(), { 1.0 }, queueCapacity = 1,
            onState = { if (it == "recording") { entered.countDown(); gate.await(5, TimeUnit.SECONDS) } })
        assertTrue(entered.await(5, TimeUnit.SECONDS)); assertTrue(w.record("one", JSONObject(), 10.0))
        assertFalse(w.record("two", JSONObject(), 20.0)); assertFalse(w.record("three", JSONObject(), 30.0))
        w.finish("done", 40.0); assertFalse(w.record("late", JSONObject()))
        gate.countDown(); assertTrue(w.awaitClosed(5000))
        val m = manifest(w); assertFalse(m.getBoolean("content_complete"))
        val loss = m.getJSONArray("losses").getJSONObject(0)
        assertEquals(2L, loss.getLong("count")); assertEquals(2L, loss.getLong("first_seq")); assertEquals(3L, loss.getLong("last_seq"))
        assertEquals("package_end", rows(w).last().getString("kind"))
        assertEquals(1L, w.health().getLong("rejected_after_finish"))
        assertFalse(w.record("after_closed", JSONObject()))
    }

    @Test fun ioFailureDoesNotPublishManifest() {
        val root = root(); val blocked = File(root, "blocked").apply { writeText("file") }
        val w = StudyPackageWriter(blocked, identity(), { 1.0 })
        assertTrue(w.awaitClosed(5000)); assertNotNull(w.failure)
        assertFalse(File(w.packageDirectory, "manifest.json").exists()); assertFalse(w.record("late", JSONObject()))
    }

    @Test fun recoveryRetainsCompletePrefixAndNeverInventsEnd() {
        val root = root(); val w = StudyPackageWriter(root, identity(), { 1.0 }, compressChunks = false)
        w.record("sample", JSONObject().put("x", 1), 1.0); w.finish("done", 2.0)
        assertTrue(w.awaitClosed(5000))
        val originalRows = rows(w)
        File(w.packageDirectory, "manifest.json").delete()
        w.packageDirectory.listFiles()!!.filter { it.name.endsWith(".jsonl") }.forEach { it.delete() }
        val partial = File(w.packageDirectory, "chunk_00001.jsonl.partial")
        val prefix = originalRows.take(2).joinToString("") { it.toString() + "\n" }
        partial.writeText(prefix + "{\"truncated\":")
        assertEquals(listOf(w.packageDirectory), StudyPackageWriter.recover(root))
        assertEquals(prefix + "{\"truncated\":", partial.readText())
        val m = manifest(w); assertEquals("recovered_incomplete", m.getString("status"))
        assertFalse(m.getBoolean("content_complete")); assertTrue(m.getBoolean("unknown_tail"))
        assertEquals(2L, m.getLong("record_count")); assertEquals("sample", rows(w).last().getString("kind"))
        assertTrue(StudyPackageWriter.recover(root).isEmpty())
    }

    @Test fun rejectedPayloadAndClockDoNotLeaveSequenceGaps() {
        val callerThread = Thread.currentThread()
        var throwClock = false
        val w = StudyPackageWriter(root(), identity(), {
            if (Thread.currentThread() === callerThread && throwClock) error("clock unavailable")
            1.0
        })
        try { w.record("invalid", JSONObject().put("unsupported", Any())); fail("Expected payload rejection") }
        catch (_: IllegalArgumentException) { }
        throwClock = true
        try { w.record("clock_failure", JSONObject()); fail("Expected clock rejection") }
        catch (_: IllegalStateException) { }
        throwClock = false
        assertTrue(w.record("valid", JSONObject(), 1.0)); w.finish("done", 2.0)
        assertTrue(w.awaitClosed(5000)); assertNull(w.failure)
        assertEquals(listOf(0L, 1L, 2L, 3L), rows(w).map { it.getLong("seq") })
        assertTrue(manifest(w).getBoolean("content_complete"))
    }

    @Test fun concurrentProducersAndFinishPreserveEveryAcceptedRecord() {
        val w = StudyPackageWriter(root(), identity(), { 1.0 }, queueCapacity = 4096)
        val ready = CountDownLatch(4); val race = CountDownLatch(1); val complete = CountDownLatch(4)
        val accepted = AtomicInteger(); val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        repeat(4) { producer ->
            Thread {
                try {
                    if (w.record("sample", JSONObject().put("producer", producer))) accepted.incrementAndGet()
                    ready.countDown(); race.await(5, TimeUnit.SECONDS)
                    repeat(200) { if (w.record("sample", JSONObject().put("producer", producer))) accepted.incrementAndGet() }
                } catch (e: Throwable) { errors.add(e) } finally { complete.countDown() }
            }.start()
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS)); race.countDown(); w.finish("race", 2.0)
        assertTrue(complete.await(5, TimeUnit.SECONDS)); assertTrue(w.awaitClosed(5000))
        assertTrue(errors.toString(), errors.isEmpty()); assertNull(w.failure)
        val all = rows(w)
        assertEquals(accepted.get(), all.count { it.getString("kind") == "sample" })
        assertEquals((0L until all.size.toLong()).toList(), all.map { it.getLong("seq") })
        assertEquals(0L, w.health().getLong("lost_records")); assertTrue(manifest(w).getBoolean("content_complete"))
    }

    @Test fun interruptedMultiChunkRecoveryStopsBeforeLaterChunks() {
        val root = root(); val w = StudyPackageWriter(root, identity(), { 1.0 }, maxChunkBytes = 1, compressChunks = false)
        repeat(5) { w.record("sample", JSONObject().put("index", it), it.toDouble()) }
        w.finish("done", 6.0); assertTrue(w.awaitClosed(5000)); assertNull(w.failure)
        val original = rows(w)
        File(w.packageDirectory, "manifest.json").delete()
        w.packageDirectory.listFiles()!!.filter { it.name.endsWith(".jsonl") }.forEach { it.delete() }
        fun encoded(records: List<JSONObject>) = records.joinToString("") { it.toString() + "\n" }
        File(w.packageDirectory, "chunk_00001.jsonl").writeText(encoded(original.take(2)))
        File(w.packageDirectory, "chunk_00002.jsonl.partial").writeText(encoded(original.drop(2).take(2)) + "{broken")
        File(w.packageDirectory, "chunk_00003.jsonl").writeText(encoded(original.drop(4)))
        assertEquals(listOf(w.packageDirectory), StudyPackageWriter.recover(root))
        val m = manifest(w); assertEquals(2, m.getJSONArray("chunks").length())
        assertEquals(4L, m.getLong("record_count")); assertTrue(m.getBoolean("unknown_tail"))
        assertEquals(listOf(0L, 1L, 2L, 3L), rows(w).map { it.getLong("seq") })
        assertTrue(File(w.packageDirectory, "chunk_00003.jsonl").exists())
    }

    @Test fun retainActualInteropPackage() {
        val target = System.getProperty("study.writer.interop") ?: return
        val w = StudyPackageWriter(File(target), identity(), { 2200.0 })
        assertTrue(w.record("session_start", JSONObject().put("phase", "reading").put("start_ms", 1000.0), 1000.0))
        assertTrue(w.record("gaze_observation", JSONObject().put("provider_epoch_id", "epoch-1").put("delivery_id", 1)
            .put("capture_ms", 1100.0).put("delivery_ms", 1110.0).put("raw_x_px", 10.0).put("raw_y_px", 20.0)
            .put("selected_x_px", 10.0).put("selected_y_px", 20.0).put("reason", "coordinate")
            .put("timeout_watchdog", false).put("coordinate_context", JSONObject.NULL)
            .put("context_epoch_id", "context-1").put("context_allowed", true), 1100.0))
        assertTrue(w.record("session_end", JSONObject().put("reason", "fixture").put("end_ms", 2000.0), 2000.0))
        w.finish("fixture", 2000.0)
        assertTrue(w.awaitClosed(5000)); assertNull(w.failure); assertTrue(manifest(w).getBoolean("content_complete"))
        println("INTEROP_PACKAGE=" + w.packageDirectory.absolutePath)
    }

    @Test fun recoveryRejectsLinkedInputsOutputsAndOriginalAliasesBeforeWriting() {
        val fixture = root()
        val sentinel = File(fixture, "sentinel").apply { writeText("untouched") }
        val probe = File(fixture, "link-probe")
        try { Files.createSymbolicLink(probe.toPath(), sentinel.toPath()) }
        catch (error: UnsupportedOperationException) { Assume.assumeNoException("Symbolic links unavailable", error) }
        catch (error: java.nio.file.FileSystemException) { Assume.assumeNoException("Symbolic links unavailable", error) }
        catch (error: SecurityException) { Assume.assumeNoException("Symbolic links unavailable", error) }
        Files.deleteIfExists(probe.toPath())
        val original = identity().put("schema_version", StudyPackageWriter.SCHEMA).put("seq", 0).toString() + "\n"
        for (attack in listOf("directory", "input", "recovered", "partial_manifest", "final_manifest", "original_alias")) {
            val storage = File(fixture, "storage-$attack").apply { mkdirs() }
            val external = File(fixture, "outside-$attack").apply { mkdirs() }
            val packageDirectory = File(storage, "package")
            if (attack == "directory") Files.createSymbolicLink(packageDirectory.toPath(), external.toPath()) else packageDirectory.mkdirs()
            val chunk = File(packageDirectory, "chunk_00001.jsonl.partial")
            if (attack == "input") Files.createSymbolicLink(chunk.toPath(), sentinel.toPath()) else chunk.writeText(original)
            val link = when (attack) {
                "recovered", "original_alias" -> File(packageDirectory, "recovered_00001.jsonl")
                "partial_manifest" -> File(packageDirectory, "manifest.json.partial")
                "final_manifest" -> File(packageDirectory, "manifest.json")
                else -> null
            }
            link?.let { Files.createSymbolicLink(it.toPath(), if (attack == "original_alias") chunk.toPath() else sentinel.toPath()) }
            try { StudyPackageWriter.recover(storage); fail("Expected path rejection: $attack") }
            catch (_: IllegalArgumentException) { }
            assertEquals(attack, "untouched", sentinel.readText())
            if (attack != "input") assertEquals(attack, original, chunk.readText())
            assertFalse(attack, File(external, "manifest.json").exists())
            if (attack != "final_manifest") assertFalse(attack, File(packageDirectory, "manifest.json").exists())
        }
    }
    @Test fun periodicSyncFlushLeavesRecoverableCompressedPrefixBeforeFinish() {
        val root = root(); val w = StudyPackageWriter(root, identity(), { 1.0 })
        try {
            assertTrue(w.record("sample", JSONObject().put("text", "repeat".repeat(1000)), 1.0))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            while (w.health().getLong("flushed_through_seq") < 1 && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue(w.health().getLong("flushed_through_seq") >= 1)
            val partial = w.packageDirectory.listFiles()!!.single { it.name.endsWith(".jsonl.gz.partial") }
            val recoveryRoot = root(); val copy = File(recoveryRoot, w.packageDirectory.name).apply { mkdirs() }
            File(copy, partial.name).writeBytes(partial.readBytes())
            assertEquals(listOf(copy), StudyPackageWriter.recover(recoveryRoot))
            val recovered = JSONObject(File(copy, "manifest.json").readText())
            assertEquals(2L, recovered.getLong("record_count")); assertTrue(recovered.getBoolean("unknown_tail"))
        } finally {
            w.finish("done", 2.0); assertTrue(w.awaitClosed(5000)); assertNull(w.failure)
        }
    }

}
