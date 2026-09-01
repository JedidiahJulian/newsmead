# Current Gaze System Diagnostics Log

## Purpose

This document is the evidence log for the initial diagnostic investigation of NewsMead's current gaze system. The investigation must determine what is working, what is failing, and what remains uncertain before any improvement is proposed or implemented.

The diagnostics are exploratory. Checks will follow the evidence found in the current implementation and test results rather than a predetermined improvement roadmap.

## Current Status

- **Started:** 2026-08-20
- **Branch:** `gaze-pipeline-improvements`
- **Status:** Fresh-session check complete; typical error remains improved, but calibration/live tails are not repeatable enough for exact-line use
- **Current backend:** MediaPipe Face Landmarker with iris-relative features, participant calibration, and screen-coordinate output
- **Repository state:** Code/document checkpoints are kept separate from numeric participant artifacts; those artifacts remain local and must stay unstaged

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

### 2026-08-23 — Reading-oriented spatial measurement revision implemented

**Question:** How should the existing 16-point calibration and nine-point live-pipeline check report quality without allowing a favorable session median to hide a severe target or regional failure?

**Action:** Revised measurement and presentation only; the raw gaze estimator, temporal pipeline, quadratic mapper, and affine correction fit were not changed. Both checks now retain signed per-target horizontal and vertical residuals, express vertical residuals in rendered article line-heights, report median/P95/maximum and counts within 0.5, 1.0, and a provisional 1.2-line reference, identify the worst target, and show regional summaries. The 16-point fit is evaluated with leave-one-out predictions and separately reports all five held-out checks and x/y drift. The nine-point check reports all nine targets and row/column medians. Result panels are scrollable so none of this evidence is hidden.

**Evidence:** E-029. The shared metrics and activity changes compile. `CalibrationQualityTest` and `ReadingSpatialMetricsTest` pass. The complete repository unit suite still contains an unrelated failure in `FirebaseTest.createAccount`.

**Interpretation:** Green now means only that every recorded target meets the provisional 1.2-line vertical spatial reference (with complete/no-exclusion and drift conditions also applied to the 16-point gate). It no longer means “good for reading” or “no correction needed.” Both screens explicitly state that direct reading validation is still required. Redo-worst selection in the 16-point flow now prioritizes the largest vertical leave-one-out residuals, consistent with line assignment as the primary use case.

**Constraint:** A correction estimated from a nine-point run remains a candidate, not proof of improvement. It must be verified on separate data before it can support a reading-performance claim, particularly when errors are regional rather than global.

### 2026-08-24 — Revised 16-point and nine-point measurements verified on device

**Question:** Do the reading-oriented result screens and JSON records correctly expose individual, tail, regional, drift, and coverage failures in a newly paired calibration and immediate live-pipeline run?

**Action:** Completed and saved one fixed-phone, normal-light, no-glasses 16-point calibration, then immediately completed an uncorrected nine-point run using that newly saved 16-point map. The first same-day attempt was retained separately but excluded as a paired comparison because its calibration activity was exited without saving and its nine-point run therefore used the previous day's map.

**Evidence:** E-030. Paired artifacts are retained locally under `diagnostics-local/2026-08-24/metrics-v2/run-2/`. The calibration outcome is `accepted`; `calibration_16point.csv` was updated at 11:51; the nine-point artifact records 16 calibration points and `drift_correction_active: false`.

**Observations:**

- The 16-point leave-one-out vertical median was 3.61 line-heights, P95/maximum was 6.65, and only 3/16 targets were within the provisional 1.2-line reference. The largest vertical miss was point 16 (row 4, column 4) at 6.65 lines.
- The five held-out checks had a 3.00-line vertical median and 8.07-line P95/maximum; only 1/5 was within 1.2 lines. The bottom-left held-out check was worst at 8.07 lines, while bottom-right was 0.86 lines, demonstrating a strongly localized rather than uniformly lower-screen error.
- Start-to-end mapped drift was approximately `dx = -59 px`, `dy = -382 px` (about -3.23 line-heights vertically), with a 387 px total, and was correctly flagged.
- The immediate nine-point live-path vertical median was 2.69 lines, P95/maximum was 8.17, and only 1/9 targets was within 1.2 lines. The worst target was top-right at 8.17 vertical lines; row medians were approximately 4.02 (top), 2.07 (middle), and 2.69 (bottom) lines, while column medians were 2.30 (left), 2.07 (center), and 4.38 (right).
- Nine-point total 2-D error was 433 px median and 985 px P95/maximum. The global affine candidate's leave-one-out 2-D median estimate was 223 px, but this is not an independent post-correction result and does not establish that the regional failures would be repaired.
- All expected target-level metric collections were present: 16 leave-one-out fit targets, five held-out targets, and nine live-pipeline targets. The provisional assessment correctly remained false and did not allow the median to hide the worst targets.

**Interpretation:** The revised validation measurement is functioning as designed. It confirms that the current problem is not a single uniform offset: large errors change location and direction across fit, held-out, and immediate live checks, alongside substantial within-session vertical drift. Applying the nine-point affine candidate is not yet justified as an improvement because it was fit and estimated on the same nine observations and has not been independently verified.

**Next inquiry:** Introduce the proposed fixed center crosshair plus contracting ring as one controlled capture-stimulus change, then repeat the same saved-calibration/immediate-nine-point pair and compare capture dispersion, drift, target-wise vertical error, and regional tails against E-030.

### 2026-08-24 — Older-adult fixation target and predictable order implemented

