# Refined gaze-triggered visual aids for NewsMead

2026-10-17. Research and design recommendations; no runtime changes. Main
population: older adults reading English news on a smartphone. Broader-population
evidence supplies bounded mechanisms and intervention precedents.

This consolidates the [trigger evidence and citation audit](scanpath-trigger-justification-2026-10-16.md)
and [current-aid review](gaze-visual-aid-rule-review-2026-10-16.md). It supersedes
their provisional design shortlist, while retaining their evidence and limits.
The independent pattern search remains relevant; the supplied five examples do
not define the scope. New aids below alter the presentation of original content.

## Trigger justification takes precedence

Owner corrected the response again: evidence for the **trigger** is the central
deliverable. The design sections below are secondary. A study showing that a cue
helps must not be substituted for evidence that a particular gaze pattern
identifies its intended process/need. The prior response overstated how far the
trigger defense was resolved.

| Trigger under discussion | Independent reference in the research | Defensible interpretation and unresolved rule |
| --- | --- | --- |
| Current-line support / proposed candidate-line search | W23 compared low-vision and sighted readers, quantified searched candidate rows, and collected reports of next-line difficulty. This is observational/convergent evidence; it did not independently label each search episode as lost place. | Current-line support locates a target; it does not establish difficulty. A transition-local candidate-row sequence is a proposed line-reacquisition predictor, with event-level specificity and thresholds still unvalidated. |
| Current1,500ms word residence / proposed prolonged word gaze | H16 compared gaze/text features with readers' known/unknown word labels. | This independently anchors vocabulary familiarity, supporting investigation of word-gaze duration as a predictor. It does not establish the present content-residence cutoff. A later return has separate S13 ambiguity-processing evidence, not evidence that the returned word is unknown. |
| Current three reported-unavailability episodes/30s or font-tier ABAB | No reviewed source independently establishes these predicates as inattention. F15 used thought reports; BD16 used thought-probe labels and multiple features, not technical absence counts. | The current exact attention trigger remains unsupported by the reviewed evidence. It must remain an original heuristic unless independently validated. Technical tracking loss cannot itself establish mind-wandering. |
| Proposed sentence-wide return and emerging rereading | MV11 manipulated garden-path ambiguity; whole-sentence rereading increased under the difficult condition, unlike brief/local checks. | This supports a condition-linked sentence-reprocessing hypothesis. It is a different target/detector, not a validation of the current focus rule. It does not diagnose universal confusion or prove successful repair. |
| Departure and return / current bookmark jump route | Cane, Cauchard & Weger2012 [original study](https://interruptions.net/literature/Cane-QJExpPsychol12.pdf) imposed known interruptions during reading and examined gaze recovery/spatial memory. | A qualified departure/return supplies a resumption opportunity, not proof of forgotten location. The lab interruption is independently known; app tracking loss and intentional within-text jumps are different events and cannot inherit that interpretation automatically. |

These distinctions should lead the research explanation: actual observed
predicate, independent label/manipulation, source finding, permitted inference,
then the proposed operational rule. Numerical thresholds require their own
development and independent checks. No aid-efficacy result appears in this table.

## Recommended decisions

| Existing aid | Refined target | Decision |
| --- | --- | --- |
| Line highlight | Spatial difficulty reacquiring a line during a transition | Test a search-conditioned guide; compare fill, underline and line-start marker as presentation variants. Current-line following remains an explicit comparison, not a new difficulty diagnosis. |
| Word-definition offer | Possible localized lexical effort/unfamiliarity | Keep optional acceptance; investigate individualized residence and later-return evidence. Separate semantic lookup from visual enlargement. |
| Focus window | Sentence-level reprocessing | Repurpose the candidate trigger around sentence returns and emerging rereading; keep the material required for the return visible. Repeated missing gaze and physical-line ABAB are not equivalent evidence. |
| Last-line bookmark | Resumption after departure/return | Keep saved place, improve the visible context cue, and separate external resumption from intentional within-text navigation. |

Four aids need not be escalation levels. A lexical offer and a line cue solve
different problems. Repetition alone supplies no evidence for a stronger mask.

## Current implementation baseline

A bounded source recheck on2026-10-17 found no material change to the four aid
rules since the earlier review. ArticleFragment selects `definitionPrototype()`
with `DEFINITION_PROFILE_V4`, `ARTICLE_SMOOTHED` and motion-compensated aids
(`ArticleFragment.kt:1317-1323`). The word offer uses1,500ms supported residence.
Focus uses three reported-unavailability/recovery episodes in30s, or font-tier
physical-line cycles; bookmark also has a non-same/non-next-line jump route.
Guide selection follows reliable current-line/sentence support without a
difficulty dwell threshold. Policy sources: `ReadingAidModels.kt:133-196`,
`ReadingAidPolicy.kt:252-288,435-451,637-645`, `ReadingFocusPolicy.kt:114-166`,
`ReadingBookmarkPolicy.kt:60-140`. Paths are under
`app/src/main/java/com/newsmead/`.

Live auto-scroll uses capture-aligned content-frame analysis. Its supported
residence/visits are not established physiological fixations. Existing camera
and scroll edits are unrelated and were preserved. The proposals below are not
descriptions of implemented behavior.

## 1. Line highlight: line-reacquisition guide

**Observed candidate:** established forward reading toward a line end, followed
by supported inspection of competing line-start regions or repeated checking
near the new line's start. Example: `L5 end -> L7 start -> L6 start`. The
subsequent forward progression is a release/confirmation signal; it must not be
used as if already known at activation time.

**Trigger justification:** Wang et al.2023 measured candidate-line searching and
documented next-line localization difficulty in low-vision readers [W23]. This
supplies a specific spatial problem. Slattery & Vasilev2019 manipulated bold
line-initial words and reduced corrective undersweeps [SV19], supplying a
placement/mechanism precedent. GazePrompt supplies related gaze-selected line
support [W24]. These studies do not jointly validate our proposed phone rule.

**Proposed rule:** qualify a line transition only with reliable source-line,
layout and supported sequence evidence. Activate during continuing candidate-row
search if adjacency makes the expected next line unambiguous. Otherwise wait for
destination support and describe the cue as orientation assistance. Withhold a
specific target when neither is reliable. A lone backward movement is insufficient
to distinguish search from deliberate review. Treat repeated short horizontal
undersweep corrections as a separate, hardware-demanding research feature.

**Design:** compare a subtle whole-line fill, a thin underline and a left-margin
notch/arrow. Keep the source layout fixed; use a non-color shape cue as well as
color. Move only after destination support rather than following raw jitter.
Clear the extra search cue after stable forward progression, departure or a
bounded timeout. Compare forms rather than display all of them together. Do not
convert more candidate visits into darker opacity without response evidence.

**Defense wording:** The guide targets observed line-reacquisition behavior.
Candidate-line searching is associated with difficulty locating the next line;
a localized spatial cue is proposed to reduce ambiguity at that transition.

**Evidence boundary:** navigation problem and related cue forms have evidence;
search-conditioned activation, appearance, release and phone performance remain
new hypotheses. Low vision is not interchangeable with ordinary aging.

## 2. Word offer: localized-effort assistance

**Observed candidates, evaluated separately:** (a) unusually prolonged supported
residence on an identifiable word relative to appropriate reader/text baselines;
(b) forward progress beyond that word, then a supported return accompanied by
renewed local hesitation. A normal short return alone is insufficient evidence
for this proposed offer.

**Trigger justification:** Hiraoka et al.2016 used reader-marked known/unknown
words as an independent reference for gaze/text features [H16]. Stites et al.2013
found later ambiguity rereading in older adults [S13]. Together they motivate
investigating sustained and delayed effort; they do not establish that the two
patterns uniquely identify missing vocabulary or validate their combination.

**Design:** start with a small word underline/help affordance; retain the current
offer-before-definition interaction. Offer a dictionary for meaning and a lens
for visibility. Keep the source sentence readable and the offer away from the
next words. Acceptance is a distinct action; gaze-based activation does not
require a fully hands-free confirmation. If gaze confirmation is tested, give it
a separate sufficiently large target, not the word being naturally read.
Dismiss when ignored/reading moves on; rearm only after a fresh qualified episode
or explicit request. Do not repeatedly reopen help on an unresolved long visit.

**Threshold refinement:** use the current1,500ms rule as a labeled comparator.
Investigate baseline speed, word length and lexical rarity with separate
development data, then test independently. Their inclusion is a proposed
operational model, not an already fitted predictor. Preserve separate results
for prolonged residence and later-return branches. If exact-word assignment is
uncertain, abstain from a specific dictionary offer; coarse phrase enlargement
can be assessed separately if phrase assignment is dependable.

**Defense wording:** The offer targets possible localized lexical effort.
Unknown-word research supplies an independently labeled gaze-feature precedent;
older-reader ambiguity findings motivate including delayed reinspection. Optional
acceptance accommodates multiple reasons for hesitation.

## 3. Focus window: sentence-reprocessing band

**Observed candidate:** an established sentence pass reaches later material,
returns toward the same sentence's beginning, and begins a sustained forward
reread. A repeated alternation between arbitrary physical rows is not the same
observation. Only the observed prefix is available to an online detector.

**Trigger justification:** von der Malsburg & Vasishth2011 associated broad
sentence rereading with experimentally manipulated syntactic garden paths [MV11].
Their finer analysis separated whole-sentence rereading from local checks.
Older-reader later ambiguity effects add population-relevant context [S13].
The intended process is reprocessing; the cue's helpfulness at those moments is
an intervention hypothesis, not a finding of either study.

**Design:** lightly outline or shade the sentence span being reprocessed. Keep
all earlier/later material legible; when the reader returns beyond the span,
expand to include the supported earlier context or release the band. Test an
outline first, with gentle surrounding attenuation as a separate variant.
Replace an arbitrary fixed row radius with a text-unit-aware span when mappings
are reliable. Release after supported progress beyond the span or deliberate
navigation elsewhere. Avoid a moving aperture chasing every sample.

**Change to the present rationale:** three unavailable episodes in30s and the
font-tier ABAB cycles remain existing experimental heuristics. They should not be
relabelled sentence reanalysis or mind-wandering. The proposed focus revision
would use a different detector. Attention-lapse research with thought-probe
labels is a distinct possible research route, with greater modeling requirements
[F15/BD16].

**Defense wording:** The band supports a detected sentence-reprocessing attempt
by visually grouping the original material being revisited. It is intended to
facilitate rereading, not suppress it or diagnose comprehension failure.

## 4. Bookmark: saved-place resumption cue

**Observed candidate:** stable reading position -> qualified departure -> return.
Keep reliable outside-article coordinates, no-face evidence and technical
unavailability distinct. A cue can also be offered after uncertain tracking loss,
but that episode must be labeled uncertain, not confirmed distraction.

**Trigger justification:** SwitchBack directly used a front-camera mobile
departure/return chain for resumption assistance [SB15]. EyeBookmark tested
sentence/block cues after imposed interruptions [EB15]. Their designs connect
the event to a location-memory/search demand and a matched cue. Benefits were
bounded; neither establishes native older-adult phone performance for this app.

**Design:** save a stable text anchor with its sentence/line context. Show the
gutter marker plus a short saved-line or sentence highlight on return. Avoid
asserting an exact last-read word when only a line is supported. If the anchor
is off-screen, show a quiet directional edge marker without forcibly scrolling.
Fade after reacquisition/reading progress, an intentional new reading location,
or a timeout. A brief timeout remains a parameter to investigate, not a copied
validated dose.

The current uninterrupted non-next-line-jump route should be tested as a separate
within-text history behavior. It does not establish loss of place. Jensen et
al.2025 supports the related idea of preserving place when typography changes
[J25], not the inference that such changes are warranted by fatigue.

**Defense wording:** Following a departure, the saved-place cue externalizes the
previous reading location and supplies context for resumption, addressing the
search demand created by interruption.

## Additional visual aids to try

| Candidate and priority | Proposed gaze event -> visible behavior | Why that event and visual response fit; remaining gap |
| --- | --- | --- |
| **Word/phrase magnifying lens — first additional prototype for text-only news** | Localized hesitation/return creates a small lens offer. Acceptance displays an enlarged copy of the original word/phrase in a stable nearby panel with an anchor to the source. Original layout stays fixed. An ignored offer can expire. After acceptance, treat the source anchor and lens region together as the active target; keep the lens during supported inspection of either. Close on explicit dismissal, confirmed departure from both or bounded inactivity. | W23 identifies word-recognition difficulties; W24 implements gaze-aware word magnification. W18 shows visual quality can affect older readers, which helps explain why hesitation has multiple causes. Gaze alone does not distinguish visual recognition from lexical effort. Phrase targeting and this offer rule are new. A related magnifier in older app profiles is not evidence that the present definition profile already supplies it. |
| **Paired text–chart outlines — strong direct system precedent, conditional on illustrated articles** | Supported reading of an explicitly mapped chart-reference phrase/sentence outlines the corresponding chart element and its source reference. Refresh on meaningful reinspection; release on another target or leaving the pair. | Lallé et al. tested reference-text-triggered chart outlining [LC21]. This gives an actual gaze rule and response precedent without inventing a rapid-switching diagnosis. Correspondences must be authored/verified independently of gaze. Effects varied by visualization literacy and used a historical control comparison. |
| **Structure-return trail — optional new within-text behavior using bookmark-style marks** | Supported section-end/body -> existing heading/topic return leaves one subdued departure tick and marks the confirmed structural destination. Retain only that pair; clear on resumed progress or deliberate new navigation. | Hyönä et al. found selective structure-related look-backs associated with better summaries [H02]. This supports recognizing purposeful structural review. The markers are a new spatial-memory aid hypothesis; the studies did not demonstrate that such readers need or benefit from it. Requires real headings or independently annotated structure; no generated summaries. |
| **Localized spacing adjustment — later exploratory prototype** | Repeated qualified candidate-line-search episodes offer a spacing adjustment. On acceptance at a pause, modestly separate the active paragraph's lines while preserving the source-word position. Start again with fresh layout mapping. | W23 grounds a line-localization problem. It does not validate spacing as the treatment; SV19 tests line-start bolding instead. This is a more speculative alternative to a fixed-layout cue. Test one typography change at a time because reflow can itself cause place loss; J25 informs context preservation after a change. Do not call the trigger fatigue detection. |

Line-start arrows, underlines and different resumption highlights are useful
variants of the four existing aids. They should not be counted as several new
independent interventions merely to enlarge the shortlist.

## Shared presentation and rule requirements

- Use the coarsest dependable target. Line support does not establish word
  support. Reliability/eligibility is a gate, not evidence of human need.
- Evaluate stationary and auto-scrolling reading separately. Use capture-aligned
  text coordinates and supported elapsed residence. Missing samples are not
  skipped words, and a screen-coordinate change during scroll is not a regression.
- Preserve event order and invalidate an interpretation when layout/target
  continuity is no longer supported. Do not fill an uncertainty gap with a
  hypothetical gaze path or use future reading to justify an earlier activation.
- Stabilize targets, use bounded offers and require fresh evidence to rearm.
  These engineering controls need tuning; they are not published cognitive limits.
- Treat gaze at aid controls, a dictionary panel or a lens as interface
  interaction. Do not count that gaze as an external interruption, skipped text
  or fresh evidence of reading difficulty; preserve the source anchor during it.
- At a given moment, keep one primary text emphasis. A resumption cue can take
  temporary priority, then yield to line or sentence support. This arbitration is
  a proposed design to compare, not a validated four-level hierarchy.
- Specify visual changes using both luminance/contrast and geometry; allow reader
  preference. No source establishes the current gray/opacity or a dose that
  increases with a loop count. Retain readable surrounding text and useful returns.

## What to assess before claiming a justified adaptive rule

For each aid, separately establish (1) whether the observable event is detected,
(2) how it relates to independently labeled need/process, and (3) whether the
matched visual change helps at that event. Detector labels must not be defined
by the same heuristic being evaluated. Include intentional review, scanning,
ordinary corrections, blink and tracker failure as relevant alternative cases.

Measure line-search or resumption time for spatial cues; accepted/requested help
and unwanted offers for lexical assistance; independently assessed understanding,
ease of rereading and comfort for the sentence band. Record correct cues/all
shown cues, detected opportunities/all independently labeled opportunities,
unwanted cues per usable reading minute, and usable coverage/missingness. Exposure
does not prove comprehension. Fewer regressions alone are not improvement.

Keep development/tuning readers and passages separate from independent checks.
Compare matched cue/no cue at comparable events and relevant fixed/static forms.
Software replay can check targeting logic and rendering, but cannot establish
human validity. No participant collection, device operation or new statistical
analysis was authorized or performed for this recommendation document.

Recommended implementation/research sequence: refine line and resumption cues;
then evaluate word offers and a visual lens where word/phrase mapping qualifies;
then the sentence-reprocessing band. Chart outlining is a parallel candidate if
appropriate annotated articles exist. Structure trails and spacing remain later
exploratory options. This ordering reflects evidence proximity, distinctness and
measurement requirements, not a measured ranking of efficacy.

## Primary evidence register

Full extraction, additional studies and retrieval limits remain in the linked
earlier reviews. The short register below identifies the evidence used here.

| ID / primary source | Population, apparatus and limits relevant to transfer |
| --- | --- |
| **W23** — Wang et al.2023, [Understanding How Low Vision People Read Using Eye Tracking](https://arxiv.org/html/2303.16346) |20 low-vision readers19-86 and20 sighted controls; desktop TobiiProFusion120Hz. Different vision, age and typography conditions constrain an aging inference. Candidate-line search and word-recognition observations inform specific hypotheses. |
| **W24** — Wang et al.2024, [GazePrompt](https://arxiv.org/html/2402.12772) |13 low-vision readers30-87; desktop120Hz. Line-support switching-time benefit; no significant switching-accuracy/word-error gain. Word thresholds adjustable;10/13 lengthened durations. Different magnification/TTS support and hardware constrain dictionary/phone transfer. |
| **SV19** — Slattery & Vasilev2019, [An eye-movement exploration into return-sweep targeting during reading](https://doi.org/10.3758/s13414-019-01742-3) |32 adults19-63; desktop EyeLink1000Hz. Static bold line-initial words reduced corrections, without page-reading-time improvement. Dynamic activation is new. |
| **H16** — Hiraoka et al.2016, [Personalized Unknown Word Detection](https://doi.org/10.1145/2993148.2993167), [institutional paper](https://ahcweb01.naist.jp/papers/conference/2016/201611_ICMI_Hiraoka_1/201611_ICMI_Hiraoka_1.paper.pdf) |12 Japanese graduate L2-English readers recruited,10 retained. Reader-marked unknown words, not native-English older readers. Gaze duration/text features support an effort/unfamiliarity hypothesis with imperfect specificity. |
| **S13** — Stites, Federmeier & Stine-Morrow2013, [Cross-age comparisons reveal multiple strategies for lexical ambiguity resolution](https://pmc.ncbi.nlm.nih.gov/articles/PMC3883559/) |18 young adults18-26 and18 older adults62-83; controlled ambiguity/context. Later rereading costs in older readers do not establish an unknown word or a specific aid benefit. |
| **MV11** — von der Malsburg & Vasishth2011, [What is the scanpath signature of syntactic reanalysis?](https://tmalsburg.github.io/MalsburgVasishth2011.pdf) |44 undergraduates; Spanish attachment ambiguity. Retrospective sentence-path classes support a process association, not every local return or a live focus detector. |
| **SB15** — Mariakakis et al.2015, [SwitchBack](https://faculty.washington.edu/wobbrock/pubs/chi-15.03.pdf) |17 adults19-52; XperiaZ front camera, landscape news. Positive distraction-subset comparison after a marginal overall interaction; sequential-reading assumptions and population constrain transfer. |
| **EB15** — Jo, Kim & Seo2015, [EyeBookmark](https://idclab.skku.edu/assets/EyeBookmark.pdf) |13 university adults20-28; desktop TobiiX60/chinrest; imposed video interruptions. Sentence/block cues reduced resumption lag; comprehension advantage was not established. |
| **LC21** — Lallé, Toker & Conati, [Gaze-Driven Adaptive Interventions for Magazine-Style Narrative Visualizations](https://doi.org/10.1109/TVCG.2019.2958540), [manuscript](https://arxiv.org/pdf/1909.01379) |63 adults18-59; desktop TobiiT120,120Hz; prior56-person comparison. Reference mapping and piloted fixation threshold were supplied; rapid switching was not a diagnostic trigger. |
| **H02** — Hyönä, Lorch & Kaakinen2002, [Individual differences in reading to summarize expository text](https://doi.org/10.1037/0022-0663.94.1.44) |47 university readers initially analyzed;40 consistent-profile readers,37 in main summary contrasts after excluding three nonselective reviewers. Desktop250Hz. Strategy/outcome association, not deficit detection or marker efficacy. |
| **W18** — Warrington et al.2018, [Effects of aging, word frequency, and text stimulus quality](https://irep.ntu.ac.uk/id/eprint/42083/) |Young/middle/older adult groups; controlled faint/normal text, dedicated desktop eye tracking. Visual-quality-related effort provides a possible alternative explanation for hesitation, not a gaze-based contrast/size prescription. |
| **J25** — Jensen et al.2025, [context preservation study](https://doi.org/10.1145/3715669.3725897) |22 desktop participants, Tobii4C90Hz; highlights after typography changes. Benefit specific to gradual-change condition. Not fatigue-triggered spacing or mobile interruption evidence. |
| **F15/BD16** — [Frank et al.2015](https://libres.uncg.edu/ir/uncg/f/M_Kane_Validating_2015.pdf), [Bixler & D'Mello2016](https://doi.org/10.1007/s11257-015-9167-1) |Thought-probe-labeled gaze associations/models motivate a distinct attention hypothesis. They do not validate the current unavailable-episode/physical-line-cycle rules. Full method limits are recorded in the earlier review. |

## Checks and ownership

Lead owns this document and the concise checkpoint update. Source-only scout
confirmed the current runtime baseline. Public primary records/manuscripts and
existing extracted evidence were used; some direct retrievals failed, so
inaccessible tables or methods were not filled in. Focused review corrected the
lens release rule: gaze into the accepted lens must count as continued use, not
departure from its source. Three reviewer source fetches failed; those estimates
remain grounded in earlier primary extractions rather than a new independent
reverification. No remaining design finding was reported. No source-code, measurement protocol,
camera/device/install, collection, build, staging or commit change by this task.
