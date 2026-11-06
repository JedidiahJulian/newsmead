"""Synthetic package integrity and adapter replay tests; no device/human validation."""
import copy
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import coordinate_mahalanobis as md
import study_package as sp


class StudyPackageTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.fixture = json.loads((Path(__file__).parent / "fixtures/coordinate_md_v1.json").read_text())
        self.identity = dict(study_id="study", participant_id="synthetic", installation_id="installation",
                             run_id="run", session_id="session", clock_domain_id="elapsed-clock")

    def tearDown(self):
        self.tmp.cleanup()

    def package(self, package_id="evaluation", reference=False, context=None):
        f = self.fixture
        data = []
        def add(kind, payload):
            seq = len(data)
            data.append(dict(schema_version=sp.SCHEMA, package_id=package_id,
                record_id=f"{package_id}:{seq}", seq=seq, recorded_elapsed_ms=seq * 100,
                source_elapsed_ms=None, kind=kind, payload=payload, **self.identity))
        add("session_start", dict(phase="coordinate_reference" if reference else "reading", start_ms=0 if reference else 600))
        for o in f["reference_observations" if reference else "evaluation_observations"]:
            p = {k: v for k, v in o.items() if k not in ("x_px", "y_px")}
            p.update(raw_x_px=o["x_px"], raw_y_px=o["y_px"], selected_x_px=-123,
                     selected_y_px=-123, reason="coordinate", timeout_watchdog=False,
                     context_allowed=True, coordinate_context=context or f["context"])
            add("gaze_observation", p)
        if not reference:
            add("rdf_tick", dict(tick_ms=810, feature_vector=dict(timestamp_ms=800,
                window_start_ms=600, window_end_ms=800), score=None))
        add("session_end", dict(end_ms=400 if reference else 800))
        directory = self.root / package_id
        directory.mkdir()
        manifest = dict(schema_version=sp.SCHEMA, package_id=package_id, status="finished",
            content_complete=True, identity=self.identity, losses=[], unknown_tail=False)
        self.save(directory, manifest, data)
        return directory

    def save(self, directory, manifest, rows):
        raw = b"".join((json.dumps(row, allow_nan=False) + "\n").encode() for row in rows)
        (directory / "chunk.jsonl").write_bytes(raw)
        manifest.update(record_count=len(rows), chunks=[dict(file="chunk.jsonl", byte_count=len(raw),
            sha256=hashlib.sha256(raw).hexdigest(), record_count=len(rows),
            first_seq=rows[0]["seq"], last_seq=rows[-1]["seq"])])
        (directory / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    def mutate(self, directory, change):
        package = sp.load_package(directory)
        change(package.manifest, package.records)
        self.save(directory, package.manifest, package.records)

    def compress(self, directory):
        manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
        for chunk in manifest["chunks"]:
            raw = (directory / chunk["file"]).read_bytes()
            chunk["file"] += ".gz"
            chunk.update(encoding=sp.GZIP_ENCODING, uncompressed_byte_count=len(raw))
            stored = gzip.compress(raw, mtime=0)
            chunk.update(byte_count=len(stored), sha256=hashlib.sha256(stored).hexdigest())
            (directory / chunk["file"]).write_bytes(stored)
        (directory / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        return manifest

    def test_compressed_directory_zip_import_and_coordinate_reference_are_lossless(self):
        directory = self.package()
        reference = self.package("compressed-reference", True)
        before = sp.load_package(directory)
        baseline = sp.analyze(before, sp.load_package(reference), md.Config(min_reference_coverage_ms=0))
        self.compress(directory); self.compress(reference)
        after = sp.load_package(directory)
        self.assertEqual(after.records, before.records)
        compressed = sp.analyze(after, sp.load_package(reference), md.Config(min_reference_coverage_ms=0))
        # Storage metadata changes the source manifest digest and therefore the
        # provenance ID; numerical inputs/results and statuses remain identical.
        for old, new in zip(baseline["points"], compressed["points"]):
            self.assertNotEqual(old["result"]["reference_id"], new["result"]["reference_id"])
            for key in ("status", "md", "md2"):
                self.assertEqual(old["result"][key], new["result"][key])
        archive = self.root / "compressed.zip"
        with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as handle:
            for entry in [directory / "manifest.json", directory / "chunk.jsonl.gz"]:
                handle.write(entry, entry.name)
        self.assertEqual(sp.load_package(archive).records, before.records)
        receipt = sp.import_package(archive, self.root / "compressed-import")
        retained = [json.loads(line) for line in (self.root / "compressed-import/records_v1.jsonl").read_text().splitlines()]
        self.assertEqual(retained, before.records)
        self.assertEqual(receipt["table_counts"]["records_v1"], len(before.records))

    def test_compressed_metadata_corruption_and_truncation_reject(self):
        mutations = ["missing-encoding", "unknown-encoding", "wrong-filename", "wrong-decoded-count",
                     "crc", "truncated", "too-large"]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                directory = self.package(mutation)
                manifest = self.compress(directory)
                chunk = manifest["chunks"][0]
                stored = (directory / chunk["file"]).read_bytes()
                if mutation == "missing-encoding": chunk.pop("encoding")
                elif mutation == "unknown-encoding": chunk["encoding"] = "unknown"
                elif mutation == "wrong-filename":
                    chunk["file"] = "wrong.bin"
                    (directory / chunk["file"]).write_bytes(stored)
                elif mutation == "wrong-decoded-count": chunk["uncompressed_byte_count"] -= 1
                elif mutation == "too-large": chunk["uncompressed_byte_count"] = sp.MAX_CHUNK_BYTES + 1
                else:
                    if mutation == "crc": stored = stored[:-8] + bytes([stored[-8] ^ 1]) + stored[-7:]
                    else: stored = stored[:-4]
                    chunk.update(byte_count=len(stored), sha256=hashlib.sha256(stored).hexdigest())
                    (directory / chunk["file"]).write_bytes(stored)
                (directory / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
                with self.assertRaises(ValueError): sp.load_package(directory)

    def test_compressed_expansion_and_cumulative_decoded_budget_are_bounded(self):
        directory = self.package()
        manifest = self.compress(directory)
        chunk = manifest["chunks"][0]
        stored = (directory / chunk["file"]).read_bytes()
        with self.assertRaisesRegex(ValueError, "decoded package"):
            sp.decode_chunk(stored, chunk, chunk["uncompressed_byte_count"] - 1)
        # An understated declaration bounds actual expansion too.
        understated = dict(chunk, uncompressed_byte_count=1)
        with self.assertRaisesRegex(ValueError, "uncompressed byte count"):
            sp.decode_chunk(stored, understated, sp.MAX_BYTES)
        with patch.object(sp, "MAX_BYTES", chunk["uncompressed_byte_count"] - 1):
            with self.assertRaisesRegex(ValueError, "decoded package"):
                sp.load_package(directory)

    def test_normal_export_and_zip(self):
        directory = self.package()
        receipt = sp.import_package(directory, self.root / "out")
        self.assertEqual(receipt["import_version"], sp.VERSION)
        rows = [json.loads(line) for line in (self.root / "out/coordinate_points_v1.jsonl").read_text().splitlines()]
        self.assertEqual(len(rows), 3)
        self.assertTrue(all(r["result"]["status"] == "REFERENCE_UNAVAILABLE" for r in rows))
        archive = self.root / "package.zip"
        with zipfile.ZipFile(archive, "w") as handle:
            for path in directory.iterdir():
                handle.write(path, path.name)
        self.assertEqual(sp.load_package(archive).records, sp.load_package(directory).records)

    def test_shared_fixture_replay(self):
        reference = sp.load_package(self.package("reference", True))
        package = sp.load_package(self.package())
        result = sp.analyze(package, reference, md.Config(min_reference_coverage_ms=0))
        self.assertEqual(result["fit"]["status"], "READY")
        for got, expected in zip(result["points"], self.fixture["expected"]["point_md2"]):
            self.assertAlmostEqual(got["result"]["md2"], expected)
        self.assertAlmostEqual(result["summaries"][0]["result"]["mean_md2"], 6.75)
        self.assertEqual(result["summaries"][0]["bounds_basis"], "rdf_window")

    def test_default_reference_gate_unchanged(self):
        result = sp.analyze(sp.load_package(self.package()), sp.load_package(self.package("reference", True)))
        self.assertEqual(result["fit"]["status"], "INSUFFICIENT_REFERENCE")

    def test_native_zero_span_warmup_is_retained_and_unavailable_without_fallback(self):
        directory = self.package()
        def warmup(manifest, rows):
            vector = next(r["payload"]["feature_vector"] for r in rows if r["kind"] == "rdf_tick")
            vector.update(window_start_ms=800, window_end_ms=800, quality="INSUFFICIENT",
                          valid_sample_count=0, total_sample_count=0)
        self.mutate(directory, warmup)
        original = sp.load_package(directory)
        receipt = sp.import_package(directory, self.root / "warmup-out")
        retained = [json.loads(line) for line in (self.root / "warmup-out/records_v1.jsonl").read_text().splitlines()]
        self.assertEqual(retained, original.records)
        summaries = [json.loads(line) for line in (self.root / "warmup-out/coordinate_summaries_v1.jsonl").read_text().splitlines()]
        self.assertEqual(len(summaries), 1)
        self.assertEqual(summaries[0]["result"]["status"], "INSUFFICIENT_SOURCE_WINDOW")
        self.assertEqual(summaries[0]["adapter_status"], "ZERO_SPAN_NATIVE_WARMUP")
        self.assertIsNone(summaries[0]["result"]["mean_md2"])
        self.assertIsNone(summaries[0]["result"]["coverage_fraction"])
        self.assertEqual(summaries[0]["result"]["start_ms"], summaries[0]["result"]["end_ms"])
        self.assertEqual(receipt["table_counts"]["records_v1"], len(original.records))

    def test_reset_warmup_summary_keeps_source_window_order(self):
        directory = self.package()
        def mixed(manifest, rows):
            index = next(i for i, r in enumerate(rows) if r["kind"] == "rdf_tick")
            template = rows[index]
            windows = []
            for start, end, quality in [(600, 650, "VALID"), (700, 700, "INSUFFICIENT"), (750, 800, "VALID")]:
                row = copy.deepcopy(template)
                row["payload"]["feature_vector"].update(window_start_ms=start, window_end_ms=end, quality=quality)
                windows.append(row)
            rows[index:index+1] = windows
            for seq, row in enumerate(rows):
                row.update(seq=seq, record_id=f"evaluation:{seq}", recorded_elapsed_ms=seq * 100)
        self.mutate(directory, mixed)
        package = sp.load_package(directory)
        result = sp.analyze(package)
        self.assertEqual([r["window_id"] for r in result["summaries"]],
                         [r["record_id"] for r in package.records if r["kind"] == "rdf_tick"])
        self.assertEqual(result["summaries"][1]["result"]["status"], "INSUFFICIENT_SOURCE_WINDOW")

    def test_invalid_zero_span_and_reversed_windows_still_reject(self):
        for name, start, end, quality in [("zero-valid", 800, 800, "VALID"),
                                          ("backwards", 801, 800, "INSUFFICIENT")]:
            directory = self.package(name)
            def invalid(manifest, rows):
                next(r["payload"]["feature_vector"] for r in rows if r["kind"] == "rdf_tick").update(
                    window_start_ms=start, window_end_ms=end, quality=quality)
            self.mutate(directory, invalid)
            with self.assertRaisesRegex(ValueError, "bounds"):
                sp.analyze(sp.load_package(directory))

    def test_hash_tamper(self):
        directory = self.package()
        with (directory / "chunk.jsonl").open("ab") as handle:
            handle.write(b" ")
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            sp.load_package(directory)

    def test_manifest_path_traversal(self):
        directory = self.package()
        path = directory / "manifest.json"
        manifest = json.loads(path.read_text())
        manifest["chunks"][0]["file"] = "../escape.jsonl"
        path.write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "unsafe"):
            sp.load_package(directory)

    def test_zip_traversal(self):
        path = self.root / "bad.zip"
        with zipfile.ZipFile(path, "w") as handle:
            handle.writestr("../escape", "bad")
        with self.assertRaisesRegex(ValueError, "unsafe"):
            sp.load_package(path)

    def test_duplicate_and_nonfinite_json(self):
        for text in ('{"a":1,"a":2}', '{"a":NaN}', '{"a":1e999}'):
            with self.assertRaises(ValueError):
                sp.strict_json(text)

    def test_count_identity_order_and_id(self):
        changes = [lambda m, r: r[1].update(participant_id="different"),
                   lambda m, r: r[1].update(seq=0),
                   lambda m, r: r[1].update(record_id=r[0]["record_id"])]
        for i, change in enumerate(changes):
            directory = self.package(f"bad{i}")
            self.mutate(directory, change)
            with self.assertRaises(ValueError):
                sp.load_package(directory)

    def test_undeclared_gap(self):
        directory = self.package()
        self.mutate(directory, lambda m, r: r.pop(1))
        with self.assertRaisesRegex(ValueError, "undeclared sequence gap"):
            sp.load_package(directory)

    def test_declared_loss_retains_points_and_blocks_summary(self):
        directory = self.package()
        def change(m, rows):
            rows.pop(0)
            m.update(content_complete=False, losses=[dict(first_seq=0, last_seq=0,
                count=1, exact=True, first_source_elapsed_ms=None, last_source_elapsed_ms=None)])
        self.mutate(directory, change)
        package = sp.load_package(directory)
        result = sp.analyze(package, sp.load_package(self.package("reference", True)), md.Config(min_reference_coverage_ms=0))
        self.assertTrue(all(p["result"]["status"] == "AVAILABLE" for p in result["points"]))
        self.assertEqual(result["summaries"][0]["result"]["status"], "RECORDING_LOSS")

    def test_unknown_tail(self):
        directory = self.package()
        self.mutate(directory, lambda m, r: m.update(content_complete=False,
            unknown_tail=True, losses_unknown=True, status="recovered_incomplete"))
        package = sp.load_package(directory)
        self.assertIn("unknown_tail", package.annotations)
        self.assertEqual(sp.analyze(package)["summaries"][0]["result"]["status"], "RECORDING_LOSS")

    def test_context_mismatch_and_no_stream_fallback(self):
        context = dict(self.fixture["context"], presentation_id="different-layout")
        result = sp.analyze(sp.load_package(self.package(context=context)),
            sp.load_package(self.package("reference", True)), md.Config(min_reference_coverage_ms=0))
        self.assertTrue(all(p["result"]["status"] == "REFERENCE_MISMATCH" for p in result["points"]))

    def test_stationary_bounds_and_boundaries(self):
        directory = self.package()
        def change(m, rows):
            row = next(r for r in rows if r["kind"] == "rdf_tick")
            row.update(kind="boundary", payload=dict(at_ms=700, reason="scroll", context_epoch_id="changed"))
        self.mutate(directory, change)
        result = sp.analyze(sp.load_package(directory))
        self.assertEqual([(s["result"]["start_ms"], s["result"]["end_ms"]) for s in result["summaries"]], [(600, 700), (700, 800)])
        self.assertTrue(all(s["bounds_basis"] == "stationary_observed_bounds" for s in result["summaries"]))

    def test_multi_package_export_zip_requires_session(self):
        first = self.package("one")
        second = self.package("two")
        self.mutate(second, lambda m, r: (m["identity"].update(session_id="second"),
            [row.update(session_id="second") for row in r]))
        archive = self.root / "export.zip"
        with zipfile.ZipFile(archive, "w") as handle:
            handle.writestr("study_export_manifest.json", json.dumps({
                "schema_version": "newsmead_study_export_v1", "late_receipts": [],
                "packages": [{"package_id": directory.name,
                    "session_id": json.loads((directory / "manifest.json").read_text())["identity"]["session_id"],
                    "manifest_sha256": hashlib.sha256((directory / "manifest.json").read_bytes()).hexdigest()}
                    for directory in (first, second)]}))
            for directory in (first, second):
                for path in directory.iterdir():
                    handle.write(path, directory.name + "/" + path.name)
        with self.assertRaisesRegex(ValueError, "exactly one package"):
            sp.load_package(archive)
        self.assertEqual(sp.load_package(archive, "second").manifest["package_id"], "two")

    def test_null_context_retained_and_summary_unavailable(self):
        directory = self.package()
        self.mutate(directory, lambda m, rows: rows[2]["payload"].update(coordinate_context=None))
        result = sp.analyze(sp.load_package(directory))
        self.assertEqual(len(result["points"]), 3)
        self.assertEqual(result["points"][1]["result"]["status"], "CONTEXT_UNAVAILABLE")
        self.assertEqual(result["summaries"][0]["result"]["status"], "CONTEXT_UNAVAILABLE")

    def test_all_contexts_unavailable_still_reports_rdf_window(self):
        directory = self.package()
        self.mutate(directory, lambda m, rows: [r["payload"].update(coordinate_context=None)
            for r in rows if r["kind"] == "gaze_observation"])
        result = sp.analyze(sp.load_package(directory))
        self.assertEqual(len(result["summaries"]), 1)
        self.assertEqual(result["summaries"][0]["result"]["status"], "CONTEXT_UNAVAILABLE")

    def test_reference_without_end_is_explicitly_unavailable(self):
        directory = self.package("reference", True)
        def change(m, rows):
            rows[-1].update(kind="writer_health", payload={})
            m.update(content_complete=False, unknown_tail=True, status="recovered_incomplete")
        self.mutate(directory, change)
        result = sp.analyze(sp.load_package(self.package()), sp.load_package(directory))
        self.assertIsNone(result["fit"])
        self.assertIn("missing_explicit_session_end", result["reference_issue"])

    def test_reference_out_of_bound_terminal_observation_not_fitted(self):
        directory = self.package("reference", True)
        def change(m, rows):
            rows[-2]["payload"]["capture_ms"] = 500
        self.mutate(directory, change)
        package = sp.load_package(directory)
        result = sp.analyze(sp.load_package(self.package()), package, md.Config(min_reference_coverage_ms=0))
        self.assertEqual(len(sp.observations(package)), 5)
        self.assertEqual(result["fit"]["stats"]["received_count"], 4)

    def test_invalid_capture_retained_as_adjacency_break(self):
        directory = self.package("reference", True)
        self.mutate(directory, lambda m, rows: rows[3]["payload"].update(capture_ms=False))
        result = sp.analyze(sp.load_package(self.package()), sp.load_package(directory),
                            md.Config(min_reference_coverage_ms=0))
        reasons = dict(result["fit"]["stats"]["reasons"])
        self.assertEqual(reasons["INVALID_CLOCK"], 1)
        self.assertEqual(result["fit"]["stats"]["received_count"], 5)

    def test_disjoint_rdf_windows_only_use_in_window_context_candidates(self):
        directory = self.package()
        def change(m, rows):
            gaze = [copy.deepcopy(r) for r in rows if r["kind"] == "gaze_observation"]
            later = copy.deepcopy(gaze)
            for row in later:
                p = row["payload"]
                p.update(capture_ms=p["capture_ms"] + 400, delivery_ms=p["delivery_ms"] + 400,
                         delivery_id=p["delivery_id"] + 3, context_epoch_id="later-context")
                p["coordinate_context"]["presentation_id"] = "later-layout"
            tick = next(r for r in rows if r["kind"] == "rdf_tick")
            later_tick = copy.deepcopy(tick)
            later_tick["payload"].update(tick_ms=1210, feature_vector=dict(timestamp_ms=1200,
                window_start_ms=1000, window_end_ms=1200))
            rows[:] = [rows[0]] + gaze + [tick] + later + [later_tick, rows[-1]]
            for seq, row in enumerate(rows):
                row.update(seq=seq, record_id=f"evaluation:{seq}")
        self.mutate(directory, change)
        result = sp.analyze(sp.load_package(directory), sp.load_package(self.package("reference", True)),
                            md.Config(min_reference_coverage_ms=0))
        self.assertEqual(len(result["summaries"]), 2)
        first, later = result["summaries"]
        self.assertEqual(first["context"]["presentation_id"], "presentation")
        self.assertEqual(first["result"]["status"], "AVAILABLE")
        self.assertAlmostEqual(first["result"]["mean_md2"], 6.75)
        self.assertEqual(later["context"]["presentation_id"], "later-layout")
        self.assertEqual(later["result"]["status"], "REFERENCE_MISMATCH")

    def test_explicit_reference_subperiod_excludes_settling_context(self):
        directory = self.package("reference", True)
        self.mutate(directory, lambda m, rows:
            rows[1]["payload"]["coordinate_context"].update(presentation_id="settling-layout"))
        reference = sp.load_package(directory)
        package = sp.load_package(self.package())
        config = md.Config(min_reference_coverage_ms=0)
        unselected = sp.analyze(package, reference, config)
        self.assertIn("multiple_reference_contexts", unselected["reference_issue"])
        selected = sp.analyze(package, reference, config, reference_start_ms=100, reference_end_ms=400)
        self.assertEqual(selected["fit"]["status"], "READY")
        profile = selected["fit"]["profile"]
        self.assertEqual((profile["reference_start_ms"], profile["reference_end_ms"]), (100, 400))
        self.assertEqual(selected["fit"]["stats"]["received_count"], 4)
        self.assertEqual(len(sp.observations(reference)), 5)
        receipt = sp.import_package(self.package("export"), self.root / "selected-output", directory,
                                    reference_start_ms=100, reference_end_ms=400)
        self.assertEqual(receipt["analysis"]["reference_bounds_override"]["start_ms"], 100)

    def test_reference_id_freezes_source_bounds_context_and_config(self):
        reference = sp.load_package(self.package("reference", True))
        package = sp.load_package(self.package())
        config = md.Config(min_reference_coverage_ms=0)
        default = sp.analyze(package, reference, config)
        same = sp.analyze(package, reference, config, reference_start_ms=0.0, reference_end_ms=400.0)
        shorter = sp.analyze(package, reference, config, reference_start_ms=0, reference_end_ms=300)
        reference_id = default["fit"]["profile"]["reference_id"]
        self.assertEqual(reference_id, same["fit"]["profile"]["reference_id"])
        self.assertNotEqual(reference_id, shorter["fit"]["profile"]["reference_id"])
        self.assertEqual(default["reference_source_package_id"], "reference")

    def test_reference_override_bounds_validation(self):
        reference = sp.load_package(self.package("reference", True))
        package = sp.load_package(self.package())
        for start, end in ((100, None), (None, 400), (300, 100), (100, 100),
                           (-1, 300), (0, 401), (float("nan"), 300), (0, float("inf"))):
            with self.subTest(start=start, end=end), self.assertRaises(ValueError):
                sp.analyze(package, reference, reference_start_ms=start, reference_end_ms=end)
        with self.assertRaisesRegex(ValueError, "separate reference package"):
            sp.analyze(package, reference_start_ms=100, reference_end_ms=300)

    def test_reference_override_preserves_separate_earlier_gate(self):
        directory = self.package()
        self.mutate(directory, lambda m, rows: rows[1]["payload"].update(capture_ms=300, delivery_ms=310))
        result = sp.analyze(sp.load_package(directory), sp.load_package(self.package("reference", True)),
            md.Config(min_reference_coverage_ms=0), reference_start_ms=0, reference_end_ms=300)
        self.assertEqual(result["fit"]["status"], "READY")
        self.assertEqual(len(result["points"]), 3)
        self.assertEqual(result["points"][0]["result"]["status"], "REFERENCE_PERIOD")
        self.assertIsNone(result["points"][0]["result"]["md2"])
        self.assertEqual(result["points"][1]["result"]["status"], "AVAILABLE")

    def late_receipt_zip(self, directory, receipt_change=None, entry_change=None, tamper=False):
        receipt = dict(session_id="session", provider_epoch_id="provider", received_elapsed_ms=5000,
                       arrivals=20, busy_drops=4, extends_reading_interval=False)
        if receipt_change:
            receipt_change(receipt)
        raw = json.dumps(receipt).encode("utf-8")
        entry = dict(file="late_receipts/terminal.json", sha256=hashlib.sha256(raw).hexdigest(),
                     byte_count=len(raw), session_id="session", provider_epoch_id="provider")
        if entry_change:
            entry_change(entry)
        export = dict(schema_version="newsmead_study_export_v1", packages=[dict(
            package_id=directory.name, session_id="session",
            manifest_sha256=hashlib.sha256((directory / "manifest.json").read_bytes()).hexdigest())],
            late_receipts=[entry])
        archive = self.root / (directory.name + "-late.zip")
        with zipfile.ZipFile(archive, "w") as handle:
            for path in directory.iterdir():
                handle.write(path, directory.name + "/" + path.name)
            handle.writestr("study_export_manifest.json", json.dumps(export))
            handle.writestr("late_receipts/terminal.json", raw + (b" " if tamper else b""))
        return archive

    def test_verified_late_receipt_preserved_without_repairing_package(self):
        directory = self.package()
        self.mutate(directory, lambda m, rows: m.update(content_complete=False, unknown_tail=True))
        original = sp.load_package(directory)
        archive = self.late_receipt_zip(directory)
        loaded = sp.load_package(archive)
        self.assertEqual(loaded.manifest, original.manifest)
        self.assertTrue(loaded.manifest["unknown_tail"])
        self.assertFalse(loaded.manifest["content_complete"])
        self.assertEqual(sp.analyze(loaded), sp.analyze(original))
        self.assertEqual(len(loaded.late_receipts), 1)
        self.assertEqual(loaded.late_receipts[0]["receipt"]["busy_drops"], 4)
        output = self.root / "late-output"
        receipt = sp.import_package(archive, output)
        rows = [json.loads(line) for line in (output / "late_receipts_v1.jsonl").read_text().splitlines()]
        self.assertEqual(rows, loaded.late_receipts)
        self.assertEqual(receipt["late_receipt_source"], "verified_export_manifest")
        self.assertEqual(receipt["late_receipts"], rows)
        self.assertFalse(receipt["late_receipts_change_original_completeness_or_md_availability"])

    def test_late_receipt_tamper_rejected(self):
        archive = self.late_receipt_zip(self.package(), tamper=True)
        with self.assertRaisesRegex(ValueError, "late receipt byte count/hash mismatch"):
            sp.load_package(archive)

    def test_late_receipt_wrong_session_rejected(self):
        archive = self.late_receipt_zip(self.package(),
            receipt_change=lambda receipt: receipt.update(session_id="wrong-session"))
        with self.assertRaisesRegex(ValueError, "late receipt session mismatch"):
            sp.load_package(archive)

    def test_late_receipt_wrong_provider_rejected(self):
        archive = self.late_receipt_zip(self.package(),
            receipt_change=lambda receipt: receipt.update(provider_epoch_id="unknown-provider"),
            entry_change=lambda entry: entry.update(provider_epoch_id="unknown-provider"))
        with self.assertRaisesRegex(ValueError, "late receipt provider linkage mismatch"):
            sp.load_package(archive)

    def test_late_receipt_cannot_extend_interval(self):
        archive = self.late_receipt_zip(self.package(),
            receipt_change=lambda receipt: receipt.update(extends_reading_interval=True))
        with self.assertRaisesRegex(ValueError, "must not extend reading interval"):
            sp.load_package(archive)

    def test_directory_import_does_not_discover_external_late_receipts(self):
        directory = self.package()
        loaded = sp.load_package(directory)
        self.assertEqual(loaded.late_receipts, [])
        self.assertEqual(loaded.late_receipt_source,
                         "external_receipts_not_discoverable_from_package_directory")

    def test_grouped_tables_preserve_native_reference_and_assign_once(self):
        directory = self.package()
        def change(m, rows):
            kinds = ("rdf_reference", "rdf_baseline_fit", "rdf_baseline_window", "scanpath_input",
                "scanpath_acceptance", "scanpath_output", "scanpath_layout", "support_revision",
                "aid_evaluation", "aid_decision", "aid_presentation", "aid_runtime_bookmark",
                "writer_health", "package_end", "writer_loss")
            for kind in kinds:
                seq = len(rows)
                rows.append(dict(rows[0], seq=seq, record_id=f"evaluation:{seq}", kind=kind,
                    payload=dict(kind="native-source-kind", reference_id="native-rdf-reference",
                        profile=dict(mean=[1.5, 2.5], source_version="unchanged"))))
        self.mutate(directory, change)
        original = sp.load_package(directory)
        output = self.root / "grouped-output"
        receipt = sp.import_package(directory, output)
        grouped = {table: [json.loads(line) for line in (output / (table + ".jsonl")).read_text().splitlines()]
                   for table in sp.GROUPED_KINDS}
        reference = next(row for row in grouped["rdf_references_v1"] if row["kind"] == "rdf_reference")
        self.assertEqual(reference["payload_reference_id"], "native-rdf-reference")
        self.assertEqual(reference["payload_kind"], "native-source-kind")
        self.assertEqual(reference["payload_profile"], dict(mean=[1.5, 2.5], source_version="unchanged"))
        original_reference = next(row for row in original.records if row["kind"] == "rdf_reference")
        for key in sp.IDENTITY + ("record_id", "recorded_elapsed_ms", "source_elapsed_ms", "kind"):
            self.assertEqual(reference[key], original_reference[key])
        assigned = [row["record_id"] for rows in grouped.values() for row in rows]
        self.assertEqual(len(assigned), len(set(assigned)))
        self.assertEqual(len(assigned), len(original.records))
        self.assertEqual({table: len(rows) for table, rows in grouped.items()},
                         {table: receipt["table_counts"][table] for table in sp.GROUPED_KINDS})
        self.assertEqual(receipt["table_counts"]["records_v1"], len(original.records))
        self.assertEqual([json.loads(line) for line in (output / "records_v1.jsonl").read_text().splitlines()],
                         original.records)
        colliding = dict(original_reference, payload_reference_id="envelope-value")
        with self.assertRaisesRegex(ValueError, "collides with envelope"):
            sp.grouped_tables([colliding])

    def test_no_self_reference_or_source_output(self):
        directory = self.package()
        package = sp.load_package(directory)
        with self.assertRaisesRegex(ValueError, "own reference"):
            sp.analyze(package, package)
        with self.assertRaisesRegex(ValueError, "separate"):
            sp.import_package(directory, directory / "output")


if __name__ == "__main__":
    unittest.main()
