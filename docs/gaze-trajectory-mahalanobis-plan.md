# Gaze-coordinate Mahalanobis: descriptive measurement plan

Updated 2026-11-02 after the owner's goal clarification and primary-source review.
Status: pure Python/Kotlin core implemented and synthetically checked; collection
integration and human validation pending. See the
[implemented API and handoff](gaze-coordinate-md-core.md) for files, conventions,
checks and remaining work. Branch:
`experiment/gaze-trajectory-mahalanobis`, base
`3119cb9c6ccae4cbb7dcd7a45c1b1f56f2b1f535`.

## Objective

Describe the app's estimated gaze coordinates relative to a declared reference
coordinate distribution. This additional MD is **not a reading-instability,
difficulty, attention, or support-need measure**. It remains observational and has
no route into visual-aid selection, strength or timing. Native R/D/F is unchanged.

The former H/V/movement vector was a lead proposal for a different question. It
is deferred and is not a prerequisite, adopted feature set, or default comparison
for this task. Full-trajectory similarity and behavioral prediction are also
outside this first implementation.

The [evidence review](gaze-coordinate-md-evidence.md) distinguishes published MD
uses, gaze-distribution summaries, tracking quality and this project's proposed
coordinate distance. It records source/access limits and a synthetic reference-
design check. There is no claim that an identical validated phone metric exists.

## Meaning of the result

For a fresh eligible calibrated pre-smoothing point g=(x,y), compare with frozen
reference mean mu and covariance S:

`D_squared(g) = (g-mu)^T S^-1 (g-mu)`

`D(g) = sqrt(D_squared(g))`

Interpretation: covariance-standardized spatial departure from that reference.
It does not explain why a point differs. Gaze behavior, article position, calibration
bias/drift and measurement noise can all contribute. No Gaussian assumption is
required to compute the distance; converting it to an outlier probability or
coverage confidence would require further assumptions that are not adopted here.

Record numeric distances without assigning stable/unstable, normal/abnormal,
difficult/easy or attentive/inattentive labels. Do not remove a point because its
distance is large. Operationally invalid source data remain explicitly missing;
that rule is separate from MD-based anomaly filtering.

## Reference is a separate artifact

Proposed first reference: an explicitly recorded, participant-specific ordinary
viewing/reading period, provisionally linked to the existing unaided baseline
attempt for practical integration. Its coordinates define a descriptive reference,
not true eye position or ideal reading. A prompted stationary target would define
a different reference and should not silently be substituted.

Record participant/run/reference IDs, provider/model/calibration identity,
coordinate stream, physical screen dimensions/rotation, actual typography/column
width and acquisition settings. Record reference text/context and source epochs.
Exact article-layout IDs are provenance, not a requirement that two articles have
the same wrapping. New provider instances can share a compatible reference;
provider-epoch IDs must not be confused with model/configuration compatibility.

Current baseline uses 22 dp/default camera policy; articles use adjustable sp and
an optional stable-FPS request. Integration must record these differences and
choose the intended reference context prospectively. No automatic model restoration
or broader baseline reuse after process restart is introduced by this branch.

Fit from reference data only and freeze the model before evaluating subsequent
points. Preserve invalid attempts and all exclusion reasons. Reference adequacy
criteria (duration, coverage, number of distinct captures and numerical conditioning)
are development decisions to set and test before real study use; the existing
R/D/F minimum does not automatically validate this coordinate reference.

**Do not re-fit each evaluation window to itself and average its squared MD.**
For conventional unregularized sample covariance that average is largely fixed
by dimension/sample count, regardless of spread. The evidence note derives the
identity and supplies a four-point numerical check.

## Current source and input contract

`MgazeNetGazeProvider.Observation` supplies `rawX/rawY` in physical screen pixels
before One Euro, selected `x/y`, capture/delivery clocks, delivery ID, reason,
blink/watchdog and source counters. These are estimates from saved SVR calibration,
not camera images or the 258 neural features. Existing output expiry is 500 ms;
current smoothing parameters remain 0.7/0.002. No filter is added or retuned.

Use original observations before `ArticleFragment` creates per-consumer exclusions.
The measurement core receives copies and immutable context events; it does not
call Scanpath/V5 or require word/line mapping, fixation extraction, regression or
dwell classification. Current `coordinate` status establishes operational validity,
not spatial accuracy. Finite fresh points can be wrong.

Required input includes participant/reference identity, provider epoch/delivery
ID, capture/delivery time, source reason/watchdog, raw and selected points, screen
geometry, context boundaries/eligibility and recorder-loss ranges. No coordinate
stream can silently substitute for another. Watchdog duplicates do not create a
new capture. Null/nonfinite/stale/nonmonotonic observations do not become (0,0).

Canonical first coordinate stream: calibrated pre-smoothing x/y in physical
screen pixels, with y increasing downward. Selected-coordinate calculations may
be a separately versioned sensitivity analysis with their own reference; do not
pool the two. Physical screen pixels are not degrees of visual angle.

## Extraction and estimation conventions to prototype

Distances are pointwise. Matching the existing 10-second/500-ms cadence is useful
only for summary alignment: accept the actual R/D/F window bounds when available,
including its possible window overhang, rather than inventing identical windows.
Pointwise results are not themselves 500-ms independent observations.

