package com.newsmead.mgazenetbenchmark

import org.junit.Assert.*
import org.junit.Test

class RidgeCalibrationTest {
    private fun row(x: Float = 0f, y: Float = 0f) = FloatArray(258).apply { this[0] = x; this[1] = y }
    private val features get() = arrayOf(row(-1f), row(), row(1f))
    private val labels get() = arrayOf(floatArrayOf(.25f, .75f), floatArrayOf(.5f, .5f), floatArrayOf(.75f, .25f))

    @Test fun analyticalOneFeatureShrinkageAndUnclampedExtrapolation() {
        val ridge = RidgeCalibration(2.0)
        ridge.fit(features, labels)
        // Centered Sxx = 2, Sxy = +/-.5: slopes = +/- .125, intercepts = .5.
        assertArrayEquals(floatArrayOf(.75f, .25f), ridge.predict(row(2f)), 1e-6f)
        assertArrayEquals(floatArrayOf(1.75f, -.75f), ridge.predict(row(10f)), 1e-6f)
        assertTrue(ridge.trained)
        assertEquals(2.0, ridge.fitMetadata!!.lambda, 0.0)
        assertEquals(3, ridge.fitMetadata!!.sampleCount)
        assertEquals(258, ridge.fitMetadata!!.featureCount)
        assertEquals("normalized_screen_xy", ridge.fitMetadata!!.coordinateSpace)
    }

    @Test fun correlatedTwoFeatureAnalyticalSolutionEmbeddedIn258Dimensions() {
        val ridge = RidgeCalibration(2.0)
        ridge.fit(arrayOf(row(-1f, 0f), row(0f, -1f), row(1f, 1f)),
            arrayOf(floatArrayOf(.4f, .7f), floatArrayOf(.3f, .4f), floatArrayOf(.8f, .4f)))
        // Means = (.5,.5), A=[[4,1],[1,4]], inverse=[[4,-1],[-1,4]]/15.
        // X RHS=(.4,.5), Y RHS=(-.3,0), hence W_x=(1.1,1.6)/15, W_y=(-.08,.02).
        assertArrayEquals(floatArrayOf((.5 + (2 * 1.1 - 1.6) / 15).toFloat(), .32f),
            ridge.predict(row(2f, -1f)), 1e-6f)
    }

    @Test fun translatingFeaturesAndLabelsPreservesInterceptBehavior() {
        val base = RidgeCalibration(2.0)
        val shifted = RidgeCalibration(2.0)
        base.fit(features, labels)
        val translated = features.map { input -> FloatArray(258) { j -> input[j] + 16f } }.toTypedArray()
        val translatedLabels = labels.map { floatArrayOf(it[0] + .125f, it[1] - .125f) }.toTypedArray()
        shifted.fit(translated, translatedLabels)
        val prediction = base.predict(row(.5f))
        assertArrayEquals(floatArrayOf(prediction[0] + .125f, prediction[1] - .125f),
            shifted.predict(FloatArray(258) { if (it == 0) 16.5f else 16f }), 1e-6f)
    }

    @Test fun constantAndSingleRowFitsReturnUnpenalizedLabelMean() {
        val ridge = RidgeCalibration(1e6)
        ridge.fit(Array(3) { FloatArray(258) { 7f } }, labels)
        assertArrayEquals(floatArrayOf(.5f, .5f), ridge.predict(row(100f)), 1e-6f)
        ridge.fit(arrayOf(row(9f)), arrayOf(floatArrayOf(.2f, .8f)))
        assertArrayEquals(floatArrayOf(.2f, .8f), ridge.predict(row(-99f)), 1e-6f)
    }

    @Test fun duplicateFeaturesAreRegularizedWithoutSingularFailure() {
        val ridge = RidgeCalibration(2.0)
        ridge.fit(arrayOf(row(-1f, -1f), row(), row(1f, 1f)), labels)
        // Each slope is .5/(2+2+2)=1/12, so the combined slope is 1/6.
        assertArrayEquals(floatArrayOf(2f / 3f, 1f / 3f), ridge.predict(row(1f, 1f)), 1e-6f)
    }

    @Test fun lambdaUsesSumLossRatherThanMeanLoss() {
        val ridge = RidgeCalibration(2.0)
        ridge.fit(features + features, labels + labels)
        assertArrayEquals(floatArrayOf(2f / 3f, 1f / 3f), ridge.predict(row(1f)), 1e-6f)
    }

