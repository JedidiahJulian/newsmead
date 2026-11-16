package com.newsmead.gaze.aid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real menu widgets on the camera-free replay activity. */
@RunWith(AndroidJUnit4::class)
class ReadingAidControlsSheetTest {
    @Test fun threeLineChoiceIsExclusiveAndSurvivesMenuReopening() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var value = ReaderAidPreferences(focusRegion = false, wordDefinition = false)
                val menu = ReadingAidControlsSheet.show(activity, value, { value = it })
                try {
                    val line = menu.findViewById<MaterialRadioButton>(R.id.readingAidMenuLine)!!
                    val three = menu.findViewById<MaterialRadioButton>(R.id.readingAidMenuThreeLine)!!
                    assertEquals(ReadingGuideStyle.THREE_LINE, value.guideStyle)
                    assertTrue(three.isChecked)
                    line.performClick()
                    assertEquals(ReadingGuideStyle.LINE, value.guideStyle)
                    three.performClick()
                    assertEquals(ReadingGuideStyle.THREE_LINE, value.guideStyle)
                    assertTrue(three.isChecked)
                    assertFalse(line.isChecked)
                    assertFalse(menu.findViewById<MaterialRadioButton>(R.id.readingAidMenuSentence)!!.isChecked)
                    val guide = menu.findViewById<SwitchMaterial>(R.id.readingAidMenuGuide)!!
                    guide.performClick()
                    assertFalse(three.isEnabled)
                    guide.performClick()
                    assertTrue(three.isEnabled); assertTrue(three.isChecked)
                    assertFalse(value.focusRegion); assertFalse(value.wordDefinition)
                } finally { menu.dismiss() }
                val reopened = ReadingAidControlsSheet.show(activity, value, { value = it })
                try {
                    assertTrue(reopened.findViewById<MaterialRadioButton>(R.id.readingAidMenuThreeLine)!!.isChecked)
                } finally { reopened.dismiss() }
            }
        }
    }

    @Test fun masterRestoresIndependentChoicesAndGuideSelectionIsExclusive() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            val dismissed = AtomicInteger()
            val dismissal = CountDownLatch(1)
            scenario.onActivity { activity ->
                var value = ReaderAidPreferences(focusRegion = false, wordMagnifier = false,
                    manualStrength = .35, maxStrength = .8)
                val menu = ReadingAidControlsSheet.show(activity, value, { value = it }, {
                    dismissed.incrementAndGet(); dismissal.countDown()
                })
                try {
                    val line = menu.findViewById<MaterialRadioButton>(R.id.readingAidMenuLine)!!
                    val sentence = menu.findViewById<MaterialRadioButton>(R.id.readingAidMenuSentence)!!
                    val off = menu.findViewById<MaterialRadioButton>(R.id.readingAidModeOff)!!
                    val auto = menu.findViewById<MaterialRadioButton>(R.id.readingAidModeAuto)!!
                    sentence.performClick()
                    assertEquals(ReadingGuideStyle.SENTENCE, value.guideStyle)
                    assertTrue(sentence.isChecked); assertFalse(line.isChecked)
                    off.performClick()
                    assertEquals(ReadingAidsMode.OFF, value.mode); assertFalse(sentence.isEnabled)
                    assertTrue(sentence.isChecked); assertFalse(value.focusRegion)
                    auto.performClick()
                    assertEquals(ReadingAidsMode.AUTO, value.mode); assertTrue(sentence.isEnabled)
                    assertFalse(value.focusRegion); assertFalse(value.wordMagnifier)
                    assertEquals(.35, value.manualStrength, 0.0)
                    assertEquals(.8, value.maxStrength, 0.0)
                } finally { menu.dismiss() }
            }
            // Dialog dismissal posts its callback. Wait off the UI thread.
            assertTrue("Menu dismissal callback", dismissal.await(2, TimeUnit.SECONDS))
            assertEquals(1, dismissed.get())
        }
    }

    @Test fun individualSwitchesApplyBeforeClosingAndKeepOtherChoices() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var value = ReaderAidPreferences(guideStyle = ReadingGuideStyle.SENTENCE)
                val menu = ReadingAidControlsSheet.show(activity, value, { value = it })
                try {
                    menu.findViewById<SwitchMaterial>(R.id.readingAidMenuDefinitions)!!.performClick()
                    assertFalse(value.wordDefinition)
                    assertTrue(value.lineStartCue); assertTrue(value.focusRegion)
                    assertEquals(ReadingGuideStyle.SENTENCE, value.guideStyle)
                    menu.findViewById<SwitchMaterial>(R.id.readingAidMenuGuide)!!.performClick()
                    assertFalse(value.lineGuide)
                    assertFalse(menu.findViewById<MaterialRadioButton>(R.id.readingAidMenuSentence)!!.isEnabled)
                    assertTrue(menu.isShowing)
                } finally { menu.dismiss() }
            }
        }
    }

    @Test fun focusAppearanceSelectionPersistsAndDisablesWithFocusOrMaster() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var value = ReaderAidPreferences(guideStyle = ReadingGuideStyle.SENTENCE,
                    wordDefinition = false, lineStartCue = false, manualStrength = .37,
                    maxStrength = .82)
                val menu = ReadingAidControlsSheet.show(activity, value, { value = it })
                try {
                    val fade = menu.findViewById<MaterialRadioButton>(R.id.readingAidMenuFocusFade)!!
                    val dark = menu.findViewById<MaterialRadioButton>(R.id.readingAidMenuFocusDark)!!
                    val focus = menu.findViewById<SwitchMaterial>(R.id.readingAidMenuFocus)!!
                    val off = menu.findViewById<MaterialRadioButton>(R.id.readingAidModeOff)!!
                    val on = menu.findViewById<MaterialRadioButton>(R.id.readingAidModeOn)!!
                    assertTrue(fade.isChecked); assertFalse(dark.isChecked)
                    dark.performClick()
                    assertEquals(ReadingFocusStyle.DARK_SURROUNDINGS, value.focusStyle)
                    assertTrue(dark.isChecked); assertFalse(fade.isChecked)
                    focus.performClick()
                    assertFalse(value.focusRegion); assertFalse(dark.isEnabled); assertFalse(fade.isEnabled)
                    assertEquals(ReadingFocusStyle.DARK_SURROUNDINGS, value.focusStyle)
                    focus.performClick()
                    assertTrue(value.focusRegion); assertTrue(dark.isEnabled); assertTrue(dark.isChecked)
                    off.performClick()
                    assertEquals(ReadingAidsMode.OFF, value.mode); assertFalse(dark.isEnabled)
                    assertEquals(ReadingFocusStyle.DARK_SURROUNDINGS, value.focusStyle)
                    on.performClick()
                    assertEquals(ReadingAidsMode.ON, value.mode); assertTrue(dark.isEnabled); assertTrue(dark.isChecked)
                    assertEquals(ReadingGuideStyle.SENTENCE, value.guideStyle)
                    assertFalse(value.wordDefinition); assertFalse(value.lineStartCue)
                    assertEquals(.37, value.manualStrength, 0.0); assertEquals(.82, value.maxStrength, 0.0)
                } finally { menu.dismiss() }

                val reopened = ReadingAidControlsSheet.show(activity, value, { value = it })
                try {
                    assertTrue(reopened.findViewById<MaterialRadioButton>(R.id.readingAidMenuFocusDark)!!.isChecked)
                    assertFalse(reopened.findViewById<MaterialRadioButton>(R.id.readingAidMenuFocusFade)!!.isChecked)
                } finally { reopened.dismiss() }
            }
        }
    }

    @Test fun hideGazeCursorToggleWorksAcrossModesAndControlsOverlayVisibility() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var value = ReaderAidPreferences()
                val withoutOption = ReadingAidControlsSheet.show(activity, value, { value = it })
                try {
                    assertNull(withoutOption.findViewById<android.view.View>(R.id.readingAidMenuHideGazeCursor))
                } finally { withoutOption.dismiss() }

                val menu = ReadingAidControlsSheet.show(activity, value, { value = it },
                    supportsHideGazeCursor = true)
                try {
                    val showCursor = menu.findViewById<SwitchMaterial>(R.id.readingAidMenuHideGazeCursor)!!
                    assertTrue(showCursor.isChecked)
                    menu.findViewById<MaterialRadioButton>(R.id.readingAidModeOff)!!.performClick()
                    assertTrue("Show gaze cursor stays enabled even when aids are off", showCursor.isEnabled)
                    showCursor.performClick()
                    assertTrue(value.hideGazeCursor)
                    assertFalse(showCursor.isChecked)
                } finally { menu.dismiss() }

                val reopened = ReadingAidControlsSheet.show(activity, value, { value = it },
                    supportsHideGazeCursor = true)
                try {
                    assertFalse(reopened.findViewById<SwitchMaterial>(R.id.readingAidMenuHideGazeCursor)!!.isChecked)
                } finally { reopened.dismiss() }

                val overlay = com.newsmead.gaze.GazeOverlayView(activity).apply {
                    setCursorStyle(com.newsmead.gaze.GazeOverlayView.CursorStyle.DOT)
                    setDebugVisualsEnabled(true)
                    setGazeScreen(100f, 100f)
                    layout(0, 0, 200, 200)
                }
                val visibleBmp = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)
                overlay.draw(android.graphics.Canvas(visibleBmp))
                assertTrue("Visible cursor draws pixels", visibleBmp.getPixel(100, 100) != 0)

                overlay.setCursorVisible(false)
                val hiddenBmp = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)
                overlay.draw(android.graphics.Canvas(hiddenBmp))
                assertEquals("Hidden cursor draws no cursor pixels", 0, hiddenBmp.getPixel(100, 100))

                overlay.setCursorVisible(true)
                val restoredBmp = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)
                overlay.draw(android.graphics.Canvas(restoredBmp))
                assertTrue("Restored cursor draws pixels again", restoredBmp.getPixel(100, 100) != 0)
            }
        }
    }

    @Test fun inlineCustomizeDrawersAndCloseWorkCleanly() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var value = ReaderAidPreferences()
                val menu = ReadingAidControlsSheet.show(activity, value, { value = it },
                    supportsAutoScroll = true, supportsHideGazeCursor = true)
                try {
                    val guideOptions = menu.findViewById<android.view.View>(R.id.readingAidGuideOptions)!!
                    val focusOptions = menu.findViewById<android.view.View>(R.id.readingAidFocusOptions)!!
                    val advancedSection = menu.findViewById<android.view.View>(R.id.readingAidAdvancedSection)!!
                    assertEquals(android.view.View.GONE, guideOptions.visibility)
                    assertEquals(android.view.View.GONE, focusOptions.visibility)
                    assertEquals(android.view.View.GONE, advancedSection.visibility)

                    menu.findViewById<android.view.View>(R.id.readingAidGuideRow)!!.performClick()
                    assertEquals(android.view.View.VISIBLE, guideOptions.visibility)
                    menu.findViewById<android.view.View>(R.id.readingAidFocusRow)!!.performClick()
                    assertEquals(android.view.View.VISIBLE, focusOptions.visibility)
                    menu.findViewById<android.view.View>(R.id.readingAidMenuAdvanced)!!.performClick()
                    assertEquals(android.view.View.VISIBLE, advancedSection.visibility)

                    menu.findViewById<android.view.View>(R.id.readingAidMenuClose)!!.performClick()
                    assertFalse(menu.isShowing)
                } finally { menu.dismiss() }
            }
        }
    }
}
