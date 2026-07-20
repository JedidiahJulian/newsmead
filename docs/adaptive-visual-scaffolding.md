# Adaptive Graded Visual Scaffolding

## Status

Implemented in NewsMead on 2026-07-20. This document is the canonical guide for
the visual-scaffolding layer described in manuscript Section 4.3.5.

The implementation provides four mutually exclusive interventions:

1. Level 1: gaze-contingent word highlighting.
2. Level 2: current-line emphasis.
3. Level 3: a five-line focus window that softly de-emphasizes surrounding text.
4. Level 4: re-entry support using the last stable line and a directional cue.

Stable reading uses an additional internal NONE state. Only one intervention is
drawn at a time. Transitions fade over 400 ms.

## Runtime Pipeline

~~~text
GazeProvider.onGaze(x, y)
-> LineAoiMapper.targetAt(x, y)
-> GazeTargetStabilizer
-> ReadingStateInferencer (cumulative session RSI/events)
-> WindowedStabilityEstimator (recent participant-relative signal)
-> AdaptiveScaffoldController
-> GazeOverlayView
~~~

Important files:

- gaze/TextTarget.kt: line and optional word target.
- gaze/LineAoiMapper.kt: visible-text, padding-aware line and word mapping.
- gaze/GazeTargetStabilizer.kt: 120 ms line, 300 ms word, and 200 ms
  off-text debounce.
- gaze/WindowedStabilityEstimator.kt: rolling metrics, automatic baseline,
  positive z-scores, gaze confidence, and exponential smoothing.
- gaze/AdaptiveScaffoldController.kt: persistence, one-level-at-a-time
  escalation, conservative withdrawal, and re-entry detection.
- gaze/GazeOverlayView.kt: all four visual renderers.
- fragments/article/ArticleFragment.kt: integration and GazeScaffold logs.
- data/StudyConfig.kt: study mode, thresholds, timing, and validation switches.

WordHighlighter.kt is deprecated. It mutated TextView content, discarded spans,
and could cause relayout. Word support is now drawn without modifying content.

## Stability Signal

The existing RSI remains a cumulative session report:

~~~text
RSI = 0.40R + 0.35D + 0.25F
~~~

It is not used directly for adaptation because cumulative values cannot recover
quickly enough to withdraw support. Adaptation derives recent metrics from the
cumulative counter deltas inside a 10-second window:

- regression rate per minute;
- dwell-time proportion;
- fixation rate per minute.

During baseline collection, the estimator learns the participant's mean and
standard deviation for each measure. After baseline, only positive deviations
associated with increased effort are retained and combined with the manuscript
weights. The result is smoothed with a 2-second exponential time constant.

Adaptation is disabled while the baseline is incomplete or while fewer than 65%
of recent gaze samples land on visible article text.

## Current Controller Policy

The thresholds below are initial engineering values expressed in positive
standard-deviation units. They are configurable and are not yet empirically
validated.

| Smoothed index | Desired state |
| --- | --- |
| less than 1.0 | NONE |
| 1.0 to less than 1.5 | WORD |
| 1.5 to less than 2.0 | LINE |
| 2.0 to less than 2.5 | FOCUS |
| at least 2.5 plus loss evidence | REENTRY |
| at least 2.5 without loss evidence | FOCUS |

Escalation requires 1.5 seconds of persistent elevation and moves one level at a
time. Withdrawal requires 3.5 seconds of recovery and also moves one level at a
time. Low confidence clears support immediately instead of treating tracking
noise as reading difficulty.

## Level Details

### Level 1: Word Highlight

The stable gaze x/y target is mapped to a character offset and locale-aware word
boundary. A translucent word rectangle is drawn only after the same horizontal
target persists for 300 ms. Whitespace and gaze outside the rendered line width
do not produce a word target.

### Level 2: Line Emphasis

A low-alpha full-text-width rectangle follows the stable current line. This is
the most reliable scaffold because the tracker and acceptance criteria are
primarily line-level.

### Level 3: Focus Window

Visible article text outside the current line plus two lines above and below is
dimmed. Toolbars and controls are not intentionally included in the mask. The
five-line window is deliberately broader than a single line to avoid creating an
overly narrow moving-window effect.

### Level 4: Re-entry Support

The controller remembers the last line observed during low-instability reading.
Loss evidence is recorded when gaze remains off text for at least 800 ms and
returns at least two lines away from that anchor. If the smoothed index is also
at least 2.5, the anchor line is emphasized. When it is outside the visible
region, an up/down arrow indicates its direction.

The app never auto-scrolls during re-entry support because unexpected movement
could further disorient the reader. A regression alone does not activate Level 4;
it may represent intentional rereading.

