"""Strict, local-only newsmead_study_v1 import and descriptive coordinate-MD replay.

Usage: python tools/gaze/study_package.py PACKAGE --output OUTPUT [--reference PACKAGE]
Source bytes are never changed. ZIP members are read in place, never extracted.
"""
from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass, field
import gzip
import hashlib
import io
import json
import math
from pathlib import Path, PurePosixPath
import re
import zipfile

import coordinate_mahalanobis as md

SCHEMA = "newsmead_study_v1"
VERSION = "newsmead_study_import_v1.3"
IDENTITY = ("study_id", "participant_id", "installation_id", "run_id", "session_id", "clock_domain_id")
MAX_BYTES = 512 * 1024 * 1024
MAX_MEMBERS = 10000
MAX_CHUNK_BYTES = 64 * 1024 * 1024
GZIP_ENCODING = "gzip_jsonl_v1"


def decode_chunk(raw, chunk, remaining_bytes):
    """Lossless storage decoding, bounded independently of ZIP/compressed size."""
    encoding = chunk.get("encoding")
    compressed = chunk["file"].endswith(".jsonl.gz")
    if encoding is None:
        if chunk["file"].endswith(".gz"):
            raise ValueError("compressed chunk requires explicit encoding")
        expected = integer(chunk.get("uncompressed_byte_count", len(raw)), "uncompressed_byte_count")
        if expected != len(raw):
            raise ValueError("uncompressed byte count mismatch")
    elif encoding == GZIP_ENCODING and compressed:
        expected = integer(chunk["uncompressed_byte_count"], "uncompressed_byte_count")
    else:
        raise ValueError("unsupported chunk encoding or filename")
    if expected > remaining_bytes:
        raise ValueError("decoded package exceeds import byte limit")
    # Legacy plain chunks retain their original package-level budget. The new
    # compressed container additionally bounds each expansion before allocating.
    if encoding == GZIP_ENCODING:
        if expected > MAX_CHUNK_BYTES:
            raise ValueError("decoded chunk exceeds import byte limit")
        try:
            with gzip.GzipFile(fileobj=io.BytesIO(raw), mode="rb") as stream:
                decoded = stream.read(expected + 1)
        except (OSError, EOFError) as error:
            raise ValueError("corrupt or truncated gzip chunk") from error
        if len(decoded) != expected:
            raise ValueError("uncompressed byte count mismatch")
        return decoded
    return raw


def strict_json(data):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError(f"duplicate JSON key: {key}")
            result[key] = value
        return result
    def invalid(value):
        raise ValueError(f"nonfinite JSON number: {value}")
    result = json.loads(data, object_pairs_hook=pairs, parse_constant=invalid)
    def check(value):
        if isinstance(value, float) and not math.isfinite(value):
            raise ValueError("nonfinite JSON number")
        if isinstance(value, dict):
            for item in value.values():
                check(item)
        elif isinstance(value, list):
            for item in value:
                check(item)
    check(result)
    return result


def integer(value, label, minimum=0):
    if type(value) is not int or value < minimum:
        raise ValueError(f"invalid {label}")
    return value


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def placement(payload):
    capture = payload.get("capture_ms")
    return capture if finite(capture) else payload.get("delivery_ms")


def timestamp(value, label, nullable=False):
    if nullable and value is None:
        return
    if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
        raise ValueError(f"invalid {label}")


def name(value):
    if (not isinstance(value, str) or not value or "\\" in value or ":" in value
            or PurePosixPath(value).is_absolute() or any(p in ("", ".", "..") for p in value.split("/"))):
        raise ValueError("unsafe package member path")
    return value


@dataclass
class Package:
    manifest: dict
    records: list
    losses: list
    annotations: list
    manifest_sha256: str
    late_receipts: list = field(default_factory=list)
    late_receipt_source: str = "none_declared"


