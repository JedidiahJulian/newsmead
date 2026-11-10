# Study collector performance investigation, 2026-11-03

User stopped live article testing because recording caused severe lag: roughly
30 FPS without recording, roughly 15 FPS during early recording, and roughly
6 FPS with absent aids/dot and hanging after a longer interval. These are user
observations, not a controlled FPS comparison. Earlier short software checks did
not establish acceptable live collection overhead. Participant collection is not
ready until performance is checked again with the actual camera/reader.

## Local evidence and cause

Only the three identified, stopped `device-check` article packages were copied
over USB. Originals remain on the phone. No new live capture, camera images or
cloud transfer was used. Hashes/counts verify; all packages report complete and
no writer losses. Local evidence is `output/study-lag-check-01/summary.json` and
the three original package directories next to it.

| Package prefix | Recorded visit duration | Bytes | Repeated audit geometry fraction |
| --- | ---: | ---: | ---: |
| `78bcb335` | 21.570s | 15,004,798 | 67.9% |
| `3a571497` | 19.225s | 15,595,619 | 22.9% |
| `929f3b00` | 16.159s | 11,469,875 | 56.5% |

The packages total 42,070,292 bytes; `support_revision` and `aid_evaluation` account
for 34,993,190 bytes. These transport visit durations should not be substituted
for the user's reported total time using the app. Some visits mostly contain
stale observations. The middle visit has 302 coordinate outputs (~15.71/s over
its declared interval) and a 263.58ms median capture-to-delivery delay. The other
two have just 1 and 17 coordinate outputs, respectively, with many stale rows.
Those values describe delivered observations; they are not display/camera FPS
or independent accuracy measurements. All three retain a matching R/D/F profile.

Source tracing found that policy audits sent a complete `ReadingAidContext`,
including all article line geometry, on the UI thread. `StudyJson` recursively
reflected it, the writer immediately deep-copied it, and the worker serialized
it again. This also happened when no aid was visible. Reflection additionally
recompiled the field-name regex and converted each field name on every instance.
The writer queue and loss metadata are bounded; no duration-growing writer scan
was found. This identifies substantial avoidable work, but does not isolate all
causes of the reported deterioration, such as inference, rendering or thermals.

## Changes

- `StudyJson` caches field names together with reflection metadata and reuses a
  compiled conversion regex. Values remain fresh for every serialization.
- `StudyAuditEncoder` writes the full native geometry only when its instance or
  generation changes. Audit context retains every other native field and raw
  sample, with an explicit `geometry_ref` and `audit_payload_version`.
- Each layout/ref has a unique `layout_revision`, even when controlled scrolling
  reuses the native snapshot ID. Rejected snapshots never become cached and their
  revision cannot resolve to an older or later frame. Writer loss metadata remains
  authoritative; an unresolved reference must not be replaced by a nearby frame.
- `StudyVisit.auditEvent` avoids serialization after sealing. Source timestamps,
  envelope/sequence rules, raw/selected streams, queue bounds and loss accounting
  are unchanged. Session records identify `study_collection_v1.1` and
  `aid_geometry_ref_v1`. The `newsmead_study_v1` transport envelope is unchanged;
  old inline geometry remains readable.
- Each article view entry receives `article_reading_id`. Tracker restarts in the
  same view create linked transport segments using `reading_segment_index` and
  `previous_reading_segment_session_id`. Returning from another article recreates
  the view and starts another reading ID. Configuration-driven view recreation is
  also a new reading entry; this is explicitly `article_view_visit_v1`, not a claim
  of cross-configuration continuity. Missing/default content IDs stay unavailable.

No gaze samples were downsampled and no metric, aid, filter or calibration
thresholds changed. No writer ordering, hash, recovery or frozen diagnostic
protocol changed. Focused review caught and fixed reference ambiguity during
controlled translation, and clarified the article-view identity boundary.

## Baseline and reading scope

User confirmed the baseline should be saved separately and reused across article
readings. This is the existing explicit R/D/F baseline, not a new definition.
`Study recording → Start run` only enables persistence. `Reading baseline →
Start / retry baseline` starts the existing passage task and feature pipeline;
`Finish and freeze` fits the existing reference. The collector mirrors inputs,
quality flags, windows, configuration and fit result as produced.

