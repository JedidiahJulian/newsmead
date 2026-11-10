"""Compact, local notebook views of strict study packages (standard library only).

Catalog values are manifest declarations, never integrity verification. Loading
verifies one selected package with study_package before projecting it. Columns
preserve native names; nested scalar names use underscores, focused arrays use
``_json``. Payload scalars colliding with envelope columns use ``payload_``;
ambiguous aliases reject. Session metadata collisions use ``session_start_``
or ``session_metadata_`` so verified provenance and bounds remain authoritative.
Native aid actions under decision.action use ``decision_action_`` columns.
Nested scalar aliases such as action.id and top-level action_id share a column
only when their native scalar types and values are exactly equal; mismatches
reject rather than overwrite either value.
`load_recording` projects source records without replay, eligibility filtering,
deduplication, or event finalization. The separate `coordinate_analysis` entry
point explicitly fits and replays a compatible reference offline. Offsets and
latency are descriptive differences in elapsed ms.
"""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
import json
import math
from pathlib import Path, PurePosixPath
import statistics
import zipfile

import study_package as sp

VERSION = "study_notebook_v1.1"
TABLES = ("gaze", "rdf", "scanpath", "aids", "boundaries", "health", "references")
MAX_MANIFEST_BYTES = 4 * 1024 * 1024


@dataclass
class NotebookRecording:
    metadata: dict
    tables: dict[str, list[dict]]
    receipt: dict


def _catalog_row(source, member, manifest):
    chunks = manifest.get("chunks", [])
    identity = manifest.get("identity", {})
    return dict(source=str(source), manifest_member=member,
                package_id=manifest.get("package_id"), **{key: identity.get(key) for key in sp.IDENTITY},
                status=manifest.get("status"), content_complete=manifest.get("content_complete"),
                unknown_tail=manifest.get("unknown_tail"), record_count=manifest.get("record_count"),
                stored_bytes=sum(c.get("byte_count", 0) for c in chunks),
                decoded_bytes=sum(c.get("uncompressed_byte_count", c.get("byte_count", 0)) for c in chunks),
                validation="DECLARED_UNVERIFIED")


def catalog_sources(source: Path) -> list[dict]:
    """Read manifests only from a package, its parent, or an exported ZIP.

    Byte totals cover declared chunk bytes, excluding manifest/ZIP overhead.
    Missing chunks and bad hashes are detected only by load_recording.
    """
    source = Path(source)
    result = []
    if source.is_dir():
        paths = [source / "manifest.json"] if (source / "manifest.json").is_file() else sorted(source.glob("*/manifest.json"))
        for path in paths:
            if path.stat().st_size > MAX_MANIFEST_BYTES:
                raise ValueError("manifest exceeds notebook catalog limit")
            result.append(_catalog_row(path.parent, "manifest.json", sp.strict_json(path.read_text(encoding="utf-8"))))
    else:
        with zipfile.ZipFile(source) as archive:
            members = archive.infolist()
            if len(members) > sp.MAX_MEMBERS:
                raise ValueError("ZIP exceeds catalog member limit")
            seen = set()
            for member in members:
                sp.name(member.filename.rstrip("/") if member.is_dir() else member.filename)
                if member.filename in seen or (member.external_attr >> 16) & 0o170000 == 0o120000:
                    raise ValueError("duplicate or symbolic ZIP member")
                seen.add(member.filename)
                if not member.is_dir() and PurePosixPath(member.filename).name == "manifest.json":
                    if member.file_size > MAX_MANIFEST_BYTES:
                        raise ValueError("manifest exceeds notebook catalog limit")
                    result.append(_catalog_row(source, member.filename, sp.strict_json(archive.read(member).decode("utf-8"))))
    return result


def _scalars(target, value, prefix="", collision_prefix=None, allow_equal=False):
    if isinstance(value, dict):
        for key, item in value.items():
            if item is None or isinstance(item, (str, int, float, bool)):
                column = prefix + key
                if column in target:
                    if allow_equal and type(target[column]) is type(item) and target[column] == item:
                        continue
                    if collision_prefix is None:
                        raise ValueError(f"ambiguous projected scalar column: {column}")
                    column = collision_prefix + column
                    if column in target or column in value:
                        raise ValueError(f"ambiguous projected scalar column: {column}")
                target[column] = item


