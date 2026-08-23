# Current Gaze System Diagnostics Log

## Purpose

This document is the evidence log for the initial diagnostic investigation of NewsMead's current gaze system. The investigation must determine what is working, what is failing, and what remains uncertain before any improvement is proposed or implemented.

The diagnostics are exploratory. Checks will follow the evidence found in the current implementation and test results rather than a predetermined improvement roadmap.

## Current Status

- **Started:** 2026-08-20
- **Branch:** `gaze-pipeline-improvements`
- **Status:** Initial Galaxy A56 calibration and live-path baseline collected and analyzed
- **Current backend:** MediaPipe Face Landmarker with iris-relative features, participant calibration, and screen-coordinate output
- **Repository state:** Work remains local and unstaged

## Investigation Boundaries

- Do not change gaze-estimation behavior while establishing the initial diagnosis.
- Do not describe a proposed modification as an improvement without comparative evidence.
- Keep calibration, validation, and accuracy-test observations distinct.
- Distinguish tracker error from calibration, coordinate-transform, filtering, timing, scrolling, and AOI-mapping errors.
- Preserve raw measurements when evaluating any displayed or filtered output.
- Do not retain participant camera frames under the current consent conditions.
- Do not stage, commit, or push diagnostic work without explicit approval.

## Baseline Context

The manuscript reports historical MediaPipe measurements of approximately 1.68-2.42 cm overall error and 0.60-1.01 cm vertical error. These values predate the rebuilt calibration flow and are context only; they are not the authoritative baseline for the current implementation.

The relevant reading threshold is approximately one rendered line height, currently treated as about 0.6 cm under the study configuration. The current system must be measured again before determining whether this threshold is met consistently.

## Investigation Journal

Use one entry for each inspection, diagnostic run, or newly discovered line of inquiry. Do not record an interpretation as a finding until supporting evidence is available.

### 2026-08-20 — Investigation opened

**Question:** What, if anything, is currently limiting the gaze system's accuracy, stability, or downstream interpretation?

**Action:** Created this diagnostic log before modifying the gaze pipeline.

**Evidence:** No diagnostic execution has been performed yet.

**Observation:** The historical manuscript measurements cannot be treated as the current baseline because the calibration flow has since been rebuilt.

**Interpretation:** Pending inspection and measurement.

**Next inquiry:** Inspect the current gaze data path and identify observable points where evidence can be collected without changing estimator behavior.

### 2026-08-21 — Static camera-to-AOI data-path audit

**Question:** Can the current implementation produce an authoritative, reproducible diagnosis without changing gaze-estimation behavior?

**Action:** Traced the current implementation from CameraX and MediaPipe through raw-feature extraction, calibration collection, polynomial mapping, temporal filtering, optional drift correction, the nine-point accuracy screen, `GazeProvider`, article AOI mapping, and RSI/scaffolding consumers. Reviewed the existing gaze unit-test inventory and attempted to execute it.

**Evidence:** E-002 through E-009.

**Observations:**

- The main coordinate contract is internally explicit: `GazeProvider` emits full-screen pixels, calibration and accuracy targets occupy full-screen `CalibrationView` instances, and article AOIs use `getLocationOnScreen`. Static inspection did not reveal a definite double offset or view-local/full-screen mix-up. This still requires an on-device coordinate check.
- The raw source averages the two eyes into one feature pair before exposing data. Separate left-eye and right-eye behavior cannot be diagnosed from the current callback or logs.
- The timestamp emitted with a raw sample is created when the MediaPipe result callback runs, not when the camera frame was captured. Downstream code creates another wall-clock timestamp when consuming gaze. The current data cannot measure camera-to-output latency.
- Calibration JSON persists per-target counts, aggregate feature medians, dispersion, blink/no-face deltas, leave-one-out fit metrics, and held-out validation errors. It does not persist the individual raw samples described by `FixationWindowFilter`, so the filter cannot actually be replayed from the saved session log.
- Calibration validation maps an aggregated raw feature directly through `GazeMapper`. The normal live path first applies a seven-sample median and One Euro filters and may then apply a saved affine drift correction. Calibration validation is therefore useful for the base mapper but is not an end-to-end measurement of the signal consumed by the article.
- The nine-point test does measure the live provider, but it automatically loads any saved drift correction. A run is not an uncorrected baseline unless the correction is absent or explicitly reverted.
- Nine-point observations and their per-target errors are printed to Logcat and shown in the UI but are not saved as a session artifact. Only applied/reverted correction summaries are persisted. Existing output is insufficient for a durable repeated-run baseline.
- The article currently uses `ScaffoldMode.DEMO_CYCLE`. This does not affect the standalone calibration or nine-point test, but it means an article run is not presently exercising adaptive scaffold triggering.
- Pure-Kotlin unit tests exist for the mapper, calibration quality, filters, drift correction, target stabilization, reading inference, stability estimation, and scaffold controller. There are no equivalent automated tests for camera transformation, MediaPipe feature extraction, Android screen-coordinate alignment, or live article AOI alignment.

