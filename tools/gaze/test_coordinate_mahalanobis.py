"""Synthetic numerical/contract checks; no participant-validity claims."""
from dataclasses import replace
import json
import math
from pathlib import Path
import unittest

from coordinate_mahalanobis import (
    Boundary, Config, Context, CoordinateScorer, INPUT_INSTRUMENT, LossRange,
    Observation, SELECTED_STREAM, Status, analyze_document, fit_reference,
    json_value, score_points, summarize,
)

CONTEXT = Context("synthetic", "installation", "calibration", "model", "acquisition",
                  "presentation", "elapsed-clock", 1080, 1920)
CONFIG = Config(min_reference_coverage_ms=0)


def point(t, x=200, y=200, delivery_id=None, **changes):
    return replace(Observation(CONTEXT, "provider", "stationary", delivery_id or int(t + 1),
                               t, t + 10, x, y), **changes)


def reference(config=CONFIG, transform=lambda x, y: (x, y)):
    xy = [(100, 100), (100, 300), (300, 100), (300, 300), (100, 100)]
    rows = [point(i * 100, *transform(x, y), delivery_id=i + 1)
            for i, (x, y) in enumerate(xy)]
    return fit_reference("synthetic-reference", CONTEXT, rows, 0, 400, config)


class CoordinateMahalanobisTest(unittest.TestCase):
    def setUp(self):
        self.fit = reference()
        self.profile = self.fit.profile
        self.evaluation = [point(600, 500, 600, delivery_id=6),
                           point(700, 200, 200, delivery_id=7),
                           point(800, 300, 300, delivery_id=8)]

    def test_hand_calculated_reference_and_distances(self):
        p = self.profile
        self.assertEqual(Status.READY, self.fit.status)
        self.assertEqual((200, 200), (p.mean.x, p.mean.y))
        self.assertEqual((10000, 0, 10000), (p.covariance.xx, p.covariance.xy, p.covariance.yy))
        self.assertEqual((180, 180), (p.unweighted_sample_mean.x, p.unweighted_sample_mean.y))
        for expected, actual in zip((12000, 2000, 12000), (p.unweighted_sample_covariance.xx,
                                    p.unweighted_sample_covariance.xy, p.unweighted_sample_covariance.yy)):
            self.assertAlmostEqual(expected, actual)
        self.assertEqual(400, p.stats.covered_ms)
        self.assertEqual(35000, p.stats.weight_squared_sum_ms2)
        scores = score_points(p, CONTEXT, self.evaluation)
        for s, md2 in zip(scores, [25, 0, 2]):
            self.assertEqual(Status.AVAILABLE, s.status)
            self.assertAlmostEqual(md2, s.md2)
        self.assertEqual(5, scores[0].md)
        self.assertEqual((300, 400), (scores[0].delta_px.x, scores[0].delta_px.y))

    def test_summary_hand_calculation_and_explicit_bounds(self):
        s = summarize(self.profile, CONTEXT, self.evaluation, 600, 800)
        self.assertEqual(Status.AVAILABLE, s.status)
        self.assertAlmostEqual(6.75, s.mean_md2)
        self.assertEqual(1, s.coverage_fraction)
        self.assertEqual(200, s.stats.covered_ms)
        # Endpoints outside the window cannot extend its observed time.
        s = summarize(self.profile, CONTEXT, self.evaluation, 650, 750)
        self.assertEqual(Status.NO_OBSERVED_INTERVALS, s.status)
        self.assertIsNone(s.mean_md2)

    def test_translation_and_uniform_scale_with_matching_reference(self):
        for scale, ox, oy in [(1, 1000, -700), (0.01, 0, 0), (100, 50, 90)]:
            transform = lambda x, y: (x * scale + ox, y * scale + oy)
            p = reference(transform=transform).profile
            rows = [replace(o, x_px=transform(o.x_px, o.y_px)[0],
                            y_px=transform(o.x_px, o.y_px)[1]) for o in self.evaluation]
            self.assertAlmostEqual(25, score_points(p, CONTEXT, rows)[0].md2, places=9)

    def test_fixed_reference_responds_to_shift_and_gain(self):
        center, shifted, wider = [point(600, 200, 200), point(700, 400, 200), point(800, 600, 600)]
        scores = score_points(self.profile, CONTEXT, [center, shifted, wider])
        self.assertEqual([0, 4, 32], [s.md2 for s in scores])
        self.assertEqual(200, self.profile.mean.x)  # evaluation cannot refit

    def test_correlated_covariance_cholesky_solve(self):
        p = reference(transform=lambda x, y: (x, x + y)).profile
        for expected, actual in zip((10000, 10000, 20000),
                                    (p.covariance.xx, p.covariance.xy, p.covariance.yy)):
            self.assertAlmostEqual(expected, actual)
        s = score_points(p, CONTEXT, [point(600, 500, 1100)])[0]
        self.assertAlmostEqual(25, s.md2)

    def test_default_reference_duration_is_a_gate(self):
        fit = reference(Config())
        self.assertEqual(Status.INSUFFICIENT_REFERENCE, fit.status)
        self.assertIsNone(fit.profile)
        self.assertEqual(400, fit.stats.covered_ms)

    def test_degenerate_reference_and_explicit_ridge(self):
        rows = [point(t, t, t) for t in range(0, 401, 100)]
        fit = fit_reference("line", CONTEXT, rows, 0, 400, CONFIG)
        self.assertEqual(Status.REFERENCE_DEGENERATE, fit.status)
        ridge = fit_reference("line-ridge", CONTEXT, rows, 0, 400,
                              replace(CONFIG, relative_ridge=1e-6))
        self.assertEqual(Status.READY, ridge.status)
        self.assertGreater(ridge.profile.ridge_added_px2, 0)
        zero = [point(t, 5, 5) for t in range(0, 401, 100)]
        self.assertEqual(Status.REFERENCE_DEGENERATE,
                         fit_reference("zero", CONTEXT, zero, 0, 400,
                                       replace(CONFIG, relative_ridge=1e-6)).status)
        p = reference(replace(CONFIG, relative_ridge=1e-6)).profile
        self.assertAlmostEqual(25 / (1 + 1e-6), score_points(p, CONTEXT, self.evaluation)[0].md2)

    def test_conditioning_guard_and_large_finite_points(self):
        fit = reference(transform=lambda x, y: (x, y * 1e-7))
        self.assertEqual(Status.REFERENCE_ILL_CONDITIONED, fit.status)
        s = score_points(self.profile, CONTEXT, [point(600, -1e8, 1e8)])[0]
        self.assertEqual(Status.AVAILABLE, s.status)  # no screen clipping/MD rejection
        self.assertGreater(s.md2, 1e10)
        s = score_points(self.profile, CONTEXT, [point(600, 1e308, 1e308)])[0]
        self.assertEqual(Status.NUMERICAL_FAILURE, s.status)
        self.assertIsNone(s.md2)

    def test_operational_statuses_keep_missing_values_missing(self):
        variants = [({"capture_ms": None}, Status.INVALID_CLOCK),
                    ({"capture_ms": math.nan}, Status.INVALID_CLOCK),
                    ({"delivery_ms": 599}, Status.INVALID_CLOCK),
                    ({"delivery_ms": 1101}, Status.SOURCE_UNAVAILABLE),
                    ({"reason": "no_face"}, Status.SOURCE_UNAVAILABLE),
                    ({"watchdog": True}, Status.WATCHDOG),
                    ({"context_allowed": False}, Status.CONTEXT_EXCLUDED),
                    ({"x_px": None}, Status.NONFINITE_COORDINATE),
                    ({"y_px": math.inf}, Status.NONFINITE_COORDINATE)]
        for changes, expected in variants:
            with self.subTest(changes=changes):
                s = score_points(self.profile, CONTEXT, [point(600, **changes)])[0]
                self.assertEqual(expected, s.status)
                self.assertIsNone(s.md2)
                self.assertIsNone(s.md)

    def test_duplicate_delivery_and_capture_do_not_add_exposure(self):
        scorer = CoordinateScorer(self.profile, CONTEXT)
        self.assertEqual(Status.AVAILABLE, scorer.score(point(600, delivery_id=6)).status)
        self.assertEqual(Status.NON_INCREASING_DELIVERY,
                         scorer.score(point(700, delivery_id=6)).status)
        self.assertEqual(Status.NON_MONOTONIC_CAPTURE,
                         scorer.score(point(600, delivery_id=7)).status)
        self.assertEqual(Status.NON_MONOTONIC_CAPTURE,
                         scorer.score(point(500, delivery_id=8, provider_epoch_id="new")).status)

    def test_reference_data_cannot_be_evaluation(self):
        self.assertEqual(Status.NON_INCREASING_DELIVERY,
                         score_points(self.profile, CONTEXT, [point(300, delivery_id=4)])[0].status)
        self.assertEqual(Status.REFERENCE_PERIOD,
                         score_points(self.profile, CONTEXT, [point(400, provider_epoch_id="new")])[0].status)
        self.assertEqual(Status.REFERENCE_UNAVAILABLE,
                         score_points(None, CONTEXT, [point(600)])[0].status)

    def test_reference_rejects_out_of_bounds_input(self):
        rows = [point(t, t, t % 300) for t in range(0, 401, 100)] + self.evaluation
        fit = fit_reference("not-segmented", CONTEXT, rows, 0, 400, CONFIG)
        self.assertEqual(Status.REFERENCE_BOUNDS_MISMATCH, fit.status)
        self.assertIsNone(fit.profile)
        self.assertEqual(Status.READY, reference().status)
        self.assertEqual(Status.AVAILABLE, score_points(self.profile, CONTEXT, self.evaluation)[0].status)

    def test_bad_source_clock_does_not_poison_future_capture(self):
        for reason in ("invalid_clock", "no_face"):
            scorer = CoordinateScorer(self.profile, CONTEXT)
            self.assertEqual(Status.AVAILABLE, scorer.score(point(600, delivery_id=6)).status)
            bad = point(1e9, delivery_id=7, delivery_ms=650, reason=reason)
            self.assertEqual(Status.INVALID_CLOCK, scorer.score(bad).status)
            self.assertEqual(Status.AVAILABLE, scorer.score(point(700, delivery_id=8)).status)

    def test_all_context_fields_are_compatibility_keys(self):
        for name, value in [("participant_id", "other"), ("calibration_id", "other"),
                            ("model_id", "other"), ("acquisition_id", "other"),
                            ("presentation_id", "other"), ("clock_domain_id", "other"),
                            ("installation_id", "other"), ("screen_width_px", 720),
                            ("rotation_degrees", 90), ("coordinate_stream", SELECTED_STREAM)]:
            other = replace(CONTEXT, **{name: value})
            with self.subTest(field=name):
                self.assertEqual(Status.CONTEXT_MISMATCH, score_points(self.profile, CONTEXT,
                                 [point(600, context=other)])[0].status)
                self.assertEqual(Status.REFERENCE_MISMATCH,
                                 score_points(self.profile, other, [point(600, context=other)])[0].status)

    def test_invalid_clock_row_breaks_adjacency(self):
        rows = [point(600), point(650, capture_ms=None), point(700)]
        s = summarize(self.profile, CONTEXT, rows, 600, 700)
        self.assertEqual(Status.NO_OBSERVED_INTERVALS, s.status)
        self.assertEqual(3, s.stats.received_count)
        self.assertEqual(0, s.stats.covered_ms)
        # A source-invalid point with a capture also breaks both adjoining edges.
        rows[1] = point(650, reason="no_face")
        self.assertEqual(0, summarize(self.profile, CONTEXT, rows, 600, 700).stats.covered_ms)

    def test_gap_and_coverage_denominators(self):
        rows = [point(600), point(700), point(1100), point(1200)]
        s = summarize(self.profile, CONTEXT, rows, 600, 1200)
        self.assertEqual(Status.INSUFFICIENT_COVERAGE, s.status)
        self.assertEqual(200, s.stats.covered_ms)
        self.assertAlmostEqual(1 / 3, s.coverage_fraction)
        self.assertIsNone(s.mean_md2)
        self.assertEqual(Status.NO_OBSERVED_INTERVALS,
                         summarize(self.profile, CONTEXT, [point(600), point(851)], 600, 851).status)
        self.assertEqual(Status.AVAILABLE,
                         summarize(self.profile, CONTEXT, [point(600), point(850)], 600, 850).status)

    def test_context_boundaries_and_provider_changes(self):
        for rows, boundaries in [
            (self.evaluation, [Boundary(650, "scroll")]),
            ([self.evaluation[0], replace(self.evaluation[1], context_epoch_id="new")], []),
            ([self.evaluation[0], replace(self.evaluation[1], provider_epoch_id="new")], []),
        ]:
            s = summarize(self.profile, CONTEXT, rows, 600, 800, boundaries)
            self.assertEqual(Status.CONTEXT_DISCONTINUITY, s.status)
            self.assertIsNone(s.mean_md2)
        # A boundary at the start does not straddle the window.
        self.assertEqual(Status.AVAILABLE, summarize(self.profile, CONTEXT, self.evaluation,
                         600, 800, [Boundary(600, "settled")]).status)

    def test_loss_ranges_censor_fit_or_summary_but_not_points(self):
        losses = [LossRange(650, 660, "queue_overflow")]
        self.assertEqual(Status.RECORDING_LOSS,
                         summarize(self.profile, CONTEXT, self.evaluation, 600, 800, losses=losses).status)
        self.assertEqual(Status.AVAILABLE, score_points(self.profile, CONTEXT, self.evaluation)[0].status)
        rows = [point(t, t, (t % 300)) for t in range(0, 401, 100)]
        self.assertEqual(Status.RECORDING_LOSS, fit_reference("loss", CONTEXT, rows, 0, 400,
                         CONFIG, losses=[LossRange(None, None, "unknown loss")]).status)

    def test_sample_density_sensitivity_is_retained(self):
        # Unequal exposure changes temporal vs sample-weighted moments.
        rows = [point(0, 0, 0), point(10, 0, 10), point(100, 10, 0), point(200, 10, 10)]
        p = fit_reference("irregular", CONTEXT, rows, 0, 200, CONFIG).profile
        self.assertAlmostEqual(7.25, p.mean.x)
        self.assertAlmostEqual(5, p.unweighted_sample_mean.x)
        self.assertEqual(200, p.stats.covered_ms)

    def test_strict_json_fixture(self):
        fixture = Path(__file__).parent / "fixtures" / "coordinate_md_v1.json"
        doc = json.loads(fixture.read_text(encoding="utf-8"))
        actual = analyze_document(doc)
        self.assertEqual("READY", actual["fit"]["status"])
        for expected, score in zip(doc["expected"]["point_md2"], actual["points"]):
            self.assertAlmostEqual(expected, score["md2"])
        self.assertAlmostEqual(doc["expected"]["mean_md2"], actual["summaries"][0]["result"]["mean_md2"])
        json.dumps(actual, allow_nan=False)
        self.assertIsNone(json_value(math.nan))
        with self.assertRaises(ValueError):
            analyze_document({"instrument": "legacy_log", "schema_version": 1})

    def test_bad_configuration_and_bounds_fail_explicitly(self):
        for args in [{"max_gap_ms": 0}, {"relative_ridge": -1}, {"max_age_ms": math.inf},
                     {"min_reference_points": 2}, {"min_summary_coverage_fraction": 1.1}]:
            with self.assertRaises(ValueError):
                Config(**args)
        with self.assertRaises(ValueError):
            summarize(self.profile, CONTEXT, self.evaluation, 800, 600)


if __name__ == "__main__":
    unittest.main()
