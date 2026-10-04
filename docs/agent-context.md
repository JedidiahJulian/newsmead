# Agent context — updated 2026-10-04

This is a routing note, not a new research protocol. Recheck source and newer
dated evidence before continuing. It summarizes the project's recent tasks and
local documentation to avoid loading full histories each time.

Use the upstream `scientific-critical-thinking` skill for evidence appraisal and
`statistical-analysis` for statistical planning and interpretation. Their source,
revision and validation limits are in `docs/codex-agent-workflow.md`. The custom
project skills were retired at the user's request. This note and the measurement
protocol remain authoritative for NewsMead-specific decisions and tools.

## Objective and current direction

**2026-10-04 continuation-marker amendment:** the owner approved replacing the
post-P09 landing cue with an expected-continuation cue. Supported first-quarter
to last-quarter progress remembers the next fully visible LTR line; a supported
landing elsewhere offers a fixed marker at that expected line. A normal next-line
arrival produces no marker. This is optional navigation assistance, not an error
or difficulty diagnosis. Appearance is fixed 6 dp / alpha 240, with the existing
4 dp gutter gap; positive strength controls do not attenuate it. New conditions
use `DEFINITION_PROFILE_V2`, policy v3 and `PILOT_DEFINITION_*_V2` /
`REPLAY_DEFINITION_V2`; detector/frozen protocols retain their identities. Exact
state, evidence limits and sources: [continuation marker](scanpath-continuation-marker.md).
The prior V1/evaluator/device checkpoints below remain historical.
Both APKs assemble and Android fixtures compile; they have no current phone
result. The first JVM gate stopped at a corrected test-only field-name error;
retest approval is pending under the one-failed-attempt boundary. Scoped
whitespace and resource XML checks pass. Independent review findings were
resolved, including first-prefix censoring, row wobble and departed-finalization
handling. Evidence/hashes: `output/continuation-marker-check-01/summary.json`.
No device install/capture, staging, commit or participant validation ran.

**2026-10-04 evaluator alignment:** the owner explicitly selected matching the
current article aids for pilot and replay. Both now use `DEFINITION_PROFILE_V1`,
gutter markers and the same near-word popup; the obsolete strip reserves 0 dp in
every condition. Pilot conditions are `PILOT_DEFINITION_*_V1`; replay is
`REPLAY_DEFINITION_V1`. Their UI/dictionary mode is recorded separately from old
magnifier traces. Pilot live lookup retains the offline glossary/online fallback;
synthetic pilot/replay disable network. Consultation boundaries preserve raw
callbacks and exclude card intervals/late captures from article paths. Prompted
tasks remain aids-off; shadow/display share geometry. Replay uses the shared
six-stage article presenter with callbacks detached from closed runtimes.
Historical detector-only replay assertions remain; current display checks are
separate because consultation changes later article evidence. See
[pilot](scanpath-live-pilot.md) and [replay](scanpath-replay-screen.md). Independent
source review found no P1/P2 issue; direct line-cycle and replay-to-demo regressions
address its coverage limits. 64 focused JVM tests and both APK assemblies pass;
updated Android fixtures compile but have not run on a phone. Evidence/hashes:
`output/evaluator-current-aids-check-01/summary.json`. No device capture/install,
staging or commit ran.

**2026-10-04 article line-start spacing:** the owner's screenshot showed the
marker covering the first letter. The article body reserves 16 dp start padding
from inflation; article overlays put the marker in this gutter with a 4 dp text
gap. Both live and demo construction select this placement. Existing geometry
already accounts for TextView total padding; marker clipping abstains. Text may
wrap differently in the narrower column. A camera-free pixel rendering regression
compiles for separation from the text. App/test APK assembly, article XML parsing
and whitespace checks pass; device execution/appearance remain pending.

**2026-10-04 definition layout/offline amendment:** the owner clarified that the
card should follow an internet template and requested a light offline dictionary.
The native card adapts Flowbite React's Default Popover (shaded header, divider,
border, pointer) using NewsMead colors/fonts, with the larger compact close control
and source highlight. A 75-entry original English glossary supplies immediate,
explicitly attributed offline definitions. The article demo prefers a covered
actual visible source word; no text substitution. Uncovered words retain the API
route/unavailable state; unsupported languages still abstain live. Source links,
coverage and check results are in [Article definition aid](article-definition-aid.md).
64 focused JVM tests pass; app/test APK assembly and popup XML parsing pass.
New offline/word-selection Android fixtures compile but have not run on a device.
Phone rendering of this revision remains unverified; no device install/capture,
staging, commit or backend change ran.

