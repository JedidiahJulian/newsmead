# Article demo and word-definition aid

Updated 2026-10-04 · `measurement/scanpath-pilot` · experimental implementation.

## Current behavior

Loaded article → diagnostics (three dots) → **Article aid demo**. The actual
article cycles every four seconds: baseline, current-line highlight, line-start
marker, near-word definition, light focus, strong focus. Previous/Next/Stop work
inside the article. Source text and fonts are unchanged.
The article body now reserves a fixed **16 dp start gutter**. Its line-start
marker is drawn in that gutter, with a **4 dp gap** before the first letter,
in both the live article and the demo. Padding is present from layout inflation,
including baseline stages, so showing the aid does not move the text. The narrower
text area may change wrapping; gaze/word anchors use the actual padded TextView
geometry. A clipped gutter causes marker abstention instead of text overlap.
The source word is highlighted for the whole consultation. Close uses a larger
vector × in a compact 28 dp gray box, within a transparent 48 dp tap target.
The card now follows the internet template described below. The revised layout
has not been installed or visually checked on a phone.
Short articles omit unavailable focus previews. Demo stops the gaze provider and keeps the camera
off after Stop/pause/return; reopen the article to resume live reading. Demo
actions/receipts have no detector evidence and never enter the live journal.

Ordinary articles use `DEFINITION_PROFILE_V2` / `EXPERIMENTAL_DEFINITION_V2` /
`CONTEXT_WORD_DEFINITION_V1`. Old magnifier IDs retain their historical meaning.
The pilot and replay now select the same definition profile with distinct
`PILOT_DEFINITION_*_V2` / `REPLAY_DEFINITION_V2` condition IDs. Replay's gallery
uses the article's current six-stage demo. Frozen comparison protocols and
measurement identities are unchanged; old magnifier results are not pooled.

The [continuation marker](scanpath-continuation-marker.md) now appears only after
supported beginning-to-end line progress is followed by a landing away from the
expected next line. It marks the expected continuation with fixed visibility;
the demo previews that form directly in the article. Normal P09 sweeps produce
no marker. Earlier V1 cue records keep their historical meaning.

| Step | Implemented behavior | Limit |
| --- | --- | --- |
| Current word | Existing raw I-DT candidate/visit, rendered UTF-16 word assignment, eligible geometry and causal fresh evidence | Agreement is not exact-word accuracy. |
| Help opportunity | At least **800 ms observed current-word time**: finalized same-word visit time plus a nonoverlapping established prefix, or the prefix alone | Configurable exploratory setting, not a validated difficulty cutoff. No obligatory two refixations. |
| Definition | Card above/below the word, inside the visible article, without covering its source line; scrollable meanings, attribution, Close | Neighboring lines may be covered. Common meanings do not establish the intended sentence sense. Hidden/ambiguous geometry or insufficient room causes abstention. |
| Consultation | Card stays open until Close, scrolling/layout/text change, disabling help or lifecycle teardown | Conservatively excludes the entire open interval from article-path interpretation; does not assert every fixation is on the card. |
| Return | Opening/closing breaks sequence continuity. Late pre-boundary frames excluded. A dismissed word cannot reopen until a different eligible target is observed | No popup-to-article regressions or new requests from consulting the card. |

The usual 1500 ms action TTL does not dismiss consultation. Policy resets at
opening; the popup owns its lifecycle. The action envelope describes initial
selection; explicit consultation boundaries describe the commanded display
interval. Demo stage change clears its card. A definition is an aid type, with
no magnification/ordinal ladder. Line/focus rules retain their existing controls.

## Internet layout template and offline preview — 2026-10-04

