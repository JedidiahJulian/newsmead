# Local study collection v1

Implemented on `feature/local-study-collection`, based on coordinate-MD commit
`93a2abd`. This is a local research instrument integration, not an approved
participant procedure or evidence of reading benefit.

Current priority, updated 2026-11-06: finish the shared reading baseline and recording pipeline before
study-level acceptance criteria or reading-benefit analysis. This includes live
performance, article/session identity and boundaries, separate baseline linkage,
local persistence/recovery and recording selection/export. Retain the existing
timestamps, raw/selected observations, missingness/loss flags and configuration
now so later analysis remains possible. This does not authorize new study cutoffs,
participant exclusions or changes to baseline/metric algorithms.

## Researcher workflow

1. Home → **Study recording**. Enter a pseudonymous participant code and a
   condition label. Choose **Reading**, then Start.
   Opening the controls alone does not start recording or the camera. The label
   records the assigned condition; it does not configure the aids. Set the
   intended aid settings through the existing reader controls.
2. Return Home → **Reading baseline**. The existing passage opens in the actual
   article layout with saved/locked article typography. Opening it starts no
   camera. Press **Start**, confirm the instructions and read without aids or a
   gaze dot. The footer shows R/D/F retained-window and covered-time progress.
3. Press **Finish**. This writes a separate `reading_baseline` package, its
   existing R/D/F fit and an explicitly completed raw-coordinate reference.
   R/D/F readiness and coordinate **recorded; offline fit pending** are separate
   outcomes. Neither status guarantees that the other profile is available.
   Pausing/leaving before Finish marks an interruption; it cannot supply an
   accepted shared reference. The core's 60-second observed-time reference gate
   is provisional, not a validated participant instruction.
4. Return Home and open a later article in the same Reading run and app process.
   It records separate R/D/F and coordinate source links. Active shared-reference
   reading and baseline acquisition both use stable-frame-rate preference and
   the explicit 650 ms provider allowance; coordinate analysis still uses its
   own 500 ms age gate. After restart, participant change or recalibration, collect
   a fresh baseline. Changed typography/viewport/acquisition remains incompatible.
   See [shared baseline contract](shared-reading-baseline-v1.md). Legacy
   `rdf_baseline` and explicitly selected `coordinate_reference` recordings keep
   their original meanings; no old package is relabeled.
5. After reading, Stop the run and choose **Transfer all study packages**. The
   exporter waits for closing writers, recovers valid incomplete prefixes where
   needed, verifies chunk hashes and stages a ZIP using the existing FileProvider.
   Choose the intended researcher-controlled transfer destination. Original
   packages are retained. Successful sharing is not a verified transfer receipt.
   Alternatively, select logical recordings in **Saved recordings** and choose
   **Transfer selected recordings**. Matching source R/D/F baseline packages are
   included when available. The declared shared coordinate source is included
   independently of R/D/F fit success; legacy coordinate references require
   explicit selection.
   See [browser workflow and live verification](study-recording-browser-2026-11-03.md).
6. Verify/import the received package before analysis or deleting originals.
   Recent full session IDs are selectable in Study recording for linking
   independently administered questions/feedback to the exported sessions.

Independent comprehension, perceived effort and loss-of-place instruments remain
protocol work; this change does not infer those outcomes from gaze. Keep an
enrollment/setup-failure register outside successful-session files. Phone route,
participant criteria, materials, sample size, duration, retention, final dot
condition and numeric progression criteria remain open.

## What is recorded

Canonical package schema: `newsmead_study_v1`, collector version
`study_collection_v1.3` (older packages retain their original collector version).
The [record envelope schema](study-data/record-schema-v1.json)
and [manifest schema](study-data/manifest-schema-v1.json) describe the transport.
Native payloads retain their source fields/versions; the importer checks the
critical observation/window/reference structures in addition to integrity.
These schemas do not replace or rewrite the frozen v0.1 draft fixtures,
diagnostic reading logs, pilot or replay formats.

