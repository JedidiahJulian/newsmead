package com.newsmead.gaze.aid

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAidPreferencesStoreTest {
    @Test fun legacyBooleanMigratesToEquivalentMode() {
        assertEquals(ReadingAidsMode.OFF,
            ReaderAidPreferencesStore.decodeMode(null, false, ReadingAidsMode.AUTO))
        assertEquals(ReadingAidsMode.AUTO,
            ReaderAidPreferencesStore.decodeMode(null, true, ReadingAidsMode.OFF))
    }

    @Test fun storedModeWinsAndMissingStateUsesCurrentDefault() {
        assertEquals(ReadingAidsMode.ON,
            ReaderAidPreferencesStore.decodeMode("on", false, ReadingAidsMode.AUTO))
        assertEquals(ReadingAidsMode.AUTO,
            ReaderAidPreferencesStore.decodeMode(null, null, ReadingAidsMode.AUTO))
    }
}
