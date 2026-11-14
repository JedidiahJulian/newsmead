# Bookmark diagnosis and revision — 14 November 2026

**Latest owner-approved implementation:** ruleV6/formV3 replaces the rejected timed persistence below. It retains the saved frontier, dims with supported rereading progress, and clears only on supported forward passage beyond the marker. See [research basis and limits](bookmark-rereading-duration-evidence-2026-11-14.md). Current verification is in `output/bookmark-progress-2026-11-14/`.

The owner authorized a bookmark for supported backward rereading and reported early AUTO appearance, retention while looking away, quick dismissal on return, and excessive bookmark strength. These are user observations. The source findings identify mechanisms that can explain them; no natural-reading capture was performed for this diagnosis.

## Source findings

1. A precise location could become the saved source after the150ms support gate. Another500ms of outside/missing input could activate a cue. There was no separate established-reading requirement.
2. Minimum visibility counted from cue activation, including time looking away. After that minimum elapsed,1s of supported return or1.5s of coherent reading elsewhere could clear it.
3. Bookmark strength was forced to1.0 and paint alpha was fixed at240/255 (about94%). The strength control affected zero/off eligibility but not the marker's intensity. Gutter width was6dp.

## Current behavior

- **Retention:** no5s/10s/30s lifetime and no same-line/earlier-line residence dismissal. Any supported adjacent forward pair at or beyond the saved row clears; jumping to a later row and remaining there does not. Missing/rejected/duplicate input cannot invent the forward pair.
- **Prominence:** starts at75% opacity, dims monotonically as supported forward rereading approaches the saved frontier, and retains a40% default floor until passage. The250ms interpolation reuses the app's existing visual-transition setting; it does not time rereading or delete the cue. Floor/curve are exploratory presentation choices, not durations established by the cited studies. Reader strength/cap changes apply immediately without inventing a new cue or exposure.
- **Lookaway:** established1500ms eligibility arming latches across brief blinks/dropouts. A500ms sustained outside/unavailable route uses the last trustworthy precise line, fresh at the START of departure. Unsettled raw rows do not overwrite that memory. Operational/layout changes still invalidate it.
- **Backreading:** after a supported adjacent forward line advance, a stable supported return at least3physical rows earlier can preserve the departure frontier immediately. It no longer waits for two later rereading advances. The3-row guard retains the existing unknown-spatial-error assumption; it is not a validated accuracy bound. Exact mapping/continuous-input boundaries remain required.
- **Trace:** bookmark rule`A2_LAST_READ_LINE_BOOKMARK_V6`, form`LAST_READ_LINE_BOOKMARK_V3`, runtime policy17. Records include normalized presentation progress/revision. Frozen extractor and study package schemas unchanged; focus behavior unchanged. Historical null/timed comparison profiles remain available only to their explicit tests.

## Historical rejected ruleV5 behavior

- **Arming:**1,500ms of fresh eligible mapped in-article capture intervals across rows before a source can activate departure cues. This is article presence, not a measurement of attentive reading. Ordinary forward/backward transitions do not restart it. Precise stabilized mapper agreement still determines the saved line; presence alone cannot invent an anchor. Duplicate captures add no credit; missing, stale/gapped, rejected-mapping and layout boundaries break the run. Outside-article and unavailable-input activation remain separate500ms routes.
- **Rereading:** the shared complete A→forward frontier B→return A-region→two forward reread advances can preserve B. Every witness visit requires trusted exact-mapper support. An isolated backward/skipped-row jump or partial reread is insufficient. Focus can be disabled independently.
- **Recovery:**3,000ms of fresh eligible same/next-row article presence anywhere can dismiss the cue, with fresh precise supported location at release. This includes reading earlier than a reread bookmark; crossing the saved frontier adds no extra route hold. Missing input resets this recovery credit.
- **Visibility limit:** ruleV5 adds a5,000ms elapsed limit beginning with the first fresh eligible in-article return; an in-article reread cue starts this budget at activation. Tracking noise/duplicate ticks do not restart it. No return input means away time cannot consume this budget; the existing30s overall age limit still applies. Expiry retires the old source so an unchanged missing-input run cannot immediately show it again. This bounds distraction from a mistaken trigger; it does not identify which triggers are false.
- **Strength:** owner subsequently rejected the47% appearance and requested60% or75%. Current form `LAST_READ_LINE_BOOKMARK_V2` now defaults to0.75 strength with a direct255-alpha opacity scale:191/255 (about75%). Manual and maximum-strength settings remain effective; there is no automatic strengthening. Width remains6dp. This is an owner-selected appearance setting. LegacyV1 and next-line cues retain their historical fixed240-alpha appearance.
- **Trace:** current rule `A2_LAST_READ_LINE_BOOKMARK_V5` retains the precise source, full reread witness, departure support and separate clocks. Runtime metadata identifies presence thresholds/time bases, return-visibility limit and opacity behavior. The audit includes `anchor_presence_credit_ms`, `recovery_presence_credit_ms` and `returned_at_ms`. Frozen scanpath extraction and study package schema are unchanged.
- **Boundaries:** invalid layout/article anchors, disabled/zero aid settings and30s maximum age still apply. ON remains a forced presentation mode; AUTO uses the activation rules.

