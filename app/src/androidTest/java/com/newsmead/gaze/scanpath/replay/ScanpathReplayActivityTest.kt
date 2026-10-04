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
        var diagnostic = "Activity did not provide a replay snapshot."
        while (result == null && SystemClock.elapsedRealtime() < deadline) {
            activity.onActivity { a ->
                result = a.lastResult
                val root = a.findViewById<android.view.View>(R.id.replayRoot)
                val body = a.findViewById<TextView>(R.id.replayBody)
                diagnostic = "replaying=${a.isReplaying} termination=${result?.termination} samples=${result?.samples} trace=${result?.trace?.name}\n" +
                    "status=${a.findViewById<TextView>(R.id.replayStatus).text}\n" +
                    "root=${root.width}x${root.height} body=${body.width}x${body.height} lines=${body.layout?.lineCount}\n"
                if (capturePreview && bitmap == null && a.findViewById<TextView>(R.id.replayStatus).text.contains("WORD_DEFINITION")) {
                    bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
                }
            }
            if (result == null) Thread.sleep(25)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.noBackupFilesDir, "scanpath_replay_last_status.txt")
            .appendText("elapsed_ns=${SystemClock.elapsedRealtimeNanos()}\n$diagnostic\n")
        bitmap?.let { preview ->
            try { File(context.noBackupFilesDir, "scanpath_replay_word_preview.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { preview.recycle() }
        }
        return checkNotNull(result) { "Replay did not finish within 10 seconds: $diagnostic" }
    }

    @Test fun allScenariosWithAidsDisabledRetainLegacyDetectorOutcomes() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            ScanpathReplayScenarios.scenarios.forEach { scenario ->
                select(activity, scenario.id, ReaderAidPreferences(enabled = false, wordDefinition = false))
                val result = await(activity, scenario.id == "word_refixations")
                assertEquals("${scenario.id}: ${result.termination}", "completed", result.termination)
                assertNull(result.traceFailure)
                assertTrue("${scenario.id}: patterns ${result.patterns}", result.patterns.containsAll(scenario.expectedPatternIds))
                assertTrue(result.chosenAids.isEmpty())
                assertTrue(result.drawnAids.isEmpty())
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

    @Test fun currentDefinitionProfileDrawsPopupAndRetainsExcludedRawCallbacks() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            select(activity, "word_refixations", ReaderAidPreferences())
            val result = await(activity, capturePreview = true)
            assertEquals("completed", result.termination)
            assertNull(result.traceFailure)
            assertTrue(result.patterns.containsAll(setOf("P04", "P13")))
            assertTrue(result.chosenAids.containsAll(setOf("CURRENT_LINE", "WORD_DEFINITION")))
            assertTrue(result.drawnAids.containsAll(setOf("CURRENT_LINE", "WORD_DEFINITION")))
            assertFalse(result.chosenAids.contains("WORD_MAGNIFIER"))
            val records = result.trace!!.readLines().map(::JSONObject)
            val config = records.first().getJSONObject("detail").getJSONObject("configuration")
            assertEquals("DEFINITION_PROFILE_V2", config.getJSONObject("settings").getString("config_id"))
            assertEquals("REPLAY_DEFINITION_V2", config.getJSONObject("settings").getString("condition_id"))
            assertEquals(3, config.getInt("policy_version"))
            assertEquals("fixed", config.getJSONObject("settings").getString("line_start_marker_strength_mode"))
            assertEquals("bundled_only", config.getString("dictionary_mode"))
            assertEquals(0, config.getInt("reserved_word_strip_dp"))
            val boundaries = records.filter { it.getString("kind") == "definition_consultation" }
                .map { it.getJSONObject("detail") }
            assertEquals("one open and one close; no consultation retarget", 2, boundaries.size)
            assertTrue(boundaries[0].getBoolean("active"))
            assertFalse(boundaries[1].getBoolean("active"))
            assertEquals("completed", boundaries[1].getString("reason"))
            val opened = boundaries[0].getDouble("at_ms")
            val closed = boundaries[1].getDouble("at_ms")
            assertTrue(closed >= opened)
            val excluded = records.filter { it.getString("kind") == "sample" }
                .map { it.getJSONObject("detail") }
                .filter { it.optString("exclusion").startsWith("definition_") }
            assertTrue("consultation retains raw callbacks", excluded.isNotEmpty())
            excluded.forEach {
                assertFalse(it.getBoolean("eligible"))
                assertFalse(it.isNull("x")); assertFalse(it.isNull("y"))
                assertTrue(it.isNull("line")); assertTrue(it.isNull("word_start_utf16"))
                assertTrue(it.getDouble("delivery_ms") >= opened)
                if (it.getString("exclusion") == "definition_boundary_late_capture")
                    assertTrue(it.getDouble("capture_ms") < opened)
                else assertTrue(it.getDouble("capture_ms") >= opened)
                assertTrue(it.getDouble("delivery_ms") <= closed)
            }
            assertEquals(result.samples, records.count { it.getString("kind") == "sample" })
            assertCausalTrace(records)
            assertTrue(records.last().getJSONObject("detail").getBoolean("software_expectations_met"))
            activity.onActivity { assertNull(it.findViewById<android.view.View>(R.id.definitionWord)) }
        }
    }

    @Test fun definitionEnabledLineCycleKeepsCurrentProfileOutcomesAndConsultationExclusions() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            select(activity, "line_cycle", ReaderAidPreferences(wordDefinition = true, focusRegion = true))
            val result = await(activity)
            assertEquals("completed", result.termination)
            assertNull(result.traceFailure)
            // Explicit current outcomes: consultation preempts later residence/focus evidence.
            assertTrue(result.patterns.containsAll(setOf("P08", "P10", "P11", "P13")))
            assertTrue(result.chosenAids.containsAll(setOf("CURRENT_LINE", "WORD_DEFINITION")))
            assertTrue(result.drawnAids.containsAll(setOf("CURRENT_LINE", "WORD_DEFINITION")))
            assertFalse(result.chosenAids.contains("WORD_MAGNIFIER"))
            assertFalse(result.chosenAids.contains("FOCUS_REGION"))
            val records = result.trace!!.readLines().map(::JSONObject)
            val settings = records.first().getJSONObject("detail").getJSONObject("configuration").getJSONObject("settings")
            assertEquals("DEFINITION_PROFILE_V2", settings.getString("config_id"))
            assertEquals("REPLAY_DEFINITION_V2", settings.getString("condition_id"))
            val boundaries = records.filter { it.getString("kind") == "definition_consultation" }
                .map { it.getJSONObject("detail") }
            assertEquals("one fixed-source consultation", 2, boundaries.size)
            assertTrue(boundaries.first().getBoolean("active"))
            assertFalse(boundaries.last().getBoolean("active"))
            assertEquals("completed", boundaries.last().getString("reason"))
            val opened = boundaries.first().getDouble("at_ms")
            val excluded = records.filter { it.getString("kind") == "sample" }
                .map { it.getJSONObject("detail") }
                .filter { it.optString("exclusion").startsWith("definition_") }
            assertTrue("line-cycle consultation retains excluded raw samples", excluded.isNotEmpty())
            excluded.forEach {
                assertFalse(it.getBoolean("eligible"))
                assertFalse(it.isNull("x")); assertFalse(it.isNull("y"))
                assertTrue(it.isNull("line")); assertTrue(it.isNull("word_start_utf16"))
                assertTrue(it.getDouble("delivery_ms") >= opened)
            }
            assertEquals(result.samples, records.count { it.getString("kind") == "sample" })
            assertCausalTrace(records)
            assertTrue(records.last().getJSONObject("detail").getBoolean("software_expectations_met"))
        }
    }

    @Test fun changingControlsClosesConsultationAndFreshRunDoesNotReusePopup() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            select(activity, "word_refixations", ReaderAidPreferences())
            val deadline = SystemClock.elapsedRealtime() + 4000
            var showing = false
            while (!showing && SystemClock.elapsedRealtime() < deadline) {
                activity.onActivity { showing = it.findViewById<android.view.View>(R.id.definitionWord) != null }
                if (!showing) Thread.sleep(25)
            }
            assertTrue("Current replay did not open its definition", showing)
            activity.onActivity {
                it.setReaderPreferences(ReaderAidPreferences(wordDefinition = false))
                assertFalse(it.isReplaying)
                assertNull(it.findViewById<android.view.View>(R.id.definitionWord))
            }
            val stopped = await(activity)
            assertEquals("reader_controls_changed", stopped.termination)
            val boundaries = stopped.trace!!.readLines().map(::JSONObject)
                .filter { it.getString("kind") == "definition_consultation" }
            assertFalse(boundaries.last().getJSONObject("detail").getBoolean("active"))
            assertEquals("reader_controls_changed", boundaries.last().getJSONObject("detail").getString("reason"))
            activity.onActivity { it.startReplay() }
            val fresh = await(activity)
            assertEquals("completed", fresh.termination)
            assertTrue(fresh.patterns.containsAll(setOf("P04", "P12")))
            assertFalse(fresh.chosenAids.contains("WORD_DEFINITION"))
            assertNotEquals(stopped.trace, fresh.trace)
            activity.onActivity { assertNull(it.findViewById<android.view.View>(R.id.definitionWord)) }
        }
    }

    @Test fun currentLineStartAndOptInFocusRenderWithDefinitionsDisabled() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            listOf("navigation", "return_sweep", "continuation_return", "continuation_skip", "line_cycle", "loss_recovery").forEach { id ->
                select(activity, id, ReaderAidPreferences(wordDefinition = false, focusRegion = true))
                val result = await(activity)
                val fixture = ScanpathReplayScenarios.scenarios.single { it.id == id }
                assertEquals("completed", result.termination)
                assertTrue(result.patterns.containsAll(fixture.expectedPatternIds))
                assertTrue(result.chosenAids.containsAll(fixture.expectedAidTypes))
                assertTrue(result.drawnAids.containsAll(fixture.expectedAidTypes))
                assertFalse(result.chosenAids.contains("WORD_DEFINITION"))
                assertCausalTrace(result.trace!!.readLines().map(::JSONObject))
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
                    if (action.getString("aid_type") == "NEXT_LINE_START") {
                        assertEquals("A2_CONTINUATION_MISMATCH_V1", action.getString("rule_id"))
                        assertEquals(1.0, action.getDouble("strength"), 0.0)
                        val supports = action.getJSONArray("continuation_evidence")
                        assertEquals(3, supports.length())
                        val source = supports.getJSONObject(0).getInt("line")
                        val landing = supports.getJSONObject(2).getInt("line")
                        assertEquals(source + 1, action.getInt("line"))
                        assertNotEquals(landing, action.getInt("line"))
                        repeat(supports.length()) { index ->
                            val support = supports.getJSONObject(index)
                            assertTrue(support.getString("evidence_id") in evidence)
                            assertTrue(support.getDouble("last_capture_ms") <= support.getDouble("evidence_available_ms"))
                            assertTrue(support.getDouble("evidence_available_ms") <= d.getDouble("decision_ms"))
                        }
                    }
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
