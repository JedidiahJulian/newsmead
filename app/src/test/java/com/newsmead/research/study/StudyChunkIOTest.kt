package com.newsmead.research.study

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class StudyChunkIOTest {
    private fun root() = Files.createTempDirectory("study-chunk-").toFile().also { it.deleteOnExit() }
    private fun gzip(file: File, bytes: ByteArray) { GZIPOutputStream(file.outputStream()).use { it.write(bytes) } }
    private fun metadata(file: File, decoded: Long) = JSONObject().put("file", file.name).put("byte_count", file.length())
        .put("encoding", StudyChunkIO.GZIP_ENCODING).put("uncompressed_byte_count", decoded)
    private fun reject(block: () -> Unit) { try { block(); fail("Expected invalid chunk rejection") } catch (_: java.io.IOException) { } catch (_: IllegalArgumentException) { } }
    @Test fun roundtripPreservesExactUtf8BytesAndLegacyPlain() {
        val bytes = "{\"text\":\"é 😀\"}\n{\"n\":2}\n".toByteArray(Charsets.UTF_8)
        val file = File(root(), "chunk_00001.jsonl.gz"); gzip(file, bytes)
        assertArrayEquals(bytes, StudyChunkIO.open(file, metadata(file, bytes.size.toLong())).use { it.readBytes() })
        val plain = File(file.parentFile, "chunk_00002.jsonl").apply { writeBytes(bytes) }
        assertArrayEquals(bytes, StudyChunkIO.open(plain, JSONObject().put("byte_count", plain.length())).use { it.readBytes() })
    }
    @Test fun strictFinishedStreamsRejectCrcTruncationEncodingAndSize() {
        val file = File(root(), "chunk_00001.jsonl.gz"); val bytes = "{\"x\":1}\n".repeat(100).toByteArray()
        gzip(file, bytes); val original = file.readBytes(); val meta = metadata(file, bytes.size.toLong())
        reject { StudyChunkIO.open(file, JSONObject(meta.toString()).put("encoding", "other")).close() }
        reject { StudyChunkIO.open(file, JSONObject(meta.toString()).apply { remove("encoding") }).close() }
        reject { StudyChunkIO.open(file, JSONObject(meta.toString()).put("uncompressed_byte_count", "800")).close() }
        reject { StudyChunkIO.open(file, JSONObject(meta.toString()).put("uncompressed_byte_count", StudyChunkIO.MAX_UNCOMPRESSED_BYTES + 1)).close() }
        reject { StudyChunkIO.open(file, metadata(file, bytes.size - 1L)).use { it.skip(bytes.size.toLong()) } }
        StudyChunkIO.open(file, meta).use { assertFalse(it.markSupported()); reject { it.reset() } }
        for (size in listOf(bytes.size - 1L, bytes.size + 1L)) reject { StudyChunkIO.open(file, metadata(file, size)).use { it.copyTo(java.io.ByteArrayOutputStream()) } }
        val corrupted = original.clone(); corrupted[corrupted.size - 8] = (corrupted[corrupted.size - 8].toInt() xor 1).toByte()
        file.writeBytes(corrupted); reject { StudyChunkIO.open(file, metadata(file, bytes.size.toLong())).use { it.readBytes() } }
        file.writeBytes(original.copyOf(original.size - 4)); reject { StudyChunkIO.open(file, metadata(file, bytes.size.toLong())).use { it.readBytes() } }
        val plain = File(file.parentFile, "chunk_00002.jsonl").apply { writeBytes(bytes) }
        reject { StudyChunkIO.open(plain, metadata(plain, bytes.size.toLong())).close() }
    }
    @Test fun compressedRecoverySalvagesNewlinePrefixAndStopsBeforeLaterChunks() {
        for (damage in listOf("truncated", "crc", "invalid_json")) {
            val root = root(); val directory = File(root, "package").apply { mkdirs() }
            fun row(seq: Long): String = JSONObject().apply {
                put("schema_version", StudyPackageWriter.SCHEMA); put("seq", seq)
                for (key in listOf("study_id", "participant_id", "installation_id", "run_id", "session_id", "clock_domain_id")) put(key, key)
            }.toString() + "\n"
            val first = File(directory, "chunk_00001.jsonl.gz.partial")
            gzip(first, (row(0) + row(1) + if (damage == "invalid_json") "{broken" else "").toByteArray())
            if (damage == "truncated") first.writeBytes(first.readBytes().dropLast(5).toByteArray())
            if (damage == "crc") { val data = first.readBytes(); data[data.size - 8] = (data[data.size - 8].toInt() xor 1).toByte(); first.writeBytes(data) }
            val before = first.readBytes()
            gzip(File(directory, "chunk_00002.jsonl.gz"), row(2).toByteArray())
            assertEquals(listOf(directory), StudyPackageWriter.recover(root))
            assertArrayEquals(before, first.readBytes())
            val manifest = JSONObject(File(directory, "manifest.json").readText())
            assertTrue(manifest.getBoolean("unknown_tail")); assertTrue(manifest.getBoolean("losses_unknown"))
            assertEquals(2L, manifest.getLong("record_count")); assertEquals(1, manifest.getJSONArray("chunks").length())
            assertEquals(row(0) + row(1), File(directory, manifest.getJSONArray("chunks").getJSONObject(0).getString("file")).readText())
        }
    }
    @Test fun completedGzipRecoveryContinuesAcrossChunks() {
        val root = root(); val identity = JSONObject()
        for (key in listOf("study_id", "participant_id", "installation_id", "run_id", "session_id", "clock_domain_id")) identity.put(key, key)
        val writer = StudyPackageWriter(root, identity, { 10.0 }, maxChunkBytes = 1)
        writer.record("sample", JSONObject()); writer.finish("done", 11.0)
        assertTrue(writer.awaitClosed(5000)); assertNull(writer.failure)
        val file = File(writer.packageDirectory, "manifest.json"); val original = JSONObject(file.readText()); file.delete()
        StudyPackageWriter.recover(root)
        val recovered = JSONObject(file.readText())
        assertEquals(original.getLong("record_count"), recovered.getLong("record_count"))
        assertEquals(original.getJSONArray("chunks").length(), recovered.getJSONArray("chunks").length())
    }
}
