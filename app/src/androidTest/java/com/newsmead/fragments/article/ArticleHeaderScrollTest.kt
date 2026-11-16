package com.newsmead.fragments.article

import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.scroll.AutoScrollAttachedFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual nested scrolling on an attached article layout; the host starts no camera. */
@RunWith(AndroidJUnit4::class)
class ArticleHeaderScrollTest {
    @Test fun downScrollHidesHeaderAndUpScrollRestoresItWithoutMovingFooterOrReflowingText() {
        AutoScrollAttachedFixture().use { f ->
            val footer = Rect()
            var lineCount = 0
            var expandedTextY = 0
            var range = 0
            f.onMain {
                assertTrue(f.binding.bottomAppBar.getVisibleRectOnScreen(footer))
                lineCount = f.binding.tvArticleText.layout.lineCount
                expandedTextY = textY(f.binding.tvArticleText)
                range = f.binding.articleHeaderAppBar.totalScrollRange
                assertTrue(range > 0)
                val consumed = IntArray(2)
                assertTrue(f.binding.nsvArticleText.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
                f.binding.nsvArticleText.dispatchNestedPreScroll(0, range, consumed, null, ViewCompat.TYPE_TOUCH)
                assertEquals(range, consumed[1])
                f.binding.nsvArticleText.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
            f.waitPosted(100)
            f.onMain {
                assertFalse(f.binding.clArticleTopBar.getVisibleRectOnScreen(Rect()))
                assertEquals(expandedTextY - range, textY(f.binding.tvArticleText))
                assertEquals(lineCount, f.binding.tvArticleText.layout.lineCount)
                assertEquals(f.originalText, f.binding.tvArticleText.text.toString())
                val currentFooter = Rect()
                assertTrue(f.binding.bottomAppBar.getVisibleRectOnScreen(currentFooter))
                assertEquals(footer, currentFooter)
                val viewport = Rect()
                assertTrue(f.binding.nsvArticleText.getVisibleRectOnScreen(viewport))
                assertTrue("Reading viewport must end above the footer", viewport.bottom <= footer.top)
                assertTrue(f.binding.nsvArticleText.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
                f.binding.nsvArticleText.dispatchNestedPreScroll(0, -range, IntArray(2), null, ViewCompat.TYPE_TOUCH)
                f.binding.nsvArticleText.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
            f.waitPosted(100)
            f.onMain {
                assertTrue(f.binding.clArticleTopBar.getVisibleRectOnScreen(Rect()))
                assertEquals(expandedTextY, textY(f.binding.tvArticleText))
                assertEquals(lineCount, f.binding.tvArticleText.layout.lineCount)
            }
        }
    }

    @Test fun demoControlsStayVisibleWhenOnlyTheArticleToolbarCollapses() {
        AutoScrollAttachedFixture().use { f ->
            f.onMain { f.binding.articleAidDemoPanel.visibility = View.VISIBLE }
            f.waitPosted(100)
            f.onMain {
                val scroll = f.binding.nsvArticleText
                assertTrue(scroll.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
                scroll.dispatchNestedPreScroll(0, f.binding.articleHeaderAppBar.totalScrollRange,
                    IntArray(2), null, ViewCompat.TYPE_TOUCH)
                scroll.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
            f.waitPosted(100)
            f.onMain {
                assertFalse(f.binding.clArticleTopBar.getVisibleRectOnScreen(Rect()))
                assertTrue(f.binding.articleAidDemoPanel.getVisibleRectOnScreen(Rect()))
                assertTrue(f.binding.articleAidDemoStop.getVisibleRectOnScreen(Rect()))
                assertTrue(f.binding.btnArticleReadingAids.getVisibleRectOnScreen(Rect()))
            }
        }
    }

    @Test fun downwardScrollHidesFooterAndUpwardScrollRestoresItWithoutBlankGap() {
        AutoScrollAttachedFixture().use { f ->
            lateinit var behavior: ArticleFooterScrollBehavior
            var rootHeight = 0
            f.onMain {
                rootHeight = f.binding.root.height
                behavior = ArticleFooterScrollBehavior(f.binding.nsvArticleText, f.binding.bottomAppBar)
                assertTrue(f.binding.bottomAppBar.getVisibleRectOnScreen(Rect()))
                f.binding.nsvArticleText.scrollBy(0, 400)
            }
            f.waitPosted(300)
            f.onMain {
                assertEquals(View.INVISIBLE, f.binding.bottomAppBar.visibility)
                assertFalse(f.binding.bottomAppBar.getVisibleRectOnScreen(Rect()))
                assertEquals(rootHeight, f.binding.articleScrollingLayout.bottom)
                f.binding.nsvArticleText.scrollBy(0, -400)
            }
            f.waitPosted(450)
            f.onMain {
                assertEquals(View.VISIBLE, f.binding.bottomAppBar.visibility)
                val footer = Rect()
                assertTrue(f.binding.bottomAppBar.getVisibleRectOnScreen(footer))
                val margin = (f.binding.bottomAppBar.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin
                assertEquals("Footer bottomMargin must restore to 0", 0, margin)
                assertEquals("Reading container must meet restored footer flush",
                    f.binding.bottomAppBar.top, f.binding.articleScrollingLayout.bottom)
                behavior.detach()
            }
        }
    }

    private fun textY(view: View): Int = IntArray(2).also(view::getLocationOnScreen)[1]
}
