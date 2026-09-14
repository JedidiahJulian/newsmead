# NewsMead Manuscript Revision Packet

> **Historical draft.** The current research plan, source-to-claim map,
> implementation corrections, and manuscript-ready wording are maintained in
> [manuscript research plan](README-reading-instability-and-gaze-measurement-plan.md). This packet predates the current `main.pdf`
> section layout and MGazeNet pipeline; use the README when revising the thesis.

Prepared from the current manuscript PDF, the project documentation, and the
implemented gaze and scaffold controller.

## Purpose

The current manuscript describes one baseline-relative Reading Stability Index
as both the real-time scaffold trigger and the study outcome. The implemented
system uses two related but non-interchangeable signals:

- **Session Reading State Index (Session RSI):** a cumulative 0--100
  descriptive outcome for the article-reading session.
- **Adaptive Instability Index (AII):** a smoothed 0--4, participant-relative
  recent-window process variable used by the scaffold controller.

The manuscript should separate these signals everywhere. Neither signal is a
comprehension score.

## Required revision map

| Location in the current manuscript | Required action |
| --- | --- |
| Abstract | Replace the singular “stability index” description with the two-signal description below. |
| Section 1.3.2, Limitations | Add circular measurement contamination, the limited meaning of gaze confidence, and the required spatial-validity measures. |
| Section 1.5, Research Methodology | Clarify that the AII drives adaptation and that Session RSI is a cumulative descriptive outcome. |
| Section 3.5, The Reading Stability Index | Replace the single-index, dual-role model with the two-measure conceptual model. |
| Opening of Section 3.6 | Replace the sentence that calls RSI the real-time baseline-relative trigger. |
| Section 4.3.1, System Architecture | Replace the single “stability index” pipeline with a branch producing Session RSI and AII. |
| Section 4.3.4 | Replace with “Session Reading State Index.” |
| New Section 4.3.4.1 | Add “Adaptive Instability Index.” |
| Section 4.3.5 | State that AII, not Session RSI, selects scaffolds; add thresholds, gates, persistence, and re-entry evidence. |
| Section 4.4.1 | Replace “the stability index driving graded scaffolding” with “the AII driving graded scaffolding.” |
| Section 4.4.4 | Distinguish externally anchored recovery latency from controller cycle duration. |
| Section 4.4.6 | Add the variable-role table, time-series treatment, synchronized records, and quality-sensitive analyses. |
| Formative/pilot method | State how provisional thresholds and timing parameters will be tuned and frozen before the main evaluation. |
| Design rationale or discussion | Record direct Session RSI control as considered but not adopted. |

---

## Manuscript-ready text

### Abstract replacement sentence

Replace the sentence beginning “This system introduces a stability index...” with:

> The system computes two complementary gaze-derived measures: a cumulative
> Session Reading State Index for describing detected reading effort and a
> participant-relative Adaptive Instability Index for selecting temporary,
> graded visual support from recent reading behavior.

The abstract should not claim that either index directly measures comprehension
or cognitive load.

### Section 1.5: Research Methodology replacement

Replace the paragraph beginning “A continuous gaze-derived stability index will
be computed...” with:

> The system computes two complementary gaze-derived measures. The Session
> Reading State Index is a cumulative 0--100 summary of regression, dwell, and
> fixation behavior across the current article-reading session and is retained
> as a descriptive research outcome. The Adaptive Instability Index is a
> participant-relative measure calculated from the same classes of behavior
> within a rolling 10-second window. Recent values are standardized against the
> participant's baseline, combined, bounded, and smoothed before being supplied
> to the adaptation controller. The Adaptive Instability Index, rather than the
> cumulative Session RSI, determines the desired level of visual support.
>
> Adaptation is trigger-based and conservative. Support is introduced only
> after elevated behavior persists, increases one level at a time, and is
> withdrawn after sustained recovery. The controller also requires a completed
> participant baseline and sufficient on-text gaze coverage. Word highlighting,
> line emphasis, a focus window, and re-entry assistance provide increasingly
> strong support. Re-entry assistance additionally requires evidence that the
> reader looked away from the text and returned at a displaced line.

### Section 3.5 replacement: Reading-State Measures

Suggested revised heading:

**3.5 Reading-State Measures**

Replace the current Section 3.5 with:

> The theories established in the preceding sections require a real-time
> representation of current reading instability while also motivating a
> session-level description of gaze-derived reading effort. These needs are
> related but occur over different time horizons. The study therefore uses two
> complementary measures: the Session Reading State Index and the Adaptive
> Instability Index.
>
> The Session Reading State Index is cumulative. It summarizes the regression,
> dwell, and fixation behavior detected across the article-reading session and
> supports descriptive comparisons between articles or experimental
> conditions. Because it retains the history of the session, it is not used to
> control temporary visual support. Difficulty or tracking error early in an
> article could keep a cumulative value elevated after the reader recovers,
> whereas a late difficulty episode could be diluted by a long period of stable
> reading.
>
> The Adaptive Instability Index represents recent departure from a
> participant's characteristic reading behavior. It must be continuous because
> both instability and scaffold intensity are graded; participant-relative
> because ordinary reading rates differ across readers; and multi-metric
> because fixation, dwell, and regression signals are individually ambiguous.
> The convergence of these signals provides evidence of a disturbance in
> reading behavior, although it does not prove cognitive difficulty.
>
> The Adaptive Instability Index drives scaffold selection and withdrawal.
> Session RSI remains a cumulative descriptive outcome. Neither measure is a
> comprehension score, and the two scales must not be pooled or treated as
> interchangeable. Recovery latency is analyzed using explicitly timestamped
> disruption and recovery events together with the recent index and scaffold
> transitions. The formal definitions of both measures are provided in Chapter
> 4.

Replace the opening reference in Section 3.6 with:

> The preceding section established the Session Reading State Index and the
> Adaptive Instability Index as complementary gaze-derived measures. Their
> validity depends on the quality of the gaze estimates from which fixation,
> dwell, regression, and intervention location are inferred.

### Section 4.3.1: System Architecture replacement paragraph

> The system follows a real-time pipeline in which gaze capture and
> area-of-interest mapping feed fixation, dwell, and regression detection. The
> detected events then branch into two measures. The cumulative Session RSI is
> retained for session description and condition-level analysis, while recent
> event-counter changes are supplied to the participant-relative Adaptive
> Instability Index. The smoothed Adaptive Instability Index, together with
> gaze-coverage and loss-of-position evidence, is evaluated by the scaffold
> controller. The selected intervention is then rendered on the NewsMead
> article view, and the reader's subsequent behavior closes the loop.

Recommended replacement for the current pipeline:

```text
Gaze capture
  -> line/word area-of-interest mapping
  -> fixation, dwell, and regression detection
       |-> Session RSI (cumulative 0-100 descriptive outcome)
       `-> Adaptive Instability Index (recent smoothed 0-4 process variable)
             + gaze-coverage gate
             + loss-of-position evidence
             -> scaffold controller
             -> one graded visual intervention
```

### Section 4.3.4 replacement: Session Reading State Index

Suggested revised heading:

**4.3.4 Session Reading State Index**

> The Session Reading State Index is a cumulative measure of detected reading
> effort across the current article-reading session. It combines regression,
> dwell, and fixation components on a 0--100 scale using weights of 0.40, 0.35,
> and 0.25, respectively. Higher values indicate that the gaze-derived reading
> pattern contains more behaviors associated with rereading, hesitation, or
> effort. Session RSI is a descriptive research outcome; it is not a
> comprehension score and does not directly select visual-scaffolding levels.

The implemented formula is:

```text
Session RSI = 100(0.40R + 0.35D + 0.25F),
```

where:

- \(R\) is the confirmed regression count divided by the number of lines
  reached, bounded to the interval \([0,1]\);
- \(D\) is accumulated dwell time divided by elapsed session time, bounded to
  \([0,1]\); and
- \(F\) is fixation rate divided by a reference rate of 120 fixations per
  minute, bounded to \([0,1]\).

> Regression receives the largest engineering weight because confirmed
> backward line movement is treated as the strongest of the three available
> indicators of rereading. Dwell receives the second-largest weight, and
> fixation rate the smallest, because both can also reflect ordinary careful
> reading or individual reading style. These weights encode the prototype's
> current design priorities; they must not be presented as population-optimal
> coefficients and should be examined during pilot validation.
>
> A cumulative measure is unsuitable for immediate adaptation. Difficulty or
> tracking noise early in an article may keep Session RSI elevated after the
> reader recovers, while a new difficulty episode late in a long stable session
> may be diluted by earlier history. Session RSI is also not normalized against
> each participant's normal reading pattern. It is therefore retained for
> end-of-session description and comparison of articles or experimental
> conditions rather than real-time scaffold selection.

### New Section 4.3.4.1: Adaptive Instability Index

Insert immediately after Section 4.3.4:

**4.3.4.1 Adaptive Instability Index**

> The Adaptive Instability Index is a recent, participant-relative measure used
> for real-time scaffold selection. It is computed from regression rate, dwell-
> time proportion, and fixation rate within a rolling 10-second window. Every
> 500 ms, after at least two seconds of recent observations are available, each
> metric is standardized against the participant's baseline. Only positive
> deviations in the direction associated with increased effort are retained.

For metric \(i\), the positive standardized deviation is:

```text
z_i+ = max(0, (x_i,recent - mean_i,baseline)
               / max(SD_i,baseline, SD_i,floor)).
