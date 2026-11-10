package com.newsmead.data

import com.newsmead.models.Article
import org.json.JSONObject

/** Parses the bundled, account-independent study article catalog. */
object StudyArticleCatalog {
    const val ID_PREFIX = "local-study-v1:"

    /**
     * Parses the complete catalog. Invalid or duplicate entries fail the entire parse so callers
     * cannot mistake a partial catalog for a valid one and fall back to live content.
     */
    fun parse(json: String): List<Article> {
        val entries = JSONObject(json).getJSONArray("articles")
        val seenIds = mutableSetOf<String>()
        return List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            val rawId = entry.opt("article_id")
            require(rawId != null && rawId !== JSONObject.NULL) { "Article at index $index has no article_id" }
            require(rawId is String || rawId is Number) { "Article at index $index has an invalid article_id" }
            val articleId = rawId.toString()
            require(articleId.isNotBlank()) { "Article at index $index has a blank article_id" }
            require(seenIds.add(articleId)) { "Duplicate article_id: $articleId" }

            val title = requiredText(entry, "title", index)
            val body = requiredText(entry, "body", index)
            val source = optionalText(entry, "source").ifBlank { "study" }
            val language = optionalText(entry, "language").ifBlank { "English" }

            Article(
                source = if (source == "study") "Sample article" else source,
                sourceImage = "sample_source_image",
                title = title,
                imageURL = optionalText(entry, "image_url"),
                date = optionalText(entry, "date"),
                body = body,
                category = optionalText(entry, "category"),
                language = language,
                readTime = optionalText(entry, "read_time"),
                url = optionalText(entry, "url"),
                newsId = ID_PREFIX + articleId
            )
        }
    }

    fun contains(article: Article): Boolean = article.newsId.startsWith(ID_PREFIX)

    private fun requiredText(entry: JSONObject, key: String, index: Int): String {
        val value = entry.opt(key)
        require(value is String && value.isNotBlank()) {
            "Article at index $index has no nonblank $key"
        }
        return value
    }

    private fun optionalText(entry: JSONObject, key: String): String {
        val value = entry.opt(key)
        return if (value == null || value === JSONObject.NULL) "" else value.toString()
    }
}