**Test execution result:** The gaze unit-test suite did not execute. The first command used JDK 21 and failed in the project's older KAPT configuration. Retrying with JDK 17 passed that point but the command-line build could not read the installed Android SDK metadata or locate Build Tools 34.0.0. These are environment/toolchain failures, not failed gaze assertions. Temporary Gradle/Android diagnostic caches created by the attempt were removed.

**Interpretation:** No specific gaze-estimation defect has yet been demonstrated. The confirmed immediate problem is diagnostic observability: the current app cannot persist a clean, condition-labelled, per-target end-to-end baseline or separate source, mapping, filtering, correction, and latency effects. A diagnostics-only instrumentation change is required before the phone run can answer the root-cause question reliably.

**Next inquiry:** Decide whether to add diagnostics-only logging that preserves current estimator behavior while recording the signals and condition metadata missing from the current baseline path.

### 2026-08-21 — Diagnostics-only instrumentation added locally

**Question:** Can the missing evidence be captured during the existing nine-point test without changing the gaze estimate delivered to the UI?

**Action:** Added passive telemetry callbacks and a numeric-only `gaze_accuracy_session_*.json` writer. The nine-point screen now asks for a run label and records data only during each target's existing SAMPLE window.

**Evidence:** E-010 through E-014.

**Recorded metadata and signals:**

- Run label, device/Android details, screen size, density, rendered line height, calibration-point count, and whether an affine drift correction was active.
- Per-attempt target, final estimate, horizontal/vertical residual, Euclidean error, output dispersion, and collector sample counts.
- Separate eye-1 and eye-2 normalized gaze features and openness, their unchanged averaged raw feature, frame dimensions/rotation, capture/submission/result timestamps, result outcome, and cumulative busy-frame drops.
- Averaged raw feature, seven-sample median output, One Euro output, base mapper output, and final corrected-or-uncorrected screen output for every emitted sample inside the measurement window.
- Run summary containing accepted-point count, median error, P95 error, median absolute vertical error, and the existing leave-one-out estimate for the optional affine correction.

**Behavioral boundary:** The values passed to `GazeProvider.onGaze` still follow the existing calculation and order. Diagnostic callbacks observe intermediate values, and JSON is flushed after a point finishes rather than during its SAMPLE window. No camera bitmap or participant image is saved.

**Verification status:** Static inspection and `git diff --check` completed. A command-line Android build remains blocked by the SDK/toolchain access issue recorded in E-009, so compilation and A56 behavior are not yet confirmed. ADB is installed, but no Android device was connected when checked.

**Interpretation:** The next phone run can be diagnostically useful once this local build compiles and is installed. It should begin with no saved drift correction so the first labelled run measures the base live pipeline.

**Next inquiry:** Compile/install the diagnostics build, run the existing full calibration, then collect a labelled nine-point base-pipeline session on the Galaxy A56.

### 2026-08-21 — Full calibration diagnostics coverage added locally

**Question:** Does the diagnostics coverage match the system's actual 16-point calibration and 5-point validation design, rather than observing only the nine-point accuracy/recalibration screen?

