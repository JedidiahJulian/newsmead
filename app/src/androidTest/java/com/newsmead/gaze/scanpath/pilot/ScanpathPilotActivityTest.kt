package com.newsmead.gaze.scanpath.pilot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.*
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.scanpath.*
import com.newsmead.gaze.scanpath.replay.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Camera-free real-layout checks. Every resulting trace explicitly labels software-only input. */
@RunWith(AndroidJUnit4::class)
class ScanpathPilotActivityTest {
    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e6
    private fun observe(activity: ScanpathPilotActivity, x: Double, y: Double, id: Long) {
        val at = SystemClock.elapsedRealtimeNanos()
        // Deliberately wrong provider/display coordinates: only raw coordinates may be scored.
        activity.deliverSyntheticObservation(MgazeNetGazeProvider.Observation(at / 1e6, at, id, "coordinate",
            0f, 0f, x.toFloat(), y.toFloat()))
    }
    private fun completeTargets(activity: ActivityScenario<ScanpathPilotActivity>, timeoutMs: Long = 10000): PilotRunResult {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var result: PilotRunResult? = null
        var ordinal = 0L
        var preview: Bitmap? = null
        while (result == null && SystemClock.elapsedRealtime() < deadline) {
            activity.onActivity { a ->
                result = a.lastResult
                if (result == null && a.currentStep >= 0 && a.isRunning) a.activeTarget?.let { t ->
                    observe(a, (t.bounds.left + t.bounds.right) / 2, (t.bounds.top + t.bounds.bottom) / 2, ++ordinal)
                    if (preview == null) {
                        val root = a.findViewById<View>(R.id.pilotRoot)
                        preview = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
                    }
                }
            }
            if (result == null) Thread.sleep(40)
        }
        preview?.let { bitmap ->
            try { File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir, "scanpath_pilot_prompt_preview.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        }
        return checkNotNull(result) { "Synthetic pilot failed to finish." }
    }
    @Test fun idleScreenDoesNotCreateCaptureOrTrace() {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir
        val before = folder.listFiles()?.count { it.name.startsWith("scanpath_pilot_") } ?: 0
        ActivityScenario.launch(ScanpathPilotActivity::class.java).use { activity ->
            activity.onActivity { assertFalse(it.isRunning); assertNull(it.lastResult) }
            assertEquals(before, folder.listFiles()?.count { it.name.startsWith("scanpath_pilot_") } ?: 0)
        }
    }
    @Test fun allPromptedTasksScoreRawCoordinatesAndPreserveReferenceAndInputProvenance() {
        ActivityScenario.launch(ScanpathPilotActivity::class.java).use { activity ->
            PilotTasks.tasks.forEach { descriptor ->
                activity.onActivity { it.startSyntheticCheck(descriptor.id) }
                val r = completeTargets(activity)
                assertEquals(descriptor.id, "completed", r.reason)
                assertNull(r.traceFailure)
                val score = checkNotNull(r.score)
                assertTrue(score.targets.all { it.attempted && it.eligibleSamples > 0 })
                assertTrue(score.targets.all { it.exactLine == it.eligibleSamples })
                assertTrue(score.targets.filter { it.target.wordStartUtf16 != null }.all { it.exactWord == it.eligibleSamples })
                assertTrue(r.chosenAids.isEmpty()); assertTrue(r.drawnAids.isEmpty())
                val records = r.trace.readLines().map(::JSONObject)
                val header = records.first().getJSONObject("detail")
                assertEquals("synthetic_pilot_check", header.getString("input_kind"))
                assertTrue(header.getJSONObject("configuration").isNull("calibration_sha256"))
                assertFalse(header.getBoolean("smoothing")); assertFalse(header.getBoolean("human_reference"))
                assertEquals(r.observations, records.count { it.getString("kind") == "raw_observation" })
                assertEquals(r.observations, records.count { it.getString("kind") == "sample" })
                assertEquals(r.engineAccepted, records.count { it.getString("kind") == "input_acceptance" && it.getJSONObject("detail").getBoolean("engine_accepted") })
                assertEquals(score.targets.size, records.count { it.getString("kind") == "prompt_onset_proxy" })
                assertTrue(records.last().getJSONObject("detail").getBoolean("complete_trace"))
                if (descriptor.id == "forward_words") assertTrue(r.patterns.contains("P01"))
                if (descriptor.id == "multiline") assertTrue(r.patterns.contains("P06"))
                if (descriptor.id == "repeated_word_hold") assertFalse("Stationary holds don't create refixations", r.patterns.contains("P04"))
            }
        }
    }
    @Test fun subsequentSetupFailureCannotInheritSuccessfulRunCounters() {
        ActivityScenario.launch(ScanpathPilotActivity::class.java).use { activity ->
            activity.onActivity { it.startSyntheticCheck("word_hold") }
            assertTrue(completeTargets(activity).observations > 0)
            activity.onActivity { it.startSyntheticCheck("invalid_task") }
            val r = completeTargets(activity)
            assertEquals("setup_failed", r.reason)
            assertEquals(0, r.observations); assertEquals(0, r.engineAccepted)
            assertTrue(r.patterns.isEmpty()); assertTrue(r.chosenAids.isEmpty()); assertNull(r.score)
        }
    }
    @Test fun scrollingAndPauseEndPromptedReferencesAndRetainUnshownTargets() {
        ActivityScenario.launch(ScanpathPilotActivity::class.java).use { activity ->
            activity.onActivity { it.startSyntheticCheck("line_cycle", PilotRunSettings(stepMs = 1500)) }
            Thread.sleep(250)
            activity.onActivity { a ->
                val t = checkNotNull(a.activeTarget); observe(a, (t.bounds.left + t.bounds.right) / 2, (t.bounds.top + t.bounds.bottom) / 2, 1)
                a.findViewById<androidx.core.widget.NestedScrollView>(R.id.pilotScroll).scrollTo(0, 120)
            }
            val r = completeTargets(activity)
            assertEquals("geometry_changed", r.reason)
            assertTrue(checkNotNull(r.score).targets.any { !it.attempted })
            activity.onActivity { it.findViewById<androidx.core.widget.NestedScrollView>(R.id.pilotScroll).scrollTo(0, 0) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            activity.onActivity { it.startSyntheticCheck("forward_words", PilotRunSettings(stepMs = 1500)) }
            Thread.sleep(250)
            activity.moveToState(Lifecycle.State.STARTED); activity.moveToState(Lifecycle.State.RESUMED)
            val paused = completeTargets(activity)
            assertEquals("activity_paused", paused.reason)
            assertTrue(checkNotNull(paused.score).targets.any { !it.attempted })
        }
    }
    @Test fun shadowAndDisplayUseIdenticalViewportAndDifferentRendering() {
        ActivityScenario.launch(ScanpathPilotActivity::class.java).use { activity ->
            var initialOrigin: Double? = null
            for (condition in listOf(PilotReadingCondition.SHADOW, PilotReadingCondition.DISPLAY)) {
                var points = emptyList<ReplayPoint>()
                var start = 0.0
                activity.onActivity { a -> a.startSyntheticCheck("ordinary_reading", PilotRunSettings(readingCondition = condition)) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                activity.onActivity { a ->
                    val body = a.findViewById<TextView>(R.id.pilotBody)
                    val g = checkNotNull(ScanpathTextGeometryReader().read(body)).forEngine("test")
                    if (initialOrigin == null) initialOrigin = g.textOriginY else assertEquals(initialOrigin!!, g.textOriginY, 0.0)
                    val layout = body.layout
                    val words = ScanpathWordMapper(body.text.toString(), body).ranges.mapNotNull { w ->
                        val line = layout.getLineForOffset(w.startUtf16)
                        if (layout.getLineForOffset(w.endUtf16 - 1) != line) return@mapNotNull null
                        val row = g.lines[line]
                        val left = g.textOriginX + layout.getPrimaryHorizontal(w.startUtf16)
                        val right = g.textOriginX + layout.getPrimaryHorizontal(w.endUtf16 - 1) + body.paint.measureText(body.text.subSequence(w.endUtf16 - 1, w.endUtf16).toString()) * 0.4
                        val r = ScanpathRect(left, g.textOriginY + row.top + 1, right, g.textOriginY + row.bottom - 1)
                        if (r.left < g.visibleRectScreen.left || r.right > g.visibleRectScreen.right || r.top < g.visibleRectScreen.top || r.bottom > g.visibleRectScreen.bottom) null
                        else ReplayWord(w, line, r)
                    }
                    points = ScanpathReplayScenarios.build("word_refixations", g, words)
                    start = now()
                }
                points.forEachIndexed { i, p ->
                    val delay = start + p.offsetMs - now()
                    if (delay > 0) Thread.sleep(delay.toLong())
                    activity.onActivity { a -> observe(a, checkNotNull(p.x), checkNotNull(p.y), i.toLong()) }
                }
                Thread.sleep(80)
                activity.onActivity { it.stopRun("reader_stop") }
                val r = completeTargets(activity)
                assertTrue(r.chosenAids.contains("WORD_MAGNIFIER"))
                if (condition == PilotReadingCondition.SHADOW) assertTrue(r.drawnAids.isEmpty())
                else assertTrue(r.drawnAids.contains("WORD_MAGNIFIER"))
                assertNull(r.score)
            }
        }
    }
}
