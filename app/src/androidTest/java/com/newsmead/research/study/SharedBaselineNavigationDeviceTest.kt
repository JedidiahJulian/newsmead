package com.newsmead.research.study

import android.app.Activity
import android.os.SystemClock
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.newsmead.R
import com.newsmead.activities.ArticleActivity
import com.newsmead.activities.MainActivity
import com.newsmead.activities.ReadingBaselineActivity
import com.newsmead.fragments.article.ArticleFragment
import com.newsmead.gaze.SharedReadingBaseline
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Home navigation and no-run instructions only. Never starts capture or changes saved study state. */
@RunWith(AndroidJUnit4::class)
class SharedBaselineNavigationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun homeBaselineOpensSharedReaderSurvivesRecreationAndReturnsHomeRepeatedly() {
        main { assertNull("Stop the existing study run before this navigation test", StudyCollection.run) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var reader: ArticleActivity? = null
            try {
                repeat(2) { visit ->
                    scenario.onActivity { home -> assertHome(home) }
                    // Exercise the installed Home listener and nav_graph, never a synthetic Intent.
                    onView(withId(R.id.btnReadingBaseline)).check(matches(isDisplayed())).perform(click())
                    reader = awaitResumed(ArticleActivity::class.java)
                    assertSharedReader(requireNotNull(reader))

                    if (visit == 0) {
                        val original = requireNotNull(reader)
                        main { original.recreate() }
                        reader = awaitResumed(ArticleActivity::class.java, excluding = original)
                        assertSharedReader(requireNotNull(reader))
                        assertNoRunInstructions()
                        // Exercise a reader swipe before using the toolbar Back control.
                        onView(withId(R.id.nsvArticleText)).perform(swipeDown())
                        instrumentation.waitForIdleSync()
                        onView(withId(R.id.btnArticleBack)).check(matches(isDisplayed())).perform(click())
                    } else {
                        pressBack()
                    }

                    awaitResumed(MainActivity::class.java)
                    scenario.onActivity { home -> assertHome(home) }
                    main {
                        assertTrue("Back must finish the reader, not expose a duplicate reader", requireNotNull(reader).isFinishing || requireNotNull(reader).isDestroyed)
                        assertNull("Navigation must not start a study run", StudyCollection.run)
                    }
                    reader = null
                }
            } finally {
                // Only close the reader opened by this test if an assertion fails while it is visible.
                main { reader?.takeUnless { it.isFinishing || it.isDestroyed }?.finish() }
            }
        }
    }

    private fun assertHome(home: MainActivity) {
        assertNull(StudyCollection.run)
        val host = home.supportFragmentManager.findFragmentById(R.id.mainContainer) as NavHostFragment
        assertEquals(R.id.nav_graph, host.navController.graph.id)
        assertEquals(R.id.homeFragment, host.navController.currentDestination?.id)
    }

    private fun assertNoRunInstructions() {
        main { assertNull("Instructions must be opened without an active run", StudyCollection.run) }
        onView(withId(R.id.btnArticleReadingAids)).perform(click())
        onView(withText(containsString("Start a Reading run in Study recording first.")))
            .inRoot(isDialog()).check(matches(isDisplayed()))
        // There must be no positive Start action; the underlying footer also says Start.
        onView(withText("Start")).inRoot(isDialog()).check(doesNotExist())
        onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click())
        main { assertNull("Dismissed instructions must not start recording", StudyCollection.run) }
    }

    private fun assertSharedReader(reader: ArticleActivity) {
        main {
            assertNull(StudyCollection.run)
            assertTrue("Home must send the shared-mode flag", reader.intent.getBooleanExtra(SharedReadingBaseline.ARGUMENT, false))
            val host = reader.supportFragmentManager.findFragmentById(R.id.flMainArticleContainer) as NavHostFragment
            assertEquals(R.id.nav_article_graph, host.navController.graph.id)
            assertEquals(R.id.articleFragment, host.navController.currentDestination?.id)
            val fragment = host.childFragmentManager.primaryNavigationFragment
            assertTrue("The actual ArticleFragment must be shown", fragment is ArticleFragment)
            assertTrue("ArticleActivity must forward shared mode into ArticleFragment", fragment?.arguments?.getBoolean(SharedReadingBaseline.ARGUMENT) == true)
            assertTrue(requireNotNull(fragment).isResumed)
            assertEquals("A recreated reader must not retain duplicate article destinations", 1,
                host.childFragmentManager.fragments.count { it is ArticleFragment && it.isAdded })
            assertEquals(ReadingBaselineActivity.BASELINE_PASSAGE,
                fragment.requireView().findViewById<TextView>(R.id.tvArticleText).text.toString())
        }
        // These checks distinguish the baseline view from the ordinary article renderer.
        onView(withId(R.id.tvArticleText)).check(matches(isDisplayed()))
        onView(withId(R.id.btnArticleReadingAids))
            .check(matches(withText("Start")))
            .check(matches(withContentDescription("Start shared reading baseline")))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btnReadAloudArticle)).check(matches(not(isEnabled())))
    }

    private fun <T : Activity> awaitResumed(type: Class<T>, excluding: Activity? = null): T {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            instrumentation.waitForIdleSync()
            var found: T? = null
            main {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filter { type.isInstance(it) && it !== excluding }
                assertTrue("Expected at most one resumed ${type.simpleName}", resumed.size <= 1)
                found = resumed.singleOrNull()?.let { type.cast(it) }
            }
            found?.let { return it }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("No resumed ${type.simpleName} within 5 seconds")
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync { block() }
}
