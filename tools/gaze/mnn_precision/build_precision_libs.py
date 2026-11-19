"""Build prebuilt dual-ABI MNN CPU precision JNI libraries into app/src/main/jniLibs.

Maintainer build tool: requires pinned NDK 26.1.10909125, pinned MNN 3.6.1
headers, and the hash-verified vendor libMNN.so/libc++_shared.so files.
Groupmates building from Android Studio consume the prebuilt .so files directly.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

REPO = Path(__file__).resolve().parents[3]
DEFAULT_NDK = Path(r"C:\Users\USER\AppData\Local\Android\Sdk\ndk\26.1.10909125")
INCLUDE_ROOT = REPO / "output/mnn-gpu-probe-2026-11-13/include"
VENDOR_JNI = REPO / "mgazenet-benchmark/vendor/jniLibs"
OUT_JNI = REPO / "app/src/main/jniLibs"

EXPECTED_HEADERS = {
    "ErrorCode.hpp": "de7d2290e4e2f024ae51707298c30721a283367eb90a7abac113d27248d56dd0",
    "HalideRuntime.h": "f1d35170fa3fc1c7433ca1d18ca5d016ad230f26a0ecfba91b9a7ef9a49f7a37",
    "Interpreter.hpp": "0c367286c5906572218ddfa6426007ca748cbf5433f6cd7c7c71ae3300b1f986",
    "MNNDefine.h": "481f2ff7a4dd29ce779384b8f5169c8b1aee8ebd8f0a9ca4c2f8444de45f9b6f",
    "MNNForwardType.h": "b9c670a78f3ec42c81328429004fdd6c3ff1a172a52373dd09945a678e5ea167",
    "Tensor.hpp": "6f28b4d0338c6760582ed8d5632fa0c3691bdfacc421753b6d86407466a7bf5c",
}

EXPECTED_VENDOR_LIBS = {
    "arm64-v8a": {
        "libMNN.so": "c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60",
        "libc++_shared.so": "f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970",
    },
    "armeabi-v7a": {
        "libMNN.so": "3504fe856a208ead295536ff8398dcdd210e5cd7fc51d7cd34a9e1fcf084c2f7",
        "libc++_shared.so": "4df8b2e81690dd1393a94f65c18b41e5b91494b10b21415823a94ab6bb81018a",
    },
}

ABI_TARGETS = {
    "arm64-v8a": ["--target=aarch64-linux-android24"],
    "armeabi-v7a": ["--target=armv7a-linux-androideabi24", "-march=armv7-a", "-mthumb"],
}

EXPECTED_JNI_EXPORTS = {
    "Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_nativeCreate",
    "Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_nativeRun",
    "Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_nativeMainBackend",
    "Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_nativeClose",
}

ALLOWED_NEEDED = {"libMNN.so", "libc++_shared.so", "liblog.so", "libm.so", "libdl.so", "libc.so"}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_inputs() -> None:
    mnn_dir = INCLUDE_ROOT / "MNN"
    for name, expected in EXPECTED_HEADERS.items():
        path = mnn_dir / name
        assert path.is_file(), f"Missing MNN header: {path}"
        assert sha256(path) == expected, f"Header hash mismatch: {path}"
    for abi, libs in EXPECTED_VENDOR_LIBS.items():
        for name, expected in libs.items():
            path = VENDOR_JNI / abi / name
            assert path.is_file(), f"Missing vendor library: {path}"
            assert sha256(path) == expected, f"Vendor library hash mismatch: {path}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, default=DEFAULT_NDK)
    args = parser.parse_args()

    verify_inputs()

    ndk = args.ndk.resolve()
    props = ndk / "source.properties"
    assert props.is_file() and "Pkg.Revision = 26.1.10909125" in props.read_text(), (
        f"Expected NDK 26.1.10909125 at {ndk}"
    )
    bin_dir = ndk / "toolchains/llvm/prebuilt/windows-x86_64/bin"
    compiler = bin_dir / "clang++.exe"
    readelf = bin_dir / "llvm-readelf.exe"
    assert compiler.is_file() and readelf.is_file()

    source = Path(__file__).with_name("mnn_cpu_precision.cpp")
    assert source.is_file()

    receipt = {"ndk": str(ndk), "source_sha256": sha256(source), "abis": {}}
    for abi, target_flags in ABI_TARGETS.items():
        out_dir = OUT_JNI / abi
        out_dir.mkdir(parents=True, exist_ok=True)
        out_lib = out_dir / "libnewsmead_mnn_cpu.so"
        link_dir = VENDOR_JNI / abi
        cmd = [
            str(compiler),
            *target_flags,
            "-shared",
            "-fPIC",
            "-O2",
            "-std=c++17",
            "-Wall",
            "-Wextra",
            "-Werror=return-type",
            "-fvisibility=hidden",
            "-nostdlib++",
            "-I" + str(INCLUDE_ROOT),
            str(source),
            "-o",
            str(out_lib),
            "-L" + str(link_dir),
            "-Wl,--no-undefined",
            "-Wl,-z,max-page-size=16384",
            "-Wl,-soname,libnewsmead_mnn_cpu.so",
            "-Wl,--no-as-needed",
            "-lMNN",
            "-lc++_shared",
            "-llog",
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Compile failed for {abi}:\n{res.stdout}\n{res.stderr}")

        dynamic = subprocess.run([str(readelf), "-d", str(out_lib)], check=True, capture_output=True, text=True).stdout
        needed = re.findall(r"\(NEEDED\).*?\[([^\]]+)\]", dynamic)
        assert set(needed) <= ALLOWED_NEEDED, f"Unexpected NEEDED libs for {abi}: {needed}"

        syms_out = subprocess.run([str(readelf), "--dyn-syms", str(out_lib)], check=True, capture_output=True, text=True).stdout
        exported = {
            m.group(1)
            for m in re.finditer(r"\bFUNC\s+GLOBAL\s+DEFAULT\s+\d+\s+(Java_com_newsmead_gaze_mgazenet_MnnCpuPrecisionNative_\w+)", syms_out)
        }
        assert EXPECTED_JNI_EXPORTS <= exported, f"Missing JNI exports for {abi}: {EXPECTED_JNI_EXPORTS - exported}"

        receipt["abis"][abi] = {
            "path": str(out_lib.relative_to(REPO)),
            "sha256": sha256(out_lib),
            "bytes": out_lib.stat().st_size,
            "needed": needed,
            "exported_jni_symbols": sorted(exported),
        }

    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
