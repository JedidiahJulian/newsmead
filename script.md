# NewsMead Gaze Detection and Quality Demo Script

This demo runs the current branch exactly as configured. Do not change
`GAZE_TOUCH_VALIDATION`, `SCAFFOLD_MODE`, `GAZE_DEBUG_VISUALS`, or any other
application setting. Calibration and the gaze-quality test use separate screens
that do not display the article scaffold.

Target duration: approximately two to three minutes.

Record calibration and testing silently, then add the narration afterward.
Speaking, looking away, or moving the phone during sample collection can reduce
accuracy. If possible, include footage from a separate external camera showing
the participant looking at the phone. The phone's front camera is already being
used by the gaze tracker.

## Scene 1: Open NewsMead

**Action:** Start the screen recording, launch NewsMead, and open a study
article.

**Narration:**

> This demonstration presents the current on-device gaze-detection pipeline
> implemented in NewsMead and evaluates the quality of its screen-coordinate
> estimates.
>
> The application uses the phone's front camera and MediaPipe Face Landmarker to
> detect facial, eye, and iris landmarks. NewsMead converts the iris positions
> into eye-relative gaze features and maps those features to screen coordinates
> through participant-specific calibration.

## Scene 2: Open Gaze Calibration

**Action:** Press the gaze-calibration button in the article toolbar.

**Narration:**

> Before gaze can be estimated on the screen, the tracker must be calibrated for
> the current participant, phone position, lighting, and reading posture.
>
> The calibration procedure contains sixteen known screen targets arranged
> across the display.

## Scene 3: Start Calibration

**Action:** Position the phone at the participant's normal reading distance.
Keep the head and phone stable, then press **Start**.

**Narration:**

> The participant positions the phone at a comfortable reading distance and
> presses Start.
>
> For each target, the application first allows the participant's eyes to
> settle. It then collects eye-relative gaze samples while the participant looks
> directly at the target.
>
> Blink frames and frames without a detected face are excluded from the usable
> gaze samples.

## Scene 4: Complete the 16 Calibration Targets

**Action:** Look directly at every target. Do not speak, move the phone, or look
ahead to where the next target might appear. The middle may be accelerated in
editing, but show the first few targets and final target at normal speed.

**Narration:**

> Each target provides a pair consisting of the known screen location and the
> measured gaze features.
>
> After all sixteen targets are collected, NewsMead fits a polynomial mapping
> from the eye-relative measurements to phone-screen coordinates.
>
> Calibration quality depends on stable lighting, a visible face, consistent
> posture, and accurate fixation on every target.

## Scene 5: Calibration Completed

**Action:** Keep the saved-calibration result visible briefly, then return to the
article.

**Narration:**

> The sixteen calibration pairs have now been saved. The same calibration will
> be used by the live gaze provider and the independent quality test.

## Scene 6: Open the Gaze-Quality Test

**Action:** Press the adjacent gaze-test button in the article toolbar.

**Narration:**

> We now open the gaze-quality test.
>
> This test uses nine known targets located at twenty, fifty, and eighty percent
> of the screen width and height. These positions differ from the calibration
> targets, allowing the system to evaluate locations that were not directly used
> to fit the calibration model.

## Scene 7: Show the Live Gaze Indicator

**Action:** Before starting the accuracy test, look at several different parts
of the screen so the live gaze indicator moves.

**Narration:**

> The moving indicator represents the current mapped gaze estimate.
>
> When the participant changes their intended gaze location, the indicator
> should move in the same general direction.
>
> Movement while the participant looks at a stationary location represents gaze
> jitter. A stable indicator that consistently lands away from the intended
> position represents systematic calibration bias.

## Scene 8: Start the Nine-Point Quality Test

**Action:** Press the accuracy-test button.

**Narration:**

> The nine-point accuracy test is now started.
>
> For each point, the participant looks directly at the displayed target while
> keeping the head and phone position stable.
>
> The application waits for the eyes to settle and then collects mapped gaze
> coordinates for approximately one second.

## Scene 9: Complete All Nine Test Points

