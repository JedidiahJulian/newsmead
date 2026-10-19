# Justifying scanpath-triggered visual assistance

2026-10-16. Research/design only. Older adults are the main target; other
populations supply bounded mechanism and detector evidence. No app rule changed.

The central question is **why this observed scanpath warrants this particular
assistance at this time**. Evidence that highlighting is generally liked or
improves reading does not answer that question. This note takes priority over
the general design recommendations in the
[earlier aid review](gaze-visual-aid-rule-review-2026-10-16.md) when defending
automatic triggers. It does not require abandoning adaptation or substituting
an always-on/manual aid for the requested scanpath-conditioned system.

## Independent pattern search: ordered behavior, meaning and visual response

Latest owner clarification: discover published patterns independently; the five
example labels supplied by the owner are examples of desired specificity, not
the search boundary. The table below prioritizes identifiable text locations
and movement order. Arrow sequences are explanatory schematics, not copied raw
traces or validated detector definitions. The last column is a design hypothesis.

| Published behavior / schematic sequence | Primary evidence for its interpretation | Proposed purely visual response / trigger boundary |
| --- | --- | --- |
| **Undersweep fixation followed by a corrective saccade:** line end -> new line, right of intended entry -> short leftward correction -> forward reading. | Slattery & Parker (2019), [original article](https://eprints.bournemouth.ac.uk/32582/7/Slattery-Parker2019_Article_ReturnSweepsInReadingProcessin.pdf), DOI 10.3758/s13423-019-01636-3. Two multiline-reading datasets characterize this spatial correction and its processing consequences. This common reading event is not a fatigue diagnosis. | **Next-line entry marker:** a small left-margin notch/arrow at a subsequent transition when a reliable history suggests entry difficulty. A completed correction cannot trigger assistance for its own earlier landing. Normal corrections alone do not establish need; phone resolution and matched benefit require validation. |
| **Candidate-line searching:** established line1 end -> line3 start -> line2 start -> forward reading. | Wang et al. (2023), [original study](https://arxiv.org/html/2303.16346), Fig6b, sections3.5.2/4.2. Low-vision readers searched more candidate lines;10/20 reported next-line difficulty. This is a more direct observed localization problem than a generic regression count. | **Target-line bracket or line-start arrow**, shown during unresolved candidate-row search when next-line inference is unambiguous. Prefer this broader event over claiming detection of individual corrective saccades on the phone. |
| **Whole-sentence rereading:** forward pass -> disambiguating region -> return toward beginning -> forward rereading over the sentence. | von der Malsburg & Vasishth (2011), [original paper](https://tmalsburg.github.io/MalsburgVasishth2011.pdf), Fig6A and finer analysis. This pattern increased under experimentally manipulated syntactic garden paths; a single early return or local disambiguator return did not show the same condition effect. | **Sentence-sized reading band or outline**, preserving earlier material needed for reprocessing. Online eligibility can use only the observed return and emerging reread, not future completion of the retrospectively classified path. |
| **Delayed ambiguity-related rereading in older readers:** encounter ambiguous word -> read onward -> later reread ambiguous/prior material. | Stites, Federmeier & Stine-Morrow (2013), [original study](https://pmc.ncbi.nlm.nih.gov/articles/PMC3883559/), DOI 10.1037/a0032860. Context/meaning were experimentally varied;18 older adults aged62-83 and18 younger adults aged18-26 showed different timing of ambiguity costs. Older readers showed later rereading costs. This is not a named universal scanpath class. | **Local context band around the returned word/sentence.** A later return is evidence to investigate meaning integration; it does not establish an unknown word, a need for a definition, or an exact backward landing on an antecedent. |
| **Topic-structure processing:** heading -> section body -> section-final sentence -> selective look-back to heading/topic material. Contrasting profiles were fast linear, slow linear and nonselective reviewers. | Hyönä, Lorch & Kaakinen (2002), [author paper](https://www.researchgate.net/publication/228918114_Individual_differences_in_reading_to_summarize_expository_text_Evidence_from_eye_fixation_patterns), DOI 10.1037/0022-0663.94.1.44. Initially 47 university readers analyzed; profiles compared 40 consistently classified across both texts. Main summary contrasts excluded the three nonselective reviewers (remaining N=37). Tracker: 250 Hz EyeLink. Selective structure processing accompanied stronger summaries than slow linear reading; fast linear reading could also succeed. | **Structure-return markers:** visually emphasize the existing heading and section boundary while a deliberate structure-related return occurs; preserve a small marker at the departure point. This supports an observed strategy rather than diagnosing confusion from look-backs. |
| **Second-pass integrative processing:** text segment -> corresponding diagram region -> reread text / reinspect diagram, after initial inspection. | Mason, Tornatora & Pluchino (2015), [institutional original](https://www.research.unipd.it/retrieve/e14fb26c-a010-3de1-e053-1705fe0ac030/11145_2015_9552_OnlinePDF_Mason_Feb16_2015.pdf), DOI 10.1007/s11145-015-9552-5; 43 Italian 7th graders, Tobii T120. Stronger second-pass integration predicted recall/transfer beyond measured reader characteristics. This is evidence for purposeful integration, not proof that switching means difficulty. | **Paired outlines/underlines:** matching visual marks on the original text phrase and corresponding chart element during supported reinspection. Requires known text-picture correspondence. Sparse switching as an automatic deficiency trigger is a separate unvalidated inference. |
| **Scanning -> reading -> decision states:** broad inspection -> concentrated text reading -> decision-related inspection/revisit. | Simola, Salojärvi & Kojo (2008), [primary publisher record](https://www.sciencedirect.com/science/article/pii/S1389041708000132), [verified institutional bibliographic record](https://researchportal.helsinki.fi/en/publications/using-hidden-markov-model-to-uncover-processing-states-from-eye-m/), DOI 10.1016/j.cogsys.2008.01.002; 10 Finnish-speaking adults,23-29; task independently assigned. HMM state labels were inferred. Task classification60.2% versus33.3% chance does not establish accurate online state detection or comprehension diagnosis. | **Paragraph reading anchor:** a small edge marker/contrast band where sustained reading begins after scanning, supporting a known search-to-read task. A literal Z shape is unnecessary. State transition and cue efficacy need separate tests. |
| **Anchor-search-confirm:** question cue -> scan passage for corresponding keyword/number -> passage-question checking. | Al Saidi & Al Hilali (2026), [original article](https://link.springer.com/article/10.1186/s40862-026-00408-6), especially Figs 10-12 and limitations; 24 Omani college L2 learners; 60 Hz Tobii Nano. Recorded paths and interviews support a task-driven coordination description. Comprehension accuracy/text-only comparison were absent. | **Matched cue outlines** on the existing question term and passage occurrence during cross-checking. Only relevant if question/search context exists; it is not a general older-adult news difficulty trigger. Fig10 is an observed example; Figs11-12 are explanatory diagrams. |

These are different evidence types: experimentally manipulated difficulty,
observed spatial navigation problems, reader-level strategy/outcome associations,
and task-dependent process descriptions. They should not all be presented as
diagnostic difficulty classes. The most concrete additions to the aid repertoire
are a line-entry notch, a sentence band, structure-return markers and paired
text-graphic outlines. Each changes the original material visually; none requires
a generated summary or context card.

For a structure-signaling response precedent, Zhang et al. (2019),
[*Effects of task instructions and topic signaling on text processing among adult readers with different reading styles*](https://doi.org/10.1016/j.learninstruc.2019.101246),
[institutional manuscript record](https://www.utupub.fi/items/776825cf-624e-4f1c-b037-e00bd3f992d2),
manipulated topic-sentence underlining. It changed structure sensitivity in some
reading styles. This supplies response/process evidence, not proof of an online
gaze trigger or verified comprehension gain. Hyönä & Nurminen (2006),
[original record](https://doi.org/10.1348/000712605X53678), also recovered linear
and topic-structure profiles and related look-backs to main-point recall.

Strukelj & Niehorster (2018),
[*One page of text*](https://bop.unibe.ch/JEMR/article/download/Strukelj/Strukelj_final_pdf/15159),
provides instructed skimming/regular/thorough/spelling profiles and comprehension
outcomes, not a published Z-path classifier. Christianson et al. (2024),
[*Retracing the garden-path*](https://www.sciencedirect.com/science/article/pii/S0749596X24000184),
also cautions that rereading amount/location alone did not predict better final
comprehension in its experiments. Supporting reinspection must not be evaluated
only by reducing the number of regressions.

Most evidence above comes from younger adults or children reading stationary
desktop text. W23 includes older low-vision participants; S13 directly compares
older and younger adults, but neither validates these proposed phone aids.
NewsMead's moving-text supported visits/residence are not established equivalents
of high-rate eye-tracker fixations. Very short undersweep detection especially
requires independent timing/spatial validation. No numeric visit/duration cutoff
is imported as a validated NewsMead trigger.

Primary full text or author manuscripts were checked for the line, reanalysis,
topic-profile, illustrated-text and anchor-search claims. Simola and the newer
Christianson comparison were bounded to accessible primary abstracts/records;
missing apparatus details are not asserted. Older-reader details retain the
earlier S13 verification below. Some direct publisher/PMC fetches were blocked;
institutional copies were used where available. No app, device or protocol edits.

Focused review of this addition checked four sources and corrected the topic-
profile comparison denominator (initial47; consistent profiles40; main summary
contrasts37). Mason/Simola retrieval failed for the reviewer; the lead's direct
institutional Mason PDF and primary indexed/record Simola evidence remain the
stated basis. Review confirms citation/interpretation limits, not human validity.

## Citation audit of the supplied Gemini response

The owner supplied the complete response and its citations after requesting
independent discoveries. Those independent findings above remain in scope.
This audit checks the additional supplied claims rather than replacing discovery
with a check of only the five examples. Useful design specificity and adequacy
of scientific support are separate judgments.

### What its cited studies actually establish

| Cited source | Verified scope and usable evidence | Boundary for the supplied claims |
| --- | --- | --- |
| Mishra et al. (2017), [*Scanpath Complexity: Modeling Reading Effort Using Gaze Information*](https://www.cse.iitb.ac.in/~pb/papers/aaai17-scanpath.pdf) | Sixteen English-proficient adults read32 Wikipedia paragraphs using EyeLink1000Plus. Paragraph difficulty ratings1-10 supplied a subjective effort reference. Learned aggregate complexity related to effort/readability measures. | Useful reading-effort measurement precedent. It does not establish3-5-word loops,3-4 rereads, a direct diagnostic mapping to intense struggle, or a matched aid. |
| Ma et al. (online2022/issue2023), [*From eye movements to scanpath networks*](https://pmc.ncbi.nlm.nih.gov/articles/PMC10027820/) | Fifty-two native-English adults18-40 read science texts sentence by sentence in an MRI scanner, EyeLink1000Plus at1000Hz. Network metrics correlated with post-reading comprehension ability. | Associations across readers in a constrained presentation are not an online lexical-failure detector or proof that a particular loop indicates confusion. No matched visual intervention was tested. |
| Li, Yin & Gu (2025), [*Approach to Eye Tracking Scanpath Analysis with Multimodal Large Language Model*](https://www.mdpi.com/2673-3951/6/4/164) | GPT-4o-based interpretation of scanpaths over industrial product posters, evaluated partly against designer interpretations. | Does not substantiate the opening reading-difficulty/abnormality claim. Source domain is industrial design, not prose comprehension. |
| Holmberg (2004), [*Eye movement patterns and newspaper design factors*](https://lup.lub.lu.se/student-papers/record/1328870) | Master's thesis experimentally varied newspaper graphics and article placement; measured viewing time and attention order. | Relevant background for layout and newspaper scanning. It does not provide the five cognitive trigger diagnoses or numerical loop thresholds. |
| Nielsen Norman Group, [*How People Read on the Web: The Eyetracking Evidence*](https://www.iso.org/files/live/sites/isoorg/files/styleguide/resources/How_People_Read_on_the_Web.pdf), especially p54 | Describes F-pattern, layer-cake, spotted and commitment viewing. F scanning includes progressively reduced rightward coverage; visual signposts are discussed. | Supports descriptive web scanning and signposting rationale. It does not establish the proposed Z-to-reading detector, diagnose hunting/confusion from diagonal motion, or test gaze-triggered entity bolding/generated summaries. The ISO site hosts the report; the source is NN/g. |
| Jensen et al. (2025), [*Context Preservation Through Eye Tracking*](https://doi.org/10.1145/3715669.3725897), [cited author PDF](https://orbit.dtu.dk/files/406853513/3715669.3725897.pdf) |22 desktop participants; Tobii4C,90Hz. After experimenter-controlled typography interventions, last-read word/sentence/paragraph highlights stayed3s then faded3s. Significant resumption-time benefit was confined to gradual typography changes. | A useful context-preservation precedent. It did not use off-text departure or fatigue as the trigger, or test error-triggered increases in active-zone line spacing. Fading the marker differs from dimming all previously read text. |
| Chahine, Hachem & Moacdieh (2023), [*Eye Tracking-Based Adaptive Displays: A Review*](https://journals.sagepub.com/doi/10.1177/21695067231192631) | Review of adaptive displays and triggering mechanisms; accessible abstract/references only. | Background route to underlying studies, not a new experiment validating the specific regression-to-summary/definition or departure-to-bookmark rules. Restricted full text prevents an exhaustive reference-level audit. |
| UArts, [*Reading Interface* PDF](https://www.uarts-eyetracking.org/wp-content/uploads/2010/11/reading_interface.pdf) | Project site describes interface concepts; PDF retrieval timed out. | Study population, gaze rule and outcome could not be verified. Mark unverified; do not conclude the concepts do not exist. |
| Lallé, Toker & Conati (2021; DOI registered2019), [*Gaze-Driven Adaptive Interventions for Magazine-Style Narrative Visualizations*](https://doi.org/10.1109/TVCG.2019.2958540), [author manuscript](https://arxiv.org/pdf/1909.01379) |63 adults18-59; TobiiT120,120Hz. Pre-mapped text references triggered outlines around corresponding bar-chart data after a piloted fixation-count threshold. Low-visualization-literacy users improved relative to a prior56-person control study; higher-literacy users were not helped. | A directly relevant tested gaze-to-visual-response system. Its trigger was reading reference text, not erratic switching or independently diagnosed integration failure. Historical comparison and subgroup effects limit causal/transfer claims. |
| Selim et al. (2024), [*A review of machine learning in scanpath analysis for passive gaze-based interaction*](https://www.frontiersin.org/journals/artificial-intelligence/articles/10.3389/frai.2024.1391745/full), [PMC mirror](https://pmc.ncbi.nlm.nih.gov/articles/PMC11188426/) | Review of77 studies; documents use of SVMs and RNNs across scanpath tasks. | The two links are the same paper. Model usage does not validate the supplied four classes: deep comprehension, hunting, lexical failure and fatigue. Their labels need independent operational definitions and task/population-specific validation. |

### Concrete consequences for the proposed rules

- Retain **coordinated chart outlining** as a real gaze-adaptive visual precedent;
  describe its actual text-reference trigger. Calling repeated switching a
  difficulty detector would require additional evidence.
- Retain **fading saved-position cues**, with known interruption/resumption
  evidence such as [SwitchBack](https://faculty.washington.edu/wobbrock/pubs/chi-15.03.pdf)
  for a closer phone departure trigger. Jensen instead informs preserving place
  when the interface changes typography.
- Retain **active-zone spacing** as a proposed visual treatment worth examining;
  replace the unestablished fatigue diagnosis with observed line-reacquisition
  difficulty. Neither supplied citation validates that exact trigger-treatment
  pair. Changing spacing also moves text, so preserving the current word/position
  and measuring disruption would be necessary in a prototype evaluation.
- For **regression-triggered sentence focus**, use the narrower reanalysis
  evidence above, distinguishing whole-sentence rereading from small corrective
  movements or useful structural returns. The cited numerical loop examples are
  operational suggestions, not literature-derived cutoffs.
- **Heading/entity emphasis** is a purely visual candidate. Generated syntax
  summaries and bulleted overviews remain outside the owner's stated aid scope.

The strongest contribution of the supplied response is its concrete behavioral
and interface descriptions. Some are source-tested responses with different
triggers; others are plausible design proposals. Its scientific certainty should
be reduced without discarding useful ideas merely because their labels are
informal. A defensible study may test a literature-motivated trigger hypothesis;
it must not describe the complete chain as already established.

Two bounded independent researchers audited eight cited URLs; the lead checked
the remaining three. Public primary sources only; no private recordings or user
attachment content was uploaded. Several direct PMC/publisher requests were
blocked, so primary indexed records/manuscripts were used and missing evidence
is explicitly marked. The UArts PDF and restricted review remain access limits.
No runtime, protocol, device, build, staging or commit change.

## The reasoning needed for each rule

1. Define the observed pattern and its text unit.
2. Identify an independently studied problem/process associated with that pattern:
   manipulated ambiguity, reader-marked unknown vocabulary, a known interruption,
   externally assessed visual condition, or a thought-probe label.
3. Explain how the aid addresses that particular process.
4. Distinguish the source-supported relationship from the proposed online detector,
   numerical threshold and matched intervention hypothesis.

An experimentally established relationship can justify a **research hypothesis**
without proving a universally diagnostic pattern. Conversely, two unrelated
findings, such as more regressions in one population and a preference for a
highlight in another, do not by themselves establish a matched treatment.

## Proposed classification-to-aid map

| Observed pattern to investigate | Problem/process with primary evidence | Matched assistance and rationale | Change needed from the present rule |
| --- | --- | --- | --- |
| **Line-transition search:** an established line-end departure followed by visits among candidate line starts before stable forward reading resumes. | W23 measures extra lines searched during next-line localization; this is spatial navigation difficulty, rather than generic comprehension failure. | Line highlight / line-start arrow supplies a spatial reference for the inferred next line or confirmed destination. W24 tests related gaze-selected line support. The selective search-triggered policy itself is our design inference. | Current guide simply follows supported lines. Add a separate search-conditioned comparison; do not reuse any ABAB sequence regardless of location. |
| **Word-localized hesitation/return:** unusually prolonged word gaze or a later supported return to the same word after reading onward. | H16 compares gaze with reader-marked known/unknown words; S13 shows age-sensitive early versus later meaning-resolution costs under controlled ambiguity. | Definition offer supplies optional semantic information at the affected word. Reader confirmation resolves whether assistance is wanted. Visual magnification is a different response when legibility/recognition is the problem. | Current offer uses 1,500 ms supported residence. Investigate personalized duration and return evidence separately; do not call this number a validated unknown-word threshold. |
| **Sentence reprocessing:** return toward the sentence beginning followed by extended forward rereading. | MV11's broad rereading cluster increased under garden-path difficulty; its finer analysis linked whole-sentence rereading most specifically to the manipulated condition. Brief/local returns were not equivalent evidence. | A context-preserving sentence focus/highlight could keep the material being reprocessed visually organized. This is a mechanism-based intervention hypothesis; a fixed narrow window might omit required earlier material. | Current font-tier ABAB rule ignores sentence boundaries and the reason for the return. A sentence-based detector is a proposed revision, not a relabeling of current cycles. |
| **Departure then re-entry:** supported reading location, qualified external departure, then return. | SB15 directly operationalizes this event and saved-location assistance on a phone. EB15 studies resumption after known interruptions. | Bookmark/resumption highlight reminds the reader where to restart, addressing location-memory and search demands. | Keep tracked outside-screen/article departure and technical unavailability distinct. A jump within text is not the same event. Three episodes/30 s is not supplied by these studies. |

### Line guidance: the closest supported trigger chain

**W23 — Wang et al. (2023),**
[*Understanding How Low Vision People Read Using Eye Tracking*](https://arxiv.org/html/2303.16346),
§§3.5.2,4.2; Figure 6b. Twenty low-vision participants, ages19–86,
and20 sighted controls read aloud using a120 Hz Tobii Pro Fusion desktop setup.
The low-vision group searched more lines during transitions and spent more
fixation time near line starts;10/20 reported difficulty locating the next line.
Figure6b illustrates a participant finishing line1, landing at line3's start,
then navigating to line2. The groups differed in age and selected typography;
the study does not isolate an aging effect or validate a phone classifier.

**W24 — Wang et al. (2024),** [*GazePrompt*](https://arxiv.org/html/2402.12772),
§§3.1.1,3.2.3,4.3.2, supplies the related response precedent: gaze-selected
line/arrow support reduced switching time in13 low-vision desktop readers,
without significant switching-accuracy improvement. It did not test the selective
trigger below against ordinary line following.

**Proposed causal rule, not a published detector:**

1. Establish source line and coarse left-to-right progression toward its end.
2. Observe the transition back toward the line-start region in unchanged layout.
3. Establish extra search evidence: supported visits to more than one candidate
   row near the starts before forward reading has stabilized. A retrospective
   example is `L1(end) -> L3(start) -> L2(start) -> forward reading`.
4. During an ongoing search, show an orientation cue only if source/adjacency make
   the next-line hypothesis sufficiently unambiguous; otherwise wait for reliable
   destination support or abstain. An expected-next-line cue is an inference and
   should allow intentional revisits/skips. Confirmed-destination highlighting
   can help orientation, but cannot be said to guide its initial landing.
5. End the extra cue after supported forward progress resumes or a bounded
   timeout; withhold on unreliable mapping, reflow, controls or missing sequence.

“More than one candidate” operationalizes extra line search; it is not an
empirically optimized diagnostic cutoff. Start/end bands, stabilization, timeout
and accepted transition duration must be selected and independently checked.
No exact word identification is required for this coarse candidate.

A single undersweep is a poor escalation rule. Parker, Slattery & Kirkby (2019),
[*Return-sweep saccades during reading in adults and children*](https://eprints.bournemouth.ac.uk/31639/1/VR_Rev2_TJS_JAK.pdf),
studied47 adults and48 children at1,000 Hz. A rightward landing followed by a
corrective leftward saccade is a genuine oculomotor correction, but is common.
It should not be conflated with a vertical wrong-row landing or unfamiliar-word
regression. Use it to understand the mechanism, not as a universal need label.

**Defensible wording:** “The conditional line cue targets candidate line-localization
search episodes. Prior work measured extra candidate-line searches during
line-switching difficulty; the cue supplies a spatial reference for resuming the
line sequence. We evaluate the proposed online rule on the target phone.”

### Word-definition offer: a direct need-related reference exists

**H16 — Hiraoka et al. (2016),**
[*Personalized Unknown Word Detection in Non-native Language Reading using Eye Gaze*](https://www.phontron.com/paper/hiraoka16icmi.pdf),
[DOI10.1145/2993148.2993167](https://doi.org/10.1145/2993148.2993167),
ICMI pp.66–70. Twelve Japanese graduate students reading English were recruited;
two records were excluded. Readers later marked known/unknown words. Maximum
word-gaze duration was the strongest gaze feature in the reported feature
analysis; word rarity also mattered. The work examined personalized and
leave-one-reader-out prediction. These are reader-marked unfamiliarity labels,
not a test of definitions or native-language older news readers.

**S13 — Stites, Federmeier & Stine-Morrow (2013),**
[*Cross-age comparisons reveal multiple strategies for lexical ambiguity resolution during natural reading*](https://pmc.ncbi.nlm.nih.gov/articles/PMC3883559/),
[DOI10.1037/a0032860](https://doi.org/10.1037/a0032860).
Two experiments included18 young adults and18 older adults, respectively; the
older sample was62–83. Controlled noun/verb ambiguity and semantic context
affected gaze timing differently by age: older readers showed later rereading
costs, rather than the young group's early first-fixation pattern. Thus a rule
restricted to a single initial long fixation may miss meaningful older-reader
processing. The measured target region included a preceding cue word; a later
return is not the same observation as repeated fixations before leaving a word.

**Recommended candidate:** separately measure an unusually long supported visit
and a supported later return to the same word. Test whether either, or their
combination with text features, predicts reader-marked need. Adjust for baseline
reader speed and word length instead of assuming the same duration means the
same thing for every reader/word. This adaptation is proposed, not a fitted model.
Offer the definition; preserve the ability to ignore it. Do not automatically
equate a visual recognition problem with missing word meaning.

The iDict precedent is informative but requires special caution. Hyrskykari's
2006 dissertation [*Eyes in Attentive Interfaces: Experiences from Creating iDict,
a Gaze-Aware Reading Aid*](https://trepo.tuni.fi/bitstream/handle/10024/67601/951-44-6643-8.pdf?sequence=1)
describes duration/text/profile-based help. The frequently repeated91% recall /
2.4% false-positive result involved trained users; H16 reports much lower recall
without training. It is not strong evidence that untrained natural long gazes
alone reveal unfamiliar words. Original retrieval was access-restricted in this
review; avoid basing the present rule on its numerical success rate.

**Defensible wording:** “The offer targets possible localized lexical difficulty.
Unknown-word detection studies compare gaze duration and text features against
reader-marked vocabulary knowledge, and older-adult ambiguity research shows
that effort can emerge during later revisits. We therefore investigate supported
word hesitation/return as assistance opportunities, with optional acceptance.”

### Focus: choose the intended process before choosing a pattern

**Sentence processing route.** MV11 — von der Malsburg & Vasishth (2011),
[*What is the scanpath signature of syntactic reanalysis?*](https://tmalsburg.github.io/MalsburgVasishth2011.pdf),
Figure6 / journal pp.119–120, reanalyzed44 undergraduates reading Spanish
sentences. A path returning to the sentence beginning and rereading all/part of
it occurred more often in the garden-path condition; brief early checks and
nearby returns did not show the same condition association. The path also
occurred in comparison sentences. It supports a **reanalysis/reprocessing**
interpretation for a particular multi-event path, not “any regression means
confusion.” In the finer analysis, whole-sentence rereading was the pattern
significantly modulated by attachment condition. Local/partial-return triggers
would broaden that signature and require separate checks. An online rule can
use only the prefix observed so far; it cannot
claim a completed whole-path classification before the rereading occurs.

This can motivate a sentence/context focus that preserves the revisited and
current material. Whether dimming helps reprocessing is the untested bridge.
The rationale should be supporting a repair process, not stopping rereading or
claiming comprehension failure. S13 above supplies an additional older-reader
reason to include later reprocessing.

**Attention-lapse route is different.** F15 — Frank et al. (2015),
[*Validating older adults' reports of less mind-wandering*](https://libres.uncg.edu/ir/uncg/f/M_Kane_Validating_2015.pdf),
[primary abstract](https://pubmed.ncbi.nlm.nih.gov/25938246/), used independent
thought reports. Older-adult task-unrelated-thought intervals had fewer words
fixated and more between-word regressions than on-task intervals; older-reader
gaze duration alone did not distinguish them. These are within-study concurrent
associations, not an independently validated online cut point. Primary sample
count/age details were not retrievable in this bounded review. Apparatus indexed
in the author manuscript: ASL H6HS120 Hz; probes occurred3–8 s after page onset.
That sampling window is not an aid threshold.

**BD16 — Bixler & D'Mello (2016),**
[*Automatic gaze-based user-independent detection of mind wandering during computerized reading*](https://link.springer.com/article/10.1007/s11257-015-9167-1),
uses thought-probe labels and multiple gaze/context features. Publisher-reported
user-independent accuracies were72% versus60% chance for end-page probes and67%
versus59% chance for mid-page probes. Features included fixation timing,
unexpectedly long reading time, skipping and pupil variability. Only the primary
publisher abstract was available here; sample, validation details and operating
threshold could not be rechecked. This supports investigating a multi-feature
attention detector, not copying any one feature as a deterministic diagnosis.

Neither route validates a physical-line ABAB count as its equivalent. Do not map
word regressions, sentence reanalysis paths and line alternations interchangeably.
Missing predictions are not one of these observed reading paths. The present
three-unavailability-episodes/30 s and font-tier cycles remain original heuristics.

**Recommendation:** if keeping focus as a scanpath-conditioned aid, choose and
name either sentence reprocessing or suspected attentional disengagement as the
primary target. The first has a more inspectable path-to-process rationale; the
second has probe-labeled detector precedents but needs more modeling and valid
features. Treat a focus window's benefit at those moments as a testable matched
response. Do not retrospectively claim that existing ABAB rules implement either.

### Bookmark: interruption is the trigger's relevant problem

SB15 — [SwitchBack](https://faculty.washington.edu/wobbrock/pubs/chi-15.03.pdf),
and EB15 — [EyeBookmark](https://idclab.skku.edu/assets/EyeBookmark.pdf), link
departure/interruption, saved reading position and resumption assistance directly.
The study settings and limits are recorded in the
[earlier review](gaze-visual-aid-rule-review-2026-10-16.md).
The defensible chain is **reading -> departure -> re-entry/search -> location
reminder**, rather than an arbitrary within-text jump -> distraction.

Keep a supported source location through uncertainty. On confirmed return, cue
that location/context. Record tracked departure separately from technical loss;
the same low-cost bookmark can be offered after uncertainty without asserting
why gaze disappeared. A frequency threshold for stronger focus is a different
hypothesis and is not established by these interruption studies.

## Extension: other reading observations and visual-aid forms

**Scope corrected after owner feedback:** context cards, summaries and semantic
navigation features do not meet the requested visual-aid category. The shortlist
must concern gaze-triggered changes to how existing text is seen/tracked, such as
magnification, contrast, arrows and underlines. The extension below retains
research leads; its context-card/overview recommendations are superseded and
should not be presented as accepted visual-aid proposals. Trigger evidence and
the proposed visual response still need to be distinguished.

Owner follow-up asks for additional patterns/observations, keeping the same
pattern -> process/problem -> matched response logic. The following are distinct
candidate forms; none is implemented. Some support a reading strategy rather
than correct a deficit. “No recorded fixation” is usable only when tracking and
the relevant region were observable; it is not proof that information was unseen.

| Observation / candidate trigger | Grounded interpretation and distinct visual response | Evidence status / phone fit |
| --- | --- | --- |
| Repeated supported reading of a referring expression or its local sentence after reading onward; text analysis identifies a reliable earlier referent. | Possible reference-resolution/integration effort -> **paired referent emphasis** or a small source-context card. Example: emphasize “the minister” and the earlier named person together, retaining the actual source text. | CW19/SS11 ground the processing interpretation; the proposed cue and online rule are new. Do not require a literal gaze jump back to the antecedent, and do not infer the referent from gaze. Exact-word targeting and reliable linguistic links make this demanding for the current phone. |
| Sustained denser fixations/shorter forward steps or prolonged reading across several words, in a known poor-contrast/legibility condition relative to the reader's baseline. | Possible visual-access effort -> **gaze-selected high-contrast text lens** or an offer of larger text. This addresses seeing the text, distinct from explaining its meaning. | W18/Y22 manipulated stimulus quality and measured gaze/reading disruption. The automated trigger and beneficial adjustment are hypotheses; the pattern also has nonvisual causes. Preserve source layout and nearby context; do not automatically reflow text during a visit. |
| Little inspection of an available explanatory image/chart and few supported transitions between its relevant region and the associated text during an adequate observation interval. | Possible incomplete text–graphic integration -> **paired text–graphic cue**, such as matching outlines around a sentence and its corresponding chart element/legend entry. | SC19 actually developed and tested related gaze-contingent adaptations. Results were null or depended on learner prerequisites, so this is direct precedent with mixed efficacy, not a recommended universal intervention. Applicable only to articles with meaningful illustrations. |
| A selected unfamiliar/target term is inspected but its available explanatory gloss is not subsequently inspected. | An available help option may be missed -> **subtle gloss/footnote callout or connector**, rather than automatically opening an explanation. | RE23 tested reactive gloss highlighting and increased visual attention, without superior recognition scores. It supplies a closely matched cue precedent; ignoring help can also be deliberate. Adaptation to ordinary news footnotes or existing lookup offers is a new study condition. |
| A sustained cluster of brief fixations, larger forward jumps and sparse word coverage during sufficiently tracked text exposure. | Candidate skimming strategy -> **section overview or heading map** that supports navigation and finding material of interest. | SN18 manipulated reading instructions and observed a skimming profile. A gaze-conditioned overview is an original design; the study did not validate intention classification or that aid. Do not equate skimming with distraction or force rereading. Lower priority than the problem-specific candidates above. |

### Primary evidence behind the extension

- **CW19:** Cook & Wei (2019),
  [*What Can Eye Movements Tell Us about Higher Level Comprehension?*](https://pmc.ncbi.nlm.nih.gov/articles/PMC6802807/),
  *Vision*3(3),45. Passage experiments manipulated antecedent/anaphor
  compatibility. Downstream rereading/regression measures reflected integration
  difficulty, and returns often did not reach the earlier antecedent. Primary
  indexed methods/results were checked; sample/apparatus details were not fully
  verified here. This does not test a paired-referent aid.
- **SS11:** Shake & Stine-Morrow (2011),
  [*Age differences in resolving anaphoric expressions during reading*](https://pubmed.ncbi.nlm.nih.gov/21995819/),
  *Aging, Neuropsychology, and Cognition*18(6),678–707. Three experiments with
  young/older adults varied stereotypical-gender pronoun violations and context.
  Context influenced immediate and later reprocessing differently by age; prior
  disambiguation reduced older readers' later reprocessing. This is controlled
  reference-processing evidence, not a generic unknown-pronoun detector. Primary
  abstract/indexed excerpts checked; exact N/tracker not verified.
- **W18:** Warrington, McGowan, Paterson & White (2018),
  [*Effects of aging, word frequency, and text stimulus quality on reading across the adult lifespan*](https://doi.org/10.1037/xlm0000543),
  [institutional record](https://irep.ntu.ac.uk/id/eprint/42083/), experiments1–2.
  Young/middle/older adults read normal or faint text, with a gaze-contingent
  upcoming-text manipulation in experiment2. Faint text disrupted older readers
  more. Researcher checked EyeLink1000/1,000 Hz desktop methods; no exact
  automatic aid threshold or successful gaze-triggered contrast treatment was
  tested. Contrast reduction and lexical frequency had distinguishable effects.
- **Y22:** Yu, Shamsi & Kwon (2022),
  [*Altered eye movements during reading under degraded viewing conditions*](https://pmc.ncbi.nlm.nih.gov/articles/PMC9465940/),
  [DOI10.1167/jov.22.10.4](https://doi.org/10.1167/jov.22.10.4).
  Fourteen normally sighted young adults,18–28, read with manipulated blur,
  contrast and background luminance. Blur/contrast degradation affected fixation,
  saccade and reading measures; luminance alone did not have equivalent effects.
  This grounds a legibility mechanism, not a clinical diagnosis or automatic
  magnifier rule. Dedicated eye tracking; sampling rate not reverified here.
- **SC19:** Scheiter et al. (2019),
  [*Adaptive multimedia: Using gaze-contingent instructional guidance to provide personalized processing support*](https://www.sciencedirect.com/science/article/pii/S0360131519301149),
  *Computers & Education*139,31–47. University learners studied cell division.
  Initial N=32: more text/picture viewing and transitions accompanied better
  learning. Adaptive presentation was then evaluated with N=79 and N=62. The
  first evaluation found no learning advantage; the later version hindered
  weaker-prerequisite learners and tended to help stronger ones. Primary
  publisher abstract/section excerpts checked. Do not import apparatus from a
  related dissertation as verified methods for every experiment.
- **RE23:** Révész, Stainer, Jung, Lee & Michel (2023),
  [*Using eye-tracking as a tool to develop lexical knowledge*](https://www.lltjournal.org/item/10125-73537/),
  [author manuscript](https://discovery.ucl.ac.uk/10133606/7/Revesz_27_01_10125-73537.pdf),
  *Language Learning & Technology*27(1),1–22. L2 reading with English pseudowords
  and glosses; reactive gaze-contingent highlighting attracted attention to
  glosses but did not improve recognition relative to the comparison condition.
  Original first pages and primary indexed material were accessible; exact
  timing/sample/apparatus were not re-extracted. Learning and attention are
  separate outcomes; adapting this to overlooked news context is a proposal.
- **SN18:** Strukelj & Niehorster (2018),
  [*One page of text*](https://bop.unibe.ch/JEMR/article/download/Strukelj/Strukelj_final_pdf/15159),
  [DOI10.16910/jemr.11.1.1](https://doi.org/10.16910/jemr.11.1.1).
  Instructed regular/thorough/skimming/spelling tasks on desktop text;120 Hz
  SMI RED-m eye trackers. Skimming had shorter fixations, longer saccades and
  more skipping; thorough reading had more rereading and higher comprehension.
  Instructions supplied the strategy reference. This is not a held-out live
  strategy classifier or a trial of a navigation overview.

For a text-focused older-adult app, the most useful new investigations are a
**reference/context link** and a **visual-access lens offer**. The former has a
more specific semantic target but requires dependable text/gaze alignment; the
latter has older-adult visual-quality evidence but nonspecific gaze symptoms.
Overlooked-help callouts provide a closer tested trigger–response precedent,
with a limited outcome. Text–chart cues become relevant when illustrated articles
are included. These are priorities for evaluation, not measured rankings of
benefit or readiness for deployment.

Syllable/morpheme coloring was also checked and not prioritized: De Simone et al.
(2023), [primary manuscript](https://pmc.ncbi.nlm.nih.gov/articles/PMC10585950/),
[DOI10.1177/17470218231160638](https://doi.org/10.1177/17470218231160638),
found no reading facilitation from color segmentation in skilled German readers;
hyphenation could slow processing. This does not rule out other populations, but
does not justify automatically splitting older English readers' words after
long dwell. The experiment was not gaze-triggered assistance.

## Evidence status and what should change in the defense

| Current shortcut | Better rationale / candidate classification |
| --- | --- |
| A line is being looked at, so highlight it. | For a conditional aid, target transition-local candidate-line search as a spatial localization problem. Current-line following can remain a separate comparison condition. |
| Long word dwell means the reader does not know its meaning. | Supported word hesitation/later return supplies candidate lexical-effort evidence; unknown-word studies provide an independent label precedent. Optional acceptance handles ambiguity in why help is needed. |
| ABAB or repeated missing gaze means distraction, so dim other text. | Choose a sentence-reprocessing or probe-grounded attention hypothesis; neither can be inferred from the present generic line cycles/unavailability alone. |
| A non-next-line jump means the reader lost their place. | Departure/re-entry supplies an interruption-resumption chain; preserve deliberate within-text navigation as an alternative explanation. |

The literature can justify **pattern choice and the targeted mechanism**. Exact
duration, number of visits, rolling window, support-confidence gate and strength
are operational parameters. Choose these using development data and reader
feedback, then freeze and test independently. Do not label an arbitrary count as
“based on literature” when only the kind of behavior was supported.

For the proposed conditional line aid, assess whether candidate episodes actually
correspond to independently labeled line search, then whether the cue reduces
search/transition time without disrupting intentional returns. For lexical help,
use known/unknown or requested-help labels. For focus, use the selected process's
independent reference and compare matched cue versus no cue. Assess comprehension
and comfort as outcomes, not merely fewer regressions. This is how the detector
and the choice of response can be defended separately and together.

## Research record

No camera, device/install, private recording, build, source-code change, staging
or commit. Current rule baseline remains in the earlier review. Evidence comes
from original full text where available and explicitly identified primary
indexed excerpts/abstracts where direct retrieval failed. Several site fetches
were blocked; no missing sample or performance detail was invented. A researcher
misattributed S13 to Rayner et al.; the lead checked the original record and
corrected it to Stites, Federmeier & Stine-Morrow. W23's raw regression count
differed significantly between groups; only its normalized revisitation-rate
comparison was nonsignificant. These are different measures.

Applied the installed scientific-critical-thinking procedure; methodological
reference only: Kassis et al. (2026),
[*Scientific Agent Skills*](https://doi.org/10.48550/arXiv.2609.00065).
It supplies no evidence for a visual-aid trigger.

Focused independent review identified overbroad wording of the reprocessing
candidate; it was narrowed to return-toward-beginning plus extended rereading,
with whole-sentence versus local-return evidence distinguished. Some indexed
source details could not be independently re-extracted by the reviewer.
