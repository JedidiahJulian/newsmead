# Live compressed-recording recheck, 2026-11-03

Scope: collection engineering on the connected test phone. User designated the
previous article visit as the valid performance check and described extra time
in the latest visit trying to trigger the definition aid. These are task
annotations, not participant exclusion criteria or evidence of reading benefit.
User recalls little visible lag and approximately21or26displayed FPS, uncertain.

Only the four finalized `storage-check` packages from run
`06c8a702-e20a-4c90-b9fa-420ff98e7978` were copied over USB. Seven older packages
were not recopied. No app changes, install, restart, new collection or source-file
deletion occurred during this audit. All original recordings remain on the phone.

## Timeline and identities

Times below are Philippine time, November3. Spans are logical recorded spans,
including explicit exclusions; they do not measure attentive reading duration.

| Start | Package prefix | Role | Recorded span | Stored / decoded MB |
| --- | --- | --- | ---: | ---: |
| 23:19:46 | `cc4fda0d` | Early GMA article, before baseline | 10.481s | 0.355 / 6.829 |
| 23:20:12 | `5c117bc1` | Separate R/D/F baseline | 62.360s | 0.366 / 6.374 |
| 23:21:18 | `04c0dd8a` | User-designated performance visit | 139.904s | 9.413 / 168.638 |
| 23:24:02 | `bcdd4bc7` | Separate popup-trigger attempt | 78.325s | 3.900 / 66.393 |

The last two visits reopen the same PR Newswire article
`0af33c8624fd6cfc596d887c2ee0588b`. Their reading IDs are distinct:
`53307f27-194e-4fda-a699-6999cf62fdbb` and
`69b52b9e-1c8d-4d86-a743-d5d2a4fff36b`. The24.418s gap between them is outside
both recordings. Their transport session IDs are respectively
`f22986df-be2f-494a-bc5b-2ef0ebc00261` and
`60bb4931-432b-4f27-85db-0718b23cb3d4`.

The baseline fit is READY, with84of124received overlapping windows retained;
these are not independent observations. Both subsequent visits reference that
exact attempt `84816f39-af7f-4223-84d8-84a792dcdb5d`, participant and calibration.
The early pre-baseline article explicitly has no frozen reference and records
BASELINE_UNAVAILABLE, which is expected for its timing.

## Performance and storage evidence

The designated performance visit spans2min19.904s, so this is not a completed
three-minute live rehearsal. It contains3,734coordinate outputs. Using
`(N-1)/(last-first capture)` gives26.961outputs/s; the30s full bins contain
26.37,27.37,27.00and26.13outputs/s. The final19.904s bin contains26.53/s. Median
capture-to-delivery age is183.25ms, p95209.22ms. This is stable recorded coordinate
output over this run, not an independent measurement of screen/camera FPS or a
matched recording-on/off comparison.

The visit uses9,412,538stored bytes for168,637,951logical JSONL bytes, a94.42%
reduction. All four packages pass stored hashes/counts, gzip decoding, record
identities/sequences and declared-loss validation. Each declares complete, no
writer loss and no unknown tail. No timestamped capture is outside its logical
session. This does not establish tracking accuracy or that every record is usable
for every analysis. The performance visit includes5.791s of excluded context;
allowed context is134.113s, which also is not verified attentive reading time.
Its native R/D/F outputs retain186AVAILABLE and91INVALID_WINDOW ticks.

The later attempt remains a separate engineering visit; no records were removed
or filtered from the originals based on the user's task annotation. It also has
no declared writer loss, a stable~26.57coordinate outputs/s and a matching baseline.

## Definition-aid sequence

In the later revisit, action `aid:568` has a recorded native word-residence span
of1009.187ms and rule `A5_FRESH_WORD_RESIDENCE_V5`. Relative to that session start:

- CHOSEN decision:73.7920s.
- Offer `shown` event:73.8117s; valid-layout/pre-draw proxy:73.8119s.
- User acceptance:75.7901s.
- Consultation/loading starts:75.8002s; panel-layout proxy:75.8133s.
- Reader-dismissed consultation ends:77.0666s.

The offer pre-draw proxy follows CHOSEN by19.875ms. These records support prompt
arrival at the layout callback; actual screen presentation latency was not
measured (`verified_visibility=false`). They do not establish when the user first
tried, why earlier gaze failed qualification, whether the intended word was
mapped accurately or whether dictionary content loaded successfully. The action
itself labels word confidence `mapping_only_unvalidated`. Trigger reliability
therefore remains a separate practical issue; no threshold or aid behavior was
changed during this collection audit.

## Evidence and remaining work

Evidence: `output/study-live-check-04/inventory.json`, preserved `packages/`,
`audit_recordings.py`, `summary.json`, `checks.json`, `popup-events.json` and
`verification.log` and `adapter-replay.json`. Deterministic coordinate-adapter
replay also passes for all four packages, preserving explicit missing/excluded
statuses. Coordinate distances remain REFERENCE_UNAVAILABLE because no separate
coordinate reference was selected; that does not negate the recorded native R/D/F
scores. The script reuses the strict importer for integrity and keeps
native outputs separate from derived timing counts. Focused independent review
confirmed identity/rate arithmetic and required the narrower display-proxy wording
used above. The baseline/metric/gaze cores and app behavior were untouched.

This run supports collection beyond the earlier reported two-minute failure
point. Longer live endurance remains unverified. A real compatible coordinate-MD
reference has not been recorded in this run; missing reference must remain
unavailable. The existing512MiB decoded analysis guard remains separate from
compressed phone storage. No study-benefit analysis or new acceptance cutoffs.
