package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.FeatureQuality
import com.newsmead.gaze.mahalanobis.MahalanobisConfig
import com.newsmead.gaze.mahalanobis.RdfFeatureVector
import org.junit.Assert.*
import org.junit.Test

class ReadingFeaturePipelineTest {
    @Test fun delayedEventsUseCaptureTimeWhenRollingStartPassesThem() {
        val rolling = RollingReadingFeatures(MahalanobisConfig(windowMs = 1000))
        rolling.tick(0)
        rolling.tick(500)
        rolling.recordQuality(900, true)
        rolling.tick(1000)
        // These callbacks arrive after the tick, but their captures precede its time.
        rolling.regression(400)
        rolling.fixation(400)
        rolling.recordQuality(1400, true)
        val vector = rolling.tick(1500)!!
        assertEquals(500L, vector.windowStartMs)
        assertEquals(0.0, vector.values.regressionRatePerMinute, 1e-9)
        assertEquals(0.0, vector.values.fixationRatePerMinute, 1e-9)
    }

    @Test fun startupWithoutUsableTargetDoesNotCreateDwellBeforeFirstMappedLine() {
        val pipeline = ReadingFeaturePipeline(MahalanobisConfig(windowMs = 1000))
        pipeline.tick(0)
        for (time in 0L..1000L step 100L) {
            pipeline.sample(if (time < 500) null else 0, 20, time >= 500, time)
            if (time == 500L) pipeline.tick(time)
        }
        val vector = pipeline.tick(1000)!!
        assertEquals(FeatureQuality.MISSING, vector.quality)
        assertEquals(0.5, vector.values.dwellFraction, 1e-9)
        assertEquals(60.0, vector.values.fixationRatePerMinute, 1e-9)
    }

    @Test fun longDwellIsClippedToWindowAndIncludesOnlyObservedOngoingResidence() {
        val pipeline = ReadingFeaturePipeline()
        pipeline.tick(0)
        var ongoing: RdfFeatureVector? = null
        for (time in 0L..20_100L step 100L) {
            pipeline.sample(0, 20, true, time)
            if (time > 0 && time % 500 == 0L) ongoing = pipeline.tick(time)
        }
        assertEquals(1.0, ongoing!!.values.dwellFraction, 1e-9)
        pipeline.sample(null, 20, false, 20_200)
        val finalized = pipeline.tick(20_500)!!
        assertEquals(10_500L, finalized.windowStartMs)
        // Observed dwell ended at 20.1s. Only 9.6s overlaps the 10.5-20.5s window.
        assertEquals(0.96, finalized.values.dwellFraction, 1e-9)
        assertEquals(0.91, pipeline.tick(21_000)!!.values.dwellFraction, 1e-9)
    }

    @Test fun regressionUsesExistingConfirmedLineReturnEvent() {
        val pipeline = ReadingFeaturePipeline(MahalanobisConfig(windowMs = 1000))
        pipeline.tick(0)
        for (time in 0L..1000L step 100L) {
            val line = when { time < 200 -> 5; time < 1000 -> 2; else -> 3 }
            pipeline.sample(line, 20, true, time)
            if (time == 500L) pipeline.tick(time)
        }
        val vector = pipeline.tick(1000)!!
        assertEquals(FeatureQuality.VALID, vector.quality)
        assertEquals(60.0, vector.values.regressionRatePerMinute, 1e-9)
        assertEquals(1.0, vector.values.dwellFraction, 1e-9)
        assertEquals(120.0, vector.values.fixationRatePerMinute, 1e-9)
    }

    @Test fun mappedLineJitterUsesExistingLineConfirmationBeforeCreatingEvents() {
        val pipeline = ReadingFeaturePipeline(MahalanobisConfig(windowMs = 1000))
        pipeline.tick(0)
        for (time in 0L..1000L step 100L) {
            pipeline.sample((time / 100 % 2).toInt(), 20, true, time)
            if (time == 500L) pipeline.tick(time)
        }
        val vector = pipeline.tick(1000)!!
        assertEquals(FeatureQuality.VALID, vector.quality)
        assertEquals(0.0, vector.values.regressionRatePerMinute, 1e-9)
        assertEquals(1.0, vector.values.dwellFraction, 1e-9)
        assertEquals(60.0, vector.values.fixationRatePerMinute, 1e-9)
    }

    @Test fun fullWindowUsesExistingLineEventDefinitionsAndSharedDenominator() {
        val pipeline = ReadingFeaturePipeline()
        pipeline.tick(0)
        for (time in 0L..10_000L step 100L) {
            pipeline.sample((time / 1000).toInt(), 30, true, time)
            if (time > 0 && time % 500 == 0L) pipeline.tick(time)
        }
        // Get the next full rolling update; ten completed 1-second stays remain in the window.
        for (time in 10_100L..10_500L step 100L) pipeline.sample(10, 30, true, time)
        val vector = pipeline.tick(10_500)!!
        assertEquals(FeatureQuality.VALID, vector.quality)
        assertEquals(500L, vector.windowStartMs)
        assertEquals(10_500L, vector.windowEndMs)
        assertEquals(0.0, vector.values.regressionRatePerMinute, 1e-9)
        assertEquals(1.0, vector.values.dwellFraction, 1e-9)
        assertEquals(60.0, vector.values.fixationRatePerMinute, 1e-9)
    }

    @Test fun updateIntervalDoesNotBecomeFeatureWindow() {
        val pipeline = ReadingFeaturePipeline()
        pipeline.sample(0, 20, true, 0)
        assertEquals(FeatureQuality.INSUFFICIENT, pipeline.tick(0)!!.quality)
        pipeline.sample(0, 20, true, 500)
        val vector = pipeline.tick(500)!!
        assertEquals(0L, vector.windowStartMs)
        assertEquals(500L, vector.windowEndMs)
        assertEquals(FeatureQuality.INSUFFICIENT, vector.quality)
        assertNull(pipeline.tick(600))
    }

    @Test fun missingGazeDoesNotAccumulateUnobservedDwellOrRepeatedFlushes() {
        val pipeline = ReadingFeaturePipeline(MahalanobisConfig(windowMs = 2000))
        pipeline.tick(0)
        for (time in 0L..500L step 100L) pipeline.sample(3, 20, true, time)
        pipeline.sample(null, 20, false, 600)
        pipeline.sample(null, 20, false, 700)
        pipeline.tick(1000)
        pipeline.tick(1500)
        val vector = pipeline.tick(2000)!!
        assertEquals(FeatureQuality.MISSING, vector.quality)
        assertEquals(0.25, vector.values.dwellFraction, 1e-9)
        assertEquals(30.0, vector.values.fixationRatePerMinute, 1e-9)
    }

    @Test fun boundaryRestartsWindowAndDoesNotBridgeRegression() {
        val pipeline = ReadingFeaturePipeline(MahalanobisConfig(windowMs = 1000))
        pipeline.tick(0)
        pipeline.sample(8, 20, true, 0)
        pipeline.sample(8, 20, true, 400)
        pipeline.boundary()
        pipeline.sample(2, 20, true, 500)
        assertEquals(FeatureQuality.INSUFFICIENT, pipeline.tick(500)!!.quality)
        for (time in 600L..1500L step 100L) {
            pipeline.sample(2, 20, true, time)
            if (time == 1000L) pipeline.tick(time)
        }
        val vector = pipeline.tick(1500)!!
        assertEquals(0.0, vector.values.regressionRatePerMinute, 1e-9)
        assertEquals(FeatureQuality.VALID, vector.quality)
    }
}
