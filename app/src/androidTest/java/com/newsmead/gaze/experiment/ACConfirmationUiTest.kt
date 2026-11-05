package com.newsmead.gaze.experiment

import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.BuildConfig
import com.newsmead.R
import com.newsmead.activities.GazeExperimentCalibrationActivity
import com.newsmead.activities.GazeExperimentRunActivity
import com.newsmead.activities.GazeExperimentSetupActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Idle UI only. No test presses Start, requests camera permission or writes a report/calibration. */
@RunWith(AndroidJUnit4::class)
class ACConfirmationUiTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun button(root: View, text: String) = descendants(root).filterIsInstance<Button>().single { it.text.toString() == text }
    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(instance)
    private fun snapshot(): Map<String, Pair<Long, Long>> {
        val base = context.noBackupFilesDir
        return listOf("mgazenet-v2", "gaze-experiment-v1", "gaze-experiment").flatMap { namespace ->
            val directory = File(base, namespace)
            if (!directory.exists()) emptyList() else directory.walkTopDown().map { file ->
                file.relativeTo(base).path to ((if (file.isDirectory) -1L else file.length()) to file.lastModified())
            }.toList()
        }.toMap()
    }
    private fun restore(prefs: SharedPreferences, original: Map<String, *>) {
        val editor = prefs.edit().clear()
        original.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
            else -> error("Unsupported preference type")
        } }
        check(editor.commit())
    }
    private fun plan() = ACConfirmationPlan.create(setupNotes = "Synthetic idle UI fixture", viewingDistanceCm = null)
    private fun intent(plan: ACConfirmationPlan, activity: Class<*>, mode: String? = null): Intent =
        ACConfirmationLaunch.put(Intent(context, activity)
            .putExtra(ExperimentFlow.EXTRA_SESSION, plan.sessionId)
            .putExtra(ExperimentFlow.EXTRA_CONFIGURATION, "C").apply {
                if (mode != null) putExtra(ExperimentFlow.EXTRA_MODE, mode)
            }, plan, 2)

    private fun awaitRefresh(screen: ActivityScenario<GazeExperimentSetupActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            screen.onActivity { ready = field(it, "refreshing") == false }
            if (!ready) Thread.sleep(40)
        }
        assertTrue("Setup refresh exceeded 5 seconds", ready)
    }

    @Test fun persistedPlanResumesFreshRoundOneAndDisablesManualRoutes() {
        assumeTrue(BuildConfig.GAZE_EXPERIMENT_ENABLED)
        val prefs = context.getSharedPreferences("gaze-experiment-navigation-v1", 0)
        val original = prefs.all.toMap()
        val before = snapshot()
        val plan = plan()
        try {
            check(prefs.edit().putString("session", plan.sessionId).putString("selected", "A")
                .putString("order", "A,C,C,A").putBoolean("guided", false)
                .putString("acPlan", plan.toJson().toString()).commit())
            ActivityScenario.launch(GazeExperimentSetupActivity::class.java).use { screen ->
                repeat(2) { pass ->
                    if (pass == 1) screen.recreate()
                    awaitRefresh(screen)
                    screen.onActivity { activity ->
                        // In-memory existing fits prove that saved calibration availability cannot skip a fresh block.
                        @Suppress("UNCHECKED_CAST")
                        val fits = field(activity, "fingerprints") as MutableMap<String, String?>
                        @Suppress("UNCHECKED_CAST")
                        val collections = field(activity, "collections") as MutableMap<String, String?>
                        @Suppress("UNCHECKED_CAST")
                        val compatible = field(activity, "compatible") as MutableMap<String, Boolean>
                        listOf("A", "C").forEach { fits[it] = "old-fit"; collections[it] = "old-collection"; compatible[it] = true }
                        activity.javaClass.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
                        val root = activity.window.decorView
                        listOf("Calibrate selected", "Validate selected", "Reading test").forEach {
                            assertFalse(button(root, it).isEnabled)
                        }
                        val next = button(root, "Continue A/C - round 1, A: Fresh calibration")
                        assertTrue(next.isEnabled)
                        assertEquals(View.VISIBLE, next.visibility)
                        assertEquals(plan.sessionId, (field(activity, "confirmationPlan") as ACConfirmationPlan).sessionId)
                        assertEquals(plan.steps.map { it.id }, (field(activity, "confirmationPlan") as ACConfirmationPlan).steps.map { it.id })
                    }
                }
            }
            assertEquals(before, snapshot())
            assertEquals(plan.toJson().toString(), prefs.getString("acPlan", null))
        } finally { restore(prefs, original) }
        assertEquals(original, prefs.all)
    }

    private fun assertCalibrationIdle(activity: GazeExperimentCalibrationActivity, plan: ACConfirmationPlan) {
        val root = activity.window.decorView
        val start = activity.findViewById<Button>(R.id.startButton)
        val check = descendants(root).filterIsInstance<CheckBox>().single()
        assertFalse(check.isChecked)
        assertFalse(start.isEnabled)
        assertTrue(activity.findViewById<TextView>(R.id.progressText).text.toString().startsWith("Round 2 of 2 - C"))
        val tags = requireNotNull(ACConfirmationLaunch.parse(activity.intent))
        assertEquals(plan.sessionId, tags["confirmation_session_id"])
        assertEquals(plan.steps[2].id, tags["confirmation_step_id"])
        assertEquals("2", tags["confirmation_round"])
        assertEquals("2", tags["confirmation_step_index"])
        listOf("source", "session", "identity").forEach { assertNull(field(activity, it)) }
        check.performClick()
        assertTrue(start.isEnabled)
        assertNull(field(activity, "source"))
        assertEquals(false, field(activity, "active"))
    }

    @Test fun secondRoundCalibrationRequiresAttestationAndRecreationStaysCameraFree() {
        assumeTrue(BuildConfig.GAZE_EXPERIMENT_ENABLED)
        val before = snapshot()
        val plan = plan()
        ActivityScenario.launch<GazeExperimentCalibrationActivity>(intent(plan, GazeExperimentCalibrationActivity::class.java)).use { screen ->
            screen.onActivity { assertCalibrationIdle(it, plan) }
            screen.recreate()
            screen.onActivity { assertCalibrationIdle(it, plan) }
        }
        assertEquals(before, snapshot())
    }

    @Test fun validationAndReadingRequireAttestationWithoutInitializingMeasurementOrProvider() {
        assumeTrue(BuildConfig.GAZE_EXPERIMENT_ENABLED)
        val before = snapshot()
        val plan = plan()
        listOf(ExperimentFlow.VALIDATION, ExperimentFlow.READING).forEach { mode ->
            ActivityScenario.launch<GazeExperimentRunActivity>(intent(plan, GazeExperimentRunActivity::class.java, mode)).use { screen ->
                repeat(2) { pass ->
                    if (pass == 1) screen.recreate()
                    screen.onActivity { activity ->
                        val root = activity.window.decorView
                        val start = button(root, if (mode == ExperimentFlow.VALIDATION) "Start validation" else "Start reading test")
                        val check = descendants(root).filterIsInstance<CheckBox>().single()
                        assertFalse(check.isChecked)
                        assertFalse(start.isEnabled)
                        val tags = requireNotNull(ACConfirmationLaunch.parse(activity.intent))
                        assertEquals(plan.sessionId, tags["confirmation_session_id"])
                        assertEquals("2", tags["confirmation_round"])
                        assertEquals(plan.steps[2].id, tags["confirmation_step_id"])
                        listOf("provider", "measurements").forEach { assertNull(field(activity, it)) }
                        assertEquals(false, field(activity, "attempted"))
                        assertEquals(false, field(activity, "running"))
                        assertTrue((field(activity, "metadata") as Map<*, *>).isEmpty())
                        check.performClick()
                        assertTrue(start.isEnabled)
                        listOf("provider", "measurements").forEach { assertNull(field(activity, it)) }
                    }
                }
            }
        }
        assertEquals(before, snapshot())
    }

    @Test fun malformedConfirmationLaunchIsRejectedWithoutTouchingFiles() {
        val before = snapshot()
        val plan = plan()
        val good = intent(plan, GazeExperimentCalibrationActivity::class.java)
        assertNotNull(ACConfirmationLaunch.parse(good))
        assertNull(ACConfirmationLaunch.parse(Intent(good).putExtra(ExperimentFlow.EXTRA_CONFIGURATION, "A")))
        assertNull(ACConfirmationLaunch.parse(Intent(good).putExtra(ExperimentFlow.EXTRA_SESSION, plan.steps[0].id)))
        val wrongVersion = JSONObject(requireNotNull(good.getStringExtra(ACConfirmationLaunch.EXTRA))).put("confirmation_plan", "future")
        assertNull(ACConfirmationLaunch.parse(Intent(good).putExtra(ACConfirmationLaunch.EXTRA, wrongVersion.toString())))
        val wrongRound = JSONObject(requireNotNull(good.getStringExtra(ACConfirmationLaunch.EXTRA))).put("confirmation_round", "1")
        assertNull(ACConfirmationLaunch.parse(Intent(good).putExtra(ACConfirmationLaunch.EXTRA, wrongRound.toString())))
        assertEquals(before, snapshot())
    }
}
