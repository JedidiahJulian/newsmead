"""Prepare a pinned, camera-free GazeNet_Next MNN benchmark artifact.

Reuses tensors from tools/gaze/mgazenet/reference.py; never opens a camera or
reads participant data. Dependencies must be provisioned explicitly. No installs
or network calls occur here. Host parity is not Android or human validation.
"""
import argparse
import hashlib
import importlib.metadata
import json
import math
from pathlib import Path
import platform
import shutil
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "mgazenet"))
import reference as baseline

SOURCE_SHA = "5f635713b50134163545d1eb76868610253c4fadc467dd5c4f69bd271610b69e"
UPSTREAM_REVISION = "5befceb1a6e1297e094331a5c114b7d0173e7c14"
MODEL_ID = "gazefollower2_gazenet_next_fp32"
SHAPES = {"face": [1, 3, 224, 224], "left": [1, 3, 112, 112],
          "right": [1, 3, 112, 112], "rect": [1, 12]}
OUTPUT_NAME = "outpu_0"
ATOL, RTOL = baseline.INFERENCE_ATOL, baseline.INFERENCE_RTOL
VERSIONS = {"MNN": "3.6.1", "numpy": "1.26.4", "opencv-python": "4.11.0.86",
            "onnx": "1.17.0", "onnxruntime": "1.20.1"}
EXPECTED_TENSORS = json.loads(Path(__file__).with_name("synthetic-inputs.json").read_text(encoding="utf-8"))["fixtures"]


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_fixtures(path):
    """Reject non-synthetic, incomplete, altered and nonfinite fixture records."""
    path = Path(path).resolve()
    report = json.loads(path.read_text(encoding="utf-8"))
    if (report.get("mode") != "synthetic_reference" or report.get("outcome") != "completed"
            or report.get("model_sha256") != baseline.MODEL_SHA
            or report.get("mnn_version") != "3.6.1" or report.get("opencv") != "4.11.0"):
        raise ValueError("Expected the pinned, completed MGazeNet synthetic reference")
    fixtures = report.get("fixtures", [])
    if [item.get("name") for item in fixtures] != list(baseline.NAMES):
        raise ValueError("Expected exactly the ordered ramp, clipped and channels fixtures")
    result = []
    for item in fixtures:
        tensors = {}
        for name, shape in SHAPES.items():
            descriptor = item[name]
            if descriptor != EXPECTED_TENSORS[item["name"]][name]:
                raise ValueError("Synthetic tensor provenance differs from checked-in pins: " + name)
            if descriptor.get("count") != math.prod(shape):
                raise ValueError("Incorrect fixture tensor count: " + name)
            values = baseline.floats(path.parent, descriptor)
            if len(values) != math.prod(shape) or not all(map(math.isfinite, values)):
                raise ValueError("Nonfinite or incomplete synthetic tensor: " + name)
            tensors[name] = values
        result.append((item, tensors))
    return result


def verify_onnx(model):
    def dimensions(value):
        return [d.dim_value if d.HasField("dim_value") else d.dim_param
                for d in value.type.tensor_type.shape.dim]
    if {i.name for i in model.graph.input} != set(SHAPES):
        raise ValueError("Unexpected ONNX inputs")
    for item in model.graph.input:
        dims = dimensions(item)
        if (item.type.tensor_type.elem_type != 1 or len(dims) != len(SHAPES[item.name])
                or dims[1:] != SHAPES[item.name][1:]
                or not (dims[0] == 1 or isinstance(dims[0], str) and dims[0])):
            raise ValueError("Unexpected ONNX input type/shape: " + item.name)
    if len(model.graph.output) != 1:
        raise ValueError("Expected one ONNX output")
    output = model.graph.output[0]
    dims = dimensions(output)
    if (output.name != OUTPUT_NAME or output.type.tensor_type.elem_type != 1
            or len(dims) != 2 or dims[1] != 258
            or not (dims[0] == 1 or isinstance(dims[0], str) and dims[0])):
        raise ValueError("Unexpected ONNX output contract")


