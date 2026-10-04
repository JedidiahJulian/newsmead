# Scanpath-to-aid runtime checkpoint

**2026-10-04 current amendment:** article, pilot and replay now share the
definition profile V2 and policy v3. The [continuation marker](scanpath-continuation-marker.md)
replaces the post-P09 cue with an expected-line mismatch cue at fixed visibility.
The older magnifier descriptions and software/device checkpoints remain historical.

**Historical 2026-10-03 article update:** ordinary articles selected the separate
`DEFINITION_PROFILE_V1` and nearby definition form instead of the top magnifier.
An explicit demo cycles aids in the loaded article with camera stopped.
[Current behavior, studies and consultation boundaries](article-definition-aid.md)
superseded the original ordinary-article word route. At that checkpoint,
replay/pilot retained the magnifier profile; all three now use definition V2.

**2026-10-02 — branch `measurement/scanpath-pilot`.** The article now connects
raw MGazeNet observations to the live word/line engine, an independent aid
policy, adjustable displays, reader controls and an inspectable action trace.
This is an experimental implementation, not a validated difficulty detector or
demonstration of improved reading. The [evidence contract](scanpath-adaptive-aid-design.md)
retains the source findings, populations, hardware and transfer limits.

## Implemented route

```text
raw observation + capture/delivery time + current Android text geometry
  -> provisional I-DT candidates + independent word/line assignments
  -> finalized P01-P13 events and a separate established ongoing prefix
  -> current target + source-linked policy + reader preferences
  -> line overlay / expected-continuation marker / near-word definition / focus region
  -> decision, numeric support and drawing receipt in a bounded memory journal
```

`StudyConfig.SCANPATH_READING_AIDS=true` selects this route for ordinary article
reading. It bypasses the old weighted index, ordinal controller and demo cycle.
Touch validation retains its existing route. The schema-1 raw diagnostic logger
and frozen v6/schema-9 validation protocol keep their identities; the new aids
are suspended during that raw diagnostic condition.

### Aid rules and displays

| ID / form | Selection | Display and strength | Source relationship |
| --- | --- | --- | --- |
| A1 / `CURRENT_LINE_HIGHLIGHT_V1` | Reliable current-line assignment from a finalized candidate or established ongoing prefix, matching the latest eligible raw target. | Yellow line emphasis; adaptive baseline 0.25, or reader-selected fixed strength. | W24 gaze-following line form; N23 ruler-form comparison. This phone form and trigger are untested. |
| A2 / `CONTINUATION_LINE_START_CUE_V1` | Supported first-quarter then last-quarter progress on a fully visible LTR line; a supported landing differs from its visible successor within 2 s of the latest source-end evidence. | Fixed 6 dp blue gutter bar, alpha 240, on the **expected successor**. No marker for a normal next-line landing. Positive strength controls do not attenuate it; off/zero still disable it. | W24/S19 provide navigation/salience precedents. This selective trigger, timing and phone benefit remain experimental; a jump can be deliberate. |
| A3 / `CONTEXT_WORD_DEFINITION_V1` | At least 800 ms observed current-word residence, from supported finalized visit time plus a nonoverlapping established prefix, or the prefix alone; current matching word/line. | Near-word definition card; the source word stays highlighted. Consultation has its own lifecycle and excludes its interval from article sequences. | H03/M25 lexical-assistance precedents and transfer limits are recorded in [article definitions](article-definition-aid.md). The exact automatic trigger and phone benefit remain experimental. |
| A4 / `CONTEXT_FOCUS_REGION_V1` | Reader opts in; recent line A-B-A-B cycle **and** at least 1,000 ms line residence **and** configured slow frontier progression; target remains that line. | Light veil outside current line plus two lines on each side. Adaptive strength rises from 0.25 to 1 between 1,000 and 2,000 ms residence. The surrounding text remains readable. | N23 compares ruler forms; S14 cautions against hiding earlier words. Neither validates this cycle/slow-line trigger or dose. |

A3 takes priority over A4. A1/A2 may accompany word support as separately
identified actions. A backward return or cycle alone does not force a support
response. Forward movement updates location; it does not increase assumed
difficulty. A0 is no aid/suspension, not a rendered level. Other grey-bar,
underline and word-progress coloring variants remain candidate forms.

