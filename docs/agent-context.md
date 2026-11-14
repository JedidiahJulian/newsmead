# Agent context — updated 2026-11-14

This is a routing note, not a new research protocol. Recheck source and newer
dated evidence before continuing. It summarizes the project's recent tasks and
local documentation to avoid loading full histories each time.

Use the upstream `scientific-critical-thinking` skill for evidence appraisal and
`statistical-analysis` for statistical planning and interpretation. Their source,
revision and validation limits are in `docs/codex-agent-workflow.md`. The custom
project skills were retired at the user's request. This note and the measurement
protocol remain authoritative for NewsMead-specific decisions and tools.

## Objective and current direction

**2026-11-14 owner-authorized progress bookmark implementation verified/installed:**
Owner explicitly says implement the evidence-informed design and improve lookaway
and backreading detection. Current ruleV6/formV3/runtime policy17 removes all current
time expiry and same/earlier-line residence release. Supported adjacent forward
passage at/past saved row clears; a jump/stationary later row alone cannot. Progress
dims75%→40% with the existing250ms visual transition (no reading deadline). Same cue
id/onset retained; preference revisions immediately apply caps. Exact source must
be fresh at departure start;1500ms eligibility arming latches through brief breaks.
After supported forward advance, repeated exact support at least3rows earlier can
show a backreading cue before the whole broad-reread witness finishes. This retains
the unknown-error neighbor guard, not an accuracy guarantee. Coarse-only mismatches
do not replace the saved reliable location; rejected samples still break histories.
Independent review resolved normal-settling presence bridge, per-cue reset, later
forward-pair release, mapper-rejection and presentation/exposure issues; no remaining
findings.90focused software checks passed;1old timed-dismissal expectation updated
and its rerun passed (91unique checks). Both APKs built. Data-preserving installs
succeeded on both SM-G991B/SM-A566B.2camera-free native checks passed on SM-A566B:
actual rendered article + motion-enabled live adapter exercises lookaway, blink,
backreading, retained/dimmed cue and forward passage; separate pixel render check.
Synthetic screenshot visually inspected. Evidence and final receipt:
`output/bookmark-progress-2026-11-14/`. No live camera recording, focus change,
calibration, frozen extractor/schema edit or commit. This supersedes rejected rules
below; human/live accuracy remains unestablished by synthetic software checks.

**2026-11-14 latest owner correction: research before bookmark redesign:**
Owner rejects clearing after sustained reading anywhere: legitimate rereading must
retain the marker until supported forward reading beyond its line. Then explicitly
rejects5/10/30s hard expiry, proposes gradual dimming, and requests observed rereading
duration papers before selecting parameters. No more policy/device changes this
research turn. Evidence and limits: `docs/bookmark-rereading-duration-evidence-2026-11-14.md`.
Cane2012 pre-interruption-region rereading5.90s(SD2.94)/highlight3.71(SD2.82);
Moreno2021 paragraph lookbacks depend on instructions, one condition4.333s(SD7.041);
Wang2018 sentence rereading young0.934s(SE0.108)/older1.619s(SE0.126).
These are different aggregates, not episode maxima/percentiles. None validates a
10s cutoff or fade rate. EyeBookmark2015 measures resumption lag, not total rereading.
Proposed direction: separate prominence from retained location; dim without deleting
solely for elapsed time, clear on supported forward progression past marker. Fade
curve/minimum opacity remain engineering choices, not validated outcomes.
IMPORTANT installed state: ruleV5 build from`output/bookmark-transient-2026-11-14/`
passed49focused tests and was installed on BOTH phones before owner correction;
it still contains the rejected3s-anywhere/5s-return/30s-global rules.10s proposal was
never coded/installed. New forward-only removal/dimming/no-expiry are NOT implemented.
Live activation failure remains unresolved; no diagnostic recording requested now.

**Historical rejected ruleV5 persistence correction:**
Owner reports no live bookmark while Replay works, offers then explicitly declines
a diagnostic recording, and asks to focus only on distracting bookmark persistence.
Read-only settings check: both phones AUTO/bookmark enabled/adaptive true/max1.0,
auto-scroll off. No existing exported aid trace. Live wiring forwards missing input;
its blink/motion/geometry gates differ from synthetic Replay. Live activation cause
is unestablished; no recording started and no focus-aid work authorized this turn.
Current ruleV5 allows3s coherent reading anywhere to release the bookmark, including
earlier rereading, and imposes5s elapsed from first fresh eligible article return
(immediate on in-article reread activation). Noise/duplicates cannot extend it;
away time before return is excluded,30s overall cap retained.75% opacity unchanged.
Independent review caught/resolved a leftover frontier route hold; boundary test
added, no remaining findings. Focused timing checks/build in progress at
`output/bookmark-transient-2026-11-14/`. This supersedes earlier-line retention below.

**2026-11-14 bookmark appearance increased to75%:**
Owner rejects47% opacity and requests60% or75%; selected75%. Current default strength
is0.75 and current bookmark opacity now maps directly to255-alpha (191/255default),
with manual/cap controls retained. Legacy cue240-alpha mapping stays separate.
No trigger, recovery or escalation change. Demo uses the shared default. Appearance,
current-profile policy/replay and demo expectations updated. Build and51focused
software checks pass;2pixel-opacity/demo checks pass per phone. Updated app is
installed on both phones. Direct A56 Replay shows the stronger marker; automated
S21 Replay selection was blocked while its screen slept. Owner explicitly says
testing is excessive for opacity-only edits; stopped further device checks. Future
minor appearance edits should use a build/one targeted appearance check, scaled to
risk. Evidence at `output/bookmark-opacity-75-2026-11-14/`.
This owner-selected appearance supersedes earlier50%strength/47%opacity references.

**2026-11-14 missing bookmark / reading-speed follow-up verified:**
Owner reports no bookmark in actual Replay and raises short normal line visits.
Reproduced missing bookmark on SM-G991B real AUTO Replay screen:250ms source
script cannot meet1500ms maturity. Prior positive Replay tests selected historical
V4 while Activity usesV5, so earlier supplied-input native tests missed this path.
Current ruleV4 separates fresh eligible mapped article-presence timing across rows
from precise stabilized location support (required for source/release). Arming1500ms;
recovery3000ms same/next-row presence. This is not attentive-reading measurement.
Missing/rejected/stale/gapped/layout boundaries break runs; duplicates earn no time;
aged UI ticks preserve established departure readiness without adding credit.
Precise stale-row reuse is blocked. Current scripts provide2000ms initial/4000ms
return and a new four450ms-row example. Default strength/formV2 unchanged.
Independent read-only review found no actionable issue;336focused JVM tests in35
suites pass. Both debug APKs built;29camera-free native tests pass per SM-G991B
and SM-A566B, including4real AUTO Replay Activity cases with production mapper,
chosen/drawn receipts, currentV5/ruleV4 traces, recovery clears and active full-screen
screenshots. Visually inspected short-visit bookmark on both phones and broad
rereading on SM-G991B. First native run had1screenshot-observation assertion failure:
status label reported focus when both aids active; read-only active-controller
observation fixed the test (production behavior unchanged), rerun29/29perphone.
Both calibrations and13existing study manifests hash-identical; app-ops show no
new camera access. No natural-reading capture/calibration performed. Evidence
`output/bookmark-replay-fix-2026-11-14/`; updated rationale
`docs/bookmark-rereading-and-recovery-2026-11-14.md`. This follow-up supersedes the
initial support-tail arming/recovery statement below.

**2026-11-14 bookmark rereading/recovery revision implemented:**
Owner explicitly approved applying the backward-rereading return-point rule,
confirmed the reported early/quick-clearing behavior was AUTO, clarified strength
meant the bookmark, and authorized testing on both connected phones. Source found
150ms-supported startup anchors without maturity, visibility time consumed while
away, and fixed1.0 strength/240alpha ignoring strength controls. Added1500ms fresh
same/next-row arming,3000ms fresh recovery before dismissal, a complete trusted
broad-rereading witness preserving frontier B, and earlier-reread retention.
Current bookmark formV2/ruleV3 defaults requested0.5/alpha120(about47%) and honors
manual/cap settings; legacyV1 and next-line cue appearance remain historical.
Focused independent review caught/resolved trusted-history loss during normal
row settling; no remaining findings. Offline builds and328JVM/replay tests pass;
25camera-free runtime/render/demo tests pass on each SM-G991B and SM-A566B.
Data-preserving installs succeeded. Both calibration files and13recording
manifests unchanged; camera app-ops show no new access. No live capture/calibration
or natural-reading validity claim. Evidence `output/bookmark-update-2026-11-14/`;
details `docs/bookmark-rereading-and-recovery-2026-11-14.md`. Thresholds remain
pilot settings. Older proposal/audit statements below are historical where
superseded by this explicitly authorized implementation.

**2026-11-14 visual-aid trigger defense audit:**
Owner supplied the current38-slide deck, now `PPT/StabiliRead_THS_ST2_Demo.pptx`,
and asks for system-design/implementation justifications. Source-linked defense
notes are `PPT/Visual_Aid_Trigger_Defense_2026-11-14.md`. Current slides29–32
already distinguish aid evidence and pilot settings. Add GazePrompt2024 as direct
line-assistance/related word-trigger precedent: line-switch time p=.014; maximum
word time/error effects nonsignificant(p=.86/.24). Its adjustable fixation rules
do not validate our1000ms residence rule. Bookmark studies support recovery cues;
rereading studies motivate focus candidate detection without validating dimming,
three losses/30s or50/100% strength. AUTO and ON/preview evidence must stay distinct.
Checked current V5 source and primary papers; no app, PPT, thresholds, device or
participant-protocol changes. Prichard/Atkins evidence was publisher-abstract-only.
Follow-up asks about backward-movement bookmark improvement: the same defense
note records a proposal-only departure-point marker during supported rereading.
GazePrompt handles revisited lines after stable fixation evidence; EyeBookmark
tests imposed interruptions, not this extension. Folk/Eskenazi2018 distinguishes
regression causes; Schotter2014 supports preserving reread access. No automatic
line-jump trigger was re-enabled and no implementation/threshold was approved.

**Latest November 14 verification: functional checks are NOT performance acceptance.**
CPU2-default checkpoint committed as32682a6 on feature/optimization-1 (not pushed
by the agent). Owner requested quick tests on both phones, then correctly rejected
calling S21 a performance pass merely because recording/cleanup passed. Human
fresh CPU2 gaze calibrations were completed/saved on BOTH phones by the owner.
Do not repeat calibration or start the deferred accuracy study.

Ordinary default (no execution extras)15sOFF→15sON completed BOTH phones:
S21 OFF16.00callbacks/s,15.80coordinates/s,age227.17ms;
ON15.40callbacks/s,13.93coordinates/s,age223.33ms. Thermal1→1, noFace0,
ON19invalid_crops/231camera callbacks. This does NOT reproduce prior CPU-control
18.59recording-ON or22.25OFF; no performance success/causal regression claim.
A56 OFF25.27callbacks/s,24.67coordinates/s,age186.05ms;
ON25.47callbacks/s,25.07coordinates/s,age184.54ms, thermal0→0.
Both numerical packages/hash/zero-loss/held-provider identity/resumed checks and
quiesced cleanup passed; calibrations unchanged WITHIN those runs, isolated raw
deleted. This exercises the cleanup happy path, not its interrupted restart case.
Each test took~136–137s overall despite only30s timed capture. Evidence:
`output/cpu2-default-2026-11-13/{s21,a56}-default-smoke-*`.

Investigating one concrete packaging difference: fastest earlier CPU2 control
was in a debug APK with optional vendor libOpenCL.so access declared for GPU
experiments; ordinary APK omitted this. Current S21 own-PID28569 TFLite log says
OpenCL dlopen failed (libvndksupport.so not found), then tries ICD loader. That
does NOT establish ultimate fallback/backend or prove the FPS cause. Android
requires explicit vendor-library access for target31+; GPU face tracking can be
affected independently of the gaze model remaining MNNCPU2. Primary docs:
https://developer.android.com/guide/topics/manifest/uses-native-library-element

Lead added optional required=false libOpenCL.so access to MAIN AndroidManifest
as the packaging correction now prepared for a follow-up commit. Normal debug
build/test/pins pass; focused source/evidence review found no actionable issue;
APK entry comparison finds ONLY AndroidManifest.xml changed, all executable code,
models and native libraries byte-identical; optional MNN GPU libraries absent.
Candidate mainba96ac00ce76fed70544d1dbad29e7a28f247e23c51f0575c8557847d5114df4,
test1928bafda0bc4667edb164eac760dd851c36cb3a5f0fdc4f89e42db38b65a2f6.
Artifacts `output/cpu2-opencl-access-2026-11-13/`. S21 run s21-driver-access
COMPLETED: OFF21.67callbacks/coordinates per second,age196.67ms;
ON17.73callbacks/s,17.60coordinates/s,age222.96ms. Thermal0→1; noFace0;
package/hash/zero-loss/calibration/identity/closure/deletion checks pass.
TFLite now logs OpenCL loaded with dlopen (ownPID9530). This is closer to the
earlier CPU-control range, not proof that the manifest alone caused the FPS gain;
baseline thermal1→1, fixed order and short capture remain confounded. Driver
access success is distinct from performance acceptance. No accuracy claim.
Both phones now have correctedba96ac00/1928bafd. A56 a56-driver-access COMPLETED:
OFF24.80callbacks/s,24.60coordinates/s,age185.67ms;
ON25.60callbacks/coordinates per second,age186.31ms, thermal0→0. Compared with
its earlier25.47ON, no material short-run change. All package/hash/zero-loss/
calibration/identity/closure/deletion checks passed. No cameras remain active.
Gaze remains CPU2; no calibration changes were made by the diagnostics. Keep the
general optional declaration, not phone/model rules. Human accuracy/endurance
and causal size of any FPS benefit remain unestablished. Host runner verifies BOTH
installed app/test hashes, uses ordinary runtime with no execution overrides,
and stops camera on failure. Never overwrite retained outputs. Corrected source
manifest and result docs are being committed as a follow-up to32682a6; exact
commit recorded in output/cpu2-opencl-access-2026-11-13/artifact-receipt.json.
Pre-existing .idea edit remains unrelated. Both runtime logs and compiled APK
comparison receipts remain in that ignored directory. Retain the original
255bbcfd APK/receipt as baseline evidence; future installed-build checks must
use the corrected ba96ac00 artifact receipt explicitly. Do not claim the earlier
best18.59ON was fully reproduced or that a15s check proves sustained performance.

**Latest 2026-11-13 decision: owner requests ordinary CPU2 default and Git commit.**
The owner explicitly supersedes the earlier isolated-only restriction: use the
promising two-thread setting in the MAIN app so groupmates with older phones can
try it. S21 remains the priority, no phone/model allowlist, and human accuracy
work remains deferred. CPU2 is the best tested thread count for this S21, not a
proven optimum or a guarantee for other devices. Do not resume accuracy testing.

Current source: MnnEstimator defaults to CPU2 at the same precision/model. The
calibration identity derives its CPU-thread token from that default, so saved
CPU4 calibrations are rejected and a fresh ordinary 13-point calibration is
required. This is truthful runtime compatibility bookkeeping, not evidence that
old calibration is inaccurate. Do not silently relabel/reuse or overwrite old
calibration. Targets, fitting, camera dimensions, clocks and eligibility gates
are unchanged. Groupmates install the ordinary debug APK, without trial flags,
and calibrate normally. The orange cursor/FPS remain visible in debug builds.

Latest S21 live screen, `output/mnn-gpu-probe-2026-11-13/s21-live-gpu-ready-*`:
CPU2 gaze/GPU face recording-ON18.59callbacks/s,18.24coordinates/s,age215.70ms;
OpenCL gaze/CPU face11.60/s,age301.81ms; OpenCL gaze/GPU face9.90callbacks/s,
age325.74ms. Six fresh processes A,B,C,C,B,A, two10s phases each, all packages,
calibration fingerprints and exact native/provider/writer closure checks passed;
isolated successful raw packages deleted. GPU is slower and is not adopted.
Rounded stage windows were recovered with PID AND device-epoch lifetime bounds;
they mix lifecycle states and are not recording-condition costs or CPU shares.
Short exploratory evidence, changing thermal state, no accuracy/endurance claim.

A56 quick CPU2/CPU4 check `output/app-performance-2026-11-12/same-model-a56-current-cpu2-*`:
recording-ON24.25callbacks/s CPU2 versus26.35CPU4 (age188.47/176.59ms).
CPU4 first OFF block is INVALID: observed provider stopped during an activity
pause/restart, early6.2/s then0; the pooled CPU4OFF14.95 in the old analysis JSON
must not be quoted. Other CPU4OFF26.80/s; CPU2OFF23.95/s. The exact cause of the
brief pause is unknown. New harness continuity guard rejects stopped/replaced
providers before accepting timing and retains uncertain raw diagnostics. This
guard has not yet been live-retested. No keep-screen-on behavior was changed.

CPU Power High work is parked, unbuilt/untested, in ignored
`output/cpu-power-wip-2026-11-13/`. App native source/Java bridge were restored to
the tested GPU-only version before preparing the requested commit. Ordinary
CPU2 APK omits optional OpenCL/bridge libraries; those remain explicitly opt-in
debug diagnostics only. The standalone frozen benchmark is unchanged.

Ordinary build/test APK/pinned assets and libraries checks PASSED, with optional
GPU libraries absent. Focused gaze/study JVM737tests:736pass,1Windows skip.
Broader JVM761tests:759pass,1skip,1existing FirebaseTest.createAccount failure
(Android Process.myPid not mocked); unrelated cloud-account test not changed.
Focused review found no default/identity issue and cleared the corrected cleanup
race. Review/compilation do not verify the asynchronous lifecycle interleaving.

Shareable APK `output/cpu2-default-2026-11-13/newsmead-cpu2-debug.apk` (65,014,266
bytes, arm64-v8a + armeabi-v7a, minimum API24), SHA256
255bbcfd5f0f60d66e302b0ad46974599f13baa4dfa2fc6fa856e1ecd6b46728;
test87d2c29186dcf4c06ab3c98ba4849150e41b9249e4230a4c4c70add13a69e78b.
Both phones now have this CPU2-default build; eight camera-free native/auth tests
PASSED EACH. Their saved calibration bytes stayed unchanged; existing CPU4
identities will require fresh gaze calibration through the ordinary UI. No live
camera run was started for promotion, so do not claim the new ordinary build has
already been live-measured. Logs/receipts are in the same ignored output directory.

User authorizes staging/committing performance task files only; this checkpoint
is prepared on feature/optimization-1, exact commit recorded in the ignored
artifact receipt after committing. Preserve the pre-existing
`.idea/deploymentTargetSelector.xml` edit and all private outputs. No push requested.

**Historical 2026-11-13 checkpoint (superseded by the decision above):**
**Latest owner correction:** S21 performance takes priority; preserving A56's
maximum rate is not a requirement. CPU2 is a useful baseline, not the final
choice or a proven optimum (~15/s is still too slow). Owner explicitly defers
accuracy work until later. Accuracy implementation was interrupted before any
files were written; do not resume it or request target-following checks now.
Next: identify remaining S21 costs and test faster execution configurations.
CPU1 isolated extension is reviewed and built/pins/216JVM pass(1skip), installed
S21 only; its updated camera-free authorization probe passes. Main SHA256
5fcbc38043c952736b825c03ae8586beac3c1a655697b3e0e9c7f878d44c2c03;
test2cf35b34333ebd360f91661319c2342324209b0f717088367dfc9cffcdbd56dc.
A56 stays884b688c main/63907ef6 test below. Hypothesis: removing MNN worker
synchronisation; zero observed
new workers alone cannot prove absence of an old native pool, so host must force
a fresh process with no earlier MNN construction. No ordinary default change.

S21 CPU2 60sOFF then60sON baseline COMPLETED: OFF17.52callbacks/s, ON15.21/s;
age206.41/229.31ms, zero no-face, ONearly15.23/late15.19/s. Calibration unchanged,
writers/providers closed, package verified/deleted. Thermal0→2. Own-PID exact
MGazeNetPerf counters retained, mixed lifecycle windows (not ON/OFF comparison):
early localizer~42ms/inference~28ms; later~50/~49ms, copy~10ms. Original app
observation callback means5.09msOFF/8.19ON; UI frame12.19/15.14ms. Fixed order
confounds recording/time/thermals; this is not proof recording causes the change.
Files `s21-cpu2-stage-baseline-*`; host script reviewed. Fresh CPU1-vsCPU2
ABBA COMPLETED S21, label s21-cpu1-vs2, reference2,candidate1,20second phases;
host delta reviewed. CPU1 recordingON14.67callbacks/s/age276.28ms versus
CPU2ON15.32/s/228.79ms; OFF14.62versus16.10. Within250msON1.77versus14.00/s.
Allfourprocesses/pools/calibration/packages/closure/cleanup passed, thermal1→2,
2→3,3→3,3→3. CPU1 native inference~67ms and queued~30–42ms in aggregate stages;
not a gain, do not adopt. Two remains best of1/2/3/4 tested for S21, not finaloptimum.

Next bounded PERFORMANCE experiment being implemented: same FaceLandmarker
weights/options, explicit GPU versus CPU delegate execution, MNNconstantCPU2,
freshprocessABBA on S21. Guarded DEBUG+ownedisolated storage, no default changes.
CPU delegate may use savedGPUcalibration only as clearly labeled unvalidated
reference mapping with EXACT dimensions/rotation; no calibration write/identity
change or accuracy claim. Source reviewed with no correctness findings;
main/test/pins/216JVM build passed(1skip), cleanup rebuild passed. Redundant
per-frame filesystem authorization removed (constructor/start retained), Regex
cached before timing. Installed S21 main
cf8a659388533a21dfb3b70591c1ed363ec7d60900d67890b1883ab5899d9688;
test419b77126cb711e9b563b8c27cedf7fca05947d2425db9a39b9f94c71c59bdfa.
Both camera-free authorization/geometry probes pass. Fresh face confirmed;
GPU/CPU/CPU/GPU trial COMPLETED, label s21-localizer-cpu, CPU2constant,20s phases.
RecordingON15.72callbacks/s BOTH; CPUage231.85ms vsGPU222.46ms, no improvement.
Allfourprocesses/actualdelegates/pools/geometry/calibration/packages/closure/
deletion checks passed. Thermal0→1,1→2,2→3,3→3. Rounded mixed-lifecycle stages:
CPUlocalizer44.19/inference49.09/capturewait112.85ms versusGPU46.09/42.98/111.10.
Don't infer ON/OFFstage differences; no accuracy/endurance evidence. Do not
spend A56 tests on this non-winning candidate. Lead next investigating same-model
MNNGPUexecution feasibility, not a blindbackendflip: currentJNIwrites rawCPUhost
tensor memory; pinned primarydocs require map/unmap or stagingtensor+copy forGPU,
withlayout/sync/backendverification. CAMERA-FREE GPU probe now implemented in
tools/gaze/mnn_gpu_probe/{build_probe.py,mnn_gpu_probe.cpp}, debug-only Java bridge,
and MnnGpuExecutionProbeDeviceTest. No integration into live gaze/source/default.
NDK26.1.10909125 installed via official AndroidCLI at
C:/Users/USER/AppData/Local/Android/Sdk/ndk/26.1.10909125 (sandbox LOCALAPPDATA is
redirected; use this actual path or explicit --ndk). Public MNN3.6.1 archive is
hash-verified46dc7e86...; original core/C++libs unchanged. Optional OpenCLlib
5293b0405a76f2fd824601c47ea5892e051235fb7c316851cabaec6a07aab184 and nativeprobe
5d1c5804c6841427258c24969d6603b1c86702ab85b246a2fab5dd885831e871 compiled/pinned
headers/ELFdeps checked. Source review found missing persistedheaderreceipt in
buildscript; fixed+review cleared; prepare/compile rerun pass(notclean-outputclaim).
Packaging is explicit -PnewsmeadMnnGpuProbe=true, debug sourceSet ONLY; normal
builds require no NDK and release doesn't include triallibs. APK/pinned checks
pass; both newlibs byte-identical inAPK. Output output/mnn-gpu-probe-2026-11-13/.
GPU main APK31443d5e872313792f86cb87bc0795f8459726e81e9cd6ded2083e9556507c4a;
test970340299794271534cbcdbd789780a8b843abb84ed9490f2225d97a11fbcb3a.
Initial S21 probe FAILED CLOSED: OpenCL init failed and mainpipeline fell backCPU;
no fixtures measured, native construction cleanup verified. Evidence s21-*.json/log
and s21-mnn-runtime.log. Diagnosed public vendor libOpenCL.so present/listed but
API34target lacked API31+uses-native-library declaration. Added optionalfalse
libOpenCL.so in probe-only manifest. First rebuild hit usage-limit approval
failure(no execution, not safetyrejection); resumed build exposed lead's mistaken
!accountFree guard. Existing accountFreeStudyBuild=true. Fixed probe overlay to
retain the original CrashlyticsRegistrar removal/analyticsdeactivation, selected
AFTERordinaryaccountFree block only underGPUproperty; release/ordinary unchanged.
Independent review and mergedmanifest privacy/driver checks pass. Rebuild/pins
passed7s. S21now mainbff3392dd4f03d04ac7966ffa7500d1e5b8168363827e93dc1aee48ddf6640e3;
test97034029...unchanged. Newcamera-free probe PASSED, label s21-driver-access:
mainbackend3, malformedlengthrejected,258finiteoutputs on3syntheticfixtures,
same-sessionrepeatdiff0, nativeclosureverified. Nativecallmeans35.65/36.04/35.49ms
includeupload/validation/sync/outputcopy, not liveFPS. Its
{device.log,summary.json,mnn-runtime.log,receipt.json} retain the evidence.
Probe must observe main
OpenCLbackend3, finite258outputs, same-sessionrepeat<=unchanged1e-5, safeclosure;
CPUfallback cannotpass. BACKENDS mainpipeline doesnotexcludeper-opCPUfallback.
NormalGPUprecision usesfp16storage/fp32compute, notCPUfloat32parity. Synthetic
timings are not liveFPS/accuracy. Accuracy remains deferred.

Live isolatedGPUintegration implemented/reviewed, not yet installed: per-instance
BACKEND extra CPU|OPENCL requiresDEBUG+isolated+referencecount2; absentbackend
preservesCPU4/directCPUconstructors. MnnEstimator delegatesGPU to cached-reflection
ExperimentalGpuSession using testeddebugJNI; CPUinfer unchanged. GPUthread/pool
countsunknown, noCPUobserver/hints. Exactmodelclosed/native_close_verified must
passafterproviderclose beforestorage reset/rawdeletion. Build/pins/216JVMpassed
(1skip), live-gpu-build-jvm.log. Focusedcamera-free GPUadaptertest added; testAPK
build session3077 pending. Planned ownface-confirmedS21livecomparison6fresh
processesA,B,C,C,B,A: A=CPU2gaze/GPUface, B=OpenCLgaze/CPUface,
C=OpenCLgaze/GPUface; recordingorderbalanced,10sphases. Newhost
run_live_gpu_comparison.py syntaxpasses, awaitingreview/devicechecks/readiness.
Do not infer GPUworkerCPU2 from the referenceargument or treat GPUreference
calibration as compatible/adopted. NoliveGPUperformance established yet.
InstalledS21 main2d2025b7f6356c0dd5047d12d47c071444671329fc8f70ea493601f5663839bc;
native/adapter/auth9checksPASSED. Firstliveattemptlabels21-live-gpu stoppedA1
coverage(61%no_face), cleanclosure/calib/rawdeletion; excludeit. Freshface run
labels21-live-gpu-face A1passedzeroNoFace(OFF25.20,ON20.49callbacks/s), butB1
timedOUTbeforecamera/timedresults atonModelWorker5ssnapshotduringstartup.
Writers/providercompleted/calibunchanged; modelreferencewasmissing soGPUclosure
unverified andisolatedrawpackage retained at
cache/study-device-test-live-perf-f8711aeb-68a6-4bde-8254-ccbdd4581ed3.
Hostforce-stoppedownedapp; thatdoesnotverifyJava/nativeclosure. NoGPUthroughput
resultfromthisattempt; don'tdeleteunrelatedcache.
Harness-onlystartupfixreviewed: explicitCPU/OPENCLarmswaitfirstnonwatchdog
coordinate<=15s, THEN3swarmup; existingprovider20swatchdog/expiry/face/timedgates
unchanged. Uncancelledmodel-referencefuturecapturesexactmodelbehindinitbefore
closeforfailurecleanupverification. Oldpartialrunsstayseparate. TestbuildPASSED
43s,newtestd05402535a72c7aa33b143f213d0e6a4adf38a20ed4a87ec98152497eb9f7694
installedS21; mainunchanged. Newlabelplanneds21-live-gpu-ready afterfreshface.
HostP2fixed: exactPIDstagedumpsboundedbydeviceepochstart/end+Decimalfilter to
excludePIDreuse. Oldpost-runstagefilesnumericPIDonly; primarytimedratesunaffected.
Host reviewed, outputnamesprotected. Added aggregate
capture-to-analyzer delay counter to existing numeric stage logs, no images.
Scout found onObservation recording→JSON snapshot/queue plus shared scanpath/
geometry work; no new safe app optimization established by source alone.
Accuracy work remains deferred.

S21 is the owner's primary success device; A56 is a regression check. Keep one
gaze model and general capability/workload logic, with no phone/model allowlists.
Prior task-specific authorization for test installs and short front-camera tests
persists: numerical data only, no retained camera images, preserve calibration
and participant packages. No staging/commit; preserve the pre-existing `.idea` edit.

Ordinary execution remains MNN3.6.1 CPU4threads/default precision, two
preprocessing slots; safe gzip/hidden-redraw/debug-cursor changes remain. The
current experimental builds and device installs are recorded below. Passing
software checks does not prove human accuracy or study readiness.

General ADPF probe: this S21 returns null session; A56 accepts sessions. A56's
face-present recording-ON exploratory comparison was23.39callbacks/s hintsOFF
versus26.30ON; OFF26.80versus26.24. No general hint default enabled. Its earlier
mostly-no-face run is excluded. Both runs closed/deleted isolated packages.

Public OS-priority fallback is TEST ONLY: one MORE_FAVORABLE(-1) increment on the
same four original MNN-worker identities; Android selects cores. A56 completed8
blocks with all priorities restored: recordingON26.10→26.94callbacks/s,
OFF26.55→26.50 (no clear large gain). S21's first control stopped on an unrelated
Binder priority change despite0mutations; verifier now records such existing
non-target variation while strictly verifying targets/new possible inherited
priorities. A later S21 control had24.24%no-face callbacks and stopped coverage;
no priority mutation occurred, calibration unchanged and isolated raw data deleted.

