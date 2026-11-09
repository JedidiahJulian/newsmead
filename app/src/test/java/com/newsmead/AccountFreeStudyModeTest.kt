package com.newsmead

import com.newsmead.data.FirebaseHelper
import com.newsmead.data.StudyConfig
import com.newsmead.models.Article
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountFreeStudyModeTest {
    @Test fun articleHistoryNeedsNoFirebaseAppInStudyBuild() {
        assertTrue("This regression check applies to the account-free study build", StudyConfig.BYPASS_LOGIN)
        // There is no initialized FirebaseApp (or Android context) in this JVM test.
        // An auth/history access here would fail, including for cached-account paths.
        FirebaseHelper.addArticleToHistory(Article("Source", "Example", "Date", "1 min", "https://example.org"))
        assertEquals("null", FirebaseHelper.getUid())
    }
}
