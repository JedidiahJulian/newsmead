package com.newsmead.gaze.aid

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class WordDefinition(val word: String, val meanings: String, val attribution: String)
enum class DefinitionFailure { INVALID_TOKEN, ENTRY_NOT_FOUND, SERVICE_UNAVAILABLE, OFFLINE_UNAVAILABLE, UNKNOWN }
data class DefinitionLookupResult(val definition: WordDefinition?, val failure: DefinitionFailure? = null)
fun interface DefinitionRequest { fun cancel() }
interface WordDefinitionRepository {
    /** Main-thread callback. Null means unavailable; it never supplies an invented definition. */
    fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest
    /** Legacy adapters may only report unknown absence. Uses the same callback/cancellation contract. */
    fun lookupResult(word: String, callback: (DefinitionLookupResult) -> Unit): DefinitionRequest =
        lookup(word) { callback(DefinitionLookupResult(it, if (it == null) DefinitionFailure.UNKNOWN else null)) }
}

internal data class DictionaryResponse(val body: String? = null,
    val failure: DefinitionFailure = DefinitionFailure.SERVICE_UNAVAILABLE)

/** Delivers a response or classified failure on the main thread, like Volley. */
internal fun interface DictionaryTransport {
    fun lookup(key: String, callback: (DictionaryResponse) -> Unit): DefinitionRequest
}

