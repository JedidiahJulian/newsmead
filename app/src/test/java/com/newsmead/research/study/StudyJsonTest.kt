package com.newsmead.research.study

import com.newsmead.gaze.aid.ReadingAidContext
import com.newsmead.gaze.scanpath.ScanpathLine
import com.newsmead.gaze.scanpath.ScanpathRect
import com.newsmead.gaze.scanpath.WordRange
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StudyJsonTest {
    @Test fun nativePrivateFieldsAndNamesRetainTheirValues() {
        val word = StudyJson.value(WordRange(7, 12)) as JSONObject
        assertTrue(word.similar(JSONObject("""{"start_utf16":7,"end_utf16":12}""")))
        assertEquals("geometry_snapshot_id", StudyJson.snake("geometrySnapshotId"))
        assertEquals("already_snake", StudyJson.snake("already_snake"))
        assertEquals("raw_x2_px", StudyJson.snake("rawX2Px"))
        assertEquals("urlvalue", StudyJson.snake("URLValue"))
        // Cache metadata, never an object's values or mutable output tree.
        word.put("start_utf16", 99)
        assertEquals(3, (StudyJson.value(WordRange(3, 4)) as JSONObject).getInt("start_utf16"))
    }

    @Test fun nestedNativeContextPreservesNullsNonfiniteValuesAndSupportedContainers() {
        val context = ReadingAidContext(2, "geometry-1", 3, null, 7, 12,
            100.0, Double.NaN, 110.0, targetX = Double.POSITIVE_INFINITY,
            targetWordBounds = ScanpathRect(1.0, 2.0, 3.0, 4.0))
        val actual = StudyJson.obj("context" to context,
            "values" to listOf(null, Double.NEGATIVE_INFINITY, Float.NaN, true, "text"),
            "pair" to (1 to "two"), "numbers" to doubleArrayOf(2.0, Double.NaN),
            "floats" to floatArrayOf(3.0f, Float.POSITIVE_INFINITY),
            "ints" to intArrayOf(4), "longs" to longArrayOf(5), "array" to arrayOf("six"))
        val expected = JSONObject("""{
          "context":{"generation":2,"geometry_snapshot_id":"geometry-1","line_count":3,
            "target_line":null,"target_word_start_utf16":7,"target_word_end_utf16":12,
            "now_ms":100.0,"capture_ms":null,"evidence_available_ms":110.0,"valid_live_input":true,
            "geometry":null,"target_x":null,"raw_sample":null,"bookmark_input_allowed":true,
            "guide_range":null,"focus_input_allowed":true,"focus_text_size_dp":22.0,
            "location_uncertainty":null,"target_word_bounds":{"left":1.0,"top":2.0,"right":3.0,"bottom":4.0}},
          "values":[null,null,null,true,"text"],"pair":[1,"two"],"numbers":[2.0,null],
          "floats":[3.0,null],"ints":[4],"longs":[5],"array":["six"]}
        """)
        assertTrue(actual.toString(), actual.similar(expected))
        val json = JSONObject().put("native", 1)
        val array = JSONArray().put(2)
        assertSame(json, StudyJson.value(json)); assertSame(array, StudyJson.value(array))
        assertSame(JSONObject.NULL, StudyJson.value(JSONObject.NULL))
        try { StudyJson.value(Any()); fail("Expected unsupported object rejection") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun concurrentMetadataInitializationDoesNotShareSerializedValues() {
        val start = CountDownLatch(1); val done = CountDownLatch(8)
        val failures = ConcurrentLinkedQueue<Throwable>()
        repeat(8) { worker ->
            Thread {
                try {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(50) { index ->
                        val first = worker * 100 + index
                        val actual = StudyJson.value(ScanpathLine(first, first + 1, 1.0, 2.0, 3.0, 4.0)) as JSONObject
                        val expected = JSONObject().put("start_utf16", first).put("end_utf16", first + 1)
                            .put("left", 1.0).put("right", 2.0).put("top", 3.0).put("bottom", 4.0)
                        assertTrue(actual.toString(), actual.similar(expected))
                    }
                } catch (error: Throwable) { failures.add(error) } finally { done.countDown() }
            }.start()
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertTrue(failures.toString(), failures.isEmpty())
    }
}
