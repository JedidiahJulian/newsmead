# Study readiness and measurement plan — 2026-11-02

Status: source-grounded review and proposed implementation order, not an approved
participant protocol. No app behavior, calibration, detector, or device changed.
Reviewed branch: `feature/scanpath-mahalanobis-score`.

The subsequent [direct pipeline trace and implementation map](study-data-pipeline-source-map.md)
identifies exact current integration points, existing logging/export limitations,
baseline/article configuration differences and shutdown requirements. The owner
confirms local recording followed by researcher transfer for initial testing.

## Current direction

The owner confirms older adults as the intended participants and wants both
feasibility/acceptability evidence and evidence about reading benefit. Phone
ownership, sample size, session duration and deadline remain undecided. The
2026-10-27 feasibility-only scope is therefore historical where it excludes an
aid comparison. Keeping both goals requires a procedure that can answer both;
collecting more gaze records cannot retrospectively create a comparison condition.

Mahalanobis remains observational. It must not change aid selection, thresholds,
strength, timing, or eligibility. A missing baseline or unavailable distance must
not prevent collection of other study outcomes. The owner also proposes a second
observational measure to explore behavior missed by the existing feature choices.
Its definition remains exploratory, with no new formula adopted by this review.

## What current source actually provides

| Component | Implemented behavior | Study consequence |
| --- | --- | --- |
| Ordinary app usage | Foreground/background sessions, article visits/dwell/scroll, TTS and translation; private JSONL. Participant defaults to `unassigned`, condition to `none`; no export caller found. | Useful infrastructure, but attribution and researcher retrieval need integration. Screen residence does not establish reading. |
| Diagnostic reading log | Manual calibrated recording, numerical observations, capture/delivery clocks, unsmoothed and displayed points, text/geometry/AOIs. Dot visible; aids/TTS disabled. | Cannot be used unchanged as normal aids-on study collection. No study manifest or process-death recovery. |
| Scanpaths and aids | Selected calibrated/One Euro-smoothed points, geometry and content-motion compensation; pattern outputs and V5 support/decisions/render proxies. | Ordinary aid trace is a 2,048-record memory ring with 512 retained support facts and manual export. It is not a full durable observation history. |
| Existing Mahalanobis | Separate smoothed-line feature path, independent of aid preferences; 10-second windows updated every 500 ms. | Baseline/model are calibration-bound and memory-only; latest/debug scores are not durable research records. No MD-driven adaptation exists. |
| Draft study package | Version 0.1 schemas, manifest, synthetic example and checker. | Proposed contract, not a live collector or a production importer. It predates MD integration and explicitly deferred that baseline. |
| Firebase | Authentication/Firestore dependencies and ordinary app operations; `BYPASS_LOGIN=true`, `OFFLINE_MODE=false`. | No study upload adapter; actual project usage, rules and permissions were not inspected. API access does not verify participant isolation or quota headroom. |

Source entry points, under `app/src/main/java/com/newsmead/`:

- `data/NewsMeadApplication.kt:118`, `research/ResearchSession.kt:40`,
  `research/SessionLog.kt:61`, `research/SessionLogExport.kt:39`.
- `gaze/ReadingMeasurementLog.kt:28`, `fragments/article/ArticleFragment.kt:805`.
- `gaze/aid/ReadingAidTrace.kt:13`, `gaze/aid/ArticleReadingAidRuntime.kt:45`.
- `fragments/article/ArticleFragment.kt:1366`, `gaze/ReadingFeaturePipeline.kt`,
  `gaze/ParticipantReadingBaseline.kt`, `gaze/mahalanobis/`.

### Existing Mahalanobis semantics

R counts confirmed backward line movements per minute. D is qualifying observed
line-residence overlap divided by the full rolling span; its 120 ms qualification
is not a validated long-fixation definition. F counts line stays of at least
300 ms per minute, despite its `fixationRatePerMinute` name. R and F also divide
by the full rolling span, not by valid observed time. Preserve these definitions
and their version rather than silently renaming the quantities physiologically.

Current operational gates include at least 65% valid samples and 65% endpoint
span, a fresh/valid latest input and discontinuity checks. The endpoint span is
first-to-last usable time, not a union of valid observed intervals. Baseline fit
requires at least 30 retained windows and 60 seconds of union window coverage.
These are software gates, not validated baseline adequacy criteria.

Adjacent 10-second windows stepped by 500 ms have nominal 95% overlap; actual
scheduler/window spans can vary. They do not provide independent replications.
Covariance has default diagonal regularization
`1e-6 I`; flat baselines can still produce a fit, and near-singularity is
diagnostic rather than an automatic rejection. Save means, covariance,
regularization, fit status, window provenance, coverage and numerical diagnostics.
Do not attach a default chi-square difficulty threshold to this fitted score.

