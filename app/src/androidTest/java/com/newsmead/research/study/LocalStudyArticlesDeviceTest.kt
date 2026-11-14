package com.newsmead.research.study

import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.newsmead.R
import com.newsmead.activities.AccountActivity
import com.newsmead.activities.MainActivity
import com.newsmead.data.StudyArticleCatalog
import com.newsmead.data.StudyConfig
import com.newsmead.fragments.saved.StudyArticlesFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Saved-list and login UI navigation only. Never opens an article, camera, calibration or recording. */
@RunWith(AndroidJUnit4::class)
class LocalStudyArticlesDeviceTest {
    @Test fun savedListsTestingArticlesAndLoginUiSurviveNavigationAndRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val expected = context.assets.open(StudyConfig.ARTICLES_ASSET).bufferedReader().use {
            StudyArticleCatalog.parse(it.readText())
        }
        assertTrue(expected.isNotEmpty())
        instrumentation.runOnMainSync { assertNull("Stop the current run before UI checks", StudyCollection.run) }

        if (StudyConfig.ACCOUNT_FREE_STUDY) {
            @Suppress("DEPRECATION")
            val metadata = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
            assertTrue(metadata.getBoolean("firebase_analytics_collection_deactivated"))
            assertFalse(metadata.getBoolean("firebase_crashlytics_collection_enabled", true))
            assertNull("The reporting component must be absent, regardless of retained SDK opt-in",
                FirebaseApp.getInstance().get(FirebaseCrashlytics::class.java))
        } else {
            // Verify AccountActivity Login <-> SignUp navigation without mutating auth state.
            // Use direct view actions rather than Espresso InputManager reflection (removed on Android 16).
            ActivityScenario.launch(AccountActivity::class.java).use { accountScenario ->
                accountScenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnAccLogIn)?.visibility)
                    val createBtn = checkNotNull(activity.findViewById<View>(R.id.btnAccCreate))
                    assertEquals(View.VISIBLE, createBtn.visibility)
                    createBtn.performClick()
                    activity.supportFragmentManager.executePendingTransactions()
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnAccStart)?.visibility)
                    val logBtn = checkNotNull(activity.findViewById<View>(R.id.btnAccLog))
                    assertEquals(View.VISIBLE, logBtn.visibility)
                    logBtn.performClick()
                    activity.supportFragmentManager.executePendingTransactions()
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnAccLogIn)?.visibility)
                }
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                if (!StudyConfig.ACCOUNT_FREE_STUDY) {
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnHistory)?.visibility)
                }
                activity.findViewById<View>(R.id.savedFragment).performClick()
            }
            selectTestingArticlesTabIfNeeded(scenario)
            awaitCatalog(scenario, expected.size, expected.first().title)

            scenario.recreate()
            awaitCatalog(scenario, expected.size, expected.first().title)

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.homeFragment).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.savedFragment).performClick()
            }
            selectTestingArticlesTabIfNeeded(scenario)
            awaitCatalog(scenario, expected.size, expected.first().title)
            instrumentation.runOnMainSync { assertNull(StudyCollection.run) }

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.profileFragment).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                if (StudyConfig.ACCOUNT_FREE_STUDY) {
                    val subtitle = checkNotNull(activity.findViewById<TextView>(R.id.tvProfileSubtitle))
                    assertEquals(activity.getString(R.string.profile_study_subtitle), subtitle.text.toString())
                } else {
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnSave)?.visibility)
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnLogout)?.visibility)
                }
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.swDarkmode)?.visibility)
                activity.findViewById<View>(R.id.savedFragment).performClick()
            }
            selectTestingArticlesTabIfNeeded(scenario)
            awaitCatalog(scenario, expected.size, expected.first().title)
        }
    }

    private fun selectTestingArticlesTabIfNeeded(scenario: ActivityScenario<MainActivity>) {
        if (StudyConfig.ACCOUNT_FREE_STUDY) return
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var selected = false
            scenario.onActivity { activity ->
                val tabs = activity.findViewById<TabLayout>(R.id.tlListTabs)
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.vpViewPager)
                if (tabs != null && pager != null && tabs.tabCount == 3) {
                    assertEquals(activity.getString(R.string.saved_your_lists), tabs.getTabAt(0)?.text?.toString())
                    assertEquals(activity.getString(R.string.saved_all), tabs.getTabAt(1)?.text?.toString())
                    assertEquals(activity.getString(R.string.saved_testing_articles), tabs.getTabAt(2)?.text?.toString())
                    pager.setCurrentItem(2, false)
                    selected = true
                }
            }
            if (selected) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Saved tabs (Your Lists, All, Testing articles) did not appear")
    }

    private fun awaitCatalog(scenario: ActivityScenario<MainActivity>, expectedCount: Int, firstTitle: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var ready = false
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.mainContainer) as NavHostFragment
                val saved = host.childFragmentManager.primaryNavigationFragment
                val studyFragment = saved?.childFragmentManager?.fragments
                    ?.firstOrNull { it is StudyArticlesFragment && it.isResumed }
                val list = studyFragment?.view?.findViewById<RecyclerView>(R.id.rvSavedAll)
                val firstItemTitle = list?.findViewHolderForAdapterPosition(0)
                    ?.itemView?.findViewById<TextView>(R.id.tvArticleTitle)?.text?.toString()
                ready = host.navController.currentDestination?.id == R.id.savedFragment &&
                    list?.adapter?.itemCount == expectedCount &&
                    firstItemTitle == firstTitle
            }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("The packaged article list did not appear")
    }
}
