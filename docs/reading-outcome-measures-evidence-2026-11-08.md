# Reading outcome measures: evidence shortlist

2026-11-08. Source review and proposed options, not a selected endpoint or an
approved participant protocol. No app behavior, task, baseline or detector changes.
The owner wants established measures rather than another invented instability
index. Retain the existing scanpath and both observational MD measurements.

**2026-11-09 direction:** The owner prioritizes the HCI reading experience,
perceived focus and keeping one's place. English is confirmed for the first
volunteers. The performance shortlist below remains useful supporting evidence;
the later HCI assessment recommendation is a draft for review, not an adopted
instrument or a replacement participant protocol.

## Performance outcomes and their measurement

| Outcome | Operational measurement | Published support and limits for NewsMead |
| --- | --- | --- |
| Comprehension accuracy | Correct answers / scored passage questions; distinguish factual/inference questions, text access and delayed recall. Open responses require a fixed rubric. | Mézière et al. (2023) examined YARC, GORT-5 and WRAT-4 in 79 undergraduates and found task-dependent relationships with gaze. A custom news quiz does not inherit those tests' validation or norms. Comparable material, question difficulty, language and scoring need piloting. |
| Reading rate / passage completion time | Known passage word count divided by declared task minutes; for oral reading, correct-word rate can also be recorded under a fixed error rule. | IReST (2012) used stopwatch-timed oral reading of matched paragraphs in 436 normally sighted native readers aged 18–35 across 17 languages. Supports standardized speed assessment, not older-reader norms or unrestricted news visits. Speed alone cannot establish comprehension. |
| Oral word-reading accuracy | Count incorrectly spoken words under explicit coding rules; normalize by passage words if prespecified. Omissions, substitutions and self-corrections need consistent treatment. | GazePrompt (2024) measured word recognition errors in 13 low-vision adults reading aloud on a desktop. The procedure changes the reading task; silent-reading mistakes are not directly observable from gaze alone. Its word-support feature also differs from NewsMead's definition popup. |
| Line-switching performance | GazePrompt measures time from last fixation at the prior line end to a successful next-line fixation followed by a non-backward saccade; wrong-line deviations per passage line; and deviation magnitude in lines. | Direct HCI precedent for gaze-aware line aids, with 13 low-vision desktop readers. It found improved switching time but no significant condition difference in deviation frequency/magnitude. These are performance endpoints, not universally validated phone measurements. Our mapped gaze needs verification before using it to score errors. A backward movement is not inherently a mistake. |
| Reading-resumption lag | Elapsed time from interruption offset to the first stable reading pattern in the pre-interruption text, following an explicit annotation rule. | InteRead (2024) used controlled laboratory reading and a 1200 Hz Tobii Pro Spectrum. Two annotators' average resumption labels had ICC = 0.916 (absolute agreement, two-way mixed, average of 2 raters). This is concrete annotation-reliability evidence for that protocol, not for a phone detector, a single rater, or MD returning to baseline. |
| Visual reading capacity | MNREAD estimates maximum reading speed, critical print size and reading acuity using standardized print-size trials. | Baskaran et al. (2019) analyzed 101 Portuguese low-vision reading curves with 7 raters: MRS inter-rater ICC = 0.97; CPS ICC = 0.77. Reliability differs across parameters. The test targets visual access/print size, not news understanding or the overall benefit of our aids. |

## Primary sources

- Mézière et al., *Using Eye-Tracking Measures to Predict Reading Comprehension*:
  https://ila.onlinelibrary.wiley.com/doi/10.1002/rrq.498
- Trauzettel-Klosinski, Dietz and the IReST Study Group, *Standardized Assessment
  of Reading Performance: The New International Reading Speed Texts IReST*:
  https://doi.org/10.1167/iovs.11-8284 ; institutional author/research record and
  abstract: https://pure.johnshopkins.edu/en/publications/standardized-assessment-of-reading-performance-the-new-internatio/
- Wang et al., *GazePrompt: Enhancing Low Vision People's Reading Experience with
  Gaze-Aware Augmentations*, sections 4.1–4.3:
  https://arxiv.org/html/2402.12772
- Zermiani et al., *InteRead: An Eye Tracking Dataset of Interrupted Reading*,
  sections 3.2 and 4: https://aclanthology.org/2024.lrec-main.802.pdf
- Baskaran et al., *Scoring reading parameters: An inter-rater reliability study
  using the MNREAD chart*: https://doi.org/10.1371/journal.pone.0216775
- MNREAD developers' description: https://legge.psych.umn.edu/mnread

## Experience and behavior remain separate

Perceived mental effort is an experience outcome. Paas et al. (1994) used 1–9
ratings after instructional problems, reporting internal consistency across
repeated problems of alpha = 0.90 and 0.82. They explicitly did not establish validity
in that analysis; it is not older-adult news-reading validation:
https://ris.utwente.nl/ws/files/248299579/Paas1994measurement.pdf

