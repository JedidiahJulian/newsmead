package com.newsmead.gaze.mgazenet

import kotlin.math.sqrt

/**
 * Experimental native mapping for frame-level 258-D embeddings -> normalized screen XY.
 * Minimizes sum_i ||y_i - intercept - x_i W||^2 + lambda ||W||^2; the intercept is
 * unpenalized. Lambda is not divided by sample count. No standardization or output clamping.
 * The default lambda follows pinned GazeFollower2, not a native accuracy validation.
 * Calls are serialized; failed refits invalidate the previous model and close is terminal.
 */
class RidgeCalibration(val lambda: Double = 3.0) : CalibrationRegressor {
    init { require(lambda.isFinite() && lambda > 0.0) { "lambda must be finite and positive" } }

    data class FitMetadata(
        val lambda: Double,
        val sampleCount: Int,
        val featureCount: Int = FEATURE_COUNT,
        val algorithm: String = "native_ridge_centered_cholesky_v1",
        val objective: String = "sum_squared_error_plus_lambda_times_squared_weight_norm_unpenalized_intercept",
        val coordinateSpace: String = "normalized_screen_xy",
        val solver: String = "double_centered_gram_cholesky",
        val featureScaling: String = "none"
    )

    private data class Model(
        val featureMean: DoubleArray,
        val labelMean: DoubleArray,
        val weights: Array<DoubleArray>,
        val metadata: FitMetadata
    )

    private var model: Model? = null
    private var closed = false
    override val trained: Boolean @Synchronized get() = model != null
    val fitMetadata: FitMetadata? @Synchronized get() = model?.metadata

    @Synchronized
    override fun fit(features: Array<FloatArray>, labels: Array<FloatArray>) {
        model = null
        check(!closed) { "Calibration is closed" }
        require(features.isNotEmpty() && features.size == labels.size) { "Expected matching nonempty rows" }
        require(features.all { it.size == FEATURE_COUNT && it.all(Float::isFinite) }) { "Expected finite 258-D features" }
        require(labels.all { it.size == 2 && it.all { v -> v.isFinite() && v in 0f..1f } }) {
            "Expected finite normalized XY labels in [0, 1]"
        }

        val featureMean = DoubleArray(FEATURE_COUNT)
        val labelMean = DoubleArray(2)
        for (r in features.indices) {
            for (j in 0 until FEATURE_COUNT) featureMean[j] += features[r][j].toDouble()
            for (axis in 0..1) labelMean[axis] += labels[r][axis].toDouble()
        }
        for (j in featureMean.indices) featureMean[j] /= features.size.toDouble()
        for (axis in 0..1) labelMean[axis] /= features.size.toDouble()

        val gram = Array(FEATURE_COUNT) { DoubleArray(FEATURE_COUNT) }
        val rhs = Array(FEATURE_COUNT) { DoubleArray(2) }
        val centered = DoubleArray(FEATURE_COUNT)
        for (r in features.indices) {
            for (j in centered.indices) centered[j] = features[r][j].toDouble() - featureMean[j]
            for (i in centered.indices) {
                for (j in 0..i) gram[i][j] += centered[i] * centered[j]
                for (axis in 0..1) rhs[i][axis] += centered[i] * (labels[r][axis].toDouble() - labelMean[axis])
            }
        }
        for (i in gram.indices) {
            val diagonal = gram[i][i]
            val regularized = diagonal + lambda
            // A positive Cholesky pivot can be a rounding artifact even when a tiny
            // penalty disappeared and duplicate columns made the stored matrix singular.
            check(regularized.isFinite() && regularized > diagonal) {
                "Configured ridge penalty is not representable at feature scale"
            }
            gram[i][i] = regularized
        }

        // Store the lower Cholesky factor in place; do not invert the Gram matrix.
        // An unresolvable penalty at extreme scales fails explicitly, with no extra jitter.
        for (i in gram.indices) {
            for (j in 0..i) {
                var value = gram[i][j]
                for (k in 0 until j) value -= gram[i][k] * gram[j][k]
                check(value.isFinite() && (i != j || value > 0.0)) { "Ridge system is numerically non-positive-definite" }
                gram[i][j] = if (i == j) sqrt(value) else value / gram[j][j]
            }
        }
        for (axis in 0..1) {
            for (i in rhs.indices) {
                var value = rhs[i][axis]
                for (j in 0 until i) value -= gram[i][j] * rhs[j][axis]
                rhs[i][axis] = value / gram[i][i]
            }
            for (i in rhs.indices.reversed()) {
                var value = rhs[i][axis]
                for (j in i + 1 until FEATURE_COUNT) value -= gram[j][i] * rhs[j][axis]
                rhs[i][axis] = value / gram[i][i]
                check(rhs[i][axis].isFinite()) { "Ridge solution is nonfinite" }
            }
        }
        model = Model(featureMean, labelMean, rhs, FitMetadata(lambda, features.size))
    }

    @Synchronized
    override fun predict(features: FloatArray): FloatArray {
        check(!closed) { "Calibration is closed" }
        val fitted = checkNotNull(model) { "Calibration is not fitted" }
        require(features.size == FEATURE_COUNT && features.all(Float::isFinite)) { "Expected finite 258-D features" }
        return FloatArray(2) { axis ->
            var value = fitted.labelMean[axis]
            for (j in features.indices) value += (features[j].toDouble() - fitted.featureMean[j]) * fitted.weights[j][axis]
            val result = value.toFloat()
            check(result.isFinite()) { "Prediction is not representable as a finite Float" }
            result
        }
    }

    @Synchronized
    override fun close() { model = null; closed = true }

    /** Bounded finite coefficients only; never serializes training rows. */
    @Synchronized
    fun encode(): ByteArray {
        check(!closed); require(lambda == 3.0)
        val fitted = checkNotNull(model)
        require(fitted.metadata.sampleCount in 1..720)
        val body = java.io.ByteArrayOutputStream().also { bytes -> java.io.DataOutputStream(bytes).use { out ->
            out.writeUTF(VERSION); out.writeDouble(lambda); out.writeInt(fitted.metadata.sampleCount)
            fitted.featureMean.forEach(out::writeDouble); fitted.labelMean.forEach(out::writeDouble)
            fitted.weights.forEach { it.forEach(out::writeDouble) }
        } }.toByteArray()
        return java.io.ByteArrayOutputStream().also { bytes -> java.io.DataOutputStream(bytes).use {
            it.writeUTF(CalibrationIdentity.hash(body)); it.write(body)
        } }.toByteArray()
    }
    companion object {
        const val FEATURE_COUNT = 258
        private const val VERSION = "newsmead_ridge_lambda3_v1"
        fun decode(bytes: ByteArray): RidgeCalibration {
            require(bytes.size in 1..8192)
            return java.io.DataInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
                val hash = input.readUTF(); val body = input.readBytes()
                require(CalibrationIdentity.hash(body) == hash)
                java.io.DataInputStream(java.io.ByteArrayInputStream(body)).use { data ->
                    require(data.readUTF() == VERSION && data.readDouble() == 3.0)
                    val count = data.readInt(); require(count in 1..720)
                    fun values(n: Int) = DoubleArray(n) { data.readDouble().also { require(it.isFinite()) } }
                    val means = values(FEATURE_COUNT); val labels = values(2)
                    require(labels.all { it in 0.0..1.0 })
                    val weights = Array(FEATURE_COUNT) { values(2) }; require(data.available() == 0)
                    RidgeCalibration().also { it.model = Model(means, labels, weights, FitMetadata(3.0, count)) }
                }
            }
        }
    }
}
