# SM-A025F gaze performance: Stages 1–2 and thesis limitations

**Date:** 2026-11-13  
**Status:** Stages 1–2 implemented and software-checked; SM-A025F device comparison pending. This is not a validated study threshold or a production thread-selection decision.

## Implementation checkpoint

The implementation now:

- reports constant-memory event-time windows with separate face-feature,
  invalid-crop and no-face denominators;
- separates processed completion rate from main-callback result rate;
- reports analyzer arrivals, early admission drops, analyzer-to-worker timing and
  bounded capture-to-main-delivery mean/max age;
- reserves one of two permits before bitmap copy/localization and retains it
  through main delivery, preventing unbounded admitted work;
- keeps FIFO analyzer→worker→main ordering and returns image, preprocessor and
  permit ownership on normal, stopped and error paths;
- makes MNN CPU thread count injectable for diagnostics while production stays
  at four threads;
- prevents a non-default diagnostic runtime from loading, fitting or saving the
  production calibration;
- provides an opt-in, camera-free 1/2/4-thread native sweep with finite-output
  and `1e-5` agreement checks but no hardware speed assertion.

Focused MGazeNet JVM tests and debug Android-test Kotlin compilation pass. The
opt-in native sweep and live SM-A025F comparison have not been run, so no FPS,
latency, thermal or accuracy improvement is claimed.

## Question and current evidence

The available study phone is a Samsung SM-A025F. In the current article pipeline,
the displayed callback rate is about 11 results/s when no face is detected and
about 5–6 results/s when a face is continuously present. These values are
tracker-result cadence, not screen refresh rate, camera capture FPS, valid-gaze
FPS, or gaze accuracy.

Read-only `MGazeNetPerf` windows from the installed build show why the rates
differ:

- no-face windows complete roughly 21–22 results per approximately two-second
  logging window; gaze-model inference is skipped;
- representative all-face windows complete roughly 11 results per window;
- representative face-present stage means are approximately 38–40 ms copy,
  66–67 ms localization, 8 ms preprocessing, 190–194 ms MNN inference, and
  125–128 ms worker queue delay;
- camera arrivals continue while work is pending, producing bounded busy drops.

These are observations from one phone, build, calibration and operating period.
They identify a device/pipeline bottleneck but do not estimate population
performance, spatial error, reading-event validity, or intervention benefit.

### Numeric evidence receipt

The baseline was read from device Logcat before the Stage 1/2 code change. No
camera image or gaze coordinate was retained.

- Device: `SM-A025F`; physical display reported by Android: `720x1600`.
- Installed package: `com.newsmead`, version code `1`, version name `0.2`, last
  update `2026-11-13 22:42:49`.
- Installed APK SHA-256:
  `ac31bf23881624a2bac9a3aef9590ddc14e21d5b115992e5ffd726c872ad15f4`.
- Camera request log: `ae_target_fps_request=[30, 30]`; this is a request, not
  delivered camera FPS.

Representative unmodified `MGazeNetPerf` lines:

```text
samples=11 face=11 arrivals=739 drops=52 avg_ms copy=38.1 queue=131.4 localizer=65.5 preprocess=8.5 inference=192.3 svr=1.7 total=438.0
samples=11 face=11 arrivals=759 drops=61 avg_ms copy=38.5 queue=139.6 localizer=67.7 preprocess=7.7 inference=192.0 svr=1.7 total=447.7
samples=22 face=0 arrivals=2627 drops=215 avg_ms copy=31.9 queue=0.2 localizer=60.3 preprocess=0.0 inference=0.0 svr=0.0 total=92.5
```

The old logger divided each stage sum by all completed samples in its window.
The all-face examples therefore support their face-present stage means; mixed
windows do not support a face-only inference mean and are not used for that
claim. The roughly two-second logging cadence was completion-triggered rather
than an exact capture cohort, which is why Stage 1 records actual window time.

## Protected behavior and exclusions

Stages 1 and 2 must not change:

- the pinned model asset or model input/output shapes;
- face/eye crop geometry, mirroring, normalization or coordinate systems;
- SVR calibration parameters, calibration bundle format or accepted calibration;
- article smoothing, blink rejection, scanpath rules or scaffold policy;
- recording schemas or frozen validation protocols except for additive,
  explicitly versioned performance provenance;
