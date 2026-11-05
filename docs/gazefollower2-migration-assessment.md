# GazeFollower2 / GazeNet_Next migration assessment

Implementation progress: the subsequent [native prototype checkpoint](gazefollower2-native-prototype.md)
records successful host conversion/parity, an opt-in benchmark and tested ridge
candidate on `experiment/gazefollower2-native`, plus two passing camera-free
native tests on SM-A566B / Android 16 (2026-11-05). The review below is retained as
the research/design basis; its earlier “not run” statements describe that stage.

Reviewed 2026-11-04. Status: source review and static model-metadata extraction;
no Android implementation, model inference, conversion, camera or device check.

Follow-up below adds the user's Desktop clone, the actual study-collection
stash, executable filter checks and a more specific improvement/evaluation plan.
The earlier review is retained; collection is implemented but stashed, not absent.

## Decision

**A native Android candidate is worth prototyping. A measured accuracy gain is
not yet established.** Prefer investigating the new neural model through our
native pipeline before adopting the browser SDK, its calibration and its filter
as one package. Keep the existing MGazeNet implementation as the comparator.

This task started on clean branch `feature/local-study-collection`, NewsMead
commit `93a2abdef779278d52ab714fcfb0a3727e7f760b`. Collection work is a separate
workstream. This assessment does not authorize switching its branch or replacing
its active tracker. The ready-to-use task brief is
[gazefollower2-new-chat-handoff.md](gazefollower2-new-chat-handoff.md).

## Upstream identity and evidence

