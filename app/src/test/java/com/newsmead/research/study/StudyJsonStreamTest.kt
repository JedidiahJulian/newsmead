package com.newsmead.research.study

import com.newsmead.gaze.aid.ReaderAidPreferences
import com.newsmead.gaze.aid.ReadingAidAction
import com.newsmead.gaze.aid.ReadingAidAnchor
import com.newsmead.gaze.aid.ReadingAidBookmarkEvidence
import com.newsmead.gaze.aid.ReadingAidContext
import com.newsmead.gaze.aid.ReadingAidContinuationEvidence
import com.newsmead.gaze.aid.ReadingAidDecision
import com.newsmead.gaze.aid.ReadingAidDecisionStatus
import com.newsmead.gaze.aid.ReadingAidFocusEvidence
import com.newsmead.gaze.aid.ReadingAidGuideEvidence
import com.newsmead.gaze.aid.ReadingAidPolicy
import com.newsmead.gaze.aid.ReadingAidPolicyConfig
import com.newsmead.gaze.aid.ReadingAidType
import com.newsmead.gaze.aid.ReadingAidsMode
import com.newsmead.gaze.aid.ReadingFocusStyle
import com.newsmead.gaze.aid.ReadingGuideDirection
import com.newsmead.gaze.aid.ReadingGuideRange
import com.newsmead.gaze.aid.ReadingGuideUnit
import com.newsmead.gaze.aid.ReadingLocationUncertainty
import com.newsmead.gaze.aid.ReadingSupportedRowBand
import com.newsmead.gaze.aid.ReadingSupportedVisit
import com.newsmead.gaze.aid.ReadingThreeLineGuideEvidence
import com.newsmead.gaze.reading.ReadingCoordinateStream
import com.newsmead.gaze.reading.ReadingGeometry
import com.newsmead.gaze.reading.ReadingLine
import com.newsmead.gaze.reading.ReadingMotionCompensation
import com.newsmead.gaze.reading.ReadingRect
import com.newsmead.gaze.reading.ReadingSample
import com.newsmead.gaze.reading.ReadingTextDirection
import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathCoordinateStream
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathFixation
import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathLine
import com.newsmead.gaze.scanpath.ScanpathMotionCompensation
import com.newsmead.gaze.scanpath.ScanpathRect
import com.newsmead.gaze.scanpath.ScanpathSample
import com.newsmead.gaze.scanpath.ScanpathTextDirection
import com.newsmead.gaze.scanpath.WordRange
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

class StudyJsonStreamTest {
    private fun streamJson(value: Any?): Any {
        val out = StringWriter()
        StudyJsonWriter(out).writeValue(value)
        val text = out.toString()
        return when {
            text.startsWith("{") -> JSONObject(text)
            text.startsWith("[") -> JSONArray(text)
            else -> text
        }
    }

