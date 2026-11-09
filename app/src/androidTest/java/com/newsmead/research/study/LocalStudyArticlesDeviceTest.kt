package com.newsmead.research.study

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.newsmead.R
import com.newsmead.activities.MainActivity
import com.newsmead.data.StudyArticleCatalog
import com.newsmead.data.StudyConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Saved-list navigation only. Never opens an article, camera, calibration or recording. */
@RunWith(AndroidJUnit4::class)
class LocalStudyArticlesDeviceTest {
    @Test fun accountFreeCatalogSurvivesRecreationAndReturningFromHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(StudyConfig.BYPASS_LOGIN)
        val expected = context.assets.open(StudyConfig.ARTICLES_ASSET).bufferedReader().use {
            StudyArticleCatalog.parse(it.readText())
        }
        assertTrue(expected.isNotEmpty())
        @Suppress("DEPRECATION")
        val metadata = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        assertTrue(metadata.getBoolean("firebase_analytics_collection_deactivated"))
        assertFalse(metadata.getBoolean("firebase_crashlytics_collection_enabled", true))
        assertNull("The reporting component must be absent, regardless of retained SDK opt-in",
            FirebaseApp.getInstance().get(FirebaseCrashlytics::class.java))
        instrumentation.runOnMainSync { assertNull("Stop the current run before UI checks", StudyCollection.run) }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.savedFragment)).perform(click())
            awaitCatalog(scenario, expected.size)
            onView(withText(R.string.saved_testing_articles)).check(matches(isDisplayed()))
            onView(withText(expected.first().title)).check(matches(isDisplayed()))

            scenario.recreate()
            awaitCatalog(scenario, expected.size)

            onView(withId(R.id.homeFragment)).perform(click())
            onView(withId(R.id.savedFragment)).perform(click())
            awaitCatalog(scenario, expected.size)
            onView(withText(expected.first().title)).check(matches(isDisplayed()))
            instrumentation.runOnMainSync { assertNull(StudyCollection.run) }

            onView(withId(R.id.profileFragment)).perform(click())
            onView(withText(R.string.profile_study_subtitle)).check(matches(isDisplayed()))
            onView(withId(R.id.swDarkmode)).check(matches(isDisplayed()))
            onView(withId(R.id.savedFragment)).perform(click())
            awaitCatalog(scenario, expected.size)
        }
    }

    private fun awaitCatalog(scenario: ActivityScenario<MainActivity>, expectedCount: Int) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var ready = false
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.mainContainer) as NavHostFragment
                val list = activity.findViewById<RecyclerView>(R.id.rvSavedAll)
                ready = host.navController.currentDestination?.id == R.id.savedFragment &&
                    list?.adapter?.itemCount == expectedCount
            }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("The packaged article list did not appear")
    }
}
