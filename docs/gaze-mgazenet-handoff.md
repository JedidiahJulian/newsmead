# Native MGazeNet feasibility - next-task handoff

Updated 2026-09-06. Read after `gaze-accuracy-handoff.md`.
This brief supersedes conflicting historical freeze, replacement, and repeat-test proposals.

**2026-09-07 continuation:** the authorized isolated benchmark and two-phone
camera-free checks are now complete. Read `gaze-mgazenet-benchmark.md`, then
`gaze-mgazenet-accuracy-readiness.md` for current evidence and next work. The user
clarified that accuracy and trustworthy measurements take priority; further
speed optimization is not the default next task. Historical authorization and
pending-check statements below describe the original handoff, not present state.
The next accuracy preparation checkpoint is `gaze-mgazenet-accuracy-protocol.md`:
localizer adaptation audit and tested offline measurements, without camera or
participant calibration runs.
The implemented harness and latest device-check status are recorded in
`gaze-mgazenet-accuracy-harness.md`; this is the current continuation entrypoint
after the required historical handoff reading.

**Protocol review follow-up:** `gaze-mgazenet-protocol-review.md` identifies
the fixed-order/region confound, limits of stationary validation, and the bounded
input-only check and subsequent pilot proposal. Its host-only scorer repairs
close manifest-removal and target-geometry bypasses; 37 host tests pass. No new
phone action or participant session occurred. Continue from that review rather
than repeating the completed performance or setup checks.

**Input-check implementation follow-up:** `gaze-mgazenet-input-check.md` records
the completed software work, the retained first SM-G991B bitmap-lifetime failure
and the retained next-frame recycled-buffer failure. The complete repair is
host- and device-verified and completed the full input check on both phones.
No personal calibration or accuracy trial occurred; continue from the input-check
checkpoint before considering the prospective v2 pilot.

**Stationary v2 follow-up:** `gaze-mgazenet-stationary-v2.md` is now the current
continuation point. The isolated app and offline scorer implement the reviewed
ten-location/two-sweep protocol and pass host verification while retaining v1
support. Its authorized camera-free setup/geometry check now also passes on the
A56 and SM-G991B with CAMERA still disabled and no record created. Both actual
layouts retain ten distinct repeated locations, no fit overlap and no default
order choice for the exact hashed APK pair. No personal calibration occurred;
participant collection remains a separate later authorization.

**Pilot-results follow-up:** `gaze-mgazenet-stationary-pilot-results.md` records
both authorized SM-G991B sessions with fresh calibrations and opposite order
plans. It retains all target blocks, extreme spatial tails, fixed four-age
availability tables and the reversal of signed bias between sessions. No gate
was assigned and CAMERA was revoked after each. Do not tune or rerun either
session. The participant subsequently disclosed severe fatigue and roughly two
weeks without good sleep during both runs; session 2 otherwise used the same
reported conditions but occurred at night in a slightly dimmer lighted room.
Treat both as fatigue-context feasibility evidence and do not causally subtract
fatigue from tracker error. The two A56 sessions remain pending separate
authorization and are paused rather than being solicited during ongoing fatigue.

## Goal and authority

Find the best feasible on-device gaze approach under the project's actual accuracy, FPS,
device, privacy, and researcher-time constraints. The user challenged a premature conclusion
that improvements were exhausted and described native MGazeNet as promising. The latest
explicit authorization is **prepare the handoff**. No MGazeNet implementation, installation,
camera session, calibration, or model promotion has been performed or authorized by that request.

Start the next task with a read-only feasibility audit. Present its bounded implementation
proposal before changing the app. Do not treat this document as advance permission for phone
actions or an open-ended model-training project.

## Why this route, and what it is not

The historical GazeFollower integration used a laptop webcam and streamed laptop-screen
coordinates to the phone (`tools/gazefollower_stream.py`). That arrangement was removed for
field-study logistics, not because its model had been tested natively on Android and failed.
Native MGazeNet would use the phone camera and local inference: no WiFi bridge or laptop camera.
The review found no documented native MGazeNet gate in the project evidence.

