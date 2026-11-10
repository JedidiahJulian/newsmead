package com.newsmead.research.study

import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Storage decoding only: logical UTF-8 JSONL bytes are never rewritten. */
object StudyChunkIO {
    const val GZIP_ENCODING = "gzip_jsonl_v1"
    const val MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024
    fun isGzip(file: File) = file.name.removeSuffix(".partial").endsWith(".jsonl.gz")

    fun validate(file: File, chunk: JSONObject) {
        require(file.name.matches(Regex("(chunk|recovered)_[0-9]+\\.jsonl(\\.gz)?"))) { "Invalid finished chunk name" }
        val gzip = isGzip(file)
        require(if (gzip) chunk.optString("encoding") == GZIP_ENCODING else !chunk.has("encoding")) { "Unknown chunk encoding" }
        require(file.length() == integer(chunk, "byte_count")) { "Chunk size mismatch" }
        val expected = if (gzip) integer(chunk, "uncompressed_byte_count") else if (chunk.has("uncompressed_byte_count")) integer(chunk, "uncompressed_byte_count") else file.length()
        require(expected in 0..MAX_UNCOMPRESSED_BYTES) { "Chunk decoded size exceeds bound" }
        if (!gzip) require(expected == file.length()) { "Chunk decoded size mismatch" }
    }

    private fun integer(chunk: JSONObject, key: String): Long {
        val value = chunk.get(key)
        require(value is Byte || value is Short || value is Int || value is Long) { "Invalid chunk integer: $key" }
        return (value as Number).toLong()
    }

    /** Reading through EOF verifies the gzip trailer and exact declared decoded length. */
    fun open(file: File, chunk: JSONObject): InputStream {
        validate(file, chunk)
        val expected = if (isGzip(file)) integer(chunk, "uncompressed_byte_count") else file.length()
        return bounded(decoded(file), expected, true)
    }

    /** Recovery has no trusted manifest. A decoding failure preserves only the prefix read so far. */
    fun openRecovery(file: File): InputStream = bounded(decoded(file), MAX_UNCOMPRESSED_BYTES, false)
    private fun decoded(file: File): InputStream {
        val raw = file.inputStream().buffered()
        return try { if (isGzip(file)) GZIPInputStream(raw) else raw } catch (e: Exception) { raw.close(); throw e }
    }
    private fun bounded(input: InputStream, limit: Long, exact: Boolean) = object : FilterInputStream(input) {
        var count = 0L
        private fun checked(n: Int): Int {
            if (n < 0) { require(!exact || count == limit) { "Chunk decoded size mismatch" }; return n }
            count += n
            require(count <= limit) { "Chunk decoded size exceeds bound" }
            return n
        }
        override fun markSupported() = false
        override fun mark(readlimit: Int) { }
        override fun reset(): Unit = throw java.io.IOException("Chunk stream cannot reset")
        override fun skip(n: Long): Long {
            if (n <= 0) return 0
            val buffer = ByteArray(8192); var skipped = 0L
            while (skipped < n) {
                val read = read(buffer, 0, minOf(buffer.size.toLong(), n - skipped).toInt())
                if (read < 0) break
                skipped += read
            }
            return skipped
        }
        override fun read(): Int { val value = `in`.read(); checked(if (value < 0) -1 else 1); return value }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            return checked(`in`.read(bytes, offset, minOf(length.toLong(), (limit - count + 1).coerceAtLeast(1)).toInt()))
        }
    }
}