## Study and Validation Modes

Set StudyConfig.SCAFFOLD_MODE to one of:

- OFF: static control condition.
- ADAPTIVE: manuscript intervention condition.
- FORCE_WORD
- FORCE_LINE
- FORCE_FOCUS
- FORCE_REENTRY

Forced modes bypass baseline, confidence, and thresholds but still require a
valid text target to render at the correct location. Use them with
GAZE_TOUCH_VALIDATION set to true to validate geometry independently of gaze
accuracy.

GAZE_DEBUG_VISUALS separately controls the red gaze dot and FPS. The current
physical-device diagnostic build enables it; it must be false for
participant-facing sessions.

Recommended validation order:

1. Enable touch validation.
2. Force and visually inspect each level before and after scrolling.
3. Confirm word and line targets around margins, punctuation, and wrapped text.
4. Return to ADAPTIVE.
5. Run calibration and confirm transition logs under GazeScaffold.
6. Verify that deliberately looking away and returning several lines away can
   produce re-entry support only after high instability persists.

### Natural adaptive-trigger test

Use this after forced geometry validation to exercise the real ADAPTIVE policy:

1. Calibrate with the participant/device in the reading posture, then open a
   fresh article with GAZE_TOUCH_VALIDATION false and SCAFFOLD_MODE ADAPTIVE.
2. Read normally for the first 20 seconds while keeping gaze within the article
   body. Deliberate difficulty during this interval contaminates the provisional
   baseline and makes later triggering harder to interpret.
3. After baseline readiness, read one difficult paragraph for 10-15 seconds:
   pause on lines for 1-2 seconds and reread between two or three nearby lines,
   but do not look at the toolbar. This elicits dwell, fixation, and regression
   evidence while preserving valid-text gaze confidence.
4. Confirm GazeScaffold reports baselineReady=true and confidence at least 0.65.
   An elevated index never activates support when confidence is below this gate.
5. Hold the behavior long enough for 1.5-second persistence. Escalation is
   stepwise, so sustained index at least 2.0 takes approximately 4.5 seconds to
   progress from NONE through WORD and LINE to FOCUS.
6. For a naturalistic re-entry check, establish a stable line, look away for at
   least 800 ms, and return at least two lines away while the index is at least
   2.5. Regression by itself must not trigger REENTRY.

Interpret the adaptive index, not the cumulative GazeRSI score, when predicting
the active scaffold. A diagnostic run on 2026-07-20 reported index=2.12,
baselineReady=true, and confidence=0.64. The index was sufficient for the focus
pathway, but the run correctly remained at NONE because confidence was below
0.65. Keep gaze on article text for a complete 10-second rolling window before
classifying that outcome as a controller or renderer failure.

## Logs

GazeScaffold emits:

- a stability summary approximately every two seconds;
- baseline readiness and gaze confidence;
- raw recent regression/dwell/fixation metrics;
- every scaffold level transition and reason;
- current and re-entry target lines;
- recovery latency when the controller returns to NONE.

The logged recovery latency currently measures time from the first scaffold
activation to return to NONE. If the experiment defines recovery from an
externally marked disruption, that marker and its latency must be logged
separately.

## Required Research Decisions Before Main Data Collection

1. **Baseline protocol:** the app currently learns a fresh baseline during the
   first 20 seconds of each article and shows no support during that interval.
   The manuscript specifies a separate standardized baseline-reading phase.
   Before the main study, capture that profile explicitly and pass it into
   WindowedStabilityEstimator across experimental articles.
2. **Threshold validation:** 1.0/1.5/2.0/2.5 z and the timing values are
   provisional. Tune and freeze them using formative/pilot data, then record the
   final values in this document and the manuscript.
3. **Word-level feasibility:** horizontal gaze accuracy remains weaker than
   vertical line accuracy. Measure word-selection accuracy on the target devices
   and older-adult participants. If it is unreliable, revise Level 1 to a
   line-compatible intervention rather than silently claiming word accuracy.
4. **Visual design validation:** highlight colors, focus-mask opacity, five-line
   window size, and 400 ms fade require formative testing with the target age
   group in light, dark, and sepia modes.
5. **Loss-of-position definition:** the 800 ms off-text plus two-line return rule
   is an operational prototype definition. Validate it against researcher-coded
   loss/recovery episodes.

## Verification

~~~powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest --tests com.newsmead.gaze.GazeTargetStabilizerTest --tests com.newsmead.gaze.WindowedStabilityEstimatorTest --tests com.newsmead.gaze.AdaptiveScaffoldControllerTest
~~~

Physical-device validation is still required because native text geometry,
scrolling, camera gaze, and overlay appearance cannot be proven by JVM tests.