def export_late_receipts(archive, manifest, manifest_digest, records):
    """Verify selected-package sidecars without repairing the sealed package."""
    export = strict_json(archive.read("study_export_manifest.json").decode("utf-8"))
    if export["schema_version"] != "newsmead_study_export_v1":
        raise ValueError("unsupported study export schema")
    if not isinstance(export["packages"], list) or not isinstance(export["late_receipts"], list):
        raise ValueError("invalid study export manifest lists")
    packages = export["packages"]
    package_ids = set()
    sessions = set()
    for entry in packages:
        if (not isinstance(entry["package_id"], str) or not entry["package_id"]
                or entry["package_id"] in package_ids or not isinstance(entry["session_id"], str)
                or not entry["session_id"] or not isinstance(entry["manifest_sha256"], str)
                or not re.fullmatch(r"[0-9a-f]{64}", entry["manifest_sha256"])):
            raise ValueError("invalid/duplicate export package entry")
        package_ids.add(entry["package_id"])
        sessions.add(entry["session_id"])
    matching = [entry for entry in packages if entry["package_id"] == manifest["package_id"]]
    session_id = manifest["identity"]["session_id"]
    if (len(matching) != 1 or matching[0]["session_id"] != session_id
            or matching[0]["manifest_sha256"] != manifest_digest):
        raise ValueError("selected package does not match export manifest")
    providers = {r["payload"]["provider_epoch_id"] for r in records
                 if isinstance(r["payload"].get("provider_epoch_id"), str)
                 and r["payload"]["provider_epoch_id"].strip()}
    results = []
    paths = set()
    for entry in export["late_receipts"]:
        member = name(entry["file"])
        if not member.startswith("late_receipts/") or member in paths:
            raise ValueError("invalid/duplicate late receipt path")
        paths.add(member)
        if (entry["session_id"] not in sessions or not isinstance(entry["provider_epoch_id"], str)
                or not entry["provider_epoch_id"].strip()):
            raise ValueError("late receipt has no export session/provider linkage")
        integer(entry["byte_count"], "late receipt byte_count")
        if not isinstance(entry["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]):
            raise ValueError("invalid late receipt hash")
        if entry["session_id"] != session_id:
            continue
        raw = archive.read(member)
        if len(raw) != entry["byte_count"] or hashlib.sha256(raw).hexdigest() != entry["sha256"]:
            raise ValueError("late receipt byte count/hash mismatch")
        document = strict_json(raw.decode("utf-8"))
        if document["session_id"] != session_id or document["session_id"] != entry["session_id"]:
            raise ValueError("late receipt session mismatch")
        if (document["provider_epoch_id"] != entry["provider_epoch_id"]
                or document["provider_epoch_id"] not in providers):
            raise ValueError("late receipt provider linkage mismatch")
        timestamp(document["received_elapsed_ms"], "late receipt received_elapsed_ms")
        integer(document["arrivals"], "late receipt arrivals")
        integer(document["busy_drops"], "late receipt busy_drops")
        if document["extends_reading_interval"] is not False:
            raise ValueError("late receipt must not extend reading interval")
        results.append({"export_entry": entry, "receipt": document})
    return results


def load_package(source, session_id=None):
    source = Path(source)
    archive = None
    total_read = 0
    if source.is_dir():
        root = source.resolve()
        def read(member):
            nonlocal total_read
            target = (root / name(member)).resolve()
            if not target.is_relative_to(root):
                raise ValueError("package path escapes source")
            size = target.stat().st_size
            total_read += size
            if total_read > MAX_BYTES:
                raise ValueError("package exceeds import byte limit")
            return target.read_bytes()
    else:
        archive = zipfile.ZipFile(source)
        members = archive.infolist()
        if len(members) > MAX_MEMBERS or sum(m.file_size for m in members) > MAX_BYTES:
            archive.close()
            raise ValueError("ZIP exceeds import limits")
        seen = set()
        for member in members:
            name(member.filename.rstrip("/") if member.is_dir() else member.filename)
            if member.filename in seen or (member.external_attr >> 16) & 0o170000 == 0o120000:
                archive.close()
                raise ValueError("duplicate or symbolic ZIP member")
            seen.add(member.filename)
        manifests = [m.filename for m in members if not m.is_dir()
                     and PurePosixPath(m.filename).name == "manifest.json"]
        candidates = []
        for manifest_path in manifests:
            candidate = strict_json(archive.read(manifest_path).decode("utf-8"))
            if session_id is None or candidate.get("identity", {}).get("session_id") == session_id:
                candidates.append(manifest_path)
        if len(candidates) != 1:
            archive.close()
            raise ValueError("ZIP must select exactly one package; supply --session-id for multiple packages")
        prefix = candidates[0][:-len("manifest.json")]
        def read(member):
            return archive.read(prefix + name(member))
    try:
        manifest_bytes = read("manifest.json")
        manifest = strict_json(manifest_bytes.decode("utf-8"))
        if manifest["schema_version"] != SCHEMA or manifest["status"] not in ("finished", "recovered_incomplete"):
            raise ValueError("unsupported package schema/status")
        package_id = manifest["package_id"]
        if not isinstance(package_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]+", package_id):
            raise ValueError("invalid package ID")
        identity = manifest["identity"]
        if session_id is not None and identity.get("session_id") != session_id:
            raise ValueError("session ID does not match package")
        for key in IDENTITY:
            if not isinstance(identity[key], str) or not identity[key].strip():
                raise ValueError(f"invalid identity: {key}")
        for key in ("content_complete", "unknown_tail"):
            if type(manifest[key]) is not bool:
                raise ValueError(f"invalid {key}")
        if not isinstance(manifest["losses"], list):
            raise ValueError("losses must be explicit list")
        annotations = []
        losses = []
        declared = []
        for loss in manifest["losses"]:
            first = integer(loss["first_seq"], "loss first_seq")
            last = integer(loss["last_seq"], "loss last_seq")
            count = integer(loss["count"], "loss count", 1)
            if last < first or count > last - first + 1:
                raise ValueError("invalid loss sequence range")
            if type(loss["exact"]) is not bool or (loss["exact"] and count != last - first + 1):
                raise ValueError("invalid exact loss range")
            declared.append((first, last, count, loss["exact"]))
            # Endpoint source times do not bound every interior dropped capture.
            # Until the writer supplies certified capture-time bounds, conservative.
            losses.append(md.LossRange(None, None, "declared_recorder_loss_unknown_capture_bounds"))
        unknown = manifest.get("losses_unknown", False)
        if type(unknown) is not bool:
            raise ValueError("invalid losses_unknown")
        if unknown:
            losses.append(md.LossRange(None, None, "unknown_recorder_losses"))
            annotations.append("losses_unknown")
        if manifest["unknown_tail"]:
            losses.append(md.LossRange(None, None, "unknown_tail_capture_bounds"))
            annotations.append("unknown_tail")
        if manifest["content_complete"] and (losses or manifest["status"] != "finished"):
            raise ValueError("content_complete contradicts recovery/loss metadata")
        if not manifest["content_complete"] and not losses:
            losses.append(md.LossRange(None, None, "incomplete_package_unknown_loss"))
            annotations.append("incomplete_without_loss_bounds")
        rows = []
        ids = set()
        paths = set()
        previous = -1
        gaps = []
        decoded_bytes = 0
        for chunk in manifest["chunks"]:
            member = name(chunk["file"])
            if member == "manifest.json" or member in paths:
                raise ValueError("duplicate/reserved chunk path")
            paths.add(member)
            raw = read(member)
            if len(raw) != integer(chunk["byte_count"], "byte_count") or hashlib.sha256(raw).hexdigest() != chunk["sha256"]:
                raise ValueError("chunk byte count/hash mismatch")
            raw = decode_chunk(raw, chunk, MAX_BYTES - decoded_bytes)
            decoded_bytes += len(raw)
            if raw and not raw.endswith(b"\n"):
                raise ValueError("unterminated chunk row")
            chunk_rows = []
            for line in raw.splitlines():
                row = strict_json(line.decode("utf-8"))
                seq = integer(row["seq"], "seq")
                if row["schema_version"] != SCHEMA or row.get("package_id") != package_id:
                    raise ValueError("record package/schema mismatch")
                if any(row[key] != identity[key] for key in IDENTITY):
                    raise ValueError("record identity mismatch")
                if not isinstance(row["record_id"], str) or not row["record_id"] or row["record_id"] in ids:
                    raise ValueError("duplicate/invalid record ID")
                if seq <= previous:
                    raise ValueError("nonincreasing record sequence")
                if seq > previous + 1:
                    gaps.append((previous + 1, seq - 1))
                previous = seq
                ids.add(row["record_id"])
                if not isinstance(row["kind"], str) or not row["kind"] or not isinstance(row["payload"], dict):
                    raise ValueError("invalid record kind/payload")
                timestamp(row["recorded_elapsed_ms"], "recorded_elapsed_ms")
                timestamp(row["source_elapsed_ms"], "source_elapsed_ms", True)
                chunk_rows.append(row)
            if len(chunk_rows) != integer(chunk["record_count"], "chunk record_count", 1):
                raise ValueError("chunk record count mismatch")
            if chunk_rows[0]["seq"] != chunk["first_seq"] or chunk_rows[-1]["seq"] != chunk["last_seq"]:
                raise ValueError("chunk sequence bounds mismatch")
            rows.extend(chunk_rows)
        if len(rows) != integer(manifest["record_count"], "record_count"):
            raise ValueError("package record count mismatch")
        for first, last in gaps:
            cursor = first
            for lo, hi, _, _ in sorted(declared):
                if lo <= cursor <= hi:
                    cursor = hi + 1
            if cursor <= last and not unknown:
                raise ValueError("undeclared sequence gap")
        present = {r["seq"] for r in rows}
        for lo, hi, count, exact in declared:
            missing = hi - lo + 1 - sum(lo <= s <= hi for s in present)
            if missing != count or (exact and any(lo <= s <= hi for s in present)):
                raise ValueError("loss count contradicts retained records")
        for a, b in zip(sorted(declared), sorted(declared)[1:]):
            if b[0] <= a[1]:
                raise ValueError("overlapping declared loss ranges")
        manifest_digest = hashlib.sha256(manifest_bytes).hexdigest()
        late_receipts = []
        late_receipt_source = "external_receipts_not_discoverable_from_package_directory"
        if archive:
            late_receipt_source = "no_export_manifest"
            if "study_export_manifest.json" in archive.namelist():
                late_receipts = export_late_receipts(archive, manifest, manifest_digest, rows)
                late_receipt_source = "verified_export_manifest"
        return Package(manifest, rows, losses, annotations, manifest_digest,
                       late_receipts, late_receipt_source)
    finally:
        if archive:
            archive.close()


def observations(package):
    result = []
    for row in package.records:
        if row["kind"] != "gaze_observation":
            continue
        p = row["payload"]
        if p["coordinate_context"] is None:
            result.append((row, None))
            continue
        context = md.Context(**p["coordinate_context"])
        for key in ("participant_id", "installation_id", "clock_domain_id"):
            if getattr(context, key) != row[key]:
                raise ValueError("coordinate context contradicts envelope identity")
        # Preserve raw and selected as separate declared streams; never substitute.
        prefix = "raw" if context.coordinate_stream == md.RAW_STREAM else "selected"
        observation = md.Observation(context, p["provider_epoch_id"], p["context_epoch_id"],
            p["delivery_id"], p["capture_ms"], p["delivery_ms"], p[prefix + "_x_px"],
            p[prefix + "_y_px"], p["reason"], p["timeout_watchdog"], p["context_allowed"])
        result.append((row, observation))
    return result


def boundaries(package):
    return [md.Boundary(r["payload"]["at_ms"], r["payload"]["reason"])
            for r in package.records if r["kind"] == "boundary"]


SHARED_BASELINE_PROTOCOL = "shared_reading_baseline_v1"
READING_SURFACE_CONTRACT = "article_reading_surface_v1"
PENDING_OFFLINE_FIT = "RECORDED_PENDING_OFFLINE_FIT"


def declared_coordinate_reference(package):
    """Return exactly one recorded link; never infer it from an R/D/F profile."""
    rows = [r["payload"] for r in package.records if r["kind"] == "coordinate_reference"]
    if not rows:
        return None, "REFERENCE_UNAVAILABLE:no_declared_coordinate_reference"
    if len(rows) != 1 or "reference" not in rows[0]:
        return None, "REFERENCE_MISMATCH:ambiguous_coordinate_reference"
    link = rows[0]["reference"]
    if link is None:
        return None, "REFERENCE_UNAVAILABLE:no_frozen_coordinate_reference"
    if not isinstance(link, dict):
        return None, "REFERENCE_MISMATCH:invalid_coordinate_reference"
    return link, None


def shared_baseline_period(reference):
    """Validate the explicit completed joint capture independently of R/D/F fit."""
    starts = [r["payload"] for r in reference.records if r["kind"] == "session_start"]
    ends = [r["payload"] for r in reference.records if r["kind"] == "session_end"]
    results = [r["payload"] for r in reference.records if r["kind"] == "shared_baseline_result"]
    if len(starts) != 1 or starts[0].get("phase") != "reading_baseline":
        return None, "REFERENCE_UNAVAILABLE:not_shared_reading_baseline"
    meta = starts[0].get("metadata", {})
    identity = reference.manifest["identity"]
    if (meta.get("baseline_protocol") != SHARED_BASELINE_PROTOCOL
            or meta.get("coordinate_reference_declared") is not True
            or meta.get("coordinate_reference_contract") != READING_SURFACE_CONTRACT
            or not isinstance(meta.get("baseline_attempt_id"), str) or not meta["baseline_attempt_id"].strip()
            or not isinstance(meta.get("calibration_id"), str) or not meta["calibration_id"].strip()
            or meta.get("baseline_participant_code") != identity["participant_id"]):
        return None, "REFERENCE_MISMATCH:shared_baseline_declaration"
    if len(ends) != 1 or len(results) != 1 or results[0].get("completed") is not True:
        return None, "REFERENCE_UNAVAILABLE:shared_baseline_not_completed"
    result = results[0]
    if (result.get("baseline_attempt_id") != meta["baseline_attempt_id"]
            or result.get("coordinate_reference_status") != PENDING_OFFLINE_FIT):
        return None, "REFERENCE_MISMATCH:shared_baseline_result"
    start, end = result.get("start_ms"), result.get("end_ms")
    if not finite(start) or not finite(end) or not 0 <= start < end:
        return None, "REFERENCE_MISMATCH:shared_baseline_bounds"
    if not (finite(starts[0].get("start_ms")) and finite(ends[0].get("end_ms"))
            and starts[0]["start_ms"] <= start < end <= ends[0]["end_ms"]):
        return None, "REFERENCE_MISMATCH:shared_baseline_bounds"
    return dict(start_ms=start, end_ms=end, baseline_attempt_id=meta["baseline_attempt_id"],
                calibration_id=meta["calibration_id"]), None


def shared_reference_link_issue(package, reference):
    link, issue = declared_coordinate_reference(package)
    if issue:
        return issue
    period, issue = shared_baseline_period(reference)
    if issue:
        return issue
    identity = reference.manifest["identity"]
    own = package.manifest["identity"]
    expected = dict(protocol=SHARED_BASELINE_PROTOCOL, source_package_id=reference.manifest["package_id"],
        source_session_id=identity["session_id"], baseline_attempt_id=period["baseline_attempt_id"],
        participant_id=identity["participant_id"], installation_id=identity["installation_id"],
        calibration_id=period["calibration_id"], clock_domain_id=identity["clock_domain_id"],
        start_ms=period["start_ms"], end_ms=period["end_ms"], status=PENDING_OFFLINE_FIT)
    if any(link.get(k) != v or (k in ("start_ms", "end_ms") and not finite(link.get(k)))
           for k, v in expected.items()):
        return "REFERENCE_MISMATCH:declared_source_link"
    if own["session_id"] == identity["session_id"]:
        return "REFERENCE_MISMATCH:self_reference_session"
    if any(own[k] != identity[k] for k in ("participant_id", "installation_id", "clock_domain_id")):
        return "REFERENCE_MISMATCH:evaluation_identity"
    starts = [r["payload"] for r in package.records if r["kind"] == "session_start"]
    if (len(starts) != 1 or starts[0].get("metadata", {}).get("calibration_id") != period["calibration_id"]):
        return "REFERENCE_MISMATCH:evaluation_calibration"
    if not finite(starts[0].get("start_ms")) or starts[0]["start_ms"] <= period["end_ms"]:
        return "REFERENCE_MISMATCH:reference_not_earlier"
    for source in (reference, package):
        for row, observation in observations(source):
            if observation is not None and observation.context.calibration_id != period["calibration_id"]:
                return "REFERENCE_MISMATCH:declared_calibration_context"
    return None


def reference_fit(reference, config, reference_start_ms=None, reference_end_ms=None):
    if (reference_start_ms is None) != (reference_end_ms is None):
        raise ValueError("reference start/end overrides must be supplied together")
    if reference_start_ms is not None:
        md._bounds(reference_start_ms, reference_end_ms)
    starts = [r for r in reference.records if r["kind"] == "session_start"]
    ends = [r for r in reference.records if r["kind"] == "session_end"]
    if len(starts) != 1 or starts[0]["payload"].get("phase") not in ("coordinate_reference", "reading_baseline"):
        raise ValueError("reference requires one explicit coordinate_reference or reading_baseline session")
    shared = starts[0]["payload"].get("phase") == "reading_baseline"
    period, issue = shared_baseline_period(reference) if shared else (None, None)
    if issue:
        return None, issue
    if len(ends) != 1:
        return None, "REFERENCE_UNAVAILABLE:missing_explicit_session_end"
    start = starts[0]["payload"]["start_ms"]
    end = ends[0]["payload"]["end_ms"]
    if shared:
        start, end = period["start_ms"], period["end_ms"]
    md._bounds(start, end)
    if reference_start_ms is not None:
        if reference_start_ms < start or reference_end_ms > end:
            raise ValueError("reference overrides must stay within declared session bounds")
        start, end = reference_start_ms, reference_end_ms
    source = observations(reference)
    # Finite placement explicitly segments the period; unplaceable rows remain adjacency breaks.
    in_bound = [(row, o) for row, o in source
                if not finite(placement(row["payload"])) or start <= placement(row["payload"]) <= end]
    if any(o is None for _, o in in_bound):
        return None, "REFERENCE_UNAVAILABLE:context_unavailable"
    if len({o.context for _, o in in_bound}) > 1:
        return None, "REFERENCE_MISMATCH:multiple_reference_contexts"
    selected = [o for _, o in in_bound]
    if not selected:
        return None, "REFERENCE_UNAVAILABLE:no_in_bound_observations"
    provenance = {"source_package_id": reference.manifest["package_id"],
        "source_manifest_sha256": reference.manifest_sha256,
        "start_ms": float(start), "end_ms": float(end),
        "context": md.json_value(selected[0].context), "config": md.json_value(config),
        "core_version": md.VERSION}
    canonical = json.dumps(provenance, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    reference_id = "coordinate_md_reference_" + hashlib.sha256(canonical).hexdigest()
    return md.fit_reference(reference_id, selected[0].context,
        selected, start, end, config, boundaries(reference), reference.losses), None


def analyze(package, reference=None, config=None, reference_start_ms=None, reference_end_ms=None):
    if (reference_start_ms is None) != (reference_end_ms is None):
        raise ValueError("reference start/end overrides must be supplied together")
    if reference_start_ms is not None and reference is None:
        raise ValueError("reference bounds require a separate reference package")
    config = config or md.Config()
    # Snapshot once: each native window/context uses the same recorded boundaries.
    evaluation_boundaries = tuple(boundaries(package))
    if reference and reference.manifest["package_id"] == package.manifest["package_id"]:
        raise ValueError("evaluation package cannot be its own reference")
    link, _ = declared_coordinate_reference(package)
    shared_source = reference and any(r["kind"] == "session_start" and r["payload"].get("phase") == "reading_baseline"
                                      for r in reference.records)
    shared_link = link is not None and link.get("protocol") == SHARED_BASELINE_PROTOCOL
    issue = shared_reference_link_issue(package, reference) if reference and (shared_source or shared_link) else None
    if issue:
        fit, reference_issue = None, issue
    else:
        fit, reference_issue = (reference_fit(reference, config, reference_start_ms, reference_end_ms)
                                if reference else (None, "REFERENCE_UNAVAILABLE:no_reference_selected"))
    profile = fit.profile if fit else None
    source = observations(package)
    all_observations = [o for _, o in source if o is not None]
    missing_context = [r for r, o in source if o is None]
    scorers = {}
    points = []
    for row, o in source:
        if o is None:
            points.append({"record_id": row["record_id"], "seq": row["seq"],
                "result": {"status": "CONTEXT_UNAVAILABLE", "reference_id": profile.reference_id if profile else None,
                           "md": None, "md2": None}, "adapter_status": "CONTEXT_UNAVAILABLE"})
            continue
        if o.context not in scorers:
            scorers[o.context] = md.CoordinateScorer(profile, o.context, config)
        scorer = scorers[o.context]
        points.append({"record_id": row["record_id"], "seq": row["seq"],
                       "result": md.json_value(scorer.score(o))})
    windows = []
    unavailable_windows = []
    for row in package.records:
        if row["kind"] == "rdf_tick":
            vector = row["payload"]["feature_vector"]
            start, end = vector["window_start_ms"], vector["window_end_ms"]
            if finite(start) and finite(end) and start >= 0 and start == end and vector.get("quality") == "INSUFFICIENT":
                # A native pipeline reset can emit its first tick before a span exists.
                # Preserve the source row and report unavailability without inventing bounds.
                unavailable_windows.append({"window_id": row["record_id"], "bounds_basis": "rdf_window", "context": None,
                    "result": {"start_ms": start, "end_ms": end, "status": "INSUFFICIENT_SOURCE_WINDOW",
                               "reference_id": profile.reference_id if profile else None,
                               "mean_md2": None, "coverage_fraction": None, "stats": None},
                    "adapter_status": "ZERO_SPAN_NATIVE_WARMUP", "source_quality": vector["quality"],
                    "adapter_missing_context_records": []})
                continue
            md._bounds(start, end)
            windows.append((row["record_id"], "rdf_window", start, end, all_observations))
    if not windows and not unavailable_windows:
        # Contiguous runs only: reused epoch names after transitions never bridge gaps.
        run = []
        def add_run():
            placed = [o.capture_ms for o in run if finite(o.capture_ms) and o.capture_ms >= 0]
            if len(placed) > 1 and max(placed) > min(placed):
                cuts = sorted({min(placed), max(placed)} | {b.at_ms for b in evaluation_boundaries
                              if min(placed) < b.at_ms < max(placed)})
                for start, end in zip(cuts, cuts[1:]):
                    windows.append((f"epoch:{len(windows)}", "stationary_observed_bounds", start, end, list(run)))
        for o in all_observations:
            if run and (o.context, o.provider_epoch_id, o.context_epoch_id) != (run[-1].context, run[-1].provider_epoch_id, run[-1].context_epoch_id):
                add_run()
                run = []
            run.append(o)
        add_run()
    summaries = list(unavailable_windows)
    for window_id, basis, start, end, obs in windows:
        # Candidate identities come only from this window. Keep the full ordered
        # observations for the core's clock, delivery and adjacency validation.
        window_contexts = list(dict.fromkeys(o.context for o in obs
            if finite(at := o.capture_ms if finite(o.capture_ms) else o.delivery_ms)
            and start <= at <= end))
        if not window_contexts:
            summaries.append({"window_id": window_id, "bounds_basis": basis, "context": None,
                "result": {"start_ms": start, "end_ms": end, "status": "CONTEXT_UNAVAILABLE",
                           "reference_id": profile.reference_id if profile else None,
                           "mean_md2": None, "coverage_fraction": 0.0, "stats": None},
                "adapter_missing_context_records": [r["record_id"] for r in missing_context]})
        for context in window_contexts:
            summary = md.json_value(md.summarize(profile, context, obs, start, end,
                                                 evaluation_boundaries, package.losses, config))
            missing = []
            for row in missing_context:
                p = row["payload"]
                at = placement(p)
                if not finite(at) or start <= at <= end:
                    missing.append(row["record_id"])
            if missing:
                summary.update(status="CONTEXT_UNAVAILABLE", mean_md2=None)
            summaries.append({"window_id": window_id, "bounds_basis": basis,
                "context": md.json_value(context), "result": summary,
                "adapter_missing_context_records": missing})
    if unavailable_windows:
        # Native reset windows stay at their original append position; a single
        # window's multiple context summaries retain their stable relative order.
        rank = {row["record_id"]: i for i, row in enumerate(package.records) if row["kind"] == "rdf_tick"}
        summaries.sort(key=lambda item: rank[item["window_id"]])
    return {"analysis_version": md.VERSION, "fit": md.json_value(fit), "points": points,
            "summaries": summaries, "config": md.json_value(config), "reference_issue": reference_issue,
            "reference_source_package_id": reference.manifest["package_id"] if reference else None,
            "reference_source_manifest_sha256": reference.manifest_sha256 if reference else None,
            "reference_source_identity": reference.manifest["identity"] if reference else None,
            "coordinate_reference_declaration": declared_coordinate_reference(package)[0],
            "reference_bounds_override": ({"start_ms": reference_start_ms, "end_ms": reference_end_ms,
                "selection": "explicit_caller_bounds_no_automatic_selection"} if reference_start_ms is not None else None)}


GROUPED_KINDS = {
    "sessions_v1": {"session_start", "session_end"},
    "gaze_observations_v1": {"gaze_observation"},
    "rdf_windows_v1": {"rdf_tick", "rdf_baseline_window"},
    "rdf_references_v1": {"rdf_reference", "rdf_baseline_fit"},
    "coordinate_references_v1": {"coordinate_reference", "shared_baseline_result"},
    "scanpath_v1": {"scanpath_input", "scanpath_acceptance", "scanpath_output", "scanpath_layout"},
    "aid_evidence_v1": {"support_revision", "aid_evaluation"},
    "aid_events_v1": {"aid_decision", "aid_presentation"},
    "boundaries_v1": {"boundary"},
    "collector_health_v1": {"provider_close_state"},
}


def grouped_tables(records):
    """Partition recognized source kinds; prefix payload columns without inference."""
    result = {table: [] for table in GROUPED_KINDS}
    for record in records:
        kind = record["kind"]
        table = next((table for table, kinds in GROUPED_KINDS.items() if kind in kinds), None)
        if table is None and kind.startswith("aid_runtime_"):
            table = "aid_events_v1"
        if table is None and kind.startswith(("writer_", "package_", "collector_")):
            table = "collector_health_v1"
        if table is None:
            continue
        row = {key: value for key, value in record.items() if key != "payload"}
        for key, value in record["payload"].items():
            column = "payload_" + key
            if column in row:
                raise ValueError(f"grouped table payload column collides with envelope: {column}")
            row[column] = value
        result[table].append(row)
    return result


def write_table(output, table, rows):
    with (output / f"{table}.jsonl").open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, allow_nan=False) + "\n")
    keys = sorted({key for row in rows for key in row})
    with (output / f"{table}.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=keys)
        writer.writeheader()
        for row in rows:
            writer.writerow({k: json.dumps(v, ensure_ascii=False, allow_nan=False) if isinstance(v, (dict, list))
                             else v for k, v in row.items()})


def import_package(source, output, reference_source=None, session_id=None, reference_session_id=None,
                   reference_start_ms=None, reference_end_ms=None):
    package = load_package(source, session_id)
    reference = load_package(reference_source, reference_session_id) if reference_source else None
    result = analyze(package, reference, reference_start_ms=reference_start_ms, reference_end_ms=reference_end_ms)
    output = Path(output).resolve()
    for path in (source, reference_source):
        if path is not None:
            resolved = Path(path).resolve()
            if output == resolved or (resolved.is_dir() and output.is_relative_to(resolved)):
                raise ValueError("output must be separate from source package")
    if output.exists() and any(output.iterdir()):
        raise ValueError("output directory must be empty")
    tables = {"records_v1": package.records, "late_receipts_v1": package.late_receipts,
              "coordinate_points_v1": result.pop("points"),
              "coordinate_summaries_v1": result.pop("summaries"), **grouped_tables(package.records)}
    output.mkdir(parents=True, exist_ok=True)
    for table, rows in tables.items():
        write_table(output, table, rows)
    receipt = {"import_version": VERSION, "package_id": package.manifest["package_id"],
        "manifest_sha256": package.manifest_sha256, "manifest": package.manifest,
        "annotations": package.annotations, "analysis": result,
        "table_counts": {table: len(rows) for table, rows in tables.items()},
        "grouped_table_payload_columns": "payload_<original_key>; native nested values retained; collisions rejected",
        "late_receipts": package.late_receipts, "late_receipt_source": package.late_receipt_source,
        "late_receipts_change_original_completeness_or_md_availability": False,
        "reference_package_id": reference.manifest["package_id"] if reference else None,
        "reference_manifest_sha256": reference.manifest_sha256 if reference else None,
        "reference_annotations": reference.annotations if reference else [],
        "reference_late_receipts": reference.late_receipts if reference else [],
        "reference_late_receipt_source": reference.late_receipt_source if reference else None,
        "units": {"coordinates": "physical_screen_pixels", "time": "elapsed_milliseconds",
                  "distance": "dimensionless"},
        "validation_limit": "integrity and deterministic descriptive replay; no participant accuracy/validity claim"}
    (output / "receipt.json").write_text(json.dumps(receipt, indent=2, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reference", type=Path)
    parser.add_argument("--session-id", help="explicit session selection within an export ZIP")
    parser.add_argument("--reference-session-id", help="explicit reference session selection within an export ZIP")
    parser.add_argument("--reference-start-ms", type=float,
                        help="explicit stationary reference start, within declared session bounds; requires --reference-end-ms")
    parser.add_argument("--reference-end-ms", type=float,
                        help="explicit stationary reference end, within declared session bounds; requires --reference-start-ms")
    args = parser.parse_args()
    try:
        import_package(args.package, args.output, args.reference, args.session_id, args.reference_session_id,
                       args.reference_start_ms, args.reference_end_ms)
    except (ValueError, TypeError, KeyError, OSError, zipfile.BadZipFile) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
