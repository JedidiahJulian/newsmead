package com.newsmead.data

/**
 * Central switches for running NewsMead as a thesis-study research instrument
 * (no real users, no backend). Flip these back to false to restore the original
 * production behaviour.
 *
 * See PROGRESS_NOTES.md for the full context and the study_articles.json schema.
 */
object StudyConfig {

    /**
     * Bypass the Firebase login gate in SplashActivity and treat every session
     * as already authenticated, so launch goes straight to the home screen.
     */
    const val BYPASS_LOGIN = true

    /**
     * Serve article content from a bundled local asset instead of the (now
     * decommissioned) newsmead-api. When true, DataHelper.loadArticleData()
     * reads from [ARTICLES_ASSET] and the app runs fully offline.
     */
    const val OFFLINE_MODE = true

    /** Name of the bundled article dataset in app/src/main/assets/. */
    const val ARTICLES_ASSET = "study_articles.json"
}