**Action:** Extended the existing calibration-session JSON to record every SAMPLE attempt from the practice target, all 16 fitting targets, the repeated near-center drift target, and all 5 held-out validation targets. Each attempt now includes its presentation kind and result status plus the same per-eye, openness, source-outcome, frame-orientation, timing, and busy-drop telemetry captured by the nine-point diagnostics. Validation entries now also retain mapped coordinates and horizontal/vertical residuals.

**Evidence:** E-015 and E-016.

**Behavioral boundary:** The calibration targets, timings, filtering, retry/exclusion rules, mapper fit, validation calculation, and save/quality-gate behavior are unchanged. Only passive callbacks and numeric JSON fields were added; no camera image is retained.

**Verification status:** Call-site inspection and `git diff --check` passed (apart from existing line-ending warnings). Device compilation and execution remain blocked until the Android build environment is usable and the Galaxy A56 is connected.

**Interpretation:** Diagnostic coverage now reflects the role of each screen: the 16-point sequence fits the main mapper, the repeated point measures within-session drift, the 5 held-out points test that mapper, and the separate 9-point screen measures the complete live path and optionally fits affine recalibration.

**Next inquiry:** Verify the local changes statically, then compile/install and collect the calibration and uncorrected nine-point artifacts on the Galaxy A56.

### 2026-08-23 — Initial Galaxy A56 baseline collected

**Question:** What measurable limitations or improvement opportunities appear in the current tracker under one normal-light, glasses, handheld baseline condition?

**Action:** Built and ran the diagnostics instrumentation through Android Studio on the Galaxy A56. Retrieved two calibration sessions and four nine-point accuracy sessions. The first calibration ended in `redo_all`; the second was accepted. The first accuracy run was unlabelled, followed by three labelled baseline repetitions. No affine drift correction was active in any accuracy artifact.

**Evidence:** E-017 through E-020. Numeric artifacts are retained locally under `diagnostics-local/2026-08-23/diagnostic-export/` and remain unstaged. Originals remain in app-private phone storage.

**Observations:**

- The accepted calibration reported a 311 px (2.62-line) leave-one-out median, 913 px (7.70-line) leave-one-out P95, 286 px (2.41-line) held-out validation median, and 253 px (2.13-line) within-calibration drift. The drift check was flagged high, but the calibration still received the green advisory because the gate uses only the worse of the two medians and does not incorporate P95 or the drift flag into its band.
- The unlabelled live-path run measured 201 px (1.70 lines) median error. The three labelled baseline repetitions then measured 404, 492, and 489 px (3.41, 4.15, and 4.12 lines). Their vertical medians were 205, 310, and 292 px (1.73, 2.61, and 2.46 lines). This is meaningful short-term between-run variation even though the correction state remained unchanged.
- Across the 27 labelled measurements, horizontal residuals were consistently leftward and increased with target column: median `dx` was -201 px at the left column, -346 px at center, and -483 px at the right. This is not random point jitter; it is a live horizontal offset/compression pattern relative to the accepted calibration.
- The labelled live raw horizontal feature range was approximately 0.477-0.523, narrower and left-shifted relative to the accepted calibration's approximately 0.49-0.55 feature progression across columns. The earlier unlabelled run reached approximately 0.537 and was substantially more accurate. This associates the live error increase with a changed eye/phone/head geometry or feature response rather than a newly enabled correction.
- Vertical target ordering remained visible in the average raw feature, but individual target estimates were unstable and sometimes extrapolated outside the screen (for example, one top-center estimate mapped to y=-230 px). The quadratic mapping's high LOO tail error is consistent with localized instability even when its median passes.
- Both eyes responded monotonically to horizontal target position in the labelled runs. Their normalized horizontal values had a consistent offset (roughly 0.07-0.10 median absolute separation), so the current evidence does not justify declaring either eye defective; it does show that per-eye treatment is worth evaluating instead of assuming averaging is optimal.
- No blink, no-face, or insufficient-landmark outcomes occurred inside the recorded SAMPLE windows. Median MediaPipe submit-to-result time was approximately 29-31 ms and P95 approximately 37-38 ms. Busy-frame drops occurred, but every accuracy target was accepted and retained enough samples. Tracking loss and inference execution time were not the dominant limitation in these runs.
- Diagnostic frames reveal a negotiated camera frame of 320x240 (240x320 after upright rotation), not the 480x360 analysis profile described in source comments and project documentation. Whether a higher supported capture resolution improves iris geometry without unacceptable throughput loss is now a testable opportunity.
- The `1.7` value displayed during the first accuracy run was its measured median error in line-heights, not a newly changed pass threshold. Current calibration and accuracy bands remain green at no more than 3.0 lines and amber at no more than 4.5 lines; those constants predate this diagnostics branch.

