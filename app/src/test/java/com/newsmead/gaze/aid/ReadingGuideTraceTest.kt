package com.newsmead.gaze.aid

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReadingGuideTraceTest {
    private fun action() = ReadingAidAction("guide", ReadingAidType.CURRENT_SENTENCE,
        "CURRENT_SENTENCE_CONTEXT_HIGHLIGHT_V1", "test", ReadingAidAnchor(1, "layout", 0),
        .25, null, null, 100.0, 1600.0, emptyList(), listOf("prefix"), 0.0, 100.0,
        100.0, "A1_RELIABLE_SENTENCE_CONTEXT_V1", listOf("V26"), "Experimental", true, "V4")

    @Test fun textUnitOffsetsDoNotManufactureWordAssignment() {
        val action = action().copy(guideRange = ReadingGuideRange(3, 41, ReadingGuideUnit.PARAGRAPH))
        val range = action.guideRangeJson() as JSONObject
        assertEquals("PARAGRAPH", range.getString("unit"))
        assertEquals(3, range.getInt("start_utf16"))
        assertEquals(41, range.getInt("end_utf16"))
        assertNull(action.anchor.wordStartUtf16)
        assertNull(action.anchor.wordEndUtf16)
    }

    @Test fun nonRangeActionsHaveExplicitNullRange() {
        assertSame(JSONObject.NULL, action().copy(aidType = ReadingAidType.CURRENT_LINE).guideRangeJson())
    }
}
