"""Inspect the separate live-pilot JSONL contract without reclassifying gaze.

Integrity checks and conditional prompted agreement are software quantities.
They cannot establish actual eye position, difficulty, or aid effectiveness.
The summary omits body text, raw coordinates, and free-text adherence notes.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


INSTRUMENT = "newsmead_scanpath_pilot_v1"
SMOOTHED_INSTRUMENT = "newsmead_scanpath_pilot_v2"


def _reject_constant(value):
    raise ValueError(f"Nonfinite JSON value: {value}")


def read_trace(path):
    records = []
    with Path(path).open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                raise ValueError(f"Blank record at line {line_number}")
            try:
                row = json.loads(line, parse_constant=_reject_constant)
            except ValueError as error:
                raise ValueError(f"Invalid JSON at line {line_number}: {error}") from error
            if not isinstance(row, dict) or not isinstance(row.get("detail"), dict):
                raise ValueError(f"Invalid record structure at line {line_number}")
            records.append(row)
    return records


def summarize(records, final_filename=True):
    issues = []
    if not records or records[0].get("kind") != "header":
        raise ValueError("First record must be the pilot header")
    header = records[0]["detail"]
    version = header.get("schema_version")
    expected_instrument = {1: INSTRUMENT, 2: SMOOTHED_INSTRUMENT}.get(version)
    if expected_instrument is None or header.get("instrument") != expected_instrument:
        raise ValueError("Unsupported instrument/schema; replay and frozen validation logs are separate")
    if version == 2:
        for key, expected in (("coordinate_stream", "article_smoothed_v1"), ("raw_scoring", False), ("processed_scoring", True)):
            if header.get(key) != expected:
                issues.append(f"unexpected_{key}")
    kinds = Counter(row.get("kind") for row in records)
    if kinds["header"] != 1:
        issues.append("header_count")
    if [row.get("sequence") for row in records] != list(range(1, len(records) + 1)):
        issues.append("noncontiguous_record_sequence")
    footer = records[-1]["detail"] if records[-1].get("kind") == "footer" else None
    if kinds["footer"] != 1 or footer is None:
        issues.append("missing_or_misplaced_footer")
    if not final_filename:
        issues.append("partial_filename")
    if footer is not None:
        if footer.get("complete_trace") is not True or footer.get("trace_failure") is not None:
            issues.append("writer_incomplete")
        if footer.get("record_count_before_footer") != len(records) - 1:
            issues.append("footer_record_count")
    body = header.get("body_text")
    if not isinstance(body, str) or hashlib.sha256(body.encode("utf-8")).hexdigest() != header.get("body_sha256"):
        issues.append("body_hash")
    for key, expected in (("coordinate_units", "physical_screen_pixels"), ("offset_units", "UTF-16"), ("time_units", "milliseconds")):
        if header.get(key) != expected:
            issues.append(f"unexpected_{key}")
    raw = [row["detail"] for row in records if row.get("kind") == "raw_observation"]
    samples = [row["detail"] for row in records if row.get("kind") == "sample"]
    if version == 2:
        sources = {row.get("input_ordinal"): row for row in raw}
        for sample in samples:
            source = sources.get(sample.get("input_ordinal"), {})
            if any(key not in source for key in ("provider_x", "provider_y")):
                issues.append("missing_provider_coordinates")
            if any(key not in sample for key in ("x", "y", "display_x", "display_y")):
                issues.append("missing_selected_display_coordinates")
            if any(sample.get(key) != source.get(provider) for key, provider in (
                    ("x", "provider_x"), ("display_x", "provider_x"),
                    ("y", "provider_y"), ("display_y", "provider_y"))):
                issues.append("provider_coordinate_retention")
            if sample.get("coordinate_stream") != "article_smoothed_v1":
                issues.append("sample_coordinate_stream")
            if any(sample.get(selected) != sample.get(display) for selected, display in (("x", "display_x"), ("y", "display_y"))):
                issues.append("selected_display_pairing")
            if any(sample.get(key) != source.get(key) for key in ("raw_x", "raw_y")):
                issues.append("raw_coordinate_retention")
    acceptance = [row["detail"] for row in records if row.get("kind") == "input_acceptance"]
    ordinals = [row.get("input_ordinal") for row in raw]
    sample_ordinals = [row.get("input_ordinal") for row in samples]
    if ordinals != list(range(1, len(raw) + 1)):
        issues.append("raw_observation_ordinals")
    if Counter(ordinals) != Counter(sample_ordinals):
        issues.append("raw_sample_pairing")
    if any(row.get("engine_accepted") not in (True, False) for row in acceptance):
        issues.append("invalid_engine_acceptance")
    acceptance_ordinals = [row.get("input_ordinal") for row in acceptance]
    terminal = sum(row.get("phase") == "terminal_delivery" for row in raw)
    if len(set(acceptance_ordinals)) != len(acceptance_ordinals) or not set(acceptance_ordinals).issubset(set(ordinals)):
        issues.append("acceptance_pairing")
    if len(acceptance) != len(raw) - terminal:
        issues.append("missing_engine_acceptance")
    engine_accepted = sum(row.get("engine_accepted") is True for row in acceptance)
    plan = header.get("plan")
    score = footer.get("score") if footer else None
    if footer is not None:
        if footer.get("observations") != len(raw) or footer.get("engine_accepted_samples") != engine_accepted or footer.get("terminal_deliveries") != terminal:
            issues.append("footer_input_counts")
        if isinstance(plan, dict) and not isinstance(score, dict):
            issues.append("missing_prompted_score")
    if isinstance(score, dict):
        if score.get("total_samples") != len(samples):
            issues.append("score_sample_count")
        targets = score.get("targets", [])
        planned = plan.get("targets", []) if isinstance(plan, dict) else []
        if [t.get("target") for t in targets] != planned:
            issues.append("planned_target_retention")
        onset_indices = [r["detail"].get("index") for r in records if r.get("kind") == "prompt_onset_proxy"]
        if onset_indices != [t.get("index") for t in targets if t.get("attempted")]:
            issues.append("prompt_onset_pairing")
        # Do not expose bounds or text, and never relabel descriptive alignment as precision.
        score = dict(score)
        score["targets"] = [dict({k: v for k, v in t.items() if k != "target"}, target_id=t.get("target", {}).get("id")) for t in targets]
    synthetic = header.get("input_kind") == "synthetic_pilot_check"
    return {
        "instrument": expected_instrument,
        "schema_version": version,
        "coordinate_stream": header.get("coordinate_stream") if version == 2 else "calibrated_raw_v1",
        "interpretation_mode": "selected_smoothed_stream" if version == 2 else "legacy_raw",
        "raw_scoring": header.get("raw_scoring") if version == 2 else True,
        "processed_scoring": header.get("processed_scoring") if version == 2 else False,
        "input_phase": header.get("input_phase"),
        "smoothing": header.get("smoothing"),
        "smoothing_applied_to_synthetic_input": header.get("smoothing_applied_to_synthetic_input"),
        "smoother_min_cutoff": header.get("smoother_min_cutoff"),
        "smoother_beta": header.get("smoother_beta"),
        "integrity_ok": not issues,
        "issues": issues,
        "synthetic": synthetic,
        "input_kind": header.get("input_kind"),
        "task_id": header.get("task_id"),
        "run_settings": header.get("run_settings"),
        "run_settings_sha256": header.get("run_settings_sha256"),
        "configuration": header.get("configuration"),
        "passage_id": header.get("passage_id"),
        "passage_origin": header.get("passage_origin"),
        "body_sha256": header.get("body_sha256"),
        "version_name": header.get("version_name"),
        "version_code": header.get("version_code"),
        "model_sha256": header.get("model_sha256"),
        "start_calibration_sha256": header.get("start_calibration_sha256"),
        "calibration_changed": footer.get("calibration_changed") if footer else None,
        "counts": {"records": len(records), "raw_observations": len(raw), "mapped_samples": len(samples),
                   "engine_accepted_samples": engine_accepted, "terminal_deliveries": terminal,
                   "raw_reasons": dict(Counter(r.get("reason") for r in raw)), "record_kinds": dict(kinds)},
        "termination": footer.get("termination") if footer else None,
        "compliance": footer.get("compliance") if footer else None,
        "score": score,
        "pattern_record_counts": footer.get("pattern_record_counts") if footer else None,
        "chosen_aids": footer.get("chosen_aids") if footer else None,
        "drawn_proxy_aids": footer.get("drawn_proxy_aids") if footer else None,
        "limits": ["Integrity is distinct from trial completion or task adherence.",
                   "Target agreement is conditional on instructions being followed; no independent eye-position reference.",
                   "Global path alignment ignores capture windows and breaks; it is not event precision.",
                   "Draw receipts are proxies; software output does not validate difficulty or aid benefit."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--output", type=Path, help="Optional local summary JSON path")
    args = parser.parse_args()
    try:
        result = summarize(read_trace(args.trace), final_filename=args.trace.name.endswith(".jsonl"))
    except (OSError, ValueError, TypeError) as error:
        parser.exit(2, f"Pilot trace error: {error}\n")
    output = json.dumps(result, indent=2, ensure_ascii=False, allow_nan=False) + "\n"
    if args.output:
        args.output.write_text(output, encoding="utf-8")
    else:
        print(output, end="")
    return 0 if result["integrity_ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
