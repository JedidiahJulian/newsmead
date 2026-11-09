package com.newsmead.gaze.aid

import android.content.Context
import android.content.SharedPreferences

/** Persistent reader choices. The legacy boolean is read only for one-way mode migration. */
object ReaderAidPreferencesStore {
    const val PREFERENCES_NAME = "article_reading_preferences"
    const val KEY_MODE = "reading_aids_mode"
    const val LEGACY_KEY_ENABLED = "reading_aids_enabled"

    fun load(context: Context, defaults: ReaderAidPreferences = ReaderAidPreferences()): ReaderAidPreferences {
        val storage = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val mode = decodeMode(storage.getString(KEY_MODE, null),
            storage.getBoolean(LEGACY_KEY_ENABLED, true).takeIf { storage.contains(LEGACY_KEY_ENABLED) },
            defaults.mode)
        val value = defaults.copy(
            mode = mode,
            lineGuide = storage.getBoolean("reading_aids_line_guide", defaults.lineGuide),
            lineStartCue = storage.getBoolean("reading_aids_bookmark", defaults.lineStartCue),
            wordMagnifier = storage.getBoolean("reading_aids_word_magnifier", defaults.wordMagnifier),
            focusRegion = storage.getBoolean("reading_aids_focus", defaults.focusRegion),
            adaptiveStrength = storage.getBoolean("reading_aids_adaptive_strength", defaults.adaptiveStrength),
            maxStrength = storage.getFloat("reading_aids_max_strength", defaults.maxStrength.toFloat()).toDouble(),
            manualStrength = storage.getFloat("reading_aids_manual_strength", defaults.manualStrength.toFloat()).toDouble(),
            wordDefinition = storage.getBoolean("reading_aids_word_definition", defaults.wordDefinition),
            guideStyle = enumValue(storage.getString("reading_aids_guide_style", null), defaults.guideStyle),
            focusStyle = enumValue(storage.getString("reading_aids_focus_style", null), defaults.focusStyle),
            autoScroll = storage.getBoolean("reading_aids_auto_scroll", defaults.autoScroll)
        )
        if (!storage.contains(KEY_MODE) && storage.contains(LEGACY_KEY_ENABLED)) save(storage, value)
        return value
    }

    fun save(context: Context, value: ReaderAidPreferences) =
        save(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE), value)

    internal fun decodeMode(stored: String?, legacyEnabled: Boolean?, default: ReadingAidsMode): ReadingAidsMode =
        stored?.let { runCatching { ReadingAidsMode.valueOf(it.uppercase()) }.getOrNull() }
            ?: legacyEnabled?.let { if (it) ReadingAidsMode.AUTO else ReadingAidsMode.OFF }
            ?: default

    private inline fun <reified T : Enum<T>> enumValue(stored: String?, default: T): T =
        stored?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun save(storage: SharedPreferences, value: ReaderAidPreferences) {
        storage.edit()
            .putString(KEY_MODE, value.mode.name.lowercase())
            .putBoolean("reading_aids_line_guide", value.lineGuide)
            .putBoolean("reading_aids_bookmark", value.lineStartCue)
            .putBoolean("reading_aids_word_magnifier", value.wordMagnifier)
            .putBoolean("reading_aids_focus", value.focusRegion)
            .putBoolean("reading_aids_adaptive_strength", value.adaptiveStrength)
            .putFloat("reading_aids_max_strength", value.maxStrength.toFloat())
            .putFloat("reading_aids_manual_strength", value.manualStrength.toFloat())
            .putBoolean("reading_aids_word_definition", value.wordDefinition)
            .putString("reading_aids_guide_style", value.guideStyle.name)
            .putString("reading_aids_focus_style", value.focusStyle.name)
            .putBoolean("reading_aids_auto_scroll", value.autoScroll)
            .apply()
    }
}
