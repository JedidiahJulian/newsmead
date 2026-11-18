# Reading aids during scrolling

## Manual-scroll extension — 2026-11-18

The live article now opts into `manualScrollAids` alongside its existing
content-frame aid adapter. The previous adapter gave every manual translation a
new geometry ID, cleared capture geometry history and restarted the 150 ms settle
guard. Existing guides/focus could remain visible, but fresh gaze could not
retarget them until scrolling ended.

The article observes single-pointer touch without consuming events. DOWN arms
the manual gate; the first compatible vertical movement creates one new geometry
boundary and settle interval. Further same-layout translations retain that ID
and append causal geometry, allowing aids to follow fresh supported word/line
targets while text moves. UP retains authorization only for already-started,
continuous fling motion. A gap of at least 150 ms since the last translation
expires that authorization. A tap without scrolling does not authorize a later
page jump. Cancellation/multiple pointers, reflow, text/locale/font/padding or
horizontal geometry changes, unavailable geometry, consultation, controls,
lifecycle boundaries and auto-scroll takeover cancel manual provenance.

The existing causal capture geometry, maximum geometry age, input freshness,
visibility and residence rules remain. No future-position substitution or new
velocity threshold is used. Raw and selected coordinates stay unchanged. A
fixed screen point crossing moving text is not automatically supported word
residence. Geometry cannot prove the physical source of a same-layout movement
inside an authorized gesture/fling interval; known programmatic navigation must
retain its explicit boundary/cancellation calls.

This is a live aid-only extension, condition
`SMOOTHED_ARTICLE_CONTENT_AIDS_V12`, with
`manual_scroll_configuration` and gesture start/end/cancel trace records. The
existing content-coordinate algebra/mode remains; the new condition and events
distinguish the manual-motion input population. Pilot/Replay default adapters,
the stationary R/D/F adapter, coordinate MD, model, calibration, smoothing,
classifier thresholds and frozen protocols are unchanged. These remain
experimental aid-trigger inputs, not validated physiological fixations or
reading-difficulty outcomes.

Lookup offers retain their existing geometry/click safety: a moving source clears
the bubble rather than leaving a clickable icon at its old position. Manual
word evidence can remain supported even though the offer clears. Automatic
navigation pauses on an offer, so the stationary-offer expectation from the
auto-scroll fixture must not be applied to a continuing finger drag.

Checks: pure gesture-gate regressions, existing scanpath/aid/navigation JVM
regressions, app build, and Android-test compilation. New synthetic Android
fixtures cover delayed pursuit/retargeting, drag/fling continuity, external and
layout invalidation, stale/missing history, fixed-screen crossing, line/sentence
guide rendering and sustained-word lookup. Their supplied gestures and positions
do not establish real finger dispatch, camera accuracy or human benefit. Device
execution is separate; evidence/status is retained in
`output/manual-scroll-aids-2026-11-18/`.

The owner-authorized phone follow-up installed the build on S21 and A56. One
bounded 12-check run per phone passed 10 checks, including actual attached
line/sentence retargeting during supplied manual drag/fling and all five existing
auto-scroll cases. The reflow fixture assumed immediate layout recovery, and the
lookup fixture expected the moving-source bubble to remain visible; both tests
failed. Their corrected local sources compile but were not rerun on phones.
Native guide screenshots were visually inspected on both devices. Camera access
was blocked for tests then restored; saved calibration hashes were preserved.
See `output/manual-scroll-aids-phones-2026-11-18/verification.md`. Actual reader
finger dispatch and camera-based usability still need the owner's live check.

On the owner's explicit request, the corrected suite was subsequently rerun:
**S21 12/12 and A56 12/12 passed (24 total)**. No production behavior or guard was
changed for this rerun. Main/test installed hashes, preserved calibration/camera
state and visually inspected guide PNGs are retained under
`output/manual-scroll-aids-phones-rerun-2026-11-18/`. Earlier failed-run evidence
remains unchanged. The tests still use supplied gestures/observations; live reader
interaction remains a separate check.

## Status — 2026-10-16

The owner reports continuous scrolling works but aids do not activate during it.
Source confirms two blockers: motion observations were excluded and each
translation replaced geometry identity/reset pending evidence. The new live-only
content-frame aid adapter is implemented and source-reviewed. The approved
corrected retry passes **273/273 focused JVM checks** across 31 suites and builds
both APKs. Both APKs are installed on the A56 using replace installs, preserving
app data and calibration. **All five aid integration checks pass in 14.222 s**:
line/sentence guides, lookup, focus, bookmark and external-motion invalidation.
Navigation speed rules remain V3. The five existing navigation checks passed on
the first attempt; they were not rerun on the corrected APK.

Source diagnosis confirms a bookmark defect: startup timer-only invalid context
advanced the capture floor to wall time, blocking a delayed first sustained
prefix. The saved correction advances it only from actual finite capture times;
explicit boundary floors remain. Both policy regression fixtures pass. The focus
fixture now uses explicit 24 dp rendered STANDARD size and checks its rule before
input; 22 sp plus system scaling was an uncontrolled test condition. It is a
possible cause of the first focus failure, not proved by that artifact. Focus now
passes with the controlled fixture; production font rules were not changed.
Final corrections passed source review before the approved retry. Current evidence:
`output/auto-scroll-reading-aids-check-02/summary.json` plus JVM/build/native/install
logs. The first attempt's 8/10 result and failures remain in
`output/auto-scroll-reading-aids-check-01/`. No camera or human collection ran.

