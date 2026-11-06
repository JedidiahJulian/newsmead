# Live recheck and saved-recording browser, 2026-11-03

Current scope is the collection pipeline. These are engineering checks, not a
reading-benefit analysis or new participant acceptance rules.

## Live performance and recording check

User completed the post-fix baseline and article steps and reported no visible
issues. Only the three identified, stopped `perf-check` packages were copied over
USB. All chunk hashes/counts pass, all packages declare complete, and none declares
writer loss or an unknown tail. Originals remain on the phone.

| Package prefix | Phase | Recorded span | Source records | Source bytes |
| --- | --- | ---: | ---: | ---: |
| `44a17be5` | R/D/F baseline | 63.811s | 3,998 | 6,304,400 |
| `8a2e7a58` | Article reading | 7.437s | 4,990 | 8,273,411 |
| `ba2c36b7` | Article reading | 88.692s | 54,125 | 112,525,071 |

Boundary clarification: **88.692s is the logical article segment's elapsed span,
not confirmed reading time**. Rechecking the source and hash-verified originals
shows that the longer segment starts when the article's live provider is attached
after its body is ready and the fragment is resumed. It has one article ID and one
text snapshot. At offset88.150599s, `screen_paused_start` marks context disallowed;
at88.692259s, `session_end` closes it with reason `screen_left`. Thus0.541660s of
the span is an excluded departure transition. Three coordinate observations and
nine no-face observations were captured during that excluded tail and retained
with `context_allowed=false`. The stopped receipt and writer housekeeping finish
after logical end; they do not extend the segment's duration. No timestamped gaze
capture in any of these three packages falls outside its own logical bounds.

The separate baseline and earlier article are not added to the88.692s. The gaps
between the three identified logical sessions are7.602s and8.209s, also outside
their durations. There is no recorded pause/resume interruption inside the longer
article before its final departure. Its88.150599s allowed context is a collection
gate, not a measure of attentive reading, valid gaze coverage or accuracy. This
audit supports separation from time spent away from the article while retaining
the explicitly excluded exit transition. Evidence:
`output/study-live-check-02/boundary-audit.json`. No new capture or app changes.

The baseline fit is `READY`. Both articles retain that exact baseline attempt and
matching participant code. Their article-reading IDs are distinct. The longer
article has 2,495 coordinate callbacks within its logical bounds: ~28.64/s using
`(N-1)/(last-first capture)`, median capture-to-delivery delay176.17ms and p95
207.79ms. Its middle 15s bins contain about29 coordinate outputs/s; the last partial
bin contains27.75/s. These are fresh coordinate outputs, not independently measured
screen/camera FPS or accuracy. It also has114 available native R/D/F score ticks
and773 drawn-proxy receipts; neither establishes that an aid was perceived or
effective. Every normalized audit layout reference resolves to an earlier exact
revision (2,185/20,826 references in the two article packages).

Evidence is local under `output/study-live-check-02/`: `summary.json`, original
package folders, `verification.log`, and `*-import-v1.1/receipt.json`. Earlier
derivative imports remain for provenance. Actual camera performance after the lag
fix is now supported by this limited live check and the user's observation;
longer endurance, other devices, battery and independent gaze accuracy remain open.
Raw volume remains high: the longer example is112.5MB (~76MB/min). This is an
engineering storage constraint requiring further optimization, not a reason to
discard gaze samples or silently truncate recordings.

## Saved recordings and export

Open Home → **Study recording**, Stop the run if needed, then use **Refresh saved
recordings**. The list loads bounded metadata on the background executor. It shows
article readings, separate R/D/F baseline recordings and coordinate-reference
recordings, with participant, content ID, timestamp, size, declared saved status,
segment count, R/D/F reference ID and stable recording ID. The duration label is
**recorded segment span, including exclusions**, not verified reading time.
Baseline fit status is not inferred from a file's saved/complete status.

Select a reading and choose **Transfer selected recordings**. All its transport
segments are included. Available source R/D/F baseline packages are included once
when attempt, participant, installation and all known calibration identifiers
match positively. Missing/unknown/mismatched source linkage produces a warning;
the article's original frozen `rdf_reference` is retained. Coordinate reference
selection remains explicit. Legacy packages without a reading ID are listed
separately by transport session; historical reading IDs are not invented.

The existing **Transfer all study packages** option remains available. Both paths
use the original chunk bytes, checksums, export inventory, late receipts and
FileProvider ZIP sharing. Originals are retained. Catalog/export is blocked while
a study run is active. Selection clears on refresh to avoid stale choices.
Bounded or malformed metadata is displayed as unavailable/warned; it is never
filled with guesses. Existing recovery and export are protected against paths or
symbolic-link aliases outside their package and against output aliases to originals.

## Importer correction

One real article contains a native `rdf_tick` with `quality=INSUFFICIENT`, zero
samples and equal start/end immediately after reset. Importer v1 rejected this
warm-up row through the coordinate core's strict positive-bound contract.
`newsmead_study_import_v1.1` preserves the original row and bounds, emits
`INSUFFICIENT_SOURCE_WINDOW` / `ZERO_SPAN_NATIVE_WARMUP`, with MD and coverage both
null, and preserves source window order. It does not fabricate an interval or
fall back to a different summary. Reversed windows and zero-span `VALID` windows
remain errors. The coordinate core, baseline calculation and aid algorithms are
unchanged. Both actual article packages import with the corrected adapter.

## Verification and changes

- Full debug build, packaged-native verification and instrumentation assembly
  pass with the normal guards enabled.
- 34 focused JVM tests:33 passed,1 skipped because Windows could not create the
  symbolic links for the alias fixture. There are35 passing Python importer tests.
- Seven unique camera-free phone tests pass after correcting an older fixture's
  direct-export/teardown timing: it now waits for the same background FIFO used
  by real UI export actions. The initial failure logs are retained.
- The new browser test verifies two provider segments grouped as one reading,
  a separate reopened reading, actual checkbox selection, exact selected ZIP
  membership (two segments plus one baseline), exclusion of the other reading,
  hashes, read-grant flag, retained recordings and active-run browsing guard.
- The selected synthetic ZIP was copied over USB and all three packages imported
  successfully. Evidence: `browser-export/receipt.json`, `browser-device.log`,
  `browser-fixture-rerun.log`, `jvm-summary.json`, `importer-tests.log`.
- Focused independent review findings on recovery containment and unknown
  calibration matching were fixed; final source review has no remaining actionable
  findings in the reviewed scope. The device test calls the export API after UI
  selection; it does not test the transfer button or an external receiving app.

Substantive files: new `StudyRecordingCatalog.kt` and its tests,
`StudyCollectionActivity.kt`, catalog/export portions of `StudyCollection.kt`,
recovery path checks in `StudyPackageWriter.kt` and its test, new
`StudyRecordingBrowserDeviceTest.kt`, and `tools/gaze/study_package.py` / its tests.
No new live capture, cloud collection, baseline restoration, metric tuning,
participant exclusions, staging or commit was performed. The update is installed
with saved recordings retained; active R/D/F models still require the existing
baseline procedure after an app-process restart.