**Question:** Can the shared target provide a clearer exact fixation location and easier attentional guidance for older adults, while avoiding repeated animation during measurement?

**Action:** Replaced the continuously pulsing inner cue with a code-drawn hit-marker: a fixed outer ring, four crosshair arms with deliberately short inward segments, a small permanent center `+`, and a saturated bright-red disc with its own black outline. The red disc begins at the outer-arm tips and contracts to the overall size of the center `+` over 1800 ms: the 1100 ms APPEAR/HOLD/SETTLE period plus the expected 700 ms base SAMPLE window. The black reticle is drawn over the opaque red disc so it remains visible throughout. Restored the original 16-point fixed row-major traversal and made redo-worst ordering deterministic. The already-fixed nine-point row-major traversal was retained. Calibration JSON now records `order_mode: fixed_row_major`.

**Evidence:** E-031. Local implementation in `CalibrationView.kt`, `GazeCalibrationActivity.kt`, and `CalibrationSessionLog.kt`. The app compiles and the focused gaze-metrics tests pass; on-device usability and outcome comparison remain pending.

**Interpretation:** The intervention is accessibility-oriented and changes only target presentation/order, not raw gaze estimation, filtering, mapping, or validation calculations. Fixed order reduces transition uncertainty for participants but couples screen region with elapsed sequence time. The center remains stationary, but radial size change now continues through the expected base sample window; its effect on sample dispersion must be checked rather than assumed beneficial.

**Next inquiry:** Visually verify target size, contrast, contraction timing, and fixed traversal on the A56, then collect the same saved-calibration/immediate-nine-point pair used for E-030.

### 2026-08-24 — Conversation-era change and instrumentation audit

**Question:** Did gaze-estimation behavior change during the diagnostic conversation, and could the added telemetry itself be affecting accuracy or FPS?

**Action:** Compared the branch baseline `f77ce6e`, diagnostics commit `79a5ceb`, and validation/target commit `e214eb6` file-by-file. Separated numerical estimator/filter logic from runtime instrumentation and later participant-facing capture changes.

**Evidence:** E-032. Git confirms that `GazeMapper`, `FixationWindowFilter`, `DriftCorrection`, `MedianFilter`, and `OneEuroFilter` are byte-for-byte unchanged from `f77ce6e`. The MediaPipe emitted feature remains the same two-eye arithmetic average, and the live order remains median → One Euro → mapper → optional correction.

**Observations:**

- Commit `79a5ceb` did not intentionally change gaze mathematics, sampling durations, or filter thresholds, but it added per-frame source/pipeline callbacks, UI-thread queue work, in-memory detailed event buffers, and repeated synchronous pretty-JSON rewrites. Calibration artifacts grew from roughly 11 KB historically to roughly 400–580 KB in the instrumented runs.
- This overhead is a plausible runtime confound even though recorded MediaPipe submit-to-result latency remained about 30–31 ms median, all diagnostic targets retained enough samples, and no face/blink loss dominated the SAMPLE windows. The current evidence neither proves nor safely excludes a callback/queue/persistence effect on effective sample arrival or participant pacing.
- Commit `e214eb6` changed validation reporting and, after E-030 had already been recorded, changed target presentation and order at the researcher's request. It therefore cannot explain E-030's earlier severe errors, although it must remain fixed during subsequent comparisons.
- The branch baseline `f77ce6e` already included the July 24 calibration redesign. Historically better behavior may therefore predate the conversation without contradicting the unchanged estimator files observed since `f77ce6e`.

**Interpretation:** Open-ended diagnosis is complete enough to stop collecting arbitrary conditions. Before attributing the regression to the July redesign or modifying the estimator, run one bounded A/B with identical target/order/model behavior and detailed telemetry selectively ON versus OFF. Retain lightweight per-point metrics and FPS in both modes.

**Next inquiry:** Implement a selectable detailed-telemetry mode, run matched saved-calibration/immediate-nine-point pairs, and use the result as the entry gate to accuracy improvement or legacy-path comparison.

### 2026-08-24 — Detailed-telemetry control implemented

**Question:** Can detailed per-frame instrumentation be selected independently while retaining enough lightweight evidence for a valid ON/OFF comparison?

**Action:** Added an explicit run-label and detailed-telemetry checkbox to both full calibration and the nine-point check. OFF clears the MediaPipe diagnostic sink and omits `source_events`/`pipeline_samples`; ON preserves the existing callbacks and arrays. Both modes retain per-point target/estimate, counts, dispersion, spatial metrics, drift/validation summaries, and constant-memory point/run FPS summaries.

**Evidence:** E-033. The implementation compiles, all `com.newsmead.gaze.*` JVM tests pass, `assembleDebug` passes, and the debug build is installed on the connected Galaxy A56. Git diff inspection confirms no changes to mapper, fixation filter, drift correction, smoothing filters, target drawing, collector timing, or point ordering.

**Interpretation:** The software control is ready, but it is not yet empirically evaluated. No estimator or general gaze improvement may begin until the matched fixed-phone ON/OFF pairs are collected and compared.

**Next inquiry:** Run one full procedurally valid 16-point calibration plus immediate uncorrected nine-point check in each telemetry mode under unchanged fixed-phone, normal-light, no-glasses conditions.

### 2026-08-24 — Detailed-telemetry handheld control evaluated

**Question:** Does disabling detailed per-frame instrumentation materially restore throughput, sample capture, or spatial accuracy under natural handheld use?