Sources: `gaze/RollingReadingFeatures.kt:50`,
`gaze/ReadingStateInferencer.kt:205`,
`gaze/mahalanobis/MahalanobisModel.kt:48`,
`gaze/mahalanobis/MahalanobisBaseline.kt:36`.

## Measurement limits that affect the study

**Cadence:** the owner previously reported roughly 6–10 gaze callbacks/second
in affected reading conditions. The current rate has not been measured here.
Historical processing logs suggest workload differences but establish no cause
or restored rate. Camera capture, inference output, delivery callbacks and UI
frames are different quantities.

The scanpath detector uses a 125 ms sample-gap limit and at least three samples.
Regular 6 Hz input has about 167 ms between samples and therefore fragments this
detector; 10 Hz input has about 100 ms, with little margin for irregular capture
intervals. Delivery delay separately affects freshness and output age.
The V5 support detector and location support use 250 ms limits, so this does not
establish a universal 8 Hz failure threshold for all aids. Do not widen gaps just
to recover event counts: that changes the measurement and can join unseen motion.
Record capture intervals, delivery age, gaps and each pipeline's eligibility.

**Placement:** the checkpoint reports one visible-dot, smoothed, known-target
v6 run with 89.3% exact-line/47.5% exact-word placement on word targets, and
61.6% exact-line/99.2% within-one-line placement in guided tasks. These are
closed-loop instructed-task results at 22 dp, not natural-reading accuracy or
older-adult validation. Earlier independent stationary confirmation on one
participant/two phones failed every frozen regional screen. The protocols differ;
neither set establishes current population word accuracy. Use checks matched to
the current stream, reading layout and intended dot condition, keeping frozen
historical protocols unchanged.

**Interpretation:** the lookup rule is 1,000 ms supported residence on an exact
mapped word and offers an icon; a tap opens the definition. It does not establish
one continuous physiological fixation or word difficulty. Scanpath rereading,
missing gaze and controller fade/recovery clocks likewise cannot supply their
own cognitive ground truth. Smoothing reduces jitter while changing timing and
dispersion; agreement between selected points and the dot establishes consistency.

Relevant local evidence:
[current checkpoint](agent-context.md),
[smoothed-coordinate contract](calibrated-smoothed-scanpath-path.md),
[independent confirmation](gaze-mgazenet-direct-validation-confirmation-analysis.md),
[current aid rules](reading-aid-evidence-aligned-v5.md).

Primary research reinforces the transfer limit. Angele et al. (2025) studied
Spanish-speaking young adults with an EyeLink and downsampling as low as
31.25 Hz; those results do not validate irregular 6–10 Hz phone data with
different spatial error. GazePrompt used a desktop Tobii Pro Fusion at 120 Hz
with low-vision adults; it motivates studying reading support, not borrowing
its effects or thresholds for this phone/older-adult population.

## A second observational measure

The useful question is whether another description of gaze adds information
beyond R/D/F. Avoid describing any representation as assumption-free.

Direct Mahalanobis distance of `[x, y]` from a reading baseline measures spatial
departure from that reference distribution. Ordinary reading at the screen edge
or lower down can have high distance. It also discards sequence order: different
paths through the same point distribution can have the same positional summary.
Adding absolute time as another coordinate does not solve that problem.

The preferred exploratory route is to retain coordinates now and develop a small
trajectory feature set offline, without fixation/regression event classification.
Candidates include horizontal/vertical spread and sample-to-sample movement
magnitude or directional variability. These are proposed descriptors, not a final
feature set or validated instability index. Account explicitly for elapsed time,
screen/layout scale, scrolling, gaps and provider/calibration changes. At this
cadence, sample displacement must not be presented as measured saccade velocity.
Begin with settled non-scrolling intervals; report the excluded fraction.

Compare pre-smoothing and selected streams as labelled sensitivity analyses;
raw jitter, head movement and tracking error can resemble behavioral variability.
Any later covariance/reference-distance choice needs its own justification and
development-data checks. Develop features/reference fit on pilot/development
data, then freeze them before independent evaluation. Do not interpolate missing trajectories to manufacture
uniform samples or concatenate variable-length windows into an arbitrary large
covariance vector. Retain unavailable results with reasons.

Test incremental information: does the candidate relate to independently
reported loss of place, reading difficulty or reading outcomes after considering
R/D/F, coverage and cadence? Correlation with R/D/F or scanpaths is not independent
validation. Distances with different dimensions/reference fits are not directly
comparable in magnitude. Neither a high MD nor a reduction after an aid is, by
itself, evidence of harm or benefit. Deliberate rereading can be useful.

## Study design proposed for both goals

