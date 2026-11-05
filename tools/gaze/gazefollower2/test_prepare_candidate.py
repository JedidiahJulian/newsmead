"""Integrity checks for synthetic candidate preparation; no model execution/camera."""
import array
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import copy
from types import SimpleNamespace

import prepare_candidate as candidate


class FixtureIntegrityTest(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[3]
        temp_root = root / "output" / "gazefollower2-native" / "unit-temp"
        temp_root.mkdir(parents=True, exist_ok=True)
        if not temp_root.resolve().is_relative_to(root.resolve()):
            raise ValueError("Test temporary path escaped workspace")
        self.temp = tempfile.TemporaryDirectory(dir=temp_root)
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.path = self.folder / "report.json"
        self.report = {"mode": "synthetic_reference", "outcome": "completed",
                       "model_sha256": candidate.baseline.MODEL_SHA,
                       "mnn_version": "3.6.1", "opencv": "4.11.0", "fixtures": []}
        for name in candidate.baseline.NAMES:
            row = {"name": name}
            for tensor, shape in candidate.SHAPES.items():
                count = candidate.math.prod(shape)
                data = array.array("f", [0.25] * count)
                if candidate.sys.byteorder != "little":
                    data.byteswap()
                raw = data.tobytes()
                file = self.folder / f"{name}_{tensor}.f32"
                file.write_bytes(raw)
                row[tensor] = {"file": file.name, "count": count,
                               "sha256": hashlib.sha256(raw).hexdigest()}
            self.report["fixtures"].append(row)
        # Independent trust root for these tiny generated unit-test fixtures.
        # Real preparation uses the checked-in deterministic fixture hashes.
        pins = copy.deepcopy({row["name"]: {name: row[name] for name in candidate.SHAPES}
                              for row in self.report["fixtures"]})
        mocked = patch.object(candidate, "EXPECTED_TENSORS", pins)
        mocked.start()
        self.addCleanup(mocked.stop)
        self.save()

    def save(self):
        self.path.write_text(json.dumps(self.report), encoding="utf-8")

    def test_accepts_complete_verified_synthetic_inputs(self):
        result = candidate.read_fixtures(self.path)
        self.assertEqual([row[0]["name"] for row in result], list(candidate.baseline.NAMES))
        self.assertEqual(len(result[0][1]["face"]), 150528)
        self.assertEqual(result[-1][1]["rect"], array.array("f", [0.25] * 12))

    def test_rejects_live_or_incompatible_reference(self):
        for key, value in [("mode", "reading"), ("outcome", "failed"),
                           ("model_sha256", "0" * 64), ("mnn_version", "3.6.0")]:
            original = self.report[key]
            self.report[key] = value
            self.save()
            with self.assertRaises(ValueError):
                candidate.read_fixtures(self.path)
            self.report[key] = original

    def test_rejects_missing_or_duplicate_fixtures(self):
        original = self.report["fixtures"]
        for fixtures in [original[:2], [original[0], original[0], original[2]], original[::-1]]:
            self.report["fixtures"] = fixtures
            self.save()
            with self.assertRaises(ValueError):
                candidate.read_fixtures(self.path)

    def test_rejects_modified_tensor_and_wrong_count(self):
        descriptor = self.report["fixtures"][0]["rect"]
        descriptor["count"] = 11
        self.save()
        with self.assertRaises(ValueError):
            candidate.read_fixtures(self.path)
        descriptor["count"] = 12
        self.save()
        (self.folder / descriptor["file"]).write_bytes(b"x" * 48)
        with self.assertRaises(ValueError):
            candidate.read_fixtures(self.path)

    def test_rejects_nonfinite_even_with_valid_hash(self):
        descriptor = self.report["fixtures"][0]["rect"]
        data = array.array("f", [float("nan")] * 12).tobytes()
        (self.folder / descriptor["file"]).write_bytes(data)
        descriptor["sha256"] = hashlib.sha256(data).hexdigest()
        candidate.EXPECTED_TENSORS["ramp"]["rect"] = copy.deepcopy(descriptor)
        self.save()
        with self.assertRaises(ValueError):
            candidate.read_fixtures(self.path)

    def test_rejects_jointly_modified_finite_tensor_and_descriptor_hash(self):
        descriptor = self.report["fixtures"][0]["rect"]
        data = array.array("f", [0.5] * 12).tobytes()
        (self.folder / descriptor["file"]).write_bytes(data)
        descriptor["sha256"] = hashlib.sha256(data).hexdigest()
        self.save()
        with self.assertRaisesRegex(ValueError, "provenance"):
            candidate.read_fixtures(self.path)

    def test_rejects_tensor_path_escape(self):
        self.report["fixtures"][0]["rect"]["file"] = "../outside.f32"
        self.save()
        with self.assertRaises(ValueError):
            candidate.read_fixtures(self.path)

    def test_rejects_unpinned_model_before_creating_artifact(self):
        fake = self.folder / "unrelated.onnx"
        fake.write_bytes(b"unreviewed")
        output = self.folder / "artifact"
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            candidate.prepare(SimpleNamespace(onnx=fake, fixtures=self.path, output=output))
        self.assertFalse(output.exists())


class OnnxContractTest(unittest.TestCase):
    @staticmethod
    def model(output_name=candidate.OUTPUT_NAME, output_count=258, face_channels=3):
        from onnx import helper, TensorProto
        inputs = [helper.make_tensor_value_info(name, TensorProto.FLOAT,
                  ["batch", face_channels, 224, 224] if name == "face" else ["batch", *shape[1:]])
                  for name, shape in candidate.SHAPES.items()]
        output = helper.make_tensor_value_info(output_name, TensorProto.FLOAT, ["batch", output_count])
        return helper.make_model(helper.make_graph([], "contract_only", inputs, [output]))

    def test_accepts_dynamic_batch_one_contract(self):
        candidate.verify_onnx(self.model())

    def test_rejects_wrong_output_spelling_width_and_input_shape(self):
        for model in [self.model(output_name="output_0"), self.model(output_count=257), self.model(face_channels=4)]:
            with self.assertRaises(ValueError):
                candidate.verify_onnx(model)


if __name__ == "__main__":
    unittest.main()
