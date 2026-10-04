package com.newsmead.gaze.aid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only bundled keys: immediate lookup, without HTTP, camera, or human data. */
@RunWith(AndroidJUnit4::class)
class OfflineDefinitionRepositoryTest {
    @Test fun bundledOnlyModeReturnsUnavailableImmediatelyForAnUncoveredWord() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val repository = DictionaryDefinitionRepository(
                InstrumentationRegistry.getInstrumentation().targetContext, allowNetwork = false,
            )
            var called = false
            repository.lookup("qzxunknown") { called = true; assertNull(it) }
            assertTrue("Offline-only checks must not wait for an HTTP timeout", called)
        }
    }

    @Test fun bundledLookupReturnsBeforeLookupCallCompletes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val repository = DictionaryDefinitionRepository(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
            var result: WordDefinition? = null
            val request = repository.lookup("Keyboard") { result = it }
            assertNotNull(result)
            assertEquals("keyboard", result!!.word)
            assertTrue(result!!.attribution.startsWith("Offline"))
            request.cancel()
        }
    }
}