Start supervised. If practical, use one verified study phone/configuration for
the initial comparison; choose personal phones if deployment feasibility is a
central question and accept the device variation/bigger compatibility workload.
In either case, each participant uses the same phone within their comparison.
No home phase is necessary to start answering the current questions.

After accessible onboarding, practice and placement checks, collect an explicit
unaided reading baseline for MD. It is separate from gaze calibration and from
the scored comparison passages. Preserve its calibration/layout/configuration
binding and failures. Use ordinary reading instructions, not instructions to
avoid natural regressions. Freeze the reference for that comparison rather than
adapting it toward the condition being evaluated. Session/calibration changes
must follow a declared invalidation/recollection rule; persistence must not
silently broaden the current profile's permitted reuse.

Compare ordinary reading with aids disabled against the existing aid package
enabled, with gaze collection active in both. Randomize/counterbalance condition
order and article assignment; use different, piloted comparable passages rather
than rereading the same article. Hold typography/language/settings fixed within
the comparison unless accessibility requires a documented change. Keep TTS,
translation and definition consultation recorded and handle them in the procedure.
Use self-selected news separately for natural-use/acceptability observations.

| Question | Proposed endpoint | Interpretation |
| --- | --- | --- |
| Can intended users operate it? | Setup/reading completion, assistance, failures, comfort and usability feedback | Include attempted setups and incompatible/failed phones in feasibility denominators. |
| Can it measure interpretable data? | Prompted placement, fresh capture cadence, valid-time proxy, available MD windows, recording/export integrity | Report by participant/device/condition and relevant screen region; availability is not accuracy. |
| Does the package help reading? | Candidate primary: passage comprehension, using piloted content questions | Final endpoint and meaningful difference must be set before sample-size planning. Small samples may only estimate preliminary effects. |
| What other changes occur? | Reading time, perceived effort/loss of place, aid acceptance/disabling; optional controlled resumption task | Time includes explicit pause/consultation definitions. Self-report and task proxies remain distinct. |
| What do gaze measures add? | Scanpath descriptors, existing MD and candidate trajectory descriptors/measure versus independent outcomes | Exploratory mechanisms/associations, conditioned on coverage and context; not interchangeable with benefit. |

Analyze the assigned on/off package, including visits with no activated aid.
Comparing activated episodes with nonactivated episodes alone confounds support
with the behavior that triggered it. A package comparison cannot attribute an
effect to each of four aids or isolate adaptation from the aid's appearance;
those would require additional conditions. Existing aid-off preferences still
run the scanpath engine/MD, but some V5 support interpretations are skipped/reset.
Log those as not evaluated. A matched shadow-policy condition is an explicit
optional implementation, not something the current disabled path already provides.

Plan participant-level paired estimates and uncertainty intervals; model repeated
passages within people (and passage differences) if sample/design support it.
Do not treat samples, overlapping windows or sessions as independent people.
Use the chosen outcome, target difference/precision, plausible variability,
feasibility failures and burden to justify sample size. Avoid assuming that a
usual small usability sample powers an efficacy claim. Report all planned
outcomes and label additional searches exploratory, including null findings.

Prespecify failure handling: calibration failure before assignment remains in
feasibility counts; failure after assignment and unavailable MD do not by
themselves remove independently collected reading outcomes from the assigned
condition. Incomplete exports remain declared losses, with independent outcomes
retained where available. Follow the agreed withdrawal/retention procedure and
report missing outcomes rather than inventing values. Accessibility-driven
typography changes create a new layout/configuration epoch and require placement
rechecking and the declared reference-invalidation/recollection decision. Report
affected passages and all exclusions by assigned condition. Gaze availability
must not determine inclusion in the primary reading-benefit analysis.

## Implementation order and acceptance criteria

1. **Extend the collection contract.** Keep v0.1 fixtures intact; propose a new
   version with baseline attempt/window/model, feature/MD score, condition and
   independent-outcome records. Persist feature order/units, signed deltas,
   MD/MD-squared, status and model/reference IDs. Each provider delivery gets one
   observation plus separate per-pipeline interpretations/eligibility. Preserve
   provider epoch, capture/delivery clocks, selected/unsmoothed points, article
   text/layout/viewport references and producer versions. Save unavailable and
   rejected windows, not only successful scores. Candidate MD is a separate
   versioned analysis output; it need not run on-device to begin collection.
2. **Build the local study recorder and researcher controls.** Explicit participant,
   baseline/reading phase and condition assignment; observable recording state;
   numerical events only. Serial background writer, bounded queue with explicit
   losses, partial chunks, durable manifest, verified finalization and censored
   recovery after process death/reboot. Save aid evaluations/decisions/draw proxies,
   interactions and boundaries without changing controller behavior. Keep
   observation copying/disk work off inference/UI critical paths. Acceptance:
   deterministic joins and replay, declared durability/losses, no silent ring
   overwrite, normal/partial recovery and failed storage/export handled. With
   identical provider inputs, require identical aid decisions before/after
   collection integration, including missing/invalid baselines, extreme MD scores
   and recorder failures. MD/candidate outputs have no route into controller
   eligibility, strength or timing.
