# Gaze-based visual aids: rule justification and recommended revisions

2026-10-16. Research/design review only; no runtime or protocol changes.
Target: mainly older adults reading English news on a smartphone, with evidence
from other populations considered explicitly. The owner confirmed this scope.
Source baseline: clean `measurement/scanpath-pilot` working tree at review start.

**Scope clarification:** the owner subsequently emphasized that defending the
scanpath-to-problem-to-aid trigger is the central need. Use the
[trigger-justification review](scanpath-trigger-justification-2026-10-16.md) for
that reasoning. The general aid/form evidence below is supplementary; an
always-on/manual alternative does not answer the adaptive-trigger question.

The most defensible direction is **spatial reading assistance selected by gaze**.
Line guidance and interruption bookmarks have direct gaze-based intervention
precedents. Word lookup has a plausible assistance rationale but needs separate
evidence of vocabulary need. The current automatic focus triggers remain original
experimental hypotheses. A published association between gaze and processing does
not establish that a particular visual aid will help at that moment.

## What the current app actually does

Production selects `definitionPrototype()` / `DEFINITION_PROFILE_V4` in
`ArticleFragment.kt:1272–1277`. These details supersede older proposal defaults.

| Aid | Current selection rule | Research status / main concern |
| --- | --- | --- |
| Line highlight, with optional sentence context | Established current-line candidate/finalized support matching current input; no long-dwell requirement. Fixed adaptive strength 0.25, capped by reader preference; gray background alpha 20/255, approximately 7.84% actual opacity. Sentence selection uses text segmentation and the supported line; ambiguity can select its containing paragraph or withhold. | The navigation function has precedent. Gray, opacity, support thresholds and automatic phone targeting have not been validated as beneficial. Sentence context is already available, rather than a wholly new suggestion. |
| Word-definition offer | At least 1,500 ms supported observed residence on the current mapped word. A five-second information bubble offers lookup; a tap opens the dictionary. Ignoring it does not query the dictionary. | Gaze initiates the offer; opening is a hybrid gaze-and-touch interaction. Residence is not proof that the word's meaning is unknown. Exact word localization and dictionary sense are separate uncertainties. |
| Focus window | Three qualifying **reported-unavailability/recovery episodes** within 30 s; loss at least 500 ms, return presence at least 100 ms / three samples. Alternative route: qualified A–B–A–B line visits, each cycle within 8 s, completed cycles counted within 30 s. Rendered size >=22 dp requires one cycle / baseline 100 ms visits; 18–<22 dp two cycles / 160 ms visits; <18 dp three cycles / 240 ms visits. | These are not validated distraction rules. The loss set includes tracker failures; explicit blink states do not count. The cycle rule can capture legitimate rereading or line-assignment error. Font tiers are engineering safeguards. |
| Last-line bookmark | Stores last supported source line. Appears after >=100 ms / three supported outside-article captures, >=500 ms reported unusable gaze timed from delivery, or uninterrupted supported movement to a line other than the same/next line. | The interruption-location function has precedent. Exact timers, the gutter marker and the jump route are unvalidated. An intentional revisit does not establish loss of place. |

Source locations: `gaze/aid/ReadingAidPolicy.kt:149,227,272,443`;
`ReadingAidModels.kt:133–193`; `ReadingFocusPolicy.kt:80–178`;
`ReadingFocusTypography.kt:6–18`; `ReadingBookmarkPolicy.kt:39–140`.
Paths are under `app/src/main/java/com/newsmead/`. Relevant current contracts:
[guide](reading-aid-menu-and-sentence-guide.md),
[lookup](article-definition-aid.md),
[focus](repeated-interruption-focus-window.md),
[bookmark](scanpath-reading-bookmark.md).

The current article enables capture-aligned, content-coordinate aids during
its own auto-scroll. Those residence/visit measures are **content-residence
proxies**, not established physiological fixations. Stationary and moving-text
conditions require separate validation; neither the scroll software checks nor
detector eligibility proves line/word accuracy. See
[motion contract](auto-scroll-reading-aids.md).

## Primary evidence and its actual reach

This is a focused evidence review, not an exhaustive systematic review. Study
counts refer to participants, not numbers of gaze samples or repeated trials.