**Action:** Continue looking at every target until its point result appears.
Complete the test without leaving the screen. Prefer one continuous recording
instead of cutting between test points.

**Narration:**

> After each collection period, NewsMead calculates the median estimated gaze
> position.
>
> The test displays the known target, the median estimated position, and the
> distance between them.
>
> The horizontal difference is represented by the x error, while the vertical
> difference is represented by the y error.
>
> Vertical error is particularly important for NewsMead because the reading
> pipeline eventually maps gaze coordinates to article lines.

## Scene 10: Explain Visible Quality

**Action:** Let at least one point result remain visible before the next target.

**Narration:**

> At this point, we can observe two aspects of quality.
>
> The first is accuracy: how close the median estimate is to the known target.
>
> The second is stability: how much the live estimate moves while the
> participant maintains fixation on that target.
>
> A low median error does not necessarily mean that the estimate is stable. The
> live movement remains important because rapid coordinate changes can cause
> incorrect article-line selection.

## Scene 11: Show the Final Accuracy Result

**Action:** When the test finishes, leave the final result visible for at least
five seconds. Read the displayed values into the placeholders below.

**Narration:**

> The test has now evaluated all nine known target positions.
>
> This run produced a median overall error of **[overall pixels] pixels**,
> equivalent to **[overall centimeters] centimeters**.
>
> The median vertical error was **[vertical pixels] pixels**, equivalent to
> **[vertical centimeters] centimeters**.
>
> The tracker operated at approximately **[FPS] frames per second**.

## Scene 12: Quality Conclusion

Choose the conclusion that matches the recorded behavior. Do not select a more
favorable conclusion than the visible result supports.

### If the Indicator Was Visibly Unstable

> Although the gaze-detection pipeline is operational, the live indicator showed
> substantial movement while the participant viewed stationary targets.
>
> The measured error and visible jitter indicate that the current tracker is
> more appropriate for coarse screen-region estimation than reliable exact-line
> or word-level detection.
>
> This quality limitation must be addressed before gaze estimates are used to
> evaluate adaptive visual scaffolding.

### If the Indicator Was Moderately Stable

> The tracker followed the participant's general gaze direction, but some
> visible jitter and target displacement remained.
>
> The result suggests promising coarse vertical tracking. However, exact-line
> accuracy still requires validation using actual article-line heights,
> repeated test runs, dispersion, and higher-percentile error measurements.

### If the Indicator Was Highly Stable

> The tracker demonstrated consistent movement toward the known targets with
> relatively limited visible jitter.
>
> Nevertheless, this run reports median error only. Repeated runs, exact-line
> accuracy, higher-percentile error, and drift over time must still be evaluated
> before claiming reliable line-level performance.

## Final Statement

**Action:** End on the final quality result or the gaze-test screen. Do not show
or discuss the adaptive scaffold in this demo.

**Narration:**

> In summary, NewsMead successfully performs front-camera gaze detection,
> participant-specific sixteen-point calibration, live screen-coordinate
> estimation, and nine-point known-target quality testing.
>
> This demonstration evaluates the gaze provider independently. Adaptive visual
> scaffolding is not evaluated in this video because its reliability depends on
> the accuracy and stability of the underlying gaze estimate.
>
> The next step is to compare repeated runs and quantify vertical error in
> article-line-height units, exact-line accuracy, dispersion, jump rate,
> tracking loss, and drift over time.

## Demo Result Worksheet

Record these values immediately after the test:

```text
Median overall error: ______ px
Median overall error: ______ cm
Median vertical error: ______ px
Median vertical error: ______ cm
Approximate FPS: ______

Visible jitter: Low / Moderate / High
Systematic bias: None / Left / Right / Up / Down
Tracking loss observed: Yes / No
```

The main demo does not need to show Logcat. The final on-screen accuracy result
is sufficient for this video; logs can be retained as supplemental evidence. If
a final log excerpt is required, use:

```powershell
adb logcat -d -s GazeStage3:I '*:S' | Select-String 'ACCURACY'
```
