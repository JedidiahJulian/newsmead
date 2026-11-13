package com.newsmead.gaze.mgazenet

import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.BuildConfig
import com.newsmead.research.study.StudyCollection
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.UUID

/** Camera-free adapter/isolation check; no accuracy or whole-app performance claim. */
class GpuEstimatorIsolationDeviceTest {
    @Test fun optionalGpuAdapterRequiresIsolationAndClosesOnItsOwner() {
        assumeTrue(BuildConfig.DEBUG && InstrumentationRegistry.getArguments()
            .getString("newsmeadGpuEstimatorProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            check(StudyCollection.run == null)
            check(StudyCollection.root(context).canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile)
        }
        var outside: MnnEstimator? = null
        try {
            val rejected = runCatching { outside = MnnEstimator(context, 2, "OPENCL") }.exceptionOrNull()
            assertTrue("GPU constructor must reject ordinary storage", rejected is IllegalStateException)
        } finally { outside?.close() }

        val directory = File(context.cacheDir, "study-device-test-live-perf-gpu-adapter-${UUID.randomUUID()}")
        check(!directory.exists() && directory.mkdir())
        var model: MnnEstimator? = null
        var isolated = false
        var failure: Throwable? = null
        val images = SyntheticFixtures.images()
        try {
            instrumentation.runOnMainSync { StudyCollection.useIsolatedStorageForTest(context, directory); isolated = true }
            check(OpenCVLoader.initLocal())
            model = MnnEstimator(context, 2, "OPENCL")
            val live = checkNotNull(model)
            assertEquals("OPENCL", live.executionBackend)
            assertEquals(3, live.executionSnapshot()["actual_main_backend"])
            assertNull(live.performanceHintsSnapshot()["thread_count"])
            Preprocessor().use { prep ->
                val fixture = images.first()
                val input = prep.prepare(fixture.frame, fixture.crops)
                val first = live.infer(input)
                try {
                    assertEquals(258, first.size)
                    assertTrue(first.all { it.isFinite() })
                    val repeat = live.infer(input)
                    try { assertArrayEquals(first, repeat, 1e-5f) } finally { repeat.fill(0f) }
                    val malformed = runCatching { live.infer(input.copy(face = FloatArray(1))) }.exceptionOrNull()
                    assertTrue(malformed is IllegalArgumentException)
                } finally { first.fill(0f) }
            }
            live.close()
            live.close()
            assertEquals(true, live.executionSnapshot()["closed"])
            assertEquals(true, live.executionSnapshot()["native_close_verified"])
            assertTrue(runCatching { live.setPerformanceHintsEnabled(true) }.exceptionOrNull() is IllegalStateException)
        } catch (problem: Throwable) {
            failure = problem
        } finally {
            images.forEach { it.frame.rgb.fill(0) }
            try {
                model?.close()
                val closed = model == null || (model.executionSnapshot()["closed"] == true &&
                    model.executionSnapshot()["native_close_verified"] == true)
                check(closed) { "GPU native closure unverified; isolated directory retained at $directory" }
                if (isolated) instrumentation.runOnMainSync { StudyCollection.useIsolatedStorageForTest(context, null) }
                check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile &&
                    directory.name.startsWith("study-device-test-live-perf-gpu-adapter-"))
                check(directory.list()?.isEmpty() == true && directory.delete())
            } catch (cleanup: Throwable) {
                failure?.addSuppressed(cleanup) ?: run { failure = cleanup }
            }
        }
        failure?.let { throw it }
    }
}
