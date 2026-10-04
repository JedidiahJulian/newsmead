# Interactive scanpath replay screen

**2026-10-04 line-start spacing:** the existing 16 dp body padding is now used
as a gutter for the article's marker renderer, with a 4 dp text gap. Both synthetic
Run and the presentation demo use it. Scripted coordinates remain derived from
the actual rendered text, including padding; a clipped gutter withholds the cue.

**2026-10-04 current article aids:** new Runs use `DEFINITION_PROFILE_V2` /
`REPLAY_DEFINITION_V2`, the current line/focus views, gutter marker and near-source
word definition card. The hidden legacy dock reserves **0 dp**; popup host is the
viewport FrameLayout. Controls name word definitions. Replay uses the shared
dictionary adapter with network disabled: the 75-entry original glossary returns
covered definitions immediately and uncovered words honestly show unavailable.
Trace configuration records `dictionary_mode=bundled_only`,
`ui_layout_id=CONTEXT_DEFINITION_GUTTER_V1` and `reserved_word_strip_dp=0`.
Historical magnifier traces and device passes below belong to their earlier
profile and must not be pooled with current definition runs. Current local checks:
**64 focused JVM tests pass**, app/test APK assembly, XML parsing and whitespace
checks pass. Android fixtures compile for detector-only scenarios, current
definition/line-cycle display, consultation raw exclusions, six-stage offline
demo and replay-to-demo teardown. Phone execution remains pending. Independent
source review found no actionable P1/P2 issue; its two coverage limits were
addressed with compiled regressions. Exact APK hashes and copied JVM XML:
`output/evaluator-current-aids-check-01/summary.json`.

**Continuation-marker amendment:** [exact current rule](scanpath-continuation-marker.md).
`continuation_return` and `continuation_skip` establish source beginning/end,
land on another row, and cue the expected successor with fixed visibility. The
ordinary `return_sweep` still verifies P05/P09 but expects no marker. New headers
record policy v3, fixed appearance and `continuation_evidence`; the 64-test/device
checkpoints above refer to the preceding profile.

**2026-10-02 — branch `measurement/scanpath-pilot`.** First milestone of the
interactive pilot screen: scripted raw coordinates run through actual Android
text geometry, the existing scanpath engine, the aid policy and real aid views.
This is a software inspection tool. The subsequent
[live pilot instrument](scanpath-live-pilot.md) now implements prompted tasks and
matched raw reading conditions; its human measurement remains pending.

## Use

1. Open **Scanpath replay** on Home, or from the article diagnostics menu.
2. Select a scenario and press **Run**. The hollow blue dot marks scripted input.
   The status shows detected patterns, chosen aid and current strength.
3. Use **Controls** to enable individual aids, choose adaptive/fixed strength,
   and set the strength cap. Focus is off by default; disable word definitions and enable focus for the
   cycle scenario to inspect that rule without a definition consultation. Settings apply to the next Run and last for
   this activity session.
4. **Stop**, scrolling, changing the scenario/controls, or leaving the screen
   terminates the run and clears its aids. Run again from the new visible layout.
5. **Trace** shows the app-private trace path and lets you copy it. Each explicit
   Run writes a separate synthetic trace; pressing Trace during a run stops it.

