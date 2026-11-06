package com.newsmead.research.study

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Bounded metadata reader. Never loads a gaze stream or treats display metadata as a path. */
object StudyRecordingCatalog {
    data class Reference(val attempt: String, val participant: String?, val calibration: String?)
    data class Package(val id: String, val session: String, val participant: String, val installation: String,
        val phase: String, val readingId: String?, val articleId: String?, val startedUtcMs: Long?,
        val durationMs: Double?, val bytes: Long, val status: String, val baselineAttempt: String?,
        val calibration: String?, val reference: Reference?, val referenceKnown: Boolean)
    data class Recording(val id: String, val packages: List<Package>) {
        val first get() = packages.first()
        val bytes get() = packages.sumOf { it.bytes }
        val startedUtcMs get() = packages.mapNotNull { it.startedUtcMs }.minOrNull()
        val durationMs get() = if (packages.all { it.durationMs != null }) packages.sumOf { it.durationMs!! } else null
        val legacy get() = first.readingId == null && first.phase != "rdf_baseline"
    }
    data class Catalog(val recordings: List<Recording>, val warnings: List<String>)
    data class Selection(val packageIds: Set<String>, val includedReferenceIds: Set<String>, val warnings: List<String>)
    private val safeName = Regex("[A-Za-z0-9_-]+")
    private val safeFile = Regex("[A-Za-z0-9_.-]+")

    fun directory(root: File, id: String): File {
        require(safeName.matches(id)) { "Unsafe package ID" }
        return File(root, id).also { require(it.canonicalFile.parentFile == root.canonicalFile) { "Package path escapes storage" } }
    }
    fun file(directory: File, name: String): File {
        require(safeFile.matches(name) && name != "." && name != "..") { "Unsafe package file" }
        return File(directory, name).also { require(it.canonicalFile.parentFile == directory.canonicalFile) { "File path escapes package" } }
    }
    /** Call before recovery too: it must never traverse a linked directory/file outside this root. */
    fun validatePaths(root: File) {
        root.listFiles()?.filter { it.isDirectory }?.forEach { candidate ->
            val directory = directory(root, candidate.name)
            directory.listFiles()?.filter { it.name == "manifest.json" || it.name == "manifest.json.partial" ||
                it.name.matches(Regex("(chunk|recovered)_[0-9]+\\.jsonl(\\.gz)?(\\.partial)?")) }
                ?.forEach { file(directory, it.name) }
        }
    }
    fun load(root: File): Catalog {
        val packages = mutableListOf<Package>(); val warnings = mutableListOf<String>()
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { candidate ->
            runCatching {
                val directory = directory(root, candidate.name)
                val manifest = file(directory, "manifest.json")
                if (manifest.isFile) packages.add(read(directory, manifest))
            }.onFailure { warnings.add("Package ${candidate.name}: metadata unavailable (${it.message}).") }
        }
        val groups = packages.groupBy { p ->
            // Length-independent JSON tuple encoding prevents delimiter collisions in metadata identities.
            listOf(p.participant, p.installation, p.phase,
                if (p.phase == "rdf_baseline") "baseline:${p.id}" else p.readingId?.let { "reading:$it" } ?: "legacy:${p.session}")
        }.map { (key, values) -> Recording(hash(org.json.JSONArray(key).toString()), values.sortedBy { it.startedUtcMs ?: Long.MAX_VALUE }) }
            .sortedByDescending { it.startedUtcMs ?: Long.MIN_VALUE }
        return Catalog(groups, warnings)
    }

    fun select(catalog: Catalog, recordingIds: Set<String>): Selection {
        require(recordingIds.isNotEmpty()) { "Select at least one recording." }
        require(catalog.recordings.map { it.id }.toSet().containsAll(recordingIds)) { "Recording list changed. Refresh and select again." }
        val selected = catalog.recordings.filter { it.id in recordingIds }.flatMap { it.packages }
        val all = catalog.recordings.flatMap { it.packages }
        val ids = selected.map { it.id }.toMutableSet(); val included = mutableSetOf<String>(); val warnings = mutableSetOf<String>()
        selected.filter { it.phase != "rdf_baseline" }.forEach { article ->
            if (!article.referenceKnown) warnings.add("${article.session}: baseline linkage unavailable in bounded startup metadata; original records are retained.")
            article.reference?.let { ref ->
                val matches = all.filter { baseline -> baseline.phase == "rdf_baseline" && baseline.baselineAttempt == ref.attempt &&
                    baseline.participant == article.participant && baseline.installation == article.installation &&
                    ref.participant == article.participant && article.calibration != null &&
                    ref.calibration == article.calibration && baseline.calibration == article.calibration }
                if (matches.isEmpty()) warnings.add("${article.session}: source R/D/F baseline ${ref.attempt} is missing or participant/calibration compatibility is unknown or mismatched; the article's frozen rdf_reference remains intact.")
                matches.forEach { baseline -> if (baseline.id !in ids) { ids.add(baseline.id); included.add(baseline.id) } }
            }
        }
        return Selection(ids, included, warnings.toList())
    }

