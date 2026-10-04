# Live scanpath pilot instrument

**2026-10-04 line-start spacing:** the existing 16 dp body padding is now used
as a gutter for the same marker renderer selected in the article. The cue ends
4 dp before the first letter in ordinary-reading display conditions. No padding
changes occur between conditions; word/line targets still come from the actual
rendered TextView geometry. A clipped gutter withholds the marker. Prompted
placement tasks keep assistance off as specified below.

**2026-10-04 current article aid profile:** new runs use `DEFINITION_PROFILE_V2`
and `PILOT_DEFINITION_NO_AIDS_V2`, `PILOT_DEFINITION_SHADOW_V2` or
`PILOT_DEFINITION_DISPLAY_V2`. The current near-word card, highlight, close control
and consultation gate replace the word magnifier. Its 800 ms trigger remains
exploratory. All conditions reserve **0 dp** for a word strip and keep the same
viewport. Settings control word definitions. Live runs use the 75-entry original
glossary and online fallback; explicitly synthetic checks use the same adapter
with networking disabled. Header/configuration record `dictionary_mode`,
`ui_layout_id=CONTEXT_DEFINITION_GUTTER_V1` and `reserved_word_strip_dp=0`.
Historical magnifier runs/device checks retain their earlier meaning; do not pool
them with the new profile.

The [continuation marker](scanpath-continuation-marker.md) replaces the old
post-P09 cue. In ordinary-reading shadow/display runs, supported first-quarter
then last-quarter progress remembers the next visible line; a supported landing
elsewhere offers a fixed marker at that expected line. Normal next-line arrival
does not choose it. Definition consultation, layout/input boundaries and expiry
clear it. Prompted tasks remain aids-off. Configuration is policy v3; source/end/
landing times and observed lines are saved in `continuation_evidence`. V1 runs
and the 64-test checkpoint below describe the preceding build.

Current local checks: **64 focused JVM tests pass**; app/test APK assembly, XML
parsing and whitespace checks pass. Updated camera-free activity fixtures compile
for all prompted tasks, matched no-aid/shadow/display geometry, current definition
choices and offline consultation/raw exclusion/resumption. Their phone execution
is pending. Independent source review found no actionable P1/P2 issue. Exact
APK hashes and copied JVM XML: `output/evaluator-current-aids-check-01/summary.json`.

**2026-10-03 — branch `measurement/scanpath-pilot`, based on `50ff2b7`.**
The separate pilot screen adds prompted placement/order tasks and matched raw
recording to the completed scanpath-to-aid prototype. It uses the existing
MGazeNet provider, raw word/line adapter, P01–P13 engine, experimental aid policy
and actual Android aid views. It creates a separately identified
`newsmead_scanpath_pilot_v1` JSONL instrument. Existing frozen v6/schema-9
validation and schema-1 natural-reading diagnostic identities remain intact.

This is an implementation checkpoint. Phone-only prompts supply intended
targets conditional on adherence. They cannot independently establish actual
eye position, physiological fixation boundaries, reading difficulty or benefit.
Research sources and transfer limits remain in the
[aid evidence contract](scanpath-adaptive-aid-design.md) and
[detector registry](scanpath-detectors.md).

## Start a run

1. Open **Scanpath pilot** on Home or in article diagnostics. Opening the screen
   is idle: it does not request camera permission or create a trace.
2. Choose practice text or a bundled study passage. The latter is original
   fictional study prose, not sampled real news. Font is English, 22 dp;
   native layout, physical line IDs and UTF-16 word offsets are recorded.
3. In **Settings**, enter pseudonymous linked-set and protocol codes, allocation
   (`PRACTICE`, `DEVELOPMENT`, `EVALUATION`), target duration and settling interval.
   A non-practice allocation requires a named protocol. The app does not freeze
   that external study plan or verify its completeness.
4. Select a prompted task or **Ordinary reading**, then press **Start live**.
   This explicitly starts the front camera. A compatible saved MGazeNet
   13-point calibration is required; the screen does not recalibrate automatically.
5. Follow the blue target until it changes. Prompted tasks force aids off and
   keep the gaze dot hidden. Ordinary reading has no blue instruction targets.
6. **Stop** ends a run. Pause/backgrounding also stops it. Prompted scrolling or
   reflow clears the prompt and terminates the run; ordinary reading may scroll,
   with the existing engine/policy geometry resets.
7. After live shutdown, record adherence before viewing the result, or choose
   **Skip**. The category and optional note are a report, not verified gaze truth.
   **Trace** provides the app-private filename after finalization.

Practice defaults are 2,000 ms per target and 300 ms excluded after each draw
onset. These are editable engineering settings, not published response-time
cutoffs. Settings and controls are locked during a run. An initial 20-second
no-coordinate timeout, 2-second undrawn-prompt timeout and 10-minute resource
limit terminate stalled runs.

## Prompted tasks

