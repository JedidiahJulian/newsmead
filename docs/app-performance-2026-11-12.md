# Whole-app performance investigation — 2026-11-12

The owner reports about 30 tracking FPS on the A56 and half that or less on the
S21/older phones, and needs numerical study recording. This investigation uses
the actual packaged article screen, current AUTO reading aids, the existing
camera/gaze source, and recording OFF/ON. It is not a model-only benchmark.
The owner authorized data-preserving test installs and short camera tests on
both phones, then explicitly requested the orange cursor during debugging.

## Latest decision — ordinary two-thread default, November 13

The owner explicitly requested promoting CPU2 to the main default and committing
it for groupmates to try on older phones. This supersedes the isolated-only and
A56-preservation decisions recorded chronologically below. The same weights and
precision are retained; ordinary calibration and article reading now use two
threads without diagnostic flags. There are no phone/model allowlists. Accuracy
validation remains deferred; this is a performance candidate for broader testing,
not a proven optimum or a claim of study readiness.

The calibration identity now records CPU2. Existing saved CPU4 calibrations fail
the compatibility check and need a fresh ordinary 13-point calibration; they are
not silently relabeled or overwritten. Calibration targets/fitting, camera
dimensions, clocks, and eligibility rules are unchanged. No accuracy comparison
has been performed for this default change.

## Implemented changes

- `StudyPackageWriter` uses gzip BEST_SPEED with its existing 8192-byte buffer,
  sync-flush behavior, framing, record order, hashes, queue, and loss accounting.
  Decoded JSONL bytes and the `gzip_jsonl_v1` storage encoding are unchanged.
- `GazeOverlayView` avoids gaze/FPS redraw requests while diagnostics are hidden,
  preserving updates for visible diagnostics, dot-only mode, and visible legacy
  scaffolds. Stored point/clear state is retained for later explicit display.
- `StudyConfig.GAZE_DEBUG_VISUALS` follows `BuildConfig.DEBUG`: the orange cursor
  and FPS display are restored in debug builds; release builds hide diagnostics.
- Source metadata reports the estimator instance's actual runtime/thread count.
  The normal estimator now uses two threads, with unchanged model/precision.
- An opt-in whole-app diagnostic retains aggregate rates, capture ages, callback
  costs, UI scheduling delays, frame-duration reports, baseline availability,
  thermal status, and recording integrity. Later summaries also count actual
  decoded input exclusions, decisions, and drawing receipts before deletion.

## Hardware and collection limits

| Phone | Model | Android SDK | Saved calibration |
| --- | --- | ---: | --- |
| S21 | SM-G991B | 35 | Existing compatible calibration reused |
| A56 | SM-A566B | 37 | Initial saved calibration rejected; owner explicitly recalibrated and saved |

No camera images were saved. Numerical recordings used a new isolated cache
directory; every completed run reports deletion of that directory after provider
and writer closure. Existing participant packages were not enumerated or copied.
Calibration fingerprints stayed unchanged within runs after the owner's A56
setup. No synthetic calibration or R/D/F baseline was installed.

