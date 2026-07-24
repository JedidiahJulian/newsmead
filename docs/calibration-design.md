# Gaze Calibration System — Design Architecture (Revised)

**Project:** NewsMead — gaze-driven adaptive reading interface (Android, Kotlin/View system)
**Scope:** 16-point calibration phase — on-screen procedure, sampling/rejection, quality gate, logging.
**Target population:** Older adults, **45–65** (per build-context.md; the draft's "65+" corrected).
**Status:** Design approved for implementation against the existing pipeline. Supersedes the
external draft (`calibration_design_architecture.md`); changes from that draft are listed in §9.

---

## 0. Existing-code contract (do not break)

The design below revises the *procedure*; these integration facts are fixed:

- **Capture:** `GazeCalibrationActivity` + `CalibrationView`; raw features arrive via
  `LocalRawGazeSource.onRawGaze(gazeX, gazeY, timestampMs)` from `MediaPipeRawGazeSource`.
- **Raw feature is NOT pixels.** It is a normalized iris-in-eye ratio (~0–1 per axis:
  corner-relative horizontal, eyelid-relative vertical, both eyes averaged). All pre-fit
  thresholds (dispersion, drift) are therefore in **feature units**, convertible to px only
  after the mapper is fitted.
- **Fit input:** `calibration_16point.csv` (`screen_x,screen_y,gaze_x,gaze_y`), read by
  `CalibrationStore.load()` → `GazeMapper` (2nd-degree poly on standardized features,
  ridge λ=1.0, live inputs clamped to calibrated feature range) → `LocalCalibratedGazeProvider`
  → `ArticleFragment`. **The CSV schema and name stay unchanged and contain only fit points**
  (never the drift-repeat or validation points). The new session log (§6) is additive.
- **Frame-level rejection that already exists:** no-face frames and blink frames
  (eye-aspect-ratio hysteresis, `StudyConfig.GAZE_BLINK_*`). MediaPipe Tasks FaceLandmarker
  exposes **no per-frame landmark confidence** — rejection is: no-face, blink, dispersion (§4).
  Nothing else is available.
- **Throughput reality:** ~20–30 fps from the source *before* blink/no-face drops. All
  sampling windows are **adaptive** (collect until target sample count or timeout), not
  fixed-duration.
- `GazeMapper` requires ≥6 points; `GazeTestActivity` (3×3 benchmark, px/cm conversion via
  DisplayMetrics) remains the independent accuracy test.

---

## 1. Design goals (priority order)

1. Each stored pair reflects a true, settled fixation on the intended point — not a
   saccade-in-progress, overshoot correction, or drift.
2. Usable by 45–65-year-old participants without assistance: slower saccadic latency,
   longer settle, clearer pacing cues than young-adult protocols assume.
3. Robust to small head/device movement inherent to handheld use.
4. **A bad calibration is caught in-session, not mid-article:** post-fit quality gate with
   researcher Accept / Redo decision (§7).
5. Self-contained per-session log auditable at thesis writeup: per-point quality, rejected
   samples, drift delta, fit residuals, validation errors (§6).

---

## 2. Target visual design

### 2.1 Background — match the reading surface
Calibrate against the **article reading background** (light/paper tone), not the current
pure-black screen. Luminance mismatch between calibration and use changes pupil size and is
a reliable source of systematic offset. Pull the actual reading-mode background color
resource so the two screens cannot drift apart. (Color-mode buttons in the article view are
a known open flag — if sepia/dark are kept available to participants, calibrate on the mode
the participant will read in.)

### 2.2 Target structure
Concentric bullseye, sized for reduced acuity/contrast sensitivity at 45–65:
- Outer ring: static circle, **40–48dp diameter** (current `CalibrationView` uses raw-px
  radius 34 — convert to dp).
- Inner dot: **8–10dp** diameter.
- Color: chosen by **contrast ratio against the §2.1 background**, not fixed hue. On a
  paper-tone background: dark/charcoal ring + warm-red inner dot. (The draft's amber was
  chosen for a black background that §2.1 removes.) Verify in pilot.

### 2.3 Per-point state machine

```
APPEAR → HOLD_ATTENTION → SETTLE → SAMPLE → CONFIRM → (advance | re-capture)
```

| State | Duration | Visual | Purpose |
|---|---|---|---|
| APPEAR | 150–250 ms | fade/scale in at new position | cue the saccade |
| HOLD_ATTENTION | 400–600 ms | static, full size | older-adult saccadic latency |
| SETTLE | 300–500 ms, **not sampled** | static, no motion | overshoot/correction saccades land here |
| SAMPLE | adaptive: until **N=10** post-rejection samples or **1500 ms** timeout | static; no animation | data collection |
| CONFIRM | ~100 ms | scale-down flash + soft audio tick | pacing feedback |

- Implemented as an explicit state enum driving `CalibrationView`, replacing the current
  scattered `Handler.postDelayed` timers. Testable; SAMPLE boundaries unambiguous.
- **No motion during SETTLE or SAMPLE** — animation there elicits pursuit/micro-saccades.
- Audio: soft tick at SETTLE→SAMPLE transition (clear temporal "now" marker), soft confirm
  at capture. Non-startling; `ToneGenerator` is sufficient.

### 2.4 Progress + researcher affordances
- Keep the existing researcher **Start gate** (position phone first).
- 16-dot mini-map (done/current/pending) + "n / 16" text.
- Practice point (§3.3) labeled as practice on screen.

---

## 3. Point layout & sequencing

### 3.1 Grid
4×4 at fractions `[0.10, 0.3667, 0.6333, 0.90]` of the inset region — unchanged. The
existing 10% margin already exceeds the draft's ≥5% edge guidance.

### 3.2 Ordering
- **Randomize presentation order per session** (prevents anticipatory saccades). Log the
  permutation and the RNG seed in the session log.
- **Drift check:** one near-center point is presented **twice** — once in the first three
  presentations, once in the last three. The second presentation is logged for the drift
  delta (§5) and **excluded from the CSV/fit**.

### 3.3 Practice point
One throwaway point before the sequence (center; not logged as data, marked `practice` in
the session log) so the participant learns the pacing and audio cues before real capture.

Session length estimate: 1 practice + 16 fit + 1 drift repeat + 5 validation ≈ 23 points
× ~2 s ≈ **45–60 s** plus the quality-gate screen. Acceptable fatigue budget; no pause
mechanism needed beyond the Start gate.

---

## 4. Sampling & rejection logic

Operates purely on the stream of timestamped `(gazeX, gazeY)` samples during SAMPLE — a
pure Kotlin class (`FixationWindowFilter` or similar in `com.newsmead.gaze`), unit-testable
on the JVM and re-runnable against logged raw samples without re-collecting.

Per point:
1. Frames already rejected upstream: no-face, blink (existing hysteresis gate).
2. Discard the first **100–150 ms** of SAMPLE unconditionally (residual settle).
3. **MAD outlier rejection:** drop samples > **2.5 × MAD** from the component-wise median
   (per axis). Removes the stray saccade/twitch without assuming Gaussian noise. MAD is
   wide when the fixation is genuinely noisy, so this trims tails without discarding
   usable data.
4. Aggregate: component-wise **median** of retained samples → the `(feature, screen)` pair.
5. Minimum retained samples: **10** (existing `MIN_SAMPLES`). Fewer → re-capture.

> **Removed after first on-device run (2026-07-24):** an absolute dispersion gate
> (0.02 feature units per axis) plus a most-stable sub-window fallback. The tracker's own
> per-frame noise floor is ~0.04 horizontally and ~0.05–0.10 vertically — *above* the gate —
> so acceptance was effectively impossible: every point timed out, retried, and was excluded,
> aborting the whole calibration ("never good enough"), and the rare sub-window that did pass
> preferred stable-but-off-target moments over the target-centred median (worse accuracy than
> the old plain median). Dispersion is still **computed and logged** for data-quality
> auditing, just not used to reject. A dispersion-based low-confidence flag can return later
> with per-axis thresholds derived from measured pilot noise — not guessed.

### 4.2 Re-capture flow
- Low-confidence or under-sampled point → **immediately re-present once** (same position,
  full state machine).
- Fails twice → mark `excluded` in the session log and **omit from the fit** (do NOT feed a
  known-bad pair to the ridge fit — one bad point warps all six coefficients). The fit
  proceeds if ≥ **12** of 16 points remain; below that, abort with "re-run calibration"
  (12 is a quality floor; the mathematical floor is 6).
- **Caveat logged automatically:** excluding an edge/corner point shrinks the calibrated
  feature range, and `GazeMapper` clamps live input to that range — record which points
  were dropped so shrunken-coverage sessions are identifiable in analysis.

---

## 5. Drift check

- Delta between the two aggregated feature vectors of the repeated center point (§3.2),
  in **feature units**; additionally converted to px through the fitted mapper afterward
  for reporting.
- Threshold: flag `high_drift` if the px-converted delta exceeds **half a line height**
  (line height as defined in §7). Threshold provisional; tune in pilot.
- **Option A (adopted):** flag in the session log and surface on the quality-gate screen
  (§7); the researcher decides. No automatic re-run. (Option B — adaptive re-centering —
  deferred; not worth the complexity before pilot data.)

---

## 6. Session log

Written **incrementally after every point** (crash-safe: an aborted session with a real
participant still yields analyzable data) to app-private storage alongside the CSV:
`calibration_session_<timestamp>.json`.

```json
{
  "session_id": "…", "participant_id": "…",
  "timestamp_start": "ISO8601", "device_model": "…",
  "screen_px": [w, h], "density_dpi": 0, "order_seed": 0,
  "points": [{
    "point_id": 0, "screen_x": 0.0, "screen_y": 0.0,
    "presentation_index": 0, "practice": false, "drift_repeat": false,
    "recaptured": false, "excluded": false,
    "raw_sample_count": 0, "retained_sample_count": 0,
    "blink_dropped": 0, "no_face": 0,
    "aggregated_feature": [0.0, 0.0],
    "dispersion_feature": [0.0, 0.0]
  }],
  "drift_check": {
    "first_pass_feature": [0.0, 0.0], "second_pass_feature": [0.0, 0.0],
    "delta_feature": [0.0, 0.0], "delta_px_postfit": 0.0, "flagged_high_drift": false
  },
  "fit": { "points_used": 16, "loo_median_px": 0.0, "loo_p95_px": 0.0,
           "loo_median_lines": 0.0, "worst_point_ids": [0] },
  "validation": [{ "screen_x": 0.0, "screen_y": 0.0, "error_px": 0.0, "error_lines": 0.0 }],
  "outcome": "accepted | redo_worst | redo_all | aborted",
  "session_quality_flags": ["…"]
}
```

Field-name change from the draft: `dispersion_px` → `dispersion_feature` etc. — px values
exist only post-fit (§0).

---

## 7. Post-fit quality gate (in-session; the loop the draft was missing)

Immediately after the last fit point:

1. **Fit** `GazeMapper` on the retained points.
2. **Leave-one-out residuals** (free, no extra participant time): refit N times leaving one
   point out, predict the held-out point, record px error per point. Report **median and
   P95**, in px **and in line-heights** — the unit that matters for line-AOI mapping.
   Line height = `ARTICLE_FONT_SIZE_DP` (22dp) × 1.6 multiplier (+1sp extra) converted via
   device density — compute from the same constants the article view uses; don't hardcode px.
3. **Validation pass** (~10 s): **5 held-out points** (center + 4 quadrant midpoints, i.e.
   positions NOT in the 4×4 grid), same state machine and rejection logic as calibration.
   Error = fitted-mapper prediction vs. true point. This is the **reportable per-session
   accuracy number** (also convertible to cm via DisplayMetrics, matching `GazeTestActivity`).
4. **Researcher decision screen** (advise-but-allow; never hard-block):
   - Median/P95 error in px and line-heights, validation error, drift flag, excluded points.
   - Color-coded guidance anchored to the tracker's DOCUMENTED accuracy (~3–4 line-heights
     on the A56, from the ~1.68–2.42 cm overall median in progress-notes.md), banded on the
     worse of LOO and validation: **green ≤ 3.0 line-heights, amber ≤ 4.5, red above**. These
     flag a calibration that is unusually bad *for this tracker*, not one that misses an
     unattainable ideal — the earlier 1.0/1.5 bands were below the tracker's own floor and
     read red on every normal run. Provisional; tighten once pilot data exists.
   - **Accept** (writes CSV + finalizes log) / **Redo worst points** (re-run only the
     worst-K LOO offenders, then re-gate) / **Redo all**.
   - Red result still acceptable at researcher discretion — logged as a quality flag.

