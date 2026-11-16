package com.newsmead

import com.newsmead.data.StudyArticleCatalog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

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
    fun bundledCatalogPreservesSelectedFormativeSnapshots() {
        val asset = sequenceOf(
            File("app/src/main/assets/study_articles.json"),
            File("src/main/assets/study_articles.json")
        ).firstOrNull { it.isFile } ?: error("Could not locate study_articles.json from ${File(".").absolutePath}")

        val articles = StudyArticleCatalog.parse(asset.readText(Charsets.UTF_8))
        assertEquals(2, articles.size)
        assertTrue(articles.all(StudyArticleCatalog::contains))
        assertEquals(
            listOf(
                "${StudyArticleCatalog.ID_PREFIX}formative-2026-11-16-a-la-union-trade-fair",
                "${StudyArticleCatalog.ID_PREFIX}formative-2026-11-16-b-bohol-recycling",
            ),
            articles.map { it.newsId },
        )
        assertEquals(listOf("2026-11-06", "2026-11-13"), articles.map { it.date })
        assertEquals(listOf("5 min read", "3 min read"), articles.map { it.readTime })
        assertTrue(articles.all { it.source == "Philippine Information Agency" && it.language == "English" })
        assertEquals(
            listOf(
                "https://pia.gov.ph/news/from-small-towns-to-big-markets-la-union-trade-fair-turns-silver-2/",
                "https://pia.gov.ph/news/bohol-town-trades-rice-for-plastic-waste-as-lgus-turn-trash-into-building-materials/",
            ),
            articles.map { it.url },
        )
        val imageUris = listOf(
            "file:///android_asset/study_images/la_union_trade_fair.png",
            "file:///android_asset/study_images/bohol_recycling.png",
        )
        val imageHashes = listOf(
            "088c3c20b12e14a782ae8520d709ad9d1458be8302fa1350aaab0db7ba046873",
            "6b42e8ba2885d14de42e009c44fe7b0060f13a23d6bcbef63543a1d38e354de1",
        )
        assertEquals(imageUris, articles.map { it.imageURL })
        assertEquals(imageHashes, imageUris.map { uri ->
            val image = File(asset.parentFile, uri.removePrefix("file:///android_asset/"))
            assertTrue("Missing bundled image asset: $image", image.isFile)
            MessageDigest.getInstance("SHA-256")
                .digest(image.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        })
        // Freeze the selected bodies, including headings and punctuation, for session traceability.
        assertEquals(
            listOf(
                "b4614ac2f573665b2ced821367682cee6c36ab4a2ef1c57eb6325f58d011026e",
                "5a2b9a3130362af853f7e0d68d45ea8640942f7b71046302356305b16ef4fd87",
            ),
            articles.map { article ->
                MessageDigest.getInstance("SHA-256")
                    .digest(article.body.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            },
        )
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
