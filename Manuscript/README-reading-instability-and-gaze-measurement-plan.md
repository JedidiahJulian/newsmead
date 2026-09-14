# Reading instability and gaze measurement: manuscript research plan

**Status:** research and revision guide, 2026-09-14. This named README is the current
source of truth for *what the manuscript should say* about reading instability,
gaze measurements, and the evidence still needed. [main.pdf](main.pdf) is the
pre-revision thesis baseline. The [older revision packet](manuscript-revision-packet.md)
is retained as a draft, but its section map and some implementation descriptions
predate the current PDF and MGazeNet work. Neither index has yet been validated as
a direct measure of comprehension, cognitive load, or a clinical condition.

## The argument the thesis needs to make

NewsMead observes where its front-camera gaze pipeline estimates that a reader is
looking on a phone article. From that estimate it derives text-line visits and
backward line transitions. **Reading instability** is the *hypothesized* pattern
in which a reader's progression through the text is disrupted and reorientation
or rereading may be needed. It is not a state that the camera directly observes.
The same gaze pattern can reflect careful reading, difficult wording, a deliberate
review, distraction, a change in posture, or tracker error. Research with older
readers finds more and longer fixations and more regressions even when
comprehension is maintained [S1]. Scanpath structure also varies with age, word
length, and sentence properties [S2]. Thus, no single raw movement, and no
unvalidated sum of movements, proves that a reader is struggling.

The manuscript needs **two different summaries** of the *same provisional online
line-event stream*:

| Measure | Question it answers | Horizon and role | What it cannot establish |
| --- | --- | --- | --- |
| **Session Reading State Index (Session RSI)** | How much of the article session's observed behavior matches the selected line-event pattern? | Cumulative component totals, rescaled 0–100; descriptive research variable. | The current moment of difficulty, recovery onset, comprehension, or a diagnosis. |
| **Adaptive Instability Index (AII)** | How far do recent event rates depart upward from this reader's baseline? | Recent roughly 10-second window, smoothed 0–4; *candidate* controller input in adaptive mode. | A probability of difficulty, an exact number of standard deviations, or independently verified need for a particular scaffold. |

The two scores are related but not interchangeable. Session RSI is based on
article-to-date totals and is not participant-baseline-relative. AII uses changes
over a recent window against a baseline and can fall after recent event rates
subside. Session RSI is called *cumulative* because its inputs cover the article
to date; the displayed ratio can still move **down** as elapsed time or lines
reached increase. The online implementation does not require all three
components to rise together. The claim in PDF §3.5 that genuine instability
*necessarily* produces a concerted rise across all three is therefore both
empirically unsupported and stronger than the implemented rule.

### Why these particular gaze measurements?

The research rationale concerns **candidate observable behaviors**, not
validated NewsMead detectors. In high-quality eye-tracking studies, longer
looking times and backward reading movements often vary with linguistic
difficulty, reader characteristics, or task demands [S1, S2, S3]. Gaze-based
support has also been feasible in a controlled low-vision reading task [S4].
Those experiments differ from NewsMead's phone, age range, text layout, and
camera signal. They justify collecting and testing measures; they supply **no
NewsMead-specific weight, cutoff, or line/word accuracy guarantee**.

| Signal | Current online operational definition | Why inspect it | Competing explanations / measurement risk |
| --- | --- | --- | --- |
| **Confirmed line regression** | One backward transition from the highest line reached to an earlier line, held for at least 300 ms; one event per episode. | May reveal revisiting earlier material or reorientation. | Deliberate checking, scrolling/layout changes, or a noisy line jump can look similar. It is **not** a word-level regression or a measured saccade. |
| **Line dwell time** | Time spent on a mapped line visit lasting at least 120 ms, accumulated when the visit ends or the stream is flushed. | May describe hesitation or sustained processing in one area. | Sentence length, careful reading, and delayed event completion affect it. The online inferencer does not currently terminate a visit on unavailable/off-text samples, so a gap can be included in its elapsed time. It is not direct attention or comprehension time. |
| **Online “fixation” event** | At most one event for a continuous line visit once it lasts at least 300 ms. The indices use **event count/rate**, not eye-fixation duration. | A frequent pattern of line-stay events may flag changed progression for inspection. | This is *not* a laboratory-defined ocular fixation. A visit can produce both dwell and a “fixation” event, so their contributions are correlated; repeated true fixations within one line are not resolved. |
| **Off-text / return** | Estimated gaze leaves valid article text, then returns to a displaced line. | Candidate evidence for re-entry support. | Looking at a control, a blink, tracking loss, or an erroneous coordinate can mimic losing one's place. |

