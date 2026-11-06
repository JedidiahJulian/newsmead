package com.newsmead.research.study

import com.newsmead.gaze.aid.ReadingAidContext
import com.newsmead.gaze.scanpath.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyAuditEncoderTest {
    private fun geometry(id: String = "layout", lines: Int = 400) = ScanpathGeometry(id, 5.0, 10.0,
        ScanpathRect(0.0, 0.0, 600.0, 800.0), 30.0,
        (0 until lines).map { ScanpathLine(it * 10, it * 10 + 9, 0.0, 590.0, it * 30.0, it * 30.0 + 30) })
    private fun context(g: ScanpathGeometry?, generation: Long = 1) = ReadingAidContext(
        generation, g?.snapshotId ?: "unavailable", g?.lines?.size ?: 0, 2, 20, 25,
        100.0, 90.0, 95.0, geometry = g, rawSample = ScanpathSample(90.0, 95.0, 30.0, 75.0,
            2, 20, 25, g?.snapshotId, displayX = 31.0, displayY = 76.0,
            coordinateStream = ScanpathCoordinateStream.ARTICLE_SMOOTHED))

    @Test fun sharedLargeLayoutIsEmittedOnceAndNativeContextCanBeReconstructed() {
        val encoder = StudyAuditEncoder()
        val g = geometry()
        val native = context(g)
        val events = mutableListOf<Pair<String, JSONObject>>()
        val times = mutableListOf<Double>()
        repeat(100) { index -> encoder.record("aid_evaluation", 100.0 + index,
            mapOf("input_context" to native, "components" to listOf("guide", "focus"))) { k, p, t ->
                events.add(k to p); times.add(t); true
            } }
        assertEquals(1, events.count { it.first == "layout_snapshot" })
        val layout = events.first().second
        assertEquals(StudyAuditEncoder.PRODUCER, layout.getString("producer"))
        val expected = StudyJson.value(native) as JSONObject
        events.drop(1).forEach { (_, event) ->
            assertEquals(StudyAuditEncoder.VERSION, event.getString("audit_payload_version"))
            val input = event.getJSONObject("input_context")
            assertFalse(input.has("geometry"))
            val ref = input.getJSONObject("geometry_ref")
            assertEquals(layout.getLong("generation"), ref.getLong("generation"))
            assertEquals(layout.getLong("layout_revision"), ref.getLong("layout_revision"))
            assertEquals(layout.getJSONObject("geometry").getString("snapshot_id"), ref.getString("geometry_snapshot_id"))
            val restored = JSONObject(input.toString())
            restored.remove("geometry_ref"); restored.put("geometry", layout.getJSONObject("geometry"))
            assertTrue(expected.similar(restored))
            assertTrue("Repeated audit should not scale with article row count", input.toString().length < 2500)
        }
        assertEquals(listOf(100.0, 100.0), times.take(2))
        assertSame(g, native.geometry)
    }

    @Test fun geometryOrGenerationChangeEmitsNewLayoutAndRejectedSnapshotIsRetried() {
        val encoder = StudyAuditEncoder()
        val a = geometry("a", 2); val b = geometry("b", 3)
        val accepted = mutableListOf<Pair<String, JSONObject>>()
        var rejectFirst = true
        val send: (String, JSONObject, Double) -> Boolean = { k, p, _ ->
            if (k == "layout_snapshot" && rejectFirst) { rejectFirst = false; false }
            else { accepted.add(k to p); true }
        }
        fun emit(c: ReadingAidContext) = encoder.record("support_revision", 100.0, mapOf("input_context" to c), send)
        emit(context(a)); emit(context(a)); emit(context(a)); emit(context(b)); emit(context(b, 2))
        val layouts = accepted.filter { it.first == "layout_snapshot" }.map { it.second }
        assertEquals(3, layouts.size)
        assertEquals(listOf("a", "b", "b"), layouts.map { it.getJSONObject("geometry").getString("snapshot_id") })
        assertEquals(listOf(1L, 1L, 2L), layouts.map { it.getLong("generation") })
        assertEquals(5, accepted.count { it.first == "support_revision" })
    }

    @Test fun missingGeometryAndNonContextAuditRetainTheirValuesWithoutInventingLayout() {
        val encoder = StudyAuditEncoder()
        val events = mutableListOf<Pair<String, JSONObject>>()
        val send: (String, JSONObject, Double) -> Boolean = { k, p, _ -> events.add(k to p); true }
        encoder.record("aid_evaluation", 1.0, mapOf("input_context" to context(null)), send)
        encoder.record("other", 2.0, mapOf("reason" to "unchanged", "value" to null), send)
        assertEquals(listOf("aid_evaluation", "other"), events.map { it.first })
        assertTrue(events[0].second.getJSONObject("input_context").isNull("geometry_ref"))
        assertTrue(StudyJson.obj("reason" to "unchanged", "value" to null).similar(events[1].second))
    }

    @Test fun translatedNativeIdCannotAliasEarlierLayoutEvenAfterRejectedSnapshot() {
        val encoder = StudyAuditEncoder()
        val a = geometry("same-native-id", 2)
        val b = a.copy(textOriginY = 45.0, visibleRectScreen = ScanpathRect(0.0, 45.0, 600.0, 800.0))
        val events = mutableListOf<Pair<String, JSONObject>>()
        var reject = false
        val send: (String, JSONObject, Double) -> Boolean = { k, p, _ ->
            if (k == "layout_snapshot" && reject) { reject = false; false }
            else { events.add(k to p); true }
        }
        fun emit(g: ScanpathGeometry) = encoder.record("aid_evaluation", 100.0, mapOf("input_context" to context(g)), send)
        emit(a); reject = true; emit(b); emit(b)
        val snapshots = events.filter { it.first == "layout_snapshot" }.associate {
            it.second.getLong("layout_revision") to it.second.getJSONObject("geometry") }
        val refs = events.filter { it.first == "aid_evaluation" }.map {
            it.second.getJSONObject("input_context").getJSONObject("geometry_ref").getLong("layout_revision") }
        assertEquals(3, refs.distinct().size)
        assertEquals(10.0, snapshots.getValue(refs[0]).getDouble("text_origin_y"), 0.0)
        assertFalse("Rejected translation must not resolve to an old or later layout", snapshots.containsKey(refs[1]))
        assertEquals(45.0, snapshots.getValue(refs[2]).getDouble("text_origin_y"), 0.0)
    }
}
