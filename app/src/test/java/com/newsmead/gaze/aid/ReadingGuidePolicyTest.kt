package com.newsmead.gaze.aid
import com.newsmead.gaze.scanpath.*
import org.junit.Assert.*
import org.junit.Test

class ReadingGuidePolicyTest {
    private val range = ReadingGuideRange(0, 20, ReadingGuideUnit.SENTENCE)
    private val geometry = ScanpathGeometry("g", 0.0, 0.0, ScanpathRect(0.0, 0.0, 100.0, 100.0),
        20.0, listOf(ScanpathLine(0, 10, 0.0, 100.0, 0.0, 20.0),
            ScanpathLine(10, 20, 0.0, 100.0, 20.0, 40.0)), ScanpathTextDirection.LTR)
    private fun context(t: Double = 100.0, line: Int = 0) = ReadingAidContext(1, "g", 2,
        line, null, null, t, t - 10, t, geometry = geometry, guideRange = range)
    private fun candidate(c: ReadingAidContext) = ScanpathCandidateEvidence("c:${c.nowMs}", 1,
        c.nowMs - 90, c.captureMs!!, 80.0, 3, null, null, c.targetLine, 0.0, 1.0,
        c.geometrySnapshotId, c.nowMs, "trigger_observation_delivery")
    private fun sentencePolicy() = ReadingAidPolicy(ReadingAidPolicyConfig.definitionPrototype(),
        ReaderAidPreferences(guideStyle = ReadingGuideStyle.SENTENCE))