**Historical article UI checkpoint — 2026-10-03:** the owner rejected the top word
magnifier and requested a definition popup and demo inside the loaded article.
[Article definition aid](article-definition-aid.md) now records the implementation,
H03/M25 lexical-assistance precedents, transfer limits and consultation exclusions.
Ordinary articles select separate `DEFINITION_PROFILE_V1` / `EXPERIMENTAL_DEFINITION_V1`;
the configurable 800 ms current-word residence trigger is exploratory, not a
validated difficulty cutoff. Cards remain during consultation and preserve source
layout; open/close boundaries exclude consultation and late captures from article
sequences while retaining numeric raw callbacks. The source word stays highlighted
using a noninteractive overlay while its definition is open. Close uses a large
vector × and a compact 28 dp gray box inside a 48 dp tap area, without source
spans/font changes. Callback resets are deferred
until engine dispatch completes. The explicit article demo stops the provider,
cycles real aid views and keeps camera off afterward. `origin/Recommendation`
was fetched at `501e9be1f83d4d962dc4931ef9044ab1f18a3e1b`, without merge; no dictionary
endpoint was found. A replaceable English Free Dictionary API adapter provides
actual definitions and bounded caching; unsupported languages abstain live.
61 focused JVM tests pass and app/test APKs assemble. New camera-free Android
fixtures compile but have not run on a device; the earlier A56 15-test evidence
applies to the preceding build. The evaluator migration above supersedes this
checkpoint's deferred replay/pilot update; frozen protocols retain their meaning.
No device install/capture, staging or commit ran.

Help older adults sustain attentive reading through real-time visual assistance
in the on-device Android NewsMead prototype. The immediate development priority
is to complete the integrated live scanpath-to-visual-aid prototype before
participant measurement/evaluation. Build software replay checks during
implementation; carry forward existing gaze evidence. The user explicitly
allows continuous, combined, or variable
intensity scaffolds; four mutually exclusive levels are an existing implementation,
not a fixed requirement for future research. Re-entry is only one possible use.

Owner clarification on 2026-09-30: visual help need not be called scaffolds or
organized as levels. Replace the legacy design when a better suited,
study-defensible design exists. The [aid design and evidence contract](scanpath-adaptive-aid-design.md)
now specifies independent aid types with within-type strength, per-rule source
provenance and exact trigger/form/outcome limits. Prioritize line guidance and
localized word support; optional ruler and word-progress forms get distinct
versioned IDs. Preserve A4's focus-region meaning rather than pooling variants.
No forceful intervention follows every return/cycle, and poor tracking cannot
increase assumed difficulty. The [runtime checkpoint](scanpath-aid-runtime.md)
now implements the independent line/word/focus route, controls, variable
word/focus strength and bounded action provenance. The
[matched pilot instrument](scanpath-live-pilot.md) now provides prompted tasks
and ordinary-reading raw/shadow/display conditions. Further authorized device
execution and participant evaluation follow this integrated build.

First device check on 2026-10-01: Samsung SM-A566B/API 36, target APK from
`e5fa5b2` installed with data-preserving update. Four synthetic geometry/magnifier
tests ran: one passed, three failed on missing detached-fixture layout params.
After local layout-parameter corrections and explicit owner authorization, the
corrected test APK was installed and all four tests passed (`OK (4 tests)`).
No gaze capture or participant measurement was started. Record and limits:
[device check 01](scanpath-device-check-01.md). Keep this distinct from natural
gaze or visible article-aid validation.

The owner then requested the other phone: SM-G991B/API 35. Identical target/test
APKs installed with `-r`; the same four synthetic tests passed on the first
attempt. Source checkpoint `9559c95`, hashes/results/limits in
[device check 02](scanpath-device-check-02.md). Both phones pass these synthetic
checks; visible article layout, live gaze and human validity remain unmeasured.