**Interpretation:** The initial evidence does not point to face detection or raw inference latency as the primary constraint. The strongest improvement opportunities are calibration-gate sensitivity to tail error/drift, robustness to small geometry changes, horizontal live-range compression/bias, localized instability in the quadratic map, confirmation of the actual CameraX resolution tradeoff, and evidence-based per-eye combination. These are diagnostic conclusions, not implemented changes.

**Next inquiry:** Reproduce the rapid post-calibration shift under one fixed-phone condition and compare the accepted calibration's target-wise feature geometry with immediate and delayed nine-point runs before selecting a modification.

### 2026-08-23 — Fixed-phone repeatability check completed

**Question:** Was the handheld baseline's rapid accuracy change primarily caused by motion of the phone, or does substantial instability remain when the phone is physically supported?

**Action:** Collected five full calibration-session artifacts while the phone was supported at approximately 50 cm, followed by three uncorrected nine-point runs immediately, after one minute, and after two minutes. Lighting and glasses status were held constant. The final accepted calibration was the mapping active for all three accuracy runs.

**Evidence:** E-021 through E-024. Artifacts are retained locally under `diagnostics-local/2026-08-23/fixed-phone/fixed-phone-export/` and remain unstaged.

**Observations:**

- Calibration repeatability remained poor with the phone fixed. Across the five sessions, leave-one-out median error ranged from 191-539 px (1.62-4.54 lines), held-out validation median ranged from 156-782 px (1.31-6.60 lines), and measured drift ranged from 12-681 px. Three sessions were redone and two were accepted.
- The final calibration had a 191 px (1.62-line) leave-one-out median and 211 px (1.78-line) validation median, but its leave-one-out P95 was 499 px (4.21 lines), one held-out validation point missed by 569 px (4.80 lines), and the repeated calibration target moved by 500 px (4.22 lines). It still received a median-based green advisory with a separate drift warning.
- Fixed-phone nine-point median error was 322, 306, and 400 px (2.72, 2.58, and 3.37 lines). This was generally better than the labelled handheld baseline's 3.41-4.15 lines, so phone stability matters, but it did not make the tracker reliably line-accurate.
- Fixed-phone nine-point P95 error remained extreme at 982-1106 px (8.28-9.33 lines). Bottom-right error was repeatably severe at 990, 846, and 1106 px across the three runs. Median-only reporting hides this regional failure.
- Horizontal median residual changed from +113 px immediately to -49 px after one minute and -188 px after two minutes. Over the same period, the observed raw horizontal range shifted left from approximately 0.492-0.532 to 0.479-0.520 even though the phone was fixed and no correction was active.
- The final calibration's repeated near-center raw feature changed from `(0.5150, 0.4030)` to `(0.5205, 0.3904)`. The vertical change of approximately 0.0126 is large relative to the roughly 0.035 total median feature separation between the top and bottom calibration rows and produced the 500 px mapped drift.
- Calibration vertical features overlap substantially between rows, especially toward the lower half of the screen. The final bottom-right calibration feature (`fy` approximately 0.38) was similar to several middle-row features, limiting how uniquely a two-value iris-in-eye feature can identify vertical screen position under changing head/eyelid geometry.
- No face, blink, or insufficient-landmark outcomes occurred during these fixed-phone SAMPLE windows. MediaPipe submit-to-result latency remained approximately 30-31 ms median and 36-38 ms P95. The new evidence therefore continues to point away from detection availability or inference execution time as the binding issue.

