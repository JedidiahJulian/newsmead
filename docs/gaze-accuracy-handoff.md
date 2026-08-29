# Gaze Accuracy Work — Task Handoff

## Purpose

This is the concise entrypoint for continuing NewsMead gaze-accuracy work in a new Codex task. Read this file first, then consult `gaze-diagnostics-log.md` only for detailed evidence.

## Current checkpoint

- Date: 2026-08-29
- Branch: `gaze-pipeline-improvements`
- Latest committed gaze checkpoint: `aad7b53` (`docs(gaze): record accuracy intervention outcomes`)
- Prior diagnostics commit: `79a5ceb` (`feat(gaze): add diagnostic telemetry and session logging`)
- Broad initial diagnosis: complete
- Required final control before estimator changes: complete; telemetry OFF did not materially restore FPS or accuracy
- Current decision gate: per-eye mapping and upper-left pre-acquisition both failed prospectively; the direct quadratic mapper and original single-practice sequence remain active. The posture-departure shadow build is installed and awaits one on-device calibration/test evaluation.
- Do not stage or commit unrelated dirty files or `diagnostics-local/` without explicit approval.

## Project goal and constraints

Improve the existing fully on-device MediaPipe Face Landmarker + iris-relative gaze system enough for gaze-adaptive reading measurement. The thesis's primary contribution is the gaze-adaptive interface and its measurement, not a novel gaze-estimation model.

Current constraints:

- No paid EyeDid dependency.
- No mounted Tobii or laptop-mounted tracker.
- No co-located laptop/WiFi gaze backend for the intended deployment.
- Improve and diagnose the existing system before considering a new model.
- Do not claim reading-line compatibility from spatial calibration alone.
- Do not retain camera images under the current consent conditions.

## What diagnostics established

1. The current tracker is substantially worse and less stable than the earlier recorded behavior. Historical A56 vertical error was approximately 0.67–1.01 cm, while recent fixed-phone live vertical medians were roughly 2.3–2.9 rendered line-heights with tails above eight lines.
2. Median-only reporting hid severe regional failures. The new validation reports every target, signed dx/dy, vertical error in line-heights, median/P95/max, threshold counts, worst region, coverage, and drift.
3. Phone stabilization helps typical error but does not eliminate large drift or regional failures.
4. MediaPipe availability and inference execution were not the dominant observed failure: SAMPLE windows had adequate accepted samples, little/no face or blink loss, and roughly 30–31 ms median submit-to-result time.
5. The A56 receives 320×240 CameraX frames despite the previously intended 480×360 profile.
6. Raw two-eye averaged iris features shift over minutes and have weak/overlapping vertical separation, especially in the lower screen region.
7. Mapper soft extrapolation amplifies some catastrophic tails, but offline restoration of the old hard clamp did not recover line-level performance by itself.
8. A single global affine correction is not justified for strongly regional errors and must be evaluated on a separate post-correction run.
9. The latest valid pre-target-change paired artifacts are in `diagnostics-local/2026-08-24/metrics-v2/run-2/`: 16-point LOO vertical median 3.61 lines, held-out median 3.00, vertical drift about -3.23 lines, and immediate nine-point median 2.69 with an 8.17-line maximum.
10. Two natural-handheld telemetry pairs per mode showed comparable throughput and mixed accuracy: calibration FPS averaged 26.54 ON versus 26.25 OFF, nine-point FPS 27.08 ON versus 26.60 OFF, and OFF did not consistently improve samples, dispersion, typical error, tails, drift, or regions. Detailed instrumentation is not supported as the regression cause.
11. Those four valid live runs were much better than the earlier diagnostic set: 0.59–1.38-line vertical medians and 2.54–3.43-line tails. A persistent regression is therefore not established; independent-session repeatability is the next question.
12. The independent August 28 OFF pair produced a 1.55-line live median but a 4.87-line top-left maximum; its calibration held-out median/max was 4.49/7.44 lines despite healthy throughput, samples, and low dispersion. Typical performance is improved, but severe regional tails remain insufficiently repeatable.
13. Across all five recent valid pairs, top-left was the worst calibration LOO target in 5/5 and the worst live target in 4/5. This is a systematic regional/first-target weakness, not a metric that distinguishes good from bad sessions.
14. Mapper conditioning was modest and did not rank the live tails. Held-out maximum also failed to rank them: OFF2 had 7.82 held-out but the smallest 2.54 live maximum, while fresh OFF had 7.44 held-out and the largest 4.87 live maximum.
15. High drift was the only useful directional warning: the three high-drift sessions had 3.08–4.87-line live maxima versus 2.54–3.04 for the two low-drift sessions. It is not sufficient to certify accuracy, and n=5 supports only a conservative stopping rule.
16. A five-coefficient ridge-linear mapper over the four separate eye features looked promising retrospectively but failed two prospective runs: live vertical median/max was 5.74/8.83 and 2.63/4.19 lines, versus 0.76/1.04 for the immediately preceding quadratic run. Same-calibration held-out comparisons were mixed, so the quadratic mapper was restored.
17. A fresh post-rollback calibration/test confirmed `quadratic_average` was active and recovered to a 1.35-line live median and 2.58-line maximum. The calibration was telemetry OFF, while the test was inadvertently telemetry ON despite its typed label; this remains valid as a mapper sanity check but not as an ON/OFF pair.