| Family | Retained facts |
| --- | --- |
| Session/configuration | Participant/run/visit/installation IDs, explicit phase/condition, elapsed/UTC anchor, source article ID/URL, actual text, rendered viewport/typography, versions and settings. |
| Original gaze | Provider epoch/delivery ID, capture and delivery clocks, original source reason/watchdog, calibrated pre-smoothing and selected points, blink telemetry and observed counters. Includes invalid/stale/stopped deliveries. |
| Per-consumer input | Separate Scanpath and R/D/F geometry, mapped sample/exclusion, detector acceptance and boundary records. No global eligibility assumption. |
| Scanpath | Native candidate revisions and clearing, finalized clusters and P01–P13 events; producer/generation scope and screen/content-frame distinction. |
| V5 aids | Copied location/word/bookmark/focus state revisions and pre-reset snapshots, actual component evaluation/gating, decisions, offers/consultation, controls/navigation and draw proxies. `evaluated` does not assert qualification; native evidence/actions carry that distinction. |
| Presentation | Continuous presentation IDs per channel, refreshed actions/targets, first/last draw proxies and censored endings. Human visibility remains unverified. |
| R/D/F MD | Initial/scheduled ticks, actual feature bounds/values/quality, baseline attempt/window retention/fit including failures, frozen profile metadata and emitted MD/MD²/deltas/status. Native participant/reference mismatch is explicitly retained. |
| Recording health | Queue losses, written/flush-through sequence, hashes/counts, finalization/recovery state and missing terminal counters. No fsync durability guarantee. |

Nothing is uploaded by the collector. No camera images or neural feature arrays
are retained. Ordinary `appusage_` logs stay in their existing separate family;
their relative timestamps are not silently joined to gaze's absolute elapsed
clock. The private study directory is `noBackupFilesDir/study-packages-v1/`.

## Coordinate-MD integration

For exploratory inspection of existing recordings in Jupyter, use the
[notebook workflow](../notebooks/README.md). It loads verified compact tables,
plots recorded gaze/native R/D/F/scanpath/aid outputs, preserves missingness and
exports CSVs. Its coordinate section now resolves the declared shared source,
fits it offline and retains point/window availability and fitting provenance.

The [coordinate core](gaze-coordinate-md-core.md) is unchanged. Canonical
coordinate analysis uses calibrated **pre-smoothing screen pixels**, and never
line/word mapping or R/D/F features. The selected stream is also retained for
separately referenced analyses. Both MDs remain disconnected from aid decisions.

Coordinate MD runs offline after export. Each point carries actual acquisition
and processing metadata plus a compatibility context: participant, installation,
calibration/model, acquisition hash, presentation hash, clock domain, physical
screen dimensions/rotation and raw stream. Unhashed metadata are retained.
Legacy presentation signatures include visible body clipping, typography,
language/color, dot setting and aid preferences. New shared baseline/readings use
the explicit `article_reading_surface_v1` coordinate compatibility signature:
physical scroll viewport, column/padding, font resource/style, language and
typography. Full clipping/color/aid settings remain recorded separately and their
transitions still break exposure. Exact text remains provenance; different news
articles need not contain identical text.

Capture-time floor lookup prevents delayed samples from receiving future
presentation settings. Controls, consultation, TTS, scrolling, renderer changes
and pauses are explicit boundaries/exclusions. Native event time and the
collector-observed effective transition time are distinct. Consumer mapping
rejection alone does not change coordinate context. Missing source rows still
break core adjacency. Windows crossing stationary context boundaries remain
unavailable under the core contract; recording does not loosen that rule.

Select an explicit earlier reference package. If its viewport settles partway
through, explicit reference bounds can select a declared compatible subperiod;
the importer never searches for a statistically favorable reference. Bounds,
context/configuration and source manifest digest determine the frozen reference
ID. Never fit an evaluation window to itself. A missing, incompatible,
degenerate, insufficient or loss-affected reference produces unavailable results,
not a substituted profile or zero distance. Large finite MD values are retained.

Examples, from repository root:

```powershell
python tools/gaze/study_package.py path/to/study.zip --session-id READING_SESSION_ID --output output/import-reading
python tools/gaze/study_package.py path/to/study.zip --session-id READING_SESSION_ID --reference path/to/study.zip --reference-session-id REFERENCE_SESSION_ID --output output/import-with-reference
```