**Interpretation:** Supporting the phone reduces some bias but does not resolve the main instability. Phone motion was a contributor, not the sole cause. The dominant observed limitations are calibration-to-calibration variability, raw-feature sensitivity to small participant geometry changes, weak/overlapping vertical feature separation, localized polynomial failure, and a quality gate that does not reject severe tail, regional, or drift failures. These findings are sufficient to guide a proposal; additional arbitrary lighting-condition runs are not required before evaluating diagnostic-backed options.

**Next inquiry:** Compare low-risk changes to calibration acceptance, mapping robustness, pose/eye normalization, resolution negotiation, and live drift handling against these recorded failure modes before choosing what to implement.

### 2026-08-23 — Historical regression and target-wise error confirmed

**Question:** Is the present performance an unavoidable limit of the MediaPipe/iris backend, or has the NewsMead calibration/live path regressed from a previously more usable state?

**Action:** Recorded the researcher's observation that earlier runs were typically close to the intended line-level range, with approximately 1.2 line-heights or lower considered the useful target. Compared that observation with the repository's dated performance notes and Git history, then replayed the final fixed-phone calibration and all three fixed-phone accuracy streams through alternative mapping/aggregation paths without changing the app.

**Evidence:** E-025 through E-028. The replay implementation is retained locally at `diagnostics-local/analyze_mapper_replay.py` and exactly reproduced the current saved point estimates (0.00 px median replay difference).

**Observations:**

- The repository independently records pre-redesign A56 vertical medians of 0.67-1.01 cm and a sister-prototype result near 0.7 cm consistently. At the current rendered line height on the A56 (approximately 0.67 cm), the prototype figure is about one line-height and broadly corroborates the researcher's memory. The current fixed-phone vertical medians of 269-341 px are approximately 1.52-1.92 cm or 2.27-2.88 line-heights.
- The likely regression window begins with the July 24 calibration redesign and includes the new shared adaptive collector/filter, subsequent live extrapolation changes, and accuracy-test rearchitecture. This identifies a bounded comparison target; it does not establish which change caused the regression.
- Replaying the old hard-clamped quadratic mapping reduced P95 from 990 to 919 px immediately, 981 to 824 px after one minute, and 1106 to 709 px after two minutes. Median error changed from 322 to 322 px, 306 to 242 px, and 400 to 350 px respectively. Soft extrapolation therefore amplifies some out-of-range regional failures, but restoring the clamp alone does not recover line-level performance.
- Replacing the current lead-trim/MAD aggregation with a plain median over the available recorded SAMPLE data produced small and mixed changes. The new point filter is therefore not sufficient by itself to explain the regression.
- A simple affine raw-feature mapper also produced mixed results and did not restore the earlier performance. The raw feature geometry and its temporal shift remain limiting even under a less curved model.
- Individual target results confirm the researcher's concern about median-only decisions. In the fixed-phone runs, the bottom-right target missed by 990, 846, and 1106 px while the session median could still receive a favorable band. One side or region can therefore fail severely while the aggregate median hides it.

**Interpretation:** The historical evidence and offline replay support treating the current state as a regression investigation, not as proof that MediaPipe iris landmarks can never approach line-level use. Mapper extrapolation contributes to catastrophic tail errors, but calibration-feature instability/overlap remains after removing it. Target-wise vertical and regional results must be used both for acceptance and for deciding whether any correction is global or localized.

**Correction-design constraint:** A single worst target should first be re-measured to establish that the miss is repeatable. If a regional miss persists, forcing that one point to zero with an unconstrained correction could distort neighboring regions. A defensible correction needs spatial regularization, target-wise reporting, and evaluation on a separate post-correction run. The same nine points used to fit a local correction cannot also serve as evidence that it works.

**Next inquiry:** Produce a diagnostics-backed proposal that separates (1) stricter target-wise acceptance/recheck, (2) restoration/comparison of the last known better calibration and mapping behavior, and (3) robust global versus regional correction, with an independent verification requirement.

## Evidence Registry

