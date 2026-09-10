package com.newsmead.gaze.mgazenet

import android.content.ContextWrapper
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.activities.GazeCalibrationActivity
import com.newsmead.activities.GazeTestActivity
import com.newsmead.gaze.GazeCoordinateFrame
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.UUID

/** Camera-free tests. Host assembly does not execute Android/native checks. Never press Start. */
class MgazeNetIntegrationTest {
    @Test fun openingSetupsIsCameraOffAndMutationFree() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun snapshot(): Map<String,String> = listOf(context.filesDir,context.noBackupFilesDir).flatMap { root ->
            root.walkTopDown().filter { it.isFile && (it.path.contains("mgazenet") || it.name.startsWith("calibration")) }
                .map { it.path to CalibrationIdentity.hash(it.readBytes()) }.toList()
        }.toMap()
        val before = snapshot()
        ActivityScenario.launch(GazeCalibrationActivity::class.java).use { scenario -> scenario.onActivity {
            assertEquals(0,it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            assertFalse(it.findViewById<android.view.View>(com.newsmead.R.id.root).keepScreenOn)
            assertTrue(it.findViewById<android.view.View>(com.newsmead.R.id.startButton).isShown)
        } }
        ActivityScenario.launch(GazeTestActivity::class.java).use { scenario -> scenario.onActivity {
            assertEquals(0,it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            assertFalse(it.findViewById<android.view.View>(com.newsmead.R.id.root).keepScreenOn)
        } }
        assertEquals(before,snapshot())
    }
    @Test fun nativeSyntheticPersistenceAndStoreReload() {
        check(OpenCVLoader.initLocal())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val temporary = File(context.cacheDir,"synthetic-mgazenet-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getNoBackupFilesDir() = temporary
        }
        try {
            val store = MgazeNetCalibrationStore(isolated)
            store.verifyNativePersistence()
            val features = SyntheticFixtures.calibrationFeatures(); val labels = SyntheticFixtures.calibrationLabels()
            try { SvrCalibration().use { original ->
                original.fit(features,labels)
                val identity = CalibrationIdentity("a".repeat(64),1080,2340,0,GazeCoordinateFrame(0,101,1080,2239),"640x480:270:GPU")
                store.save(identity,original)
                val before = store.fingerprint()
                try { store.save(identity,original) { false }; fail("Canceled save committed") }
                catch (_: IllegalStateException) { }
                assertEquals(before,store.fingerprint())
                val artifact = CalibrationBundle.decode(File(temporary,"mgazenet-v1/calibration.bin").readBytes()) { it == identity }
                MgazeNetCalibrationStore(isolated).load(artifact).use { restored ->
                    MgazeNetCalibrationStore.syntheticQueries().forEach { query ->
                        assertArrayEquals(original.predict(query),restored.predict(query),0f); query.fill(0f)
                    }
                }
                assertEquals(listOf("calibration.bin"),File(temporary,"mgazenet-v1").list()!!.sorted())
            } } finally { features.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) } }
        } finally { temporary.deleteRecursively() }
    }
}
