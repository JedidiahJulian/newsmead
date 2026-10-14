package com.newsmead.gaze.aid

import org.json.JSONObject

/** Guide ranges are text units; word-assignment offsets retain their separate meaning. */
fun ReadingAidAction.guideRangeJson(): Any = guideRange?.let {
    JSONObject().put("unit", it.unit.name).put("start_utf16", it.startUtf16)
        .put("end_utf16", it.endUtf16)
} ?: JSONObject.NULL