Optional paired `--reference-start-ms` and `--reference-end-ms` use the exported
absolute elapsed clock and must stay within the reference's declared bounds.
The importer emits all-record JSONL/CSV, grouped session/gaze/RDF/Scanpath/aid/
boundary/health tables, coordinate point/summary tables, late receipt tables and
an analysis receipt. Grouped rows keep envelope keys and prefix payload columns
with `payload_`, preserving nested native values without overwriting identities.
It uses actual R/D/F window bounds when
present, otherwise declared stationary-episode bounds. Raw records remain intact.
The importer cannot prove gaze accuracy or whether reference material is suitable.

## Shutdown, recovery and transfer

Logical reading closes separately from asynchronous camera shutdown. A provider
callback keeps its original recorder/epoch after the Fragment detaches. Baseline
sealing also waits for the pending fit result. A three-second operational close
timeout marks unknown terminal counters and an incomplete tail; it never extends
reading time. Late terminal counters are separate atomic receipt files. The ZIP
inventories their hashes and session/provider links; the importer retains them
without repairing original completeness or MD availability.

Recovery/export atomically reserve maintenance, blocking a new run or writer
until preparation finishes. Writer chunk rotation targets 2,000,000 bytes/60s;
one large JSON record stays intact. Queue capacity is 2,048 records and flush
interval approximately one second. These are engineering budgets requiring phone
measurement. Flush-through is not a guarantee against power loss. Overflow is
declared by sequence ranges/counts; it never silently overwrites earlier data.
Recovery keeps originals and copies a valid newline-terminated prefix into
recovered chunks, with an unknown ending in the original clock domain.

Collector1.2 writes independent gzip chunks on its background writer. New chunk
names end `.jsonl.gz`, with explicit `encoding=gzip_jsonl_v1` and
`uncompressed_byte_count`. `byte_count` and SHA-256 cover stored compressed bytes;
after decompression the original JSONL event bytes and record order are unchanged.
Rotation still uses logical bytes and the same60s budget. Approximately1s
sync-flush permits recovery of a valid prefix from an interrupted gzip stream;
recovery stops at corruption/truncation, preserves originals and reports unknown
loss/tail. Finished decoding requires the declared logical size and valid gzip
trailer. Plain legacy and recovered JSONL chunks remain readable.

Decoded gzip chunks are capped at64MiB. Python analysis retains a separate512MiB
decoded-package budget as well as its stored/ZIP input budget. Compression reduces
phone storage, not decoded analysis size or memory use. Very long single readings
can still exceed that analysis guard; do not remove it or discard raw records to
make an import pass. A streaming analysis change remains separate work if needed
for the final session duration.

The ZIP is staged under cache `research-export/`. Its optional
`study_export_manifest.json` links package manifests and late receipts. The
ordinary usage-log exporter filter remains unchanged.

After moving a selected ZIP to the computer, verify the entire received export:

```powershell
python tools/gaze/study_transfer_check.py path/to/received.zip --output output/transfer-receipt.json
```

The receipt verifies all discovered packages with the strict importer and checks
their IDs, session IDs and manifest digests against the declared export inventory.
It lists reading IDs, source R/D/F baseline matches, stored/decoded byte counts and
coordinate-reference sessions. A missing entire declared package is an error.
Without an export inventory it explicitly verifies package contents only. It
does not automatically pick a coordinate reference or favorable time bounds.

For an explicitly chosen engineering reference/evaluation pair in the same ZIP:

```powershell
python tools/gaze/study_transfer_check.py path/to/received.zip --reference-session-id REFERENCE_SESSION_ID --evaluation-session-id READING_SESSION_ID --output output/reference-check-receipt.json
```

Optional paired `--reference-start-ms` / `--reference-end-ms` use the same explicit
bounds as the importer. The receipt preserves fit status/configuration, context
inventory and point/summary status counts. READY in synthetic software checks is
not approval of reference material or a participant procedure.

## Verification and connected-phone checkpoint