def _nested(target, payload, key, children=(), arrays=()):
    value = payload.get(key)
    if key in payload and value is None:
        target[key] = None
    _scalars(target, value, key + "_", allow_equal=True)
    if isinstance(value, dict):
        for child in children:
            _scalars(target, value.get(child), key + "_" + child + "_", allow_equal=True)
        for child in arrays:
            if child in value:
                target[key + "_" + child + "_json"] = json.dumps(value[child], ensure_ascii=False, separators=(",", ":"))


def _table(kind):
    if kind == "gaze_observation": return "gaze"
    if kind in ("rdf_reference", "rdf_baseline_fit", "coordinate_reference", "shared_baseline_result"): return "references"
    if kind.startswith("rdf_"): return "rdf"
    if kind.startswith("scanpath_") or kind == "layout_snapshot": return "scanpath"
    if kind.startswith("aid_") or kind == "support_revision": return "aids"
    if kind in ("boundary", "session_start", "session_end"): return "boundaries"
    return "health"


def _project(record):
    row = {key: record[key] for key in (*sp.IDENTITY, "package_id", "record_id", "seq", "kind", "recorded_elapsed_ms", "source_elapsed_ms")}
    payload = record["payload"]
    # Envelope identities/times are authoritative. Reserve their aliases before
    # projection so payload ordering cannot change collision handling.
    reserved = set(row)
    for key, value in payload.items():
        if value is None or isinstance(value, (str, int, float, bool)):
            column = "payload_" + key if key in reserved else key
            if column != key and column in payload:
                raise ValueError(f"ambiguous projected scalar column: {column}")
            if column in row:
                raise ValueError(f"ambiguous projected scalar column: {column}")
            row[column] = value
    # Restrict nesting to focused evidence. In particular no full geometry,
    # fixation history, policy context, or presentation settings is retained.
    for key, children, arrays in (
        ("coordinate_context", (), ()), ("feature_vector", ("values",), ()),
        ("score", (), ("delta",)), ("sample", ("motion_compensation",), ()),
        ("output", ("metrics", "event", "fixation", "candidate"), ("fixation_indices", "limitations")),
        ("action", ("anchor",), ()), ("decision", (), ()),
        ("input_context", ("geometry_ref",), ()), ("metadata", (), ()),
        ("reference", (), ("profile",)), ("fit", ("sampling", "stability"), ("profile",)),
        ("config", (), ()),
        ("anchor", (), ()), ("geometry_ref", (), ()),
    ):
        _nested(row, payload, key, children, arrays)
    for key in ("components", "losses", "pending_provider_epochs"):
        if key in payload:
            row[key + "_json"] = json.dumps(payload[key], ensure_ascii=False, separators=(",", ":"))
    decision = payload.get("decision")
    if isinstance(decision, dict):
        # Native aid_decision path is payload.decision.action; preserve its
        # action ID and UTF-16 anchor for scoped offer/render joins.
        action = decision.get("action")
        if "action" in decision and action is None:
            row["decision_action"] = None
        _scalars(row, action, "decision_action_")
        if isinstance(action, dict):
            _scalars(row, action.get("anchor"), "decision_action_anchor_")
    if _table(record["kind"]) == "aids":
        row["verified_visibility"] = False
    if record["kind"] == "scanpath_output":
        if "output_type" in payload:
            row["output_type_basis"] = "native_payload"
        elif payload.get("producer") == "pattern":
            # ArticleFragment.onEvent serializes ScanpathEvent directly.
            row["output_type"] = "direct_pattern_event"
            row["output_type_basis"] = "ArticleFragment.onEvent_direct_ScanpathEvent"
    return row