```

The raw index is:

```text
AII_raw = clamp(0, 4, 0.40z_R+ + 0.35z_D+ + 0.25z_F+).
```

The implemented standard-deviation floors are:

| Metric | Standard-deviation floor |
| --- | ---: |
| Regression rate | 1.0 regression/minute |
| Dwell-time proportion | 0.05 |
| Fixation rate | 5.0 fixations/minute |

> The standard-deviation floors prevent a near-zero baseline variance from
> producing an arbitrarily large index. The raw value is exponentially smoothed
> with a two-second time constant. For update interval \(\Delta t\), the
> smoothing factor is \(\alpha=1-e^{-\Delta t/2s}\), and the updated index is
> \(AII_t=AII_{t-1}+\alpha(AII_{raw,t}-AII_{t-1})\). The smoothed value is the
> Adaptive Instability Index supplied to the scaffold controller.
>
> The AII is reported in weighted z-derived index units. It must not be
> interpreted as an exact number of standard deviations or assigned a
> standard-normal percentile because its components are truncated at zero,
> weighted, calculated from overlapping windows, capped, and smoothed.
>
> The current prototype automatically estimates a fresh baseline during the
> first 20 seconds of each article. This is an engineering fallback and is not
> the intended main-study baseline protocol. For the main study, each
> participant will first read standardized passages under the intended device
> position, lighting, font, layout, and posture. The resulting mean and standard
> deviation of each metric will be persisted and reused across the participant's
> experimental articles. Baseline duration and test-retest reliability will be
> checked during the pilot before the profile is used in the main evaluation.

### Section 4.3.5 replacement: Adaptation and Graded Visual Scaffolding

> Visual-scaffolding levels are selected using the smoothed Adaptive Instability
> Index rather than the cumulative Session RSI. The controller applies only one
> intervention at a time and escalates or withdraws support conservatively to
> avoid rapid visual oscillation. The desired state is determined as follows:

| Adaptive Instability Index | Desired state |
| ---: | --- |
| Less than 1.0 | No scaffold |
| 1.0 to less than 1.5 | Level 1: word highlighting |
| 1.5 to less than 2.0 | Level 2: line emphasis |
| 2.0 to less than 2.5 | Level 3: five-line focus window |
| At least 2.5 with loss-of-position evidence | Level 4: re-entry support |
| At least 2.5 without loss-of-position evidence | Level 3: five-line focus window |

> Level 1 dynamically emphasizes the currently targeted word. Level 2
> emphasizes the current reading line. Level 3 softly de-emphasizes text outside
> a five-line region centered on the current line. Level 4 directs the reader
> toward the last stable line after evidence of loss of position. These
> interventions retain the literature basis already discussed in this section:
> gaze-contingent highlighting, line support, moving-window research, and
> re-entry cues.
>
> Adaptation remains disabled until baseline collection is complete. At least
> 65% of gaze samples in the recent window must land on visible article text.
> This value is a coverage gate, not proof that the correct line or word was
> identified. Elevated behavior must persist for 1.5 seconds before escalation,
> while recovery must persist for 3.5 seconds before withdrawal. Both escalation
> and withdrawal proceed one level at a time. Low gaze coverage clears the
> active intervention instead of allowing possible tracking loss to be treated
> as reading difficulty.
>
> Level 4 requires evidence beyond an elevated AII. In the current operational
> definition, gaze must remain off the visible article text for at least 800 ms
> and then return at least two lines away from the last stable line. The
> resulting re-entry evidence remains eligible for 10 seconds. A high index or a
> regression alone does not activate Level 4.
>
> The thresholds 1.0, 1.5, 2.0, and 2.5; the 65% coverage gate; the 1.5- and
> 3.5-second persistence intervals; and the current loss-of-position rule are
> provisional engineering parameters. The literature supports the direction of
> the selected features and participant-relative adaptation but does not
> validate these exact cutoffs. The final parameters will be tuned and frozen
> using pilot data with independently labeled stable, difficulty, tracking-
> error, loss-of-position, and recovery episodes.

### Alternative considered: direct Session RSI control

Add at the end of Section 4.3.5 or in a design-rationale subsection:

> Direct mapping of cumulative Session RSI to scaffold levels was considered
> but not adopted. Its historical accumulation can preserve early difficulty
> after recovery, dilute a late difficulty episode after prolonged stable
> reading, and apply non-personalized absolute cutoffs to readers with different
> normal reading patterns. Direct control would also require a new set of
> empirically validated 0--100 thresholds; the current AII thresholds cannot be
> converted directly to Session RSI ranges. A high Session RSI would not replace
> the independent loss-of-position evidence required for Level 4.

Do not include illustrative Session RSI bands as study parameters.

### Formative pilot and parameter-freezing addition

Add to Section 4.1 or immediately before the main experimental design:

> A separate formative pilot will be used to select the adaptive-controller
> parameters before the main evaluation. Pilot sessions will collect a
> standardized participant baseline and multiple reading passages containing
> independently labeled stable reading, mild difficulty, sustained rereading,
> loss-of-position, recovery, and tracker-error periods. Candidate thresholds
> and persistence intervals will be evaluated using detection sensitivity,
> false-activation rate, intervention latency, recovery latency, spatial
> placement accuracy, and participant acceptability. Tracker-error periods will
> be excluded from behavioral threshold estimation or analyzed under a separate
> quality label. The selected values, selection procedure, and any parameter
> changes will be documented and frozen before the main data collection.

### Section 4.4.1 terminology correction

Replace:

> the adaptive interface, with the stability index driving graded scaffolding

with:

> the adaptive interface, with the Adaptive Instability Index and controller
> conditions driving graded scaffolding

### Section 4.4.4 replacement: Measures and Recovery Latency

> The primary outcome is recovery latency following an explicitly timestamped
> disruption or loss-of-position event. For a disruption marked at time \(t_d\),
> recovery time \(t_r\) is the first subsequent time at which the prespecified
> stable-reading criterion is met and remains met for its confirmation interval.
> Recovery latency is \(t_r-t_d\). The disruption marker, recovery criterion,
> confirmation interval, and treatment of tracker-error periods will be fixed
> during the pilot and applied identically to the adaptive and static
> conditions.
>
> The application also records controller cycle duration: the time from the
> first transition out of the no-scaffold state until the controller returns to
> no scaffold. This implementation-derived duration is a process measure and is
> not interchangeable with externally anchored recovery latency. Both
> timestamps will be retained so that scaffold activation, behavioral recovery,
> and controller withdrawal can be examined separately.
>
> Secondary outcomes include comprehension score and reading speed in words per
> minute. Gaze-derived secondary measures include regression frequency,
> fixation rate or duration, dwell-time proportion, cumulative Session RSI, and
> the trajectory of the Adaptive Instability Index. Scaffold transition counts,
> time spent at each scaffold level, false activations, intervention latency,
> controller cycle duration, and gaze-quality measures will contextualize the
> primary outcome. Neither Session RSI nor AII will be interpreted as a direct
> measure of comprehension or cognitive load.

### Section 4.4.6 addition: Variables, records, and analysis

Insert before the current paragraph describing the paired statistical test:

| Variable | Research role | Scale and horizon |
| --- | --- | --- |
| Session RSI | Descriptive outcome for participant, article, or condition comparison | Cumulative 0--100 |
| Adaptive Instability Index | Time-varying process variable controlling support | Smoothed 0--4 over recent 10-second behavior |
| Scaffold transition | Timestamped intervention event | NONE, WORD, LINE, FOCUS, or REENTRY |
| Externally anchored recovery latency | Primary recovery outcome | Milliseconds from marked disruption to confirmed recovery |
| Controller cycle duration | Implementation process measure | Milliseconds from first activation to return to NONE |
| Gaze coverage confidence | Controller quality gate | Proportion from 0 to 1 over the recent window |
| Spatial gaze quality | Validity/quality measures | Error, line accuracy, dispersion, jumps, loss, and drift |

Then add:

> Session RSI and the Adaptive Instability Index have different definitions,
> scales, and time horizons and will not be pooled or treated as
> interchangeable. Session RSI may be compared as an overall article- or
> condition-level outcome. AII will be retained as a time series and analyzed
> together with transition timestamps, recent raw metrics, gaze coverage,
> spatial gaze quality, disruption markers, and recovery events. Descriptive
> analyses will report the frequency and duration of each scaffold level, false
> activations, intervention latency, withdrawal latency, and the distribution of
> AII surrounding independently labeled events.
>
> Participant-level condition comparisons for the primary outcome will follow
> the paired procedure described below. If multiple disruption episodes per
> participant are analyzed as separate observations, the analysis will account
> for their clustering within participants rather than treating all episodes as
> independent. The final unit of analysis and aggregation rule will be specified
> before main data collection.
>
> Analyses based on gaze-derived events will be accompanied by gaze-quality
> summaries. Episodes that fail the preregistered spatial-validity or tracking-
> quality criteria will be excluded from the primary behavioral analysis or
> retained under a separate tracker-error label, as determined before the main
> study. Sensitivity analyses will compare conclusions with and without
> borderline-quality episodes.

The synchronized study record should retain:

- raw gaze coordinates;
- raw and stabilized line and word targets;
- fixation, dwell, and regression events;
- cumulative Session RSI;
- raw and smoothed AII;
- recent regression, dwell, and fixation metrics;
- baseline readiness and baseline profile identifier;
- on-text gaze coverage and spatial-quality measurements;
- scaffold state, transition reason, and rendered target;
- externally marked disruption and recovery timestamps; and
- controller activation, withdrawal, and cycle-duration timestamps.

### Section 1.3.2 addition: Gaze quality and circular contamination

Add after the existing consumer-camera limitation:

> The Adaptive Instability Index and scaffold location depend on the same gaze-
> estimation stream. Tracking noise may therefore create artificial line
> changes, regressions, fixations, or dwell events that increase inferred
> instability while simultaneously placing the intervention on an incorrect
> word or line. This circular measurement contamination limits both
> instability-detection validity and intervention-placement validity. A
> successful scaffold transition cannot, by itself, prove that genuine reading
> instability occurred.
>
> The prototype's current gaze-confidence value measures only the proportion of
> recent samples that land somewhere on visible article text. It does not
> measure distance from true gaze, correct-line accuracy, dispersion, head
> stability, or drift. An estimate on the wrong line can therefore count as a
> valid sample. Before participant-facing claims are made, the complete pipeline
> will be evaluated on the target devices and population using median and
> 95th-percentile vertical error in pixels and line-height units, exact-line and
> within-one-line accuracy, word-selection accuracy, dispersion, jump rate,
> off-text rate, tracking loss, and drift across scrolling and session time. If
> exact-line or word accuracy is insufficient, the intervention hierarchy will
> be revised toward a broader region-level scaffold or the gaze provider will
> be replaced.

---

## Claims to avoid until pilot validation is complete

- Do not say that Session RSI selects scaffolding.
- Do not call AII a comprehension score or a direct cognitive-load measure.
- Do not describe an AII value of 2.0 as “exactly two standard deviations above
  baseline.”
- Do not present 1.0, 1.5, 2.0, 2.5, 65%, 1.5 seconds, 3.5 seconds, 800 ms, or
  two lines as validated final study parameters.
- Do not treat on-text coverage as proof of correct-line or word accuracy.
- Do not treat a scaffold transition as independent confirmation of genuine
  instability.
- Do not report the prototype's activation-to-NONE duration as the primary
  disruption-to-recovery latency.
- Do not state that the standardized cross-article participant baseline is
  already implemented. The current build still uses a 20-second per-article
  fallback unless a baseline profile is supplied.
- Do not make unconditional word-level or exact-line performance claims until
  they are validated on the target hardware and population.

## Pre-main-study implementation and reporting checklist

- Implement and verify persistence and reuse of the standardized participant
  baseline across experimental articles.
- Pilot the baseline duration and evaluate its reliability.
- Independently label stable, difficulty, loss, recovery, and tracker-error
  periods.
- Tune, document, and freeze controller thresholds and timing parameters.
- Define the externally anchored disruption and recovery criteria.
- Log disruption-to-recovery latency separately from controller cycle duration.
- Define spatial-validity and tracking-quality exclusion rules.
- Report the required error, line-accuracy, dispersion, jump, loss, and drift
  measures.
- Revise word/exact-line scaffolds if the spatial-validity gate is not met.
- Update the manuscript with the final frozen pilot values before the main
  evaluation.
