package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import org.junit.Assert.*
import org.junit.Test

class ReadingAidDefinitionPolicyTest {
    private fun context(now: Double = 1000.0, valid: Boolean = true) = ReadingAidContext(
        1, "g1", 3, 0, 3, 10, now, now - 10, now, valid)
    private fun candidate(duration: Double, now: Double = 1000.0) = ScanpathCandidateEvidence(
        "prefix", 1, now - 10 - duration, now - 10, duration, 12, 3, 10, 0,
        1.0, 1.0, "g1", now, "trigger_observation_delivery")
    private fun definition(values: List<ReadingAidDecision>) = values.firstOrNull {
        it.status == ReadingAidDecisionStatus.CHOSEN && it.action?.aidType == ReadingAidType.WORD_DEFINITION }?.action

    @Test fun singleObservedPrefixCanOfferHelpWithoutClaimingRefixations() {
        val p = ReadingAidPolicy(ReadingAidPolicyConfig.definitionPrototype())
        assertNull(definition(p.onObservation(candidate(799.0), context())))
        val action = definition(p.onObservation(candidate(800.0), context()))!!
        assertEquals("CONTEXT_WORD_DEFINITION_V1", action.aidFormId)
        assertEquals("A5_WORD_RESIDENCE_DEFINITION_V1", action.ruleId)
        assertTrue(action.eventIds.isEmpty())
        assertEquals(listOf("prefix"), action.evidenceIds)
        assertNull(action.magnificationScale)
        assertTrue(action.experimental)
        assertFalse(p.activeActions().any { it.aidType == ReadingAidType.WORD_MAGNIFIER })
    }

    @Test fun unavailableStaleAndOtherWordEvidenceCannotOfferDefinition() {
        for (c in listOf(context(valid = false), context(now = 1500.0), context().copy(targetWordStartUtf16 = 12, targetWordEndUtf16 = 15))) {
            val p = ReadingAidPolicy(ReadingAidPolicyConfig.definitionPrototype())
            assertNull(definition(p.onObservation(candidate(900.0), c)))
        }
        val disabled = ReadingAidPolicy(ReadingAidPolicyConfig.definitionPrototype(), ReaderAidPreferences(wordDefinition = false))
        assertNull(definition(disabled.onObservation(candidate(900.0), context())))
    }
}