For duration-based reference/summary calculations, give each endpoint half the
interval to the next fresh eligible capture in the same context, with a provisional
250-ms maximum adjacent gap. Add weights from the preceding and following accepted
intervals. There is no edge extension, interpolation or bridging invalid segments.
Time covered by accepted intervals is an observation-time proxy, not proof that
all intermediate gaze was measured. Retain raw sample counts as well as weights.
This reduces high-cadence segments' disproportionate exposure weight; it cannot
recover missing movement or correct location-dependent tracking failure.

Initial reference moments use explicitly **weighted population moments**:
`mu=sum(w*g)/sum(w)` and `S=sum(w*(g-mu)*(g-mu)^T)/sum(w)`.
This defines an empirical observed-time coordinate distribution; it is not claimed
to be an unbiased covariance estimator. Also retain an ordinary unweighted sample-
covariance sensitivity result to expose effects of irregular cadence. The evidence
note's self-reference identity uses n-1 covariance and is not falsely presented
as the exact formula for this weighted variant.

Use Cholesky solves, not an explicit inverse. Preserve S (from which its
determinant is derivable) and conditioning diagnostics. Full-rank unregularized distance is the initial
mathematical benchmark. A singular or unreliable reference produces an explicit
unavailable state. A separate recorded regularized variant may use
`S + epsilon*trace(S)/2*I`, with provisional epsilon=1e-6 and sensitivity checks;
zero trace is degenerate. This ridge has pixel-squared units and scales with uniform
coordinate rescaling. It is not the same estimator as native R/D/F's fixed ridge,
or a cure for contaminated/nonrepresentative reference data. Never silently clamp
or delete large finite observations, drop a dimension, or switch estimators.

Start summaries in stationary presentation/context windows. Windows crossing
scrolling, reflow, provider/calibration change, controls, definition consultation
or TTS state changes are explicitly discontinuous; do not pool positions before
and after a scroll as if presentation were unchanged. Missing tracking inside an
unchanged context follows the declared gap rule and retains coverage information.
Pointwise raw observations remain in the collection package even when a summary
is unavailable. Later moving-viewport analysis requires its own declared frame.

Prototype summary outputs: time-weighted mean MD-squared, available point count,
observed interval duration/coverage, and the empirical distance distribution.
Any median/quantile definition must state its weighting. Report each score's own
availability and the common-window subset separately. Do not convert MD to a
percentage of gaze quality or force equality with the existing three-dimensional
R/D/F distance.

## Descriptive companions and separate quality assessment

Retain mean, covariance and coordinate plots alongside distance. A covariance
ellipse/area and axes describe observed spatial extent; the Gaussian BCEA coverage
formula is a model-based contour, so show the observed inclusion fraction and
inspect multimodal clouds. These companions do not constitute the earlier
engineered-feature MD. The data may contain several ordinary viewing locations.

During ordinary viewing, observed spread combines behavior and measurement error.
It cannot alone establish tracker precision. Accuracy requires independent known-
location observations; cadence, latency and missingness remain separate quantities.
Do not diagnose the cause of a large distance from the distance itself.

## Implementation ownership and order

This branch owns new measurement/analysis files. The collection workstream owns
existing activity/provider logging integration, package persistence/export and
producer joins. Do not create a competing collection format or edit aid behavior.

1. Define a small immutable observation/reference/point-distance/window-summary
   contract and version identifiers. Preserve all units/status/reference metadata.
2. Implement a pure offline Python reference, with explicitly synthetic fixtures,
   in `tools/gaze/coordinate_mahalanobis.py` and focused tests. Fit/score/summary
   operations remain separate. Adapt existing log formats explicitly; frozen
   diagnostic/pilot schemas are not silently reinterpreted.
3. Exercise reference choice, covariance weighting/conditioning and error/cadence
   sensitivity before declaring a v1 production configuration. Keep development
   and independent evaluation material separate. No private capture is needed for
   these first checks.
4. Port the agreed pure core into new `gaze/trajectory/` Kotlin types with matching
   parity fixtures. Keep Android Views, camera, Firebase, Handler and aid/controller
   dependencies outside the core. Do not refactor native R/D/F to fit this method.
5. Supply the collection workstream an adapter contract for points, reference
   moments, distances, statuses and optional aligned summaries. Later integration
   needs unchanged-aid assertions and measured phone overhead under separately
   authorized device work.

## Acceptance and limitations

Check hand-calculated 2-D distance and covariance; translation/uniform scaling
with reference transformed consistently; shifted/wider evaluation clouds against
a frozen reference; self-normalization; rank-deficient/near-singular/zero-spread
references; weighting/cadence differences; duplicate/out-of-order/watchdog input;
context/scroll changes; no future samples or gap interpolation; reference/participant
mismatch; raw/selected separation; recorder-loss censoring; and Python/Kotlin parity.

Stress prescribed trajectories with offset changes, random jitter, isolated spikes,
slow drift, changed gain, dropout and irregular 6/10/15/30-Hz input. Distinguish
synthetic perturbations from measured phone errors. Report distance and unavailable
time changes without requiring a preferred metric to win. Some genuine gaze changes
and tracker errors are unidentifiable from this source alone.

No reading-instability threshold, cognitive label, automatic outlier removal,
clinical interpretation, evidence of reading benefit or empirical phone-accuracy
claim follows from these checks. This plan supersedes the previous behavioral
feature-vector direction for the owner's clarified descriptive-gaze objective.