    @Test fun sentenceUsesNullWordAndRetainsRangeAcrossWrappedLines() {
        val p = sentencePolicy()
        val c = context()
        p.onObservation(candidate(c), c)
        val action = p.activeActions().single()
        assertEquals(ReadingAidType.CURRENT_SENTENCE, action.aidType)
        assertEquals("CURRENT_SENTENCE_CONTEXT_HIGHLIGHT_V1", action.aidFormId)
        assertEquals("A1_RELIABLE_SENTENCE_CONTEXT_V1", action.ruleId)
        assertEquals(range, action.guideRange)
        assertNull(action.anchor.wordStartUtf16)
        assertEquals(0.25, action.strength, 0.0)
        val moved = context(200.0, 1)
        p.onObservation(candidate(moved), moved)
        assertEquals(action.id, p.activeActions().single().id)
    }
    @Test fun guideDoesNotDependOnOtherAidActivationAndStalePrefixClears() {
        val p = sentencePolicy()
        val c = context()
        p.updatePreferences(p.preferences.copy(lineStartCue = false, wordMagnifier = false,
            wordDefinition = false, focusRegion = false), c)
        p.onObservation(candidate(c), c)
        assertEquals(ReadingAidType.CURRENT_SENTENCE, p.activeActions().single().aidType)
        val later = context(500.0)
        p.onObservation(candidate(c), later)
        assertTrue(p.activeActions().isEmpty())
    }
    @Test fun styleSwitchClearsAndGuidesAreExclusive() {
        val p = sentencePolicy()
        val c = context()
        p.onObservation(candidate(c), c)
        assertTrue(p.updatePreferences(p.preferences.copy(guideStyle = ReadingGuideStyle.LINE), c)
            .any { it.status == ReadingAidDecisionStatus.CLEARED })
        p.onObservation(candidate(c), c)
        assertEquals(ReadingAidType.CURRENT_LINE, p.activeActions().single().aidType)
        assertEquals("CURRENT_LINE_HIGHLIGHT_GRAY_V1", p.activeActions().single().aidFormId)
    }
    @Test fun uncertainMissingOutOfBoundsAndChangedRangeClear() {
        for (bad in listOf(context().copy(guideRange = null),
            context().copy(guideRange = range.copy(endUtf16 = 21)),
            context().copy(guideRange = range.copy(startUtf16 = 10)),
            context().copy(validLiveInput = false), context().copy(generation = 2),
            context().copy(geometrySnapshotId = "other"))) {
            val p = sentencePolicy()
            p.onObservation(candidate(context()), context())
            p.tick(bad)
            assertTrue(p.activeActions().isEmpty())
        }
        val p = sentencePolicy()
        p.onObservation(candidate(context()), context())
        p.onObservation(null, context())
        assertTrue(p.activeActions().isEmpty())
    }
    @Test fun disableMasterManualZeroAndResetClear() {
        for (preferences in listOf(ReaderAidPreferences(enabled = false),
            ReaderAidPreferences(lineGuide = false), ReaderAidPreferences(maxStrength = 0.0),
            ReaderAidPreferences(adaptiveStrength = false, manualStrength = 0.0))) {
            val p = sentencePolicy()
            p.onObservation(candidate(context()), context())
            p.updatePreferences(preferences.copy(guideStyle = ReadingGuideStyle.SENTENCE), context())
            assertTrue(p.activeActions().isEmpty())
        }
        val p = sentencePolicy()
        p.onObservation(candidate(context()), context())
        p.reset(context())
        assertTrue(p.activeActions().isEmpty())
    }
    @Test fun sentenceCanBeChosenImmediatelyAtExpiryWithoutCooldown() {
        val p = sentencePolicy()
        val initial = context()
        p.onObservation(candidate(initial), initial)
        val prior = p.activeActions().single()
        val expiry = context(prior.expiresMs)
        val decisions = p.onObservation(candidate(expiry), expiry)
        assertTrue(decisions.any { it.status == ReadingAidDecisionStatus.EXPIRED })
        assertTrue(decisions.any { it.status == ReadingAidDecisionStatus.CHOSEN &&
            it.action?.aidType == ReadingAidType.CURRENT_SENTENCE })
        assertFalse(decisions.any { it.reason == "cooldown" })
        assertNotEquals(prior.id, p.activeActions().single().id)
    }
    @Test fun sentenceCanBeChosenImmediatelyAfterControlRestoreOrReset() {
        for (clearWith in listOf("disable", "style", "reset")) {
            val p = sentencePolicy()
            val c = context()
            p.onObservation(candidate(c), c)
            val prior = p.activeActions().single()
            when (clearWith) {
                "disable" -> {
                    p.updatePreferences(p.preferences.copy(lineGuide = false), c)
                    p.updatePreferences(p.preferences.copy(lineGuide = true), c)
                }
                "style" -> {
                    p.updatePreferences(p.preferences.copy(guideStyle = ReadingGuideStyle.LINE), c)
                    p.onObservation(candidate(c), c)
                    p.updatePreferences(p.preferences.copy(guideStyle = ReadingGuideStyle.SENTENCE), c)
                }
                "reset" -> p.reset(c)
            }
            val decisions = p.onObservation(candidate(c), c)
            assertFalse(decisions.any { it.reason == "cooldown" })
            assertEquals(ReadingAidType.CURRENT_SENTENCE, p.activeActions().single().aidType)
            assertNotEquals(prior.id, p.activeActions().single().id)
        }
    }
    @Test fun identicalOffsetsAcrossGenerationOrGeometryRequireNewActionId() {
        for (next in listOf(context(200.0).copy(generation = 2),
            context(200.0).copy(geometrySnapshotId = "g2", geometry = geometry.copy(snapshotId = "g2")))) {
            val p = sentencePolicy()
            val c = context()
            p.onObservation(candidate(c), c)
            val prior = p.activeActions().single()
            p.onObservation(candidate(next), next)
            val action = p.activeActions().single()
            assertNotEquals(prior.id, action.id)
            assertEquals(next.generation, action.anchor.generation)
            assertEquals(next.geometrySnapshotId, action.anchor.geometrySnapshotId)
            assertEquals(prior.guideRange, action.guideRange)
        }
    }
    @Test fun manualStrengthAndLegacyFormRemainAvailable() {
        val p = sentencePolicy()
        p.updatePreferences(p.preferences.copy(adaptiveStrength = false, manualStrength = 0.4), context())
        p.onObservation(candidate(context()), context())
        assertEquals(0.4, p.activeActions().single().strength, 0.0)
        val legacy = ReadingAidPolicy(ReadingAidPolicyConfig.experimentalPrototype(),
            ReaderAidPreferences(guideStyle = ReadingGuideStyle.SENTENCE))
        legacy.onObservation(candidate(context()), context())
        assertEquals("CURRENT_LINE_HIGHLIGHT_V1", legacy.activeActions().single().aidFormId)
    }
}
