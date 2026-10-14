package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathCoordinateStream
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathLine
import com.newsmead.gaze.scanpath.ScanpathRect
import com.newsmead.gaze.scanpath.ScanpathSample
import com.newsmead.gaze.scanpath.ScanpathTextDirection
import org.junit.Assert.*
import org.junit.Test

/** Synthetic policy integration only: no engine, Android view, provider or camera. */
class FocusActivationIntegrationTest {
    @Test fun focusStyleChangeReissuesAppearanceWithoutChangingActivationOrEvidence() {
        val f = Fixture()
        f.support(100.0)
        f.episode(200.0); f.episode(1000.0); f.episode(1800.0)
        val original = f.focus()!!
        assertEquals(ReadingFocusStyle.FADE_TEXT, original.focusStyle)
        val changed = f.policy.updatePreferences(f.policy.preferences.copy(
            focusStyle = ReadingFocusStyle.DARK_SURROUNDINGS), f.context(2400.0))
        val dark = f.focus()!!
        assertEquals(ReadingFocusStyle.DARK_SURROUNDINGS, dark.focusStyle)
        assertNotEquals(original.id, dark.id)
        assertEquals(original.strength, dark.strength, 0.0)
        assertEquals(original.anchor, dark.anchor)
        assertEquals(original.focusEvidence, dark.focusEvidence)
        assertTrue(changed.any { it.status == ReadingAidDecisionStatus.CHOSEN && it.action == dark })
        f.policy.updatePreferences(f.policy.preferences.copy(focusStyle = ReadingFocusStyle.FADE_TEXT),
            f.context(2400.0))
        assertEquals(ReadingFocusStyle.FADE_TEXT, f.focus()!!.focusStyle)
        assertEquals(original.focusEvidence, f.focus()!!.focusEvidence)
    }

    private class Fixture {
        var geometry = ScanpathGeometry("g1", 10.0, 100.0,
            ScanpathRect(10.0, 100.0, 210.0, 180.0), 20.0,
            List(4) { i -> ScanpathLine(i * 10, i * 10 + 9, 0.0, 200.0,
                i * 20.0, (i + 1) * 20.0) }, ScanpathTextDirection.LTR)
        val episodes = mutableListOf<ReadingAidFocusEpisode>()
        val policy = ReadingAidPolicy(ReadingAidPolicyConfig.definitionPrototype(),
            ReaderAidPreferences(lineGuide = false, lineStartCue = false,
                wordMagnifier = false, wordDefinition = false), onFocusEpisode = episodes::add)

        fun context(t: Double, line: Int? = null, missing: Boolean = false,
                    allowed: Boolean = true, presenceRow: Int = 0): ReadingAidContext {
            val capture = t.takeUnless { missing }
            val x = 40.0.takeUnless { missing }
            val y = (geometry.textOriginY + (line ?: presenceRow) * 20.0 + 10.0).takeUnless { missing }
            val sample = ScanpathSample(capture, t, null, null, line, null, null,
                geometry.snapshotId, !missing, if (missing) "no_face" else null,
                x, y, ScanpathCoordinateStream.ARTICLE_SMOOTHED)
            return ReadingAidContext(7, geometry.snapshotId, geometry.lines.size, line,
                null, null, t, capture, t, !missing, geometry, x, sample,
                bookmarkInputAllowed = false, focusInputAllowed = allowed)
        }

        /** These moving returns have no line/word mapping and never supply a candidate. */
        fun presence(t: Double, row: Int = 0, allowed: Boolean = true) =
            policy.onObservation(null, context(t, allowed = allowed, presenceRow = row))

        fun episode(start: Double, allowed: Boolean = true) {
            policy.onObservation(null, context(start, missing = true, allowed = allowed))
            policy.onObservation(null, context(start + 400.0, missing = true, allowed = allowed))
            for ((i, offset) in listOf(500.0, 550.0, 600.0).withIndex())
                presence(start + offset, row = i, allowed = allowed)
        }

        fun support(t: Double, line: Int = 0, allowed: Boolean = true) {
            val c = context(t, line, allowed = allowed)
            val candidate = ScanpathCandidateEvidence("candidate:$t:$line", 1,
                t - 100.0, t, 100.0, 3, null, null, line, 0.0, 1.0,
                geometry.snapshotId, t, "trigger_observation_delivery")
            policy.onObservation(candidate, c)
        }

        fun definitionBoundary(t: Double, reason: String) {
            val c = context(t, allowed = false)
            policy.onEvent(ScanpathEvent("boundary:$reason:$t", "P13", "line", emptyList(),
                t, t, t, "trigger_observation_delivery", mapOf("reason" to reason)), null, c)
        }

        fun focus() = policy.activeActions().singleOrNull { it.aidType == ReadingAidType.FOCUS_REGION }
        fun diagnostics(t: Double) = policy.focusDiagnostics(t)
    }

