# MGazeNet calibration-observability v3 results — collection in progress

Updated 2026-09-09. Read after
`gaze-mgazenet-calibration-observability-v3-protocol.md`.

Status: one of four frozen development attempts is complete and preserved.
No calibration-audit scorer has been run and no LOO, drift or held-out accuracy
value has been inspected. The remaining order is A56 round 1, A56 round 2, then
G991B round 2. The active tracker remains unchanged.

## Frozen sequence status

| Sequence | Device | Run label | Status |
| ---: | --- | --- | --- |
| 1 | SM-G991B (`R5CR60YSJ2X`) | `g991b_v3_r1` | Complete and hash-verified |
| 2 | A56 / SM-A566B (`R5CY40Y2C5W`) | `a56_v3_r1` | Not started |
| 3 | A56 / SM-A566B (`R5CY40Y2C5W`) | `a56_v3_r2` | Not started |
| 4 | SM-G991B (`R5CR60YSJ2X`) | `g991b_v3_r2` | Not started |

Do not reorder, replace or add a run after seeing evidence. All four attempts
must be preserved before scoring or comparison.

## G991B round 1

- Run label: `g991b_v3_r1`
- Session ID: `1788944307040_7525fa1f-cca6-4112-afcb-ebdae2788a34`
- Start timestamp encoded by the session ID: 2026-09-09 16:58:27 +08:00
- Outcome exposed by the collector: complete
- Raw report SHA-256:
  `fe2f2edc9750d4761b6027f570004ad17a8fbaaa72fcacffca55a0e9dbad0403`
- Preserved raw directory:
  `diagnostics-local/2026-09-09/mgazenet-calibration-observability-v3/sm-g991b/session-1/`

Before the run, the installed app SHA-256 exactly matched the frozen build:
`2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`.
CAMERA runtime grant was `false`, effective app-op was `ignore`, and the
benchmark process was absent. Opening the benchmark home screen did not change
that state. The participant explicitly started the named calibration audit and
granted CAMERA for the run.

The prospectively declared conditions were no glasses, approximately 30 cm
viewing distance with ordinary small variation, medium screen brightness, a
lighted room, daytime and mild fatigue. After completion, the participant
reported losing attention for approximately one second during one target. The
target is not identified. This note is retained without inspecting the results;
it is not an exclusion, correction or reason to rerun the session.

After completion, the camera app-op showed a run duration of approximately
3 minutes 1 second. CAMERA was explicitly revoked and returned to effective
app-op `ignore`; runtime grant `false` was confirmed. The app and instrumentation
packages were force-stopped and the benchmark process was confirmed absent.

`collect_calibration_audit.py` copied only the named numeric record through the
debug package boundary, verified its device-written SHA-256, schema, protocol,
session identity, privacy flags, correction flags and null decision fields, and
saved it without scoring. Its collector contract tests bring the MGazeNet Python
suite to 53 passing tests. The report hash above was independently recomputed on
the preserved bytes and matched. No raw gaze values were printed or opened.

## Current boundary

G991B round 1 is immutable and will not be repeated. No accuracy statement can
be made while scoring remains deferred. The next frozen attempt is
`a56_v3_r1`, only after the required interval and a separate explicit
authorization. Before that run, confirm the same frozen APK, camera-denied state,
stopped process and current participant/environment conditions. Do not score the
G991B record, contact the A56, grant CAMERA or start its calibration in advance.
