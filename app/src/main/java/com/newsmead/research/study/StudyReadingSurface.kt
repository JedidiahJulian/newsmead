package com.newsmead.research.study

import org.json.JSONObject

/** Explicit geometric compatibility for the shared article surface only.
 * Actual colors, aid preferences and visible body clipping remain in full metadata;
 * aid/scroll/control transitions still break exposure through the capture timeline.
 * The reading viewport is the scroll container, not the moving TextView's clip.
 */
object StudyReadingSurface {
    private val keys = listOf("text_size_px", "column_width_px", "density", "font_scale",
        "line_spacing_extra_px", "line_spacing_multiplier", "text_scale_x", "letter_spacing",
        "typeface_style", "language", "reading_viewport_screen_px", "body_padding_px", "font_resource")
    fun specification(settings: JSONObject): JSONObject = JSONObject().put("version", StudyBaselineProtocol.SURFACE).also { result ->
        keys.forEach { key ->
            require(settings.has(key) && !settings.isNull(key)) { "Missing reading-surface field: $key" }
            result.put(key, settings.get(key))
        }
    }
}
