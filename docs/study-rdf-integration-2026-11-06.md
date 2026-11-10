# R/D/F branch integration — 2026-11-06

Integrated the groupmate's branch into the local-study collector after the user
confirmed checkpoint `71831c7` had already been pushed. This is an integration
checkpoint; the shared R/D/F and coordinate-reference workflow remains a separate
implementation task.

## Integrated behavior

- `7cc20ca` makes the provider's output-age limit configurable through the output
  gate, selected-coordinate path and watchdog. `ReadingBaselineActivity` supplies
  650 ms through `StudyConfig.READING_MAX_OUTPUT_AGE_MS`; other callers keep the
  500 ms default. This changes operational input eligibility, not R/D/F formulas.
- `44d27fa` handles optional saved-list/history failures without preventing article
  reading, preserves coroutine cancellation, removes the history delete/write
  race, and trims history using timestamp-checked transactions.
- The merge retains the collector's raw-observation callbacks, provider metadata,
  scoped epochs and asynchronous final-counter delivery. The provider now exports
  its effective `output_max_age_ms` so acquisition identity distinguishes limits.
- The checkpoint-note conflict retains both collection history and the incoming
  baseline-fix history. Unrelated local edits are excluded from the merge commit.

## Checks and limits

The offline debug build and 38 focused JVM tests passed with zero failures or
skips: output gate (5), live output (8), reading feature pipeline (10), Mahalanobis
core (12), and debug snapshot (3). The opt-in host-test init script excludes the
external-account/data-creating Firebase test. Independent source review found no
actionable correctness issues in the scoped integration.

Local evidence is under `output/rdf-merge-2026-11-06/`: `build-tests.log` and
`checks.json`. APK SHA-256:
`8760c8abbfe869760dd7cf90a456c86c64fadaeb4db2cfbb02edb0fdb08dae4f`.
The APK was built without installation or camera capture. No live Firestore
behavior, gaze accuracy or human measurement validity was established.

## Next baseline work

One unassisted reading should supply the existing R/D/F feature profile and a
separate raw-coordinate reference, each with explicit source identity and fitting
status. The coordinate fit can remain offline for exploratory analysis.

The existing baseline Activity uses different viewport, typography and acquisition
settings from ordinary articles. Reusing the actual article reading surface is
the preferred integration direction; do not treat those contexts as equivalent
by changing a phase label. Coordinate MD still has its own 500 ms analysis age
limit and strict context requirements. Any new compatibility contract or reading
configuration must be explicit, tested and recorded. Existing baseline packages
and evaluation recordings remain unchanged.