The thesis presently switches between **fixation duration**, **fixation count**,
and **fixation rate** as if they were the same measurement. They are not. The
current online indices use the *rate of line-visit events*. The separate
[natural-reading pilot](../docs/reading-measurement-pilot.md) estimates durations
using a provisional dispersion detector and can produce word-visit measures;
those outputs do **not** currently feed AII or Session RSI. Fixation detection
method can materially change downstream results [S5]. The manuscript should
call the online events *line-stay events* on first definition, retain the code's
“fixation” label only when describing the implementation, and reserve *eye
fixation duration* for the separately validated offline measure.
The online article path timestamps a gaze delivery with the UI clock; the
natural-reading recording retains camera **capture** and delivery timestamps
separately and uses capture time for offline duration estimates. The two
measurement paths must not be treated as temporally identical.

## Exact implemented indices and what the numbers mean

### Session RSI — article-to-date description

[`ReadingStateInferencer`](../app/src/main/java/com/newsmead/gaze/ReadingStateInferencer.kt)
receives mapped lines and maintains counts. At each update it computes:

```text
R = clamp(confirmed regressions / max(1, highest line reached + 1), 0, 1)
D = clamp(total completed line-dwell ms / elapsed article-reading ms, 0, 1)
F = clamp((line-stay events / elapsed minutes) / 120 events/min, 0, 1)
Session RSI = 100 × (0.40R + 0.35D + 0.25F)
```

The 120-events/min reference, the 120 ms dwell rule, the 300 ms line-stay rule,
and the weights are **implementation choices**. A conventional figure for
word-level fixations per minute cannot validate a 120/min denominator for a
detector that emits at most one event per continuous *line* visit. The
regression denominator is the highest line index reached plus one; it is **not**
the number of distinct lines read or an independent count of reading
opportunities. `D` accumulates completed line visits, so its value can lag a
long current visit. All three components depend on the same estimated line
stream. `R`, `D`, and `F` should be reported with their raw numerators,
denominators, session duration, and gaze quality rather than interpreting a
single 0–100 score alone.

The literature does not establish that regressions are universally more
diagnostic than dwell, or that dwell is universally more diagnostic than this
app's line-stay count. The order **0.40 > 0.35 > 0.25** is a transparent
starting hypothesis chosen by the team. It must be assessed against separately
observed behavior and with sensitivity analyses. A high Session RSI can result
from ordinary slow or deliberate reading, and a low score can result from
missing or inaccurate gaze data. It must not be used as a comprehension score.

### AII — recent departure from a baseline

[`WindowedStabilityEstimator`](../app/src/main/java/com/newsmead/gaze/WindowedStabilityEstimator.kt)
takes differences of the online cumulative counters over a roughly 10-second
window and updates about every 500 ms, after at least 2 seconds of metric span:

```text
xR = recent confirmed regressions per minute
xD = recent completed line-dwell time / recent elapsed time
xF = recent line-stay events per minute
z+i = max(0, (xi − baseline mean_i) / max(baseline SD_i, SD floor_i))
AIIraw = clamp(0.40z+R + 0.35z+D + 0.25z+F, 0, 4)
AII(t) = AII(previous) + [1 − exp(−Δt / 2 s)] × [AIIraw − AII(previous)]
```

The SD floors are 1 regression/min, 0.05 dwell proportion, and 5 line-stay
events/min. They prevent a near-zero estimated SD from creating an extreme
index, but when a floor binds, AII is no longer scaled by the measured
variability alone. The inputs are positive-part standardized deviations;
their weighted, correlated, clipped, capped, and smoothed combination is **not
a standard z-score**. An AII of 2 does not mean “exactly two standard
deviations above baseline.” The 10-second window, floors, 2-second smoothing,
weights, and controller thresholds need empirical evaluation [S6].

The thesis plans a standardized, unassisted baseline passage, frozen once per
participant and reused across conditions. **That cross-article profile is not
the current default behavior.** Unless supplied a baseline profile, the app
collects overlapping windows during the first 20 seconds *of each article*.
Those overlapping updates are autocorrelated, and their count alone does not
prove baseline reliability. The README and manuscript must distinguish this
prototype fallback from the study protocol. There is no basis yet to describe
the resulting AII as a validated personal difficulty detector [S6].

In **ADAPTIVE** mode, the controller proposes NONE, WORD, LINE, FOCUS, or
REENTRY at initial AII boundaries 1.0, 1.5, 2.0, and 2.5. It also requires a
ready baseline, at least 65% recent gaze coordinates landing on visible text,
1.5 seconds of persistence before escalation, and 3.5 seconds before
withdrawal. REENTRY additionally needs the prototype's off-text and displaced
return evidence. These settings are listed for reproducibility, **not** as
validated distinctions between mild and severe instability. On-text coverage
is an availability gate, not proof that the correct line was identified. The
current build sets `StudyConfig.SCAFFOLD_MODE = DEMO_CYCLE`, which displays the
levels on a timer for a presentation; visible demo transitions are not AII
decisions. Natural-reading recording hides the scaffolds and does not feed the
old index/controller, but retains the orange diagnostic gaze dot. Its records
are therefore identified as diagnostic-dot recordings rather than an
unassisted study condition.