def prepare(args):
    # Validate all source/provenance before running third-party model code.
    if sha256(args.onnx) != SOURCE_SHA:
        raise ValueError("Pinned ONNX hash mismatch")
    fixtures = read_fixtures(args.fixtures)
    if args.output.exists():
        raise ValueError("Choose a new output directory to retain previous evidence")
    license_bytes = args.license.read_bytes()
    versions = {name: importlib.metadata.version(name) for name in VERSIONS}
    if versions != VERSIONS:
        raise ValueError(f"Toolchain must match {VERSIONS}; found {versions}")
    import numpy as np
    import onnx
    import onnxruntime as ort
    import MNN

    source = onnx.load(str(args.onnx), load_external_data=False)
    onnx.checker.check_model(source, full_check=True)
    verify_onnx(source)
    args.output.mkdir(parents=True)
    model_path = args.output / "GazeNet_Next.mnn"
    report = {
        "schema_version": 1, "mode": "gazefollower2_synthetic_candidate",
        "model_id": MODEL_ID, "source_sha256": SOURCE_SHA,
        "upstream_revision": UPSTREAM_REVISION, "toolchain": versions,
        "host": {"platform": platform.platform(), "python": platform.python_version()},
        "input_shapes": SHAPES, "output_name": OUTPUT_NAME, "output_shape": [1, 258],
        "runtime": "MNN 3.6.1 CPU Session, 4 threads, default precision",
        "preprocessing": "existing_mgazenet_synthetic_rgb_uint8_linear_nchw_right_eye_flip",
        "fixture_report_sha256": sha256(args.fixtures),
        "tolerances": {"atol": ATOL, "rtol": RTOL},
        "onnx_checker_passed": True, "android_validated": False,
        "human_accuracy_established": False, "fixtures": [], "outcome": "failed",
    }
    try:
        command = [sys.executable, str(Path(__file__).resolve()), "_convert",
                   str(args.onnx.resolve()), str(model_path.resolve())]
        conversion = subprocess.run(command, capture_output=True, text=True, timeout=120)
        (args.output / "conversion.log").write_text(conversion.stdout + conversion.stderr, encoding="utf-8")
        report["conversion"] = {"engine": "_tools.mnnconvert (native entry used by upstream CLI)",
                                "batch": 1, "optimize_level": 1, "weight_precision": "FP32",
                                "returncode": conversion.returncode}
        if conversion.returncode != 0 or not model_path.is_file():
            raise RuntimeError("MNN conversion failed; inspect conversion.log")
        report["model_file"] = model_path.name
        report["model_sha256"] = sha256(model_path)
        report["model_bytes"] = model_path.stat().st_size

        ort.disable_telemetry_events()
        options = ort.SessionOptions()
        options.intra_op_num_threads = 4
        options.inter_op_num_threads = 1
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
        session = ort.InferenceSession(str(args.onnx), sess_options=options, providers=["CPUExecutionProvider"])
        interp = MNN.Interpreter(str(model_path))
        native = interp.createSession({"backend": "CPU", "numThread": 4})
        inputs = interp.getSessionInputAll(native)
        outputs = interp.getSessionOutputAll(native)
        if set(inputs) != set(SHAPES) or set(outputs) != {OUTPUT_NAME}:
            raise ValueError("Converted model input/output names differ")
        for name, shape in SHAPES.items():
            if list(inputs[name].getShape()) != shape:
                raise ValueError("Converted input layout mismatch: " + name)
        if list(outputs[OUTPUT_NAME].getShape()) != [1, 258]:
            raise ValueError("Converted output shape mismatch")
        report["converted_contract_checked"] = True
        for item, values in fixtures:
            feeds = {name: np.asarray(values[name], dtype=np.float32).reshape(shape)
                     for name, shape in SHAPES.items()}
            expected = session.run([OUTPUT_NAME], feeds)[0]
            if expected.shape != (1, 258) or not np.isfinite(expected).all():
                raise ValueError("Invalid ONNX output")
            for name, data in feeds.items():
                tensor = MNN.Tensor(tuple(SHAPES[name]), MNN.Halide_Type_Float,
                                    data, MNN.Tensor_DimensionType_Caffe)
                inputs[name].copyFrom(tensor)
            interp.runSession(native)
            host = MNN.Tensor((1, 258), MNN.Halide_Type_Float, np.zeros((1, 258), np.float32),
                              MNN.Tensor_DimensionType_Caffe)
            outputs[OUTPUT_NAME].copyToHostTensor(host)
            actual = list(host.getData())
            if len(actual) != 258 or not all(map(math.isfinite, actual)):
                raise ValueError("Invalid MNN output")
            expected_values = expected.reshape(-1).tolist()
            check = baseline.difference(actual, expected_values, ATOL, RTOL)
            row = {"name": item["name"], "onnx_output": expected_values,
                   "host_mnn_output": actual, "comparison": check}
            for name in SHAPES:
                descriptor = item[name]
                shutil.copyfile(args.fixtures.parent / descriptor["file"], args.output / descriptor["file"])
                row[name] = descriptor
            report["fixtures"].append(row)
        report["outcome"] = ("host_parity_passed" if all(row["comparison"]["pass"]
                             for row in report["fixtures"]) else "host_parity_failed")
        (args.output / "GazeFollower2-LICENSE.txt").write_bytes(license_bytes)
        (args.output / "NOTICE.txt").write_text(
            "GazeNet_Next by the GazeFollower team, CC BY-NC-SA 4.0.\n"
            f"https://github.com/GanchengZhu/GazeFollower2/tree/{UPSTREAM_REVISION}\n"
            "Converted to MNN 3.6.1 FP32, batch one, for NewsMead synthetic research checks.\n"
            "Model hash and conversion evidence: candidate.json. Not an accuracy validation.\n", encoding="utf-8")
    except Exception as error:
        report["error"] = str(error)
        raise
    finally:
        (args.output / "candidate.json").write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("outcome", "model_sha256", "model_bytes", "android_validated")}))
    return 0 if report["outcome"] == "host_parity_passed" else 1


def main():
    if len(sys.argv) == 4 and sys.argv[1] == "_convert":
        # The installed Python CLI wrapper tries to install an optional telemetry
        # SDK during import. Use its native conversion entry directly: no network,
        # logging wrapper or automatic install, with the exact pinned toolchain.
        import _tools
        _tools.mnnconvert(["MNNConvert", "-f", "ONNX", "--modelFile", sys.argv[2],
                           "--MNNModel", sys.argv[3], "--bizCode", "NewsMeadGazeFollower2",
                           "--batch", "1", "--optimizeLevel", "1"])
        return 0
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--onnx", required=True, type=Path)
    parser.add_argument("--fixtures", required=True, type=Path)
    parser.add_argument("--license", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return prepare(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
