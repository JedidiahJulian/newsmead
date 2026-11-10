package com.newsmead.gaze.trajectory

import com.newsmead.gaze.trajectory.CoordinateMahalanobis.Boundary
import com.newsmead.gaze.trajectory.CoordinateMahalanobis.Config
import com.newsmead.gaze.trajectory.CoordinateMahalanobis.Context
import com.newsmead.gaze.trajectory.CoordinateMahalanobis.CoordinateScorer
import com.newsmead.gaze.trajectory.CoordinateMahalanobis.LossRange
import com.newsmead.gaze.trajectory.CoordinateMahalanobis.Observation
import com.newsmead.gaze.trajectory.CoordinateMahalanobis.Profile
import com.newsmead.gaze.trajectory.CoordinateMahalanobis.Status
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

class CoordinateMahalanobisTest {
    private val core = CoordinateMahalanobis
    private val context = Context("p", "i", "c", "m", "a", "presentation", "elapsed", 1080, 2400)
    // Short synthetic fixture only. Production default retains 60 seconds of reference coverage.
    private val config = Config(minReferenceCoverageMs = 0.0)
    private fun o(id: Long, t: Double, x: Double = 200.0, y: Double = 200.0) =
        Observation(context, "provider", "stationary", id, t, t + 10, x, y)
    private fun reference() = listOf(o(1, 0.0, 100.0, 100.0), o(2, 100.0, 100.0, 300.0),
        o(3, 200.0, 300.0, 100.0), o(4, 300.0, 300.0, 300.0), o(5, 400.0, 100.0, 100.0))
    private fun fit(rows: List<Observation> = reference(), cfg: Config = config) =
        core.fitReference("ref", context, rows, 0.0, 400.0, cfg)
    private fun profile(): Profile = fit().profile!!
    private fun evaluation() = listOf(o(6, 600.0, 500.0, 600.0), o(7, 700.0), o(8, 800.0, 300.0, 300.0))
    private fun near(expected: Double, actual: Double?) = assertEquals(expected, actual!!, 1e-9)

    @Test fun sharedPythonKotlinFixture() {
        val relative = "tools/gaze/fixtures/coordinate_md_v1.json"
        val file = listOf(File(relative), File("../$relative")).firstOrNull { it.isFile }
            ?: error("Shared parity fixture missing from repository/app working directory")
        val doc = JSONObject(file.readText())
        assertEquals(CoordinateMahalanobis.INPUT_INSTRUMENT, doc.getString("instrument"))
        assertEquals(1, doc.getInt("schema_version"))
        assertTrue(doc.getBoolean("synthetic"))
        fun readContext(c: JSONObject) = Context(c.getString("participant_id"), c.getString("installation_id"),
            c.getString("calibration_id"), c.getString("model_id"), c.getString("acquisition_id"),
            c.getString("presentation_id"), c.getString("clock_domain_id"), c.getInt("screen_width_px"),
            c.getInt("screen_height_px"), c.optInt("rotation_degrees", 0),
            c.optString("coordinate_stream", CoordinateMahalanobis.RAW_STREAM))
        val ctx = readContext(doc.getJSONObject("context"))
        val c = doc.getJSONObject("config")
        val cfg = Config(c.optDouble("max_gap_ms", 250.0), c.optDouble("max_age_ms", 500.0),
            c.optInt("min_reference_points", 3), c.optDouble("min_reference_coverage_ms", 60000.0),
            c.optDouble("min_summary_coverage_fraction", 0.65), c.optDouble("relative_ridge", 0.0),
            c.optDouble("max_condition_number", 1e12))
        fun nullableNumber(o: JSONObject, key: String): Double? = if (o.isNull(key)) null else o.getDouble(key)
        fun rows(a: JSONArray) = (0 until a.length()).map { index ->
            val r = a.getJSONObject(index)
            Observation(if (r.has("context")) readContext(r.getJSONObject("context")) else ctx,
                r.getString("provider_epoch_id"), r.getString("context_epoch_id"), r.getLong("delivery_id"),
                nullableNumber(r, "capture_ms"), r.getDouble("delivery_ms"), nullableNumber(r, "x_px"),
                nullableNumber(r, "y_px"), r.optString("reason", "coordinate"),
                r.optBoolean("watchdog", false), r.optBoolean("context_allowed", true))
        }
        val boundaries = doc.getJSONArray("boundaries").let { a -> (0 until a.length()).map {
            val b = a.getJSONObject(it); Boundary(b.getDouble("at_ms"), b.getString("reason"))
        } }
        val losses = doc.getJSONArray("losses").let { a -> (0 until a.length()).map {
            val loss = a.getJSONObject(it)
            LossRange(nullableNumber(loss, "start_ms"), nullableNumber(loss, "end_ms"), loss.getString("reason"))
        } }
        val bounds = doc.getJSONObject("reference_bounds")
        val fit = core.fitReference(doc.getString("reference_id"), ctx, rows(doc.getJSONArray("reference_observations")),
            bounds.getDouble("start_ms"), bounds.getDouble("end_ms"), cfg, boundaries, losses)
        assertEquals(Status.READY, fit.status)
        val p = fit.profile!!
        val expected = doc.getJSONObject("expected")
        fun numbers(key: String, actual: List<Double>) {
            val values = expected.getJSONArray(key)
            assertEquals(values.length(), actual.size)
            actual.forEachIndexed { index, value -> near(values.getDouble(index), value) }
        }
        numbers("mean", listOf(p.mean.x, p.mean.y))
        numbers("covariance", listOf(p.covariance.xx, p.covariance.xy, p.covariance.yy))
        numbers("unweighted_sample_mean", listOf(p.unweightedSampleMean.x, p.unweightedSampleMean.y))
        numbers("unweighted_sample_covariance", listOf(p.unweightedSampleCovariance.xx,
            p.unweightedSampleCovariance.xy, p.unweightedSampleCovariance.yy))
        near(expected.getDouble("reference_covered_ms"), p.stats.coveredMs)
        near(expected.getDouble("reference_weight_squared_sum_ms2"), p.stats.weightSquaredSumMs2)
        val evaluation = rows(doc.getJSONArray("evaluation_observations"))
        val scores = core.scorePoints(p, ctx, evaluation)
        assertTrue(scores.all { it.status == Status.AVAILABLE })
        numbers("point_md2", scores.map { it.md2!! })
        val windows = doc.getJSONArray("windows")
        for (i in 0 until windows.length()) {
            val w = windows.getJSONObject(i)
            val summary = core.summarize(p, ctx, evaluation, w.getDouble("start_ms"), w.getDouble("end_ms"), boundaries, losses)
            assertEquals(Status.AVAILABLE, summary.status)
            near(expected.getDouble("mean_md2"), summary.meanMd2)
            near(expected.getDouble("coverage_fraction"), summary.coverageFraction)
        }
    }