    @Test fun expiredSourceClearsDisplayedActionDespiteFreshUnmappedPresenceAndNewSupportRestoresDemand() {
        val f = Fixture()
        f.support(100.0)
        f.episode(200.0); f.episode(1000.0)
        f.episode(27400.0) // Third confirmation at 28000; first confirmation at 800 remains within 30s.
        val original = f.focus()!!
        assertEquals(3, original.focusEvidence!!.episodeCount)
        assertEquals(100.0, original.focusEvidence!!.support.evidenceAvailableMs, 0.0)
        for (t in 28100..30100 step 100) f.presence(t.toDouble(), row = (t / 100) % 4)
        assertNotNull("The source remains valid at exactly 30 seconds", f.focus())
        val cleared = f.presence(30101.0, row = 2)
        assertNull("Fresh presence cannot renew an expired line anchor", f.focus())
        assertTrue(cleared.any { it.status == ReadingAidDecisionStatus.CLEARED &&
            it.action?.aidType == ReadingAidType.FOCUS_REGION })
        assertEquals(true, f.diagnostics(30101.0)["has_demand"])
        assertEquals(false, f.diagnostics(30101.0)["has_anchor"])
        assertEquals("waiting_for_supported_anchor", f.diagnostics(30101.0)["reason"])
        f.support(30200.0, line = 2)
        val restored = f.focus()!!
        assertNotEquals(original.id, restored.id)
        assertEquals(2, restored.anchor.line)
        assertEquals(original.focusEvidence!!.episodes.map { it.episodeId },
            restored.focusEvidence!!.episodes.map { it.episodeId })
        assertEquals(original.focusEvidence!!.observedRecoveryMs,
            restored.focusEvidence!!.observedRecoveryMs, 0.0)
        assertEquals(original.strength, restored.strength, 0.0)
    }

    @Test fun sameGenerationGeometryResetRetainsTwoCompletionsButRequiresANewSupportedAnchor() {
        val f = Fixture()
        f.support(100.0); f.episode(200.0); f.episode(1000.0)
        assertEquals(2, f.episodes.size)
        f.geometry = f.geometry.copy(snapshotId = "g2", textOriginY = 120.0,
            visibleRectScreen = ScanpathRect(10.0, 120.0, 210.0, 200.0))
        f.policy.reset(f.context(1700.0), preserveCompletedFocus = true)
        assertNull(f.focus())
        assertEquals(false, f.diagnostics(1700.0)["has_anchor"])
        for (t in listOf(1800.0, 1850.0, 1900.0)) f.presence(t, row = 1)
        f.episode(2000.0)
        assertEquals(3, f.episodes.size)
        assertEquals(listOf("g1", "g1", "g2"), f.episodes.map { it.geometrySnapshotId })
        assertNull(f.episodes.last().source)
        assertNull(f.focus())
        assertEquals(true, f.diagnostics(2600.0)["has_demand"])
        assertEquals("waiting_for_supported_anchor", f.diagnostics(2600.0)["reason"])
        f.support(2700.0, line = 2)
        val action = f.focus()!!
        assertEquals("g2", action.anchor.geometrySnapshotId)
        assertEquals(7L, action.anchor.generation)
        assertEquals(2, action.anchor.line)
        assertEquals(3, action.focusEvidence!!.episodeCount)
        assertEquals(0.5, action.strength, 0.0)
        assertEquals(2700.0, action.focusEvidence!!.support.lastCaptureMs, 0.0)
    }

    @Test fun definitionBoundariesPreserveCompletedEpisodesAndConsultedSamplesCannotCountOrAnchor() {
        val f = Fixture()
        f.support(100.0); f.episode(200.0); f.episode(1000.0)
        f.definitionBoundary(1700.0, "definition_opened")
        assertEquals(2, f.diagnostics(1700.0)["recent_episode_count"])
        assertEquals(false, f.diagnostics(1700.0)["has_anchor"])
        f.episode(1800.0, allowed = false)
        for ((i, line) in listOf(0, 1, 0, 1).withIndex())
            f.support(2500.0 + i * 100, line, allowed = false)
        assertEquals(2, f.episodes.size)
        assertEquals(0, f.diagnostics(2800.0)["recent_alternation_count"])
        assertEquals(false, f.diagnostics(2800.0)["has_anchor"])
        assertNull(f.focus())
        f.definitionBoundary(3000.0, "definition_closed")
        assertEquals(2, f.diagnostics(3000.0)["recent_episode_count"])
        f.support(3100.0, line = 1)
        assertNull(f.focus())
        f.episode(3200.0)
        assertEquals(3, f.episodes.size)
        assertEquals(3, f.focus()!!.focusEvidence!!.episodeCount)
        assertTrue(f.episodes.none { it.completedDeliveryMs in 1700.0..3000.0 })
    }

    @Test fun definitionOpenAndCloseCannotJoinPartialAbabEvenWithoutACaptureGap() {
        val f = Fixture()
        f.support(100.0, 0); f.support(200.0, 1); f.support(300.0, 0)
        f.definitionBoundary(325.0, "definition_opened")
        f.definitionBoundary(350.0, "definition_closed")
        f.support(400.0, 1) // Only 100ms since prior support, so the operational boundary must break it.
        assertNull(f.focus())
        assertEquals(0, f.diagnostics(400.0)["recent_alternation_count"])
        f.support(500.0, 0); f.support(600.0, 1); f.support(700.0, 0)
        val alternation = f.focus()!!.focusEvidence!!.alternations.single()
        assertEquals(listOf(1, 0, 1, 0), alternation.visits.map { it.line })
        assertTrue(alternation.visits.all { it.lastCaptureMs >= 400.0 })
    }
}
