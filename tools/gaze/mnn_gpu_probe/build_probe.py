"""Build an optional, camera-free MNN GPU probe; never installs or opens a camera.

The original vendor libraries are read-only. Extra debug libraries live in output/.
Run --prepare-only first; --ndk is required to compile the reviewed JNI source.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.request
import zipfile

REPO = Path(__file__).resolve().parents[3]
OUT = REPO / 'output/mnn-gpu-probe-2026-11-13'
REVISION = 'd407447ed56c4121a11ccbd266dc184ca1ead0c2'
ARCHIVE_SHA = '46dc7e86d45b8d4e957db81d2603e0b7f6c9ce9b84092ffdcee1b843cbfc9d71'
ARCHIVE_URL = 'https://github.com/alibaba/MNN/releases/download/3.6.1/mnn_3.6.1_android_armv7_armv8_cpu_opencl_vulkan.zip'
PREFIX = 'mnn_3.6.1_android_armv7_armv8_cpu_opencl_vulkan/arm64-v8a/'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--prepare-only', action='store_true')
parser.add_argument('--ndk', type=Path)
args = parser.parse_args()
assert args.prepare_only or args.ndk, '--ndk is required for compilation'
OUT.mkdir(parents=True, exist_ok=True)

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for data in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(data)
    return digest.hexdigest()

def verified_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        assert path.read_bytes() == data, f'Retained file differs: {path}'
    else:
        path.write_bytes(data)

archive = OUT / 'mnn-3.6.1-official.zip'
if not archive.exists():
    partial = archive.with_suffix('.zip.partial')
    assert not partial.exists(), 'Retained incomplete download needs diagnosis before another attempt'
    print('Downloading the hash-pinned official MNN release', flush=True)
    request = urllib.request.Request(ARCHIVE_URL, headers={'User-Agent': 'NewsMead-isolated-GPU-probe'})
    with urllib.request.urlopen(request, timeout=90) as response, partial.open('xb') as target:
        for data in iter(lambda: response.read(1024 * 1024), b''):
            target.write(data)
    assert sha(partial) == ARCHIVE_SHA, 'Official archive hash mismatch; nothing extracted'
    partial.rename(archive)
assert sha(archive) == ARCHIVE_SHA, 'Retained archive hash mismatch'

include = OUT / 'include/MNN'
headers = {}
pending = ['Interpreter.hpp', 'Tensor.hpp', 'MNNForwardType.h', 'MNNDefine.h', 'ErrorCode.hpp', 'HalideRuntime.h']
prior_receipt = OUT / 'headers-receipt.json'
prior = {row['name']: row['sha256'] for row in json.loads(prior_receipt.read_text())} if prior_receipt.exists() else {}
while pending:
    name = pending.pop()
    if name in headers:
        continue
    assert re.fullmatch(r'[A-Za-z0-9_]+\.(?:h|hpp)', name), f'Unexpected public header path: {name}'
    path = include / name
    url = f'https://raw.githubusercontent.com/alibaba/MNN/{REVISION}/include/MNN/{name}'
    if path.exists():
        assert name in prior and sha(path) == prior[name], f'Unverified retained header: {name}'
    else:
        verified_write(path, urllib.request.urlopen(url, timeout=40).read())
    headers[name] = dict(sha256=sha(path), url=url)
    pending.extend(re.findall(r'#include\s*[<"]MNN/([A-Za-z0-9_]+\.(?:h|hpp))[>"]', path.read_text()))
prior_receipt.write_text(json.dumps([
    dict(name=name, **headers[name]) for name in sorted(headers)
], indent=2) + '\n')

jni = OUT / 'jniLibs/arm64-v8a'
link = OUT / 'link-libs/arm64-v8a'
with zipfile.ZipFile(archive) as package:
    available = [n[len(PREFIX):] for n in package.namelist() if n.startswith(PREFIX) and n.endswith('.so')]
    print('Official arm64 libraries: ' + ', '.join(available), flush=True)
    for name in ('libMNN.so', 'libc++_shared.so', 'libMNN_CL.so'):
        assert name in available, f'Required OpenCL dependency absent: {name}'
        target = (jni if name == 'libMNN_CL.so' else link) / name
        verified_write(target, package.read(PREFIX + name))
    # The probe links exactly the same CPU core and C++ runtime already in NewsMead.
    for name in ('libMNN.so', 'libc++_shared.so'):
        vendor = REPO / 'mgazenet-benchmark/vendor/jniLibs/arm64-v8a' / name
        assert sha(vendor) == sha(link / name), f'Existing pinned library differs: {name}'

provenance = dict(mnn_revision=REVISION, archive_url=ARCHIVE_URL, archive_sha256=ARCHIVE_SHA,
    headers=headers, opencl_library_sha256=sha(jni / 'libMNN_CL.so'),
    existing_vendor_unchanged=True, camera_access=False, install_performed=False)
(OUT / 'source-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
if args.prepare_only:
    print('Prepared pinned headers/OpenCL library; original vendor files unchanged', flush=True)
    raise SystemExit(0)

ndk = args.ndk.resolve()
assert 'Pkg.Revision = 26.1.10909125' in (ndk / 'source.properties').read_text(), 'Use the pinned probe compiler NDK26.1.10909125'
bin_dir = ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
compiler = bin_dir / 'clang++.exe'
assert compiler.is_file()
source = Path(__file__).with_name('mnn_gpu_probe.cpp')
assert source.is_file()
library = jni / 'libnewsmead_mnn_gpu_probe.so'
command = [str(compiler), '--target=aarch64-linux-android24', '-shared', '-fPIC', '-O2', '-std=c++17',
    '-Wall', '-Wextra', '-Werror=return-type', '-fvisibility=hidden', '-nostdlib++',
    '-I' + str(OUT / 'include'), str(source), '-o', str(library),
    '-L' + str(link), '-L' + str(jni), '-Wl,--no-undefined', '-Wl,-z,max-page-size=16384',
    '-Wl,-soname,libnewsmead_mnn_gpu_probe.so', '-Wl,--no-as-needed', '-lMNN', '-lMNN_CL', '-lc++_shared', '-llog']
result = subprocess.run(command, capture_output=True, text=True)
(OUT / 'native-build.log').write_text(result.stdout + result.stderr)
assert result.returncode == 0, 'Native build failed; inspect native-build.log before another attempt'
dependencies = {}
for path in (jni / 'libMNN_CL.so', library):
    dynamic = subprocess.run([str(bin_dir / 'llvm-readelf.exe'), '-d', str(path)], check=True, capture_output=True, text=True).stdout
    (OUT / (path.name + '-dynamic.txt')).write_text(dynamic)
    dependencies[path.name] = re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', dynamic)
    allowed = {'libMNN.so', 'libMNN_CL.so', 'libc++_shared.so', 'liblog.so', 'libandroid.so',
        'libm.so', 'libdl.so', 'libc.so', 'libz.so', 'libGLESv2.so', 'libEGL.so'}
    assert set(dependencies[path.name]) <= allowed, f'Unresolved native dependencies: {dependencies[path.name]}'
receipt = dict(source_sha256=sha(source), ndk=str(ndk), command=command, native_dependencies=dependencies,
    libraries={p.name: sha(p) for p in jni.glob('*.so')}, abi='arm64-v8a', min_api=24,
    optional_debug_only=True, camera_access=False, install_performed=False)
(OUT / 'native-build-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print('Built optional arm64 GPU probe; no install/camera/default change', flush=True)
