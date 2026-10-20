# Agent context — updated 2026-10-20

This is a routing note, not a new research protocol. Recheck source and newer
dated evidence before continuing. It summarizes the project's recent tasks and
local documentation to avoid loading full histories each time.

Use the upstream `scientific-critical-thinking` skill for evidence appraisal and
`statistical-analysis` for statistical planning and interpretation. Their source,
revision and validation limits are in `docs/codex-agent-workflow.md`. The custom
project skills were retired at the user's request. This note and the measurement
protocol remain authoritative for NewsMead-specific decisions and tools.

## Objective and current direction

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
Current app APK SHA-256:
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
