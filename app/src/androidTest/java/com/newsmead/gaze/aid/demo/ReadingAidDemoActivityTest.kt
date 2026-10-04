package com.newsmead.gaze.aid.demo

import android.view.View
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.widget.TextView
import android.widget.Spinner
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.activities.ScanpathReplayResult
import com.newsmead.gaze.scanpath.replay.ScanpathReplayScenarios
import org.json.JSONObject
import com.newsmead.gaze.aid.ReadingAidWordView
import com.newsmead.gaze.aid.OfflineWordDefinitions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit presentation inspection only; no permissions, camera, or detector input. */
@RunWith(AndroidJUnit4::class)
class ReadingAidDemoActivityTest {
    private fun idle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    private fun awaitPrepared(scenario: ActivityScenario<ScanpathReplayActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 2000
        var ready = false
        var diagnostic = "Activity did not provide a preparation snapshot."
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { a ->
                ready = a.findViewById<View>(R.id.replayDemoNext).isEnabled
                val body = a.findViewById<TextView>(R.id.replayBody)
                val dock = a.findViewById<ReadingAidWordView>(R.id.replayWordDock)
                val visible = Rect().also { body.getGlobalVisibleRect(it) }
                diagnostic = "ready=$ready presenting=${a.isDemoPresenting} stage=${a.demoStageIndex}\n" +
                    "status=${a.findViewById<TextView>(R.id.replayStatus).text}\n" +
                    "body=${body.width}x${body.height} visible=$visible lines=${body.layout?.lineCount} textSizePx=${body.textSize}\n" +
                    "dock=${dock.width}x${dock.height} visibility=${dock.visibility} totalPadding=${dock.totalPaddingTop},${dock.totalPaddingBottom} " +
                    "compoundPadding=${dock.compoundPaddingTop},${dock.compoundPaddingBottom}\n"
            }
            if (!ready) Thread.sleep(25)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.noBackupFilesDir, "scanpath_demo_preparation.txt")
            .appendText("elapsed_ns=${SystemClock.elapsedRealtimeNanos()}\n$diagnostic\n")
        assertTrue("Demo did not prepare visible targets within 2 seconds: $diagnostic", ready)
    }

    /** Only this app's synthetic root is drawn; no screen or camera capture. */
    private fun capturePreview(activity: ScanpathReplayActivity, name: String) {
        val root = activity.findViewById<View>(R.id.replayRoot)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            File(activity.noBackupFilesDir, name).outputStream().use {
                assertTrue("App preview PNG was saved", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun explicitSixStageCycleUsesOfflineDefinitionAndPublishesNoReplayResult() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { assertFalse(it.isDemoPresenting); it.startDemoCycle() }
            awaitPrepared(scenario)
            var originalText = ""
            scenario.onActivity { a ->
                assertTrue(a.isDemoPresenting); assertFalse(a.isReplaying); assertNull(a.lastResult)
                assertEquals(View.GONE, a.findViewById<ReadingAidWordView>(R.id.replayWordDock).visibility)
                originalText = a.findViewById<TextView>(R.id.replayBody).text.toString()
                a.stepDemo(3)
                assertEquals(3, a.demoStageIndex)
                val word = a.findViewById<TextView>(R.id.definitionWord).text.toString()
                val entry = checkNotNull(OfflineWordDefinitions.lookup(word))
                assertTrue("Popup uses a real source word", originalText.contains(word))
                assertEquals(entry.meanings, a.findViewById<TextView>(R.id.definitionMeaning).text.toString())
                assertTrue(a.findViewById<TextView>(R.id.definitionSource).text.contains(entry.attribution))
                assertEquals(originalText, a.findViewById<TextView>(R.id.replayBody).text.toString())
                capturePreview(a, "scanpath_demo_definition_preview.png")
                a.stepDemo(1)
                assertEquals(4, a.demoStageIndex)
                assertNull(a.findViewById<View>(R.id.definitionWord))
                a.stepDemo(1)
                assertEquals(5, a.demoStageIndex)
                capturePreview(a, "scanpath_demo_focus_preview.png")
                a.stepDemo(1)
                assertEquals(0, a.demoStageIndex)
                assertEquals(originalText, a.findViewById<TextView>(R.id.replayBody).text.toString())
                a.stopReplay("reader_stop")
                assertFalse(a.isDemoPresenting); assertNull(a.lastResult)
                assertNull(a.findViewById<View>(R.id.definitionWord))
            }
        }
    }

    /** Reflection observes teardown without adding a production API for closed session/runtime state. */
    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(instance)

    @Test fun completedDefinitionReplayThenSixStageDemoCannotWriteToClosedRuntimeOrTrace() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<Spinner>(R.id.replayScenario).setSelection(
                    ScanpathReplayScenarios.scenarios.indexOfFirst { item -> item.id == "word_refixations" })
            }
            idle()
            scenario.onActivity { it.startReplay() }
            var completed: ScanpathReplayResult? = null
            var closedRuntime: Any? = null
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (completed == null && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity {
                    if (it.findViewById<View>(R.id.definitionWord) != null)
                        closedRuntime = field(it, "runtime")
                    completed = it.lastResult
                }
                if (completed == null) Thread.sleep(25)
            }
            val result = checkNotNull(completed) { "Replay did not finish within ten seconds" }
            assertEquals("completed", result.termination)
            assertTrue(result.drawnAids.contains("WORD_DEFINITION"))
            val oldRuntime = checkNotNull(closedRuntime) { "Replay never presented its current definition" }
            val runtimeTrace = checkNotNull(field(oldRuntime, "trace"))
            val originalSequence = field(runtimeTrace, "sequence")
            val file = checkNotNull(result.trace)
            val originalBytes = file.readBytes()
            val originalRecords = file.readLines()
            val records = originalRecords.map(::JSONObject)
            assertEquals("footer", records.last().getString("kind"))
            assertTrue(records.last().getJSONObject("detail").getBoolean("complete_trace"))
            val boundaries = records.filter { it.getString("kind") == "definition_consultation" }
            assertEquals(2, boundaries.size)
            assertFalse(boundaries.last().getJSONObject("detail").getBoolean("active"))
            scenario.onActivity {
                assertNull(it.findViewById<View>(R.id.definitionWord))
                assertNull(field(it, "session")); assertNull(field(it, "runtime")); assertNull(field(it, "log"))
                it.startDemoCycle()
            }
            awaitPrepared(scenario)
            // Visit every current stage with idle time to dispatch asynchronous popup render receipts.
            repeat(6) { index ->
                scenario.onActivity {
                    assertEquals(index, it.demoStageIndex)
                    assertTrue(it.isDemoPresenting); assertFalse(it.isReplaying); assertNull(it.lastResult)
                    assertNull(field(it, "session")); assertNull(field(it, "runtime")); assertNull(field(it, "log"))
                    if (index == 3) {
                        val word = it.findViewById<TextView>(R.id.definitionWord).text.toString()
                        assertNotNull(OfflineWordDefinitions.lookup(word))
                    }
                }
                idle()
                if (index == 3) {
                    scenario.onActivity {
                        assertNotNull(it.findViewById<View>(R.id.definitionWord))
                        it.findViewById<View>(R.id.definitionClose).performClick()
                        assertNull(it.findViewById<View>(R.id.definitionWord))
                    }
                    idle()
                }
                assertEquals("demo callback cannot mutate the closed runtime journal", originalSequence,
                    field(runtimeTrace, "sequence"))
                assertEquals(originalBytes.size.toLong(), file.length())
                assertEquals(originalRecords, file.readLines())
                assertArrayEquals(originalBytes, file.readBytes())
                scenario.onActivity { it.stepDemo(1) }
                idle()
            }
            scenario.onActivity {
                assertEquals(0, it.demoStageIndex)
                it.stopReplay("reader_stop")
                assertNull(it.lastResult); assertFalse(it.isDemoPresenting)
                assertNull(it.findViewById<View>(R.id.definitionWord))
            }
            idle()
            assertEquals(originalSequence, field(runtimeTrace, "sequence"))
            assertArrayEquals(originalBytes, file.readBytes())
        }
    }

    @Test fun pauseClearsPresentationAndDoesNotAutomaticallyResume() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { it.startDemoCycle() }
            awaitPrepared(scenario)
            scenario.onActivity { it.stepDemo(3); assertTrue(it.isDemoPresenting) }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity {
                assertFalse(it.isDemoPresenting)
                assertNull(it.findViewById<View>(R.id.definitionWord))
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertFalse(it.isDemoPresenting); assertNull(it.lastResult) }
        }
    }

    @Test fun timerAdvancesOnlyAfterExplicitStart() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { assertFalse(it.isDemoPresenting); it.startDemoCycle() }
            awaitPrepared(scenario)
            val deadline = SystemClock.elapsedRealtime() + 5000
            var advanced = false
            while (!advanced && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { advanced = it.demoStageIndex == 1 }
                if (!advanced) Thread.sleep(25)
            }
            assertTrue("Explicit demo timer did not advance", advanced)
            scenario.onActivity { it.stopReplay("reader_stop"); assertNull(it.lastResult) }
        }
    }

    @Test fun scrollClearsPresentationBeforeNextDisplay() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { it.startDemoCycle() }
            awaitPrepared(scenario)
            scenario.onActivity {
                it.stepDemo(3)
                it.findViewById<NestedScrollView>(R.id.replayScroll).scrollBy(0, 80)
            }
            idle()
            scenario.onActivity {
                it.stepDemo(1) // Synchronous geometry guard also protects delayed callbacks.
                assertFalse(it.isDemoPresenting)
                assertNull(it.findViewById<View>(R.id.definitionWord))
                assertNull(it.lastResult)
            }
        }
    }
}
