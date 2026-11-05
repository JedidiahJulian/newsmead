# GazeFollower2 native candidate — first implementation checkpoint

**Build setup update, 2026-11-05:** ordinary Android Studio Run consumes the
verified files in tracked `third_party/gaze`. No Python/conversion/vendor-fetch
step is required. See [current setup](gaze-build-setup.md). Conversion commands
below document the original research work; retained ignored output is evidence,
not a required input to a fresh checkout.

Subsequent app work: [three-configuration comparison flow](gazefollower2-app-comparison.md).
The first milestone's implementation scope below is retained as historical context.

Started 2026-11-04; phone checks completed 2026-11-05 (Asia/Manila).
Branch `experiment/gazefollower2-native`, based on NewsMead
`93a2abdef779278d52ab714fcfb0a3727e7f760b`. The owner approved creating this
branch and beginning the assessed native approach. Existing review notes were
carried forward; study-collection stash `44840d4` was not applied or modified.

## Implemented scope

The public FP32 model now has a checked host conversion and an opt-in Android
benchmark build. A native ridge calibration candidate is implemented and tested
in that benchmark. **The NewsMead app still uses its existing model and SVR.**
Android synthetic inference now passes on the owner's SM-A566B. This checkpoint
does not establish phone gaze accuracy or a live three-condition user comparison.

| Files | Responsibility |
| --- | --- |
| `tools/gaze/gazefollower2/prepare_candidate.py` | Validate the pinned ONNX graph, convert with MNN 3.6.1, check host Session outputs against ONNX Runtime, retain provenance and synthetic inputs. No camera/network/install code. |
| `tools/gaze/gazefollower2/synthetic-inputs.json` | Independent filename/count/SHA-256 pins for the existing benchmark's 12 deterministic input tensors. |
| `tools/gaze/gazefollower2/test_prepare_candidate.py` | Source/shape/fixture integrity checks, including jointly altered data and descriptor hashes. |
| `tools/gaze/gazefollower2/requirements-win-py312.txt` | Explicit workspace-local host dependencies. No Android runtime dependency was added. |
| `mgazenet-benchmark/.../MnnEstimator.kt` and `GazeNetNextCandidate.kt` | Allow a named, hash-checked candidate with output `outpu_0`; original default model/output remain unchanged. |
| `mgazenet-benchmark/.../RidgeCalibration.kt` | 258-feature, normalized-screen-XY mapping through centered Double Cholesky ridge; unpenalized intercept, explicit lambda/objective, finite inputs, failed-refit invalidation and terminal close. No fitting-data retention or persistence. |
| `mgazenet-benchmark/.../RidgeCalibrationTest.kt` | Ten analytical, degenerate-input, lifecycle and numerical tests. |
| `mgazenet-benchmark/.../GazeNetNextNativeTest.kt` | Two camera-free Android instrumentation checks: all synthetic outputs against ONNX and malformed-input rejection. Both passed on SM-A566B / Android 16. |
| `mgazenet-benchmark/build.gradle.kts` | Explicit candidate opt-in, independent always-running artifact validation and restricted asset copying. |

The ridge objective is sum of squared residuals plus lambda times squared
weights, without feature standardization or division of lambda by sample count.
Labels must be finite and in [0,1]; predictions are allowed outside the screen.
The default lambda 3 follows upstream but is not tuned or validated for NewsMead.
Fitting fails if the configured penalty disappears when added to a Gram diagonal;
it does not silently add jitter or change lambda. It implements the existing
`CalibrationRegressor` interface; no frozen calibration audit protocol was changed.

## Evidence

Source: GazeFollower2 `5befceb1a6e1297e094331a5c114b7d0173e7c14`.

