package com.newsmead.gaze.aid

import org.junit.Assert.*
import org.junit.Test

class OfflineWordDefinitionsTest {
    @Test fun previewDefinitionIsBundledAndClearlyAttributed() {
        val entry = OfflineWordDefinitions.lookup(" Keyboard ")!!
        assertEquals("keyboard", entry.word)
        assertTrue(entry.meanings.contains("set of keys"))
        assertEquals("Offline · NewsMead English glossary", entry.attribution)
    }

    @Test fun unknownWordsAndNonWordInputDoNotReceiveGenericDefinitions() {
        assertNull(OfflineWordDefinitions.lookup("qzxunknown"))
        assertNull(OfflineWordDefinitions.lookup("two words"))
        assertNull(OfflineWordDefinitions.lookup("keyboard?private=value"))
        assertNull(OfflineWordDefinitions.lookup(""))
    }

    @Test fun glossarySupportsNewsAndCommonWordPreviewsWithBoundedContent() {
        for (word in listOf("article", "government", "community", "research", "the", "of")) {
            val entry = OfflineWordDefinitions.lookup(word)!!
            assertTrue(entry.meanings.isNotBlank())
            assertTrue(entry.meanings.length <= 1000)
            assertTrue(entry.attribution.startsWith("Offline"))
        }
        assertTrue(OfflineWordDefinitions.entryCount in 50..100)
    }
}