Direct launch from CMD, without opening a camera-enabled article:

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s R5CY40Y2C5W shell am start -n com.newsmead/.activities.ScanpathReplayActivity
```

That serial is the checked A56. Use your connected phone's serial for another device.

The replay activity never creates a gaze provider, requests camera permission,
or reads calibration. Entering it from an article stops the existing provider;
returning to that article follows the article's existing restart behavior.

## Explicit presentation demo — 2026-10-04

Press **Demo cycle** to inspect the article's six stages without camera input or
detector evidence: no aid, current-line highlight, destination-line-start marker,
word definition, focus at 25%, then focus at 100%. Previous/Next step manually;
stages repeat every four seconds. The shared article presenter chooses a wholly
visible actual source word, preferring a covered offline entry. It preserves
UTF-16 offsets and the original body. Two complete visible lines suffice;
focus explicitly reports unavailable if fewer than five lines fit.

Stop, pause, scroll, changed scenario/controls or changed text geometry clears
the display. Runtime consultation/render callbacks are detached before entering
this presentation. No session, provider, policy, calibration, trace or replay
result is created; manufactured presentation actions contain no detector evidence.

The following checks describe the **earlier magnifier build**, not this revision.

The corrected build passes 56 focused JVM tests and app/test APK assembly.
Four camera-free demo activity tests and the centered-strip regression pass on
A56/API 36. App-only word/focus previews were inspected for readable labels/text
and full-word fit. No camera or human measurement is involved.

**2026-10-03 A56 attempt:** after explicit owner installation/test approval,
all four demo activity tests failed waiting for ready navigation. Four replay
tests stopped on tiny negative callback lag incorrectly classified as
`scheduler_late`; the capped-trace test passed. [Device check 04](scanpath-device-check-04.md)
retains exact APK hashes, failures and limits. This supersedes the pending
execution status for the installed build. Local timing/fit fixes passed source
review, all 56 focused JVM tests and both APK assemblies. The owner-authorized
corrected retest then passed **all 15 checks in 50.908 s**, including all four
demo and all five replay tests. All five replay scenarios completed with causal
trace/aid draw assertions. Earlier S21 passes apply to earlier APKs; this
corrected build has been checked only on A56.
The [trigger evidence review](scanpath-trigger-evidence-review.md) contains the
decision flowchart, primary findings, numeric origins and panel questions.

## Input-to-display route

```text
visible rendered word rectangles + current physical screen origin
  -> scripted raw x/y and scheduled synthetic capture times
  -> actual callback delivery times
  -> ArticleScanpathSession: raw word/line assignments and geometry guard
  -> ScanpathEngine: provisional prefixes and finalized P01–P13 events
  -> ArticleReadingAidRuntime: current definition policy + reader controls
  -> ReadingAidOverlayView / ReadingAidDefinitionPopup
  -> decision and drawing-proxy receipts in a full synthetic trace
