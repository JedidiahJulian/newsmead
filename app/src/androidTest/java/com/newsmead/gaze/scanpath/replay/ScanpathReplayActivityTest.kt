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
import com.newsmead.gaze.aid.ReadingGuideStyle
import com.newsmead.gaze.aid.ReadingFocusStyle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real laid-out activity, post-filter scripted input and normal engine/policy/views; no camera. */
@RunWith(AndroidJUnit4::class)
class ScanpathReplayActivityTest {
    @Test fun sentenceSelectionProducesSeparateRangeAndNoLineGuide() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            select(activity, ScanpathReplayScenarios.scenarios.first().id,
                ReaderAidPreferences(guideStyle = ReadingGuideStyle.SENTENCE, wordDefinition = false, focusRegion = false))
            val result = await(activity)
            assertEquals("completed", result.termination)
            assertTrue(result.chosenAids.contains("CURRENT_SENTENCE"))
            assertTrue(result.drawnAids.contains("CURRENT_SENTENCE"))
            assertFalse(result.chosenAids.contains("CURRENT_LINE"))
            val choices = result.trace!!.readLines().map(::JSONObject).filter { it.optString("kind") == "decision" }
                .mapNotNull { it.getJSONObject("detail").optJSONObject("action") }
                .filter { it.optString("aid_type") == "CURRENT_SENTENCE" }
            assertTrue(choices.isNotEmpty())
            choices.forEach {
                assertTrue(it.isNull("word_start_utf16")); assertTrue(it.isNull("word_end_utf16"))
                val range = it.getJSONObject("guide_range")
                assertTrue(range.getInt("end_utf16") > range.getInt("start_utf16"))
                assertTrue(range.getString("unit") in setOf("SENTENCE", "PARAGRAPH"))
            }
        }
    }

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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Initial Spinner state restoration can follow ActivityScenario's RESUMED callback.
        var laidOut = false
        val layoutDeadline = SystemClock.elapsedRealtime() + 3000
        while (!laidOut && SystemClock.elapsedRealtime() < layoutDeadline) {
            activity.onActivity { laidOut = it.findViewById<android.view.View>(R.id.replayRoot).isLaidOut }
            if (!laidOut) Thread.sleep(25)
        }
        check(laidOut) { "Replay layout did not become ready before scenario selection." }
        instrumentation.waitForIdleSync()
        val index = ScanpathReplayScenarios.scenarios.indexOfFirst { it.id == id }
        check(index >= 0) { "Unknown replay scenario: $id" }
        val label = ScanpathReplayScenarios.scenarios[index].label
        activity.onActivity {
            it.setReaderPreferences(preferences)
            it.findViewById<Spinner>(R.id.replayScenario).setSelection(index, false)
        }
        var selectionLaidOut = false
        val selectionDeadline = SystemClock.elapsedRealtime() + 3000
        while (!selectionLaidOut && SystemClock.elapsedRealtime() < selectionDeadline) {
            activity.onActivity {
                val spinner = it.findViewById<Spinner>(R.id.replayScenario)
                selectionLaidOut = spinner.selectedItemPosition == index &&
                    (spinner.selectedView as? TextView)?.text?.toString() == label
            }
            if (!selectionLaidOut) Thread.sleep(25)
        }
        check(selectionLaidOut) { "Replay selector did not display requested scenario: $id" }
        instrumentation.waitForIdleSync()
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
                assertEquals(2, header.getInt("schema_version"))
                assertEquals("article_smoothed_v1", header.getString("coordinate_stream"))
                assertEquals("post_filter_synthetic_trajectory", header.getString("input_phase"))
                assertFalse(header.getBoolean("smoothing_applied_to_synthetic_input"))
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
            assertEquals("DEFINITION_PROFILE_V4", config.getJSONObject("settings").getString("config_id"))
            assertEquals("SMOOTHED_REPLAY_DEFINITION_V9", config.getJSONObject("settings").getString("condition_id"))
            assertEquals(14, config.getInt("policy_version"))
            assertEquals(1500.0, config.getJSONObject("settings").getDouble("definition_min_observed_ms"), 0.0)
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
            // The independent line-cycle route may precede consultation; consultation suspends new choices.
            assertTrue(result.patterns.containsAll(setOf("P08", "P10", "P11", "P13")))
            assertTrue(result.chosenAids.containsAll(setOf("CURRENT_LINE", "WORD_DEFINITION")))
            assertTrue(result.drawnAids.containsAll(setOf("CURRENT_LINE", "WORD_DEFINITION")))
            assertFalse(result.chosenAids.contains("WORD_MAGNIFIER"))
            val records = result.trace!!.readLines().map(::JSONObject)
            val settings = records.first().getJSONObject("detail").getJSONObject("configuration").getJSONObject("settings")
            assertEquals("DEFINITION_PROFILE_V4", settings.getString("config_id"))
            assertEquals("SMOOTHED_REPLAY_DEFINITION_V9", settings.getString("condition_id"))
            val boundaries = records.filter { it.getString("kind") == "definition_consultation" }
                .map { it.getJSONObject("detail") }
            assertEquals("one fixed-source consultation", 2, boundaries.size)
            assertTrue(boundaries.first().getBoolean("active"))
            assertFalse(boundaries.last().getBoolean("active"))
            assertEquals("completed", boundaries.last().getString("reason"))
            val opened = boundaries.first().getDouble("at_ms")
            val closed = boundaries.last().getDouble("at_ms")
            val focusChoices = records.filter { it.getString("kind") == "decision" }
                .map { it.getJSONObject("detail") }.filter { it.optString("status") == "CHOSEN" &&
                    it.optJSONObject("action")?.optString("aid_type") == "FOCUS_REGION" }
            assertTrue(focusChoices.none { it.getDouble("decision_ms") in opened..closed })
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
            listOf("navigation", "return_sweep", "continuation_return", "continuation_skip", "bookmark_return",
                "bookmark_skip", "bookmark_lookaway", "bookmark_tracking_loss", "repeated_interruptions",
                "brief_interruptions", "line_cycle", "loss_recovery").forEach { id ->
                select(activity, id, ReaderAidPreferences(wordDefinition = false, focusRegion = true))
                val result = await(activity)
                val fixture = ScanpathReplayScenarios.scenarios.single { it.id == id }
                assertEquals("completed", result.termination)
                assertTrue(result.patterns.containsAll(fixture.expectedPatternIds))
                val currentAids = fixture.expectedAidTypes.map {
                    if (it == "NEXT_LINE_START") "READING_BOOKMARK" else it
                }.toSet()
                assertTrue(result.chosenAids.containsAll(currentAids))
                assertTrue(result.drawnAids.containsAll(currentAids))
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
                    val delivery = d.getDouble("delivery_ms")
                    if (!d.isNull("capture_ms")) {
                        val capture = d.getDouble("capture_ms")
                        assertTrue("capture precedes actual delivery", capture <= delivery)
                        assertTrue("scheduler lag remains bounded", delivery - capture <= 100.0)
                    }
                    assertTrue("delivery precedes recording", delivery <= record.getDouble("record_elapsed_ms"))
                }
                "pattern" -> events.add(d.getString("id"))
                "candidate_prefix" -> evidence.add(d.getString("id"))
                "fixation" -> evidence.add("fixation:${d.getInt("index")}")
                "focus_interruption_episode" -> evidence.add(d.getString("episode_id"))
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
                    if (action.getString("aid_type") == "READING_BOOKMARK") {
                        assertEquals("A2_LAST_READ_LINE_BOOKMARK_V2", action.getString("rule_id"))
                        assertEquals("LAST_READ_LINE_BOOKMARK_V1", action.getString("form_id"))
                        assertEquals(1.0, action.getDouble("strength"), 0.0)
                        val bookmark = action.getJSONObject("bookmark_evidence")
                        val source = bookmark.getJSONObject("source")
                        assertEquals(source.getInt("line"), action.getInt("line"))
                        assertTrue(source.getString("evidence_id") in evidence)
                        if (bookmark.getString("departure_time_basis") == "delivery_elapsed_time") {
                            assertTrue(bookmark.isNull("departure_first_capture_ms"))
                            assertTrue(bookmark.isNull("departure_last_capture_ms"))
                            val firstMissing = bookmark.getDouble("departure_first_delivery_ms")
                            assertTrue(source.getDouble("evidence_available_ms") <= firstMissing)
                            assertTrue(bookmark.getDouble("departure_last_delivery_ms") >= firstMissing)
                            assertTrue(bookmark.getDouble("departure_evidence_available_ms") - firstMissing >= 500.0)
                            assertEquals("sustained_gaze_unavailable", bookmark.getString("departure_reason"))
                            assertEquals(source.getDouble("last_capture_ms"), action.getDouble("last_capture_ms"), 0.0)
                        } else {
                            assertTrue(source.getDouble("last_capture_ms") <= bookmark.getDouble("departure_first_capture_ms"))
                            assertTrue(bookmark.getDouble("departure_last_capture_ms") <= bookmark.getDouble("departure_evidence_available_ms"))
                        }
                        assertTrue(bookmark.getDouble("departure_evidence_available_ms") <= d.getDouble("decision_ms"))
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

    @Test fun movingSearchReturnsAndOngoingFourthVisitDrawCurrentFocus() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            for (id in listOf("interruption_place_search", "line_alternation")) {
                val style = if (id == "interruption_place_search") ReadingFocusStyle.DARK_SURROUNDINGS else ReadingFocusStyle.FADE_TEXT
                select(activity, id, ReaderAidPreferences(wordDefinition = false, focusRegion = true, focusStyle = style))
                val result = await(activity)
                assertEquals("Run must use the selected scenario", id, result.scenarioId)
                assertEquals("completed", result.termination)
                assertNull(result.traceFailure)
                val records = result.trace!!.readLines().map(::JSONObject)
                assertCausalTrace(records)
                assertTrue(result.drawnAids.contains("FOCUS_REGION"))
                val chosen = records.filter { it.getString("kind") == "decision" }
                    .map { it.getJSONObject("detail") }.first { it.optString("status") == "CHOSEN" &&
                        it.optJSONObject("action")?.optString("aid_type") == "FOCUS_REGION" }
                val action = chosen.getJSONObject("action")
                val focus = action.getJSONObject("focus_evidence")
                assertEquals("CONTEXT_FOCUS_REGION_V4", action.getString("form_id"))
                assertEquals(style.name, action.getString("focus_style"))
                assertEquals(0.5, action.getDouble("strength"), 0.0)
                if (id == "interruption_place_search") {
                    assertEquals(3, focus.getInt("episode_count"))
                    val episodes = focus.getJSONArray("episodes")
                    repeat(episodes.length()) { index ->
                        val episode = episodes.getJSONObject(index)
                        assertTrue(episode.isNull("recovery"))
                        val presence = episode.getJSONObject("presence_recovery")
                        assertFalse(presence.getBoolean("requires_line_or_word_assignment"))
                        assertTrue(presence.getInt("sample_count") >= 3)
                        assertTrue(presence.getDouble("last_capture_ms") - presence.getDouble("first_capture_ms") >= 100.0)
                        assertEquals("article_smoothed_v1", presence.getString("coordinate_stream"))
                        assertTrue(episode.getDouble("completed_delivery_ms") <= chosen.getDouble("decision_ms"))
                    }
                    val third = episodes.getJSONObject(2).getJSONObject("presence_recovery")
                    assertTrue("Initial focus uses known reading area during moving search",
                        focus.getJSONObject("support").getDouble("last_capture_ms") < third.getDouble("first_capture_ms"))
                } else {
                    val sequence = focus.getJSONArray("line_visit_alternations").getJSONObject(0)
                    assertEquals("aid_supported_line_visits_not_finalized_p11", sequence.getString("kind"))
                    assertEquals(4, sequence.getJSONArray("visits").length())
                    assertEquals(0, action.getJSONArray("event_ids").length())
                    assertFalse(records.any { it.getString("kind") == "pattern" &&
                        it.getJSONObject("detail").optString("pattern_id") == "P11" })
                }
            }
        }
    }

    @Test fun distinctCompletedInterruptionsDrawFocusOnThirdReturnAndIncreaseOnFourth() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { activity ->
            for (id in listOf("bookmark_tracking_loss", "brief_interruptions", "repeated_interruptions")) {
                select(activity, id, ReaderAidPreferences(wordDefinition = false, focusRegion = true))
                val result = await(activity)
                assertEquals("completed", result.termination)
                assertNull(result.traceFailure)
                val records = result.trace!!.readLines().map(::JSONObject)
                assertCausalTrace(records)
                val episodes = records.filter { it.getString("kind") == "focus_interruption_episode" }
                    .map { it.getJSONObject("detail") }
                assertEquals(when (id) { "repeated_interruptions" -> 4; "bookmark_tracking_loss" -> 1; else -> 0 },
                    episodes.size)
                assertEquals(episodes.size, episodes.map { it.getString("episode_id") }.distinct().size)
                val missingSamples = records.filter { it.getString("kind") == "sample" }
                    .map { it.getJSONObject("detail") }.filter { it.optString("exclusion") == "no_face" }
                episodes.forEach { episode ->
                    for (bound in listOf("first", "last")) {
                        val capture = episode.getDouble("missing_${bound}_capture_ms")
                        val delivery = episode.getDouble("missing_${bound}_delivery_ms")
                        assertTrue("Missing observation clocks retain supplied sample provenance", missingSamples.any {
                            it.getDouble("capture_ms") == capture && it.getDouble("delivery_ms") == delivery
                        })
                    }
                }
                val choices = records.filter { it.getString("kind") == "decision" }
                    .map { it.getJSONObject("detail") }.filter { it.optString("status") == "CHOSEN" &&
                        it.optJSONObject("action")?.optString("aid_type") == "FOCUS_REGION" }
                if (id != "repeated_interruptions") {
                    assertTrue(choices.isEmpty()); assertFalse(result.drawnAids.contains("FOCUS_REGION"))
                    continue
                }
                assertTrue(result.drawnAids.contains("FOCUS_REGION"))
                val first = choices.first().getJSONObject("action")
                assertEquals(3, first.getJSONObject("focus_evidence").getInt("episode_count"))
                assertEquals(0.5, first.getDouble("strength"), 0.0)
                val fourth = choices.first { it.getJSONObject("action").getJSONObject("focus_evidence")
                    .getInt("episode_count") == 4 }.getJSONObject("action")
                assertEquals(1.0, fourth.getDouble("strength"), 0.0)
                val fourthGapStart = episodes[3].getDouble("missing_first_delivery_ms")
                val fourthTrackedReturn = episodes[3].getDouble("first_recovery_delivery_ms")
                val clearsDuringBriefGap = records.filter { it.getString("kind") == "decision" }
                    .map { it.getJSONObject("detail") }.filter { it.optString("status") == "CLEARED" &&
                        it.optJSONObject("action")?.optString("aid_type") == "FOCUS_REGION" &&
                        it.getDouble("decision_ms") >= fourthGapStart &&
                        it.getDouble("decision_ms") < fourthTrackedReturn }
                assertTrue("A brief fourth gap must retain the already displayed focus", clearsDuringBriefGap.isEmpty())
                assertTrue(choices.none { it.getDouble("decision_ms") < episodes[2]
                    .getDouble("completed_delivery_ms") })
                choices.forEach { decision ->
                    val focus = decision.getJSONObject("action").getJSONObject("focus_evidence")
                    assertEquals("eligible_ordered_selected_capture_deltas", focus.getString("recovery_time_basis"))
                    assertEquals(30000.0, focus.getDouble("window_ms"), 0.0)
                    assertEquals(3000.0, focus.getDouble("recovery_hold_ms"), 0.0)
                    assertEquals(7000.0, focus.getDouble("recovery_fade_ms"), 0.0)
                    val support = focus.getJSONArray("episodes")
                    assertTrue(support.length() >= 3)
                    repeat(support.length()) { index ->
                        val episode = support.getJSONObject(index)
                        assertTrue(episode.getString("episode_id") in episodes.map { it.getString("episode_id") })
                        assertEquals("delivery_elapsed_time", episode.getString("time_basis"))
                        // Missing coordinates still retain the supplied observation capture clocks.
                        assertTrue(episode.getDouble("missing_first_capture_ms") <=
                            episode.getDouble("missing_first_delivery_ms"))
                        assertTrue(episode.getDouble("missing_last_capture_ms") <=
                            episode.getDouble("missing_last_delivery_ms"))
                        assertTrue(episode.getInt("missing_sample_count") > 1)
                        assertEquals("no_face", episode.getJSONArray("missing_reasons").getString(0))
                        val source = episode.getJSONObject("source")
                        assertTrue(episode.isNull("recovery"))
                        val recovery = episode.getJSONObject("presence_recovery")
                        assertTrue(source.getDouble("evidence_available_ms") <= episode.getDouble("missing_first_delivery_ms"))
                        assertTrue(episode.getDouble("missing_last_delivery_ms") -
                            episode.getDouble("missing_first_delivery_ms") >= 500.0)
                        assertTrue(recovery.getDouble("last_capture_ms") <= recovery.getDouble("confirmed_delivery_ms"))
                        assertTrue(episode.getDouble("completed_delivery_ms") <= decision.getDouble("decision_ms"))
                    }
                }
                missingSamples.forEach {
                        assertFalse(it.isNull("capture_ms"))
                        assertTrue(it.isNull("x")); assertTrue(it.isNull("y"))
                        assertTrue(it.isNull("raw_x")); assertTrue(it.isNull("raw_y"))
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
