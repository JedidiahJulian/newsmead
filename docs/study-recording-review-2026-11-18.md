# Study recording source review and host checks — 2026-11-18

Scope: the current in-app Study recording flow on
`optimization/panel-demo-login`, starting from `5640375`. Reviewed source and
synthetic tests; did not inspect private recordings, start a recording/camera,
install an app, contact a cloud service, or modify phone data. Existing local
documentation, IDE, presentation and Firestore-rule changes were kept separate.

**Authorized phone follow-up:** the later quick checks and data-preserving app
updates are recorded in `study-recording-phone-check-2026-11-18.md`. They supersede
the original no-device-work status above: S21 passed 8/9 and A56 passed 7/9
checks; 13 synthetic ZIP copies passed hash/import verification. The outstanding
failures concern a font-size fixture and automated-input compatibility. The two
new recovery regressions passed on both phones. No live camera test occurred.

## Findings and fixes

### Interrupted recording with no recoverable row could block all transfers

`StudyPackageWriter.recover` previously published a `recovered_incomplete`
manifest even if an empty/truncated first chunk supplied no complete valid JSON
row. Such a manifest had no usable identity or chunks. The catalog could not
display it, but Transfer all attempted to read its session identity and failed,
preventing intact packages from being transferred too.

A new regression reproduced the original failure before the fix. Recovery now
keeps the original bytes without publishing a package unless a valid row was
recovered. The catalog warns about directories without a recoverable manifest.
Both export modes now use the catalog's readable package set, so invalid
manifests left by the older recovery behavior do not block intact recordings.
Skipped/unreadable originals remain on disk. Export warnings are retained in
the ZIP inventory and displayed by the Study recording activity. When nothing
is transferable, specific warnings are displayed instead of only an empty-list
message. Clean, unused storage retains its normal no-recordings behavior.

Chunk/path/hash verification and selected-baseline dependency matching remain
in place. An exported ZIP with warnings must not be described as containing
every original recording; review the warnings before deleting any originals.

### Restarted article segments omitted retained navigation state

An automatic-scroll controller can remain enabled or manually paused across a
provider/recording restart. The new recording segment previously received its
configuration and enabled flag without the retained pause/motion/status state.
Deduplicated controller events did not necessarily restate those values.

Each attachment now includes `paused_by_reader`, `is_moving`, `status_reason`
and current `scroll_y` (pixels) in the existing auto-scroll configuration event.
The event timestamp remains elapsed milliseconds. No controller behavior,
gaze eligibility, context-boundary logic, calibration or metric changed.

## Changed implementation and tests

- `app/src/main/java/com/newsmead/research/study/StudyPackageWriter.kt`
- `app/src/main/java/com/newsmead/research/study/StudyRecordingCatalog.kt`
- `app/src/main/java/com/newsmead/research/study/StudyCollection.kt`
- `app/src/main/java/com/newsmead/activities/StudyCollectionActivity.kt`
- `app/src/main/java/com/newsmead/fragments/article/ArticleFragment.kt`
- `app/src/test/java/com/newsmead/research/study/StudyPackageWriterTest.kt`
- `app/src/test/java/com/newsmead/research/study/StudyRecordingCatalogTest.kt`
- `app/src/androidTest/java/com/newsmead/research/study/StudyCollectionDeviceTest.kt`

The added Android regressions cover an actual isolated synthetic ZIP export
containing an intact package alongside an old empty recovery manifest and a
truncated start, plus the warning shown when only an unrecoverable start exists.
They compile but were not executed on a device in this review.

## Verification

Evidence: `output/study-recording-review-2026-11-18/` (ignored local output).

- Existing focused writer/catalog/chunk runner completed successfully, reporting
  29 cases after adding the regressions. The broader Gradle suite reports the
  capability-dependent symlink test separately as skipped.
- Full targeted study JVM suite: **48 passed, 1 skipped, no failures** across
  nine test classes. The skipped case requires Windows symbolic-link support.
- Python study importer/notebook/transfer suite: **56 tests passed**.
- The actual synthetic Kotlin writer output was accepted by the strict Python
  importer; its receipt records matching chunks/identity and no declared loss.
- `:app:compileDebugAndroidTestKotlin` passed, including the two new export
  regressions. Normal vendor and comparison-manifest guards were enabled.
- Focused independent reviews found no remaining actionable issue in the
  persistence/export fixes or additive navigation-state snapshot.
- `git diff --check` passed.

Logs: `zero-prefix-reproduction.log`, `writer-tests-fixed.log`,
`import-transfer-tests.log`, `study-gradle-allowed.log`,
`kotlin-writer-import/receipt.json`, and `check-summary.json`.

The first host-run attempt used the machine's Java 25, which the pinned Kotlin
compiler cannot parse. Checks used the already installed Java 17 explicitly;
no Java installation or personal configuration was changed. The initial Gradle
attempt could not access its build-cache lock; the authorized offline check
then completed successfully with cache access.

## Remaining limits and operator checks

- No new phone/receiver transfer rehearsal, process-kill experiment, long live
  recording or actual participant validation was performed. New phone tests
  remain unexecuted; no updated APK was installed by this task.
- Recovery can retain only complete readable prefixes. The writer still makes
  no fsync/power-loss durability guarantee; unavailable data are not invented.
- Start run labels the condition; the operator must separately set the actual
  reading-aid switch. Fresh participant calibration, compatible baseline and
  linkage still follow the existing session procedure. A process restart
  requires a fresh shared reading baseline.
- Declared recorded span includes setup/excluded periods and is not attentive
  reading duration. Successful software transport does not establish gaze
  accuracy, correct human aid triggering, or study benefit.
- Before collection, rehearse Start → baseline → article → Stop → transfer and
  verify the received copy with the importer on the intended phone/build.
  This note itself authorizes no device installation or capture.

Changes remain local and uncommitted.
