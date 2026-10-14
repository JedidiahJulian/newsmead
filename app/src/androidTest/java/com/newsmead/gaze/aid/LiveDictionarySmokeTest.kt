package com.newsmead.gaze.aid

import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Explicitly opted-in public-word HTTP check. No activity, camera, gaze, or human data. */
@RunWith(AndroidJUnit4::class)
class LiveDictionarySmokeTest {
    @Test fun freshPublicVocabularyLookupsReportTheirActualSources() {
        assumeTrue("Requires an explicitly authorized network check",
            InstrumentationRegistry.getArguments().getString("liveDictionarySmoke") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (word in listOf("hello", "analyst")) {
            val delivered = CountDownLatch(1)
            val result = AtomicReference<DefinitionLookupResult?>()
            val mainThread = AtomicBoolean(false)
            var request: DefinitionRequest? = null
            val started = SystemClock.elapsedRealtime()
            try {
                instrumentation.runOnMainSync {
                    // A new repository and disabled HTTP cache require a real request.
                    request = DictionaryDefinitionRepository(instrumentation.targetContext,
                        allowHttpCache = false).lookupResult(word) {
                        result.set(it)
                        mainThread.set(Looper.myLooper() == Looper.getMainLooper())
                        delivered.countDown()
                    }
                }
                assertTrue("No dictionary callback for $word", delivered.await(6, TimeUnit.SECONDS))
                val outcome = requireNotNull(result.get())
                val entry = outcome.definition
                assertNotNull("No definition for $word: ${outcome.failure}", entry)
                val definition = requireNotNull(entry)
                assertEquals(word, definition.word)
                assertTrue(definition.meanings.isNotBlank())
                assertTrue(mainThread.get())
                val liveSuccess = definition.attribution.startsWith("Free Dictionary API") ||
                    definition.attribution.startsWith("Datamuse · Wiktionary/WordNet")
                assertTrue("A glossary fallback is not a live service success for $word", liveSuccess)
                if (word == "hello") assertTrue("Hello must still use the primary dictionary",
                    definition.attribution.startsWith("Free Dictionary API"))
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("dictionary_check_word", word)
                    putString("dictionary_check_source", definition.attribution.substringBefore(" · "))
                    putLong("dictionary_check_elapsed_ms", SystemClock.elapsedRealtime() - started)
                    putBoolean("dictionary_http_cache_allowed", false)
                    putBoolean("dictionary_live_success", liveSuccess)
                })
            } finally { instrumentation.runOnMainSync { request?.cancel() } }
        }
    }

    @Test fun liveDefinitionPopulatesNativeCardAndCloseWorks() = verifyLivePopupFor("keyboard")

    @Test fun liveFallbackDefinitionPopulatesNativeCardAndCloseWorks() = verifyLivePopupFor("analyst")

    private fun verifyLivePopupFor(word: String) {
        assumeTrue("Requires an explicitly authorized network check",
            InstrumentationRegistry.getArguments().getString("liveDictionarySmoke") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val delivered = CountDownLatch(1)
        val result = AtomicReference<WordDefinition?>()
        val callbackFailure = AtomicReference<Throwable?>()
        val mainThread = AtomicBoolean(false)
        lateinit var popup: ReadingAidDefinitionPopup
        var popupCreated = false
        lateinit var host: FrameLayout
        lateinit var body: TextView
        var elapsedMs = 0L
        val sourceText = "$word source word\nAnother article line\nThird article line"
        try {
            instrumentation.runOnMainSync {
                val context = instrumentation.targetContext
                host = FrameLayout(context)
                body = TextView(context).apply {
                    text = sourceText
                    textSize = 16f
                }
                host.addView(body, FrameLayout.LayoutParams(1000, 1000))
                host.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY))
                host.layout(0, 0, 1000, 1400)
                val live = DictionaryDefinitionRepository(context, allowHttpCache = false)
                val repository = object : WordDefinitionRepository {
                    override fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest {
                        val started = SystemClock.elapsedRealtime()
                        return live.lookup(word) { definition ->
                            result.set(definition)
                            mainThread.set(Looper.myLooper() == Looper.getMainLooper())
                            elapsedMs = SystemClock.elapsedRealtime() - started
                            try { callback(definition) }
                            catch (failure: Throwable) { callbackFailure.set(failure) }
                            finally { delivered.countDown() }
                        }
                    }
                }
                popup = ReadingAidDefinitionPopup(context, host, body, repository)
                popupCreated = true
                val action = ReadingAidAction("live-smoke", ReadingAidType.WORD_DEFINITION,
                    "CONTEXT_WORD_DEFINITION_V1", "EXPLICIT_NETWORK_SMOKE",
                    ReadingAidAnchor(1, "g1", 0, 0, word.length), 1.0, null, null,
                    1.0, Double.MAX_VALUE, emptyList(), emptyList(), 1.0, 1.0, 1.0,
                    "TEST", emptyList(), "public sample; no detector evidence", true, "TEST")
                popup.showAction(action)
                assertTrue("The native definition card must open", popup.isShowing)
            }
            assertTrue("Dictionary callback did not complete within six seconds",
                delivered.await(6, TimeUnit.SECONDS))
            callbackFailure.get()?.let { throw AssertionError("Popup update failed", it) }
            val definition = result.get()
            assertNotNull("The live dictionary returned no definition", definition)
            assertTrue("Offline fallback is not a successful live-service check",
                definition!!.attribution.startsWith("Free Dictionary API") ||
                    definition.attribution.startsWith("Datamuse · Wiktionary/WordNet"))
            assertEquals(word, definition.word)
            assertTrue(definition.meanings.isNotBlank())
            assertTrue("Volley must deliver the popup update on the main thread", mainThread.get())
            instrumentation.runOnMainSync {
                assertTrue(popup.isShowing)
                assertEquals(sourceText, body.text.toString())
                assertEquals(word, host.findViewById<TextView>(R.id.definitionWord).text.toString())
                assertEquals(definition.meanings,
                    host.findViewById<TextView>(R.id.definitionMeaning).text.toString())
                val source = host.findViewById<TextView>(R.id.definitionSource)
                assertEquals(View.VISIBLE, source.visibility)
                assertTrue(source.text.toString().contains(definition.attribution))
                host.findViewById<View>(R.id.definitionClose).performClick()
                assertFalse(popup.isShowing)
                assertNull(host.findViewById<View>(R.id.definitionMeaning))
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("dictionary_live_word", definition.word)
                putString("dictionary_live_source", definition.attribution.substringBefore(" · "))
                putLong("dictionary_live_elapsed_ms", elapsedMs)
                putBoolean("dictionary_native_card_updated", true)
                putBoolean("dictionary_close_passed", true)
                putBoolean("dictionary_http_cache_allowed", false)
            })
        } finally {
            instrumentation.runOnMainSync { if (popupCreated) popup.close() }
        }
    }
}
