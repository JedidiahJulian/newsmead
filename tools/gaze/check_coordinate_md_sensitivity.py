"""Reproducible synthetic perturbations, not measured phone errors or validation.

Run: python tools/gaze/check_coordinate_md_sensitivity.py --output <receipt.json>
Only mathematical/counter invariants are asserted. No method must 'win'.
"""
from dataclasses import replace
import argparse
import json
import math
from pathlib import Path
import random

from coordinate_mahalanobis import (Config, Context, Observation, Status, fit_reference,
                                    json_value, score_points, summarize)


def run():
    context = Context("synthetic", "synthetic", "synthetic", "synthetic", "10hz-reference",
                      "stationary-screen", "synthetic-elapsed", 1080, 1920)
    config = Config()
    def position(t):
        return (540 + 150 * math.cos(2 * math.pi * t / 2000),
                960 + 240 * math.sin(2 * math.pi * t / 3000))
    def observation(t, identity, xy=None, reason="coordinate"):
        x, y = xy if xy is not None else position(t)
        return Observation(context, "synthetic-provider", "stationary", identity, t, t + 10,
                           x, y, reason)
    reference = [observation(t, i + 1) for i, t in enumerate(range(0, 60001, 100))]
    fit = fit_reference("frozen-60s-synthetic", context, reference, 0, 60000, config)
    assert fit.status == Status.READY
    profile = fit.profile
    cases = []
    for cadence in (6, 10, 15, 30, "irregular10"):
        hz = 10 if cadence == "irregular10" else cadence
        count = hz * 20
        times = [61000 + 20000 * i / count for i in range(count + 1)]
        if cadence == "irregular10":
            rng = random.Random(614)
            times = [t + rng.uniform(-30, 30) if i not in (0, count) else t
                     for i, t in enumerate(times)]
        for perturbation in ("none", "offset_x_75px", "jitter_sd_30px", "one_spike_900px",
                             "drift_x_0_to_150px", "gain_1.25", "dropout_3s"):
            rng = random.Random(42)
            rows = []
            for i, t in enumerate(times):
                x, y = position(t)
                reason = "coordinate"
                if perturbation == "offset_x_75px":
                    x += 75
                elif perturbation == "jitter_sd_30px":
                    x += rng.gauss(0, 30)
                    y += rng.gauss(0, 30)
                elif perturbation == "one_spike_900px" and i == count // 2:
                    x += 900
                elif perturbation == "drift_x_0_to_150px":
                    x += 150 * (t - 61000) / 20000
                elif perturbation == "gain_1.25":
                    x, y = 540 + 1.25 * (x - 540), 960 + 1.25 * (y - 960)
                elif perturbation == "dropout_3s" and 69000 <= t <= 72000:
                    reason = "no_face"  # preserve the point but exclude exposure
                rows.append(observation(t, 1000 + i, (x, y), reason))
            scores = score_points(profile, context, rows)
            summary = summarize(profile, context, rows, 61000, 81000)
            available = [s.md2 for s in scores if s.status == Status.AVAILABLE]
            assert summary.status == Status.AVAILABLE
            assert 0 <= summary.coverage_fraction <= 1 + 1e-12
            cases.append({"cadence_hz": cadence, "perturbation": perturbation,
                          "input_points": len(rows), "available_points": len(available),
                          "sample_mean_md2": math.fsum(available) / len(available),
                          "summary": json_value(summary)})
    # A four-point, n-1 covariance calculation independent of implementation.
    # Self-fitting hides both a translation and a uniform scale change.
    squares = [(0.4, 0.4), (0.4, 0.6), (0.6, 0.4), (0.6, 0.6)]
    variance = sum((x - .5) ** 2 for x, y in squares) / 3
    def fixed_mean_md2(cloud):
        return sum(((x - .5) ** 2 + (y - .5) ** 2) / variance for x, y in cloud) / 4
    def own_mean_md2(cloud, population=False):
        mx = sum(x for x, y in cloud) / len(cloud)
        my = sum(y for x, y in cloud) / len(cloud)
        divisor = len(cloud) if population else len(cloud) - 1
        xx = sum((x - mx) ** 2 for x, y in cloud) / divisor
        xy = sum((x - mx) * (y - my) for x, y in cloud) / divisor
        yy = sum((y - my) ** 2 for x, y in cloud) / divisor
        determinant = xx * yy - xy * xy
        return sum((yy * (x - mx) ** 2 - 2 * xy * (x - mx) * (y - my)
                    + xx * (y - my) ** 2) / determinant for x, y in cloud) / len(cloud)
    transformed = [squares, [(.5 + 2 * (x - .5), .5 + 2 * (y - .5)) for x, y in squares],
                   [(x + .2, y) for x, y in squares]]
    self_reference = {"n_minus_1_original": fixed_mean_md2(squares),
                      "n_minus_1_scaled_against_fixed": fixed_mean_md2(
                          [(.5 + 2 * (x - .5), .5 + 2 * (y - .5)) for x, y in squares]),
                      "n_minus_1_shifted_against_fixed": fixed_mean_md2([(x + .2, y) for x, y in squares]),
                      "n_minus_1_refitted_means": [own_mean_md2(c) for c in transformed],
                      "equal_weight_population_refitted_means": [own_mean_md2(c, True) for c in transformed]}
    for key, expected in [("n_minus_1_original", 1.5), ("n_minus_1_scaled_against_fixed", 6),
                          ("n_minus_1_shifted_against_fixed", 4.5)]:
        assert math.isclose(self_reference[key], expected)
    assert all(math.isclose(v, 1.5) for v in self_reference["n_minus_1_refitted_means"])
    assert all(math.isclose(v, 2) for v in self_reference["equal_weight_population_refitted_means"])
    return {"synthetic": True, "seed_jitter": 42, "seed_cadence": 614,
            "interpretation": "Prescribed coordinate perturbations; no cognitive or phone-accuracy claim.",
            "reference": json_value(profile), "cases": cases, "self_reference": self_reference}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    receipt = run()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(receipt, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(f"PASS: {len(receipt['cases'])} synthetic sensitivity cases; receipt: {args.output}")


if __name__ == "__main__":
    main()
