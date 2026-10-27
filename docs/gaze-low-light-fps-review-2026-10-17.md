# Low-light tracking FPS: source review — 2026-10-17

The owner reports a significant FPS drop in low light and confirms the affected
quantity is tracking FPS. They requested the current GazeFollower source and
related documentation be read. No bright/dim rates, exposure metadata, or paired
timing logs were supplied. Auto-exposure is a plausible contributor, not a
confirmed diagnosis. No private recordings, camera frames, or device logs were
accessed for this task.

## Upstream findings and transfer

Sources below were read from moving `main` on 2026-10-17; no upstream commit SHA
was established. Current upstream differs from NewsMead's vendored reference.

| Source | What it establishes | Implication for NewsMead |
| --- | --- | --- |
| [README, Note](https://github.com/GanchengZhu/GazeFollower#note) | Authors warn that substantial lighting/head-position changes affect tracking and suggest recalibration. No quantified low-light FPS test or luminance threshold is given. | Lighting is a credible quality concern. Recalibration is not evidence of restored capture throughput. The undefined 7M/32M model labels are not interpreted here. |
| [WebCamCamera.py](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/camera/WebCamCamera.py), constructor/capture/open | Defaults are 640×480 and a 30 FPS OpenCV property request. The wrapper does not set exposure, check the property-write result, or verify delivered FPS. Properties are set before `open`. | This is a requested configuration, not proof of a fixed capture rate or a low-light cure. NewsMead already requests the same resolution. |
| [MORE_INFO.md](https://github.com/GanchengZhu/GazeFollower/blob/main/MORE_INFO.md), introduction/Camera | Warns about PsychoPy preview lag; its custom PupilioCamera example uses separate near-infrared camera hardware. | Desktop preview behavior and NIR hardware do not establish an Android RGB-camera fix. No illumination/exposure recipe was found in this guide. |
| [GazeFollower.py](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/GazeFollower.py) and [mp_gazefollower.py](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/multiprocess/mp_gazefollower.py) | Default construction can choose a child process for tracking. The gaze delivery queue is bounded; the [worker](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/multiprocess/mp_worker.py) removes an old queued result when full. | UI isolation and bounded backlog are relevant principles. NewsMead already localizes on its camera executor, infers on a worker, and uses CameraX latest-frame backpressure. Python GIL avoidance is not an Android optimization to transplant. |
| [MediaPipeFaceAlignment.py](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/face_alignment/MediaPipeFaceAlignment.py) | Current Tasks path uses IMAGE mode with fallback paths. Optional crop/landmark smoothing exists; [DefaultConfig](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/misc/DefaultConfig.py) disables that smoothing by default. | Do not copy current defaults wholesale. NewsMead uses serial VIDEO localization with actual camera timestamps. Google's [Android guide](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android) explains that VIDEO tracking reduces repeated detector work. More localization work during poor tracking is a hypothesis to profile, not an observed cause here. |
| [MGazeNetGazeEstimator.py](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/gaze_estimator/MGazeNetGazeEstimator.py) | Uses a four-thread CPU MNN Module with low precision. Preprocessing resizes face/eyes, normalizes values, and flips the right eye; no brightness enhancement appears in this estimator. | NewsMead's pinned CPU Session uses default precision. Changing precision or adding image enhancement would require separate parity/accuracy checks and is not part of this mitigation. |

The related [2024 smartphone MGazeNet paper](https://onlinelibrary.wiley.com/doi/full/10.1155/2024/2644725),
§§4.5–4.7, reports multi-device pipeline timings and theoretical camera-limited
rates. It explicitly describes an indoor training dataset and leaves broader
lighting robustness open. These results do not demonstrate dim-light operating
FPS on the A56 or NewsMead's exact model/runtime/calibration path. The accessible
[2025 smartphone field-study abstract](https://link.springer.com/article/10.3758/s13428-025-02718-y)
does not resolve controlled illumination performance; full text was restricted.
The [GazeFollower paper DOI](https://doi.org/10.1145/3729410) could not be accessed,
so its full methods/results were not evaluated. No low-light cutoff is adopted.

## Current source and experimental mitigation

`MgazeNetGazeProvider.onFps` counts completed source callbacks, including
noncoordinate results. It measures neither UI rendering FPS nor accuracy.
Before this edit, `MgazeNetCameraSource` specified 640×480 and latest-frame
backpressure but did not explicitly request an AE frame-rate range.
[Android's capture-request contract](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AE_TARGET_FPS_RANGE)
allows AE to adjust capture rate within the configured range; output stream
constraints still limit achievable rates.

The pending three-file change adds a default-false stable-frame-rate preference.
Live article reading opts in and requests [30,30] only if the selected front
camera advertises that exact range. Unsupported or absent capability data leaves
the request unset. A startup `MGazeNetPerf` log identifies the request/fallback;
it does not claim a measured rate. Resolution, model, crop arithmetic,
timestamps, coordinate mapping, smoothing and gaze gates are unchanged.

Separate calibration, accuracy/reading-validation and Pilot callers retain their
defaults. Source review caught the shared article natural-reading recorder.
Before creating that log, the article now waits for complete source close and
starts a provider with the original camera policy; ending recording restores
the live preference. Pending, generation, lifecycle and view guards protect the
handoff. The recording schema and default source metadata are unchanged.
Restart/warm-up behavior still needs runtime verification.

The setting may constrain exposure in dim light, resulting in darker/noisier
input and poorer eligibility or accuracy. It cannot fix insufficient light,
guarantee delivered 30 FPS, or establish calibration transfer. It remains an
experimental mitigation until checked on the actual phone.

## Checks, blocker, and smallest next check

- `git diff --check` passes.
- Focused independent source review found the shared recorder route; follow-up
  review found no remaining actionable issue after the handoff correction.
- One offline Gradle attempt requested MGazeNet JVM regressions and a debug APK.
  It failed **before compilation** because the sandbox denied access to the
  existing Gradle wrapper cache lock. Neither tests nor a build passed. Per the
  bounded-failure agreement, no retry ran. Evidence:
  `output/low-light-fps-check-01/gradle.log` and `gradle-exit-code.txt`.
- No install, camera start, calibration, human collection, staging or commit ran.
  Existing scroll and research edits are preserved.

After host verification is unblocked, an explicitly authorized same-phone
bright/dim check should compare callback FPS, capture intervals/age, camera
exposure/frame duration where available, localization/inference times,
eligibility and busy drops. Analyzer arrivals omit CameraX-undelivered frames;
they cannot alone prove the sensor's capture rate. Existing `MGazeNetPerf` stage
timings help separate processing from capture, but are not exposure telemetry.
Check manual recording start/stop and pause/navigation during handoff. Higher
throughput alone does not establish usable reading gaze or human benefit.
