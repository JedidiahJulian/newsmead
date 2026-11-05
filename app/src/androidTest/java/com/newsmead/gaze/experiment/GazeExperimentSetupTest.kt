package com.newsmead.gaze.experiment

import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Spinner
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.BuildConfig
import com.newsmead.R
import com.newsmead.activities.GazeExperimentCalibrationActivity
import com.newsmead.activities.GazeExperimentSetupActivity
import com.newsmead.gaze.mgazenet.GazeExperimentConfiguration
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Idle navigation only: never clicks Start. Normal setup reads saved status; tests preserve storage. */
@RunWith(AndroidJUnit4::class)
class GazeExperimentSetupTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun button(root: View, text: String) = descendants(root).filterIsInstance<Button>().single { it.text.toString() == text }
    private fun snapshot(): Map<String,Pair<Long,Long>> {
        val base = context.noBackupFilesDir
        return listOf("mgazenet-v2","gaze-experiment-v1","gaze-experiment").flatMap { namespace ->
            val directory = File(base,namespace)
            if (!directory.exists()) emptyList() else directory.walkTopDown().map { file ->
                file.relativeTo(base).path to (if (file.isDirectory) -1L to file.lastModified() else file.length() to file.lastModified())
            }.toList()
        }.toMap()
    }
    private fun restore(prefs: SharedPreferences, previous: Map<String,*>) {
        val editor = prefs.edit().clear()
        previous.forEach { (key,value) ->
            when (value) {
                is String -> editor.putString(key,value)
                is Boolean -> editor.putBoolean(key,value)
                is Int -> editor.putInt(key,value)
                is Long -> editor.putLong(key,value)
                is Float -> editor.putFloat(key,value)
                is Set<*> -> editor.putStringSet(key,value.map { it as String }.toSet())
                else -> error("Unsupported preference type")
            }
        }
        check(editor.commit())
    }
    private fun awaitRefresh(screen: ActivityScenario<GazeExperimentSetupActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            screen.onActivity { ready = button(it.window.decorView,"Calibrate selected").isEnabled }
            if (!ready) Thread.sleep(40)
        }
        assertTrue("Setup refresh did not finish within 5 seconds",ready)
    }
    @Test fun setupOffersThreeOptionsAndSelectionKeepsUnvalidatedReadingDisabled() {
        assumeTrue(BuildConfig.GAZE_EXPERIMENT_ENABLED)
        val prefs = context.getSharedPreferences("gaze-experiment-navigation-v1",0)
        val original = prefs.all.toMap()
        val before = snapshot()
        try {
            check(prefs.edit().putString("session",UUID.randomUUID().toString()).putString("selected","A")
                .putBoolean("guided",false).putString("order","A,B,C").remove("acPlan").commit())
            ActivityScenario.launch(GazeExperimentSetupActivity::class.java).use { screen ->
                awaitRefresh(screen)
                screen.onActivity { activity ->
                    val root = activity.window.decorView
                    val radios = descendants(root).filterIsInstance<RadioButton>()
                    assertEquals(3,radios.size)
                    assertEquals(setOf("A","B","C"),radios.map { it.tag as String }.toSet())
                    listOf(GazeExperimentConfiguration.NEXT_SVR,GazeExperimentConfiguration.NEXT_RIDGE).forEach { config ->
                        // CompoundButton toggles even without an OnClickListener returning handled.
                        radios.single { it.tag == config.id }.performClick()
                        assertTrue(radios.single { it.tag == config.id }.isChecked)
                        val status = descendants(root).filterIsInstance<TextView>().single {
                            it.text.toString().startsWith(config.label + "\n")
                        }.text.toString()
                        assertTrue(status.contains("Calibration needed.") || status.contains("Calibration fitted:"))
                        if (status.contains("Calibration needed.")) assertFalse(button(root,"Validate selected").isEnabled)
                        // A fresh UUID session has no independent validation, even if a fit already exists.
                        assertFalse(button(root,"Reading test").isEnabled)
                        assertTrue(button(root,"Calibrate selected").isEnabled)
                    }
                }
                screen.moveToState(Lifecycle.State.CREATED)
                screen.moveToState(Lifecycle.State.RESUMED)
                awaitRefresh(screen)
            }
            assertEquals(before,snapshot())
        } finally { restore(prefs,original) }
        assertEquals(original,prefs.all)
    }
    private fun assertIdleCalibration(activity: GazeExperimentCalibrationActivity, config: GazeExperimentConfiguration) {
        val start = activity.findViewById<Button>(R.id.startButton)
        assertEquals(View.VISIBLE,start.visibility)
        assertTrue(start.isEnabled)
        assertEquals("Start 13-point calibration",start.text.toString())
        assertEquals(config.label,activity.findViewById<TextView>(R.id.progressText).text.toString())
        assertTrue(activity.findViewById<TextView>(R.id.statusText).text.toString().contains("One collection fits both B and C."))
        assertEquals(View.GONE,activity.findViewById<View>(R.id.gatePanel).visibility)
        // Inspect state without introducing a production test hook or touching camera APIs.
        listOf("source","session","identity").forEach { name ->
            val field = GazeExperimentCalibrationActivity::class.java.getDeclaredField(name)
            field.isAccessible = true
            assertNull("Idle navigation initialized $name",field.get(activity))
        }
        val active = GazeExperimentCalibrationActivity::class.java.getDeclaredField("active")
        active.isAccessible = true; assertFalse(active.getBoolean(activity))
    }
    @Test fun savedResultsSurviveCalibrationAndComparisonChangesWithoutUnlockingReading() {
        assumeTrue(BuildConfig.GAZE_EXPERIMENT_ENABLED)
        val prefs = context.getSharedPreferences("gaze-experiment-navigation-v1", 0)
        val original = prefs.all.toMap()
        val before = snapshot()
        val previousSession = UUID.randomUUID().toString()
        fun report(condition: String, error: Double) = JSONObject().put("reportId", UUID.randomUUID().toString())
            .put("metadata", JSONObject().put("conditionId", condition).put("sessionId", previousSession)
                .put("calibrationFingerprint", "previous-fit").put("collectionId", "previous-collection")
                .put("outcome", "completed").put("startedAtUtc", "2026-11-05T00:00:00Z").put("sourceMetadata", JSONObject()))
            .put("result", JSONObject().put("mode", "validation").put("rowsTruncated", false).put("validUniqueFreshCount", 40)
                .put("summary", JSONObject().put("missingHeldOutTargetIds", JSONArray())
                    .put("freshHz", 15.0).put("p95AgeMs", 100.0).put("coverage", .8)
                    .put("rawHeldOut", JSONObject().put("medianErrorPx", error).put("spatialSdPx", 3.0))))
            .put("centimeters", JSONObject().put("summary", JSONObject().put("rawHeldOut",
                JSONObject().put("medianErrorCm", error / 100).put("spatialSdCm", .03))))
        try {
            check(prefs.edit().putString("session", UUID.randomUUID().toString()).putString("selected", "A")
                .putBoolean("guided", false).putString("order", "A,B,C").remove("acPlan").commit())
            ActivityScenario.launch(GazeExperimentSetupActivity::class.java).use { screen ->
                awaitRefresh(screen)
                screen.onActivity { activity ->
                    fun field(name: String) = GazeExperimentSetupActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
                    field("reports").set(activity, listOf(report("C", 303.0), report("B", 202.0), report("A", 101.0)))
                    @Suppress("UNCHECKED_CAST")
                    val fingerprints = field("fingerprints").get(activity) as MutableMap<String, String?>
                    @Suppress("UNCHECKED_CAST")
                    val collections = field("collections").get(activity) as MutableMap<String, String?>
                    listOf("A", "B", "C").forEach { fingerprints[it] = "new-fit"; collections[it] = "new-collection" }
                    GazeExperimentSetupActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
                    val text = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                    assertTrue(text.containsAll(listOf("1.010", "2.020", "3.030")))
                    assertTrue(text.containsAll(listOf("Error cm", "Jitter cm")))
                    assertTrue(text.any { it.contains("previous calibration") && it.contains(previousSession.take(8)) })
                    assertFalse(button(activity.window.decorView, "Reading test").isEnabled)
                    descendants(activity.window.decorView).filterIsInstance<Spinner>().single().setSelection(1)
                }
                instrumentation.waitForIdleSync()
                screen.onActivity { activity ->
                    val text = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                    assertFalse(text.contains("1.010"))
                    assertTrue(text.contains("No saved run"))
                    descendants(activity.window.decorView).filterIsInstance<Spinner>().single().setSelection(2)
                }
                instrumentation.waitForIdleSync()
                screen.onActivity { activity ->
                    val text = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                    assertTrue(text.containsAll(listOf("1.010", "2.020", "3.030")))
                    assertFalse(button(activity.window.decorView, "Reading test").isEnabled)
                }
            }
            assertEquals(before, snapshot())
        } finally { restore(prefs, original) }
    }
    @Test fun nextCalibrationStaysIdleAcrossLifecycleAndRecreation() {
        assumeTrue(BuildConfig.GAZE_EXPERIMENT_ENABLED)
        val before = snapshot()
        listOf(GazeExperimentConfiguration.NEXT_SVR,GazeExperimentConfiguration.NEXT_RIDGE).forEach { config ->
            val intent = Intent(context,GazeExperimentCalibrationActivity::class.java)
                .putExtra(ExperimentFlow.EXTRA_CONFIGURATION,config.id)
                .putExtra(ExperimentFlow.EXTRA_SESSION,UUID.randomUUID().toString())
            ActivityScenario.launch<GazeExperimentCalibrationActivity>(intent).use { screen ->
                instrumentation.waitForIdleSync()
                screen.onActivity { assertIdleCalibration(it,config) }
                screen.moveToState(Lifecycle.State.CREATED)
                screen.moveToState(Lifecycle.State.RESUMED)
                screen.onActivity { assertIdleCalibration(it,config) }
                screen.recreate()
                screen.onActivity { assertIdleCalibration(it,config) }
            }
        }
        assertEquals(before,snapshot())
    }
}