The ongoing prefix becomes available only after the minimum detector span and
sample count. It can add **observed time**, but never an extra finalized fixation
or P04 refixation. Duration sums exclude unobserved gaps. Word and line agreement
remain independent; a usable line does not manufacture a word assignment.

The historical `CONTEXT_WORD_MAGNIFIER_V1` comparison form remains available in
the separate experimental profile. It requires two finalized same-word
refixations and 750 ms residence, with strength rising over 750–1,500 ms. It is
not the current article/pilot/replay word aid.

## Experimental settings and historical magnifier comparison

These are versioned engineering settings, not published cutoffs, confidence
weights or calibrated probabilities of reading difficulty. Current definition
conditions use `DEFINITION_PROFILE_V2`; the separate magnifier comparison uses
`EXPERIMENTAL_PROFILE_V2`, condition `EXPERIMENTAL_AIDS_V1`. Shared settings and
the retained comparison parameters are listed below.

| Setting | Value / units |
| --- | --- |
| Candidate minimum span / samples | 100 ms / 3 samples |
| Dispersion | Sum of x and y ranges <= 0.5 physical line heights |
| Maximum sample / transition gap | 125 ms / 125 ms |
| Assignment agreement | 0.8, independently for word and line |
| Geometry settle guard | 150 ms after physical geometry change |
| P09 endpoint fractions | Departure >= 0.75; arrival <= 0.25 of line width; verified LTR |
| Continuation source progress / remembered-end window | First-quarter <=0.25 then last-quarter >=0.75; landing within 2,000 ms of latest end evidence; no limit on source-line reading time |
| Continuation marker appearance | Fixed 6 dp / alpha 240, 4 dp before source text in the existing 16 dp gutter |
| Current definition observed residence | 800 ms; no obligatory refixation count |
| Word minimum refixations / residence / strength saturation | 2 finalized refixations / 750 ms / 1,500 ms |
| Line residence minimum / strength saturation | 1,000 ms / 2,000 ms |
| P11 cycle window | 2,000 ms |
| P12 frontier window / maximum line rate / minimum observed fraction | 1,000 ms / 0.5 lines per second / 0.5 |
| Maximum evidence age | 250 ms, coherent capture/delivery/elapsed time |
| Action lifetime / non-guide cooldown | 1,500 ms / 2,000 ms |
| Adaptive minimum / maximum magnification | 0.25 / 1.5 times source text size |
| Focus context radius | 2 physical lines each side; strength changes veil prominence, not readable span |
| Pending sample / finalized history caps | 4,096 samples / 512 finalized candidates per Android engine generation |
| Journal / source-fact capacity | 2,048 records / 512 facts |

The generic engine still leaves P09 endpoint and P12 long/slow thresholds unset.
The article runtime supplies the explicit profile above. At 512 finalized
candidates the adapter emits `history_limit`, starts a new generation and carries
the last capture-time floor forward. Long paths crossing this resource boundary
are not joined. It bounds prefix-support work; it is not a human reading threshold.

New actions require recent coherent evidence with actual observation-delivery
provenance. Missing/invalid input, movement away from an anchor, geometry changes,
pause, provider restart and text changes clear stale actions.
The continuation marker retains its expected anchor while raw gaze is elsewhere;
supported arrival or distinct continued reading clears it. Its source and landing
support times are recorded separately, and it is not selected by P09.
A 100 ms timer and pre-draw check enforce expiration even when new gaze stops. Rendering verifies
the current geometry and action lifetime again.

## Reader controls and trace

The article diagnostics menu contains **Reading aids** and **Save aid diagnostics**.
Controls independently enable the aid forms, opt into focus, switch adaptive or
fixed strength, and set maximum/fixed strength from 0 to 100 percent. Positive
strength values do not attenuate the continuation marker. Preferences currently
last for that fragment session. Zero strength clears actions.

