# Mahalanobis replacement implementation report

Status: exploratory software implementation on `measurement/mahalanobis`, 2026-10-17. This report describes code and host checks; no phone or participant validation was performed. See the [detector contract](mahalanobis-detector.md) for operating details.

## A. Branch audit

The [pre-change audit](mahalanobis-replacement-audit.md) found the live path selected by `StudyConfig.SCANPATH_READING_AIDS`: gaze provider, `ArticleScanpathSession`, `ScanpathEngine`, pattern-driven aid runtime and policy, then renderers. The Scanpath pilot and replay screens, pattern rules, trace writers, tests and offline pattern analyzer formed separate paths. The branch had an older R/D/F estimator, but no standardized unassisted participant baseline; its validation activity was a guided accuracy task. Reusable camera/calibration, capture-time coordinate mapping, text geometry, reading event extraction, gaze quality, scaffolding renderer, session identity and independent displaced-return evidence were preserved. The baseline stimulus remains provisional.

## B. Scanpath cleanup

The [removal inventory](mahalanobis-removed-files.json) lists **every removed file**, including detector/policy classes, pilot and replay activities and layouts, logs, tests, menu resources, the old weighted estimator, and its Scanpath-only test fixture. Shared geometry, motion and consultation behavior was moved into the neutral `gaze/reading` package; `ArticleFragment`, `HomeFragment`, manifest, config and offline measurement analyzer were rewired. No dependency was exclusively Scanpath-owned, so none was removed. Historical research documents and prior output remain as provenance, not runtime code. The generic `ReadingAidControlGate` remains to exclude reader-control interactions from measurement.

## C. Existing feature pipeline

`ReadingStateInferencer` emits cumulative regression, completed line dwell, and sustained line-stay counts. A regression is a confirmed return at least one line behind the reached high-water line; dwell counts completed residence of at least 120 ms; a sustained stay counts after 300 ms. `RollingReadingFeatures` differences those counters over one approximately 10-second window: R = regression events per minute, D = completed dwell milliseconds divided by window span and clipped to [0,1], F = sustained line-stay events per minute. These are existing line-based proxies, not physiological fixation estimates. Baseline and article use the same `ReadingFeaturePipeline`. Dwell is credited on closure, so a visit can cross the window boundary.

## D. Final data flow

Camera/MGazeNet → calibrated screen coordinates → neutral article text/line mapping → existing reading events → common 10-second R/D/F vector → frozen participant baseline → squared Mahalanobis distance → configured baseline percentile or custom threshold → candidate deviation → persistence/recovery controller → confirmed instability → existing scaffold renderer. Independent displaced-return evidence can choose re-entry support after confirmation. No Scanpath classifier or Scanpath severity decision is in this path.

## E. Baseline

After spatial calibration, Home exposes an explicit unassisted reading activity. It records the same valid full vectors as live scoring. At least 30 retained valid windows and 60,000 ms of union interval coverage are required. Invalid or nonfinite windows cannot fit the model. An explicit Finish freezes the sample mean and full 3×3 sample covariance (denominator n−1), regularized covariance `Σr = Σ + λI`, baseline MD² distribution, percentiles, sampling/count metadata, and the Cholesky factor. Default λ is `1e-6` in raw feature units; Cholesky diagonal ratio is logged as a stability diagnostic, not a condition number. Insufficient, degenerate, or numerically unusable baselines return explicit invalid statuses and cannot score. The profile is bound to the participant session and spatial calibration fingerprint in process memory; a new session, recalibration or app restart requires a new baseline. Live reading never updates it.

## F. Mahalanobis calculation

For `δ = x − μ`, the cached Cholesky factor `L` of `Σr` solves `Ly = δ`; the implementation computes `MD² = yᵀy`, algebraically `δᵀΣr⁻¹δ`, then `MD = sqrt(MD²)`. It does not form an explicit inverse. Signed deltas and above/below/near labels are logged; both positive and negative deviations can raise the two-sided distance. Missing/invalid input yields a null distance and unavailable status, never a manufactured zero.