Public GazeFollower source contains an MGazeNet estimator and pretrained `base.mnn` (GitHub
reports 5.96 MB). It uses eye and face appearance plus crop geometry, retaining information
that the current two averaged landmark features omit. The related smartphone research includes
Android/MNN deployment. These are reasons to test feasibility, not proof of better gaze.
Sources below were inspected on 2026-09-06; pin exact revisions before implementation.

MGazeNet is **not** the previously rejected MobileGaze/L2CS angular model. It is also not the
rejected compact 468-face/two-iris landmark pipeline. Do not silently substitute one for another.

## Exact first action - no new calibration required

1. Recheck repository status and the source/evidence anchors below. Preserve all dirty work.
2. Audit and pin the intended public model, source revision, model hash, and usage terms.
   The historical Python requirements pin `gazefollower==1.0.2`; reviewed upstream `main`
   is not automatically that version or the exact historically tested weights.
3. Write down the exact preprocessing contract: frame rotation/mirroring, RGB/BGR, left/right
   eye identity and flip, crop bounds/padding, resize interpolation, tensor layouts, numerical
   normalization, and rectangle coordinate units. Inspect executed code, not comments alone.
4. Verify output shape/meaning and calibration. Upstream exposes the complete output as
   features and its first two values as raw coordinates; its default calibration uses two
   OpenCV RBF support-vector regressors. Do not plug these outputs into the current eye-local
   calibration or call a new ridge fit equivalent to upstream calibration.
5. Identify an Android inference/build path and its total cost, including face/eye localization.
   Produce a short feasibility decision, unresolved requirements, and a bounded benchmark plan.
   Do not download participant datasets, contact authors, or train a new network automatically.

Expected first deliverable: a verified model/preprocessing/calibration manifest and a go/no-go
proposal for an isolated benchmark, not a request for another 16-point/9-point run.

## Proposed gates after implementation approval

- **Numerical correctness:** compare reference and Android preprocessing/inference on identical
  permitted inputs. Use synthetic/public appropriately licensed fixtures, or explicitly approved
  in-memory comparison; never retain participant camera frames. Test boundaries, eye flipping,
  colour conventions, layout, and coordinate units before interpreting accuracy.
- **Full-path speed on both phones:** measure capture-to-output latency, sustained completion
  FPS, dropped frames, warm-up, and thermal conditions. Separate isolated-model time from total
  face detection/cropping/model/calibration cost. A benchmark must not touch the real calibration
  store or feed reading/AOI/RSI. Confirm authority before installation or starting its camera.
- **Accuracy only after feasibility:** plan a fresh matched reference/candidate comparison,
  each with its own compatible calibration and independent held-out targets. Retain failures and
  preregister replication. Measure natural modest posture changes and known-target reading,
  vertical median/P95/max in actual line heights, signed regional errors, 2-D error, coverage,
  and sustained FPS. Five held-out targets give a very coarse tail estimate, not precise P95.
- **No test-set tuning:** fix preprocessing/calibration choices before held-out evaluation;
  split whole targets/blocks/sessions, not adjacent frames of one fixation. If same-frame dual
  inference is used for paired numeric evidence, benchmark each path alone for speed.
- Agree numerical acceptance criteria before trials. The historical 1.2-line all-target screen
  is provisional, not a validated reading requirement. There is no user-approved 15-18 FPS floor.

## Important uncertainties and alternatives

- The public repository distinguishes a model trained on 7 million images from a larger model
  available by request. Do not assign the latter's results, or paper results, to `base.mnn`.
- The smartphone paper reports about 12.19-17.20 ms Android model inference and about 16 ms
  preprocessing separately. Neither is measured on our current phones/build; 30 FPS is not assured.
- The default upstream face alignment uses refined MediaPipe FaceMesh; adding MGazeNet to our
  slow 478-point path might worsen throughput. Upstream also provides a BlazeFace alignment
  variant, but switching crop generators needs numerical/accuracy evaluation, not assumption.
- Public source lists CC BY-NC-SA 4.0. Verify model/runtime terms and required notices before
  inclusion; public availability is not unrestricted use. No legal conclusion is asserted here.