New TEST-ONLY face preflight is reviewed and built: after warmup/before settings,
wait up to20s for1s continuous reason!=no_face observations with <=500ms gaps and
latest capture age. Positioning Toast appears while waiting and is cancelled
before intervention/settle/timing. Existing timed <=.20 no-face/+coordinate guard
stays unchanged. These are setup heuristics, not study/accuracy eligibility gates.

Usage-limit approval failure stopped the build Nov12; no safety rejection or
command execution occurred. Nov13 default Java25 failed before compilation;
per-command JAVA_HOME to installed JDK21.0.3.9 restored the successful build.
No project/global Java setting changed. Test APK SHA256
`b93587ecddd5e8085417bb7ed7af91d2d78e21a72c002527dfd3a2346daafbff` installed S21.
Eight-block S21 priority trial with preflight COMPLETED: recordingON9.95→10.04/s,
age331.85→325.36ms; no material improvement, not adopted. All8priorities restored,
calibration unchanged and isolated raw packages deleted; thermal0→2. Receipts:
`worker-priority-s21-preflight-*`; `summarize_scheduler_trials.py` now uses this
valid run instead of the earlier rejected coverage run.

Owner explicitly authorized SAME-model execution exploration in a separate path,
working study pipeline intact, accuracy checks before adoption. Added guarded
Intent route CPU2/4 (now extended to CPU3): DEBUG+validated isolated live-perf
cache only, constant count per instrumentation invocation. Ordinary/calibration/
validation default4 and CalibrationIdentity CPU4 unchanged. Candidate uses saved
CPU4 calibration only as explicitly unvalidated reference mapping. No writes.
MNN retains FIRST process pool size; old both-models-loaded timings do not isolate
a clean2worker pool. New host ABBA runs force fresh processes4,2,2,4 and verifies
distinct PID/start identities plus observed pool sizes (not just requested count).

CPU2 fresh-process ABBA PASSED on BOTH phones, all8packages verified/deleted and
calibrations unchanged. S21 ON8.40→14.50callbacks/s, age380.85→233.80ms;
<=250ms camera results0.35→12.85/s (not joint aid eligibility). A56 ON25.14→21.80,
OFF26.79→21.85: globalCPU2 regresses A56. No adoption. Main/test builds/pins and
216JVM checks pass (1Windows skip);8camera-free native/auth checks pass each.
Current installed main on both:82309a3163a83b93ecace4af81058af01d18f32dd20ca340553483684132a55e,
still ordinaryCPU4. Evidence `same-model-{s21,a56}-*`; analysis scripts in output.

Testing ONE additional shared CPU3 compromise, not per-phone rules. Source
extension+authorization probe reviewed (no Android findings); build/pins and216JVM
checks passed (1Windows skip), `same-model-cpu3-build-jvm.log`, process-local JDK21.
Main SHA256884b688cd77ed80335c90649f354f0f01ba2a276c583e5676b9ace55dbe86949;
test8bbe561617ae47825c3c987b3d21912bb2717b2f5eb544b76eee20cb5b1bdaef.
Installed BOTH;8camera-free native/auth checks pass each. CPU3 ABBA COMPLETED on
both: S21 recordingON8.45→11.70callbacks/s, age377.60→272.88ms,
<=250ms camera results0.40→6.55/s; A56ON24.90→26.25/s, age188.60→188.35ms.
All8processes/pools/packages/closure/calibration/cleanup checks passed. S21
thermal0→1→2→3→3; A56 receipts retain its thermal states. Short exploratory
checks only; no endurance or accuracy established. Host runner now
accepts --candidate-threads3, --serial and distinct --label, rejects ANY existing
output destinations before device operations (mock guard verified; independent
review cleared). Completed labels s21-cpu3 and a56-cpu3. CPU3 fixture parity PASSES
both phones unchanged1e-5(max5.841255e-6 across774features/3syntheticfixtures),
using CPU4-first shared native pool; not a performance or human accuracy check.
PriorCPU2/4 strict1e-5 failure remains. New parameterized parity test APK63907ef68a8dba518095a6f38558ddecb0e099bfcc18e8973718a9424c682d7d installed BOTH,
main unchanged884b688c above. Evidence same-model-cpu3-parity-{s21,a56}-*.
Before adoption need raw held-out accuracy checks. Existing ComparisonRuntime is unsuitable (frozen device
slots and saveComparisonCalibration overwrites normal calibration); do not reuse
it for isolation. The briefly planned accuracy harness was stopped before edits
on the owner's correction above. Preserve oldcalib; ordinary default4 unchanged.
Do not repeat earlier rejected candidates.

**Historical 2026-11-12 performance path (current state above supersedes it):**
Owner authorizes test installs and short camera performance tests on S21/SM-G991B
(SDK35) and A56/SM-A566B (SDK37), numerical recording only, no camera images.
A56 initially rejected its saved calibration; owner explicitly completed/saved a
new one. Existing participant packages were not copied. Whole-app article OFF/ON
tests use isolated cache storage and retain only aggregate summaries after closure.
Current safe changes: BEST_SPEED gzip with exact decoded-byte checks, hidden
diagnostic redraw suppression retaining visible scaffolds, and user-requested
orange cursor/FPS in debug builds (release hides diagnostics). Four MNN threads
and the original two preprocessing slots remain. Two-thread strict fixture parity
failed; one-slot trial reduced delay but hurt throughput and did not resolve
S21 recording freshness, so neither candidate was adopted. No gate was loosened.
S21 ~9-12 callbacks/s vs A56 ~26, with S21 recording age ~319ms vs A56 ~184ms;
actual scanpath `scroll_input_stale` counts substantiate the 250ms aid gate as a
blocker, with other exclusions also present. R/D/F/shared references unavailable
in these process-restarted tests; no accuracy, benefit, endurance or release
readiness claim. Owner asks recommendation; lead recommends preserving estimator
and continuing app/camera CPU/scheduling profiling. Ten-second traces on both
phones now exist: S21 four native workers use29.737CPU-s within9.603s, with843
surviving MNN-ancestry sched_yield stacks; JSON writer is secondary. Perf sample
loss is substantial; do not infer exact hotspot shares or merge later stage logs.
Both profiled full-app runs passed and removed their isolated raw packages.
Final APK SHA256130cb3831e2ba2935a6ae5b938af814e7df5057ac561d871fc0b3014266fe3bd
is installed on both phones (debugcursor,4threads,2slots). After usage-limit
interruption, owner resumed and confirmed S21 face ready. The shell-domain
affinity-query permission failure was diagnosed; app-owned test-only commands
work. Eight S21 timed blocks verified masks restored, unchanged calibration,
healthy recorded packages and deleted isolated raw data. Whole-app CPUs4–7
placement raised callback rate modestly but did not improve within250ms rate;
thermal0→3. Not adopted. Three synthetic same-session4thread model fixtures
were exactly equal before/during placement, with restoration verified; not
human accuracy evidence. A byte-identical reusable recording encoder saved only
0.8%CPU on text-heavy synthetic rows despite33%lower allocation, so not adopted.
Details/receipts: report section 'Resumed CPU-placement and recording checks'.
Version-matched MediaPipe bytecode confirms per-frame MPImage.close recycles
upright Bitmap, but native pixel ownership remains unproven; no bitmap-lifetime
change was made. Camera-free thread timing completed on both: S21four15.88ms
vs two27.98ms; A56four46.91ms vs two25.34ms. These are isolated/background
synthetic timings with uncontrolled scheduling, not live-app performance;
two-thread ramp parity again failed unchanged1e-5, and no candidate was promoted.
Selective4worker placement completed on BOTH phones with16restorations, unchanged
calibrations, verified zero-loss packages and deleted isolated raw data. S21 ON:
callbacks10.15→14.95/s, age323.34→218.55ms, <=250ms camera results2.85→13.55/s.
A56 ON25.15→25.00/s, OFF27.20→24.64/s: no general affinity benefit. S21thermal0→3,
A560→0; exploratory short blocks, no human accuracy/endurance claim. Owner rejects
phone/model allowlists; proposed S21-only patch was stopped BEFORE any source
edit/SDK install/deployment. Same model must support other phones too. Current
work: general Android PerformanceHintManager API31+ experiment, defaultdisabled,
capability/null fallback, same four identified workers, actual inference duration
versus intended30Hz target, no forced cores. Official AOSP recommends hints over
static affinity. Implemented/reviewed,171JVM tests and pinned checks pass. Both
phones now have default-disabled hint APK SHA256fdd1792a2e8b74658ba96024ec04ff30662e249b7bd481be3781d4486afc19f4.
Seven camera-free native/probe checks pass each: S21 null session (no live hint
run), A56 active7reports+closed,3fixture parity unchanged1e-5. First A56 live hint
run mostly no face, excluded despite safe closure/raw deletion. Owner confirmed
BOTH faces ready again; A56 guarded rerun `performance-hints-live-a56-face-*`
active. Coverage guard is diagnostic only. Engineer preparing test-only one-step
more-favorable MNN-worker priority lease/harness fallback using public Android
API, same model, no forced cores/phone allowlists; not adopted or run yet. See
report's latest sections and `resume-mnn-placement-*`; aggregation script
`summarize_mnn_placement.py` corrects a discarded PowerShell age-denominator bug.
Do not repeat calibration or rejected thread/slot/wholeapp-affinity trials.
Evidence/checks/limits: docs/app-performance-2026-11-12.md and ignored
output/app-performance-2026-11-12/. No staging/commit.

**2026-11-09 reading-aids ON/AUTO/OFF modes implemented in software:**
Replaced the session-only master boolean UI with an immediate radio-mode control.
AUTO retains the existing trigger/evidence path; OFF centrally clears presentations
and disables auto-scroll while preserving individual choices; ON presents enabled
guide, bookmark and focus visuals at a current mapped or visible article row without
claiming trigger evidence. Word lookup still requires a mapped word and user tap;
Sentence context resolves from the selected visible article row. Article preferences now
persist through `article_reading_preferences`; a legacy `reading_aids_enabled` value,
if present, maps false to OFF and true to AUTO. Focused policy/migration tests passed
and the Android test APK compiled. No device, camera, calibration or human-validity
check was performed.

**2026-11-09 interview sheet simplified after owner feedback:**
Owner rejects the4-page guide as too wordy for live interviewing and supplies
Downloads/annex8-fm1.pdf as a STRUCTURE reference, then explicitly asks for short,
open-ended questions and follow-ups. Inspected reference text/first-page layout;
did not adopt its medical content, demographics or verbal-only consent procedure.
Rewrote sibling formative_interview_guide.tex as2readable pages with numbered
topics1-10, one open-ended follow-up each, simple grey section bars and brief
opening/preview cues. VersionFT-2026-11-09-S1;284words including header/footer.
Questions1-5 remain after natural reading/written notes;6-8 repeat per previewed
aid;9-10 close before debrief. Preview is explicitly deliberate, not gaze-triggered.
Moved detailed administration/interpretation notes to existing
participant-session-steps.md; methodology safeguards remain in Chapter4 unchanged.
Updated operator-guide question references to1-10. This is a conversational guide,
not a script requiring every follow-up. Known consent/screening/debrief and app
instruction alignment remains outstanding; no protocol, eligibility, audio-scope,
questionnaire or measurement change was made by this simplification.
Rebuilt original main.pdf(116pages) and replaced original interview-guide PDF
with the2-page extract. Compile/crossrefs, ten-question/phase checks, both-page
visual QA and final hashes pass. Source-before backups, reference renders, checks
and final receipt: sibling output/pdf/interview-guide-simple-2026-11-09/.
Main SHA256188791d82d63516e13fbab5ea0c0c30ec0d04504e0a915e5bd6be3f931e532bc;
guide SHA2560f488776718c049b51e7145c1af12cbcf3db3546adef3caf6a438cedacf1f39f.
No original consent/screening/debrief PDF changes, app/device work, staging or commit.

**2026-11-09 formative methodology and interview guide revised:**
Owner explicitly authorizes updating the existing formative thesis section and
interview questions. Edited sibling THS-ST-Paper Chapter4 Section4.1 (plus its
intro phrase), Chapter1 formative summary/figure caption, AppendixA guide inclusion,
and corrected the placeholder GazePrompt bibliography entry. Added shared source
formative_interview_guide.tex and a4-page printable extract; rebuilt main.pdf
(118pages). No wholesale manuscript rewrite or original3form-PDF edits this task.
Initial round remains3-5target participants: normal reading with live aids after
existing preparation/shared baseline; exit article/Stop run; written N1-N5 feedback;
labelled display preview; post-task P1-P3 for each previewed aid and closing C1-C2;
formative-aligned debrief/data-use confirmation. No mandatory control group or
off/on comparison, no forced triggers or concurrent think-aloud. MD remains
observational; reference/score failures stay unavailable; no new questionnaire,
clinical gate or validated focus/stability score is adopted. Preserve existing
eligibility/retries and label phase/version; do not pool pilot with later main data.
Preview is not natural trigger evidence, and recorded rendering is not reported
noticing. Guide explicitly notes ordinary article opening can start camera before
demo mode; recording OFF alone does not stop capture. PartA/reading/preview audio
is outside the current post-session audio permission; final interview audio only
if separately permitted. Operator guide now distinguishes formative vs comparison
flows; older per-article UES proposal remains a later-evaluation candidate.
IMPORTANT: current consent/screening/debriefing PDFs still describe2comparison
readings; target their procedure wording before using this formative variant.
Review caught the debrief mismatch, fixed the prerequisite and explicit natural/
preview explanation in revised text; re-review has no remaining findings. Approval
is documented elsewhere, not newly questioned. Installed app Session instructions
also still describe comparison flow. Legacy later-outcome/controller sections
remain outside this bounded revision. Source boundary checks preserve Chapter4
afterSection4.1, Chapter1 outside the targeted summary and existing appendix PDFs.
LaTeX build/crossrefs and guide question-ID checks pass; revised pages visually
inspected. Backups/build/QA/receipts in sibling output/pdf/formative-methods-2026-11-09/.
Final original main.pdf SHA25638b034d5713cbbd25ab7423d280df5a6773409248456995747cc5d228b914a6f;
formative_interview_guide.pdf SHA256d0719f394e565a81ebfbe7d7fc8284ebb38a19ccc02a533bbeb13ef587e3f015.
No app code change, device operation, collection, staging or commit in this task.

**2026-11-09 account-free fixed article list implemented:**
Owner explicitly requests prefilled articles without creating a testing account
or syncing history. The account-free build now shows Saved -> Testing articles,
using the existing2 bundled setup samples (not approved participant comparison
texts). New StudyArticleCatalog validates explicit unique IDs/title/body, preserves
exact text and namespaces IDs as local-study-v1:<article_id>. StudyArticlesFragment
uses the existing card/list/navigation design. ArticleFragment gives packaged
body precedence over account Room cache and URL extraction; the network feed
remains enabled. Actual final English articles/questions still need selection.
Source audit also found normal saved offline articles can be refreshed after1day
by startup preload; the prior claim that normal saving alone permanently freezes
them was incomplete. Packaged testing articles bypass that account path.
Single accountFreeStudyBuild flag in app/build.gradle.kts supplies BYPASS_LOGIN
and pre-start manifest policy. Account preload/history/saved checks and auth UI
are bypassed; no testing account or participant Firebase identity was created.
Analytics is deactivated in the manifest. Focused review caught a retained-opt-in
Crashlytics startup race; fixed with the accountFree manifest overlay removing
the eager CrashlyticsRegistrar before SDK initialization. Logging wrappers skip
cloud reporting. Reviewer confirmed the exact18.4.3 registrar is absent from
merged debug manifest and the remaining KTX registrar cannot recreate reporting.
No gaze, aid, MD or baseline algorithm changes. No claim of whole-app network
silence: news/dictionary requests remain possible and Firebase SDKs remain bundled.
Final offline main/test APK builds and16focused JVM tests pass; packaged catalog
matches source. LocalStudyArticlesDeviceTest is compiled and camera-free (list,
Home/Profile, recreation, manifest/component checks), but NOT run. No phone
install/capture, staging, commit, original-PDF or manuscript edits in this task.
APK SHA256 b194c610f6586faca493c155f192979acfdb87e47926159baf72338857ebbe21.
Checks receipt/log: ignored output/local-study-articles-checks-2026-11-09.json
and output/local-study-articles-final-build-2026-11-09.log. Hardware startup,
retained-data upgrade and article-reading runtime remain unverified for this build.
SDK policy sources: https://firebase.google.com/docs/analytics/android/configure-data-collection
and https://firebase.google.com/docs/crashlytics/android/customize-crash-reports .

**2026-11-09 assessment research and concrete recommendation prepared:**
Owner says proceed with HCI-focused evaluation preparation and confirms English
for the first volunteers' articles/assessments. Added a clearly marked draft to
the existing reading-outcome-measures-evidence-2026-11-08.md and linked it from
participant-session-steps.md; original forms/manuscript/app are unchanged in this
task. Recommendation: full12-item English UES-SF after each assigned reading,
with Focused Attention the candidate numerical experience outcome, plus neutral
post-condition interviews for continuity/comfort/distraction, existing logs and
supporting comprehension questions. This is NOT a selected instrument, frozen
primary endpoint or active participant protocol. UES-SF FA measures absorption/
time passing; it cannot establish concentration lapses or keeping one's place.
The source supports separate subscale means, reverse-coded usability items and
separate repeated administrations; retaining all12 avoids claiming three selected
items are the complete instrument. No validated place-keeping score was established
in this bounded review. English wording needs no new translation, but older-adult
phone-news validity remains unestablished. No custom composite, new clinical
threshold, formal hypothesis, significance test or numeric stability endpoint.
Draft includes6neutral interview prompts, explicit task/package linkage, missingness,
counterbalancing, per-article assessment priming, and materials still to be named.
Primary source: https://oro.open.ac.uk/68806/1/1-s2.0-S1071581918300041-main.pdf
sections6/8 and AppendicesA-B. Direct HCI reading-aid precedent remains GazePrompt
sections4.1.2/5.1 (desktop low vision). No device capture, PDF rewrite or staging.

**2026-11-09 owner prioritizes reading experience, focus and stability:**
The owner clarifies that focus and stability during news reading should lead the
HCI evaluation, with comprehension receiving less priority. This is an outcome
direction, not selection of a questionnaire, validated stability score or final
statistical endpoint. Define perceived concentration/keeping one's place separately
from objectively demonstrated attention or reading errors. Scanpath classifications
are categorical algorithm outputs; their counts/durations can be quantitative.
Interviews supply qualitative evidence. Both MD variants remain numerical
observations, not established reader-benefit/stability measures. Do not equate
lower MD, fewer regressions or smoother coordinates with improved reading.
The owner also notes the live recommendation feed changes articles. Source check:
StudyConfig.OFFLINE_MODE=false; DataHelper.loadArticleData uses the network path
with a 10-minute response cache; loadLocalArticleData and study_articles.json
already offer a bundled-content route, currently inactive. No feed or content
change was made. Proposed controlled comparisons can freeze selected real-news
texts and balance their condition/order assignments while retaining normal
recommendations for exploratory use; this has not been adopted or implemented.
Relevant primary precedent: GazePrompt sections4.1.2 and5.1 used experience ratings,
interviews and controlled passages; silent-reading participants were13 low-vision
adults aged30-85 on a desktop, not validation for older phone-news readers:
https://arxiv.org/html/2402.12772 . UES/UES-SF is a published engagement-instrument
candidate, not a selected measure or verified place-keeping assessment:
https://doi.org/10.1016/j.ijhcs.2018.01.004 . Assessment selection remains open.

**2026-11-09 post-reading assessment preparation remains open:**
User asks whether user testing also requires assessments and interview data.
Current Chapter 4 already specifies comprehension after each article, researcher
observations, a usability survey, then semi-structured interview and debrief.
The session guide likewise links external answers/notes by participant/task IDs;
the local study recorder has no comprehension/survey/interview capture UI.
Bounded search found no finalized article question/answer-key set, questionnaire
instrument or separate interview-guide artifact in the current project files.
Do not claim the consent/screening revisions finished assessment preparation.
Proposed initial collection: fixed article questions with scoring key; actual task
start/end/completion and technical/assistance notes; an established usability
questionnaire selected with the owner; neutral interview on noticed changes,
help/distraction, comfort and preference before debrief; existing post-debrief
data-use confirmation. Paper/local records can link to gaze data without Firebase.
SUS is only a candidate for perceived system usability, not a reading-performance
test or adopted instrument. Primary author source retrieved:
https://uxpajournal.org/wp-content/uploads/sites/7/pdf/JUS_Brooke_February_2013.pdf
No new assessment forms, questionnaire, primary outcome or scoring protocol were
selected/created in this clarification. Chapter 4's older recovery-latency-primary
and index/controller wording still need a separate substantive revision.

**2026-11-08 WHOeyes descriptive recording and functional eligibility adopted:**
User reports WHOeyes near output is only 0-100%, without a 20/40 result, and asks
whether that exact acuity cutoff is necessary. No percentage-to-Snellen mapping
was verified; do not assume 50%=20/40 or use an undocumented percentage cutoff.
User explicitly approved the proposed alternative ('yes lets do that'). WHOeyes
score/setup are now descriptive background; eligibility uses ability to see and
read the existing held-out practice passage at fixed study settings with habitual
correction, while tracking is assessed separately. No Snellen or percentage
eligibility cutoff and no clinical/validated-reading-score claim. A wrong practice
comprehension answer alone is not a visual-screening failure. The record now asks
'Practice text readable? Yes / No / Unclear'. All remaining criteria must still
be completed; age, health exclusions and two full calibration attempts unchanged.
Updated original sibling THS-ST-Paper/screening_guide.pdf in place (9 pages),
targeted its Chapter 4 eligibility/source paragraphs, and aligned the existing
docs/participant-session-steps.md. Consent/debrief PDFs were not changed again.
Nineteen targeted PDF edits; changed pages visually inspected, drawings preserved,
zero changed pixels outside amended regions. Independent review found a legacy
'Section 3 passed' enrolment reference; fixed and reviewer confirmed resolution.
Backups, scripts and final receipt: ignored
output/pdf/functional-reading-eligibility-2026-11-08/.
No app/device operation, new clinical validation, staging or commit. Whole-manuscript
PDF was not rebuilt; other legacy methodology sections remain a separate task.
GazePrompt's functional-reading eligibility is precedent in 13 desktop low-vision
participants, not validation of our phone screen:
https://arxiv.org/html/2402.12772 sections4.1.1-4.1.2.

**2026-11-08 original PDFs revised with explicit authorization:**
User then explicitly instructed: work directly on originals and provide correct
PDFs. Edited the three original files in sibling THS-ST-Paper in place:
`Informed_Consent_Form.pdf` (3 pages), `screening_guide.pdf` (9 pages), and
`debriefing_script.pdf` (5 pages). This is targeted PDF text surgery, not a new
packet or redesign. Original letterheads, section numbering, Times typography,
bilingual scripts, form/table rules, checkboxes and signature areas retained.
Consent corrects camera tasks, numerical local records/transfer, coded identity
and identifiable optional audio. Screening integrates WHOeyes near/binocular/
40 cm with actual score/units/device/version/distance and uncertain-result handling,
current 13-target/six-check cm gate (including 12 usable samples/check), same
two-full-attempt limit, fresh participant calibration before practice and explicit
shared baseline controls. Clinical exclusions and age criteria were not changed.
Debrief removes unestablished recovery-primary/baseline-driven aid claims, preserves
both languages, clarifies scored answers and linked-copy withdrawal; early-exit
debrief exceptions fixed after independent copy review. No app logic changed.
All 17 pages rendered/inspected; candidate-to-original pixel comparisons find zero
changes outside intended text regions, and drawing geometry/counts are preserved.
Original unsigned PDFs have no AcroForms; printed fields remain intact. Original
hashes checked before promotion, and final original-file hashes match candidates.
Byte backups, edit manifests and QA: ignored
`output/pdf/original-document-edits-2026-11-08/`, final-receipt.json. No staging,
commit, phone operation, third-party-app test or new clinical validation performed.

**2026-11-08 correction: use the existing consent/screening documents:**
The user explicitly rejected the lead's unsolicited wholesale rebuild of the
forms. They asked to see and understand their existing documents, identify needed
revisions and prepare printing, not replace the documents' structure and wording.
The original thesis PDFs were never changed. The generated replacement packet,
its builder/Markdown copies and its runbook changes were removed. Work from the
existing forms with page/section-specific proposed edits, preserving their layout
and established procedure. Do not infer permission for another complete rebuild.
The user conditionally prefers WHOeyes if credible for screening and permits
any researcher's screening phone, including iPhone; Android-only applies to
NewsMead. That discussion is not a finalized amendment of their screening guide.
WHO officially supports near testing at 40 cm, manual distance on Android and
an automatic iOS option. Direct validation retrieved used iOS (220 participants,
50 aged 41-80); no best-app or exact Android-device accuracy claim is established.
Evidence retained for retrieval:
https://www.who.int/teams/noncommunicable-diseases/sensory-functions-disability-and-rehabilitation/whoeyes
https://ira.lib.polyu.edu.hk/bitstream/10397/107489/1/Wu_Real-world_Application_Smartphone-based.pdf
Alternatives' near studies: https://pmc.ncbi.nlm.nih.gov/articles/PMC4815669/
and https://pmc.ncbi.nlm.nih.gov/articles/PMC7933864/.

**2026-11-08 consent/screening review and participant-readiness work:**
User supplied the THS-ST2 agreement PDF and asks to prioritize it with about one
week until the demo (working target around Nov15, not a confirmed date). User
believes1-4complete and would be happy to reach5: recruit volunteers and begin
initial data collection. Roughly4possible volunteers/1currently available is the
working interpretation, not confirmed scheduling. User explicitly does NOT want
another drafted plan: inspect the paper's actual consent/screening requirements,
avoid crucial procedural mistakes, and prepare the current model/app for testing.
User CONFIRMS approval is documented elsewhere; do not keep asking or treat the
unsigned repository clearance template as an approval blocker.
AppendixA includes consent(3pages), screening guide(9pages), debriefing(5pages).
Consent covers camera calibration, two reading conditions, comprehension questions,
interview, separate optional interview audio and45-60min sessions. Screening specifies
age45+, teach-back capacity before signature, habitual correction, scaled near card
at its stated distance, binocular20/40 criterion, reported diagnoses, background
questions and a held-out150-200word practice. These are documented protocol rules,
not newly validated medical screening or model-accuracy criteria.
Critical stale instructions: guide16pointcalibration/LOOCV/green-amber-red bands
and automatic20sbaseline do not match currentMGazeNet13point +6post-fitchecks,
engineering PASS/RETRY cm guardrails, explicit shared-baseline Start/Finish and
offline coordinate fitting. Debriefing falsely describes index-driven adaptation
and recovery as the measured main outcome; factual alignment is needed before use.
Current templates require survey->interview->debrief, post-debrief permission for
data use and honoring withdrawal. Interview-audio permission does not cover
recording reading/practice audio. Names/signatures and screening responses remain
separate from coded gaze packages. Read-only PDF evidence is in ignored
output/ths2-agreement-review-2026-11-08/.
User confirms research approval is documented elsewhere. User separately
authorized a data-preserving app/test install and camera-free checks. New shared
toolbar policy fixes expanded geometry for baseline/linked article views while
preserving passage scrolling and actual metadata/gates. Deferred attach race found
in review was fixed with coalescing/cancellation/lifecycle guards; re-review has no
remaining findings. Participant article gaze/FPS diagnostics are now hidden, with
numeric capture and explicit dot-validation protocols preserved.
Offline main/test builds and3focused JVM tests pass;3camera-free phone tests pass
(viewport swipes/flings/restoration and baseline navigation). Calibration hash
and all13recording manifests unchanged; camera app-op shows no new access.
The earlier viewport build was installed and tested. The final session-guide APK
installed below has SHA256
13f25c7bf560d52791830e57f674162ceab95b6fef7a785cab9ffb80536a6455.
Evidence: ignored output/participant-readiness-2026-11-08/. User subsequently
deferred the manual self-rehearsal because of time/token limits. Current scope is
clear participant/researcher steps and app session instructions, not another
development schedule or a request for live capture. Added the researcher help
dialog in Study recording, clearer baseline prompts, and
`docs/participant-session-steps.md` with the operational sequence, participant
wording, a blank session record and actual protocol/app differences. Final offline
main/test builds pass, final wording was checked inside the APK, and the updated
baseline-navigation test passes on SM-A566B (1 test). Calibration and all 13 saved
package manifests are unchanged. The new eight-section guide was opened and
visually inspected on the phone; it is left open, recording stopped. Camera app-op
shows no new access. USB briefly became unauthorized; user accepted it again and
the data-preserving install succeeded. Receipts are in ignored
`output/participant-readiness-2026-11-08/session-guide/`; build logs in its parent.
Live default-gate coordinate-reference fit is PENDING; do not call repaired
collection or older-adult accuracy validated yet. No live capture was performed.
The owner then requested branch `initial-demo`, created from the current
`feature/local-study-collection` HEAD with all working edits preserved. Home hides
Scanpath pilot/replay and places Study recording directly below Reading baseline.
Offline build, data-preserving phone install and Home UI checks pass; both hidden
controls are absent while baseline/recording controls remain visible. Evidence:
`output/participant-readiness-2026-11-08/initial-demo-home.json`. That latest demo
APK SHA256 is 7ce302dd7cc79b8e461ecdcdf9a6d7fc700f27b33d06744c2705381743d94aad.
Bounded source audit confirms StudyConfig.BYPASS_LOGIN=true/OFFLINE_MODE=false;
auth/recommendation paths exist but runtime item1verification is still needed.
Study recording OFF does not disable the article camera; condition text labels
do not control aid preferences. The actual controls sheet sets aids on/off.
Saved MGazeNet calibration has no participant binding: fresh calibration for
each new person is a required researcher step, not guaranteed by changing a code.
Coordinate reference in the latest rehearsal remains unavailable until a fresh
fixed-viewport baseline is actually captured and verified. Do not relabel scores
or lower gates. Retain broader thesis goals and agreement6/7(data analysis and
mandatory conference paper), without making a full rewrite or conclusive efficacy
study a new requirement before initial volunteer collection.

