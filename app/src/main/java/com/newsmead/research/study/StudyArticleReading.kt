package com.newsmead.research.study

import org.json.JSONObject
import java.util.UUID

/** One article view visit; tracker/package restarts remain segments of this reading. */
class StudyArticleReading {
    val id: String = UUID.randomUUID().toString()
    private var segment = 0
    private var previousSession: String? = null

    fun nextSegment(articleId: String?): JSONObject = StudyJson.obj(
        "article_reading_id" to id, "article_reading_scope" to "article_view_visit_v1",
        "reading_segment_index" to ++segment, "previous_reading_segment_session_id" to previousSession,
        "article_id" to articleId?.takeIf { it.isNotBlank() && it != "0" },
        "article_id_status" to if (articleId.isNullOrBlank() || articleId == "0") "unavailable" else "available")

    fun opened(sessionId: String) { previousSession = sessionId }
}
