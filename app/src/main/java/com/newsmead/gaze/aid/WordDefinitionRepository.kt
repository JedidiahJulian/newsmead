package com.newsmead.gaze.aid

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale

data class WordDefinition(val word: String, val meanings: String, val attribution: String)
fun interface DefinitionRequest { fun cancel() }
interface WordDefinitionRepository {
    /** Main-thread callback. Null means unavailable; it never supplies an invented definition. */
    fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest
}

/** Replaceable English dictionary adapter. Sends one word, never article text or gaze. */
class DictionaryDefinitionRepository(
    context: Context,
    private val allowNetwork: Boolean = true,
) : WordDefinitionRepository {
    private val applicationContext = context.applicationContext
    // A bundled entry must not create a network queue or wait for a timeout.
    private val queue by lazy {
        synchronized(DictionaryDefinitionRepository::class.java) {
            sharedQueue ?: Volley.newRequestQueue(applicationContext).also { sharedQueue = it }
        }
    }
    private val cache = object : LinkedHashMap<String, WordDefinition>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WordDefinition>?) = size > 128
    }

    override fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest {
        val key = normalize(word)
        if (key == null) { callback(null); return DefinitionRequest {} }
        cache[key]?.let { callback(it); return DefinitionRequest {} }
        OfflineWordDefinitions.lookup(key)?.let { callback(it); return DefinitionRequest {} }
        if (!allowNetwork) { callback(null); return DefinitionRequest {} }
        val request = StringRequest(Request.Method.GET,
            "https://api.dictionaryapi.dev/api/v2/entries/en/${URLEncoder.encode(key, "UTF-8")}",
            { response ->
                val entry = runCatching { parse(response) }.getOrNull()
                if (entry != null) cache[key] = entry
                callback(entry)
            }, { callback(null) })
        request.retryPolicy = DefaultRetryPolicy(2500, 0, 1f)
        queue.add(request)
        return DefinitionRequest { request.cancel() }
    }

    companion object {
        private var sharedQueue: com.android.volley.RequestQueue? = null
        internal fun normalize(word: String): String? = word.trim().lowercase(Locale.ENGLISH)
            .replace('\u2019', '\'').takeIf { it.length in 1..60 && it.matches(Regex("[a-z]+(?:['-][a-z]+)*")) }

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