**Interpretation example:** A participant may read a complex paragraph slowly
and return to one line intentionally. The online components could rise without
a loss of reading position. A second participant might make several backward
line visits after an interruption; recent rates could raise AII even while the
article-to-date Session RSI changes little. A tracker that shifts estimates by
one line can create the same apparent events. Only independent observations
and spatial checks can distinguish these cases. These are illustrations, not
observed study results.

## Gaze estimation and validity of the measurements

The **current** study path is CameraX front-camera frames → MediaPipe Tasks
FaceLandmarker for face/eye localization → MGazeNet appearance model →
participant calibration with 13 fit targets (45 accepted samples per target)
and per-axis SVR mapping → physical-screen gaze coordinates through
`GazeProvider` → article text geometry and line/word AOI → online line events →
Session RSI/AII and the scaffold controller. MediaPipe is still used for
localization; **MGazeNet is the gaze estimator**. The 16-point polynomial
MediaPipe-Iris tracker described as selected in PDF §4.3.2 is historical, not
the current estimator. A separate nine-point held-out accuracy task and
reading-target validation tasks must be identified as measurements rather
than calibration-fit accuracy.

The spatial validity question is *task-specific*: can the stream reliably
distinguish the exact line or word needed for an event or scaffold, over the
actual font, scrolling, posture, and age range? An earlier stationary MGazeNet
pilot with four sessions gave **14.8–33.5% exact-line** and **56.5–85.3%
within-one-line** assignment, with substantial tail errors. The **older
16-point tracker** had a separate first valid known-target reading run reporting
**40.5% guided exact-line** and **10.9% exact-word** accuracy; subsequent
vertical-alignment controls did not establish a reliable correction for that
older path. These protocols, estimators, sessions, and versions must not be
pooled or presented as current MGazeNet reading performance. At the time of
this plan, the repo contains a MGazeNet-backed known-target reading instrument,
but no documented current MGazeNet reading result establishing word accuracy.
See the [MGazeNet stationary pilot](../docs/gaze-mgazenet-stationary-pilot-results.md),
[historical reading-validation protocol](../docs/reading-validation-protocol.md),
and [progress notes](../docs/progress-notes.md) for original denominators and
timing. Work on smartphone tracking shows that reading-related associations
are possible [S7], but accuracy, precision, and valid-data rate must be
checked in the *actual* setup [S8].

| Quality dimension | Required report | Why it matters to AII, Session RSI, or scaffolds |
| --- | --- | --- |
| Spatial accuracy | Signed X/Y bias, median/P95 2-D and vertical error in pixels and line heights; exact/adjacent line and exact/adjacent word assignment at held-out targets. | A line error can create a false regression; a word error can place a scaffold incorrectly. |
| Precision and jumps | Within-target dispersion and rate/magnitude of implausible jumps, by screen region. | High jitter can inflate line-stay counts and backward transitions. |
| Availability and timing | Capture-time gaps, usable sample rate, output age/latency, dropped or stale results, off-text fraction. | A displayed FPS does not establish timely usable event samples; missing spans may hide visits or shift event timing. |
| Drift and movement | Repeat held-out targets before/after reading, after scrolling, and under recorded posture changes. | A stable-looking but displaced estimate can be wrong for an entire block. |
| Event validity | Agreement with independently labeled line/word visits, false regressions, missed revisits, and detector-parameter sensitivity. | Spatially plausible coordinates alone do not validate the *derived* reading measurements. |

Accuracy targets should be independent of the points used to fit calibration.
Report participant/session-level distributions, not only pooled frames, and
keep tracker-error intervals identifiable. The same estimated gaze stream
drives both AII and scaffold placement, so an error can simultaneously produce
a false trigger and an incorrectly placed intervention. A controller transition
is not independent evidence that reading instability occurred.

The [natural-reading pilot](../docs/reading-measurement-pilot.md) writes
capture-timestamped raw and display coordinates plus article geometry. Its
offline provisional I-DT analysis uses 0.5 line-height maximum dispersion,
100 ms minimum observed duration, at least 3 samples, at most 125 ms between
samples, and 80% word-assignment agreement. It reports detected fixation
duration, observed first-pass gaze duration, rereading proportion, backward
word-visit probability, and first-pass skipping rate with explicit eligible
denominators and censoring. These are **candidate research measures**, not
validated physiological events, proof that a skipped word was unread, or an
implemented replacement AII. Do not merge prompted known-target recordings
with natural reading or treat watching the diagnostic dot as ground truth.

## Source-to-claim map

These are original research studies and a reporting guideline for the
manuscript argument. The study methods and populations must be described when
used; none supplies NewsMead's exact formula or cutoffs. Numerical reading
norms cited in `main.pdf` must be checked
against their original papers and must not become universal thresholds for
this different detector.

