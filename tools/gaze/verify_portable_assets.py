#!/usr/bin/env python3
"""Optional byte audit of portable gaze inputs and a NewsMead debug APK.

No Gradle, network, Android SDK, or third-party Python modules are required.
Generated debug BuildConfig, when present, must match the requested mode;
this source check does not inspect the compiled APK's BuildConfig field.
"""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import zipfile


# Reviewed identities independent of the editable distribution inventory.
KNOWN_SHA256 = {
    'third_party/gaze/mgazenet/assets/base.mnn':
        '2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96',
    'third_party/gaze/mgazenet/assets/vendor-manifest.json':
        '56d075aa1b36b574ecb359da741f56c51a39635165bed8f64a1f77ec2275b063',
    'third_party/gaze/mgazenet/assets/notices/GazeFollower-CC-BY-NC-SA-4.0.txt':
        '9e502ba75fd0fd30cea5497b438a4bc12f07f02bc932a7a6041fd6a18b8f46a0',
    'third_party/gaze/mgazenet/assets/notices/MNN-Apache-2.0.txt':
        'a830d59c4f98d110e0540d0581efada06192ba6e798434307d1794b07a290302',
    'third_party/gaze/mgazenet/jniLibs/arm64-v8a/libMNN.so':
        'c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60',
    'third_party/gaze/mgazenet/jniLibs/arm64-v8a/libmnncore.so':
        '075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9',
    'third_party/gaze/mgazenet/jniLibs/arm64-v8a/libc++_shared.so':
        'f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970',
    'third_party/gaze/mgazenet/jniLibs/armeabi-v7a/libMNN.so':
        '3504fe856a208ead295536ff8398dcdd210e5cd7fc51d7cd34a9e1fcf084c2f7',
    'third_party/gaze/mgazenet/jniLibs/armeabi-v7a/libmnncore.so':
        '966aa04af136c8c99fe84309bc845e16478cee215a2b717b829635bfc20b1f9c',
    'third_party/gaze/mgazenet/jniLibs/armeabi-v7a/libc++_shared.so':
        '4df8b2e81690dd1393a94f65c18b41e5b91494b10b21415823a94ab6bb81018a',
    'third_party/gaze/gazefollower2/GazeNet_Next.mnn':
        '4547cc4685a7176aad75c50a25dc9a3368d1dd1fafe0d8ea5c32421af93a49b0',
    'third_party/gaze/gazefollower2/candidate.json':
        'e5a2ecbe665a8024fc601d875d8f58f63ff26c5ea14e69e36e0a1f5a7b9132c5',
    'third_party/gaze/gazefollower2/NOTICE.txt':
        '1ce8acc97a4c9afeae0eddcca2fe5d8368079a68dfcc03754cdbe4f4a4d099f1',
    'third_party/gaze/gazefollower2/GazeFollower2-LICENSE.txt':
        '846fe2d3b8891afd3552f7a221d1c745a8f190c90e0d6a20ccd8bc723e45353c',
    'app/src/main/assets/face_landmarker.task':
        '64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff',
    'app/src/main/assets/comparison/newsmead-current-vs-mgazenet-reading-v1.json':
        '41a3fbccd4df38c2bb13d4695a052fe15d3ef78423b19a4c1737d3de6944f185',
    'app/src/main/assets/notices/NewsMead-MGazeNet-attribution.txt':
        '4bdc9ad30439efca14bdb5f6b961d142627b56f4abc0fb9ce0b7273601fbd7a0',
}
CANDIDATE_PREFIX = "third_party/gaze/gazefollower2/"
EXPECTED_ABIS = {"arm64-v8a", "armeabi-v7a"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def project_file(root, relative):
    require(isinstance(relative, str) and relative, "Inventory path must be a nonempty string")
    path = PurePosixPath(relative)
    require(not path.is_absolute() and ".." not in path.parts
            and "\\" not in relative and ":" not in relative,
            f"Unsafe inventory path: {relative}")
    resolved = (root / relative).resolve()
    require(resolved.is_relative_to(root), f"Inventory path escapes project: {relative}")
    return resolved


def audit_sources(root, report):
    inventory = json.loads((root / "third_party/gaze/artifacts.json").read_text(encoding="utf-8"))
    require(inventory.get("schemaVersion") == 1, "Unsupported artifact inventory schema")
    files = inventory.get("files")
    require(isinstance(files, dict) and files, "Inventory files must be a nonempty object")
    for relative, expected in KNOWN_SHA256.items():
        require(relative in files, f"Missing reviewed inventory entry: {relative}")
        require(isinstance(files[relative], dict) and files[relative].get("sha256") == expected,
                f"Inventory redefines reviewed SHA-256: {relative}")
    checks = report["sourceChecks"] = []
    for relative, record in files.items():
        source = project_file(root, relative)
        require(isinstance(record, dict), f"Invalid inventory record: {relative}")
        size, sha = record.get("bytes"), record.get("sha256")
        require(type(size) is int and size >= 0, f"Invalid byte count: {relative}")
        require(isinstance(sha, str) and re.fullmatch(r"[0-9a-f]{64}", sha),
                f"Invalid SHA-256: {relative}")
        require(source.is_file(), f"Missing source file: {relative}")
        data = source.read_bytes()
        require(len(data) == size, f"Source byte count mismatch: {relative}")
        require(digest(data) == sha, f"Source SHA-256 mismatch: {relative}")
        checks.append({"path": relative, "bytes": size, "sha256": sha})
    return files


def apk_path(relative):
    for source, destination in (
        ("third_party/gaze/mgazenet/assets/", "assets/"),
        ("third_party/gaze/mgazenet/jniLibs/", "lib/"),
        ("app/src/main/assets/", "assets/"),
    ):
        if relative.startswith(source):
            return destination + relative[len(source):]
    # Synthetic tensors are benchmark assets; the app carries only these four files.
    if relative.startswith(CANDIDATE_PREFIX) and relative in KNOWN_SHA256:
        return "assets/gazefollower2/" + relative[len(CANDIDATE_PREFIX):]
    return None


def audit_apk(apk_pathname, files, baseline_only, report):
    report["apk"] = {"path": str(apk_pathname), "sha256": digest(apk_pathname.read_bytes())}
    checks = report["apk"]["entryChecks"] = []
    with zipfile.ZipFile(apk_pathname) as apk:
        names = apk.namelist()
        abis = sorted({name.split("/")[1] for name in names
                       if name.startswith("lib/") and len(name.split("/")) >= 3})
        report["apk"]["abis"] = abis
        require(set(abis) == EXPECTED_ABIS, f"Debug APK ABI mismatch: {abis}")
        if baseline_only:
            require(not any(name.startswith("assets/gazefollower2/") for name in names),
                    "Baseline-only APK contains candidate assets")
        skipped = report["apk"]["unmappedSourceEntries"] = []
        for relative, record in files.items():
            if baseline_only and relative.startswith(CANDIDATE_PREFIX):
                continue
            entry = apk_path(relative)
            if entry is None:
                skipped.append(relative)
                continue
            require(names.count(entry) == 1, f"Missing or duplicate APK entry: {entry}")
            data = apk.read(entry)
            require(len(data) == record["bytes"], f"APK byte count mismatch: {entry}")
            require(digest(data) == record["sha256"], f"APK SHA-256 mismatch: {entry}")
            checks.append({"path": entry, "bytes": len(data), "sha256": record["sha256"]})


def audit_build_config(root, baseline_only, report):
    config = root / "app/build/generated/source/buildConfig/debug/com/newsmead/BuildConfig.java"
    result = report["buildConfig"] = {"path": str(config), "present": config.is_file()}
    if config.is_file():
        match = re.search(r"\bGAZE_EXPERIMENT_ENABLED\s*=\s*(true|false)\s*;",
                          config.read_text(encoding="utf-8"))
        require(match is not None, "Generated debug BuildConfig lacks the experiment flag")
        enabled = match.group(1) == "true"
        result["gazeExperimentEnabled"] = enabled
        require(enabled == (not baseline_only),
                "Generated debug BuildConfig flag disagrees with requested mode; rebuild that mode")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--apk", type=Path, help="NewsMead both-ARM debug APK")
    parser.add_argument("--baseline-only", action="store_true", help="Expect candidate assets disabled")
    parser.add_argument("--report", type=Path, help="Write audit results as JSON")
    args = parser.parse_args()
    root = args.project.resolve()
    report = {"project": str(root), "mode": "baseline-only" if args.baseline_only else "candidate"}
    try:
        files = audit_sources(root, report)
        audit_build_config(root, args.baseline_only, report)
        if args.apk:
            audit_apk(args.apk.resolve(), files, args.baseline_only, report)
        report["passed"] = True
    except (OSError, ValueError, TypeError, KeyError, zipfile.BadZipFile) as error:
        report.update(passed=False, error=str(error))
    if args.report:
        try:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        except OSError as error:
            print(f"FAIL: Cannot write report: {error}", file=sys.stderr)
            return 1
    if not report["passed"]:
        print(f"FAIL: {report['error']}", file=sys.stderr)
        return 1
    print(f"PASS: {len(report['sourceChecks'])} source files verified"
          + (f"; {len(report['apk']['entryChecks'])} APK entries verified" if args.apk else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