    @Test fun invalidConfigurationAndTrainingRowsAreRejectedAndClearFit() {
        for (lambda in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            expectFailure<IllegalArgumentException> { RidgeCalibration(lambda) }
        }
        val invalidFits = listOf<Pair<Array<FloatArray>, Array<FloatArray>>>(
            emptyArray<FloatArray>() to emptyArray(),
            features to arrayOf(floatArrayOf(.5f, .5f)),
            arrayOf(FloatArray(257)) to arrayOf(floatArrayOf(.5f, .5f)),
            arrayOf(FloatArray(259)) to arrayOf(floatArrayOf(.5f, .5f)),
            arrayOf(row(Float.NaN)) to arrayOf(floatArrayOf(.5f, .5f)),
            arrayOf(row(y = Float.NEGATIVE_INFINITY)) to arrayOf(floatArrayOf(.5f, .5f)),
            arrayOf(row()) to arrayOf(floatArrayOf(.5f)),
            arrayOf(row()) to arrayOf(floatArrayOf(.5f, .5f, .5f)),
            arrayOf(row()) to arrayOf(floatArrayOf(Float.NaN, .5f)),
            arrayOf(row()) to arrayOf(floatArrayOf(.5f, Float.POSITIVE_INFINITY)),
            arrayOf(row()) to arrayOf(floatArrayOf(-.01f, .5f)),
            arrayOf(row()) to arrayOf(floatArrayOf(.5f, 1.01f))
        )
        val ridge = RidgeCalibration()
        for ((x, y) in invalidFits) {
            ridge.fit(features, labels)
            expectFailure<IllegalArgumentException> { ridge.fit(x, y) }
            assertFalse(ridge.trained)
            assertNull(ridge.fitMetadata)
            expectFailure<IllegalStateException> { ridge.predict(row()) }
        }
    }

    @Test fun numericalFailureInvalidatesPriorFitWithoutAddingJitter() {
        val ridge = RidgeCalibration(Double.MIN_VALUE)
        val constantFeatures = Array(3) { row() }
        // The penalty is representable for this zero Gram matrix.
        ridge.fit(constantFeatures, labels)
        assertTrue(ridge.trained)
        // Lambda is below Double resolution beside Sxx=2; duplicate columns lose SPD.
        expectFailure<IllegalStateException> {
            ridge.fit(arrayOf(row(-1f, -1f), row(1f, 1f)),
                arrayOf(floatArrayOf(0f, 1f), floatArrayOf(1f, 0f)))
        }
        assertFalse(ridge.trained)
        assertNull(ridge.fitMetadata)
        expectFailure<IllegalStateException> { ridge.predict(row()) }
        ridge.fit(constantFeatures, labels)
        assertTrue(ridge.trained)
    }

    @Test fun rejectsPredictionThatOverflowsFloatOutput() {
        val ridge = RidgeCalibration(1e-100)
        ridge.fit(arrayOf(row(-Float.MIN_VALUE), row(Float.MIN_VALUE)),
            arrayOf(floatArrayOf(0f, 1f), floatArrayOf(1f, 0f)))
        expectFailure<IllegalStateException> { ridge.predict(row(Float.MAX_VALUE)) }
        assertTrue(ridge.trained)
    }

    @Test fun predictionValidationLifecycleAndInputOwnership() {
        val ridge = RidgeCalibration()
        expectFailure<IllegalStateException> { ridge.predict(row()) }
        val x = features
        val y = labels
        ridge.fit(x, y)
        val expected = ridge.predict(row(1f))
        x[0].fill(99f); y[0].fill(0f)
        assertArrayEquals(expected, ridge.predict(row(1f)), 0f)
        for (bad in listOf(FloatArray(257), FloatArray(259), row(Float.NaN), row(y = Float.POSITIVE_INFINITY))) {
            expectFailure<IllegalArgumentException> { ridge.predict(bad) }
            assertTrue(ridge.trained)
        }
        ridge.close(); ridge.close()
        assertFalse(ridge.trained)
        assertNull(ridge.fitMetadata)
        expectFailure<IllegalStateException> { ridge.predict(row()) }
        expectFailure<IllegalStateException> { ridge.fit(features, labels) }
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try { block() } catch (failure: Throwable) {
            if (failure is T) return
            throw AssertionError("Expected ${T::class.java.simpleName}, got $failure", failure)
        }
        fail("Expected ${T::class.java.simpleName}")
    }
}