**Action:** Evaluated two valid calibration-plus-immediate-nine-point pairs per mode on the Galaxy A56, normal light, no glasses, natural handheld posture. Classification used the embedded `telemetry_mode`, not the typed label. One attempted OFF redo (`handheld-tell-off_cal_redo-1`) actually recorded `detailed_on` and was excluded with its following OFF test as a mismatched pair; the second OFF redo was valid. No affine correction was active in any included nine-point run.

**Evidence:** E-034. Calibration FPS averaged 26.54 ON versus 26.25 OFF; nine-point FPS averaged 27.08 ON versus 26.60 OFF. Median retained samples were 12/12 for ON calibrations versus 13/13 OFF, and 12/13 for ON tests versus 12/11 OFF. Calibration feature dispersion was comparable and did not improve consistently OFF. Nine-point vertical medians were 1.09 and 0.59 lines ON versus 1.38 and 1.06 OFF; P95/max were 3.43 and 3.04 ON versus 3.08 and 2.54 OFF. ON had better typical error and threshold coverage, while OFF had somewhat smaller tails; calibration LOO, held-out validation, drift, output dispersion, and failure regions varied by run rather than mode. OFF artifacts were roughly 9–25 KB versus 251–440 KB ON, confirming that the control removed the intended detailed history.

**Interpretation:** Telemetry OFF did not restore FPS, sample arrival, or accuracy. Mixed tail and regional differences are consistent with the tracker’s existing run-to-run/handheld variation, not a mode-specific recovery. The control rules out a material detailed-instrumentation regression under these runs, though it cannot exclude a subtle effect. Close this diagnostic gate and do not spend the next iteration optimizing telemetry as an accuracy intervention.

**Next inquiry:** Compare the current July 24 calibration path against the pre-redesign behavior, one behavior change at a time, while keeping detailed telemetry OFF for lower runtime/storage overhead.

### 2026-08-24 — Pre-run countdown added before fresh-session confirmation

**Question:** Can the abrupt transition from setup confirmation to the first target be removed without changing capture behavior?

**Action:** Added a centered 3–2–1 countdown before initial full calibration, redo-worst, redo-all, and every nine-point measurement. Each numeral displays for one second; the countdown hides before the existing first target begins. No estimator, mapper, filter, target, order, SAMPLE boundary, or per-target duration changed.

**Evidence:** E-035. `compileDebugKotlin`, all `com.newsmead.gaze.*` JVM tests, and `assembleDebug` pass. The updated APK is installed on the connected A56 with existing app data preserved.

**Interpretation:** The four valid telemetry-control accuracy runs (0.59–1.38-line medians and 2.54–3.43-line tails) were substantially better than E-030 and the earlier fixed-phone set. A stable regression is no longer established; those earlier sessions may have been poor runs or may reflect the pre-E-031 target/order behavior. Before changing calibration math, obtain one independent natural-handheld OFF pair after a genuine session reset.

**Next inquiry:** After several hours or on another day, collect one telemetry-OFF full calibration plus immediate uncorrected nine-point run with the countdown build. Accept the first procedurally valid calibration as-is and do not repeat merely for a poor score.

### 2026-08-28 — Fresh-session OFF repeatability check completed

**Question:** Does the substantially improved August 24 handheld accuracy repeat after a genuine multi-day reset without selecting a favorable calibration?

**Action:** Collected one natural-handheld full calibration (`handheld_fresh_off_cal_20260828_1`) and immediate nine-point check (`handheld_fresh_off_test_20260828_1`) with detailed telemetry OFF. The first procedurally valid calibration was saved as-is, and no affine correction was active.

**Evidence:** E-036. Calibration averaged 25.81 FPS with median raw/retained counts 20/14 and low median feature dispersion (0.00102 x, 0.00184 y). Its LOO vertical median/P95/max was 1.29/6.24/6.24 lines, drift was 286 px, and held-out validation median/P95/max was 4.49/7.44/7.44 lines. The immediate live test averaged 25.61 FPS with 19/12 raw/retained samples, a 1.55-line vertical median, 4.87-line P95/max, 277 px 2-D median, and 4/9 targets within 1.2 lines. Top-left was worst at 4.87 lines; bottom-right, bottom-center, and middle-left were 2.35–2.67 lines. All nine estimates were horizontally left of target.

**Interpretation:** The independent session remains better in typical vertical error than E-030 (1.55 versus 2.69 lines), so the recent improvement was not purely a single-session accident. However, severe regional tails returned, and the calibration’s held-out validation was markedly poor despite healthy FPS, sample counts, and low within-point dispersion. The system is therefore improved but not repeatable enough for exact-line attribution. Across the five recent valid live runs, top-left is the worst vertical target in four, identifying a recurring regional weakness rather than purely random bad runs.

**Next inquiry:** Without collecting another run or changing code, compare the five recent calibration/test pairs for predictors of tail failure—feature-range geometry, target-wise calibration residuals, held-out regions, drift, and mapper conditioning—with special attention to the recurring top-left failure. Determine whether bad sessions can be detected reliably at the quality gate before choosing a single calibration-path intervention.

### 2026-08-28 — Five-pair offline tail-predictor analysis

**Question:** Can any metric available before Save reliably distinguish a calibration that will produce a severe immediate live tail?

**Action:** Read the five valid calibration/immediate-nine-point pairs directly from app-private storage and compared only saved aggregate evidence. For each calibration, reconstructed the standardized six-term quadratic design and ridge normal matrix; measured feature spread, row/column ordering and separation, ridge leverage, fitted top-left vertical gain, pointwise LOO error, held-out validation, and drift. Compared these with live median, maximum, top-left, 2-D, and signed-horizontal errors. The mismatched ON-calibration/OFF-test pair remained excluded. No raw participant artifact was copied out of app-private storage; the reusable script prints derived aggregates only.

