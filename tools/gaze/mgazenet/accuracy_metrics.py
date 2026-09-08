"""Offline scoring of independent, stationary point-target blocks; never fits gaze.

No camera/device access, interpolation, snapping, parameter selection or promotion.
See docs/gaze-mgazenet-accuracy-protocol.md for the versioned input contract.
"""
import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path

HARNESS_PROTOCOL = "mgazenet_stationary_viewport_v1"
FIT_GRID = (1, 5, 9, 12, 16, 19, 27, 30, 34, 37, 41, 45, 23)
TEST_GRID = (2, 8, 13, 15, 31, 33, 38, 44)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def number(value):
    require(type(value) in (int, float) and math.isfinite(value), "Expected finite number")
    return value


def pair(value):
    require(isinstance(value, list) and len(value) == 2, "Expected coordinate pair")
    return [number(v) for v in value]


def rectangle(value):
    require(isinstance(value, list) and len(value) == 4, "Expected rectangle")
    left, top, right, bottom = map(number, value)
    require(left < right and top < bottom, "Empty or inverted rectangle")
    return left, top, right, bottom


def inside(point, rect):
    x, y = point
    left, top, right, bottom = rect
    return left <= x < right and top <= y < bottom


def distribution(values):
    if not values:
        return {"n": 0, "median": None, "p95": None, "max": None}
    ordered = sorted(values)
    return {"n": len(values), "median": statistics.median(ordered),
            "p95": ordered[math.ceil(.95 * len(ordered)) - 1], "max": ordered[-1]}


def signed_distribution(values):
    if not values:
        return {"n": 0, "min": None, "p05": None, "median": None, "p95": None, "max": None}
    ordered = sorted(values)
    return {"n": len(values), "min": ordered[0],
            "p05": ordered[max(0, math.ceil(.05 * len(ordered)) - 1)],
            "median": statistics.median(ordered),
            "p95": ordered[math.ceil(.95 * len(ordered)) - 1], "max": ordered[-1]}