3. **Build export, importer and summaries together.** Researcher-controlled package
   export; validate hashes/counts/references/clock domains before deriving local
   tables. Recompute features/MD from retained inputs/reference where supported;
   report any unreplayable inputs. Acceptance: reconstruct a synthetic session,
   reject corruption, retain missingness and preserve originals. The current
   example checker is a fixture check, not this production importer.
4. **Run an authorized instrument shakedown.** First use synthetic/host checks,
   then separately authorized phone checks: matched placement across the reading
   area, prompted sequence tasks and ordinary reading under relevant conditions;
   assess cadence/latency, logger overhead, package size, heat/battery and recovery.
   Declare operational acceptance limits for recorder overhead, record loss and
   durability, export completeness and per-feature usable coverage before judging
   the shakedown successful. Preserve failed attempts and condition-specific
   losses. Use capture-gap distributions and usable windows, not aggregate FPS
   alone. Set per-feature progression criteria before the main study; exact-word
   analysis can remain unavailable while coarse/support and usability endpoints proceed.
   Failure to fit MD is a recorded measurement failure, not an invented zero.
5. **Pilot the procedure, freeze, then collect the planned comparison.** Use a
   small developmental pilot to check burden, comprehension items, reference
   variability and candidate trajectory features. Keep tuning and independent
   evaluation data separate. Resolve participant criteria/age definition,
   protocol review/consent, device route, retention and endpoint/sample rationale
   before recruitment. Freeze the study build/configuration and analysis plan.

## Firebase and storage boundary

Retain local session packages plus verified researcher export for supervised
testing. Firebase need not be the high-rate source of truth. Firestore's current
free quotas include 20,000 writes/day, 50,000 reads/day and 1 GiB storage, with
a 1 MiB document cap. They are project resources, not a per-participant allowance.
One document per gaze sample at 30 Hz would require 36,000 writes for a single
20-minute session (30 × 60 × 20), already above the free daily write quota.

Current Firebase documentation requires Blaze for Cloud Storage access. No plan
upgrade, upload, console inspection or live quota/rules check occurred here.
If remote home collection becomes necessary, select an authenticated transport
after measuring package sizes and checking access/quota behavior. Firestore may
hold small summaries/manifests or bounded chunks after a separate design/test;
keep transfer receipts separate from analytical events and do not block the
reading pipeline on network writes. Define controlled researcher storage and
pseudonymous identity linkage; do not collect camera frames.

## Checks performed and remaining limits

- `python tools/gaze/check_study_data_example.py`: passed 34 synthetic records,
  manifest/hash/count checks and all six corruption cases; recovery metadata
  shape passes, not real crash recovery.
- `python -m unittest discover -s tools/gaze -p test_analyze_reading_measurement.py`:
  39 passed.
- `python -m unittest discover -s tools/gaze -p test_scanpath_patterns.py`:
  13 passed.
- One bounded engineer source audit was reused for the current runtime findings.
  No Android build, device install, camera, calibration, recording or cloud access
  was performed. Earlier 655-gaze-test/build results are historical, not rerun
  here; the documented full-unit Firebase failure remains a separate issue.

These establish existing synthetic contracts and the basis for a plan. They do
not establish older-adult validity, current phone FPS, field reliability, or
reading benefit. Existing unrelated edits and untracked artifacts were preserved.

## Primary sources and method provenance

- Angele, B., Ozkan, Z. G., Serrano-Carot, M., & Duñabeitia, J. A. (2025).
  [How low can you go? Tracking eye movements during reading at different sampling rates](https://link.springer.com/article/10.3758/s13428-025-02713-3).
- Wang et al. (2024).
  [GazePrompt: Enhancing Low Vision People's Reading Experience with Gaze-Aware Augmentations](https://arxiv.org/html/2402.12772).
- [Firestore quotas and document limits](https://firebase.google.com/docs/firestore/quotas),
  checked 2026-11-02.
- [Cloud Storage billing requirements](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024),
  checked 2026-11-02.
- Methodological review used the installed scientific-critical-thinking and
  statistical-analysis skills. Software/procedure provenance: Kassis, T.,
  Agarwal, V., He, Y., Patel, D., & Brueckner, A. M. (2026).
  [Scientific Agent Skills: A Library of Procedural Knowledge for Research Agents](https://doi.org/10.48550/arXiv.2609.00065).
  This is tool provenance, not evidence validating NewsMead's measurements.