**Evidence:** E-037. All five calibrations had perfect horizontal target ordering. Vertical feature ordering was reversed at 0–2 of 12 adjacent row comparisons, with especially weak top-row/second-row separation in OFF1 and the fresh OFF run. Unregularized design condition numbers were modest (4.23–7.91), and the ridge-normal condition numbers were 16.35–39.30; the worst fresh live run was not the most ill-conditioned and had the lowest fitted top-left vertical gain. The top-left calibration target was the maximum vertical LOO error in every pair (5.23–6.59 lines), while it was the worst live target in four of five pairs. Held-out maximum did not rank live tails: OFF2 had the largest held-out maximum (7.82 lines) but the smallest live maximum (2.54), whereas fresh OFF had 7.44 held-out and 4.87 live. High-drift calibrations (215, 241, and 286 px) had live maxima of 3.43, 3.08, and 4.87 lines; low-drift calibrations (71 and 36 px) had 3.04 and 2.54. With only five pairs, rank correlations are descriptive and not inferential.

**Interpretation:** Polynomial conditioning, LOO aggregates, and held-out errors do not provide a reliable pre-save selector for the later live tail. High drift is the only practical directional warning, but it is not a guarantee: a low-drift pair still reached a 3.04-line live maximum. The repeated top-left failure is systematic across calibration and live measurement, not a marker unique to the fresh bad-tail session. Because fixed row-major traversal makes top-left both a corner and the first measured fit target, these data cannot separate regional feature geometry from a first-target/sequence effect.

**Next inquiry:** Adopt one narrow stopping rule before changing calibration math: when the existing gate flags high drift, do not Save that calibration; use Redo All once, and if the repeat is also high-drift, abort and retain the prior calibration. Treat this as risk control, not proof of line-level accuracy. After that rule is implemented, isolate the top-left region/first-target confound in a separate controlled intervention rather than combining changes.

### 2026-08-29 — Regularized per-eye linear mapper rejected prospectively

**Question:** Does retaining four per-eye iris features in a small five-coefficient ridge-linear mapper improve accuracy over the six-coefficient quadratic mapper of the two-eye average?

**Action:** Compared five small mapper families offline on nine compatible detailed calibration sessions, then prospectively deployed the best descriptive candidate (`per_eye_linear`, ridge 1). The implementation preserved the target, sequence, capture filter, smoothing parameters, and separate affine drift layer; it added per-eye aggregation and an eight-column calibration CSV with legacy four-column fallback. Two telemetry-OFF full-calibration/immediate-nine-point pairs were collected. The active mapper was then restored to `quadratic_average` after both live runs failed.

**Evidence:** E-038. The immediately preceding quadratic live run had a 0.76-line vertical median and 1.04-line maximum. Per-eye run 1 produced 5.74/8.83 lines and per-eye run 2 produced 2.63/4.19 lines. Run 1 had 1/9 targets within 1.2 lines; run 2 had 0/9. FPS remained comparable (18.14 and 20.12). Same-calibration offline counterfactuals were mixed: per-eye improved run-1 held-out median/max (3.40/5.74 lines versus quadratic 3.78/6.85), but worsened run 2 (2.51/4.87 versus quadratic 1.63/3.21). Run-1 drift was 774 px and run-2 drift was 71 px, so the live failure was not restricted to a high-drift calibration.

**Interpretation:** Selective per-eye retention is not a deployable accuracy improvement in this form. Earlier descriptive wins across repeated sessions did not generalize to the two prospective runs, and the same-calibration comparison did not consistently favor it. Keep the quadratic mapper active. Retain aggregate-only per-eye logging and the offline comparison tool so future alternatives can be evaluated without enabling costly per-frame telemetry.

**Next inquiry:** Do not deploy another mapper from retrospective rankings alone. Any posture compensation must first establish that its posture inputs vary independently of target position; the present fixed calibration sequence cannot identify that safely. Treat pose-departure detection as reliability protection rather than an accuracy claim.

### 2026-08-29 — Quadratic rollback sanity check completed

**Question:** After rejecting the per-eye intervention, does a fresh calibration and immediate test confirm that the installed app is again executing the quadratic-average pipeline?

**Action:** Installed the tested rollback build, then collected a fresh 16-point calibration (`quadratic_restored_tel-off_cal_1`) and immediate nine-point test (`quadratic_restored_tel-off_test_1`). The embedded mapper field, rather than the typed label, was used to verify the active path.

**Evidence:** E-039. Both artifacts recorded `active_mapping_mode: quadratic_average`, 16 calibration points, no affine correction, and approximately 20–21 FPS. The calibration was detailed-OFF and had 2.50/6.33-line LOO median/max, 1.53/3.73-line held-out median/max, and 259 px drift. The live test produced a 1.35-line vertical median, 2.58-line maximum, and 3/9 targets within 1.2 lines. Its embedded telemetry mode was `detailed_on` despite the `tel-off` typed label, so it is not an OFF/OFF telemetry pair. All three top-row targets were the largest vertical errors at 2.39–2.58 lines; top-center, not top-left, was worst.

**Interpretation:** The rollback is operational and materially better than both prospective per-eye runs, although this fresh quadratic session does not reproduce the unusually strong 0.76/1.04-line baseline. The remaining run-to-run spread belongs to the base tracker/calibration problem, not a failure to restore the mapper. The telemetry mismatch does not invalidate this mapper sanity check because the active mapper is explicit and the completed E-034 control found no material telemetry effect; do not require another participant run solely to correct the label/mode mismatch.

