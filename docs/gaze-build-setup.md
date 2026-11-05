# Building the native gaze experiment branch

## Android Studio

1. Check out `experiment/gazefollower2-native` including `third_party/gaze`.
2. Open the repository in Android Studio. Use JDK 17 for Gradle and install the
   Android SDK Platform 34/build tools requested by the project.
3. Sync Gradle, choose `app` / `debug`, select a compatible ARM Android phone,
   and press Run. Allow normal Gradle/SDK dependency downloads on first use.
4. Home → **Calibrate eye tracker** → **Compare all three** starts the A/B/C guide.
   Use **Continue comparison** for its tasks. The A/C-only guide is temporarily
   disabled. Opening these screens is idle; capture requires the task's Start.

No model folder copy, Python installation, model conversion or vendor-fetch step
is needed. Models and both debug ARM runtimes are ordinary tracked Git files in
`third_party/gaze`. The existing tracked landmark is reused. `.gitattributes`
protects pinned model/runtime/reference/license inputs from newline conversion.
Gradle fails on a missing or altered input instead of downloading or silently
replacing it. `third_party/gaze/README.md` describes sources and licenses.

Android Studio creates its usual ignored SDK configuration (`local.properties`).
An optional personal `NEWSDATA_API_KEY` in that file is for live news fetching;
it is not required to build or use the controlled comparison passage. Do not
commit personal SDK paths or keys. App Firebase client configuration is already
tracked; this change adds no new account/credential setup.

The experiment applies to its controlled calibration/validation/reading screens.
It does not select the experimental model for ordinary article aids or change the
separate study-collection work.

## Command line and verification

With JDK 17 and the SDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:verifyMgazeNetDebugApk
```

Use `bash ./gradlew` on Linux/macOS. The app debug APK contains `arm64-v8a` and
`armeabi-v7a`, with the same MNN 3.6.1 CPU Session runtime and model bytes as the
previously verified build. Source hash checks run during ordinary `preBuild`.
The explicit APK task checks packaged original/candidate models, provenance,
six native libraries, landmark and notices against fixed SHA-256 identities.

The baseline-only override remains:

```powershell
.\gradlew.bat -Pgazefollower2CandidateDir= :app:assembleDebug :app:verifyMgazeNetDebugApk
```

It generates `GAZE_EXPERIMENT_ENABLED=false`, restores the original Home route
and omits the entire candidate asset subtree, even after a candidate build.
Omit the override on the next invocation to return to the default experiment.
A custom nonempty candidate path is still accepted only when its files match
the reviewed hashes. Calibration stores and algorithms are unchanged.

Optional benchmark build:

```powershell
.\gradlew.bat -PmgazenetBenchmark=true :mgazenet-benchmark:assembleDebug
```

The benchmark retains its ARM64 device target, verifies both distributed runtime
ABIs as inputs, and packages the same model plus synthetic fixtures for its
camera-free checks. No conversion is run by Gradle.

Maintainers can optionally audit the distribution and an APK with standard-library
Python; this is not needed by Android Studio or testers:

```powershell
python tools/gaze/verify_portable_assets.py --apk app/build/outputs/apk/debug/app-debug.apk
```

Add `--baseline-only` for the baseline APK. `third_party/gaze/artifacts.json` lists
the complete distribution subset. The old vendor manifest is preserved verbatim
as historical provenance, including references not needed in the distributed
build. Original ignored `output/` and `mgazenet-benchmark/vendor/` folders can be
absent. Never stage those folders or private recording/diagnostic files wholesale.

## Verified checkpoint — 2026-11-05

Built a fresh HEAD archive plus the scoped asset/build changes, then used a
throwaway Git index for Windows (`core.autocrlf=true`) and Linux-style (`false`)
checkouts. All 30 inventoried files retain exact lengths and SHA-256 hashes under
both settings. The final attribute set also passed this round trip. The main
repository index and study stash were not changed; no commits were created.

The Windows checkout contained no old ignored candidate/vendor folder, project
cache, build outputs or `local.properties`. With the installed JDK 17/SDK and
normal shared Gradle dependency cache, the offline default build completed all
96 tasks from scratch, including app, APK verification, optional benchmark and
43 focused experiment tests (zero failures/errors/skips). The app audit matched
17 packaged entries: both models, six native libraries, landmark, protocol,
provenance and notices. Generated experiment flag was true; UI/algorithm sources
are unchanged from the branch's existing A/B/C and A/C guide implementation.

An immediate empty-override build in the same isolated project also passed,
matching 13 baseline APK entries with flag false and no candidate subtree. This
checks stale candidate outputs cannot leak into a baseline APK. Benchmark APK
audits matched 25 enabled and nine baseline entries, including its synthetic
fixtures where enabled. Final independent static review found no remaining
actionable findings. Evidence: ignored `output/gaze-build-portability-01/`
(checkout audit, logs, APK audits/archives, host test XMLs and receipt).

These builds ran on Windows; Linux-style Git byte preservation was tested, not
a Linux Android SDK build. No device install/capture or gaze/calibration algorithm
change was performed. Ordinary first-time Gradle/SDK downloads still require
network access; model/runtime files do not.
