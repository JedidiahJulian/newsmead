# Proposed Thesis Project Scope

**Status:** Working scope for discussion, dated 2026-10-27. This is not an
approved participant protocol. Ethics/committee review, participant criteria,
readiness criteria, retention rules and final study duration remain open.

## Thesis focus

Assess the **technical feasibility and participant acceptability** of
StabiliRead's gaze-supported news reading on compatible personal Android phones.
The first phase should establish whether study sessions can be started, recorded,
recovered and interpreted with clear measures of gaze availability and prompted
placement agreement. It should also describe how the existing adaptive cues
behave and how participants experience them.

**Primary question:** Can intended older-adult users complete study setup and
news-reading sessions on their own compatible Android phones while producing
recoverable records with quantified gaze availability and prompted placement
agreement, and what usability or acceptability issues arise with the current
adaptive support?

This is a feasibility question. It does not ask whether StabiliRead improves
reading comprehension, speed or recovery, or whether one detector is more
accurate than another.

## Refined objectives

1. **Prepare the StabiliRead study build** with participant account creation,
   sign-in/sign-out, article reading, the existing recommendation flow and
   ordinary app logs. Keep research records in a separate, access-controlled
   namespace. The current study configuration bypasses sign-in, so this workflow
   needs to be checked before recruitment.
2. **Complete the gaze and session collector** so a clearly started session
   records the available numerical gaze observations, timing, mapping context,
   data gaps, configuration, aid decisions, draw acknowledgments, interactions
   and session boundaries. Preserve local recovery and record integrity; do not
   save camera frames or face video.
3. **Create and validate a pseudonymous study dataset** that can distinguish
   reading completion, record completeness and upload status. Retain the
   provenance needed to interpret timestamps, coordinate frames, layout changes,
   calibration and missing data. Treat gaze-derived scanpath labels and current
   thresholds as exploratory system outputs, not ground truth.
4. **Characterize the current adaptive behavior** by recording when each aid is
   eligible, evaluated, selected, withheld or drawn, and how readers interact
   with it. Describe the implemented rules accurately; do not label gaze loss as
   inattention or rereading as difficulty.
5. **Run a staged feasibility study** with volunteers on their own compatible
   phones. Begin with supervised onboarding and short supervised readings.
   Consider an independent home-use phase only after the device, calibration,
   recording and recovery checks meet criteria set in advance. Participant
   number, age threshold and home-use duration are protocol decisions, not fixed
   by this scope.
6. **Analyze feasibility and acceptability descriptively**, reporting participant
   and device counts, explicit denominators, failures, missingness, data
   availability, prompted placement results, recording/recovery/upload integrity,
   aid behavior and feedback. Summarize interviews separately and use them to
   interpret observed usability issues. Do not pool repeated sessions as if they
   were independent participants.
7. **Prepare a conference paper** that reports the feasibility findings and
   limitations without claiming clinical benefit, validated difficulty
   detection, gaze accuracy from tracking eligibility alone, or causal reading
   improvement.

## Study boundaries and sequence

- Use participants' own compatible Android phones and let them choose news
  articles through the normal reader/recommendation experience. Record article,
  device, layout and configuration context; retain exact article text only under
  an approved collection and retention policy.
- First test setup, prompted placement checks and several short readings under
  supervision. A later home phase is optional and conditional on reliable
  collection. The number of days and daily reading schedule remain open until
  the procedure is piloted.
- For the initial supervised collection, keep session packages on-device and
  use researcher-controlled export to approved research storage. Verify the
  exported package against its manifest and hashes before deleting any local
  copy. Participant cloud uploads are deferred.
- Keep this phase observational and feasibility-focused. Do not claim causal
  effects from self-selected articles or changing phone/environment conditions.
  A controlled aid-versus-no-aid or detector-comparison study would be a later,
  separately designed phase.
- Report capture availability separately from placement agreement. A high
  agreement among available observations does not show accuracy during all
  reading time. Report unavailable intervals and collection losses rather than
  silently excluding them.
- Keep article/reading context, participant feedback and computed system labels
  distinct. A displayed cue or draw acknowledgment is not proof that the reader
  noticed it or that it helped.

## Firebase Spark implications

The user reports having access to the existing Firebase project, which is on the
**Spark plan**. The app already has Firebase Authentication and Cloud Firestore
dependencies, but the study sign-in flow is bypassed and the study-data collector
and upload adapter are not implemented.

- Cloud Firestore is available on Spark within its no-cost quotas. Current
  published Firestore quotas include 1 GiB stored data, 50,000 document reads
  and 20,000 document writes per day, and 10 GiB of outbound transfer per month.
  Only one database per project qualifies for the free quota. These limits are
  shared by apps in the project, so existing project usage must be checked.
- A Firestore document is limited to 1 MiB. Avoid one write per gaze sample.
  This does not block local data collection. The initial route is researcher
  export; cloud sync is future work. Keep the record schema independent of
  Firebase so a later adapter can read finalized packages, use stable package
  and chunk IDs, verify hashes, and record transport receipts separately from
  analytical events. If Firestore is selected later, test compressed chunks
  below the document limit against project quotas and security rules.
- Firebase Cloud Storage is **not available on Spark**; Firebase currently
  requires the Blaze billing plan to provision or access its Storage buckets.
  The thesis does not require a plan upgrade. Revisit hosted file storage only
  if a later phase needs it and has an approved billing arrangement.
- Use pseudonymous study IDs in analytical records, restrict participant access
  through reviewed Firestore rules, keep any account-to-study-code mapping
  separately protected, and define retention/deletion before recruitment.

This is a design boundary, not an implemented Firebase integration. The
[study-data framework](study-data-framework.md) defines the proposed local record
contract and recovery requirements.

## Decisions required before recruitment

- Ethics/committee approval, participant eligibility and age threshold.
- Sample-size rationale and predefined device/session readiness criteria.
- Whether the optional home phase is needed, and its duration and schedule.
- Exact calibration/placement checks and the interpretation limits for those
  checks.
- Article text/licensing, account mapping, access, retention, withdrawal and
  deletion procedures.
- A researcher export/import procedure and the secure destination for study
  packages. If independent home use is retained, verify participant-friendly
  retrieval or a cloud-sync adapter before beginning that phase.
- Measured package sizes, existing project usage, Firestore security rules and
  quota behavior in a bounded test before deciding whether future remote chunks
  are viable.
- Whether any efficacy or detector-comparison question belongs in a later
  controlled study.

## Evidence and service references

- [Firebase pricing plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans)
- [Cloud Firestore pricing and free quota](https://firebase.google.com/docs/firestore/pricing)
- [Cloud Firestore limits, including the 1 MiB document cap](https://firebase.google.com/docs/firestore/quotas)
- [Cloud Storage billing requirements for Spark projects](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024)
- [Current research checkpoint](agent-context.md)
- [Current study-data framework](study-data-framework.md)