**2026-11-08 thesis hypotheses require a revised outcome decision:**
User confirms the manuscript predates the current scanpath/MD system and its
recovery-latency objective must be reconsidered. The earlier suggested recovery
H0/H1 is provisional, not an approved current hypothesis. Preserve the confirmed
goals of feasibility/acceptability and reading benefit. Scanpaths drive the
existing aids; R/D/F and coordinate MD remain observational and are not validated
instability/recovery outcomes. User tentatively preferred perceived effort/loss
of place because the thesis is HCI, then challenged the evidence for that choice
and clarified the need for a performance/evaluation metric alongside the scanpath
algorithm. Perceived effort is not a finalized primary outcome. Do not invent a
new composite or claim reliability from logical fit/precedent alone. Retain
scanpaths and both MD measures; observational use does not inherently prevent a
prespecified comparison endpoint, but a change in MD/patterns alone is not proof
of reduced effort or reader benefit. Separate detector correctness, reader-task
performance/experience and technical feasibility. No questionnaire, task scoring
rule or final hypothesis is approved yet. NASA-TLX official guidance
https://www.nasa.gov/human-systems-integration-division/nasa-task-load-index-tlx/
describes subjective workload across six dimensions, not reading effort alone;
an instrument choice must preserve that distinction and be piloted with intended
readers. Primary evidence checked: Paas et al.(1994),
https://ris.utwente.nl/ws/files/248299579/Paas1994measurement.pdf pp.422-425,
used1-9mental-effort ratings in student statistics/geometry training; reported
internal consistency across repeated problems alpha.90/.82, explicitly did not
assess validity. This is not older-phone-reading validation. GazePrompt(2024),
https://arxiv.org/html/2402.12772 sections4.1-4.2, compared line-switching times/
errors and word-recognition outcomes plus subjective experience in13low-vision
desktop reading-aloud participants; precedent for independent task outcomes,
not transferred NewsMead accuracy. Bounded MD-source review found gaze-processing
uses, not validation of our exact R/D/F or rawXY MD as reading effort. Mishra et
al.(2017), https://ojs.aaai.org/index.php/AAAI/article/download/11159/11018,
evaluated a different learned scanpath-complexity measure against readers' ratings
in16EyeLink participants; does not validate our exact features. Select the
outcome and comparison before freezing hypotheses, sample size and analysis.
An MD reference baseline does not replace an aids-off comparison condition.
No manuscript/app/protocol changes are authorized by this clarification alone.
The owner then asks for established reading performance metrics. See
[reading-outcome evidence shortlist](reading-outcome-measures-evidence-2026-11-08.md)
for comprehension, passage speed, oral errors, line switching, resumption lag and
MNREAD, including actual reliability findings and transfer limits. No metric is
selected. Existing visit spans do not establish completion/reading speed, and no
independent task outcome is retroactively created by the gaze logs.

**2026-11-06 latest run EDA completed at the user's request:**
User completed engineering-01/shared-baseline-check and designates the latest
run, keeping the earlier bug-interrupted attempt separate. Copied only its
finalized shared baseline and article packages over USB; originals retained.
Private evidence is under `output/study-shared-live-check-2026-11-06-01/`.
User initially requested learning-paced EDA, then moved teaching to another chat
and explicitly authorized this chat to complete EDA because of time limits.
Executed notebook, seven figures, compact baseline/article CSVs, report and
provenance are in `output/study-shared-live-check-2026-11-06-01/eda/`.
Both sources strictly load and all129source files retain original hashes. Baseline
72.805s, article198.006s (193.568s collector-allowed, not verified attentive
reading);33.055s gap outside both. Article coordinate first-to-last cadence25.876/s,
median delivery age185.55ms/p95220.00ms, not measured camera/screen FPS. User
reports baseline appeared ready and no observed article lag/issues; no FPS recalled.
R/D/F baseline READY75/145retained; article254AVAILABLE/138INVALID_WINDOW ticks,
quality254VALID/92MISSING/46INSUFFICIENT. Of5143RDFinputs,952excluded;656are
geometry_unsettled. Aid CHOSEN rows1598CURRENT_LINE/68FOCUS_REGION/3READING_BOOKMARK/
1WORD_DEFINITION are updates, not independent help episodes; one lookup offer
and acceptance. R/D/F MD median2.277, not a validated difficulty label.
Coordinate source links match, but full-source fit returns
REFERENCE_MISMATCH:multiple_reference_contexts (22baseline/15article contexts;
only viewport varies in the structural signature). Independent unchanged-core
coverage diagnostic finds largest single-context eligible coverage50.459s, below
60s; do not lower gates, pool/relabel contexts or select a favorable source subset.
All coordinate scores stay null.12notebook code cells execute with zero errors;
tracked notebook unchanged. Independent metric/denominator review fixed cache
lineage checks; final source and plots reviewed before handoff. No inference,
app behavior/protocol change, device operation or upload. Proposed next work is
stable compatible baseline viewport and coordinate-specific readiness, then
geometry exclusions and predefined aid exposure/outcomes before participant tests.

**2026-11-06 shared-baseline entry crash fixed and verified on phone:**
User reported Reading baseline force-close. Device crash stack identified
HomeFragment navigating articleFragment in MainActivity's nav_graph, while news
reading belongs to ArticleActivity's separate nav_article_graph. Home now uses
the existing article Activity action; ArticleActivity explicitly forwards shared
mode into ArticleFragment. Earlier build/JVM tests did not exercise this route.
Updated main/test APKs built and installed data-preserving on SM-A566B/SDK36.
Real phone regression passes Home entry, mode/passage/Start UI, Activity
recreation, repeated entry, no-run Start guard, toolbar Back after a swipe and
system Back. First test-only Back assertion assumed the collapsed toolbar was
visible; corrected to reveal it with a real swipe, then retest passed. No camera
or study run started. Saved calibration hash and inventory of11study packages
match before/after; installed APK hash matches local tested build.
Evidence `output/shared-baseline-crash-2026-11-06/`;
[fix and phone checks](shared-baseline-navigation-fix-2026-11-06.md).
Actual gaze capture, reference fitting and endurance still need the user's fresh
reading rehearsal. Source fix is not committed automatically. Unrelated local
edits and private diagnostics remain excluded.

**2026-11-06 shared baseline implemented; live rehearsal pending:**
Home Reading baseline now opens the existing passage in the actual ArticleFragment
layout through SharedBaselineReader. Explicit Start only: no normal article
network/usage/aid/dot/TTS path. The existing R/D/F collector fits on Finish;
raw-coordinate reference is independently recorded as RECORDED_PENDING_OFFLINE_FIT.
New phase reading_baseline declares shared_reading_baseline_v1 and a completed
result; interruptions cannot activate a source. Later compatible readings retain
exact independent source links; selected export includes the coordinate source
even when R/D/F failed, without duplicating a shared dependency. New structural
article_reading_surface_v1 identity retains fixed viewport/font/geometry checks;
full actual clipping/color/aid metadata and all boundaries remain recorded.
Shared baseline/linked article provider age is650ms, coordinate analysis remains
500ms. Legacy settings/packages are preserved; no MD formula/gate changed.
Strict importer1.3 and notebook1.1 resolve/freeze the declared source offline and
retain point/window statuses, nulls/zero, both availabilities and provenance.
Source review found missing evaluation calibration metadata; fixed through the
tested segment metadata builder. Follow-up review has no actionable findings.
Debug + test APKs build;39focused JVM tests and56Python tests pass. Writer/catalog
host suite25passes; actual Kotlin writer/producer metadata→Python synthetic65s
reference fitsREADY with MD2≈0/2/4 and mean≈2 despite unavailableRDF. Updated
notebook executes22sourcecells+QA on legacy recordings without errors; absent
coordinate reference stays unavailable/null. Boundary scans are cached once per
analysis with unchanged-result regression. No device install/camera/new capture.
Evidence `output/shared-baseline-2026-11-06/`;
[workflow and contract](shared-reading-baseline-v1.md). Next: checkpoint source,
then separately authorized phone update and fresh shared baseline→later article→
verified export/EDA. Live layout, performance and real coordinate fitting remain
unverified. Unrelated local framework/scope edits and private artifacts excluded.

**2026-11-06 groupmate fixes integrated; shared baseline is the next task:**
Checkpoint `71831c7` was already committed and pushed by the user. Merged
`origin/feature/scanpath-mahalanobis-score` through `44d27fa`: configurable
650 ms output age for the existing R/D/F baseline, plus optional article/Firebase
state error handling. Retained study callbacks and asynchronous terminal capture;
added effective `output_max_age_ms` to provider metadata/acquisition identity.
Article callers and coordinate-MD analysis retain their 500 ms defaults; no metric
formula, coverage gate or automatic reference-compatibility override changed.
Offline debug build and 38 focused JVM tests pass; independent source review has
no actionable findings. No install, camera, Firebase test or new collection ran.
Evidence: `output/rdf-merge-2026-11-06/`; details in
[R/D/F integration checkpoint](study-rdf-integration-2026-11-06.md).
User now prioritizes the joint R/D/F/coordinate baseline before another article
run, superseding the earlier deferral. That workflow is not implemented by this
merge. Reuse a compatible article reading surface and acquisition configuration,
retain raw coordinates and separate profile statuses/source links, and do not
relabel legacy `rdf_baseline` packages as compatible coordinate references.

**2026-11-04 aid-count/timing capability clarified from existing records:**
User asks whether aid triggers/times can be linked to readings. Yes: canonical
aid_decision + separate trace/render/presentation/lookup/consultation records
already retain scopedIDs, reasons/rules and sharedelapsed timestamps. Notebook
Section7 currently groups native decisionrecords and showspopup timeline; no
intervention-episodecounting orbefore/aftercorrelationtableimplemented yet.
Actualprimary1,068CURRENT_LINE/188FOCUS_REGION CHOSEN include replacements/updates,
not1,256independenthelp episodes. Laterpopup1WORD_DEFINITION CHOSEN/1shown/1accepted.
Don'tsum aid_decision withruntime_tracecopy orrender withpresentationcopy. Need
explicitcountdefinition forcontinuousguide/focus andproxies/censoredduration;
rollingRDFwindows canstraddleaidonset. Associationcanreflecttriggeringgaze, not
alonebenefit. Clarified notebookREADME; noAndroid/notebookcode/devicechanges.

**2026-11-04 Jupyter exploration ready; combined baseline work deferred:**
User explicitly postpones unifiedRDF/coordinate baseline integration and asks to
inspect stored data/process inJupyter. NoAndroid/device/calibration/baseline
changes. Added clean20cell `notebooks/gaze_study_exploration.ipynb`, README and
tested dependency pins; installedPython3.10already hasJupyter/pandas/Matplotlib.
Newstdlib `tools/gaze/study_notebook.py` catalogsdeclaredmetadata and strictly
loadsonepackage into compact scoped gaze/RDF/scanpath/aid/boundary/health/reference
tables without defaultcoordinate replay or fullgeometry/history blobs. Preserve
invalid/null/MD0, originalclock IDs/rawselectedseparation; protectenvelopefield
collisions, native decision.action IDs andequaltypedaliases.6focusedhelpertests
pass; actualmain+popup sessions STRICT_VERIFIED. Notebookexecuted20cells with
5plots andCSVs,277RDFticks/186AVAILABLE,3,754gazerecords.3,481directpatternrows
include2,495P13boundaries/858P12summaries; splitword/line anddo notinferbehavior
counts. Separatepopup8eventtimeline retained. Null-actionSUPPRESSED notebook
regressionpasses. Independentreview findingsfixed; noresidualsourcefindings;
leadvisuallycheckedplots. Sourcecleared; executednotebook/HTMLpreview/receipts
under `output/study-notebook-check/`, compactexports under output/study-notebook/.
Kernelstartupstalledinsandbox; approvedlocalNBClientexecutionwithworkspace
runtimefilespasses. Statistical-analysisguidanceappliedexploratorily andcited
verifiedScientificAgentSkillspaperinnbrefs; nohypothesistests/benefitclaims or
inventedcoordinateMDscores. See [notebook workflow](../notebooks/README.md).

**2026-11-03 live compressed-run audit: stable2m20, popup attempt separate:**
User completed storage-check/compressed run, designating previous article visit
as performance check; later revisit deliberately attempted lookup. User recalls
littlelag,~21or26displayedFPS uncertain. Fourfinalized packages verified: early
prebaselinearticle10.481s, RDFbaseline62.360s READY84/124retained, designated
04c0dd8a article139.904s, laterbcdd4bc7 article78.325s. Lasttwo are samearticle but
distinctreadingIDs with24.418s gap outside both, matching exactnewbaselineattempt.
Designatedvisit9.413MBstored/168.638MBlogical (94.42% reduction),3,734coordinate
callbacks~26.961/s;30s bins~26-27/s, medianlat183.25ms/p95209.22ms. All4complete,
0declaredwriterloss/unknownend/outsidecapture. This is2m20live, notfull3m, cameraFPS,
accuracy orbenefit. NativeRDF186AVAILABLE/91INVALID_WINDOW preserved. Laterlookup
offer at73.8117s, accepted75.7901s, panelproxy75.8133s; CHOSEN73.7920s followed
native1009.187mswordresidence. Pre-drawproxy19.875msafterdecision doesnotmeasure
actualscreenlatency/wordaccuracy/dictionarysuccess or explain priorqualification
delay. Reviewer confirmedmath/identities; narrowed displaytimingclaim. Nocode,
install,restart,newcapture orrecordingdeletion. Nextissues: triggerreliability,
realcoordinate-reference collection andlongerintended-session endurance. Evidence
`output/study-live-check-04/`; [live storage recheck](study-live-storage-recheck-2026-11-03.md).

**2026-11-03 lossless storage1.2 installed; actual local sharing verified:**
User authorized remaining pipeline work. New gzip worker chunks preserve every
logical JSONL byte/row with explicit gzip_jsonl_v1 + uncompressed count; stored
hash/count cover compressed bytes. Plain legacy remains supported; recovery
preserves originals, salvages prefix, stops at gzip failure and flags unknown
loss/end. Strict decoded64MiB chunk/512MiB Python analysis limits remain. Actual
stopped112.5MB article replays to6.36MB (94.35% reduction), byte-identical after
decompression. Camera-free Android180s fixture gives5,398callbacks/29.985Hz,
36,116records,0writerloss, max sampled queue3,83.31MBlogical→2.416MBstored; this
does not test camera/rendering. All8device checks pass; focusedJVM41 (40pass,
1Windowslinkskip), Python38importer+6transfer tests pass. Added received-export
verification CLI with complete inventory reconciliation and explicit coordinate
reference/evaluation check; synthetic65s default reference fits READY. No actual
coordinate reference has yet been collected/confirmed. Focused review finding on
missing whole packages fixed with regression; follow-up has no residual issues.
Actual Transfer selected button/Androidchooser/separate-UID local receiver/USB
copy verifies exactrealarticle+sourcebaseline, excludesearlierarticle, allhashes
match. FirstreceiverKotlinruntime fixture failed; Java-only fix passes, mainapp
unaffected. Receivercomponent disabled after check. Mainupdate installed without
clearing data; savedcalibration hashunchanged. ActiveRDFbaseline is process-only,
so full live scoring rehearsal needs existing baseline again. Next: user3min
article + separate short article performance/boundary rehearsal; no camera/live
capture started automatically. Evidence `output/study-storage-check-03/` and
[storage/transfer checkpoint](study-storage-transfer-2026-11-03.md). No commits.

**2026-11-03 article-duration wording corrected against actual boundaries:**
The reported88.692s is one article segment's elapsed span, not verified reading
time. Hash-verified originals show final pause at88.150599s and screen_left end
at88.692259s; the0.541660s exit tail retains3coordinate/9no_face observations,
all context_allowed=false. No earlier pause in this longer segment; no captured
timestamp outside its logical bounds. Baseline/earlier article are separate, with
7.602s/8.209s gaps outside the identified segments. Allowed context88.150599s does
not establish attention or valid gaze coverage. No app changes or new capture.
Evidence `output/study-live-check-02/boundary-audit.json`; explanation in
[live recheck and browser](study-recording-browser-2026-11-03.md).

**2026-11-03 live recheck successful; recording browser/selected export installed:**
User completed baseline+article and reports no visible issues. Three stopped
perf-check packages verify complete/no writer loss or unknown tail. BaselineREADY;
two article-reading IDs are distinct and both reference the matching same attempt.
Longer article spans88.69s,2495 coordinate outputs (~28.64/s overcapture span),
medianlatency176.17ms,114available nativeRDFticks,773drawn proxies. This supports
this device/run's performance recovery, not accuracy/benefit/longendurance.
Raw volume112.5MB (~76MB/min) remains a material pipeline concern. New catalog
groups reading segments, lists baseline/coordinate/legacy entries separately,
loads boundedprefix/tail metadata offUI, and supports selectedZIP with positively
matched sourceRDFbaseline dependencies (notcoordinate-reference inference).
Original bytes/hashinventory/latereceipts retained. Recovery path/leafalias guards
andunknown-calibration matching were fixed in independent review. Importerv1.1
retains validnative zero-spanINSUFFICIENT warmup ticks as explicit unavailable
withnullcoverage/MD andsource order; nativecore/baseline/aidalgorithms unchanged.
Fullguardedbuildpasses;34focusedJVM (33pass,1Windowslinkskip),35Pythontests,
7unique camera-free devicechecks pass after oneoldfixtureFIFOtiming fix.
Selected synthetic readingZIP includes2segments+1baseline, excludesotherreading,
andall3packages import afterUSB transfer. Browserupdate installeddata-preserving;
no morecapture requested. Source details/limits:
[live recheck and browser](study-recording-browser-2026-11-03.md).
Evidence `output/study-live-check-02/`, `summary.json`, `*-import-v1.1/`,
`browser-export/receipt.json`. Studyqualitycriteria/benefitanalysis remain deferred.

**2026-11-03 next live-test clarification: include the existing R/D/F baseline:**
User questioned skipping baseline after the update. Source confirms that a
baseline-free run can exercise recording/aid performance, but MahalanobisScorer
short-circuits to BASELINE_UNAVAILABLE and cannot verify normal scoring/reference
linkage. For the next representative pipeline check, collect the existing Reading
baseline with the same participant code first, freeze it, then read an article
for30s in the same app process. Reuse saved gaze calibration if compatible; no
calibration redo is implied. Earlier no-baseline instruction was limited FPS
triage, not full pipeline validation. No baseline algorithms or app code changed.

**2026-11-03 scope clarified: prioritize the recording pipeline:**
User defers study-level recording acceptance criteria and independent outcomes /
reading-benefit analysis. Current work should focus on recording performance,
clear article-reading boundaries and identities, separate linked baseline records,
durable local storage/recovery, and accessible recording selection/export.
Existing raw/selected observations, timestamps, missingness/loss flags, reference
identities and actual configuration must remain available for later analysis;
preserving these is collection instrumentation, not choosing study cutoffs.
Do not add new analysis thresholds, participant exclusion rules, benefit tests or
change the original R/D/F baseline algorithm under this pipeline task. Existing
measurement gates remain as implemented. No new live capture was started for
this scope discussion. Real camera/reader performance after v1.1 remains pending.

**2026-11-03 live article collector lag; v1.1 synthetic load check passed:**
User stopped testing/run: ~30FPS off, ~15FPS early recording, ~6FPS/hanging with
absent aids/dot later (user observations, no matched benchmark). Three identified
stopped engineering article packages verify complete/no declared writer loss but
total42.07MB; auditrows34.99MB, repeated fullgeometry23-68% bypackage. Source shows
UI-thread fullcontext.geometry reflection/deepcopy and repeatedfield-name regex
allocation. New StudyAuditEncoder normalizes geometry to earlier aid_audit layout
snapshots with unique layout_revision; all other nativecontext/raw fields retained.
StudyJson caches fieldnames. Collector1.1, explicit aid_geometry_ref_v1 payload;
no metric/filter/aidthreshold/downsampling/writerprotocol changes. Review fixed
controlled-scroll nativeID alias viaunique revisions and rejection/retry test.
Article view entries nowget article_reading_id; provider restarts linked bysegment
index/previoussession. Back/configuration viewrecreation = newreadingentry, scope
article_view_visit_v1. Home/setup creates no study packages. User confirms explicit
RDFbaseline remains separate/reusable; asks to preserve baseline measurement as-is.
Explained two Start controls and existing Start/fit/onPause behavior; no baseline
algorithm/lifecycle changes. Active profile remains process-only. Full guarded
APK and27 focusedJVM tests pass; data-preserving update installed. Seven device
tests pass including camera-free180s mainthread policy/audit/writer endurance:
5381ticks (~29.89/s),19missedslots,35997records,10763resolvedlayoutrefs,0writerloss.
Meanwork12.01/5.65/5.22ms perminute; no progressivedeterioration in this fixture.
Warmed fullcontext/reference encoding benchmark31x faster,96.65% smaller; excludes
writer overhead/camera/rendering and usescachedfields inbothpaths. Offline importer
accepts83.04MB endurancepackage; syntheticloadstorage27.7MB/min remainsmaterial.
`fix-summary.json`, `endurance/result.json` and `endurance/import/receipt.json`
retain results; no newlive capture. BaselineStart/Finish/passagerules/onPause were
confirmedalready present on feature/scanpath-mahalanobis-score via gitshow; native
MD/pipeline/profile corefiles have nodiff againstthatbranch. OnlyStudyRecording UI
andpersistencehooks are new. User asked provenance afterambiguouswording; clarified.
Evidence `output/study-lag-check-01/`, docs
[performance investigation](study-collector-performance-2026-11-03.md).
Actual camera/reader FPS and overhead remain unverified; do not claim ready for
participants. A bounded30s user check is next, no freshbaseline needed forFPSonly.

**2026-11-03 first live R/D/F baseline verified; article check next:**
User completed the supervised engineering baseline and reported "Baseline frozen".
Read only its identified package over USB, without restarting the app/provider;
actual importer and manifest hashes pass: 4,415 records, four chunks, complete,
no declared writer losses or unknown tail. Fit is READY, 105/140 windows retained;
these overlapping windows are not independent observations. 1,619 coordinate
callbacks span 69.297s (~23.35 coordinate outputs/s, not camera FPS or accuracy);
six blink observations and one stopped receipt. Median capture-to-delivery latency
178.33ms; terminal arrivals 1,651 / busy drops 25. This is one engineering session,
not an overhead comparison or independent tracking validation. Local numeric/text
evidence only: `output/study-live-check-01/baseline-summary.json`, original package
`729f9467-db38-4674-8fd6-9e51a881d490/`, and `baseline-import/receipt.json`.
Important identity discrepancy: study-run code is `device_check`; the frozen
baseline code is `device-check`. Preserve originals and explicitly document this;
before article reading, Stop/Start only the study run with `device-check` to match
the in-memory profile. Do not restart/reinstall or repeat calibration. Subsequent
article package should retain a matching RDF reference. No coordinate reference
has been collected yet, so coordinate MD remains unavailable as designed.

**2026-11-03 connected-phone camera-free rehearsal passed; live check next:**
On user-authorized SM-A566B/SDK36/arm64, restored the three missing ARMv7 files
from the existing hash-pinned official MNN archive using
`tools/gaze/mgazenet/restore_armv7_vendor.py`; no guard/hash/model changes.
Full debug APK, packaged-library verification and instrumentation assembly pass
with normal guards. Data-preserving installs retain the saved gaze calibration
file; this does not establish its current accuracy/compatibility. The active
R/D/F baseline remains process memory. 51 unique on-device tests pass after
correcting two obsolete preference/timestamp fixtures and a synthetic zero
delivery ID. Six synthetic USB exports/seven packages import with matching
hashes; all 77 retained records and manifests pass JSON schemas. Added scrollable
study controls and constrained debug test storage; focused review fixed failed
setup teardown and immutable per-visit late-receipt routing, with no residual
findings in the reviewed scope. No camera access, private recording inspection,
cloud transfer, data clearing or commit. Logs/hashes/receipts:
`output/study-phone-check-01/summary.json` and associated artifacts; see
[rehearsal and limits](study-device-rehearsal-2026-11-03.md).
User is available for a live check using the saved calibration. Next: explicitly
start an engineering study run, fresh R/D/F baseline and article reading; inspect
actual cadence/missingness/aid events and separate coordinate-reference coverage.
Real accuracy, collection overhead, endurance and human validity remain untested.

**Earlier 2026-11-03 local integration checkpoint; vendor blocker since resolved:**
Created `feature/local-study-collection` from coordinate-MD core commit `93a2abd`.
Added explicit Home study controls, local baseline/article provider recording,
per-consumer inputs/native Scanpath outputs, V5 support/evaluation/decision/draw
instrumentation, R/D/F baseline/window/fit/score records, context/close/loss/recovery
handling, verified ZIP transfer and offline coordinate-MD import/reference analysis.
Both MDs remain observational; coordinate core, algorithms, filters, Firebase and
frozen diagnostic/pilot formats are unchanged. Saving R/D/F research profiles does
not restore active models after restart. See [local workflow and limits](study-collection-local-v1.md).
Transport is `newsmead_study_v1`, distinct from the preserved v0.1 draft. Explicit
earlier coordinate-reference periods require actual context compatibility; no
automatic use of the different 22dp R/D/F baseline. Captures retain source clocks,
per-provider IDs, raw/selected points and capture-aligned presentation epochs;
consumer invalidity alone cannot create a coordinate presentation boundary.
Review fixes cover sequence holes, window-local context attribution, maintenance
races, aid presentation signatures and integrity-checked late close receipts.
Writer9/ importer32 synthetic checks and actual Kotlin→Python package interop pass;
final reviewed isolated Kotlin compile/333 JVM tests pass, zero failures/errors.
Typed observer wiring removes an ambiguous compiler nullability warning. Focused
review has no remaining actionable findings; full APK remains blocked as below.
Full APK guard fails on missing pinned `armeabi-v7a` vendor libraries; isolated
JVM checks exclude only that unavailable packaging prerequisite. Evidence is in
`output/study-collection-*`, `study-writer-v1-checks`, `study-importer-v1-checks`.
No device/install/capture/private data/cloud work or staging/commit. Phone overhead,
lifecycle/transfer rehearsal and participant-protocol decisions remain open.

**2026-11-03 checkout location correction:**
Owner requested that coordinate-MD work reside in the original checkout
`C:/Users/USER/OneDrive/Desktop/newsmead` on branch
`experiment/gaze-trajectory-mahalanobis`. The earlier separate-worktree path
is obsolete. Existing original planning edits were preserved; no staging or commit.

**2026-11-02 coordinate-MD core implemented; integration pending:**
Owner approved proceeding with descriptive direct-coordinate MD. Added pure
Python `tools/gaze/coordinate_mahalanobis.py` and Kotlin
`gaze/trajectory/CoordinateMahalanobis.kt`, frozen separately fitted references,
point MD/MD-squared and actual-bound observed-time summaries. Weighted population
moments, sample-covariance sensitivity, scaled Cholesky, explicit stream/context/
clock/missingness/loss gates and no MD-based deletion. Separate earlier reference
input must be segmented to its declared bounds. Independent review found/fixed
invalid-source clock poisoning and out-of-bounds reference delivery state.
21 Python and 12 host Kotlin/JUnit tests passed, including shared synthetic JSON
and correlated covariance; 35 synthetic error/cadence cases have reproducible
receipt `output/coordinate-md-v1-checks/sensitivity.json`. These are software and
prescribed numerical checks, not measured phone or older-adult validity. See
[core and collection handoff](gaze-coordinate-md-core.md). Core version
`coordinate_md_v1` is experimental; duration/coverage/conditioning gates are
provisional. Study reference task and device overhead remain unresolved. No
activity/provider/aids/RDF/filter/Firebase or frozen protocol changes, private
data/device work, staging or commit. Historical plan-only entries below precede
this implementation. Other branch owns study recording and integration.

**2026-11-02 clarified objective: describe estimated gaze, not reading instability:**
Owner explicitly removes reading instability from the new MD's measurement goal
and requests research on existing practice. The rewritten
[coordinate-MD plan](gaze-trajectory-mahalanobis-plan.md) supersedes the earlier
H/V/movement direction below; that feature family is deferred. The bounded
[primary-source/method review](gaze-coordinate-md-evidence.md) distinguishes MD
calibration/filtering/classification precedents from descriptive spatial
distribution, covariance ellipses and independent accuracy measurement. Proposed
output is pointwise raw-coordinate distance from a separate frozen reference,
with transparent spatial/quality context, no cognitive labels or MD-based removal.
Self-fitted window mean MD-squared can normalize spread away: a four-point
synthetic check confirms 1.5 despite shifted/wider clouds, versus 4.5/6.0 against
a fixed reference. Receipt: `output/gaze-coordinate-md-research-2026-11-02/sanity.json`.
This is mathematical evidence, not phone/human validation. One bounded researcher
was used; no score implementation, runtime changes, private data or device work.
Earlier same-day behavior-oriented recommendations are historical alternatives.

**2026-11-02 error robustness and interpretation clarification:**
Owner's motivation for the additional MD is inaccurate gaze and avoiding
assumptions introduced by reading-event/engineered features. Direct coordinate MD
is the proposed first simple benchmark; H/V/movement remains an alternative.
Neither method automatically labels cognition; each has representation/reference
assumptions. Prioritize offset/jitter/spike/drift/gain/cadence/missingness
sensitivity before favoring a representation. Provider `coordinate` status is
not accuracy, and shared-score agreement cannot establish it. The plan preserves
numeric observations and treats reading-instability interpretation as unvalidated.
No runtime implementation or device/private-data work in this clarification.

**2026-11-02 representation clarification — decision still open:**
Owner's original additional-MD idea was direct raw numerical gaze data, rather
than the proposed H/V/movement vector. The vector in the experiment plan remains
an alternative, not an approved feature set. Lead recommends low-level spatial /
temporal descriptors as the more relevant starting hypothesis for behavior,
with direct coordinate MD as a spatial comparator; no empirical superiority or
reading-instability validity is established. Direct full-trajectory MD would also
require an explicit alignment/dimension/missingness design. No implementation.

**2026-11-02 gaze-trajectory Mahalanobis branch — plan only:**
Owner explicitly assigns this chat the separate trajectory experiment before
collection integration. Branch `experiment/gaze-trajectory-mahalanobis` starts
at `3119cb9c6ccae4cbb7dcd7a45c1b1f56f2b1f535` in its own managed worktree.
[Implementation plan](gaze-trajectory-mahalanobis-plan.md) proposes a small
H/V/spatial-movement feature family without fixation/regression/dwell detectors,
separate pre-smoothing/selected variants, actual shared window bounds,
reference-only fitting and matched-estimator sensitivity analysis. Numerical gates
are exploratory; no feature set or threshold is validated. Native R/D/F and all
aid decisions remain unchanged; both distances are observational. Fair comparison
requires explicit context/coverage/reference choices, not equal score magnitude.
Pure Python reference and Kotlin core are proposed new-file work; the other
workstream owns collection, existing activity integration and local export.
The original checkout's uncommitted planning/framework edits were preserved there,
not copied into this worktree. No score implementation, tests/device collection,
private recording access, cloud change, staging or commit in this planning turn.

