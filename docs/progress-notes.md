# NewsMead — Progress Notes

## 2026-09-12 - MGazeNet article pipeline throughput

On the Galaxy A56 article screen, face-present MGazeNet output improved from about 8 FPS to a sustained 24 FPS without changing resolution, model precision, pixel/crop semantics, gaze coordinates, or calibration identity. Promoted the previously validated finite-input guard, moved bitmap conversion/normalization out of Kotlin hot loops, removed landmark/article/UI allocations and redundant redraw/log work, and overlapped serial GPU localization with bounded double-buffered CPU inference. The steady face-present stage averages were 4.5-6.2 ms copy/rotation, 21.9-23.7 ms localization, 1.3-1.4 ms preprocessing, 26.5-29.0 ms MNN, and 0.3-0.4 ms SVR; article rendering remained healthy at 5 ms median and 8 ms P99 in the post-reset sample. Focused MGazeNet/geometry/stability tests and all six native A56 pipeline tests pass; the full 216-test JVM run has one unrelated legacy Firebase-runtime failure.

## 2026-09-03 - Replicated vertical control and offset-only offline screening

At the user's request, retained the original experiment unchanged for two more pairs rather than ending evaluation after one adverse run. The second ON run helped guided reading but hurt word targeting; the third harmed accuracy on its own samples. Then evaluated one fixed median-reference offset rule across all six recordings. It improves some aggregate scores strongly but turns top-left within-one-line accuracy from 100% to 0% in three recordings, so it is not ready for implementation. The line-only replay reconstructs actual layout geometry and reproduces all 10,580 base/effective assignments; all 11 pure analyzer tests pass. Detailed findings and aggregate results are in `gaze-offset-replay.md`. Newer raw logs were read from the phone in memory, not copied into OneDrive. No Android source/build/install or git staging/commit change was made.

## 2026-09-02 - Reading-surface vertical alignment rejected

The protocol-v4 telemetry-OFF pair completed under one fresh calibration and no nine-point correction. ON passed the original three-reference guards, but the same-sample counterfactual showed a harmful trade: raw word exact-line accuracy rose from 13.1% to 35.7%, while guided within-one-line accuracy fell from 74.6% to 29.2%, overall within-one-line fell from 70.5% to 47.8%, and corrected validity fell to 80.2%. One bottom-line checkpoint became entirely invalid. The global session y gain/bias candidate is rejected; do not repeat or tune it from this run. Restore the uncorrected reading path before the next bounded upstream intervention.

## 2026-09-01 - Known-target reading accuracy instrument

Device smoke testing rejected the initial free-reading/adaptive protocol as confusing and unable to establish independent reading ground truth. Replaced it with an accuracy-only instrument: unscored visible-dot preview, eight highlighted A/B word fixations, and six highlighted physical-line reading trials; timestamped numeric-only JSONL remains, while adaptive validation is explicitly deferred. The first accuracy-only v2 device run was also rejected because changing instructions and timing status remained visible below the passage during active targets and competed for the participant's gaze. Protocol v3 presents instructions only in centered between-section dialogs and hides the entire control panel while targets are active. Its first valid run showed only 40.5% guided exact-line and 10.9% exact-word accuracy with vertical center compression. Protocol v4 adds an unvalidated control: OFF and ON collect identical top/middle/bottom live reading-surface references, while only ON may apply a fixed-guard, session-only vertical gain/bias layer; estimator, quadratic mapper, temporal filters, and existing affine persistence are unchanged.

## 2026-08-28 - Five-pair offline tail analysis

Compared the five valid recent calibration/immediate-test pairs from app-private aggregate logs without collecting another run or copying participant artifacts. Mapper conditioning and held-out errors did not rank the later live tails. Top-left was the worst calibration LOO point in all five runs and the worst live point in four, confirming a systematic region/first-target weakness rather than a bad-session selector. High drift was the only directional warning, but low drift still allowed a 3.04-line live tail. The next isolated change is therefore a conservative gate rule: high drift forces one Redo All; a second high-drift result aborts and retains the prior calibration. This is not an accuracy guarantee, and no estimator/mapper/filter/target/order change is authorized by this finding.

## 2026-08-28 - Fresh-session handheld repeatability check

The independent telemetry-OFF calibration/test pair completed correctly with no affine correction: 25.6 FPS, 12 retained test samples median, 1.55-line vertical median, and a 4.87-line top-left maximum. Typical error remains substantially better than E-030, but severe calibration/held-out tails returned (LOO max 6.24 lines; held-out median/max 4.49/7.44), so the current path is improved but not repeatable enough for exact-line claims; no further phone run or code change yet.

## 2026-08-24 - Centered pre-run countdown

Added a centered 3–2–1 countdown before full calibration, calibration redo paths, and every nine-point measurement so capture no longer begins immediately after the setup dialog or gate action. The countdown is outside all target/sample timing and does not change estimator, mapper, filters, order, or per-target behavior; all gaze JVM tests and `assembleDebug` pass, and the build is installed on the A56.

## 2026-08-24 - Detailed telemetry ON/OFF control

Added labelled ON/OFF selection to full calibration and the nine-point check. OFF removes per-frame source/pipeline callbacks and arrays while both modes retain point metrics plus constant-memory FPS summaries; estimator, mapper, filters, target, timings, and order are unchanged. Focused and all gaze JVM tests plus `assembleDebug` pass. Two valid natural-handheld calibration/test pairs per mode showed no OFF FPS or accuracy recovery, so detailed telemetry is closed as the regression cause; the mismatched `off_cal_redo-1` attempt is excluded by its embedded ON mode.

## 2026-07-26 - DEMO_CYCLE scaffold mode for presentation recording

Added `StudyConfig.ScaffoldMode.DEMO_CYCLE` (+ `SCAFFOLD_DEMO_LEVEL_DURATION_MS`
12s, `SCAFFOLD_DEMO_CAPTION`): holds each level in turn (NONE -> WORD -> LINE ->
FOCUS -> REENTRY, looping) while real gaze still drives placement, so all four
render in one continuous take without a rebuild per level. `forcedLevel` is now a
settable `var` and forced runs also track `lastStableLine`, so a forced REENTRY
anchors to the line the reader actually left. **`SCAFFOLD_MODE` is currently set
to DEMO_CYCLE - set it back to ADAPTIVE before any participant-facing session.**
Demo bypasses baseline/confidence/persistence, so it shows the renderers only,
never instability detection. compileDebugKotlin + assembleDebug + all
com.newsmead.gaze.* tests pass (new forced-anchor test).

