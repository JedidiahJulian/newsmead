# Shared reading baseline v1

Implemented for `feature/local-study-collection`, 2026-11-06. This joins collection
and reference provenance; neither MD formula, existing fitting gate nor aid
decision rule is changed. Software checks do not validate reading instability,
tracking accuracy or benefit for older adults.

## Reader workflow and statuses

Start a **Reading** run in Study recording with the intended participant and
condition. Home → **Reading baseline** opens the existing baseline passage in
`fragment_article`, inside the same Activity/navigation surface as ordinary news.
Opening the screen starts no camera, ordinary article logging, network requests,
aid runtime, gaze dot or TTS. It uses the saved/locked article font settings and
scrolls the passage into the normal reading viewport.

The shared baseline and later article views with a matching active participant/
calibration source hold the toolbar expanded, so hand scrolling does not collapse
and expand the scroll-container viewport. Ordinary unlinked article views keep
their existing collapsing toolbar. The actual viewport is still measured; font,
reflow and aid-dock changes retain their boundaries and compatibility checks.
Once applied, the expanded-toolbar policy lasts for that view's lifetime, even
if its reference later becomes unavailable; a newly opened unlinked view uses
the ordinary toolbar policy. Deferred starts wait for layout, coalesce competing
requests, recheck live-camera prerequisites and are cancelled on pause/destruction.

Participant-facing ordinary articles currently hide the diagnostic gaze dot
(`GAZE_DEBUG_VISUALS=false`); raw observations and aid processing still run.
Explicit visible-feedback validation retains its own dot and protocol.

Press **Start**, dismiss the instructions and read naturally, scrolling by hand.
The existing R/D/F pipeline/collector receive the selected smoothed stream;
original provider observations are saved before mapping/exclusions, including
calibrated raw screen x/y, capture/delivery time and unavailable observations.
The footer shows valid R/D/F windows and covered time without resizing the
reading viewport. Press **Finish** to freeze R/D/F and complete the raw reference.

These outcomes remain independent:

- R/D/F reports its existing fit status and saves an active profile only on
  success. Its overlapping windows are not independent observations.
- Coordinate reference reports `RECORDED_PENDING_OFFLINE_FIT`. It is explicitly
  captured, not yet a successful coordinate profile. The strict Python adapter
  fits it after export and reports coverage/numerical/context failure or READY.
  R/D/F failure alone does not invalidate a completed coordinate capture.
- Leaving/pausing during capture stops it and marks `completed=false`; no
  reference is activated. No menu/background time is silently added to reading.

Continue into later articles in the same process with the same participant and
compatible calibration/settings. A source link can carry across those readings.
Before testing a new person, the researcher must complete and save that person's
calibration. The saved tracker calibration is device/screen compatible but is
not bound to a participant code. Its engineering quality PASS is not validated
reading accuracy; use the separate explicit accuracy checks as specified for the
session. Starting a study run alone does not replace the previous calibration.
After process restart, a new baseline is needed; saved eye-tracker calibration
and an active reading reference are distinct. Fonts/layout/acquisition changes
must not silently reuse an incompatible coordinate profile.

## Recorded contract and export

One source package has phase `reading_baseline` and metadata:
`baseline_protocol=shared_reading_baseline_v1`, attempt/participant/calibration,
`coordinate_reference_declared=true`, and
`coordinate_reference_contract=article_reading_surface_v1`.

`shared_baseline_result` records attempt, completion, R/D/F status, coordinate
pending status and the exact source elapsed bounds. Asynchronous camera shutdown
retains terminal observations/counters; fitting results hold package closure.
Writer failure or close timeout cannot activate a successful coordinate fit.
Original incomplete/loss-bearing packages remain available for inspection.

Each later article stores separate `rdf_reference` and `coordinate_reference`
records. The coordinate link names exact source package/session, attempt,
participant, installation, calibration, process clock, bounds and pending status.
Article metadata records calibration through the tested segment metadata builder.
Selected export includes the exact shared source independently of R/D/F success;
when both links name it, it is included once. Missing or incompatible sources
produce warnings rather than a guessed latest/closest reference.