**2026-11-02 direct source trace and local-only collection direction — design only:**
Owner confirms initial study records stay local and are transferred afterward.
[Pipeline source map and implementation seams](study-data-pipeline-source-map.md)
traces source at `3119cb9c6ccae4cbb7dcd7a45c1b1f56f2b1f535`, including camera /
provider, separate Scanpath/V5/MD paths, baseline, existing logs/export, lifecycle
and offline tools. V5 is not a P01–P13-to-aid controller. Diagnostic recording
changes aid/display/camera conditions and cannot be repurposed unchanged.
Baseline uses 22dp/default camera policy versus adjustable sp/stable-FPS request
in normal articles; current profile identity binds calibration, not typography.
Usage-log elapsed time is session-relative; gaze elapsed is since boot. Provider
terminal counters arrive asynchronously after logical reading close; the proposed
collector must capture their old epoch independently and finalize after drain.
Saving a research baseline artifact must not silently restore an active profile.
Read and consulted **Review landed gaze metrics proposal (3)**: selected-stream
consistency was accepted, while full collector details and final study dot setting
remain proposed/open. No runtime/schema/device/Firebase changes or repeated tests.

**2026-11-02 study-readiness review — planning only:**
Current branch is `feature/scanpath-mahalanobis-score`; the 2026-11-01 source
adds a separate calibration-bound, in-memory reading baseline and observational
R/D/F Mahalanobis score (see `progress-notes.md`). Owner confirms older adults
and wants both feasibility/acceptability and reading-benefit evidence; device
route, numbers and duration remain open. MD must remain observational and must
not influence aids. Owner also wants an exploratory gaze-trajectory measure that
can reveal behavior missed by R/D/F; no second feature set/formula is adopted.
[Readiness review and implementation plan](study-readiness-review-2026-11-02.md)
records current source, evidence limits, proposed supervised on/off comparison,
independent outcomes and local-recorder/export-first work packages. Earlier
feasibility-only scope and v0.1 baseline deferral are historical where conflicting.
Ordinary app usage logging already exists, but durable joint study collection,
baseline/MD export, package recovery and a production importer are still missing.
Scanpath's 125ms gap differs from V5 support's 250ms gap; do not infer a universal
aid failure threshold from FPS. MD endpoint-span coverage is not valid-time union;
overlapping windows and regularized covariance do not validate instability.
The 34-record draft checker and 39+13 focused Python tests pass. One bounded
engineer source audit was reused; no app/schema behavior, device, camera, Firebase,
staging or commit change. The review does not establish human benefit/readiness.
**2026-11-03 reading-baseline valid-window regression fixed in software:**
The current scanpath/Mahalanobis branch had removed the baseline-specific 650 ms
MGazeNet output-age allowance restored on `measurement/mahalanobis`, leaving the
500 ms provider default. Slow but bounded reading coordinates could therefore be
classified stale before reaching the feature pipeline: feature windows still
increased the denominator while no window became usable. Restored the configurable
output-age gate through `MgazeNetOutputGate`, `MgazeNetLiveOutput` and
`MgazeNetGazeProvider`, and applied 650 ms only in `ReadingBaselineActivity` via
`StudyConfig.READING_MAX_OUTPUT_AGE_MS`. The newer stable-frame-rate camera policy,
blink recovery, capture timestamps, feature denominators and validity/coverage
thresholds are unchanged. Focused output/live-output plus reading-feature tests
passed; independent source review found no actionable issue. This establishes the
software path, not live-device recovery or coordinate accuracy; no device/camera
operation was performed.

**2026-10-27 proposed thesis-scope and Firebase Spark update — design only:**
Added [thesis-project-scope.md](thesis-project-scope.md) from the prior scope
discussion and the user's report of access to the existing Firebase project on
Spark. The working study is a staged, descriptive feasibility/acceptability
study on participants' own compatible Android phones, with supervised setup and
reading first and any home phase conditional on readiness. It does not test
reading efficacy or validate gaze-derived difficulty labels. The initial
collection route is local session packages plus researcher-controlled export;
cloud sync is deferred. Spark supports Firestore within shared project quotas
but does not provide Cloud Storage. The package schema, IDs and hashes remain
backend-neutral so a later adapter can be added without rewriting analytical
records. Added a proposed researcher-export and local analysis workflow to
Section 6 of the framework: validate original packages, derive versioned tables,
and use local Python/Jupyter with optional DuckDB SQL; Colab is deferred for raw
records unless institutionally approved. Updated the Firebase mapping in
[study-data-framework.md](study-data-framework.md) and linked the new scope from
[thesis-memory.md](thesis-memory.md). No Firebase settings, live uploads,
collector, participant protocol or app code were changed; participant count,
duration, eligibility, retention and readiness criteria remain open.

**2026-10-22 proposed feasibility collection framework — design only:**
Added [study-data-framework.md](study-data-framework.md), draft record/manifest
schemas and a 34-record synthetic example in `docs/study-data/`. It maps current
V5 observations, support revisions, evaluations including gating/nonactivation,
decisions, presentation episodes, controls, losses and local package recovery.
Keep descriptive P01–P13 outputs versioned/exploratory; a complete catalogue
rewrite is not a prerequisite for this proposed collection phase. Recovery
metadata separates original/recovery clock domains and permits an unknown end.
`python tools/gaze/check_study_data_example.py` passes schema/example consistency,
manifest byte/hash/count checks and six deliberately corrupted variants; these
are fixture checks, not runtime/human validation. No live collector, Firebase
connection, phone collection or participant-protocol change was implemented.
Own-phone/self-selected home use is proposed; duration, deployment settings,
retention and participant-readiness decisions remain open. Existing app edits
and the earlier measurement protocols are preserved.

**2026-10-21 scanpath explanation deck updated:**
Preserved the seven-slide template and original PPTX. Delivered
`outputs/scanpath-system/Scanpath_System_Insert_Template_Revision_2026-10-21.pptx`.
The deck follows current source and `docs/reading-aid-evidence-aligned-v5.md`:
selected calibrated/smoothed gaze, ordinary-reading guide with uniform directional
three-line placement, accepted word-lookup offers after 1,000 ms supported residence,
qualified focus routes and persistent last-supported-line bookmarks. Primary studies
appear beside claims, with full references, populations/hardware and transfer limits
in speaker notes. Included the original von der Malsburg & Vasishth (2011) Fig. 6,
journal p. 120, plus native synthetic guide/lookup previews. Exact phone thresholds
and scanpath-conditioned aid benefit remain unvalidated pilot choices. The deck
does not claim difficulty diagnosis or treat tracking loss as inattention.
Content source review found no actionable issue; all seven final slides were
rendered and visually inspected. Package/layout/font checks and reimport passed;
seven DOI hyperlinks match their labels and all seven notes are retained. This does
not establish native PowerPoint execution or human reading benefit. Private build,
source extraction, renders and receipt: `output/scanpath-deck-update-2026-10-21/`.
Final SHA-256: `32877836e79132b37e39d38cc16b9eed38d0ef130681cbcc0462409b0118d59e`.
No app/device changes or staging/commits were part of this deck task.

**2026-10-20 positional three-line band — software and A56 camera-free checks passed:**
Owner confirms the alpha fix works, but rejects stepped row opacity as distracting.
Requested directional position rather than darker/lighter rows: for supported
row L5, DOWN shows L5-L7, settled/BALANCED L4-L6, UP L3-L5. Implemented one
continuous 9% gray fill, fading only over the outer 6% of band height at each end.
No internal row fills/seams. Policy guideRange and renderer bounds share the same
row-range helper; the anchor remains the supported gaze row, not the band's center.
Two consecutive same-direction supported content-row changes establish direction;
first reversal balances. After 900ms of fresh same-row capture support following
confirmation, recenter with existing 250ms eased motion. Settling keeps prior
movement history so a later continuation can resume direction. Duplicates/ticks
cannot settle; new visits/missing segments reset the residence basis. Only actual
article-edge rows exist; do not shift the anchor to manufacture three rows.
Numerical appearance/settling settings remain UI pilot choices, not validated
reading-difficulty rules. Single-line targets/timing and other aid rules are retained.
Controls and trace/config now describe uniform fill and requested gaze slot;
version `UNIFORM_DIRECTIONAL_BAND_MOTION_V5` identifies the presentation change.
Native Replay fixtures compare 135 pixel samples including directional coverage,
uniform interior boundaries while moving, reader caps and unchanged Single line.
Synthetic previews use app cache `guide-directional-band/`. Independent source
review found no actionable issue. All 21 focused motion/policy JVM tests across
two suites passed, zero failures/errors/skips; both APKs built on the first
bounded attempt. Evidence: `output/guide-directional-band-check-01/summary.json`
and `build.log`. App SHA-256:
`50492bd5f1c82c83e6aa350fbc0d41e011114fdf16ca799917550808dba005c9`.
Owner approved the A56 update and camera-free Replay checks. Verified both APK
hashes against this checkpoint, installed with `-r -t` and preserved data and
calibration. All six native checks passed on SM-A566B: four rendering tests,
the Three-line menu selection/reopening check and the balanced demo preview.
All 135 numeric pixel checks passed. On white, every requested uniform band row
is #f4f4f4; expected first/middle/last anchor placement was verified in DOWN,
BALANCED and UP. Inspected the three light Replay preview PNGs and confirmed a
continuous band with the requested positions. Evidence additionally includes
`a56-rendering.log` and `previews/` (12 synthetic PNGs plus `samples.json`) in
the checkpoint folder. These are synthetic software-Canvas checks on the phone,
not natural-reading, camera, calibration or hardware-frame-pacing validation.
No camera start, private capture, data clearing, staging or commit was performed.

**2026-10-20 actual guide-opacity correction — numeric A56 checks passed:**
Owner confirms Single line is invisible and Three-line looks like one row even
in Replay. The shared renderer's gradient branches used the requested alpha both
in Paint.color and in shader stops. Paint alpha modulates shader alpha, so a
nominal 8% single-line guide became roughly 0.6%, and a 9% neighbor about 0.8%.
The solid three-line center kept its full 13%, matching the reported asymmetry.
Corrected `ReadingAidOverlayView.kt` to keep opaque gray gradient plateaus and
apply requested opacity once through Paint.alpha. Fading endpoints retain gray
RGB at zero alpha; caching no longer varies with requested opacity. Appearance
targets, motion and eligibility are unchanged. Runtime version:
`SINGLE_ALPHA_BACKGROUND_MOTION_V4`.
The earlier native checks asserted any nonzero pixel change, which could pass
for an almost invisible guide. New Replay native checks compare actual RGB
against independently calculated compositing (2-channel-level tolerance), across
light/dark/sepia, all three rows/directions, Single-line 8/13/19%, half entry/exit
and reader caps: 99 sampled row/case combinations. Synthetic preview PNGs and
numeric results are written only to app cache `guide-opacity/` for inspection.
Independent source review found no actionable issue. All 19 focused guide
motion/policy JVM tests across two suites passed (zero failures/errors/skips),
and both APKs built on the first bounded attempt. Continued the owner's approved
camera-free A56 rendering verification: installed app/test APKs with `-r -t`,
preserved data/calibration and ran all four native rendering tests successfully.
All 99 numeric row/case checks passed. On white, Single-line 8/13/19% plateaus
rendered #f5f5f5/#efefef/#e7e7e7; balanced Three-line 9/13/9% rendered
#f4f4f4/#efefef/#f4f4f4. Inspected the saved light Replay previews: one row and
all three rows are present at the expected strengths. These are synthetic
software-Canvas draws on the A56, not a live-gaze/hardware-frame-rate experiment.
Evidence: `output/guide-alpha-check-01/summary.json`, `build.log`,
`a56-rendering.log`, `previews/samples.json` and six synthetic preview PNGs.
App APK SHA-256: `44f9c29616a68e074ea777956276191adcea7ba1ed45c9100ba67e0cc64a6efa`.
Prior camera/header edits and all unrelated changes remain preserved. No staging,
commit, camera activity or private recording/screen capture was performed.
This concrete opacity defect does not establish the cause of the separate FPS drop.

**2026-10-20 guide rendering regression — host and camera-free A56 checks passed; live FPS unresolved:**
owner reports invisible Single line, only one apparent row in Three-line mode,
and roughly 6–10 callback FPS instead of 30 on the first/top recommended article.
Latest clarifications: guide OFF does not restore FPS; refreshing the feed/new
headline still leaves the top item affected; lag occurs while looking at the
phone and improves when looking away. These observations supersede the earlier
faint-neighbor-only hypothesis. No fixed first-article navigation bug was found.
Source showed unnecessary full-body span remove/readd per update and per-row
text copies/layout checks. Animated gray guides now use a reusable
`ReadingLineGuideBackground` Drawable: original background, one clipped guide band,
then glyphs; no animated-guide text/span mutation. It retains 250ms motion, target
opacities and caps, caches Layout/physical-row validation, reuses paint/gradients,
and cancels scheduled invalidations/restores the underlying background on removal.
Sentence/legacy text spans remain separate. Runtime sentence-range processing now
runs only for enabled selected Sentence mode, avoiding unused full-text/row work
in Line/Three-line/off modes. Version: `STABLE_SINGLE_DRAW_BACKGROUND_MOTION_V3`.
Independent source review found no actionable issue; native fixtures now cover
body/background/Layout identity reuse, one draw, invalidation callbacks, long text,
padding, parent/internal scroll, clipping, theme changes and body replacement.
First bounded build in `output/guide-renderer-regression-check-01/` built the app
but stopped compiling the test APK: fixture line155 assigned a shadowing local
`val text` instead of TextView.text. Corrected to `this.text = body.text`; this
correction is test-only. JVM tests did not execute in that first attempt. Source
review of the substantive renderer found no actionable issue. Owner approved the
retry, A56 update and three camera-free checks. **All 306 focused aid/replay JVM
tests across 31 suites passed**, zero failures/errors/skips; both APKs built.
Updated the A56 with `adb install -r -t`, without data clearing/calibration changes.
All three camera-free native rendering checks passed: one/three-row coverage,
behind-glyph drawing, movement/caps/reflow, long-body text/Layout/background reuse,
scroll/padding clipping/theme/body replacement and drawable callback cancellation.
Pixel checks use software Canvas bitmaps on the actual phone; hardware frame pacing
and live gaze throughput were not tested. Current app APK SHA-256:
`f9e6833596c2e87f5b384cb897af4bcac0998bb709453bac1ca6a807bb8bb2fe`.
Current evidence: `output/guide-renderer-regression-check-02/summary.json`,
`build.log` and `a56-rendering.log`; the first failed run is retained in check-01.
No additional retry required. No camera start, recording, calibration, staging or
commit; app data were retained by the update.
Read-only ADB found the A56 and read only existing `MGazeNetPerf` numeric messages;
no camera start, gaze/camera recording, screen capture or calibration was initiated.
Existing same-process windows differ across provider instances: face-present
localizer roughly 71–80ms/model 62–103ms in slow windows versus 24–31ms/26–30ms in
another instance; no-face windows roughly 28–31ms/0ms. These are aggregate
processing/callback-throughput observations, not UI frame times, paired article
tests, causal evidence or a proven fix. A first-article-specific cause and restored
live gaze FPS remain unresolved. Prior camera/FPS and header edits are preserved.

**2026-10-20 line-guide visibility and shared smoothing — implemented and software-verified:**
owner likes three-line motion but reports seeing only one highlighted row; asks
for clearer three-row coverage and the same smoothing on Single line. Source
inspection shows three-row painting, but previous 3–5% gray neighbors could be
hard to see. No phone clipping cause has been demonstrated. New trial targets
above/center/below: balanced 9/13/9%, down 8/13/10%, up 10/13/8%; shared constants
in `ReadingLineGuideAppearance.kt` feed policy, menu, demo and trace configuration.
Reader limits scale all rows proportionally. Direction/support rules are unchanged.
The shared renderer now includes the V5/demo Single line with the same 250ms eased
body-coordinate motion, preserving its 8→13% residence ramp and measured-only19%
search emphasis. Position and alpha interpolation must use independent clocks
so opacity updates cannot keep restarting movement. Single-line footprint stays
one row at rest; three-line footprint stays three available rows at rest.
Legacy gray actions without guide evidence, sentence mode, bookmarks and focus
retain their existing behavior. Positive receipts for both animated line modes
come from actual nonzero text-background draws. Independent source review found
no actionable issue. **306 focused aid/replay tests across 31 suites passed**,
zero failures/errors/skips; both debug APKs built. Focused whitespace check passed.
Evidence: `output/line-guide-visibility-motion-check-01/summary.json` and `build.log`.
Current app APK SHA-256:
`2fee5e9482edd815d4bdf92e05a94cd51021fa34a76293287ab742b67f666d52`.
Native bitmap fixtures have been
extended for neighbor coverage and single-line start/midpoint/arrival/caps/style
switches; they compiled but have not run on a phone. Live visibility is not yet
verified; the previous faint-neighbor explanation remains a hypothesis.
No device operations, staging or commit authorized/performed in this correction.

**2026-10-20 optional directional three-line guide — implemented and software-verified:**
owner approved an explicit three-line option beside Single line and Sentence
context. The default remains Single line; the bookmark remains one line. Shared
Article/Pilot/Replay controls select `ReadingGuideStyle.THREE_LINE`; the article
demo previews a manufactured balanced band without detector evidence.
The supported estimated row remains centered. Original gray target opacities, above/center/
below: balanced 4/8/4%, downward 3/8/5%, upward 5/8/3%. Reader limits scale all
three together; the visibility correction above supersedes those original targets.
This style does not apply the single-line 13/19% emphasis ramp.
Two consecutive same-direction distinct supported content-row changes establish
direction; the first reversal returns to balanced. Duplicate/unsettled input
does not add changes, missing segments/reflow/style changes reset history, and
viewport translation alone cannot reverse direction. Existing support extraction,
calibration and focus triggers are unchanged.
`CURRENT_THREE_LINE_GUIDE_GRAY_V1` paints behind glyphs, with one body-coordinate
band interpolated over 250ms. Repeated captures do not restart motion; retargeting
starts from the current interpolated position. Actual row metrics handle font and
spacing changes; unavailable edge neighbors are clipped. Outer edges fade, and a
moving boundary may cross part of another row. Text/UTF-16 offsets/foreground and
wrapping are preserved. Existing 150ms entry, 750ms loss grace and 300ms exit apply.
Trace separates directional presentation from internal measured/unknown uncertainty
bounds; visible three-line width is not an accuracy or coverage guarantee.
Independent source review found and resolved the CURRENT_LINE eligibility gate
omission; no remaining actionable issue. **303 focused aid/replay tests across 31
suites passed**, zero failures/errors/skips; both debug APKs built. Focused
whitespace check passed. Evidence: `output/three-line-guide-check-01/summary.json`
and `build.log`. Historical initial three-line app APK SHA-256:
`c384e9d3fe12af931d9138946ec4549c31169df4768b742723a5da7bcca9ddf1`.
New native renderer/menu/demo checks compiled but did not run on a phone at this
historical initial three-line checkpoint; the correction above supersedes targets.
No device install, camera activity, staging or commit. Prior header/camera edits
are preserved. Numerical settings and reading benefit remain unvalidated pilot
choices; supporting ruler evidence/limits are in `reading-aid-evidence-aligned-v5.md`.

**2026-10-20 supported independent focus episodes — implemented and software-verified:**
owner approved the revised proposal below. V5 now accepts one supported broad
rereading episode within 15 seconds, including intermediate first-pass rows.
It retains the existing 150ms support stage and selected calibrated/smoothed
coordinates, separates candidate-onset and first-confirmation clocks, and excludes
unsettled/missing time. ABAB requires separated anchor bands as well as the
existing font-dependent repetition count; the unknown-error fallback requires
at least three rows between anchors. Adjacent forward reread steps remain valid.
A shared, bounded qualification ledger gives initial 50% focus and permits 100%
only after a fresh complete qualified episode while the response is active.
Witnesses cannot be reused across routes or overlapping suffixes. Interruption
activation still requires three loss/return episodes within 30 seconds; escalation
requires three more fresh episodes, not one extra return. Tracking loss remains
unavailability, not a diagnosis of distraction. Font/layout changes invalidate row
evidence; refreshes preserve onset/recovery progress. Existing styles, reader caps,
context protection and supported-reading hold/fade are retained.
Trace version: `SUPPORTED_INDEPENDENT_FOCUS_EPISODES_V8`. Replay includes current
separated-anchor and adjacent-anchor negative examples and separately reports
detector demand, requested focus and actual render/withheld status.
Independent source review found no actionable issue. First bounded run built both
APKs and passed 285/287 focused tests across 29 suites. Two Replay checks failed:
a refreshed guide retains its original presentation onset while fresh evidence
advances, invalidating the old generic timestamp assertion; and the new fourth
ABAB visit ended before median transition plus fresh support could finish.
Corrections separate evidence/presentation assertions and provide sufficient
current-only Replay residence plus three independent blocks to cover all font
tiers. Tests now explicitly cover 22/18/16dp tiers and an incomplete first-block
prefix. Legacy timing remains unchanged. Independent review found no actionable
issue in those corrections. The owner approved the retry: **all 287 focused tests
across 29 suites passed**, zero failures/errors/skips, and both debug APKs built.
Current evidence: `output/focus-supported-episodes-check-02/summary.json` and
`build.log`; the earlier failed run remains in `focus-supported-episodes-check-01`.
Historical focus-task APK SHA-256:
`2f3b2342c373ebcd3319350394fbc120ad675255d4b5c29f6c1abad0e8898e8c`.
Focused whitespace check passed. No phone install, camera activity, staging or
commit. Existing article-header and camera/FPS edits are preserved. Numerical
settings remain pilot choices; software checks do not establish human benefit,
real gaze accuracy or live phone appearance.

**Earlier proposal checkpoint — superseded by implementation above:** owner requested
a concrete better design after critique. Proposed: reuse existing selected stream
and 150ms location-residence stage (not another filter or blanket300ms), with
250ms max-gap and existing missing/duplicate protections. Keep candidate onset
and confirmation clocks separate; speed is logged, not a reason to tighten a
threshold automatically. Require spatial separation for diagnostic anchors:
frontier versus earlier-return region, or ABAB's two anchors. Adjacent forward
reread steps remain allowed. Measured uncertainty is preferred; current ±1row
unknown-error fallback implies a coarse >=3row anchor separation, explicitly not
validated accuracy. Applying that to ABAB reduces its current adjacent-row
sensitivity; font-aware repetition rules remain during migration.
One coherent first-pass/broad-return/two-forward-step witness within15s proposes
initial50% focus. Existing interruption activation remains3 separate completed
unavailability/recovery episodes in30s. Higher100% is a trial response to a fresh
complete independently qualified focus episode while active, with globally
disjoint capture witnesses across routes; extending the original episode or
double-labelled evidence cannot increase strength. Stronger-dose benefit is
unvalidated. Preserve fade-text60/85% or dark40/60% target opacity, reader caps,
400ms onset and existing supported-reading recovery/fade; these are current
presentation settings, not scientific cutoffs. Keep5rows or needed reread context
clear and distinguish detection/action/visible-render states in Replay.
Independent logical review found no fatal flaw after anchor-only separation and
global witness-disjointness were specified. Evidence/limits and concrete examples
are in the revised-proposal section of `docs/reading-aid-evidence-aligned-v5.md`.
No app source changes, builds, installs or camera activity occurred during that
proposal-only review; subsequent implementation is recorded above.

**2026-10-20 preimplementation challenge of switch confirmation:** owner explicitly
requested scrutiny before implementing a replacement. No source changes, build,
device actions, staging or commit in this review. Independent adversarial review
and bounded primary-source research support retaining candidate/supported location
states, but do NOT establish adaptive speed-based confirmation as the best method.
Key issues: true brief rereads and noise can share selected coordinates; agreeing
smoothed samples are correlated and cannot establish spatial accuracy; confirmation
delays change intervals measured between confirmations; counts depend on frame
rate; existing One-Euro + median + residence stages already add delay. Capture
candidate onset and confirmation separately, preserve missingness/geometry/content
coordinates, and compare one fixed support criterion with an adaptive experimental
variant before selecting it. No blanket 300ms or replacement trigger is approved.
Rereading association is not an individual diagnosis or evidence for stronger
focus. Primary CHI2023 reading-ruler evidence supports investigating line emphasis,
not this automatic phone trigger or its strength schedule. Moving-window/trailing-
mask experiments do not validate an assistive focus window. Context and reader
controls remain essential. Details/source URLs are saved in the preimplementation
review section of `docs/reading-aid-evidence-aligned-v5.md`. Earlier follow-up
source edits below still await execution checks; no new implementation in this review.

**Earlier 2026-10-20 live-focus follow-up — historical investigation:**
owner reports no focus in a normal article on the latest Android Studio build;
Replay has not been checked on the phone. Source review identified a detector
restriction: only five consecutive supported visits could match, excluding a
normal first pass such as L4→L5→L6→L7 followed by L4→L5→L6. Current unbuilt edits
accept monotonic intermediate first-pass rows, retain a bounded 15-second history,
and coalesce same-row requalification without bridging unsettled support time.
Focus witnesses can contain more than five visits. Protected passage bounds use
the representative visited rows before adding neighbouring context, avoiding a
second expansion of the unknown-error fallback. Replay now uses ordinary forward
passes. Regression tests are saved; independent source review found no actionable
issue, but these follow-up edits have not been built or executed yet.
Owner then suggested replacing three whole sequences with a stricter filter for
very fast individual row switches. They rejected the proposed blanket 300ms
per-location residence rule and requested a better approach that judges each
individual switch. No replacement trigger has been agreed or implemented;
current config remains three. Next design should distinguish candidate versus
confirmed line changes and treat rapid candidate reversals as ambiguous evidence,
while allowing normal first-pass intermediate rows. Do not silently implement
the rejected 300ms rule or retain three repetitions as the owner's settled choice.
Neither the cause of the live failure nor phone appearance has been established.
No device actions or private-log access. Do not repeat the scout's withdrawn
inference that ordinary blink/no-face exclusions call focus.pause: the runtime's
interpretationAllowed deliberately admits those reasons.

**Earlier 2026-10-20 stricter broad-rereading focus gate — superseded:** owner selected three complete
nonoverlapping five-visit sequences within 30,000ms. The whole interval from the
first sequence's first capture through the latest sequence's last capture must
fit. Third sequence activates at 50%; a fourth in the rolling window may reach
100%, subject to reader caps. Duplicate/relabelled or shared captures cannot add
counts. Operational pause, font change, layout invalidation and article/generation
changes discard pending counts. Existing unavailable/recovery and ABAB routes,
focus styles, supported-reading hold/fade and onset behaviour are unchanged.
Replay's broad-reread scenario now supplies three full sequences. Traces retain
all counted witnesses plus the count, required count and capture-span window.
These are pilot settings; repetition does not establish gaze accuracy or difficulty.
Independent source review found no actionable issue. All 275 focused aid/replay
tests across 28 suites passed (zero failures/errors/skips); both APKs built.
Evidence: `output/focus-reread-gate-check-02/summary.json` and `build.log`.
Current app APK SHA-256:
`2fd8af906c377c9e310c6b6abc18b1ca36f229ef9f3736e002108b07e1575ea7`.
Initial build attempt stopped at a sandbox-denied Gradle cache lock before
compilation; owner approved the successful retry with cache access. No device
install, camera activity, staging or commit. Existing header/camera work preserved.

**2026-10-20 article header scrolling:** the top action toolbar now collapses
on downward nested scrolling and returns on upward scrolling. Gaze auto-scroll
start also collapses it. Theme/font/reading-aid footer controls remain fixed;
the article viewport ends above that footer. Demo controls remain accessible.
The outer ConstraintLayout is retained for existing gaze/aid overlay parameters.
Changed `fragment_article.xml`, two header hooks in `ArticleFragment.kt`, and
added `ArticleHeaderScrollTest.kt`. Independent camera/FPS edits were preserved.
Both debug APKs built successfully; the two new attached-layout tests compiled
but were not run on a phone. XML parsing, focused whitespace checks and source
review passed. Build evidence: `output/article-header-scroll-check-01/build.log`.
Header-task APK SHA-256: `0bbad7ad7df038c363ad6f2623e9402020a4e4dd9eab2857a1c21e1fbcabd14c`.
No device install, camera activity, staging or commit for this header task.

**2026-10-19 new explicit implementation request — current direction:** user
supplied a four-aid proposal and authorized its implementation after the rollback
below. New opt-in `EVIDENCE_ALIGNED_V5` is selected by Article, Pilot and Replay.
It adds ordinary stabilized location guides (8–13% gray, measured-only 19% search
emphasis), fresh 1000ms word residence followed by an optional lookup offer,
five-visit broad rereading alongside existing focus routes, and persistent
bookmarks with supported recovery. Frozen extractor and earlier profiles remain.
Details and primary-source findings: `docs/reading-aid-evidence-aligned-v5.md`.
**Latest owner correction:** both gray line guide and blue bookmark must remain
single-line; the earlier wider display was explicitly rejected. Line mode now
paints one stabilized estimated row. V5 bookmark no longer activates on earlier
or skipped rows: outside gaze and unavailable tracking each require 500ms.
Saving its source requires agreement with the eligible line mapper; a different
unsupported coarse row withholds an unshown stale source. Persistence timers stay
1.5s minimum visible, 1s supported return or 1.5s coherent reading elsewhere.
Precise bookmark support uses its own accepted capture run/evidence IDs; rejected
mapper samples cannot backfill return credit from the longer coarse visit.
Correction verified: 271 aid/replay tests across 28 suites passed (zero failures,
errors or skips), both APK tasks succeeded, focused whitespace check passed and
independent source review found no remaining actionable issue. Results:
`output/aid-single-line-bookmark-check-01/summary.json` and `build.log`.
Historical single-line correction APK SHA-256:
`3009eeafb1e310206ed2e3dc6b9b22cacb1464bf6cfa3afa9adcebb366b00117`.
No phone install/camera capture was performed for this correction. These checks
establish software behaviour, not real gaze accuracy or human reading benefit.
No measured uncertainty radius is persisted in the current calibration artifact;
neighbouring-row uncertainty remains internal, not displayed. Pairwise
measured search emphasis stays gated; mapping-only word confidence is logged as
unvalidated. Settings remain pilot choices, not paper-validated cutoffs.
Independent source reviews found timing/scroll-clearance/zero-opacity-receipt
issues that were corrected. Initial V5 validation before the single-line correction: 267 tests across 28
aid/replay suites, zero failures/errors/skips; app and instrumentation APKs built.
Results: `output/aid-evidence-v5-check-01/summary.json` and `build.log` (ignored).
Historical initial APK SHA-256: `5a7e2010cd97ba315ceabacb3a7dd3801484756479faa74c7c5fb97c23165d21`.
Focused whitespace check passed. Tests cover software timing, duplicates/gaps,
5Hz continuity, uncertainty gating, route separation and translated protected
context; no new device/human appearance, gaze-accuracy or benefit validation.
No device actions, staging or commit in this task; independent low-light camera
work and existing documentation were preserved.