| Artifact | SHA-256 / result |
| --- | --- |
| Public FP32 ONNX, 8,006,908 bytes | `5f635713b50134163545d1eb76868610253c4fadc467dd5c4f69bd271610b69e` |
| Reviewed MNN conversion, 11,432,348 bytes | `4547cc4685a7176aad75c50a25dc9a3368d1dd1fafe0d8ea5c32421af93a49b0` |
| Pinned candidate reference JSON | `e5a2ecbe665a8024fc601d875d8f58f63ff26c5ea14e69e36e0a1f5a7b9132c5` |
| Archived candidate benchmark APK | `946d7eeecf638b60df6436c53d18bb90aff0d083133589b12812fe229a1d2be8` |
| Archived instrumentation APK | `5a2f715792f1987c177e153f4eff61032d11dd7924cda2cc34cdb3d60a1bfe65` |

Full ONNX checking passed. Converted input names/shapes are exactly batch-one
face `[1,3,224,224]`, eyes `[1,3,112,112]`, rect `[1,12]`; output is
`outpu_0 [1,258]`. Host MNN CPU Session/default precision/four threads and ONNX
Runtime CPU agreed on all 258 values in each existing synthetic fixture:

| Fixture | Maximum absolute output difference |
| --- | ---: |
| ramp | 0.0000352263 |
| clipped | 0.0002307892 |
| channels | 0.0001296997 |

The predeclared comparator uses `abs(error) <= 0.001 + 0.0001 * abs(reference)`.
These are feature-output differences, not gaze error in pixels/cm/degrees.
Inputs reuse `tools/gaze/mgazenet/reference.py` and its original synthetic
preprocessing. This establishes neither parity with browser Canvas crops nor
representative camera-input accuracy.

Completed checks:

- 10 Python integrity/contract tests passed.
- All 163 benchmark JVM tests passed, including 10 new ridge tests.
- Candidate benchmark and instrumentation APKs compiled successfully.
- APK inspection verified both model hashes, the reference, all 12 input tensors
  and candidate notices.
- A missing candidate directory produced the intended validation failure.
- A subsequent ordinary benchmark build succeeded; APK inspection confirmed no
  candidate assets and the unchanged baseline-model hash.
- Focused review found and corrected the empty-directory build bypass and
  self-declared fixture provenance. Host testing exposed a lost-regularization
  numerical case; the explicit rejection fix was reviewed and all tests rerun.

Evidence lives under `output/gazefollower2-native/`: `candidate-01/candidate.json`,
`python-tests.log`, `build-final.log`, `missing-candidate-check.log`,
`default-assets-check.json`, and `build-01/receipt.json` plus saved test XMLs.
The candidate APKs are archived as `build-01/benchmark.apk` and
`build-01/benchmark-tests.apk`; the normal Gradle output now holds the later
baseline-only build. The archived candidate files were used for the phone check.

## Phone check — 2026-11-05

The owner authorized testing on the USB-connected phone. After ADB could not
start inside the sandbox, the authorized host connection succeeded. Installed
the two hash-verified archived APKs with `adb install -r`, preserving existing
benchmark package data. NewsMead itself was not installed or changed. Ran only
`com.newsmead.mgazenetbenchmark.GazeNetNextNativeTest`; no activity or camera was
started and no participant recordings were accessed.

Device: Samsung SM-A566B, Android 16 / API 36, arm64-v8a. Runtime: MNN 3.6.1 CPU
Session, four threads, default precision. Both tests passed (`OK (2 tests)`):

- All 258 values for each of three synthetic fixtures agreed with the pinned
  ONNX output at the original atol .001 / rtol .0001. Repeated inference agreed
  within the test's absolute 1e-5 tolerance on every output.
- Wrong-sized, NaN and infinite inputs were rejected; a subsequent valid input
  still produced 258 outputs.

| Fixture | Android vs ONNX maximum absolute output difference |
| --- | ---: |
| ramp | 0.0000310838 |
| clipped | 0.0001192093 |
| channels | 0.0000702739 |

