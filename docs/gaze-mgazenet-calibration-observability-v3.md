# MGazeNet calibration observability v3 software checkpoint — 2026-09-09

Status: the isolated input-only calibration audit is implemented and
host-verified. This checkpoint used no phone, camera, participant calibration or
personal data. It does not modify the active NewsMead tracker or calibration
store, assign an accuracy gate, or authorize a device run.

Read after `gaze-mgazenet-post-pilot-review.md`. This is the current MGazeNet
continuation point.

## Why the 16-point NewsMead procedure is reused—but not its calibration

The v3 audit deliberately reuses the active calibration's spatial procedure:

- one practice target at viewport centre;
- sixteen fit targets in fixed row-major order on the 4×4 viewport fractions
  `.10`, `.3667`, `.6333`, `.90`;
- a post-fit repeat of the nearest-centre fit target (`fit_6`);
- five held-out checks at centre and the four quarter diagonals.

It cannot reuse the active calibration observations or mapper. The active
tracker reduces each target to one robustly aggregated two-value eye-local
observation and fits a six-term quadratic ridge mapper. MGazeNet emits 258-value
features, keeps 45 accepted rows at each target, and fits independent X/Y OpenCV
RBF EPS-SVRs. V3 therefore shares target geometry and validation roles while
keeping the model-specific input, fit and storage paths isolated. It never reads
`calibration_16point.csv` or any active tracker state.

## Frozen v3 collection and audit contract

`CalibrationAuditSession.VERSION` is
`mgazenet_calibration_observability_v3`. Opening its setup Activity leaves the
camera off and creates no record. A run label is required before the explicit
Start action; the 4×4 target order is fixed and has no order selector.

The unchanged MGazeNet eligibility rule accepts a row only when all 258 features
are finite and both eye polygon areas are strictly above 10 pixels. Each target
has a 1,500 ms settle, exactly 45 accepted samples, a 500 ms post-collection
wait, and a 30,000 ms timeout. The practice target never enters training. The
sixteen fit groups therefore contain exactly 720 rows.

Before the final calibration is used, `CalibrationAuditEngine` performs sixteen
leave-one-complete-target-group-out folds. Each fold fits the same fixed X/Y RBF
EPS-SVRs on 15 whole groups (675 rows), then predicts all 45 rows of the omitted
group. No frame-level random split is available. After the audit, a separate
final SVR is fit on all 720 rows.

Post-fit verification is fixed to six blocks in this order:

1. `drift_repeat_fit_6`, at the exact `fit_6` position;
2. `validation_1`, viewport centre;
3. `validation_2`, upper-left quarter;
4. `validation_3`, upper-right quarter;
5. `validation_4`, lower-left quarter;
6. `validation_5`, lower-right quarter.

Each block uses a 3,000 ms settle, 2,500 ms measurement window and 250 ms drain.
The repeat observes within-session drift; it does not refit or correct. The five
validation positions are disjoint from all fit positions. No prediction filters,
bias subtraction, line snapping, target selection or post-hoc correction are
applied.

## Numeric evidence and privacy boundary

Complete and partial records use `mgazenet_calibration_audit_v3` and
`mgazenet_calibration_audit_partial_v3`. The manifest binds the protocol,
pipeline and device identity, physical screen and viewport, exact fit and
verification geometry/order, timings, whole-target audit method, row counts,
unchanged SVR parameters and calibration digest.

The record contains per-target collection counts and rejections, timing and
output-age summaries, one scalar RMS within-target feature-dispersion value,
the 16×45 numeric LOO predictions, and numeric/null post-fit verification
results. It contains no image, landmark, 258-value feature vector or persistent
personal model. Feature dispersion describes input variation only; it is not a
gaze-error metric.

`tools/gaze/mgazenet/calibration_audit_metrics.py` independently validates the
manifest hash and identity, reconstructs every target from the viewport,
requires exactly sixteen 45-row target-isolated folds and all six verification
blocks, and rejects changed clocks, timings, methods, targets or privacy flags.
It reports LOO and post-fit results separately using X/Y, Euclidean and vertical
line-height errors, including target-balanced summaries. It always emits
`accuracy_gate_pass: null` and `promotion_decision: not_evaluated`.

## Camera-free verification

The following checks passed on 2026-09-09:

- all 147 benchmark JVM tests, including 9 new audit engine/session tests;
- compilation of the debug app, JVM tests and instrumentation tests;
- all 49 MGazeNet Python tests, including 8 new audit scorer tests;
- independent Python validation of the complete synthetic JSON emitted by the
  Kotlin report path: 16 target-isolated folds and 6 verification blocks;
- debug and instrumentation APK assembly;
- `git diff --check`.

The resulting, uninstalled artifacts are:

- app APK SHA-256
  `e7ee3d38ec189b2f4c217747c0b91ba061a3136d1cbbcfab29791aeabdcf7692`;
- instrumentation APK SHA-256
  `fa0341bf2f2582f4e5ddcf58d88d3894055943716995f267521980462de8294e`.

The read-only native package audit still reports the already documented
MediaPipe `libimage_processing_util_jni.so` 4 KB alignment exception, so the
package as a whole cannot claim 16 KB page-size compatibility. The required JNI
exports remain present and strong C++ imports resolve. V3 did not alter native
dependencies or treat that packaging observation as an accuracy result.

The synthetic cases cover a well-separated calibration, one corrupted target,
global feature collapse, a successful final-fit record with poor held-out
behavior, target-group omission, malformed row counts, non-finite values,
changed anchors, manifest removal and report identity changes. The setup-only
instrumentation test is compiled but was not run on a phone in this checkpoint.

## Files in this checkpoint

- `CalibrationAuditEngine.kt`: pure whole-target-fold audit and memory cleanup;
- `CalibrationAuditSession.kt`: fixed 16-point collection and six checks;
- `CalibrationAuditReport.kt`: hashed numeric-only v3 evidence;
- `CalibrationAuditActivity.kt`: explicit-start isolated UI;
- `AccuracyCameraSource.kt` and `SvrCalibration.kt`: audited fit path using the
  unchanged SVR implementation;
- `BenchmarkActivity.kt` and the benchmark manifest: camera-off setup entry;
- JVM/instrumentation tests under `mgazenet-benchmark/src/test` and
  `mgazenet-benchmark/src/androidTest`;
- `calibration_audit_metrics.py` and
  `test_calibration_audit_metrics.py`: independent validator/scorer and tests.

## Authorization boundary and next decision

The software checkpoint is complete. Do not install these APKs, contact a
phone, grant CAMERA, run a personal calibration, collect a v3 record, change a
threshold, promote MGazeNet or modify the active tracker without a new explicit
authorization. If a later device boundary is approved, the first step should
be a setup-only, camera-disabled geometry check on the exact hashed APK—not a
participant run.
