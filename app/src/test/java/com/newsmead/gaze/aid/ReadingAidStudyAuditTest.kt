package com.newsmead.gaze.aid

import com.newsmead.gaze.scanpath.*
import com.newsmead.research.study.StudyJson
import org.junit.Assert.*
import org.junit.Test

/** Audit consumers, including failed consumers, cannot influence the policy's actions. */
class ReadingAidStudyAuditTest {
    private val geometry = ScanpathGeometry("g", 0.0, 0.0, ScanpathRect(0.0,0.0,300.0,600.0),30.0,
        (0 until 20).map { ScanpathLine(it*10,it*10+8,0.0,300.0,it*30.0,it*30.0+30) })
    private fun context(t: Double, line: Int, reason: String? = null): ReadingAidContext {
        val y = line*30.0+15
        val sample = ScanpathSample(t,t,55.0,y,line,line*10,line*10+5,"g",reason == null,reason,
            55.0,y,ScanpathCoordinateStream.ARTICLE_SMOOTHED)
        return ReadingAidContext(1,"g",20,line,line*10,line*10+5,t,t,t,reason == null,
            geometry,55.0,sample,targetWordBounds=ScanpathRect(30.0,line*30.0,90.0,line*30.0+30))
    }
    @Test fun auditSnapshotsAndFailedWriterDoNotChangeDecisions() {
        val config = ReadingAidPolicyConfig.evidenceAlignedPrototype()
        val plain = ReadingAidPolicy(config)
        val events = mutableListOf<org.json.JSONObject>()
        val audited = ReadingAidPolicy(config,onAudit={ kind,_,payload -> events.add(StudyJson.obj("kind" to kind,"payload" to payload)) })
        val failed = ReadingAidPolicy(config,onAudit={ _,_,_ -> throw IllegalStateException("writer failed") })
        val inputs = buildList {
            repeat(14) { add(context(it*100.0,3)) }
            repeat(6) { add(context(1400+it*100.0,7)) }
            repeat(6) { add(context(2000+it*100.0,3)) }
            add(context(2700.0,3,"no_face")); add(context(3300.0,3,"no_face"))
            repeat(6) { add(context(3400+it*100.0,3)) }
        }
        for (input in inputs) {
            val expected = plain.onObservation(null,input)
            assertEquals(expected,audited.onObservation(null,input))
            assertEquals(expected,failed.onObservation(null,input))
        }
        assertTrue(events.any { it.getString("kind") == "support_revision" })
        assertTrue(events.any { it.getString("kind") == "aid_evaluation" })
        assertTrue(events.all { !it.toString().contains("Infinity") && !it.toString().contains("NaN") })
    }
    @Test fun disabledPolicyReportsNotEvaluatedAndInvalidationPreservesEarlierState() {
        val events = mutableListOf<Pair<String,org.json.JSONObject>>()
        val policy = ReadingAidPolicy(ReadingAidPolicyConfig.evidenceAlignedPrototype(),
            onAudit={ kind,_,payload -> events.add(kind to (StudyJson.value(payload) as org.json.JSONObject)) })
        repeat(4) { policy.onObservation(null,context(it*100.0,4)) }
        policy.updatePreferences(ReaderAidPreferences(mode=ReadingAidsMode.OFF),context(450.0,4))
        val evaluation = events.last { it.first == "aid_evaluation" }.second.getJSONArray("components")
        assertEquals(4,evaluation.length())
        repeat(4) { assertEquals("not_evaluated",evaluation.getJSONObject(it).getString("status")) }
        policy.reset(context(500.0,4))
        assertTrue(events.filter { it.first == "support_revision" }.any { it.second.optBoolean("before_invalidation") })
    }
}