## 2026-07-24 - Calibration redesign doc (review of external draft)

Reviewed an externally drafted 16-point calibration design and wrote the revised,
codebase-accurate version as docs/calibration-design.md (state machine, feature-unit
rejection/dispersion, drift check, post-fit LOO + validation quality gate with
Accept/Redo, session JSON log; §9 lists all corrections vs. the draft).

Same day - implemented the full redesign. New: `FixationWindowFilter` (lead-in
trim, MAD outlier rejection, dispersion gate + sub-window fallback),
`CalibrationQuality` (LOO residuals, median/P95), `CalibrationSessionLog`
(incremental per-session JSON). Rewrote `GazeCalibrationActivity` (per-point
APPEAR/HOLD/SETTLE/SAMPLE-adaptive/CONFIRM state machine, randomized order +
practice point + drift repeat, 2-attempt exclusion with 12-point floor, audio
cues, 5-point held-out validation pass, Accept/Redo-worst/Redo-all gate with
line-height error bands; **CSV now written only on Accept**) and
`CalibrationView` (light reading-surface background, dp bullseye, appear/confirm
animation, 16-dot mini-map). Verified: `:app:assembleDebug` + all
`com.newsmead.gaze.*` JVM tests pass (JDK 17), incl. new
FixationWindowFilterTest/CalibrationQualityTest. On-device (A56) validation of
the new flow still required; thresholds (dispersion 0.02, gate bands 1.0/1.5
lines, drift 0.5 line) are provisional pending pilot.

Same day - **first on-device run regressed vs. the old design** (worse accuracy,
gate always said redo). Root cause: the 0.02 feature-unit dispersion gate was
BELOW the tracker's own per-frame noise floor (~0.04 horizontal, ~0.05-0.10
vertical), so no SAMPLE window ever passed -> every point timed out, retried,
excluded -> full-calibration abort; the rare sub-window that passed picked
stable-but-off-target moments (worse than plain median). Fix: removed the
dispersion gate AND sub-window selection from `FixationWindowFilter` - kept only
lead-in trim + 2.5x MAD outlier rejection + median (old robust behavior plus a
light trim). Dispersion still computed/logged, not used to reject. Also
re-anchored gate bands to the tracker's DOCUMENTED accuracy (green <=3.0 /
amber <=4.5 line-heights; drift flag at 1.0 line) since the old 1.0/1.5 bands
were below the tracker floor and read red on every normal run. Tests updated;
compileDebugKotlin + all com.newsmead.gaze.* pass. Re-test on A56.

## 2026-07-24 - Calibration button moved to home + drift correction from accuracy test