## G. Thresholds

Baseline vectors are scored against their own frozen model, sorted, and P90/P95/P99 are calculated by type-7 linear interpolation at `(n−1)p`. The default configured threshold is exploratory **P95**; P90, P99, and a numeric custom MD² threshold are supported. Actual participant percentile values only exist after that participant's valid baseline is collected. Overlapping default windows are autocorrelated and percentiles are in-sample. No threshold is claimed to be scientifically validated. The replay exports predictions for all three percentile settings without inventing labels.

## H. Temporal controller

Default window: 10,000 ms. Recalculation interval: 500 ms. A high candidate must persist for 1,500 ms to confirm. A valid low score must persist for 3,500 ms to recover (default recovery threshold ratio 1.0). Invalid, missing, out-of-order or overly gapped updates break continuity. Consecutive high updates form a single candidate episode with start/end, duration, peak/mean MD², count and threshold. These timings are configuration, not validated human thresholds.

## I. Logging and debug view

Private JSONL records include session identity/time base; baseline config, stimulus provenance, participant code and calibration fingerprint; each baseline vector's timestamp, actual window bounds, R/D/F, quality, valid fraction and sample counts; a successful profile's μ, original/regularized Σ, λ, factor diagnostic, baseline MD² distribution, P90/P95/P99, selected threshold, baseline counts/coverage and sampling mode; each scoring update's vector fields, μ, signed deltas, direction labels, MD²/MD, active threshold/mode, candidate/confirmed status, baseline/quality validity, persistence status and scaffold state; and grouped candidate episodes. Article options explicitly export all private detector logs; the baseline screen can export its log. The article's developer diagnostics expose the scoring JSON; ordinary reading does not display these values.

## J. Offline validation support

`tools/gaze/run_mahalanobis_replay.ps1` invokes the shared Kotlin scorer/controller through `:app:mahalanobisReplay`. Baseline JSONL and recorded feature JSONL produce P90/P95/P99 update and episode CSVs with timestamp, MD², DETECTED/NOT_DETECTED/UNAVAILABLE, candidate/confirmed and quality fields. Later, separately annotated episodes and a predeclared matching tolerance can be joined to predictions for TP/FP/TN/FN, precision, recall, specificity, F1, false alerts per minute, missed events and latency. The current tool supplies no ground truth or validation statistics.

## K. Checks

`:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed with the offline Gradle cache and host-test init script: 307 JVM tests, zero failures/errors. The Mahalanobis core tests cover zero and increasing distance, feature scale/covariance, singular regularization, insufficient and frozen baselines, percentiles, threshold boundary, two-sided deltas, persistence/recovery, invalid/gapped updates, episode grouping, replay and interval-union coverage. Five replay CLI tests cover exports, strict config/input parsing and unavailable output; rolling-feature tests cover window timing and invalid quality. `tools/gaze/test_analyze_reading_measurement.py` passed 36 tests. Android tests were compiled into an APK, not run on a device. `git diff --check` found no whitespace errors.

## L. Scanpath verification

No file path or content matching `scanpath` remains under active `app` and `tools` source after removing the orphan test fixture. The active detector, controller, UI, configuration and tests therefore have no Scanpath path. The debug app and Android-test APK assemble successfully. Historical `docs/` notes and older `output/` artifacts still contain the term and are explicitly retained as historical evidence; the removal inventory itself names deleted files. No Scanpath-only external dependency was present.

## M. Open research issues

The provisional passage may be too short for natural 60-second reading. Thirty overlapping windows represent much less independent information than thirty independent observations. In-sample thresholds can be optimistic, and high MD² can reflect below-baseline behavior. Correlated or low-variance features can leave a poor matrix despite successful regularization; inspect the diagnostic. Valid/on-text gaze is an eligibility check, not proof of coordinate accuracy. Dwell closure and line mapping can distort R/D/F; hardware, readers and passages need independent measurement. Tune on separately annotated formative sessions, then evaluate on held-out readers/sessions before interpreting alerts as instability or assigning benefit to scaffolds.
