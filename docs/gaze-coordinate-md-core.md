# Coordinate MD core and collection handoff

Implemented 2026-11-02 on `experiment/gaze-trajectory-mahalanobis`.
Version: `coordinate_md_v1`. This is an experimental descriptive instrument,
with synthetic software verification and no phone/participant validation yet.
The [method plan](gaze-trajectory-mahalanobis-plan.md) and
[source review](gaze-coordinate-md-evidence.md) explain the research decisions.

## What it computes

For each eligible point `g=(x,y)`, the frozen reference supplies mean `mu` and
covariance `S`. The core solves for `MD²=(g-mu)' S^-1 (g-mu)` using scaled
Cholesky factors, and returns both MD² and its square root. Distances are unitless;
coordinates/deltas are physical screen pixels, covariance is pixels squared.
The large finite values remain available, including off-screen coordinates.

The summary is the time-weighted mean MD² over the caller's exact bounds.
It describes spatial departure, not the order/shape of an entire scanpath.
It cannot distinguish genuine looking from calibration drift or tracking error,
and has no reading-instability or quality percentage interpretation. There is no
MD threshold, cognitive label, outlier removal or connection to aid decisions.

## Files and API

| Responsibility | File |
| --- | --- |
| Python reference, strict JSON analysis CLI | `tools/gaze/coordinate_mahalanobis.py` |
| Pure Kotlin port, no Android dependencies | `app/src/main/java/com/newsmead/gaze/trajectory/CoordinateMahalanobis.kt` |
| Shared hand-calculated synthetic fixture | `tools/gaze/fixtures/coordinate_md_v1.json` |
| Python/Kotlin regression checks | `tools/gaze/test_coordinate_mahalanobis.py`, `app/src/test/java/com/newsmead/gaze/trajectory/CoordinateMahalanobisTest.kt` |
| Synthetic sensitivity receipt generator | `tools/gaze/check_coordinate_md_sensitivity.py` |
| Cached-compiler host runner | `tools/gaze/run_coordinate_md_host_tests.ps1` |

Python exports `fit_reference`, `CoordinateScorer.score`, `score_points` and
`summarize`. Kotlin exposes equivalent camelCase methods and nested immutable
types through `CoordinateMahalanobis`. Keep a scorer for one ordered evaluation
stream; it advances validation state, never the reference moments. The batch
summary accepts ordered observations and re-scores them against the supplied
profile, avoiding mixtures of different references. It is an offline/bounded
batch operation; processing the entire session anew every UI tick is not an
approved real-time integration design.

## Adapter contract for the collection branch

1. Supply copies of original `MgazeNetGazeProvider.Observation` events before
   line/word mapping or consumer-specific exclusions. For the canonical stream,
   use `rawX/rawY`, `captureMs`, `outputMs`, `deliveryId`, `reason` and
   `timeoutWatchdog`. `outputMs=deliveryElapsedNs/1e6`; timestamps use the verified
   elapsed-clock domain. Preserve original events even when distance is unavailable.
   Map missing values to null, never zero. Do not retain camera images.
2. Construct an explicit `Context`: participant, installation, calibration, model,
   acquisition configuration, presentation compatibility, clock domain, physical
   screen dimensions/rotation and coordinate stream. Every field must match the
   reference. The adapter owns reproducible identities and their underlying
   metadata; the core cannot verify a caller's identity string against the phone.
   A reading-setting/font/layout or acquisition-mode change cannot reuse an
   incompatible reference. The current 22dp baseline and adjustable article text
   are not automatically equivalent. Camera preference and actual cadence differ.
3. Supply a new provider epoch whenever delivery IDs restart. `context_epoch_id`
   identifies a stationary episode; change it for scroll/reflow/control/TTS or aid
   presentation transitions relevant to the comparison. Also pass explicit
   `Boundary` events, including transitions during tracking gaps. Set
   `context_allowed=false` during excluded periods. These are presentation rules,
   not inferred reading-event labels. No exposure interval crosses an epoch or
   boundary. A summary crossing an interior boundary or multiple epochs is
   unavailable; the reference may pool declared compatible stationary episodes.
4. Select a **separate earlier reference period**, with a unique reference ID,
   before evaluation. `fit_reference` rejects rows outside its inclusive declared
   bounds with `REFERENCE_BOUNDS_MISMATCH`; segment input explicitly. Placement
   uses finite capture time, otherwise finite delivery time. Fully unplaceable rows
   remain invalid adjacency breaks. No evaluation data enter a successful reference.
   All evaluated captures must be later than its end. Persist profile/config,
   source-period identity and raw records so fitting can be reproduced offline.
5. Supply original event order, watchdog/source-invalid rows, recorder-loss ranges
   and actual summary bounds. Unknown loss endpoints are conservative unbounded
   ranges. Any overlapping recording loss makes a reference fit or summary
   unavailable; available point distances still survive as observations. The core
   cannot discover unreported lost rows. Align bounds with the existing R/D/F
   outputs when comparing availability; do not assume its window is exactly 10s.
