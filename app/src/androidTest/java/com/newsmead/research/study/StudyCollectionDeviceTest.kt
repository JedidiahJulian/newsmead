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
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.activities.StudyCollectionActivity
import com.newsmead.gaze.mahalanobis.BaselineFit
import com.newsmead.gaze.mahalanobis.BaselineStatus
import com.newsmead.gaze.mahalanobis.SamplingMetadata
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.scanpath.ScanpathSample
import org.json.JSONArray
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/** Synthetic numerical transport rehearsal. Never instantiates a provider or opens a camera. */
@RunWith(AndroidJUnit4::class)
class StudyCollectionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var storage: File
    private var isolationInstalled = false
    private val visits = mutableListOf<StudyVisit>()

    @Before fun isolate() {
        storage = File(context.cacheDir, "study-device-test-${UUID.randomUUID()}")
        main {
            check(StudyCollection.run == null) { "Stop the existing researcher run before this rehearsal." }
            StudyCollection.useIsolatedStorageForTest(context, storage)
            isolationInstalled = true
        }
    }

    @After fun release() {
        if (!isolationInstalled) return
        main { StudyCollection.stop("synthetic_test_cleanup") }
        visits.forEach { assertTrue("Writer failed to close", it.awaitClosed(10_000)) }
        drainStudyBackground()
        // Originals and evidence are intentionally retained for the lead's pull/import checks.
        main { StudyCollection.useIsolatedStorageForTest(context, null); isolationInstalled = false }
    }

    @Test fun controlsAndLaidOutPresentationCloseAndExportOriginalValues() {
        ActivityScenario.launch(StudyCollectionActivity::class.java).use { scenario ->
            onView(withHint("Participant code")).perform(scrollTo(), replaceText("synthetic-device"), closeSoftKeyboard())
            onView(withHint("Condition label")).perform(scrollTo(), replaceText("transport-only"), closeSoftKeyboard())
            onView(withText("Start run")).perform(scrollTo(), click())
            assertEquals("synthetic-device", StudyCollection.run?.participant)
            lateinit var visit: StudyVisit
            lateinit var epoch: String
            scenario.onActivity { activity ->
                visit = open()
                epoch = visit.providerStarted("synthetic-calibration")
                visit.metadata(epoch, mapOf("model_sha256" to "synthetic-model", "coordinate_frame" to "physical_screen_px"))
                val body = TextView(activity).apply { text = BODY; textSize = 20f }
                activity.setContentView(body)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val body = (activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as TextView
                assertTrue(body.width > 0 && body.layout.lineCount > 0)
                visit.presentation(body)
                listOf(20f to 30f, 80f to 35f, 40f to 95f, 95f to 85f).forEachIndexed { i, point ->
                    val capture = StudyVisit.now()
                    visit.observation(epoch, observation(capture, i + 1L, point.first, point.second), 1080, 1920, 0)
                    visit.record("scanpath_input", StudyJson.obj("generation" to 1,
                        "sample" to ScanpathSample(capture, capture + 5, point.first.toDouble(), point.second.toDouble(),
                            0, 2, 4, "synthetic-layout", displayX = point.first + 1.0, displayY = point.second + 2.0)), capture)
                }
                // Restore controls and exercise the real Stop listener.
            }
            ActivityScenario.launch(StudyCollectionActivity::class.java).use {
                onView(withText("Stop run")).perform(scrollTo(), click())
            }
            assertNull(StudyCollection.run)
            main { visit.observation(epoch, stopped(), 1080, 1920, 0) }
            val rows = closedRows(visit, complete = true)
            assertEquals(BODY, rows.single { it.kind() == "text_snapshot" }.payload().getString("body_text"))
            val raw = rows.first { it.kind() == "gaze_observation" }.payload()
            assertEquals(20.0, raw.getDouble("raw_x_px"), 0.0)
            assertEquals(21.0, raw.getDouble("selected_x_px"), 0.0)
            assertEquals("calibrated_raw_v1", raw.getJSONObject("coordinate_context").getString("coordinate_stream"))
            val sample = rows.first { it.kind() == "scanpath_input" }.payload().getJSONObject("sample")
            assertEquals(2, sample.getInt("word_start_utf16")); assertEquals(4, sample.getInt("word_end_utf16"))
            assertEquals("stopped", rows.last { it.kind() == "gaze_observation" }.payload().getString("reason"))
            exportReceipt("controls_presentation")
        }
    }

    @Test fun heldBaselineResultIsRecordedAfterLogicalEndBeforeSeal() {
        lateinit var visit: StudyVisit
        main {
            start(); visit = open("rdf_baseline"); visit.beginResult()
            StudyCollection.stop("synthetic_baseline_done")
        }
        assertFalse(visit.awaitClosed(100))
        main {
            val fit = BaselineFit(BaselineStatus.INSUFFICIENT_WINDOWS, null, SamplingMetadata(4, 4, 4, 10, 40, 30))
            visit.record("rdf_baseline_fit", StudyJson.obj("fit" to fit, "baseline_attempt_id" to "synthetic-attempt"), StudyVisit.now())
            visit.endResult()
        }
        val rows = closedRows(visit, true)
        assertTrue(rows.indexOfFirst { it.kind() == "rdf_baseline_fit" } > rows.indexOfFirst { it.kind() == "session_end" })
        assertEquals("INSUFFICIENT_WINDOWS", rows.single { it.kind() == "rdf_baseline_fit" }.payload().getJSONObject("fit").getString("status"))
        exportReceipt("held_baseline_result")
    }

    @Test fun missingTerminalTimesOutAndLateReceiptDoesNotRepairCompleteness() {
        lateinit var visit: StudyVisit
        lateinit var epoch: String
        main { start(); visit = open(); epoch = visit.providerStarted("synthetic-calibration"); StudyCollection.stop() }
        assertFalse(visit.awaitClosed(100))
        val rows = closedRows(visit, false)
        assertTrue(rows.single { it.kind() == "provider_close_state" }.payload().getBoolean("unknown_terminal_counters"))
        main { visit.observation(epoch, stopped(), 1080, 1920, 0) }
        // Same FIFO executor as the receipt write; no polling or sleep is needed.
        val barrier = CountDownLatch(1)
        StudyCollection.background { barrier.countDown() }
        assertTrue(barrier.await(10, TimeUnit.SECONDS))
        val receipt = File(storage, "study-late-receipts/${visit.id}-$epoch.json")
        assertTrue(receipt.isFile)
        assertFalse(JSONObject(receipt.readText()).getBoolean("extends_reading_interval"))
        assertFalse(manifest(visit).getBoolean("content_complete"))
        val entries = exportReceipt("late_terminal")
        assertTrue(entries.keys.any { it.startsWith("late_receipts/") })
    }

    @Test fun delayedCaptureKeepsEarlierPresentationAndInvalidMapperIsNotABoundary() {
        ActivityScenario.launch(StudyCollectionActivity::class.java).use { scenario ->
            lateinit var visit: StudyVisit
            lateinit var epoch: String
            lateinit var body: TextView
            var capture = 0.0
            scenario.onActivity { activity ->
                start(); visit = open(); epoch = visit.providerStarted("synthetic-calibration")
                visit.metadata(epoch, mapOf("model_sha256" to "synthetic-model"))
                body = TextView(activity).apply { text = BODY; textSize = 20f }; activity.setContentView(body)
            }
            instrumentation.waitForIdleSync()
            main { visit.presentation(body); capture = StudyVisit.now() }
            // Separate main-loop turns keep the earlier capture strictly before the next boundary.
            main {
                body.textSize = 25f; visit.presentation(body)
                assertFalse(StudyCaptureContext.presentationBoundary("invalid_sample"))
                assertFalse(StudyCaptureContext.presentationBoundary("non_monotonic_capture"))
                if (StudyCaptureContext.presentationBoundary("invalid_sample")) visit.boundary("invalid_sample")
                visit.observation(epoch, observation(capture, 1, 30f, 40f), 1080, 1920, 0)
                visit.observation(epoch, observation(StudyVisit.now(), 2, 60f, 80f), 1080, 1920, 0)
                StudyCollection.stop(); visit.observation(epoch, stopped(), 1080, 1920, 0)
            }
            val rows = closedRows(visit, true)
            val observations = rows.filter { it.kind() == "gaze_observation" && it.payload().getString("reason") == "coordinate" }
            val first = observations[0].payload(); val second = observations[1].payload()
            assertEquals(20.0 * context.resources.displayMetrics.scaledDensity,
                first.getJSONObject("presentation_settings").getDouble("text_size_px"), 0.1)
            assertNotEquals(first.getString("context_epoch_id"), second.getString("context_epoch_id"))
            assertNotEquals(first.getJSONObject("coordinate_context").getString("presentation_id"), second.getJSONObject("coordinate_context").getString("presentation_id"))
            assertFalse(rows.any { it.kind() == "boundary" && it.payload().optString("reason") == "invalid_sample" })
            exportReceipt("delayed_capture")
        }
    }

    @Test fun queuedLateReceiptRetainsOriginAfterIsolatedStorageSwitch() {
        val originalStorage = storage
        val nextStorage = File(context.cacheDir, "study-device-test-${UUID.randomUUID()}")
        lateinit var visit: StudyVisit
        lateinit var epoch: String
        main { start(); visit = open(); epoch = visit.providerStarted("synthetic-calibration"); StudyCollection.stop() }
        closedRows(visit, false)

        val blocked = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val drained = CountDownLatch(1)
        val blockerTimedOut = AtomicBoolean(false)
        StudyCollection.background {
            blocked.countDown()
            blockerTimedOut.set(!unblock.await(10, TimeUnit.SECONDS))
        }
        try {
            assertTrue("Background FIFO blocker did not start", blocked.await(10, TimeUnit.SECONDS))
            main {
                // Queue the old visit's write while the executor is held, then change the global root.
                visit.observation(epoch, stopped(), 1080, 1920, 0)
                StudyCollection.useIsolatedStorageForTest(context, nextStorage)
            }
            StudyCollection.background { drained.countDown() }
        } finally {
            unblock.countDown()
        }
        assertTrue("Queued late receipt did not drain", drained.await(10, TimeUnit.SECONDS))
        assertFalse("FIFO blocker expired before storage switch", blockerTimedOut.get())
        val name = "study-late-receipts/${visit.id}-$epoch.json"
        val receipt = File(originalStorage, name)
        assertTrue("Originating test storage must receive the receipt", receipt.isFile)
        assertFalse("Replacement test storage must not receive the old receipt", File(nextStorage, name).exists())
        assertEquals(visit.id, JSONObject(receipt.readText()).getString("session_id"))
        assertEquals(epoch, JSONObject(receipt.readText()).getString("provider_epoch_id"))
        main { StudyCollection.useIsolatedStorageForTest(context, originalStorage) }
        assertFalse(manifest(visit).getBoolean("content_complete"))
        val entries = exportReceipt("late_receipt_storage_switch")
        assertTrue(entries.containsKey("late_receipts/${visit.id}-$epoch.json"))
    }

    @Test fun recoveryRetainsOnlyValidNewlineTerminatedPrefixAndOriginalPartial() {
        lateinit var visit: StudyVisit
        main { start(); visit = open(); StudyCollection.stop() }
        val sourceRows = closedRows(visit, true)
        val packageId = "synthetic_recovery_${UUID.randomUUID()}"
        val sessionId = UUID.randomUUID().toString()
        val directory = File(StudyCollection.root(context), packageId).apply { mkdirs() }
        val prefix = sourceRows.take(2).mapIndexed { i, row ->
            val copy = JSONObject(row.toString()).put("package_id", packageId)
                .put("session_id", sessionId).put("record_id", "$packageId:$i")
            if (copy.kind() == "package_start") copy.payload().getJSONObject("identity").put("session_id", sessionId)
            copy.toString() + "\n"
        }.joinToString("")
        val original = File(directory, "chunk_00001.jsonl.partial").apply { writeText(prefix + "{\"truncated\":", Charsets.UTF_8) }
        val bytes = original.readBytes()
        assertEquals(1, StudyCollection.recover(context))
        val manifest = JSONObject(File(directory, "manifest.json").readText())
        assertEquals("recovered_incomplete", manifest.getString("status"))
        assertFalse(manifest.getBoolean("content_complete")); assertEquals(2L, manifest.getLong("record_count"))
        assertArrayEquals(bytes, original.readBytes())
        val retained = File(directory, manifest.getJSONArray("chunks").getJSONObject(0).getString("file"))
        assertEquals(prefix, retained.readText())
        verifyManifest(directory)
        exportReceipt("valid_prefix_recovery")
    }

    private fun start() = StudyCollection.start(context, "synthetic-device", "transport-only", "reading")
    private fun open(phase: String? = null): StudyVisit = StudyCollection.open(context, phase,
        StudyJson.obj("fixture" to "camera_free_synthetic_v1"))!!.also { visits.add(it) }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync { block() }
    private fun drainStudyBackground() {
        // The browser queues metadata work after Stop. Direct test API calls must
        // wait on the same FIFO used by real UI export actions.
        val drained = CountDownLatch(1)
        StudyCollection.background { drained.countDown() }
        assertTrue("Background catalog did not finish", drained.await(10, TimeUnit.SECONDS))
    }
    private fun observation(capture: Double, id: Long, x: Float, y: Float) = MgazeNetGazeProvider.Observation(
        capture, SystemClock.elapsedRealtimeNanos(), id, "coordinate", x + 1, y + 2, x, y,
        arrivals = id, busyDrops = 0)
    private fun stopped() = MgazeNetGazeProvider.Observation(null, SystemClock.elapsedRealtimeNanos(), 999, "stopped", arrivals = 4, busyDrops = 0)
    private fun packageDirectory(visit: StudyVisit) = StudyCollection.root(context).listFiles()!!.single { directory ->
        val file = File(directory, "manifest.json")
        file.isFile && JSONObject(file.readText()).getJSONObject("identity").getString("session_id") == visit.id
    }
    private fun manifest(visit: StudyVisit) = JSONObject(File(packageDirectory(visit), "manifest.json").readText())
    private fun closedRows(visit: StudyVisit, complete: Boolean): List<JSONObject> {
        assertTrue("Timed out closing synthetic visit", visit.awaitClosed(10_000))
        val directory = packageDirectory(visit)
        val manifest = verifyManifest(directory)
        assertEquals(complete, manifest.getBoolean("content_complete"))
        return (0 until manifest.getJSONArray("chunks").length()).flatMap { index ->
            val chunk = manifest.getJSONArray("chunks").getJSONObject(index)
            StudyChunkIO.open(File(directory, chunk.getString("file")), chunk).bufferedReader().use { input ->
                input.readLines().map(::JSONObject)
            }
        }
    }
    private fun verifyManifest(directory: File): JSONObject {
        val manifest = JSONObject(File(directory, "manifest.json").readText())
        var records = 0L
        val chunks = manifest.getJSONArray("chunks")
        repeat(chunks.length()) { index ->
            val chunk = chunks.getJSONObject(index); val file = File(directory, chunk.getString("file"))
            assertEquals(chunk.getLong("byte_count"), file.length())
            assertEquals(chunk.getString("sha256"), sha(file.readBytes()))
            StudyChunkIO.open(file, chunk).bufferedReader().use { input ->
                assertEquals(chunk.getLong("record_count"), input.lineSequence().count().toLong())
            }
            records += chunk.getLong("record_count")
        }
        assertEquals(manifest.getLong("record_count"), records)
        return manifest
    }
    @Suppress("DEPRECATION")
    private fun exportReceipt(test: String): Map<String, ByteArray> {
        drainStudyBackground()
        val chooser = requireNotNull(StudyCollection.export(context))
        val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        val uri = requireNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("content", uri.scheme)
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    assertFalse(entries.containsKey(entry.name)); entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        }
        val inventory = JSONObject(String(requireNotNull(entries["study_export_manifest.json"]), Charsets.UTF_8))
        val packages = inventory.getJSONArray("packages")
        val ids = JSONArray()
        repeat(packages.length()) { index ->
            val item = packages.getJSONObject(index); val id = item.getString("package_id"); ids.put(id)
            val manifestBytes = requireNotNull(entries["$id/manifest.json"])
            assertEquals(item.getString("manifest_sha256"), sha(manifestBytes))
            val chunks = JSONObject(String(manifestBytes, Charsets.UTF_8)).getJSONArray("chunks")
            repeat(chunks.length()) { chunkIndex ->
                val chunk = chunks.getJSONObject(chunkIndex)
                val bytes = requireNotNull(entries["$id/${chunk.getString("file")}"])
                assertEquals(chunk.getLong("byte_count"), bytes.size.toLong())
                assertEquals(chunk.getString("sha256"), sha(bytes))
            }
        }
        val zip = File(context.cacheDir, "research-export/${uri.lastPathSegment}")
        assertTrue(zip.isFile)
        File(storage, "result.json").writeText(StudyJson.obj("fixture" to "camera_free_synthetic_v1",
            "test" to test, "checks_passed" to true, "storage_path" to storage.absolutePath,
            "recorded_utc_ms" to System.currentTimeMillis(),
            "zip_path" to zip.absolutePath, "zip_uri" to uri.toString(), "package_ids" to ids,
            "zip_sha256" to sha(zip.readBytes()), "scientific_validation" to false).toString(2))
        return entries
    }
    private fun JSONObject.kind() = getString("kind")
    private fun JSONObject.payload() = getJSONObject("payload")
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    companion object { private const val BODY = "A 😀 word. Synthetic text only.\nSecond line for layout." }
}