    @Test fun handCalculatedReferenceAndFrozenScores() {
        val p = profile()
        near(200.0, p.mean.x); near(200.0, p.mean.y)
        near(10000.0, p.covariance.xx); near(0.0, p.covariance.xy); near(10000.0, p.covariance.yy)
        near(180.0, p.unweightedSampleMean.x); near(180.0, p.unweightedSampleMean.y)
        near(12000.0, p.unweightedSampleCovariance.xx)
        near(2000.0, p.unweightedSampleCovariance.xy); near(12000.0, p.unweightedSampleCovariance.yy)
        near(400.0, p.stats.coveredMs); near(35000.0, p.stats.weightSquaredSumMs2)
        assertEquals(4, p.stats.intervalCount)
        val scores = core.scorePoints(p, context, evaluation())
        listOf(25.0, 0.0, 2.0).zip(scores).forEach { (expected, s) ->
            assertEquals(Status.AVAILABLE, s.status); near(expected, s.md2)
        }
        val summary = core.summarize(p, context, evaluation(), 600.0, 800.0)
        assertEquals(Status.AVAILABLE, summary.status)
        near(6.75, summary.meanMd2); near(1.0, summary.coverageFraction)
        near(200.0, p.mean.x) // Scoring has not refitted the reference.
    }

    @Test fun defaultAdequacyAndNumericalGates() {
        assertEquals(Status.INSUFFICIENT_REFERENCE, fit(cfg = Config()).status)
        val zero = reference().map { it.copy(xPx = 10.0, yPx = 10.0) }
        assertEquals(Status.REFERENCE_DEGENERATE, fit(zero).status)
        assertEquals(Status.REFERENCE_DEGENERATE, fit(zero, config.copy(relativeRidge = 1e-6)).status)
        val line = reference().map { it.copy(yPx = it.xPx) }
        assertEquals(Status.REFERENCE_DEGENERATE, fit(line).status)
        val regularized = fit(line, config.copy(relativeRidge = 1e-6))
        assertEquals(Status.READY, regularized.status)
        assertTrue(regularized.profile!!.ridgeAddedPx2 > 0)
        val thin = reference().map { it.copy(yPx = it.yPx!! * 1e-5) }
        assertEquals(Status.REFERENCE_ILL_CONDITIONED, fit(thin, config.copy(maxConditionNumber = 1e6)).status)
        val overflow = reference().map { it.copy(xPx = if (it.deliveryId % 2L == 0L) 1e308 else -1e308) }
        assertEquals(Status.NUMERICAL_FAILURE, fit(overflow).status)
    }

