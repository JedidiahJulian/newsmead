package com.newsmead.fragments.article

import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.scroll.AutoScrollAttachedFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File

/** Attached, supplied-coordinate navigation only; no camera or human validation. */
@RunWith(AndroidJUnit4::class)
class ArticleAutoScrollFooterTest {
    @Test fun preparationBeforeFirstLayoutHidesAfterMeasurement() {
        checkUnmeasuredPreparation("armed")
    }

    @Test fun pauseBeforeFirstLayoutCancelsDeferredHide() {
        checkUnmeasuredPreparation("show")
    }

    @Test fun disableBeforeFirstLayoutPreventsDeferredHide() {
        checkUnmeasuredPreparation("disable")
    }

    @Test fun detachBeforeFirstLayoutCancelsDeferredHide() {
        checkUnmeasuredPreparation("detach")
    }

    @Test fun armedModeReclaimsFooterUntilExplicitPauseOrDisable() {
        AutoScrollAttachedFixture(prepareHeader = true, prepareFooter = true).use { f ->
            var formerFooterFraction = 0.0
            f.onMain {
                assertFalse("Preparation must finish before first motion", f.controller.isMoving)
                assertTrue(f.boundaries.none { it.start })
                assertTrue(f.preparationSeenBeforeState.all { it })
                assertHiddenWithoutGap(f)
                formerFooterFraction = f.originalFooterRect.exactCenterY().toDouble() / f.screenHeight
                val body = Rect()
                val viewport = Rect()
                val pause = Rect()
                assertTrue(f.binding.tvArticleText.getVisibleRectOnScreen(body))
                assertTrue(f.binding.nsvArticleText.getVisibleRectOnScreen(viewport))
                assertTrue(body.intersect(viewport))
                val x = body.centerX()
                val y = f.originalFooterRect.centerY()
                assertTrue("Former footer center must now be readable article", body.contains(x, y))
                assertTrue(f.binding.btnArticleAutoScrollPause.getVisibleRectOnScreen(pause))
                assertFalse("Low-center input must avoid floating Pause", pause.contains(x, y))
                assertEquals(f.binding.root.context.getString(R.string.article_auto_scroll_pause),
                    f.binding.btnArticleAutoScrollPause.contentDescription.toString())
                assertTrue("Control uses an icon rather than a visible text label",
                    f.binding.btnArticleAutoScrollPause.text.isEmpty())
                saveButtonPreview(f, "running")
            }
            val before = f.scrollY()
            f.feed(650, formerFooterFraction)
            assertTrue("Gaze in former footer region must drive scrolling", f.scrollY() > before)
            f.feed(450, .25)
            f.onMain {
                assertFalse(f.controller.isMoving)
                assertHiddenWithoutGap(f)
            }
            f.feed(400, formerFooterFraction)
            f.waitPosted(300)
            f.onMain {
                assertFalse("Delivery silence must stop motion", f.controller.isMoving)
                assertHiddenWithoutGap(f)
            }
            f.feed(400, formerFooterFraction)
            f.onMain { f.emit(formerFooterFraction, reason = "blink") }
            f.waitPosted(300)
            val missingY = f.scrollY()
            f.onMain {
                assertFalse(f.controller.isMoving)
                assertHiddenWithoutGap(f)
            }
            f.waitPosted(250)
            assertEquals("Missing input must not continue navigation", missingY, f.scrollY())
            f.onMain {
                assertTrue(f.binding.btnArticleAutoScrollPause.performClick())
                assertTrue(f.controller.pausedByReader)
                assertEquals(f.binding.root.context.getString(R.string.article_auto_scroll_resume),
                    f.binding.btnArticleAutoScrollPause.contentDescription.toString())
            }
            f.waitPosted(400)
            f.onMain {
                assertFooterShown(f)
                saveButtonPreview(f, "paused")
                assertTrue(f.binding.btnArticleAutoScrollPause.performClick())
                assertFalse(f.controller.pausedByReader)
            }
            f.waitPosted(400)
            f.onMain { assertHiddenWithoutGap(f) }
            val resumedY = f.scrollY()
            f.feed(450, formerFooterFraction)
            assertTrue("Explicit resume must accept fresh low gaze", f.scrollY() > resumedY)
            f.onMain { f.controller.setEnabled(false) }
            f.waitPosted(400)
            f.onMain {
                assertFooterShown(f)
                assertEquals(View.GONE, f.binding.btnArticleAutoScrollPause.visibility)
                assertEquals(f.originalText, f.binding.tvArticleText.text.toString())
            }
        }
    }

