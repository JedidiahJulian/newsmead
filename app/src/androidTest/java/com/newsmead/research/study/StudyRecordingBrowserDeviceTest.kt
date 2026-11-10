package com.newsmead.research.study

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.activities.StudyCollectionActivity
import com.newsmead.gaze.ParticipantReadingBaseline
import com.newsmead.gaze.mahalanobis.*
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import org.hamcrest.Matchers.containsString
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

/** Synthetic saved-recording selection only. Never creates a camera/provider or changes active calibration. */
@RunWith(AndroidJUnit4::class)
class StudyRecordingBrowserDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var storage: File
    private var isolated = false
    private val visits = mutableListOf<StudyVisit>()

    @Before fun isolate() {
        storage = File(context.cacheDir, "study-device-test-${UUID.randomUUID()}")
        main {
            check(StudyCollection.run == null)
            StudyCollection.useIsolatedStorageForTest(context, storage)
            isolated = true
        }
    }
    @After fun release() {
        if (!isolated) return
        main { StudyCollection.stop("synthetic_browser_cleanup") }
        visits.forEach { assertTrue(it.awaitClosed(10_000)) }
        main { StudyCollection.useIsolatedStorageForTest(context, null); isolated = false }
    }

    @Test fun browserGroupsReadingsAndSelectedZipIncludesOnlySegmentsAndRequiredBaseline() {
        val reading = StudyArticleReading()
        val reopened = StudyArticleReading()
        val attempt = "synthetic-browser-attempt"
        val calibration = "synthetic-browser-calibration"
        val collector = MahalanobisBaselineCollector(MahalanobisConfig(minimumRetainedWindows = 2, minimumBaselineDurationMs = 0))
        collector.add(RdfFeatureVector(10_000, 0, 10_000, RdfValues(1.0, .2, 3.0)))
        collector.add(RdfFeatureVector(10_500, 500, 10_500, RdfValues(2.0, .3, 4.0)))
        val fit = collector.freeze()
        assertEquals(BaselineStatus.READY, fit.status)
        val profile = ParticipantReadingBaseline.Entry("synthetic-browser", attempt, calibration, requireNotNull(fit.profile))
        lateinit var baseline: StudyVisit
        lateinit var first: StudyVisit
        lateinit var continuation: StudyVisit
        lateinit var other: StudyVisit
        main {
            StudyCollection.start(context, "synthetic-browser", "browser-only", "reading")
            baseline = open("rdf_baseline", StudyJson.obj("baseline_attempt_id" to attempt,
                "baseline_participant_code" to profile.participantCode, "calibration_id" to calibration))
            baseline.record("rdf_baseline_fit", StudyJson.obj("fit" to fit, "baseline_attempt_id" to attempt))
            baseline.finish("synthetic_baseline_ready")
            fun article(identity: StudyArticleReading): StudyVisit {
                val visit = open("reading", identity.nextSegment("synthetic-same-article"))
                identity.opened(visit.id)
                visit.baseline(profile)
                val epoch = visit.providerStarted(calibration)
                visit.observation(epoch, MgazeNetGazeProvider.Observation(null, SystemClock.elapsedRealtimeNanos(), 1,
                    "stopped", arrivals = 0, busyDrops = 0), 1080, 1920, 0)
                visit.finish("synthetic_article_ready")
                return visit
            }
            first = article(reading); continuation = article(reading); other = article(reopened)
            StudyCollection.stop("synthetic_fixtures_ready")
        }
        visits.forEach { assertTrue(it.awaitClosed(10_000)) }
        val catalog = background { StudyCollection.catalog(context) }
        assertEquals(3, catalog.recordings.size)
        val group = catalog.recordings.single { it.first.readingId == reading.id }
        assertEquals(setOf(first.id, continuation.id), group.packages.map { it.session }.toSet())
        val expected = group.packages.map { it.id }.toSet() + catalog.recordings.single { it.first.phase == "rdf_baseline" }.first.id
        val unselectedId = catalog.recordings.single { it.first.readingId == reopened.id }.first.id

        ActivityScenario.launch(StudyCollectionActivity::class.java).use { scenario ->
            // Wait for the real background metadata load and its posted UI update.
            background { Unit }; instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any {
                    it.text.toString().contains("3 saved recording(s)") })
            }
            onView(withText(containsString("Recording ID: ${reading.id}"))).perform(scrollTo(), click())
            scenario.onActivity { activity ->
                assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any {
                    it.text.toString().contains("3 package(s) to transfer") })
            }
        }
        val chooser = requireNotNull(background { StudyCollection.export(context, setOf(group.id)) })
        @Suppress("DEPRECATION")
        val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        @Suppress("DEPRECATION")
        val uri = requireNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)!!.use { input -> ZipInputStream(input).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes(); zip.closeEntry() }
        } }
        val inventory = JSONObject(String(requireNotNull(entries["study_export_manifest.json"]), Charsets.UTF_8))
        val items = inventory.getJSONArray("packages")
        val actual = (0 until items.length()).map { items.getJSONObject(it).getString("package_id") }.toSet()
        assertEquals(expected, actual)
        assertFalse(actual.contains(unselectedId))
        assertEquals(1, (0 until items.length()).count { items.getJSONObject(it).getBoolean("included_reference") })
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val bytes = requireNotNull(entries["${item.getString("package_id")}/manifest.json"])
            assertEquals(item.getString("manifest_sha256"), sha(bytes))
            val manifest = JSONObject(String(bytes, Charsets.UTF_8))
            val chunks = manifest.getJSONArray("chunks")
            repeat(chunks.length()) { chunkIndex ->
                val chunk = chunks.getJSONObject(chunkIndex)
                val content = requireNotNull(entries["${item.getString("package_id")}/${chunk.getString("file")}"])
                assertEquals(chunk.getLong("byte_count"), content.size.toLong())
                assertEquals(chunk.getString("sha256"), sha(content))
            }
        }
        val zip = File(context.cacheDir, "research-export/${uri.lastPathSegment}")
        File(storage, "browser-result.json").writeText(StudyJson.obj("fixture" to "camera_free_recording_browser_v1",
            "scientific_validation" to false, "selected_reading_id" to reading.id, "unselected_reading_id" to reopened.id,
            "selected_package_ids" to actual.sorted(), "zip_path" to zip.absolutePath, "zip_sha256" to sha(zip.readBytes()),
            "session_ids" to listOf(baseline.id, first.id, continuation.id), "verified" to true).toString(2))
        assertTrue(background { StudyCollection.catalog(context) }.recordings.any { it.first.readingId == reopened.id })
        main { StudyCollection.start(context, "synthetic-browser", "guard-check", "reading") }
        try { background { StudyCollection.catalog(context) }; fail("Active run must block browsing") }
        catch (_: IllegalStateException) { }
    }

    private fun open(phase: String, metadata: JSONObject) = requireNotNull(StudyCollection.open(context, phase, metadata)).also { visits.add(it) }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync { block() }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun <T> background(block: () -> T): T {
        val done = CountDownLatch(1); val result = AtomicReference<T>(); val error = AtomicReference<Throwable?>()
        StudyCollection.background { try { result.set(block()) } catch (failure: Throwable) { error.set(failure) } finally { done.countDown() } }
        assertTrue("Background browser operation timed out", done.await(10, TimeUnit.SECONDS))
        error.get()?.let { throw it }
        return result.get()
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
