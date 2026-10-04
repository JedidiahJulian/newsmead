package com.newsmead.gaze.aid

import org.junit.Assert.*
import org.junit.Test

class DictionaryLookupKeyTest {
    @Test fun lookupContainsOnlyOneBoundedEnglishWord() {
        assertEquals("don't", DictionaryDefinitionRepository.normalize("Don’t"))
        assertEquals("keyboard", DictionaryDefinitionRepository.normalize(" keyboard "))
        assertNull(DictionaryDefinitionRepository.normalize("two words"))
        assertNull(DictionaryDefinitionRepository.normalize("word?secret=value"))
        assertNull(DictionaryDefinitionRepository.normalize("😀"))
        assertNull(DictionaryDefinitionRepository.normalize("a".repeat(61)))
    }
}
