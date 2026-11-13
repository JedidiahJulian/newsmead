package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test

class PipelinePerformanceMetricsTest {
    private fun record(metrics: PipelinePerformanceMetrics, reason: String, inference: Double) {
        metrics.processed(reason, 1.0, 2.0, 3.0, 4.0, inference, 6.0, 100.0)
    }

    @Test fun mixedWindowsKeepInferenceDenominatorsSeparateAndResetCounts() {
        val metrics = PipelinePerformanceMetrics()
        assertNull(metrics.arrival(0, false))
        record(metrics, "features", 80.0)
        record(metrics, "features", 100.0)
        repeat(8) { record(metrics, "no_face", 0.0) }
        record(metrics, "invalid_crops", 0.0)
        metrics.delivered("features", 120.0)
        metrics.delivered("no_face", 80.0)
        val first = metrics.arrival(2_000_000_000, true)!!
        assertTrue(first.contains("samples=11 processed_hz=5.50 results=2 result_hz=1.00"))
        assertTrue(first.contains("arrivals=2 early_busy_drops=1 late_busy_drops=0"))
        assertTrue(first.contains("face_features processed=2"))
        assertTrue(first.contains("inference=90.0"))
        assertTrue(first.contains("delivery_age_ms=mean=120.0,max=120.0"))
        assertTrue(first.contains("face_present_invalid_crops processed=1"))
        assertTrue(first.contains("no_face processed=8"))
        assertTrue(first.contains("inference=not_run"))
        val second = metrics.arrival(4_000_000_000, false)!!
        assertTrue(second.contains("samples=0 processed_hz=0.00"))
        assertTrue(second.contains("arrivals=1 early_busy_drops=0"))
        assertTrue(second.contains("arrivals_total=3 drops_total=1"))
        assertTrue(second.contains("avg_ms=unavailable"))
    }

    @Test fun delayedDeliveryIsCountedInItsOwnEventWindow() {
        val metrics = PipelinePerformanceMetrics()
        metrics.arrival(10, false)
        record(metrics, "features", 80.0)
        val first = metrics.arrival(2_000_000_010, false)!!
        assertTrue(first.contains("samples=1 processed_hz=0.50 results=0 result_hz=0.00"))
        metrics.delivered("features", 250.0)
        val second = metrics.arrival(4_000_000_010, false)!!
        assertTrue(second.contains("samples=0 processed_hz=0.00 results=1 result_hz=0.50"))
        assertTrue(second.contains("delivery_age_ms=mean=250.0,max=250.0"))
        assertFalse(second.contains("NaN"))
        assertFalse(second.contains("Infinity"))
    }

    @Test fun busyOnlyWindowStillReportsArrivalsAndDrops() {
        val metrics = PipelinePerformanceMetrics()
        metrics.arrival(0, true)
        val window = metrics.arrival(3_000_000_000, true)!!
        assertTrue(window.contains("window_s=3.000"))
        assertTrue(window.contains("samples=0"))
        assertTrue(window.contains("arrivals=2 early_busy_drops=2"))
    }

    @Test fun deliveryAgeRequiresFiniteNonnegativeElapsedTime() {
        val metrics = PipelinePerformanceMetrics()
        assertThrows(IllegalArgumentException::class.java) { metrics.delivered("features", -1.0) }
        assertThrows(IllegalArgumentException::class.java) { metrics.delivered("features", Double.NaN) }
    }

    @Test fun finalPartialWindowIncludesTheTailWithoutZeroDivision() {
        val metrics = PipelinePerformanceMetrics()
        assertNull(metrics.finish(0))
        metrics.arrival(10, false)
        assertNull(metrics.finish(10))
        record(metrics, "no_face", 0.0)
        metrics.delivered("no_face", 90.0)
        val tail = metrics.finish(500_000_010)!!
        assertTrue(tail.contains("window_s=0.500 samples=1 processed_hz=2.00 results=1 result_hz=2.00"))
    }

    @Test fun admissionHoldsBothSlotsUntilTheOwnerReleasesThem() {
        val admission = BoundedFrameAdmission(2)
        var expensiveCalls = 0
        fun admit(): Boolean {
            if (!admission.tryAcquire()) return false
            expensiveCalls++
            return true
        }
        assertTrue(admit())
        assertTrue(admit())
        repeat(1000) { assertFalse(admit()) }
        assertEquals(2, expensiveCalls)
        admission.release()
        assertTrue(admit())
        assertFalse(admit())
        admission.release()
        admission.release()
        assertTrue(admit())
        assertTrue(admit())
        assertFalse(admit())
    }

    @Test fun runtimeReportsDiagnosticThreadCountAndDefaultRemainsFour() {
        assertEquals(4, MnnEstimator.DEFAULT_THREADS)
        assertEquals(MnnEstimator.RUNTIME, MnnEstimator.runtimeForThreads(MnnEstimator.DEFAULT_THREADS))
        listOf(1, 2, 4).forEach { assertTrue(MnnEstimator.runtimeForThreads(it).contains("$it threads")) }
    }
}
