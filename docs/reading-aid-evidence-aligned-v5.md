# Reading aid implementation V5

Implemented for the user's 2026-10-19 proposal. Profile: `EVIDENCE_ALIGNED_V5`.
Article, Scanpath pilot and Scanpath replay select this profile. Earlier profiles
remain available to deterministic tests; the frozen scanpath extractor is unchanged.

Owner correction: the visible line guide and blue bookmark must both remain
**single-line**. The earlier automatic three-row display was rejected. Bookmark
activation from within-article row jumps is removed from this profile.

## Flow

```mermaid
flowchart TD
  A[Selected calibrated and smoothed article gaze] --> B[Fresh causal coordinates and current layout]
  B --> C[Stabilized estimated line; uncertainty retained internally]
  C --> D[Ordinary reading guide: 8 to 13 percent gray]
  C --> E[Three distinguishable measured search locations]
  E --> F[Temporary 19 percent guide]
  B --> G[Same exactly mapped word: 1000 ms supported residence]
  G --> H[Lookup icon: tap to open definition]
  C --> I[Earlier / later / return / forward / continued reread]
  B --> J[Interruption recovery or repeated revisits]
  I --> R[One supported complete episode within 15 seconds]
  R --> K[50 percent focus; record route and witness]
  J --> K
  K --> O[Fresh complete disjoint episode while active: up to 100 percent]
  C --> L[Save last supported location]
  L --> M[Outside article / unavailable tracking for 500 ms]
  M --> N[Single-line bookmark; clear after supported recovery]
```

## Operational rules

### Location and guide

- Consume the same selected coordinate stream as the dot. During automatic
  scrolling, stabilize body-content Y and project it into the current viewport.
- Use a trailing 150 ms median and require 150 ms of fresh row-candidate residence
  before publishing support. A duplicate capture adds no time. A gap over 250 ms,
  missing coordinates, eye closure or an operational pause breaks pending support.
- No beginning/end fraction of a line is required. Ordinary supported reading
  receives the guide without a difficulty classification.
- The visible line guide highlights **one stabilized estimated row**. It never
  expands automatically into a neighbouring-row band. A single displayed row is
  the owner's chosen presentation, not a claim of validated row accuracy.
- A supplied measured error radius defines internal row uncertainty. **Current
  calibration has no saved error radius**; internal neighbouring-row bounds are
  logged as `neighboring_rows_uncertainty_unknown`, not painted. They are an
  engineering fallback, not a coverage guarantee. Jitter is not accuracy.
- Search emphasis requires three pairwise separated **measured** intervals, such
  as L5 → L7 → L6, within 8 seconds. Unknown-error input cannot activate it.
  Each new witness triple may receive 19% emphasis for 1.5 seconds.
- Gray target opacity ramps from 8% toward 13% over 1.5 seconds of the current
  supported visit. Presentation entry is 150 ms. After the last supported
  delivery: retain for 750 ms, then fade out over 300 ms. These clocks are separate.
- Reader strength caps apply. The separately selected sentence option retains
  its text-context behaviour; the default line option stays one row. Background
  spans preserve glyph color and wrapping.

### Word offer

- Require 1000 ms of fresh, contiguous supported residence on the same exact
  mapped word. Missing intervals, duplicates and unsettled locations add no time.
- The selected point must be inside that word's current screen rectangle, and
  its UTF-16 range must belong to the mapped row. No nearest-word expansion.
- If measured uncertainty is supplied, its entire rectangular bound must fit
  inside the word. Without that measurement, retain the existing exact mapper
  gate and log `mapping_only_unvalidated`; this is **not validated word accuracy**.
- Present the existing lookup icon. It expires after its existing 5-second offer
  lifetime. Only a tap opens the definition. Do not offer again for the same
  unresolved word, including after a brief loss and return. Supported residence
  on a different valid word resolves the earlier visit.

### Focus