| ID | Date | Evidence source | Conditions | Artifact or location | Notes |
|---|---|---|---|---|---|
| E-001 | 2026-08-20 | Manuscript historical measurements | Pre-rebuild calibration | `Manuscript/Thesis Paper.pdf` | Context only; requires remeasurement |
| E-002 | 2026-08-21 | Raw gaze source audit | Static code inspection | `MediaPipeRawGazeSource.kt` | Both eyes averaged; callback-time timestamp |
| E-003 | 2026-08-21 | Live provider audit | Static code inspection | `LocalCalibratedGazeProvider.kt` | Median + One Euro before mapping; optional correction after mapping |
| E-004 | 2026-08-21 | Mapper audit | Static code inspection | `GazeMapper.kt` | Standardized ridge-regularized quadratic mapping with bounded extension |
| E-005 | 2026-08-21 | Calibration persistence audit | Static code inspection | `CalibrationSessionLog.kt` | Aggregates persist; individual raw samples do not |
| E-006 | 2026-08-21 | Calibration validation audit | Static code inspection | `GazeCalibrationActivity.kt` | Validates aggregated raw feature through base mapper |
| E-007 | 2026-08-21 | Accuracy-test audit | Static code inspection | `GazeTestActivity.kt` | Live path measured; active correction may be loaded; results not persisted |
| E-008 | 2026-08-21 | Article coordinate/AOI audit | Static code inspection | `ArticleFragment.kt`, `LineAoiMapper.kt`, `GazeOverlayView.kt` | Full-screen contract is explicit; on-device verification remains |
| E-009 | 2026-08-21 | Existing unit-test execution attempt | JDK 21 then JDK 17 | Gradle output | Blocked by KAPT/JDK mismatch, then inaccessible Android SDK metadata/build tools |
| E-010 | 2026-08-21 | Raw-source diagnostics callback | Local source edit, not yet device-verified | `LocalRawGazeSource.kt`, `MediaPipeRawGazeSource.kt` | Numeric per-result eye/source/timing telemetry |
| E-011 | 2026-08-21 | Pipeline diagnostics callback | Local provider edit, not yet device-verified | `LocalCalibratedGazeProvider.kt` | Observes raw, median, filtered, mapped, and output values |
| E-012 | 2026-08-21 | SAMPLE-window boundary callback | Local collector edit, not yet device-verified | `CalibrationPointCollector.kt` | Restricts recorded samples to the existing measurement window |
| E-013 | 2026-08-21 | Persistent accuracy-session writer | Local new file, not yet device-verified | `GazeAccuracySessionLog.kt` | Numeric-only JSON; no camera images retained |
| E-014 | 2026-08-21 | Nine-point diagnostics integration | Local activity edit, not yet device-verified | `GazeTestActivity.kt` | Labelled runs, per-attempt evidence, persisted summary |
| E-015 | 2026-08-21 | Full calibration source diagnostics | Local activity edit, not yet device-verified | `GazeCalibrationActivity.kt` | Covers practice, 16 fit targets, repeated drift target, retries, and 5 validation targets |
| E-016 | 2026-08-21 | Extended calibration-session artifact | Local logger edit, not yet device-verified | `CalibrationSessionLog.kt` | Per-attempt source events plus validation mapped coordinates and residuals; no images |
| E-017 | 2026-08-23 | On-device calibration diagnostics | Galaxy A56; normal-light handheld run with glasses | `calibration_session_20260823_181644.json`, `calibration_session_20260823_181801.json` | First session redone; second accepted with high drift flag |
| E-018 | 2026-08-23 | Immediate uncorrected live-path run | Galaxy A56; run label omitted | `gaze_accuracy_session_20260823_181952_096.json` | 201 px / 1.70-line median; no correction active |
| E-019 | 2026-08-23 | Three labelled uncorrected baseline repetitions | Galaxy A56; normal light, glasses, handheld | `gaze_accuracy_session_20260823_182120_179.json`, `...182148_794.json`, `...182216_339.json` | 3.41-4.15-line medians; persistent leftward compression |
| E-020 | 2026-08-23 | Source reliability and timing telemetry | Same calibration and accuracy SAMPLE windows | Per-frame `source_events` in E-017-E-019 | No face/blink loss in-window; ~29-31 ms median inference result time; actual 320x240 input |
| E-021 | 2026-08-23 | Fixed-phone calibration repetitions | Galaxy A56; normal light, glasses, supported at ~50 cm | Five `calibration_session_20260823_184*.json` artifacts | Large between-calibration variability despite fixed phone |
| E-022 | 2026-08-23 | Fixed-phone immediate/delayed live-path runs | Same final accepted calibration; no correction | Three `gaze_accuracy_session_20260823_184*.json` artifacts | 2.58-3.37-line medians; 8.28-9.33-line P95 |
| E-023 | 2026-08-23 | Fixed-phone feature-drift comparison | Immediate, +1 minute, +2 minutes | Per-frame raw/pipeline samples in E-022 | Raw horizontal range shifts left while phone remains fixed |
| E-024 | 2026-08-23 | Final calibration regional/feature geometry | Final accepted fixed-phone calibration | `calibration_session_20260823_184452.json` | 500 px drift, overlapping lower-row vertical features, one 4.80-line validation miss |
| E-025 | 2026-08-23 | Researcher historical observation | Prior hands-on NewsMead/prototype use | Conversation record | Earlier behavior perceived near the ~1.2-line useful range; current behavior clearly worse |
| E-026 | 2026-08-23 | Pre-redesign accuracy record | Galaxy A56 and sister prototype before calibration rebuild | `docs/progress-notes.md` | A56 vertical 0.67-1.01 cm; prototype approximately 0.7 cm consistently |
| E-027 | 2026-08-23 | Regression-window audit | Git history around July 24 rebuild | Commits `4b5198f`, `3dca061`, `4322154`, `8dddc02` | Sampling, filtering, extrapolation, and accuracy-test behavior changed after earlier measurements |
| E-028 | 2026-08-23 | Offline mapper/aggregation replay | Final fixed-phone calibration and three live runs | `diagnostics-local/analyze_mapper_replay.py` | Exact current replay; clamp reduces tails but does not restore line accuracy; plain median/affine results mixed |

