# Known-Target Reading Accuracy Protocol

Current status (2026-09-03): the v4 gain/bias experiment has completed three pairs
without establishing reliable benefit. A fixed offset-only offline replay also
retains major regional failures; see [gaze-offset-replay.md](gaze-offset-replay.md).
Keep the experimental correction OFF for ordinary use. The procedure below
documents the evaluated protocol. The approved E-053 screen-coordinate repair is now
implemented, built, layout-verified and installed; its next evaluation is one fresh telemetry-OFF
16-point calibration followed immediately by this existing test with alignment **OFF**.
Use `coords_v1_tel-off_cal_1` and `coords_v1_off_reading_1`. Do not perform the historical ON
repeat in step 13 for this evaluation, and do not apply a nine-point affine correction.

## Purpose and boundary

This protocol answers one question: when the participant is instructed to look
at a visibly specified word or read a visibly specified physical text line,
does the calibrated gaze pipeline return that word or line?

It does not evaluate adaptive scaffolding, infer unconstrained reading intent,
or assume that an RSI event describes what the participant actually read. Free
reading and adaptive validation remain blocked until known-target accuracy is
acceptable.

The test uses the existing calibrated `GazeProvider`, `LineAoiMapper`, and
`GazeTargetStabilizer` without changing their parameters. Protocol v4 adds one
reversible session-only vertical screen-space layer after `GazeProvider`; it
does not change or persist the raw estimator, quadratic mapper, temporal
filters, or existing nine-point affine correction. The fixed passage uses the
study's locked 22 dp article font.

## Test sequence

### Unscored orange-dot preview

The orange gaze dot is visible before measurement. Look at several words around
the page and confirm that the tracker is producing a live signal. This is only a
setup check: the dot is noisy, creates visual feedback, and is not accuracy
ground truth. Its samples are logged but excluded from all accuracy results.

Tap **Start vertical references** when ready. The orange gaze dot and bottom
control panel then disappear so neither can guide or compete with any target.

### Reading-surface vertical alignment control

Before the preview, select one condition:

- **OFF** records the reference evidence but does not apply it; or
- **ON** applies the fit only if every safety guard passes.

Both conditions show the identical three orange bullseyes at top, middle, and
bottom on the actual reading surface, in the same order and with the same
timing. Each target has 2 seconds of finding time and 2.5 seconds of
measurement. No gaze dot, instruction, timer, or status label is visible while
a reference is active.

The three live-pipeline median coordinates fit only `targetY = intercept +
gain * observedY`; x is untouched. The fit is rejected if sample coverage,
target span, monotonic order, gain, residual, leave-one-out error, or correction
magnitude fails a fixed participant-independent guard. A rejected ON run stops
and saves as `alignment_rejected`, rather than silently running as an
uncorrected ON condition. OFF never applies the fit even when it passes.

### Word-fixation accuracy

Eight actual laid-out words are highlighted yellow across different horizontal
and vertical regions. Timing advances automatically and is deliberately not
shown while a target is active. Every target has:

- 3 seconds of unscored finding time; and
- 2.5 seconds of scored measurement.

Look only at the yellow word and keep looking at it until the next word appears.
No instruction, countdown, progress, or ACQUIRE/MEASURING label is visible
during an active target. The expected physical line and exact character range
come directly from the Android text layout, so the result does not assume which
word the participant intended to view.

Runs alternate automatically between order A and B. Both use the same regions
but begin in different regions, helping separate an always-first-target problem
from a persistent regional problem.

### Guided physical-line reading accuracy

Before this section begins, a centered instruction dialog appears while no
target is active. Six complete physical lines are then highlighted yellow at
top, middle, and bottom viewport positions, including positions requiring
programmatic scrolling. Each line has:

- 2.5 seconds to identify the highlighted line; and
- 5 seconds of measurement.

Read the yellow physical line from left to right once, then keep gaze on that
same line until the next line appears. Timing is automatic and no instructions
or status labels are visible while a line is active. Exact word identity is not
scored during moving line reading because the protocol has no independent
timestamp for each word. Exact-line and within-one-line accuracy are valid
because the instructed line is visibly fixed throughout the trial.