| Source / primary location | Population, apparatus and design | Finding and permitted inference |
| --- | --- | --- |
| **W24:** Wang et al. (2024), [GazePrompt](https://arxiv.org/html/2402.12772), §§3.1–3.3, 4.1, 4.3.2–4.3.3 | Controlled study: 13 low-vision adults, ages 30–87; enlarged desktop text read aloud, Tobii Pro Fusion 120 Hz, 24-inch display. Within-reader aid conditions; highlight/arrow form chosen by reader. | Line support reduced switching time without significant deviation-frequency/magnitude improvement. It followed the current line, detected a return sweep, or waited for a stable jump destination. Word-assistance **defaults** used one-pass first fixation >500 ms **OR** refixations >4 **OR** total fixation time >1,500 ms. Duration settings were adjustable; 10/13 readers increased them to reduce false positives. This is a navigation/word-assistance precedent, not validation of phone timing, semantic definitions or a difficulty-to-opacity scale. |
| **SB15:** Mariakakis et al. (2015), [SwitchBack author paper](https://faculty.washington.edu/wobbrock/pubs/chi-15.03.pdf), PDF pp.4,6–8 | 17 adults, ages 19–52; Sony Xperia Z front camera, landscape phone, New York Times articles; within-reader interface/distraction/posture conditions. Face-loss confirmation used a two-second window; saved-line highlight ended five seconds after return. | In the distraction subset, reported +19 WPM / 7.7% versus control; the overall interface × distraction interaction was marginal (p=.09), with a significant follow-up subset comparison. This is relevant mobile resumption evidence with statistical and transfer limits. It assumed sequential line advancement and did not handle multi-line rereading; do not import its line counter or treat 3.9% line-count error as exact-line accuracy for NewsMead. |
| **EB15:** Jo, Kim & Seo (2015), [EyeBookmark](https://idclab.skku.edu/assets/EyeBookmark.pdf), Figure 1 / Table 1, PDF pp.2–4 | Main study: 13 Korean university adults, ages 20–28; Tobii X60 / 24-inch desktop / chin rest; counterbalanced sentence/block/no-cue conditions after imposed 60-second video interruptions. | Sentence/block context reduced resumption lag; comprehension did not significantly improve. Supports testing a richer resumption cue, not declaring a gutter bar, spontaneous jump, or repeated-loss focus trigger effective. |
| **N23:** Niklaus et al. (2023), [Digital Reading Rulers](https://thereadabilityconsortium.org/wp-content/uploads/2023/08/Digital-Reading-Rulers-Evaluating-Inclusively-Designed-Rulers-for-Readers-With-Dyslexia-and-Without.pdf), §4.4 / Table 4, PDF p.11 | 177 desktop crowdworkers: 91 classified with dyslexia and 86 without; mouse-controlled rulers, no gaze tracking. | Grey Bar improved speed versus no ruler in the dyslexia group. No significant comprehension effect in that group; no significant speed/comprehension effect in the other group. Lightbox preference did not establish superior performance. Supports alternative forms and reader choice; a gaze-controlled version and an older-adult benefit require testing. |
| **V26:** Vatány et al. (2026), [publisher PDF](https://journals.uc.edu/index.php/vl/article/download/8818/7090/13184), pp.63–67,71–78 | 80 regular digital readers; standardized MacBooks; hover-controlled line/sentence/paragraph versus static text. Eligibility was 18–75, but reported participant age bins were 18–25,25–40,40–60. This is not an older-adult sample. No eye tracker. | No significant reading-time/comprehension differences detected. Self-reported attention ratings favored sentence/paragraph conditions in the authors' analysis; this is not objective attention or a validated gaze trigger. Table 5 gives response-category comparisons, not a direct causal test that gaze-selected sentences outperform lines. |
| **SV19:** Slattery & Vasilev (2019), [author manuscript](https://eprints.bournemouth.ac.uk/32336/25/Slattery-Vasilev2019_Article_AnEye-movementExplorationIntoR.pdf), methods/results pp.2–6; [DOI](https://doi.org/10.3758/s13414-019-01742-3) | 32 normally sighted/corrected adults, ages 19–63; desktop EyeLink 1000 at 1,000 Hz with head support; line-initial words permanently bolded versus normal text. | Bolding shifted return-sweep landing and reduced corrective undersweep probability, without improving page reading time. Mechanism/placement evidence; static emphasis does not validate a dynamically timed gaze cue. |
| **ST14:** Schotter, Tran & Rayner (2014), [primary journal abstract](https://www.psychologicalscience.org/journals/psychological-science/0956797614531148/); [DOI](https://doi.org/10.1177/0956797614531148) | Controlled gaze-contingent experiment masking words after the eyes passed them. This review uses the primary abstract; sample/apparatus details were not re-extracted. | Preventing useful regressions impaired comprehension. This challenges an assumption that rereading should always be suppressed; it does not directly estimate the effects of NewsMead's partially dimmed 4–5-line window. |
| **PT13:** Paterson et al. (2013), [indexed primary abstract](https://pubmed.ncbi.nlm.nih.gov/23066801/); [DOI](https://doi.org/10.1037/a0030350), article pp.352–364 | Young adults 18–30 versus older adults 65+; gaze-contingent normal-text window with surrounding text filtered by spatial frequency. Full methods unavailable in this review; N, tracker and sampling rate were not verified. | Older readers were affected by filtering over a wider surrounding region than younger readers. This supplies an age-specific perceptual rationale for caution about removing surrounding information, not proof that a particular focus aid helps or harms. Character-window findings cannot specify a 4–5-line phone window. |

**Dictionary-specific lead:** Hyrskykari et al. (2000),
[*Design Issues of iDict: A Gaze-Assisted Translation Aid*](https://researchportal.tuni.fi/en/publications/design-issues-of-idict-a-gaze-assisted-translation-aid/),
ETRA pp.9–14, DOI [10.1145/355017.355019](https://doi.org/10.1145/355017.355019).
The institutional record verifies the paper; its original institutional full-text
link failed in this review. Author-uploaded excerpts describe gaze-assisted
translation and reader-profile adjustment. Treat this as a design precedent only;
no verified sample, comparative benefit or transferable threshold is claimed here.
GazePrompt's visual word-recognition task should not substitute for evidence that
a reader needs an English definition.

## Recommended rules to defend and evaluate

These are **NewsMead design recommendations**, not published validated rules.
No source reviewed establishes the exact phone cutoffs or a probability of need.

### 1. Give the line highlight an explicit navigation rule

**Primary candidate: supported current-line guidance.** Show the highlight only
when fresh eligible gaze supports one visible line. Retain the selected line
through small coordinate fluctuations; move after the destination has consistent
support. Hide/withhold during uncertainty or invalid geometry. This is already
close to the current app and needs no claim that the reader is struggling.

**Event-specific comparison: line-entry assistance.** On a supported uninterrupted
transition, briefly cue the observed destination. For adjacent forward movement,
large leftward displacement and a left-side landing can support a return-sweep-like
transition; a revisit/jump needs stable destination support. With insufficient
samples, retain coarse navigation only or abstain. Never compare across a missing
segment. A cue detected after landing supports orientation/stabilization; do not
claim it guided the initial landing.

Use line-height/text-width units and actual capture timestamps for candidate
confirmation settings. Choose stability, latency and timeout on development
data, then freeze them for independent evaluation. GazePrompt's three-fixation
rule and desktop pixel cutoffs are precedents, not interchangeable with three
phone-camera samples. A live classifier cannot require the future forward
movement used in a retrospective outcome definition.

Keep strength fixed and adjustable initially. Neither normal line changes nor
longer residence justify stronger opacity. The superseded 8%/20% entry pulse has
no established dose rationale. Defensible thesis wording:

> The guide provides a spatial reference for the currently supported reading
> line. It is evaluated as gaze-contingent navigation assistance; line changes
> are not treated as evidence of comprehension difficulty.

### 2. Keep word assistance optional and separate visual from semantic need

Retain the offer-before-panel interaction. Describe 1,500 ms as an **initial
engineering threshold for offering assistance**, measured as supported residence,
not a diagnostic fixation cutoff. Do not claim that its numerical match to one
GazePrompt default threshold transfers validation: the detector, logical predicate,
population, task and assistance differ.

Candidate improvements: personalize the offer threshold using separate baseline
reading data; examine word length/familiarity when evaluating false offers; keep
ignoring an offer easy; and suppress exact-word assistance when targeting is
ambiguous. A visual lens addresses recognition/legibility; a dictionary addresses
meaning. Let readers choose the assistance form. If a fully gaze-operated version
is required, test deliberate dwell on a separate, sufficiently large confirmation
target; natural reading residence alone should not immediately open a panel.
That extra interaction is a new design and needs its own targeting checks.

### 3. Treat the focus window as the most exploratory component

The current rule counts **unavailability**, including `no_face`, invalid crops,
stale predictions and unavailable coordinates. It does not count every tracked
outside-article look-away. Calling all these events confirmed attention lapses
would misdescribe the implementation. Repeated tracker faults can reach three
episodes. Font-scaled ABAB visits also do not establish an attention problem.

A more defensible gaze-based candidate is an **optional context spotlight**:
once enabled, it follows a supported line/paragraph and gently emphasizes that
region. Its purpose is reader-selected visual emphasis, with no distraction
diagnosis. This changes the selection rationale while preserving a gaze-based aid.

If the research question requires adaptive activation, retain repeated losses
and line alternation as separately labeled exploratory predictors. Validate each
against independent reports of unwanted loss of place/help need, then compare
focus with no aid and simpler guidance at those opportunities. Do not increase
mask strength merely because events recur. Compare appearances directly, keep
surrounding context readable, and measure ease of rereading as well as comfort.
PT13 adds a target-population reason to preserve context, although its filtered
character-window manipulation differs from our multi-line dimming.
No paper reviewed establishes three events/30 s, font-tier cycle counts, the
50%/100% steps, or current 3 s hold / 7 s fade as effective doses.

### 4. Refocus the bookmark on resumption

Save the last supported location before a qualified departure; preserve it
through temporary absence. On supported return, show a brief resumption cue at
that saved location. Keep reliable outside coordinates, no-face evidence and
technical tracking failures distinct in analysis. A low-cost bookmark may be
reasonable after uncertainty without labeling the cause as distraction.

Treat the non-next-line jump route separately: intentional rereading/scanning
must remain possible. My recommendation is to compare it as an optional
navigation-history marker, rather than use it as a default lost-place diagnosis.
Test a saved-line highlight or sentence/block context against the present gutter
bar. Exact duration and clearance on reading elsewhere remain experimental.

## Other gaze-based visual aids worth comparing

| Candidate | Gaze-based selection rule to test | Evidence and priority |
| --- | --- | --- |
| **Line-start arrow / gutter pointer** | Point at the start of the reliably selected current/destination line after confirmation. | Closest new presentation alternative: W24 includes an arrow. Compare it with whole-line fill; form superiority was not isolated there. Predicting the next line before a sweep would be a separate unvalidated design. |
| **Gaze-following underline** | Place a thin rule below a stable current line, changing only on confirmed destination support. | N23 supplies mouse-controlled form evidence. Gaze control is an extension. Useful low-occlusion comparison; no promised older-adult benefit. |
| **Gaze-selected word or short-phrase lens** | Offer enlargement after sustained supported residence; accept through a distinct gaze target if fully hands-free use is needed. Use coarse phrase support when a word cannot be selected reliably, and withhold if the region is ambiguous. | W24 supplies a word-magnifier precedent. Phrase targeting is an original extension. A magnifier exists in older NewsMead profiles but is not the current dictionary aid. Preserve source layout/context. |
| **Contextual resumption highlight** | After departure and return, mark the saved sentence/block as well as its location. | EB15 supplies direct resumption-context evidence. This is a stronger alternative form of the existing bookmark, not a separate difficulty detector. |
| **Gaze-selected paragraph outline / subtle background** | Track supported entry into a paragraph without hiding other paragraphs. | V26 motivates testing the unit; automatic gaze selection is new. Current sentence-context guidance already has paragraph fallback, so prototype only the distinct appearance/control if useful. |

A persistent “already read” trail is lower priority: gaze exposure does not prove
reading/comprehension, especially during scanning, missing data and scrolling.
Avoid making earlier text disappear or forcing forward progress.

## What would turn these rationales into defensible findings

1. **Measurement:** establish line and, separately, word/region assignment against
   known intended targets on the actual phones, sizes, screen regions and relevant
   older readers. Report wrong-line/word rates, unavailable time, latency and
   abstention. Validate auto-scroll separately. Do not require every reader to
   qualify for exact-word aids before using coarse aids.
2. **Rule specificity:** independently label line entry, genuine interruption and
   requested help. Include ordinary rereading, skimming, blinks and tracker loss
   as alternatives. Report correct cues / all shown cues, captured opportunities /
   all independently labeled opportunities, unwanted cues per usable reading
   minute, and eligibility/coverage with excluded time reported separately.
3. **Aid benefit:** compare one aid at a time with no aid and a matched static or
   reader-controlled version where relevant. Counterbalance passages/conditions.
   Measure navigation/resumption time, independently scored comprehension and
   comfort/distraction; lower regression counts alone are not success.
4. **Adaptation:** only after an aid helps, compare the adaptive rule with the same
   aid offered at matched exposure. Keep tuning readers/passages separate from
   independent validation; account for repeated observations within readers and
   passages. Synthetic replay checks causality and rendering, not human benefit.

Recommended order: line guide/arrow and interruption bookmark first; word offers
after a word-localization gate; automatic focus last. All four may remain in the
prototype, but the thesis should state their different evidential status.

## Review provenance and limits

Primary paper pages and current code were checked; no private recordings, camera,
device install, collection, build, staging or commit was used. Original ruler PDF
page opening was unreliable; primary-source indexed §4.4/Table 4 text was checked.
No new numerical claim was taken from an inaccessible table image. ST14, PT13
and iDict are explicitly limited above. Yang et al.'s progress-highlighting manuscript was
not used for efficacy claims because retrieved versions differed in sample/design.

Applied the installed scientific-critical-thinking procedure to distinguish
observations, triggers, interventions and transfer. Procedural reference only:
Kassis, T., Agarwal, V., He, Y., Patel, D., & Brueckner, A. M. (2026),
[*Scientific Agent Skills: A Library of Procedural Knowledge for Research Agents*](https://doi.org/10.48550/arXiv.2609.00065).
Metadata checked on arXiv; this reference is not evidence that a visual aid works.

Focused independent review checked source-rule and claim distinctions; its finding
about adjustable GazePrompt defaults was corrected. Some PDF numerical claims
were checked by the lead/researcher but could not be independently re-extracted
by the reviewer. This remains a bounded source review, not human validation.
