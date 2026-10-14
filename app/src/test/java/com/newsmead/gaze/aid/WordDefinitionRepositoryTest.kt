package com.newsmead.gaze.aid

import org.junit.Assert.*
import org.junit.Test

class WordDefinitionRepositoryTest {
    private class ControlledTransport : DictionaryTransport {
        data class Pending(val key: String, val callback: (DictionaryResponse) -> Unit, var cancelled: Boolean = false)
        val requests = mutableListOf<Pending>()
        override fun lookup(key: String, callback: (DictionaryResponse) -> Unit): DefinitionRequest {
            val pending = Pending(key, callback)
            requests.add(pending)
            return DefinitionRequest { pending.cancelled = true }
        }
    }

    private fun onlineResponse(word: String = "keyboard") = """
        [{"word":"$word","meanings":[{"partOfSpeech":"noun","definitions":[
          {"definition":"Live first meaning."},{"definition":"Live second meaning."},
          {"definition":"This third meaning must not display."}]}],
          "license":{"name":"CC BY-SA 3.0"},"sourceUrls":["https://example.org/dictionary"]}]
    """.trimIndent()

    private fun secondaryResponse(word: String = "analyst") =
        """[{"word":"$word","score":100,"defs":["n\tA person who analyzes.","n\tA second sense.","n\tOmitted third sense."]}]"""