6. Use `article_smoothed_v1` only for a separate analysis with a matching separate
   reference. No stream substitution. Export all statuses alongside distances,
   counts/coverage and reference ID. This standalone JSON input is an analysis
   interchange/example, not a replacement study recording/package schema.

## Timing, covariance and availability

Adjacent fresh eligible captures in the same episode receive half their interval
at each endpoint, only when the gap is at most 250ms. Invalid rows break adjacency,
including those with no capture timestamp. No interpolation, edge extension,
future endpoint or exposure across a large gap is added. Coverage is summed
accepted interval duration divided by the entire requested window span. It is an
observed-time proxy; unmeasured movement between samples remains unknown.

Reference moments use weighted **population** covariance, denominator `sum(w)`.
The profile also retains unweighted mean/sample covariance (`n-1`) on points with
positive temporal weight, showing sensitivity to unequal cadence. These are
different estimators. The profile retains covariance entries, factor scale,
conditioning, weights/count diagnostics and the recorded ridge. Singular and
ill-conditioned references return unavailable. Default ridge is zero; an explicit
separate configuration can use `epsilon*trace(S)/2*I`. It never switches silently.

| Config field (Python) | Default | Meaning/limit |
| --- | ---: | --- |
| `max_gap_ms` | 250 | Maximum accepted adjacent capture gap |
| `max_age_ms` | 500 | Maximum capture-to-delivery age; matches current provider expiry |
| `min_reference_points` | 3 | Positive-weight points; mathematical minimum, not evidence of adequacy |
| `min_reference_coverage_ms` | 60000 | Provisional observed-duration gate, not an approved participant procedure |
| `min_summary_coverage_fraction` | 0.65 | Provisional availability gate, not validated quality |
| `relative_ridge` | 0 | Unregularized benchmark; explicit sensitivity variant can use 1e-6 |
| `max_condition_number` | 1e12 | Numerical guard, not a behavioral threshold |

Missing/invalid/stale clocks, unavailable source, watchdog, repeated delivery,
nonincreasing capture, context mismatch/exclusion and numerical failure produce
distinct statuses with null distance. Invalid clocks cannot advance the capture
floor. A genuine center point has MD=0 with `AVAILABLE`; missing input does not.

## Verification and measured limits

Run from the repository root:

```powershell
python -m unittest discover -s tools/gaze -p test_coordinate_mahalanobis.py -v
./tools/gaze/run_coordinate_md_host_tests.ps1
python tools/gaze/coordinate_mahalanobis.py tools/gaze/fixtures/coordinate_md_v1.json --output output/coordinate-md-v1-checks/fixture-analysis.json
python tools/gaze/check_coordinate_md_sensitivity.py --output output/coordinate-md-v1-checks/sensitivity.json
```

The host runner needs an existing Java executable and cached Kotlin 1.9.0/JUnit
dependencies; `-Java <java.exe>` overrides PATH. It downloads nothing and compiles
only this pure core and its test class. It does not exercise the Android build.

2026-11-02: **21 Python tests and 12 Kotlin/JUnit tests passed**. Both languages
read the same hand-calculated fixture (point MD² 25/0/2; summary 6.75, full coverage).
Additional correlated-covariance checks exercise the off-diagonal Cholesky solve.
Independent source review found clock poisoning and out-of-period reference
delivery state; both were fixed and checked with regression cases, then re-reviewed.

The reproducible receipt contains 35 synthetic cases: 6/10/15/30Hz and irregular
10Hz, crossed with no perturbation, offset, jitter, a spike, drift, gain and dropout.
It holds a separate 60s reference fixed. For the prescribed curve, unperturbed
mean MD² is about 1.990 at 6Hz and 1.990 at 30Hz; +75px horizontal offset adds
0.5. One +900px frame changes the mean by 0.4 at 6Hz and 0.08 at 30Hz because
its assigned exposure differs. Dropout retains about 83–85% coverage; missing
movement is not reconstructed. Independent four-point calculations reproduce
self-normalization (refitted means 1.5 with sample covariance, 2 with equal-weight
population covariance), motivating a separate frozen reference.

These are prescribed synthetic effects, not measured tracker errors, evidence of
older-adult validity or improvement in reading. Scores from 2-D coordinates and
3-D R/D/F have different reference/estimator/missingness assumptions; matching
numeric scale is not a fairness criterion. Report each score's availability and
the common-window subset, and assess calibration/cadence separately.

Collection integration, reference-task selection, phone overhead, local persistence,
package recovery/export and participant testing remain with the collection
workstream. No provider, activity, visual aid, R/D/F, model/filter, Firebase or
frozen collection protocol was changed. No device or private-recording work ran.