def validate_target_geometry(report, manifest):
    """Reconstruct the declared v1 targets independently of the predictions."""
    viewport = rectangle(manifest.get("viewport_screen_px"))
    screen = manifest.get("screen_px")
    require(isinstance(screen, list) and len(screen) == 2 and
            all(type(v) is int and v > 0 for v in screen), "Invalid manifest screen")
    require(0 <= viewport[0] < viewport[2] <= screen[0] and
            0 <= viewport[1] < viewport[3] <= screen[1], "Viewport outside physical screen")

    def grid(index):
        x = 50/1920 + ((index-1) % 9) * (1820/1920)/8
        y = 50/1080 + ((index-1) // 9) * (980/1080)/4
        return [viewport[0] + x*(viewport[2]-viewport[0]),
                viewport[1] + y*(viewport[3]-viewport[1])]

    def matches(actual, expected):
        # Cross-language double arithmetic allowance, far below a physical pixel.
        return all(math.isclose(a, b, rel_tol=0, abs_tol=1e-7)
                   for a, b in zip(pair(actual), expected))

    targets = manifest["targets"]
    expected_ids = ["practice"] + [f"fit_{i}" for i in FIT_GRID]
    require([t["id"] for t in targets] == expected_ids, "Changed calibration target order")
    for target, index in zip(targets, (23,) + FIT_GRID):
        require(matches(target.get("point_px"), grid(index)), "Changed calibration target position")
    require(all(p.get("practice") is t["practice"]
                for p, t in zip(report["fit_points"], targets)), "Fit-point practice flag mismatch")
    blocks = report["blocks"]
    require([b["id"] for b in blocks] == [f"test_{i}" for i in TEST_GRID],
            "Changed held-out target contract")
    lines = blocks[0]["lines"]
    require(isinstance(lines, list) and len(lines) >= 5, "Insufficient harness line geometry")
    require([line["index"] for line in lines] == list(range(len(lines))), "Changed harness line indices")
    boxes = [rectangle(line["rect_px"]) for line in lines]
    for block, index in zip(blocks, TEST_GRID):
        require(block["lines"] == lines, "Line geometry changed within stationary session")
        point = grid(index)
        line = min(range(len(boxes)), key=lambda i: abs((boxes[i][1]+boxes[i][3])/2-point[1]))
        left, top, right, bottom = boxes[line]
        expected = [min(max(point[0], left+(right-left)*.02), right-(right-left)*.02),
                    (top+bottom)/2]
        require(block["region"] == f"grid_{index}" and block["target_line_index"] == line and
                matches(block["target_px"], expected), "Held-out target does not match protocol geometry")
    points = [pair(b["target_px"]) for b in blocks]
    require(len({tuple(p) for p in points}) == len(points), "Duplicate held-out target positions")
    require(not any(matches(p, t["point_px"]) for p in points for t in targets[1:]),
            "Fit/test coordinate leakage")


def score_block(block, max_age):
    require(block["role"] == "held_out" and block["task"] == "stationary_point",
            "Only independent stationary held-out blocks are supported")
    start, end = number(block["start_ms"]), number(block["end_ms"])
    require(0 <= start < end, "Invalid measurement window")
    target = pair(block["target_px"])
    height = number(block["line_height_px"])
    require(height > 0, "Invalid line height")
    viewport = rectangle(block["viewport_screen_px"])
    require(inside(target, viewport), "Target is outside viewport")
    lines = block["lines"]
    require(isinstance(lines, list) and lines, "Missing rendered line geometry")
    indices = [line["index"] for line in lines]
    require(all(type(i) is int and i >= 0 for i in indices), "Invalid line index")
    require(indices == sorted(set(indices)), "Duplicate or unordered line indices")
    boxes = [rectangle(line["rect_px"]) for line in lines]
    for i, box in enumerate(boxes):
        require(viewport[0] <= box[0] < box[2] <= viewport[2] and
                viewport[1] <= box[1] < box[3] <= viewport[3], "Line outside clipped viewport")
        if i:
            require(boxes[i-1][3] <= box[1], "Overlapping or unordered line bands")
    def line_at(point):
        return next((index for index, box in zip(indices, boxes) if inside(point, box)), None)
    target_line = block["target_line_index"]
    require(type(target_line) is int and line_at(target) == target_line,
            "Target coordinate and target line disagree")

    samples = block["samples"]
    require(isinstance(samples, list), "Expected sample list; empty blocks must remain")
    events, dxs, dys, errors, vertical, ages = [], [], [], [], [], []
    valid = exact = adjacent = off_text = late = 0
    previous_capture = previous_output = -1
    for sample in samples:
        capture, output = number(sample["capture_ms"]), number(sample["output_ms"])
        require(start <= capture < end and output >= capture, "Timestamp outside capture window or reversed")
        require(capture > previous_capture and output > previous_output,
                "Duplicate or out-of-order samples; resolve explicitly before scoring")
        previous_capture, previous_output = capture, output
        point = sample["point_px"]
        age = output - capture
        ages.append(age)
        late += output >= end
        assigned = None
        if point is not None:
            point = pair(point)
            dx, dy = point[0] - target[0], point[1] - target[1]
            require(math.isfinite(dx) and math.isfinite(dy) and math.isfinite(math.hypot(dx, dy)),
                    "Coordinate error overflow")
            dxs.append(dx); dys.append(dy); errors.append(math.hypot(dx, dy))
            vertical.append(abs(dy) / height)
            require(math.isfinite(vertical[-1]), "Line error overflow")
            assigned = line_at(point)
            valid += 1
            exact += assigned == target_line
            adjacent += assigned is not None and abs(assigned - target_line) <= 1
            off_text += assigned is None
        events.append((output, capture + max_age, point is not None, assigned))

    # Availability is measured on result time. Never fill the initial gap,
    # carry across blocks, or keep a result beyond its capture-age limit.
    fresh_intervals = []
    fresh_ms = exact_ms = adjacent_ms = 0.0
    for i, (output, expiry, present, assigned) in enumerate(events):
        stop = min(end, expiry, events[i+1][0] if i+1 < len(events) else end)
        begin = max(start, output)
        if not present or stop <= begin:
            continue
        duration = stop - begin
        fresh_intervals.append((begin, stop))
        fresh_ms += duration
        exact_ms += duration if assigned == target_line else 0
        adjacent_ms += duration if assigned is not None and abs(assigned-target_line) <= 1 else 0
    cursor = start
    gaps = []
    for begin, stop in fresh_intervals:
        if begin > cursor:
            gaps.append(begin-cursor)
        cursor = stop
    if cursor < end:
        gaps.append(end-cursor)
    duration = end-start
    total = len(samples)
    return {
        "id": block["id"], "region": block["region"], "planned_duration_ms": duration,
        "received_samples": total, "coordinate_samples": valid,
        "explicit_invalid_samples": total-valid, "off_text_coordinate_samples": off_text,
        "outputs_after_window": late,
        "exact_line_samples": exact, "within_one_line_samples": adjacent,
        "coordinate_fraction_of_received": valid/total if total else None,
        "exact_line_fraction_of_received": exact/total if total else None,
        "within_one_line_fraction_of_received": adjacent/total if total else None,
        "exact_line_fraction_of_coordinates": exact/valid if valid else None,
        "fresh_coordinate_ms": fresh_ms, "fresh_exact_line_ms": exact_ms,
        "fresh_within_one_line_ms": adjacent_ms,
        "fresh_coordinate_time_fraction": fresh_ms/duration,
        "fresh_exact_line_time_fraction": exact_ms/duration,
        "fresh_within_one_line_time_fraction": adjacent_ms/duration,
        "unavailable_ms": duration-fresh_ms, "longest_unavailable_ms": max(gaps, default=0),
        "signed_dx_px": signed_distribution(dxs), "signed_dy_px": signed_distribution(dys),
        "euclidean_px": distribution(errors), "absolute_vertical_lines": distribution(vertical),
        "output_age_ms": distribution(ages),
    }


def evaluate(report):
    require(report["schema"] == "mgazenet_accuracy_v1", "Unknown accuracy schema")
    require(report.get("outcome", "complete") == "complete", "Incomplete session cannot be scored as completed")
    require(report["evidence_kind"] in ("synthetic_contract", "recorded"), "Unknown evidence kind")
    manifest_fields = {"calibration_manifest_json", "calibration_manifest_sha256",
                       "calibration_manifest", "pipeline_json"}
    strict_harness = (report["evidence_kind"] == "recorded" or
                      report.get("protocol_id") == HARNESS_PROTOCOL or
                      bool(manifest_fields.intersection(report)))
    if strict_harness:
        require(manifest_fields.issubset(report), "Complete harness manifests required")
        require(report.get("protocol_id") == HARNESS_PROTOCOL, "Unsupported recorded/harness protocol")
        require(report.get("outcome") == "complete", "Harness completion must be explicit")
    if "calibration_manifest_json" in report:
        encoded = report["calibration_manifest_json"]
        require(isinstance(encoded, str), "Invalid calibration manifest encoding")
        digest = hashlib.sha256(encoded.encode("utf-8")).hexdigest()
        require(digest == report["calibration_manifest_sha256"] and
                json.loads(encoded) == report["calibration_manifest"] and
                report["calibration_id"] == report["session_id"] + ":" + digest,
                "Calibration manifest integrity mismatch")
        manifest = report["calibration_manifest"]
        require(manifest.get("protocol") == report["protocol_id"] and
                manifest.get("device") == report["device_id"] and
                manifest.get("coordinate_space") == report["coordinate_space"],
                "Manifest provenance mismatch")
        require(manifest.get("label_space") == "physical_screen_fractions" and
                manifest.get("filter") == "none" and manifest.get("correction") == "none",
                "Unsupported calibration transformation")
        pipeline_encoded = report.get("pipeline_json")
        require(isinstance(pipeline_encoded, str) and
                hashlib.sha256(pipeline_encoded.encode("utf-8")).hexdigest() == report["pipeline_id"] and
                json.loads(pipeline_encoded) == manifest.get("pipeline"),
                "Pipeline manifest integrity mismatch")
        pipeline = manifest["pipeline"]
        if report["evidence_kind"] == "recorded":
            require(pipeline.get("model_sha256") == "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96" and
                    pipeline.get("face_landmarker_sha256") == "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
                    "Unexpected model or localizer asset")
        require(manifest.get("eye_area_rule") == "both_pixel_polygon_areas_strictly_above_10" and
                manifest.get("fit_settle_ms") == 1500 and
                manifest.get("fit_samples_per_target") == 45 and
                manifest.get("fit_wait_ms") == 500 and
                manifest.get("fit_timeout_ms") == 30000 and
                manifest.get("test_settle_ms") == 3000 and
                manifest.get("test_measure_ms") == 2500 and
                manifest.get("test_drain_ms") == 250 and
                manifest.get("svr") == "OpenCV EPS-SVR/RBF C=1 gamma=.005 P=.001 MAX_ITER=10000 epsilon_argument=.0001",
                "Changed calibration or measurement protocol")
        require(manifest.get("training_rows") == 585 and
                isinstance(manifest.get("training_digest"), str) and
                len(manifest["training_digest"]) == 64 and
                all(c in "0123456789abcdef" for c in manifest["training_digest"]),
                "Incomplete calibration training provenance")
        targets = manifest.get("targets")
        require(isinstance(targets, list) and len(targets) == 14 and
                targets[0].get("id") == "practice" and targets[0].get("practice") is True and
                all(t.get("practice") is False for t in targets[1:]),
                "Changed fit/practice target contract")
        require(report["fit_block_ids"] == [t["id"] for t in targets[1:]],
                "Fit IDs do not match calibration manifest")
        fit_points = report.get("fit_points")
        require(isinstance(fit_points, list) and len(fit_points) == 14 and
                [p.get("id") for p in fit_points] == [t["id"] for t in targets] and
                all(p.get("accepted") == 45 for p in fit_points),
                "Incomplete fit-point record")
        require(report.get("active_tracker_access") is False and
                report.get("calibration_store_access") is False and
                report.get("camera_frames_retained") is False and
                report.get("features_retained") is False and
                report.get("personal_model_retained") is False,
                "Unexpected data or active-tracker access")
    require(report["coordinate_space"] == "physical_screen_px" and
            report["clock"] == "shared_monotonic_ms", "Incompatible coordinate or clock contract")
    for key in ("protocol_id", "session_id", "device_id", "pipeline_id", "calibration_id"):
        require(isinstance(report[key], str) and bool(report[key].strip()), "Missing provenance: " + key)
    max_age = number(report["max_output_age_ms"])
    require(max_age > 0, "Explicit positive freshness limit required; no default is selected")
    fit_ids = report["fit_block_ids"]
    require(isinstance(fit_ids, list) and fit_ids and
            all(isinstance(i, str) and i for i in fit_ids) and len(fit_ids) == len(set(fit_ids)),
            "Missing or duplicate fit block IDs")
    blocks = report["blocks"]
    require(isinstance(blocks, list) and blocks, "No planned held-out blocks")
    ids = [block["id"] for block in blocks]
    require(all(isinstance(i, str) and i for i in ids) and len(ids) == len(set(ids)),
            "Invalid or duplicated held-out block IDs")
    require(not set(ids).intersection(fit_ids), "Fit/test block leakage")
    if "calibration_manifest_json" in report:
        validate_target_geometry(report, manifest)
        screen = manifest.get("screen_px")
        require(isinstance(screen, list) and len(screen) == 2 and
                all(type(v) is int and v > 0 for v in screen), "Invalid manifest screen")
        manifest_viewport = list(rectangle(manifest.get("viewport_screen_px")))
        manifest_height = number(manifest.get("line_height_px"))
        require(manifest_height > 0, "Invalid manifest line height")
        settle_ms = number(manifest.get("test_settle_ms"))
        measure_ms = number(manifest.get("test_measure_ms"))
        for block in blocks:
            shown = number(block.get("shown_ms"))
            require(number(block["start_ms"]) == shown + settle_ms and
                    number(block["end_ms"]) == block["start_ms"] + measure_ms,
                    "Test timing does not match manifest")
            require(block["viewport_screen_px"] == manifest_viewport and
                    block["line_height_px"] == manifest_height,
                    "Test geometry does not match calibration manifest")
    previous_end = -1
    results = []
    for block in blocks:
        require(isinstance(block["region"], str) and block["region"], "Missing target region")
        require(number(block["start_ms"]) >= previous_end, "Overlapping or unordered blocks")
        results.append(score_block(block, max_age))
        previous_end = block["end_ms"]
    return {
        "schema": "mgazenet_accuracy_summary_v1", "evidence_kind": report["evidence_kind"],
        "accuracy_gate_pass": None, "promotion_decision": "not_evaluated",
        "scope": "Stationary instructed-point scoring; no verified fixation or natural-reading ground truth",
        "provenance": {key: report[key] for key in
                       ("protocol_id", "session_id", "device_id", "pipeline_id", "calibration_id")},
        "max_output_age_ms": max_age, "planned_blocks": len(results),
        "blocks_without_coordinates": sum(b["coordinate_samples"] == 0 for b in results),
        "blocks": results,
        "target_balanced": {key: statistics.mean(b[key] for b in results) for key in
                            ("fresh_coordinate_time_fraction", "fresh_exact_line_time_fraction",
                             "fresh_within_one_line_time_fraction")},
        "worst_block_vertical_lines": max((b["absolute_vertical_lines"]["max"] for b in results
                                           if b["coordinate_samples"]), default=None),
        "limitations": ["Declared fit/test IDs do not independently prove provenance or participant compliance.",
                        "Freshness is a declared analysis parameter, not a validated study threshold.",
                        "Time fractions describe bounded availability of the latest result, not eye position between samples.",
                        "No word, fixation, dwell, regression or population-validity claim is computed."],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = evaluate(json.loads(args.input.read_text(encoding="utf-8")))
    result["input_sha256"] = hashlib.sha256(args.input.read_bytes()).hexdigest()
    # Refuse accidental overwriting of an earlier result or the input evidence.
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    print(f"Scored {result['planned_blocks']} planned blocks; no accuracy gate or promotion decision")


if __name__ == "__main__":
    main()