`GazeTestActivity` (3×3, 20/50/80%) stays as the independent benchmark; the in-flow
validation pass is a quick subset, not a replacement.

### 7.1 Mid-session drift correction (added 2026-07-24)

The accuracy test doubles as a drift-correction instrument. Each run yields 9
(predicted px, true target px) pairs — exactly the data needed to correct posture-shift
drift, which is mostly bias/gain, not a change in the polynomial's shape:

- After a run, an **affine correction** `truth ≈ A·predicted + b` (6 params, least squares)
  is fitted from the pairs and offered to the researcher with the measured pre-error and the
  estimated post-error (in-sample — re-run the test to verify independently).
- On Apply, the correction is stored (`drift_correction.csv`) and applied by
  `LocalCalibratedGazeProvider` **after** `GazeMapper` — the 16-point fit and the
  `GazeProvider` boundary are untouched, and the article screen picks it up on next start.
- The test always measures the live pipeline (correction included), so a subsequent fit
  maps corrected→truth and is stored as the **composition** with the active correction.
- Any accepted full calibration **clears** the correction (`CalibrationStore.save`).
- Deliberately NOT a fresh 9-point polynomial re-fit: 6 coefficients from 9 points is a
  thin, edge-unstable fit that risks replacing a good-but-drifted map with a worse one.
  If the affine correction can't fix it (error isn't affine), the honest remedy is a full
  recalibration.