    @Test fun referenceBoundsRejectCombinedReferenceAndEvaluationInput() {
        val rejected = fit(reference() + evaluation())
        assertEquals(Status.REFERENCE_BOUNDS_MISMATCH, rejected.status)
        assertNull(rejected.profile)
        val exact = fit()
        assertEquals(Status.READY, exact.status)
        assertTrue(core.scorePoints(exact.profile, context, evaluation()).all { it.status == Status.AVAILABLE })
        // A missing capture uses a finite delivery timestamp for range placement.
        val missingCapture = o(6, 500.0).copy(captureMs = null)
        assertEquals(Status.REFERENCE_BOUNDS_MISMATCH, fit(reference() + missingCapture).status)
        // Fully unplaceable rows retain their invalid status and break adjacency.
        val unplaceable = o(6, 500.0).copy(captureMs = null, deliveryMs = Double.NaN)
        val withInvalid = fit(reference() + unplaceable)
        assertEquals(Status.READY, withInvalid.status)
        assertTrue(withInvalid.stats.reasons.contains(Status.INVALID_CLOCK.name to 1))
    }

    @Test fun deliveryCaptureAndMissingInputsRemainExplicit() {
        val scorer = CoordinateScorer(profile(), context)
        assertEquals(Status.NON_INCREASING_DELIVERY, scorer.score(o(5, 600.0)).status)
        assertEquals(Status.INVALID_CLOCK, scorer.score(o(6, 600.0).copy(captureMs = null)).status)
        assertEquals(Status.INVALID_CLOCK, scorer.score(o(7, 600.0).copy(deliveryMs = 599.0)).status)
        assertEquals(Status.WATCHDOG, scorer.score(o(8, 600.0).copy(watchdog = true)).status)
        assertEquals(Status.SOURCE_UNAVAILABLE, scorer.score(o(9, 600.0).copy(deliveryMs = 1101.0)).status)
        assertEquals(Status.NON_MONOTONIC_CAPTURE, scorer.score(o(10, 600.0)).status)
        assertEquals(Status.NONFINITE_COORDINATE, scorer.score(o(11, 700.0).copy(xPx = null)).status)
        assertEquals(Status.NONFINITE_COORDINATE, scorer.score(o(12, 800.0).copy(yPx = Double.NaN)).status)
        assertEquals(Status.CONTEXT_EXCLUDED, scorer.score(o(13, 900.0).copy(contextAllowed = false)).status)
        assertEquals(Status.SOURCE_UNAVAILABLE, scorer.score(o(14, 1000.0).copy(reason = "blink")).status)
        assertEquals(Status.NON_MONOTONIC_CAPTURE, scorer.score(o(15, 1000.0)).status)
        val available = scorer.score(o(16, 1100.0))
        assertEquals(Status.AVAILABLE, available.status)
        assertEquals(Status.NON_INCREASING_DELIVERY, scorer.score(o(16, 1200.0)).status)
        assertNull(CoordinateScorer(null, context).score(o(1, 100.0)).md2)
    }

    @Test fun invalidRowsBreakAdjacencyEvenWithNullClock() {
        val rows = evaluation().toMutableList()
        rows[1] = rows[1].copy(captureMs = null)
        val summary = core.summarize(profile(), context, rows, 600.0, 800.0)
        assertEquals(Status.NO_OBSERVED_INTERVALS, summary.status)
        assertEquals(3, summary.stats.receivedCount)
        near(0.0, summary.stats.coveredMs)
        assertNull(summary.meanMd2)
        val duplicate = listOf(o(6, 600.0), o(6, 650.0), o(7, 700.0))
        assertEquals(Status.NO_OBSERVED_INTERVALS,
            core.summarize(profile(), context, duplicate, 600.0, 700.0).status)
    }

    @Test fun invalidSourceClocksCannotPoisonCaptureRecovery() {
        for (reason in listOf("invalid_clock", "no_face")) {
            val scorer = CoordinateScorer(profile(), context)
            val good = o(6, 600.0)
            val bad = o(7, 1e9).copy(deliveryMs = 650.0, reason = reason)
            val recovered = o(8, 700.0)
            assertEquals(Status.AVAILABLE, scorer.score(good).status)
            assertEquals(Status.INVALID_CLOCK, scorer.score(bad).status)
            assertEquals(Status.AVAILABLE, scorer.score(recovered).status)
            // An invalid capture remains a break even when its timestamp lies outside the window.
            val summary = core.summarize(profile(), context, listOf(good, bad, recovered), 600.0, 700.0)
            assertEquals(Status.NO_OBSERVED_INTERVALS, summary.status)
            near(0.0, summary.stats.coveredMs)
        }
    }