## What changed during the conversation

The branch began from `f77ce6e`, which already contained the July 24 calibration redesign.

Commit `79a5ceb` added detailed telemetry and JSON persistence. Git confirms that it did not change `GazeMapper`, `FixationWindowFilter`, `DriftCorrection`, `MedianFilter`, or `OneEuroFilter`, and the final MediaPipe two-eye average remained numerically the same. It added per-frame callbacks, UI-thread work, event buffering, and repeated synchronous JSON rewrites, but the completed E-034 ON/OFF control found no material FPS or accuracy recovery when that work was disabled.

Commit `e214eb6` added reading-oriented validation and the researcher-requested older-adult target/order behavior. The 16-point order is fixed row-major; the nine-point order was already fixed row-major. The shared target uses a fixed black ring/reticle and center plus with a saturated red, black-outlined disk contracting over approximately 1800 ms. These target/order changes occurred after the poor E-030 pair and cannot explain that earlier result, but they must remain identical across the next A/B.

After E-034, a centered 3–2–1 pre-run countdown was added to remove the abrupt start. It finishes before the first target and does not alter any target, sampling, estimator, mapper, filter, correction, or ordering behavior. The verified build is installed on the A56.

No uncommitted gaze source changes remained immediately after `e214eb6`; unrelated pre-existing working-tree changes and local diagnostic artifacts remain.

On August 29, per-eye aggregate persistence, legacy CSV compatibility, a small experimental per-eye mapper, and an offline candidate-comparison tool were added locally. After two prospective failures, the experimental mapper/factory/live filters were removed completely. Aggregate-only per-eye calibration evidence and the offline tool remain for research; the live runtime is again the direct quadratic mapper.

## Exact next action

Collect one ordinary telemetry-OFF 16-point calibration and its immediate telemetry-OFF nine-point test on the installed posture-shadow build. The new full calibration is necessary because older 4/8-column calibration files contain no posture reference; it is not a request to repeat the rejected upper-left intervention. Use labels `posture-shadow_tel-off_cal_1` and `posture-shadow_tel-off_test_1`.

Evaluate only whether the shadow flag is available and behaves plausibly: report the per-point and whole-run in-range/out-of-range counts and which posture axes triggered, alongside the unchanged accuracy metrics. Do not suppress samples, change AOI/scaffold decisions, or claim an accuracy improvement from this run. If the shadow flag is mostly unavailable or marks ordinary calibration-like posture as out of range, adjust the monitoring envelope before any downstream use.

Keep `QUADRATIC_AVERAGE` active. Do not deploy another mapper from the retrospective ranking alone, and do not add the previously proposed automatic high-drift rejection: the later 201 px-drift quadratic calibration produced the best live result (0.76-line median, 1.04-line maximum), showing that such a rule would reject a demonstrably useful calibration.

The next accuracy intervention requires evidence that is independent of target position. In particular, do not fit face position or scale as calibration coefficients from the existing fixed sequence, because posture drift can be correlated with row-major target order. Per-eye aggregates may remain in telemetry-OFF logs for research. The implemented pose-departure detector is therefore evidence-only: it records eye-midpoint face centre, eye separation/scale, and roll against a conservative calibration envelope, but cannot alter coordinates, filters, the mapper, AOI, scaffold decisions, or sample inclusion.