**2026-10-19 user-requested rollback — historical baseline:** user explicitly
rejected the assistant's scanpath changes and requested the older version.
Restored the pre-experiment `DEFINITION_PROFILE_V4` implementation from current
HEAD `eaa3c1ed3f20309816135eb66a46eccf14be3519`: nine aid production files and
`ReadingFocusOnsetTest.kt` match that commit byte-for-byte. ArticleFragment again
calls `definitionPrototype()`; its independent camera/frame-rate/lifecycle work
and both MGazeNet camera/provider files were preserved. Original rules, strength,
fade and bookmark release are restored, including the original 1500ms definition
threshold. Do not re-enable V1 or V2 experimental triggers without a new request.

All 14 experiment-only source/test files were backed up then removed from
`app/src`; no experiment symbols remain in active Kotlin sources. Recoverable
backup and exact before hashes: `tmp/aid-experiment-backup-2026-10-19/manifest.json`.
All ten restored files were verified against `git show HEAD:path`; no mismatch.
The old renderer has no recursive horizontal-layout check in its span callback.
Independent read-only review found no missed experiment entrypoint or unsafe
mixing in the rollback boundaries. Research notes and presentation artifacts
remain historical and do not describe the restored app behavior.

Device checks remain authorized; the user previously selected automated checks
instead of a human live-gaze session. Kept the demo-test explicit-offer fix and
corrected the old `ReadingGuideRenderingTest` fixture to find its overlay in
`replayViewport`, matching the existing activity hierarchy. These are test-only
corrections. No stage or commit, calibration changes, or app-data clear.
Validation completed: all 190 original aid unit tests passed (22 suites, no
failures/skips), and both app/test APKs built successfully. Installed via
`adb install -r` on the same Samsung Android 16 phone. Restored app APK SHA256
`2d436b2cf11e29aea0fd432c9e3ec58675a6fdeec572fe4c486b85abdf4920f2`.
All 24 focused on-phone tests passed with the phone awake: legacy guide2,
rendering8, definition-offer13, explicit demo1. First run was 23/24 with a demo
offer-layout timeout; the phone was found asleep afterward. Waking/dismissing
the non-authenticated keyguard and rerunning produced 24/24 without source
changes. Receipts: `tmp/phone-aid-rollback-tests-awake.txt`. Normal app navigation
opened a full article successfully; no new crash buffer entries. Returned from
the article after checking. Human gaze accuracy/benefit was not evaluated.

**2026-10-19 on-phone article crash fix:** user explicitly authorized phone
verification after article opening repeatedly crashed and no aids appeared.
Connected Samsung SM_A566B / Android 16 crash buffer showed four NewsMead main
thread `StackOverflowError`s, last at 13:54:16 local. Repeating chain:
`GuideBackgroundSpan.updateDrawState -> guideSpanDrawEligible ->
geometryMatchesLayout -> Layout.getLineRight -> TextLine.measure ->
updateDrawState`. `ReadingAidOverlayView.kt` now validates horizontal geometry
outside its span callback, caches the verified typography/width signature
without Layout identity, and checks offsets/heights plus that signature inside
the callback. This avoids recursive text measurement while withholding stale
width/font geometry; appearance-only Spannable conversion can recreate Layout.
Independent read-only review found no remaining actionable issue in this fix.

Built `:app:assembleDebug :app:assembleDebugAndroidTest` with the Java 17 /
in-process flags below and installed both APKs using `adb install -r` without
clearing app data. The previously installed target was last updated Oct 17.
Installed app APK SHA256:
`a3f7a49df5c45578c3cb6128afc5615d0491f5437830fa55dedb2f6909a768e2`.
Normal navigation opened two articles; the article remained resumed and no
new crash entries appeared. Phone camera permission/provider remained available;
no calibration was changed or live reading session/camera images collected.
The user selected automated phone checks instead of a human live-gaze check.

All 27 focused camera-free device tests passed: five new
`ReadingPatternGuideRenderingTest` cases using the actual V1/V2 profile IDs,
eight existing `ReadingAidRenderingTest` cases, 13 definition-offer cases, and
the explicit six-stage demo case. The new tests exercise installed spans through
measure/layout/geometry-read/draw, selected-row pixels, glyph preservation,
removal, and stale width/typography. The demo fixture was corrected to accept
the lookup offer and await its offline definition instead of assuming automatic
panel opening. Another older demo test retains that assumption and was not run;
do not claim the entire Android test suite passed. Receipts under
`tmp/phone-aid-rendering-tests-final.txt` (27 passed), with earlier diagnostic
runs retained separately. `git diff --check` passed for changed source/tests.
These checks establish Android rendering and navigation, not real gaze accuracy
or human reading benefit. No staging or commit; unrelated edits preserved.

**2026-10-19 gaze-aid usability correction:** user reports that strict line
and sentence scanpaths almost never activate, bookmark dismissal is too fast,
and focus lost its earlier routes. The live `scanpathPrototype()` now selects
`SCANPATH_NOISE_TOLERANT_V2`; `strictScanpathPrototype()` retains V1 for replay.
This supersedes the V1 trigger behavior described in the slide inserts below;
the presentation files were not revised during this app-behavior task.

The aid-local `ReadingAidCoarseTracker.kt` uses fresh selected presentation
coordinates, nearest visible body-row centers, a 600ms median window with
two-thirds agreement within one row, at least three samples/240ms supported
residence, and 250ms/three-sample anchor-change hysteresis. It accepts bounded
250ms sample intervals (including 5Hz input); missing input receives no residence
credit. A 350ms gap resets the anchor. These are pilot tolerance settings, not
measured gaze accuracy or validated reading-difficulty thresholds. Frozen
`ScanpathEngine` eligibility, classifier thresholds and word assignment were not
changed. Word definition remains 1000ms of exact word residence.

The light guide appears during ordinary supported reading over the estimated
row plus one neighboring row each side, with no horizontal start/end requirement.
Its adaptive strength starts at .25 and reaches .4 after 2s supported residence;
a confirmed backward anchor jump of at least two rows raises it to .6 for 2s
supported reading. Actual gray alpha is 20/32/48 out of 255, with 150ms entry,
750ms support grace and 300ms exit. Refreshing the same guide preserves its onset;
duplicate timer callbacks cannot renew support. Reader caps and operational
exclusions still take precedence.

Focus again permits three qualifying unavailable/return episodes in 30s and
font-scaled stable ABAB row revisits within 8s. It also permits coarse
`A -> B (at least A+2) -> A' (within one row of A) -> C (after A')` within 8s,
and retains exact sentence rereading as an additional route. V2 starts at half
strength, with 400ms presentation onset, 3s supported recovery hold and 7s fade,
and a 1.5s tracking grace. A supported coarse sample survives extractor-only
break callbacks; duplicates neither add time nor interrupt recovery. None of
these routes diagnoses confusion, distraction or fatigue.

Bookmark persistence is opt-in for V2: at least 1.5s visible, then 1s supported
saved-row return or 1.5s coherent supported reading elsewhere; a 30s age bound
and geometry/article/input invalidation remain. Visit intervals are disjoint
when the stabilized row changes, so smoothing cannot freeze an old saved row
or fabricate a skipped-line jump. Missing intervals are never dismissal credit.
Fresh inside-body points with extractor assignment exclusions preserve bookmark
progress. Same-epoch continuous raw article presence can bridge row-estimator
settling for jump arming only; actual missing/outside/invalid input or raw sample
gaps break that bridge. Extractor-only resets do not erase this independent
coarse presence, and the bridge never adds residence or release credit.
Implementation/evidence paths: `ReadingAidPolicy.kt`, `ReadingBookmarkPolicy.kt`,
`ReadingFocusPolicy.kt`, `ReadingPatternFocusPolicy.kt`, `ReadingAidModels.kt`,
`ReadingAidOverlayView.kt`, `ArticleReadingAidRuntime.kt`, `ReadingAidTrace.kt`
and `ReadingAidFocusTrace.kt` under `app/src/main/java/com/newsmead/gaze/aid/`.
Focused regressions are `ReadingNoiseTolerantProfileTest.kt`,
`ReadingAidCoarseTrackerTest.kt` and `ReadingBookmarkPersistenceTest.kt`.
Software validation and independent source review cover controller behavior,
not on-device appearance, actual tracking accuracy or reader benefit. No device
install, calibration or capture was performed; unrelated camera edits remain.
Validation: all 282 aid unit tests across 30 suites passed, with no failures or
skips; `:app:compileDebugAndroidTestKotlin` and focused `git diff --check` passed.
Gradle used Java 17 with `-Pkotlin.compiler.execution.strategy=in-process` and
`-Dorg.gradle.java.home=C:/Program Files/Java/jdk-17` to avoid the local compiler
daemon/JDK module-export failure; no build configuration change was required.
Independent read-only review checked the controller and the late disjoint-visit,
duplicate-recovery and bookmark raw-continuity fixes. No staging or commit.

**2026-10-18 template design correction:** user rejected repeating the source's
three-box layout across all five content slides. Rebuilt the seven-slide insert
as `outputs/scanpath-system/Scanpath_System_Insert_Template_Revision.pptx`.
Directly adapts original source layouts 17 (aid overview with original right
motif and circular illustrative photo), 11 (line-search image plus numbered
steps), 22 (word-residence pipeline), 6 (focus sequence with left motif), 2
(bookmark routes with top motif), and 28 for both reference slides. Retains
all four aid rules, source links, pilot strength/fade settings, detailed notes
and image provenance. No repeated three-card layout. Five content slides plus
two references, no cover. All seven slides rendered and visually compared with
the relevant source layouts. Package/layout/reference-font checks and reimport
passed, with only original decoration boundary warnings. Private builder:
`tmp/scanpath-slides/build_template_revision.mjs`; previews/receipt under
`tmp/scanpath-slides/template-revision/`. SHA256
`0c4725125d875feeb2c03424cae10389009a2e905f2ae193c69af71feec8d4b9`.
No app/protocol/device edits, staging or commit.

**2026-10-18 condensed scanpath insert:** user clarified a maximum of five
content slides, plus references, and requested photos/graphics. Supersedes the
17-slide presentation below with the insertion-ready seven-slide deck
`outputs/scanpath-system/Scanpath_System_Insert_5_Slides.pptx` (five content,
two references, no cover). Reuses source slide 8 and 28 layouts/theme. Contains
an illustrative older-adult phone-reading photo and four enlarged cue graphics,
all AI-generated, labelled as illustrative, with provenance/prompts in notes and
`outputs/scanpath-system/insert-assets/image-provenance.json`. Primary trigger
citations remain Wang2023, Hiraoka2016, Pellicer-Sanchez2016, Malsburg/Vasishth2011
and Cane2012. Five aid/system slides retain explicit algorithms and pilot
strength/fade settings, with detailed rules and population limits in notes.
Independent content review corrected a line-release overstatement: supported
start-to-nonstart progression on the same row or 3s timeout, not merely settling
directly in another row's middle. All seven previews reviewed, cue illustrations
corrected, package/layout/reference-font checks and reimport passed. Private
builder/previews/receipt: `tmp/scanpath-slides/insert/` and `build_insert.mjs`.
Final SHA256 `841292792d86c1ace45827d2d9a6f8a22df0daf5aa09622dfee02a6ecc45ea06`.
No app/protocol/device edits, staging or commit.

**2026-10-18 scanpath presentation:** user supplied the previous 28-slide PPTX
as a visual template and requested slides covering all aids, trigger evidence and
citations. Created a separate 17-slide deck at
`outputs/scanpath-system/Scanpath_System_Triggers_and_Justifications.pptx`.
Reuses source slide layouts, yellow/black/cream theme and original decoration.
Contains each aid's evidence and exact rule, strength/fading table, quality
gates, disabled coverage/regression proposal and two linked reference slides.
Primary citations: Wang2023/2024, Hiraoka2016, Pellicer-Sanchez2016,
von der Malsburg/Vasishth2011, Cane/Cauchard/Weger2012 and Frank2015.
Speaker notes retain primary URLs and population/measurement limits. Independent
content review corrected the mistaken statement that ordinary target changes
break patterns. Private build/render/validation evidence is under
`tmp/scanpath-slides/`. Seventeen slides rendered and reviewed; package/layout,
reference-font policy and final Artifact Tool reimport passed. Original-template
overflow warnings remain visually intentional. No app/protocol/device edits.

**2026-10-18 scanpath pilot implementation:** user authorized the explicit
pattern/strength/fade proposal. Live ArticleFragment now selects the separate
`SCANPATH_PATTERNS_V1` profile; legacy definition/replay profiles retain their
rules. `ReadingAidPatternDetector.kt` implements continuous 150ms region and
sentence-word visits, candidate-line search and sentence-reprocessing prefix;
ended short detours, gaps, uncertainty and layout changes break pending matches.
`ReadingSentenceResolver.kt` uses exact original UTF-16 mapped-word boundaries.
`ReadingPatternFocusPolicy.kt` replaces loss/ABAB activation in the new profile:
fixed initial strength .5 (60% background/40% dark), reader may lower, no count
escalation; 400ms onset, 3s supported-reading hold then 7s supported-reading fade,
1s missing-support grace. Statistical coverage/regression branch stays disabled
without the separate matched on-task baseline and independent validation.
Line/sentence guide now needs the line-search episode, gray alpha20/255 with
150ms entry/300ms exit after supported progression or 3s timeout. Traces retain
all qualifying reread steps and fade timing. New profile also uses the agreed
1000ms optional definition offer. These are pilot engineering rules, not
validated cognitive diagnoses, threshold optima, or human outcomes. Focused
independent review corrected same-row retrigger dedup, lowered-cap rendering,
gap-based false release, and omitted intermediate step evidence. Tests/build
validation: 227 aid JVM tests passed (zero failures/errors/skips) through
`:app:testDebugUnitTest --tests com.newsmead.gaze.aid.*`; Android debug source and
`:app:compileDebugAndroidTestKotlin` compile passed. `git diff --check` passed.
Tests include actual extractor callbacks, gaps, same-row disjoint retriggers,
uncertain mapping and recovery clocks; synthetic checks do not establish human
validity or appearance on a device. No install, capture, staging or commit.

**2026-10-18 explicit patterns/strength/fade proposal (no runtime changes):**
owner wants algorithmic sequences and actual visual values in chat. Proposed
pilot visit minimum150ms continuous supported residence (not physiological
fixation), deduplicate by content-target+region; ended short detours/invalid
mapping break matches. Line: middle->end(last20%)->two distinct starts(first20%)
among adjacent body rows{i,i+1,i+2}, within3s after line-end exit. Example
L5middle->L5end->L7start->L6start. Normal L5end->L6start->L6middle does not match.
FocusA candidate reprocessing: N>=8, early<=25%N ->late>=75%N ->early<=25%N,
then>=2 forward steps of1-2words spanning>=25%N, return/rereadphase<=8s.
Example12-wordS3:w2->w10->w2->w4->w6; no completed-path claim at prefix.
FocusB DEVELOPMENTONLY candidate: two nonoverlapping valid5s windows with
coverage<P10 and backward-transition rate>P90 of separate matched on-task
baseline. Article-wide indices; distinct reliable word transitions only;
coverage over eligible continuously observable words between window min/max
word indices. Invalid/uncertain windows reset pair; zero denominators/<2distinct
visits rejected. These arbitrary percentile cutoffs are explicitly a new
heuristic, not a literature-validated joint signature, and require independent
thought-probe checks before activation. Keep line8%actualgrayopacity; focus
defaults Fade60%theme-backgroundveil/Dark40%blackveil, with reader cap and no
automatic escalation. Proposed onset150ms line/400ms focus, line offset300ms
after resolution/max3s. Retain existing focus3s supported-readinghold+7s linear
fade; both clocks exclude missing/retained-draw time. New disjoint episodes may
restart at same opacity; frame refreshes cannot. Existing1s tracking grace then
clear; no fabricated evidence. Review tightened word-order denominators,
reread-prefix continuity and clocks. All numeric choices are pilot parameters,
not scientific cognitive thresholds or validated doses. No app/device/protocol,
collection/build/staging/commit change.

**2026-10-18 final line/focus proposal (chat deliverable; no runtime change):**
owner asks choose800/1000ms word offer and finalize both trigger proposals.
Recommendation:1,000ms supported-residence optional offer,800ms comparator;
provisional engineering choice, not a superior/validated unfamiliarity cutoff.
Line: established line-end progression -> supported visits to at least two
distinct line-start candidates in one continuous reliable transition (W23).
Two candidates operationalize extra search, not an optimized threshold. Cue
expected next row only if unambiguous; otherwise confirmed-destination
orientation. Clear after forward reading/new settled location; freshness and
uncertainty timeout remain gates. Focus has two proposed OR routes: A sentence-
beginning return plus already observed forward rereading(MV11); B a model-
development disengagement detector using probe-grounded candidate features
(Frank2015, Foulsham2013, Mézière Study2). These features do not constitute an
already validated joint signature. RouteB remains unavailable until word-level
features and independent thought labels can be measured adequately. Coverage
requires a defined traversed text segment with continuously observable words
under matched reading/scroll conditions; unseen/uncertain words are not skips.
Preserve purposeful skimming and useful rereading. Replace causal claims for
current3-loss/30s and physical-lineABAB rules. Review resolved these distinctions;
numeric sensitivities remain explicit development parameters. No code/device,
protocol, collection, build, staging or commit change by this task.