    private fun assertJsonParity(label: String, value: Any) {
        val expected = StudyJson.value(value) as JSONObject
        val actual = streamJson(value) as JSONObject
        assertEquals("$label key set mismatch", expected.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
        assertTrue("$label mismatch:\nexpected=$expected\nactual=$actual", expected.similar(actual))
    }

    @Test fun fastPathAndFallbackStructsMatchStudyJsonExactly() {
        val spRect = ScanpathRect(1.5, 2.5, 300.0, 600.0)
        val spLine = ScanpathLine(0, 12, 10.0, 290.0, 20.0, 50.0)
        val spMotion = ScanpathMotionCompensation(95.0, "g1", 10.0, 20.0, 45.0, 30.0)
        val spSample = ScanpathSample(
            100.0, 105.0, Double.NaN, 55.0, 0, 0, 5, "g1",
            eligible = true, exclusion = null, displayX = 55.0, displayY = Double.POSITIVE_INFINITY,
            coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED, motionCompensation = spMotion,
        )
        val spGeom = ScanpathGeometry("g1", 10.0, 20.0, spRect, 30.0, listOf(spLine), ScanpathTextDirection.LTR)
        val spCandidate = ScanpathCandidateEvidence(
            "cand:1", 2, 10.0, 90.0, 80.0, 4, 0, 5, 0, 1.0, 1.0, "g1", 95.0, "trigger_observation_delivery",
        )
        val spFixation = ScanpathFixation(
            1, 2, 10.0, 120.0, 110.0, 5, 50.0, 35.0, 4.2, 0, 5, 0.9, 0, 1.0,
            leftCensored = false, rightCensored = true, geometrySnapshotId = "g1",
            evidenceAvailableMs = 125.0, evidenceTimeBasis = "trigger_observation_delivery",
        )
        val spEvent = ScanpathEvent(
            "ev:1", "P12", "line", listOf(1, 2), 10.0, 120.0, 125.0, "trigger_observation_delivery",
            mapOf("kind" to "residence_update", "observed_span_ms" to 110.0, "bad" to Double.NaN),
            listOf("limitation_a"),
        )
        val rdRect = ReadingRect(0.0, 0.0, 400.0, 800.0)
        val rdLine = ReadingLine(0, 20, 5.0, 395.0, 0.0, 32.0)
        val rdMotion = ReadingMotionCompensation(95.0, "rg1", 5.0, 10.0, 50.0, 25.0)
        val rdSample = ReadingSample(
            100.0, 105.0, 55.0, 35.0, 0, 0, 4, "rg1", true, null, 55.0, 35.0,
            ReadingCoordinateStream.ARTICLE_SMOOTHED, rdMotion,
        )
        val rdGeom = ReadingGeometry("rg1", 5.0, 10.0, rdRect, 32.0, listOf(rdLine), ReadingTextDirection.RTL)
        val cont = ReadingAidContinuationEvidence("c:1", 1, 0, 10.0, 100.0, 105.0, 50.0, 55.0)
        val band = ReadingSupportedRowBand(0, 1, 0, true, "measured", 0.0, 60.0)
        val visit = ReadingSupportedVisit("v:1", 1, 3L, "g1", band, 10.0, 100.0, 105.0, 50.0, 55.0)
        val anchor = ReadingAidAnchor(3L, "g1", 0, 0, 5)
        val guideRange = ReadingGuideRange(0, 12, ReadingGuideUnit.READING_BAND)
        val threeLine = ReadingThreeLineGuideEvidence(ReadingGuideDirection.DOWN, 0.08, 0.13, 250.0, listOf(cont))
        val guideEvidence = ReadingAidGuideEvidence(0, 1, "prov", false, 105.0, 10.0, 0.13, listOf("s:1"), threeLine = threeLine)
        val uncertainty = ReadingLocationUncertainty(12.0, 18.0, "pilot_calib")
        val context = ReadingAidContext(
            3L, "g1", 1, 0, 0, 5, 110.0, 100.0, 105.0, true, spGeom, 55.0, spSample,
            true, guideRange, true, 22.0, uncertainty, spRect,
        )
        val bookmarkEvidence = ReadingAidBookmarkEvidence(cont, "outside", 100.0, 600.0, 605.0, 10)
        val focusEvidence = ReadingAidFocusEvidence(
            initialStrength = 0.5, observedRecoveryMs = 100.0, recoveryFadeMs = 7000.0,
            windowMs = 30000.0, support = cont, ruleId = "FOCUS_RULE",
        )
        val action = ReadingAidAction(
            "aid:1", ReadingAidType.CURRENT_LINE, "CURRENT_THREE_LINE_GUIDE_GRAY_V1", "COND",
            anchor, 0.75, null, null, 10.0, 999.0, listOf("ev:1"), listOf("c:1"),
            10.0, 100.0, 105.0, "RULE", listOf("W23"), "rel", true, "EVIDENCE_ALIGNED_V5",
            listOf(cont), bookmarkEvidence, guideRange, focusEvidence, ReadingFocusStyle.FADE_TEXT,
            guideEvidence, "mapped",
        )
        val decision = ReadingAidDecision(
            ReadingAidDecisionStatus.CHOSEN, action, "rule_matched", 110.0, listOf("ev:1"), "RULE", listOf("W23"),
        )

        listOf(
            "ScanpathRect" to spRect,
            "ScanpathLine" to spLine,
            "ScanpathMotionCompensation" to spMotion,
            "ScanpathSample" to spSample,
            "ScanpathGeometry" to spGeom,
            "ScanpathCandidateEvidence" to spCandidate,
            "ScanpathFixation" to spFixation,
            "ScanpathEvent" to spEvent,
            "ReadingRect" to rdRect,
            "ReadingLine" to rdLine,
            "ReadingMotionCompensation" to rdMotion,
            "ReadingSample" to rdSample,
            "ReadingGeometry" to rdGeom,
            "ReadingAidContinuationEvidence" to cont,
            "ReadingSupportedRowBand" to band,
            "ReadingSupportedVisit" to visit,
            "ReadingAidAnchor" to anchor,
            "ReadingGuideRange" to guideRange,
            "ReadingThreeLineGuideEvidence" to threeLine,
            "ReadingAidGuideEvidence" to guideEvidence,
            "ReadingLocationUncertainty" to uncertainty,
            "ReadingAidContext" to context,
            "ReadingAidAction" to action,
            "ReadingAidDecision" to decision,
            "WordRange(fallback)" to WordRange(3, 9),
        ).forEach { (label, value) -> assertJsonParity(label, value) }

        val tricky = mapOf(
            "quote" to "a\"b\\c/d\n\t\r\u0001café😀",
            "floats" to floatArrayOf(1.5f, Float.NaN, Float.NEGATIVE_INFINITY),
            "doubles" to doubleArrayOf(2.0, Double.NaN, Double.POSITIVE_INFINITY),
            "ints" to intArrayOf(1, -2),
            "longs" to longArrayOf(3L, 4L),
            "pair" to (1 to "two"),
            "nullVal" to null,
        )
        assertJsonParity("TrickyMap", tricky)
    }

    @Test fun studyAuditEncoderStreamMatchesRecordParity() {
        val g1 = ScanpathGeometry("g1", 5.0, 10.0, ScanpathRect(0.0, 0.0, 600.0, 800.0), 30.0,
            listOf(ScanpathLine(0, 10, 0.0, 500.0, 0.0, 30.0)))
        val g2 = g1.copy(textOriginY = 40.0)
        fun ctx(g: ScanpathGeometry?, gen: Long = 1L) = ReadingAidContext(
            gen, g?.snapshotId ?: "", g?.lines?.size ?: 0, 0, 0, 4, 100.0, 90.0, 95.0,
            geometry = g, rawSample = ScanpathSample(90.0, 95.0, 20.0, 15.0, 0, 0, 4, g?.snapshotId,
                displayX = 21.0, displayY = 16.0, coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED),
        )
        val legacyEncoder = StudyAuditEncoder()
        val streamEncoder = StudyAuditEncoder()
        val legacyOut = mutableListOf<Triple<String, Double, JSONObject>>()
        val streamOut = mutableListOf<Triple<String, Double, JSONObject>>()
        var rejectLegacy = true
        var rejectStream = true

        val details = listOf(
            "aid_evaluation" to mapOf("input_context" to ctx(g1), "components" to listOf(mapOf("component" to "guide", "status" to "evaluated"))),
            "support_revision" to mapOf("input_context" to ctx(g1), "before_invalidation" to false),
            "aid_evaluation" to mapOf("input_context" to ctx(g2), "components" to emptyList<Any>()),
            "aid_evaluation" to mapOf("input_context" to ctx(g2, 2L), "components" to emptyList<Any>()),
            "aid_evaluation" to mapOf("input_context" to ctx(null), "components" to emptyList<Any>()),
            "other" to mapOf("reason" to "plain", "value" to null),
        )
        details.forEachIndexed { idx, (kind, detail) ->
            val at = 100.0 + idx
            legacyEncoder.record(kind, at, detail) { k, p, t ->
                if (k == "layout_snapshot" && rejectLegacy) { rejectLegacy = false; false }
                else { legacyOut.add(Triple(k, t, p)); true }
            }
            streamEncoder.recordStream(kind, at, detail) { k, t, writePayload ->
                if (k == "layout_snapshot" && rejectStream) { rejectStream = false; false }
                else {
                    val sw = StringWriter()
                    writePayload(StudyJsonWriter(sw))
                    streamOut.add(Triple(k, t, JSONObject(sw.toString())))
                    true
                }
            }
        }
        assertEquals(legacyOut.size, streamOut.size)
        for (i in legacyOut.indices) {
            assertEquals("kind[$i]", legacyOut[i].first, streamOut[i].first)
            assertEquals("time[$i]", legacyOut[i].second, streamOut[i].second, 0.0)
            assertTrue("payload[$i]\nlegacy=${legacyOut[i].third}\nstream=${streamOut[i].third}",
                legacyOut[i].third.similar(streamOut[i].third))
        }
    }

    @Test fun studyPackageWriterRecordStreamWritesValidCompleteChunks() {
        val root = Files.createTempDirectory("study-stream-writer-test").toFile().also { it.deleteOnExit() }
        val id = JSONObject().apply {
            for (k in listOf("study_id", "participant_id", "installation_id", "run_id", "session_id", "clock_domain_id")) put(k, k)
        }
        val writer = StudyPackageWriter(root, id, { 50.0 }, maxChunkBytes = 512)
        val sample = ScanpathSample(10.0, 12.0, 20.0, 30.0, 1, 0, 4, "g1",
            displayX = 21.0, displayY = 31.0, coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED)
        repeat(12) { idx ->
            assertTrue(writer.recordStream("scanpath_input", 10.0 + idx) { w ->
                w.beginObject(); w.fieldInt("generation", idx, first = true); w.field("sample", sample); w.endObject()
            })
        }
        writer.finish("done", 90.0)
        assertTrue(writer.awaitClosed(5000))
        assertNull(writer.failure)
        val manifest = JSONObject(File(writer.packageDirectory, "manifest.json").readText())
        assertTrue(manifest.getBoolean("content_complete"))
        val chunks = manifest.getJSONArray("chunks")
        val rows = (0 until chunks.length()).flatMap { i ->
            val chunk = chunks.getJSONObject(i)
            StudyChunkIO.open(File(writer.packageDirectory, chunk.getString("file")), chunk)
                .bufferedReader().use { it.readLines().map(::JSONObject) }
        }
        assertEquals(15, rows.size)
        assertEquals((0L until 15L).toList(), rows.map { it.getLong("seq") })
        val expectedSample = StudyJson.value(sample) as JSONObject
        val streamedInputs = rows.filter { it.getString("kind") == "scanpath_input" }
        assertEquals(12, streamedInputs.size)
        streamedInputs.forEachIndexed { idx, row ->
            assertEquals(idx, row.getJSONObject("payload").getInt("generation"))
            assertTrue(expectedSample.similar(row.getJSONObject("payload").getJSONObject("sample")))
        }
    }

    @Test fun modeOnForceGuideDoesNotThrashAcrossRepeatedTicks() {
        val geometry = ScanpathGeometry("g1", 0.0, 0.0, ScanpathRect(0.0, 0.0, 400.0, 800.0), 30.0,
            (0 until 10).map { ScanpathLine(it * 10, it * 10 + 8, 10.0, 390.0, it * 30.0, (it + 1) * 30.0) },
            ScanpathTextDirection.LTR)
        val policy = ReadingAidPolicy(
            ReadingAidPolicyConfig.evidenceAlignedPrototype(),
            ReaderAidPreferences(mode = ReadingAidsMode.ON, focusRegion = false, lineStartCue = false, wordDefinition = false),
        )
        fun ctx(now: Double) = ReadingAidContext(1L, "g1", 10, 2, null, null, now, now, now, true, geometry = geometry)
        val first = policy.tick(ctx(100.0))
        assertEquals(1, first.size)
        assertEquals(ReadingAidDecisionStatus.CHOSEN, first.single().status)
        val initialActionId = first.single().action!!.id
        val second = policy.tick(ctx(120.0))
        val third = policy.tick(ctx(140.0))
        assertTrue("Expected no churn on steady ON tick: $second", second.isEmpty())
        assertTrue("Expected no churn on steady ON tick: $third", third.isEmpty())
        assertEquals(initialActionId, policy.activeActions().single().id)
        assertFalse(policy.activeActions().isEmpty())
    }

    @Test fun auditDirtyGuardEmitsBoundaryAndResetSupportRevisionsAccurately() {
        val geometry = ScanpathGeometry("g1", 0.0, 0.0, ScanpathRect(0.0, 0.0, 400.0, 800.0), 30.0,
            (0 until 10).map { ScanpathLine(it * 10, it * 10 + 8, 10.0, 390.0, it * 30.0, (it + 1) * 30.0) },
            ScanpathTextDirection.LTR)
        val revisions = mutableListOf<Map<String, Any?>>()
        val policy = ReadingAidPolicy(
            ReadingAidPolicyConfig.evidenceAlignedPrototype(),
            ReaderAidPreferences(mode = ReadingAidsMode.AUTO),
            onAudit = { kind, _, detail -> if (kind == "support_revision") revisions.add(detail) },
        )
        fun sampleCtx(t: Double, line: Int, allowed: Boolean = true): ReadingAidContext {
            val y = line * 30.0 + 15.0
            val s = ScanpathSample(t, t, 55.0, y, line, line * 10, line * 10 + 4, "g1", true, null, 55.0, y,
                ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            return ReadingAidContext(1L, "g1", 10, line, line * 10, line * 10 + 4, t, t, t, true, geometry, 55.0, s,
                bookmarkInputAllowed = allowed, focusInputAllowed = allowed)
        }
        // Accumulate partial location state without activating an aid yet, then hit operational boundary.
        policy.onObservation(null, sampleCtx(100.0, 2, allowed = true))
        val beforeBoundary = revisions.size
        assertTrue(beforeBoundary > 0)
        policy.onObservation(null, sampleCtx(140.0, 2, allowed = false))
        assertTrue("Expected post-boundary support_revision after partial state reset", revisions.size > beforeBoundary)
        val afterFirstBoundary = revisions.size
        // Repeated steady boundary ticks must not emit duplicate support_revision rows.
        policy.onObservation(null, sampleCtx(180.0, 2, allowed = false))
        policy.onObservation(null, sampleCtx(220.0, 2, allowed = false))
        assertEquals(afterFirstBoundary, revisions.size)
        // Explicit reset always emits before_invalidation=true.
        policy.reset(sampleCtx(260.0, 2, allowed = true))
        assertTrue(revisions.any { it["before_invalidation"] == true })
    }
}