- the production four-thread default until device evidence supports a change.

The existing gaze-dot/FPS setting is separate uncommitted work and must not be
rewritten as part of the performance experiment.

## Stage 1 — interpretable constant-memory observability

### Implementation

Refine the existing two-second performance summary so mixed windows cannot make
face inference appear cheaper merely because no-face results contribute zeros.
Keep only counters, sums and bounded extrema or quantile estimators—no per-frame
arrays and no synchronous file writes.

Report at least:

- actual window duration and completed-result rate;
- camera arrivals and cumulative/delta busy drops;
- face-present, no-face and invalid-crop result counts and rates;
- copy/localization timing for all applicable frames;
- preprocessing, MNN inference, SVR and analyzer-to-worker completion time for
  face-present frames using face-present denominators;
- capture-to-main-delivery age as bounded mean/max statistics, explicitly not a
  full percentile distribution;
- worker queue delay for submitted inference work;
- selected MNN thread count and runtime/backend provenance;
- source feature-result rate separately from no-face/invalid-crop rates. A
  provider-valid-coordinate cadence is not established by this source logger
  and must be derived separately from provider observations.

The log must preserve units and denominators. A mixed result rate must never be
called camera FPS, display FPS, or valid gaze FPS.

### Checks

- Pure tests with synthetic face/no-face/mixed records verify denominators,
  window reset behavior, finite-value handling and zero-face reporting.
- Existing focused MGazeNet JVM/native tests remain green.
- A debug build compiles without modifying model or calibration assets.

Software tests establish accounting and control-flow behavior only; they do not
establish phone throughput or human gaze validity.

## Stage 2 — bounded scheduling and thread experiments

### Thread-count injection

Allow `MnnEstimator` to accept an explicit diagnostic thread count while keeping
the production default at four. Runtime metadata must report the effective value.
An on-device native fixture should compare 1-, 2- and 4-thread sessions using
identical finite tensors and report timings without asserting a hardware speed
threshold in the test suite. This is an isolated synthetic-input sweep, not a
calibrated live-gaze comparison.

Candidate outputs must be compared with the four-thread reference. Any material
numeric change becomes a new runtime identity and cannot silently inherit the
current calibration.

### Early admission control

Before bitmap copy and face localization, check whether the bounded
preprocessing/inference pipeline has capacity. When both slots are occupied,
discard the new camera frame immediately and increment an explicit early-busy
counter.

This change is intended to reduce wasted work, thermal load and result age. It
does not make the MNN model intrinsically faster and therefore must not be
presented as a guaranteed FPS increase.

Requirements:

- memory remains bounded;
- no unbounded executor queue is introduced;
- accepted result timestamps remain monotonic;
- every acquired image and preprocessing slot is released exactly once;
- early-busy drops are distinguishable from camera arrivals and later pipeline
  rejection;
- a newest-frame replacement policy is deferred unless resource cleanup,
  ordering and timestamp behavior can be tested directly.

### On-phone comparison

The live on-phone comparison in Stages 1–2 is limited to the unchanged production
CPU4 runtime before and after the admission/observability change. Use the same
article, orientation, calibration, lighting, approximate distance, thermal
starting condition and diagnostic state. Retain all attempts, including failures.

Non-default thread counts cannot load, fit or save production calibration in
this implementation. A 1/2/4-thread result therefore establishes only isolated
finite-output agreement and timing potential. Any later calibrated live
comparison requires a separately identified runtime/calibration path and a fresh
calibration; it must not reuse the CPU4 calibration.

For each candidate, collect three bounded face-present runs and matched no-face
windows. Report:

- processed-result cadence and separately derived provider-valid-coordinate
  cadence, with their different denominators preserved;
- bounded capture-to-delivery age summaries, not only a mean FPS; percentile
  distributions remain an explicit follow-up measurement;
- camera arrivals, early-busy drops, other drops and invalid reasons;
- face-present stage timings and thermal status;
- run duration and any initialization/warm-up exclusion.

An exploratory engineering target is at least 8 sustained processed results/s
in every face-present run, no hang or unbounded age growth, and no fixed-fixture
numeric regression. This is not a validated reading-measurement threshold. The
selected candidate still requires the existing held-out spatial check before it
can be used for thesis data collection.