**2026-10-17 broader search / shortlist-completeness correction:** owner asks
whether iMon/AFF-Net/TinyTracker are the best options. They are verified artifact
candidates, not an exhaustive or best-performing ranking. Expanded primary-source
screening includes newer phone systems, compact encoders and screen calibration.
New public model lead: [GazeTorch](https://github.com/liszth87/GazeTorch),
[MobileNet checkpoint inventory](https://huggingface.co/7thChord/GazeTorch/tree/main),
[paper v2](https://arxiv.org/html/2603.26945v2). MobileNetV2+Coordinate Attention:
3.8M parameters, 15.7 MB pickle, 224px RGB face input, 3D gaze output. Paper
RealGaze uses 20 volunteers on a 13-inch tablet at 0.5m with lighting/accessory
conditions; MobileNet five-point calibrated mean Euclidean error is 29.7 mm
overall (24.0 ideal, 24.3 side-lit, 33.5 glasses, 33.1 masks), not a Samsung or
reading result. Public frozen weights remove a need to repeat training, but
native conversion/runtime, normalization/head-pose/camera geometry, screen
projection, exact checkpoint configuration and CC-BY-NC-ND reuse terms remain
unresolved. One-/five-point linear screen calibration does not establish that
these geometry steps are unnecessary. This reopens a bounded research lead,
not a demonstrated accuracy upgrade or a recommendation to port it immediately.
[MAC-Gaze](https://arxiv.org/html/2505.22769v3) uses MobileViT-v2 with IMU and
continual calibration/labels; its gains do not establish a public frozen Android
replacement. [COMETIC](https://pi.cs.tsinghua.edu.cn/wp-content/uploads/2025/06/3706598.3713936.pdf)
adds cursor-derived fine-tuning; [GazeSync](https://arxiv.org/html/2604.15348v1)
is iOS coordinate synchronization using a commercial SDK and logging server.
GLANCE targets an additional eye camera; EyeTheia uses iTracker-derived models
and backend personalization; neither supplies the required new standalone
phone accuracy evidence. Do not imply every relevant model has been ruled out.
MGazeNet remains the strongest established local baseline; no preferred
replacement has been established. No candidate integration/execution, model
download, branch change or device operation occurred; checkpoint updated only.

**2026-10-17 recommendation reassessment against the MGazeNet baseline:** owner
requires a defensible accuracy recommendation, recognizing MGazeNet as the best
option actually found for this project. Keep the current pretrained model and
calibration as the control. Public artifact availability is necessary but does
not itself justify a port. Earlier advice to prioritize iMon was premature:
the public CoreML MobileNetV2 file has not been tied to the study's headline
accuracy or timing configuration. Its [Swift reference](https://raw.githubusercontent.com/imonimwut/imon/main/iOS/GazeTracking/GazeTracking/ViewController.swift)
disables enhancement, calls prediction, and leaves output retrieval commented;
it does not implement the paper's fixation averaging/personal calibration or an
accuracy/FPS evaluation. That incompleteness does not prove the weights are bad.
Do not compare iMon's phone-only study result directly with the MGazeNet paper's
separately processed GazeCapture result or our calibrated target checks.
[MGazeNet study Table 2 / section 4.5](https://onlinelibrary.wiley.com/doi/full/10.1155/2024/2644725)
uses matched MediaPipe preprocessing: MGazeNet beats AFF-Net in its GazeCapture-
trained comparisons; mixed-training results trade small wins, and AFF-Net was
slower on tested mobile devices. This is architecture/study evidence, not proof
that our exact public binary beats the public AFF-Net checkpoint. TinyTracker
offers efficiency evidence, not a strong accuracy replacement case. No candidate
currently justifies a full native integration on an expectation of higher
accuracy. iMon remains a conditional checkpoint/configuration investigation.
Any subsequent comparison must give each model its own valid calibration, match
the calibration burden and independent test conditions, and examine vertical
reading error/tails, regional failures, availability and latency on both phones.
Do not promote a smooth cursor, extra filtering, paper mean error or extra
training images as evidence of improved reading accuracy. Existing failed
calibration experiments must not be re-proposed without new evidence.
The clean `experiment/gaze-model-comparison` worktree starts at `d44a305`;
no candidate was integrated or run. This reassessment only updates this checkpoint.

**2026-10-17 pinned-model provenance and Android candidate audit:** owner asks
to verify the exact base training count and search again using recovered limits.
The [README at our pinned GazeFollower revision](https://github.com/GanchengZhu/GazeFollower/blob/553920edcb7998c029828677f50f6d8eb4a16249/README.md#note)
explicitly identifies its bundled model as trained on **7 million images**.
Local `mgazenet-benchmark/vendor/assets/base.mnn` SHA-256 was recomputed as
`2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96`, matching
the manifest and app build's expected model hash; build copies this vendor asset.
Thus 7M is supported by version-linked author documentation for our actual
distribution, superseding the blanket unverified-count statement below and in
older chats. No published per-image training manifest or precise corpus
composition was recovered; do not claim an independently counted 7,000,000
unique images or equivalence to a particular smartphone-paper checkpoint.
Training-image count alone establishes neither an accuracy ceiling nor phone
inference cost.
New artifact evidence resolves earlier directory-access gaps:
- [iMon MobileNetV2 CoreML weights](https://github.com/imonimwut/imon/blob/main/iOS/GazeTracking/GazeTracking/gaze_mobilenetv2.mlmodel)
  are public (GitHub metadata 20.8 MB); an eye-enhancement CoreML model is also
  present. The [paper](https://doi.org/10.1145/3494999) reports 1.49 cm smartphone
  person-independent per-frame error, 1.11 cm with fixation averaging/calibration,
  and up to 60 FPS on iPhone 12 Pro. Those are full-study results, not verified
  performance of this specific MobileNetV2 file or either Samsung phone. Native
  Swift reference uses Apple Vision/CoreML, eye crops and optional enhancement;
  Android conversion, exact input/output parity, calibration and reuse terms
  remain open. Conditional accuracy lead; the reassessment above supersedes the
  earlier recommendation to prioritize its native port.
- [AFF-Net pretrained checkpoint](https://github.com/vigil1917/AFF-Net/blob/main/checkpoint/Iter_10_AFF-Net.pt)
  is public (7.42 MB, commit `8fb9c704000e86d5b4e26731b29acbf34a6bc8a7`,
  Add pretrained model). Availability is no longer unresolved. Native conversion,
  crop/calibration parity and reuse terms still require checking. The MGazeNet
  paper's matched comparisons do not establish a general accuracy advantage for
  AFF-Net; do not assign any paper score to this unexecuted checkpoint.
- [TinyTracker TFLite](https://github.com/pbonazzi/tinytracker/blob/main/tinytracker/models/TinyTracker.tflite)
  and TinyTrackerS files are public (GitHub metadata 611 KB / 169 KB). This is
  2D screen gaze with face-crop/localization inputs, not another generic direction
  model. [Paper Table III](https://arxiv.org/pdf/2307.07813) reports 2.62 cm for
  112px INT8 TinyTracker; embedded-board timing is not Samsung Android evidence.
  Supports an efficiency experiment, not a demonstrated accuracy upgrade; reuse
  terms and target-phone performance remain unresolved.
SAGE-SFO had no public weights verified; EyeMU artifact availability was not
resolved by the source review. Qualcomm EyeGaze supplies small mobile artifacts
but outputs pitch/yaw, leaving the old screen-mapping/target-hardware gaps.
No candidate has demonstrated better accuracy than our exact calibrated model
on both phones or older-adult reading. Only source/metadata and local hash checks
ran: no candidate weights were downloaded/executed, no app/model/branch/device
change or build occurred. Updated this checkpoint only; whitespace check passes.

**2026-10-17 model-history correction (supersedes today's generic shortlist):**
owner explicitly requests recovering older Android constraints instead of
repeating unsuitable replacements. Read actual turns from these Codex chats:
`Add telemetry ON OFF control` (`01a03222-4717-7a22-9d55-151e8aa91dae`,
MGazeNet discovery/reassessment), `Plan hybrid gaze tracker`
(`01a04582-fc00-7603-97a4-bba387f720c7`), `Verify git clone success`
(`01a045a5-14cc-79b2-a044-27d2732992f7`, MobileEye pilot), archived
`Explore gaze model fine tuning` (`01a0c304-43f9-7830-a37c-3aabdba32c33`),
and `Find mGazeNet origin thread` (`01a0c339-8781-7963-9bbb-74803af0e5cf`).
Retained constraints: standalone native Android on both A56 and SM-G991B;
no laptop/Python/WiFi inference backend; no assumption that the thesis can
fund a new labeled image corpus or substantial model training. Normal per-user
calibration is distinct from training a new image estimator. Public weights
alone do not establish a usable replacement: verify mobile execution/conversion,
localization/crops, screen mapping/calibration, complete pipeline cost, and
independent screen/reading accuracy on both phones before recommending adoption.
MGazeNet was selected because an actual small pretrained `base.mnn` and explicit
inference/calibration contract could be reproduced natively, not because all
alternatives had been disproved. See [native handoff](gaze-mgazenet-handoff.md)
and [replacement history](gaze-model-replacement.md); their older tracker status
must not override the current implementation.
The MobileEye chat reports one A56 pilot (150 frames/25 targets): CNN 5.42 cm
2D RMSE versus 5.01 cm centre baseline; this is a failed transfer in that session,
not a population result. Historical docs also record MobileGaze/L2CS and compact
landmark failures, and the laptop GazeFollower deployment rejection. Do not
repackage those as new options or confuse them with native MGazeNet.
Today's 3DGazeNet, FAZE, UniGaze and WebEyeTrack leads have not cleared the above
deployment/accuracy checks; withdraw them as immediate recommendations. This
does not establish that 3DGazeNet is impossible or that it failed a phone test.
Reopen a prior route only with concrete evidence addressing its earlier blocker.
The archived fine-tuning and origin chats had retracted the training-count claim;
the subsequent pinned-source audit above now supports the author's reported 7M
count. Exact corpus composition and smartphone-paper checkpoint identity remain
unverified. Do not infer a 7M accuracy ceiling or promise that the restricted
GazeFollower model is a compatible smartphone upgrade. No replacement has been
demonstrated better under the recovered constraints. This correction changes
only this checkpoint; no branch/model/runtime/device change was made.

**2026-10-17 quantitative trigger follow-up:** owner requests measured word
durations/probabilities and multiple focus candidates; wants concise explanation
in chat, no Markdown-file/essay deliverable. New primary anchors: Pellicer-Sánchez
2016 [Table4,p114](https://www.cambridge.org/core/services/aop-cambridge-core/content/view/S0272263115000224),
L2 first encounter invented/known controls: gaze421.83/293.82ms, total703.16/369.87ms.
These are group means, not inverse probabilities; individual fixations>800ms were
excluded. iDict2003 [author paper](https://www.researchgate.net/publication/221054255_Proactive_Response_to_Eye_Movements)
used a variable typical800ms accumulated-word trigger, unvalidated and partly
intentional gaze control. Hiraoka2016 Table4 personalized precision.507/recall.616,
10-fold CV, not a duration-probability curve. Newer [EyeLingo2025](https://arxiv.org/html/2502.10378v1)
uses gaze+text and post-reading labels; Table5 cross-user precision52.8%,recall68.4%,
F159.6%; cross-document precision43.3%,recall62.8%,F151.1%. Model-score cutoffs are
tuned, not universal ms limits; no probability calibration established. Extended
3s gaze windows include following context; preserve latency/causality caveat.
Focus must not be restricted to sentence reprocessing: investigate probe-labeled
reduced word coverage(Frank2015), altered lexical sensitivity/irregular reading
(Reichle2010/Foulsham2013), and skipped-long-word repair reductions
([Mézière et al.,Study2](https://doi.org/10.3758/s13421-025-01797-8),56 young Finnish readers,
1000Hz, exploratory). Distinct features can index the same disengagement process;
do not invent separate diagnoses or equate any one event with a lapse. No app,
device, protocol, collection, build, staging or commit change.

**2026-10-17 trigger-first correction:** owner again states trigger justification
must dominate the answer; prior consolidation still mixed in aid efficacy.
Lead acknowledged the repeated mismatch. Added a trigger-only evidence table at
the start of the [design review](gaze-aid-design-recommendations-2026-10-17.md).
Present observable predicate -> independent label/manipulation -> source finding
-> permitted inference before any design discussion. W23 provides observational
line-search evidence, H16 reader-marked vocabulary labels, MV11 controlled
ambiguity, Cane2012 known interruptions; each has transfer/diagnostic limits.
Current focus loss/ABAB rules remain unsupported as attention classifiers. S13
later returns indicate ambiguity-related effort, not unknown-word status. Do not
substitute aid benefit or a newly named process for validation of existing rules.
No runtime/device/protocol changes by this correction.

**2026-10-17 consolidated aid designs (research/design only):** owner requests
refined justifications/designs for the existing four aids and additional visual
candidates. [Consolidated recommendations](gaze-aid-design-recommendations-2026-10-17.md)
supersede earlier provisional shortlists: candidate-line search -> localized guide;
word hesitation/later return -> optional lexical offer; sentence reprocessing ->
context-preserving band; departure/return -> resumption cue. New candidates:
word/phrase lens, mapped text-chart outlines, structure-return trail, later
exploratory spacing adjustment. Each specifies observed order, evidence bridge,
appearance/release and validation gap; current thresholds are not literature-
validated constants. Source scout confirms V4 rules unchanged despite unrelated
camera/scroll edits. Focused review corrected accepted-lens persistence across
source/lens gaze; three reviewer paper fetches failed, so earlier source extracts
remain the basis for those claims. Documentation whitespace checks pass. No
runtime/protocol/device/build/collection/staging/commit change by this task.

**2026-10-17 low-light FPS source review (experimental change, build blocked):**
owner reports tracking FPS dropping in dim light and requests current GazeFollower
source/docs. Upstream requests 640×480/30 FPS but has no verified low-light
exposure fix; multiprocessing addresses desktop UI/GIL isolation. Current upstream
Tasks IMAGE/fallback/calibration defaults differ from our pinned integration.
Lighting warnings/papers do not validate a dim-light FPS threshold. Live article
now requests [30,30] only when the selected front camera advertises it; other
callers default off. Review caught shared natural-reading recording: source-close
handoff now restores original camera defaults before log creation and live policy
afterward, guarded across pause/view changes. Follow-up source review has no
remaining findings; diff whitespace check passes. One offline Gradle attempt was
blocked before compilation by denied wrapper-cache lock access; no build/tests
passed and no retry/device/camera/calibration/collection/staging/commit ran.
Actual throughput, dim-light quality, calibration transfer and handoff runtime
remain unmeasured. [Sources, changes and limits](gaze-low-light-fps-review-2026-10-17.md);
evidence: `output/low-light-fps-check-01/`. Existing scroll/research edits preserved.

**2026-10-17 V3 ramp widened to30–60% (software/device checks passed):** owner keeps start at30%
and moves full speed from50% to60%, making the50% target4% physical-screen
height/s (two-thirds of the unchanged6%/s maximum). This is a parameter change on
`measurement/scanpath-pilot`, not the separate feature-branch redesign. Host
mapping expectations and attached35/50/60% speed phases updated. All16 policy tests
pass; both APKs build. Owner reauthorized A56 USB debugging after the unauthorized
preflight. Continuing approved replace installs preserve data/calibration;1/1
camera-free native check passes in4.879 s, including50% slower than60%, upper stop
and fresh restart. Evidence: `output/auto-scroll-region-30-60-check-01/summary.json`.
Human comfort/accuracy and other test suites were not evaluated in this adjustment.
Pre-existing research notes and edits are preserved; no staging or commit.

**2026-10-16 supplied Gemini response audited (research only):** owner supplied
the full response and11 cited URLs. Added claim/source audit to the
[trigger rationale](scanpath-trigger-justification-2026-10-16.md). Useful direct
precedents: Lallé et al. chart outlines triggered by reading pre-mapped reference
text; Jensen et al. fading position highlights after typography changes. Neither
used the supplied erratic-switching/fatigue/departure trigger explanation.
AAAI2017 supports aggregate complexity/subjective effort, Ma2023 scanpath networks
and comprehension associations; neither supplies exact loop thresholds. MDPI2025
is industrial-poster interpretation. Frontiers/PMC ML links duplicate one review,
not four validated reading states. Keep concrete visual hypotheses, qualify
complete chains, and retain independent discoveries above the audit. UArts PDF
and restricted review remain access limits. No app/device/protocol changes.

**2026-10-16 independent pattern search (research only):** owner says the five
provided example labels are not the search boundary. Added an independently
located ordered-pattern register near the top of the
[trigger rationale](scanpath-trigger-justification-2026-10-16.md): undersweep
correction, candidate-line search, whole-sentence rereading, older-reader delayed
ambiguity rereading, topic-structure returns, second-pass text-picture integration,
scanning/reading/decision states, and anchor-search-confirm. Each row links primary
evidence for meaning and a proposed purely visual response; successful strategies
are not called deficits. Concrete additions: line-entry notch, sentence band,
structure-return markers, paired text/graphic outlines. Exact thresholds and
older-adult phone transfer remain unvalidated. Sources and retrieval limits are
retained in the register. No app/device/protocol/build/staging/commit change.

**2026-10-16 visual-form scope correction:** owner rejects the suggested context
cards/summaries as the wrong aid category. Focus on gaze-triggered perceptual
changes to existing text: magnification, contrast, arrows, underlines/rulers and
related visual presentation. Do not substitute summaries, explanations or
semantic navigation features for visual assistance. Preserve the central
pattern -> evidenced process/problem -> matched visual-response requirement.
Earlier additional-candidate recommendations below are superseded in scope;
their primary evidence is retained, not accepted as a feature shortlist.

**2026-10-16 additional trigger–aid candidates (research only):** owner asks for
other scanpaths/reading observations and visual forms. Added an extension to
the [trigger rationale](scanpath-trigger-justification-2026-10-16.md): referential
reprocessing -> paired context/reference cue; visual-quality-related effort ->
gaze-selected high-contrast lens offer; sparse text–graphic integration -> paired
signaling; overlooked gloss -> visual callout; skimming profile -> optional
section map. Source-tested relationships are separated from proposed rules.
Scheiter2019 adaptive multimedia had null/moderated/adverse subgroup results;
Révész2023 increased attention without superior word recognition. Reference
problems do not necessarily cause a literal gaze return to the antecedent.
Gaze patterns alone do not distinguish visual from lexical effort; no automatic
size/contrast/spacing rule or clinical inference is established. No runtime,
device/camera/collection/build, staging or commit change.

**2026-10-16 trigger-justification clarification (research only):** owner stresses
that the central project task is defending **scanpath -> independently evidenced
problem/process -> specifically matched visual response**, not generic cue
efficacy or replacing adaptation with an always-on/manual mode. New primary
evidence: W23 candidate-line searching/line-start confirmation motivates a
transition-local line-search trigger; Hiraoka2016 unknown-word labels and
Stites/Federmeier/Stine-Morrow2013 older-reader later ambiguity rereading motivate
lexical hesitation/return candidates. Whole-sentence reanalysis paths(MV11) and
probe-linked attention patterns(Frank2015/Bixler2016) are distinct possible focus
targets, neither equivalent to current physical-line ABAB/unavailability counts.
Known departure/re-entry remains the bookmark chain. Relationship evidence can
justify a hypothesis without a universal diagnostic cutoff; online thresholds
and matched aid benefit remain separate empirical questions. The corrected
[trigger rationale](scanpath-trigger-justification-2026-10-16.md) takes priority
over the earlier general aid-review recommendations for this question. No app,
protocol, device/camera/collection/build, staging or commit change.

**2026-10-16 visual-aid rule research (design review only):** owner asks for
study-defensible rules for the four current aids, especially line highlighting,
and other gaze-based aids; confirms older adults are primary while accepting
broader-population evidence. Clean source review confirms current V4 guide is
supported-line following at fixed adaptive strength, not a long-residence trigger;
sentence context already exists. Literature supports navigation/resumption
precedents, not exact NewsMead phone thresholds or difficulty doses. SwitchBack
adds a direct front-camera phone/news resumption precedent, with younger sample,
landscape/sequential-reading assumptions and marginal overall interaction before
the significant distraction-subset comparison. Current focus counts reported
unavailability/recovery, including technical failures, rather than all tracked
look-aways. ABAB/font-tier/3-in-30s rules remain exploratory. Recommendations:
validate line guidance/bookmark first, preserve opt-in word offers, compare a
gaze-following context spotlight and independently test adaptive focus. V26's
18–75 age range is eligibility; reported sample bins only extend through40–60,
so it is not older-adult validation. Primary URLs, current rules, alternatives,
proposed checks and transfer limits: [dated review](gaze-visual-aid-rule-review-2026-10-16.md).
No runtime/protocol change, build/device/camera/collection, staging or commit.

**2026-10-16 measurement V3 region adjustment (software/device checks passed):** owner requests
return to `measurement/scanpath-pilot` and explicitly selects the original
downward-scroll mapping: start below 30%, reach maximum speed at 50% physical
screen height. Only those defaults change; existing response/freshness/control
and moving-text aid behavior remains V3. Tests and current contract updated.
The feature branch had twelve staged files; they were preserved with staging in
the named stash `feature/gaze-auto-scroll preserved before measurement 30-50
region adjustment 2026-10-16` before switching. Stash SHA and patch backups:
`output/auto-scroll-region-30-50-check-01/`. Stash SHA:
`5bad148d6b5913d9568b8f23009552c98367290e`; restore staging with `git stash apply
--index <SHA>` only after returning to the feature branch with a clean worktree.
The separate bidirectional/movement design is not applied here. All 16 focused
scroll-policy checks pass (zero failures/errors/skips); both APKs build. A short-
viewport fixture was corrected to keep its full-speed-unreachable premise, then
the final focused check passed. Owner-approved A56 replace installs preserve app
data/calibration; 1/1 attached scroll check passes in 4.019 s using synthetic
80 ms lag inputs: 35% movement, 50% maximum, upper 25% stop and fresh restart.
Other native cases were not rerun; actual reading accuracy/comfort is unchecked.
Evidence: `output/auto-scroll-region-30-50-check-01/summary.json`. No camera,
human collection, staging or commit ran for this adjustment.

**2026-10-16 gaze-scroll direction research (design pending):** owner explicitly
requests gaze down → scroll down, gaze up → scroll up, rejecting manual speed
control and calibration-specific design. Primary-source review found a direct
bidirectional steady-speed precedent in Käki et al.2014, plus mobile gesture/dwell
comparisons in DynamicRead2023. Stanford2007 reading auto-scroll was intentionally
forward-only; it does not establish bidirectional control or our old thresholds.
Actual reading gaze before manual scrolling varies across people/regions
(Turner et al.2015). Intent confirmation and phone-specific timings remain open.
A provisional policy/test draft is saved outside active source in
`output/gaze-scroll-direction-research/directional-policy-draft.patch`; its two
initially clean source files were restored so the app retains the verified V3
controller and existing moving-text aid edits. No new build/device attempt.
[Evidence and exact source locations](gaze-scroll-interaction-evidence.md).
Follow-up: owner supplied GazeScroll (officially bidirectional, undisclosed
controller). OptiKey Look-to-scroll is a detailed public reference for a chosen
anchor, stationary deadzone, two-way control and optional zero distance
acceleration. Android Deep-SkyLabs source exists but uses discrete actions and
has simulator/configuration caveats. SeeSo UIAutoScroll source is forward-only.
No external program/camera/install or source import ran; findings appended to
the evidence note. No new direction controller has been installed.

**2026-10-16 aids during auto-scroll (verified software checkpoint):** owner
reports aids do not trigger. Source confirms broad motion exclusion plus geometry
reset on every translation. Live-only opt-in adapter now uses capture-aligned
text-local analysis while preserving raw/display/processing screen points. Last
geometry at/before capture, max100 ms snapshot age/250 ms input age, two-second/
4096-snapshot history. Explicit self-scroll retains layout ID; manual/external
scroll or reflow clears it. Engine coordinates are local; policy/render positions
project into current display. Runtime preserves same-ID translation evidence and
logs motion start/end without clearing it. Existing guide, definition, focus and
bookmark rules remain; lookup offer/panel pauses navigation. Condition
SMOOTHED_ARTICLE_CONTENT_AIDS_V11/modeauto_scroll_content_frame_v1 labels
content-residence proxies, not physiological fixations or validated difficulty.
Pilot/Replay default adapter and classifier thresholds remain unchanged. Review
caught callback-before-preDraw external scroll retaining history; controller
prechecks and forced invalidation/newID now cover it. Follow-up source review
clean. First attempt passed 271 JVM checks/builds and 8/10 native checks, with
focus/bookmark failures retained. Source confirmed startup no-input timer advanced
bookmark capture floor past delayed first prefix; corrected to advance only actual
finite capture floors, with two passing policy regressions. Native focus fixture
uses explicit 24 dp STANDARD tier and asserts its premise; font scaling is a
possible first-failure cause, not proved from that log. Final corrections reviewed.
Owner-approved retry passes **273/273 JVM checks across 31 suites**, both APK builds
and **5/5 aid integration checks in 14.222 s**. A56 replace installs preserve app
data/calibration. Guides, lookup/navigation block, real line-cycle focus,
tracking-loss bookmark and forced external-motion invalidation pass. The old five
navigation tests passed on attempt 01, not rerun on the corrected APK. Evidence:
`output/auto-scroll-reading-aids-check-02/summary.json` plus copied XML/build/
native/install logs; attempt 01 remains intact. Camera stayed off; no human
collection. Replay-hosted attached fixtures exercise production components with
synthetic inputs; complete live Fragment routing and human validity remain unchecked.
[Contract](auto-scroll-reading-aids.md).

**2026-10-16 auto-scroll V3 continuous speed (verified software checkpoint):**
owner confirms V2 works but wants immediate, controlled Athena-like movement;
explicit choice is lower gaze speeds up, higher gaze slows/stops. V3 replaces
steps/dwell/rearm/cooldown with continuous velocity: target0 atY<=40% physical
screen, linear to6% screen height/s atY>=65%; nominal180 ms acceleration/braking,
per-frame elapsed cap50 ms. Selected calibrated/smoothed coordinates stay shared
with dot; no line/word/reading-speed inference. Freshness delivery150 ms/capture
250 ms; missing/invalid gaze hard-stops. Own translation does not reset control;
real reflow/external scroll/controls require fresh settled input. Settling rejects
do not move input floor forward, avoiding starvation of delayed camera captures.
Motion/+150 ms remains excluded from stationary scanpaths, so continuous mode
can exclude most reading time; no motion-compensated metric validation is claimed.
Review caught idle time contributing to the first lower-gaze restart frame;
acceptance now resets its baseline when zero demand/speed becomes positive, with
a10 ms regression. Source follow-up clean.202/202 JVM checks across22 suites and
both APK builds pass. Continuing owner-authorized A56 app/test updates use-r,
preserving data/calibration.5/5 camera-free attached/controller checks pass in
13.550 s: speed changes, upper braking/restart, stale/noncoordinate stops,
lookup blocking, touch pause/resume, tail and menu scope. The80 ms capture lag
fixtures verify first actual movement within400 ms and multiple scroll changes.
Evidence: `output/article-auto-scroll-continuous-check-01/summary.json` plus
XML/build/native/install logs. No camera started or human gaze collected;
actual comfort/accuracy and complete live Fragment routing remain unvalidated.
Official Athena
product description and Kumar/Winograd2007 Figure4 support interaction precedent,
not exact trial settings or human outcomes. [Contract](article-auto-scroll-prototype.md).

**2026-10-16 auto-scroll V2 correction (verified software checkpoint):** owner
reports Pause button but no movement and explicitly requests gaze up/down rather
than line classification. Policy/controller now use clipped-body gaze regions:
Y>=45% screen for700 ms/min5, across changing X/Y/rows; fresh Y<=37.5% for200 ms/
min3 rearms, with existing2 s cooldown. Final gaze's text point moves toward30%
screen, guarded3% below viewport top;400 ms motion/150 ms settling remain. Row
lookup, same-row support and Layout-instance comparisons are removed. Content,
size and viewport changes still break support. The menu reports pre-open status;
phase/block reasons are journaled. A new actual attached article-layout test uses
synthetic varying-region gaze and real capture clocks, without ArticleFragment
or camera startup. Review caught an unreachable upper-rearm viewport; geometry
now requires usable upper space before moving. Both APKs build;200/201 JVM checks
pass. A new test used gaze outside its shortened candidate body and got
outside_article rather than geometry_unavailable; its gaze now uses the body's
center. Owner-approved retry passes201/201 JVM checks across22 suites and builds
both APKs. Approved A56 app/test updates use-r preserving app data/calibration;
7/7 camera-free native checks pass in3.459 s in one run, including attached actual
article layout, changing lower-region Y and400 ms animation. Same text point
reaches30% screen; motion/settle exclusion, controls, touch cancellation, missing
gaze and optional tail pass. The attached fixture uses the controller directly;
it does not start ArticleFragment, a gaze provider, camera or human collection.
Evidence: `output/article-auto-scroll-region-check-02/summary.json` and copied
XML/build/native/install logs; failed first-attempt evidence remains in01.
Actual live reading comfort and complete Fragment routing remain unverified.
This removes an unnecessary row gate; the exact
cause of the owner's live failure remains unconfirmed. [Contract](article-auto-scroll-prototype.md).

**2026-10-16 optional auto-scroll (verified software checkpoint):** owner-approved
article-only prototype adds default-off aids-menu switch and visible Pause/Resume
while enabled. Same calibrated/smoothed point; no exact-word requirement or model,
calibration, numeric smoothing or frozen Pilot/Replay changes. Trial V1 uses700 ms
same-row support/min5 captures at/below45% physical-screen height,150 ms maximum
sample gap,250 ms maximum age;400 ms step moves row center toward30% screen,
subject to toolbar/viewport/actual scroll bounds. Fresh upper-row support200 ms/
min3 and2 s cooldown precede another700 ms trigger. Touch pauses until explicit
Resume. Controls/lookup offers/definition panels/read-aloud/gaze loss/reflow/pause
interrupt pending support and motion. Enabled-only70% screen-height tail lets
last body lines reach the band. Journals mark support/request/actual motion and
start/end/cancel; scroll-depth events mark auto versus manual-or-layout origin.
Motion and captures through end+150 ms are excluded from reading patterns and
look-away evidence; actual geometry still must settle150 ms. Completed focus
summaries survive operational boundaries; pending anchors/chains do not. Review
caught control-excluded inputs feeding navigation and P13 auto boundaries erasing
focus history; both fixed with regressions, follow-up clean.202/202 focused JVM
checks and both APK builds pass. Owner-approved A56 app/test installs preserve
data/calibration. Initial native run passes3/6; three tests asserted zero-duration
animation completion within one UI callback. Owner-approved test-only retry waits
off UI for next frame/end latch;2/3 rerun checks pass,5/6 distinct native checks
now pass. Remaining check confirms scroll delta then uses an invalid detached
getLocationOnScreen position (SDK36.1 View source confirms zero without AttachInfo).
Fixture now uses descendant-to-root rectangle conversion including parent scroll;
review clean. Owner-approved final test APK rebuild/install passes remaining1/1
native check in0.060 s. All6/6 distinct native checks pass across initial run and
two approved retries, not one six-check run. App hash unchanged. Detached fixture
does not establish production window-inset behavior or human reading benefit.
No camera, recalibration, staging or commit. Upper-region advantage is intermittent, not demonstrated
accuracy improvement. Primary studies, trial rules, source paths and limitations:
[contract](article-auto-scroll-prototype.md). Evidence:
`output/article-auto-scroll-check-03/summary.json`; prior gates retained in01/02.

**2026-10-15 lower-screen vertical error (user observation; unresolved):** owner
reports good top-half vertical accuracy and worse behavior lower down. Owner
clarifies the dot still moves downward but starts to struggle past the middle;
it is not a hard stop, and this predates the blink update.
Owner subsequently reports good tracking again without another install or runtime
change; the quality issue appears intermittent and its cause remains unresolved.
Source-only inspection finds live output has no lower-quarter cutoff: normalized
SVR predictions scale to physical screen pixels, reading smoothing is independent
of position, and the article dot uses the same selected coordinates as scanpaths.
Blink gating can withhold output but does not shift coordinates. Calibration and
live prediction share camera preprocessing and features; no static scaling
mismatch was found. Regional fit quality and posture changes remain unconfirmed
possibilities, not established causes. Calibration fit targets reach90% viewport height
(`CalibrationIdentity.kt`); save-gate checks aggregate regions and cannot establish
bottom-specific accuracy (`MgazeNetCalibrationQualityGate.kt`). The existing
nine-point accuracy task samples20/50/80% viewport rows and reports signed dy per
target (`MgazeNetAccuracyProtocol.kt`, `GazeTestActivity.kt`). No recordings read,
new tests, device actions, recalibration or runtime changes for this observation.
The next diagnostic is the existing raw nine-point check using the current saved
calibration, comparing signed vertical errors and availability across its rows;
it excludes article smoothing/blink filtering and does not reproduce article
reading. Preserve this distinction when interpreting results. Do not infer a
cause or apply a vertical correction without matched evidence.

**2026-10-15 live blink filter (verified software checkpoint):** owner reports
closed eyes still move gaze vertically; old16-point source had eyelid hysteresis,
while MGazeNet only guards pixel eye areas. New live-only per-eye projected lid
gap/width guard reuses initial0.040close/0.055open tuning and requires2fresh
clearlyopen frames at startup/reopening. Article/livePilot explicitly enable it;
provider/camera flags defaultfalse, preserving calibration/accuracy raw contracts
and saved calibration. Invalid/closed frames have nullselectedpoint/no dot callback;
rawpredictions remain excluded diagnostics. Source clock/expiry precedes blinking;
invalid inputs reset smoothing. Brief blinks do not enter look-away sets or reading
time; existing focus grace holds, scanpath gap remains. Metadata/optional per-eye
ratio fields support later review without camera images. Source review caught
blink time lengthening a pending short no-face loss; the corrected focus branch
discards unqualified pending losses and preserves already qualified evidence.
Mixed-loss and repeated-blink regressions are added; follow-up review is clean.
All70/70 focused JVM checks and both APK builds pass. Owner-approved A56 app/test
updates preserve app data and calibration;2/2 camera-free native checks pass in
3.276 s, covering closure/reopening scanpath gaps and focus scroll continuity.
No camera started. Actual eyelid closure still needs the owner's live article
check; these are trial gates, not validated human blink labels. Evidence:
`output/live-blink-check-01/summary.json` and native/build logs.
[Contract](live-gaze-blink-filter.md).

**2026-10-15 font-aware focus (verified software checkpoint):** owner reports
small fonts activate focus too quickly, then clarifies to keep look-away at
3 episodes/30 s and use an 85% maximum mask. Line route uses rendered font dp:
22+ requires 1 ABAB; 18..<22 requires 2 cycles with each supported visit >=160 ms;
below18 requires 3 cycles with visits >=240 ms. Detector baseline remains100 ms.
Each cycle is four new visits/<=8 s; counts retain the existing30 s window.
Resize clears only line-cycle history/demand, including within one size tier.
Completed look-away histories remain. Fade changes90%/98% to60%/85%, all masks
capped85%; Dark40%/60%, 4–5 lines, motion/scroll, hold/recovery stay the same.
Source review's short-detour finding is fixed: an unqualified intervening
established visit breaks pending ABAB instead of being removed from the order.
Follow-up has no actionable findings. Policy16/font-ruleV1/line-ruleV3 and
appearanceV4 record current settings and actual size in traces. Values are trial
safeguards, not validated difficulty cutoffs. Initial run built both APKs and
passed84/85 JVM checks; the new fixture assumed80 ms baseline. Its reviewed
correction uses the actual unchanged100 ms minimum, sampled at120 ms on a40 ms
grid. Owner-approved retry passes85/85 JVM checks and both builds. Approved
data-preserving A56 updates succeed;5/5 camera-free native checks pass in5.007 s:
actual153/217 mask pixels, Dark onset, scroll/fresh-anchor continuity, rendered
size settings and unchanged three-episode return. Small-font activation and
look-away preservation across resizing rely on policy fixtures/source review;
natural gaze accuracy, visibility and comfort remain unvalidated. No camera,
calibration changes, staging or commit. Final evidence:
`output/focus-font-visibility-check-02/summary.json`; initial failed gate in01.
[Contract](repeated-interruption-focus-window.md).

**2026-10-15 footer appearance revision (verified software checkpoint):** owner requests gear
Aids, matching circular font controls and tighter theme spacing. All six controls
share a transparent circle/caption style: theme swatches fixed 144 dp on the left,
font/gear fixed 144 dp on the right, 8 dp outer padding and flexible center gap.
Symbols are 28 dp with 48 dp touch widths; font and gear tint follow article
colors. First build caught plain-Button binding types for font icon tinting;
explicit MaterialButton declarations correct it. Owner-approved retry passes both
APK builds and data-preserving A56 updates. The existing native footer check
passes at 320/360/400 dp with all controls visible, contained, nonoverlapping and
at least 48 dp touch dimensions. Synthetic 320/400 dp previews are visually
inspected. Test-only pre-draw refinement renders detached labels; final native
check passes in 0.513 s. No camera, calibration change, staging or commit.
Evidence: `output/article-footer-style-check-02/summary.json` and previews/logs;
failed build retained in 01.
[Contract](article-text-size-controls.md).

**2026-10-15 article font controls (verified software checkpoint):** owner asks
to restore increase/decrease and proportional line spacing. Ordinary articles now
use remembered 22 sp default, 14–60 sp bounds, symmetric 2 sp steps and system font
scaling. Footer fits themes, A−/A+ and Aids in one row. Spacing is 1.6× plus extra
px equal to rendered font px/22. Resize preserves the first visible source offset
where possible, clears old aid/lookup/demo state, gates reflow, refreshes actual
geometry and keeps the existing 150 ms settling guard. Recording disables size
controls; dedicated Validation/Pilot/Replay keep their controlled 22 dp settings.
Typography pixel metrics accompany aid trace configuration and geometry. Timers
are unchanged, not font-normalized or validated. Initial review caught pause
discarding the pending reading-position restore; the callback/gate now persist
for the same view's next draw, with destruction invalidating them. Follow-up
review has no actionable findings. First app build passed but new Android test
had a View/ViewParent property ambiguity; reviewed explicit-View correction and
owner-approved retry pass both APK builds and 10/10 focused JVM checks. Approved
data-preserving A56 app/test updates succeed; all 7/7 camera-free native checks
pass in 2.002 s, including real footer bounds at 320/360/400 dp, proportional
spacing/reflow, fresh settled geometry and stale lookup invalidation. Fragment
pause/resume is source-reviewed, not exercised by the native fixtures. No camera,
calibration changes, staging or commit. Evidence:
`output/article-text-size-check-02/summary.json`; failed build retained in 01.
[Contract](article-text-size-controls.md).

**2026-10-15 closer lookup bubble (verified software checkpoint):** owner screenshot shows the
V3 bubble too far above the source word. V4 anchors its vector tail to the word's
painted top (layout baseline plus current word ink bounds), with3dp gap, replacing
line-top/font-leading placement. Blue28dp glyph,48dp hit target, right-corner
alignment/mirroring,1,500ms rule and5s lifetime remain. Target must fit viewport
and end above painted source letters; it may occupy unused font leading. No text,
font size/spacing, detector or calibration changes. Plain-string English article
text uses Arial22dp and1.6 spacing; native regression uses those settings on a
third-line target plus actual rendered letter pixels. Source review has no
actionable findings; both APK builds and data-preserving A56 installs pass. All15
focused native checks pass in9.384s (13 offer fixtures plus accepted/ignored Replay),
including 3dp painted-letter gap, normal/mirrored alignment, no hit-area overlap,
geometry cleanup, expiry and tap-only consultation. Normal and article-spacing
synthetic previews visually inspected. Evidence:
`output/lookup-bubble-gap-check-01/summary.json`. No camera, calibration changes,
staging or commit. UI spacing choice; no human benefit/accuracy validation.
[Contract](article-definition-aid.md).


**2026-10-15 blue information bubble (verified software checkpoint):** owner rejects the box,
asks for a more noticeable bubble and a tail aimed at the word's upper-right, then
supplies a blue speech bubble with white lowercase “i”. Native28dp vector uses
blue fill/dark-blue outline/white “i”, transparent exterior, no outer tile/shadow.
The48dp target remains wholly above the source line; bottom-aligned glyph leaves
its tail about6dp above the word. Tail aligns to wordright and mirrors near the
right edge; “i” stays upright. Missing room withholds rather than shifting tail.
Runtime style `WORD_LOOKUP_INFO_BUBBLE_CORNER_V3`; policy15/conditionV10/trigger/
timer unchanged. Prior monochrome placement build and15 camera-free A56 checks
passed; that appearance is superseded. Layout delta review found no actionable
findings. Blue app/test builds and3 selected native checks pass in4.657s.
Synthetic bitmap inspection exposed missing native stroke; filled outer silhouette
with inset blue gradient corrects the border. Final app rebuild/install and both
normal/mirrored native checks pass in0.169s; previews visually inspected. Existing
test APK reused (only app vector assets changed). A56 data/calibration preserved.
Evidence: `output/lookup-bubble-corner-check-03/summary.json`; prior15-case layout
gate in01, initial blue3-case gate/previews in02.
No camera, calibration changes, staging or commit. [Contract](article-definition-aid.md).

**2026-10-15 opt-in word lookup (verified software checkpoint):** owner asks
for an above-word icon before the distracting automatic card, suggesting an outlined
speech-bubble/search glyph. Existing1,500ms word-residence rule now creates an
above-only20dp icon/48dp target with5s fixed lifetime, tap confirmation and no
query/consultation until accepted. Ignore expires; same-word repeats suppressed
until observed departure to another valid word. Geometry/control/lifecycle clears
state. Icon hit bounds exclude article interpretation; rest of reading continues.
Offer may coexist with focus; actual panel consultation keeps prior gating.
Article/Pilot/Replay/demo use `CONTEXT_WORD_LOOKUP_OFFER_V1`, policy15/conditionV10;
trace phase/event records distinguish offer and panel, preserve evidence times.
Replay reserves56dp above its body for fixture icons; real article layout unchanged.
Five seconds is trial interaction tuning, not a published validated dose.

Source review's pre-tap phase labeling finding and first build's ancestor type
ambiguity are fixed. A new JVM assertion wrongly forbade normal focus refresh;
reviewed correction preserves demand/strength/episode assertions. Approved retry
passes199/199 JVM tests and both APK builds. Approved data-preserving A56 installs
succeed;20/21 camera-free native checks pass in21.148s, including expiry, tap-only
query, headline headroom, invalidation, Replay consultation and demo. Remaining
hit-area fixture used untransformed detached getLocationOnScreen coordinates;
reviewed test-only correction now uses visible bounds, retains48dp/edge assertions.
App unchanged. Owner-approved test APK rebuild/install and corrected native
check pass in0.030s. All21 distinct native cases pass across the two invocations;
199 JVM checks and both APK builds pass. Attached Replay rendering and consultation
are covered; natural gaze accuracy and human benefit remain unverified.
Evidence: `output/definition-lookup-offer-check-04/summary.json`,03 build/native logs;
failed software gates retained in01/02 and failed native fixture in03. No camera, calibration changes, staging or
commit. [Contract](article-definition-aid.md).

**2026-10-15 softer dark focus appearance (verified software checkpoint):** owner finds
Dark surroundings too strong, clarifying that the issue is abrupt visual change,
not outside-text readability. Reduce only DARK shading from90%/98% to40%/60%
at initial50%/100% action strength, and introduce a400ms smooth onset. Fade style
stays90%/98%; action strengths/triggers/opening/scroll positions remain. Onset
must follow continuous presentation, survive action refresh/scroll handover and
reset at a real removal/style/operational boundary. It changes paint only, with
no successful draw receipt at zero alpha. Selector `FOCUS_MASK_STYLE_V3` and
per-style settings identify this cosmetic revision; policy14/conditionV9 remain.
Opacity-only13 JVM checks/builds pass. Final onset build initially failed because
the lead removed a still-required per-frame flag; restored and correction reviewed
with no remaining actionable finding. Owner-approved retry passes15 focused JVM
checks, both APK builds and data-preserving A56 installs. Both camera-free native
checks pass in9.957s: deterministic0/51/102 alpha at0/200/400ms, ID-refresh/hold/
handover continuity, removal/style resets, clear rows and actual scheduled Dark
and Fade Replay rendering. Display confirmed awake/unlocked. Evidence:
`output/focus-dark-opacity-check-03/summary.json`; failed build retained in02,
opacity-only evidence in01. No camera, calibration changes, staging or commit.
Trial interaction tuning, not an established comfort/reading-benefit threshold.

**2026-10-14 selectable focus appearance (verified software checkpoint):** owner requests a
dark surround option in Reading aids. Session-only `focusStyle` offers
`FADE_TEXT` (existing default, reader-background mask) and `DARK_SURROUNDINGS`
(black mask), both at90%/98% opacity for initial50%/100% strength. The clear
window/motion/scroll/activation rules are unchanged. Style is explicit on current
focus actions, including held presentation provenance, preferences and action
exports. Menu disables appearance choices with focus/master off while retaining
the selection. Article and Replay demo, live article and Pilot share the choice.
Policy14/conditionV9 identify the new selector; form remains V4.18 focused JVM
checks and both APK builds/installs pass.5/7 native initially pass; existing
dismissal assertion ran before its posted callback, and Replay had chosen focus
but zero render records. Device was found dozing afterward. Reviewed test-only
callback wait, owner-approved test rebuild/install and awake/unlocked-display
retry pass both checks in10.728s. All7 distinct native checks pass across both
runs. App APK unchanged on retry; data/calibration preserved. Evidence:
`output/focus-style-menu-check-02/summary.json`; initial failure/trace/XML retained
under `output/focus-style-menu-check-01/`. No camera/staging/commit. Checks do not
establish human comfort or reading benefit.

**2026-10-14 focus display revision (verified software checkpoint):** owner reports weak
gray surroundings, abrupt motion and disappearance on scroll; selects very faint
outside text rather than hiding it. Current V4 uses article-background90%/98%
opacity at50%/100% initial strength, continuous fade below50%,180ms anchor-safe
window motion and a pinned viewport presentation during translation-only scroll
plus<=1500ms after the last translation. Fresh supported focus takes over.
This presentation has separate trace provenance, no new gaze evidence/action;
scanpath breaks and geometry settling remain. Reflow/text/operational changes
clear the hold.5/4 clear rows and full width remain. Policy13/conditionsV8; demo
uses V4 but still stops on layout change. Review caught row snapping and repeated
disable redraw loops; corrections reviewed with no actionable findings.50 focused
JVM checks pass; both APKs build/install on A56 preserving data/calibration.5/6
native initially pass; the failed detached-parent fixture had no screen-coordinate
translation. Reviewed test-only TextView-offset correction, approved rebuild and
one native retry pass in0.707s. All6 distinct native checks pass across both runs.
Evidence: `output/focus-display-scroll-check-02/summary.json`; initial logs/XML
retained under `output/focus-display-scroll-check-01/`. No camera/staging/commit.
Attached live scroll/human benefit remain unverified. Trial settings, not paper doses.

**2026-10-14 wider/full-width focus (verified software checkpoint):** owner requests4–5
clear lines instead of3 and removal of unshaded side gutters. Current V3 form
prefers5 physical rows, slides at visible/article edges, and uses4 only when5
would leave no shaded surroundings. It never shrinks to3 to create shade. Fewer
available rows remain clear; empty surroundings are honestly withheld. The veil
uses the full visible overlay width and the existing text vertical extent, while
gaze/text geometry remains unchanged. Article/demo/pilot/replay select radius2,
policy12/conditionV7. Independent source review's demo-availability finding was
corrected: preview availability now uses the same appearance resolver. New unit
and native bitmap/demo regressions are saved.14 focused JVM checks and both APK
builds pass; A56 data-preserving installs succeed.3/4 camera-free native checks
pass: actual full-width/five-row pixels and both demo cases. Failed Replay check
recorded no focus because its exact complete synthetic trace ran `navigation`,
not the requested `interruption_place_search`. Test helper now waits for initial
layout/visible requested Spinner selection and asserts returned scenario identity.
Correction source review reports no actionable finding. Owner approved the test
APK rebuild/retry; it builds/installs and the corrected native check passes in
10.002s, asserting both requested scenario identities. All4 distinct camera-free
checks now pass across the initial run and approved retry; failed trace retained.
App APK and calibration/data stay preserved. Evidence/hashes/logs:
`output/focus-window-width-check-02/summary.json`, with the initial failure in
`output/focus-window-width-check-01/failed-native-replay.jsonl`.
No activation, calibration/filter, dictionary timing, frozen measurement or Git
staging/commit changes. This supersedes V2's60% context cap below.

**2026-10-14 return recognition implementation (verified software checkpoint):** owner
authorized the reviewed redesign. Current policy11/conditionV6 confirms returns
from100ms/3 fresh selected-coordinate article-presence samples, allowing movement
between lines without fixation or word assignment.3/30s/500ms and50/100% retained.
Completed summaries survive same-article geometry/definition/control pauses;
pending episodes/visits/anchors clear. Known same-geometry fully visible source
can anchor search for<=30s; fresh presence maintains grace. Aid-specific ongoing
fourth ABAB visit uses8s; finalized fast P11 remains2s and is not double counted.
V2 focus prefers3 clear lines, reduces neighbors above60% viewport height and
withholds empty shading. New Replay search/ongoing-fourth fixtures and regression
tests are saved. Independent review's expired-anchor retention finding is fixed;
correction review has no actionable findings. All **219 focused JVM tests pass**,
zero failures/errors/skips, and both APKs build. Existing authorization covered
data-preserving A56 app/test updates; both installs succeed and **5 camera-free
Android tests pass** in31.501s. Checks cover moving search and ongoing fourth
visits, third/fourth50/100%, definition consultation exclusions, geometry history
retention/fresh anchors and actual bitmap shading. Natural gaze/benefit remain
unverified. Evidence/XML/APK hashes: `output/focus-return-activation-check-01/summary.json`.
No camera/staging/commit. Trial settings are not validated
distraction thresholds. [Current contract](repeated-interruption-focus-window.md).

**2026-10-14 focus activation reconsideration (source review, no app change):**
owner still finds live focus unreliable for both look-away and line alternation,
and suggests searching for the old reading position prevents recovery recognition.
Source confirms interruption completion currently requires a supported line
candidate/fixation; mere return to the article is insufficient. This is a plausible
barrier, not a trace-established cause. Independent read-only review also confirms
same-article scroll/geometry and definition open/close erase completed history;
P11 requires four finalized alternating visits within2s; five clear lines may fill
the viewport while a zero-area shade still reports a successful draw proxy.
Fresh-support display grace and evidence-age gates remain additional barriers.

Recommended next contract: separate return/presence confirmation from line/word
assignment, so moving among lines during place search can complete an episode.
Debounce/rearm presence to avoid counting tracked blips as separate returns;
preserve missing reasons and the chosen3/30s/500ms trial settings. Activate demand
independently of whether an anchor is currently drawable. Retain completed recent
episode summaries through same-article scrolling/consultation, clearing pending
sequences and stale screen anchors; exclude those operational intervals. Require
current geometry before drawing. Consider an aid-specific supported-line-visit
route that recognizes the ongoing fourth ABAB visit; retain finalized fast P11
as a separate descriptive observation. An explicit aid window and presence
confirmation settings still need definition; no replacement timing is validated.
Review actual dimmed area/contrast and log why activation or rendering is withheld.
No new runtime thresholds, build, device operation, capture, staging or commit.

Evidence: current `ReadingFocusPolicy.kt` input/support paths;
`ReadingAidPolicy.kt` focusSupport/prepare;
`ArticleReadingAidRuntime.kt` geometry/consultation resets;
`ScanpathEngine.kt` P11 finalization/window;
`ReadingAidOverlayView.kt` FOCUS_REGION rendering. Reviewer
`focus_activation_audit` independently checked source and proposed safeguards.
Primary precedent rechecked: [SwitchBack (2015), pp.3,6](https://ubicomplab.cs.washington.edu/pdfs/switchback.pdf)
separates phone-facing detection from reading-location estimation, restoring a
saved line on return. This supports the interaction rationale, not our repeated
count, dimming response, timing or an individual distraction diagnosis. The
[existing evidence review](focus-window-improvement-review.md) retains ruler
findings and population/hardware limits. No current natural-reading trace was
available in this review; earlier synthetic successes do not resolve live failure.

**2026-10-14 focus visibility / stricter definition (completed checkpoint):** owner now
reports live focus can trigger but immediately disappears; authorizes stability
through brief loss and line transitions, and longer word-definition dwell.
Current defaults: focus holds3s accumulated usable reading then fades7s,
last supported line retained through<=1s delivery-age grace without renewing
evidence on ticks; new supported line moves it. Operational invalidation and
definition priority remain. Definition threshold rises800→1500ms observed
current-word residence; missing time is not counted. Two initial strengths50/100%
and3/30s/500ms count rules retained. Policy10/conditionV5. Focused source review
has no actionable finding. All **207 focused JVM tests pass**, zero failures,
errors or skips, and both APKs build. First gate passed205/207; the extended
Replay definition dwell crossed a separate legacy cycle window, corrected to
1750ms before the owner-approved passing retry. Existing owner authorization
covers continuing data-preserving A56 aid updates and camera-free checks; both
APK installs succeeded and **3 Android tests pass** in 23.743 s. Native checks
cover brief fourth-gap focus retention, third/fourth strengths, delayed dictionary
popup and line-cycle consultation priority/excluded samples. Natural reading and
human benefit remain unverified. No camera/staging/commit.
Evidence/XML/APK hashes: `output/focus-stability-definition-delay-check-02/summary.json`.
[Focus contract](repeated-interruption-focus-window.md),
[definition contract](article-definition-aid.md).

**2026-10-14 live focus correction / two strengths:** owner reports
normal article guide/bookmark work and repeated-interruption Replay shows focus,
but neither intentional live alternation nor repeated look-away displayed it.
Owner requests two or three strengths for short articles; default focus now uses
50% at 3 completed episodes, 100% at4+; independent cycles1/2+ use50/100%. Fade
remains continuous; 3/30s/500ms thresholds retained. Source fixes ordinary P13
unclassified-fixation gaps erasing completed focus history, and line-only focus
requiring x inside glyph ink despite a valid physical-line assignment. Engine
continuity remains broken, with no cycle joined across gaps. Shared article,
pilot, replay and demo are synchronized; policy9/conditionV4. New real-engine
regressions use live detector settings and rawSample/selected-stream callbacks.
Source review reports no actionable finding. All **200 focused JVM tests pass**;
both APKs build. The same new regression needed three assertion corrections
before the approved passing gate: fresh post-gap support already existed, rolling
cycles overlap, and pending pre-gap visits finalize later. No app source changed
during those retries. Evidence/XML/APK hashes:
`output/focus-live-two-strength-check-04/summary.json`; failed gates retained in
folders01–03. Owner authorized data-preserving A56 updates and camera-free Replay
checks; both APK installs succeeded and **2 Android tests passed** in50.631s,
covering15 scripted runs. Interruption50/100% choices, line-cycle focus and actual
draw proxies passed. Natural live gaze was not collected; no current phone trace
proves the exact live cause or human benefit. No camera, staging or commit.
[Current focus contract](repeated-interruption-focus-window.md).

**2026-10-13 repeated-interruption focus implementation:** owner authorized the
revised feature and selected 3 separate episodes within 30 seconds. V4 focus
counts qualifying unavailable/recovery episodes, not missing frames; 500 ms loss
qualification, 25/50/75/100% initial strength at 3/4/5/6+ episodes, fade over10s
accumulated usable selected-capture deltas. Independent supported line cycles
also trigger without residence/slow AND; counts are separate. Window follows
supported lines; bookmark remains independent. Policy8/conditionV3, typed
episode/action provenance; calibration/filter/frozen instruments retained.
First build stopped on nullable cycle geometry. Approved second gate built both
APKs and passed 188/189 JVM tests; the failed replay assertion is corrected.
Review corrections cover continued-alternation anchoring, expired frequency,
preference-adjusted route strength and tracked outside-text time inflating brief
losses. Work resumed on 2026-10-14 after a helper usage limit; the owner-approved
final retry passes **194 focused JVM tests**, zero failures/errors/skips, and both
APKs build. Four timing regressions and capped/manual mixed-route cases pass.
Source review has no remaining actionable P1/P2 finding; scoped whitespace passes.
Native fixtures compile but phone execution remains pending. No device
install/camera/collection/staging/commit. Current contract:
[repeated focus](repeated-interruption-focus-window.md),
`output/repeated-focus-check-03/summary.json` (test XML and APK/source hashes;
failed gates retained separately in folders 01 and 02).

**Historical 2026-10-13 focus-window improvement discussion:** owner proposes repeated
look-away/no-gaze episodes and inconsistent progression as additional focus
inputs. Current A4 still requires cycle + residence + slow progress, with
residence-based strength. Bounded primary review recommends independent
resumption and supported-alternation routes; count distinct loss/recovery
episodes, retain tracked departure separately from unavailability, and withhold
scattered-jump distraction claims. The window may support perceived focus or
resumption; no study validates the proposed phone trigger/count/strength dose.
Manual support and readable context are useful comparators. Full2026 highlighting
PDF/table now accessible; prior retrieval limits are historical. No runtime,
build/device/camera or staging changed in this discussion. Evidence/proposal:
[focus improvement review](focus-window-improvement-review.md).

**Historical owner clarification before threshold selection:** focus should activate from a specific number of
distinct unavailability/recovery episodes in a recent time window, not one
sustained absence. Count complete episodes, not missing frames; one long absence
counts once. The independent bookmark/line guidance handles placekeeping after
a single interruption. Focus then dims surroundings around a supported reading
area, with potential frequency-based prominence/duration and easing after stable
continuation. Exact count/window/dose remains unset and unvalidated; no focus
runtime change yet. Uncertainty applies equally to line alternation/scattered
paths and missing gaze. Repeated tracker faults can mimic repeated interruptions;
retain reason distinctions and avoid a conclusive distraction label. This
supersedes the earlier single-gap focus suggestion.

**2026-10-13 sustained missing-gaze bookmark correction:** owner expects looking
away to trigger even when tracking disappears. Added a 500 ms delivery-time
confirmation of explicit unusable-gaze reports, marking the last supported line.
Brief recovery cancels pending loss; geometry, controls, dictionary, pause and
disable clear memory. Reason `sustained_gaze_unavailable` is not an attention or
difficulty diagnosis. Nullable departure capture bounds and separate delivery
evidence avoid invented capture samples. Policy 7 / bookmark rule V2 / smoothed
condition suffix V2; calibration/filter, outside/jump rules and fixed visual form
remain. Replay adds `bookmark_tracking_loss`. All **173 focused JVM tests pass**,
app/test APKs build, whitespace passes and source review has no actionable
finding. Native runtime-timer/replay fixtures compile; phone execution is pending.
No device install/camera/collection, staging or commit ran. Evidence:
[bookmark contract](scanpath-reading-bookmark.md),
`output/bookmark-tracking-loss-check-01/summary.json`.

**2026-10-13 authorized calibrated-smoothed implementation:** owner explicitly
requests one path for the dot, live scanpaths and aid decisions. Article and live
pilot now select the exact provider `x/y` output as `article_smoothed_v1`;
mapping/grouping/centroids, aid anchors/bookmark checks and pilot scoring use it.
True calibrated pre-filter raw values are retained separately; missing filtered
input abstains without fallback. Existing calibration and filter 0.7/0.002 are
unchanged. Replay supplies honestly labeled post-filter synthetic trajectories,
raw originals null, without pretending to run the smoother. New policy 6,
schema-2 traces and smoothed condition IDs distinguish the route; frozen v6 and
schema-1 diagnostics retain their own identities. Host/Android/JVM fixtures
cover disagreement, missingness and provenance. **166 focused JVM tests and 10
host checks pass; app and test APKs build.** The first host invocation failed
before tests because of the sibling import path; owner-approved discovery retry
passes. Focused source review's old replay policy assertion and missing
provider/display audit pairing were corrected. Native fixtures compile but were
not executed on a phone for this build. See [stream contract](calibrated-smoothed-scanpath-path.md) and
`output/smoothed-scanpath-check-01/`. No device install/camera/collection, staging
or commit was authorized for this implementation task.

**Historical 2026-10-13 calibrated/raw/smoothed clarification:** owner challenges using raw
for aid decisions after smoothing work. Current `rawX/rawY` are calibrated SVR
screen predictions, not uncalibrated camera/model features. Saved calibration and
output eligibility gates remain upstream. Article One Euro filtering (minCutoff
0.7, beta0.002) supplies displayed x/y; new scanpath engine and its aid decisions
use the calibrated unsmoothed branch. The raw branch was deliberately matched to
the research/replay protocol; the checked records do not establish its superiority
for interaction. One Euro's author documentation describes jitter/lag tradeoffs
(https://gery.casiez.net/1euro/). Calibration, smoothing precision and actual target
accuracy must not be conflated. A prospective filtered aid route must keep its
mapping, candidates and triggering on the same declared stream while preserving
raw research records; mixing raw duration support with another filtered word
anchor is not an acceptable shortcut. This was an explanatory source review,
not authorization to replace the live engine or frozen protocol. No runtime,
smoother parameters, device operation or test changed.

**Historical 2026-10-13 dot/tolerance clarification and continued dictionary report:** owner
asks whether the orange dot limits matching and reports that popups open without
definitions. Source inspection confirms 18 screen-pixel drawing radius only;
ordinary scanpath word/line mapping uses raw coordinates while the dot may show
smoothed ones. There is no padded nearest-word radius. Current 0.5-line-height
dispersion groups candidate samples, not model uncertainty; 80% assignment vote
is consistency, not accuracy. Calibration saves no per-user target uncertainty
radius. No detector, tolerance, radius or frozen protocol change is authorized
by this explanatory question. Any wider target region needs a measured error
bound and ambiguity handling rather than assigning every overlapping word.
Definition popup appearing means selection fired; empty content is a separate
lookup/coverage/rendering question. Live card field inspection returned no
readable hierarchy; no screen image or gaze recording was collected. A public
exact-word Datamuse analyst probe returned 200 in 493 ms, one exact entry/six
definitions. Added one exact-match online fallback separately from gaze:
Free Dictionary API → Datamuse exact match → glossary. No fuzzy substitution;
provider/route metadata updated, offline replay/pilot remain bundled-only.
Source review has no actionable P1/P2 finding. All 125 focused JVM checks and
both APK assemblies pass. Authorized data-preserving A56 updates installed.
Nine camera-free checks pass in 1.642 s: keyboard/hello primary live, analyst
Datamuse live in 794 ms. A targeted native analyst popup check also passes in
3.529 s, definition/card/Close verified with a fresh 3,467 ms lookup. Ten distinct
cases across two invocations, not one ten-test run. Native fixtures establish
delivery but not the owner's exact natural-reading state. Evidence:
`output/dictionary-article-check-03/summary.json`. Datamuse docs require keys from
Feb 1, 2027; future configuration remains necessary. No camera/gaze, participant
data, staging or commit ran. Matching/detection tolerance was not changed.

**2026-10-13 fresh dictionary connection check:** owner reported continuing
dictionary trouble and authorized ADB retry, data-preserving app/test updates
and camera-free phone checks. A56 / API 36 Internet permission is granted. Fresh
requests bypass both per-instance memory and Volley HTTP cache: keyboard live
definition in 408 ms populated the native card and Close worked; unbundled hello
returned live in 83 ms; analyst returned labeled glossary fallback in 79 ms.
All nine phone checks pass in 0.792 s; 19 focused JVM checks and both APK assemblies
pass. Installed app hash matches the built APK. Default reading retains HTTP
caching; `allowHttpCache=false` is only used in explicit connection fixtures.
Evidence: `output/dictionary-connection-check-02/summary.json`. This confirms
phone-to-service connectivity and native fixture delivery, not complete service
coverage or natural gaze-triggered article behavior. The analyst host request
returned 404; its error-body parser did not preserve the payload. No new provider
was integrated. No camera, gaze, participant data, staging or commit ran.

**Historical 2026-10-13 fixed entry and dictionary feedback correction:** owner reports
running the app successfully and now wants **Aids** fixed in its existing bottom
position. Article tap/scroll reveal logic is removed; fixed button coordinates
remain excluded from article interpretation and raw diagnostics retain their
original recording route. Button is disabled during diagnostic recording.
Owner saw unavailable/connection text for **analyst** in a normal article. One
host request returned HTTP 404 in 406 ms; its payload and phone cause are not
verified. Merged debug manifest has Internet permission. Dictionary feedback now
distinguishes unsupported token, exact missing-entry response, service failure,
offline-only absence and unknown absence. Added original, clearly labeled
analyst glossary fallback; live-first lookup, retryability, successful cache and
consultation safeguards remain. Source review caught the disabled-entry diagnostic
dot exclusion; recording now updates its full-screen dot before those exclusions,
independently confirmed in source. Initial build compiled main/Android sources but
the new JVM fixture had a nullable-property compile error. Owner-approved corrected
retry passes all 119 focused JVM checks and both APK assemblies; whitespace passes.
Evidence, initial failure, XML and hashes:
`output/fixed-aids-dictionary-check-01/summary.json`. No new device operation,
camera, staging or commit. Actual fragment/dictionary phone behavior remains untested.

**Historical 2026-10-13 Reading aids menu and sentence prototype:** owner approved both and
selected tap-the-article access. Bottom **Aids** starts hidden, toggles on body
tap and hides on scroll/menu/pause. Shared sheet has independent switches and
exclusive Line/Sentence context options; coarse supported lines select text
sentences, with containing-paragraph fallback or abstention on ambiguity, without
word-level targeting. Current guides use behind-letter gray at 20/255 = 7.843%
default opacity; no proposed 20% pulse or word-step release. Article/pilot/replay
now select definition V4 / policy 5; evaluator layout and action ranges are
versioned. Scoped menu suspension excludes delayed captured frames and screen
coordinates use the existing helper. Source review findings resolved. After an
initial cache-denied attempt and a stale demo-strength assertion (113/114 pass),
owner-approved corrected retry passes all 114 JVM checks and both APK assemblies;
whitespace check passes. Logs, failed/corrected XML and hashes:
`output/guide-menu-check-01/summary.json`. No new device installation or camera
action; actual article taps/insets/lifecycle remain untested on phone.
[Current contract](reading-aid-menu-and-sentence-guide.md) retains research limits.
Prior phone results refer to V3, not current V4.

**Historical 2026-10-13 guidance-unit challenge and accuracy constraint:** owner questions
the line-highlight rationale and suggests sentences, while requiring low reliance
on word-level gaze accuracy. [Evidence/design review](scanpath-guidance-evidence-review.md)
records GazePrompt's navigation-only support, EyeBookmark's interruption-context
benefit, and Vatány et al. 2026 (80 adults, desktop hover): sentence/paragraph
ratings exceed static perceived-attention ratings, without detected speed or
comprehension gain. No study selects a winning automatic phone guide or validates
the 8%/20% opacity pulse. Proposed sentence/paragraph context units would come
from text segmentation plus supported coarse regions, with no guessed sentence
on shared/ambiguous lines. Retain line guidance as an option. The prior proposed
P01/P02 word-step release is withdrawn; choose the unit before refining intensity.
These are design candidates; installed app and previous test evidence are
unchanged. No new build, device action, recording, staging or commit ran.

**Historical 2026-10-13 independent line-highlight proposal:** owner clarified that A1 must
have its own rule; the coordinated cycle/focus proposal is superseded. Current
code still follows position at its fixed adaptive baseline; no APK/code change
in this design review. [Independent rule](scanpath-line-highlight-rule.md) proposes
baseline guidance, entry emphasis on a confirmed uninterrupted line change, then
baseline after supported same-line forward word movement or the bounded existing
transient lifetime. It consumes no focus/cycle/bookmark/definition activation.
GazePrompt sections 3.1.1/3.2.3/4.2.1 support line entry/stabilization and observed
landing/progress criteria; the boosted phase, release, normalized strength and
timeout are unvalidated NewsMead choices. No paper-backed difficulty dose is
claimed. Owner requested exact percentages and asked about gray: proposed A1
appearance is `#808080`, actual opacity 8% for same-line reading and 20% on
confirmed line entry, 0% when hidden. This replaces the prior proposed normalized
25%/reader-cap appearance, not installed behavior. Current yellow 25% strength
actually draws at 20/255 = 7.84% opacity. Gray/percentages and behind-letter fill
are unimplemented design choices requiring theme/reader checks; no numerical
paper justification or universal distraction reduction is claimed.
Digital Reading Rulers direct PDF retrieval timed out during the prior
review; no new exact final-table claim was drawn. Prior software/device evidence
remains unchanged and does not test this proposal.

**2026-10-13 last-line bookmark:** owner requested replacing expected-next-line
prediction and selected both look-away and non-normal line-jump triggers.
Current article/pilot/replay select definition V3 / policy 4 / `READING_BOOKMARK`,
anchored at the last supported source line with fixed gutter appearance. Outside
activation requires sustained usable coordinates beyond the article rectangle;
tracking loss alone retains memory silently. Source/transition memory are separate,
so unknown gaps cannot create jumps or clear a held cue on first elsewhere recovery.
Geometry/lifecycle/consultation and reader disable clear the cue. Demo and raw
replay are aligned; source/departure evidence is retained by both trace writers.
EB15/CW12 support known-interruption location cues, not the exact autonomous
phone trigger/bar. [Bookmark contract](scanpath-reading-bookmark.md) gives rules,
sources and limits. After owner-approved correction of a nullable-geometry build
error, 94 focused JVM checks and both APK assemblies pass. Data-preserving APK
updates installed on SM-A566B/API 36. Camera-free checks passed 30/32 initially;
the two new assertions used the wrong JSON field. After correction and explicit
retry approval, both pass in 28.861 s. All 32 distinct cases are covered across
those invocations; this is not a single clean 32-test run. Initial failures,
corrected logs and installed hashes remain in `output/reading-bookmark-check-01/`.
No camera/live gaze, calibration, private recording, participant validation,
staging or commit ran.

**2026-10-08 A56 dictionary check 06:** owner authorized the connected-device
check and explicitly approved retry after a test-only local-lateinit cleanup
compile error. Corrected app/test APKs installed with data-preserving updates on
SM-A566B/API 36. One camera-free invocation passes all 15 checks in 0.835 s:
real-repository public `keyboard` definition delivered in 443 ms, live API
attribution and native-card content/Close verified, plus offline, popup lifecycle,
synthetic consultation and article-presenter checks. The new HTTP smoke test is
opt-in (`liveDictionarySmoke=true`), so normal test runs do not send requests.
Evidence/hashes and initial failure: `output/dictionary-device-check-06/summary.json`.
No camera/live gaze, human reading, private-recording access, staging or commit
ran. Actual gaze-triggered article reading remains a manual check.

**2026-10-08 live dictionary:** the owner requested actual online word
definitions. Article/demo and live pilot now use successful online cache →
Free Dictionary API → bundled glossary on failure. Synthetic pilot/replay stay
bundled-only. Fallbacks remain retryable; cancellation/duplicate delivery guards
preserve the popup lifecycle. Pilot records `online_then_bundled` for live runs.
NewsData has no dictionary endpoint and its key is not sent to this service.
One public host probe returned `keyboard` with definitions/attribution, HTTP 200
in 395 ms. All 26 focused JVM checks pass; both APKs assemble from a fresh build;
independent source review has no finding. No device install/tests or live gaze
ran. Evidence: `output/dictionary-online-check-01/summary.json` and `api-probe.json`;
current behavior/limits: [article definitions](article-definition-aid.md).

**2026-10-08 timing/font evidence audit:** current article text is locked at
22 dp (`StudyConfig`); geometry and dispersion use actual rendered line height,
but aid timing/strength have no font-size adjustment. Hyrskykari et al. (2003),
sections 4.2.1/5.2, provide an approximately 800 ms lexical-help precedent with
word-length/frequency adjustments, not validation of our flat current-visit timer.
The 1000–2000 ms focus ramp and 25% line-guide baseline remain prototype settings.
GazePrompt supports user-adjustable delays; Franken et al. show font-dependent
fixation measures, not an aid timing formula. Exact links and limits are in
[aid runtime](scanpath-aid-runtime.md). No detector, timing or font code changed.

**2026-10-08 focus default:** the owner requested enabling the focus window by
default for now. Shared article/pilot/replay preferences now enable it; existing
cycle/residence/slow-progression requirements and reader opt-out remain. Pilot
shadow/no-aid conditions still suppress display. All 30 focused policy, definition
and replay JVM checks pass, and the debug APK assembles. The default is included
in the A56 APK installed for dictionary check 06; its human-reading behavior is
not established by those dictionary checks. See [aid runtime](scanpath-aid-runtime.md).

**2026-10-08 A56 check 05:** the owner authorized the connected phone check.
Merged app/test APKs installed with data-preserving updates on SM-A566B/API 36.
One camera-free run passed 33/38 checks. Three failures are stale popup-fixture
bytecode calling the old action constructor; two are continuation replay setup
requiring an unnecessary quarter-line destination center. The generator now
accepts actual supported landing/arrival centers while retaining source progress,
visibility and timing guards; three real-engine JVM regressions are added and
source review has no finding. The first fresh-build command stopped before
compilation at a PowerShell argument error, now corrected. Owner-authorized fresh
rebuild passes 83 JVM checks and the corrected A56 retest passes all 38 in
94.573 s; popup constructor bytecode now matches. Both corrected APKs are
installed. Home launch succeeded but its UI snapshot showed the launcher and
zero app nodes, so phone feed display/publisher extraction remain unverified;
no retained app crash message was found. No camera/calibration, private journal
export, staging or commit ran in this task.
Evidence/limits: [device check 05](scanpath-device-check-05.md),
`output/scanpath-device-check-05/`. Preserve the initial failed result.

**2026-10-08 Recommendation integration:** the owner authorized merging fetched
`origin/Recommendation` at `501e9be` into `measurement/scanpath-pilot`. The NewsData
key is configured only in ignored `local.properties`; current feed mode is online
(`OFFLINE_MODE=false`), while the existing login bypass remains enabled. Current
article aids/demo/pilot/replay and frozen estimator/protocol settings are retained.
Ten conflicts are resolved. Article readiness awaits extraction; callbacks and
pending gaze restart respect view/lifecycle/calibration boundaries. Usage periods
reopen after backgrounding; home callbacks retain the latest selection. A source
review found no remaining actionable issue after repairs. One computer-side API
probe returned 10 articles. All 80 focused JVM checks and both APK assemblies
pass, including current continuation checks; Android fixtures compile but have
no current device result. The historical compile failure remains retained.
Exact resolution and limits: [Recommendation integration](recommendation-merge.md).
Evidence: `output/recommendation-merge-check-01/`. No device capture/install,
calibration, private-log export or push ran.

**2026-10-04 continuation-marker amendment:** the owner approved replacing the
post-P09 landing cue with an expected-continuation cue. Supported first-quarter
to last-quarter progress remembers the next fully visible LTR line; a supported
landing elsewhere offers a fixed marker at that expected line. A normal next-line
arrival produces no marker. This is optional navigation assistance, not an error
or difficulty diagnosis. Appearance is fixed 6 dp / alpha 240, with the existing
4 dp gutter gap; positive strength controls do not attenuate it. New conditions
use `DEFINITION_PROFILE_V2`, policy v3 and `PILOT_DEFINITION_*_V2` /
`REPLAY_DEFINITION_V2`; detector/frozen protocols retain their identities. Exact
state, evidence limits and sources: [continuation marker](scanpath-continuation-marker.md).
The prior V1/evaluator/device checkpoints below remain historical.
Both APKs assemble and Android fixtures compile; they have no current phone
result. The first JVM gate stopped at a corrected test-only field-name error;
retest approval is pending under the one-failed-attempt boundary. Scoped
whitespace and resource XML checks pass. Independent review findings were
resolved, including first-prefix censoring, row wobble and departed-finalization
handling. Evidence/hashes: `output/continuation-marker-check-01/summary.json`.
No device install/capture, staging, commit or participant validation ran.

**2026-10-04 evaluator alignment:** the owner explicitly selected matching the
current article aids for pilot and replay. Both now use `DEFINITION_PROFILE_V1`,
gutter markers and the same near-word popup; the obsolete strip reserves 0 dp in
every condition. Pilot conditions are `PILOT_DEFINITION_*_V1`; replay is
`REPLAY_DEFINITION_V1`. Their UI/dictionary mode is recorded separately from old
magnifier traces. Pilot live lookup retains the offline glossary/online fallback;
synthetic pilot/replay disable network. Consultation boundaries preserve raw
callbacks and exclude card intervals/late captures from article paths. Prompted
tasks remain aids-off; shadow/display share geometry. Replay uses the shared
six-stage article presenter with callbacks detached from closed runtimes.
Historical detector-only replay assertions remain; current display checks are
separate because consultation changes later article evidence. See
[pilot](scanpath-live-pilot.md) and [replay](scanpath-replay-screen.md). Independent
source review found no P1/P2 issue; direct line-cycle and replay-to-demo regressions
address its coverage limits. 64 focused JVM tests and both APK assemblies pass;
updated Android fixtures compile but have not run on a phone. Evidence/hashes:
`output/evaluator-current-aids-check-01/summary.json`. No device capture/install,
staging or commit ran.

**2026-10-04 article line-start spacing:** the owner's screenshot showed the
marker covering the first letter. The article body reserves 16 dp start padding
from inflation; article overlays put the marker in this gutter with a 4 dp text
gap. Both live and demo construction select this placement. Existing geometry
already accounts for TextView total padding; marker clipping abstains. Text may
wrap differently in the narrower column. A camera-free pixel rendering regression
compiles for separation from the text. App/test APK assembly, article XML parsing
and whitespace checks pass; device execution/appearance remain pending.

**2026-10-04 definition layout/offline amendment:** the owner clarified that the
card should follow an internet template and requested a light offline dictionary.
The native card adapts Flowbite React's Default Popover (shaded header, divider,
border, pointer) using NewsMead colors/fonts, with the larger compact close control
and source highlight. A 75-entry original English glossary supplies immediate,
explicitly attributed offline definitions. The article demo prefers a covered
actual visible source word; no text substitution. Uncovered words retain the API
route/unavailable state; unsupported languages still abstain live. Source links,
coverage and check results are in [Article definition aid](article-definition-aid.md).
64 focused JVM tests pass; app/test APK assembly and popup XML parsing pass.
New offline/word-selection Android fixtures compile but have not run on a device.
Phone rendering of this revision remains unverified; no device install/capture,
staging, commit or backend change ran.

**Historical article UI checkpoint — 2026-10-03:** the owner rejected the top word
magnifier and requested a definition popup and demo inside the loaded article.
[Article definition aid](article-definition-aid.md) now records the implementation,
H03/M25 lexical-assistance precedents, transfer limits and consultation exclusions.
Ordinary articles select separate `DEFINITION_PROFILE_V1` / `EXPERIMENTAL_DEFINITION_V1`;
the configurable 800 ms current-word residence trigger is exploratory, not a
validated difficulty cutoff. Cards remain during consultation and preserve source
layout; open/close boundaries exclude consultation and late captures from article
sequences while retaining numeric raw callbacks. The source word stays highlighted
using a noninteractive overlay while its definition is open. Close uses a large
vector × and a compact 28 dp gray box inside a 48 dp tap area, without source
spans/font changes. Callback resets are deferred
until engine dispatch completes. The explicit article demo stops the provider,
cycles real aid views and keeps camera off afterward. `origin/Recommendation`
was fetched at `501e9be1f83d4d962dc4931ef9044ab1f18a3e1b`, without merge; no dictionary
endpoint was found. A replaceable English Free Dictionary API adapter provides
actual definitions and bounded caching; unsupported languages abstain live.
61 focused JVM tests pass and app/test APKs assemble. New camera-free Android
fixtures compile but have not run on a device; the earlier A56 15-test evidence
applies to the preceding build. The evaluator migration above supersedes this
checkpoint's deferred replay/pilot update; frozen protocols retain their meaning.
No device install/capture, staging or commit ran.

Help older adults sustain attentive reading through real-time visual assistance
in the on-device Android NewsMead prototype. The immediate development priority
is to complete the integrated live scanpath-to-visual-aid prototype before
participant measurement/evaluation. Build software replay checks during
implementation; carry forward existing gaze evidence. The user explicitly
allows continuous, combined, or variable
intensity scaffolds; four mutually exclusive levels are an existing implementation,
not a fixed requirement for future research. Re-entry is only one possible use.

Owner clarification on 2026-09-30: visual help need not be called scaffolds or
organized as levels. Replace the legacy design when a better suited,
study-defensible design exists. The [aid design and evidence contract](scanpath-adaptive-aid-design.md)
now specifies independent aid types with within-type strength, per-rule source
provenance and exact trigger/form/outcome limits. Prioritize line guidance and
localized word support; optional ruler and word-progress forms get distinct
versioned IDs. Preserve A4's focus-region meaning rather than pooling variants.
No forceful intervention follows every return/cycle, and poor tracking cannot
increase assumed difficulty. The [runtime checkpoint](scanpath-aid-runtime.md)
now implements the independent line/word/focus route, controls, variable
word/focus strength and bounded action provenance. The
[matched pilot instrument](scanpath-live-pilot.md) now provides prompted tasks
and ordinary-reading raw/shadow/display conditions. Further authorized device
execution and participant evaluation follow this integrated build.

First device check on 2026-10-01: Samsung SM-A566B/API 36, target APK from
`e5fa5b2` installed with data-preserving update. Four synthetic geometry/magnifier
tests ran: one passed, three failed on missing detached-fixture layout params.
After local layout-parameter corrections and explicit owner authorization, the
corrected test APK was installed and all four tests passed (`OK (4 tests)`).
No gaze capture or participant measurement was started. Record and limits:
[device check 01](scanpath-device-check-01.md). Keep this distinct from natural
gaze or visible article-aid validation.

The owner then requested the other phone: SM-G991B/API 35. Identical target/test
APKs installed with `-r`; the same four synthetic tests passed on the first
attempt. Source checkpoint `9559c95`, hashes/results/limits in
[device check 02](scanpath-device-check-02.md). Both phones pass these synthetic
checks; visible article layout, live gaze and human validity remain unmeasured.

The owner authorized the interactive pilot screen's first replay milestone.
[Scanpath replay screen](scanpath-replay-screen.md) now provides five explicit
raw-coordinate scenarios through actual rendered word/line geometry, the existing
engine/policy and real aid views, with controls and full private synthetic traces.
Replay stops on geometry change, pause, Stop or changed controls/scenario. Its
capture clock is scheduled synthetic elapsed time; delivery is actual callback
time, and >100 ms scheduling lag aborts. Synthetic identity skips calibration
and creates no camera/provider. This is the scripted inspection route; live
prompted tasks and matched human recording remain pending. Check the guide's
dated build/device results before inferring readiness. All 34 focused JVM tests
and APK assembly pass; [device check 03](scanpath-device-check-03.md) retains the
initial test lookup failure, authorized correction/retest and final five-test
pass on SM-G991B/API 35. Actual laid-out replay and variable word/focus choices
have software evidence on that phone; the synthetic word preview was inspected.

**2026-10-03 implementation:** starting from `50ff2b7`, a separate live pilot
screen now provides ten geometry-derived prompted word/line tasks, continuous
engine processing, hidden-dot ordinary reading and no-aid/shadow/display
conditions with the same reserved viewport. Private JSONL retains raw inputs,
capture-time prompted intervals, engine acceptance, full evidence/action/draw
provenance, adherence reports and shutdown counters. Per-target agreement and
unshown opportunities are separate from descriptive global alignment; holds
cannot label physiological refixations. Five camera-free activity tests compile,
51 focused JVM and 8 host summary tests pass, and both APKs assemble. Focused
independent review found no remaining issue after scoring/lifecycle fixes.
Device execution of the new pilot awaits task-specific authorization; no live
gaze or human data were collected. Read [the guide](scanpath-live-pilot.md) for
units, denominators, time assumptions, partial-file rules and remaining study
procedures. Do not infer a completed participant plan from default task settings.
No human validity follows from synthetic replay.

**Trigger review/demo — 2026-10-03:** [evidence review and flowchart](scanpath-trigger-evidence-review.md)
rechecked primary methods/outcomes and current decision predicates. Line guidance
has the strongest intervention precedent; the exact word AND trigger differs
from GazePrompt's OR trigger, while the cycle-to-focus and monotonic dose rules
lack direct validation. The [replay screen](scanpath-replay-screen.md) now adds
an explicit render-only Demo cycle with seven four-second stages, Previous/Next
and Stop. Manufactured actions never enter detector/policy/results/traces, and
no camera/calibration opens. Independent review fixed insufficient max-word
height in replay/pilot strips: 68 dp reserved height remains, vertical padding
is 10 dp. All 53 focused JVM tests pass and app/test APKs assemble; new device
execution awaits task-specific authorization. Preserve this status when making
panel claims. No human difficulty/aid-benefit evidence was collected.

**A56 compatibility — 2026-10-03:** the owner explicitly approved installation
and camera-free checks after automatic installation review required explicit
A56 approval. Both APKs installed with `-r`. [Device check 04](scanpath-device-check-04.md)
records one bounded 14-test attempt: **6 pass, 8 fail**. All five pilot tests
pass, including all ten prompted plans and shadow/display behavior. Four demo
tests fail readiness; four replay tests abort because tiny negative callback
lag was treated as late. Later replay scenarios were not executed. Known
synthetic traces and an app-only pilot preview are retained locally. Device
attempts stopped; local fixes now retry unchanged early deadlines and use the
actual compound-padded word-strip height. Source review closed; all 56 focused
JVM tests and both APK assemblies pass. Improved status diagnostics and a
centered-strip regression compile. The owner then authorized the corrected
installation/retest: **all 15 tests pass, 50.908 s** on A56/API 36. All five replay
scenarios completed with expected patterns/aid draw proxies, strict causal
timing and synthetic callback lag 0.013–2.879 ms. Demo/renderer and all five pilot
checks pass. App-only demo word/focus and replay word previews are readable.
Corrected hashes/results/evidence paths are in check 04; this build has not been
tested on S21. Home → Scanpath replay → Demo cycle is ready for camera-free
inspection. Earlier pending-device statements describe pre-attempt status.
No camera/human collection, staging or commit ran.

The [scanpath-to-visual-aid implementation proposal](adaptive-visual-aid-proposal.md)
is the current **NewsMead feature specification**. Re-reading the source task
**Explore feasible gaze metrics** clarified the owner's final intent: preserve
ordered gaze-to-text visits, classify **observable navigation patterns**, then
select or parameterize visual help. The owner subsequently clarified that the
proposal must derive and evaluate **both word-level and line-level** scanpaths,
trace the order categories to primary papers, and connect each visual aid to
the observed situation it might address. Word-level analysis is a feasibility
track despite weak current exact-word placement. Slow line progression to a
focus window is an experimental mapping with no identified direct intervention
evidence; window restrictions may remove useful parafoveal text. A universal
reading-instability construct, supervised support-need model, and fixed
four-scaffold ladder are **not** required. Aid type, target, strength, duration,
and combination may vary during ordinary reading; the candidate policy and its
benefit must be independently tested. The current proposal specifies code
boundaries, derivation, a descriptive classifier, a configurable aid policy,
primary-study precedents, and separate measurement, pattern, and intervention
gates. An
[illustrated PDF companion](../output/pdf/newsmead-scanpath-visual-aid-proposal.pdf)
shows the same derivation, order-to-aid bridge, and proposed UI states; its
examples are schematic, not observed NewsMead scanpaths. The earlier
[three-state line-guide research plan](adaptive-visual-aid-research-plan.md)
is retained as a historical narrower option, not the current requirement.
The [known-interruption bookmark](evidence-backed-reading-support-proposal.md)
and [fixed pattern/scaffold proposal](scanpath-scaffolding-proposal.md) are
historical options. The [critical audit](gaze-proposal-evidence-audit.md) still
applies to unsupported line-pattern meanings and aid mappings; the
[primary-study ledger](scanpath-scaffolding-evidence.md) records evidence and
transfer limits. An HSMM is at most an offline exploration; the old weighted
score and Mahalanobis distance do not establish reading instability. Preserve
useful rereading and abstain across ambiguous or missing gaze. The thesis source
still describes the legacy index and a circular recovery-latency endpoint;
align it only after the method and independent outcome are fixed.

The [order-category provenance table](scanpath-scaffolding-evidence.md#provenance-of-the-proposed-order-categories)
identifies Olivier's word-distance alphabet and Yamaya's multiline segment
classes as precedents, while marking NewsMead's physical-line bins as original
operational definitions. Preserve separate fixation-level word transitions,
word visits, and line visits. Olivier's same-word `Rfx` is not a long line hold;
`HOLD` is duration, not order; `BREAK` marks missing sequence continuity. The
historical forward multi-line symbol `FWD_LONG` (formerly `FWD_SKIP`) must not
imply that intervening text was unread.

The [published sequence-to-behavior table](scanpath-sequence-behavior-table.md)
is the current lookup for the exact word, segment, line, and longer-path labels
used in the proposal. It separates observed geometry from model/author
interpretations and maps NewsMead arrow examples only as proposed descriptors.
The [pattern ground-truth audit](scanpath-pattern-ground-truth-audit.md) identifies
which studies used human movement annotations, known geometry, instructed tasks,
experimental text conditions, or exploratory clusters. Exact line cycles such as
`L4 -> L5 -> L4 -> L5` remain NewsMead illustrations, not validated difficulty
classes.
The [original figure reading guide](scanpath-source-figure-guide.md) links directly
to primary-source pages and images for NN/g's named web patterns, the 2026
anchor-search-confirm process, sentence-region rereading prototypes, Hyönä's
expository reading profiles, Parshina's sentence-path prototypes, and Chen's
task-reading strategy transitions. It distinguishes individual gaze plots from
heatmaps, aggregate profiles, and authors' explanatory figures and records
task/evidence limits. Do not substitute generated redraws for original figures
when the owner asks for verifiable paths.
The [reading-difficulty scanpath evidence guide](scanpath-reading-difficulty-evidence.md)
separately audits paths against independent reading-skill, comprehension, and
controlled-difficulty outcomes, with original figure and table locations. No
reviewed named path is a conclusive live difficulty sign for older adults reading
news on a phone. Notably, regressive paths relate differently to comprehension
across genres, and news-page gaze studies found so far do not validate a
path-to-difficulty outcome. Its quantitative audit shows that published cluster
shares, correlations, model coefficients, R², and benchmark AUROCs are not
calibrated `P(current difficulty | path)` or aid-benefit weights. Preserve
candidate paths as observations pending NewsMead measurement, independent
outcome prediction, calibration, and randomized aid-benefit tests; low gaze
quality is an abstention reason, not a difficulty signal. The current
[implementation proposal](adaptive-visual-aid-proposal.md#confidence-and-weights-for-adaptive-decisions)
records how a future weighted policy would be learned and gated.
The [classification dictionary](scanpath-classification-map.md) and its
[table-form PDF](../output/pdf/newsmead-scanpath-classification-map.pdf) group
movement labels, ordered path patterns, empirical whole-path clusters, and
model-inferred phases. Those are different types of labels; sequence length
alone does not define their type. The dictionary names primary studies and
marks NewsMead examples as proposed descriptions rather than published classes.
The [visual-aid evidence map](scanpath-visual-aid-evidence-map.md) and its
[PDF table](../output/pdf/newsmead-scanpath-visual-aid-evidence-map.pdf) connect
those observations to tested aids and separates movement-matched results from
untested NewsMead trigger rules. Current closest aid evidence is for return-
sweep/next-line localization; automatic word magnification after refixation,
focus windows after slow line residence, and cues for spontaneous rereading
remain unvalidated. Preserve context and reader control during rereading.

The [public gaze-sequence capability-model survey](scanpath-capability-models.md)
checks EyeBench and individual LSTM, CNN, transformer, and HSMM precedents against
their actual labels, public code, validation splits, and NewsMead fit. EyeBench is
the strongest offline comparison framework, but its published models do not
validate a live aid-need detector for older phone readers. No candidate model
has been selected for the app, and no task-ready trained weights were verified.
The survey also covers phone front-camera precedents: Lepton, StrategicReading,
and Valliappan et al. A hardware-relevant study may use any text read on a phone;
it need not use news. These studies support testing coarse line/region signals,
but none supplies a public validated model that chooses real-time aid strength.
Lepton's reported comprehension correlation gain comes from scrolling features
in its leave-one-reader-out table, not gaze alone. Preserve that distinction.

MGazeNet has been integrated and optimized. Old July README descriptions and the
September 6 replacement handoff do not describe the latest runtime. Verify the
active provider in source before making tracker claims. Better visible tracking
does not by itself validate word assignment, fixation detection, or an index.

## Current measurement work

- Owner clarification on 2026-09-30: the pilot's objective is to build and evaluate
  the scanpath-to-visual-aid system, carrying forward existing gaze evaluation.
  The owner clarified that measurement tasks follow completion of the prototype.
  [Pilot revision 6](scanpath-measurement-pilot.md) is the current build/evaluation
  plan: six objectives, seven work packages, P01–P13 pattern rules and A0–A4 aid
  candidates, plus separately identified optional ruler/coloring forms. Build
  the Android causal word/line engine, pattern-to-aid policy,
  adjustable renderers with variable strength, reader controls and action/render
  logging first. Complete software replay/build checks and the matched evaluation
  instrument before running participant measurement. Phases then cover prompted feasibility,
  ordinary-reading shadow decisions and independent support-need reports,
  controlled aid/strength trials, then frozen varying-strength evaluation.
  Offline analyzer v5 now includes the [P01–P13 named detector layer](scanpath-detectors.md):
  word/line movements, geometric sweep candidates with explicit endpoint settings
  and verified LTR direction,
  revisit/frontier episodes, structural A–B–A–B cycles, residence/progression
  descriptors and breaks. Long/slow flags require explicit development settings.
  Live engine v1 now runs P01–P13 from the article's raw observation callback.
  Focused JVM tests and 13 synthetic cases/183 raw-action prefixes pass; common
  fixation fields and P01–P12 agree with Python, while live P13 resets immediately.
  The debug app builds and Android geometry tests compile; device execution,
  human validity and runtime timing are unmeasured. Layout/bidi caching and old
  geometry eviction were independently reviewed and corrected. The in-memory
  adapter uses callback-only event delivery and starts a new generation on
  text/locale changes or a 512-finalized-candidate resource cap, preserving the
  capture-time floor. Pattern events require finalization; a separate established
  ongoing-prefix contract can add observed time without incrementing refixations.
  `gaze/aid/` now implements a source-linked policy, independent displays,
  reader controls and bounded numeric action/render journal. Normal article
  reading selects this route instead of the legacy weighted controller/demo.
  Word magnification and optional focus strength vary under an explicitly
  experimental profile; focus requires cycle + residence + slow progression,
  never a cycle alone. Action audits are exported privately only from the menu;
  they do not contain raw gaze or replace the matched evaluation instrument.
  Focus new checks on sequence,
  classifier and aid behavior; do not make a broad gaze benchmark restart a
  prerequisite. Raw/smoothed and conditional-reference limits still apply.
  The owner is requesting a stronger research model from the public-model owner;
  artifact/compatibility/performance are unverified. Continue model-versioned
  sequence development and reevaluate affected measurements when supplied.
  Focused detector checks: 39 extraction/integration plus 13 classifier tests
  passed (52 total). Independent review findings on direction, episode phase/
  evidence and censored residence were corrected with regression fixtures.
  Evidence contract: `docs/scanpath-detectors.md`; tests:
  `tools/gaze/test_scanpath_patterns.py` and `test_analyze_reading_measurement.py`.
- The active scanpath preparation branch is `measurement/scanpath-pilot`, created
  from the same commit as `measurement/RII_2` while retaining pre-existing
  uncommitted changes. No scanpath measurement has been run on the device in this
  branch. The [phone-only scanpath measurement pilot](scanpath-measurement-pilot.md)
  specifies separate word and physical-line paths, explicit continuity breaks,
  known-target placement checks, and natural-reading evidence limits. It lists
  descriptive patterns to implement; cognitive labels and validated aid/intensity
  weights are not established. Review the analyzer's
  current `analysis_version` and focused tests before using its outputs. The
  structured v6 target score is based on smoothed displayed coordinates, whereas
  the natural-reading replay uses raw coordinates and raw-derived AOIs; v6's
  current score cannot pass a raw path placement gate. A separate matched raw
  target/order instrument supports evaluation of the completed prototype,
  without changing frozen v6. The offline registry/classifier is implemented;
  the integrated live engine/policy/displays/controls/action journal are built.
  Next build the matched raw evaluation instrument, then conduct authorized
  device checks and participant tasks. New participant measurement was not a
  prerequisite for prototype implementation. The blank
  record now covers pattern and aid outcomes, condition assignment and holdout
  allocation. Set participant counts, numeric tolerances and study conditions
  from the phase-specific plan before collection; they are not validated defaults.
- The [2026-09-30 pilot audit](scanpath-pilot-audit.md) corrected a timestamp-order
  reversal across replay boundaries and overstated accepted-sample coverage.
  Analysis v4 preserves a global capture timestamp, separates eligibility from
  accepted samples, and reports visit censoring, duration coverage, output age
  and terminal boundary events. Focused suite: 31 tests; independent review
  found no remaining issue in the fixes. Protocol revision 2 requires the same
  calibration through pre-check/read/post-check, current locked 22 dp font,
  matched raw placement plus continuous-order tasks, and a frozen sampling/
  material plan. Use the blank session record privately. This is diagnostic
  preparation, not validated natural-reading or aid behavior.
- Ordinary known-target structured validation is now protocol v6/schema 9: the
  orange gaze dot stays visible and the same article One Euro-smoothed
  coordinates drive display, word/line mapping, and scoring. Raw MGazeNet source
  coordinates remain logged separately. Interpret its output as closed-loop
  article-pipeline UI performance, not unaided estimator accuracy, and do not
  pool it with raw visible-dot v5 or hidden-dot v4 data. The frozen
  matched-comparison path remains raw hidden-dot v4. The interrupted raw-visible
  v5 diagnostic is retained at
  `diagnostics-local/reading_validation_session_20260921_114932_153.jsonl`; the
  preceding hidden-dot evidence is
  `diagnostics-local/reading_validation_session_20260920_190328_306.jsonl`.
- The source task **Explore feasible gaze metrics** reports one completed v6 run
  (`reading_validation_session_20260921_155148_059.jsonl`): 89.3% word-target
  exact-line, 47.5% exact-word, 61.6% guided exact-line, and 99.2% guided
  within-one-line at 22 dp, after recalibration with the visible smoothed dot.
  This is promising closed-loop known-target evidence, not unaided or natural-
  reading line-transition validation. Repeatability, font-size effects, and
  target-population performance remain open.
- `docs/reading-measurement-pilot.md`: specification for raw natural-reading JSONL
  recording and reproducible offline extraction. The requested orange gaze dot
  stays visible; scaffolds and the old adaptation feed are disabled during this
  recording mode. This is a diagnostic-dot condition, not an unassisted study.
- Raw and displayed coordinates are separate. Analysis uses capture timestamps,
  physical-screen geometry, and Android UTF-16 offsets. Coordinate/gaze validity,
  scrolling, changed geometry, ambiguous assignment, and unclassified gaps matter.
- `docs/reading-measurement-run-01.md`: first run is a plumbing/measurement
  diagnostic with user-reported vertical misalignment, not independent accuracy
  ground truth or index validation. The corrected analyzer breaks continuity
  across long unclassified inter-fixation intervals. Sparse retained transitions
  limit sequence claims; do not restore the earlier gap-bridging behavior.
- `tools/gaze/analyze_reading_measurement.py` and
  `tools/gaze/test_analyze_reading_measurement.py`: current offline analysis and
  regression checks. Check the actual test runner and current version before use.
- Candidate fixation, first-pass, rereading, regression, skipping, and scanpath
  features remain provisional. No final RII formula, weights, baseline, or
  replacement online controller is established by this pilot.
- Preserve explicit eligible denominators, undefined rates as `null`, ambiguous
  assignments, censoring and continuity breaks. Use the current analyzer CLI and
  tests from the pilot document; generic statistical utilities do not replace it.
- Repeated windows are not independent participants. Plan participant/session/
  passage separation appropriate to the target claim, account for clustering,
  and prevent evaluation leakage through normalization or future observations.
  Generic assumption-check reports do not establish independence or measurement
  validity; do not automatically remove or winsorize observations they flag.
- `docs/reading-stability-index.md` and `docs/adaptive-visual-scaffolding.md`
  explain the legacy line-event score and rolling controller. They are distinct
  from the new raw-coordinate measurement pipeline.

## Research judgment

Prior tasks discussed scanpath regularity and a personalized multivariate
deviation score. These are candidate approaches, not approved final methods.
Verify each paper's population, hardware, sampling, feature definition, outcome,
and validation split before adapting it. Do not turn an association into a causal,
clinical, or real-time detection claim. Do not assume unusual rereading is harmful;
deliberate careful rereading and tracker noise are alternative explanations.

Select reproducible measurements first; compare candidate indices to independent
labels/outcomes; evaluate scaffolds on reading benefit rather than simply a
reduction in the score used to trigger them. Retain the on-device deployment
objective. Older constraints include both A56 and SM-G991B relevance and no paid
EyeDid or required laptop/WiFi backend; revisit only if the user changes scope.

## Evidence entry points

Read `docs/progress-notes.md` from the top for recent implementation decisions.
Read a historical gaze handoff only when the task concerns that experiment.
Related workspaces are `../THS-ST-Paper` and `../gaze-thesis-prototype`; do not scan
or edit them unless relevant to the current request.

Task context checked during setup (retrieve only if a specific ambiguity remains):

| Task title | Task ID | Relevant context |
| --- | --- | --- |
| Check scanpath usage | 01a0ae5f-010b-7b83-9566-6634fb337b56 | Event counts versus ordered visits; scanpath proposal status |
| Review thesis gaze limitations | 01a09609-43ab-7b61-8640-d158dd5cdfb8 | Index validity and candidate deviation score |
| Review thesis gaze | 01a0a29f-cd3f-7de0-997c-b2f2e1ad5260 | Index first; flexible scaffolds; literature leads |
| Gaze Measurement Pilot | 01a0a2a3-8959-7be3-aa3c-02de33b98972 | Orange dot visible, scaffolds off; raw measurement implementation |
| Improve article reading FPS | 01a09405-e15c-7112-8dd1-770f1d1ed400 | Integrated MGazeNet and calibration keeper checkpoint |
| Explore feasible gaze metrics | 01a0be09-fe3e-76c2-827c-94dcfadcc416 | Proposal decisions, Mahalanobis comparison, v6 run, primary-paper discussion |

At setup, existing uncommitted pilot/analyzer edits were present. Preserve them;
this agent-framework task does not assess or adopt those edits as its own.


Owner wording clarification after implementation: “backreading” means revisiting
text before the saved frontier; advancement beyond that frontier removes the cue.
“Retains beyond30s” describes no automatic expiry during an artificial retention
fixture, never a mandatory30s display/hold. Both phones have ruleV6.