The importer requires the explicit completed declaration and matching source
identity, chronology and bounds. A legacy `rdf_baseline` is never accepted merely
because it contains raw gaze. Existing explicit `coordinate_reference` analysis
is still supported. Sources and original bytes are never rewritten.

## Compatibility, time and numerical meaning

Shared baseline and linked article acquisition use stable-frame-rate preference,
smoothing, blink rejection and the recorded 650 ms provider output-age limit.
Coordinate analysis retains its independent 500 ms default. Samples that R/D/F
can receive may therefore be unavailable to the coordinate instrument; missing
scores remain null, while available zero remains zero. Legacy article acquisition
keeps its default when no active matching shared link exists.

`article_reading_surface_v1` explicitly hashes the physical scroll-container
viewport, column width/padding, font resource/style, font size/scaling/spacing and
language. It uses the same article resource for reference and evaluation. Actual
body clipping, text, colors, diagnostic settings and aid preferences remain in
full presentation metadata. Those aid settings are not geometric compatibility
requirements: the intended comparison can use an unassisted reference and an
assisted reading on the same surface. Scroll, aid, control and layout transitions
still create boundaries/epochs; no temporal exposure bridges them. A changed
viewport or font still causes unavailable mismatched scores. No earlier package
receives the new compatibility identity retroactively.

Raw-coordinate MD remains spatial departure from a frozen coordinate distribution,
not a scanpath-order measure or validated instability percentage. Its default
60,000 ms observed reference coverage, conditioning and missingness rules remain
unchanged. Passing R/D/F gates does not establish coordinate coverage or accuracy.

## Offline use and verification

Transfer the selected article and linked source, verify the received ZIP with
`tools/gaze/study_transfer_check.py`, then select the article session in
`notebooks/gaze_study_exploration.ipynb`. Its coordinate section resolves the exact
declared sibling/ZIP source, or accepts an explicit source path. It displays and
exports raw-coordinate MD/MD2/status, exact native-window summaries, separate
R/D/F and coordinate availability, source digest and frozen fit/config provenance.
Unavailable or interrupted sources do not produce replacement scores.

Focused JVM checks cover shared source reuse/invalidation, failed R/D/F with an
independent coordinate link, exact export dependencies, metadata requirements and
surface identity. Python checks cover completed/interrupted/legacy sources,
identity/bounds/leakage, directory/ZIP resolution and missing/zero/mismatch cases.
`SharedBaselineInteropTest` uses the real Kotlin package writer and producer
metadata with a synthetic 65-second ellipse reference; the independent Python
checker expects MD2 0/2/4 and mean MD2 2 within floating-point tolerance with default gates, despite unavailable
R/D/F. It does not measure a camera or human reader.

Evidence belongs under `output/shared-baseline-2026-11-06/`. Build/test APKs and
synthetic fixtures are local artifacts. Live phone layout/provider/endurance and
real shared-reference fitting still require a separately authorized rehearsal.

2026-11-06 software checks: both APKs build; 39 focused Gradle JVM tests and
56 Python study tests pass. The writer/catalog host suite passes 25 tests (with
catalog overlap). Independent review's missing calibration-provenance finding
was fixed with a producer regression; follow-up has no actionable issues. The
updated notebook executes all 22 source cells plus a legacy null-MD QA cell
without errors. Repeated boundary extraction is cached once per analysis without
changing point/window outputs. The synthetic cross-language receipt reproduces
the expected values above, including independent coordinate availability.

App APK SHA-256:
`7d77526fba352ad23d405cd41741dbf556ad68b9d881b8929bda9adcdbf8bda8`.

Reproduce the cross-language check from cmd after the named JVM test:

```cmd
python tools/gaze/check_shared_baseline_interop.py --output output/shared-baseline-2026-11-06/kotlin-python-interop.json
```