/** Replaceable English dictionary adapter. Sends one word, never article text or gaze. */
class DictionaryDefinitionRepository internal constructor(
    private val allowNetwork: Boolean,
    private val transport: DictionaryTransport,
    private val secondaryTransport: DictionaryTransport = DictionaryTransport { _, callback ->
        callback(DictionaryResponse(failure = DefinitionFailure.ENTRY_NOT_FOUND))
        DefinitionRequest {}
    },
) : WordDefinitionRepository {
    constructor(context: Context, allowNetwork: Boolean = true, allowHttpCache: Boolean = true) :
        this(allowNetwork, volleyTransport(context.applicationContext, allowHttpCache),
            volleyTransport(context.applicationContext, allowHttpCache, secondary = true))

    private val cache = object : LinkedHashMap<String, WordDefinition>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WordDefinition>?) = size > 128
    }

    override fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest {
        return lookupResult(word) { callback(it.definition) }
    }

    override fun lookupResult(word: String, callback: (DefinitionLookupResult) -> Unit): DefinitionRequest {
        val key = normalize(word)
        if (key == null) {
            callback(DefinitionLookupResult(null, DefinitionFailure.INVALID_TOKEN)); return DefinitionRequest {}
        }
        if (!allowNetwork) {
            val entry = OfflineWordDefinitions.lookup(key)
            callback(DefinitionLookupResult(entry, if (entry == null) DefinitionFailure.OFFLINE_UNAVAILABLE else null))
            return DefinitionRequest {}
        }
        cache[key]?.let { callback(DefinitionLookupResult(it)); return DefinitionRequest {} }
        val lock = Any()
        // A stage consumes exactly one callback, including synchronous transports.
        var stage = 0
        val requests = mutableListOf<DefinitionRequest>()
        fun finish(entry: WordDefinition?, failure: DefinitionFailure) {
            stage = 2
            // Only live successes enter memory; a fallback must allow a later retry.
            if (entry != null) cache[key] = entry
            val definition = entry ?: OfflineWordDefinitions.lookup(key)
            callback(DefinitionLookupResult(definition, if (definition == null) failure else null))
        }
        fun start(source: DictionaryTransport, expectedStage: Int, complete: (DictionaryResponse) -> Unit) {
            val request = try {
                source.lookup(key, complete)
            } catch (failure: RuntimeException) {
                // A synchronous delivery may throw from the consumer; do not swallow it.
                if (stage != expectedStage) throw failure
                complete(DictionaryResponse())
                DefinitionRequest {}
            }
            requests.add(request)
            if (stage == 2) request.cancel()
        }
        val primaryComplete: (DictionaryResponse) -> Unit = { response ->
            synchronized(lock) {
                if (stage == 0) {
                    val entry = response.body?.let { runCatching { parse(it) }.getOrNull() }
                    val failure = if (response.body != null) DefinitionFailure.SERVICE_UNAVAILABLE else response.failure
                    if (entry != null) finish(entry, failure) else {
                        stage = 1
                        start(secondaryTransport, 1) { secondaryResponse ->
                            synchronized(lock) {
                                if (stage == 1) {
                                    val parsed = secondaryResponse.body?.let {
                                        runCatching { parseDatamuse(it, key) }
                                    }
                                    val secondaryEntry = parsed?.getOrNull()
                                    val finalFailure = if (parsed?.isFailure == true)
                                        DefinitionFailure.SERVICE_UNAVAILABLE
                                    else if (secondaryResponse.body == null &&
                                        secondaryResponse.failure != DefinitionFailure.ENTRY_NOT_FOUND)
                                        secondaryResponse.failure else failure
                                    finish(secondaryEntry, finalFailure)
                                }
                            }
                        }
                    }
                }
            }
        }
        synchronized(lock) { start(transport, 0, primaryComplete) }
        return DefinitionRequest {
            synchronized(lock) {
                stage = 2
                requests.forEach { it.cancel() }
            }
        }
    }

    companion object {
        const val ONLINE_ROUTE_VERSION = "FREE_THEN_DATAMUSE_EXACT_THEN_GLOSSARY_V2"
        private var sharedQueue: com.android.volley.RequestQueue? = null
        private fun volleyTransport(applicationContext: Context, allowHttpCache: Boolean,
            secondary: Boolean = false): DictionaryTransport {
            // Offline-only lookups never initialize the lazy queue.
            val queue by lazy {
                synchronized(DictionaryDefinitionRepository::class.java) {
                    sharedQueue ?: Volley.newRequestQueue(applicationContext).also { sharedQueue = it }
                }
            }
            return DictionaryTransport { key, callback ->
                val encodedKey = URLEncoder.encode(key, "UTF-8")
                val url = if (secondary) "https://api.datamuse.com/words?sp=$encodedKey&md=d&max=5"
                    else "https://api.dictionaryapi.dev/api/v2/entries/en/$encodedKey"
                val request = StringRequest(Request.Method.GET, url,
                    { callback(DictionaryResponse(body = it)) }, { error ->
                        callback(DictionaryResponse(failure = failureForHttpResponse(error.networkResponse?.statusCode,
                            error.networkResponse?.data?.toString(Charsets.UTF_8))))
                    })
                request.retryPolicy = DefaultRetryPolicy(2500, 0, 1f)
                // Explicit connection checks must reach the service, rather than
                // succeeding from Volley's HTTP cache. Normal reading keeps its cache.
                request.setShouldCache(allowHttpCache)
                queue.add(request)
                DefinitionRequest { request.cancel() }
            }
        }

        // Volley supplies no HTTP status for timeout, DNS, TLS and connection failures.
        internal fun failureForHttpResponse(statusCode: Int?, body: String?): DefinitionFailure {
            val missingEntry = statusCode == 404 && body != null && runCatching {
                JSONObject(body).optString("title") == "No Definitions Found"
            }.getOrDefault(false)
            return if (missingEntry) DefinitionFailure.ENTRY_NOT_FOUND else DefinitionFailure.SERVICE_UNAVAILABLE
        }

        internal fun normalize(word: String): String? = word.trim().lowercase(Locale.ENGLISH)
            .replace('\u2019', '\'').takeIf { it.length in 1..60 && it.matches(Regex("[a-z]+(?:['-][a-z]+)*")) }

        internal fun parseDatamuse(response: String, key: String): WordDefinition? {
            val entries = JSONArray(response)
            val parts = mapOf("n" to "noun", "v" to "verb", "adj" to "adjective", "adv" to "adverb")
            for (i in 0 until minOf(entries.length(), 5)) {
                val entry = entries.optJSONObject(i) ?: continue
                // sp returns similar spellings. Never display a fuzzy or inflected substitute.
                val word = entry.opt("word") as? String ?: continue
                if (word != key) continue
                val definitions = entry.optJSONArray("defs") ?: continue
                val senses = mutableListOf<String>()
                for (j in 0 until definitions.length()) {
                    val raw = (definitions.opt(j) as? String)?.takeIf { it.isNotBlank() } ?: continue
                    val separator = raw.indexOf('\t')
                    val part = if (separator >= 0) parts[raw.substring(0, separator)] else null
                    val text = if (part != null) raw.substring(separator + 1).trim() else raw.trim()
                    if (text.isBlank()) continue
                    senses.add(if (part == null) text else "$part: $text")
                    if (senses.size == 2) break
                }
                if (senses.isNotEmpty()) return WordDefinition(word, senses.joinToString("\n\n").take(1000),
                    "Datamuse · Wiktionary/WordNet · https://www.datamuse.com/api/")
            }
            return null
        }

        internal fun parse(response: String): WordDefinition? {
            val entry = JSONArray(response).optJSONObject(0) ?: return null
            val word = entry.optString("word").takeIf { it.isNotBlank() } ?: return null
            val meanings = entry.optJSONArray("meanings") ?: return null
            val senses = mutableListOf<String>()
            for (i in 0 until meanings.length()) {
                val meaning = meanings.optJSONObject(i) ?: continue
                val definitions = meaning.optJSONArray("definitions") ?: continue
                val part = meaning.optString("partOfSpeech")
                for (j in 0 until definitions.length()) {
                    val text = definitions.optJSONObject(j)?.optString("definition")?.takeIf { it.isNotBlank() } ?: continue
                    senses.add(if (part.isBlank()) text else "$part: $text")
                    if (senses.size == 2) break
                }
                if (senses.size == 2) break
            }
            if (senses.isEmpty()) return null
            val license = entry.optJSONObject("license")?.optString("name")?.takeIf { it.isNotBlank() }
            val source = entry.optJSONArray("sourceUrls")?.optString(0)?.takeIf { it.startsWith("https://") }
            val attribution = listOfNotNull("Free Dictionary API", license, source).joinToString(" · ")
            return WordDefinition(word, senses.joinToString("\n\n").take(1000), attribution)
        }
    }
}
