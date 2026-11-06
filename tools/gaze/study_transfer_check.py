"""Verify a locally received study export and inventory its explicit references.

No reference is selected automatically. This is collection verification, not
reading-benefit analysis or proof of gaze accuracy.
"""
import argparse
import collections
import json
from pathlib import Path, PurePosixPath
import zipfile

import study_package as sp


def session_ids(source):
    source = Path(source)
    if source.is_dir():
        return [sp.strict_json((source / "manifest.json").read_text(encoding="utf-8"))["identity"]["session_id"]]
    with zipfile.ZipFile(source) as archive:
        members = archive.infolist()
        if len(members) > sp.MAX_MEMBERS or sum(m.file_size for m in members) > sp.MAX_BYTES:
            raise ValueError("ZIP exceeds import limits")
        result = []
        for member in members:
            sp.name(member.filename.rstrip("/") if member.is_dir() else member.filename)
            if not member.is_dir() and PurePosixPath(member.filename).name == "manifest.json":
                if member.file_size > 1024 * 1024:
                    raise ValueError("manifest exceeds metadata limit")
                result.append(sp.strict_json(archive.read(member).decode("utf-8"))["identity"]["session_id"])
        if not result or len(result) != len(set(result)):
            raise ValueError("missing or duplicate session IDs in export")
        return result


def inventory(package):
    rows = package.records
    starts = [r["payload"] for r in rows if r["kind"] == "session_start"]
    ends = [r["payload"] for r in rows if r["kind"] == "session_end"]
    start = starts[0] if len(starts) == 1 else {}
    meta = start.get("metadata", {})
    contexts = {}
    for row in rows:
        if row["kind"] != "gaze_observation":
            continue
        p = row["payload"]
        context = p.get("coordinate_context")
        key = json.dumps(context, sort_keys=True, separators=(",", ":"), allow_nan=False)
        entry = contexts.setdefault(key, dict(context=context, observation_count=0, allowed_coordinate_count=0,
                                             first_placed_ms=None, last_placed_ms=None))
        entry["observation_count"] += 1
        at = sp.placement(p)
        if sp.finite(at):
            entry["first_placed_ms"] = at if entry["first_placed_ms"] is None else min(at, entry["first_placed_ms"])
            entry["last_placed_ms"] = at if entry["last_placed_ms"] is None else max(at, entry["last_placed_ms"])
        entry["allowed_coordinate_count"] += int(p.get("reason") == "coordinate" and p.get("context_allowed") is True)
    provider = next((r["payload"] for r in rows if r["kind"] == "provider_start"), {})
    refs = [r["payload"].get("reference") for r in rows if r["kind"] == "rdf_reference"]
    manifest = package.manifest
    return dict(package_id=manifest["package_id"], **manifest["identity"], phase=start.get("phase", "unavailable"),
                article_reading_id=meta.get("article_reading_id"), article_id=meta.get("article_id"),
                started_utc_ms=start.get("started_utc_ms"),
                recorded_segment_span_ms=(ends[0]["end_ms"] - start["start_ms"]
                    if len(ends) == 1 and "start_ms" in start else None),
                record_count=len(rows), content_complete=manifest["content_complete"], annotations=package.annotations,
                stored_chunk_bytes=sum(c["byte_count"] for c in manifest["chunks"]),
                decoded_chunk_bytes=sum(c.get("uncompressed_byte_count", c["byte_count"]) for c in manifest["chunks"]),
                manifest_sha256=package.manifest_sha256, calibration_id=meta.get("calibration_id", provider.get("calibration_id")),
                baseline_attempt_id=meta.get("baseline_attempt_id"), rdf_reference_count=len(refs),
                rdf_reference=refs[0] if len(refs) == 1 else None, coordinate_context_inventory=list(contexts.values()))


