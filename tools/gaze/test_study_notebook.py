"""Synthetic package/projection tests; no scientific or device validation."""
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import study_notebook as sn
import study_package as sp


class NotebookTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def package(self, package_id="one", compressed=False):
        directory = self.root / package_id
        directory.mkdir()
        identity = dict(study_id="study", participant_id="synthetic", installation_id="install", run_id="run",
                        session_id=package_id + "-session", clock_domain_id="clock")
        data = [
            ("session_start", dict(start_ms=1000, phase="reading", condition="test", metadata=dict(article_reading_id="visit", article_id="article"))),
            ("gaze_observation", dict(capture_ms=1100, delivery_ms=1250, raw_x_px=0, raw_y_px=20, selected_x_px=None, selected_y_px=None,
                reason="blink", context_allowed=False, coordinate_context=dict(clock_domain_id="clock", screen_width_px=1080, screen_height_px=2340))),
            ("gaze_observation", dict(capture_ms=11100, delivery_ms=11350, raw_x_px=55, raw_y_px=60, selected_x_px=56, selected_y_px=61,
                reason="coordinate", context_allowed=True)),
            ("gaze_observation", dict(capture_ms=None, delivery_ms=11400, raw_x_px=None, selected_x_px=None, reason="no_face", context_allowed=True)),
            ("rdf_tick", dict(feature_vector=dict(quality="VALID", window_start_ms=1000, window_end_ms=1100,
                values=dict(dwell_fraction=0)), score=dict(md=0, md2=0, status="AVAILABLE"))),
            ("rdf_tick", dict(feature_vector=dict(quality="INSUFFICIENT", window_start_ms=1200, window_end_ms=1200),
                score=dict(md=None, md2=None, status="INVALID_WINDOW"))),
            ("rdf_baseline_window", dict(retained=False, baseline_attempt_id="attempt", feature_vector=dict(quality="INSUFFICIENT"))),
            ("rdf_reference", dict(reference=dict(session_id="attempt", profile=dict(mean=dict(dwell_fraction=0))), participant_match=True)),
            ("scanpath_output", dict(producer="pattern", generation=2, output=dict(event_id="pattern:1", pattern_id="P01", start_ms=1100, end_ms=1200, fixation_indices=[1, 2]))),
            ("scanpath_output", dict(producer="aid", generation=2, output_type="revision", output=dict(event=dict(event_id="pattern:1", start_ms=1100)))),
            ("aid_decision", dict(decision=dict(status="CHOSEN", reason="qualified", now_ms=1200,
                action=dict(id="action:1", aid_type="WORD_DEFINITION", anchor=dict(word_start_utf16=2, word_end_utf16=4))))),
            ("aid_decision", dict(decision=dict(status="CLEARED", reason="expired", now_ms=1300, action=None))),
            ("aid_runtime_definition_offer_shown", dict(action_id="action:1", render_phase="lookup_offer")),
            ("aid_runtime_definition_offer_accepted", dict(action_id="action:1")),
            ("aid_runtime_definition_consultation", dict(active=True)),
            ("aid_presentation", dict(action_id="action:1", render_phase="definition_panel", drawn_proxy=True, verified_visibility=False,
                action=dict(id="action:1", aid_type="WORD_DEFINITION", anchor=dict(generation=0, geometry_snapshot_id="geometry:40", line=5,
                    word_start_utf16=None, word_end_utf16=None)))),
            ("aid_evaluation", dict(input_context=dict(capture_ms=None, geometry=dict(lines=[dict(text="large")]*2000)), components=[dict(status="unavailable")])),
            ("boundary", dict(reason="paused", allowed=False)),
            ("session_end", dict(end_ms=12500)),
        ]
        rows = [dict(schema_version=sp.SCHEMA, package_id=package_id, **identity,
                     record_id=f"{package_id}:{seq}", seq=seq, recorded_elapsed_ms=1000+seq,
                     source_elapsed_ms=None, kind=kind, payload=payload) for seq, (kind,payload) in enumerate(data)]
        raw = b"".join((json.dumps(r)+"\n").encode() for r in rows)
        stored = gzip.compress(raw, mtime=0) if compressed else raw
        filename = "chunk.jsonl.gz" if compressed else "chunk.jsonl"
        chunk = dict(file=filename, byte_count=len(stored), sha256=hashlib.sha256(stored).hexdigest(), record_count=len(rows), first_seq=0,last_seq=len(rows)-1)
        if compressed: chunk.update(encoding=sp.GZIP_ENCODING, uncompressed_byte_count=len(raw))
        manifest = dict(schema_version=sp.SCHEMA, package_id=package_id, identity=identity, status="finished", content_complete=True,
                        losses=[], unknown_tail=False, record_count=len(rows), chunks=[chunk])
        (directory/filename).write_bytes(stored)
        (directory/"manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        return directory

    def test_shared_reference_directory_zip_explicit_and_missing_routes(self):
        import test_study_package as fixtures
        f = fixtures.StudyPackageTest("runTest")
        f.setUp()
        self.addCleanup(f.tearDown)
        reference, evaluation = f.shared_packages()
        config = sp.md.Config(min_reference_coverage_ms=0)
        for source, session in ((evaluation, None), (f.root, "reading-session")):
            got = sn.coordinate_analysis(source, session, config=config)
            self.assertEqual(got["coordinate_availability"], "READY")
            self.assertEqual(got["points"][1]["result"]["md"], 0)
            self.assertEqual(got["rdf_availability"], {"UNAVAILABLE": 1})
        archive = f.root / "export.zip"
        with zipfile.ZipFile(archive, "w") as handle:
            for directory in (reference, evaluation):
                for path in directory.iterdir(): handle.write(path, directory.name + "/" + path.name)
        got = sn.coordinate_analysis(archive, "reading-session", config=config)
        self.assertEqual(got["coordinate_availability"], "READY")
        got = sn.coordinate_analysis(evaluation, reference_source=reference, config=config)
        self.assertEqual(got["fit"]["status"], "READY")
        compact = sn.load_recording(evaluation)
        link = compact.tables["references"][0]
        self.assertEqual(link["kind"], "coordinate_reference")
        self.assertEqual(link["reference_baseline_attempt_id"], "attempt")
        baseline = sn.load_recording(reference)
        self.assertTrue(baseline.tables["references"][0]["completed"])
        # Keep evaluation untouched and hide the sibling from catalog discovery.
        original = sn.catalog_sources
        with patch.object(sn, "catalog_sources", side_effect=lambda source: [r for r in original(source) if r["package_id"] != "shared"]):
            missing = sn.coordinate_analysis(evaluation, config=config)
        self.assertIn("declared_source_missing", missing["reference_issue"])
        self.assertTrue(all(p["result"]["md"] is None for p in missing["points"]))

    def test_legacy_gzip_strict_load_once_and_compact_native_values(self):
        for compressed in (False, True):
            with self.subTest(compressed=compressed):
                source = self.package(str(compressed), compressed)
                with patch.object(sp, "load_package", wraps=sp.load_package) as loader, patch.object(sp, "analyze", side_effect=AssertionError("no replay")):
                    got = sn.load_recording(source)
                    self.assertEqual(loader.call_count, 1)
                self.assertEqual(set(got.tables), set(sn.TABLES))
                self.assertEqual(got.metadata["recorded_span_s"], 11.5)
                self.assertEqual(got.metadata["article_reading_id"], "visit")
                self.assertIsNone(got.metadata["coordinate_md"])
                self.assertEqual(got.metadata["coordinate_md_status"], "NOT_COMPUTED_NO_REFERENCE")
                gaze = got.tables["gaze"]
                self.assertEqual(gaze[0]["raw_x_px"], 0)
                self.assertIsNone(gaze[0]["selected_x_px"])
                self.assertEqual(gaze[0]["capture_offset_s"], .1)
                self.assertEqual(gaze[0]["delivery_offset_s"], .25)
                self.assertEqual(gaze[0]["latency_ms"], 150)
                self.assertIsNone(gaze[2]["capture_offset_s"])
                rdf = got.tables["rdf"]
                self.assertEqual(rdf[0]["score_md"], 0)
                self.assertIsNone(rdf[1]["score_md"])
                self.assertEqual(rdf[1]["score_status"], "INVALID_WINDOW")
                self.assertEqual(rdf[2]["kind"], "rdf_baseline_window")
                self.assertEqual(got.tables["scanpath"][0]["output_type"], "direct_pattern_event")
                self.assertEqual(got.tables["scanpath"][1]["output_type"], "revision")
                self.assertEqual(got.tables["scanpath"][1]["output_event_event_id"], "pattern:1")
                self.assertFalse(any("geometry" in key and "geometry_snapshot_id" not in key for key in got.tables["aids"][-1]))
                self.assertEqual([r.get("decision_status") for r in got.tables["aids"][:2]], ["CHOSEN", "CLEARED"])
                self.assertEqual(got.tables["aids"][0]["decision_action_id"], got.tables["aids"][2]["action_id"])
                self.assertEqual(got.tables["aids"][0]["decision_action_anchor_word_start_utf16"], 2)
                self.assertEqual(got.tables["aids"][0]["decision_action_aid_type"], "WORD_DEFINITION")
                self.assertIsNone(got.tables["aids"][1]["decision_action"])
                self.assertTrue(all(r["verified_visibility"] is False for r in got.tables["aids"]))
                self.assertEqual(got.metadata["source_reference_attempts"][0]["reference_session_id"], "attempt")

    def test_payload_and_metadata_cannot_overwrite_provenance(self):
        source = self.package()
        raw_path = source / "chunk.jsonl"
        rows = [json.loads(line) for line in raw_path.read_text(encoding="utf-8").splitlines()]
        protected = dict(package_id="shadow-package", session_id="shadow-session", clock_domain_id="shadow-clock",
                         record_id="shadow-record", seq=999, source_elapsed_ms=9999, recorded_elapsed_ms=9999)
        rows[1]["payload"].update(protected)
        rows[0]["payload"]["metadata"].update(protected, validation="SHADOW", start_ms=0, end_ms=1, recorded_span_s=999)
        raw = b"".join((json.dumps(r)+"\n").encode() for r in rows)
        raw_path.write_bytes(raw)
        manifest_path = source / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["identity"].update(package_id="catalog-shadow", validation="SHADOW", source="shadow-source")
        manifest["chunks"][0].update(byte_count=len(raw), sha256=hashlib.sha256(raw).hexdigest())
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        catalog = sn.catalog_sources(source)[0]
        self.assertEqual(catalog["package_id"], "one")
        self.assertEqual(catalog["source"], str(source))
        self.assertEqual(catalog["validation"], "DECLARED_UNVERIFIED")
        got = sn.load_recording(source)
        row = got.tables["gaze"][0]
        for key,value in protected.items():
            self.assertEqual(row[key], rows[1][key])
            self.assertEqual(row["payload_"+key], value)
        self.assertEqual(got.metadata["package_id"], "one")
        self.assertEqual(got.metadata["session_id"], "one-session")
        self.assertEqual(got.metadata["validation"], "STRICT_VERIFIED")
        self.assertEqual((got.metadata["start_ms"],got.metadata["end_ms"],got.metadata["recorded_span_s"]), (1000,12500,11.5))
        for payload in (dict(session_id="shadow", payload_session_id="ambiguous"),
                        dict(payload_session_id="ambiguous", session_id="shadow")):
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                sn._project(dict(rows[1], payload=payload))

    def test_native_presentation_equal_action_alias_and_mismatch(self):
        # Actual aid_presentation shape (main visit seq 1643): a top-level
        # action_id repeats action.id. Equal native aliases must remain usable.
        got = sn.load_recording(self.package())
        presentation = next(row for row in got.tables["aids"] if row["kind"] == "aid_presentation")
        self.assertEqual(presentation["action_id"], "action:1")
        self.assertEqual(presentation["action_anchor_geometry_snapshot_id"], "geometry:40")
        envelope = {k: presentation[k] for k in (*sp.IDENTITY, "package_id", "record_id", "seq", "kind", "recorded_elapsed_ms", "source_elapsed_ms")}
        for left,right in (("action:1","action:2"), (1,True)):
            with self.assertRaisesRegex(ValueError,"ambiguous"):
                sn._project(dict(envelope,payload=dict(action_id=left, action=dict(id=right))))

    def test_catalog_is_declared_not_verification_and_integrity_rejects(self):
        for mode in ("missing", "bad_hash", "missing_hash"):
            source = self.package(mode)
            chunk = source / "chunk.jsonl"
            if mode == "missing": chunk.unlink()
            elif mode == "bad_hash": chunk.write_bytes(chunk.read_bytes()+b" ")
            else:
                path = source / "manifest.json"
                manifest = json.loads(path.read_text(encoding="utf-8"))
                manifest["chunks"][0].pop("sha256")
                path.write_text(json.dumps(manifest), encoding="utf-8")
            catalog = sn.catalog_sources(source)[0]
            self.assertEqual(catalog["validation"], "DECLARED_UNVERIFIED")
            self.assertTrue(catalog["content_complete"])
            with self.assertRaises((ValueError, FileNotFoundError, KeyError)):
                sn.load_recording(source)

    def test_parent_zip_selection_and_scoped_duplicate_event_ids(self):
        one, two = self.package("one"), self.package("two", True)
        archive = self.root / "export.zip"
        with zipfile.ZipFile(archive, "w") as handle:
            for directory in (one,two):
                for path in directory.iterdir(): handle.write(path, directory.name+"/"+path.name)
        self.assertEqual(len(sn.catalog_sources(archive)), 2)
        self.assertEqual(len(sn.catalog_sources(self.root)), 2)
        with self.assertRaisesRegex(ValueError, "select exactly"):
            sn.load_recording(self.root)
        a = sn.load_recording(self.root, "one-session")
        b = sn.load_recording(archive, "two-session")
        self.assertEqual(a.tables["scanpath"][0]["output_event_id"], b.tables["scanpath"][0]["output_event_id"])
        self.assertNotEqual(a.tables["scanpath"][0]["session_id"], b.tables["scanpath"][0]["session_id"])
        self.assertEqual(b.receipt["validation"], "STRICT_VERIFIED")

    def test_cadence_capture_time_partial_denominator_and_filters(self):
        got = sn.load_recording(self.package())
        rows = got.tables["gaze"]
        bins = sn.cadence_bins(rows, 1000, 12500)
        self.assertEqual([r["span_s"] for r in bins], [10,1.5])
        self.assertEqual(bins[1]["coordinate_hz"], 1/1.5)
        self.assertEqual(bins[0]["coordinate_count"], 0)
        self.assertEqual(bins[0]["all_count"], 1)
        self.assertEqual(bins[0]["missing_capture_count_total"], 1)
        self.assertEqual(bins[1]["median_latency_ms"], 250)
        endpoint = dict(rows[1],capture_ms=12500,latency_ms=-10)
        bins = sn.cadence_bins(rows+[endpoint],1000,12500)
        self.assertEqual(bins[1]["all_count"],2)
        self.assertAlmostEqual(bins[1]["p95_latency_ms"],237)
        with self.assertRaisesRegex(ValueError,"one explicit"):
            sn.cadence_bins(rows+[dict(rows[0],session_id="other")],1000,12500)
        with self.assertRaisesRegex(ValueError,"clock"):
            sn.cadence_bins([dict(rows[0],derived_clock_status="CONTEXT_CLOCK_MISMATCH")],1000,12500)
        with self.assertRaises(ValueError): sn.cadence_bins(rows,1000,1000)


if __name__ == "__main__":
    unittest.main()