def load_recording(source: Path, session_id=None) -> NotebookRecording:
    """Strictly verify one package, project compact rows, release original rows.

    A parent directory with several packages requires an explicit session ID.
    This does not compute coordinate MD or resolve separate reference packages.
    """
    source = Path(source)
    if source.is_dir() and not (source / "manifest.json").exists():
        candidates = [r for r in catalog_sources(source) if session_id is None or r.get("session_id") == session_id]
        if len(candidates) != 1:
            raise ValueError("select exactly one package with session_id")
        source = Path(candidates[0]["source"])
    package = sp.load_package(source, session_id=session_id)
    manifest = package.manifest
    metadata = _catalog_row(source, "manifest.json", manifest)
    metadata.update(validation="STRICT_VERIFIED", record_kind_counts=dict(Counter(r["kind"] for r in package.records)),
                    coordinate_md_status="NOT_COMPUTED_NO_REFERENCE", coordinate_md=None)
    starts = [r["payload"] for r in package.records if r["kind"] == "session_start"]
    ends = [r["payload"] for r in package.records if r["kind"] == "session_end"]
    start = starts[0].get("start_ms") if len(starts) == 1 else None
    end = ends[0].get("end_ms") if len(ends) == 1 else None
    metadata.update(start_ms=start, end_ms=end,
                    recorded_span_s=(end - start) / 1000 if sp.finite(start) and sp.finite(end) and end >= start else None,
                    session_start_count=len(starts), session_end_count=len(ends))
    if len(starts) == 1:
        _scalars(metadata, starts[0], collision_prefix="session_start_")
        _scalars(metadata, starts[0].get("metadata"), collision_prefix="session_metadata_")
    tables = {name: [] for name in TABLES}
    screen_contexts = set()
    for record in package.records:
        row = _project(record)
        table = _table(record["kind"])
        if table == "gaze":
            for clock in ("capture", "delivery"):
                value = row.get(clock + "_ms")
                row[clock + "_offset_s"] = (value - start) / 1000 if sp.finite(value) and sp.finite(start) else None
            capture, delivery = row.get("capture_ms"), row.get("delivery_ms")
            context_clock = row.get("coordinate_context_clock_domain_id")
            compatible = context_clock in (None, row["clock_domain_id"])
            row["latency_ms"] = delivery - capture if compatible and sp.finite(capture) and sp.finite(delivery) else None
            row["derived_clock_status"] = "SAME_PACKAGE_ELAPSED_CLOCK" if compatible else "CONTEXT_CLOCK_MISMATCH"
            screen_contexts.add((row.get("coordinate_context_screen_width_px"), row.get("coordinate_context_screen_height_px")))
        tables[table].append(row)
    metadata["screen_dimensions"] = [dict(width_px=w, height_px=h) for w, h in sorted(screen_contexts, key=str)]
    metadata["source_reference_attempts"] = [dict(record_id=r["record_id"], kind=r["kind"], reference_session_id=r.get("reference_session_id"),
        baseline_attempt_id=r.get("baseline_attempt_id"), participant_match=r.get("participant_match"),
        status=r.get("fit_status", r.get("status")), reference_present="reference_session_id" in r or "reference_profile_json" in r,
        fit_profile_present=r.get("fit_profile_json") not in (None, "null"))
        for r in tables["references"]]
    receipt = dict(adapter_version=VERSION, importer_version=sp.VERSION, validation="STRICT_VERIFIED",
                   manifest_sha256=package.manifest_sha256, annotations=package.annotations,
                   declared_losses=manifest["losses"], losses_unknown=manifest.get("losses_unknown", False), unknown_tail=manifest["unknown_tail"],
                   content_complete=manifest["content_complete"], late_receipts=package.late_receipts,
                   late_receipt_source=package.late_receipt_source,
                   table_counts={k: len(v) for k, v in tables.items()},
                   coordinate_md_computed=False)
    return NotebookRecording(metadata, tables, receipt)


