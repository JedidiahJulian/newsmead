package com.newsmead.research.study

import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Handler
import android.view.View
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ArticleActivity
import com.newsmead.fragments.article.ArticleFragment
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.mahalanobis.MahalanobisBaselineCollector
import com.newsmead.gaze.mahalanobis.RdfFeatureVector
import com.newsmead.gaze.mahalanobis.RdfValues
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Camera-free control-flow rehearsal on the actual reader. Synthetic preview time is
 * deliberately independent of the recording clock. Never calls start(), creates a
 * provider, changes calibration, or publishes a completed participant reference. */
@RunWith(AndroidJUnit4::class)
class SharedBaselineAutoCompletionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var isolated = false
    private lateinit var storage: File
    private val visits = mutableListOf<StudyVisit>()

    @Before fun isolate() = main {
        check(StudyCollection.run == null) { "Stop the existing study run before this synthetic rehearsal." }
        storage = File(context.cacheDir, "study-device-test-${UUID.randomUUID()}")
        StudyCollection.useIsolatedStorageForTest(context, storage)
        android.util.Log.i("BaselineAutoFixture", "camera_free_baseline_auto_v1 storage=${storage.absolutePath}")
        isolated = true
        StudyCollection.start(context, "synthetic-baseline-ui", "camera-free-control-flow", "reading")
    }

    @After fun release() {
        if (!isolated) return
        main { StudyCollection.stop("synthetic_fixture_cleanup") }
        visits.forEach { assertTrue("Synthetic writer did not close", it.awaitClosed(10_000)) }
        val drained = CountDownLatch(1)
        StudyCollection.background { drained.countDown() }
        assertTrue(drained.await(10, TimeUnit.SECONDS))
        main { StudyCollection.useIsolatedStorageForTest(context, null) }
    }

    @Test fun missingAndShortCoordinatesKeepActualReaderRunningUntilExactMinimum() {
        launch().use { scenario ->
            lateinit var reader: SharedBaselineReader
            lateinit var visit: StudyVisit
            var shortLayoutFailure: Throwable? = null
            scenario.onActivity { activity ->
                reader = reader(activity)
                visit = prepare(reader)
                // Sixty seconds of real collector window-union coverage cannot replace missing gaze.
                tick(reader)
                assertTrue(field(reader, "running") as Boolean)
                assertEquals("R/D/F: 60/60 s\nGaze: waiting", progress(reader).text.toString())
                installCoverage(visit, 57_624.855821)
                tick(reader)
                assertTrue(field(reader, "running") as Boolean)
                assertEquals("R/D/F: 60/60 s\nGaze: 57.6/60 s", progress(reader).text.toString())
            }
            instrumentation.waitForIdleSync()
            screenshot("baseline-short.png")
            scenario.onActivity { activity ->
                // Measure the short-coverage state now, but report layout failures after
                // shutdown and package assertions so they cannot hide control-flow evidence.
                runCatching {
                    assertTextFullyVisible(progress(reader))
                    assertTextFullyVisible(activity.findViewById(R.id.btnArticleReadingAids))
                }.onFailure { shortLayoutFailure = it }
                installCoverage(visit, 59_999.999)
                tick(reader)
                assertTrue(field(reader, "running") as Boolean)
                assertTrue(progress(reader).text.contains("Gaze: 59.9/60 s"))
                installCoverage(visit, 60_000.0)
                val state = reader.javaClass.getDeclaredMethod("currentProgress", Double::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(reader, StudyVisit.now()) as SharedBaselineProgress
                assertTrue(state.minimumsMet)
                assertEquals("R/D/F: 60/60 s\nGaze: 60/60 s", state.footer)
                tick(reader) // The production runnable makes the completion decision and calls finish().
                assertFalse(field(reader, "running") as Boolean)
                assertFalse(field(reader, "stopping") as Boolean)
                assertNull(field(reader, "provider"))
                assertEquals("Start", activity.findViewById<TextView>(R.id.btnArticleReadingAids).text.toString())
                assertEquals("R/D/F: 60/60 s\nGaze: 60/60 s", progress(reader).text.toString())
            }
            instrumentation.waitForIdleSync()
            screenshot("baseline-auto-stopped.png")
            assertSavedInterruption(visit, "shared_baseline_finished")
            receipt("automatic_decision_and_nonpublishing_shutdown", visit,
                listOf("baseline-short.png", "baseline-auto-stopped.png"), shortLayoutFailure?.toString())
            shortLayoutFailure?.let { throw it }
        }
    }

    @Test fun actualStopButtonRetainsEarlyInterruptionWithoutReferenceActivation() {
        launch().use { scenario ->
            lateinit var visit: StudyVisit
            scenario.onActivity { activity ->
                val reader = reader(activity)
                visit = prepare(reader)
                installCoverage(visit, 57_624.855821)
                tick(reader)
                assertTrue(activity.findViewById<View>(R.id.btnArticleReadingAids).performClick())
                assertFalse(field(reader, "running") as Boolean)
                assertNull(field(reader, "provider"))
                assertEquals("Start", activity.findViewById<TextView>(R.id.btnArticleReadingAids).text.toString())
                assertTrue((field(reader, "lastResult") as String).contains("Baseline stopped early"))
            }
            assertSavedInterruption(visit, "shared_baseline_stopped_early")
            receipt("early_stop_nonpublishing_shutdown", visit, emptyList())
        }
    }

    private fun launch() = ActivityScenario.launch<ArticleActivity>(
        Intent(context, ArticleActivity::class.java).putExtras(SharedBaselineReader.arguments()))

    private fun reader(activity: ArticleActivity): SharedBaselineReader {
        val host = activity.supportFragmentManager.findFragmentById(R.id.flMainArticleContainer) as NavHostFragment
        val fragment = host.childFragmentManager.primaryNavigationFragment as ArticleFragment
        return field(fragment, "sharedBaselineReader") as SharedBaselineReader
    }

    private fun prepare(reader: SharedBaselineReader): StudyVisit {
        assertTrue("Synthetic preview requires elapsed uptime above 61 seconds", StudyVisit.now() > 61_000)
        assertNull(field(reader, "provider"))
        // A stale, test-specific attempt means finish() cannot freeze or replace any saved reference.
        set(reader, "attempt", "synthetic-stale-${UUID.randomUUID()}")
        val collector = MahalanobisBaselineCollector()
        repeat(30) { i ->
            val start = if (i == 29) 50_000L else i * 1_750L
            assertTrue(collector.add(RdfFeatureVector(start + 10_000, start, start + 10_000,
                RdfValues(1.0 + i % 3, 0.3, 20.0 + i % 4))))
        }
        assertEquals(60_000L, collector.coveredDurationMs)
        assertEquals(30, collector.baselineVectors.size)
        set(reader, "collector", collector)
        val visit = requireNotNull(StudyCollection.open(context, "reading_baseline",
            StudyJson.obj("synthetic_test_only" to true, "preview_time_axis" to "synthetic_elapsed_ms")))
        visits.add(visit)
        set(reader, "visit", visit)
        set(reader, "running", true)
        (field(reader, "binding") as com.newsmead.databinding.FragmentArticleBinding)
            .btnArticleReadingAids.text = "Stop"
        return visit
    }

    private fun installCoverage(visit: StudyVisit, end: Double) {
        val monitor = StudyCoordinateCoverage(0.0)
        val coordinateContext = StudyJson.obj("participant_id" to "synthetic", "installation_id" to "synthetic",
            "calibration_id" to "synthetic", "model_id" to "synthetic", "acquisition_id" to "synthetic",
            "presentation_id" to "synthetic", "clock_domain_id" to "synthetic_elapsed_ms",
            "screen_width_px" to 1080, "screen_height_px" to 2400,
            "rotation_degrees" to 0, "coordinate_stream" to "calibrated_raw_v1")
        val times = (0..(end / 100).toInt()).map { it * 100.0 }.toMutableList()
        if (times.last() < end) times.add(end)
        times.forEachIndexed { index, capture ->
            monitor.observation(StudyJson.obj("coordinate_context" to coordinateContext,
                "provider_epoch_id" to "synthetic", "context_epoch_id" to "synthetic",
                "delivery_id" to index + 1L, "capture_ms" to capture, "delivery_ms" to capture + 10,
                "raw_x_px" to 100.0 + index % 3, "raw_y_px" to 200.0 + index % 2,
                "reason" to "coordinate", "timeout_watchdog" to false, "context_allowed" to true))
        }
        assertEquals(end, monitor.snapshot(end + 1000).coveredMs, 1e-8)
        set(visit, "coordinateCoverageMonitor", monitor)
    }

    private fun tick(reader: SharedBaselineReader) {
        try { (field(reader, "tick") as Runnable).run() }
        finally { (field(reader, "handler") as Handler).removeCallbacks(field(reader, "tick") as Runnable) }
    }

    private fun progress(reader: SharedBaselineReader) = field(reader, "progress") as TextView

    private fun assertTextFullyVisible(view: TextView) {
        assertTrue(view.isShown)
        val visible = Rect()
        // Existing production helper converts the ROOT-local global rect using
        // rootView's screen origin; the Point overload is affected by child scroll offsets.
        assertTrue(view.getVisibleRectOnScreen(visible))
        val screen = IntArray(2)
        view.getLocationOnScreen(screen)
        val layout = requireNotNull(view.layout)
        assertTrue("Footer needs all status lines", layout.lineCount >= view.text.count { it == '\n' } + 1)
        for (line in 0 until layout.lineCount) {
            assertEquals("Ellipsized footer line $line", 0, layout.getEllipsisCount(line))
            val left = screen[0] + view.totalPaddingLeft - view.scrollX
            val top = screen[1] + view.totalPaddingTop - view.scrollY
            val lineRect = Rect(left + kotlin.math.floor(layout.getLineLeft(line).toDouble()).toInt(),
                top + layout.getLineTop(line), left + kotlin.math.ceil(layout.getLineRight(line).toDouble()).toInt(),
                top + layout.getLineBottom(line))
            assertTrue("Clipped text line $line: $lineRect outside $visible", visible.contains(lineRect))
        }
    }

    private fun assertSavedInterruption(visit: StudyVisit, reason: String) {
        assertTrue(visit.awaitClosed(10_000))
        val directory = File(StudyCollection.root(context), visit.packageId)
        val manifest = JSONObject(File(directory, "manifest.json").readText())
        val chunks = manifest.getJSONArray("chunks")
        val rows = (0 until chunks.length()).flatMap { i ->
            val chunk = chunks.getJSONObject(i)
            StudyChunkIO.open(File(directory, chunk.getString("file")), chunk).bufferedReader()
                .use { it.readLines().filter(String::isNotBlank).map(::JSONObject) }
        }
        assertEquals(reason, rows.single { it.getString("kind") == "session_end" }
            .getJSONObject("payload").getString("reason"))
        assertFalse(rows.single { it.getString("kind") == "shared_baseline_result" }
            .getJSONObject("payload").getBoolean("completed"))
        assertFalse(rows.any { it.getString("kind") == "rdf_baseline_fit" })
    }

    private fun screenshot(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(storage, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }

    private fun receipt(check: String, visit: StudyVisit, screenshots: List<String>, layoutIssue: String? = null) {
        File(storage, "result.json").writeText(StudyJson.obj(
            "fixture" to "camera_free_baseline_auto_v1", "check" to check, "passed" to (layoutIssue == null),
            "control_flow_passed" to true, "layout_measurement_error" to layoutIssue,
            "storage_path" to storage.absolutePath, "package_id" to visit.packageId,
            "screenshots" to screenshots.map { File(storage, it).absolutePath },
            "provider_started" to false, "completed_reference_activated" to false,
            "limitation" to "Synthetic preview clock and stale attempt: decision/shutdown only; no calibrated reference activation or gaze validity evidence."
        ).toString(2))
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)
    private fun set(target: Any, name: String, value: Any) = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(target, value)
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync { block() }
}