**Next inquiry:** Close the per-eye intervention. Keep the quadratic mapper active and choose the next intervention from a mechanism that is identifiable under the calibration procedure; do not fit posture terms that are confounded with fixed target order.

### 2026-08-29 — Upper-left acquisition practice intervention installed

**Question:** Is the recurring upper-left/early-top-row weakness partly caused by the first recorded point also requiring the largest initial center-to-corner acquisition movement?

**Action:** Removed the rejected per-eye mapper, mapper factory, live per-eye filters, and associated runtime tests. Kept aggregate-only per-eye calibration persistence and the offline comparison tool. Added one unrecorded upper-left practice fixation after the existing center practice and immediately before the unchanged 16-point row-major fit sequence. If Redo Worst includes point 0, the same practice fixation precedes its recapture. No mapper, filter, capture timing, recorded target coordinate/order, camera resolution, nine-point sequence, or affine behavior changed.

**Evidence:** E-040. The calibration log now records `practice_target_count: 2` and `upper_left_practice_before_first_fit: true`, while continuing to record `quadratic_average` as the requested and active mapper. `compileDebugKotlin`, the focused gaze JVM suite, and `assembleDebug` passed; the APK was installed over the existing app with data and prior logs preserved. Git staging and commit were not performed.

**Interpretation:** This is a narrow acquisition/sequence intervention, not a new estimator. It preserves the predictable older-adult row-major traversal while preventing recorded point 0 from being the participant's first upper-left fixation. One prospective calibration/immediate-test pair is sufficient for its initial keep/revert decision; do not repeat merely to select a favorable run.

**Next inquiry:** Collect one telemetry-OFF 16-point calibration and immediate telemetry-OFF nine-point test. Compare point-0/top-row LOO and live top-row errors with E-039, while also reporting whole-session median/max so a local improvement cannot hide a global regression.

### 2026-08-29 — Upper-left acquisition practice rejected prospectively

**Question:** Did the extra unrecorded upper-left fixation improve the recurring upper-left/early-top-row error without degrading whole-session behavior?

**Action:** Collected exactly one telemetry-OFF calibration (`upperleft-practice_tel-off_cal_1`) and immediate telemetry-OFF test (`upperleft-practice_tel-off_test_1`). Verified two accepted practice targets, `quadratic_average`, 16 fit points, and no affine correction from embedded fields. After evaluation, removed the extra practice from initial calibration and point-0 recapture, rebuilt, retested, and installed the original one-practice sequence.

**Evidence:** E-041. Against E-039, live vertical median improved from 1.35 to 0.96 lines and coverage improved from 3/9 to 5/9 within 1.2 lines. However, top-left worsened from 2.52 to 2.78 lines, maximum error worsened from 2.58 to 3.53 lines, 2-D median increased from 215 to 343 px, and estimates developed a large leftward bias. Calibration point-0 LOO improved from 6.33 to 4.30 lines, but the LOO maximum worsened to 7.44 lines, held-out median/max worsened from 1.53/3.73 to 5.72/10.12 lines, and drift increased from 259 to 850 px. Top-center/top-right live errors improved, but bottom-left became worst at 3.53 lines.

**Interpretation:** The intervention failed its primary target-specific hypothesis and traded a better median for worse regional/global tails. It is rejected rather than retained based on the favorable aggregate. The upper-left failure cannot be attributed primarily to the initial center-to-corner acquisition under this one-run controlled intervention. Do not add target-order complexity on this evidence.

**Next inquiry:** Keep the original predictable calibration sequence and quadratic mapper. Do not request another repeat of this intervention. The next change should not be another retrospective mapper or target-order variant; posture-related work must be framed as reliability protection unless independent posture information can support an accuracy model.

### 2026-08-29 — Calibration-pose departure monitoring added in shadow mode

**Question:** Can the system identify obvious departure from the pose under which the active calibration was collected, without feeding posture into the gaze estimate or reading logic?

**Action:** Added passive eye-midpoint face centre, aspect-correct eye separation/scale, and eye-line roll features to raw samples and calibration aggregates. Extended the calibration CSV additively to 12 columns while retaining 4/8-column read compatibility. A participant-independent rule builds a conservative envelope from at least eight valid calibration points (observed min/max plus the larger of three MADs or a fixed minimum margin). The live provider assesses each sample but does not alter or suppress it. Accuracy logs retain lightweight per-point and whole-run summaries even with detailed telemetry OFF; telemetry ON also records per-frame fields. Article use emits only throttled status in the existing diagnostic log.

**Evidence:** E-042. Static data-flow audit confirms posture assessment is side-channel only: the existing average-eye values still enter the same median-7, One Euro, quadratic mapping, and optional affine correction in the same order, and the mapped output is always emitted. All gaze-focused JVM tests pass, the APK assembles, and the build was installed over the A56 app with data preserved. The full local suite reaches 52 tests with only the unrelated existing `FirebaseTest.createAccount` failure. A posture-aware calibration/test remains pending.

**Interpretation:** This is reliability instrumentation, not an accuracy intervention. It uses the same algorithm and margins for every participant while referencing each participant's own calibration pose. Existing calibrations remain usable but report posture as unavailable until replaced through the normal calibration flow.

**Next inquiry:** Collect one telemetry-OFF ordinary calibration and immediate telemetry-OFF nine-point test. Evaluate flag availability and false-positive behavior before allowing posture status to influence any downstream event.

