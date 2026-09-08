import copy
import hashlib
import json
import unittest
from pathlib import Path
from accuracy_metrics import evaluate
from collect_accuracy import validate


class AccuracyIntegrityTests(unittest.TestCase):
    @staticmethod
    def bind_manifests(report):
        pipeline_encoded = json.dumps(report["calibration_manifest"]["pipeline"], separators=(",", ":"))
        report["pipeline_json"] = pipeline_encoded
        report["pipeline_id"] = hashlib.sha256(pipeline_encoded.encode()).hexdigest()
        encoded = json.dumps(report["calibration_manifest"], separators=(",", ":"))
        digest = hashlib.sha256(encoded.encode()).hexdigest()
        report["calibration_manifest_json"] = encoded
        report["calibration_manifest_sha256"] = digest
        report["calibration_id"] = report["session_id"] + ":" + digest

    def manifest_fixture(self):
        # Frozen Kotlin export uses invented features/predictions, never a person's data.
        # Keep this in the source tree so integrity tests also run before a Gradle build.
        path = Path(__file__).parent / "fixtures/accuracy-harness-kotlin-synthetic.json"
        return json.loads(path.read_text(encoding="utf-8"))

    def test_manifest_changes_or_misbound_session_are_rejected(self):
        report = self.manifest_fixture()
        self.assertIsNone(evaluate(report)["accuracy_gate_pass"])
        for change in (
            lambda r: r["calibration_manifest"].update(training_rows=586),
            lambda r: r.update(calibration_manifest_json='{}'),
            lambda r: r.update(calibration_id="wrong_session"),
            lambda r: r.update(outcome="stopped"),
        ):
            corrupted = copy.deepcopy(report); change(corrupted)
            with self.assertRaises(ValueError):
                evaluate(corrupted)

    def test_complete_harness_export_rejects_cross_field_changes(self):
        report = self.manifest_fixture()
        self.assertEqual(8, evaluate(report)["planned_blocks"])
        for change in (
            lambda r: r.update(device_id="other"),
            lambda r: r.update(pipeline_id="0"*64),
            lambda r: r["calibration_manifest"]["pipeline"].update(model_sha256="0"*64),
            lambda r: r["fit_points"][1].update(accepted=44),
            lambda r: r.update(fit_block_ids=list(reversed(r["fit_block_ids"]))),
            lambda r: r["blocks"][0].update(id="test_3"),
            lambda r: r["blocks"][0].update(start_ms=r["blocks"][0]["start_ms"]+1),
            lambda r: r["blocks"][0].update(line_height_px=r["blocks"][0]["line_height_px"]+1),
            lambda r: r.update(features_retained=True),
        ):
            corrupted = copy.deepcopy(report); change(corrupted)
            with self.assertRaises(ValueError):
                evaluate(corrupted)

    def test_removing_manifests_cannot_bypass_harness_validation(self):
        keys = ("calibration_manifest_json", "calibration_manifest_sha256",
                "calibration_manifest", "pipeline_json")
        for kind in ("recorded", "synthetic_contract"):
            for removed in [(key,) for key in keys] + [keys]:
                report = self.manifest_fixture()
                report["evidence_kind"] = kind
                for key in removed:
                    report.pop(key)
                with self.subTest(kind=kind, removed=removed), self.assertRaises(ValueError):
                    evaluate(report)

    def test_rehashed_geometry_or_protocol_changes_are_rejected(self):
        for change in (
            lambda r: r["blocks"][0].update(target_px=[200, 191]),
            lambda r: r["blocks"][0].update(region="grid_8"),
            lambda r: r["calibration_manifest"]["targets"][1].update(point_px=[100, 100]),
            lambda r: r["calibration_manifest"].update(screen_px=[100, 100]),
            lambda r: r["fit_points"][0].update(practice=False),
            lambda r: r["blocks"][1]["lines"][0].update(rect_px=[41, 101, 1040, 161]),
            lambda r: (r.update(protocol_id="changed"), r["calibration_manifest"].update(protocol="changed")),
            lambda r: r["calibration_manifest"].update(test_settle_ms=2000),
            lambda r: r.pop("outcome"),
        ):
            report = self.manifest_fixture()
            change(report)
            self.bind_manifests(report)
            with self.assertRaises(ValueError):
                evaluate(report)

    def test_recorded_evidence_requires_the_pinned_assets(self):
        report = self.manifest_fixture()
        report["evidence_kind"] = "recorded"
        report["calibration_manifest"]["pipeline"].update(
            model_sha256="2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96",
            face_landmarker_sha256="64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
        )
        self.bind_manifests(report)
        self.assertIsNone(evaluate(report)["accuracy_gate_pass"])
        report["calibration_manifest"]["pipeline"]["model_sha256"] = "0" * 64
        self.bind_manifests(report)
        with self.assertRaises(ValueError):
            evaluate(report)

    def test_collector_retains_partial_record_but_refuses_hash_or_identity_change(self):
        report = {"schema": "mgazenet_accuracy_partial_v1", "session_id": "1_abc", "outcome": "stopped",
                  "camera_frames_retained": False}
        raw = json.dumps(report).encode()
        digest = hashlib.sha256(raw).hexdigest()
        self.assertEqual(validate(raw, digest, "1_abc")["outcome"], "stopped")
        with self.assertRaises(ValueError):
            validate(raw, "0"*64, "1_abc")
        with self.assertRaises(ValueError):
            validate(raw, digest, "2_def")


if __name__ == "__main__":
    unittest.main()