- New route: stabilized A → forward first-pass rows → B → A-region → C → D, with B later than
  A and A-region < C < D ≤ B, within 15 seconds. The broad departure/return bands
  must be separate; adjacent forward reread steps use stable row order. Example:
  L4 → L7 → L4 → L5 → L6, or the ordinary first pass
  L4 → L5 → L6 → L7 → L4 → L5 → L6. Intermediate first-pass rows must progress
  forward; after the broad return, at least two supported forward row changes
  must remain within the earlier frontier. Same-row requalification after
  unsettled samples replaces its witness rather than adding a movement. Missing
  input and long gaps still break incomplete patterns. This is an **online rereading prefix**, not proof that a
  sentence was misunderstood. Traces state whether movement distinguishability
  was measured or remained unknown.
- **One complete supported broad episode within 15,000 ms activates 50% focus.**
  Require separation between its later frontier and earlier-return region, not
  between every adjacent forward reread step. Use measured uncertainty if supplied.
  With the current unknown-error +/-1-row fallback, diagnostic anchors need at
  least three rows of separation. This is a coarse safeguard, not measured accuracy.
- Preserve interruption recovery: three separately qualified unavailable/recovery
  episodes within 30 seconds make one complete qualification. Missing gaze is
  labelled tracking unavailability. Font-aware A–B–A–B repetition remains, with
  the same separated-anchor requirement. Adjacent uncertain A–B–A–B alone cannot
  activate current focus. Route IDs remain distinct in logs.
- A shared ledger across routes grants initial 50% and allows **100% only after
  a fresh complete independently qualified episode while the response is active**.
  All credited evidence must follow the previous episode's final credited capture.
  Duplicate IDs, relabelled/shared captures, double-labelled patterns and extended
  overlapping suffixes cannot escalate. Interruption escalation therefore needs
  three more complete loss/return episodes, not just a fourth return. Retain at
  most two dose witnesses. These strengths are trial settings, not a difficulty score.
- Pending row evidence is discarded on operational pause, article/layout change
  or font resize. Reflow also removes active row-based qualifications; retained
  interruption qualifications may continue. Reader caps remain in effect.
- Keep candidate-onset capture time separate from the first support-confirmation
  capture and delivery times. Ongoing updates retain those first-confirmation
  clocks. Fresh support uses the existing 150 ms residence stage; no blanket
  300 ms or speed-dependent confirmation rule was added.
- Both styles enter over 400 ms. Hold for 3 seconds of fresh **supported reading**,
  then fade over 7 seconds of supported reading. Ticks, duplicates, loss and
  unsupported transition time cannot advance recovery. Refreshes preserve recovery
  progress and do not restart a continuous presentation's onset animation.
- Target surrounding opacity is fade-text 60%/85%, or dark 40%/60%, at requested
  strengths 50%/100%; onset, recovery and reader caps reduce actual opacity.
  Retain the existing styles and maximum 85% surrounding suppression. Protect
  the reread passage plus neighbouring context during tracking and scroll holds.
  If that leaves no space to shade, legibility takes priority over a visible mask.

### Bookmark

- Save a single supported row only when it agrees with the current eligible
  line mapper. A different coarse row without that agreement invalidates an
  unshown saved source, rather than presenting a stale, apparently precise marker.
- Activate after **500 ms of outside-article gaze** (at least three fresh samples),
  or **500 ms of tracking unavailability**. Keep those two reasons distinct.
  Earlier-row and skipped-row movements **do not activate this bookmark**.
  The marker freezes at the saved line rather than following the departure.
- Minimum visibility: 1.5 seconds. Thereafter clear after 1 second of supported
  return to the saved row, or 1.5 seconds of coherent same/next-row reading elsewhere.
- Fresh article input may bridge the settling of a new row, but only supported
  capture intervals earn dismissal time. A brief glance does not clear the cue.
- Mapper-rejected observations break precise return credit. A new accepted run
  starts at its first accepted capture; earlier coarse residence cannot fill in
  rejected time. Bookmark support has its own evidence IDs in the trace.
- Clear invalid article/layout anchors; bound a cue to 30 seconds. Layout reflow
  invalidates source coordinates. Same-layout viewport translation retains the
  supported content location where applicable.

All numerical settings above are engineering/pilot choices. They are not
published optimal thresholds, confidence probabilities or evidence of benefit.
Synthetic tests establish software contracts, not reading difficulty or efficacy.