    /** Component-only visual receipt: synthetic UI, with no camera or article image. */
    private fun saveButtonPreview(f: AutoScrollAttachedFixture, state: String) {
        val button = f.binding.btnArticleAutoScrollPause
        val preview = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
        try {
            button.draw(Canvas(preview))
            File(button.context.cacheDir, "auto-scroll-button-$state.png").outputStream().use {
                preview.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            preview.recycle()
        }
    }

    private fun assertHiddenWithoutGap(f: AutoScrollAttachedFixture) {
        assertEquals(View.INVISIBLE, f.binding.bottomAppBar.visibility)
        assertFalse(f.binding.bottomAppBar.getVisibleRectOnScreen(Rect()))
        assertEquals(-f.binding.bottomAppBar.height,
            (f.binding.bottomAppBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
        assertEquals(f.binding.root.height, f.binding.articleScrollingLayout.bottom)
        assertTrue("Floating Pause must remain accessible",
            f.binding.btnArticleAutoScrollPause.getVisibleRectOnScreen(Rect()))
        assertTrue(f.binding.btnArticleAutoScrollPause.isEnabled)
        assertTrue(f.binding.btnArticleAutoScrollPause.isClickable)
    }

    private fun assertFooterShown(f: AutoScrollAttachedFixture) {
        assertTrue(f.binding.bottomAppBar.getVisibleRectOnScreen(Rect()))
        assertEquals(0, (f.binding.bottomAppBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
        assertEquals(f.binding.bottomAppBar.top, f.binding.articleScrollingLayout.bottom)
    }

    private fun checkUnmeasuredPreparation(action: String) {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            lateinit var binding: FragmentArticleBinding
            lateinit var behavior: ArticleFooterScrollBehavior
            var armed = true
            val settled = CountDownLatch(1)
            scenario.onActivity { activity ->
                binding = FragmentArticleBinding.inflate(activity.layoutInflater)
                assertEquals(0, binding.bottomAppBar.height)
                behavior = ArticleFooterScrollBehavior(binding.nsvArticleText, binding.bottomAppBar) { armed }
                behavior.prepareForAutoScroll()
                when (action) {
                    "show" -> behavior.show()
                    "disable" -> armed = false
                    "detach" -> behavior.detach()
                }
                binding.root.doOnPreDraw {
                    binding.root.postDelayed({ settled.countDown() }, 400)
                }
                activity.setContentView(binding.root)
            }
            try {
                assertTrue("Unmeasured preparation did not settle", settled.await(3, TimeUnit.SECONDS))
                scenario.onActivity {
                    assertTrue(binding.bottomAppBar.height > 0)
                    val margin = (binding.bottomAppBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
                    if (action == "armed") {
                        assertEquals(View.INVISIBLE, binding.bottomAppBar.visibility)
                        assertEquals(-binding.bottomAppBar.height, margin)
                        assertEquals(binding.root.height, binding.articleScrollingLayout.bottom)
                    } else {
                        assertTrue(binding.bottomAppBar.getVisibleRectOnScreen(Rect()))
                        assertEquals(0, margin)
                    }
                }
            } finally {
                scenario.onActivity { behavior.detach() }
            }
        }
    }
}