def coordinate_analysis(source, session_id=None, reference_source=None, reference_session_id=None,
                        config=None, reference_start_ms=None, reference_end_ms=None):
    """Replay an explicitly declared source from a sibling package or export ZIP.

    An explicit source also supports legacy coordinate_reference sessions. Catalog
    declarations locate bytes only; strict package loading and link validation
    precede scoring. No closest/latest baseline or R/D/F substitution is used.
    """
    source = Path(source)
    candidates = catalog_sources(source)
    selected = [r for r in candidates if session_id is None or r["session_id"] == session_id]
    if len(selected) != 1:
        raise ValueError("select exactly one evaluation package with session_id")
    selected = selected[0]
    evaluation_source = Path(selected["source"])
    package = sp.load_package(evaluation_source, selected["session_id"])
    link, issue = sp.declared_coordinate_reference(package)
    reference = None
    if reference_source is not None or link is not None:
        search = Path(reference_source) if reference_source is not None else (
            source if not source.is_dir() or not (source / "manifest.json").exists() else source.parent)
        refs = catalog_sources(search)
        if reference_source is not None and reference_session_id is not None:
            refs = [r for r in refs if r["session_id"] == reference_session_id]
        elif link is not None:
            refs = [r for r in refs if r["package_id"] == link.get("source_package_id")
                    and r["session_id"] == link.get("source_session_id")]
        if len(refs) == 1:
            reference = sp.load_package(Path(refs[0]["source"]), refs[0]["session_id"])
        elif reference_source is not None:
            raise ValueError("select exactly one reference package with reference_session_id")
        else:
            issue = "REFERENCE_UNAVAILABLE:declared_source_missing_or_ambiguous"
    result = sp.analyze(package, reference, config, reference_start_ms, reference_end_ms)
    if reference is None and issue:
        result["reference_issue"] = issue
    result["coordinate_availability"] = (result["fit"]["status"] if result["fit"] else result["reference_issue"])
    result["rdf_availability"] = dict(Counter(
        r["payload"].get("score", {}).get("status", "UNAVAILABLE")
        if isinstance(r["payload"].get("score"), dict) else "UNAVAILABLE"
        for r in package.records if r["kind"] == "rdf_tick"))
    return result


def cadence_bins(gaze_rows, start_ms, end_ms, bin_seconds=10):
    """Capture-time bins, half-open except inclusive final endpoint.

    Filter to one package/session/clock first. Missing captures are excluded and
    reported, never replaced by delivery time. Rates use each actual bin span;
    coordinate means native reason == 'coordinate', not an accuracy criterion.
    Latency p95 uses linear interpolation at (n-1)*.95, with no clipping.
    """
    if not all(sp.finite(x) for x in (start_ms, end_ms, bin_seconds)) or end_ms <= start_ms or bin_seconds <= 0:
        raise ValueError("finite increasing bounds and positive bin_seconds required")
    rows = list(gaze_rows)
    scopes = {(r.get("package_id"), r.get("session_id"), r.get("clock_domain_id")) for r in rows}
    if len(scopes) > 1 or any(None in scope for scope in scopes):
        raise ValueError("filter to one explicit package/session/clock before cadence")
    if any(r.get("derived_clock_status") == "CONTEXT_CLOCK_MISMATCH" for r in rows):
        raise ValueError("incompatible coordinate context clock")
    missing = sum(not sp.finite(r.get("capture_ms")) for r in rows)
    outside = sum(sp.finite(r.get("capture_ms")) and not start_ms <= r["capture_ms"] <= end_ms for r in rows)
    count = math.ceil((end_ms - start_ms) / (bin_seconds * 1000))
    buckets = [[] for _ in range(count)]
    for row in rows:
        capture = row.get("capture_ms")
        if sp.finite(capture) and start_ms <= capture <= end_ms:
            index = min(int((capture - start_ms) // (bin_seconds * 1000)), count - 1)
            buckets[index].append(row)
    result = []
    for index, bucket in enumerate(buckets):
        lo = start_ms + index * bin_seconds * 1000
        hi = min(end_ms, lo + bin_seconds * 1000)
        span = (hi - lo) / 1000
        row = dict(start_ms=lo, end_ms=hi, span_s=span, missing_capture_count_total=missing, outside_capture_count_total=outside,
                   denominator="recorded_elapsed_span_not_active_reading", time_basis="capture_ms")
        if scopes:
            row.update(zip(("package_id", "session_id", "clock_domain_id"), next(iter(scopes))))
        for label, predicate in (("all", lambda r: True), ("coordinate", lambda r: r.get("reason") == "coordinate"),
                ("context_allowed", lambda r: r.get("context_allowed") is True),
                ("coordinate_context_allowed", lambda r: r.get("reason") == "coordinate" and r.get("context_allowed") is True)):
            row[label + "_count"] = sum(predicate(r) for r in bucket)
            row[label + "_hz"] = row[label + "_count"] / span
        latency = sorted(r["latency_ms"] for r in bucket if sp.finite(r.get("latency_ms")))
        pos = (len(latency) - 1) * .95
        row["latency_count"] = len(latency)
        row["median_latency_ms"] = statistics.median(latency) if latency else None
        row["p95_latency_ms"] = latency[math.floor(pos)] + (pos % 1) * (latency[math.ceil(pos)] - latency[math.floor(pos)]) if latency else None
        result.append(row)
    return result