    @Test fun correlatedCovarianceUsesFullCholeskySolve() {
        val transformed = reference().map { it.copy(yPx = it.xPx!! + it.yPx!!) }
        val fit = fit(transformed)
        assertEquals(Status.READY, fit.status)
        val p = fit.profile!!
        near(10000.0, p.covariance.xx)
        near(10000.0, p.covariance.xy)
        near(20000.0, p.covariance.yy)
        val score = CoordinateScorer(p, context).score(o(6, 600.0, 500.0, 1100.0))
        assertEquals(Status.AVAILABLE, score.status)
        near(25.0, score.md2)
    }

    @Test fun contextAndStreamMustMatchExactly() {
        val p = profile()
        val contexts = listOf(context.copy(participantId = "other"), context.copy(screenWidthPx = 720),
            context.copy(rotationDegrees = 90), context.copy(coordinateStream = CoordinateMahalanobis.SELECTED_STREAM))
        contexts.forEach { other ->
            assertEquals(Status.REFERENCE_MISMATCH, core.fitReference("ref", context,
                reference().map { it.copy(context = other) }, 0.0, 400.0, config).status)
            assertEquals(Status.CONTEXT_MISMATCH, CoordinateScorer(p, context).score(o(6, 600.0).copy(context = other)).status)
            assertEquals(Status.REFERENCE_MISMATCH, CoordinateScorer(p, other).score(o(6, 600.0).copy(context = other)).status)
        }
        val rows = evaluation().mapIndexed { index, obs -> if (index == 1) obs.copy(contextEpochId = "scroll") else obs }
        assertEquals(Status.CONTEXT_DISCONTINUITY, core.summarize(p, context, rows, 600.0, 800.0).status)
        assertEquals(Status.CONTEXT_DISCONTINUITY, core.summarize(p, context, evaluation(), 600.0, 800.0,
            boundaries = listOf(Boundary(700.0, "reflow"))).status)
    }

    @Test fun boundedWindowsNoEdgeExtensionOrFutureAndLossCensoring() {
        val p = profile()
        assertEquals(Status.REFERENCE_PERIOD, CoordinateScorer(p, context).score(o(6, 400.0)).status)
        val summary = core.summarize(p, context, evaluation(), 650.0, 750.0)
        assertEquals(Status.NO_OBSERVED_INTERVALS, summary.status)
        assertEquals(1, summary.stats.receivedCount)
        val sparse = listOf(o(6, 600.0), o(7, 900.0))
        assertEquals(Status.NO_OBSERVED_INTERVALS, core.summarize(p, context, sparse, 600.0, 900.0).status)
        val short = core.summarize(p, context, evaluation(), 500.0, 1000.0)
        assertEquals(Status.INSUFFICIENT_COVERAGE, short.status); near(0.4, short.coverageFraction)
        val loss = listOf(LossRange(null, 700.0, "recorder"))
        val lost = core.summarize(p, context, evaluation(), 600.0, 800.0, losses = loss)
        assertEquals(Status.RECORDING_LOSS, lost.status); assertNull(lost.meanMd2)
        assertEquals(Status.RECORDING_LOSS, core.fitReference("ref", context, reference(), 0.0, 400.0,
            config, losses = loss).status)
        // Boundary at the left edge does not split an otherwise stationary window.
        assertEquals(Status.AVAILABLE, core.summarize(p, context, evaluation(), 600.0, 800.0,
            boundaries = listOf(Boundary(600.0, "start"))).status)
    }

    @Test fun commonTranslationAndUniformScalePreserveDistance() {
        val original = core.scorePoints(profile(), context, evaluation())
        for (scale in listOf(0.001, 10.0, -2.0)) {
            fun transform(o: Observation) = o.copy(xPx = o.xPx!! * scale + 12345, yPx = o.yPx!! * scale - 54321)
            val p = fit(reference().map(::transform)).profile!!
            val transformed = core.scorePoints(p, context, evaluation().map(::transform))
            original.zip(transformed).forEach { (a, b) -> assertEquals(a.md2!!, b.md2!!, 1e-6) }
        }
    }

    @Test fun invalidBoundsAndConfigurationRejected() {
        assertThrows(IllegalArgumentException::class.java) { Config(relativeRidge = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Config(minReferencePoints = 2) }
        assertThrows(IllegalArgumentException::class.java) { context.copy(rotationDegrees = 45) }
        assertThrows(IllegalArgumentException::class.java) { LossRange(20.0, 10.0, "loss") }
        assertThrows(IllegalArgumentException::class.java) { core.summarize(profile(), context, evaluation(), 800.0, 800.0) }
    }
}
