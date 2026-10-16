package com.newsmead.gaze.scroll

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.switchmaterial.SwitchMaterial
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.gaze.aid.ReaderAidPreferences
import com.newsmead.gaze.aid.ReadingAidControlsSheet
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

/** Camera-free controls and attached controller boundaries; no human gaze validation. */
@RunWith(AndroidJUnit4::class)
class ArticleAutoScrollControllerTest {
    @Test fun articleMenuHasDefaultOffSwitchWhileOtherScreensHideIt() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var preferences = ReaderAidPreferences()
                val article = ReadingAidControlsSheet.show(activity, preferences, { preferences = it },
                    supportsAutoScroll = true)
                try {
                    val toggle = article.findViewById<SwitchMaterial>(R.id.readingAidMenuAutoScroll)!!
                    assertFalse(toggle.isChecked)
                    toggle.isChecked = true
                    assertTrue(preferences.autoScroll)
                    article.findViewById<SwitchMaterial>(R.id.readingAidMenuEnabled)!!.isChecked = false
                    assertFalse(toggle.isEnabled)
                } finally { article.dismiss() }
                val frozen = ReadingAidControlsSheet.show(activity, preferences, {})
                try { assertNull(frozen.findViewById<View>(R.id.readingAidMenuAutoScroll)) }
                finally { frozen.dismiss() }
            }
        }
    }

    @Test fun descendantTouchPausesUntilExplicitResumeAndTailFollowsSetting() {
        AutoScrollAttachedFixture().use { f ->
            f.onMain {
                assertEquals(ceil(.70 * f.screenHeight).toInt(), f.binding.articleAutoScrollTail.height)
            }
            f.feed(500, .65)
            f.onMain {
                assertTrue(f.controller.isMoving)
                f.touchBody()
                assertTrue(f.controller.pausedByReader)
                assertFalse(f.controller.isMoving)
                assertEquals("manual_touch", f.boundaries.last().detail.getString("reason"))
            }
            val pausedY = f.scrollY()
            f.feed(350, .65)
            assertEquals(pausedY, f.scrollY())
            f.onMain { f.controller.resumeByReader() }
            f.feed(350, .65)
            assertTrue("Fresh input after Resume must move without dwell", f.scrollY() > pausedY)
            f.onMain {
                f.controller.setEnabled(false)
                assertEquals(0, f.binding.articleAutoScrollTail.layoutParams.height)
                assertEquals(View.GONE, f.binding.articleAutoScrollTail.visibility)
            }
            val disabledY = f.scrollY()
            f.feed(250, .65)
            assertEquals(disabledY, f.scrollY())
            f.onMain { assertFalse(f.controller.isMoving) }
        }
    }

    @Test fun blockedLookupOfferStopsAndDiscardsCoordinatesCapturedBeforeRelease() {
        AutoScrollAttachedFixture().use { f ->
            f.feed(500, .65)
            var oldCapture = 0.0
            f.onMain {
                assertTrue(f.controller.isMoving)
                oldCapture = f.nowMs()
                f.blockedReason = "lookup_offer"
                f.controller.onPreDraw()
                assertFalse(f.controller.isMoving)
                assertEquals("lookup_offer", f.controller.statusReason)
                assertEquals("lookup_offer", f.boundaries.last().detail.getString("reason"))
            }
            val blockedY = f.scrollY()
            f.feed(250, .65)
            assertEquals(blockedY, f.scrollY())
            f.onMain {
                f.blockedReason = null
                f.emit(.65, captureMs = oldCapture)
                assertFalse(f.controller.isMoving)
            }
            f.waitPosted(250)
            assertEquals(blockedY, f.scrollY())
            f.feed(350, .65)
            assertTrue("Fresh released input must resume", f.scrollY() > blockedY)
        }
    }
}
