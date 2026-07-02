package com.newsmead.gaze

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression

/**
 * Affine mapping from the WiFi gaze feature (GazeFollower laptop-screen px) to
 * phone-screen px, fit by least squares over the calibration pairs:
 *
 *   screen_x = a0 + a1·gx + a2·gy
 *   screen_y = b0 + b1·gx + b2·gy
 *
 * The phone and laptop screens are near-coplanar, so an affine transform is the
 * correct model — it captures translation/scale/rotation/shear without the range
 * compression a regularized polynomial introduced on the weak (horizontal) axis.
 *
 * Ported verbatim (except package) from gaze-thesis-prototype.
 */
class GazeMapper(samples: List<CalibrationSample>) {

    private val betaX: DoubleArray
    private val betaY: DoubleArray

    init {
        require(samples.size >= MIN_SAMPLES) {
            "Need at least $MIN_SAMPLES calibration pairs, got ${samples.size}"
        }
        val features = Array(samples.size) {
            doubleArrayOf(samples[it].gazeX.toDouble(), samples[it].gazeY.toDouble())
        }
        val screenX = DoubleArray(samples.size) { samples[it].screenX.toDouble() }
        val screenY = DoubleArray(samples.size) { samples[it].screenY.toDouble() }
        betaX = fit(features, screenX)
        betaY = fit(features, screenY)
    }

    /** Map a gaze feature to an on-screen point in pixels. */
    fun map(gazeX: Float, gazeY: Float): FloatArray =
        floatArrayOf(evaluate(betaX, gazeX, gazeY), evaluate(betaY, gazeX, gazeY))

    private fun fit(features: Array<DoubleArray>, target: DoubleArray): DoubleArray {
        val regression = OLSMultipleLinearRegression()
        regression.newSampleData(target, features)
        return regression.estimateRegressionParameters() // [intercept, a1, a2]
    }

    private fun evaluate(beta: DoubleArray, gx: Float, gy: Float): Float =
        (beta[0] + beta[1] * gx + beta[2] * gy).toFloat()

    companion object {
        private const val MIN_SAMPLES = 3 // affine has 3 params per axis
    }
}