    private fun read(directory: File, manifestFile: File): Package {
        require(manifestFile.length() in 1L..1_048_576L) { "Manifest exceeds metadata bound" }
        val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
        require(manifest.getString("schema_version") == StudyPackageWriter.SCHEMA)
        require(manifest.getString("package_id") == directory.name) { "Package identity mismatch" }
        val identity = manifest.getJSONObject("identity")
        val session = identity.getString("session_id").also { require(it.isNotBlank()) }
        val participant = identity.getString("participant_id").also { require(it.isNotBlank()) }
        val installation = identity.getString("installation_id").also { require(it.isNotBlank()) }
        val chunks = manifest.getJSONArray("chunks"); val files = mutableListOf<Pair<File, JSONObject>>(); var bytes = manifestFile.length()
        val names = mutableSetOf<String>()
        repeat(chunks.length()) { index ->
            val chunk = chunks.getJSONObject(index); val name = chunk.getString("file")
            require(name.matches(Regex("(chunk|recovered)_[0-9]+\\.jsonl(\\.gz)?")) && names.add(name)) { "Invalid chunk name" }
            val input = file(directory, name); require(input.isFile) { "Missing chunk" }
            StudyChunkIO.validate(input, chunk)
            files.add(input to chunk); bytes = Math.addExact(bytes, input.length())
        }
        var start: JSONObject? = null; var reference: Reference? = null; var known = false; var calibration: String? = null
        readPrefix(files) { row ->
            require(row.getString("package_id") == directory.name && row.getString("session_id") == session) { "Record identity mismatch" }
            val payload = row.getJSONObject("payload")
            when (row.getString("kind")) {
                "session_start" -> start = payload
                "rdf_reference" -> {
                    require(payload.has("reference")) { "Missing frozen reference field" }
                    known = true
                    if (!payload.isNull("reference")) payload.getJSONObject("reference").let { ref ->
                        reference = Reference(requireNotNull(string(ref, "session_id")), string(ref, "participant_code"), string(ref, "calibration_fingerprint"))
                    }
                }
                "provider_start" -> { calibration = string(payload, "calibration_id"); return@readPrefix false }
            }
            true
        }
        val metadata = start?.optJSONObject("metadata") ?: JSONObject()
        val phase = start?.let { string(it, "phase") } ?: "unavailable"
        val begin = start?.finite("start_ms")
        val end = manifest.finite("end_elapsed_ms") ?: files.lastOrNull()?.let { tailEnd(it) }
        return Package(directory.name, session, participant, installation, phase, string(metadata, "article_reading_id"),
            string(metadata, "article_id")?.takeUnless { it == "0" }, start?.opt("started_utc_ms")?.let { (it as? Number)?.toLong() },
            if (begin != null && end != null && end >= begin) end - begin else null, bytes,
            manifest.optString("status", "unavailable") + if (!manifest.optBoolean("content_complete")) " / incomplete" else "",
            string(metadata, "baseline_attempt_id"), if (phase == "rdf_baseline") string(metadata, "calibration_id") else calibration,
            reference, known)
    }

    private fun readPrefix(files: List<Pair<File, JSONObject>>, visit: (JSONObject) -> Boolean) {
        var remaining = 2_097_152; var rows = 0
        for ((file, chunk) in files) StudyChunkIO.open(file, chunk).buffered().use { input ->
            val line = ByteArrayOutputStream()
            while (remaining-- > 0 && rows < 256) {
                val byte = input.read(); if (byte < 0) break
                if (byte == 10) {
                    rows++; val row = JSONObject(line.toString("UTF-8")); line.reset()
                    if (!visit(row)) return
                } else { if (line.size() >= 524_288) return; line.write(byte) }
            }
            if (remaining <= 0 || rows >= 256) return
        }
    }
    private fun tailEnd(chunk: Pair<File, JSONObject>): Double? {
        val (file, metadata) = chunk
        if (StudyChunkIO.isGzip(file)) {
            // Decode only the last chunk, retain a fixed-size suffix, and reach EOF to check CRC/length.
            val ring = ByteArray(131_072); var total = 0L
            StudyChunkIO.open(file, metadata).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    repeat(n) { ring[(total++ % ring.size).toInt()] = buffer[it] }
                }
            }
            val size = minOf(total, ring.size.toLong()).toInt()
            val tail = ByteArray(size) { ring[((total - size + it) % ring.size).toInt()] }
            return tailEnd(tail, total > size)
        }
        return RandomAccessFile(file, "r").use { input ->
            val start = (input.length() - 131_072).coerceAtLeast(0); input.seek(start)
            val bytes = ByteArray((input.length() - start).toInt()); input.readFully(bytes)
            tailEnd(bytes, start > 0)
        }
    }
    private fun tailEnd(bytes: ByteArray, clipped: Boolean): Double? {
        val lines = String(bytes, Charsets.UTF_8).split('\n')
        return lines.drop(if (clipped) 1 else 0).asReversed().firstNotNullOfOrNull { line ->
            if (line.isBlank()) null else runCatching { JSONObject(line) }.getOrNull()?.let { row ->
                if (row.optString("kind") in listOf("session_end", "package_end")) row.optJSONObject("payload")?.finite("end_elapsed_ms")
                    ?: row.optJSONObject("payload")?.finite("end_ms") else null
            }
        }
    }
    private fun JSONObject.finite(key: String) = (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun string(value: JSONObject, key: String) = (value.opt(key) as? String)?.takeIf { it.isNotBlank() }
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
