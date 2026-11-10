"""Descriptive 2-D coordinate distance. No gaze-event or cognitive classification.

All clocks are milliseconds in the declared elapsed-clock domain; coordinates
are physical screen pixels. The module has no Android or third-party dependency.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, fields, is_dataclass
from enum import Enum
import argparse
import json
import math
from pathlib import Path
from typing import Sequence

VERSION = "coordinate_md_v1"
INPUT_INSTRUMENT = "newsmead_coordinate_md_input_v1"
RAW_STREAM = "calibrated_raw_v1"
SELECTED_STREAM = "article_smoothed_v1"


def _finite(value):
    return type(value) in (int, float) and math.isfinite(value)


class Status(str, Enum):
    READY = "READY"
    AVAILABLE = "AVAILABLE"
    INSUFFICIENT_REFERENCE = "INSUFFICIENT_REFERENCE"
    REFERENCE_DEGENERATE = "REFERENCE_DEGENERATE"
    REFERENCE_ILL_CONDITIONED = "REFERENCE_ILL_CONDITIONED"
    REFERENCE_UNAVAILABLE = "REFERENCE_UNAVAILABLE"
    REFERENCE_MISMATCH = "REFERENCE_MISMATCH"
    REFERENCE_BOUNDS_MISMATCH = "REFERENCE_BOUNDS_MISMATCH"
    REFERENCE_PERIOD = "REFERENCE_PERIOD"
    CONTEXT_MISMATCH = "CONTEXT_MISMATCH"
    CONTEXT_EXCLUDED = "CONTEXT_EXCLUDED"
    SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE"
    WATCHDOG = "WATCHDOG"
    INVALID_CLOCK = "INVALID_CLOCK"
    NON_MONOTONIC_CAPTURE = "NON_MONOTONIC_CAPTURE"
    NON_INCREASING_DELIVERY = "NON_INCREASING_DELIVERY"
    NONFINITE_COORDINATE = "NONFINITE_COORDINATE"
    NUMERICAL_FAILURE = "NUMERICAL_FAILURE"
    RECORDING_LOSS = "RECORDING_LOSS"
    CONTEXT_DISCONTINUITY = "CONTEXT_DISCONTINUITY"
    INSUFFICIENT_COVERAGE = "INSUFFICIENT_COVERAGE"
    NO_OBSERVED_INTERVALS = "NO_OBSERVED_INTERVALS"


@dataclass(frozen=True)
class Context:
    participant_id: str
    installation_id: str
    calibration_id: str
    model_id: str
    acquisition_id: str
    presentation_id: str
    clock_domain_id: str
    screen_width_px: int
    screen_height_px: int
    rotation_degrees: int = 0
    coordinate_stream: str = RAW_STREAM

    def __post_init__(self):
        for f in fields(self):
            if f.name.endswith("_id") and (not isinstance(getattr(self, f.name), str)
                                           or not getattr(self, f.name).strip()):
                raise ValueError(f"{f.name} must be a nonempty identity")
        if any(type(n) is not int or n <= 0 for n in
               (self.screen_width_px, self.screen_height_px)):
            raise ValueError("screen dimensions must be positive integers")
        if type(self.rotation_degrees) is not int or self.rotation_degrees not in (0, 90, 180, 270):
            raise ValueError("invalid rotation")
        if self.coordinate_stream not in (RAW_STREAM, SELECTED_STREAM):
            raise ValueError("unsupported coordinate stream")


@dataclass(frozen=True)
class Config:
    max_gap_ms: float = 250.0
    max_age_ms: float = 500.0
    min_reference_points: int = 3
    min_reference_coverage_ms: float = 60_000.0
    min_summary_coverage_fraction: float = 0.65
    relative_ridge: float = 0.0
    max_condition_number: float = 1e12

    def __post_init__(self):
        if any(not _finite(x) or x <= 0 for x in
               (self.max_gap_ms, self.max_age_ms, self.max_condition_number)):
            raise ValueError("gap, age and conditioning limits must be finite and positive")
        if type(self.min_reference_points) is not int or self.min_reference_points < 3:
            raise ValueError("a 2-D reference needs at least three points")
        if not _finite(self.min_reference_coverage_ms) or self.min_reference_coverage_ms < 0:
            raise ValueError("invalid reference coverage")
        if not _finite(self.min_summary_coverage_fraction) or not 0 <= self.min_summary_coverage_fraction <= 1:
            raise ValueError("invalid summary coverage")
        if not _finite(self.relative_ridge) or self.relative_ridge < 0:
            raise ValueError("invalid relative ridge")
        if self.max_condition_number < 1:
            raise ValueError("condition limit must be at least one")


@dataclass(frozen=True)
class Observation:
    context: Context
    provider_epoch_id: str
    context_epoch_id: str
    delivery_id: int
    capture_ms: float | None
    delivery_ms: float
    x_px: float | None
    y_px: float | None
    reason: str = "coordinate"
    watchdog: bool = False
    context_allowed: bool = True

    def __post_init__(self):
        if not isinstance(self.context, Context):
            raise ValueError("context must be explicit")
        if any(not isinstance(s, str) or not s.strip() for s in
               (self.provider_epoch_id, self.context_epoch_id, self.reason)):
            raise ValueError("epoch identities and source reason must be nonempty")
        if type(self.delivery_id) is not int or self.delivery_id < 1:
            raise ValueError("delivery ID must be a positive integer")
        if type(self.watchdog) is not bool or type(self.context_allowed) is not bool:
            raise ValueError("watchdog/context flags must be booleans")


@dataclass(frozen=True)
class Boundary:
    at_ms: float
    reason: str

    def __post_init__(self):
        if not _finite(self.at_ms) or self.at_ms < 0 or not self.reason.strip():
            raise ValueError("invalid context boundary")


@dataclass(frozen=True)
class LossRange:
    start_ms: float | None
    end_ms: float | None
    reason: str

    def __post_init__(self):
        if any(v is not None and (not _finite(v) or v < 0)
               for v in (self.start_ms, self.end_ms)):
            raise ValueError("invalid loss range")
        if self.start_ms is not None and self.end_ms is not None and self.end_ms < self.start_ms:
            raise ValueError("reversed loss range")
        if not self.reason.strip():
            raise ValueError("loss reason required")

    def overlaps(self, start_ms, end_ms):
        return ((self.start_ms is None or self.start_ms <= end_ms)
                and (self.end_ms is None or self.end_ms >= start_ms))


@dataclass(frozen=True)
class Vec2:
    x: float
    y: float


@dataclass(frozen=True)
class Cov2:
    xx: float
    xy: float
    yy: float


@dataclass(frozen=True)
class Stats:
    received_count: int
    eligible_count: int
    weighted_point_count: int
    interval_count: int
    covered_ms: float
    weight_squared_sum_ms2: float
    reasons: tuple[tuple[str, int], ...]


@dataclass(frozen=True)
class Profile:
    reference_id: str
    context: Context
    config: Config
    reference_start_ms: float
    reference_end_ms: float
    mean: Vec2
    covariance: Cov2
    scoring_covariance: Cov2
    ridge_added_px2: float
    condition_number: float
    factor_scale: float
    factor: tuple[float, float, float]
    unweighted_sample_mean: Vec2
    unweighted_sample_covariance: Cov2
    stats: Stats
    reference_delivery_maxima: tuple[tuple[str, int], ...]
    version: str = VERSION


@dataclass(frozen=True)
class FitResult:
    status: Status
    profile: Profile | None
    stats: Stats


@dataclass(frozen=True)
class PointScore:
    observation: Observation
    reference_id: str | None
    status: Status
    md2: float | None = None
    md: float | None = None
    delta_px: Vec2 | None = None


@dataclass(frozen=True)
class Summary:
    start_ms: float
    end_ms: float
    reference_id: str | None
    status: Status
    mean_md2: float | None
    coverage_fraction: float
    stats: Stats


class _Validator:
    def __init__(self, context, config, maxima=()):
        self.context, self.config = context, config
        self.maxima = dict(maxima)
        self.capture_floor = None

    def accept(self, o):
        if o.context != self.context:
            return Status.CONTEXT_MISMATCH
        old = self.maxima.get(o.provider_epoch_id)
        if old is not None and o.delivery_id <= old:
            return Status.NON_INCREASING_DELIVERY
        self.maxima[o.provider_epoch_id] = o.delivery_id
        if o.watchdog:
            return Status.WATCHDOG
        if (not _finite(o.capture_ms) or not _finite(o.delivery_ms) or o.capture_ms < 0
                or o.delivery_ms < o.capture_ms):
            return Status.INVALID_CLOCK
        if o.reason != "coordinate":
            self.capture_floor = max(self.capture_floor or 0.0, o.capture_ms)
            return Status.SOURCE_UNAVAILABLE
        if self.capture_floor is not None and o.capture_ms <= self.capture_floor:
            return Status.NON_MONOTONIC_CAPTURE
        self.capture_floor = o.capture_ms
        if o.delivery_ms - o.capture_ms > self.config.max_age_ms:
            return Status.SOURCE_UNAVAILABLE
        if not o.context_allowed:
            return Status.CONTEXT_EXCLUDED
        if not _finite(o.x_px) or not _finite(o.y_px):
            return Status.NONFINITE_COORDINATE
        return Status.AVAILABLE


def _bounds(start, end):
    if not _finite(start) or not _finite(end) or start < 0 or end <= start:
        raise ValueError("bounds must be finite, nonnegative and increasing")


def _weights(observations, statuses, start, end, config, boundaries):
    weights = [0.0] * len(observations)
    intervals = 0
    previous = None
    for i, (o, status) in enumerate(zip(observations, statuses)):
        if status != Status.AVAILABLE or not start <= o.capture_ms <= end:
            previous = None
            continue
        if previous is not None:
            p = observations[previous]
            dt = o.capture_ms - p.capture_ms
            if (p.provider_epoch_id == o.provider_epoch_id
                    and p.context_epoch_id == o.context_epoch_id and 0 < dt <= config.max_gap_ms
                    and not any(p.capture_ms < b.at_ms <= o.capture_ms for b in boundaries)):
                weights[previous] += dt / 2
                weights[i] += dt / 2
                intervals += 1
        previous = i
    return weights, intervals


def _stats(statuses, weights, intervals):
    reasons = {}
    for status in statuses:
        reasons[status.value] = reasons.get(status.value, 0) + 1
    return Stats(len(statuses), sum(s == Status.AVAILABLE for s in statuses),
                 sum(w > 0 for w in weights), intervals, math.fsum(weights),
                 math.fsum(w * w for w in weights), tuple(sorted(reasons.items())))


def _moments(observations, weights, sample=False):
    # Weighted Welford avoids subtracting large raw coordinate squares.
    total = mx = my = cxx = cxy = cyy = 0.0
    for o, w in zip(observations, weights):
        if w <= 0:
            continue
        updated = total + w
        dx, dy = o.x_px - mx, o.y_px - my
        nx, ny = mx + dx * (w / updated), my + dy * (w / updated)
        cxx += w * dx * (o.x_px - nx)
        cxy += w * (dx * (o.y_px - ny) + dy * (o.x_px - nx)) / 2
        cyy += w * dy * (o.y_px - ny)
        total, mx, my = updated, nx, ny
    denominator = total - 1 if sample else total
    if denominator <= 0:
        raise ArithmeticError("no weighted observations")
    result = Vec2(mx, my), Cov2(cxx / denominator, cxy / denominator, cyy / denominator)
    if not all(_finite(v) for item in result for v in asdict(item).values()):
        raise ArithmeticError("nonfinite moments")
    return result


def _factor(covariance, config):
    trace = covariance.xx + covariance.yy
    if not _finite(trace):
        return Status.NUMERICAL_FAILURE, None
    if trace <= 0:
        return Status.REFERENCE_DEGENERATE, None
    ridge = config.relative_ridge * (trace / 2)
    cov = Cov2(covariance.xx + ridge, covariance.xy, covariance.yy + ridge)
    scale = max(abs(cov.xx), abs(cov.xy), abs(cov.yy))
    if not all(_finite(v) for v in (ridge, cov.xx, cov.xy, cov.yy, scale)) or scale <= 0:
        return Status.NUMERICAL_FAILURE, None
    a, b, d = cov.xx / scale, cov.xy / scale, cov.yy / scale
    if a <= 0:
        return Status.REFERENCE_DEGENERATE, None
    l00 = math.sqrt(a)
    l10 = b / l00
    residual = d - l10 * l10
    if residual <= 0:
        return Status.REFERENCE_DEGENERATE, None
    l11 = math.sqrt(residual)
    largest = (a + d) / 2 + math.hypot((a - d) / 2, b)
    smallest = (a * residual) / largest
    if smallest <= 0:
        return Status.REFERENCE_DEGENERATE, None
    condition = largest / smallest
    if not _finite(condition) or condition > config.max_condition_number:
        return Status.REFERENCE_ILL_CONDITIONED, None
    return Status.READY, (cov, ridge, condition, scale, (l00, l10, l11))


def fit_reference(reference_id: str, context: Context, observations: Sequence[Observation],
                  start_ms: float, end_ms: float, config: Config = Config(),
                  boundaries: Sequence[Boundary] = (), losses: Sequence[LossRange] = ()) -> FitResult:
    _bounds(start_ms, end_ms)
    if not isinstance(reference_id, str) or not reference_id.strip():
        raise ValueError("reference identity required")
    validator = _Validator(context, config)
    statuses = [validator.accept(o) for o in observations]
    weights, intervals = _weights(observations, statuses, start_ms, end_ms, config, boundaries)
    stats = _stats(statuses, weights, intervals)
    def fail(status):
        return FitResult(status, None, stats)
    # Reference input is a separately segmented period, not a whole recording.
    # Reject extra rows rather than freezing evaluation-time delivery identities.
    for o in observations:
        at = o.capture_ms if _finite(o.capture_ms) else o.delivery_ms
        if _finite(at) and not start_ms <= at <= end_ms:
            return fail(Status.REFERENCE_BOUNDS_MISMATCH)
    if Status.CONTEXT_MISMATCH in statuses:
        return fail(Status.REFERENCE_MISMATCH)
    if any(loss.overlaps(start_ms, end_ms) for loss in losses):
        return fail(Status.RECORDING_LOSS)
    if (stats.weighted_point_count < config.min_reference_points
            or stats.covered_ms < config.min_reference_coverage_ms):
        return fail(Status.INSUFFICIENT_REFERENCE)
    try:
        mean, covariance = _moments(observations, weights)
        unweighted_mean, unweighted_cov = _moments(observations, [float(w > 0) for w in weights], True)
        status, factor = _factor(covariance, config)
    except (ArithmeticError, ValueError, OverflowError):
        return fail(Status.NUMERICAL_FAILURE)
    if status != Status.READY:
        return fail(status)
    cov, ridge, condition, scale, lower = factor
    return FitResult(Status.READY, Profile(reference_id, context, config, start_ms, end_ms,
        mean, covariance, cov, ridge, condition, scale, lower, unweighted_mean, unweighted_cov,
        stats, tuple(sorted(validator.maxima.items()))), stats)


class CoordinateScorer:
    """Frozen profile; only delivery/capture validation state advances."""
    def __init__(self, profile: Profile | None, context: Context, config: Config | None = None):
        self.profile = profile
        self.context = context
        self.config = profile.config if profile else config or Config()
        maxima = profile.reference_delivery_maxima if profile and profile.context == context else ()
        self._validator = _Validator(context, self.config, maxima)

    def score(self, observation: Observation) -> PointScore:
        p = self.profile
        reference_id = p.reference_id if p else None
        status = self._validator.accept(observation)
        if status != Status.AVAILABLE:
            return PointScore(observation, reference_id, status)
        if p is None:
            return PointScore(observation, None, Status.REFERENCE_UNAVAILABLE)
        if p.context != self.context:
            return PointScore(observation, reference_id, Status.REFERENCE_MISMATCH)
        if observation.capture_ms <= p.reference_end_ms:
            return PointScore(observation, reference_id, Status.REFERENCE_PERIOD)
        delta = Vec2(observation.x_px - p.mean.x, observation.y_px - p.mean.y)
        l00, l10, l11 = p.factor
        root_scale = math.sqrt(p.factor_scale)
        zx = (delta.x / root_scale) / l00
        zy = (delta.y / root_scale - l10 * zx) / l11
        md2 = zx * zx + zy * zy
        if not all(_finite(v) for v in (delta.x, delta.y, md2)):
            return PointScore(observation, reference_id, Status.NUMERICAL_FAILURE)
        return PointScore(observation, reference_id, Status.AVAILABLE, md2, math.sqrt(md2), delta)


def score_points(profile, context, observations, config=None):
    scorer = CoordinateScorer(profile, context, config)
    return tuple(scorer.score(o) for o in observations)


def summarize(profile: Profile | None, context: Context, observations: Sequence[Observation],
              start_ms: float, end_ms: float, boundaries: Sequence[Boundary] = (),
              losses: Sequence[LossRange] = (), config: Config | None = None) -> Summary:
    _bounds(start_ms, end_ms)
    cfg = profile.config if profile else config or Config()
    # Re-score the ordered input against this exact profile. Keep invalid-clock
    # rows in the sequence: dropping one could bridge a missing segment. Only
    # endpoints inside the requested window receive weight; no edge extension.
    scores = score_points(profile, context, observations, cfg)
    statuses = [s.status for s in scores]
    weights, intervals = _weights(observations, statuses, start_ms, end_ms, cfg, boundaries)
    def in_window(o):
        at = o.capture_ms if _finite(o.capture_ms) else o.delivery_ms
        return _finite(at) and start_ms <= at <= end_ms
    selected = [i for i, o in enumerate(observations) if in_window(o)]
    stats = _stats([statuses[i] for i in selected], [weights[i] for i in selected], intervals)
    coverage = stats.covered_ms / (end_ms - start_ms)
    def result(status, mean=None):
        return Summary(start_ms, end_ms, profile.reference_id if profile else None,
                       status, mean, coverage, stats)
    if any(loss.overlaps(start_ms, end_ms) for loss in losses):
        return result(Status.RECORDING_LOSS)
    if profile is None:
        return result(Status.REFERENCE_UNAVAILABLE)
    if profile.context != context or any(observations[i].context != context for i in selected):
        return result(Status.REFERENCE_MISMATCH)
    epochs = {(observations[i].provider_epoch_id, observations[i].context_epoch_id) for i in selected}
    if len(epochs) > 1 or any(start_ms < b.at_ms < end_ms for b in boundaries):
        return result(Status.CONTEXT_DISCONTINUITY)
    if stats.covered_ms <= 0:
        return result(Status.NO_OBSERVED_INTERVALS)
    if coverage < cfg.min_summary_coverage_fraction:
        return result(Status.INSUFFICIENT_COVERAGE)
    mean = math.fsum(w / stats.covered_ms * s.md2 for w, s in zip(weights, scores) if w > 0)
    return result(Status.AVAILABLE, mean) if _finite(mean) else result(Status.NUMERICAL_FAILURE)


def json_value(value):
    """Strict JSON output; unavailable numeric inputs remain null with their status."""
    if isinstance(value, Enum):
        return value.value
    if is_dataclass(value):
        return {f.name: json_value(getattr(value, f.name)) for f in fields(value)}
    if isinstance(value, dict):
        return {k: json_value(v) for k, v in value.items()}
    if isinstance(value, (tuple, list)):
        return [json_value(v) for v in value]
    if isinstance(value, float) and not math.isfinite(value):
        return None
    return value


def analyze_document(document):
    if document.get("instrument") != INPUT_INSTRUMENT or document.get("schema_version") != 1:
        raise ValueError("expected the explicit coordinate-MD input contract; no legacy log inference")
    context = Context(**document["context"])
    config = Config(**document.get("config", {}))
    def observations(rows):
        return tuple(Observation(context=Context(**r["context"]) if "context" in r else context,
                                 **{k: v for k, v in r.items() if k != "context"}) for r in rows)
    bounds = document["reference_bounds"]
    boundaries = tuple(Boundary(**b) for b in document.get("boundaries", []))
    losses = tuple(LossRange(**r) for r in document.get("losses", []))
    fit = fit_reference(document["reference_id"], context,
        observations(document["reference_observations"]), bounds["start_ms"], bounds["end_ms"],
        config, boundaries, losses)
    evaluation = observations(document["evaluation_observations"])
    points = score_points(fit.profile, context, evaluation, config)
    summaries = [{"window_id": w["window_id"], "result": summarize(fit.profile, context, evaluation,
                  w["start_ms"], w["end_ms"], boundaries, losses, config)}
                 for w in document.get("windows", [])]
    return json_value({"analysis_version": VERSION, "coordinate_units": "physical_screen_pixels",
                       "time_units": "elapsed_milliseconds", "distance_units": "dimensionless",
                       "fit": fit, "points": points, "summaries": summaries})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    def invalid_constant(value):
        raise ValueError(f"nonstandard JSON constant: {value}")
    try:
        result = analyze_document(json.loads(args.input.read_text(encoding="utf-8"),
                                            parse_constant=invalid_constant))
        rendered = json.dumps(result, indent=2, allow_nan=False) + "\n"
        if args.output:
            if args.output.resolve() == args.input.resolve():
                raise ValueError("output must not overwrite the source input")
            args.output.write_text(rendered, encoding="utf-8")
        else:
            print(rendered, end="")
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
