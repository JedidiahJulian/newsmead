package com.newsmead.gaze.scanpath.replay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.activities.ScanpathReplayResult
import com.newsmead.gaze.aid.ReaderAidPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real laid-out activity, raw scripted input and normal engine/policy/views; no camera. */
@RunWith(AndroidJUnit4::class)
class ScanpathReplayActivityTest {
    @Test fun cappedTraceRetainsAnIncompleteFooterAndPartialFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recorder = ScanpathReplayLog(context, ScanpathReplayScenarios.scenarios.first(), "synthetic",
            JSONObject(), emptyList(), 0.0, recordLimit = 4)
        repeat(4) { recorder.append("test_record", JSONObject()) }
        recorder.close("trace_failure", JSONObject())
        assertEquals("record_limit", recorder.failure)
        assertTrue(recorder.file.name.endsWith(".partial"))
        val records = recorder.file.readLines().map(::JSONObject)
        assertEquals(4, records.size)
        val footer = records.last()
        assertEquals("footer", footer.getString("kind"))
        assertFalse(footer.getJSONObject("detail").getBoolean("complete_trace"))
        assertEquals("record_limit", footer.getJSONObject("detail").getString("trace_failure"))
    }

    private fun select(activity: ActivityScenario<ScanpathReplayActivity>, id: String, preferences: ReaderAidPreferences) {
        activity.onActivity {
            it.setReaderPreferences(preferences)
            it.findViewById<Spinner>(R.id.replayScenario).setSelection(ScanpathReplayScenarios.scenarios.indexOfFirst { s -> s.id == id })
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activity.onActivity { it.startReplay() }
    }
    private fun await(activity: ActivityScenario<ScanpathReplayActivity>, capturePreview: Boolean = false): ScanpathReplayResult {
        val deadline = SystemClock.elapsedRealtime() + 10000
        var result: ScanpathReplayResult? = null
        var bitmap: Bitmap? = null
        while (result == null && SystemClock.elapsedRealtime() < deadline) {
            activity.onActivity { a ->
                result = a.lastResult
                if (capturePreview && bitmap == null && a.findViewById<TextView>(R.id.replayStatus).text.contains("WORD_MAGNIFIER")) {
                    val root = a.findViewById<android.view.View>(R.id.replayRoot)
                    bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
                }
            }
            if (result == null) Thread.sleep(25)
        }
        bitmap?.let { preview ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            try { File(context.noBackupFilesDir, "scanpath_replay_word_preview.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { preview.recycle() }
        }
        return checkNotNull(result) { "Replay did not finish within 10 seconds." }
    }

    @Test fun allScenariosUseActualLayoutAndProduceExpectedPatternsAndDrawReceipts() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            ScanpathReplayScenarios.scenarios.forEach { scenario ->
                select(activity, scenario.id, ReaderAidPreferences(focusRegion = true))
                val result = await(activity, scenario.id == "word_refixations")
                assertEquals("${scenario.id}: ${result.termination}", "completed", result.termination)
                assertNull(result.traceFailure)
                assertTrue("${scenario.id}: patterns ${result.patterns}", result.patterns.containsAll(scenario.expectedPatternIds))
                assertTrue("${scenario.id}: chosen ${result.chosenAids}", result.chosenAids.containsAll(scenario.expectedAidTypes))
                assertTrue("${scenario.id}: drawn ${result.drawnAids}", result.drawnAids.containsAll(scenario.expectedAidTypes))
                val records = result.trace!!.readLines().map(::JSONObject)
                assertEquals("header", records.first().getString("kind"))
                val header = records.first().getJSONObject("detail")
                assertEquals("synthetic_replay", header.getString("input_kind"))
                assertEquals("synthetic_coordinates", header.getJSONObject("configuration").getString("model"))
                assertTrue(header.getJSONObject("configuration").isNull("calibration_sha256"))
                assertFalse(header.getBoolean("human_reference"))
                assertEquals(result.samples, records.count { it.getString("kind") == "sample" })
                assertCausalTrace(records)
                val footer = records.last()
                assertEquals("footer", footer.getString("kind"))
                assertTrue(footer.getJSONObject("detail").getBoolean("complete_trace"))
                assertTrue(footer.getJSONObject("detail").getBoolean("software_expectations_met"))
            }
        }
    }

    private fun assertCausalTrace(records: List<JSONObject>) {
        val events = mutableSetOf<String>()
        val evidence = mutableSetOf<String>()
        val actions = mutableMapOf<String, JSONObject>()
        records.forEach { record ->
            val d = record.getJSONObject("detail")
            when (record.getString("kind")) {
                "sample" -> {
                    val capture = d.getDouble("capture_ms")
                    val delivery = d.getDouble("delivery_ms")
                    assertTrue("capture precedes actual delivery", capture <= delivery)
                    assertTrue("scheduler lag remains bounded", delivery - capture <= 100.0)
                    assertTrue("delivery precedes recording", delivery <= record.getDouble("record_elapsed_ms"))
                }
                "pattern" -> events.add(d.getString("id"))
                "candidate_prefix" -> evidence.add(d.getString("id"))
                "fixation" -> evidence.add("fixation:${d.getInt("index")}")
                "decision" -> if (d.getString("status") == "CHOSEN") {
                    val action = d.getJSONObject("action")
                    val eventIds = action.getJSONArray("event_ids")
                    val evidenceIds = action.getJSONArray("evidence_ids")
                    assertTrue("chosen action has prior support", eventIds.length() + evidenceIds.length() > 0)
                    repeat(eventIds.length()) { assertTrue("event precedes action", eventIds.getString(it) in events) }
                    repeat(evidenceIds.length()) {
                        val id = evidenceIds.getString(it)
                        assertTrue("support $id precedes action", id in evidence)
                    }
                    assertTrue(action.getDouble("last_capture_ms") <= action.getDouble("evidence_available_ms"))
                    assertTrue(action.getDouble("evidence_available_ms") <= d.getDouble("decision_ms"))
                    actions[action.getString("id")] = action
                }
                "render" -> if (d.getBoolean("drawn_proxy")) {
                    val action = checkNotNull(actions[d.getString("action_id")]) { "Draw receipt has no prior choice." }
                    val at = d.getDouble("render_ms")
                    assertTrue("draw follows onset", at >= action.getDouble("onset_ms"))
                    assertTrue("draw precedes expiry", at < action.getDouble("expires_ms"))
                }
            }
        }
    }

    @Test fun zeroStrengthWithholdsEveryAidWhileTheDetectorStillRuns() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            select(activity, "word_refixations", ReaderAidPreferences(maxStrength = 0.0))
            val result = await(activity)
            assertEquals("completed", result.termination)
            assertTrue(result.patterns.contains("P04"))
            assertTrue(result.chosenAids.isEmpty())
            assertTrue(result.drawnAids.isEmpty())
        }
    }

    @Test fun scrollingInterruptsTheReplayAndWritesTheBoundaryInsteadOfUsingOldCoordinates() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            select(activity, "word_refixations", ReaderAidPreferences())
            Thread.sleep(550)
            activity.onActivity { it.findViewById<androidx.core.widget.NestedScrollView>(R.id.replayScroll).scrollTo(0, 150) }
            val result = await(activity)
            assertEquals("geometry_changed", result.termination)
            assertTrue(result.patterns.contains("P13"))
            assertTrue(result.samples > 0)
            assertFalse(result.trace!!.readLines().map(::JSONObject).last().getJSONObject("detail").getBoolean("software_expectations_met"))
        }
    }

    @Test fun pausingWithoutStoppingTheActivityTerminatesTheReplay() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            select(activity, "word_refixations", ReaderAidPreferences())
            Thread.sleep(550)
            activity.moveToState(Lifecycle.State.STARTED)
            activity.moveToState(Lifecycle.State.RESUMED)
            val result = await(activity)
            assertEquals("activity_paused", result.termination)
            assertTrue(result.samples > 0)
            activity.onActivity { assertFalse(it.isReplaying) }
        }
    }
}
