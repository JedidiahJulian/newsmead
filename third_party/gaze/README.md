# Verified native gaze build inputs

This is the explicit distribution subset needed by the app and optional native
benchmark. Files were copied byte-for-byte from the reviewed local artifacts;
no model conversion, quantization, retraining or runtime upgrade was performed.
Ordinary Android Studio Sync/Run requires no Python or asset download step.

- `mgazenet/assets`: original GazeFollower model, archival provenance manifest,
  GazeFollower and MNN license texts.
- `mgazenet/jniLibs`: the six verified MNN 3.6.1 CPU Session/JNI/C++ shared libraries
  for `arm64-v8a` and `armeabi-v7a`. The app debug APK includes both architectures.
- `gazefollower2`: verified GazeNet_Next FP32 MNN conversion, its unchanged host
  parity reference, upstream license and conversion attribution, and twelve
  deterministic synthetic input tensors for optional benchmark checks.
- `artifacts.json`: complete distribution inventory with file lengths and SHA-256.
  It also lists the existing tracked landmark, frozen protocol and attribution
  files used by this build. Gradle independently pins critical inputs and APK bytes.

The already tracked `app/src/main/assets/face_landmarker.task` is reused by both
modules; there is no second copy. Original source snapshots, conversion logs,
download archives, build products and participant records are excluded.
`mgazenet/assets/vendor-manifest.json` is retained verbatim as historical upstream
provenance: it lists the original download/reference set, not the current package
inventory. Use `artifacts.json` for the distributed subset and both native ABIs.

All hash-verified inputs have scoped `-text` Git attributes to prevent automatic
Windows/Linux newline conversion. Preserve these bytes, including license texts
and JSON references. These are ordinary Git files, not Git LFS pointers.

## Source and attribution

Original GazeFollower: commit `553920edcb7998c029828677f50f6d8eb4a16249`:
<https://github.com/GanchengZhu/GazeFollower/tree/553920edcb7998c029828677f50f6d8eb4a16249>.
See `mgazenet/assets/notices/GazeFollower-CC-BY-NC-SA-4.0.txt` and the app/benchmark
attribution assets for the original preprocessing and Android adaptations.

GazeFollower2: commit `5befceb1a6e1297e094331a5c114b7d0173e7c14`:
<https://github.com/GanchengZhu/GazeFollower2/tree/5befceb1a6e1297e094331a5c114b7d0173e7c14>.
See `gazefollower2/GazeFollower2-LICENSE.txt` (CC BY-NC-SA 4.0 and upstream dual
licensing notice) and `gazefollower2/NOTICE.txt` for conversion attribution.

MNN 3.6.1: <https://github.com/alibaba/MNN/tree/d407447ed56c4121a11ccbd266dc184ca1ead0c2>.
Both ABIs come from the verified release archive identified in the original
manifest. See `mgazenet/assets/notices/MNN-Apache-2.0.txt`. All existing upstream
terms and packaged app attributions continue to apply.

Updates require an explicit reviewed replacement, renewed numerical/runtime
checks, corresponding pin changes, and updated provenance. Editing this directory
alone cannot bypass the Gradle model/native hash gates. The archived parity
reference predates later device checks; its `android_validated=false` field is
historical and has intentionally not been rewritten.