def check_transfer(source, reference_session_id=None, evaluation_session_id=None,
                   reference_start_ms=None, reference_end_ms=None):
    if (reference_session_id is None) != (evaluation_session_id is None):
        raise ValueError("reference and evaluation session IDs must be supplied together")
    if (reference_start_ms is None) != (reference_end_ms is None):
        raise ValueError("reference bounds must be supplied together")
    if reference_start_ms is not None and reference_session_id is None:
        raise ValueError("reference bounds require explicit reference/evaluation sessions")
    identifiers = session_ids(source)
    packages = [inventory(sp.load_package(source, identifier)) for identifier in identifiers]
    inventory_status = "NOT_PROVIDED_PACKAGE_CONTENTS_ONLY"
    if not Path(source).is_dir():
        with zipfile.ZipFile(source) as archive:
            if "study_export_manifest.json" in archive.namelist():
                export = sp.strict_json(archive.read("study_export_manifest.json").decode("utf-8"))
                declared = {(p["package_id"], p["session_id"], p["manifest_sha256"]) for p in export["packages"]}
                received = {(p["package_id"], p["session_id"], p["manifest_sha256"]) for p in packages}
                if declared != received:
                    raise ValueError("received package set differs from declared export inventory")
                inventory_status = "ALL_DECLARED_PACKAGES_PRESENT"
    links = []
    for item in packages:
        if item["phase"] == "rdf_baseline":
            continue
        ref = item["rdf_reference"]
        matching = []
        if ref and item["calibration_id"]:
            matching = [b["package_id"] for b in packages if b["phase"] == "rdf_baseline"
                and b["baseline_attempt_id"] == ref.get("session_id")
                and b["participant_id"] == item["participant_id"] == ref.get("participant_code")
                and b["installation_id"] == item["installation_id"]
                and b["calibration_id"] == item["calibration_id"] == ref.get("calibration_fingerprint")]
        links.append(dict(session_id=item["session_id"], source_baseline_package_ids=matching,
                          status="MATCHING_SOURCE_PRESENT" if matching else
                          "NO_FROZEN_REFERENCE" if ref is None and item["rdf_reference_count"] == 1 else
                          "SOURCE_MISSING_OR_COMPATIBILITY_UNCONFIRMED"))
    reference_check = dict(status="NOT_CHECKED", reason="explicit_reference_and_evaluation_selection_required")
    if reference_session_id is not None:
        if reference_session_id not in identifiers or evaluation_session_id not in identifiers:
            raise ValueError("selected reference/evaluation session missing from received export")
        reference = sp.load_package(source, reference_session_id)
        evaluation = sp.load_package(source, evaluation_session_id)
        result = sp.analyze(evaluation, reference, reference_start_ms=reference_start_ms, reference_end_ms=reference_end_ms)
        reference_check = {k: v for k, v in result.items() if k not in ("points", "summaries")}
        reference_check.update(point_status_counts=dict(collections.Counter(p["result"]["status"] for p in result["points"])),
            summary_status_counts=dict(collections.Counter(s["result"]["status"] for s in result["summaries"])),
            reference_session_id=reference_session_id, evaluation_session_id=evaluation_session_id)
    return dict(verification_version="newsmead_study_transfer_check_v1", importer_version=sp.VERSION,
                verified_package_count=len(packages), all_packages_content_complete=all(p["content_complete"] for p in packages),
                export_inventory_status=inventory_status,
                packages=packages, rdf_source_links=links,
                coordinate_reference_sessions=[p["session_id"] for p in packages if p["phase"] == "coordinate_reference"],
                coordinate_reference_check=reference_check, automatic_reference_selection=False,
                limits=["Recorded spans include exclusions; allowed context does not prove attention or gaze accuracy.",
                        "Context inventory is provenance, not automatic reference-bound selection.",
                        "Reference defaults are engineering gates, not validated participant procedures."])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reference-session-id")
    parser.add_argument("--evaluation-session-id")
    parser.add_argument("--reference-start-ms", type=float)
    parser.add_argument("--reference-end-ms", type=float)
    args = parser.parse_args()
    try:
        output = args.output.resolve()
        source = args.source.resolve()
        if output == source or output.exists() or (source.is_dir() and output.is_relative_to(source)):
            raise ValueError("verification output must be a new file outside the source package")
        receipt = check_transfer(source, args.reference_session_id, args.evaluation_session_id,
                                 args.reference_start_ms, args.reference_end_ms)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(receipt, indent=2, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
        print(f"Verified {receipt['verified_package_count']} package(s). Receipt: {output}")
    except (ValueError, TypeError, KeyError, OSError, zipfile.BadZipFile) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
