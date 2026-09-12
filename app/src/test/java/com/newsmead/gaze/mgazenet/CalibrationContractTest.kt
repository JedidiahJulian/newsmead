package com.newsmead.gaze.mgazenet

import com.newsmead.gaze.GazeCoordinateFrame
import org.junit.Assert.*
import org.junit.Test

class CalibrationContractTest {
    private fun identity() = CalibrationIdentity("a".repeat(64),1080,2340,0,
        GazeCoordinateFrame(0,101,1080,2239),"640x480:270:GPU")
    private fun rejected(action: () -> Unit) { try { action(); fail("Incompatible artifact accepted") } catch (_: Exception) {} }
    @Test fun fullViewportOriginAndOrderedGeometryRoundTrip() {
        val id = identity()
        assertEquals(id,CalibrationIdentity.parse(id.canonical()))
        assertEquals(13,id.targets.distinct().size)
        assertEquals(listOf(1,5,9,12,16,19,27,30,34,37,41,45,23),
            CalibrationIdentity.UPSTREAM_GRID_INDICES)
        assertTrue(CalibrationIdentity.TARGET_CUE_MS < MgazeNetCalibrationSession.SETTLE_MS)
        assertTrue(CalibrationIdentity.TARGET_FRACTIONS.all { (x, y) ->
            x in .10f..90f/100f && y in .10f..90f/100f
        })
        assertEquals(108f,id.targets.first().x,.001f)
        assertEquals(324.9f,id.targets.first().y,.001f)
        assertEquals(540f,id.targets.last().x,.001f)
        assertEquals(1220.5f,id.targets.last().y,.001f)
        assertEquals(id.targets[0].y,id.targets[2].y,0f)
        assertTrue(id.targets[3].y > id.targets[2].y)
        rejected { CalibrationIdentity.parse(id.canonical().replace(CalibrationIdentity.VERSION,"newsmead_mgazenet_calibration_v1")) }
        rejected { CalibrationIdentity.parse(id.canonical().replace("screen_px_v1","legacy")) }
        rejected { CalibrationIdentity.parse(id.canonical().replace(CalibrationIdentity.MODEL,"0".repeat(64))) }
        val first = id.targets.first()
        rejected { CalibrationIdentity.parse(id.canonical().replace("${first.x},${first.y}","${first.x+1},${first.y}")) }
    }
    @Test fun bundleRejectsLegacyPartialCorruptAndWrongDevice() {
        val id = identity(); val data = CalibrationBundle.encode(CalibrationBundle.Artifact(id,byteArrayOf(1,2),byteArrayOf(3,4)))
        val result = CalibrationBundle.decode(data) { it == id }
        assertEquals(id,result.identity); assertArrayEquals(byteArrayOf(1,2),result.x)
        rejected { CalibrationBundle.decode(data) { it.device == "b".repeat(64) } }
        rejected { CalibrationBundle.decode(data.copyOf(data.size-1)) { true } }
        rejected { CalibrationBundle.decode(data.copyOf().also { it[it.lastIndex] = 10 }) { true } }
        rejected { CalibrationBundle.decode("raw_x,raw_y,target_x,target_y\n1,2,3,4".toByteArray()) { true } }
        rejected { CalibrationBundle.encode(CalibrationBundle.Artifact(id,byteArrayOf(),byteArrayOf(3))) }
        rejected { CalibrationBundle.encode(CalibrationBundle.Artifact(id.copy(acquisition="unbound"),byteArrayOf(1),byteArrayOf(3))) }
        rejected { CalibrationBundle.decode(data) { it == id.copy(viewport = GazeCoordinateFrame(0,0,1080,2340)) } }
    }
    @Test fun drawsSettlesAdmitsExactly45AndNeverTrainsPractice() {
        val session = MgazeNetCalibrationSession(identity()); session.begin()
        val source = FloatArray(258) { 1f }
        session.sample(2000.0,2001.0,source,11.0,11.0); assertEquals(0,session.count)
        var now = 0.0
        for (target in -1 until identity().targets.size) {
            assertEquals(target,session.index); session.drawn(now)
            session.sample(now+1499,now+1500,source,11.0,11.0); assertEquals(0,session.count)
            session.sample(now+1500,now+1501,source,10.0,11.0); assertEquals(0,session.count)
            for (i in 0..44) session.sample(now+1600+i*100,now+1650+i*100,source,11.0,11.0)
            assertEquals(45,session.count)
            session.sample(now+6200,now+6250,source,11.0,11.0); assertEquals(45,session.count)
            session.tick(now+6600); now+=7000
        }
        source.fill(99f)
        val (features,labels) = session.takeTraining()
        assertEquals(585,features.size); assertEquals(585,labels.size)
        assertTrue(features.all { it[0] == 1f })
        assertEquals(identity().targets.first().y/2340,labels.first()[1],0f)
        assertEquals(identity().targets.last().x/1080,labels.last()[0],0f)
        session.close() // transferred arrays belong to worker, not the canceled UI
        assertEquals(1f,features[0][0],0f)
        features.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) }
    }
    @Test fun nonfiniteDuplicateLateRowsAndDeadlineAreRejected() {
        val s = MgazeNetCalibrationSession(identity()); s.begin(); s.drawn(0.0)
        s.sample(1600.0,1700.0,FloatArray(258){ Float.NaN },11.0,11.0)
        s.sample(1800.0,1801.0,FloatArray(258),11.0,11.0)
        s.sample(1800.0,1802.0,FloatArray(258),11.0,11.0)
        s.sample(1900.0,30001.0,FloatArray(258),11.0,11.0)
        assertEquals(1,s.count)
        s.tick(30000.0); assertEquals(MgazeNetCalibrationSession.Phase.FAILED,s.phase)
        rejected { s.takeTraining() }
    }
}
