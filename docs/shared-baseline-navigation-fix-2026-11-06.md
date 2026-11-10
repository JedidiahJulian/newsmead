# Shared-baseline entry crash and phone verification

Fixed 2026-11-06, following shared-baseline checkpoint `bb290f1`.

## Cause and change

The user's device reported `IllegalArgumentException`: destination
`articleFragment` could not be found from `homeFragment`. The new Home listener
had targeted a fragment in the separate article navigation graph. Building and
the earlier host tests did not exercise this runtime navigation path.

Home now uses its existing `action_homeFragment_to_articleActivityStart` action,
with the shared-reader arguments. `ArticleActivity` forwards the shared-baseline
flag into the article Fragment bundle, so the reader takes its explicit baseline
branch rather than ordinary article initialization. Both baseline and news now
use the real article Activity/layout. No metric, coordinate, timestamp or fitting
rule changed in this fix.

## Actual device checks

Built and installed the main and instrumentation APKs on the connected Samsung
SM-A566B, Android SDK36, preserving app data. The instrumented regression clicks
the real Home button and verifies:

- Resumed `ArticleActivity`, actual article graph/destination and shared flag in
  both Activity and Fragment.
- Exact baseline passage, visible Start control and disabled Read Aloud.
- Activity recreation with one active shared-reader Fragment.
- Start without an active Reading run shows the prerequisite instructions and
  offers no positive capture action; cancelling leaves the run absent.
- Toolbar Back after revealing the collapsed header with a swipe, system Back,
  return to Home and a second baseline entry.

Final result: `OK (1 test)` covering those actions. The first attempt passed entry
and recreation but failed a test-only assumption that the collapsed toolbar Back
button was visible. The corrected test uses the normal reveal gesture; device
animation/settings were not changed.

Saved calibration hash is unchanged. All11existing study-package inventory
entries are unchanged. The test starts no study run or camera; camera-operation
history retained the preceding access and duration. No calibration, account,
database or original study recordings were cleared.

Installed APK SHA-256, verified against the local build:
`2d3ced9972c99e48b4f402e36325be35876f9550a282e645012e68a824c07e2c`.

## Evidence and remaining scope

Local evidence: `output/shared-baseline-crash-2026-11-06/`, including the relevant
crash excerpt, build logs, first/final phone test output, before/after saved-state
checks, installed APK digest and `checks.json`. Private device diagnostics and
generated APKs are not source-control artifacts.

The navigation/guard/recreation checks do not validate real gaze accuracy,
baseline data sufficiency, coordinate fitting or sustained reading performance.
Those require the user's fresh shared-baseline and article rehearsal. The fixed
app is already installed; reopen it from its normal icon. A direct ADB launch of
the non-exported MainActivity was denied as expected; no component access or
security settings were changed to force that handoff.
