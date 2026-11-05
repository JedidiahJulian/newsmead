package com.newsmead.gaze.mgazenet

import com.newsmead.gaze.GazeCoordinateFrame
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class GazeExperimentCoreTest {
    private val legacy = CalibrationIdentity("a".repeat(64),1080,2340,0,
        GazeCoordinateFrame(0,101,1080,2239),"640x480:270:GPU")
    private fun rejected(block: () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Invalid artifact accepted")
    }
    private fun artifact(config: GazeExperimentConfiguration, id: String = "12345678-1234-1234-1234-123456789abc") =
        CalibrationBundle.Artifact(legacy.copy(configuration = config),byteArrayOf(1),byteArrayOf(2),123L,id)

    @Test fun identityPreservesLegacyAndSeparatesAllExperiments() {
        assertEquals(16,legacy.canonical().split('\n').size)
        assertEquals(CalibrationIdentity.VERSION,legacy.canonical().substringBefore('\n'))
        assertEquals(legacy,CalibrationIdentity.parse(legacy.canonical()))
        assertEquals(4,(listOf(legacy) + GazeExperimentConfiguration.entries.map { legacy.copy(configuration = it) })
            .map { it.canonical() }.distinct().size)
        GazeExperimentConfiguration.entries.forEach { config ->
            val identity = legacy.copy(configuration = config)
            assertEquals(identity,CalibrationIdentity.parse(identity.canonical()))
            assertTrue(identity.canonical().contains(config.modelSha))
            assertTrue(identity.canonical().contains(config.mappingId))
        }
        val b = legacy.copy(configuration = GazeExperimentConfiguration.NEXT_SVR).canonical()
        rejected { CalibrationIdentity.parse(b.replace(GazeExperimentConfiguration.NEXT_MODEL_SHA,CalibrationIdentity.MODEL)) }
        rejected { CalibrationIdentity.parse(b.replace("\nB", "\nC")) }
        assertNull(GazeExperimentConfiguration.fromId("unknown"))
    }
    @Test fun pairRejectsPartialMixedGenerationCorruptAndIncompatible() {
        val b = artifact(GazeExperimentConfiguration.NEXT_SVR)
        val c = artifact(GazeExperimentConfiguration.NEXT_RIDGE)
        val bytes = CalibrationBundle.encodeExperiment(listOf(b,c))
        val restored = CalibrationBundle.decodeExperiment(bytes) { it.screenWidth == 1080 }
        assertEquals(listOf(b.identity,c.identity),restored.map { it.identity })
        assertTrue(restored.all { it.calibrationCollectionId == b.calibrationCollectionId && it.savedAt == 123L })
        rejected { CalibrationBundle.encodeExperiment(listOf(b)) }
        rejected { CalibrationBundle.encodeExperiment(listOf(b,b)) }
        rejected { CalibrationBundle.encodeExperiment(listOf(b,c.copy(calibrationCollectionId = "aaaaaaaa-1234-1234-1234-123456789abc"))) }
        rejected { CalibrationBundle.encodeExperiment(listOf(b,c.copy(savedAt = 124L))) }
        rejected { CalibrationBundle.encodeExperiment(listOf(b,c.copy(identity = c.identity.copy(rotation = 1)))) }
        rejected { CalibrationBundle.decodeExperiment(bytes.copyOf(bytes.size - 1)) { true } }
        rejected { CalibrationBundle.decodeExperiment(bytes) { false } }
        rejected { CalibrationBundle.decode(bytes) { true } }
        val a = artifact(GazeExperimentConfiguration.CURRENT_SVR)
        assertEquals(a.identity,CalibrationBundle.decodeExperiment(CalibrationBundle.encodeExperiment(listOf(a))) { true }.single().identity)
        rejected { CalibrationBundle.encodeExperiment(listOf(a,a)) }
    }
    @Test fun ridgeRoundTripFiniteAndMalformedBoundaries() {
        val features = Array(8) { i -> FloatArray(258) { j -> (i * (j % 3 + 1) * .01).toFloat() } }
        val labels = Array(8) { i -> floatArrayOf(i / 10f, .8f - i / 20f) }
        RidgeCalibration().use { ridge ->
            ridge.fit(features,labels)
            val bytes = ridge.encode()
            RidgeCalibration.decode(bytes).use { restored ->
                assertTrue(restored.trained)
                (features.toList() + listOf(FloatArray(258) { .123f })).forEach {
                    assertArrayEquals(ridge.predict(it),restored.predict(it),0f)
                }
            }
            rejected { RidgeCalibration.decode(bytes.copyOf(bytes.size - 1)) }
            rejected { RidgeCalibration.decode(bytes + byteArrayOf(0)) }
            rejected { RidgeCalibration.decode(ByteArray(8193)) }
            // Rehash a malicious but structurally correct non-finite coefficient payload.
            val body = DataInputStream(ByteArrayInputStream(bytes)).use { it.readUTF(); it.readBytes() }
            val offset = DataInputStream(ByteArrayInputStream(body)).use { it.readUTF(); it.readDouble(); it.readInt(); body.size - it.available() }
            val corrupt = body.copyOf()
            val nan = ByteArrayOutputStream().also { DataOutputStream(it).use { out -> out.writeDouble(Double.NaN) } }.toByteArray()
            nan.copyInto(corrupt,offset)
            val forged = ByteArrayOutputStream().also { DataOutputStream(it).use { out -> out.writeUTF(CalibrationIdentity.hash(corrupt)); out.write(corrupt) } }.toByteArray()
            rejected { RidgeCalibration.decode(forged) }
            rejected { ridge.fit(arrayOf(FloatArray(258) { Float.NaN }),arrayOf(floatArrayOf(.5f,.5f))) }
            assertFalse(ridge.trained)
        }
    }
}