```

Scripts do not inject pattern labels or force aid actions. They choose positions
inside wholly visible words from the current rendered layout. Impossible layouts
produce an explicit setup error rather than fabricated word/line targets. The
body is newly written synthetic prose, not news or a comprehension stimulus.

## Scenarios

The table gives minimum expected outputs, not an exhaustive list of every event
the detector may emit. Terminal closure also produces continuity boundaries.

| Scenario | Scripted observation | Expected patterns | Expected aids when enabled |
| --- | --- | --- | --- |
| Forward/backward navigation | Four consecutive visible words on one line; advances, a jump, backward return and revisits. | P01, P02, P03, P07, P10 | Current-line highlight |
| Observed return sweep | Right end of one verified LTR line to left start of the next; another word finalizes the landing. | P05, observed P09 | Current-line highlight; no continuation marker |
| Continuation return | Source beginning → end → earlier row, then expected successor. | P08 and raw ordered line observations | Fixed marker on the expected successor, clears on arrival |
| Continuation skip | Source beginning → end → a later row beyond the successor, then expected successor. | P06 and raw ordered line observations | Fixed marker on the expected successor, clears on arrival |
| Word refixations/residence | Separated positions inside one sufficiently wide word, then ongoing residence. | P04, P13 with definitions enabled; P04, P12 in detector-only run | Current-line highlight and word definition |
| Line cycle/residence | Adjacent A–B–A–B lines, then distinct word positions on B with enough residence and configured slow frontier progression. | P08, P10, P11, P13 with definitions enabled; P12 in detector-only run | Current-line highlight and definition; disable definitions and enable focus to inspect the focus rule |
| Loss/recovery | Established word/line, eight explicit missing observations, then fresh evidence. | P13 | Clear on loss; current-line highlight after reacquisition |

The live/offline engine still implements P01–P13. This initial screen does not
provide a dedicated P06 forward-multiline scenario or every counterexample.
The deterministic reference suites cover broader cases. These scripts exercise
the experimental profile in [the runtime guide](scanpath-aid-runtime.md); they
do not establish that their paths indicate difficulty or that the aids help.

## Time, coordinates and interruptions

- Coordinates are physical screen pixels. Line rows are text-local; origin and
  visible-screen rectangle are recorded separately. Word offsets are UTF-16.
- Script points are spaced at most 50 ms apart and begin 200 ms after the replay
  origin, allowing the existing 150 ms geometry settle guard.
- `capture_ms` is a **scheduled synthetic elapsed-realtime** timestamp.
  `delivery_ms` is the actual elapsed-realtime callback timestamp. Neither is a
  camera exposure or measured eye-to-cue latency.
- A callback more than 100 ms late terminates the run as `scheduler_late`; delayed
  input is not silently presented as a timely successful run.
- A fractionally early Handler callback retries the same ordinal/capture deadline
  before moving the cursor or sending engine input. It uses a fresh actual
  delivery timestamp on retry and checks cancellation/geometry each time.
- The activity appends 1,000 ms of ordinary observations at the final eligible
  coordinate to allow inspection of the last aid. This tail can add observed
  residence/events. The full plan includes it.
- Any physical geometry change terminates the existing script, including scroll
  or a changed viewport. No coordinates are reused across that boundary.
- Pause, Stop, controls and scenario changes clear aid actions and cancel input.
  The aid runtime retains its ordinary lifetime and draw-time expiry checks.

## Trace contract

Files are app-private `noBackupFilesDir/scanpath_replay_<uuid>.jsonl`.
They contain only the synthetic body/input and software outputs, with no human
reference, camera images, calibration fingerprint or upload.

`definition_consultation` records preserve active/open versus close, `at_ms`
and reason. Consultation and late boundary captures retain raw x/y and capture/
delivery times with `definition_*` exclusions and null article AOIs. The source
anchor stays fixed until dismissed; stopping closes consultation before session
and log closure. Popup reading never becomes article fixation evidence.

Records include the complete planned input, settings/hash and source identity,
actual geometry, assigned raw samples with exclusions, finalized candidates,
ongoing prefixes, pattern metrics/support IDs, decisions with full actions and
anchors, strength, evidence/onset/expiry times, and drawing receipts. Each record
has a sequence number and actual recording time. The footer reports samples,
patterns, chosen aids and drawn proxies. `software_expectations_met` requires a
completed run and the expected patterns, choices **and** drawing proxies for
enabled aids; it is not a human-validity result.

Writing starts with `.jsonl.partial`. A final `.jsonl` name is published only
after successful writing/close. The 20,000-record limit reserves a termination
record; capped traces retain an incomplete footer and `.partial` name. Require
both a final filename and `complete_trace=true` footer before accepting a trace.
A complete trace can still describe an interrupted or unsuccessful scenario.

Draw receipts are canvas/text drawing proxies. They do not establish physical
screen visibility, a reader's gaze location, fixation validity or aid benefit.
Installed APK hashes belong in the host device record, separately from runtime
version/configuration identity.

## Checks and next package

Focused JVM fixtures cover script geometry, translated origins, pattern output,
refixation finalization, variable magnification, focus gating and loss recovery.
Current source Android fixtures check all five detector scenarios with aids disabled, current definition consultation/raw exclusions, line/focus drawing receipts,
timestamp causality and prior support IDs, zero-strength withholding, scrolling,
pause without stop, and incomplete capped traces. All **34 focused JVM tests**
passed for the preceding magnifier version; debug and Android-test assembly passed for that version. [Device check 03](scanpath-device-check-03.md)
records an initial test-ID lookup failure, its correction and owner-authorized
retest, plus the final layout check: **all five tests passed on SM-G991B/API 35**.
The synthetic preview was visually inspected and clipped button labels corrected.
Existing phone geometry checks remain separate:
[check 01](scanpath-device-check-01.md), [check 02](scanpath-device-check-02.md).

The separate live pilot now implements raw placement/continuous-order tasks
and their matched recording contract. Further authorized device execution and
phase-specific study procedures precede participant evaluation. Replay supplies
the scripted inspection route alongside that live instrument.
