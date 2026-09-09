# MGazeNet direct-validation confirmation collection — 2026-09-10

Status: one of the four frozen confirmation attempts is complete and preserved
without scoring. A56 confirmation 1 used the required forward-then-reverse
order. Three attempts remain. No spatial result has been inspected or exposed,
and the active NewsMead tracker under `app/` remains unchanged.

Read after
`gaze-mgazenet-direct-validation-confirmation-device-check.md`. The software and
camera-free checks passed on both phones before participant collection began.
The frozen targets, timing, thresholds, session sequence and delayed-analysis
rule remain unchanged.

## Retained attempt 1 — A56 confirmation 1

After explicit participant/CAMERA authorization, the exact checked app APK
(`95cfc1c457955131e0271ae81472afd66d90a5006556d98fd05d53570410e73d`)
was reinstalled on A56 `R5CY40Y2C5W`. Before setup, CAMERA was denied/ignored,
the app was stopped and `files/confirmation` did not exist. The setup used:

- run label `a56_confirmation_1`;
- device identity `samsung/SM-A566B`;
- validation order `forward_then_reverse`; and
- protocol/schema `mgazenet_direct_validation_confirmation_v1`.

The participant declared the same viewing setup as the preceding sessions:
no glasses, approximately 30 cm viewing distance with ordinary closer/farther
variation, medium screen brightness and the same lighted room. The changed
context was daytime and “more fatigued.” Fatigue is retained as context and is
not an exclusion, correction or reason to rerun.

The app reported completion. CAMERA was immediately re-denied, its effective
UID app-op was returned to `ignore`, both benchmark package processes were
force-stopped and absent, and the single new numeric record was copied through
the hash-verifying collector to ignored local evidence:

| Field | Value |
| --- | --- |
| Session ID | `1788996966439_c741e1b1-4887-4c01-9527-b58228a44b57` |
| Outcome | `complete` |
| Raw report SHA-256 | `a498f3584142ae5f69e6b0f090b543684f353064be8c87de8ef78852a190986a` |
| Local evidence | `diagnostics-local/2026-09-10/mgazenet-direct-validation-confirmation-v1/a56_confirmation_1/1788996966439_c741e1b1-4887-4c01-9527-b58228a44b57/` |

The computed local report hash exactly matches the device-written hash. The
collector verified the complete schema, protocol/session identity, privacy,
no-correction, hidden-screen and null-decision contracts. The record reports
no active-tracker access and no retained camera frames, features or personal
model. The accuracy gate remains null and promotion remains `not_evaluated`.

No confirmation scorer was run. The screen result and all spatial performance
values remain unexamined until all four frozen attempts have terminated and
their hashes are preserved. The ignored `diagnostics-local` evidence must not
be staged.

## Remaining frozen sequence and boundary

| Next order | Attempt | Required sweep order | State |
| ---: | --- | --- | --- |
| 2 | G991B confirmation 1 | reverse, then forward | pending |
| 3 | G991B confirmation 2 | forward, then reverse | pending |
| 4 | A56 confirmation 2 | reverse, then forward | pending |

Permit at least ten minutes away from the target task before G991B confirmation
1. Permit at least fifteen minutes between rounds before G991B confirmation 2;
longer delays or a different day are valid. Each remaining attempt requires the
correct connected phone and a separate participant/CAMERA authorization. Do not
score early, substitute orders, omit a retained failure or automatically rerun
an unfavorable or fatigued attempt.
