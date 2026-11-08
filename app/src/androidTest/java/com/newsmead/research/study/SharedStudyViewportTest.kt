package com.newsmead.research.study

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.SparseArray
import android.view.ViewTreeObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.fragments.article.ArticleFragment
import com.newsmead.gaze.SharedReadingBaseline
import com.newsmead.gaze.getVisibleRectOnScreen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real article layout and touch/fling dispatch in a replay host: no camera, network or run. */
@RunWith(AndroidJUnit4::class)
class SharedStudyViewportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val reference = SharedReadingBaseline.Entry("package", "session", "attempt", "person",
        "installation", "calibration", "clock", 0.0, 65000.0)

    @Test fun baselineAndLinkedArticleKeepActualViewportWhilePassageScrollsBothWays() {
        var baselineViewport: Rect? = null
        for (baseline in listOf(true, false)) {
            ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
                lateinit var binding: FragmentArticleBinding
                var reader: SharedBaselineReader? = null
                val viewport = Rect()
                val observed = mutableListOf<Rect>()
                val draw = ViewTreeObserver.OnPreDrawListener {
                    val rect = Rect()
                    if (binding.nsvArticleText.getVisibleRectOnScreen(rect)) observed += rect
                    true
                }
                scenario.onActivity { activity ->
                    binding = FragmentArticleBinding.inflate(activity.layoutInflater)
                    if (baseline) reader = SharedBaselineReader(ArticleFragment(), binding).also { it.bind() }
                    else SharedStudyToolbar.forLinkedArticle(binding, reference)
                    binding.tvArticleText.text = (1..150).joinToString("\n\n") {
                        "Paragraph $it. Read this passage naturally and scroll by hand."
                    }
                    activity.setContentView(binding.root)
                }
                instrumentation.waitForIdleSync()
                var before = 0
                scenario.onActivity {
                    assertEquals(0, binding.articleHeaderAppBar.totalScrollRange)
                    binding.nsvArticleText.scrollTo(0, 1200)
                    before = binding.nsvArticleText.scrollY
                    assertTrue(binding.nsvArticleText.getVisibleRectOnScreen(viewport))
                    if (baseline) baselineViewport = Rect(viewport)
                    else assertEquals("Baseline and linked article must share viewport", baselineViewport, viewport)
                    binding.root.viewTreeObserver.addOnPreDrawListener(draw)
                }
                onView(withId(R.id.nsvArticleText)).perform(swipeUp())
                instrumentation.waitForIdleSync()
                var afterUp = 0
                scenario.onActivity {
                    afterUp = binding.nsvArticleText.scrollY
                    assertTrue("Up swipe must move passage", afterUp > before)
                }
                onView(withId(R.id.nsvArticleText)).perform(swipeDown())
                instrumentation.waitForIdleSync()
                var beforeFling = 0
                scenario.onActivity {
                    assertTrue("Down swipe must move passage", binding.nsvArticleText.scrollY < afterUp)
                    beforeFling = binding.nsvArticleText.scrollY
                    binding.nsvArticleText.fling(2200)
                }
                awaitMotion()
                var afterFling = 0
                scenario.onActivity {
                    afterFling = binding.nsvArticleText.scrollY
                    assertTrue("Positive fling must move passage", afterFling > beforeFling)
                    binding.nsvArticleText.fling(-2200)
                }
                awaitMotion()
                scenario.onActivity {
                    assertTrue("Negative fling must move passage", binding.nsvArticleText.scrollY < afterFling)
                    assertTrue("Expected viewport observations during gestures", observed.isNotEmpty())
                    observed.forEach { assertEquals("Viewport changed during passage motion", viewport, it) }
                    binding.root.viewTreeObserver.removeOnPreDrawListener(draw)
                    reader?.close()
                }
            }
        }
    }

    private fun awaitMotion() {
        val elapsed = CountDownLatch(1)
        Handler(Looper.getMainLooper()).postDelayed({ elapsed.countDown() }, 1200)
        assertTrue("Motion check timed out", elapsed.await(3, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    @Test fun restoredCollapsedHierarchyCannotCollapseLinkedToolbarAndUnlinkedEntryKeepsFlags() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            lateinit var binding: FragmentArticleBinding
            val saved = SparseArray<Parcelable>()
            scenario.onActivity { activity ->
                binding = FragmentArticleBinding.inflate(activity.layoutInflater)
                assertFalse(SharedStudyToolbar.forLinkedArticle(binding, null))
                activity.setContentView(binding.root)
                binding.articleHeaderAppBar.setExpanded(false, false)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertTrue("Ordinary entry retains collapsing toolbar", binding.articleHeaderAppBar.totalScrollRange > 0)
                binding.root.saveHierarchyState(saved)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                binding = FragmentArticleBinding.inflate(activity.layoutInflater)
                SharedStudyToolbar.forLinkedArticle(binding, reference)
                activity.setContentView(binding.root)
                binding.root.restoreHierarchyState(saved)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(0, binding.articleHeaderAppBar.totalScrollRange)
                assertEquals(0, binding.articleHeaderAppBar.top)
                assertTrue(binding.clArticleTopBar.getVisibleRectOnScreen(Rect()))
                assertFalse("Repeated attachment is idempotent", SharedStudyToolbar.forLinkedArticle(binding, reference))
            }
        }
    }
}