- Home page: replaced the language-toggle button ("Show me English/Filipino
  news" - inert in offline study mode, all articles English) with "Calibrate
  eye tracker" launching GazeCalibrationActivity. Removed the calibration
  button from the article top bar (accuracy-test button stays, re-anchored
  next to back). Language-toggle logic deleted from HomeFragment; restore from
  git history if Filipino content ever returns.
- Accuracy test now doubles as re-adaptation (James's idea): after a run, an
  affine drift correction (truth ~ A*predicted + b) is fitted from the 9
  (predicted, target) pairs and offered with pre -> est.-post medians. Applied
  post-mapper in LocalCalibratedGazeProvider (16-pt fit + GazeProvider boundary
  untouched), persisted as drift_correction.csv, composed across successive
  runs, cleared automatically when a new full calibration is accepted.
  Deliberately an affine layer, NOT a fresh 9-point polynomial refit (thin fit
  would risk replacing a better map). New DriftCorrection + 6 JVM tests;
  design doc §7.1. assembleDebug + all com.newsmead.gaze.* tests pass.
  On-device drift-correction round-trip (test -> apply -> re-test) pending.
- On-device: drift correction confirmed to improve accuracy (James), but gaze
  hit an "invisible barrier" - would not track above/below a session-varying
  vertical bound (sometimes near top, sometimes mid-screen). Root cause:
  GazeMapper hard-clamped live features to the calibrated min/max range, so
  head-pitch drift beyond the calibrated range froze the mapped output at the
  boundary px - and the active affine drift correction relocated that frozen
  point per session, which is why the wall moved. Fix: replaced the hard clamp
  with gradient (first-order) extrapolation beyond the calibrated range, capped
  at 1.5 standardized units, so out-of-range gaze continues smoothly instead of
  stopping while garbage features still can't fling the estimate to infinity.
  New GazeMapperTest covers inside-range fidelity, barrier removal, and the
  cap. No recalibration needed (fit unchanged, mapping only).

## 2026-07-24 - Barrier persisted after recalibration + fixation-pulse dot

- Barrier still appeared after a fresh calibration. Found the remaining hard
  clamp: the gradient extrapolation in GazeMapper.map() hard-capped the
  out-of-range excess at +/-1.5 z (coerceIn) -> a flat wall once gaze went 1.5 z
  past the calibrated range, which lands mid-screen when a calibration captured
  little vertical feature spread. Replaced the hard cap with a tanh soft-limit
  (widened to 2.5 z): ~linear near the boundary (keeps moving, no wall), smooth
  asymptote far out (still bounds glitch features). Note: polynomialFeatures()
  retains a raw-space clamp but is used only for FITTING (in-range samples), not
  live mapping - harmless.
- Added a raw->mapped diagnostic log (tag `GazeMap`, every 15 samples) in
  LocalCalibratedGazeProvider: shows raw feature, filtered feature, and mapped
  px together. If a wall persists, this distinguishes source-side vertical
  feature saturation (raw fy flatlines) from mapping (raw moves, mapped stops).
  Watch this next on-device: `adb logcat -s GazeMap`.
- Calibration target: inner red dot now "breathes" (scale-only pulse 0.7-1.3x,
  650ms, against the fixed ring) to hold fixation on the centre. Scale-only =
  no translation, so it does not induce smooth pursuit. This intentionally
  relaxes the design doc's "no motion during SAMPLE" for a centred pulse;
  revisit if pilot data shows fixation instability.

## 2026-07-24 - Fixed live gaze not restarting after the accuracy test

- Symptom: after running the accuracy test / applying drift correction from the
  article page, gaze appeared dead until fully exiting and re-entering the
  article - and reading showed no benefit from the correction.
- Root cause: a CameraX teardown race. GazeTestActivity shares the process-wide
  ProcessCameraProvider singleton and calls unbindAll() in ITS onDestroy, which
  runs *after* ArticleFragment.onResume. The article rebound the camera in
  onResume, then the finishing test activity's unbindAll() immediately clobbered
  it. A fresh re-entry worked only because nothing tore down afterward. This
  also explained "no improvement": the article never actually ran with the newly
  applied correction (it reloads the correction in attachLiveGaze, but the
  camera was dead).
- Fix: ArticleFragment.onResume now defers restartLiveGazeAfterCalibration by
  GAZE_RESTART_DELAY_MS (800ms, isAdded-guarded) so the test activity finishes
  its onDestroy camera release before the article rebinds. attachLiveGaze
  already reloads drift correction, so reading now uses it without re-entry.
  assembleDebug OK. On-device: test -> apply -> back should now show the live
  dot resume within ~1s and reflect the correction; confirm via `GazeMap` log
  showing the "[drift-corrected]" suffix during reading.

## 2026-07-25 - Corrected stale tracker documentation (GazeFollower -> MediaPipe)

`docs/build-context.md` and `CLAUDE.md` both still declared GazeFollower-over-WiFi
the "confirmed, final tracker" and instructed *not* to build MediaPipe/CameraX
code here - contradicted by the code since 2026-07-06 (WiFiGazeProvider.kt and
GazeStream.kt deleted, MediaPipeRawGazeSource.kt active) and by
implementation-spec.md, which calls MediaPipe "the current tracker."

Both updated. The STATUS UPDATE in build-context.md is now a **tracker decision
history table** (MediaPipe on-device failed -> MobileGaze/TFLite failed ->
GazeFollower passed and was adopted -> reverted to on-device MediaPipe on
deployability grounds), kept deliberately because it is the evidence trail for
the manuscript's "alternatives considered" section. Recorded explicitly that the
revert traded raw accuracy for deployability (no co-located laptop per session)
and is NOT an accuracy improvement, and that accuracy remains the binding
limitation with word/exact-line claims conditional on pilot validation. Stage
3's historical FAILED verdict is retained with a "superseded" note rather than
deleted. Deliverables checklist corrected (Stages 4-5 complete; accuracy
re-measurement and target-age validation outstanding).

Also fixed `docs/session-setup.md` steps 3-4: calibration now launches from the
**home screen**, not the article page; the article top-bar control is the 9-point
accuracy check.

Follow-up same day: Stage 4 and Stage 5 section headers in build-context.md also
corrected (were "[ACTIVE - CURRENT WORK]" and "[NOT STARTED]"; both are complete
- Stage 5 is `ReadingStateInferencer.kt` + `WindowedStabilityEstimator.kt`).

Working figures/assumptions recorded for now, both to be confirmed later:
- **Stage 3 vertical error: ~1.1-2.3 cm** (per James). Other figures recorded
  elsewhere in the repo differ (prototype 16-point R1 vertical 0.60 cm / R2
  2.30 cm; newsmead 3-run vertical median 0.67-1.01 cm) and all predate the
  2026-07-24 calibration rebuild. Re-measure on the current build and make every
  doc + the manuscript cite one set.
- **The manuscript is assumed to still describe the GazeFollower/laptop
  architecture and to need revision.** NOT verified - `Manuscript_Updated.pdf`
  is password-protected and could not be read from here. Confirm by searching it
  for "GazeFollower", "WiFi", "laptop".

Defense prep: expect "your own docs say this tracker failed validation" - answer
is the deployability trade, the calibration rebuild, and the limitation being
retained rather than claimed solved.

## 2026-07-25 - Section 4.3.2 manuscript draft for the gaze/calibration system

Added `docs/calibration-methods.md`: insertable Section 4.3.2 (Gaze Tracking)
draft covering feature extraction, the 16-point calibration procedure, the
polynomial mapping model, quality assurance, and 9-point verification/drift
correction. Follows the conventions of `manuscript-implementation-alignment.md`
(blockquoted manuscript prose, formula fences, provisional-parameter marking,
traceability table, consistency checklist), which now cross-references it.

Terminology decision recorded: the 3x3 procedure is **accuracy verification**
(+ affine **drift correction**), NOT "9-point calibration" - it never re-estimates
the polynomial. Two items flagged for James before submission: (1) the A56
accuracy figures in these notes predate the calibration redesign and must be
re-measured; (2) MediaPipe confidence thresholds read 0.5 in source but 0.35 in
the 2026-07-06 note - source is authoritative, confirm before citing.

## 2026-07-24 - Re-calibration system re-architected to the calibration standard

Full re-architecture (design doc §7.2); re-calibration stays on the article page.
- Extracted `CalibrationPointCollector` - the shared per-point capture engine
  (APPEAR/HOLD/SETTLE/SAMPLE-adaptive/CONFIRM + FixationWindowFilter driving
  CalibrationView). Both GazeCalibrationActivity and GazeTestActivity now use it,
  removing the duplicated/divergent sampling (the test previously used the old
  plain-median-over-fixed-window path, which is why improving calibration never
  improved the test). GazeCalibrationActivity refactored onto it with behavior
  preserved.
- Redesigned GazeTestActivity as an accuracy-check + drift re-calibration screen:
  shared capture (good sampling, light bg, pulsing bullseye, audio, mini-map);
  3x3 measure feeding an affine fit in screen space; **honest leave-one-out**
  post-correction estimate (DriftCorrection.leaveOneOutMedianPx) instead of the
  optimistic in-sample residual; median/P95/vertical + line-height bands
  matching the calibration gate. Remedy gate now offers Apply / **Revert to
  calibration** / **Full recalibration** / Measure again (was Apply-only).
- Added append-only recalibration_log.csv audit trail (apply/revert) alongside
  the single active drift_correction.csv.
- New DriftCorrection.leaveOneOutMedianPx + tests. assembleDebug + all
  com.newsmead.gaze.* tests pass. On-device: re-verify measure -> apply -> revert
  and that calibration still runs identically after the collector refactor.

## 2026-07-07 — Align MediaPipe gaze feature with the prototype

Reworked `gaze/MediaPipeRawGazeSource.kt` to match the sister-repo prototype's
`FaceLandmarkerHelper` feature extraction: front-camera frame is now mirrored
(`postScale(-1,1)` in `uprightMirrored`), each iris is paired to its nearer eye
via the `frac`-based corner/eyelid feature (iris rings 468..472 / 473..477), and
blink frames are dropped via eye-aspect-ratio openness with hysteresis
(BLINK_CLOSE 0.04 / BLINK_OPEN 0.055) before `onRawGaze` emits. Calibration and
live gaze share this source, so both benefit and stay self-consistent. Raw gaze
feature values are NOT comparable to the pre-change ones — recalibrate.

Follow-up (same day) — fixed low FPS / lost vertical accuracy: `MediaPipeRawGazeSource`
was running CPU inference on an uncapped-resolution stream with no FPS floor, so it
ran far below real time and starved the median/One Euro smoothing. Ported the
prototype's Stage 1 camera profile: GPU delegate with CPU fallback, 480x360 analysis
resolution, and `CONTROL_AE_TARGET_FPS_RANGE` pinned to 30. Also switched
`detectAsync` to the monotonic `nextTimestampMs()` (avoids MediaPipe timestamp
collisions at 30 fps). Added an FPS readout: `LocalRawGazeSource.OnFps` callback
surfaced from the source, shown top-right on the calibration screen (`fpsText`) and
top-left on the article `GazeOverlayView` during live reading.
Verified `:app:compileDebugKotlin` passes. NOTE: CLI kapt fails on JDK 21 without
`--add-opens jdk.compiler/...`; build in Android Studio or add those to
`gradle.properties` `org.gradle.jvmargs`.

Follow-up (same day) — crash fix + gaze test. On-device: repeated native SIGBUS in
`libmediapipe_tasks_vision_jni.so` (~75-90s in). Bumped MediaPipe tasks-vision
0.10.14 -> 0.10.29 (matches prototype, which crashed far less); crash gone in
testing. Diagnosed "dot stuck at top": logcat of live raw feature shows gaze_y
varies fine (0.19-0.38) and mapped Y sweeps full screen (108-2207) on deliberate
up/down, but median gaze_y in reading ~0.33 (upper part of calib range) so the dot
biases high during natural reading — this is the inherent appearance-based limit,
not a mapper bug. Ported the prototype's Stage 3 accuracy test as
`GazeTestActivity` (+ `GazeDotView`, `activity_gaze_test.xml`, `gaze_test_*`
strings): live calibrated dot on a blank screen + 3x3 (20/50/80%) benchmark that
logs median + vertical error (px/cm) under tag `GazeStage3`. Launch via the new
dot button next to the calibrate button in the article toolbar (needs calibration
first). Diagnostic raw-gaze logging added to `MediaPipeRawGazeSource` (tag
`MediaPipeGaze`, every 15 frames).

Measured accuracy (A56, MediaPipe on-device path, 3 runs): vertical median
0.67-1.01 cm, overall median 1.68-2.42 cm; horizontal poor/right-shifted. James
reports the prototype gets ~0.7 cm CONSISTENTLY with this same adopted logic, so
this is NOT a ceiling - newsmead was still diverging. Gaze-test UX: per-point
error now shown on-screen (numeric + yellow estimate dot + miss line); fixed the
article toolbar so Read Aloud no longer overlaps the new test button.


Follow-up (same day) - blink threshold configurability + accuracy diagnostics: moved the MediaPipe blink hysteresis thresholds out of hardcoded source constants into `StudyConfig.GAZE_BLINK_CLOSE_THRESHOLD` / `GAZE_BLINK_OPEN_THRESHOLD` (defaults remain 0.04 / 0.055). Added `LocalRawGazeSource.BlinkStats` so the raw source reports emitted samples, blink-dropped frames, no-face frames, last openness, blink state, and active thresholds. `GazeTestActivity` now logs these as per-point and whole-run deltas in `GazeStage3`, alongside signed `dx/dy`, so accuracy runs can show whether poor results came from bias, false blink filtering, missing landmarks, or low usable sample count.
Found + fixed the last calibration divergence: `GazeCalibrationActivity` was
collecting differently from the prototype - COLLECT_MS 2500 (proto 1000),
MIN_SAMPLES 5 (proto 10), and MAD outlier rejection (`robustCenter`) instead of a
plain component-wise median. Since the mapper is only as good as its calibration
points, aligned all three to the prototype (1000 ms, 10 samples, plain median;
removed robustCenter/OUTLIER_K). MUST RECALIBRATE for this to take effect, then
re-run the accuracy test. If still short, next suspect is the live provider blink
handling: prototype `MediaPipeGazeProvider` resets the median filter on
blink-reopen; newsmead drops blinks at the source and doesn't reset - not yet
ported.

## 2026-07-01 — Bypass login gate for thesis research-instrument use

**Context:** NewsMead is being repurposed as a research instrument for a thesis
study. No real users, no personalization, no account system. The Firebase login
screen was blocking access to the home/article screens, and we don't want to
stand up the backend auth flow just to get past it.

### What the login gate was
- Enforced in `app/src/main/java/com/newsmead/activities/SplashActivity.kt`
  (the `LAUNCHER` activity, see `AndroidManifest.xml`).
- Check: `if (auth.currentUser != null)` — a **Firebase Authentication** check
  (`FirebaseAuth.getInstance().currentUser`). Not a local session token, not a
  `newsmead-api` call. If a Firebase user existed → `MainActivity` (home);
  otherwise → `AccountActivity` sign-up/login.
- `MainActivity` hosts `nav_graph.xml`, whose `startDestination` is already
  `homeFragment`. So the only thing gating the home screen was that one `if`.

### The change (smallest, reversible, non-destructive)
Added a feature flag in `SplashActivity` and short-circuited the auth check:

```kotlin
companion object {
    const val BYPASS_LOGIN = true   // false = restore normal login/sign-up flow
}
...
if (BYPASS_LOGIN || auth.currentUser != null) {
    navigateToMainActivity()
}
```

- All login code (LogInFragment, SignUpFragment, AccountActivity, Firebase) is
  left fully intact for later reference. Flip `BYPASS_LOGIN` to `false` to revert.
- Launch now goes straight to `MainActivity` → `homeFragment`.

### Known secondary consideration (feed content)
- Article/home content comes from a **public REST API** (Volley GETs to
  `newsmead.southeastasia.cloudapp.azure.com`) that does **not** require a
  Firebase token — see `DataHelper.loadArticleData()`. Browsing by
  category/source/search uses the `/articles/?…` endpoint and works with no login.
- BUT the **default home feed** hits `…/recommendations/{uid}`, where
  `uid = FirebaseHelper.getUid()`. With no Firebase user, `getUid()` returns the
  literal string `"null"`, so the request becomes `…/recommendations/null`.
  If the recommender rejects an unknown uid, the **default home feed may be
  empty** (screen still renders; list just comes back empty).
  - Server behavior for `recommendations/null` was not verifiable at edit time
    (the Azure host was unreachable from the dev sandbox).
  - If the feed comes up empty on-device, the smallest fix is to fall back to the
    generic `/articles/` endpoint when `uid == "null"` inside
    `DataHelper.loadArticleData()` (the code already knows how to parse that
    response). Not applied yet — pending confirmation of actual server behavior.
- Firebase-backed features (Saved lists, History, Profile) already guard on
  `uid == "null"` and degrade gracefully to a "Please login" toast — no crash.

---

## 2026-07-02 — Offline local article dataset (backend is gone)

**Context:** The content backend no longer exists — both API hosts
(`newsmead.southeastasia.cloudapp.azure.com` and `…-fil.…`) **no longer resolve
in DNS** (Azure VMs deprovisioned). So bypassing login alone leaves every feed
empty. Per the study plan (Option 1), article content is now served from a
bundled local JSON asset so the app runs **fully offline**.

### Switches
All study-mode flags now live in one place:
`app/src/main/java/com/newsmead/data/StudyConfig.kt`
- `BYPASS_LOGIN = true` — used by `SplashActivity` (replaces the local const
  from the previous entry).
- `OFFLINE_MODE = true` — serve articles from the local asset.
- `ARTICLES_ASSET = "study_articles.json"`.
Flip both to `false` to restore full production behaviour.

### What changed
1. **`DataHelper.loadArticleData()`** — when `OFFLINE_MODE`, delegates to a new
   private `loadLocalArticleData()` that reads `assets/study_articles.json`,
   parses the **same JSON schema the old API returned**, and applies the same
   filters server-side used to (source / category / language / search / pageSize).
   So home feed, search, by-source, by-category, and the recommended list all
   work unchanged against local data.
2. **`FirebaseHelper.isNetworkAvailable()`** — returns `true` immediately when
   `OFFLINE_MODE`. The feed screens only render inside
   `if (isNetworkAvailable(...))` (8 sites), so without this the shimmer would
   spin forever on a device with no network.
3. **`app/src/main/assets/study_articles.json`** — the dataset. Now holds **2
   original dummy study articles** (`article_id` 1 = health/lifestyle,
   2 = drainage/news), both English, `source: "study"`. Source anonymized:
   `"study"` maps to the neutral in-app label **"NewsMead"** (via `sourceNameMap`
   / `reverseSourceNameMap` in `DataHelper`) so nothing looks like a real outlet.
   Add more articles (up to the 4 A–D conditions) by appending to the array.

### JSON schema (dictated by the parser in `DataHelper`)
Top-level object with an `articles` array. Each article:

| field        | type   | notes |
|--------------|--------|-------|
| `source`     | string | raw key: `gmanews`, `inquirer`, `philstar`, `manilabulletin`, `news5`, `abantenews`, or `study`. Maps to the bundled logo + display name; any other value → generic logo + blank name. `study` → display name **"NewsMead"** + generic logo (added for the anonymous thesis articles). |
| `title`      | string | headline |
| `image_url`  | string | hero image. `""` = none. For an offline image, put the file in `assets/study_images/` and use `file:///android_asset/study_images/<file>` (Glide loads it). A normal `http(s)` URL only shows if the device happens to be online. |
| `date`       | string | **must** be `yyyy-MM-dd HH:mm:ss` (falls back to the raw string if unparseable, logged as a warning) |
| `body`       | string | full article text, no length limit |
| `category`   | string | e.g. `news`, `opinion`, `sports`, `technology`, `lifestyle`, `business`, `entertainment` |
| `language`   | string | `English` or `Filipino` (non-English hides the Translate button, which needs the dead API) |
| `read_time`  | string | free text, e.g. `"5 min read"` |
| `url`        | string | original article URL (used by the Share button) |
| `article_id` | int    | unique integer; identity used for de-dup and Latin-square condition assignment |

Latin-square note: the 4 articles map cleanly to conditions A–D via
`article_id`. The feed shows them in array order; per-participant ordering is a
study-runtime concern, not yet wired into the app.

---

## 2026-07-02 — Stage 4 prep: article view confirmed native + font size locked

- **WebView vs native (Stage 4 open question):** RESOLVED — the article body is a
  native `TextView` (`tvArticleText`, set via `.text = article.body`). No WebView
  anywhere (only a boilerplate mention in `proguard-rules.pro`). So line boxes for
  AOI mapping come from the `TextView` `Layout` API, not injected JS.
- **Font size (Stage 4 blocker):** RESOLVED and locked.
  - The article view had **no "large" preset** — just a ± stepper (min 14 / max 60,
    `ArticleFragment.addBottomAppBarListeners`). Default body = **20sp**
    (`ts_body_large`). The stepper is buggy (mixes px/sp: "larger" +~0.7sp/tap,
    "smaller" −2sp/tap) and density-dependent — unusable for fixed AOIs.
  - **Decision: 22sp**, locked. Implemented in `StudyConfig`
    (`LOCK_ARTICLE_FONT_SIZE`, `ARTICLE_FONT_SIZE_DP = 22f`) and applied in
    `ArticleFragment` via `setTextSize(COMPLEX_UNIT_DIP, 22f)` — **dp, not sp**, so
    the OS accessibility font-scale can't change the pixel size. The ± controls
    (`llArticleBottomButtons`) are hidden in study mode → no runtime adjustment.
  - NOTE: body line spacing is `lineSpacingMultiplier 1.6` + `lineSpacingExtra 1sp`
    (from `ts_body_large`) — relevant when computing per-line vertical bands.
  - Color-mode buttons (light/dark/sepia) are left active; they don't change line
    geometry. Flag if you want them locked too for visual consistency.

---

## 2026-07-02 — Stage 4 slice 1: WiFi gaze stack ported into NewsMead

Ported the self-contained GazeFollower-over-WiFi tracker from
gaze-thesis-prototype into **`app/src/main/java/com/newsmead/gaze/`** (verbatim
except `package`): `GazeProvider` (the swappable boundary), `WiFiGazeProvider`,
`GazeStream` (UDP listener, port 5005), `GazeMapper` (affine laptop-px→phone-px),
`OneEuroFilter`, `MedianFilter`, `CalibrationStore` (+`CalibrationSample`).
- Added dep `org.apache.commons:commons-math3:3.6.1` (GazeMapper least-squares).
- Added `<uses-permission android:name="android.permission.INTERNET"/>` for the
  UDP socket (was only present transitively via Firebase/Volley).
- `:app:compileDebugKotlin` → BUILD SUCCESSFUL. Not yet wired to any screen.
- NOT ported (prototype-only, not needed here): the Stage-1/2/3 camera/menu
  activities, `WiFiListenerActivity`, `CalibrationActivity`/`CalibrationView`,
  `GazeDotView`. The calibration *capture* UI is still needed to produce
  `calibration_wifi.csv` before `WiFiGazeProvider` can map real gaze — see slice
  decision below.

## 2026-07-02 — Stage 4 slice 2: article-screen wiring (touch-validation)

Wired the gaze AOI layer into the article reading screen, driven by touch for
validation (no tracker/laptop needed yet). New files in `com.newsmead.gaze`:
- **`LineAoiMapper`** — `lineAt(screenY)` → body line index. Scroll-aware for free:
  uses `tvArticleText.getLocationOnScreen()` (which already moves with scroll), so
  `screenY − textViewTop − totalPaddingTop` indexes the `Layout` directly; returns
  −1 when the point is off the text. Stable because font size is locked (22dp).
- **`GazeOverlayView`** — translucent dot drawn from full-screen px; non-interactive
  (passes touch through), sits on top of the article.
- **`ArticleFragment.setupGazeDebug()`** — gated by `StudyConfig.GAZE_ENABLED`. Adds
  the overlay over `binding.root`, and routes all gaze through one
  `GazeProvider.OnGaze` entry point (so live `WiFiGazeProvider.setOnGaze` drops in
  later with no fragment changes). With `GAZE_TOUCH_VALIDATION`, an
  `nsvArticleText` touch listener feeds `event.rawX/rawY` and returns false so
  scrolling still works.
- Logs `GazeAOI: gaze=(x,y) line=N/total` per event.

**On-device test (James, A56 — no laptop needed):** open a study article, drag a
finger down the text, `adb logcat -s GazeAOI`. The logged `line=N` should match the
line under your finger, and stay correct after scrolling. This is the build-spec
touch-validation gate; passing it isolates any later error to the gaze tracker,
not the integration.

`:app:assembleDebug` → BUILD SUCCESSFUL (with JDK 17).

**On-device (A56):** VALIDATED — `GazeAOI` logs stream and `line=N` is responsive
to touch as the finger moves down the passage (touch-validation gate). Scroll-
awareness confirmation recommended as a final check.

## 2026-07-02 — Stage 4 slice 3: live WiFi gaze wired (needs laptop rig to test)

Ported the calibration *capture* screen and wired the live tracker into the
article screen. Builds (`:app:assembleDebug` OK, JDK 17). **VALIDATED live on the
A56 (2026-07-02):** full rig run passed — laptop stream → 16-dot phone
calibration → live gaze on the article view, dot follows gaze and `line=N` tracks
the read line, scroll-correct. Stage 4 acceptance met.
- **`GazeCalibrationActivity`** (`com.newsmead.activities`) + `CalibrationView`
  (`com.newsmead.gaze`) + `activity_gaze_calibration.xml` + `calib_*` strings —
  full-screen 16-dot calibration, reads UDP gaze via `GazeStream`, robust
  outlier-rejected median per dot, writes `calibration_wifi.csv` via
  `CalibrationStore`. Registered in manifest (`exported=true`, see below). Logs
  the phone IP (`GazeCalib` tag) for the laptop's `--phone-ip`. Has a **"Start"
  gate** — opens and waits for a tap so the researcher can position the phone
  before the dots begin (doesn't auto-run).
- **`ArticleFragment`**: when `GAZE_TOUCH_VALIDATION = false`, builds
  `WiFiGazeProvider(GazeMapper(CalibrationStore.load()))`, streams gaze through
  the same `GazeProvider.OnGaze` entry point (main-thread hop), and stops it in
  `onDestroy` (releases the UDP socket). Falls back to touch validation if no
  calibration exists or the fit fails.
- Flags unchanged (`GAZE_TOUCH_VALIDATION = true`), so current behaviour is still
  touch validation until the flag is flipped for a live session.

**Launch trigger (decided):** adb, no in-app UI (laptop is always present at a
session). Launched with
`adb shell am start -n com.newsmead/.activities.GazeCalibrationActivity`.
NOTE: `GazeCalibrationActivity` must be `android:exported="true"` — Android 16 /
One UI on the A56 blocks adb from starting non-exported activities (confirmed:
`SecurityException: not exported`). Verified launching on-device. Full runbook:
**docs/session-setup.md**.

**End-to-end live test — PASSED (A56, 2026-07-02):** per docs/session-setup.md, all
steps 1–6 confirmed by James (laptop stream → phone calibration → live
gaze-to-line on the article, scroll-aware, no socket leak on re-entry).
Manifest fix required along the way: `GazeCalibrationActivity` set
`exported="true"` (Android 16 blocks adb launching non-exported activities).

### Build note
- The project's kapt is **incompatible with JDK 21** (fails with
  `module jdk.compiler does not export com.sun.tools.javac.main`). This is a
  pre-existing environment issue, unrelated to this change.
- Build with **JDK 17** instead, e.g. `C:\Program Files\Java\jdk-17`:
  `JAVA_HOME="C:\Program Files\Java\jdk-17"; ./gradlew :app:compileDebugKotlin`
- Verified: `:app:compileDebugKotlin` → **BUILD SUCCESSFUL** with all changes
  above (login bypass + offline dataset). `study_articles.json` validated as
  well-formed. Full on-device confirmation (home feed renders, article opens)
  still requires an emulator/device.

## 2026-07-02 - Stage 5 slice 1: RSI feed from line AOI stream

Added `ReadingStateInferencer` in `com.newsmead.gaze` and wired it at the existing `GazeProvider.OnGaze` entry point in `ArticleFragment`. It emits/logs `GazeRSI` line samples, fixation, dwell, and regression events only; no saccade or microsaccade features. Targeted JVM tests for the RSI inferencer pass; full `testDebugUnitTest` still has the pre-existing `FirebaseTest` JVM Android API failure.
## 2026-07-05 - Stage 5 slice 2: RSI scoring, regression debounce, and interpretation docs

Extended `ReadingStateInferencer` with a composite RSI score logged through `GazeRSI`: `score`, normalized `reg`, `dwell`, `fix`, and raw `events=fN/dNms/rN`. The score is a reading-effort signal, not a comprehension grade.

Adjusted regression detection to reduce false positives from gaze jitter: a backward move counts as a regression only after gaze remains on the earlier line for 300ms. Brief upward bounces are ignored. Later follow-up changed the anchor from the immediately previous line to the highest line reached, so confirmed one-line rereads are caught while duplicate counts during the same backward episode are suppressed.

Added targeted JVM coverage for sustained regressions, ignored upward bounces, and the composite score. Verified:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
```

Result: BUILD SUCCESSFUL.

Added `docs/reading-stability-index.md` with score ranges, raw metric meanings, interpretation examples, and calibration/noise caveats. `README.md` now links to the RSI guide from the Stage 5 section.

---

## 2026-07-06 - Local 16-point MediaPipe gaze integration, no backend/Wi-Fi

**Context:** `docs/implementation-spec.md` was updated to remove the backend/Wi-Fi gaze path and require Stage 4/5 to depend only on the `GazeProvider` boundary. Stage 3 remains accuracy-limited, but the app now runs the gaze stack locally on the phone.

### What changed
- Removed the live UDP/Wi-Fi gaze path from source:
  - deleted `app/src/main/java/com/newsmead/gaze/GazeStream.kt`
  - deleted `app/src/main/java/com/newsmead/gaze/WiFiGazeProvider.kt`
  - removed the gaze-related `INTERNET` permission from `AndroidManifest.xml`
- Added local raw gaze pipeline:
  - `MediaPipeRawGazeSource` uses CameraX front camera + MediaPipe FaceLandmarker.
  - `LocalRawGazeSource` is the raw feature boundary: emits `gaze_x,gaze_y,timestamp`.
  - `LocalCalibratedGazeProvider` smooths raw features, applies `GazeMapper`, and emits screen px through `GazeProvider`.
  - `LocalGazeSources.create(context)` now returns `MediaPipeRawGazeSource(context)`.
- Added required runtime pieces:
  - `android.permission.CAMERA`
  - CameraX dependencies
  - `com.google.mediapipe:tasks-vision:0.10.14`
  - `app/src/main/assets/face_landmarker.task`
- Updated calibration storage:
  - new file name: `calibration_16point.csv`
  - legacy `calibration_wifi.csv` can still be read with a warning, but recalibration should write the new file.
- Updated `GazeMapper` from the old affine/Wi-Fi mapper to the Stage 3 local mapper:
  - second-degree polynomial features
  - standardized gaze inputs
  - clamping to calibration feature range
  - ridge regularization (`lambda = 1.0`)
- Updated `GazeCalibrationActivity`:
  - collects samples from `LocalRawGazeSource`, not UDP.
  - still uses the 4x4 / 16-dot sequence.
  - restarts the local raw gaze source after camera permission is granted.
- Updated `ArticleFragment`:
  - live mode now builds `LocalCalibratedGazeProvider(GazeMapper(samples), LocalGazeSources.create(...))`.
  - Stage 4 line mapping and Stage 5 RSI still consume only the `GazeProvider.OnGaze` stream.
- Set `StudyConfig.GAZE_TOUCH_VALIDATION = false`, so article reading now uses live local MediaPipe gaze by default.

### Current pipeline

```text
CameraX front camera
-> MediaPipe FaceLandmarker
-> raw iris/eye ratio feature
-> 16-point calibration CSV
-> GazeMapper
-> GazeProvider
-> LineAoiMapper
-> ReadingStateInferencer / RSI logs
```

### Test plan
1. Run app on a real phone.
2. Grant camera permission.
3. Launch calibration:

```powershell
adb shell am start -n com.newsmead/.activities.GazeCalibrationActivity
```

4. Complete all 16 dots.
5. Open an article normally.
6. Watch Logcat tags:

```text
MediaPipeGaze
GazeCalib
GazeAOI
GazeRSI
```

Expected logs:

```text
MediaPipeGaze: Local MediaPipe raw gaze source started
GazeCalib: Wrote 16 calibration pairs
GazeAOI: gaze=(x,y) line=N/total
GazeRSI: score=...
```

### Verification
Verified locally:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
```

Both commands returned `BUILD SUCCESSFUL`.

### Remaining validation
- Needs on-device calibration quality check with the new phone-local MediaPipe source.
- If calibration dots keep retrying, likely causes are camera permission, missing face visibility, poor lighting, unstable phone/head position, or FaceLandmarker not producing iris landmarks consistently.
- Stage 4/5 integration is still isolated behind `GazeProvider`; if the raw feature needs tuning, downstream line mapping and RSI should not need rewrites.

### 2026-07-06 follow-up: MediaPipe calibration read fixed on device

Initial on-device logs showed two issues:

```text
MediaPipeGaze: No face landmarks
GazeCalib: Dot 1: only 0-4 samples, re-collecting
GazeCalib: Loading legacy calibration_wifi.csv
```

Fixes applied:
- `MediaPipeRawGazeSource` now converts CameraX `RGBA_8888` frames with row-stride handling before passing them to FaceLandmarker. The previous direct buffer copy could corrupt frames on devices where `rowStride != width * pixelStride`, causing repeated `No face landmarks`.
- FaceLandmarker confidence thresholds lowered from `0.5` to `0.35` for the phone-local source.
- Live-stream timestamps are forced monotonic before `detectAsync()`.
- Calibration no longer loads legacy `calibration_wifi.csv`; local MediaPipe gaze requires `calibration_16point.csv`.
- Calibration collection window changed to `2500ms`, with `MIN_SAMPLES = 5`, because phone-local FaceLandmarker throughput is lower than the old UDP stream.
- Added periodic `MediaPipeGaze raw=(x,y)` diagnostic logging.

Confirmed on connected phone after installing the fixed debug build:

```text
MediaPipeGaze: Local MediaPipe raw gaze source started
GazeCalib: pair 1/16 ... n=10 used=10
...
GazeCalib: pair 16/16 ...
GazeCalib: Wrote 16 calibration pairs to /data/user/0/com.newsmead/files/calibration_16point.csv
```

Verified again:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
.\gradlew.bat :app:installDebug
```

All returned `BUILD SUCCESSFUL`; the fixed APK was installed on the connected phone.

### 2026-07-06 follow-up: in-article recalibration button

Added an in-app control to rerun calibration without using adb:
- `fragment_article.xml` now has `btnRunGazeCalibration` in the article top bar beside the back button.
- The button launches `GazeCalibrationActivity` directly.
- `ArticleFragment` stops the current live gaze provider before launching calibration.
- When the user returns from calibration, `ArticleFragment` reloads `calibration_16point.csv` and restarts `LocalCalibratedGazeProvider` so the new calibration is used immediately.
- Added string `article_calibrate_gaze` for the button content description.

Verified:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
.\gradlew.bat :app:installDebug
```

All completed successfully.

## 2026-07-20 - Manuscript-aligned adaptive graded visual scaffolding

Implemented the full Section 4.3.5 adaptation layer. The previous word
highlighter ran on every gaze sample and the line renderer was not connected;
focus, re-entry, level selection, and withdrawal did not exist.

### Implementation

- Added visible-text, padding-aware line and locale-aware word targeting.
- Added 120 ms line, 300 ms word, and 200 ms off-text target stabilization.
- Added a 10-second WindowedStabilityEstimator using participant baseline
  mean/std, positive z deviations, manuscript weights, smoothing, and gaze
  confidence. Cumulative GazeRSI remains the session report.
- Added NONE, WORD, LINE, FOCUS, and REENTRY states. Escalation is stepwise
  after 1.5 seconds; withdrawal is stepwise after 3.5 seconds; support clears
  below 65% valid-text gaze confidence.
- Re-entry requires index at least 2.5 plus at least 800 ms off text and a return
  at least two lines from the last stable line. Regression alone is insufficient.
- Added overlay word, line, five-line focus-window, and re-entry line/arrow
  rendering. Transitions fade over 400 ms and never auto-scroll.
- Deprecated WordHighlighter, added OFF/ADAPTIVE/forced validation modes, hid
  gaze debug visuals by default, and added GazeScaffold transition/recovery logs.
- Geometry/baseline starts after final article text loads and resets after
  calibration/test interruption.

### Verification and open research work

- compileDebugKotlin and 18 targeted gaze/scaffold JVM tests pass.
- Physical-device forced-level and adaptive visual validation remains required.
- The per-article 20-second baseline is a fallback; carry the manuscript's
  standardized baseline profile into experimental articles before collection.
- Thresholds, timing, visual parameters, word accuracy, and the operational
  re-entry/recovery definitions require formative/pilot validation.

Canonical guide: docs/adaptive-visual-scaffolding.md.

## 2026-07-20 - Gaze diagnostics enabled

- Enabled `StudyConfig.GAZE_DEBUG_VISUALS` for physical-device debugging.
- The article overlay now shows the live gaze dot and tracker FPS independently
  of the active scaffold level.
- This is a researcher diagnostic, not a participant scaffold. Disable it before
  participant-facing data collection.

## 2026-07-20 - Natural adaptive-trigger validation guidance

- Added a repeatable naturalistic smoke test: clean 20-second baseline followed
  by 10-15 seconds of within-text pauses and rereading.
- Documented that adaptation uses the recent windowed index rather than the
  cumulative GazeRSI score, requires at least 0.65 valid-text confidence, and
  escalates one level per 1.5-second persistence interval.
- Recorded the observed index=2.12/confidence=0.64 run as an expected confidence
  gate, not a scaffold-rendering failure.
- Recommended forced touch/geometry validation before adaptive validation,
  calibration in the reading posture, and disabling debug visuals before
  participant-facing collection.

## 2026-07-20 - Scaffold displacement exposes gaze-validity limitation

- Physical-device observation: adaptive scaffolding triggered, but its apparent
  target moved inconsistently together with the raw gaze indicator.
- Recorded circular measurement contamination as a key limitation: one noisy
  stream can inflate regression/dwell/fixation evidence and misplace the scaffold.
- Clarified that the current confidence value is valid-text coverage, not
  correct-line or spatial-accuracy confidence.
- Added required validation measures across the research docs: median/P95
  vertical error in pixels and line heights, exact-line/within-one-line accuracy,
  word accuracy, dispersion, jump/off-text rates, tracking loss, and drift.
- Added recommendations for synchronized pipeline logs, spatial-quality gating,
  robust filtering, line hysteresis, minimum dwell, outlier rejection,
  head/face and drift checks, recalibration prompts, and independent or
  researcher-coded ground truth.
- Marked word/exact-line intervention claims as conditional. If pilot accuracy
  is inadequate, prefer a broader focus/region scaffold, revise the manuscript,
  or replace the backend rather than hiding tracker error in the renderer.

## 2026-07-20 - Session RSI and adaptive-index terminology decision

- Formalized two separate signals to remove research and implementation
  ambiguity. **Session RSI** is the cumulative 0-100 `GazeRSI score` used for
  session/article/condition reporting. **Adaptive Instability Index** is the
  recent participant-relative 0-4 `GazeScaffold index` used for scaffold control.
- Recorded why Session RSI does not drive adaptation: early difficulty can keep
  a cumulative value elevated after recovery, while a new late-session event can
  be diluted by a long stable history. It is also not baseline-relative.
- Documented the exact adaptive calculation: 10-second counter deltas for
  regression/minute, dwell proportion, and fixation/minute; positive baseline
  z-scores; 0.40/0.35/0.25 weighting; clamp to 0-4; and two-second exponential
  smoothing. Metrics update every 500 ms after at least two seconds of data.
- Retained Session RSI for overall descriptive analysis. A short-window 0-100
  alternative would still need personalization, smoothing, quality gates, and
  recovery logic and would reproduce the current adaptive signal under another
  scale.
- Recorded the current 1.0/1.5/2.0/2.5 adaptive thresholds as provisional
  engineering values requiring pilot tuning against labeled reading episodes
  with tracker-error periods excluded or separately marked.

### Controller alternative considered

- Considered using cumulative Session RSI directly for scaffold levels.
- Rejected it as the current controller because cumulative history can preserve
  early difficulty after recovery, dilute late difficulty after long stable
  reading, and apply non-personalized cutoffs to different reading styles.
- Confirmed that direct use would require new pilot-validated 0-100 thresholds;
  the current adaptive thresholds cannot be converted directly. Any example
  Session RSI bands remain illustrative only and are not study parameters.
- Recorded that Level 4 still requires independent loss-of-position evidence.
- Retained a rolling 0-100 RSI as a possible display-scale alternative, while
  noting that adding personalization, smoothing, quality gates, escalation, and
  recovery makes it functionally equivalent to the Adaptive Instability Index.

## 2026-07-21 - Manuscript RSI/adaptive revision guide

- Added `docs/manuscript-implementation-alignment.md` as the authoritative,
  living insertion guide for updating the actual manuscript as implementation
  decisions continue.
- Mapped required changes to Section 4.3.4, a new Section 4.3.4.1, Section
  4.3.5, methodology/data analysis, and limitations.
- Included the implemented formulas, controller table, persistence and quality
  gates, research-variable separation, synchronized logging requirements,
  circular measurement contamination, the rejected direct Session RSI
  alternative, and a manuscript consistency checklist.
- No PDF or manuscript source was edited; the repository currently contains only
  `Manuscript/Manuscript_Updated.pdf` and no editable DOCX or LaTeX source.
