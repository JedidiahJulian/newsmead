# Study collection: connected-phone rehearsal, 2026-11-03

Branch: `feature/local-study-collection`. User authorized installation and testing
on the connected Samsung A56 (Android SDK 36, `arm64-v8a`). This checkpoint covers
camera-free software checks. A user-assisted live reading check is the next step.

## Results

- Full `assembleDebug`, `verifyMgazeNetDebugApk` and instrumentation assembly pass
  with the normal vendor/model/packaging guards enabled; no exclusions.
- Installed the app and test APK with `adb install -r -t`. App data and camera
  permission were retained. The existing gaze calibration file remains present
  with its original 2026-10-14 modification time. Presence does not establish
  compatibility or current accuracy. Gaze calibration persists; the active R/D/F
  reading baseline remains in memory and must be collected again after restart.
- **51 unique on-device tests pass after fixture corrections.** Initial collection
  suite: 6/6. Existing native/scanpath/aid suite: 43/45 initially. Reran only the
  two corrected existing fixtures and the corrected collection-export fixture:
  3/3 pass. Already passing tests were not repeated.
- Six isolated synthetic ZIP exports were copied over USB with matching SHA-256
  hashes. The actual offline importer accepts all seven selected packages,
  including the intentionally recovered incomplete package. Manifest and record
  JSON schemas also accept all 77 retained records.
- Android camera access history shows no new app camera access during the
  automated checks. No live provider, calibration capture, camera images, private
  recordings or cloud transfer were used. Synthetic artifacts remain available.

Evidence directory: `output/study-phone-check-01/` (local, ignored by Git).
`summary.json` contains APK hashes, per-test results, package counts and limits.
Build logs: `final-build.log`, `fixture-test-build.log`. Device logs:
`collection-device.log`, `native-aids-device.log`, `fixture-rerun-device.log`.
`device-import-summary.json` and `device-artifacts/*/import-*/receipt.json`
retain importer receipts. Earlier failed fixture artifacts were preserved;
the summary selects the corrected controls export by its hash and receipt time.

## What the checks establish

The collection suite exercises visible Start/Stop controls using Espresso,
laid-out Android text and original numerical values, writer close, held baseline
result ordering, the three-second terminal timeout, separate late receipts,
capture-aligned settings and valid-prefix recovery. Hashes/counts are checked in
the FileProvider ZIP and independently on the transferred copy. Baseline and
Scanpath payloads supplied here are fixtures, not a live baseline fit or an
ArticleFragment-to-detector integration test. Recovery constructs an interrupted
partial file; it does not kill a real collecting process. FileProvider is opened
in the app; external receiver permissions and a human-operated share destination
remain separate checks.

The existing suite covers camera-free native OpenCV/MNN execution, stable finite
258-feature inference, synthetic SVR fitting, smoothed Scanpath missingness and
consultation/scroll rejection, runtime routing and rendering of focus regions,
guides, line-start markers and definition offers/popups. It does not establish
that a reader's gaze will trigger the intended aid. One corrected runtime fixture
intentionally exercises the legacy V4 supplied-residence path; it is not evidence
for the live V5 detector's human validity.

Coordinate MD remains offline. These very short synthetic exports have no fitted
compatible earlier reference; unavailable scores are expected. Numerical reference
fitting/scoring is covered by the existing core/importer tests. Actual on-phone
reference collection, compatibility and usable observed-time coverage remain open.

## Changes and review

- `tools/gaze/mgazenet/restore_armv7_vendor.py` restores only the three missing
  ARMv7 libraries from the project's already pinned official MNN 3.6.1 archive.
  Archive and library hashes are verified before writing, and differing files are
  preserved. The original guard and model assets are unchanged. The first Python
  HTTPS attempt failed certificate validation; Windows certificate-validated
  `Invoke-WebRequest` supplied the archive without disabling TLS checks. Receipt:
  `vendor-restore.json`.
- `StudyCollectionActivity.kt` puts researcher controls in a scrollable container.
- `StudyCollection.kt` adds a debug-only, constrained test storage override. Each
  visit captures its receipt directory so asynchronous writes remain with their
  originating storage location.
- `StudyCollectionDeviceTest.kt` provides six isolated synthetic checks. Teardown
  runs only after successful isolation; a blocked-FIFO regression verifies receipt
  routing across storage switches.
- Existing `SmoothedReadingAidRuntimeTest.kt` now explicitly selects SENTENCE for
  its sentence assertion and supplies fresh ordered timestamps.
  `SmoothedScanpathSessionTest.kt` schedules scrolling after its consultation
  timeline, so it reaches the geometry-settling branch rather than the earlier
  consultation-lateness exclusion. Missingness, wrong-stream and candidate-clearing
  assertions remain. The controls-export fixture now uses positive delivery IDs
  matching the actual provider; the importer correctly rejected its old ID zero.

Focused independent review caught and resolved the failed-setup teardown and
asynchronous receipt-routing issues. Review of the final isolation and fixture
changes reports no remaining actionable findings within that scope. No production
aid algorithms, metric thresholds, coordinate core or frozen protocols changed.
No staging or commit was performed.

## Next live check

Use a clearly marked engineering participant code such as `device-check`, start
a Reading run, and collect a fresh R/D/F reading baseline using the saved gaze
calibration. Record the actual displayed fit status; do not relax coverage or
variance gates to obtain a score. Then read an article in the same app process
and inspect raw/selected observations, acquisition and delivery cadence, drops,
missingness, native features, aid events and close counters in the exported data.
Coordinate MD also needs its separate earlier compatible reference period.

Actual accuracy, collector overhead versus an unrecorded control, endurance,
storage pressure and older-adult reading benefit are not established by these
software checks. Participant-study protocol decisions remain in the local study
workflow and framework documents.

## First live baseline result

User subsequently completed the engineering baseline and reported "Baseline
frozen". Its identified package was copied over USB without restarting the app.
The importer verifies all chunk hashes/counts: 4,415 records in four chunks,
`content_complete=true`, no declared writer losses and no unknown tail. The native
fit is `READY`, retaining 105 of 140 emitted windows. These windows overlap and
are not independent observations. The recorded union-of-retained-window duration
is 70,478ms; this is the collector's window-interval union, not independently
measured valid gaze duration.

There are 1,619 coordinate callbacks over a 69,296.94ms first-to-last coordinate
capture span: 23.35 coordinate outputs/s using `(N-1)/span`. This is not camera
arrival FPS, gaze accuracy or fixation rate. Six other observations are marked
blink, followed by one stopped observation. Terminal counters report 1,651
arrivals and 25 busy drops. Median/max coordinate capture-to-delivery latency is
178.33/389.50ms. These measurements describe this baseline run with collection
enabled; they do not isolate collector overhead or establish article performance.

Local evidence: `output/study-live-check-01/baseline-summary.json`, package
`729f9467-db38-4674-8fd6-9e51a881d490/`, `baseline-import/receipt.json`.
Only numeric/text records were copied; no camera images or cloud transfer.

The run's participant code is `device_check`, while its baseline entry uses
`device-check`. Originals are retained with both identities. Before article
testing, align the new study run to `device-check` without restarting the app,
which preserves the frozen baseline. No earlier compatible coordinate-reference
period has been collected yet. Article aid behavior and coordinate MD collection
remain pending.