The owner authorized the interactive pilot screen's first replay milestone.
[Scanpath replay screen](scanpath-replay-screen.md) now provides five explicit
raw-coordinate scenarios through actual rendered word/line geometry, the existing
engine/policy and real aid views, with controls and full private synthetic traces.
Replay stops on geometry change, pause, Stop or changed controls/scenario. Its
capture clock is scheduled synthetic elapsed time; delivery is actual callback
time, and >100 ms scheduling lag aborts. Synthetic identity skips calibration
and creates no camera/provider. This is the scripted inspection route; live
prompted tasks and matched human recording remain pending. Check the guide's
dated build/device results before inferring readiness. All 34 focused JVM tests
and APK assembly pass; [device check 03](scanpath-device-check-03.md) retains the
initial test lookup failure, authorized correction/retest and final five-test
pass on SM-G991B/API 35. Actual laid-out replay and variable word/focus choices
have software evidence on that phone; the synthetic word preview was inspected.

**2026-10-03 implementation:** starting from `50ff2b7`, a separate live pilot
screen now provides ten geometry-derived prompted word/line tasks, continuous
engine processing, hidden-dot ordinary reading and no-aid/shadow/display
conditions with the same reserved viewport. Private JSONL retains raw inputs,
capture-time prompted intervals, engine acceptance, full evidence/action/draw
provenance, adherence reports and shutdown counters. Per-target agreement and
unshown opportunities are separate from descriptive global alignment; holds
cannot label physiological refixations. Five camera-free activity tests compile,
51 focused JVM and 8 host summary tests pass, and both APKs assemble. Focused
independent review found no remaining issue after scoring/lifecycle fixes.
Device execution of the new pilot awaits task-specific authorization; no live
gaze or human data were collected. Read [the guide](scanpath-live-pilot.md) for
units, denominators, time assumptions, partial-file rules and remaining study
procedures. Do not infer a completed participant plan from default task settings.
No human validity follows from synthetic replay.

**Trigger review/demo — 2026-10-03:** [evidence review and flowchart](scanpath-trigger-evidence-review.md)
rechecked primary methods/outcomes and current decision predicates. Line guidance
has the strongest intervention precedent; the exact word AND trigger differs
from GazePrompt's OR trigger, while the cycle-to-focus and monotonic dose rules
lack direct validation. The [replay screen](scanpath-replay-screen.md) now adds
an explicit render-only Demo cycle with seven four-second stages, Previous/Next
and Stop. Manufactured actions never enter detector/policy/results/traces, and
no camera/calibration opens. Independent review fixed insufficient max-word
height in replay/pilot strips: 68 dp reserved height remains, vertical padding
is 10 dp. All 53 focused JVM tests pass and app/test APKs assemble; new device
execution awaits task-specific authorization. Preserve this status when making
panel claims. No human difficulty/aid-benefit evidence was collected.

**A56 compatibility — 2026-10-03:** the owner explicitly approved installation
and camera-free checks after automatic installation review required explicit
A56 approval. Both APKs installed with `-r`. [Device check 04](scanpath-device-check-04.md)
records one bounded 14-test attempt: **6 pass, 8 fail**. All five pilot tests
pass, including all ten prompted plans and shadow/display behavior. Four demo
tests fail readiness; four replay tests abort because tiny negative callback
lag was treated as late. Later replay scenarios were not executed. Known
synthetic traces and an app-only pilot preview are retained locally. Device
attempts stopped; local fixes now retry unchanged early deadlines and use the
actual compound-padded word-strip height. Source review closed; all 56 focused
JVM tests and both APK assemblies pass. Improved status diagnostics and a
centered-strip regression compile. The owner then authorized the corrected
installation/retest: **all 15 tests pass, 50.908 s** on A56/API 36. All five replay
scenarios completed with expected patterns/aid draw proxies, strict causal
timing and synthetic callback lag 0.013–2.879 ms. Demo/renderer and all five pilot
checks pass. App-only demo word/focus and replay word previews are readable.
Corrected hashes/results/evidence paths are in check 04; this build has not been
tested on S21. Home → Scanpath replay → Demo cycle is ready for camera-free
inspection. Earlier pending-device statements describe pre-attempt status.
No camera/human collection, staging or commit ran.