Scanpath descriptors and MD can describe changes in observed gaze or departure
from a frozen reference. They do not inherently measure correct reading,
comprehension or experienced effort. Choosing them as a prespecified comparison
endpoint is distinct from claiming that lower values establish reader benefit.
Do not invent a combined speed/comprehension/MD index to resolve this distinction.

## Practical decision for the current prototype

For normal silent news reading, confirmed completion time/reading rate and
comprehension accuracy are practical independent outcomes to shortlist. Select a
primary endpoint based on the intended claim; retain the complementary outcome
and any tradeoff instead of treating speed alone as improvement. Replacing texts
or instructions in a standardized test does not preserve its norms automatically.

For a specific place-keeping claim, line-switching performance or resumption lag
is closer to the support mechanism but needs a controlled task and demonstrated
measurement/annotation quality. Published laboratory reliability cannot be
transferred to our approximately 26 coordinate outputs/s and imperfect word/line
mapping. Do not relax detector gates just to obtain more measurable events.

Current study packages record visit spans, not verified passage completion or
comprehension answers. These new outcomes require a linked task record, which
can initially be researcher-administered outside the app. Aids-on/off order and
passage assignment must be controlled; neither an MD reference baseline nor a
before/after-trigger comparison replaces that design. No primary outcome,
questionnaire or sample size is finalized by this shortlist.

## HCI assessment recommendation — 2026-11-09 draft

**Phase clarification:** The owner subsequently prioritizes an initial formative
usability round because natural aid exposure may be limited. The manuscript's
revised Section 4.1 and Appendix A interview guide now separate natural reading,
written initial feedback and a labelled preview, followed by post-task feedback.
The two-condition/per-article UES-SF proposal below is retained as a later-evaluation
candidate; it is not a required instrument or procedure for the initial round.
No questionnaire was adopted by the formative-guide revision.

### Proposed question and claim boundaries

How do gaze-responsive visual aids influence older adults' perceived focus,
ability to keep their reading place, and experience of reading news on a phone?

Retain both the reading-benefit and feasibility goals. A defensible early result
can describe reported benefits and problems alongside recorded behaviour. It
must distinguish perceived absorption, experienced continuity, software events
and tracker quality. None alone establishes objective sustained attention.

### Recommended assessment set

| Component | Proposed use | What it can establish / limit |
| --- | --- | --- |
| Full English UES-SF, 12 items, after each assigned article | Published questionnaire; examine the Focused Attention dimension as the main candidate numerical experience outcome; retain the other dimensions separately. | Subjective engagement, with Focused Attention emphasizing absorption/time passing. It is an adjacent measure for focus, not a direct test of concentration lapses or keeping one's place. Do not rename its result a reading-stability score. |
| Short interview after both readings, before debrief | Main direct evidence about keeping one's place, concentration, comfort, distraction and usefulness; ask for examples and contrary experiences. | Qualitative participant experience. Responses do not verify exact error counts or prove the aid caused the experience. |
| Existing per-article logs plus researcher notes | Describe aid decisions/interactions, scanpath outputs, both MDs, missingness, assistance, pauses and responsiveness. | Context for reported experience. A draw proxy does not confirm the participant noticed an aid; an algorithm output does not verify its classification against ground truth. |
| Prepared brief comprehension questions | Retain the manuscript's supporting engagement check after each article. | Not the leading reading-benefit outcome and not a validated custom-news test. Prepare a key for the selected texts; no score-based exclusion is adopted here. |

