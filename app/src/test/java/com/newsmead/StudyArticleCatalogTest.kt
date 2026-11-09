package com.newsmead

import com.newsmead.data.StudyArticleCatalog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StudyArticleCatalogTest {
    @Test
    fun parsesAndPreservesArticleFieldsAndFullBody() {
        val body = "  First paragraph.\n\nSecond paragraph.  "
        val article = StudyArticleCatalog.parse(catalog(article(body = body))).single()

        assertEquals("${StudyArticleCatalog.ID_PREFIX}sample", article.newsId)
        assertEquals("Sample article", article.source)
        assertEquals("sample_source_image", article.sourceImage)
        assertEquals("A title", article.title)
        assertEquals("https://example.test/image.jpg", article.imageURL)
        assertEquals("2026-06-20", article.date)
        assertEquals(body, article.body)
        assertEquals("news", article.category)
        assertEquals("Filipino", article.language)
        assertEquals("4 min read", article.readTime)
        assertEquals("https://example.test/story", article.url)
        assertTrue(StudyArticleCatalog.contains(article))
        assertFalse(StudyArticleCatalog.contains(com.newsmead.models.Article("feed", "A title", "", "", "")))
    }

    @Test
    fun idsAreStableWhenEntriesReorderAndSourceCanBeRealText() {
        val first = article(id = "one", source = "study")
        val second = article(id = "two", source = "Example Gazette")
        val forward = StudyArticleCatalog.parse(catalog(first, second))
        val reversed = StudyArticleCatalog.parse(catalog(second, first))

        assertEquals("${StudyArticleCatalog.ID_PREFIX}one", forward[0].newsId)
        assertEquals("${StudyArticleCatalog.ID_PREFIX}two", forward[1].newsId)
        assertEquals(forward[0].newsId, reversed[1].newsId)
        assertEquals(forward[1].newsId, reversed[0].newsId)
        assertEquals("Example Gazette", forward[1].source)
    }

    @Test
    fun rejectsDuplicateOrMissingIdsAndBlankRequiredText() {
        assertRejected(catalog(article(), article()))
        assertRejected(catalog("""{"title":"A title","body":"Body"}"""))
        assertRejected(catalog(article(title = " ")))
        assertRejected(catalog(article(body = "")))
    }

    @Test
    fun bundledCatalogHasTwoLoadableSamples() {
        val asset = sequenceOf(
            File("app/src/main/assets/study_articles.json"),
            File("src/main/assets/study_articles.json")
        ).firstOrNull { it.isFile } ?: error("Could not locate study_articles.json from ${File(".").absolutePath}")

        val samples = StudyArticleCatalog.parse(asset.readText())
        assertEquals(2, samples.size)
        assertTrue(samples.all(StudyArticleCatalog::contains))
    }

    private fun assertRejected(json: String) {
        try {
            StudyArticleCatalog.parse(json)
            throw AssertionError("Expected catalog parse to reject invalid entry")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }

    private fun catalog(vararg entries: String): String =
        """{"articles":[${entries.joinToString(",")}] }"""

    private fun article(
        id: String = "sample",
        source: String = "study",
        title: String = "A title",
        body: String = "Body",
    ): String = JSONObject()
        .put("article_id", id)
        .put("source", source)
        .put("title", title)
        .put("image_url", "https://example.test/image.jpg")
        .put("date", "2026-06-20")
        .put("body", body)
        .put("category", "news")
        .put("language", "Filipino")
        .put("read_time", "4 min read")
        .put("url", "https://example.test/story")
        .toString()
}