- Historical laptop accuracy used a different camera/setup and is not a paired phone baseline.
- One researcher on two phones establishes neither population generalization nor a causal
  device effect. Avoid constants fitted to this person's known failures. Participant calibration
  is expected; a long per-participant experimental A/B workflow is not acceptable logistics.
- Proper limited pose compensation remains the second candidate. Current eye-local geometry
  handles image-plane roll/translation/scale, not explicit pitch/yaw or perspective compensation.
  The posture envelope is shadow-only, not compensation. Collect independent within-target pose
  variation before fitting extra coefficients; simply adding pose to row-major calibration can
  learn target/time confounds. Verify pose geometry and overhead rather than assuming accuracy.
- Alternatives reviewed: MobileGaze/L2CS already failed in its previous form; iTracker requires
  legacy conversion; SAGE and the reviewed CVPR 2025 cross-task project's public materials did
  not establish a ready mobile implementation. 3DGazeNet has public code/model links, but its
  Android cost and screen calibration remain unverified. These are not declared impossible.
- Do not use expected reading order, line snapping, or scaffold behaviour as gaze ground truth.
  Such priors can hide genuine regressions and contaminate the thesis outcome measures.

## What the previous experiments actually rule out

| Evidence | Keep this conclusion | Do not infer |
| --- | --- | --- |
| E-034 telemetry control | OFF did not restore accuracy/FPS materially | Every instrumentation or performance question is resolved |
| E-038 separate-eye linear mapper | That prospective mapper failed twice | Per-eye information has no value in other representations |
| E-043 posture envelope | Its flag did not identify high-error targets | Head-pose compensation was tested and failed |
| E-045 eye-local geometry | Retained after two favorable prospective runs | Generalizable reading-line accuracy is certified |
| E-053 coordinate repair | Real view/screen-origin bug repaired | Remaining errors are all estimator limitations |
| E-056 performance screen | Those implementations did not recover throughput | An 81.6 ms trace or a source-inequivalent low-resolution path exhausts optimization |
| E-070-E-073 compact calibration | Exact candidate repeatedly missed the accuracy gate | Current baseline was proven superior in fresh matched sessions |
| E-074 fixed translation | Full constant shift gives mixed benefit and tails | Time-dependent or measured-pose correction was disproved |

E-074 changes every fit feature by the same vector; it is not per-point temporal dewarping.
The rejected results stay in the record. Do not tune variants against the same held-out answers.
Also do not add automatic high-drift rejection: a prior 201 px-drift calibration produced the
best live result (0.76-line median/1.04 maximum). Drift is evidence, not a validated quality gate.

## Workspace, runtime, and preserved evidence

- Work only in `C:\Users\USER\OneDrive\Desktop\newsmead` for app changes, not the old prototype.
- Branch verified: `gaze-pipeline-improvements`; HEAD `6104d56`, compact research harness.
  Earlier commits: `03c74f3` hybrid harness; `74f43c4` screen-coordinate repair/performance record.
- Active runtime: MediaPipe 478-point GPU with CPU fallback; 640x480 request, upright 480x640;
  `eye_local_width_average_v1`; two-eye average; standardized six-term ridge quadratic (ridge 1,
  2.5-z soft extension), temporal filters, separate affine correction, posture shadow only.
  Preserve target timing/order, countdown, screen-coordinate/hash contracts, and telemetry control.
  Hybrid shadow is disabled. No native MGazeNet assets/runtime changes were made in this review.
- Last-checkpoint device IDs: A56 `R5CY40Y2C5W`; SM-G991B `R5CR60YSJ2X`. These are identifiers,
  not assertions of current connection, calibration freshness, installed APK, or phone state.
  No device was contacted while preparing this handoff. Verify hashes before later device work.
- E-054 references: `diagnostics-local/2026-09-04/coords-v1-a56/`, calibration JSON timestamps
  `20260904_024210` / `20260904_195333`, and corresponding reading JSONL files
  `20260904_024346_370` / `20260904_195507_232`. Within-one-line accuracy was 36.5% / 85.4%,
  with median/P95 line error 2/4 and 1/2 respectively. Both remain; neither proves repeatability.
