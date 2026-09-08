package com.newsmead.mgazenetbenchmark

import android.view.WindowManager
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Opens setup or an artificial target view only. Never clicks Start or requests a camera. */
class AccuracyHarnessTest {
    @Test fun openingInputCheckSetupKeepsCameraOffAndCreatesNoRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir,"input-check")
        val before = directory.list()?.toSet() ?: emptySet()
        ActivityScenario.launch(InputCheckActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        assertEquals(before,directory.list()?.toSet() ?: emptySet<String>())
    }

    @Test fun openingSetupDoesNotCreateACalibrationOrRequestActiveSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir,"accuracy")
        val before = directory.list()?.toSet() ?: emptySet()
        ActivityScenario.launch(AccuracyActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        assertEquals(before,directory.list()?.toSet() ?: emptySet<String>())
    }
    @Test fun insetViewUsesPhysicalScreenCoordinatesAndAcknowledgesDrawnTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var targetView: AccuracyTargetView? = null
        var acknowledged = -1
        ActivityScenario.launch(AccuracyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = FrameLayout(activity).apply { setPadding(17,29,19,31) }
                targetView = AccuracyTargetView(activity).also { view ->
                    view.onPresented = { token, _ -> acknowledged = token }
                    root.addView(view,FrameLayout.LayoutParams(-1,-1))
                }
                activity.setContentView(root)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val view = targetView!!; val layout = view.snapshot()
                val location = IntArray(2); view.getLocationOnScreen(location)
                assertEquals(location[0] + 12.0*view.resources.displayMetrics.density,layout.viewport.left,.001)
                assertEquals(location[1] + 12.0*view.resources.displayMetrics.density,layout.viewport.top,.001)
                val session = AccuracySession(layout,100.0,"synthetic_view_only")
                assertEquals(session.fitPoints.first().point.y/layout.screenHeight,
                    layout.label(session.fitPoints.first().point)[1].toDouble(),1e-7)
                view.target = session.target
            }
            instrumentation.waitForIdleSync()
            // Force a UI draw traversal before checking the posted acknowledgement.
            scenario.onActivity { targetView!!.invalidate() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(0,acknowledged) }
        }
    }
}
