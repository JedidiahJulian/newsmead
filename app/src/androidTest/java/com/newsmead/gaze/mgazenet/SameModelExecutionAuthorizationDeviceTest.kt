package com.newsmead.gaze.mgazenet

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.BuildConfig
import com.newsmead.research.study.StudyCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Camera-free isolation boundary probe; never creates a model or study run. */
@RunWith(AndroidJUnit4::class)
class SameModelExecutionAuthorizationDeviceTest {
    @Test fun experimentalExecutionRequiresOwnedLivePerformanceStorage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicit storage probe opt-in required",
            InstrumentationRegistry.getArguments().getString("newsmeadExecutionAuthorizationProbe") == "true")
        assumeTrue("Debug-only execution diagnostic", BuildConfig.DEBUG)
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            check(StudyCollection.run == null) { "Existing study run must remain untouched" }
            check(StudyCollection.root(context).canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile) {
                "Existing isolated storage must remain untouched"
            }
            assertNull(SameModelExecutionTrial.requested(context, null))
            assertNull(SameModelExecutionTrial.requested(context, Bundle()))
            assertEquals(2, MnnEstimator.DEFAULT_CPU_THREADS)
            SameModelExecutionTrial.requireBackendAuthorized(context, MnnEstimator.DEFAULT_CPU_THREADS, null)
            rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requested(context, trial(2))
            }
            rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requested(context, trial(3))
            }
            rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requested(context, trial(1))
            }
            for (mode in listOf("CPU", "GPU")) rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requestedLocalizer(context, localizerTrial(mode))
            }
            for (backend in listOf("CPU", "OPENCL")) rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requestedBackend(context, backendTrial(backend))
            }
            val directory = File(context.cacheDir, "study-device-test-live-perf-auth-${UUID.randomUUID()}")
            var isolated = false
            try {
                check(!directory.exists() && directory.mkdir())
                check(directory.list()?.isEmpty() == true)
                StudyCollection.useIsolatedStorageForTest(context, directory)
                isolated = true
                assertEquals(1, SameModelExecutionTrial.requested(context, trial(1)))
                assertEquals(2, SameModelExecutionTrial.requested(context, trial(2)))
                assertEquals(3, SameModelExecutionTrial.requested(context, trial(3)))
                assertEquals(4, SameModelExecutionTrial.requested(context, trial(4)))
                rejected(IllegalArgumentException::class.java) {
                    SameModelExecutionTrial.requested(context, Bundle().apply {
                        putBoolean(SameModelExecutionTrial.OPT_IN, true)
                    })
                }
                rejected(IllegalArgumentException::class.java) {
                    SameModelExecutionTrial.requested(context, Bundle().apply {
                        putInt(SameModelExecutionTrial.THREADS, 2)
                    })
                }
                rejected(IllegalArgumentException::class.java) {
                    SameModelExecutionTrial.requested(context, trial(5))
                }
                for (mode in listOf("CPU", "GPU")) {
                    assertEquals(mode, SameModelExecutionTrial.requestedLocalizer(context, localizerTrial(mode)))
                    for (threads in listOf(1, 3, 4)) rejected(IllegalArgumentException::class.java) {
                        SameModelExecutionTrial.requestedLocalizer(context, localizerTrial(mode, threads))
                    }
                    rejected(IllegalArgumentException::class.java) {
                        SameModelExecutionTrial.requestedLocalizer(context, localizerTrial(mode).apply {
                            remove(SameModelExecutionTrial.OPT_IN)
                        })
                    }
                    rejected(IllegalArgumentException::class.java) {
                        SameModelExecutionTrial.requestedLocalizer(context, localizerTrial(mode).apply {
                            remove(SameModelExecutionTrial.THREADS)
                        })
                    }
                }
                rejected(IllegalArgumentException::class.java) {
                    SameModelExecutionTrial.requestedLocalizer(context, localizerTrial("invalid"))
                }
                for (backend in listOf("CPU", "OPENCL")) {
                    assertEquals(backend, SameModelExecutionTrial.requestedBackend(context, backendTrial(backend)))
                    for (threads in listOf(1, 3, 4)) rejected(IllegalArgumentException::class.java) {
                        SameModelExecutionTrial.requestedBackend(context, backendTrial(backend, threads))
                    }
                    rejected(IllegalArgumentException::class.java) {
                        SameModelExecutionTrial.requestedBackend(context, backendTrial(backend).apply {
                            remove(SameModelExecutionTrial.OPT_IN)
                        })
                    }
                    rejected(IllegalArgumentException::class.java) {
                        SameModelExecutionTrial.requestedBackend(context, backendTrial(backend).apply {
                            remove(SameModelExecutionTrial.THREADS)
                        })
                    }
                }
                rejected(IllegalArgumentException::class.java) {
                    SameModelExecutionTrial.requestedBackend(context, backendTrial("invalid"))
                }
                check(StudyCollection.run == null)
            } finally {
                if (isolated) StudyCollection.useIsolatedStorageForTest(context, null)
                val owned = directory.canonicalFile
                check(owned.parentFile == context.cacheDir.canonicalFile &&
                    owned.name.startsWith("study-device-test-live-perf-auth-"))
                if (directory.exists()) {
                    check(directory.isDirectory && directory.list()?.isEmpty() == true)
                    check(directory.delete()) { "Cannot delete empty authorization-probe directory" }
                }
            }
            assertNull(SameModelExecutionTrial.requested(context, null))
            for (mode in listOf("CPU", "GPU")) rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requestedLocalizer(context, localizerTrial(mode))
            }
            for (backend in listOf("CPU", "OPENCL")) rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requestedBackend(context, backendTrial(backend))
            }
            rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requested(context, trial(1))
            }
            rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requested(context, trial(2))
            }
            rejected(IllegalStateException::class.java) {
                SameModelExecutionTrial.requested(context, trial(3))
            }
        }
    }

    @Test fun localizerReferenceExceptionPreservesGeometryAndOrdinaryStrictness() {
        val reference = "640x480:90:GPU"
        assertTrue(SameModelExecutionTrial.acquisitionMatches(reference, reference, null))
        assertTrue(SameModelExecutionTrial.acquisitionMatches(reference, reference, "GPU"))
        assertTrue(SameModelExecutionTrial.acquisitionMatches(reference, "640x480:90:CPU_experimental", "CPU"))
        for (mode in listOf(null, "GPU")) {
            assertTrue(!SameModelExecutionTrial.acquisitionMatches(reference, "640x480:90:CPU_experimental", mode))
        }
        for (actual in listOf("641x480:90:CPU_experimental", "640x481:90:CPU_experimental",
            "640x480:0:CPU_experimental", "640x480:90:CPU_initialization_fallback",
            "640x480:90:CPU", "bad:CPU_experimental")) {
            assertTrue(!SameModelExecutionTrial.acquisitionMatches(reference, actual, "CPU"))
        }
        assertTrue(!SameModelExecutionTrial.acquisitionMatches("640x480:90:CPU_initialization_fallback",
            "640x480:90:CPU_experimental", "CPU"))
    }

    private fun localizerTrial(mode: String, threads: Int = 2) = trial(threads).apply {
        putString(SameModelExecutionTrial.LOCALIZER, mode)
    }
    private fun backendTrial(backend: String, threads: Int = 2) = trial(threads).apply {
        putString(SameModelExecutionTrial.BACKEND, backend)
    }

    private fun trial(threads: Int) = Bundle().apply {
        putBoolean(SameModelExecutionTrial.OPT_IN, true)
        putInt(SameModelExecutionTrial.THREADS, threads)
    }

    private fun rejected(expected: Class<out Throwable>, action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue("Expected ${expected.simpleName}; actual=$failure", expected.isInstance(failure))
    }
}
