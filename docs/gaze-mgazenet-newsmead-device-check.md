# MGazeNet primary integration: camera-disabled device check — 2026-09-10

The user committed the host integration as `5811486` and separately authorized
in-place installation and camera-disabled setup, geometry, native loading and
synthetic persistence checks. The worktree was clean at the start. No CAMERA
grant, camera capture, calibration Start, participant calibration or accuracy
trial is authorized or performed in this phase. Staging and commits remain the
user's responsibility.

## Exact artifacts

The existing host-built artifacts were verified before installation; no source
change or rebuild was needed:

- NewsMead APK SHA-256:
  `131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9`.
- Android-test APK SHA-256:
  `76bcf8759237409549f82257c6f360bd465ca0d7d3d4246a5c03e2d0f0feb7bd`.

Only these reviewed test classes run, nine tests total:

1. `com.newsmead.gaze.mgazenet.MgazeNetIntegrationTest`: idle setup and isolated
   synthetic native SVR persistence, reload, canceled-save rejection and cleanup.
2. `com.newsmead.gaze.mgazenet.NativePipelineTest`: RGBA stride/rotation,
   RGB/NCHW/right-eye flip, recycled-bitmap recovery, MNN 258-value synthetic
   inference/finite guards and native OpenCV SVR fitting.
3. `com.newsmead.gaze.GazeScreenCoordinatesTest`: actual idle calibration/reading
   geometry and an artificial inset/scrolled text/AOI/dot fixture.

The native persistence test uses synthetic inputs and an isolated cache-backed
context; it does not touch the participant calibration namespace. It verifies
reloading into a new store/model instance within the test process. It is not an
Android process-death/restart experiment or an accuracy measurement. Native
camera acquisition and the Face Landmarker camera path are not exercised.

## A56: passed

- Device: `SM-A566B`, serial `R5CY40Y2C5W`, 4096-byte page size, unlocked.
- Prior installed NewsMead APK SHA-256:
  `a0d857fce114d29bc232c24b14886e7db554849277e7dd435ecd59fcdcd5ee07`.
- CAMERA was initially granted. Before installation, NewsMead was force-stopped,
  the runtime grant was revoked, and the effective CAMERA app-op set to `ignore`.
- Both `install -r` updates succeeded. No uninstall or app-data reset occurred.
  Device-side hashes of both installed APKs matched the exact pair above.
- All **9 tests passed in 5.063 seconds**, with no failed or repeated test run.
- All **122 pre-existing protected gaze/calibration/reading file hashes** matched
  after installation and after testing. No new protected file or personal
  MGazeNet calibration was created.
- Both packages were force-stopped afterward; the NewsMead process was absent.
  CAMERA remained `granted=false` and effectively `ignore`.
- The historical CAMERA `allow` entry remained approximately four days old; its
  age increased throughout these checks and no new access was recorded.

Measured layout evidence:

| Layout | Physical geometry |
| --- | --- |
| Calibration view | origin `(0,101)`, size `1080×2239`, display `1080×2340` |
| Calibration centre | `(540,1220.5)` physical pixels |
| Artificial inset window | origin `(91,272)`, size `650×850`; scrolled AOI and dot checks passed |
| Reading root | origin `(0,0)`, size `1080×2340` |
| Reading viewport | origin `(0,259)`, size `1080×1373` |

This confirms use of the real view origin rather than hard-coding or omitting
the 101-pixel offset. No calibration target sequence was started.

Hash-only snapshots, instrumentation output and coordinate-only log output are
retained under ignored
`diagnostics-local/2026-09-10/mgazenet-primary-device-check/a56/`. They must not
be staged. No camera images or participant feature/model contents were copied.

## SM-G991B: passed

The user connected the second phone after committing the A56 report as
`cc3f4db`. The worktree was clean and the local APK hashes were unchanged.

- Device: `SM-G991B`, serial `R5CR60YSJ2X`, 4096-byte page size, unlocked.
- Prior installed NewsMead APK SHA-256:
  `f417ceece6d6654e9b66f0f1e153be7b084baf605c78f241b53414c9856426fb`.
- CAMERA was initially granted. Before installation, NewsMead was force-stopped,
  the runtime grant was revoked, and the effective CAMERA app-op set to `ignore`.
- Both `install -r` updates succeeded without uninstalling or resetting app data.
  Device-side hashes of both installed APKs matched the exact pair above.
- All **9 tests passed in 5.065 seconds**, with no failed or repeated test run.
- All **13 pre-existing protected gaze/calibration/reading file hashes** matched
  after installation and after testing. No new protected file or personal
  MGazeNet calibration was created.
- Both packages were force-stopped afterward; the NewsMead process was absent.
  CAMERA remained `granted=false` and effectively `ignore`.
- The historical CAMERA `allow` entry remained approximately four days old; its
  age increased throughout these checks and no new access was recorded.

Measured layout evidence:

| Layout | Physical geometry |
| --- | --- |
| Calibration view | origin `(0,80)`, size `1080×2320`, display `1080×2400` |
| Calibration centre | `(540,1240)` physical pixels |
| Artificial inset window | origin `(91,251)`, size `650×850`; scrolled AOI and dot checks passed |
| Reading root | origin `(0,0)`, size `1080×2400` |
| Reading viewport | origin `(0,238)`, size `1080×1414` |

The checked view origins differ from the A56 and are measured on this phone.
No calibration target sequence was started. Hash-only snapshots,
instrumentation output and coordinate-only log output are retained under ignored
`diagnostics-local/2026-09-10/mgazenet-primary-device-check/g991b/` and must not
be staged. No camera images or participant feature/model contents were copied.

## Interpretation and next boundary

Both phones passed all nine camera-disabled checks using the same exact APK pair.
This establishes executable synthetic model persistence and the checked layout
behavior on these devices, not calibrated gaze
accuracy, reading-event validity or population reliability. The known APK
16 KB page-size limitations remain; a 4 KB device pass does not resolve them.
CAMERA and participant calibration remain separate later authorization boundaries.