The test ends after guided line reading. It contains no adaptive phase.

## Exact device procedure

1. Use the intended handheld posture, lighting, orientation, and reading
   distance.
2. Run and accept a fresh 16-point calibration. Do not apply the nine-point
   affine correction unless correction is the declared condition being tested.
3. Open an article and tap the gaze-diagnostics dots beside Back.
4. Select **Structured reading validation**, then **Start validation**.
5. For the control run, select **OFF**, enter `vertical_off_1`, and tap
   **Continue**.
6. In the unscored preview, look around the passage and verify that the orange
   gaze dot responds. Tap **Start vertical references**.
7. Read the centered reference instructions, tap **Begin**, and look only at
   the center of each orange target until the next appears.
8. Read the centered word-test instructions, then tap **Begin**. The bottom
   panel disappears before the first target.
9. For each yellow word, look only at that word and continue looking until the
   next yellow word appears. Do not look for a timer or status message; the
   finding and measurement intervals advance invisibly and automatically.
10. Between sections, read the centered line-test instructions and tap **Begin**.
11. For each yellow line, read only that physical line once from left to right,
   then remain on the same line until the next trial.
12. Wait for the accuracy summary and record the JSONL filename.
13. Without recalibrating or changing posture, immediately repeat the complete
    test with **ON** and label it `vertical_on_1`. The second run automatically
    uses the other word/line order. If the guard rejects the ON fit, retain its
    log but do not treat it as a completed accuracy run.

Stopping early writes an `interrupted` session. Timestamped filenames ensure
that retries never overwrite earlier records.

## Persistent data

The coordinate repair uses log schema **5**, while the target/timing protocol remains **4**.
New logs declare `coordinate_space: screen_px_v1`, the accepted calibration CSV's SHA256,
physical display dimensions, and measured root/viewport/text coordinate frames at reference
setup and measurement boundaries. These fields distinguish repaired runs from historical
local/screen-mixed recordings; old recordings are preserved, not silently rescored.

Each run writes:

```text
reading_validation_session_<yyyyMMdd_HHmmss_SSS>.jsonl
```

Records contain synchronized timestamps, alignment mode, all three reference
medians, guard results, whether the layer was applied, base and effective gaze
y coordinates, base/effective raw AOIs, protocol/trial state, target kind,
expected line and character range, stabilized AOI, text position, scroll
offset, and lightweight FPS. The summary reports word-trial exact-line,
within-one-line, and exact-word accuracy plus guided-reading exact-line and
within-one-line accuracy.

No camera frames or face images are stored. Detailed source telemetry remains
off. Tracking gaps can be reconstructed from output timestamps and FPS records.

## Copy logs using Windows CMD

```bat
adb shell run-as com.newsmead ls files
adb exec-out run-as com.newsmead cat files/reading_validation_session_YYYYMMDD_HHMMSS_SSS.jsonl > diagnostics-local\reading_validation_session_YYYYMMDD_HHMMSS_SSS.jsonl
```

Replace the example timestamp with the exact filename shown by the app.

## Interpretation

Report the two trial types separately. Do not use the preview samples in any
metric. For word trials, report exact-line, within-one-line, exact-word,
off-text, acquisition latency, dispersion, jumps, and region/order results. For
guided line trials, report exact-line and within-one-line accuracy while the
participant moves naturally across the highlighted line.

These results validate compliance with known visible targets. They do not prove
what someone reads during an unconstrained article session. That claim would
require an independent reference such as researcher-coded synchronized video or
a validated external eye tracker.

Protocol v2 runs are excluded from accuracy analysis because changing
instructions and timing status were displayed below the passage during active
targets, requiring participants to look away from the target. Protocol v3 moves
all instructions to centered dialogs between sections and hides the control
panel throughout every measured trial. Protocol v4 retains those protections
and adds the identical OFF/ON reading-surface reference control described above.