    @Test fun primarySuccessSkipsSecondaryAndSecondaryExactSuccessIsCached() {
        val primary = ControlledTransport()
        val secondary = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, primary, secondary)
        val results = mutableListOf<WordDefinition?>()
        repository.lookup("keyboard", results::add)
        primary.requests.single().callback(DictionaryResponse(body = onlineResponse()))
        assertTrue(secondary.requests.isEmpty())
        repository.lookup(" Analyst ", results::add)
        primary.requests.last().callback(DictionaryResponse(failure = DefinitionFailure.ENTRY_NOT_FOUND))
        assertEquals("analyst", secondary.requests.single().key)
        assertEquals(1, results.size)
        secondary.requests.single().callback(DictionaryResponse(body = secondaryResponse()))
        val definition = requireNotNull(results.last())
        assertEquals("analyst", definition.word)
        assertEquals("noun: A person who analyzes.\n\nnoun: A second sense.", definition.meanings)
        assertEquals("Datamuse · Wiktionary/WordNet · https://www.datamuse.com/api/", definition.attribution)
        repository.lookup("ANALYST", results::add)
        assertEquals(2, primary.requests.size)
        assertEquals(1, secondary.requests.size)
        assertEquals(definition, results.last())
    }

    @Test fun everyPrimaryAbsenceStartsOneSecondaryAndFailedChainRemainsRetryable() {
        val primary = ControlledTransport()
        val secondary = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, primary, secondary)
        val results = mutableListOf<WordDefinition?>()
        val failures = listOf(DictionaryResponse(failure = DefinitionFailure.ENTRY_NOT_FOUND),
            DictionaryResponse(), DictionaryResponse(body = "not json"), DictionaryResponse(body = "[]"))
        for (response in failures) {
            repository.lookup("analyst", results::add)
            val pending = primary.requests.last()
            pending.callback(response)
            pending.callback(DictionaryResponse(body = onlineResponse("analyst")))
            assertEquals(primary.requests.size, secondary.requests.size)
            val fallback = secondary.requests.last()
            fallback.callback(DictionaryResponse(body = secondaryResponse("analysts")))
            fallback.callback(DictionaryResponse(body = secondaryResponse()))
            assertEquals(OfflineWordDefinitions.lookup("analyst"), results.last())
        }
        assertEquals(4, primary.requests.size)
        assertEquals(4, results.size)
        repository.lookup("analyst", results::add)
        assertEquals(5, primary.requests.size)
    }

    @Test fun secondaryCancellationSuppressesLateDeliveryCacheAndPrimaryDuplicates() {
        val primary = ControlledTransport()
        val secondary = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, primary, secondary)
        val results = mutableListOf<WordDefinition?>()
        val request = repository.lookup("analyst", results::add)
        primary.requests.single().callback(DictionaryResponse())
        request.cancel()
        assertTrue(primary.requests.single().cancelled)
        assertTrue(secondary.requests.single().cancelled)
        secondary.requests.single().callback(DictionaryResponse(body = secondaryResponse()))
        primary.requests.single().callback(DictionaryResponse(body = onlineResponse("analyst")))
        assertTrue(results.isEmpty())
        repository.lookup("analyst", results::add)
        assertEquals(2, primary.requests.size)
    }

    @Test fun datamuseParserRejectsSimilarWordsMissingDefinitionsAndNonStringFields() {
        for (body in listOf("[]", secondaryResponse("analysts"), secondaryResponse("Analyst"),
            """[{"word":"analyst","defs":[]}]""", """[{"word":"analyst","defs":[null,17,"n\t "]}]""",
            """[{"word":17,"defs":["n\tMeaning"]}]""")) {
            assertNull(DictionaryDefinitionRepository.parseDatamuse(body, "analyst"))
        }
        val matches = """[{"word":"analysis","defs":["n\tWrong word"]},{"word":"analyst","defs":["v\tAnalyze.","adj\tAnalytical."]}]"""
        assertEquals("verb: Analyze.\n\nadjective: Analytical.",
            DictionaryDefinitionRepository.parseDatamuse(matches, "analyst")!!.meanings)
        val raw = """[{"word":"analyst","defs":["Unmarked plain definition.","unknown\tUnmapped definition."]}]"""
        assertEquals("Unmarked plain definition.\n\nunknown\tUnmapped definition.",
            DictionaryDefinitionRepository.parseDatamuse(raw, "analyst")!!.meanings)
        val long = """[{"word":"analyst","defs":["n\t${"x".repeat(1500)}"]}]"""
        assertEquals(1000, DictionaryDefinitionRepository.parseDatamuse(long, "analyst")!!.meanings.length)
    }

    @Test fun secondaryServiceMalformedAndSetupFailuresKeepDetailedAbsence() {
        val primary = ControlledTransport()
        val secondary = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, primary, secondary)
        val results = mutableListOf<DefinitionLookupResult>()
        for (response in listOf(DictionaryResponse(), DictionaryResponse(body = "not json"),
            DictionaryResponse(body = "[]"))) {
            repository.lookupResult("qzxunknown", results::add)
            primary.requests.last().callback(DictionaryResponse(failure = DefinitionFailure.ENTRY_NOT_FOUND))
            secondary.requests.last().callback(response)
        }
        assertEquals(listOf(DefinitionFailure.SERVICE_UNAVAILABLE, DefinitionFailure.SERVICE_UNAVAILABLE,
            DefinitionFailure.ENTRY_NOT_FOUND), results.map { it.failure })
        val failing = DictionaryDefinitionRepository(true, primary, DictionaryTransport { _, _ ->
            throw IllegalStateException("Secondary unavailable")
        })
        failing.lookupResult("qzxunknown", results::add)
        primary.requests.last().callback(DictionaryResponse(failure = DefinitionFailure.ENTRY_NOT_FOUND))
        assertEquals(DefinitionFailure.SERVICE_UNAVAILABLE, results.last().failure)
    }

    @Test fun synchronousSecondarySuccessPreservesConsumerException() {
        val failure = IllegalStateException("Consumer failure")
        val repository = DictionaryDefinitionRepository(true,
            DictionaryTransport { _, callback -> callback(DictionaryResponse()); DefinitionRequest {} },
            DictionaryTransport { _, callback ->
                callback(DictionaryResponse(body = secondaryResponse())); DefinitionRequest {}
            })
        try {
            repository.lookup("analyst") { throw failure }
            fail("Consumer failure should propagate")
        } catch (actual: IllegalStateException) { assertSame(failure, actual) }
    }

    @Test fun bundledWordWaitsForLiveSuccessWithSourceAndBoundedMeanings() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<WordDefinition?>()
        repository.lookup(" Keyboard ", results::add)
        assertTrue(results.isEmpty())
        assertEquals("keyboard", transport.requests.single().key)
        transport.requests.single().callback(DictionaryResponse(body = onlineResponse()))
        val result = results.single()!!
        assertEquals("keyboard", result.word)
        assertEquals("noun: Live first meaning.\n\nnoun: Live second meaning.", result.meanings)
        assertEquals("Free Dictionary API · CC BY-SA 3.0 · https://example.org/dictionary", result.attribution)
    }

    @Test fun failureFallsBackWithoutPoisoningCacheThenLiveSuccessIsCached() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<WordDefinition?>()
        repository.lookup("keyboard", results::add)
        transport.requests[0].callback(DictionaryResponse())
        assertEquals(OfflineWordDefinitions.lookup("keyboard"), results.single())
        repository.lookup("KEYBOARD", results::add)
        assertEquals(2, transport.requests.size)
        transport.requests[1].callback(DictionaryResponse(body = onlineResponse()))
        repository.lookup(" keyboard ", results::add)
        assertEquals(2, transport.requests.size)
        assertEquals(3, results.size)
        assertEquals(results[1], results[2])
        assertTrue(results[2]!!.attribution.startsWith("Free Dictionary API"))
    }

    @Test fun analystUsesLiveFirstThenLabeledGlossaryWithoutBlockingLaterLiveRetry() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<DefinitionLookupResult>()
        repository.lookupResult("Analyst", results::add)
        assertTrue(results.isEmpty())
        assertEquals("analyst", transport.requests.single().key)
        transport.requests.single().callback(DictionaryResponse(failure = DefinitionFailure.SERVICE_UNAVAILABLE))
        val fallback = results.single()
        val glossary = requireNotNull(fallback.definition)
        assertEquals("analyst", glossary.word)
        assertTrue(glossary.meanings.isNotBlank())
        assertEquals("Offline · NewsMead English glossary", glossary.attribution)
        assertNull(fallback.failure)
        repository.lookupResult("analyst", results::add)
        assertEquals(2, transport.requests.size)
        transport.requests.last().callback(DictionaryResponse(body = onlineResponse("analyst")))
        assertTrue(results.last().definition!!.attribution.startsWith("Free Dictionary API"))
    }

    @Test fun malformedAndUnavailableResponsesFallBackAndRemainRetryable() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<WordDefinition?>()
        for (response in listOf("not json", "[]", "[{\"word\":\"keyboard\",\"meanings\":[]}]")) {
            repository.lookup("keyboard", results::add)
            transport.requests.last().callback(DictionaryResponse(body = response))
            assertEquals(OfflineWordDefinitions.lookup("keyboard"), results.last())
        }
        assertEquals(3, transport.requests.size)
        assertEquals(3, results.size)
    }

    @Test fun uncoveredWordFailureAndEmptyResponseReturnUnavailable() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<WordDefinition?>()
        repository.lookup("qzxunknown", results::add)
        transport.requests.last().callback(DictionaryResponse())
        repository.lookup("qzxunknown", results::add)
        transport.requests.last().callback(DictionaryResponse(body = "[]"))
        assertEquals(listOf<WordDefinition?>(null, null), results)
        assertEquals(2, transport.requests.size)
    }

    @Test fun offlineOnlyIsImmediateBundledOnlyAndNeverStartsTransport() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(false, transport)
        var result: WordDefinition? = null
        repository.lookup("Keyboard") { result = it }
        assertEquals(OfflineWordDefinitions.lookup("keyboard"), result)
        var missingCalled = false
        repository.lookup("qzxunknown") { missingCalled = true; assertNull(it) }
        assertTrue(missingCalled)
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun cancellationSuppressesLateSuccessFailureAndCachePopulation() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<WordDefinition?>()
        val request = repository.lookup("keyboard", results::add)
        request.cancel()
        assertTrue(transport.requests.single().cancelled)
        transport.requests.single().callback(DictionaryResponse(body = onlineResponse()))
        transport.requests.single().callback(DictionaryResponse())
        assertTrue(results.isEmpty())
        repository.lookup("keyboard", results::add)
        assertEquals(2, transport.requests.size)
        assertTrue(results.isEmpty())
    }

    @Test fun duplicateTransportCallbacksCompleteOnceAndCannotOverwriteSuccess() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<WordDefinition?>()
        repository.lookup("keyboard", results::add)
        transport.requests.single().callback(DictionaryResponse(body = onlineResponse()))
        transport.requests.single().callback(DictionaryResponse())
        transport.requests.single().callback(DictionaryResponse(body = "[]"))
        assertEquals(1, results.size)
        repository.lookup("keyboard", results::add)
        assertEquals(1, transport.requests.size)
        assertEquals(results[0], results[1])
    }

    @Test fun duplicateSuccessAfterFallbackCannotPopulateCache() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<WordDefinition?>()
        repository.lookup("keyboard", results::add)
        transport.requests.single().callback(DictionaryResponse())
        transport.requests.single().callback(DictionaryResponse(body = onlineResponse()))
        assertEquals(1, results.size)
        repository.lookup("keyboard", results::add)
        assertEquals(2, transport.requests.size)
    }

    @Test fun invalidOrTooLongInputCompletesImmediatelyWithoutTransport() {
        val transport = ControlledTransport()
        val repository = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<WordDefinition?>()
        for (word in listOf("", "two words", "keyboard?private=value", "a".repeat(61))) {
            repository.lookup(word, results::add)
        }
        assertEquals(4, results.size)
        assertTrue(results.all { it == null })
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun transportSetupFailureReturnsBundledFallback() {
        val repository = DictionaryDefinitionRepository(true, DictionaryTransport { _, _ ->
            throw IllegalStateException("Transport unavailable")
        })
        val results = mutableListOf<WordDefinition?>()
        repository.lookup("keyboard", results::add)
        assertEquals(listOf(OfflineWordDefinitions.lookup("keyboard")), results)
    }

    @Test fun synchronousDeliveryDoesNotSwallowConsumerFailure() {
        val repository = DictionaryDefinitionRepository(true, DictionaryTransport { _, callback ->
            callback(DictionaryResponse(body = onlineResponse()))
            DefinitionRequest {}
        })
        val failure = IllegalStateException("Consumer failure")
        try {
            repository.lookup("keyboard") { throw failure }
            fail("Consumer failure should propagate")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
    }

    @Test fun detailedAbsenceDistinguishesInvalidOfflineMissingEntryAndServiceFailure() {
        val transport = ControlledTransport()
        val live = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<DefinitionLookupResult>()
        live.lookupResult("two words", results::add)
        assertEquals(DefinitionFailure.INVALID_TOKEN, results.last().failure)
        assertTrue(transport.requests.isEmpty())
        DictionaryDefinitionRepository(false, transport).lookupResult("qzxunknown", results::add)
        assertEquals(DefinitionFailure.OFFLINE_UNAVAILABLE, results.last().failure)
        assertTrue(transport.requests.isEmpty())
        for (failure in listOf(DefinitionFailure.ENTRY_NOT_FOUND, DefinitionFailure.SERVICE_UNAVAILABLE)) {
            live.lookupResult("qzxunknown", results::add)
            transport.requests.last().callback(DictionaryResponse(failure = failure))
            assertEquals(failure, results.last().failure)
            assertNull(results.last().definition)
        }
        for (body in listOf("not json", "[]", "[{\"word\":\"qzxunknown\",\"meanings\":[]}]")) {
            live.lookupResult("qzxunknown", results::add)
            transport.requests.last().callback(DictionaryResponse(body = body))
            assertEquals(DefinitionFailure.SERVICE_UNAVAILABLE, results.last().failure)
        }
        assertEquals(5, transport.requests.size) // Every absence remains retryable.
    }

    @Test fun detailedFallbackAndCacheCarryDefinitionsWithoutUnavailableReason() {
        val transport = ControlledTransport()
        val live = DictionaryDefinitionRepository(true, transport)
        val results = mutableListOf<DefinitionLookupResult>()
        live.lookupResult("keyboard", results::add)
        transport.requests.last().callback(DictionaryResponse(failure = DefinitionFailure.ENTRY_NOT_FOUND))
        assertEquals(OfflineWordDefinitions.lookup("keyboard"), results.last().definition)
        assertNull(results.last().failure)
        live.lookupResult("keyboard", results::add)
        transport.requests.last().callback(DictionaryResponse(body = onlineResponse()))
        live.lookupResult("keyboard", results::add)
        assertEquals(2, transport.requests.size)
        assertEquals(results[1], results[2])
        assertNull(results.last().failure)
    }

    @Test fun legacyAdapterKeepsNullableContractAndUnknownAbsence() {
        val request = DefinitionRequest {}
        val legacy = object : WordDefinitionRepository {
            override fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest {
                callback(null)
                return request
            }
        }
        var result: DefinitionLookupResult? = null
        assertSame(request, legacy.lookupResult("word") { result = it })
        assertEquals(DefinitionFailure.UNKNOWN, result!!.failure)
        assertNull(result!!.definition)
    }
    @Test fun onlyDictionaryMissingEntryPayloadAtHttp404MeansMissingEntry() {
        val missing = "{\"title\":\"No Definitions Found\",\"message\":\"No entry\"}"
        assertEquals(DefinitionFailure.ENTRY_NOT_FOUND,
            DictionaryDefinitionRepository.failureForHttpResponse(404, missing))
        for (status in listOf(null, 400, 401, 403, 408, 429, 500, 503)) {
            assertEquals(DefinitionFailure.SERVICE_UNAVAILABLE,
                DictionaryDefinitionRepository.failureForHttpResponse(status, missing))
        }
        for (body in listOf(null, "", "<html>Not found</html>", "[]", "{}", "{\"title\":\"Not Found\"}")) {
            assertEquals(DefinitionFailure.SERVICE_UNAVAILABLE,
                DictionaryDefinitionRepository.failureForHttpResponse(404, body))
        }
    }
}