Evidence: `output/gazefollower2-native/device-01/` contains `device.json`, both
install logs, `instrumentation.log`, the retrieved synthetic-only `report.json`
and `receipt.json`. The report's model/reference hashes match the archived build.
The benchmark and test packages remain installed with their existing data.

This establishes native loading, synthetic numerical parity and input rejection
on this phone. It does not measure gaze error, camera preprocessing parity,
end-to-end latency, sustained performance or ridge calibration on a person.
Instrumentation suite elapsed time is not an inference-latency benchmark.

## Reproduction and packaging

Use CPython 3.12 with the explicitly pinned requirements in an isolated
environment. This run used `output/gazefollower2-native/tooling/Scripts/python.exe`.
The existing system Python has MNN 3.6.0 and is not the matching reference.
The Python CLI's optional telemetry import tried and failed to install an SDK
during an initial help probe. Preparation calls the same native `_tools.mnnconvert`
entry directly, avoiding that wrapper and its automatic installation/logging.
ONNX Runtime telemetry is disabled in the host check.

Commands below use Windows Command Prompt and new output names so earlier
evidence is retained:

```cmd
output\gazefollower2-native\tooling\Scripts\python.exe tools\gaze\mgazenet\reference.py reference --model mgazenet-benchmark\vendor\assets\base.mnn --output output\gazefollower2-native\reference-02

output\gazefollower2-native\tooling\Scripts\python.exe tools\gaze\gazefollower2\prepare_candidate.py --onnx C:\Users\USER\OneDrive\Desktop\GazeFollower2\models\GazeNet_Next.onnx --fixtures output\gazefollower2-native\reference-02\report.json --license C:\Users\USER\OneDrive\Desktop\GazeFollower2\LICENSE --output output\gazefollower2-native\candidate-02

output\gazefollower2-native\tooling\Scripts\python.exe -m unittest discover -s tools\gaze\gazefollower2 -p test_*.py -v

set "JAVA_HOME=C:\Program Files\Java\jdk-17"
gradlew.bat --offline --no-daemon -PmgazenetBenchmark=true -Pgazefollower2CandidateDir=output/gazefollower2-native/candidate-01 :mgazenet-benchmark:testDebugUnitTest :mgazenet-benchmark:assembleDebug :mgazenet-benchmark:assembleDebugAndroidTest
```

The first offline build required uncached existing AAR dependencies; the successful
build fetched those declared versions. No dependency version was changed.

**Artifact pinning:** conversion is not byte-reproducible. A second conversion
differed only in 29 bytes within a UUID-shaped header string (byte offsets 73–107),
with equal file sizes; `reconversion-check.json` retains that comparison. An
optional native UUID-inspection call exited without a diagnostic, so the receipt
uses direct byte comparison. A regenerated model/reference needs review and
explicit pin updates in `GazeNetNextCandidate.kt` and the Gradle gate. The build
command above deliberately uses the retained, reviewed `candidate-01` artifact.
Do not replace it with an unchecked regeneration or loosen the hash gates.

## Remaining integration work

1. Establish behavior over representative valid camera inputs and boundary
   geometry under separately authorized validation. Three artificial fixtures
   alone do not establish conversion correctness for every plausible input.
2. Connect a candidate configuration to the app's acquisition/provider path with
   separate model/mapping/calibration storage. Ridge persistence and live model
   selection are not implemented at this checkpoint. Reconcile the stashed
   collector without changing its clock/raw/selected/missingness contracts.
3. Compare old+SVR, new+SVR and new+ridge with fresh matched calibration and
   independent targets; assess spatial error, precision, usable coverage and
   sample age. Filtering and any later fine-tuning need separate identities.
4. Promote only from phone accuracy/performance evidence and prepare compatible study baselines.

No main-app source, aid rules, metric semantics, active calibration, participant
recordings or study stash was changed. Camera-free benchmark installation and
execution were authorized and completed; no staging or commit occurred. The research interpretation remains in
[the assessment](gazefollower2-migration-assessment.md).