The historical magnifier uses a fixed **68 dp reserved strip** below the article
top bar while enabled. Current definition conditions reserve **0 dp**. The old
strip changes the available article viewport, preserves the paragraph,
and avoids layout changes whenever a word appears. A word that cannot fit is
suppressed and recorded, rather than truncated. Phone layout and preference
effects still need device checks.

The trace records configuration/version/model/calibration/text hashes, reader
preferences, action/form/condition IDs, UTF-16/line anchors, capture/evidence/onset
times, strength, reasons, study keys, numeric supporting event/prefix facts and
drawing receipts. Save is explicit and writes an app-private
`noBackupFilesDir/scanpath_aids_<uuid>.json`. It contains no word strings, raw gaze
coordinates, camera images or upload. It is an action audit, not a replacement
for the matched raw evaluation instrument.

Drawing receipts are **canvas/text draw proxies**, not verified physical screen
visibility or eye-to-cue latency. Bounded exports report dropped records, evicted
facts, missing support IDs and `complete_trace`; partial traces must not be treated
as complete sessions. Record the installed APK/build identity separately during
future collection.

## Historical software checkpoints and remaining work

The [trigger evidence review](scanpath-trigger-evidence-review.md) rechecks the
actual activation predicates against primary methods and results. It identifies
the GazePrompt OR-to-NewsMead AND adaptation and missing focus/dose validation.
The current replay screen provides the shared article presentation-only demo.
Its six four-second stages show baseline, line guidance, fixed continuation
marker, word definition, light focus and strong focus without detector evidence.
Stop/pause/layout changes clear it. This presentation is not a detection result.

The [interactive replay screen](scanpath-replay-screen.md) is the first milestone
of the pilot screen: raw-coordinate scenarios use actual Android text
layout and the same engine, policy and drawing views. Home and article diagnostics
provide entry points. Its private full-input trace explicitly labels synthetic
capture times and actual delivery times, with no camera/calibration access.
Controls, geometry changes and pause terminate a run. The guide records the
current build/device checks and limits. The separate
[live pilot screen](scanpath-live-pilot.md) now implements prompted placement/order
tasks and matched raw reading conditions; their human measurement remains pending.
All five real-activity tests pass on SM-G991B/API 35, including expected aid draw
proxies and causal trace links; [device check 03](scanpath-device-check-03.md)
retains the initial test lookup failure, correction and successful results for
that preceding build. The new continuation scenarios have no device result yet.

Focused synthetic JVM checks exercise extraction, prefix parity, policy choices,
adaptive/fixed strength, reset/suppression and the real engine-to-policy route.
The Python reference suite and fixture consistency check protect the shared
detector contract. Debug APK assembly and Android test compilation establish
build readiness. [Device check 01](scanpath-device-check-01.md) ran four synthetic
geometry/render tests on SM-A566B/API 36. The first attempt had one pass and three
failures from missing fixture layout parameters. After correction and an
owner-authorized retest, **all four passed**. [Device check 02](scanpath-device-check-02.md)
then passed the same four tests on SM-G991B/API 35 using identical APKs. Visible
article UI and live gaze behaviour still need separate checks.
Independent source review covered timestamp/geometry boundaries, causal support,
policy state, resource bounds and action provenance.

The current-build A56/API 36 check is [device check 04](scanpath-device-check-04.md):
the first attempt had eight demo/replay failures and five passing pilot checks.
The shared word renderer's available-height check now uses compound padding,
retaining true font/word fit and reserved height; corrected local builds and
56 JVM tests pass. The owner-authorized corrected A56 retest passes **all 15
checks**, including demo/replay, all five pilot checks and the centered-strip
regression. App-only word/focus previews were inspected. This is synthetic
software evidence; natural gaze and human benefit remain unmeasured.

The matched raw placement/continuous-order instrument is now built under its own
pilot identity, with capture-window agreement, private full-input traces and
no-aid/shadow/display conditions. Further authorized device inspection and
phase-specific study procedures precede participant tasks in
[pilot revision 6](scanpath-measurement-pilot.md). Natural fixation
validity, word/line accuracy, processing/render latency, unwanted cues and human
benefit remain unmeasured. Whole-path clusters, HMM/HSMM phases, a trained
difficulty model and calibrated decision weights are not implemented.