### 2026-08-29 — Posture-departure shadow control evaluated

**Question:** Is the calibration-pose shadow status available during a normal OFF run, and does it identify the targets with poor coordinates well enough to justify any downstream reliability action?

**Action:** Collected `posture-shadow_tel-off_cal_1` and the immediate `posture-shadow_tel-off_test_1` with embedded `detailed_off`, `quadratic_average`, no affine correction, and `posture_affects_gaze_output: false`. Compared posture summaries target by target with the unchanged spatial metrics and with adjacent-run throughput.

**Evidence:** E-043. The calibration produced a complete 16-point posture profile; all 145 live samples were assessed and none were unavailable. The monitor marked 31/145 (21.4%) out of range, exclusively for `face_center_x` during bottom-center (15/15) and bottom-right (16/16). Exceedance was modest (maximum 0.29 profile margins beyond the bound, approximately 0.0063 normalized frame width). Those flagged targets had 1.58- and 0.66-line vertical errors, while unflagged top-center was worst at 4.20 lines. Live median/max was 2.76/4.20 lines with 2/9 within 1.2; calibration LOO median/max was 2.43/6.24, held-out median/max 4.72/8.25, and drift 121 px. Calibration/test FPS was 20.65/20.40 versus 18.62/18.07 in the immediately preceding OFF pair, so the shadow calculation did not produce an observed throughput regression.

**Interpretation:** Availability and persistence are verified, but this run provides no evidence that the current binary posture departure identifies inaccurate gaze. The temporal block at the final two targets is plausible as a small pose drift, yet it coincided with better—not worse—coordinates. Keep it as research evidence only; do not use it to exclude events or drive the adaptive interface, and do not tune a favorable threshold from this single participant/run.

**Next inquiry:** Move to one reversible feature-geometry intervention that is participant-independent: a roll-aware eye-local iris displacement normalized by eye width, retaining two-eye averaging and every downstream mapper/filter/target/sequence component. Do not combine it with posture terms.

### 2026-08-30 — Roll-aware eye-local raw-feature candidate implemented

**Question:** Can a participant-independent eye-local coordinate improve the unstable vertical feature without adding calibration coefficients or fitting the participant's posture?

**Action:** Added `eye_local_width_average_v1` as the active reversible raw-feature mode. For each eye, iris displacement is projected onto the corner-to-corner eye axis and its downward perpendicular in aspect-correct pixel coordinates, then normalized by eye width. The two eyes are still averaged before the unchanged live filters and quadratic mapper. The original `eyelid_fraction_average` path remains available as the rollback enum value. Calibration and accuracy logs now persist `raw_feature_mode`, and `calibration_feature_mode.txt` prevents candidate/rollback builds from silently consuming an incompatible saved calibration.

**Evidence:** E-044. Pure JVM tests confirm that the candidate output is invariant to translation, uniform scale, roll, and MediaPipe eye-corner index order. All gaze-focused JVM tests and `assembleDebug` pass, and the APK was installed over the A56 app with data/logs preserved. Static diff confirms no changes to `GazeMapper`, `FixationWindowFilter`, `MedianFilter`, `OneEuroFilter`, `DriftCorrection`, or `CalibrationView`; calibration target order/timing is unchanged. Physical calibration/test evidence remains pending.

**Interpretation:** Unlike the failed per-eye mapper, this is a bounded raw-geometry intervention with the same two-dimensional averaged input and the same six-coefficient quadratic mapper. It is not personalized to the researcher: eye-width normalization and eye-local rotation use the same computation for every face. Improvement is not claimed until one prospective pair succeeds.

**Next inquiry:** Collect one telemetry-OFF calibration labelled `eye-local-v1_tel-off_cal_1` and immediate telemetry-OFF test labelled `eye-local-v1_tel-off_test_1`; do not apply affine correction. Compare live median/max and regional vertical errors with the restored quadratic and posture-shadow runs, then keep or roll back the feature mode.

### 2026-09-01 — Eye-local raw-feature candidate succeeds prospectively

**Question:** Does the roll-aware eye-local feature improve vertical accuracy in a prospective run, and does the result repeat rather than depend on one favorable calibration?

**Action:** Collected two complete telemetry-OFF calibration/immediate-test pairs. The first (`eye-local-v1_tel-off_cal_1` / `eye-local-v1_tel-off_test_1`) is the pre-specified primary result; the separately calibrated `_cal_2` / `_test_2` pair is treated only as a repeatability check. All four artifacts embed `raw_feature_mode: eye_local_width_average_v1`, `quadratic_average`, and no affine correction.

**Evidence:** E-045. The primary calibration produced 0.85-line LOO median, 1.84-line LOO max, 0.82-line held-out median, and 1.31-line held-out max. Its live test produced 0.62-line vertical median and 2.07-line max, with 6/9 targets within 1.2 lines and 209/334 px 2-D median/max. The repeat calibration produced 0.73/1.76-line LOO median/max and 1.03/1.90-line held-out median/max; its live test produced 0.96/1.51-line median/max, 7/9 within 1.2, and 126/216 px 2-D median/max. FPS remained comparable at 18.68/20.50 and 19.53/19.29 for the two calibration/test pairs. The first run retained a systematic 108–226 px left bias, while the repeat's horizontal signs were mixed and smaller overall. The second run's affine leave-one-out estimate was 117 px versus its measured 126 px median, so applying it is not justified by a meaningful expected gain.

