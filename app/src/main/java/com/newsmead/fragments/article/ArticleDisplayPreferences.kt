package com.newsmead.fragments.article

import android.content.Context
import com.newsmead.data.StudyConfig

/** Persistent article presentation choices; these do not change gaze processing. */
object ArticleDisplayPreferences {
    private const val PREFERENCES_NAME = "article_display_preferences"
    private const val KEY_SHOW_GAZE_DIAGNOSTICS = "show_gaze_diagnostics"

    fun showGazeDiagnostics(context: Context): Boolean = context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_SHOW_GAZE_DIAGNOSTICS, StudyConfig.GAZE_DEBUG_VISUALS)

    fun setShowGazeDiagnostics(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_GAZE_DIAGNOSTICS, visible)
            .apply()
    }
}