## Confirmed Findings

1. No definite gaze-coordinate defect was established by static inspection alone.
2. The current saved artifacts are insufficient for a reproducible end-to-end baseline because nine-point observations are not persisted or condition-labelled.
3. Current data cannot isolate individual-eye behavior or camera-to-output latency.
4. Calibration validation and live accuracy testing measure different processing paths and must not be treated as interchangeable.
5. A saved drift correction can make a nine-point run a corrected measurement rather than a base-pipeline baseline.
6. The initial A56 baseline shows a repeatable leftward live-output compression that worsens toward the right side of the screen.
7. The current median-only green calibration advisory can pass a session despite a high drift flag and very large leave-one-out tail error.
8. The diagnostics build receives 320x240 CameraX frames on the A56 despite the intended 480x360 analysis profile.
9. Physically supporting the phone improves typical error but does not eliminate rapid raw-feature drift or severe regional failures.
10. The current two-value averaged iris-in-eye feature has weak and overlapping vertical separation in the lower screen region under the observed participant geometry.
11. Median accuracy alone materially understates current live-path risk: fixed-phone P95 error exceeded eight line-heights in all three repetitions.
12. Historical records and researcher observation establish a credible regression relative to the earlier NewsMead/prototype behavior.
13. Current soft extrapolation amplifies some catastrophic regional errors, but it is not the sole cause of the regression.
14. Accuracy and correction decisions must include individual target and regional errors; session median alone is insufficient.

## Open Questions

- What is the complete current path from camera frame to `GazeProvider` output?
- Which transformations or processing stages can be observed independently?
- Are the historical errors still reproducible with the rebuilt calibration flow?
- Is any observed failure caused by gaze estimation itself or by a downstream coordinate or AOI interpretation?
- Which uncertainties cannot be resolved without additional instrumentation?
- Does the upright/mirror transformation align with physical A56 screen direction in practice?
- What are the per-eye feature ranges and dispersions at each target?
- How much error is introduced or removed by mapping, temporal filtering, and drift correction individually?
- What is the actual frame-capture-to-gaze-output latency and its variance?

## Decisions Resulting From Diagnostics

No improvement decisions have been made. Any future decision must cite the relevant journal entry and evidence ID from this document.
