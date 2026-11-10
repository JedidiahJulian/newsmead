package com.newsmead.research.study

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyReadingSurfaceTest {
    private fun settings() = JSONObject().put("text_size_px", 55.0).put("column_width_px", 990)
        .put("density", 2.5).put("font_scale", 1.0).put("line_spacing_extra_px", 2.5)
        .put("line_spacing_multiplier", 1.6).put("text_scale_x", 1.0).put("letter_spacing", 0.0)
        .put("typeface_style", 0).put("language", "en-US").put("font_resource", "fragment_article/ts_body_large")
        .put("reading_viewport_screen_px", JSONArray(listOf(0, 100, 1080, 2200)))
        .put("body_padding_px", JSONArray(listOf(16, 0, 0, 0)))

    @Test fun compatibilitySeparatesActualAidSettingsAndMovingBodyClipFromFixedReadingSurface() {
        val original = settings().put("aid_preferences", JSONObject().put("enabled", false))
            .put("viewport_rect_screen_px", JSONArray(listOf(10, 200, 1050, 2200)))
        val assisted = JSONObject(original.toString()).put("aid_preferences", JSONObject().put("enabled", true))
            .put("viewport_rect_screen_px", JSONArray(listOf(10, 100, 1050, 2100))).put("text_color", 123)
        assertEquals(StudyReadingSurface.specification(original).toString(), StudyReadingSurface.specification(assisted).toString())
        assertTrue(original.has("aid_preferences"))
        assertFalse(original.has("version"))
    }

    @Test fun typographyPaddingAndActualReadingViewportCannotReuseTheSameIdentity() {
        val base = settings()
        for ((key, value) in listOf("text_size_px" to 60.0, "column_width_px" to 950,
            "line_spacing_multiplier" to 1.8, "language" to "fil-PH",
            "reading_viewport_screen_px" to JSONArray(listOf(0, 200, 1080, 2200)),
            "body_padding_px" to JSONArray(listOf(20, 0, 0, 0)))) {
            assertNotEquals(key, StudyReadingSurface.specification(base).toString(),
                StudyReadingSurface.specification(JSONObject(base.toString()).put(key, value)).toString())
        }
        val missing = settings().apply { remove("font_resource") }
        try { StudyReadingSurface.specification(missing); fail("Missing geometry metadata must reject") }
        catch (_: IllegalArgumentException) { }
    }
}