Existing feature windows are approximately 10s, updated every 500ms; usable
windows supply the R/D/F mean and sample covariance with the existing ridge.
The field called fixation rate counts line stays. Existing minimum-window,
quality and coverage gates are unchanged; overlapping windows are not independent
observations and union-of-window duration is not verified cognitive reading time.

An unfinished baseline attempt is stopped by the activity's existing `onPause`;
late terminal receipts cannot re-enter its stopped feature pipeline. Eligible
inputs must map to the visible passage. This excludes Home/background/setup from
baseline reading features; it does not prove that every eligible gaze point is
reading. An already frozen profile survives activity navigation in the same app
process. It is not automatically restored after app restart. Baseline measurement
rules, Start behavior and pause handling were not changed by this performance fix.

Provenance was checked directly with `git show feature/scanpath-mahalanobis-score`:
that branch already includes the Home Reading baseline entry, the dedicated
passage, Start, Finish and freeze, reuse within the app process and interruption
handling. `ReadingBaselineActivity` was present in October commits including
`b5e666f`. The study branch adds persistence hooks and recording-close bookkeeping.
There is no diff against that branch in `MahalanobisBaseline.kt`,
`MahalanobisModel.kt`, `ReadingFeaturePipeline.kt`, `RollingReadingFeatures.kt` or
`ParticipantReadingBaseline.kt`. The separate Study recording control is the new
collection UI; it must not be confused with the older baseline task controls.

Only article provider visits and explicitly started baseline attempts create
study packages; starting a run on Home creates none. Transient article controls,
TTS, consultation and other existing exclusions remain boundary/quality metadata,
not additional reading sessions. The separate legacy usage logger is unchanged.

## Verification

Full guarded APK verification and 27 focused JVM tests pass. They cover serializer
equivalence/concurrency, complete context reconstruction from normalized layouts,
translated native-ID reuse with rejection/retry, reading/segment identity, writer
integrity, capture context and audit-consumer decision equality. Logs and XML
summary: `output/study-lag-check-01/final-build.log`, `jvm-summary.json`.

A camera-free device fixture performs a warmed full-context versus referenced
context encoding comparison, then 180 seconds of actual V5 policy/audit/writer
work paced at 30 Hz with a 400-line synthetic layout. It retains actual callback
times, missed scheduling slots, per-minute work/lateness, heap samples and writer
health; it verifies all hashes/counts/sequences and earlier layout references.
This excludes camera inference and view rendering, and the comparison excludes
writer copying/disk cost. All seven device tests (six collection regressions and
the endurance fixture) pass. The endurance run delivered 5,381 callbacks across
the three-minute target (~29.89/s), with 19 scheduling slots missed rather than
fabricated or replayed. Per-minute callback counts were 1,797 / 1,784 / 1,800;
mean main-thread work per callback was 12.01 / 5.65 / 5.22ms. There was no progressive
work-duration deterioration in this synthetic run. Sampled writer queue depth
peaked at 7; there were no writer losses. Heap samples fluctuate with collection;
first/last samples were 25.32/20.29MB, not a retained-memory measurement.

The warmed-cache encoding comparison for 100 pairs measured 942.15ms and
7,249,200 bytes with inline layouts versus 30.38ms and 243,033 bytes with the
referenced layout, including its first snapshot: ~31x faster and 96.65% smaller
for that encoding benchmark. Both paths use the new cached field names, so this
does not quantify the caching change's separate benefit or the complete app's
FPS improvement.

All 35,997 endurance records, 10,763 earlier layout references, sequence/source
times and hashes verify. The 83,041,768-byte source package also passes the actual
offline importer. Storage remains material: this deliberately busy synthetic
fixture writes about 27.7MB/min and is not a representative article-size estimate.
Evidence: `output/study-lag-check-01/endurance/result.json`,
`endurance/import/receipt.json`, `endurance-device.log`, `fix-summary.json`.
The verified update is installed with app data retained. Camera access history
shows its last use predates the automated test; the calibration file is present.
Actual camera/reader performance must still be checked with a short user session
before participant use. No new baseline is needed just to recheck FPS; the active
in-memory baseline was cleared by the app update, while saved calibration and
baseline recording artifacts remain available.