Independent review initially found that median settling plus supported residence can leave over250ms between precise row supports despite continuous valid raw input. Trusted spatial history survives a validated presence bridge. The follow-up below separates the UI presence clock from supported row dwell; presence is not backfilled into a precise visit.

## Missing bookmark and reading-speed follow-up

The owner reported no bookmark even in Replay and raised normal reading speed. On SM-G991B, the actual **Bookmark after looking away** Replay screen completed with CURRENT_LINE/WORD_DEFINITION only, reproducing the missing bookmark. Before-fix screen/status evidence is in `output/bookmark-replay-fix-2026-11-14/R5CR60YSJ2X/`.

Two concrete coverage/behavior issues were found:

1. Departure scripts held their source for only250ms, below the new1500ms arming threshold. Existing positive Replay tests selected historical V4, while the actual screen selected V5; prior supplied-input native checks therefore did not prove the current Replay interaction.
2. Supported-tail arming discarded/reset much of ordinary row-transition time. It could fail to mature despite continuous eligible article input. Current V4 bookmark rule uses a separate presence clock across rows; precise spatial support remains required for the source and release. An established departure latch survives an aged UI tick without earning more credit, and departure cannot reuse an old row anchor after fresh input moves to an unsupported new row.

Replay departure examples now supply2000ms before departure and4000ms recovery. **Bookmark after short line visits** supplies four450ms row visits before departure, exercising the speed concern without a long hold on one row. These scripted timings are software regression inputs, not normative human reading speeds. The150ms location support gate remains; very brief/uncertain locations may still be withheld. No physiological fixation detector or population-speed threshold was introduced.

These timing/appearance settings are pilot choices responding to the interaction report, not research-established optima. Literature rationale remains in `PPT/Visual_Aid_Trigger_Defense_2026-11-14.md`.

## Bookmark-only follow-up: limit distracting persistence

The owner reports the live bookmark remains missing while Replay works, then declines a proposed diagnostic recording and prioritizes the distraction from a wrongly triggered persistent bookmark. Both connected phones' saved settings are AUTO with bookmark enabled, adaptive strength enabled and maximum1.0; auto-scroll is off. There are no existing exported aid traces. Live input forwards unavailable observations but additionally uses blink filtering and capture-geometry/motion checks; Replay trajectories do not establish those live conditions. The cause of missing live activation remains unestablished.

This follow-up changes persistence only as described above, preserves75% opacity and activation criteria, and makes no focus-aid change. Independent focused review found a leftover frontier-route hold that would delay dismissal across the saved row; it is removed for current3s recovery and retained only for legacy zero-hold comparison fixtures. The new regression crosses the frontier at3s. Review reports the finding resolved with no new actionable issue. Focused checks/build evidence is in `output/bookmark-transient-2026-11-14/`. No new camera recording, calibration or broad device test is required for this correction.

## Checks

Earlier supplied-input runtime/render results remain in `output/bookmark-update-2026-11-14/`; they do not establish the actual current Replay screen behavior. Follow-up builds, explicit current-profile JVM regressions, real AUTO Replay Activity checks, synthetic Activity screenshots, APK hashes and before/after calibration/recording hashes are retained in `output/bookmark-replay-fix-2026-11-14/`. The final receipt identifies completed checks and limits. Synthetic checks establish software behavior, not natural-reading accuracy or benefit.

Follow-up results:336JVM/replay tests across35suites pass; both debug APKs build. Both SM-G991B and SM-A566B pass29camera-free native tests, including4actual Replay Activity runs (lookaway, tracking loss, four short row visits, full broad reread). All4choose/draw current V5/ruleV4 bookmarks at0.5; departure cases clear after supported recovery. Actual Activity images show the marker; short-visit screenshots on both phones and the broad-reread screenshot on SM-G991B were visually inspected. Independent focused source review reports no actionable findings. The first native run failed only a screenshot observation that incorrectly relied on the last-chosen-aid label when focus and bookmark coexisted; the test now reads active production actions without injecting them. Its initial log is preserved, and the corrected29-test set passed per phone. Both calibration files and all13existing study manifests remain hash-identical; camera app-ops show no new access.
