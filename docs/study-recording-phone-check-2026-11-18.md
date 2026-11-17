# Study recording: quick two-phone checks — 2026-11-18

The owner authorized quick checks on the connected phones before their own live
test. Both received the updated main APK and instrumentation APK using
data-preserving replacement installs. Tests used isolated synthetic cache
storage, not the private participant recording directory. No gaze provider or
camera session was started. One diagnostic run was made on each phone, with no
retries of failed phone tests.

## Results

| Phone | Automated checks | Synthetic ZIPs copied, hash-verified and imported |
| --- | --- | --- |
| Samsung S21 / SM-G991B | 8 passed, 1 failed | 7 ZIPs / 10 package instances |
| Samsung A56 / SM-A566B | 7 passed, 2 failed | 6 ZIPs / 7 package instances |

Both new regressions passed on both phones:

- An intact recording remains transferable alongside an interrupted zero-row
  recording and an older unusable recovery manifest, with warnings retained.
- If only an unrecoverable start exists, transfer reports why and retains the
  original bytes instead of manufacturing an exportable package.

The received synthetic copies passed device/host SHA-256 equality and the strict
study transfer importer. These ZIPs include deliberately incomplete fixtures;
successful import does not relabel them complete or establish human validity.

## Failed checks and interpretation

**S21:** `delayedCaptureKeepsEarlierPresentationAndInvalidMapperIsNotABoundary`
expected 61.875 px from `20 * scaledDensity`, while the stored value was
59.625003814697266 px. The fixture assumed a linear font-scale conversion instead
of using the actual TextView pixel size. The recorder snapshots `body.textSize`.
The fixture now records the TextView's actual size before each presentation
transition and checks both old and new values. Updated Android test code compiles;
this correction was not rerun on the phone during this quick check.

**A56:** the Start/Stop UI fixture and saved-recording-selection UI fixture failed
inside Espresso's input injector with
`NoSuchMethodException: android.hardware.input.InputManager.getInstance`.
This is an automated-input compatibility blocker, not evidence that an app
button or recording failed. Seven non-blocked checks passed. Updating the test
framework is separate from changing app behavior and was not undertaken here.
The visible Start/Stop and selection actions remain part of the owner's manual
rehearsal.

## Preservation and installed build

- Main APK SHA-256 on both phones:
  `82b1b5279f194b58a7c23436bd4d537c85e1d2edcdf96e2daafcd5b156b13cae`.
- Installed test APK SHA-256:
  `8814d56d91f10c3fe0ca5e9d4a53c6cab8fdc2907e22ab3010ef242a3c60bf58`.
  This predates the local font-fixture correction above.
- Each saved `no_backup/mgazenet-v2/calibration.bin` hash matched its pre-check
  value. Calibration presence/preservation does not establish current accuracy
  or suitability for the next participant.
- Camera access was temporarily blocked at UID level. The original `foreground`
  UID mode was restored on both phones, and the camera-access duration was
  unchanged at cleanup. Runtime permissions were not revoked or regranted.
- Actual participant packages were not inspected, deleted, or exported. The
  selected browser test did not run its separate synthetic deletion test.
- The normal vendor, frozen comparison-manifest and APK packaging guards passed.

Evidence is under `output/study-recording-phone-check-2026-11-18/`: preflight
state, per-phone `checks.json` and instrumentation logs, individual synthetic
ZIP/import receipts, `summary.json`, and `font-fixture-compile.log`. Build log:
`output/study-recording-review-2026-11-18/phone-check-build.log`.

## Owner's next test

The phone updates/checks are finished. Start a clearly labelled test run, complete
a fresh reading baseline because the app was restarted, read a short article,
Stop, and transfer the selected recording. Verify the received ZIP before
deleting originals. The actual aid condition still needs to be set separately
from its recording label. Live gaze, comfort and recording endurance remain
unverified by these synthetic checks.

Source and documentation changes remain local and uncommitted.
