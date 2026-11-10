package com.newsmead.research.study

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StudyRecordingCatalogTest {
    private fun root() = Files.createTempDirectory("study-catalog-").toFile().also { it.deleteOnExit() }
    private fun fixture(root: File, id: String, reading: String? = null, phase: String = "reading",
        participant: String = "p", installation: String = "phone", attempt: String? = null,
        reference: String? = null, calibration: String? = "cal", terminal: Boolean = true,
        oversizedPrefix: Boolean = false, coordinateReference: JSONObject? = null,
        clockDomain: String = "clock"): File {
        val directory = File(root, id).apply { mkdirs() }
        val metadata = JSONObject().put("article_id", "same-article")
        reading?.let { metadata.put("article_reading_id", it) }
        attempt?.let { metadata.put("baseline_attempt_id", it) }
        calibration?.let { metadata.put("calibration_id", it) }
        if (phase == "reading_baseline" || coordinateReference != null)
            metadata.put("coordinate_reference_contract", StudyBaselineProtocol.SURFACE)
        if (phase == "reading_baseline") metadata.put("baseline_protocol", StudyBaselineProtocol.VERSION)
            .put("coordinate_reference_declared", true)
        fun row(kind: String, payload: JSONObject) = JSONObject().put("package_id", id).put("session_id", "session-$id")
            .put("kind", kind).put("payload", payload).toString() + "\n"
        var text = row("session_start", JSONObject().put("phase", phase).put("metadata", metadata)
            .put("start_ms", 100.0).put("started_utc_ms", 1000L))
        if (oversizedPrefix) text += row("text_snapshot", JSONObject().put("body_text", "x".repeat(530_000)))
        if (phase != "rdf_baseline") text += row("rdf_reference", JSONObject().put("reference", reference?.let {
            JSONObject().put("session_id", it).put("participant_code", participant).put("calibration_fingerprint", calibration)
        } ?: JSONObject.NULL))
        text += row("coordinate_reference", JSONObject().put("reference", coordinateReference ?: JSONObject.NULL))
        text += row("provider_start", JSONObject().put("calibration_id", calibration ?: JSONObject.NULL))
        if (terminal) text += row("package_end", JSONObject().put("end_elapsed_ms", 350.0))
        val chunk = File(directory, "chunk_00001.jsonl").apply { writeText(text) }
        File(directory, "manifest.json").writeText(JSONObject().put("schema_version", StudyPackageWriter.SCHEMA)
            .put("package_id", id).put("status", "finished").put("content_complete", true)
            .put("identity", JSONObject().put("participant_id", participant).put("installation_id", installation)
                .put("session_id", "session-$id").put("clock_domain_id", clockDomain))
            .put("chunks", JSONArray().put(JSONObject().put("file", chunk.name).put("byte_count", chunk.length()))).toString())
        return directory
    }

    private fun coordinateLink() = JSONObject().put("protocol", StudyBaselineProtocol.VERSION)
        .put("source_package_id", "joint").put("source_session_id", "session-joint")
        .put("baseline_attempt_id", "attempt").put("participant_id", "p")
        .put("installation_id", "phone").put("calibration_id", "cal").put("clock_domain_id", "clock")

    @Test fun jointSourceIsIncludedWithoutAnRdfProfileAndBothDependenciesDeduplicate() {
        val root = root()
        fixture(root, "joint", phase = "reading_baseline", attempt = "attempt")
        fixture(root, "a", "visit-a", coordinateReference = coordinateLink())
        fixture(root, "b", "visit-b", reference = "attempt", coordinateReference = coordinateLink())
        val catalog = StudyRecordingCatalog.load(root)
        assertTrue(catalog.warnings.toString(), catalog.warnings.isEmpty())
        assertFalse(catalog.recordings.single { it.first.phase == "reading_baseline" }.legacy)
        val articles = catalog.recordings.filter { it.first.phase == "reading" }.map { it.id }.toSet()
        val selection = StudyRecordingCatalog.select(catalog, articles)
        assertEquals(setOf("a", "b", "joint"), selection.packageIds)
        assertEquals(setOf("joint"), selection.includedReferenceIds)
        assertTrue(selection.warnings.toString(), selection.warnings.isEmpty())
    }

    @Test fun coordinateSourceMustMatchExactPackageSessionAttemptAndClock() {
        for (field in listOf("source_package_id", "source_session_id", "baseline_attempt_id", "participant_id",
            "installation_id", "calibration_id", "clock_domain_id")) {
            val root = root()
            fixture(root, "joint", phase = "reading_baseline", attempt = "attempt")
            fixture(root, "article", "visit", coordinateReference = coordinateLink().put(field, "other"))
            val catalog = StudyRecordingCatalog.load(root)
            val selected = StudyRecordingCatalog.select(catalog, setOf(catalog.recordings.single { it.first.phase == "reading" }.id))
            assertEquals(field, setOf("article"), selected.packageIds)
            assertTrue(field, selected.includedReferenceIds.isEmpty())
            assertTrue(field, selected.warnings.single().contains("source coordinate baseline"))
        }
        val root = root()
        fixture(root, "joint", phase = "rdf_baseline", attempt = "attempt")
        fixture(root, "article", "visit", coordinateReference = coordinateLink())
        val catalog = StudyRecordingCatalog.load(root)
        assertEquals(setOf("article"), StudyRecordingCatalog.select(catalog,
            setOf(catalog.recordings.single { it.first.phase == "reading" }.id)).packageIds)
    }

    @Test fun logicalReadingsGroupSegmentsButReopenedSameContentAndLegacyStaySeparate() {
        val root = root()
        fixture(root, "a", "visit-1"); fixture(root, "b", "visit-1")
        fixture(root, "c", "visit-2"); fixture(root, "old")
        val catalog = StudyRecordingCatalog.load(root)
        assertTrue(catalog.warnings.toString(), catalog.warnings.isEmpty())
        assertEquals(3, catalog.recordings.size)
        val grouped = catalog.recordings.single { it.first.readingId == "visit-1" }
        assertEquals(setOf("a", "b"), grouped.packages.map { it.id }.toSet())
        assertEquals(500.0, grouped.durationMs!!, 0.0)
        assertEquals(1000L, grouped.startedUtcMs!!)
        assertTrue(grouped.bytes > 0)
        assertEquals("session-old", catalog.recordings.single { it.legacy }.first.session)
        assertEquals(catalog.recordings.map { it.id }.toSet(), StudyRecordingCatalog.load(root).recordings.map { it.id }.toSet())
    }

    @Test fun selectedArticlesIncludeSharedBaselineOnceAndCoordinateReferenceOnlyExplicitly() {
        val root = root()
        fixture(root, "a", "visit-1", reference = "fit-1")
        fixture(root, "b", "visit-2", reference = "fit-1")
        fixture(root, "baseline", phase = "rdf_baseline", attempt = "fit-1")
        fixture(root, "coordinate", "visit-3", phase = "coordinate_reference")
        fixture(root, "unselected", "visit-4")
        val catalog = StudyRecordingCatalog.load(root)
        assertEquals(5, catalog.recordings.size)
        val articles = catalog.recordings.filter { it.first.id in setOf("a", "b") }.map { it.id }.toSet()
        val plan = StudyRecordingCatalog.select(catalog, articles)
        assertEquals(setOf("a", "b", "baseline"), plan.packageIds)
        assertEquals(setOf("baseline"), plan.includedReferenceIds)
        assertTrue(plan.warnings.toString(), plan.warnings.isEmpty())
        val baselineOnly = StudyRecordingCatalog.select(catalog, setOf(catalog.recordings.single { it.first.id == "baseline" }.id))
        assertEquals(setOf("baseline"), baselineOnly.packageIds); assertTrue(baselineOnly.includedReferenceIds.isEmpty())
        val coordinate = catalog.recordings.single { it.first.id == "coordinate" }
        assertEquals(setOf("coordinate"), StudyRecordingCatalog.select(catalog, setOf(coordinate.id)).packageIds)
    }

    @Test fun twoReferencesIncludeBothWithoutCrossParticipantInstallationOrCalibrationMatches() {
        val root = root()
        fixture(root, "a", "visit", reference = "fit-1"); fixture(root, "b", "visit", reference = "fit-2")
        fixture(root, "one", phase = "rdf_baseline", attempt = "fit-1")
        fixture(root, "two", phase = "rdf_baseline", attempt = "fit-2")
        fixture(root, "wrongPerson", phase = "rdf_baseline", participant = "other", attempt = "fit-1")
        fixture(root, "wrongPhone", phase = "rdf_baseline", installation = "other", attempt = "fit-1")
        fixture(root, "wrongCalibration", phase = "rdf_baseline", calibration = "other", attempt = "fit-1")
        val catalog = StudyRecordingCatalog.load(root)
        val plan = StudyRecordingCatalog.select(catalog, setOf(catalog.recordings.single { it.first.readingId == "visit" }.id))
        assertEquals(setOf("a", "b", "one", "two"), plan.packageIds)
        assertEquals(setOf("one", "two"), plan.includedReferenceIds)
        val onlyWrong = root()
        fixture(onlyWrong, "article", "visit", reference = "fit-1")
        fixture(onlyWrong, "wrong", phase = "rdf_baseline", participant = "other", attempt = "fit-1")
        val missing = StudyRecordingCatalog.load(onlyWrong)
        val missingPlan = StudyRecordingCatalog.select(missing, setOf(missing.recordings.single { it.first.phase == "reading" }.id))
        assertEquals(setOf("article"), missingPlan.packageIds)
        assertTrue(missingPlan.warnings.single().contains("frozen rdf_reference remains intact"))
    }

    @Test fun malformedAndEscapingPathsAreUnavailableAndSelectionIdsAreNotPaths() {
        val root = root()
        val bad = fixture(root, "bad")
        val manifestFile = File(bad, "manifest.json"); val manifest = JSONObject(manifestFile.readText())
        manifest.getJSONArray("chunks").getJSONObject(0).put("file", "../outside.jsonl")
        manifestFile.writeText(manifest.toString())
        File(root, "broken").mkdirs(); File(root, "broken/manifest.json").writeText("{broken")
        val catalog = StudyRecordingCatalog.load(root)
        assertTrue(catalog.recordings.isEmpty()); assertEquals(2, catalog.warnings.size)
        try { StudyRecordingCatalog.directory(root, "../outside"); fail("Expected unsafe directory rejection") } catch (_: IllegalArgumentException) { }
        try { StudyRecordingCatalog.file(root, "../outside"); fail("Expected unsafe file rejection") } catch (_: IllegalArgumentException) { }
        try { StudyRecordingCatalog.select(catalog, setOf("../outside")); fail("Expected stale selection rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun boundedPrefixAndMissingTerminalRemainExplicitlyUnavailable() {
        val root = root(); fixture(root, "large", "visit", reference = "fit", terminal = false, oversizedPrefix = true)
        val catalog = StudyRecordingCatalog.load(root)
        assertTrue(catalog.warnings.toString(), catalog.warnings.isEmpty())
        val recording = catalog.recordings.single()
        assertNull(recording.durationMs); assertFalse(recording.first.referenceKnown)
        val selection = StudyRecordingCatalog.select(catalog, setOf(recording.id))
        assertEquals(setOf("large"), selection.packageIds)
        assertTrue(selection.warnings.single().contains("bounded startup metadata"))
    }

    @Test fun unknownParticipantOrCalibrationAtAnyLinkNeverAutomaticallyIncludesBaseline() {
        for (missing in listOf("provider", "reference", "baseline", "participant")) {
            val root = root()
            val article = fixture(root, "article", "visit", reference = "fit")
            val baseline = fixture(root, "baseline", phase = "rdf_baseline", attempt = "fit")
            val directory = if (missing == "baseline") baseline else article
            val chunk = File(directory, "chunk_00001.jsonl")
            val rows = chunk.readLines().map { JSONObject(it) }
            when (missing) {
                "provider" -> rows.single { it.getString("kind") == "provider_start" }.getJSONObject("payload").remove("calibration_id")
                "reference" -> rows.single { it.getString("kind") == "rdf_reference" }.getJSONObject("payload")
                    .getJSONObject("reference").remove("calibration_fingerprint")
                "participant" -> rows.single { it.getString("kind") == "rdf_reference" }.getJSONObject("payload")
                    .getJSONObject("reference").remove("participant_code")
                "baseline" -> rows.single { it.getString("kind") == "session_start" }.getJSONObject("payload")
                    .getJSONObject("metadata").remove("calibration_id")
            }
            chunk.writeText(rows.joinToString("") { it.toString() + "\n" })
            val manifestFile = File(directory, "manifest.json")
            val manifest = JSONObject(manifestFile.readText())
            manifest.getJSONArray("chunks").getJSONObject(0).put("byte_count", chunk.length())
            manifestFile.writeText(manifest.toString())
            val catalog = StudyRecordingCatalog.load(root)
            val plan = StudyRecordingCatalog.select(catalog, setOf(catalog.recordings.single { it.first.id == "article" }.id))
            assertEquals(missing, setOf("article"), plan.packageIds)
            assertTrue(missing, plan.includedReferenceIds.isEmpty())
            assertTrue(missing, plan.warnings.single().contains("compatibility is unknown or mismatched"))
        }
    }
    private fun compress(directory: File): File {
        val plain = File(directory, "chunk_00001.jsonl"); val bytes = plain.readBytes()
        val gzip = File(directory, "chunk_00001.jsonl.gz")
        java.util.zip.GZIPOutputStream(gzip.outputStream()).use { it.write(bytes) }; plain.delete()
        val file = File(directory, "manifest.json"); val manifest = JSONObject(file.readText())
        manifest.getJSONArray("chunks").getJSONObject(0).put("file", gzip.name).put("byte_count", gzip.length())
            .put("encoding", StudyChunkIO.GZIP_ENCODING).put("uncompressed_byte_count", bytes.size)
        file.writeText(manifest.toString()); return gzip
    }

    @Test fun gzipMetadataAndBoundedSuffixMatchPlainAndMissingness() {
        val root = root()
        compress(fixture(root, "article", "visit", reference = "fit"))
        compress(fixture(root, "baseline", phase = "rdf_baseline", attempt = "fit"))
        compress(fixture(root, "large", "other", oversizedPrefix = true))
        val catalog = StudyRecordingCatalog.load(root)
        assertTrue(catalog.warnings.toString(), catalog.warnings.isEmpty())
        val article = catalog.recordings.single { it.first.id == "article" }
        assertEquals(250.0, article.durationMs!!, 0.0)
        assertEquals(setOf("article", "baseline"), StudyRecordingCatalog.select(catalog, setOf(article.id)).packageIds)
        val large = catalog.recordings.single { it.first.id == "large" }
        assertEquals(250.0, large.durationMs!!, 0.0); assertFalse(large.first.referenceKnown)
    }

    @Test fun gzipCorruptionAndDecodedSizeBoundsAreUnavailable() {
        for (damage in listOf("crc", "truncation", "bound", "count", "encoding")) {
            val root = root(); val directory = fixture(root, "article", "visit"); val gzip = compress(directory)
            val file = File(directory, "manifest.json"); val manifest = JSONObject(file.readText())
            val chunk = manifest.getJSONArray("chunks").getJSONObject(0)
            when (damage) {
                "crc" -> { val bytes = gzip.readBytes(); bytes[bytes.size - 8] = (bytes[bytes.size - 8].toInt() xor 1).toByte(); gzip.writeBytes(bytes) }
                "truncation" -> gzip.writeBytes(gzip.readBytes().dropLast(4).toByteArray())
                "bound" -> chunk.put("uncompressed_byte_count", StudyChunkIO.MAX_UNCOMPRESSED_BYTES + 1)
                "count" -> chunk.put("uncompressed_byte_count", chunk.getLong("uncompressed_byte_count") + 1)
                "encoding" -> chunk.put("encoding", "unknown")
            }
            chunk.put("byte_count", gzip.length()); file.writeText(manifest.toString())
            val catalog = StudyRecordingCatalog.load(root)
            assertTrue(damage, catalog.recordings.isEmpty()); assertEquals(damage, 1, catalog.warnings.size)
        }
    }

}