## Decision gates

1. **Accounting gate:** Stage 1 tests and source review confirm correct units and
   denominators.
2. **Scheduling gate:** early admission reduces wasted pipeline work without
   ordering, cleanup or model-output changes.
3. **Thread gate:** Stage 2 may identify a promising non-default thread count
   from the isolated sweep, but cannot select it for production. Promotion needs
   a separately identified runtime, fresh calibration and replicated live
   accuracy/performance evidence.
4. **Measurement gate:** do not trade spatial accuracy, missingness, tail latency
   or thermal stability for a higher headline FPS.
5. **Escalation gate:** if the SM-A025F remains below the exploratory target,
   stop tuning scheduling and open a separately identified FP16/INT8 experiment.
   A changed precision/backend requires new identity, calibration and held-out
   validation.

## What this exposes about the thesis

### Critical validity limitations

1. **Hardware-dependent feasibility.** Real-time behavior is not an app-only
   property. The same pipeline previously ran substantially faster on other
   phones, while the SM-A025F shows a face-present inference bottleneck. A thesis
   claim about a generally deployable mobile gaze aid would therefore exceed the
   observed device evidence.

2. **Informative missingness and variable cadence.** Missing or delayed gaze is
   associated with face visibility and compute load, not random sampling.
   Fixation, dwell, regression and loss-of-place evidence can be distorted when
   observation opportunity changes with posture, lighting, device load or face
   presence. Aggregate FPS cannot repair or characterize that bias.

3. **Sampling limits event observability.** At roughly 5–6 processed results/s,
   short events may be missed, merged or timestamped too coarsely for rules
   designed around faster cadence. Tracking eligibility is not accuracy, and a
   software threshold does not validate an inferred human reading event.

4. **Circular measurement risk.** The same gaze stream is used to infer reading
   instability and position the aid. Spatial or temporal errors can both create
   apparent instability and misplace the response. A visible aid or favorable
   user impression cannot independently validate the detector.

### Important scope limitations

- One researcher-operated SM-A025F cannot establish performance or usability in
  older adults, other low-cost phones, different lighting, or natural handheld
  postures.
- Tuning and evaluating on the same phone/session risks device-specific
  overfitting. The selected configuration needs a frozen, later confirmation
  run; tuning attempts must not be reported as independent validation.
- Calibration success does not establish held-out line/word accuracy, temporal
  validity, or reading-event validity.
- Thermal state, background load and exposure behavior are plausible
  confounders. They must be recorded rather than attributed to the code change
  after one favorable run.
- A throughput improvement cannot compensate for worse regional error,
  missingness, drift or output age.

### Defensible thesis framing if the constraint remains

The thesis can still support a bounded claim: NewsMead is an engineering
prototype that investigates gaze-driven reading support under an explicitly
identified phone, model, calibration and operating configuration. It should not
claim universal low-end-phone feasibility, clinically meaningful gaze accuracy,
or improved older-adult reading from engineering and single-device evidence.

If the SM-A025F cannot deliver adequate observation coverage after the bounded
experiment, that is a study limitation and feasibility result—not a result to
hide by lowering denominators or selecting only favorable intervals. Possible
responses are to narrow the supported measurements to events observable at the
measured cadence, narrow the thesis claim to prototype feasibility, or identify
hardware/runtime requirements prospectively.

## Evidence and references

- `docs/agent-context.md` — current research and implementation checkpoint.
- `docs/gaze-mgazenet-inference-profile.md` — earlier bounded inference profiling
  and limits of transferring performance claims.
- `docs/gaze-mgazenet-accuracy-readiness.md` — accuracy, calibration and
  measurement-validity gates.
- `docs/study-data-framework.md` — cadence, missingness and denominator rules.
- `docs/thesis-memory.md` — existing circular measurement and scope limitations.
- Kassis, T., Agarwal, V., He, Y., Patel, D., & Brueckner, A. M. (2026).
  *Scientific Agent Skills: A Library of Procedural Knowledge for Research
  Agents*. arXiv:2609.00065. https://doi.org/10.48550/arXiv.2609.00065

