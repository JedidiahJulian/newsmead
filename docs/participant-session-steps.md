# Researcher session steps

Use with the approved consent, screening and debriefing documents. This is an
operator checklist for the current app, not a new eligibility policy, questionnaire
or analysis protocol. Research approval is confirmed by the owner elsewhere.

Session order: consent and screening -> this person's calibration -> Start run ->
Reading baseline -> assigned articles and questions -> Stop run -> survey ->
interview -> debrief and data-use confirmation -> transfer.

## Before the person arrives

- Have the approved consent form, screening record, correctly scaled near-vision
  card at its specified distance, background questions and debriefing record ready.
- Have the named practice passage and the assigned comparison articles ready,
  with their comprehension questions and scoring key. Choose the condition/article
  order in advance, following the approved counterbalancing procedure.
- Have the actual selected usability survey and interview questions ready. The
  thesis describes these activities but does not establish a finalized survey
  instrument by naming them. Do not invent a combined reading/MD score.
- Use a private participant code such as P001. Keep names, signatures, screening
  answers and any code-to-person key separate from the numerical gaze packages.
- Use the installed study build. Ordinary article gaze/FPS diagnostics are hidden;
  numerical gaze collection and the existing aid rules remain active.

## 1. Consent and screening

Explain the approved form in accessible language. Before the signature, ask the
person to explain what they will do and their right to stop in their own words.
Clarify once if needed; follow the approved capacity rule. Give the signed copy.
Obtain separate permission before any interview audio; the current form does not
authorize reading/practice audio recording through that checkbox.

Apply the approved screening guide: its current copy specifies age 45+, habitual
phone-reading correction, a correctly scaled near-vision card at the card's stated
distance, binocular 20/40 or better, reported relevant diagnoses, background
questions and a held-out 150-200 word practice passage. Record failed criteria and
reasons consistently. These are study criteria, not a clinical diagnosis.
Keep the person's usual correction, reading posture and lighting consistent.

Two differences require attention in the approved researcher instructions:

- The guide's 16-point/LOOCV/green-amber-red calibration gate is obsolete for this
  build. Current MGazeNet uses 13 targets and 6 checks, with engineering PASS/RETRY
  values in centimetres. Record the actual result and follow the approved retry
  and exclusion procedure. Do not equate PASS with verified word/line accuracy
  or invent a conversion to the old bands. Align the outdated written calibration
  criterion with the current model before using that criterion to screen people.
- Study recording OFF prevents study packages, not ordinary article camera
  startup. The documented unrecorded practice needs the agreed workflow; opening
  a normal article is not a camera-off preview. Complete consent before it and
  do not use a previous person's calibration for the new person's reading.

## 2. Calibrate the current person

Home -> **Calibrate eye tracker**. Complete and save the person's calibration
using the current screen instructions. Follow the approved retry/exclusion rule.
The saved tracker profile is not bound to a participant code: changing P001 to
P002 does not replace it. Never reuse the previous person's calibration.

Participant wording: "Please look at each target and follow the on-screen
instructions. We are checking whether the camera can estimate where you look.
Tell me if you need a break."

## 3. Start a run and collect the reading baseline

1. Open **Study recording**. Enter the participant code and first assigned
   condition label, select **Reading**, then **Start run**.
2. Return Home -> **Reading baseline** -> **Start**. Read the reader-facing
   instruction; the researcher handles technical setup and status.
3. Ask the person to read normally and scroll as needed. Honour a request to stop.
   This is a separate setup passage; it is not an automatic 20-second warm-up in
   the comparison article.
4. Use the footer's covered time and valid-window count. After at least 60 seconds
   covered and 30 valid windows, use **Finish** and inspect the actual result.
   An incomplete or unavailable reference stays explicit; follow the approved
   retry decision.
5. R/D/F readiness concerns the behaviour reference. **Raw ref: fit pending** means
   the coordinates were recorded for fitting after transfer, not that a coordinate
   model passed. Keep the same calibration/font/orientation for subsequent readings.

Participant wording: "Please read this passage as you normally would and scroll
when needed. You may ask for a break or stop at any time."

## 4. Conduct each assigned article reading

1. Open the assigned article. The researcher opens **Reading aids** and sets its
   main enabled switch to the assigned ON/OFF condition before asking the person
   to begin. Check this for **every article**. The run's condition label does not
   operate this switch. Keep other settings consistent with the approved procedure.
2. Record when participant reading begins/finishes and any setup, pause or change
   in settings. The saved visit includes setup/excluded intervals; it does not by
   itself confirm article completion or measure words actually read.
3. Say: "Please read this article as you normally would. There will be a few
   questions afterwards. You do not need to make any particular feature appear.
   Tell me if you want to pause or stop."
4. Avoid asking them to trigger an aid or explaining the withheld trigger mechanism
   during reading. Use the approved limited-disclosure responses when asked.
5. At the end, leave the article. Ask its prepared comprehension questions and
   record answers outside the gaze package, linked by participant and task IDs.
   Give the planned break. Record technical failures and requests for help.

For the next condition: **Stop run**, change its label and **Start run** again with
the same participant. The separate frozen baseline can carry across readings in
the same process while calibration/settings remain compatible. Check the actual
aid switch for the next article. After a process restart or calibration change,
collect the appropriate new baseline; do not silently substitute an old source.

## 5. Close the session and handle data

1. Leave the article and **Stop run**. Administer the selected usability survey,
   then the interview, then the approved factual debriefing. Interview audio is
   recorded only if separately permitted.
2. The repository debriefing copy still claims index-driven adaptation and recovery
   latency as the main outcome. Align that explanation with the actual initial
   study before using it. MD observes deviations; it does not drive the aids.
3. Ask whether permission to use the data is maintained after debriefing and record
   the answer. Follow the approved withdrawal/deletion procedure across stored and
   transferred copies. Do not pressure the person to retain their data.
4. In **Study recording**, refresh saved recordings and select this participant's
   article readings. **Transfer selected recordings** includes available linked
   shared baselines. Verify the received packages before relying on them; preserve
   the original files as required by the approved retention procedure.

The camera-free UI checks passed, but a fresh live coordinate-reference fit after
the viewport fix has not been verified. The owner chose to defer that engineering
rehearsal. Keep coordinate status truthful; retain available raw data and other
outcomes without presenting missing scores as zero or successful fits.

## Quick session record

Keep this with the private researcher records, separate from signed consent.
Use the same codes as the app. Repeat the article row for each task. Record events
as they happen; do not reconstruct precise reading times from memory.

| Field | Entry |
| --- | --- |
| Participant code / date / researcher | |
| Consent, screening and optional interview-audio decision recorded | |
| Fresh calibration saved / result / retries | |
| Shared baseline result: R/D/F / raw-reference status | |
| Assigned article order and condition labels | |
| Article/task ID / actual aids switch / reading start and finish | |
| Breaks, assistance, lag, interruptions or setting changes | |
| Comprehension-answer record / survey / interview record IDs | |
| Debrief completed / permission to retain data confirmed | |
| Selected recording IDs / transfer location / copy verified | |
