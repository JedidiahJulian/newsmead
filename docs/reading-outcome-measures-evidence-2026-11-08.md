# Reading outcome measures: evidence shortlist

2026-11-08. Source review and proposed options, not a selected endpoint or an
approved participant protocol. No app behavior, task, baseline or detector changes.
The owner wants established measures rather than another invented instability
index. Retain the existing scanpath and both observational MD measurements.

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