- The [original GazeFollower repository](https://github.com/GanchengZhu/GazeFollower)
  now directs readers to GazeFollower2. This is the same GitHub maintainer's
  successor project.
- Review pin: [GazeFollower2 commit
  `5befceb1a6e1297e094331a5c114b7d0173e7c14`](https://github.com/GanchengZhu/GazeFollower2/tree/5befceb1a6e1297e094331a5c114b7d0173e7c14),
  committed 2026-11-03 06:24:02 UTC. `package.json` declares version `2.0.0`.
  This pins the reviewed main-branch source; it is not a claim about a separately
  validated/tagged Android release.
- Public artifacts include FP32 ONNX, FP16 ONNX and PyTorch weights. The pinned
  tree does not contain a ready-made `GazeNet_Next.mnn` asset; the optional
  fine-tuning server includes an ONNX-to-MNN conversion path.
- The reviewed files provide implementation and structural tests. They do not
  establish a matched Android comparison with NewsMead, older-adult reading
  accuracy, or a gain on the A56/G991B. Current README recommendations describe
  desktop-style webcam/display conditions. Treat advertised accuracy/speed as
  upstream claims until their protocol and relevance are verified.
- Prefer pinned source over cached README snippets. During this review, the
  unpinned raw README response contained performance claims absent from the
  downloaded pinned README. No accuracy forecast here relies on those claims.

The [pinned license](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/LICENSE)
declares CC BY-NC-SA 4.0 for software, weights and documentation, with a separate
commercial-licensing notice. Retain notices and attribution for any adaptation;
the existing MGazeNet attribution does not identify the new model/version.

## Current Android baseline

Source anchors:

- `mgazenet-benchmark/vendor/assets/vendor-manifest.json`: GazeFollower revision
  `553920edcb7998c029828677f50f6d8eb4a16249`, original `base.mnn` and native-library
  hashes. MNN is version 3.6.1.
- `app/src/main/java/com/newsmead/gaze/mgazenet/MnnEstimator.kt`: native CPU
  Session API, four threads, default precision, fixed input/output checks and
  hard-coded original model hash.
- `Preprocessor.kt` and `GazeGeometry.kt` in that directory: RGB /255, NCHW,
  right-eye horizontal flip, MediaPipe-based crops and normalized rectangle
  metadata. Existing geometry preserves original Python rounding and rejection
  semantics; it is not identical to the new browser crop code.
- `SvrCalibration.kt`, `CalibrationIdentity.kt`, `CalibrationBundle.kt` and
  `MgazeNetCalibrationStore.kt`: separate X/Y RBF epsilon-SVRs, 258 features,
  model/localizer/pipeline/device/camera/screen identity and private persistence.
  Current production fitting uses 13 targets with 45 admitted samples per target.
- `MgazeNetGazeProvider.kt` and `MgazeNetLiveOutput.kt`: explicit validity,
  capture/delivery clocks, raw/selected coordinates, optional blink rejection and
  optional reading smoothing. `raw` means calibrated pre-smoothing coordinates.
- `MgazeNetReadingSmoother.kt`: One Euro filtering, minimum cutoff 0.7, beta 0.002.
- `app/build.gradle.kts`: asset generation/hash checks, MediaPipe Tasks Vision
  0.10.29 and OpenCV 4.11.0. Do not silently remove these packaging checks.

MGazeNet is already integrated. Some September handoffs describe a then-pending
integration or a different calibration/validation condition. Read the dated
checkpoint and current source before using those statements as current status.

## Actual new-model contract

Downloaded only into ignored review storage, not app assets:

| Property | Extracted value |
|---|---|
| File | `GazeNet_Next.onnx` |
| Bytes | 8,006,908 |
| SHA-256 | `5f635713b50134163545d1eb76868610253c4fadc467dd5c4f69bd271610b69e` |
| ONNX IR / opset | IR 7; standard-domain opset 14 |
| Producer | PyTorch 2.7.0 |
| Inputs | Float32 `face`: `[batch_size,3,224,224]`; `left`/`right`: `[batch_size,3,112,112]`; `rect`: `[batch_size,12]` |
| Output | Float32 **`outpu_0`**, `[batch_size,258]` |
| Graph summary | 83 nodes; 56 initializers; standard-domain operators in the extracted graph |

The important spelling is `outpu_0`, not our current `output_0`. Conversion can
affect names/shapes, so inspect the converted MNN asset as well. Bind a batch of
one explicitly where required. Matching feature count is not feature equivalence
and does not permit reuse of the current SVRs.

Evidence: `output/gazefollower2-review-2026-11-04/model-metadata.json` and
`static_onnx_metadata.py`. The script reads selected protobuf fields using the
[official ONNX field definitions](https://github.com/onnx/onnx/blob/main/onnx/onnx.proto).
It neither executes the model nor substitutes for the official ONNX checker,
shape inference, MNN conversion or numerical parity. ONNX and ONNX Runtime were
not installed in the inspected bundled Python runtime; no packages were added.

## Compatibility changes that matter

| Area | What is known / next action |
|---|---|
| Native inference | The four input interfaces and output width are promising. Upstream server code explicitly converts ONNX to MNN. Conversion and execution on our pinned Android MNN are still untested. |
| Crop/preprocessing | Both use RGB /255, NCHW and right-eye flip. New JS code rounds/clamps rectangles differently and uses Canvas resizing; existing Kotlin rounds landmarks first and preserves stricter crop rejection. Compare synthetic inputs and define the candidate's exact preprocessing version. |
| Output meaning | Upstream fine-tuning code treats the first two output values as gaze predictions and the remaining 256 as embeddings. Its fine-tuning labels use a desktop camera-relative centimetre convention. A 258-value output is not an Android pixel coordinate; preserve explicit personal mapping. |
| Calibration | Current Android uses two SVRs; new browser default is multivariate ridge regression. Start by assessing the new model with a freshly fitted native mapping. Assess ridge separately if needed; do not attribute a whole-pipeline improvement solely to weights. |
| Identity/storage | New model, mapping, preprocessing or filter requires distinct identity/configuration and fresh personal fitting. Preserve the old calibration artifact rather than overwriting it. Reading baselines and MD references must remain separated by their actual model/stream/context contracts. |
| Filtering/timestamps | Our One Euro path is distinct from the browser's default heuristic. The heuristic returns the centre of a five-sample window after warm-up; with look-ahead 2, the effective input age is roughly two sample intervals (about 67 ms at 30 Hz, before other delays). The browser loop calls its listener with the current loop timestamp. Do not import that timing convention into capture-time measurements or stack both filters silently. |
| Missingness | The browser coordinate listener alone does not reproduce our explicit no-face/blink/stale/control/lifecycle observation stream. Its no-face branch clears prediction state. Keep Android's reason-bearing observations and invalidation semantics. |
| Optional fine-tuning | Upstream sends face/eye tensors to a Python service, which archives calibration crops and annotations. This is a separate image-retention workflow. It has not been run or authorized for NewsMead; the first native candidate need not use it. |

Relevant pinned upstream files:

- [Estimator](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/src/GazeEstimator.js)
- [Crop/preprocessing](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/src/FaceAlignment.js)
- [Ridge mapping](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/src/MultivariateRidgeCalibration.js)
- [Heuristic filter](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/src/HeuristicFilter.js)
- [Browser loop](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/src/GazeFollower.js)
- [Fine-tuning service](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/finetune_server.py)

## Recommended staged implementation

1. **Isolate the candidate.** Keep collection work and the existing MGazeNet
   comparator intact. Coordinate a separate branch/checkout rather than switching
   a shared working directory under another active chat.
2. **Verify native compatibility.** Use the pinned FP32 ONNX model first. Convert
   in an isolated tooling environment if feasible; record converter/runtime
   versions and converted hash. Check batch-one tensors, output naming, finite
   results and numerical parity on deterministic non-personal tensors. If MNN
   conversion fails, report the concrete operator/runtime blocker before choosing
   an ONNX Runtime Android alternative. The upstream converter path is precedent,
   not proof that our runtime works.
3. **Integrate only the candidate seam.** Add explicit model/configuration identity
   and fresh calibration in separate storage. Preserve output gates, blink
   handling, provider lifecycle, clock semantics and the selected calibrated /
   smoothed path used by the dot, scanpaths, scrolling, aids and study inputs.
   Keep filters and mapping changes separately identifiable.
4. **Check software behavior.** Reuse existing preprocessing, calibration identity,
   selected-stream, blink, replay and lifecycle checks where they cover the
   changed contracts. Add focused model/output/parity checks. Obtain focused
   independent review of substantive numerical, timestamp or controller changes.
   Software success does not establish gaze accuracy.
5. **Compare on authorized phones.** Use freshly fitted old/new mappings, matched
   calibration effort, independent held-out target material, identical feedback
   conditions, font/layout and camera conditions. Record regional X/Y error,
   median/tail error, exact/neighboring line and word alignment where defined,
   usable coverage, cadence, latency and repeatability. Include the lower-screen
   region previously reported as unreliable. Dot-visible evidence describes that
   feedback condition; do not generalize it to unaided tracking.
6. **Decide promotion from evidence.** Predefine the practical improvement and
   unacceptable regressions before examining comparison results. Retain failures
   and per-phone differences. Do not tune on held-out validation. Phone readings
   are not guaranteed to improve because a desktop model or README claims gains.

The user has requested review/context preparation in this chat, not phone
installation, calibration, private capture or an active-model replacement.

## Existing evidence to carry forward

- `docs/agent-context.md`: current routing and dated evidence; its November
  baseline/collection developments supersede older plan-only statements.
- `docs/calibrated-smoothed-scanpath-path.md`: the accepted consistency contract.
- `docs/gaze-mgazenet-newsmead-integration-handoff.md`: native production integration.
- `docs/gaze-mgazenet-native-parity-results.json`: historical numerical parity.
- `docs/gaze-mgazenet-inference-profile.md` and associated results: historical
  performance conditions, not a benchmark of GazeNet_Next.
- `docs/gaze-mgazenet-calibration-observability-v3-results.md` and
  `docs/gaze-mgazenet-direct-validation-confirmation-analysis.md`: earlier
  calibration/held-out outcomes, including failures and protocol limitations.
- `docs/gaze-current-vs-mgazenet-evidence.md`: a historical comparison with another
  NewsMead tracker; do not treat its then-current label as today's baseline.
- `docs/study-data-pipeline-source-map.md`: collection and clock/context seams.
- `docs/gaze-coordinate-md-core.md`: separate experimental coordinate MD;
  neither coordinate MD nor R/D/F MD may select assistance.

## Review artifacts and limits

`output/gazefollower2-review-2026-11-04/` retains pinned source, tree/commit metadata,
the downloaded public ONNX file and static extraction receipt. This directory is
review storage, not an app model package or participant dataset. No upstream
scripts, servers or model inference were executed. No model conversion, Gradle
build, package install or device action was performed. One bounded read-only
scout supplied current Android source/provenance; lead inspected upstream code
and the public ONNX metadata. Android compatibility, performance and accuracy
remain open.

## Follow-up: local clone, current source and research usefulness

Reviewed again on 2026-11-04 for the owner's accuracy, responsiveness and
reliability goal. This is investigation and a proposed experiment, not an active
model replacement. Existing algorithms are comparators, not permanent constraints.

### Source state and checks

- NewsMead remains at `93a2abdef779278d52ab714fcfb0a3727e7f760b` on
  `feature/local-study-collection`. The three review/checkpoint files were already
  dirty when this follow-up started; their previous content was preserved.
- The chat **Plan gaze data collection (study collection)** says its collection
  and notebook implementation was saved in stash
  `44840d4fe761501dc24671358a9e4e40119cd50d` on November 4. Relevant source was
  inspected directly with `git show 44840d4:<path>`; the stash was not applied.
  Missing collector files in the working tree therefore do not mean the feature
  was never implemented. Later integration must reconcile this saved work.
- Desktop clone `C:/Users/USER/OneDrive/Desktop/GazeFollower2` has local main ref
  `5befceb1a6e1297e094331a5c114b7d0173e7c14`. Ten core files match the prior pinned
  snapshot after normalizing CRLF/LF. Its FP32 model hash matches the metadata
  table above exactly. Git could not change into that external directory under
  the sandbox, so this is a targeted content/ref check, not a clean-tree claim.
- Fifteen upstream host JavaScript calibration/filter tests passed. A separate
  monotonic ramp through the actual `HeuristicFilter(2)` returned
  `0,1,2,3,2,3,4,5,6,7` for inputs `0..9`. This verifies two-sample delay after
  warm-up, including a backwards warm-up transition. It does not measure phone
  latency or validate reading-event preservation.
- Reproduction commands, receipt and test output are retained in
  `output/gazefollower2-review-2026-11-04/followup/`. No inference/conversion,
  Gradle/device/camera work or dependency installation was performed.

### What the repository comparison actually establishes

The original repository's current README describes 7M and 32M as **training-image
counts**, not parameter counts or file sizes. It identifies the released 32M
weights with the successor repository. That is author-reported training
provenance; the reviewed files do not independently establish its dataset
composition, subgroup coverage or superiority for older-adult Android reading.
The public FP32 ONNX is approximately 8 MB.

The original repository's current main branch has also evolved: its
[`GazeFollower.py`](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/GazeFollower.py)
now defaults to multivariate ridge and heuristic filtering. NewsMead's pinned
adaptation uses RBF SVR and optional One Euro. Therefore “v1 versus v2” must
identify exact weights, preprocessing, mapping and filter settings; current
upstream defaults are not a faithful description of the app's comparator.

Three findings refine the earlier migration advice:

1. **V2's `svr` option is also ridge.**
   [`SVRCalibration.js`](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/src/SVRCalibration.js)
   fits separate linear ridge models, despite its name. The default multivariate
   ridge implementation excludes the intercept from regularization, whereas
   this legacy-named variant penalizes it. Neither is NewsMead's OpenCV RBF
   epsilon-SVR. Ridge is a worthwhile native candidate because prediction is a
   small linear mapping, but better accuracy or a material total-latency saving
   must be measured. Its regularization depends on feature/label scaling and
   sample count; copying a browser lambda is not a validated phone setting.
2. **Filtering changes both responsiveness and the signal used for research.**
   At evenly spaced 30 Hz, two samples represent about 66.7 ms; at 15 Hz, about
   133.3 ms. With irregular cadence, use actual source times. The filter replaces
   local extrema; an extremum could be noise or real gaze movement. Its README
   “no latency” description is inconsistent with the executed sample behavior.
   Keep unfiltered calibrated estimates available and evaluate One Euro,
   heuristic filtering and no filtering separately. A smoother dot is not an
   independent accuracy test. A delayed filter needs source-sample provenance,
   emission time and reset semantics across gaps.
3. **The shipped fine-tuning workflow is narrower than full neural adaptation.**
   `finetune_server.py:340–474` extracts frozen 256-D embeddings, trains a
   `256 -> 128 -> 2` MLP head on CPU and replaces four ONNX head tensors. It uses
   the development targets for early stopping/best-epoch selection. Its displayed
   improvement compares the uncalibrated base head against the selected head on
   that development set; it is not a test against freshly fitted NewsMead SVR,
   nor an independent test-set result. The client defaults to laptop dimensions
   and a top-centre camera convention. A phone implementation would need verified
   geometry/labels, a separate test set and a deliberate image-retention workflow.
   Fine-tuning is a later experiment, not a prerequisite for using the new model.

### Empirical evidence and limits

The primary [2025 GazeFollower paper](https://doi.org/10.1145/3729410), sections
3.2–4.2 and Table 2, reports 31 young volunteers (mean age 22.45) using an HP
laptop at approximately 45–55 cm. With filtering, personalized fine-tuning plus
calibration reported mean spatial error 0.92 cm, compared with 1.12 cm for
calibration alone. It used separate within-person target sets: 13 fine-tuning,
12 calibration and 20 evaluation points. Its training updated layers across the
network, unlike v2's shipped frozen-embedding head procedure. The source was
retrieved as indexed primary article text; direct ACM access returned 403, and
an independently accessible PDF/page mapping was not obtained. These results
motivate testing personalization, not an accuracy forecast for this Android app.

The publisher abstract of [Smartphone eye-tracking with deep learning: Data
quality and field testing](https://doi.org/10.3758/s13428-025-02718-y) reports a
32-person benchmark against EyeLink: accuracy 1.32 versus 1.20 degrees, precision
0.177 versus 0.028 degrees. It also describes a separate 98-person field study.
Full methods, phone hardware, sampling and participant-age details were not
available in the accessible abstract. It does not compare these public old/new
weight files. Neither retrieved study establishes a GazeNet_Next improvement in
older-adult Android reading, line/word assignment, or reading benefit.

### Study collection: what can already be analysed

In the saved implementation, `research/study/StudyCollection.kt:230–300` records
provider metadata, model hash/calibration identity, calibrated pre-smoothing XY,
selected XY, capture/delivery clocks, validity reasons, eyelid telemetry,
observed counters and capture-context links. Here **raw means calibrated
pre-smoothing coordinates**, not camera pixels or an independent gaze reference.
Aid decisions, presentation callbacks and interaction events are also retained;
count meaningful aid episodes separately from repeated movement/update records.
Their association with gaze can reflect the trigger mechanism and does not by
itself demonstrate an intervention benefit.

`docs/study-collection-local-v1.md:77–78` in the stash explicitly excludes neural
feature arrays. They are transient and cleared by the camera source after its
callback. Consequently:

- Existing packages can support exploratory scanpath, timing, missingness,
  raw/selected-stream and aid-event analyses.
- They cannot reconstruct the camera input, re-run old sessions through new
  weights, refit a new feature-to-screen mapping, or establish gaze accuracy
  without independent targets/reference measurements.
- Coordinate-MD reference fitting remains separate from the integrated R/D/F
  baseline. Model/calibration/preprocessing/filter changes require explicit
  reference compatibility; a changed MD distribution may reflect changed
  measurement error. Neither MD is an accuracy measure or an aid controller.

For a model experiment, build on this collector. Add a stable tracker/configuration
identifier covering model hash, preprocessing version, runtime/backend/precision,
mapping type/parameters and an explicit filter version. Existing metadata already
contains several components, including filter flags/cutoff/beta. Add target
position/onset/settled intervals for validation, declared coordinate units,
per-stage timing summaries with median/tail values, and an explicit accounting
of admitted, rejected and unobserved frames. Keep capture time separate from
delivery time and UI draw proxies; software timestamps alone do not establish
photon-to-display latency. No camera-image retention is necessary for ordinary
reading collection. Any optional feature diagnostic would be a new, bounded
collection capability rather than something old packages already contain.

### README-based choice for NewsMead

The owner asked for an explicit choice after the compatibility review. The full
local README was read through, including its browser requirements and hardware
recommendations. The strongest next engineering option is **native Android
integration of GazeNet_Next FP32 with fresh personalized calibration, including
a ridge-versus-SVR comparison**. This selects a development direction; it does
not declare an empirically winning model or mapping before phone measurements.

| Option | Priority and reason |
| --- | --- |
| Native new-model candidate | First. Fits the Kotlin app and existing capture/collector interfaces, allows explicit validity/timing, and tests the new estimator directly. Start with the existing MNN route, contingent on conversion and numerical checks; direct ONNX Runtime Android remains an alternative if evidence warrants it. |
| Ridge calibration | Include in the first candidate experiment. The README deliberately makes it the default, and it is a compact regularized mapping. Compare against freshly fitted SVR under the same samples and validation targets; existing SVR is not a permanent constraint. |
| Full browser/WebView pipeline | Low priority. The README itself lists mobile browsers as experimental and not recommended for precision research. Its iOS bridge injects ARKit/TrueDepth coordinates into web callbacks; it is not an Android camera implementation. |
| FP16 model | Later performance experiment. The README advertises reduced size and GPU/WebGPU use, without measured Android CPU accuracy/speed parity. Establish the FP32 reference first. |
| Personalized neural head fine-tuning | Later accuracy experiment if base-model-plus-mapping results leave unacceptable systematic errors. It introduces a Python backend, image/tensor handling, additional target collection and separate model selection/test requirements. CPU support does not mean the repository already implements training inside an Android app. |

Source: [pinned README](https://github.com/GanchengZhu/GazeFollower2/blob/5befceb1a6e1297e094331a5c114b7d0173e7c14/README.md),
Calibration & Data, Personalized Neural Fine-Tuning, iOS Native App Integration,
and System Requirements & Browser Compatibility. Its hardware recommendations
describe a desktop monitor/webcam environment. Moving to native inference does
not eliminate handheld motion, viewing-angle effects or thermal constraints.
Neither its advertised angular accuracy nor its compatibility tests establish
NewsMead reading accuracy. Retain checks under the intended phone use conditions.

Use the existing 13-point stationary routine as the first matched comparison
condition; evaluate shorter or pursuit routines only with a measured need. The
README's point table recommends ridge lambda 1 for 13 points, while its API text
and constructor default are 3. Set and log the actual value explicitly and select
it using development data. Do not present either value as validated for NewsMead.
Keep raw calibrated output and one identified display-filter path. The default
look-ahead filter should not be adopted automatically given its demonstrated age.

### Proposed implementation and decision sequence

1. **Native model feasibility:** use the pinned FP32 ONNX and verify conversion
   to the existing MNN runtime with synthetic input/output parity, correct output
   name, batch-one shapes, finite results and exact preprocessing. Inspect the
   converted graph and compare its output numerically with an ONNX reference.
   Synthetic checks are the first gate; representative valid camera-derived
   input ranges/crops, including boundary geometry, need later explicitly
   authorized validation before generalizing parity. Existing reading packages
   cannot provide those missing inputs.
   Consider ONNX Runtime Android only if a concrete MNN compatibility/performance
   result warrants it. FP16 size reduction alone does not establish CPU speed.
2. **Small controlled comparison:** A = current model + freshly fitted RBF SVR;
   B = new model + freshly fitted RBF SVR; C = new model + ridge. Compare A/B under
   matched calibration effort and filtering; compare B/C using the same candidate
   calibration samples. Evaluate raw outputs first, then selected outputs.
   This permits algorithm changes while identifying what actually helps.
3. **Phone/task validation:** use independent, spatially distributed stationary
   targets and separate movement/gap trials, including the lower reading region.
   Counterbalance model order, repeat sessions, and record device, posture,
   distance/lighting, glasses, layout, font and dot-feedback conditions. Keep
   development tuning and final evaluation separate. Use the existing benchmark
   and analysis tools where suitable; do not invent a parallel incompatible
   recording format. Later representative older-adult testing is still needed.
4. **Decision measures:** report horizontal/vertical bias and median/tail spatial
   error in pixels, precision during known stationary targets, error relative to
   actual line spacing, target-conditioned line/word hit rates, usable coverage
   with its denominator, gap/recovery durations, fresh-output cadence and
   capture-to-consumer age. Convert to cm/degrees only with defensible physical
   geometry. Present per-phone/person results, not just pooled frame averages.
   Prespecify practical improvement and unacceptable regressions before viewing
   test results. No universal line-level accuracy threshold is established here.
5. **Responsiveness/reliability work:** profile localization, preprocessing,
   inference, mapping, queue and main-thread delivery separately. Current code
   performs a synthetic native-persistence self-check at each source start and
   rejects busy inference slots after localization. Caching that self-check and
   moving work admission earlier are hypotheses to benchmark, not demonstrated
   fixes. Earlier gating could remove no-face/blink evidence and change MediaPipe
   timing/drop denominators; preserve explicit skipped-observation semantics.
   Do not loosen the 500 ms expiry gate to manufacture better coverage.
6. **Promotion:** choose the best evidenced pipeline, fit fresh personal
   calibration and appropriate baseline references, and retain the old pipeline
   as a rollback comparator. Finish tracker selection before a fixed study phase
   where possible; otherwise label/split model eras explicitly. Gaze measurement
   validation and independent reading/acceptability outcomes answer different
   research questions.

The next concrete engineering deliverable is an isolated native candidate and
comparison harness with these records. This follow-up did not switch branches,
restore the stash, change app behavior, access private recordings, install on a
phone, start capture, stage or commit. Two bounded helpers covered native/stashed
source and primary empirical evidence; the lead reviewed upstream code and checks.
The native-source helper also reviewed this follow-up: no blocking issue; its
feature-clearing and synthetic-parity-limit clarifications were incorporated.

### Analysis-method attribution

The scientific-critical-thinking skill informed the distinction between
implementation claims, tuning evidence and independent validation. Its methods
reference is Kassis, T., Agarwal, V., He, Y., Patel, D., & Brueckner, A. M. (2026),
[*Scientific Agent Skills: A Library of Procedural Knowledge for Research
Agents*](https://doi.org/10.48550/arXiv.2609.00065). The current arXiv record was
checked on 2026-11-04. This is procedural guidance, not gaze-validation evidence.
