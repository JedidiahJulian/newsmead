# Interactive scanpath replay screen

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
   and set the strength cap. Focus is off by default; enable it for the cycle
   scenario to inspect that aid. Settings apply to the next Run and last for
   this activity session.
4. **Stop**, scrolling, changing the scenario/controls, or leaving the screen
   terminates the run and clears its aids. Run again from the new visible layout.
5. **Trace** shows the app-private trace path and lets you copy it. Each explicit
   Run writes a separate synthetic trace; pressing Trace during a run stops it.

Direct launch from CMD, without opening a camera-enabled article:

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s R5CR60YSJ2X shell am start -n com.newsmead/.activities.ScanpathReplayActivity
```

The replay activity never creates a gaze provider, requests camera permission,
or reads calibration. Entering it from an article stops the existing provider;
returning to that article follows the article's existing restart behavior.

## Input-to-display route

```text
visible rendered word rectangles + current physical screen origin
  -> scripted raw x/y and scheduled synthetic capture times
  -> actual callback delivery times
  -> ArticleScanpathSession: raw word/line assignments and geometry guard
  -> ScanpathEngine: provisional prefixes and finalized P01–P13 events
  -> ArticleReadingAidRuntime: existing experimental policy + reader controls
  -> ReadingAidOverlayView / ReadingAidWordView
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
| Observed return sweep | Right end of one verified LTR line to left start of the next; another word finalizes the landing. | P05, observed P09 | Current-line highlight and reactive destination-line-start cue |
| Word refixations/residence | Separated positions inside one sufficiently wide word, then ongoing residence. | P04, P12 | Current-line highlight and variable-strength word magnifier |
| Line cycle/residence | Adjacent A–B–A–B lines, then distinct word positions on B with enough residence and configured slow frontier progression. | P08, P10, P11, P12 | Current-line highlight and optional focus region |
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
Real-activity Android tests check all five scenarios, actual drawing receipts,
timestamp causality and prior support IDs, zero-strength withholding, scrolling,
pause without stop, and incomplete capped traces. All **34 focused JVM tests**
passed; debug and Android-test assembly passed. [Device check 03](scanpath-device-check-03.md)
records an initial test-ID lookup failure, its correction and owner-authorized
retest, plus the final layout check: **all five tests passed on SM-G991B/API 35**.
The synthetic preview was visually inspected and clipped button labels corrected.
Existing phone geometry checks remain separate:
[check 01](scanpath-device-check-01.md), [check 02](scanpath-device-check-02.md).

The separate live pilot now implements raw placement/continuous-order tasks
and their matched recording contract. Further authorized device execution and
phase-specific study procedures precede participant evaluation. Replay supplies
the scripted inspection route alongside that live instrument.
