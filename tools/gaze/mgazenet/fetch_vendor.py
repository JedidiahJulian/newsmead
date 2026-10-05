"""Explicit, hash-pinned downloads for the isolated Android harness; no installs.

Only writes mgazenet-benchmark/vendor. Never executes downloaded code, opens a
camera, invokes ADB, or downloads a participant dataset. Standard library only.
"""
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[3]
VENDOR = ROOT / "mgazenet-benchmark" / "vendor"
GF = "553920edcb7998c029828677f50f6d8eb4a16249"
MNN = "d407447ed56c4121a11ccbd266dc184ca1ead0c2"
MODEL_SHA = "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96"
MNN_ZIP_SHA = "46dc7e86d45b8d4e957db81d2603e0b7f6c9ce9b84092ffdcee1b843cbfc9d71"
LANDMARK_SHA = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"


def fetch(url, sha=None):
    request = urllib.request.Request(url, headers={"User-Agent": "NewsMead-MGazeNet-benchmark"})
    with urllib.request.urlopen(request, timeout=90) as response:
        payload = response.read()
    digest = hashlib.sha256(payload).hexdigest()
    if sha is not None and digest != sha:
        raise ValueError(f"SHA256 mismatch for {url}: {digest}")
    return payload


def write(relative, data):
    target = VENDOR / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)


def main():
    model_url = f"https://raw.githubusercontent.com/GanchengZhu/GazeFollower/{GF}/gazefollower/res/model_weights/base.mnn"
    model = fetch(model_url, MODEL_SHA)
    archive_url = "https://github.com/alibaba/MNN/releases/download/3.6.1/mnn_3.6.1_android_armv7_armv8_cpu_opencl_vulkan.zip"
    archive = zipfile.ZipFile(io.BytesIO(fetch(archive_url, MNN_ZIP_SHA)))
    # CPU Session JNI. Do not bundle GPU, audio, LLM, or MNN's OpenCV substitute.
    libraries = {
        "arm64-v8a": {
            "libMNN.so": "c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60",
            "libmnncore.so": "075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9",
            "libc++_shared.so": "f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970",
        },
        "armeabi-v7a": {
            "libMNN.so": "3504fe856a208ead295536ff8398dcdd210e5cd7fc51d7cd34a9e1fcf084c2f7",
            "libmnncore.so": "966aa04af136c8c99fe84309bc845e16478cee215a2b717b829635bfc20b1f9c",
            "libc++_shared.so": "4df8b2e81690dd1393a94f65c18b41e5b91494b10b21415823a94ab6bb81018a",
        },
    }
    write("assets/base.mnn", model)
    for abi, pinned_files in libraries.items():
        prefix = f"mnn_3.6.1_android_armv7_armv8_cpu_opencl_vulkan/{abi}/"
        for name, expected_sha in pinned_files.items():
            data = archive.read(prefix + name)
            if hashlib.sha256(data).hexdigest() != expected_sha:
                raise ValueError(f"SHA256 mismatch for {abi}/{name}")
            write(f"jniLibs/{abi}/{name}", data)
    landmark = ROOT / "app/src/main/assets/face_landmarker.task"
    data = landmark.read_bytes()
    if hashlib.sha256(data).hexdigest() != LANDMARK_SHA:
        raise ValueError("Existing Face Landmarker asset differs from the audited model")
    write("assets/face_landmarker.task", data)
    sources = ["gaze_estimator/MGazeNetGazeEstimator.py", "face_alignment/MediaPipeFaceAlignment.py",
               "calibration/SVRCalibration.py", "calibration/CalibrationController.py", "camera/WebCamCamera.py"]
    for source in sources:
        write("reference/" + source, fetch(f"https://raw.githubusercontent.com/GanchengZhu/GazeFollower/{GF}/gazefollower/{source}"))
    for name, url in {
        "GazeFollower-CC-BY-NC-SA-4.0.txt": f"https://raw.githubusercontent.com/GanchengZhu/GazeFollower/{GF}/LICENSE-CC-BY-NC-SA",
        "MNN-Apache-2.0.txt": f"https://raw.githubusercontent.com/alibaba/MNN/{MNN}/LICENSE.txt",
    }.items():
        write("assets/notices/" + name, fetch(url))
    manifest = {"gazefollower_revision": GF, "mnn_revision": MNN, "mnn_version": "3.6.1",
                "model_sha256": MODEL_SHA, "mnn_archive_sha256": MNN_ZIP_SHA,
                "face_landmarker_sha256": LANDMARK_SHA, "model_url": model_url,
                "mnn_archive_url": archive_url, "files": {}}
    for path in sorted(VENDOR.rglob("*")):
        if path.is_file() and path.name != "vendor-manifest.json":
            manifest["files"][path.relative_to(VENDOR).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    write("assets/vendor-manifest.json", (json.dumps(manifest, indent=2) + "\n").encode())
    print(f"Verified benchmark assets at {VENDOR}; no software installed.")


if __name__ == "__main__":
    main()