Both phones used matching AUTO preferences. R/D/F and shared coordinate references
were unavailable after process restarts, so reference-dependent scoring work is
not represented. These are short debug-build diagnostics with observer overhead,
uncontrolled gaze/interaction, and changing thermal state. They do not establish
release performance, endurance, gaze accuracy, eligibility, or participant benefit.
Android recommends release builds for representative performance measurement:
[measuring app performance](https://developer.android.com/topic/performance/measuring-performance).

`camera_result_hz` counts completed non-watchdog callbacks, including no-face
results. It is neither sensor FPS nor rendering FPS. Coordinate callbacks also
do not establish gaze accuracy. Package decision/render counts include startup,
warmup, timed capture, and cleanup; they are not timed-only rates or user noticing.

## Initial measured results

The initial matched order was OFF/ON/ON/OFF, with 20 timed seconds per S21 block
and 15 timed seconds per repeated A56 block. A56's earlier mixed/no-face run is
retained separately and excluded from this table. Rates aggregate counts over
actual measured elapsed time, rather than averaging nominal FPS labels.

| Phone/build | Recording OFF callbacks/s | Recording ON callbacks/s |
| --- | ---: | ---: |
| S21 before storage/redraw changes | 10.15 | 8.95 |
| S21 after storage/redraw changes | 10.82 | 9.02 |
| A56 before storage/redraw changes | 26.16 | 26.12 |
| A56 after storage/redraw changes | 26.26 | 25.60 |

These runs do not show a resolved FPS gap or an isolated whole-app FPS benefit.
The later S21 OFF block included no-face results, and its initial longer run
reached thermal status 3. Source-stage logs put S21 face localization around
50–80 ms and inference around 85–135 ms, with substantial worker queue delay.
Those stage means include scheduling/competition and are not isolated CPU kernel
costs. A56 live stages were materially faster in these sessions.

The camera-free, warmed ABBA compression comparison uses approximately 2 MB of
deterministic synthetic JSONL in memory, with the real buffer and sync flush.
Every sample decompresses to exactly the original bytes.

| Phone | Default gzip mean thread CPU | BEST_SPEED mean thread CPU | Compressed bytes, default / BEST_SPEED |
| --- | ---: | ---: | ---: |
| S21 | 103.06 ms | 24.37 ms | 613,041 / 691,147 |
| A56 | 112.08 ms | 35.05 ms | 613,041 / 691,147 |

This is about 4.2x/3.2x less compression CPU, with 12.7% larger compressed output
for this fixture. It excludes JSON construction/snapshotting, disk I/O, and the
rest of the app; it is not an expected per-minute participant storage estimate.

## Why automatic aids differed

Both phones had the same AUTO settings. In the later four-thread, visible-cursor
OFF/ON diagnostic (15 timed seconds/block):

| Phone, recording ON | Callbacks/s | Mean capture-to-delivery | Timed results over 250 ms | Whole-package scanpath stale-age exclusions | Whole-package drawn proxies |
| --- | ---: | ---: | ---: | ---: | ---: |
| S21 | 10.27 | 319.43 ms | 124 / 154 | 116 / 192 inputs | 1 |
| A56 | 26.33 | 183.59 ms | 0 / 395 | 0 / 455 inputs | 14 |

The provider permits outputs up to 500 ms old; the motion-aware article adapter
and automatic aid evidence require freshness within 250 ms. Actual
`scroll_input_stale` exclusions substantiate latency as a blocker on the S21.
Other exclusions include geometry changes and points outside visible text, so
latency is not established as the only cause. A drawn proxy is not independently
verified visibility or noticing. No freshness or accuracy threshold was relaxed.

## Rejected thread experiment

Explicit two-thread versus four-thread inference on the three existing synthetic
fixtures produced maximum feature difference 5.2928924560546875e-5 on both phones,
above the existing 1e-5 repeatability criterion. Four-thread repeats were stable.
This does not establish a gaze-accuracy loss, but the candidate was not promoted
or live-tested. The temporary build option was removed; production remains four
threads. The reproducible parity diagnostic requires `newsmeadThreadParity=true`
and is skipped by normal test runs.

## Rejected single-slot candidate

A bounded live-article-only one-slot trial retained four MNN threads, the same
preprocessing arithmetic, capture clocks and gates. Calibration/frozen routes
retained two slots. Busy drops still occurred after serial localization, and a
slot remained worker-owned until its `finally` block. This bounded prepared face
inputs, not no-face work or main-thread delivery callbacks.

| Recording ON | Two-slot callbacks/s / mean age | One-slot callbacks/s / mean age | Within-250-ms results, two / one slots |
| --- | ---: | ---: | ---: |
| S21 | 10.27 / 319.43 ms | 7.86 / 269.20 ms | 30/154 / 31/118 |
| A56 | 26.33 / 183.59 ms | 22.67 / 177.60 ms | 395/395 / 340/340 |

These short exploratory runs are not independently matched accuracy or rendering
comparisons. One slot reduced S21 queue delay but still left most recording-ON
results too old; the approximately 15-second fresh-result count barely changed,
and output throughput fell on both phones. It was not adopted. The final app
retains the original two-slot pipeline for every caller.

Historical candidate receipts use `max_in_flight_frames` for the pool size;
interpret that field only as the number of preprocessing slots. Final source
uses `preprocessor_slot_count` in the harness and live-article metadata, explicitly
excluding no-face work and main-queue delivery. No claim of a total frame bound
or zero queue latency is made.

## Interim conclusion and checks before execution trials

The safe changes reduce compression CPU and unnecessary hidden rendering; they
do not resolve S21 tracking throughput or its automatic-aid latency problem.
The orange cursor is restored for debugging. Neither changing the freshness gate
nor adopting the numerically different two-thread candidate is an accepted fix.
Further estimator/backend changes need explicit exploratory scope and separate
accuracy validation before participant use; the frozen model/runtime is retained.

The pinned debug APK verification passes. Focused study JVM checks have 21 passes
and one Windows filesystem-link skip; 171 MGazeNet JVM tests pass. The existing
Python importer/transfer suites have 49 passes. Each phone passes six native
pipeline checks and the hidden/visible cursor rendering regression; the opt-in
thread-parity diagnostic is skipped by normal runs. Both phones completed
recording OFF/ON full-app tests with verified record counts/hashes, no declared
writer loss, unchanged within-run calibration, and deleted isolated raw packages.
These checks establish software/transport behavior, not human accuracy, benefit,
complete R/D/F scoring, long-session endurance, or release-build readiness.

## Evidence and rerun

Local aggregate evidence, build/test logs, APK hashes/artifacts, and stage logs:
`output/app-performance-2026-11-12/`. Initial recomputed rates are in
`initial-comparison-summary.json`; `cursor4-*-summary.json` contains the aid
diagnostics. Initial failed A56 setup and no-face blocks remain separately named.

The live test requires an existing compatible calibration and explicit
`-e newsmeadLivePerf true`. Normal test execution does not start the camera.
Select each serial explicitly: S21 `R5CR60YSJ2X`, A56 `R5CY40Y2C5W`.
Example, after an authorized main/test APK install:

```powershell
adb -s R5CR60YSJ2X shell am instrument -w -r `
  -e class com.newsmead.research.study.ArticleFullAppPerformanceDeviceTest `
  -e newsmeadLivePerf true -e newsmeadPerfWarmupSeconds 3 `
  -e newsmeadPerfDurationSeconds 15 -e newsmeadPerfOrder OFF,ON `
  com.newsmead.test/androidx.test.runner.AndroidJUnitRunner
```

Read only `cache/newsmead-live-performance-summary.json` via `run-as` to retrieve
the aggregate receipt. Require `successful`, healthy finished writers, complete
hash/count-verified packages, unchanged calibration during the run, and
`isolated_raw_recordings_deleted=true`. A close timeout is a cleanup blocker:
report the owned cache path and resolve closure/deletion before claiming that
only aggregates remain. Check face coverage and baseline flags before interpreting
rates. A run without a face is not representative inference/aid performance.

## CPU follow-up and restart checkpoint

The owner asked which direction to take. Recommendation: preserve the estimator
and investigate the full app/camera CPU path before considering a new estimator.
The final installed APK is
`output/app-performance-2026-11-12/final-app-debug.apk`, SHA-256
`130cb3831e2ba2935a6ae5b938af814e7df5057ac561d871fc0b3014266fe3bd`.
It is installed on both phones with four MNN threads, two preprocessing slots,
BEST_SPEED recording, and visible debug cursor/FPS. The rejected candidates are
not active. The final pinned build verification passes.

Ten-second Perfetto captures include app-only userspace CPU stacks plus system
scheduling/frequency/process timing metadata. They contain no camera images,
heap dumps, logcat, or exported gaze coordinates. Both accompanying whole-app
runs passed and deleted their isolated numerical packages. Evidence lives in
`cpu-profile-s21/` and `cpu-profile-a56/`, with capture receipts, raw timing traces,
verified whole-app summaries, reusable `triage.sql`, and query outputs. The S21
has a bounded `triage-report.md`. Windows named analysis sessions were not usable
across tool calls; both server processes were stopped. Direct batch queries of
these small traces work with the installed skill's pinned processor.

| Scheduler-derived quantity | S21 | A56 |
| --- | ---: | ---: |
| Common analysis window | 9.603272 s | 9.721023 s |
| App CPU seconds | 43.205787 | 40.363525 |
| Average occupied CPU cores | 4.499 | 4.152 |
| Four dominant native worker CPU seconds | 29.736906 | 28.410845 |
| Recording writer CPU seconds | 2.515466 | 1.461389 |

Surviving S21 samples contain 843 `sched_yield` stacks with MNN ancestry; A56 has
318. This supports investigating native coordination/waiting, not assuming all
worker CPU is useful arithmetic. Recording JSON serialization is secondary.
The upstream pinned-version
[MNN thread pool](https://raw.githubusercontent.com/alibaba/MNN/3.6.1/source/backend/cpu/ThreadPool.cpp)
uses yield loops while waiting for work/completion. Its
[CPU runtime](https://raw.githubusercontent.com/alibaba/MNN/3.6.1/source/backend/cpu/CPUBackend.cpp)
also discusses CPU grouping/affinity. These identify relevant code paths, not a
proven fix for this binary or hardware. The pinned JNI exposes session thread
count but no CPU-placement setter among its exported JNI methods.

Trace limitations are material: the S21 has 2,443 skipped perf samples and a
kernel perf lost-record event; A56 has 2,664 skipped samples. About 54% of S21
analysis-window rows lack callsites. Exact flamegraph CPU percentages are not
supported. Reported ftrace overruns/drops are zero, but setup/skipped-event
warnings remain. The profiler itself consumes CPU. Broad whole-app rates and
the later stage-log window must not be merged with the narrower trace window;
the S21 stage logs cover a later worker. The current capture wrapper's DISCARD
buffer/file-flush warnings should be fixed before another capture (ring buffer,
periodic flush, lower CPU sampling rate), retaining these original receipts.

A temporary S21 CPU-placement trial was attempted without changing app code or
model arithmetic. Its initial read-only `run-as ... taskset -ap PID` returned 1
before any mask was applied. The wrapper then timed out waiting for its longer
test; no placement change occurred. Its diagnostics are in
`cpu-affinity-s21-f0/`. Do not rerun blindly: diagnose that read-only blocker and
improve wrapper cleanup/error capture first. A final bounded check found no
`cache/study-device-test-live-perf-*` directories on S21 and no S21 app process.

The owner then reported hitting usage limits and subsequently resumed this work.
Do not repeat calibration, successful full-app comparisons, or rejected
thread/slot experiments. All edits remain unstaged/uncommitted; preserve the
pre-existing `.idea` edit.

## Resumed CPU-placement and recording checks

The shell-domain `run-as taskset` query fails with permission denied even for the
app's own PID. A camera-free instrumentation probe running inside the app's
domain can query its own threads. Its idle inherited mask was `f7`; the foreground
article trial inherited `ff`. Always query the actual mask; neither is a constant.
`AppCpuPlacementDeviceTest` and the optional full-app `newsmeadPerfCpuMasks`
argument keep this experiment in androidTest. No production placement policy was
added. Commands target only the current process, verify the requested subset,
confirm process/reader termination, and verify original-mask restoration before
source closure. Unconfirmed mutator termination blocks further placement commands
and is a cleanup failure. Focused static review passed after timeout handling was
corrected; forced termination was not fault-injected.

The owner confirmed the S21 face was ready. Eight ten-second timed article blocks
used three seconds warmup and one second settling each. Recording-ON blocks used
inherited/f0/f0/inherited placement; recording-OFF blocks used
f0/inherited/inherited/f0. `f0` restricts the **whole app process**, including
rendering/writer threads, to CPUs 4–7; this is not an MNN-only placement test.

| Recording | Placement | Callbacks/s | Coordinate results/s | Results within 250 ms/s |
| --- | --- | ---: | ---: | ---: |
| OFF | inherited | 9.40 | 9.40 | 1.60 |
| OFF | f0 | 11.45 | 11.45 | 0.50 |
| ON | inherited | 10.30 | 10.30 | 1.60 |
| ON | f0 | 11.55 | 11.40 | 1.30 |

Rates use summed counts divided by summed actual timed durations (about 20s per
row). The modest throughput increase did not recover useful freshness. Thermal
status rose from 0 to 3; short blocks and order cannot eliminate thermal or face
variation. No accuracy or causal whole-device conclusion follows. This candidate
was not adopted. All eight blocks restored the original `ff` mask, calibration
was unchanged, and all four recording packages closed with counts/hashes verified,
zero declared writer loss, and isolated raw recordings deleted. Evidence:
`resume-placement-s21-summary.json`, `resume-placement-aggregate.json`, and
`resume-placement-s21-device.log` under the existing output directory.

A separate camera-free same-session test retained four threads and the pinned
model. Before/during `f0` placement, all three existing synthetic fixtures had
exactly identical 258-feature outputs; four-thread repeats were also exact, and
all mask restorations passed. This supports fixture equivalence only, not human
gaze accuracy. Evidence: `resume-cpu-placement-parity-summary.json`.

A recording-only microbenchmark compared the current JSON-string/newline/UTF-8
encoding with a reusable OutputStreamWriter and bounded byte buffer. Seventeen
per-record byte comparisons passed, including Unicode, malformed surrogates,
numeric edge cases and oversized-buffer reset. All warmed ABBA block hashes and
byte counts matched. Mean CPU was 485.71ms versus 481.78ms per approximately
2.1MB block (0.8% lower); process-wide allocation deltas were 46.84MB versus
31.40MB (33% lower). These text-heavy synthetic fixtures exclude producer
snapshotting, gzip, chunk rotation and disk I/O, and do not establish allocation
or FPS savings for actual mostly numerical study rows. The encoder was not
adopted. Evidence: `resume-encoding-performance-summary.json`; both camera-free
checks passed in `resume-parity-encoding-device.log` (2 tests).

The future trace wrapper now uses ring buffering, periodic write/flush, and 25Hz
CPU sampling to address the earlier configuration warnings. No replacement trace
was captured, so the original sample-loss limitations still apply.

## Camera bitmap ownership investigation

Pinned `tasks-core:0.10.29` bytecode confirms BitmapImageBuilder retains the
supplied Bitmap object and BitmapImageContainer.close recycles it when MPImage's
reference count reaches zero. The current analyzer's per-frame MPImage.close
therefore recycles the upright Bitmap; CameraFrames recreates it on the next
frame. App-side review found localization and bitmap preprocessing serial on the
camera executor, with only prepared arrays crossing to the inference worker;
shutdown drains the analyzer before closing the landmarker and CameraFrames.

Caching one MPImage could avoid repeated allocation, but the matched Java
bytecode ends at native packet creation and does not establish native pixel
copy/alias lifetime or historical-image retention after synchronous processing.
The bounded source lookup did not resolve that dependency condition. No bitmap
reuse change was made. Promoting this requires native ownership evidence and a
fixed multi-frame localizer comparison, as well as exact pixels/preprocessing
checks across transparency, stride, rotations and dimension transitions. Pixel
checks alone cannot detect stale native packet caching. Focused bytecode evidence
is retained under `bitmap-ownership/`; no private image or device capture was
used in this investigation.

## Isolated inference timing follow-up

`EstimatorThreadPerformanceDeviceTest` is separately opt-in
(`newsmeadThreadPerf=true`) and camera-free. It retains both four/two-thread model
sessions, preprocesses one synthetic ramp once, warms each model five times, then
runs four/two/two/four twice with twelve calls per block (48 calls per condition).
It times infer plus its returned feature copy; extra output checks/wiping are
outside the timers. Calling-thread CPU excludes native worker CPU. Static metric
review found no actionable issues. Both phones completed; thermal status was
0 before and after these short runs.

| Phone | Four-thread mean wall / call | Two-thread mean wall / call |
| --- | ---: | ---: |
| S21 | 15.88 ms | 27.98 ms |
| A56 | 46.91 ms | 25.34 ms |

These differ markedly from the live article timings. The test has no camera,
writer, article rendering or foreground activity workload; scheduling/frequency
are uncontrolled and two sessions remain loaded. It does not establish live-app
thread-count benefit or explain the device inversion causally. The two-thread
ramp difference was 3.2782554626464844e-5, again failing the unchanged 1e-5
criterion; measurement completion is explicitly separate from parity passing.
Four-thread repeats were stable. This does not reopen the rejected candidate.
The short S21 isolated timing motivates checking contention/placement of only
the existing four inference workers before proposing different model arithmetic.

Evidence: `resume-thread-perf-{s21,a56}-summary.json` and corresponding successful
device logs. An initial S21 invocation installed the older test APK before its
new build finished and failed with ClassNotFoundException; its log is retained
as `resume-thread-perf-s21-stale-test-apk.log`. After the completed build was
installed, the new test passed on each phone. No camera or production APK was
changed by these checks.

## Selective inference-worker placement: both test phones

The next diagnostic targets the executor parent plus three native workers
identified by exact comm grouping and stable TID/comm/kernel-starttime identities.
It changes no other app thread masks. Native-worker attribution remains a
heuristic supported by the earlier trace and configured four-thread pool, not a
new native stack capture. The new `newsmeadPerfMnnCpuMasks` argument is mutually
exclusive with whole-app placement. Original uniform masks, subset checks,
individual readbacks and restoration are verified. Review caught a possible
new-child inheritance leak; the fix also checks surviving new non-target masks
against the baseline and requires restart if cleanup cannot be verified. The
focused rereview found no remaining actionable blocker; failure paths were not
fault-injected.

Each phone completed eight ten-second blocks, using the same ON/OFF and ABBA
placement order as above, with three seconds warmup and one second settling.
The owner confirmed each phone's face setup. The model, four inference threads,
two preprocessing slots, camera settings, calibration, clocks and gates stayed
the same.

| Phone | Recording | Placement | Callbacks/s | Coordinates/s | Camera results within 250ms/s | Weighted mean age |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| S21 | ON | inherited | 10.15 | 9.75 | 2.85 | 323.34ms |
| S21 | ON | four workers, f0 | 14.95 | 14.95 | 13.55 | 218.55ms |
| S21 | OFF | inherited | 9.00 | 8.90 | 1.05 | 349.37ms |
| S21 | OFF | four workers, f0 | 12.65 | 12.65 | 11.10 | 228.63ms |
| A56 | ON | inherited | 25.15 | 25.15 | 25.15 | 187.48ms |
| A56 | ON | four workers, f0 | 25.00 | 24.85 | 25.00 | 191.89ms |
| A56 | OFF | inherited | 27.20 | 27.20 | 27.20 | 180.81ms |
| A56 | OFF | four workers, f0 | 24.64 | 24.44 | 24.64 | 193.02ms |

Rates use summed counts over summed actual durations, about20seconds/row; age
uses the sum of age-count times per-block mean, divided by total age count.
An initial PowerShell aggregation incorrectly used the collection's Count for
the age denominator; it was replaced before reporting the table. The reusable
Python aggregator asserts age-count accounting, weighted-mean bounds, all16
restorations/non-target checks, calibration/cleanup and package health. Timeliness
counts all timestamped camera results, not a joint coordinate/aid-eligibility
measure. One no-face result occurred in the first S21 inherited block; all other
timed blocks had no no-face results. Unsupported baseline-dependent R/D/F work
remains absent, as in previous comparisons.

The S21 improved in this exploratory trial; A56 did not. S21 thermal status rose
0→3, A56 stayed0. This is not evidence that static affinity generalizes to other
phones or long sessions. All16 blocks restored original settings; all8 numerical
packages passed completion/count/hash/loss checks and were deleted from isolated
cache; both calibrations were unchanged. No production scheduling policy was
adopted. Receipts, stage counters and test APK are `resume-mnn-placement-*` in the
existing output directory; rerun analysis with `summarize_mnn_placement.py`.

## General scheduling direction after owner correction

The owner clarified that these are test phones, not the complete device list,
and rejects maintaining phone/model-specific fixes. The proposed S21-only debug
patch was stopped before any production edit, compiler installation or device
deployment. The same gaze model remains the goal across phones.

The next bounded experiment uses Android's platform performance hints rather
than hard-coded CPU assignments. The official
[AOSP guide](https://source.android.com/docs/core/perf/performance-hint-api)
advises against static app CPU affinity because suitable cores and thermal
conditions vary. API31+ Java PerformanceHintManager can receive a related thread
group's target and actual task durations while the OS decides placement/frequency;
session creation can be unsupported/null. See the
[manager](https://developer.android.com/reference/android/os/PerformanceHintManager)
and [session](https://developer.android.com/reference/android/os/PerformanceHintManager.Session)
references. No custom JNI/NDK is needed for the Java hint API. Whether either
Samsung implementation supports and benefits from it must be measured, not
assumed. Older/unsupported devices retain the normal path.

Implemented: general capability-gated, default-disabled
hint session for the same four verified MNN workers, per-infer monotonic elapsed
duration (not camera age, queue time or summed CPU), target33,333,333ns for intended
30Hz cadence, honest support/status counters and model-owned closure. No model,
thread-count, precision, clock, calibration or research threshold change. Test
ON/OFF within real article recording conditions on both phones before promotion.

The main/test builds and171focused MGazeNet JVM tests passed; pinned APK entries
also verified. Focused source review found a counter-label issue: cumulative
enabled-infer counters include settling and work through the queued post-window
snapshot. They are now named `performance_hints_final_cumulative` with explicit
scope, and must not be divided by the FPS-window duration. Rereview passed.
Android's SystemClock reference documents System.nanoTime as suitable for awake
interval timing; research camera/output clocks were not changed.

Both phones passed six existing native pipeline tests plus the opt-in hint probe
(7tests each). Four-worker membership was resolved on both. S21 returned a null
session, with zero API errors/reports; no S21 live hint trial was run. A56 created
a session, successfully reported all7probe tasks (six valid plus a rejected-input
task), preserved the original input error, and closed the session. All three
synthetic fixtures passed the unchanged1e-5 criterion; this is software parity,
not human accuracy. Evidence: `performance-hints-probe-{s21,a56}-summary.json` and
device logs.

Both phones now have `performance-hints-default-disabled-app-debug.apk`, SHA256
`fdd1792a2e8b74658ba96024ec04ff30662e249b7bd481be3781d4486afc19f4`.
Hints remain disabled outside explicit test configuration. The earlier130cb3...
APK is retained as the pre-hint checkpoint, not the current installed build.

The first A56 live hint run closed successfully and deleted its isolated raw
packages, but the first7blocks had99–100%no-face results and almost no inference;
only the last block had a face throughout. It is excluded from normal gaze/FPS
comparison despite transport/closure success. Receipts are retained as
`performance-hints-live-a56-*`. A new diagnostic-only coverage guard requires
no-face ratio<=0.20 and at least one coordinate result per timed block; this
heuristic is benchmark coverage, not a study/accuracy eligibility change. The
owner then confirmed BOTH phones ready again. A56 rerun with the guard is active,
using separately named `performance-hints-live-a56-face-*` receipts.

Because S21 does not accept this hint API, a separate test-only fallback using
one `Process.THREAD_PRIORITY_MORE_FAVORABLE` increment for the same four workers
is being prepared. This uses public OS priority control, leaves CPU core selection
to Android, and has no phone/model allowlist. No priority policy has been adopted;
one paired trial per phone is planned before any integration decision.

## November 13 continuation: priority and fresh-process execution trials

The owner explicitly prioritizes S21 improvements, with A56 as a regression
check. A general public-API priority trial made one MORE_FAVORABLE(-1) increment
on the four original MNN-worker identities. All target changes and restorations
were verified; no cores were assigned. An initial unchanged S21 control failed
an over-strict diagnostic because a Binder thread independently changed nice0
to-20 while zero test setters had run. The verifier now records existing
non-target variation while still rejecting wrong target identities/priorities
and possible inherited candidate priorities on new/replaced threads. Rereview
passed. A later control exceeded the callback-weighted no-face coverage guard
(32/132callbacks); no setting had changed and cleanup completed.

A reviewed preflight now waits up to20seconds after warmup, before intervention,
for1second of continuous face-present labels with500ms capture gaps/recency.
It uses main-thread state and a positioning Toast, cancelled before timing.
These are diagnostic setup heuristics; the timed20%callback no-face guard and
existing study/accuracy gates are unchanged. The resulting S21 eight-block run
completed with all priorities restored, unchanged calibration, healthy packages
and deleted isolated raw data. RecordingON was9.95/s inherited versus10.04/s
priority; delivery331.85versus325.36ms. A56 was26.10versus26.94/s ON and
26.55versus26.50/s OFF. These short results do not establish a useful priority
improvement; no policy was adopted. S21 thermal status0→2. Evidence:
`worker-priority-s21-preflight-*`, `worker-priority-a56-*` and
`scheduler-trial-aggregate.json`.

The owner then explicitly authorized faster execution settings for the SAME
model in a separate test path, retaining the working study pipeline and requiring
accuracy checks before adoption. The experimental CPU2/CPU4 Intent route is
accepted only in DEBUG with the validated isolated live-performance cache active.
Ordinary article/calibration/validation defaults and the CPU4 calibration contract
remain unchanged. Candidate predictions use the saved CPU4 calibration as a
labeled exploratory reference mapping, not validated compatibility. Source review
found no actionable issues;216focused JVM checks passed with one Windows skip,
pinned APK checks passed, and eight camera-free native/auth checks passed on
each phone. The authorization probe rejects experimental counts outside isolation
and after isolation reset.

The pinned MNN thread pool keeps its first process size, keyed by CPU mask.
The earlier isolated test loaded4then2 in one process; its2-thread session still
shared a four-worker pool and cannot estimate clean2-worker resource behavior.
The new experiment instead uses four fresh process identities in4,2,2,4 order,
with one OFF and one ON recording block each (orders balanced). Per-phase actual
requested session size and passively observed first pool size must match. The
host runner checks JUnit success, all package/closure/calibration/coverage flags,
and deletes only its exact aggregate summary between runs. It stops on failure.
Review caught a KeyboardInterrupt camera-stop omission; catching BaseException
now stops the owned camera, rethrows, and never assumes writer closure from
force-stop. A mocked interrupt check passed without contacting devices.

| Phone | Recording | CPU4 callbacks/s | CPU2 callbacks/s | CPU4 / CPU2 mean age |
| --- | --- | ---: | ---: | ---: |
| S21 | ON | 8.40 | 14.50 | 380.85 / 233.80ms |
| S21 | OFF | 9.20 | 15.70 | 346.43 / 225.72ms |
| A56 | ON | 25.14 | 21.80 | 186.03 / 203.37ms |
| A56 | OFF | 26.79 | 21.85 | 179.85 / 205.16ms |

S21 recording-ON camera results within250ms increased0.35→12.85/s. This counts
timely camera observations, not joint coordinate/aid eligibility or gaze accuracy.
All eight process runs completed with unique process identities, matching pool
sizes, unchanged calibrations, healthy count/hash-verified packages and deleted
isolated raw records. S21's thermal progression was1→2,2→2,3→3,3→3 across ABBA;
order balancing cannot remove nonlinear thermal/time effects. One session per
phone, three-second warmup and ten-second blocks do not establish endurance or
population accuracy. The A56 regression prevents a global CPU2 default decision.
CPU2's failed strict1e-5 feature criterion remains recorded; no tolerance was
relaxed and no CPU2 accuracy/adoption claim is made.

Both phones currently have the guarded experimental main APK, SHA256
`82309a3163a83b93ecace4af81058af01d18f32dd20ca340553483684132a55e`,
with ordinary CPU4 still active. All aggregate data and analysis are under
`same-model-{s21,a56}-*`; run `analyze_same_model_execution.py --label s21` or
`--label a56`. Source/metric independent review reproduced S21 totals and flags.
The generic runner now also accepts a candidate3 argument and refuses existing
output names before touching a device, preserving these CPU2 receipts.

One additional shared CPU3 compromise is now being built, under the same explicit
isolation and fresh-process rules. It is not a phone-specific model or policy.
Accuracy and CPU3 numerical parity remain unestablished; the normal CPU4 path
and current calibration remain intact. The existing frozen comparison framework
is not used: its per-device slots and normal-calibration save path do not provide
the required isolation. Any subsequent accuracy work must preserve that boundary.

### Shared CPU3 check and owner's performance priority correction

CPU3 fresh-process ABBA completed on both phones, main SHA256
`884b688cd77ed80335c90649f354f0f01ba2a276c583e5676b9ace55dbe86949`.
All eight process/pool/coverage/calibration/package/closure/deletion checks passed.

| Phone | Recording | CPU4 callbacks/s | CPU3 callbacks/s | CPU4 / CPU3 mean age |
| --- | --- | ---: | ---: | ---: |
| S21 | ON | 8.45 | 11.70 | 377.60 / 272.88ms |
| S21 | OFF | 9.55 | 13.45 | 331.41 / 243.41ms |
| A56 | ON | 24.90 | 26.25 | 188.60 / 188.35ms |
| A56 | OFF | 25.65 | 27.00 | 182.37 / 178.21ms |

S21 recording-ON camera results within250ms were0.40/sCPU4 versus6.55/sCPU3.
S21 thermal progression0→1,1→2,2→3,3→3; A560→0,0→0,0→0,0→1.
These are exploratory short blocks, not endurance or accuracy evidence. CPU3
also passed the existing1e-5 feature criterion on three synthetic fixtures on
both phones, max5.841255e-6/774features; shared CPU4-first pool, not a performance
comparison. Separate cache/output names preserve the earlier failedCPU2 evidence.
Parities used test APK63907ef68a8dba518095a6f38558ddecb0e099bfcc18e8973718a9424c682d7d.
Evidence: `same-model-{s21,a56}-cpu3-*`, `same-model-cpu3-parity-{s21,a56}-*`.

The owner corrected the lead's weighting: S21 is the priority, CPU2's A56
throughput near22/s is an acceptable performance trade-off to explore, and
accuracy work is deferred. CPU2 is a baseline, not the optimum; aim beyond15/s.
The proposed accuracy harness was stopped before any files were written.

A fresh CPU2 S21 baseline then measured60sOFF followed by60sON, with five-second
warmups: OFF17.52callbacks/s (early18.83/late16.20), ON15.21(15.23/15.19),
mean age206.41/229.31ms, zero no-face. Thermal0→2; one verified package, all
closures/calibration/deletion checks passed. Fixed OFF→ON order confounds
recording with elapsed time/thermal state; do not attribute the rate difference
solely to recording. App observation callback means5.09/8.19ms, UI frame
means12.19/15.14ms. Exact-tag own-PID stage counters show early localizer~42ms,
inference~28ms, later~50/~49ms and copy~10ms. These two-second windows mix
startup/warmup/timed/cleanup and are rounded; they cannot establish ON/OFF stage
differences. No coordinates, features or images were exported.
Evidence: `s21-cpu2-stage-baseline-*`; runner source independently reviewed.

CPU1 is the next bounded same-model execution candidate, compared to CPU2 rather
than the slower CPU4 baseline. Source is isolated/debug-only and reviewed;
build/pins/216JVM checks pass(1skip). Ordinary CPU4 remains unchanged. CPU1's
zero-new-worker observation cannot independently exclude an unobserved prior
native pool: fresh app processes with no earlier MNN construction are required.

The CPU1-vsCPU2 S21 ABBA completed with20-second phases and balanced recording
order. CPU1ON14.67callbacks/s, age276.28ms versusCPU2ON15.32/s,228.79ms;
OFF14.62versus16.10/s. Within250msON1.77versus14.00camera results/s. All four
fresh processes passed pool/coverage/calibration/package/closure/deletion checks;
thermal1→2,2→3,3→3,3→3. Own-PID exact-tag stage snapshots show CPU1 inference
around67ms and worker queue30–42ms; reducing to1 is not an improvement. CPU2 is
the best tested thread count for this S21, not a final performance optimum.
Evidence `same-model-s21-cpu1-vs2-*`. Installed S21 main
`5fcbc38043c952736b825c03ae8586beac3c1a655697b3e0e9c7f878d44c2c03`,
test`2cf35b34333ebd360f91661319c2342324209b0f717088367dfc9cffcdbd56dc`;
ordinary default4 unchanged, updated camera-free authorization probe passed.

Next performance candidate is CPU versus GPU execution of the same pinned
FaceLandmarker, constant MNNCPU2. A reviewed DEBUG/isolated-only mode forwards
explicitCPU/GPU requests without changing ordinary GPU→CPU-init-fallback. Its
CPU exception accepts savedGPUacquisition only at identical camera dimensions
and rotation, as an explicitly unvalidated reference mapping; no identity/store/
calibration changes. Added capture-to-analyzer wait counter uses the existing
shared realtime clock, with a separate valid-observation denominator. Build is
in progress. Accuracy work remains deferred at the owner's request.

## Completed CPU/localizer/GPU screen and A56 follow-up — November 13

Changing only the face localizer to CPU did not improve S21 recording-ON
throughput: 15.72callbacks/s for both CPU and GPU localizer arms with MNNCPU2;
mean capture age231.85msCPU versus222.46msGPU. Four fresh processes and package,
calibration, closure and deletion checks passed. Evidence: `s21-localizer-cpu-*`
under `output/app-performance-2026-11-12/`.

The later live GPU screen used six fresh processes A,B,C,C,B,A, balanced ON/OFF
order, and two10-second phases per process. It retained the same gaze weights;
OpenCL Normal uses fp16 storage/fp32 computation and is not CPU float32 parity.
Main OpenCL backend3 was observed, which does not exclude individual CPU fallback
operators. Results count completed camera callbacks, not display or sensor FPS:

| S21 gaze / face execution | Recording ON callbacks/s | ON coordinates/s | ON mean capture age |
| --- | ---: | ---: | ---: |
| CPU2 / GPU | 18.59 | 18.24 | 215.70ms |
| OpenCL / CPU | 11.60 | 11.60 | 301.81ms |
| OpenCL / GPU | 9.90 | 9.60 | 325.74ms |

The CPU2/GPU-face OFF rate was22.25callbacks/s. All six processes passed actual
execution, coverage, package count/hash, calibration fingerprint, provider/writer
closure and exact GPU-model native closure checks; successful isolated raw
packages were deleted. Thermal status progressed0→1,1→2,2→2,2→2,2→3,3→3.
Short blocks and order balance cannot remove nonlinear heat/time effects. GPU
was slower and is not adopted. Evidence:
`output/mnn-gpu-probe-2026-11-13/s21-live-gpu-ready-*`.

Earlier GPU attempts stay separate: one failed face coverage; another timed out
during model startup before GPU timing. The latter could not verify exact native
closure and retained its isolated cache package. It is not throughput evidence.
The corrected diagnostic waits for the first coordinate within15seconds, then
warms up; ordinary provider watchdog/expiry and eligibility thresholds did not
change. Available rounded stage logs were recovered using exact PID plus device
epoch lifetime bounds after an empty-log filter bug. They mix startup/warmup/
timing/cleanup, so they cannot establish recording-condition costs or CPU shares.

The requested quick A56 CPU2-versus-CPU4 follow-up found recording-ON24.25 versus
26.35callbacks/s, with mean age188.47 versus176.59ms. Every ON block had a face
and healthy recording. CPU2OFF23.95/s; only the second CPU4OFF block was valid,
26.80/s. The first CPU4OFF block was interrupted: the observed provider stopped
during an activity pause/restart (early6.2/s, late0). Exclude its3.10/s and the
old generated pooled CPU4OFF14.95/s. The brief pause's cause is unknown; the
configured five-minute screen timeout does not explain a pause after ten seconds.
The owned-PID lifecycle log is `output/mnn-gpu-probe-2026-11-13/a56-current-A1-lifecycle.log`;
numerical summaries are `output/app-performance-2026-11-12/same-model-a56-current-cpu2-*`.
Calibrations remained unchanged and recorded packages closed/verified/deleted.

The full-app diagnostic now checks the held provider and resumed fragment before
and after timing and rejects any timed `stopped` observation. Cleanup suppresses
restarts in the owned activity, captures distinct current/held providers, pauses
the activity, and awaits every captured provider before allowing storage reset or
raw deletion. Unknown continuity retains evidence. Review caught and corrected a
pending-restart cleanup race. This test-harness guard has not been live-retested
or covered by a deterministic asynchronous regression. Main app lifecycle behavior
was not changed.

## CPU2 default checkpoint for group testing

The ordinary debug APK omits optional OpenCL/probe libraries. Explicit GPU and
CPU-placement diagnostics remain available in source; historical four-worker
full-app interventions fail closed under the new two-thread default. The
standalone frozen benchmark and explicit four-thread synthetic reference tests
are unchanged. Unbuilt CPU Power High work was parked in ignored
`output/cpu-power-wip-2026-11-13/` before preparing this checkpoint.

This APK contains application code/assets, not the owner's saved gaze calibration
or session data. New users calibrate on their own phones. Existing CPU4 users
must recalibrate once because the runtime identity now says CPU2. The same
calibration procedure remains; rejecting the old identity is not evidence of
accuracy degradation. Older-phone performance and human accuracy remain open.

The full JVM run reached761 tests with one Windows skip and one unrelated failure:
`FirebaseTest.createAccount` invokes Android Process.myPid without an Android
runtime. That existing cloud-account test was not changed. Focused gaze/study
checks and final APK verification are recorded in the current agent checkpoint.
Local build logs/artifact receipts: `output/cpu2-default-2026-11-13/`.

The normal debug build, test APK, pinned asset/native-library checks and737focused
gaze/study JVM tests passed (736pass,1Windows skip). Eight camera-free native/auth
checks passed on each connected phone after installing the ordinary CPU2 APK;
their calibration file hashes stayed unchanged. These checks cover default CPU
construction,258finite/repeatable features, malformed input rejection and trial
authorization, not human gaze accuracy or the new asynchronous cleanup guard.

Shareable artifact: `output/cpu2-default-2026-11-13/newsmead-cpu2-debug.apk`,
SHA256`255bbcfd5f0f60d66e302b0ad46974599f13baa4dfa2fc6fa856e1ecd6b46728`,
65,014,266bytes, ARM32/64, minimum Android API24. Both phones have that build;
no live gaze test or calibration rewrite was performed for this promotion.
No source changes were made to address the unrelated Firebase test environment.

## November 14 ordinary-default verification and driver-access correction

The owner completed fresh CPU2 gaze calibration on each phone, then authorized
quick full-app recording checks. The first ordinary build completed recording
and cleanup checks, but the owner correctly rejected calling the S21 performance
a pass: its rates were lower than the earlier CPU2 control. These are separate
functional and performance conclusions.

| Phone/build | Recording OFF callbacks/s | Recording ON callbacks/s | ON coordinates/s | ON mean capture age |
| --- | ---: | ---: | ---: | ---: |
| S21 ordinary CPU2, omitted driver declaration | 16.00 | 15.40 | 13.93 | 223.33ms |
| S21 ordinary CPU2, restored driver declaration | 21.67 | 17.73 | 17.60 | 222.96ms |
| A56 ordinary CPU2, omitted driver declaration | 25.27 | 25.47 | 25.07 | 184.54ms |
| A56 ordinary CPU2, restored driver declaration | 24.80 | 25.60 | 25.60 | 186.31ms |

Each row is one fresh process with15sOFF followed by15sON, five-second warmups,
ordinary article execution and no experimental runtime extras. All four runs
passed face coverage, held-provider identity before/after timing, no timed
stopped callbacks, verified numerical package hashes/counts, zero loss, unchanged
calibration within the run, quiesced provider/writer closure, and deletion of
isolated raw diagnostic packages. No camera images were saved. The full test took
roughly136seconds per invocation despite only30seconds of timed capture.

The earlier fast CPU2 control ran inside a probe-enabled debug APK that declared
optional access to the phone's public OpenCL driver. The ordinary CPU2 APK
omitted that declaration. Android requires explicit access for vendor non-NDK
libraries when targeting API31+, and `required=false` permits installation on
devices without the library. This access can affect the existing GPU face tracker
independently of MNN CPU gaze execution. [Android native-library declaration](https://developer.android.com/guide/topics/manifest/uses-native-library-element).

The ordinary S21's own-PID28569 TFLite log reported an OpenCL dlopen failure
(libvndksupport.so unavailable), followed by an ICD loader attempt. That line
does not prove ultimate OpenGL fallback or identify the inference backend.
After restoring optional libOpenCL.so access in the MAIN manifest, own-PID9530
logs report successful OpenCL dlopen. The actual GPU kernel backend is not
instrumented. APK entry comparison against the first ordinary CPU2 build finds
ONLY AndroidManifest.xml changed; app code, model assets and native libraries
are byte-identical. Optional MNN OpenCL/probe libraries remain absent. This is
the same CPU2 gaze path and the same configured GPU face-tracking options.

The declaration is retained as a general packaging correction for the existing
GPU tracker, after focused source/evidence review and tests on both phones.
The S21 rates are closer to the earlier22.25OFF/18.59ON control but do not fully
reproduce its best result. Baseline S21 thermal status was1→1 versus0→1 for the
corrected run; A56 was0→0 in both. Fixed order, time, pose and short capture
confound causal interpretation. Do not attribute the entire increase to the
manifest, infer sustained performance, or treat these as accuracy results.
The successful cleanup path was exercised; no interrupted-restart fault was
deliberately induced.

Both phones now have corrected ordinary CPU2 main APK SHA256
`ba96ac00ce76fed70544d1dbad29e7a28f247e23c51f0575c8557847d5114df4`,
test`1928bafda0bc4667edb164eac760dd851c36cb3a5f0fdc4f89e42db38b65a2f6`.
Evidence: `output/cpu2-default-2026-11-13/{s21,a56}-default-smoke-*` and
`{s21,a56}-driver-access-default-smoke-*`; build/pins, artifact byte-comparison
receipt and own-PID runtime logs in `output/cpu2-opencl-access-2026-11-13/`.
The earlier APK/receipts are retained as baseline evidence. No calibration
procedure, model precision, timing clock or eligibility threshold was changed.