| ID | Source | Supports | Does not establish |
| --- | --- | --- | --- |
| S1 | Warrington et al., [*Effects of Aging, Word Frequency, and Text Stimulus Quality on Reading Across the Adult Lifespan*](https://pmc.ncbi.nlm.nih.gov/articles/PMC6233613/) (2018) | Age and text properties change eye-movement behavior; older readers can have longer/more fixations and regressions while maintaining comprehension. | That any NewsMead event denotes difficulty, or that all adults 45+ share one baseline. |
| S2 | von der Malsburg, Kliegl & Vasishth, [*Determinants of Scanpath Regularity in Reading*](https://doi.org/10.1111/cogs.12208) (2015) | Reading paths vary with age, word length, and sentence properties. | A live phone AII or a validated three-feature weighting. |
| S3 | Mézière et al., [*Using Eye-Tracking Measures to Predict Reading Comprehension*](https://doi.org/10.1002/rrq.498) (2023) | Gaze features can be evaluated against independent comprehension outcomes; task demands affect associations. | That gaze alone directly measures an individual's momentary comprehension. |
| S4 | Wang et al., [*GazePrompt: Enhancing Low Vision People's Reading Experience with Gaze-Aware Augmentations*](https://doi.org/10.1145/3613904.3642878) (2024) | Task-specific gaze support can help in controlled low-vision reading, and trigger behavior needs user evaluation. | Cutoffs or benefit for NewsMead's middle-aged/older news readers. |
| S5 | Salvucci & Goldberg, [*Identifying Fixations and Saccades in Eye-Tracking Protocols*](https://doi.org/10.1145/355017.355028) (2000) | Event-detection choices influence higher-level analysis. | Validity of NewsMead's line-visit rule or provisional I-DT settings. |
| S6 | Staub, [*How Reliable Are Individual Differences in Eye Movements in Reading?*](https://doi.org/10.1016/j.jml.2020.104190) (2021) | Some simple individual-difference estimates have poor split-half reliability; baseline reliability must be checked. | That a 20-second, overlapping-window phone baseline is sufficient. |
| S7 | Valliappan et al., [*Accelerating Eye Movement Research via Accurate and Affordable Smartphone Eye Tracking*](https://www.nature.com/articles/s41467-020-18360-5) (2020) | Smartphone gaze can be studied during reading with independent outcomes. | Transferable line/word accuracy for NewsMead's model and device. |
| S8 | Nyström et al., [*The Influence of Calibration Method and Eye Physiology on Eyetracking Data Quality*](https://doi.org/10.3758/s13428-012-0247-4) (2013) | Accuracy, precision, and valid-data proportion should be measured separately. | A calibration procedure or quality threshold validated for this phone. |
| S9 | Angele et al., [*How Low Can You Go? Tracking Eye Movements During Reading at Different Sampling Rates*](https://doi.org/10.3758/s13428-025-02713-3) (2025) | Sampling rate affects timing precision and power for reading measures; report the actual usable stream and event sensitivity. | That downsampling a precise laboratory tracker validates NewsMead's noisier approximately 24-FPS front-camera stream. |
| S10 | Dunn et al., [*Minimal Reporting Guideline for Research Involving Eye Tracking*](https://doi.org/10.3758/s13428-023-02187-1) (2023 edition) | A structured reporting checklist for device, calibration, data quality, event processing, and exclusions. | Any task-specific accuracy pass criterion or validated instability index. |

## Detailed gaze-measurement execution plan

This is the operational plan for the **next evidence**, not a description of
data already collected. Complete each measurement layer before using its
output to justify the next inference. The [natural-reading recording
specification](../docs/reading-measurement-pilot.md) defines the current JSONL
and offline replay; this section defines how to *validate and use* them. The
[older reading-validation protocol](../docs/reading-validation-protocol.md)
contains 16-point calibration and vertical-correction instructions from the
previous tracker. For the current branch, the code in
[`ReadingValidationActivity`](../app/src/main/java/com/newsmead/activities/ReadingValidationActivity.kt)
and the 13-target MGazeNet calibration contract take precedence. Ordinary
current runs use alignment **OFF**; no rejected historical ON correction is
silently promoted.

### 0. Freeze the question and inventory the evidence

Answer four distinct questions in order: **(a)** where does the gaze output
land, **(b)** what reading events can be recovered from it, **(c)** whether
those events covary with independently observed difficulty or recovery, and
**(d)** whether acting on them helps. A good result for one question does not
answer the next. Prepare a manifest with one row per run, recording:

| Field group | Required entries |
| --- | --- |
| Identity | An anonymous participant/session code, date, target age band, device model, app build/commit, MGazeNet calibration identity and hash, test mode. |
| Reading surface | Article/text hash, language, font size, physical line height, viewport and screen coordinate frame, orientation, scrolling and layout changes. |
| Context | Lighting, habitual visual correction, approximate phone distance/posture, whether movement was deliberately requested, and whether the diagnostic dot or scaffolds were visible. |
| Data provenance | Calibration-fit rows, nine-point check, prompted reading targets, or natural-reading JSONL; raw versus smoothed coordinates; capture versus delivery clock; file completeness and record loss. |

Inventory the four stationary MGazeNet sessions, all later tracker/reading
tests, and any actual natural-reading recordings **without pooling different
estimators or task types**. The documented older 40.5% line/10.9% word run is
not a MGazeNet result. The 2026-09-13 recording implementation reports that
its next step is to inspect a first real file; until such a file is analyzed,
natural-reading measurement quality is unknown. An oral report that the dot
tracks a moving reader or individual words is a useful observation to test,
not an accuracy result. Save a dated evidence table identifying observed,
missing, and incompatible records.

### 1. Verify collection and timing before interpreting a reading measure

Make one short **diagnostic plumbing run** with a known deliberate reread,
one scroll, and a brief look away, as the recording specification instructs.
This intentionally scripted run tests whether the file records the expected
events; it is not a positive example of spontaneous reading instability.
Confirm that `session_start`, geometry changes, samples, and a complete
`session_end` are present, and that the calibration/article hashes and
coordinate spaces match. Preserve the raw JSONL and the offline analysis
output together with their exact software version. Incomplete files remain
diagnostic and do not enter ordinary reading summaries.

For every run, report the distribution (median, P95, maximum) of capture-time
sample intervals, capture-to-delivery age, valid/invalid spans, duplicate or
out-of-order timestamps, queue drops, and geometry-settling exclusions.
Separate camera/analyzer availability from coordinates that merely fall
somewhere on the article. About 24 face-present article outputs per second
have been observed on one A56 build, but that is a throughput observation,
not proof that this run supplied 24 usable, correctly located samples each
second. A duration spanning missing samples must remain censored rather than
filled in. The lower-rate reading study [S9] motivates checking temporal
sensitivity; its simulations assumed much better spatial precision than this
phone has yet demonstrated.

### 2. Measure current MGazeNet spatial accuracy on independent targets

Use a fresh, compatible **13-fit-target MGazeNet calibration** for each test
session. Run the existing nine-point screen check: eight positions are held
out from the fit and the centre is a repeat. Preserve the per-target samples,
not only the screen summary; the centre repeat measures drift or repeatability,
not independent generalization. Then use the current MGazeNet-backed reading
validation activity with eight highlighted word targets across the viewport
and six guided physical-line trials. Its orange-dot preview is unscored; the
dot and control instructions are hidden during measured targets. The line
task supports a known *line* label throughout the trial; it has no independent
timestamp for each word during moving reading, so do not score exact words
from that task. Keep vertical alignment OFF unless a separately declared
correction experiment is being run.

First run one technical smoke session to verify the logging contract. Then
obtain at least **three fresh calibration/test sessions per target phone**,
spanning more than one sitting and alternating the instrument's A/B target
order. Do not discard poor sessions simply because a later calibration looks
better. In the formative participant phase, repeat the same unchanged task
after each participant's calibration, before natural reading, and again after
the reading block when feasible to observe drift. Record ordinary handheld
movement separately from any deliberately requested movement; report both
conditions, not a mixed average. If the present instrument cannot label a
movement condition or produce a needed per-sample field, add that logging in
a separately reviewed implementation step *before* using the condition for
analysis.

For each word trial, calculate raw-sample and per-target-median signed `dx`/
`dy`, radial error, exact and adjacent **physical-line** assignment, exact and
adjacent **word-range** assignment, off-text fraction, dispersion, jump rate,
and availability during the scored window. For each guided-line trial,
calculate exact-line and within-one-line fractions, separately. Express
vertical errors in both pixels and the *actual rendered line height*; retain
horizontal pixels and word-box geometry for word claims. Show top/middle/
bottom and left/centre/right breakdowns, before/after scroll, target order,
and per-calibration variation. Report sample-weighted and target-balanced
results separately, with the target/session/participant as the repeated unit;
thousands of adjacent frames are not thousands of independent participants.
The required output is an accuracy table with numerators, denominators,
median/P95 tails, missingness, and examples of wrong-line transitions.

### 3. Validate event extraction against the measurement it claims

The online AII/Session RSI path needs a **line-event audit**. Reconstruct
line-visit starts/ends, “fixation” emits, dwell accumulation, and confirmed
backward episodes from logged coordinates or a diagnostic replay. Include
cases where a reader stays on one line, rereads a previous line, scrolls,
looks off text, loses gaze, and returns to the same line. In particular, test
whether the live inferencer's continuing line visit bridges an off-text gap
and inflates dwell or the 300 ms line-stay event. Compare raw target,
stabilized target, and emitted event; count false backward episodes caused by
one-line jumps. This exposes whether the current online component names are
defensible before tuning the index.

For the separate natural-reading path, run
`python tools/gaze/analyze_reading_measurement.py recording.jsonl --output analysis.json`
with its logged defaults, then repeat **sensitivity** versions varying the
dispersion and minimum-duration parameters without selecting the one that
best supports the hypothesis. Record event sample count, observed span,
centroid, dispersion, word-assignment agreement, and censor flags. The
following outputs must keep their numerator/denominator or eligible-event
count and not turn missing denominators into zero:

| Offline output | What to calculate and verify | Source of uncertainty |
| --- | --- | --- |
| Mean/median detected fixation duration | Duration among events with both boundaries observed; include count and duration distribution. | Provisional I-DT rule, approximately 24-FPS timing, gaps, and coordinate dispersion. |
| Observed first-pass gaze duration | Sum of fixation time in complete first visits advancing beyond the known word frontier; report eligible visits. | A missed earlier visit or segment start makes “first pass” unknown. |
| Observed rereading proportion | Word-attributed time in later visits / all word-attributed observed fixation time in eligible segments. | A spatially wrong word label or missing span changes both numerator and denominator. |
| Backward word-visit probability | Eligible backward between-word transitions / all eligible between-word transitions. | Word ambiguity, line-wrap order, noise, and genuine deliberate review. |
| First-pass skipping rate | Unfixated intervening words / newly traversed eligible words during observed forward advances. | Peripheral processing and missing fixations; “skipped” never means “unread.” |

Use instructed line/word tasks and a separately time-stamped observer record
to check assignment and transitions. If there is no independent high-quality
eye tracker, the team can validate **operational screen/line/word-event
agreement**, but must not claim that the phone measures physiological fixation
duration with laboratory accuracy. Repeating synthetic analyzer tests checks
software consistency, not this human measurement validity [S5].

### 4. Collect independent evidence of reading difficulty and recovery

The current recording deliberately keeps the orange gaze dot at the user's
request. Retain that behavior for **diagnostic** reading and name the condition
accordingly. A thesis claim about ordinary *unassisted* reading requires a
separate protocol decision: either implement and verify a no-dot study variant
while preserving the requested diagnostic mode, or describe and analyze a
no-scaffold **dot-visible** condition with the dot held identical across
comparison arms. Do not silently call the current recording unassisted.
Whichever condition is chosen should begin at a fresh passage under fixed
article/font settings. Scaffold-demo, prompted-target, and reading-recording
sessions remain separate.

In the formative target-age sample, obtain time-aligned evidence that does not
come from AII or Session RSI: researcher-marked reading interruptions and
observed return to text, participant reports of difficulty or loss of place,
post-passage comprehension questions, and unwanted-support reports. A short
event note must distinguish intentional rereading, unfamiliar wording,
interruption, off-task looking, and tracker failure when these can be
observed. Record the exact prompt/annotation timing because asking about
difficulty can itself change reading; use the same procedure across
conditions. The study may include a separately declared, standardized
interruption to estimate recovery, but scripted trials must be identified as
such and analyzed apart from spontaneous events.

Define recovery *before* the main comparison as an independently marked
disruption time followed by an independently coded return to sustained
forward reading, with a fixed confirmation interval. Pilot whether this can
be coded consistently; if it cannot, revise the outcome definition before
main collection. Keep `t_recovery − t_disruption` separate from the app's
activation-to-NONE controller duration. Neither the drop in AII nor the
withdrawal of a scaffold may serve as its own recovery ground truth.

### 5. Decide whether the current indices are useful

Start with component-level plots and counts for each participant and passage:
confirmed line regressions, completed line dwell, line-stay event rate,
valid-target coverage, spatial error, and gaps. Examine correlation between
line dwell and the line-stay event because the same visit can create both;
also check whether the 120-events/min reference saturates or makes the
Session RSI fixation component negligible. Compare index trajectories with
independently labeled episodes and ordinary careful reading. Report false
alerts, missed episodes, alert timing, and uncertainty by participant, rather
than labeling every high AII interval as a true event. Compare AII with its
individual components; a composite is justified only if it adds useful,
repeatable information.

Assess the intended frozen cross-article baseline using repeated unassisted
passages and report the number of *independent* observations, mean, unfloored
SD, applied floor, repeatability, and variation across passages. The app's
20-second per-article overlapping-window fallback is evaluated as a separate
prototype condition, not renamed as the study baseline [S6]. Evaluate the
current 10-second window, smoothing, 0.40/0.35/0.25 weights, and thresholds
only with formative data; include reasonable component-only and parameter
sensitivity checks, and keep participant/passage separation so a person's
samples do not leak across tuning and evaluation. A 3–5-person formative
sample identifies feasibility and obvious failures; it cannot certify precise
population-wide detection rates. If the evidence is weak, keep the indices
descriptive/exploratory or revise the measurement design rather than calling
the existing formula validated.

### 6. Freeze the method, then test adaptive benefit

Before the approximately 15-person within-subject adaptive-versus-static
study described in `main.pdf`, create one frozen protocol record: software
identity, calibration and baseline procedure, target granularity, validated
event definitions, index calculation, weights/window/smoothing, spatial and
temporal quality rules, scaffold policy, independent episode/recovery labels,
primary outcome, exclusions, and unit of analysis. Choose any numerical
accuracy and false-alert acceptance criteria **before** looking at the final
confirmation data, using the actual line pitch, word boxes, and cost of a
misplaced intervention. Report whether each criterion was met, including
failed calibrations and excluded episodes. Do not borrow a cutoff from a
different tracker or retrofit one to an observed favorable session.

For each condition, retain comprehension, reading speed, participant effort
and acceptability, gaze quality, raw event counts, Session RSI, AII trajectory,
scaffold target and transition, false activation, and independently defined
recovery latency. Counterbalance condition/article order. Report uncertainty
at participant level; multiple episodes from one participant are clustered,
not independent study subjects. If word targeting fails but broader focus
support is credible, narrow the intervention claim accordingly. If event
validity fails, report AII and Session RSI as exploratory tracker-derived
summaries and do not make an effectiveness claim based on their values.

The work produces six reviewable outputs: **(1)** a versioned run manifest,
**(2)** a per-session gaze-quality/known-target report, **(3)** an offline
reading-event report with sensitivity and censoring, **(4)** an independent
episode/recovery label set, **(5)** an AII/Session RSI validation report, and
**(6)** a frozen main-study protocol and manuscript amendment. Follow the
eye-tracking reporting checklist [S10] when preparing the final methods and
results. Keep participant recordings in their authorized private location;
the repo should contain analysis definitions and de-identified summaries,
not camera images or identifiable raw session data. The September 13 progress
note records calibration loss after a connected Android test installation:
avoid connected Gradle tests on the working study app without a verified
recoverable calibration backup.

## Manuscript-ready expansion and correction map

The following prose is a *proposed replacement*, not a claim that the PDF has
already been revised. Cite the source-to-claim map where indicated and update
the final bibliography in the manuscript.

### §3.5 — conceptual definition

> Reading instability is treated in this study as a hypothesized disruption in
> the reader's progression through an article, potentially involving revisiting
> earlier text, extended time in a region, or difficulty re-establishing position.
> These behaviors are observable only through estimated gaze and do not uniquely
> identify confusion, reduced comprehension, or cognitive load. Age, text
> properties, deliberate rereading, and gaze-estimation error can produce similar
> patterns [S1–S3]. NewsMead therefore uses gaze-derived measures as provisional
> indicators to be tested against independent reading observations and outcomes.
> It maintains two related summaries: Session RSI describes the selected
> line-event pattern across the article to date, while AII describes recent
> positive departures from a participant baseline for possible visual support.
> Neither score alone defines successful recovery.

### §3.6 and §4.3.2 — sensing and measurement boundary

> The current on-device implementation uses MediaPipe FaceLandmarker to locate
> facial and eye regions and MGazeNet to estimate gaze from camera images. A
> participant-specific 13-target calibration maps model features to physical
> screen coordinates. The coordinates are associated with the rendered article's
> text geometry before line-event measures are computed. Since line and word
> assignment are downstream of spatial estimation, the accuracy, precision,
> availability, and latency of the *complete* pipeline must be measured at
> held-out reading-surface targets; calibration fit and an on-screen gaze dot do
> not establish event accuracy [S7, S8]. Earlier 16-target MediaPipe-Iris results
> remain historical comparisons, not the selected estimator's performance.

### §4.3.4 — Session Reading State Index

> Session RSI is a 0–100 descriptive index calculated from article-to-date
> confirmed backward line episodes per line reached, completed line-dwell time
> as a fraction of elapsed reading time, and the rate of sustained line-visit
> events relative to a reference rate. Its current combination is
> `100 × (0.40R + 0.35D + 0.25F)`, with each component bounded from 0 to 1.
> The app's sustained line-visit event is operationally distinct from a
> laboratory eye fixation; the third component is an *event rate*, not fixation
> duration. The component weights and reference rate are initial engineering
> hypotheses, and the component events are not independent. Session RSI
> summarizes behavior observed across the article; it is neither a direct
> comprehension measure nor a controller trigger or recovery clock.

### §4.3.5 — Adaptive Instability Index

> AII is a 0–4, smoothed process variable based on the recent regression rate,
> completed line-dwell proportion, and sustained line-visit event rate. Each
> metric's positive departure from its participant baseline is divided by the
> larger of the estimated baseline SD and a specified floor. The three positive
> deviations are combined with provisional weights 0.40, 0.35, and 0.25, capped
> at 4, and exponentially smoothed. AII reflects change in the *measured* line
> pattern, not an estimated probability of difficulty. The current per-article
> baseline fallback differs from the study's planned frozen cross-article
> participant baseline; this distinction must be reported. Window length,
> floors, weights, and scaffold cutoffs will be assessed in a separate formative
> phase, then fixed before the main comparison [S4–S6].

### §1.3.2 and §4.4.4 — limitations and recovery outcome

> Both indices and the intervention location depend on the same gaze stream.
> Spatial error can therefore mimic an event and simultaneously misplace a
> scaffold. The study will report tracker quality and event-detection validity
> alongside index values; intervals with inadequate quality will be identified
> separately. Recovery latency will be timed from a prespecified, independently
> marked disruption to a separately defined and confirmed return to reading.
> Controller activation-to-withdrawal duration will be retained as an
> implementation measure, not substituted for the primary recovery outcome.

Also correct the abstract, §1.5, §4.3.1, §4.3.6, §4.4.1, and §4.4.6 wherever
they describe a single “stability index” as both trigger and study outcome.
`main.pdf` already contains separate §4.3.4 and §4.3.5 sections: **do not**
insert the older packet's proposed “§4.3.4.1.” Rename the inconsistent §4.3.4
heading/body to *Session Reading State Index (Session RSI)* throughout. Keep
the former MediaPipe tracker table only as explicitly dated historical evidence.
Check the PDF's quoted fixation/regression/skipping numbers against the original
papers before retaining them, and state their population, task, device, and
measurement unit. Do not map laboratory word-level norms to line-visit cutoffs.

## What to do with the evidence: sequence and decisions

| Step | Collection or analysis | Decision enabled / required output |
| --- | --- | --- |
| **1. Audit existing records** | Inventory calibration, held-out accuracy, natural-reading JSONL, code version, device, font/layout, capture gaps, incomplete sessions, and diagnostic-dot status. Replay the current offline extractor without changing its defaults to chase favorable results. | A reproducible dataset manifest and an explicit list of measurements that are currently unavailable. No participant-facing validity claim from the dot or four stationary sessions alone. |
| **2. Establish spatial and temporal quality** | Run the separate known-target task at actual text geometry, including top/middle/bottom, word boundaries, line transitions, scroll and posture changes; report session-level exact/adjacent assignment, bias, tails, dispersion, gaps, and latency. | Decide which *spatial granularity* is supportable. If word or exact-line placement is unreliable, do not claim it or use it as a study-validated scaffold location; investigate broader support or tracker improvement before the main study. |
| **3. Validate derived events** | Compare line-stay, dwell, backward transitions, and offline fixation/word-visit measures with independent observed targets or coded reading behavior. Replay plausible detector settings as sensitivity analyses, retaining missing/censored denominators. | Identify false regressions, missed rereads, double-counting, and whether online line-event metrics correspond to the phenomena named in the manuscript. Do not treat a synthetic unit test as participant validity evidence. |
| **4. Collect independent reading evidence** | In a small, separate formative phase with the target age group (45+), obtain time-aligned reports or annotations of following, difficulty, lost place, and recovery, plus passage comprehension and acceptability. Define how prompts avoid changing the measured reading task. Keep scaffold/demo, prompted-target, and natural-reading records separate. | A labeled reference for assessing false activation, missed episodes, intervention location, and recovery. Unlabeled high AII cannot serve as its own ground truth. |
| **5. Evaluate the indices** | Examine each raw component and the composite, baseline split-half/repeatability, correlation of dwell and line-stay counts, tracker-quality sensitivity, AII trajectories around independent events, and Session RSI by article/condition. Compare candidate weights/windows/thresholds only in the formative data, with participant and passage separation. | Retain, revise, or abandon the provisional operational definitions. Record chosen parameters and the reasons; freeze the study configuration before summative data collection. A 3–5-person formative phase primarily establishes feasibility and failure modes, not definitive diagnostic accuracy. |
| **6. Test the adaptive intervention** | After quality and index decisions, run the planned counterbalanced adaptive-versus-static comparison. Record scaffold transitions and targets, comprehension, speed, effort/usability, externally anchored disruption/recovery times, and controller cycle duration. Analyze repeated episodes within participants rather than treating them as independent people. | Evaluate whether adaptive support improves the specified outcome without increasing unwanted intervention or harming comprehension. Report uncertainty and quality exclusions with the result. |

Before the main study, prespecify the spatial validity requirement for each
claimed scaffold granularity, event-label protocol, baseline procedure,
detector settings, quality exclusions, outcome definition, unit of analysis,
and parameter-freezing record. The pilot should choose these with the target
text geometry and actual data; this README intentionally does not invent
numerical success thresholds. If the tracker or event measures fail their
prespecified validity checks, report the corresponding index as an
**exploratory tracker-derived signal** and narrow the manuscript's claim.

## Completion checklist for this documentation work

- [x] Separate Session RSI, AII, their formulas, and their research roles.
- [x] State the exact online event semantics and distinguish them from offline
      natural-reading measurements.
- [x] Map supporting studies to the claims they can and cannot justify.
- [x] Expose the MGazeNet/MediaPipe, baseline, demo-mode, section-number, and
      recovery-outcome discrepancies in `main.pdf`.
- [ ] Collect independent target and natural-reading evidence; these are study
      tasks, not results supplied by this README.
- [ ] Freeze validated methods and update the thesis after the formative phase.