## Input and display contract

1. The orange dot and recorded selected input remain the calibrated, smoothed
   physical-screen coordinates. Raw diagnostics also remain unchanged.
2. A bounded two-second geometry timeline selects the last observed text geometry
   at or before each capture timestamp. It never substitutes delivery-time geometry
   or interpolates across an unobserved change. Capture-to-snapshot age must be
   at most100 ms; the capture itself must be at most250 ms old.
3. Explicit analysis coordinates are selected screen X/Y minus that captured text
   origin. These are text-local pixels. The engine's opt-in geometry has zero
   origin/full text bounds and a stable layout ID during controlled translation.
4. Following one moving word can therefore accumulate content residence; a fixed
   screen point over changing words does not automatically become word residence.
   A moving-text cluster is an experimental content-residence proxy, not a claim
   that the eye was physiologically stationary.
5. For policy visibility checks and rendering, the supported content anchor is
   projected into the current display geometry. Its original source offsets stay
   attached to the text. The recorded input point is not replaced by this projection.

The timeline is bounded to4096 snapshots as an additional resource guard.
Geometry timestamps are main-thread observed view positions, not hardware display
presentation timestamps. Frame timing, model error, lag and smooth-pursuit behavior
still limit physical accuracy; the100/250 ms settings are engineering guards.

## Aid behavior

| Aid | Existing rule retained during auto-scroll |
| --- | --- |
| Line/sentence guide | Established supported line prefix selects the guide in current display geometry |
| Word lookup | Same word's supported residence reaches the existing1500 ms minimum, then offers the icon |
| Focus window | Existing font-aware supported line alternations or repeated qualifying tracking interruptions |
| Last-reading bookmark | Existing supported-reading departure/loss rule; missing gaze stops actual scrolling |

A lookup offer or open definition panel still pauses navigation so its target
does not move away from the reader. No automatic definition panel or network
request is introduced by offering the icon. The original font-aware focus counts,
look-away counts, definition duration and strength rules are unchanged.

In V11, only controller-declared self-scroll retains layout identity. Manual/external
scrolling, text replacement, reflow, font/spacing/padding changes, missing geometry,
menus and consultations retain their discontinuity/input gates. The controller
checks movement before a gaze sample is interpreted. External/layout stop reasons
also force history invalidation and an engine boundary even if geometry was
already observed, preventing old evidence from bridging an external jump.
The V12 extension above additionally authorizes explicit manual gestures;
unannounced external movement outside that interval retains the original gates.

## Research and trace limits

Live article condition: SMOOTHED_ARTICLE_CONTENT_AIDS_V11.
Analysis mode: auto_scroll_content_frame_v1. Trace inputs retain processing/raw
screen points and add captured text origin/time, text-local analysis coordinates,
and current presentation origin. Configuration marks motion-compensated aids and
the content-residence interpretation. The frozen Pilot/Replay adapter defaults
remain screen-frame processing with their original geometry settling behavior.
No calibration, numeric smoothing or classifier thresholds change.

These aid observations must not be pooled as validated stationary-text fixations,
regressions or diagnostic difficulty labels. The existing pattern code supplies
operational trigger descriptions in a new coordinate condition; that condition
requires its own human measurement/benefit validation. The user request authorizes
making aids operational while scrolling, not a new scientific validity claim.

## Checks

Focused source review corrected an external-scroll callback-order edge and found
no remaining actionable issue. Pure tests cover causal geometry lookup,
preservation of selected input, content pursuit, fixed-screen crossing, malformed
metadata, frame changes and projection abstention. Five new attached Android
fixtures use real40 ms observations with80 ms capture lag to exercise actual
controller/session/engine/policy/rendering. They cover guides, lookup, line-cycle
focus, tracking-loss bookmark and forced external-motion invalidation.
The corrected retry passes all five attached fixtures. They run in a Replay-hosted
article layout with synthetic inputs and the production controller, session,
engine, policy and renderer; they do not start ArticleFragment or a gaze provider.
Complete live routing, human gaze accuracy and reading benefit still require
actual reading checks. No camera or human collection is part of these tests.

## Main files

- scanpath/ScanpathMotionCompensation.kt, ScanpathModels.kt, ScanpathEngine.kt
- scanpath/ArticleScanpathSession.kt
- aid/ArticleReadingAidRuntime.kt, ReadingAidModels.kt, ReadingAidTrace.kt
- aid/ReadingAidPolicy.kt, ReadingFocusPolicy.kt, ReadingBookmarkPolicy.kt
- fragments/article/ArticleFragment.kt
- AutoScrollReadingAidIntegrationTest.kt, ScanpathMotionCompensationTest.kt,
  ReadingAidMotionContextTest.kt and ReadingAidBookmarkPolicyTest.kt
