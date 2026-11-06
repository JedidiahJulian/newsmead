# Lossless storage and transfer verification, 2026-11-03

Scope: finish collection engineering before participant benefit analysis. No
changes to gaze estimation, calibration, smoothing, detector thresholds, aid
decisions, either MD core or baseline calculation. No downsampling or field loss.

## Storage change

Collector1.2 compresses complete JSONL chunks on the existing background writer.
Each gzip chunk declares `encoding=gzip_jsonl_v1` and its logical byte count.
Stored SHA-256/byte counts cover the actual compressed file. Event envelopes,
payloads, order, capture clocks, missingness, losses and logical session boundaries
are preserved. The record/package schema remains `newsmead_study_v1`; frozen v0.1
drafts and prior recordings are untouched. Legacy plain chunks remain supported.

Chunk rotation continues to use2MB logical bytes/60s; sync-flush is approximately
1s, without an fsync claim. Interrupted gzip recovery salvages only a valid JSONL
prefix into separate plain recovered chunks, retains originals, marks unknown
loss/end and stops before later chunks after decoding failure. Strict finalized
decoding rejects missing/unknown encoding, count mismatch, invalid trailer or
truncation; logical expansion is capped at64MiB/chunk.

Offline replay of the three already-stopped engineering packages produced:

| Package prefix | Original JSONL bytes | Gzip bytes | Reduction |
| --- | ---: | ---: | ---: |
| `44a17be5` | 6,304,400 | 360,682 | 94.28% |
| `8a2e7a58` | 8,273,411 | 423,035 | 94.89% |
| `ba2c36b7` | 112,525,071 | 6,356,348 | 94.35% |

Every decompressed chunk matched its original bytes exactly; the strict importer
verified all3,998/4,990/54,125 records. These are Python gzip-level6 replay figures,
not phone writer CPU/battery measurements. Copies are separate; source recordings
and their original manifests remain unchanged. Changing a source manifest's
storage metadata changes its digest and derived reference provenance ID; native
values and numerical MD outputs are unchanged. Evidence:
`output/study-storage-check-03/compression-receipt.json` and `repacked/`.

## Transfer and references

`tools/gaze/study_transfer_check.py` verifies all packages in a received ZIP using
the strict importer. It reconciles the entire declared export inventory, so a
missing whole article package cannot pass merely because remaining files verify.
The receipt lists logical reading/transport IDs, separate baselines, positively
matched source R/D/F baseline packages and stored/decoded sizes. It inventories
coordinate contexts/reference sessions without automatically selecting a
reference, altering bounds or fitting evaluation data to themselves.

An explicit reference/evaluation pair can check the existing default coordinate
fit and summarize availability statuses without generating the large analysis
tables. A synthetic65s reference plus a later separate provider epoch fits READY
and yields3available points after compressed transfer. This checks software
plumbing; actual reference material, duration and independent participant validity
remain protocol decisions. No actual coordinate-reference session has yet been
confirmed among the three inspected live packages.

The test APK adds a separate-UID, local-only share receiver for checking the real
transfer button and Android URI read grant. It does not ship in the main app and
does not send data over the network. The actual phone button/chooser delivered
the selected real article plus its matching baseline to that receiver, then USB
copied the received ZIP to the computer. All3SHA-256values (origin, receiver,
computer) match. The6,733,415byte ZIP contains exactly packages `ba2c36b7` and
`44a17be5`; the earlier article is excluded. Source manifests and all source chunk
hashes match the previously retained originals. Strict verification reports
`ALL_DECLARED_PACKAGES_PRESENT` and `MATCHING_SOURCE_PRESENT` for the R/D/F link.
Evidence: `real-transfer/summary.json`, `receiver-receipt.json`,
`verification-receipt.json` and `received.zip`. The receiver component was disabled
after this check so it does not clutter the normal share menu; its local cache
copy remains retained.

## Checks and remaining limits

- Full guarded debug/app-test build and native-package checks pass.
- Focused JVM suite:41tests,40passed,1Windows symbolic-link skip.
- Python importer38tests and transfer/reference6tests pass.
- Actual Kotlin gzip writer package loads in Python with6records and complete
  status; source receipt is `kotlin-python-interop.json`.
- Manifest JSON schema and all3compressed replay manifests validate.
- Focused review's missing-whole-package finding was fixed and regression-tested;
  follow-up review has no residual actionable findings in the reviewed scope.
- All8camera-free phone collection/browser/endurance tests pass. The180s writer
  fixture delivered5,398callbacks (29.985/s), with2missed schedule slots,
  zero writer loss and maximum sampled queue depth3. Mean main callback work by
  minute was11.81/10.92/10.78ms; the fixture shows no progressive slowdown in this
  run. It excludes camera inference and rendering.
- The actual Android writer produced2,416,005stored bytes from83,314,996logical
  bytes (97.10% reduction) for36,116synthetic records. The transferred package
  passes Python integrity/decoding. Evidence: `endurance/result.json`,
  `endurance/import-check.json` and `device-tests.log`.
- The actual share-route check above passes after a fixture-only correction. The
  first separate-UID test receiver attempt failed because the standalone test
  APK lacks the main app's Kotlin runtime. A platform/Java-only receiver replaces
  that fixture; the main app/exporter and source recordings were unaffected.
  Evidence: `receiver-first-attempt-crash.log`, `receiver-fixture-build.log`.
  No real camera/gaze collection has been started by these automated checks.

Compression does not reduce logical analysis size. The existing Python importer
retains its512MiB decoded-package guard; a sufficiently long reading may need
streaming analysis support. Actual camera/reader endurance, battery, other phones
and an appropriate coordinate-reference task remain to be checked. The update
restarts the app and clears its process-only active R/D/F model; saved gaze
calibration and existing recordings are preserved. No staging or commit.

## Next user-operated engineering rehearsal

No live capture is started automatically. Use the installed update:

1. Home → Study recording: participant `storage-check`, condition `compressed`,
   phase Reading → Start run.
2. Complete the existing Reading baseline again with participant `storage-check`
   and freeze it. This repeats the process-only R/D/F profile after installation;
   saved gaze calibration has an unchanged checksum and is a separate object.
3. Read one article naturally for3minutes with the intended aids/dot settings.
   Note approximate displayed FPS around1/2/3minutes and any lag or disappearing
   aids. Return Home, spend about10seconds there, then open a different article
   for about30seconds.
4. Return to Study recording and Stop run. Report completion and the observations;
   inspect the resulting separate packages before further capture.

Check actual new compressed size, callbacks/latency over time, declared losses,
reading IDs and matching baseline links, explicit departure exclusions and the
gap between articles. These are pipeline checks, not reading-benefit outcomes.
A real coordinate-reference pass is still separate; do not silently use this
evaluation recording or the R/D/F passage as its reference.