The [scanpath-to-visual-aid implementation proposal](adaptive-visual-aid-proposal.md)
is the current **NewsMead feature specification**. Re-reading the source task
**Explore feasible gaze metrics** clarified the owner's final intent: preserve
ordered gaze-to-text visits, classify **observable navigation patterns**, then
select or parameterize visual help. The owner subsequently clarified that the
proposal must derive and evaluate **both word-level and line-level** scanpaths,
trace the order categories to primary papers, and connect each visual aid to
the observed situation it might address. Word-level analysis is a feasibility
track despite weak current exact-word placement. Slow line progression to a
focus window is an experimental mapping with no identified direct intervention
evidence; window restrictions may remove useful parafoveal text. A universal
reading-instability construct, supervised support-need model, and fixed
four-scaffold ladder are **not** required. Aid type, target, strength, duration,
and combination may vary during ordinary reading; the candidate policy and its
benefit must be independently tested. The current proposal specifies code
boundaries, derivation, a descriptive classifier, a configurable aid policy,
primary-study precedents, and separate measurement, pattern, and intervention
gates. An
[illustrated PDF companion](../output/pdf/newsmead-scanpath-visual-aid-proposal.pdf)
shows the same derivation, order-to-aid bridge, and proposed UI states; its
examples are schematic, not observed NewsMead scanpaths. The earlier
[three-state line-guide research plan](adaptive-visual-aid-research-plan.md)
is retained as a historical narrower option, not the current requirement.
The [known-interruption bookmark](evidence-backed-reading-support-proposal.md)
and [fixed pattern/scaffold proposal](scanpath-scaffolding-proposal.md) are
historical options. The [critical audit](gaze-proposal-evidence-audit.md) still
applies to unsupported line-pattern meanings and aid mappings; the
[primary-study ledger](scanpath-scaffolding-evidence.md) records evidence and
transfer limits. An HSMM is at most an offline exploration; the old weighted
score and Mahalanobis distance do not establish reading instability. Preserve
useful rereading and abstain across ambiguous or missing gaze. The thesis source
still describes the legacy index and a circular recovery-latency endpoint;
align it only after the method and independent outcome are fixed.