## What the cited studies actually establish

| Primary source | Findings relevant to the implementation | Limits |
|---|---|---|
| [Wang et al., 2023](https://arxiv.org/html/2303.16346), §§3–5 | Exploratory lab study with 20 low-vision and 20 sighted participants; Tobii Pro Fusion, 120 Hz, reading aloud. Measured lines searched before the next line was located and proposed line/word assistance concepts. | No trial of this aid; desktop aloud-reading findings do not validate silent phone reading, row mapping, gray opacity or timing. |
| [Hiraoka et al., 2016](https://doi.org/10.1145/2993148.2993167), [author paper](https://www.phontron.com/paper/hiraoka16icmi.pdf), §§4–6 | Ten analyzed Japanese graduate students reading English, desktop Tobii X2-30. Per-word gaze and lexical features predicted post-reading known/unknown word reports; reported F-measure 0.556 versus threshold baseline 0.422. | Small L2 sample and a word-knowledge target. No validated 1000 ms cutoff, phone word accuracy or definition-offer benefit. |
| [von der Malsburg & Vasishth, 2011](https://tmalsburg.github.io/MalsburgVasishth2011.pdf), pp.117–123 | Reanalysis of 44 participants' Spanish ambiguous-sentence data distinguished regression patterns. Whole-sentence rereading varied with manipulated syntactic conditions; short regressions were less specific. | Rereading occurred in multiple conditions. Sentence analysis does not validate this five-row online prefix or a focus intervention. |
| [Cane et al., 2012](https://doi.org/10.1080/17470218.2012.656666), [article PDF](https://www.interruptions.net/literature/Cane-QJExpPsychol12.pdf), Experiment 2 | Controlled desktop paragraph reading with EyeLink 1000 and imposed spoken-story interruptions. A highlighted interruption word aided resumption. | Supports a spatial recovery cue in that task. No evidence that missing phone gaze means forgotten location, or that our dismissal times are optimal. |

## Implementation and verification

### Preimplementation switch-confirmation review — 2026-10-20

The owner requested a challenge of the proposed replacement before implementation.
Recommendation: retain candidate/supported line states, but compare fixed support
with an experimental adaptive timing variant; do not assert that rapid switches
are error, that extra smoothed frames establish accuracy, or that this is optimal.

- Existing One-Euro smoothing, trailing median and 150ms residence already affect
  transition timing. Additional confirmation can suppress genuine brief returns.
  Counts alone give different supported spans at different frame rates. Preserve
  distinct capture times, gap limits and separate candidate-onset/confirmation times.
- If speed is calculated from confirmations, altering confirmation delays alters
  the supposed speed. Use an explicitly defined observational time basis; selected
  smoothed coordinate crossings are not measured physiological saccade onsets.
- Stable calibration offset can pass temporal support; no measured per-session
  spatial error radius is currently saved. Avoid manufacturing accuracy bounds
  from recent jitter. Retain ambiguous events for diagnostics without granting
  them activation credit or labelling them errors/inattention.
- A rereading pattern can support a candidate assistance hypothesis, not a
  diagnosis or a validated 50%→100% dose. Preserve readable context, reader caps,
  exposure timestamps and distinct trigger versus actual-render receipts.
- Compare on the same traces: boundary jitter, stable offset, genuine brief
  rereads, slow recovery, normal forward passes, 5/15/30Hz, gaps, content translation
  and font reflow. Compare unintended activations/minute, missed scripted routes,
  onset latency and visible-mask availability; reserve separate tuning/evaluation
  paths. Scripted intention and synthetic paths are not natural-reading gaze truth.
  Reader benefit needs separate comprehension/interruption/preference evidence.

Primary evidence and limits:

- [Hessels et al., 2017, I2MC](https://doi.org/10.3758/s13428-016-0822-1): noise-
  robust fixation labelling research supports evaluating robustness; it does not
  validate our online phone line-switch timing or adaptive gate.
- [von der Malsburg & Vasishth, 2011](https://tmalsburg.github.io/MalsburgVasishth2011.pdf),
  pp.118–122: 44 undergraduate Spanish sentence-reading data; whole-sentence
  rereading was more frequent under the manipulated syntactic condition, but also
  occurred in the other condition. No phone detector or focus intervention trial.
- [Niklaus et al., CHI2023, author publication page](https://research.adobe.com/publication/digital-reading-rulers-evaluating-inclusively-designed-rulers-for-readers-with-dyslexia-and-without/):
  evaluated four ruler designs with 91 crowdsourced readers with dyslexia and 86
  without; reports reading-speed gains, especially in the dyslexic group. This
  supports investigating rulers, not a reread-triggered 4–5-line phone mask. Full
  paper access failed in the bounded search; detailed effect sizes were not verified.
- [Schotter, Tran & Rayner, 2014](https://journals.sagepub.com/doi/10.1177/0956797614531148):
  trailing masks that prevented useful rereading reduced comprehension. Permanent
  word masking differs from partial line dimming; this is a warning about context
  restriction, not direct evidence that our focus display is harmful.

No replacement or tests/device operations occurred during that review. The later
approved implementation and its verification status are recorded below.

### Existing implementation checkpoints

### Revised focus proposal — approved and implemented (2026-10-20)

The replacement uses supported movement episodes. It removes the three-whole-
reread prerequisite and the proposed speed-dependent confirmation requirement.
The owner subsequently authorized implementation; current verification is below.

1. Reuse the orange dot's selected calibrated/smoothed coordinates and existing
   150ms supported-location residence setting. Do not add another smoothing or
   residence stage. Fresh capture times, <=250ms gaps, missingness boundaries and
   layout/content coordinates remain explicit. 150ms is not total system latency.
2. A tentative row change enters behavior history only after that support gate.
   No residence credit accrues to the old row while the candidate is unsettled.
   Rapid changes receive no special dose or automatic error/inattention label.
   Candidate-onset and support-confirmation timestamps remain separate.
3. Require separation between the broad frontier and earlier-return region;
   for ABAB, between the alternating anchors. Do not apply this separation to
   every ordinary adjacent forward step. Use measured uncertainty if supplied;
   current unknown-error +/-1row fallback implies >=3row anchor separation and
   is only a coarse pilot safeguard. Stable offset and small-font accuracy remain
   unresolved; this fallback is not a spatial coverage guarantee.
4. Broad reread: A -> forward first-pass rows -> B -> earlier visited A-region
   -> C -> D, where the two reread advances are forward and do not exceed B.
   One complete supported witness within15s can request initial50% focus.
   Examples: 4,7,4,5,6 and4,5,6,7,4,5,6. A brief earlier glance without subsequent
   rereading, or ordinary forward progress, does not satisfy this route.
5. Keep distinct interruption and ABAB route IDs. Interruption activation remains
   three completed qualified unavailable/recovery episodes in30s; do not label
   unavailable tracking distracted reading. Retain current font-adjusted ABAB
   repetition rules during migration, with the additional separated-anchor gate.
   Adjacent uncertain4,5,4,5 can no longer activate focus from that route alone.
6. Two trial target strengths: initial50%; a fresh, complete independently
   qualified episode while focus is active may select100%. All witness captures
   for escalation must follow the first episode's last credited capture and be
   globally disjoint across routes. Extended/overlapping suffixes cannot escalate.
   This is an experimental dose rule, not evidence of greater difficulty/benefit.
7. Preserve current target appearance: fade-text60% at50% strength,85% at100%;
   dark40%/60%. Entry/recovery transitions and reader caps reduce actual opacity.
   Keep400ms onset,3s supported-reading hold,7s supported-reading fade and current
   scroll motion/refresh semantics. Do not restart continuous onset animation.
8. Keep five rows or the needed reread passage/context clear. If no surrounding
   area remains, do not manufacture a visible veil by hiding required context.
   Replay reports distinct recognized-pattern, requested-aid and actual-render
   states, including the no-dimmed-area reason.

The studies in the review above motivate robustness checks, investigating rereading
and investigating reading rulers. They do not validate these numerical settings,
the line-level phone detector or the dose rule. The implementation comparison must
check false activation, missed intended episodes, delay and visible-mask availability
before selecting this as the live replacement. Human benefit remains a separate
question. The proposal-only review did not run tests/build/device operations.

### Current implementation verification — 2026-10-20

Rule trace version: `SUPPORTED_INDEPENDENT_FOCUS_EPISODES_V8`.

- Implemented supported episodes, separate candidate/confirmation clocks,
  anchor-only separation, a shared disjoint-witness dose ledger and preserved
  presentation recovery. Independent source review found no actionable issue.
- Replay's **Broad passage rereading** uses one ordinary forward pass followed by
  a broad return and two forward reread steps. Current separated-anchor and
  adjacent-anchor negative scenarios expose the spatial gate. Replay distinguishes
  detector demand, requested action and actual render/withheld status, including
  no available surrounding area to dim.
- First bounded run: both APKs built; **285 of 287 focused tests across 29 suites
  passed**. Two Replay checks failed. The generic timestamp assertion incorrectly
  required a refreshed guide's fresh evidence to precede its retained original
  presentation onset; it now tests those clocks separately. The new ABAB script's
  fourth 200ms hold ended before median transition plus supported residence;
  current-only scripts now allow 350ms and supply three independent blocks for
  the existing 1/2/3 repetition tiers. New explicit 22/18/16dp checks include the
  incomplete first fourth-visit prefix, adjacent-anchor rejection and independently
  qualified escalation. Legacy scripts retain their original timing.
- Source review found no actionable issue in these corrections. The owner approved
  the retry: **all 287 focused tests across 29 suites passed**, zero failures,
  errors or skips. Both debug APKs built and the focused whitespace check passed.
  Current evidence: `output/focus-supported-episodes-check-02/summary.json` and
  `build.log`. The initial failed run is retained separately in
  `output/focus-supported-episodes-check-01/`. Current app APK SHA-256:
  `2f3b2342c373ebcd3319350394fbc120ad675255d4b5c29f6c1abad0e8898e8c`.
  No device install/camera activity, staging or commit. Human benefit, phone gaze
  accuracy and live appearance remain unverified by these software checks.

### Earlier implementation evidence — historical, superseded where rules differ

- `ReadingLocationSupport.kt`: support/uncertainty, fresh word residence and reread prefix.
- `ReadingAidPolicy.kt`: V5 routing, ordinary guide, measured search emphasis and offer.
- `ReadingFocusPolicy.kt` / `ReadingBookmarkPolicy.kt`: independently retained response state.
- `ReadingAidOverlayView.kt` / `ReadingFocusContext.kt`: presentation and protected context.
- `ReadingAidTrace.kt`: route, support provenance, uncertainty and effective guide opacity.
- Broad-focus traces retain every counted sequence and its visit capture times,
  together with `reread_sequence_count`, `required_reread_sequences`,
  `reread_window_ms` and its explicit capture-span time basis.
- Replay includes **Broad passage rereading**. Article demo remains a labelled,
  manufactured display preview and does not validate the detectors.
  At the historical three-sequence checkpoint that scenario supplied three full
  sequences. The current scenario and one-episode rule are described above.

Three-sequence gate verification (2026-10-20): **275 focused aid/replay tests
across 28 suites passed**, zero failures/errors/skips. Both debug APKs built.
Boundary coverage includes first/two sequences staying off, third activation,
fourth-sequence strength, shared/relabeled capture exclusion, the inclusive
30,000ms full-span boundary, rolling expiry and pending-count invalidation.
Recovery tests use an explicit one-sequence override to isolate their existing
presentation contracts; production integration and Replay test the three-sequence
default. Independent source review found no actionable issue. Local evidence:
`output/focus-reread-gate-check-02/summary.json` and `build.log`. No device install
or camera activity; phone appearance, actual gaze accuracy and human benefit
remain untested for this update.

Historical integrated verification after the single-line correction, before the
three-sequence gate: **271 tests across 28 aid/replay suites passed**, with
zero failures, errors or skips. App and instrumentation APKs built successfully;
focused whitespace check passed. Private local build/test summary:
`output/aid-single-line-bookmark-check-01/summary.json`. No device install or camera capture
was performed. Physical appearance, actual phone mapping and human reading
benefit require their own checks. `agent-context.md` records the build checkpoint.