**Evidence.** O'Brien, Cairns and Hall (2018), sections 6/8 and Appendices A-B,
describe four three-item UES-SF dimensions: Focused Attention, Perceived Usability,
Aesthetic Appeal and Reward. Shopping samples numbered 427/779, with additional
digital-library data from 344 respondents. Table 5 reports short-form Focused
Attention omega .82 in shopping data. Older-adult phone-news validity is not
established. Preserve the published English items and five-point agreement
anchors; follow the authors' randomization/hidden-dimension guidance. Compute
separate three-item means, reversing usability responses as 6 minus raw response;
score repeated administrations separately. [Primary paper, including the actual
questionnaire and scoring](https://oro.open.ac.uk/68806/1/1-s2.0-S1071581918300041-main.pdf).

Our recommendation is all 12 items to retain the full instrument, with instructions
anchored to the just-completed reading and raw answers retained. No overall
engagement/stability composite is proposed. Our draft missing-data rule leaves
an affected subscale unavailable if an item is missing; this is a study rule,
not a claimed author requirement.

This recommendation is conditional on the owner accepting **perceived focused
engagement** as the numerical construct. If the intended claim specifically
requires demonstrated concentration or fewer losses of reading place, this
questionnaire is insufficient; the relevant independent measurement still needs
to be established. No arbitrary pass score, meaningful-change threshold or
older-adult norm is asserted. Do not fit a new factor model or claim local
psychometric validation from the few initial volunteers.

SUS remains an option for a separate overall-usability question. It is not added
to the core draft because it does not resolve the focus/place-keeping gap; a
single post-session score would describe the overall system, not the aid-condition
effect. [Brooke's original description](https://hci-studies.org/methods-and-measures/downloads/SUS_Brooke1996.pdf).
NASA-TLX measures multidimensional workload, so it is not substituted for a focus
assessment. [Official instrument scope](https://www.nasa.gov/human-systems-integration-division/nasa-task-load-index-tlx/).

### Direct reading-aid precedent and its limits

GazePrompt used experience ratings and interviews; its silent-reading study
included 13 low-vision adults aged 30-85 on a desktop. Sections 4.1.2 and 5.1
describe comfort/distraction/effectiveness questions, selected passages, and two
simple comprehension questions. Its silent-reading analysis focused on qualitative
experience. This supports a mixed-method reading-aid evaluation, not the validity
of a new NewsMead rating scale or transfer of its measured performance to a phone.
[Primary paper](https://arxiv.org/html/2402.12772).

Thought probes have been used with older readers: Jackson and Balota's reading
experiment included 32 younger and 38 older adults, 30-minute desktop reading,
self-caught reports and probes every 2-4 minutes. That is a possible attention
method, but adding probes would interrupt our reading and change the interaction.
It is not included in this initial draft. [Experiment 4](https://pmc.ncbi.nlm.nih.gov/articles/PMC3508668/).

### Draft interview prompts

These are study-specific qualitative prompts, not a standardized scored scale.
Use neutral condition labels and ask about both readings, including no difference.

1. How was it to read each article using this interface?
2. What, if anything, made it easier or harder to concentrate? Please describe an example.
3. How easy or difficult was it to keep your place? If you lost your place, how did you find it again? Record no loss or no occasion separately.
4. What changes, if any, did you notice on the screen? How did they affect your reading? Only after this open question, probe a noticed change's timing, position and duration.
5. Did anything feel uncomfortable or distracting? What would you change?
6. Which reading setup, if either, would you prefer for everyday news reading, and why? Could the article's topic, familiarity or difficulty explain part of that preference?

Do not lead with 'did the aid help?' or explain the withheld trigger rules before
the interview. A participant who noticed no aid has not rated its effectiveness
as zero. Do not require deliberately triggering a popup, looking away or thinking
aloud during the scored reading. Record requested assistance and offer breaks.
Provide readable assessment material; if needed, read the exact English wording
and anchors neutrally, and record assistance or misunderstood/skipped items.

### Fit to the existing app and session

The draft flow is each assigned article -> leave the article -> UES-SF for that
reading -> supporting questions/break; after both readings -> interview -> factual
debrief and data-use confirmation. Repeated questionnaires may cue attention to
the interface in the next reading. Use the same procedure in both conditions,
retain counterbalancing and acknowledge that limitation. This proposed extra
per-article assessment needs alignment with the existing procedure before use;
it is not silently inserted into the installed app or original forms.

The live feed is active (`StudyConfig.OFFLINE_MODE=false`); the local article
loader already exists. For a condition comparison, select and preserve exact
English news texts and balance article/condition/order using Chapter 4's existing
four assignments. Each person reads each comparison text once. Similar lengths
alone do not establish equal difficulty; review language, topic/familiarity and
content. The bundled sample texts have not been approved as study materials.
Keep the reference-baseline and practice passages separate from comparison tasks.
Follow-up implementation on 2026-11-09 adds **Saved -> Testing articles** as a
local packaged-material list while retaining the live feed. Its two entries are
setup samples, not selected real-news comparison material. No study article
selection or feed-mode switch has been made.

Keep assessment records local and coded. Link participant ID, task/condition,
actual aid-switch setting, questionnaire version, raw item IDs/responses,
reading start/end/completion and assistance notes. A condition label does not
operate the aid switch. Join after verified export through actual `run_id`,
`article_reading_id`, package `session_id` values and the rendered text's
`body_sha256`; one article visit can contain several package segments. Record
neutral task labels during the session, then reconcile IDs. The current screen
lists recent package session IDs; it has no questionnaire-entry UI. Never use
only a changing headline, URL, or phone installation ID as participant/task identity.

For the initial small pilot, show each participant's condition-specific subscale
scores and differences, interview findings and technical context. Participants
are the independent units; gaze points/windows are not extra participants. No
new significance test, sample-size claim, stability endpoint or formal hypothesis
is frozen by this recommendation. Retain unavailable MD/reference results and
exposure gaps explicitly rather than substituting zero.

### Outstanding choices before adopting this draft

- Accept the questionnaire's perceived-engagement scope, or require a different
  independent concentration measure. A validated place-keeping score has not
  been established in this bounded review; the draft treats continuity qualitatively.
- Name and preserve the English practice/reference/comparison materials and the
  supporting article questions/keys. No final article-specific assessments exist yet.
- Finalize item administration/order, missing-data rules and the versioned
  assessment record, then align Chapter 4 and the existing session instructions.
  The manuscript's recovery-latency-primary/index-driven wording remains stale.

This is assessment preparation. It does not validate the gaze model, demonstrate
reader benefit, replace the approved consent/screening/debrief documents or
establish that the pending live coordinate-reference fit succeeded.
