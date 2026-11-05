# GazeFollower2 app comparison

2026-11-05, Asia/Manila. Experimental branch: `experiment/gazefollower2-native`.
Android Studio follow-up: this branch now sets `gazefollower2CandidateDir` in
project `gradle.properties` to tracked `third_party/gaze/gazefollower2`, so normal
Sync/Run includes the selector without local asset preparation. Original model,
both ARM runtimes, candidate and notices are tracked in `third_party/gaze` with
byte-preserving attributes. See [clean-checkout setup](gaze-build-setup.md). Previously
the CLI flag was omitted by Studio Run and produced the original calibration UI.
Verified a no-flag app build, both packaged model hashes and Home → A/B/C on the
phone after a data-preserving install. Empty override produces flag false and no
candidate in merged assets. Evidence: `output/gazefollower2-native/android-studio-default-01/`.
This implements the owner's approved Home-button selection, calibration,
independent validation, reading test and results flow. It is a development
comparison, not a claim of improved human gaze accuracy.

2026-11-05 results-history follow-up: owner reports earlier A/B/C table results
disappearing and subjectively finds A more accurate and C steadier. The prior
table hid anything outside the current session/calibration and omitted incomplete
validation attempts. Reports themselves were immutable and not deleted by this
filter. History now defaults to latest saved attempts across comparisons, with
comparison/collection/run IDs and current/previous-calibration labels. A selector
opens the current or a saved comparison, and **Show / hide saved attempts** exposes
earlier attempts. Failed/incomplete attempts remain visible with missing-target
counts. Workflow eligibility still requires current-session/current-calibration
validation; history browsing cannot satisfy it. Export chooses the displayed
comparison (or asks which comparison when viewing all).

These observations are owner feedback, not a measured model ranking or an
established explanation based on development effort. A/C change both model and
mapping; B/C share new-model features and the same calibration collection. All
three initial conditions share acquisition/preprocessing/output handling and use
no smoothing. Interpret stability alongside fresh cadence and sample age.