The selected visual reference is the official
[Flowbite React Default Popover](https://flowbite-react.com/docs/components/popover#default-popover).
Its shaded title area, divider, padded content, rounded outline and pointer are
adapted in native Android XML. The larger close control, scrollable meanings,
source attribution and persistent source-word highlight serve this app's existing
requirements. Colors and Arial styles follow NewsMead's day/night theme.
Source provenance: [official component documentation](https://flowbite-react.com/docs/components/popover)
and [project MIT license](https://github.com/themesberg/flowbite-react/blob/main/LICENSE).
The native implementation is independently written; no React/Flowbite dependency
or webview was added. This template supplies a layout reference, not evidence of
reading benefit; H03/M25 below remain the relevant interaction precedents.

`OfflineWordDefinitions` bundles **75 English entries**, including common news
words and function words. These are original, concise NewsMead glossary entries,
not quotations from Free Dictionary API and not a complete English dictionary.
Every bundled definition is labeled **Offline · NewsMead English glossary**.
The lookup order is memory cache → bundled glossary → online API. A bundled hit
returns immediately, without creating the lazy network queue or waiting for an
HTTP timeout. Unknown words still require the online service; on failure the
card honestly shows unavailable text. There is no guessed definition or automatic
morphological expansion. Neither dictionary supplies sentence-sense disambiguation.

The article demo prefers an actual, completely visible article word covered by
the glossary, prioritizing words of at least four UTF-16 code units. Its line must
also have a completely visible successor for the line-start stage. If no covered
word is available, it retains an actual article word and the usual online/unavailable
behavior. Source text and UTF-16 offsets are preserved. With an article already
loaded, the covered definition stage works without an internet connection; this
change does not add offline news fetching or a full dictionary download.

Native resources: `view_word_definition_popover.xml`, `bg_definition_popover.xml`,
`bg_definition_close.xml`; lookup/demo changes are confined to the aid package.

## Primary studies

**H03 — Hyrskykari, Majaranta & Räihä (2003), _Proactive Response to Eye
Movements_, INTERACT, pp. 129–136.**
[Author-uploaded paper](https://www.researchgate.net/publication/221054255_Proactive_Response_to_Eye_Movements).
Read §4.2.1, §4.2.2, §5.1–5.2 and the original gloss/dictionary figure.
iDict placed translation help near words, accumulated fixation time including
returns, and adjusted timing for word length/frequency. Its typical approximately
800 ms setting varied with those factors. It avoided further accumulation/redraw
during consultation. Readers also intentionally looked longer to request help.
The SMI iViewX exhibition used visitor feedback (approximately 60 visitors,
47 note sheets), without a controlled comprehension benefit test. This is an
interaction precedent; the flat threshold, English definitions and older phone
readers remain unvalidated.

**M25 — Minas et al. (2025), _Adaptive Real-Time Translation Assistance Through
Eye-Tracking_, AI 6(1), 5.** [Publisher DOI](https://doi.org/10.3390/ai6010005),
[author-uploaded paper](https://www.researchgate.net/publication/387693494_Adaptive_Real-Time_Translation_Assistance_Through_Eye-Tracking).
Read §3, results and user feedback. The Tobii Spectrum desktop technical-text
experiment analyzed 47 predominantly young adults after exclusions. It reports
more words read and mixed popup feedback, including intrusion and wrong-line
help after movement. Its letter-dependent trigger differs. Reading count is not
comprehension/retention evidence; group-test analysis limits interpretation of
the within-participant experiment. English definitions and older phone readers
were not tested.

The owner's reported top-magnifier distraction motivates replacing the aid.
A nearby definition still draws gaze. Consultation gating addresses recording
interpretation; participant testing must establish distraction and reading benefit.

## Dictionary and teammates' branch

Fetched `origin/Recommendation` at
`501e9be1f83d4d962dc4931ef9044ab1f18a3e1b` without checkout/merge.
Inspected `data/news/NewsDataApi.kt`, recommendation application paths and
`logging/Analytics.kt` / `AppLog.kt`. Bounded tree/source searches found no
dictionary endpoint. Its analytics contract excludes free text; dictionary words
and gaze are not added to that remote analytics route here.

The replaceable `WordDefinitionRepository` uses the bundled glossary above and,
for uncovered words, the [official Free Dictionary API](https://dictionaryapi.dev/),
a 128-entry memory cache and existing Volley request cache. HTTPS requests send one normalized
English word, without article text, user ID or gaze. Timeout 2500 ms, no retry.
One bounded host request for the reference example `keyboard` timed out after
8 seconds; no definition/source response was received. API availability is not
verified and no second host request was attempted.
Dismissal cancels requests; invalidated callbacks cannot reopen the card.
Returned source/license attribution is displayed. Missing words/network failure
show unavailable text. Demo definitions are never fabricated.

Only explicitly English original articles permit lookup. Unsupported live article
languages abstain from definition display entirely; the render-only demo can show
an unavailable-language card to explain coverage. A teammates' backend dictionary
can later implement the repository contract. No backend deployment, database
write or branch merge ran.

## Recording and checks

- Monotonic elapsed milliseconds, screen-pixel raw coordinates, UTF-16 offsets.
- Session input callbacks retain numeric raw observations during consultation,
  with `definition_consultation` / `definition_boundary_late_capture`, absent
  article target and false acceptance. The private bounded aid journal retains
  inputs/boundaries and eviction counters; it is not a complete study recording.
- The gate activates immediately. Engine mutation waits for callback completion,
  preventing recursive fixation flush if finalized evidence opens the popup.
- Text/geometry/lifecycle invalidation cancels lookup. Host screen origin is
  subtracted for overlay positioning; the paragraph does not reflow.
- Provider shutdown completes before demo presentation; demo callbacks/receipts
  are detached from the closed runtime. No camera session starts from demo.

The first local compile found a nullable receiver error; it was corrected.
All **61 focused JVM tests** and app/test APK assembly then pass. New camera-free Android
fixtures compile for source/UTF-16 anchors, geometry boundaries, stale lookup,
unsupported language, response parsing and consultation opening inside finalized
P12 callbacks without duplicate fixations or bridged transitions. Their device
execution is pending. Earlier A56 15-test evidence belongs to the pre-definition
build. API availability, phone rendering, natural word targeting, distraction and
reading benefit are not established by compilation or synthetic checks.

2026-10-03 local check: **61 tests, zero failures/errors**, app and instrumentation
APK assembly pass after the language guard and finalized-resumption regression.
XML parsing and `git diff --check` pass. Machine-readable summary, APK hashes
and copied JVM XML evidence: `output/article-definition-check-01/summary.json`
and neighboring `TEST-*.xml`. Independent review's reentrancy and non-vacuous
resumption findings are addressed; no new device attempt ran.

2026-10-04 template/offline check: **64 focused JVM tests, zero failures/errors or
skips**, app and instrumentation APK assembly pass. The four popup resource XMLs
parse and `git diff --check` passes. New JVM tests check real bundled content,
attribution, normalization and honest unknown-word rejection. Camera-free Android
fixtures additionally compile for immediate repository delivery and covered-word
selection/fallback with unchanged source offsets; these fixtures have not run on
a phone. Summary, APK hashes and copied JVM XML:
`output/article-definition-offline-check-01/summary.json` and neighboring XML.
No device installation or capture ran. These checks do not establish phone
appearance, natural gaze accuracy or reading benefit.

2026-10-04 line-gutter follow-up: app/test APK assembly, article layout XML parsing
and whitespace checks pass. The camera-free rendering regression compiles and
asserts nonempty marker pixels remain separated from the text at maximum width.
It has not executed on a device; phone appearance remains pending. No device
install or capture ran.

Evidence appraisal used scientific-critical-thinking;
[Kassis et al. (2026), _Scientific Agent Skills_](https://arxiv.org/abs/2609.00065)
describes tooling, not reading-aid evidence.

## Recommended checkpoint (Windows CMD)

These paths include the preceding uncommitted demo/compatibility work required
by this revision. They exclude private output, local skills/environment files
and unrelated untracked documents. Nothing was staged or committed by the agent.

```bat
git add -- app/src/main/java/com/newsmead/gaze/aid app/src/main/java/com/newsmead/gaze/scanpath app/src/main/java/com/newsmead/activities/ScanpathReplayActivity.kt app/src/main/java/com/newsmead/fragments/article/ArticleFragment.kt app/src/main/res/layout/activity_scanpath_pilot.xml app/src/main/res/layout/activity_scanpath_replay.xml app/src/main/res/layout/fragment_article.xml app/src/main/res/values/strings.xml app/src/androidTest/java/com/newsmead/gaze/aid app/src/androidTest/java/com/newsmead/gaze/scanpath/replay/ScanpathReplayActivityTest.kt app/src/test/java/com/newsmead/gaze/aid app/src/test/java/com/newsmead/gaze/scanpath/ConsultationCaptureGateTest.kt app/src/test/java/com/newsmead/gaze/scanpath/replay/ReplayDispatchTimingTest.kt docs/README.md docs/agent-context.md docs/progress-notes.md docs/scanpath-adaptive-aid-design.md docs/scanpath-aid-runtime.md docs/scanpath-live-pilot.md docs/scanpath-replay-screen.md docs/scanpath-device-check-04.md docs/scanpath-trigger-evidence-review.md docs/article-definition-aid.md
git add -- app/src/main/res/drawable/ic_definition_close.xml app/src/main/res/drawable/bg_definition_close.xml app/src/main/res/drawable/bg_definition_popover.xml app/src/main/res/layout/view_word_definition_popover.xml
git add -- app/src/main/java/com/newsmead/activities/ScanpathPilotActivity.kt app/src/androidTest/java/com/newsmead/gaze/scanpath/pilot/ScanpathPilotActivityTest.kt
git diff --cached --check
git diff --cached --stat
git commit -m "Align article, pilot and replay with contextual reading aids"
```
