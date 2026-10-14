package com.newsmead.gaze.aid

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.newsmead.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Detached native fixtures with a fake repository: never network, camera, or human data. */
@RunWith(AndroidJUnit4::class)
class ReadingAidDefinitionPopupTest {
    private class Repository : WordDefinitionRepository {
        var callback: ((WordDefinition?) -> Unit)? = null
        var resultCallback: ((DefinitionLookupResult) -> Unit)? = null
        var requests = 0
        var cancelled = false
        override fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest {
            requests++; this.callback = callback
            return DefinitionRequest { cancelled = true }
        }
        override fun lookupResult(word: String, callback: (DefinitionLookupResult) -> Unit): DefinitionRequest {
            resultCallback = callback
            return lookup(word) { callback(DefinitionLookupResult(it, if (it == null) DefinitionFailure.UNKNOWN else null)) }
        }
    }
    private fun fixture(check: (ReadingAidDefinitionPopup, Repository, TextView, MutableList<Boolean>, ReadingAidAction) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val host = FrameLayout(ctx)
            val body = TextView(ctx).apply { text = "keyboard source word\nAnother article line\nThird article line"; textSize = 16f }
            host.addView(body, FrameLayout.LayoutParams(1000, 1000))
            host.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 1000, 1400)
            val repo = Repository()
            val popup = ReadingAidDefinitionPopup(ctx, host, body, repo)
            val boundaries = mutableListOf<Boolean>()
            popup.onConsultationChanged = { active, _, _ -> boundaries.add(active) }
            val action = ReadingAidAction("test", ReadingAidType.WORD_DEFINITION, "CONTEXT_WORD_DEFINITION_V1", "TEST",
                ReadingAidAnchor(1, "g1", 0, 0, 8), 1.0, null, null, 1.0, Double.MAX_VALUE,
                emptyList(), emptyList(), 1.0, 1.0, 1.0, "TEST", emptyList(), "synthetic fixture", true, "TEST")
            try { check(popup, repo, body, boundaries, action) } finally { popup.close() }
        }
    }

    @Test fun consultationPersistsAndCancelledLateResponseCannotReopen() = fixture { popup, repo, body, boundaries, action ->
        val text = body.text.toString()
        val height = body.height
        popup.showAction(action)
        assertTrue(popup.isShowing)
        assertEquals(listOf(true), boundaries)
        assertEquals(1, repo.requests)
        repo.callback!!(WordDefinition("keyboard", "A test definition.", "test fixture"))
        assertTrue(popup.isShowing)
        popup.showAction(action.copy(id = "same-anchor"))
        assertEquals(1, repo.requests)
        assertEquals(text, body.text.toString()); assertEquals(height, body.height)
        popup.clear("reader_dismissed")
        assertEquals(listOf(true, false), boundaries)
        assertTrue(repo.cancelled)
        repo.callback!!(WordDefinition("keyboard", "Late response.", "test fixture"))
        assertFalse(popup.isShowing)
    }

    @Test fun changedArticleInvalidatesPendingDefinition() = fixture { popup, repo, body, boundaries, action ->
        popup.showAction(action)
        body.text = "Changed article source"
        repo.callback!!(WordDefinition("keyboard", "Old definition.", "test fixture"))
        assertFalse(popup.isShowing)
        assertEquals(listOf(true, false), boundaries)
    }

    @Test fun unsupportedLanguageNeverRequestsEnglishDictionary() = fixture { popup, repo, _, _, action ->
        popup.languageSupported = false
        popup.showAction(action)
        assertTrue(popup.isShowing)
        assertEquals(0, repo.requests)
    }

    @Test fun unavailableFeedbackDistinguishesReasonsAndKeepsConsultationOpen() = fixture { popup, repo, body, boundaries, action ->
        val messages = mapOf(
            DefinitionFailure.INVALID_TOKEN to R.string.definition_token_unavailable,
            DefinitionFailure.ENTRY_NOT_FOUND to R.string.definition_entry_not_found,
            DefinitionFailure.SERVICE_UNAVAILABLE to R.string.definition_service_unavailable,
            DefinitionFailure.OFFLINE_UNAVAILABLE to R.string.definition_offline_unavailable,
            DefinitionFailure.UNKNOWN to R.string.definition_unknown_unavailable,
        )
        val host = body.parent as FrameLayout
        for ((failure, message) in messages) {
            popup.showAction(action)
            repo.resultCallback!!(DefinitionLookupResult(null, failure))
            assertEquals(body.context.getString(message),
                host.findViewById<TextView>(R.id.definitionMeaning).text.toString())
            assertEquals(View.GONE, host.findViewById<TextView>(R.id.definitionSource).visibility)
            assertTrue(popup.isShowing)
            assertTrue(boundaries.last())
            popup.clear("fixture_next_reason")
        }
    }

    @Test fun dictionaryParserRetainsSourceAndHandlesMissingSenses() {
        val entry = DictionaryDefinitionRepository.parse("""[{"word":"test","meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"An examination."}]}],"license":{"name":"CC BY-SA"},"sourceUrls":["https://en.wiktionary.org/wiki/test"]}]""")!!
        assertEquals("noun: An examination.", entry.meanings)
        assertTrue(entry.attribution.contains("CC BY-SA"))
        assertTrue(entry.attribution.contains("https://en.wiktionary.org/wiki/test"))
        assertNull(DictionaryDefinitionRepository.parse("[]"))
    }
}
