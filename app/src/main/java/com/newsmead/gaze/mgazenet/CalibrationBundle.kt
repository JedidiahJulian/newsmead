package com.newsmead.gaze.mgazenet

import java.io.*

/** One bounded, hash-bound payload. Never encodes training rows or legacy calibration. */
object CalibrationBundle {
    data class Artifact(val identity: CalibrationIdentity, val x: ByteArray, val y: ByteArray,
        val savedAt: Long? = null, val calibrationCollectionId: String? = null)
    private const val MAX_MODEL_BYTES = 8 * 1024 * 1024
    fun encode(artifact: Artifact): ByteArray {
        require(artifact.identity.acquisition != "unbound")
        require(artifact.x.size in 1..MAX_MODEL_BYTES && artifact.y.size in 1..MAX_MODEL_BYTES)
        val body = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeUTF(artifact.identity.canonical())
            listOf(artifact.x,artifact.y).forEach { out.writeInt(it.size); out.write(it) }
        } }.toByteArray()
        return ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use {
            it.writeUTF(CalibrationIdentity.VERSION); it.writeUTF(CalibrationIdentity.hash(body)); it.write(body)
        } }.toByteArray()
    }
    fun decode(bytes: ByteArray, compatible: (CalibrationIdentity) -> Boolean): Artifact {
        require(bytes.size <= MAX_MODEL_BYTES * 2 + 8192)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUTF() == CalibrationIdentity.VERSION) { "MGazeNet calibration required" }
            val hash = input.readUTF()
            val body = input.readBytes()
            require(CalibrationIdentity.hash(body) == hash) { "Calibration hash mismatch" }
            DataInputStream(ByteArrayInputStream(body)).use { payload ->
                val identity = CalibrationIdentity.parse(payload.readUTF())
                require(identity.acquisition != "unbound")
                require(compatible(identity)) { "Device or screen geometry changed" }
                fun model(): ByteArray {
                    val size = payload.readInt(); require(size in 1..MAX_MODEL_BYTES && size <= payload.available())
                    return ByteArray(size).also { payload.readFully(it) }
                }
                val artifact = Artifact(identity, model(), model())
                require(payload.available() == 0)
                artifact
            }
        }
    }
    /** One atomic generation contains both mappings; neither member is accepted alone. */
    fun encodeExperiment(artifacts: List<Artifact>): ByteArray {
        require(artifacts.size in 1..2)
        val configurations = artifacts.map { it.identity.configuration }.toSet()
        require(configurations.size == artifacts.size)
        require(configurations == setOf(GazeExperimentConfiguration.CURRENT_SVR) ||
            configurations == setOf(GazeExperimentConfiguration.NEXT_SVR, GazeExperimentConfiguration.NEXT_RIDGE))
        require(artifacts.all { it.savedAt != null && it.savedAt > 0 &&
            it.savedAt == artifacts.first().savedAt && it.calibrationCollectionId == artifacts.first().calibrationCollectionId })
        require(artifacts.first().calibrationCollectionId?.matches(Regex("[a-f0-9-]{36}")) == true)
        require(artifacts.all { it.identity.copy(configuration = artifacts.first().identity.configuration) == artifacts.first().identity })
        val body = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeUTF("newsmead_experiment_bundle_v1"); out.writeLong(artifacts.first().savedAt!!)
            out.writeUTF(artifacts.first().calibrationCollectionId!!); out.writeInt(artifacts.size)
            artifacts.forEach { a -> val encoded = encode(a); out.writeInt(encoded.size); out.write(encoded) }
        } }.toByteArray()
        return ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use {
            it.writeUTF(CalibrationIdentity.hash(body)); it.write(body)
        } }.toByteArray()
    }
    fun decodeExperiment(bytes: ByteArray, compatible: (CalibrationIdentity) -> Boolean): List<Artifact> {
        require(bytes.size in 1..(MAX_MODEL_BYTES * 4 + 32768))
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val hash = input.readUTF(); val body = input.readBytes()
            require(CalibrationIdentity.hash(body) == hash)
            DataInputStream(ByteArrayInputStream(body)).use { payload ->
                require(payload.readUTF() == "newsmead_experiment_bundle_v1")
                val savedAt = payload.readLong(); val id = payload.readUTF(); val count = payload.readInt()
                require(count in 1..2)
                val artifacts = List(count) {
                    val length = payload.readInt(); require(length in 1..(MAX_MODEL_BYTES * 2 + 8192) && length <= payload.available())
                    decode(ByteArray(length).also(payload::readFully), compatible).copy(savedAt = savedAt, calibrationCollectionId = id)
                }
                require(payload.available() == 0)
                // Reuse all pairing, metadata and geometry invariants on the read boundary.
                require(encodeExperiment(artifacts).contentEquals(bytes))
                artifacts
            }
        }
    }

}