The [order-category provenance table](scanpath-scaffolding-evidence.md#provenance-of-the-proposed-order-categories)
identifies Olivier's word-distance alphabet and Yamaya's multiline segment
classes as precedents, while marking NewsMead's physical-line bins as original
operational definitions. Preserve separate fixation-level word transitions,
word visits, and line visits. Olivier's same-word `Rfx` is not a long line hold;
`HOLD` is duration, not order; `BREAK` marks missing sequence continuity. The
historical forward multi-line symbol `FWD_LONG` (formerly `FWD_SKIP`) must not
imply that intervening text was unread.

The [published sequence-to-behavior table](scanpath-sequence-behavior-table.md)
is the current lookup for the exact word, segment, line, and longer-path labels
used in the proposal. It separates observed geometry from model/author
interpretations and maps NewsMead arrow examples only as proposed descriptors.
The [pattern ground-truth audit](scanpath-pattern-ground-truth-audit.md) identifies
which studies used human movement annotations, known geometry, instructed tasks,
experimental text conditions, or exploratory clusters. Exact line cycles such as
`L4 -> L5 -> L4 -> L5` remain NewsMead illustrations, not validated difficulty
classes.
The [original figure reading guide](scanpath-source-figure-guide.md) links directly
to primary-source pages and images for NN/g's named web patterns, the 2026
anchor-search-confirm process, sentence-region rereading prototypes, Hyönä's
expository reading profiles, Parshina's sentence-path prototypes, and Chen's
task-reading strategy transitions. It distinguishes individual gaze plots from
heatmaps, aggregate profiles, and authors' explanatory figures and records
task/evidence limits. Do not substitute generated redraws for original figures
when the owner asks for verifiable paths.
The [reading-difficulty scanpath evidence guide](scanpath-reading-difficulty-evidence.md)
separately audits paths against independent reading-skill, comprehension, and
controlled-difficulty outcomes, with original figure and table locations. No
reviewed named path is a conclusive live difficulty sign for older adults reading
news on a phone. Notably, regressive paths relate differently to comprehension
across genres, and news-page gaze studies found so far do not validate a
path-to-difficulty outcome. Its quantitative audit shows that published cluster
shares, correlations, model coefficients, R², and benchmark AUROCs are not
calibrated `P(current difficulty | path)` or aid-benefit weights. Preserve
candidate paths as observations pending NewsMead measurement, independent
outcome prediction, calibration, and randomized aid-benefit tests; low gaze
quality is an abstention reason, not a difficulty signal. The current
[implementation proposal](adaptive-visual-aid-proposal.md#confidence-and-weights-for-adaptive-decisions)
records how a future weighted policy would be learned and gated.
The [classification dictionary](scanpath-classification-map.md) and its
[table-form PDF](../output/pdf/newsmead-scanpath-classification-map.pdf) group
movement labels, ordered path patterns, empirical whole-path clusters, and
model-inferred phases. Those are different types of labels; sequence length
alone does not define their type. The dictionary names primary studies and
marks NewsMead examples as proposed descriptions rather than published classes.
The [visual-aid evidence map](scanpath-visual-aid-evidence-map.md) and its
[PDF table](../output/pdf/newsmead-scanpath-visual-aid-evidence-map.pdf) connect
those observations to tested aids and separates movement-matched results from
untested NewsMead trigger rules. Current closest aid evidence is for return-
sweep/next-line localization; automatic word magnification after refixation,
focus windows after slow line residence, and cues for spontaneous rereading
remain unvalidated. Preserve context and reader control during rereading.

The [public gaze-sequence capability-model survey](scanpath-capability-models.md)
checks EyeBench and individual LSTM, CNN, transformer, and HSMM precedents against
their actual labels, public code, validation splits, and NewsMead fit. EyeBench is
the strongest offline comparison framework, but its published models do not
validate a live aid-need detector for older phone readers. No candidate model
has been selected for the app, and no task-ready trained weights were verified.
The survey also covers phone front-camera precedents: Lepton, StrategicReading,
and Valliappan et al. A hardware-relevant study may use any text read on a phone;
it need not use news. These studies support testing coarse line/region signals,
but none supplies a public validated model that chooses real-time aid strength.
Lepton's reported comprehension correlation gain comes from scrolling features
in its leave-one-reader-out table, not gaze alone. Preserve that distinction.

MGazeNet has been integrated and optimized. Old July README descriptions and the
September 6 replacement handoff do not describe the latest runtime. Verify the
active provider in source before making tracker claims. Better visible tracking
does not by itself validate word assignment, fixation detection, or an index.

## Current measurement work

- Owner clarification on 2026-09-30: the pilot's objective is to build and evaluate
  the scanpath-to-visual-aid system, carrying forward existing gaze evaluation.
  The owner clarified that measurement tasks follow completion of the prototype.
  [Pilot revision 6](scanpath-measurement-pilot.md) is the current build/evaluation
  plan: six objectives, seven work packages, P01–P13 pattern rules and A0–A4 aid
  candidates, plus separately identified optional ruler/coloring forms. Build
  the Android causal word/line engine, pattern-to-aid policy,
  adjustable renderers with variable strength, reader controls and action/render
  logging first. Complete software replay/build checks and the matched evaluation
  instrument before running participant measurement. Phases then cover prompted feasibility,
  ordinary-reading shadow decisions and independent support-need reports,
  controlled aid/strength trials, then frozen varying-strength evaluation.
  Offline analyzer v5 now includes the [P01–P13 named detector layer](scanpath-detectors.md):
  word/line movements, geometric sweep candidates with explicit endpoint settings
  and verified LTR direction,
  revisit/frontier episodes, structural A–B–A–B cycles, residence/progression
  descriptors and breaks. Long/slow flags require explicit development settings.
  Live engine v1 now runs P01–P13 from the article's raw observation callback.
  Focused JVM tests and 13 synthetic cases/183 raw-action prefixes pass; common
  fixation fields and P01–P12 agree with Python, while live P13 resets immediately.
  The debug app builds and Android geometry tests compile; device execution,
  human validity and runtime timing are unmeasured. Layout/bidi caching and old
  geometry eviction were independently reviewed and corrected. The in-memory
  adapter uses callback-only event delivery and starts a new generation on
  text/locale changes or a 512-finalized-candidate resource cap, preserving the
  capture-time floor. Pattern events require finalization; a separate established
  ongoing-prefix contract can add observed time without incrementing refixations.
  `gaze/aid/` now implements a source-linked policy, independent displays,
  reader controls and bounded numeric action/render journal. Normal article
  reading selects this route instead of the legacy weighted controller/demo.
  Word magnification and optional focus strength vary under an explicitly
  experimental profile; focus requires cycle + residence + slow progression,
  never a cycle alone. Action audits are exported privately only from the menu;
  they do not contain raw gaze or replace the matched evaluation instrument.
  Focus new checks on sequence,
  classifier and aid behavior; do not make a broad gaze benchmark restart a
  prerequisite. Raw/smoothed and conditional-reference limits still apply.
  The owner is requesting a stronger research model from the public-model owner;
  artifact/compatibility/performance are unverified. Continue model-versioned
  sequence development and reevaluate affected measurements when supplied.
  Focused detector checks: 39 extraction/integration plus 13 classifier tests
  passed (52 total). Independent review findings on direction, episode phase/
  evidence and censored residence were corrected with regression fixtures.
  Evidence contract: `docs/scanpath-detectors.md`; tests:
  `tools/gaze/test_scanpath_patterns.py` and `test_analyze_reading_measurement.py`.
- The active scanpath preparation branch is `measurement/scanpath-pilot`, created
  from the same commit as `measurement/RII_2` while retaining pre-existing
  uncommitted changes. No scanpath measurement has been run on the device in this
  branch. The [phone-only scanpath measurement pilot](scanpath-measurement-pilot.md)
  specifies separate word and physical-line paths, explicit continuity breaks,
  known-target placement checks, and natural-reading evidence limits. It lists
  descriptive patterns to implement; cognitive labels and validated aid/intensity
  weights are not established. Review the analyzer's
  current `analysis_version` and focused tests before using its outputs. The
  structured v6 target score is based on smoothed displayed coordinates, whereas
  the natural-reading replay uses raw coordinates and raw-derived AOIs; v6's
  current score cannot pass a raw path placement gate. A separate matched raw
  target/order instrument supports evaluation of the completed prototype,
  without changing frozen v6. The offline registry/classifier is implemented;
  the integrated live engine/policy/displays/controls/action journal are built.
  Next build the matched raw evaluation instrument, then conduct authorized
  device checks and participant tasks. New participant measurement was not a
  prerequisite for prototype implementation. The blank
  record now covers pattern and aid outcomes, condition assignment and holdout
  allocation. Set participant counts, numeric tolerances and study conditions
  from the phase-specific plan before collection; they are not validated defaults.
- The [2026-09-30 pilot audit](scanpath-pilot-audit.md) corrected a timestamp-order
  reversal across replay boundaries and overstated accepted-sample coverage.
  Analysis v4 preserves a global capture timestamp, separates eligibility from
  accepted samples, and reports visit censoring, duration coverage, output age
  and terminal boundary events. Focused suite: 31 tests; independent review
  found no remaining issue in the fixes. Protocol revision 2 requires the same
  calibration through pre-check/read/post-check, current locked 22 dp font,
  matched raw placement plus continuous-order tasks, and a frozen sampling/
  material plan. Use the blank session record privately. This is diagnostic
  preparation, not validated natural-reading or aid behavior.
- Ordinary known-target structured validation is now protocol v6/schema 9: the
  orange gaze dot stays visible and the same article One Euro-smoothed
  coordinates drive display, word/line mapping, and scoring. Raw MGazeNet source
  coordinates remain logged separately. Interpret its output as closed-loop
  article-pipeline UI performance, not unaided estimator accuracy, and do not
  pool it with raw visible-dot v5 or hidden-dot v4 data. The frozen
  matched-comparison path remains raw hidden-dot v4. The interrupted raw-visible
  v5 diagnostic is retained at
  `diagnostics-local/reading_validation_session_20260921_114932_153.jsonl`; the
  preceding hidden-dot evidence is
  `diagnostics-local/reading_validation_session_20260920_190328_306.jsonl`.
- The source task **Explore feasible gaze metrics** reports one completed v6 run
  (`reading_validation_session_20260921_155148_059.jsonl`): 89.3% word-target
  exact-line, 47.5% exact-word, 61.6% guided exact-line, and 99.2% guided
  within-one-line at 22 dp, after recalibration with the visible smoothed dot.
  This is promising closed-loop known-target evidence, not unaided or natural-
  reading line-transition validation. Repeatability, font-size effects, and
  target-population performance remain open.
- `docs/reading-measurement-pilot.md`: specification for raw natural-reading JSONL
  recording and reproducible offline extraction. The requested orange gaze dot
  stays visible; scaffolds and the old adaptation feed are disabled during this
  recording mode. This is a diagnostic-dot condition, not an unassisted study.
- Raw and displayed coordinates are separate. Analysis uses capture timestamps,
  physical-screen geometry, and Android UTF-16 offsets. Coordinate/gaze validity,
  scrolling, changed geometry, ambiguous assignment, and unclassified gaps matter.
- `docs/reading-measurement-run-01.md`: first run is a plumbing/measurement
  diagnostic with user-reported vertical misalignment, not independent accuracy
  ground truth or index validation. The corrected analyzer breaks continuity
  across long unclassified inter-fixation intervals. Sparse retained transitions
  limit sequence claims; do not restore the earlier gap-bridging behavior.
- `tools/gaze/analyze_reading_measurement.py` and
  `tools/gaze/test_analyze_reading_measurement.py`: current offline analysis and
  regression checks. Check the actual test runner and current version before use.
- Candidate fixation, first-pass, rereading, regression, skipping, and scanpath
  features remain provisional. No final RII formula, weights, baseline, or
  replacement online controller is established by this pilot.
- Preserve explicit eligible denominators, undefined rates as `null`, ambiguous
  assignments, censoring and continuity breaks. Use the current analyzer CLI and
  tests from the pilot document; generic statistical utilities do not replace it.
- Repeated windows are not independent participants. Plan participant/session/
  passage separation appropriate to the target claim, account for clustering,
  and prevent evaluation leakage through normalization or future observations.
  Generic assumption-check reports do not establish independence or measurement
  validity; do not automatically remove or winsorize observations they flag.
- `docs/reading-stability-index.md` and `docs/adaptive-visual-scaffolding.md`
  explain the legacy line-event score and rolling controller. They are distinct
  from the new raw-coordinate measurement pipeline.

## Research judgment

Prior tasks discussed scanpath regularity and a personalized multivariate
deviation score. These are candidate approaches, not approved final methods.
Verify each paper's population, hardware, sampling, feature definition, outcome,
and validation split before adapting it. Do not turn an association into a causal,
clinical, or real-time detection claim. Do not assume unusual rereading is harmful;
deliberate careful rereading and tracker noise are alternative explanations.

Select reproducible measurements first; compare candidate indices to independent
labels/outcomes; evaluate scaffolds on reading benefit rather than simply a
reduction in the score used to trigger them. Retain the on-device deployment
objective. Older constraints include both A56 and SM-G991B relevance and no paid
EyeDid or required laptop/WiFi backend; revisit only if the user changes scope.

## Evidence entry points

Read `docs/progress-notes.md` from the top for recent implementation decisions.
Read a historical gaze handoff only when the task concerns that experiment.
Related workspaces are `../THS-ST-Paper` and `../gaze-thesis-prototype`; do not scan
or edit them unless relevant to the current request.

Task context checked during setup (retrieve only if a specific ambiguity remains):

| Task title | Task ID | Relevant context |
| --- | --- | --- |
| Check scanpath usage | 01a0ae5f-010b-7b83-9566-6634fb337b56 | Event counts versus ordered visits; scanpath proposal status |
| Review thesis gaze limitations | 01a09609-43ab-7b61-8640-d158dd5cdfb8 | Index validity and candidate deviation score |
| Review thesis gaze | 01a0a29f-cd3f-7de0-997c-b2f2e1ad5260 | Index first; flexible scaffolds; literature leads |
| Gaze Measurement Pilot | 01a0a2a3-8959-7be3-aa3c-02de33b98972 | Orange dot visible, scaffolds off; raw measurement implementation |
| Improve article reading FPS | 01a09405-e15c-7112-8dd1-770f1d1ed400 | Integrated MGazeNet and calibration keeper checkpoint |
| Explore feasible gaze metrics | 01a0be09-fe3e-76c2-827c-94dcfadcc416 | Proposal decisions, Mahalanobis comparison, v6 run, primary-paper discussion |

At setup, existing uncommitted pilot/analyzer edits were present. Preserve them;
this agent-framework task does not assess or adopt those edits as its own.
