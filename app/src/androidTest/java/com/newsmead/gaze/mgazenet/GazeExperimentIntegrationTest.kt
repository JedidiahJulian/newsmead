package com.newsmead.gaze.mgazenet

import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.BuildConfig
import com.newsmead.R
import com.newsmead.activities.GazeExperimentCalibrationActivity
import com.newsmead.gaze.experiment.ExperimentFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.UUID

/** Camera-free synthetic checks. Every store, including legacy, lives in an owned cache UUID. */
@RunWith(AndroidJUnit4::class)
class GazeExperimentIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun rejected(block: () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Invalid or canceled calibration was accepted")
    }
    @Test fun nextModelProduces258FiniteRepeatableFeaturesFromSyntheticFixtures() {
        assumeTrue(BuildConfig.GAZE_EXPERIMENT_ENABLED)
        check(OpenCVLoader.initLocal())
        Preprocessor().use { prep ->
            MnnEstimator(context, configuration = GazeExperimentConfiguration.NEXT_SVR).use { model ->
                SyntheticFixtures.images().forEach { fixture ->
                    try {
                        val inputs = prep.prepare(fixture.frame,fixture.crops)
                        val output = model.infer(inputs)
                        assertEquals(fixture.name,258,output.size)
                        assertTrue(fixture.name,output.all(Float::isFinite))
                        assertArrayEquals(output,model.infer(inputs),1e-5f)
                        output.fill(0f)
                    } finally { fixture.frame.rgb.fill(0) }
                }
            }
        }
    }
    @Test fun isolatedNativeStoresPreserveLegacyAndCommitNextMappingsTogether() {
        assumeTrue(BuildConfig.GAZE_EXPERIMENT_ENABLED)
        check(OpenCVLoader.initLocal())
        val parent = context.cacheDir.canonicalFile
        val root = File(parent,"gaze-experiment-integration-${UUID.randomUUID()}").canonicalFile
        check(root.parentFile == parent && !root.exists()); check(root.mkdirs())
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getNoBackupFilesDir() = root
        }
        val legacy = MgazeNetCalibrationStore(isolated)
        val a = MgazeNetCalibrationStore(isolated,GazeExperimentConfiguration.CURRENT_SVR)
        val b = MgazeNetCalibrationStore(isolated,GazeExperimentConfiguration.NEXT_SVR)
        val c = MgazeNetCalibrationStore(isolated,GazeExperimentConfiguration.NEXT_RIDGE)
        val features = SyntheticFixtures.calibrationFeatures()
        val labels = SyntheticFixtures.calibrationLabels()
        val queries = SyntheticFixtures.queries()
        val intent = Intent(context,GazeExperimentCalibrationActivity::class.java)
            .putExtra(ExperimentFlow.EXTRA_CONFIGURATION,"B")
            .putExtra(ExperimentFlow.EXTRA_SESSION,UUID.randomUUID().toString())
        try {
            ActivityScenario.launch<GazeExperimentCalibrationActivity>(intent).use { screen ->
                instrumentation.waitForIdleSync()
                lateinit var identity: CalibrationIdentity
                screen.onActivity { activity ->
                    val view = activity.findViewById<View>(R.id.calibrationView)
                    assertTrue(view.width > 0 && view.height > 0)
                    identity = b.identity(view).copy(acquisition = "640x480:270:GPU")
                }
                SvrCalibration().use { svr -> RidgeCalibration().use { ridge ->
                    svr.fit(features,labels); ridge.fit(features,labels)
                    legacy.save(identity.copy(configuration = null),svr)
                    val legacyHash = legacy.fingerprint()
                    val aId = UUID.randomUUID().toString()
                    a.saveExperiment(identity.copy(configuration = GazeExperimentConfiguration.CURRENT_SVR),svr,null,aId)
                    val aHash = a.fingerprint()
                    val collection = UUID.randomUUID().toString()
                    b.saveExperiment(identity,svr,ridge,collection)
                    assertNotNull(b.fingerprint()); assertEquals(b.fingerprint(),c.fingerprint())
                    assertEquals(collection,b.calibrationCollectionId()); assertEquals(collection,c.calibrationCollectionId())
                    assertEquals(b.savedAt(),c.savedAt()); assertNotNull(b.savedAt())
                    assertEquals(aId,a.calibrationCollectionId())
                    assertEquals(legacyHash,legacy.fingerprint()); assertEquals(aHash,a.fingerprint())
                    lateinit var savedB: CalibrationBundle.Artifact
                    lateinit var savedC: CalibrationBundle.Artifact
                    screen.onActivity { activity ->
                        val view = activity.findViewById<View>(R.id.calibrationView)
                        assertNull(a.compatibilityIssue(view)); assertNull(b.compatibilityIssue(view)); assertNull(c.compatibilityIssue(view))
                        savedB = b.snapshot(view); savedC = c.snapshot(view)
                    }
                    rejected { c.loadRegressor(savedB) }
                    b.loadRegressor(savedB).use { restored ->
                        assertTrue(restored is SvrCalibration)
                        queries.forEach { assertArrayEquals(svr.predict(it),restored.predict(it),0f) }
                    }
                    c.loadRegressor(savedC).use { restored ->
                        assertTrue(restored is RidgeCalibration)
                        queries.forEach { assertArrayEquals(ridge.predict(it),restored.predict(it),0f) }
                    }
                    val previous = b.fingerprint()
                    var checks = 0
                    // Cancel after startWrite and payload write; AtomicFile must roll back both mappings.
                    rejected { b.saveExperiment(identity,svr,ridge,UUID.randomUUID().toString()) { ++checks < 3 } }
                    assertEquals(3,checks)
                    assertEquals(previous,b.fingerprint()); assertEquals(previous,c.fingerprint())
                    assertEquals(collection,b.calibrationCollectionId()); assertEquals(collection,c.calibrationCollectionId())
                    assertEquals(legacyHash,legacy.fingerprint()); assertEquals(aHash,a.fingerprint())
                    // Simulate an interrupted pair write: neither configuration may accept a partial generation.
                    val partial = File(root,"gaze-experiment-v1/next-pair.bin.new")
                    partial.writeBytes(byteArrayOf(1,2,3))
                    try {
                        screen.onActivity { activity ->
                            val view = activity.findViewById<View>(R.id.calibrationView)
                            assertNotNull(b.compatibilityIssue(view)); assertNotNull(c.compatibilityIssue(view))
                            rejected { b.snapshot(view) }; rejected { c.snapshot(view) }
                        }
                    } finally { check(partial.delete()) }
                    assertEquals(previous,b.fingerprint())
                } }
            }
        } finally {
            features.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) }; queries.forEach { it.fill(0f) }
            check(root.canonicalFile.parentFile == parent && root.name.startsWith("gaze-experiment-integration-"))
            check(root.deleteRecursively())
        }
    }
}