Targets are derived from actual wholly visible word/line geometry. Large words
and complete lines are preferred for these initial tasks; selection is
deterministic and is not a spatially representative accuracy sample. A task
fails explicitly if the visible layout cannot supply its targets. Physical
blank paragraph rows retain their indices and are never selected as targets.

| Task ID | Intended sequence | What it exercises / reference limit |
| --- | --- | --- |
| `word_hold` | One visible word | Conditional word/line assignment and observed residence. A hold cannot label refixations. |
| `line_hold` | One full visible line | Conditional line assignment; no prescribed word-order reference. |
| `forward_words` | Three adjacent words A → B → C | P01 forward-word navigation, conditional on following the prompts. |
| `backward_words` | The same words C → B → A | P03 backward-word navigation and P07 same-line backward returns; no inference of difficulty. |
| `nonadjacent_words` | A → C → A | P02 forward word jump, P03 backward return and revisit opportunities. |
| `next_line` | Line end → next line start | Adjacent line advance and P09 geometric return-sweep candidate; verified LTR endpoints ≥75% / ≤25%. Not independently measured saccade truth. |
| `earlier_line` | Lower line B → preceding line A | Earlier-line return opportunity. |
| `multiline` | Line A → A+2 | P06 multi-line advance opportunity, rather than two adjacent advances. |
| `line_cycle` | Line A → B → A → B | Line revisit and P11 alternation opportunities; no difficulty label. |
| `repeated_word_hold` | A → A → A, cue repeated | Negative control: the reference stays on one word. Cue repetitions must not manufacture distinct physiological P04 refixations. |

The session/engine runs continuously across cue changes. It is not flushed or
restarted to manufacture fixation transitions. These ten tasks do not provide
independent reference labels for every detector. In particular, physiological
P04, natural P08/P10 episodes and P12 slow-reading meaning need their own
reference/design. The existing replay route exercises algorithm behavior.

### Recorded score

| Quantity | Definition / denominator |
| --- | --- |
| Prompt interval | Actual first canvas-draw proxy until next proxy or task stop, `[onset, end)`, using capture time. Requested time is also retained. |
| Interval samples | Samples with usable strictly increasing nonnegative capture clocks in that interval. |
| Scorable samples | Interval samples after that target's configured settling period. |
| Eligible samples | Scorable samples with finite raw XY, raw eligibility and the original geometry snapshot. This does not imply engine acceptance or accurate placement. |
| Exact word/line agreement | Exact assigned target among eligible samples, calculated independently by resolution. Empty denominators and unavailable word references have null rates. |
| Unavailable among scorable | Ineligible scorable samples plus eligible-but-unassigned samples at that resolution. Retain this beside conditional agreement. |
| Prompted opportunities | All planned targets, actual drawn targets, targets with scorable input and targets with at least one exact sample. Unshown targets remain in the table. |
| Ordered raw runs | Collapse adjacent identical AOIs after only the first settling period. Later settling is excluded for agreement but does not hide movement from descriptive paths. Unknown input and >125 ms capture gaps break the affected resolution independently. |
| Descriptive alignment | Global insertion/deletion alignment of intended and observed runs. It ignores prompt windows and continuity breaks. Segment paths and maximum matches in one segment are retained. It is not temporally matched event recovery, event recall or event precision. |
| Engine acceptance | Exact `ScanpathEngine.onSample` acceptance callback, retained separately from raw eligibility and scoring. Terminal deliveries are not processed after stop. |

The capture-window rows handle callbacks delivered after a cue switch by their
capture time. Clock-invalid, reversed/duplicate, invalid-coordinate, ineligible,
geometry-mismatch and capacity-loss counts remain explicit. Exclusion counters
can overlap; do not add them as disjoint missingness categories. No expected
reading order fills a gap. Scorer capacity is 20,000 samples; lost capacity
marks the score incomplete.

## Ordinary reading and matched conditions

| Setting | Recorded processing | Display |
| --- | --- | --- |
| `NO_AIDS` | Raw stream, engine events, policy disabled | No reading aid |
| `SHADOW` | Raw stream, engine events, candidate/suppressed/chosen policy decisions | No reading aid |
| `DISPLAY` | Same route and experimental policy, reader-selected forms and strength | Actual aid views with drawing receipts |

The legacy strip is hidden and reserves 0 dp in all current pilot conditions,
including prompted tasks, keeping the available viewport comparable. Definitions
are overlays in the text viewport. Reading has a hidden gaze dot, no scoring
target reference and no prompted score. A reported interruption/adherence note
cannot label every reading moment.

Historical 2026-10-03 magnifier display correction: the child used 16 dp horizontal and 10 dp vertical
padding, preserving the parent height while leaving 48 dp for the maximum
magnifier font. The [trigger review](scanpath-trigger-evidence-review.md) records
the font-height issue and explicit demo checks.

Match linked-set codes, calibration/model/build, passage/geometry, font, settings
and exposure conditions in the external plan. A linked code alone does not make
trials matched. Start/end calibration hashes and a change flag are retained;
record the installed APK SHA-256 separately. The screen's practice task order
and convenient target selection do not implement randomized study allocation.

