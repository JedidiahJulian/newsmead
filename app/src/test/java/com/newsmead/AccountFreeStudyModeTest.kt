package com.newsmead

import com.newsmead.data.FirebaseHelper
import com.newsmead.data.StudyConfig
import com.newsmead.models.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AccountFreeStudyModeTest {
    @Test fun loginAndSavedListsAreEnabledByDefaultOnPanelDemoBranch() {
        assertFalse("SplashActivity startup login gate should be enabled on panel demo branch", StudyConfig.BYPASS_LOGIN)
        assertFalse("Panel demo build must enable Firebase login and saved lists by default", StudyConfig.ACCOUNT_FREE_STUDY)
    }

    @Test fun articleHistoryNeedsNoFirebaseAppInStudyBuild() {
        assumeTrue("This check applies when built with -PnewsmeadAccountFreeStudy=true", StudyConfig.ACCOUNT_FREE_STUDY)
        // There is no initialized FirebaseApp (or Android context) in this JVM test.
        // An auth/history access here would fail, including for cached-account paths.
        FirebaseHelper.addArticleToHistory(Article("Source", "Example", "Date", "1 min", "https://example.org"))
        assertEquals("null", FirebaseHelper.getUid())
    }
}