Focused checks and receipts are retained under `output/study-collection-*`,
`output/study-writer-v1-checks/` and `output/study-importer-v1-checks/`.
The writer has nine host tests including mutation isolation, overflow, rejected
serialization/clock calls, concurrent finish, interrupted multi-chunk recovery
and a real package passed to the Python importer. The importer has 32 tests,
including strict JSON/hashes/IDs/path containment, missingness, fixed reference,
actual windows, disjoint contexts, reference-bound selection and sidecar integrity.
Additional JVM tests check capture-context timing, maintenance races, and V5
decision equality with successful/failed audit consumers.

Final isolated Android Kotlin compilation and **333 focused JVM tests passed**,
zero failures/errors/skips. Final log/receipt:
`output/study-collection-jvm-check-03/build.log` and `summary.json`.
Focused source review found no remaining actionable findings. These are software
checks and synthetic observations, not a phone timing/measurement experiment.

The initial missing-ARMv7 packaging blocker is resolved. The bounded
`tools/gaze/mgazenet/restore_armv7_vendor.py` restores the three missing libraries
from the existing hash-pinned official archive, preserving differing files and
all model/guard pins. Full debug build and packaged-native verification now pass
with the normal guards enabled. The updated APK was installed on the authorized
SM-A566B without clearing app data; its saved gaze calibration remains present.

**51 unique camera-free on-device tests pass after fixture corrections.** Six
synthetic USB exports contain seven successfully imported packages, including an
intentionally incomplete recovery package. Manifest/record schemas accept all 77
retained records. New device checks use constrained isolated storage, exercise
reachable Start/Stop controls, held result closure, timeout/late receipts,
capture-aligned presentation and valid-prefix recovery. Existing native and aid
checks cover numerical inference, mapping/runtime routing and rendering. No new
camera access is shown during the automated rehearsal. Focused review findings
on test teardown and delayed receipt routing were fixed and checked.

See [device rehearsal, changed files and limits](study-device-rehearsal-2026-11-03.md)
and `output/study-phone-check-01/summary.json`. This does not establish live
ArticleFragment-to-detector behavior, current gaze accuracy, coordinate-reference
availability, collector overhead, endurance or older-adult measurement validity.
User-assisted live baseline/article collection is next; gaze calibration can be
reused if compatible, while the active R/D/F reading baseline needs recollection
after process restart. No private recordings were inspected or uploaded.

## Live performance issue and collector v1.1

The first live article rehearsal revealed severe recording lag. The earlier
software passes do not establish acceptable live performance. See the
[investigation, evidence and verification limits](study-collector-performance-2026-11-03.md).
Collector `study_collection_v1.1` removes repeated full-layout serialization from
policy audits and caches reflection field names. Raw gaze cadence is not reduced;
metric and aid algorithms remain unchanged.

For `audit_payload_version=aid_geometry_ref_v1`, `input_context.geometry_ref`
joins an earlier `layout_snapshot` with `producer=aid_audit` within the same
transport session. Match `generation`, `geometry_snapshot_id` against native
`geometry.snapshot_id`, and `layout_revision`. Native IDs alone are insufficient
during controlled scrolling. Keep original sequence order and declared loss
metadata; a missing revision remains unresolved. Full contexts can be reconstructed
from that exact layout and the retained scalar/raw input fields. Legacy audit
rows continue to contain inline geometry. All layout rows remain in `records_v1`.

Article session-start metadata now includes `article_reading_id`,
`reading_segment_index`, `previous_reading_segment_session_id` and explicit
content-ID availability. A new article view entry gets a new reading ID, including
returning from another article or configuration-driven view recreation. Provider
restarts in that view remain linked segments. `session_id` still identifies each
transport segment; the foreground-wide legacy `usage_session_id` is not a reading
ID. Home/menu setup does not open article packages. Baseline packages remain
explicit, separate and reusable by later article references; their native fitting
rules and activity Start/pause behavior are unchanged.

The saved-recording browser and selected export are now implemented and installed.
It lists metadata/declared status from bounded background reads, warns about
unavailable metadata or baseline linkage, and preserves originals. Importer
`newsmead_study_import_v1.1` also retains native zero-span insufficient warm-up
windows as unavailable, preserving source order and null undefined coverage. The
post-fix live recheck found no visible issue and its packages verify; raw recording
size remains high. See the linked browser/recheck note for exact checks and limits.