The rollback sanity check is complete; do not request another run merely because its nine-point telemetry checkbox was ON. It explicitly confirmed the quadratic mapper and no affine correction. Its 1.35/2.58-line median/max was materially better than both per-eye runs, though worse than the preceding unusually strong 0.76/1.04-line quadratic result.

The upper-left-practice intervention is complete and rejected. Its live median improved from 1.35 to 0.96 lines, but top-left worsened from 2.52 to 2.78, the maximum worsened from 2.58 to 3.53, the 2-D median worsened from 215 to 343 px, and calibration held-out median/max worsened to 5.72/10.12 lines with 850 px drift. The original single-center-practice sequence was rebuilt, retested, and installed. Do not repeat this intervention.

The app-private `calibration_16point.csv` still contains the calibration collected during the intervention; installing a rollback does not rewrite calibration data. Its source JSON is preserved. Do not overwrite it automatically from tooling. A future ordinary user calibration will replace it through the normal application flow.

Both modes must retain lightweight evidence:

- Run/mode label
- Point target and accepted estimate
- dx/dy and reading-line metrics
- Retained/raw sample counts and dispersion
- Lightweight FPS value or summary
- Drift and validation summaries

Telemetry OFF must omit per-frame source/pipeline arrays and avoid repeatedly serializing the large detailed event history. Telemetry ON preserves current instrumentation.

Control result:

- Included: two correctly labelled/embedded ON calibration-test pairs and two OFF pairs.
- Excluded: `handheld-tell-off_cal_redo-1` plus its following test because the calibration embedded `detailed_on` while the test embedded `detailed_off`.
- Decision: modes were comparable; telemetry OFF produced no material recovery. Close the instrumentation diagnostic, but defer any July 24 regression comparison until the fresh-session check establishes whether the current improvement is repeatable.

Fresh-session result:

- Included pair: `handheld_fresh_off_cal_20260828_1` plus `handheld_fresh_off_test_20260828_1`.
- Calibration: 25.81 FPS, 20/14 median raw/retained samples, 1.29-line LOO median, 6.24-line LOO tail, 286 px drift, and 4.49/7.44-line held-out median/max.
- Live: 25.61 FPS, 19/12 raw/retained samples, 1.55-line median, 4.87-line tail, 4/9 within 1.2 lines, no correction.
- Decision: typical improvement partly repeated, but severe tails returned. Investigate calibration repeatability/region geometry before estimator or mapper changes.

Offline result:

1. No saved calibration metric reliably predicted the live tail across the five pairs.
2. Mapper conditioning and held-out validation were not useful selectors.
3. High drift was directional but imperfect and supports only the narrow redo/abort stopping rule above.
4. Top-left was systematically weak in both calibration LOO and live testing; fixed row-major order confounds screen region with first-target timing.

## Files to read next

1. `docs/gaze-accuracy-handoff.md` — this checkpoint.
2. `docs/gaze-diagnostics-log.md` — full evidence registry E-001 through E-036.
3. `docs/calibration-design.md` — current calibration and validation behavior.
4. `docs/build-context.md` — architecture history and deployment constraints; note that portions are historical.
5. `docs/gaze-model-replacement.md` — alternatives/proposal context only; do not jump to model replacement before completing the telemetry control and regression comparison.

## Verification state

- The posture-shadow implementation compiles, all gaze-focused JVM tests pass, the debug APK assembles, and it is installed on the A56 with app data preserved (the 52-test full suite has the unrelated existing `FirebaseTest.createAccount` failure).
- The August 29 quadratic-default rollback also passed all 46 gaze-focused JVM tests, assembled successfully, and was installed over the existing app with data preserved.
- The subsequent quadratic-only upper-left-practice build passed the focused gaze tests, assembled successfully, and was installed with existing data preserved.
- After E-041, the original one-practice sequence was rebuilt, passed the focused gaze tests, assembled, and installed with diagnostic data preserved.
- The complete unit suite has one unrelated existing failure in `FirebaseTest.createAccount`.
- Do not apply the nine-point affine candidate during baseline comparisons.
- Preserve diagnostic JSON locally and keep participant artifacts unstaged.
