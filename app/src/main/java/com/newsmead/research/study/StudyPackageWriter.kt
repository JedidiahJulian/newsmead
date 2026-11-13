package com.newsmead.research.study

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.security.DigestOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Deflater
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Same gzip framing and sync-flush contract, with the explicit best-speed level. */
internal class StudyBestSpeedGzipOutputStream(output: OutputStream) : GZIPOutputStream(output, 8192, true) {
    init { def.setLevel(Deflater.BEST_SPEED) }
}

/** Local append-only transport. Flush acknowledgement is not an fsync/durability claim.
 * Call recover only at process startup, before creating writers in that root.
 * Input JSON trees must not be concurrently mutated during record(); later mutation is safe.
 */
class StudyPackageWriter(
    rootDirectory: File,
    identity: JSONObject,
    private val clock: () -> Double,
    packageId: String = UUID.randomUUID().toString(),
    queueCapacity: Int = 2048,
    private val maxChunkBytes: Long = 2_000_000,
    private val onState: (String) -> Unit = {},
    private val compressChunks: Boolean = true,
) {
    val packageDirectory = checkedChild(rootDirectory, packageId)
    private val id = packageId
    private val identityCopy = copyJson(identity) as JSONObject
    private val lock = Any()
    private val queue = ArrayBlockingQueue<JSONObject>(queueCapacity)
    private val done = CountDownLatch(1)
    private var nextSeq = 0L
    private var finishing = false
    private var reason = ""
    private var endMs: Double? = null
    private var unknownTail = false
    private var rejectedAfterFinish = 0L
    private var lost = 0L
    private val losses = mutableListOf<JSONObject>()
    @Volatile private var written = 0L
    @Volatile private var flushedThrough = 0L
    @Volatile var failure: String? = null
        private set
    private val startedMs = clock()

    init {
        require(packageId.matches(Regex("[A-Za-z0-9_-]+")))
        require(maxChunkBytes > 0)
        for (key in IDENTITY_KEYS) require(identityCopy.optString(key).isNotBlank()) { "Missing $key" }
        Thread({ drain() }, "study-package-writer").start()
    }

    fun record(kind: String, payload: JSONObject, sourceMs: Double? = null): Boolean = synchronized(lock) {
        if (finishing || failure != null) { rejectedAfterFinish++; return false }
        // Invalid caller data or a failing clock must not consume an observation sequence.
        val snapshot = copyJson(payload) as JSONObject
        val recordedMs = clock()
        val seq = nextSeq + 1
        val record = envelope(kind, snapshot, seq, recordedMs, sourceMs)
        nextSeq = seq
        if (queue.offer(record)) true else {
            lost++
            val last = losses.lastOrNull()
            if (last != null && last.getLong("last_seq") == seq - 1) {
                last.put("last_seq", seq).put("count", last.getLong("count") + 1)
                    .put("last_source_elapsed_ms", finite(sourceMs))
            } else if (losses.size < 256) {
                losses.add(JSONObject().put("first_seq", seq).put("last_seq", seq).put("count", 1)
                    .put("first_source_elapsed_ms", finite(sourceMs)).put("last_source_elapsed_ms", finite(sourceMs))
                    .put("exact", true))
            } else {
                // Bounded metadata: this final range becomes an enclosing range, with exact count.
                last!!.put("last_seq", seq).put("count", last.getLong("count") + 1)
                    .put("last_source_elapsed_ms", finite(sourceMs)).put("exact", false)
            }
            false
        }
    }

    fun finish(reason: String, endMs: Double, unknownTail: Boolean = false) = synchronized(lock) {
        if (!finishing) {
            this.reason = reason; this.endMs = endMs; this.unknownTail = unknownTail; finishing = true
        }
    }

    fun awaitClosed(timeoutMs: Long): Boolean = done.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun health(): JSONObject = synchronized(lock) {
        JSONObject().put("written_records", written).put("flushed_through_seq", flushedThrough)
            .put("flush_is_fsync", false).put("lost_records", lost).put("queue_depth", queue.size)
            .put("rejected_after_finish", rejectedAfterFinish).put("failure", failure ?: JSONObject.NULL)
            .put("state", if (failure != null) "failed" else if (done.count == 0L) "finished" else if (finishing) "closing" else "recording")
    }

    private fun envelope(kind: String, payload: JSONObject, seq: Long, at: Double, source: Double?): JSONObject =
        JSONObject().apply {
            put("schema_version", SCHEMA); put("package_id", id)
            for (key in IDENTITY_KEYS) put(key, identityCopy.get(key))
            put("kind", kind); put("record_id", "$id:$seq"); put("seq", seq)
            put("recorded_elapsed_ms", finite(at)); put("source_elapsed_ms", finite(source)); put("payload", payload)
        }

    private fun drain() {
        var ownsDirectory = false
        var output: OutputStream? = null
        var partial: File? = null
        val chunks = JSONArray()
        var chunkRecords = 0L; var bytes = 0L; var firstSeq = 0L; var lastSeq = 0L
        var digest = MessageDigest.getInstance("SHA-256")
        var openedNs = System.nanoTime(); var flushNs = openedNs
        fun flush() { output?.flush(); flushedThrough = lastSeq; flushNs = System.nanoTime() }
        fun closeChunk() {
            if (output == null) return
            flush(); output!!.close(); output = null
            val final = checkedChild(packageDirectory, partial!!.name.removeSuffix(".partial"))
            check(partial!!.renameTo(final)) { "chunk_rename_failed" }
            chunks.put(JSONObject().put("file", final.name).put("byte_count", final.length())
                .put("sha256", hex(digest.digest())).put("record_count", chunkRecords)
                .put("first_seq", firstSeq).put("last_seq", lastSeq).apply {
                    if (compressChunks) put("encoding", StudyChunkIO.GZIP_ENCODING).put("uncompressed_byte_count", bytes)
                })
        }
        fun write(record: JSONObject) {
            val data = (record.toString() + "\n").toByteArray(Charsets.UTF_8)
            if (output != null && (bytes + data.size > maxChunkBytes || System.nanoTime() - openedNs >= CHUNK_NS)) closeChunk()
            if (output == null) {
                partial = checkedChild(packageDirectory, ("chunk_%05d.jsonl" + if (compressChunks) ".gz.partial" else ".partial").format(java.util.Locale.US, chunks.length() + 1))
                digest = MessageDigest.getInstance("SHA-256")
                val stored = DigestOutputStream(partial!!.outputStream().buffered(), digest)
                output = if (compressChunks) StudyBestSpeedGzipOutputStream(stored) else stored
                bytes = 0; chunkRecords = 0; openedNs = System.nanoTime()
                firstSeq = record.getLong("seq")
            }
            output!!.write(data); bytes += data.size; chunkRecords++; written++
            lastSeq = record.getLong("seq")
        }
        try {
            check(!packageDirectory.exists()) { "package_exists" }
            check(packageDirectory.mkdirs()) { "package_directory_failed" }
            ownsDirectory = true
            notifyState("recording")
            write(envelope("package_start", JSONObject().put("identity", identityCopy), 0, startedMs, null))
            while (true) {
                queue.poll(100, TimeUnit.MILLISECONDS)?.let { write(it) }
                if (System.nanoTime() - flushNs >= FLUSH_NS) flush()
                if (synchronized(lock) { finishing && queue.isEmpty() }) break
            }
            val terminal = synchronized(lock) {
                val healthSeq = ++nextSeq
                write(envelope("writer_health", health().put("losses", JSONArray(losses)), healthSeq, clock(), null))
                envelope("package_end", JSONObject().put("reason", reason).put("end_elapsed_ms", finite(endMs))
                    .put("unknown_tail", unknownTail).put("lost_records", lost)
                    .put("post_finish_calls_outside_recording_scope", true), ++nextSeq, clock(), endMs)
            }
            write(terminal); closeChunk()
            publish(packageDirectory, JSONObject().put("schema_version", SCHEMA).put("package_id", id)
                .put("status", "finished").put("content_complete", lost == 0L && !unknownTail)
                .put("identity", identityCopy).put("record_count", written).put("chunks", chunks)
                .put("losses", JSONArray(losses)).put("unknown_tail", unknownTail).put("flush_is_fsync", false))
            notifyState("finished")
        } catch (e: Exception) {
            failure = e.javaClass.simpleName + ": " + (e.message ?: "")
            synchronized(lock) { finishing = true }
            try { output?.close() } catch (_: Exception) { }
            // Failure marker is best effort. An absent final manifest also identifies an incomplete package.
            try { if (ownsDirectory) checkedChild(packageDirectory, "failure.json").writeText(JSONObject().put("status", "failed")
                .put("failure", failure).toString()) } catch (_: Exception) { }
            notifyState("failed")
        } finally { done.countDown() }
    }

    private fun notifyState(state: String) { try { onState(state) } catch (_: Exception) { } }

    companion object {
        const val SCHEMA = "newsmead_study_v1"
        private const val FLUSH_NS = 1_000_000_000L
        private const val CHUNK_NS = 60_000_000_000L
        private val IDENTITY_KEYS = listOf("study_id", "participant_id", "installation_id", "run_id", "session_id", "clock_domain_id")
        private fun finite(value: Double?): Any = value?.takeIf { it.isFinite() } ?: JSONObject.NULL
        private fun copyJson(value: Any?): Any = when (value) {
            null, JSONObject.NULL -> JSONObject.NULL
            is JSONObject -> JSONObject().apply { val keys = value.keys(); while (keys.hasNext()) { val key = keys.next(); put(key, copyJson(value.get(key))) } }
            is JSONArray -> JSONArray().apply { for (i in 0 until value.length()) put(copyJson(value.get(i))) }
            is Double -> finite(value)
            is Float -> finite(value.toDouble())
            is Number, is String, is Boolean -> value
            else -> throw IllegalArgumentException("Unsupported JSON value: ${value.javaClass.name}")
        }
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        /** Reject linked leaf aliases too: a recovery output must never alias an original chunk. */
        private fun checkedChild(parent: File, name: String): File {
            require(name.matches(Regex("[A-Za-z0-9_.-]+")) && name != "." && name != "..") { "unsafe_child_name" }
            val child = File(parent, name)
            require(child.canonicalFile.parentFile == parent.canonicalFile &&
                child.canonicalFile == File(parent.canonicalFile, name)) { "child_path_escapes_or_aliases_parent" }
            return child
        }
        private fun publish(directory: File, manifest: JSONObject) {
            checkedChild(directory.parentFile, directory.name)
            val partial = checkedChild(directory, "manifest.json.partial")
            val final = checkedChild(directory, "manifest.json")
            partial.writeText(manifest.toString(), Charsets.UTF_8)
            check(partial.renameTo(final)) { "manifest_rename_failed" }
        }

        /** Retains original files; publishes only newline-terminated, valid JSON prefix rows.
         * Never synthesizes a terminal event or borrows the current boot's clock domain.
         */
        fun recover(root: File): List<File> {
            val recovered = mutableListOf<File>()
            for (directory in root.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
                checkedChild(root, directory.name)
                // Validate read and write candidates before any recovery output is created.
                directory.listFiles()?.filter { it.name == "manifest.json" || it.name == "manifest.json.partial" ||
                    it.name.matches(Regex("(chunk|recovered)_[0-9]+\\.jsonl(\\.gz)?(\\.partial)?")) }
                    ?.forEach { checkedChild(directory, it.name) }
                if (checkedChild(directory, "manifest.json").exists()) continue
                val originals = directory.listFiles()?.filter { it.name.matches(Regex("chunk_[0-9]+\\.jsonl(\\.gz)?(\\.partial)?")) }
                    ?.sortedBy { it.name } ?: continue
                if (originals.isEmpty()) continue
                val chunks = JSONArray(); var count = 0L; var identity = JSONObject(); var stop = false; var previousSeq = -1L
                for (file in originals) {
                    if (stop) break
                    checkedChild(directory, file.name)
                    var rows = 0L; var first = 0L; var last = 0L; var retainedBytes = 0L
                    val target = checkedChild(directory, "recovered_%05d.jsonl".format(java.util.Locale.US, chunks.length() + 1))
                    val recoveredDigest = MessageDigest.getInstance("SHA-256")
                    DigestOutputStream(target.outputStream().buffered(), recoveredDigest).use { retained ->
                        try {
                            StudyChunkIO.openRecovery(file).buffered().use { input ->
                                val line = ByteArrayOutputStream()
                                while (true) {
                                    val byte = input.read()
                                    if (byte < 0) { if (line.size() != 0) stop = true; break }
                                    if (byte != 10) {
                                        require(line.size() < StudyChunkIO.MAX_UNCOMPRESSED_BYTES) { "recovery_line_too_large" }
                                        line.write(byte); continue
                                    }
                                    val data = line.toByteArray()
                                    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
                                    val row = JSONObject(text)
                                    require(row.getString("schema_version") == SCHEMA)
                                    val seq = row.getLong("seq"); require(seq > previousSeq)
                                    if (count == 0L && rows == 0L) for (key in IDENTITY_KEYS) identity.put(key, row.get(key))
                                    for (key in IDENTITY_KEYS) require(row.get(key) == identity.get(key))
                                    if (rows == 0L) first = seq
                                    previousSeq = seq; last = seq; rows++
                                    retained.write(data); retained.write(10); retainedBytes += data.size + 1
                                    line.reset()
                                }
                            }
                        } catch (_: Exception) { stop = true }
                    }
                    if (rows == 0L) { target.delete(); continue }
                    chunks.put(JSONObject().put("file", target.name).put("byte_count", retainedBytes)
                        .put("sha256", hex(recoveredDigest.digest()))
                        .put("record_count", rows).put("first_seq", first).put("last_seq", last))
                    count += rows
                }
                publish(directory, JSONObject().put("schema_version", SCHEMA).put("package_id", directory.name)
                    .put("identity", identity).put("status", "recovered_incomplete").put("content_complete", false)
                    .put("unknown_tail", true).put("end_elapsed_ms", JSONObject.NULL).put("chunks", chunks)
                    .put("record_count", count).put("losses", JSONArray()).put("losses_unknown", true)
                    .put("original_files_retained", true).put("flush_is_fsync", false))
                recovered.add(directory)
            }
            return recovered
        }
    }
}