These reading conditions permit extraction and display inspection. They do not
yet implement independent comprehension questions, momentary help-response
windows, trial randomization, temporally matched event scoring or an intervention
outcome study. Complete those phase-specific procedures in the
[pilot plan](scanpath-measurement-pilot.md) and
[session template](scanpath-pilot-session-record.md) before human collection.

## Time and stop boundaries

Live capture time is CameraX's image timestamp in ns divided by 1e6; delivery,
prompt proxies and task stop use Android elapsed-realtime ms. The provider
checks positive/nonfuture timestamps. This code check does not independently
establish clock alignment or end-to-end camera latency on a phone. Treat shared
clock alignment as a measurement assumption to check for collection.

The first coordinate delivery starts the live prompt timeline, avoiding use of
model startup as the first target's observation window. Each target's timer
starts from its actual first draw proxy. A canvas draw is not verified screen
visibility, a physiological eye landing or a display-photon timestamp.

Stop closes the engine and clears aid views, then waits for the provider's
terminal callbacks/counters before scoring and finalization. Those trailing raw
callbacks remain in the trace and score denominators as unknown/ineligible;
they are not remapped through a paused or changed layout. Capture after task end
stays outside the task intervals. Terminal null-clock counters remain in global
exclusions. Exact arrival/busy-drop counts come from the stopped observation.

## Private trace and host summary

`noBackupFilesDir/scanpath_pilot_<uuid>.jsonl.partial` is written during the run
and adherence report. Only successful write/close publishes `.jsonl`. A final
filename and complete footer are both required. Process death, write failure or
the 100,000-record cap leaves a partial file; no truncated trace is treated as a
complete audit. Completed traces may still describe interrupted/failed trials.

Records retain body/text identity, settings, geometry, raw provider coordinates,
raw-derived assignments, exact engine acceptance, candidate/finalized evidence,
patterns, decisions, anchors, strengths, prompt/draw proxies, adherence category
and terminal counters. There are no camera images or uploads. Free-text notes
may contain private information; participant copies belong in approved private
storage, outside this repository.

Current display runs also retain `definition_consultation` open/close records.
Every raw callback is still recorded while the card is open. Its derived sample
has no article target, is ineligible and is not passed to the article engine;
the consultation and late-boundary exclusions identify why. Opening/closing
breaks sequence continuity. Shadow runs make choices without displaying a card,
so they do not have a consultation exclusion. Matched viewport does not imply
identical observed paths between display and shadow.

For an explicitly authorized trace, export its exact filename with `adb exec-out
run-as com.newsmead cat no_backup/<filename>` into approved private storage.
CMD redirection preserves its UTF-8 bytes. Then inspect it locally:

```cmd
python tools\gaze\summarize_scanpath_pilot.py "C:\private-study\scanpath_pilot_<uuid>.jsonl" --output "C:\private-study\summary.json"
```

The summary validates sequence, finalization, body hash, raw/sample pairing,
acceptance counts and retained prompt targets. It omits body text, raw XY,
target bounds and free-text notes. It preserves null denominators and labels
descriptive alignment. Exit 0 means this bounded integrity check passed;
exit 1 reports inconsistencies/partial status; exit 2 reports unreadable or
unsupported input. It does not reclassify gaze or prove human validity.

## Earlier magnifier software checks and remaining gates

- **51 focused JVM tests pass**, including 17 pure task/scorer tests. These cover
  native line IDs, visible targets, delayed capture assignment, half-open windows,
  settling, missingness, resolution-specific breaks, unshown targets, capacity,
  unavailable references and the repeated-hold negative control.
- **8 host contract tests pass** for the summary tool, including incomplete files,
  count/hash/target corruption, terminal pairing and omitted private fields.
- Debug app and Android test APKs build. Five new camera-free Android activity
  tests are compiled for idle behavior, all task plans/raw scoring, setup-failure
  contamination, scroll/pause termination and shadow/display viewport equivalence.
- Focused independent source review found no remaining issue in the corrected
  counters, prompted/descriptive score distinction, geometry termination or
  terminal unknown-data retention.

Five synthetic activity checks pass on A56/API 36 in device check 04.
Earlier replay/device results apply to their dated builds. Live camera execution,
shutdown timing, actual prompt visibility, human adherence, natural scanpath
accuracy, independent event scoring and aid effectiveness remain unmeasured.
Do not begin participant measurement merely because software checks pass.

**2026-10-03 A56/API 36 software execution:** all five pilot activity tests
passed under synthetic input, including all ten prompted task plans, raw scoring
and provenance, idle/setup reset behavior, scroll/pause and matched shadow/display
viewports with distinct rendering. [Device check 04](scanpath-device-check-04.md)
retains installed hashes and the first combined 14-test runner, including its
eight demo/replay failures. After local timing/fit fixes and explicit owner
retest authorization, all **15 checks pass** (including the same five pilot
checks). The corrected APKs are installed. No camera or human measurement ran;
actual human target placement, natural paths and benefit remain unmeasured.
