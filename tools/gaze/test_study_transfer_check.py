"""Transfer/reference workflow checks using synthetic packages only."""
import json
import hashlib
import math
import unittest
import zipfile

import study_package as sp
import study_transfer_check as transfer
import test_study_package as fixtures


class StudyTransferCheckTest(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.StudyPackageTest("runTest")
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)

    def archive(self, *directories):
        target = self.fixture.root / "received.zip"
        with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as handle:
            for directory in directories:
                for path in directory.iterdir():
                    if path.name == "manifest.json" or path.name.endswith(".gz"):
                        handle.write(path, directory.name + "/" + path.name)
        return target

    def test_shared_source_inventory_and_link_check_are_independent_of_rdf_fit(self):
        reference, evaluation = self.fixture.shared_packages()
        for directory in (reference, evaluation): self.fixture.compress(directory)
        result = transfer.check_transfer(self.archive(reference, evaluation))
        self.assertEqual(result["coordinate_reference_sessions"], ["shared-session"])
        link = next(r for r in result["coordinate_source_links"] if r["session_id"] == "reading-session")
        self.assertEqual(link["status"], "DECLARED_SOURCE_VERIFIED_PENDING_OFFLINE_FIT")
        baseline = next(p for p in result["packages"] if p["session_id"] == "shared-session")
        self.assertEqual(baseline["shared_baseline_results"][0]["rdf_status"], "INSUFFICIENT_REFERENCE")
        self.assertIsNotNone(baseline["shared_baseline_period"])

    def test_received_multi_package_export_lists_separate_reference_without_auto_selecting(self):
        reading = self.fixture.package("reading")
        reference = self.fixture.package("reference", True)
        for directory, session in [(reading, "reading-session"), (reference, "reference-session")]:
            def identity(manifest, rows):
                manifest["identity"]["session_id"] = session
                for row in rows: row["session_id"] = session
            self.fixture.mutate(directory, identity)
            self.fixture.compress(directory)
        result = transfer.check_transfer(self.archive(reading, reference))
        self.assertEqual(result["verified_package_count"], 2)
        self.assertTrue(result["all_packages_content_complete"])
        self.assertEqual(result["coordinate_reference_sessions"], ["reference-session"])
        self.assertFalse(result["automatic_reference_selection"])
        self.assertEqual(result["coordinate_reference_check"]["status"], "NOT_CHECKED")
        self.assertTrue(all(p["stored_chunk_bytes"] < p["decoded_chunk_bytes"] for p in result["packages"]))

    def test_default_coordinate_reference_and_later_evaluation_survive_compressed_transfer(self):
        reference = self.fixture.package("reference", True)
        evaluation = self.fixture.package("evaluation")
        def extend_reference(manifest, rows):
            manifest["identity"]["session_id"] = "reference-session"
            template = next(r for r in rows if r["kind"] == "gaze_observation")
            start, end = rows[0], rows[-1]
            end["payload"]["end_ms"] = 65_000
            import copy
            observations = []
            for i in range(651):
                row = copy.deepcopy(template)
                row["payload"].update(capture_ms=i*100, delivery_ms=i*100+10, delivery_id=i+1,
                                      raw_x_px=500+100*math.cos(i/10), raw_y_px=900+60*math.sin(i/10))
                observations.append(row)
            rows[:] = [start] + observations + [end]
            for seq, row in enumerate(rows):
                row.update(seq=seq, record_id=f"reference:{seq}", session_id="reference-session", recorded_elapsed_ms=seq*100)
        self.fixture.mutate(reference, extend_reference)
        def later_evaluation(manifest, rows):
            manifest["identity"]["session_id"] = "evaluation-session"
            for row in rows:
                row["session_id"] = "evaluation-session"
                p = row["payload"]
                if row["kind"] == "session_start": p["start_ms"] = 66_000
                elif row["kind"] == "session_end": p["end_ms"] = 66_400
                elif row["kind"] == "gaze_observation":
                    p["capture_ms"] += 65_500; p["delivery_ms"] += 65_500
                    p["provider_epoch_id"] = "evaluation-provider"
                elif row["kind"] == "rdf_tick":
                    for key in ["timestamp_ms", "window_start_ms", "window_end_ms"]: p["feature_vector"][key] += 65_500
        self.fixture.mutate(evaluation, later_evaluation)
        self.fixture.compress(reference); self.fixture.compress(evaluation)
        result = transfer.check_transfer(self.archive(reference, evaluation), "reference-session", "evaluation-session")
        check = result["coordinate_reference_check"]
        self.assertEqual(check["fit"]["status"], "READY")
        self.assertEqual(check["point_status_counts"], {"AVAILABLE": 3})
        self.assertEqual(check["reference_session_id"], "reference-session")

    def test_missing_selection_or_duplicate_sessions_are_rejected(self):
        a = self.fixture.package("a"); b = self.fixture.package("b")
        self.fixture.compress(a); self.fixture.compress(b)
        with self.assertRaisesRegex(ValueError, "duplicate session"):
            transfer.check_transfer(self.archive(a, b))
        with self.assertRaisesRegex(ValueError, "supplied together"):
            transfer.check_transfer(a, "session")
        with self.assertRaisesRegex(ValueError, "missing from received"):
            transfer.check_transfer(a, "missing", "session")

    def test_missing_coordinate_reference_is_explicit_and_raw_identity_is_retained(self):
        directory = self.fixture.package()
        result = transfer.check_transfer(directory)
        self.assertEqual(result["coordinate_reference_sessions"], [])
        self.assertEqual(result["packages"][0]["session_id"], "session")
        self.assertEqual(result["packages"][0]["coordinate_context_inventory"][0]["observation_count"], 3)
        self.assertEqual(result["rdf_source_links"][0]["status"], "SOURCE_MISSING_OR_COMPATIBILITY_UNCONFIRMED")

    def test_export_inventory_detects_an_entire_missing_package(self):
        a = self.fixture.package("a")
        b = self.fixture.package("b")
        def second_session(manifest, rows):
            manifest["identity"]["session_id"] = "second"
            for row in rows: row["session_id"] = "second"
        self.fixture.mutate(b, second_session)
        self.fixture.compress(a); self.fixture.compress(b)
        export = dict(schema_version="newsmead_study_export_v1", late_receipts=[], packages=[])
        for directory in [a, b]:
            raw = (directory / "manifest.json").read_bytes()
            manifest = json.loads(raw)
            export["packages"].append(dict(package_id=manifest["package_id"], session_id=manifest["identity"]["session_id"],
                                           manifest_sha256=hashlib.sha256(raw).hexdigest()))
        archive = self.archive(a, b)
        with zipfile.ZipFile(archive, "a") as handle:
            handle.writestr("study_export_manifest.json", json.dumps(export))
        self.assertEqual(transfer.check_transfer(archive)["export_inventory_status"], "ALL_DECLARED_PACKAGES_PRESENT")
        missing = self.fixture.root / "missing-package.zip"
        with zipfile.ZipFile(archive) as source, zipfile.ZipFile(missing, "w") as target:
            for entry in source.infolist():
                if not entry.filename.startswith("b/"): target.writestr(entry, source.read(entry))
        with self.assertRaisesRegex(ValueError, "package set differs"):
            transfer.check_transfer(missing)

    def test_rdf_source_match_requires_positive_calibration_identity(self):
        reading = self.fixture.package("reading")
        baseline = self.fixture.package("baseline")
        def baseline_metadata(manifest, rows):
            manifest["identity"]["session_id"] = "baseline-session"
            for row in rows: row["session_id"] = "baseline-session"
            rows[0]["payload"].update(phase="rdf_baseline", metadata=dict(baseline_attempt_id="attempt", calibration_id="calibration"))
        self.fixture.mutate(baseline, baseline_metadata)
        def reader_reference(manifest, rows):
            import copy
            template = rows[0]
            provider = copy.deepcopy(template)
            provider.update(kind="provider_start", payload=dict(calibration_id="calibration"))
            reference = copy.deepcopy(template)
            reference.update(kind="rdf_reference", payload=dict(reference=dict(session_id="attempt",
                              participant_code="synthetic", calibration_fingerprint="calibration")))
            rows[1:1] = [provider, reference]
            for seq, row in enumerate(rows): row.update(seq=seq, record_id=f"reading:{seq}")
        self.fixture.mutate(reading, reader_reference)
        self.fixture.compress(reading); self.fixture.compress(baseline)
        received = self.archive(reading, baseline)
        link = transfer.check_transfer(received)["rdf_source_links"][0]
        self.assertEqual(link["status"], "MATCHING_SOURCE_PRESENT")
        self.assertEqual(link["source_baseline_package_ids"], ["baseline"])
        def remove_calibration(manifest, rows): rows[0]["payload"]["metadata"]["calibration_id"] = None
        self.fixture.mutate(baseline, remove_calibration)
        self.fixture.compress(baseline)
        link = transfer.check_transfer(self.archive(reading, baseline))["rdf_source_links"][0]
        self.assertEqual(link["status"], "SOURCE_MISSING_OR_COMPATIBILITY_UNCONFIRMED")


if __name__ == "__main__":
    unittest.main()
