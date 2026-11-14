package com.newsmead.gaze.aid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.activities.ScanpathReplayResult
import com.newsmead.gaze.scanpath.replay.ScanpathReplayScenarios
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Starts the real camera-free Replay screen in its default AUTO mode. No supplied actions. */
@RunWith(AndroidJUnit4::class)
class ScanpathReplayBookmarkDeviceTest {
    @Test fun lookingAwayDrawsBookmarkAndClearsAfterRecovery() = checkReplay("bookmark_lookaway")
    @Test fun trackingLossDrawsBookmarkAndClearsAfterRecovery() = checkReplay("bookmark_tracking_loss")
    @Test fun shortLineVisitsArmBookmarkWithoutLongLineHold() = checkReplay("bookmark_fast_reading")
    @Test fun broadRereadingDrawsBookmarkAtDepartureLine() = checkReplay("broad_reread")
    @Test fun backwardReadingDimsThenClearsAfterPassage() = checkReplay("bookmark_backreading")

    private fun checkReplay(id: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.cacheDir, "bookmark-replay-check").apply { mkdirs() }
        // Read the production controller only; the status label shows whichever aid was chosen last.
        val runtimeField = ScanpathReplayActivity::class.java.getDeclaredField("runtime").apply { isAccessible = true }
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { screen ->
            val index = ScanpathReplayScenarios.scenarios.indexOfFirst { it.id == id }
            assertTrue(index >= 0)
            screen.onActivity { it.findViewById<Spinner>(R.id.replayScenario).setSelection(index) }
            instrumentation.waitForIdleSync()
            screen.onActivity { it.findViewById<android.view.View>(R.id.replayRun).performClick() }
            var result: ScanpathReplayResult? = null
            var screenshot: Bitmap? = null
            val deadline = SystemClock.elapsedRealtime() + 20000
            while (result == null && SystemClock.elapsedRealtime() < deadline) {
                screen.onActivity { activity ->
                    val activeBookmark = (runtimeField.get(activity) as? ArticleReadingAidRuntime)?.activeActions()?.any {
                        it.aidType == ReadingAidType.READING_BOOKMARK } == true
                    if (screenshot == null && activity.isReplaying && activeBookmark) {
                        val view = activity.window.decorView
                        screenshot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                            view.draw(Canvas(it))
                        }
                    }
                    if (!activity.isReplaying) result = activity.lastResult
                }
                SystemClock.sleep(50)
            }
            val finished = checkNotNull(result) { "$id did not complete" }
            assertEquals("Wrong replay scenario", id, finished.scenarioId)
            assertEquals(id, "completed", finished.termination)
            assertNull(id, finished.traceFailure)
            assertTrue("$id: expected ${finished.enabledExpectedAids}", "READING_BOOKMARK" in finished.enabledExpectedAids)
            assertTrue("$id: chosen ${finished.chosenAids}", "READING_BOOKMARK" in finished.chosenAids)
            assertTrue("$id: drawn ${finished.drawnAids}", "READING_BOOKMARK" in finished.drawnAids)
            assertNotNull("$id: no active bookmark screenshot", screenshot)
            // Encode off the UI thread so screenshot I/O cannot delay the 50 ms replay schedule.
            screenshot!!.let { bitmap ->
                try { File(output, "$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                finally { bitmap.recycle() }
            }
            val records = checkNotNull(finished.trace).readLines().map(::JSONObject)
            val configuration = records.first().getJSONObject("detail").getJSONObject("configuration").toString()
            assertTrue(id, configuration.contains("A2_LAST_READ_LINE_BOOKMARK_V6"))
            val decisions = records.filter { it.optString("kind") == "decision" }.map { it.getJSONObject("detail") }
            val bookmark = decisions.first { it.optString("status") == "CHOSEN" &&
                it.optJSONObject("action")?.optString("aid_type") == "READING_BOOKMARK" }.getJSONObject("action")
            assertEquals(id, "LAST_READ_LINE_BOOKMARK_V3", bookmark.getString("form_id"))
            assertEquals(id, 0.75, bookmark.getDouble("strength"), 0.0)
            if (id != "broad_reread") assertTrue("$id: recovery did not clear bookmark", decisions.any {
                it.optString("status") == "CLEARED" && it.optJSONObject("action")?.optString("id") == bookmark.getString("id") &&
                    it.optString("reason") == "supported_forward_passage" })
            File(output, "$id-summary.json").writeText(JSONObject().put("scenario", id)
                .put("termination", finished.termination).put("chosen", finished.chosenAids.joinToString())
                .put("drawn", finished.drawnAids.joinToString()).put("bookmark", bookmark).toString(2))
        }
    }
}