- Compact/hybrid evidence: `diagnostics-local/2026-09-06/compact-face/` and `hybrid-shadow/`.
  Compact SM held-out vertical median/max: 1.75/2.22, 0.83/2.09 lines; A56: 2.89/4.76,
  3.31/6.80, 4.68/6.45. Faster candidate FPS does not erase these errors.
- E-074: `docs/gaze-end-pose-translation-screen.md`, derived JSON, analyzer and tests below;
  13 calibrations, 273 baseline prediction pairs reproduced exactly; mixed held-out changes.
- Historical verification, not rerun by this documentation task: compact checkpoint reports
  80 gaze-focused JVM tests; E-074 reports 27 offline analysis tests. Full Android unit suite
  has the previously reported unrelated `FirebaseTest.createAccount` failure.
- Local Java 17 exists at `C:\Program Files\Java\jdk-17`; the older documented JBR 17 path
  also exists. Avoid stale JDK 21 Kotlin daemons; use a consistent Java 17/in-process build.
  ADB: `C:\Users\USER\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

### Dirty work and git boundary

At handoff preparation, no staged changes were present. Pre-existing tracked modifications:
`docs/calibration-design.md`, `docs/gaze-accuracy-handoff.md`, `docs/gaze-diagnostics-log.md`.
Pre-existing untracked paths:

- `docs/gaze-end-pose-translation-screen.md`
- `docs/gaze-end-pose-translation-screen-2026-09-06.json`
- `tools/gaze/screen_end_pose_translation.py`
- `tools/gaze/test_screen_end_pose_translation.py`
- `diagnostics-local/`, `tmp/`, `tools/gaze/__pycache__/`

This handoff adds this file and updates the three already-dirty documents plus a supersession
notice in `docs/gaze-model-replacement.md`. It does not erase the E-074 changes. Recheck status
on arrival; do not treat this snapshot as a clean checkout or migrate only committed files and
lose the new handoff. The user stages/commits themselves and wants Windows CMD suggestions,
not PowerShell, when requested. Never use a broad `git add .` that sweeps private data into git.

## Source and context reading order

After this brief, read E-038/E-043/E-045/E-053-E-056/E-070-E-075 as needed in
`gaze-diagnostics-log.md`; calibration design sections 0, 3-7; then `build-context.md` and
`gaze-model-replacement.md` with their historical-status qualifications. The actual thesis is
`Manuscript/Thesis Paper.pdf`: sections 4.2-4.4 (PDF pages 43-61 contain key constraints).
It targets adults 45+, uses line-level measurement, and prohibits retained camera frames.
Use `manuscript-implementation-alignment.md` for the circular gaze/RSI contamination warning.

Primary upstream sources inspected during the reassessment:

- [GazeFollower release/readme](https://github.com/GanchengZhu/GazeFollower): public/larger weights distinction and project terms.
- [Public base model](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/res/model_weights/base.mnn).
- [MGazeNet estimator](https://raw.githubusercontent.com/GanchengZhu/GazeFollower/main/gazefollower/gaze_estimator/MGazeNetGazeEstimator.py): face 224x224, eyes 112x112, 12 rectangle values; inspect exact input/output contract.
- [Reference calibration](https://raw.githubusercontent.com/GanchengZhu/GazeFollower/main/gazefollower/calibration/SVRCalibration.py).
- [MediaPipe alignment](https://raw.githubusercontent.com/GanchengZhu/GazeFollower/main/gazefollower/face_alignment/MediaPipeFaceAlignment.py) and [BlazeFace alternative](https://raw.githubusercontent.com/GanchengZhu/GazeFollower/main/gazefollower/face_alignment/BlazeFaceAlignment.py).
- [Smartphone model research/code](https://github.com/GanchengZhu/PhoneRealTimeGazeEstimation) and [paper](https://onlinelibrary.wiley.com/doi/full/10.1155/2024/2644725): Android speed and calibration experiments, not our phone results.
- [MNN runtime](https://github.com/alibaba/MNN).
- [MediaPipe pose/landmarker capabilities](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker): optional face transforms do not themselves establish gaze compensation accuracy.

No claim of a best possible model, expected success probability, or population accuracy is
justified yet. The decision is which bounded experiment has the strongest next-step rationale.