---

## 8. Implementation notes

- State machine (§2.3): explicit enum owned by the activity (or a small controller class),
  driving `CalibrationView` rendering. No behavioral timers in the view.
- Rejection logic (§4): pure function of `List<TimestampedSample>` → result; JVM unit tests
  for MAD rejection, sub-window search, low-confidence flagging.
- LOO + line-height conversion: pure helpers, unit-tested (`GazeMapper` is already
  JVM-constructible from `CalibrationSample` lists).
- CSV written **only on Accept** — never overwrite a good previous calibration with an
  unreviewed new one. (Current code writes immediately at sequence end; change this.)
- Incremental JSON writes after each point (§6).
- Build/verify: `:app:compileDebugKotlin` + new JVM tests (JDK 17); device validation per
  build-spec on the A56.

---

## 9. Changes from the external draft (audit trail)

**Corrected:**
1. "MediaPipe landmark confidence" rejection — no such per-frame signal exists in the Tasks
   API; replaced with no-face + blink (existing) + dispersion.
2. All pre-fit px units (`dispersion_px`, drift `delta_px`) → feature units; px only post-fit.
3. Twice-failed points are **excluded from the fit** (with coverage caveat), not merely
   flagged — a flagged-but-included bad point warps all coefficients.
4. Amber-on-black target color contradicted the background-matching recommendation; color
   now derived from contrast against the reading background.
5. Target population 65+ → 45–65 (manuscript/build-context).
6. Fixed-duration SAMPLE windows → adaptive (sample-count target with timeout), because
   real post-rejection throughput (~20–30 fps minus drops) can undershoot 10 samples in
   400 ms.

**Added:**
7. §7 post-fit quality gate: immediate fit, LOO residuals, line-height error units,
   Accept / Redo-worst / Redo-all researcher screen; CSV written only on Accept.
8. §0 integration contract: CSV schema/name unchanged, fit points only; session log additive;
   `GazeTestActivity` retained as independent benchmark.
9. Fit floor raised 6 → 12 points for session validity; abort path defined.
10. Concrete starting thresholds (dispersion 0.02 feature units, drift ½ line-height,
    green/amber/red gate bands) — all marked provisional pending pilot.
