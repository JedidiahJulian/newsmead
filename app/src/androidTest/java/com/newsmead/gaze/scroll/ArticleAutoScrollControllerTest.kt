package com.newsmead.gaze.scroll

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.radiobutton.MaterialRadioButton
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
    /** Supplied delayed coordinates exercise navigation timing, not live gaze accuracy. */
    @Test fun trackerLatencySupportsContinuousMiddleGazeButSilenceStillStopsMotion() {
        AutoScrollAttachedFixture(prepareHeader = true).use { f ->
            val before = f.scrollY()
            f.feed(1600, .50, captureLagMs = 220.0, deliveryIntervalMs = 55L)
            val middleEnd = f.scrollY()
            f.onMain {
                assertTrue("Delayed middle-screen gaze should keep scrolling", f.controller.isMoving)
                assertTrue("Expected sustained movement, not a fractional startup step",
                    middleEnd - before > f.screenHeight * .02)
                assertEquals("Continuous input should start a single motion interval", 1,
                    f.boundaries.count { it.start })
                assertFalse("Fresh delivered coordinates must not expire between callbacks",
                    f.boundaries.any { !it.start && it.detail.optString("reason") == "stale" })
            }
            f.waitPosted(200)
            f.onMain {
                assertFalse("Delivery silence must still stop scrolling", f.controller.isMoving)
                assertEquals("stale", f.controller.statusReason)
            }
            val stoppedY = f.scrollY()
            f.waitPosted(200)
            assertEquals("No timer-driven movement after gaze expires", stoppedY, f.scrollY())
            f.feed(800, .65, captureLagMs = 232.0, deliveryIntervalMs = 55L)
            assertTrue("New delayed input must recover after silence", f.scrollY() > stoppedY)
            f.onMain { assertTrue(f.controller.isMoving) }
            f.feed(400, .25, captureLagMs = 232.0, deliveryIntervalMs = 55L)
            f.onMain {
                assertFalse("Upper gaze must still brake to a stop", f.controller.isMoving)
                assertEquals("waiting_lower", f.controller.statusReason)
            }
        }
    }

    @Test fun viewportPreparationPrecedesEnableAndResumeMotionWithoutLayoutCancellation() {
        AutoScrollAttachedFixture(prepareHeader = true).use { f ->
            f.onMain {
                f.controller.setEnabled(true)
                assertEquals("Repeated enable must not prepare the viewport again", listOf("prepare"), f.viewportEvents)
            }
            val before = f.scrollY()
            f.feed(750, .50)
            f.onMain {
                assertEquals(listOf("prepare", "start"), f.viewportEvents)
                assertTrue("Header must already be collapsed at first motion", f.headerBottomAtStarts.single() <= 1f)
                assertTrue(f.controller.isMoving)
                assertTrue(f.scrollChanges.count { it.second > before } >= 3)
                assertFalse(f.boundaries.any { !it.start && it.detail.optString("reason") == "reading_layout_changed" })
                f.controller.pauseByReader()
                f.binding.articleHeaderAppBar.setExpanded(true, false)
            }
            f.waitPosted(200)
            f.onMain {
                assertTrue("Resume fixture must have expanded header", f.binding.articleHeaderAppBar.y >= 0f)
                f.controller.resumeByReader()
                assertEquals(listOf("prepare", "start", "prepare"), f.viewportEvents)
            }
            val resumedY = f.scrollY()
            f.feed(750, .60)
            f.onMain {
                assertEquals(listOf("prepare", "start", "prepare", "start"), f.viewportEvents)
                assertEquals(listOf(true, true), f.preparationSeenBeforeState)
                assertTrue(f.headerBottomAtStarts.all { it <= 1f })
                assertTrue(f.controller.isMoving)
                assertTrue(f.scrollChanges.count { it.second > resumedY } >= 3)
                assertFalse(f.boundaries.any { !it.start && it.detail.optString("reason") == "reading_layout_changed" })
            }
        }
    }

    @Test fun actualGeometryChangeStillStopsAndRejectsCapturesFromBeforeChange() {
        AutoScrollAttachedFixture(prepareHeader = true).use { f ->
            f.feed(500, .50)
            var oldCapture = 0.0
            f.onMain {
                assertTrue(f.controller.isMoving)
                oldCapture = f.nowMs()
                val body = f.binding.tvArticleText
                body.setPadding(body.paddingLeft, body.paddingTop + 4, body.paddingRight, body.paddingBottom)
                f.controller.onPreDraw()
                assertFalse(f.controller.isMoving)
                assertEquals("reading_layout_changed", f.boundaries.last().detail.getString("reason"))
            }
            f.waitPosted(200)
            val stoppedY = f.scrollY()
            f.onMain {
                f.emit(.50, captureMs = oldCapture)
                assertFalse(f.controller.isMoving)
                assertEquals("settling", f.controller.statusReason)
            }
            f.waitPosted(100)
            assertEquals(stoppedY, f.scrollY())
            f.feed(500, .50)
            assertTrue("New captures after settled reflow must resume", f.scrollY() > stoppedY)
        }
    }

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
                    article.findViewById<MaterialRadioButton>(R.id.readingAidModeOff)!!.performClick()
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