Owner then requested cm to match the existing checks. Error and jitter displays
now use centimetres to three decimals. New runs snapshot `pixelsPerCmX=xdpi/2.54`
and `pixelsPerCmY=ydpi/2.54` from Android display metrics, matching the physical
scale convention in `GazeTestActivity` and `GazeCalibrationActivity`. Android
documents [xdpi/ydpi](https://developer.android.com/reference/android/util/DisplayMetrics#xdpi)
as physical pixels per inch on each screen axis. This is device-reported scale;
no ruler measurement has been performed in this task.

Each sample's X/Y target error and successive differences are scaled before
radial percentiles, pooled jitter and RMS are calculated. Dividing a stored radial
median by average DPI would be incorrect for unequal axis scales. The experiment's
sample-error aggregation remains unchanged; adopting cm does not turn it into
the older check's distance-of-median-coordinate statistic.

Raw pixel coordinates/statistics remain in the files. Additive `centimeters`
objects carry method `newsmead_gaze_cm_spatial_v1`, axis scales and `scaleSource`
for held-out and per-target raw/selected metrics. New runs record their scale at
capture. Older local no-backup reports can be converted for display/export using
the current display scale only when saved physical pixel dimensions and rotation
match, explicitly labelled `current_device_display_metrics_for_legacy_local_report`.
Invalid/partial recorded scales never use that fallback. Unsupported conversion
shows unavailable; pixel reports remain saveable. Reading has no spatial-accuracy
metric. Older saved files are not rewritten by history browsing or export.

History/unit verification: 25 focused host tests and three phone checks passed
for history, then 35 focused host tests and three phone checks passed for the
combined cm/history build. Initial cm UI attempt found the phone Dozing; lifecycle
and spinner assertions failed until display wake, after which unchanged code
passed. Final app installed with data-preserving `-r`. Tests verify old-session/
old-calibration history, incomplete attempts, current-only eligibility, anisotropic
cm statistics, provenance, pixel retention and export without saved-file mutation.
Phone checks are camera-free. See `output/gazefollower2-native/result-history-01/`
and `output/gazefollower2-native/centimeters-01/` receipts, APKs and logs.

## User flow

The branch's default candidate build routes **Home → Calibrate eye tracker** to **Eye
tracker comparison**. Choose A (original model + SVR), B (new model + SVR) or C
(new model + ridge). Opening the menu, changing selection or opening a task is
idle; only the task's Start button requests the camera.

- **Calibrate selected:** reuse the existing 13-target collection and posture/
  timing/sample admission routine. A has a separate experiment artifact. A new
  model collection fits B and C on identical feature/label arrays and commits
  both fitted mappings atomically. Features are then cleared; images/features
  are not saved. These are *fitted, unvalidated* artifacts. The experiment does
  not apply or modify the legacy post-fit quality gate, nor label a fitted model
  an accuracy success.
- **Validate selected:** nine stationary positions from the existing accuracy
  geometry, eight held out from calibration plus a separately reported centre
  repeat. No estimated dot is displayed. Actual target draw acknowledgement
  anchors the 3,000–5,500 ms capture window and 5,750 ms delivery deadline.
- **Reading test:** enabled after a completed validation with samples at all
  eight held-out targets for this configuration, session and calibration. This
  is a navigation prerequisite, not a validated sample-count/accuracy threshold.
  Read the fixed passage for 60 seconds, scroll manually, with the raw gaze dot
  visible and assistance disabled. All configurations use the same settings.
- **Compare all three / Continue comparison:** start a new session, obtain fresh
  original and new-model collections, then guide validation and reading in one
  of six rotating orders. B/C reuse the same new-model collection. Progress is
  reconstructed from reports bound to the current calibration. Recalibration
  invalidates matching completion indicators without deleting older reports.
- **Saved results:** latest saved attempts remain visible across new comparisons
  and recalibration. Use the results selector to view one comparison; each row
  identifies its calibration collection. Mixed-comparison/calibration rows are
  exploratory. Expand saved attempts to inspect earlier runs in that view.
- **Export comparison results:** explicit Android document-picker export of the
  chosen comparison JSON, including incomplete/failed validation and reading
  runs. Full coordinate rows remain in export; history loads only scalar summaries
  into memory. Files remain private until the owner exports them.

The normal reading tracker remains available through **Calibrate normal reading
tracker**. Experimental selection applies to these controlled task screens;
ordinary articles, aids, study baselines and the older frozen comparison are not
silently switched. A future promotion to normal reading needs a separate decision
based on the collected accuracy/performance evidence.

## Guided A/C confirmation

2026-11-05: after exploratory inspection of the owner's exported comparison, the
owner approved repeating A/C with consistent setup and reversed order. **Start
A/C confirmation** creates four blocks: round 1 **A → C**, round 2 **C → A**.
Every block requires a new calibration, the existing nine-target validation and
the existing 60-second reading task. Even the consecutive C blocks require
different calibration collections. This provides two fresh-fit observations per
configuration; it is an exploratory repeatability check, not a population study
or a new accuracy threshold.

Record support/lighting/glasses notes and, optionally, a measured or estimated
viewing distance in cm. Distance is explicitly stored as self-report; the app
does not measure it. Keep phone and seated head position, lighting and glasses
consistent, and take breaks between blocks. Each task opens idle. Its setup
checkbox must be selected before Start becomes available. Opening or resuming a
plan never starts the camera.

**Continue A/C** opens the next task. Completed validation and reading reports
must match this comparison, block, configuration, collection and fingerprint.
Validation retains the existing all-held-out-targets eligibility prerequisite;
completion is not an accuracy pass. Reading must follow validation: elapsed
times are compared only for a known common boot; strict UTC timestamps are the
fallback when boot identity is unavailable. Known different boots require a
fresh calibration. A completed historical block remains complete when a later
block replaces its active calibration. An unfinished block whose fit has been
replaced requires another fresh fit. Preferences persist the plan and its
generation bindings so navigation can resume after activity/process recreation.

The A/C round table retains four rows with validation error/jitter in cm,
reading fresh Hz/sample age and collection IDs. The general saved-results view
continues to include older comparisons. Export the **current comparison** after
all four blocks; it includes the plan snapshot, per-attempt block/round/order,
setup notes, self-reported distance, setup-attestation time and boot identity.
Past-plan reports retain their tags even when the current preference no longer
holds that plan. Raw pixels, cm scale provenance and incomplete attempts remain
in the export. Reports without the optional `endedAtUtc` key remain readable;
an incorrect type, when present, is rejected.

Implementation: `ACConfirmationPlan.kt`, `ACConfirmationLaunch.kt` and the three
`GazeExperiment*Activity` screens. Model weights, calibration fitting, capture
windows, filtering and metric formulas are unchanged. Main reading and the
separate study collector remain outside this flow. Passing software checks does
not establish human repeatability or choose between A/C; use the owner's fresh
sessions as independent confirmation data rather than tuning these algorithms
on the previous export. Evidence: `output/gazefollower2-native/ac-confirmation-01/`.

Verification: final app/test APK builds, vendor APK hash checks and all **43**
focused experiment JVM tests passed. All **seven** camera-free UI tests passed
on SM-A566B / Android 16, including four A/C checks plus the existing history/
selection checks. Tests restore preferences and compare private calibration/report
file metadata before/after; no Start is pressed. Focused independent review found
no remaining actionable issues. Installed with data-preserving `-r`, verified the
Home route and creation-form layout, then left that form idle for the owner.

## Measurement contract

All coordinates are physical screen pixels. Camera and consumer timestamps use
elapsed-realtime milliseconds. Wall-clock UTC identifies the run, not sample
durations. Each report identifies model hash, calibration fingerprint, shared
collection ID, mapping through condition ID, protocol, filter, acquisition/runtime
metadata, original geometry and task outcome.

Target error is the distribution of individual sample-to-target distances,
including median and nearest-rank p95, X/Y mean bias and per-target sample counts.
It is not the distance of the median coordinate from the target. Precision is
2D sample SD pooled within targets; successive RMS pairs do not cross invalid
observations, expired continuity or target boundaries. The main aggregate is
sample-weighted over held-out targets; per-target results remain available.
The centre repeat never enters held-out aggregates. Missing expected targets
remain explicit even if a task aborts before presenting them.

Fresh rate is unique valid gaze outputs divided by the actual measured interval.
Usable fraction is valid outputs divided by observed real delivered outputs;
watchdog and terminal counter events are excluded from that denominator. Analyzer
arrivals and busy drops are separate cumulative source totals, which can include
initialization; CameraX undelivered frames remain unknown. Sample age covers all
real deliveries with usable clocks, including invalid/stale gaze, avoiding a
latency statistic limited to successful outputs. These are software pipeline
measurements, not photon-to-display latency or true eye-movement reaction time.

All three initial conditions use unsmoothed calibrated gaze and the existing
eye-area/output gates. Raw and selected coordinates are both recorded; no
look-ahead filter is introduced. Natural reading has no ground-truth accuracy
or precision claim. It supplies sustained cadence, age, coverage and gap records.
Reading measurement begins at a view-tree draw-cycle acknowledgement, a software
display proxy; priming/pre-display frames are excluded and counted separately.
Initialization and asynchronous resource shutdown are separate from that reading
interval. Original geometry is snapshotted before capture and used after shutdown
so an interrupted/detached activity can still save its aborted report.

Reports retain bounded coordinate/time/reason rows, per-target windows, raw and
selected summaries, observed gaps and explicit truncation/corruption indicators.
No images or model embeddings are exported. The reading passage/font/line height,
dot condition and bounded scroll-event timeline are identified. This lightweight
reading test is not the study collector, a scanpath/AOI validation, or an aid
effectiveness experiment. The separate study-collection stash remains untouched.

## Storage, build and implementation

Legacy storage remains `noBackupFilesDir/mgazenet-v2/calibration.bin` with its
original canonical identity and `load(): SvrCalibration` API. Experiment artifacts
live under `gaze-experiment-v1`: A in `current-svr.bin`, B/C in one `next-pair.bin`.
The experiment envelope binds configuration, real model/mapping identity,
device/geometry/acquisition, timestamp and collection ID. Interrupted pair writes
are rejected for both configurations. Ridge is centered Double Cholesky, lambda
3, sum-squared-loss convention and unpenalized intercept; this lambda is an
explicit development choice, not a phone-validated optimum.

Reports live under `noBackupFilesDir/gaze-experiment/runs` as immutable UUID JSON
files. Navigation preferences track the current session, order and selection,
plus the current A/C plan and its calibration-generation bindings when present.

Primary implementation files:

- `activities/GazeExperimentSetupActivity.kt`, `GazeExperimentCalibrationActivity.kt`,
  `GazeExperimentRunActivity.kt`; Home entry and manifest declarations.
- `gaze/experiment/ExperimentFlow.kt`, `ExperimentMeasurements.kt`,
  `ExperimentReportStore.kt`.
- `gaze/mgazenet/GazeExperimentConfiguration.kt`, `CalibrationRegressor.kt`,
  `RidgeCalibration.kt` and optional configuration seams in the existing model,
  store, identity, source and provider. Default legacy behavior is retained.
- `gradle.properties`, `app/build.gradle.kts`: branch default candidate setting,
  explicit baseline override and always-running candidate hash gate.

Build using the already reviewed candidate (Windows Command Prompt):

```cmd
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
gradlew.bat --offline --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:verifyMgazeNetDebugApk
```

In Android Studio, sync the project with Gradle files and Run the `app` debug
configuration normally. No per-run CLI flag is needed. The reviewed
files in tracked `third_party/gaze` are included in a fresh checkout; old ignored
candidate/vendor folders are no longer build inputs. Missing or changed artifacts
fail verification instead of silently hiding the selector.

For an explicit baseline build, pass `-Pgazefollower2CandidateDir=`. This empty
override selects the original Home calibration entry and excludes candidate
assets in both app and benchmark modules. Leaving the override off uses this
branch's default experiment. The experiment uses the pinned FP32
conversion and MNN 3.6.1 already checked on SM-A566B; see the
[native checkpoint](gazefollower2-native-prototype.md).

## Verification checkpoint

Implementation evidence is retained under
`output/gazefollower2-native/app-integration-01/`.

Focused review found and fixed detached-view access during asynchronous stop,
and a reading interval starting before the passage draw cycle. Compilation found
the legacy store return-type regression; the original API was restored and a
separate regressor API added. Initial corrected build passed 188 focused JVM
tests and built both APKs with vendor-asset verification. Final checks completed:

- **195 focused JVM tests passed**, zero failures/errors/skips. These include
  existing MGazeNet/coordinate regressions and new identity, ridge, measurement
  and report-store tests. Report checks cover nullable metrics, malformed content,
  immutable IDs, filtering and bounded export.
- **10 unique camera-free Android tests passed** on SM-A566B / Android 16 API 36 /
  arm64-v8a. Nine passed initially; one incorrect assertion on `performClick()`'s
  handled return was corrected to check selection/control state and passed its
  focused rerun. Application code did not change for this correction.
- Native checks exercise original/new inference and use an isolated UUID cache
  directory for all A/B/C and legacy persistence tests. They verify paired
  collection IDs and exact reload predictions, wrong-configuration rejection,
  cancellation rollback and interrupted-pair rejection. Temporary test data is
  removed; real calibrations are not overwritten.
- Idle UI tests cover selection, disabled controls, lifecycle and recreation.
  Navigation preferences are restored and calibration/report file metadata is
  checked unchanged. Normal setup reads saved status; this is not a claim that
  tests avoid all storage reads.
- Installed experiment app and corrected tests with `adb install -r`, preserving
  existing app data. Verified Home → selector → guided ABC → idle A calibration.
  `setup.png` records the checked layout. Phone is left at **Start 13-point
  calibration**; the agent did not press Start.
- Before the Android Studio default fix, ordinary app build passed without the candidate property: flag false, no
  candidate assets and original model hash intact. It was **not installed**.
  That check preceded the project-default setting above; the archived candidate
  remains available for reinstalling the original verified experiment build.
- `git diff --check` passed. No staging, commits or study-stash application.

Installed application archive: `app-integration-01/candidate-build/app.apk`,
SHA-256 `5282055317237206736f3890616acee89e3f8e2ffa3e15b6768c2a0e1e2e322d`.
Corrected test archive: `candidate-build/tests-ui-corrected.apk`, SHA-256
`21ea7315e132fd22ecc2078b6ed625009ba3aad29fb9e92775ca38a4f47dcdac`.
Full receipts, logs, saved host XMLs and UI evidence are indexed by
`app-integration-01/receipt.json` under `output/gazefollower2-native/`.

No experiment results from a person have been collected by this implementation
task. Full live camera-to-calibration/reading behavior remains for the owner's
session. Passing software checks cannot select the best tracker for reading.
