# NewsMead Gaze-Driven Reading Prototype

NewsMead is an existing Android news-reading app repurposed as a research prototype for gaze-driven adaptive reading. The current prototype demonstrates an end-to-end loop from eye tracking to article line detection and basic reading-state inference.

This is not a finished study instrument yet. It is a working adviser-demo prototype that proves the core mechanism: the system can estimate which article line a reader is looking at and infer simple reading behavior signals such as fixation, dwell, and regression.

## Prototype Goal

The prototype answers two questions:

1. Can live gaze be mapped to a line of text in the NewsMead article screen?
2. Can that line stream produce useful reading-state signals for later adaptation or study logging?

The current answer is yes, at line level, under a controlled laptop-plus-phone setup.

## Stack Used

Android app:

- Kotlin
- Android Views / XML layouts
- Native `TextView` article rendering
- ViewBinding
- Gradle Android plugin
- Apache Commons Math for affine gaze calibration fitting
- Logcat for prototype validation output

Gaze backend:

- Python 3.12 virtual environment
- GazeFollower `1.0.2`
- MediaPipe `0.10.21`
- OpenCV
- pygame calibration UI
- UDP socket stream from laptop to phone

Target test device:

- Samsung Galaxy A56 5G
- Android 16 / One UI 8.5

## High-Level Architecture

```text
Laptop webcam
    -> GazeFollower gaze estimate in laptop-screen pixels
    -> UDP stream on port 5005
    -> Android GazeStream
    -> phone calibration mapper
    -> phone-screen gaze x/y
    -> article line AOI mapper
    -> RSI inferencer
    -> Logcat prototype output
```

All gaze output enters the Android app through one interface:

```kotlin
GazeProvider.OnGaze { x, y -> ... }
```

This keeps the article screen and RSI code independent from the underlying tracker. The tracker can be replaced later without rewriting the article mapping pipeline.

## Why This Design

### Laptop GazeFollower Instead of Fully On-Device Tracking

Earlier on-device approaches using mobile camera gaze estimation were not accurate enough for reliable line-level reading. The final prototype uses GazeFollower on a co-located laptop because it produced better practical accuracy for a phone placed within the laptop screen area.

This is acceptable for the prototype because the research session already assumes a controlled setup: laptop, phone, participant, and researcher present together.

### Line-Level Instead of Word-Level Tracking

The prototype targets line-level reading state, not exact word fixation. This is a deliberate scope choice. Consumer webcam gaze estimates are not stable enough to support word-level or microsaccade-level claims, but they can support approximate line-level reading behavior when calibrated carefully.

### Native TextView AOI Mapping

The article body is rendered in a native Android `TextView`, not a WebView. That lets the app use Android's text layout data to map a gaze y-coordinate to a text line.

The line mapper uses the `TextView` position on screen plus the text layout's line bounds. Because the `TextView` position changes naturally as the article scrolls, the mapping stays scroll-aware without separate scroll offset tracking.

### Fixed Article Font Size

The study build locks the article body font size. This keeps line heights stable during a session, which is required for reliable area-of-interest mapping. Runtime text-size controls are hidden in study mode.

### Logcat First, Export Later

The current prototype writes validation output to Logcat:

```text
GazeAOI: gaze=(x,y) line=N/total
GazeRSI: fixation line=N/total duration=...
GazeRSI: dwell line=N/total duration=...
GazeRSI: regression A->B
```

This is enough for adviser demonstration and debugging. A later study-ready version should export the same events to CSV or a session file.

## Implementation Summary

### Stage 4: Gaze to Article Line

Implemented components:

- `GazeProvider` - common gaze callback interface
- `WiFiGazeProvider` - receives laptop gaze samples and maps them to phone screen coordinates
- `GazeStream` - UDP listener on the phone
- `GazeMapper` - affine mapping from laptop pixels to phone pixels
- `CalibrationStore` - saves/loads phone calibration samples
- `GazeCalibrationActivity` - 16-point phone calibration screen
- `LineAoiMapper` - maps gaze y-coordinate to article body line index
- `GazeOverlayView` - red debug dot overlay on the article screen

Expected Stage 4 output:

```text
GazeAOI: gaze=(520,1040) line=10/76
```

### Stage 5: Reading-State Inference

Implemented component:

- `ReadingStateInferencer`

It consumes the line index stream and emits:

- line samples
- fixation events after sustained gaze on a line
- dwell events when leaving a line after spending time there
- regression events when gaze moves from a later line to an earlier line

It intentionally does not use saccade velocity or microsaccades because those are not reliable with the current consumer-camera setup.

Expected Stage 5 output:

```text
GazeRSI: fixation line=21/76 duration=406ms
GazeRSI: dwell line=21/76 duration=1018ms
GazeRSI: regression 21->18
```

See `RSI_README.md` for score ranges, raw metric meanings, and interpretation caveats.

## Running the Prototype

Build and install the Android app:

```powershell
.\gradlew.bat assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Start the laptop gaze stream using the project virtual environment:

```powershell
.\.venv\Scripts\python.exe tools\gazefollower_stream.py --phone-ip <PHONE_IP> --port 5005 --print
```

Run laptop GazeFollower calibration. Press `Space` to accept the calibration result.

Launch phone calibration:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.newsmead/.activities.GazeCalibrationActivity
```

On the phone, tap Start and look at each calibration dot until saved.

Open NewsMead, open a study article, then watch logs:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s GazeAOI GazeRSI GazeCalib GazeStream
```

## Expected Demo Behavior

During normal reading:

- The red dot follows the estimated gaze point.
- `GazeAOI` line numbers mostly move forward as the reader moves down the article.
- `GazeRSI` emits fixation and dwell events when the reader pauses on a line.

When deliberately re-reading an earlier line:

- `GazeRSI` emits a regression event, for example:

```text
GazeRSI: regression 21->18
```

## Current Limitations

- The phone must be positioned within or near the laptop screen area.
- Accuracy depends heavily on calibration, lighting, face visibility, and posture stability.
- Current output is in Logcat only.
- The system tracks lines, not exact words.
- Negative or off-screen gaze x-values can occur when calibration is shifted; line detection may still work because it primarily uses vertical position.
- This is validated as a prototype, not yet as a full participant-study instrument.

## Recommended Next Steps

1. Add CSV/session export for RSI events.
2. Reduce Logcat noise by gating raw line-sample logs behind a debug flag.
3. Add participant/session identifiers to exported data.
4. Run pilot tests with target-age participants and record calibration accuracy issues.
5. Use the RSI event stream as input for the later adaptive-reading intervention module.