**Interpretation:** The candidate passes the prospective keep decision. Both live medians and maxima improve over the preceding posture-shadow baseline (2.76/4.20 lines) and restored quadratic sanity run (1.35/2.58), and the improvement appears in calibration LOO, held-out, and live paths. This is strong within-device/participant evidence for retaining the geometry, not proof of cross-participant performance or direct reading compatibility. Remaining horizontal error may still limit word-level use. The posture shadow again failed as a quality proxy: 88.5% of samples were flagged in the accurate primary test and none in the similarly accurate repeat.

**Next inquiry:** Stop repeating dot-grid runs for score selection. Keep the eye-local feature, leave posture shadow non-operative, and validate actual reading-line assignment plus word/line scaffold behavior. Only pursue a separate horizontal intervention if that direct validation shows the remaining horizontal error materially harms the intended reading measures.

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
| E-029 | 2026-08-23 | Reading-oriented validation revision | Local implementation and JVM tests | `ReadingSpatialMetrics.kt`, calibration and accuracy activities/loggers | Per-target dx/dy, vertical line metrics, tails, regions, provisional all-target reference; estimator unchanged |
| E-030 | 2026-08-24 | On-device revised-metrics verification | Galaxy A56; fixed phone, normal light, no glasses; saved calibration plus immediate uncorrected run | `diagnostics-local/2026-08-24/metrics-v2/run-2/` | Complete 16 LOO + 5 held-out + 9 live target metrics; large regional errors and vertical drift correctly exposed |
| E-031 | 2026-08-24 | Older-adult target/order intervention | Visual behavior reviewed on Galaxy A56; accuracy comparison pending | `CalibrationView.kt`, `GazeCalibrationActivity.kt`, `CalibrationSessionLog.kt` | Fixed hit-marker, contraction through expected base sample, fixed row-major traversal; estimator unchanged |
| E-032 | 2026-08-24 | Conversation-era regression audit | Git comparison of `f77ce6e`, `79a5ceb`, and `e214eb6` | Repository history and relevant gaze source diffs | Estimator/filter math unchanged; detailed telemetry is a remaining runtime confound; July redesign predates conversation |
| E-033 | 2026-08-24 | Detailed-telemetry ON/OFF control | Compile, focused/all gaze JVM tests, APK assembly, A56 install, source diff audit | Telemetry mode UI, session loggers, diagnostic listener boundary, FPS accumulator/test | OFF removes detailed callbacks/arrays; lightweight comparison evidence remains in both modes; physical A/B pending |
| E-034 | 2026-08-24 | Replicated natural-handheld telemetry control | Galaxy A56; normal light, no glasses; two valid calibration/immediate-nine-point pairs per mode | Ten new app-private artifacts from 15:21–15:37; eight included, one mismatched pair excluded | OFF greatly reduced artifact size but did not restore FPS, samples, or accuracy; instrumentation confound closed |
| E-035 | 2026-08-24 | Centered pre-run countdown | Static entry-path audit, compile, all gaze JVM tests, APK assembly/install | Calibration/test activities and layouts | 3–2–1 occurs only before the first target; capture behavior unchanged; fresh OFF confirmation pending |
| E-036 | 2026-08-28 | Independent-session handheld repeatability | Galaxy A56; natural handheld, normal light, no glasses, telemetry OFF, no correction | `calibration_session_20260828_060527.json`, `gaze_accuracy_session_20260828_060732_402.json` in app-private storage | Median remained improved at 1.55 lines, but 4.87-line live and 7.44-line held-out tails confirm unresolved regional/repeatability risk |
| E-037 | 2026-08-28 | Five-pair offline tail-predictor analysis | Five valid recent natural-handheld calibration/immediate-test pairs; descriptive n=5 comparison | `diagnostics-local/analyze_recent_repeatability.py`; source artifacts streamed from app-private storage | No reliable gate predictor; drift is directional only; top-left is worst calibration LOO point in 5/5 and worst live point in 4/5 |
| E-038 | 2026-08-29 | Prospective per-eye mapper intervention | Galaxy A56; natural handheld, telemetry OFF, two full calibration/immediate-test pairs, no affine correction | Four app-private artifacts at 20:49–20:54; `tools/gaze/compare_mapper_candidates.py` | Per-eye live medians/tails 5.74/8.83 and 2.63/4.19 lines; candidate rejected and quadratic default restored |
| E-039 | 2026-08-29 | Quadratic rollback sanity check | Galaxy A56; fresh calibration OFF, immediate test inadvertently ON, no affine correction | `calibration_session_20260829_214116.json`, `gaze_accuracy_session_20260829_214301_000.json` in app-private storage | Mapper confirmed quadratic; live median/max 1.35/2.58 lines, materially better than both per-eye runs |
| E-040 | 2026-08-29 | Upper-left acquisition-practice intervention | Static sequence/diff audit, focused gaze tests, APK assembly/install | Calibration activity and lightweight session fields | Adds one unrecorded upper-left practice before fit point 0; rejected per-eye runtime removed; prospective pair pending |
| E-041 | 2026-08-29 | Prospective upper-left-practice evaluation and rollback | Galaxy A56; telemetry OFF calibration/test, quadratic mapper, no affine correction | `calibration_session_20260829_220839.json`, `gaze_accuracy_session_20260829_221046_730.json`; subsequent rollback build/install | Median improved but top-left and global tail did not; intervention rejected and original sequence restored |
| E-042 | 2026-08-29 | Posture-departure shadow monitor | Static path audit, compile, gaze-focused JVM tests, APK assembly/install | Posture features/profile, additive calibration CSV fields, live assessment and lightweight summaries | Evidence-only; no coordinate/filter/mapper/AOI/scaffold effect; physical run pending |
| E-043 | 2026-08-29 | Posture-departure shadow control | Galaxy A56; telemetry-OFF calibration/immediate test, quadratic mapper, no correction | `calibration_session_20260829_230755.json`, `gaze_accuracy_session_20260829_230917_726.json` | 145/145 assessed; flags confined to two relatively accurate final targets; shadow status not validated for downstream gating |
| E-044 | 2026-08-30 | Roll-aware eye-local raw-feature candidate | Static path audit, pure geometry/focused gaze JVM tests, APK assembly/install | `RawGazeFeatureMode.kt`, MediaPipe feature extraction, calibration-mode sidecar and explicit log field | Reversible raw geometry only; mapper/filter/target/sequence unchanged; evaluated in E-045 |
| E-045 | 2026-09-01 | Prospective eye-local candidate evaluation | Galaxy A56; two telemetry-OFF calibration/immediate-test pairs; no correction | `calibration_session_20260901_191457.json`, `gaze_accuracy_session_20260901_191648_408.json`, `calibration_session_20260901_191802.json`, `gaze_accuracy_session_20260901_191926_612.json` | Vertical improvement repeats; candidate retained; remaining horizontal/read-context limits documented |

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
15. The 16-point and nine-point result screens now implement target-wise, reading-oriented spatial reporting while keeping reading compatibility as an unestablished outcome.
16. The revised measurements are verified on device and correctly prevent favorable aggregate interpretation when individual targets, tails, or drift fail (E-030).
17. A five-coefficient per-eye ridge-linear mapper failed both prospective live runs and was restored to the averaged quadratic default; retrospective multi-session wins were not sufficient deployment evidence.
18. A fresh rollback sanity run explicitly recorded the quadratic mapper and recovered to a 1.35-line median/2.58-line maximum; it confirms restoration but also confirms unresolved base-pipeline session variability.
19. The next isolated intervention changes only acquisition context: point 0 is preceded by an unrecorded fixation at the same upper-left coordinate while all recorded points and gaze math remain unchanged.
20. Upper-left pre-acquisition did not improve live top-left error and worsened the live maximum, 2-D median, held-out tail, and drift despite improving the vertical median; the original sequence was restored.
21. Posture-departure monitoring is implemented only as a shadow side channel. It does not compensate coordinates or exclude samples, and older calibrations correctly produce `UNAVAILABLE` until a posture-aware calibration is accepted (E-042).
22. The first physical posture-shadow control had complete availability but its only flagged targets were relatively accurate; posture status must remain evidence-only rather than an AOI/scaffold reliability gate (E-043).
23. The eye-local candidate changes only the two raw averaged iris features and is explicitly bound to its calibration mode; it has passed local invariance/compile tests but has no accuracy evidence until its prospective pair is collected (E-044).
24. Two prospective eye-local pairs substantially improved vertical calibration and live accuracy, supporting retention on the A56; typical horizontal error remains too large to assume word-level compatibility without direct reading validation (E-045).
17. The next controlled intervention uses a predictable row-major order and a fixed-center contracting hit-marker, with temporal/spatial order coupling documented as a tradeoff (E-031).
18. Conversation-era commits did not change mapper/filter mathematics, and the replicated ON/OFF control found no material FPS, sample-count, or accuracy recovery with detailed telemetry disabled (E-032, E-034).
19. Detailed telemetry materially increases artifact size, but it is not supported as the cause of the observed gaze-accuracy regression; keep it OFF for leaner routine runs rather than as an accuracy fix (E-034).
20. The current handheld build can produce markedly better accuracy than the earlier diagnostic sessions, so repeatability across an independent session must be checked before treating the July path as regressed (E-034, E-035).
21. The independent-session run retained the typical-error improvement but reproduced severe regional tails; healthy FPS/samples and low calibration dispersion rule out simple throughput or noisy-fixation failure for that session (E-036).
22. Top-left was the worst vertical live target in four of the five recent valid runs, making it the clearest recurring regional failure for the next bounded analysis (E-034, E-036).
23. Across those five pairs, top-left was also the worst calibration LOO target in every run, while mapper conditioning and held-out validation did not rank the later live tails (E-037).
24. No saved calibration metric reliably selects a good immediate live run. High drift is the only directional warning observed, so it can support a conservative redo/abort rule but not an accuracy claim (E-037).

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

1. Revise validation measurement before modifying the gaze estimator (E-029).
2. Use vertical error in rendered article line-heights as the primary spatial reading metric while retaining signed horizontal error and total 2-D error (E-029).
3. Treat 1.2 line-heights as a provisional all-target spatial reference, not a validated reading pass threshold (E-025, E-026, E-029).
4. Do not allow session median alone to produce a favorable assessment; report and consider every target, tail error, region, drift, and coverage (E-022, E-024, E-028, E-029).
5. Prioritize older-adult target predictability for the next controlled comparison by restoring fixed row-major order and replacing continuous pulsing with a one-way pre-sample contraction around a fixed reticle (E-031).
6. Do not use calibration LOO, held-out error, or mapper conditioning to claim that a run will have a small live tail; the five-pair comparison does not support that inference (E-037).
7. Before changing calibration math, make the existing high-drift flag a stopping rule: Redo All once instead of saving; if the repeat also flags high drift, abort and retain the prior calibration. This is risk control, not a line-accuracy gate (E-037).
