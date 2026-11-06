# Explore NewsMead recordings in Jupyter

Open `gaze_study_exploration.ipynb`. It is an exploratory inspection workflow for
the saved local recordings, with a checked default pointing to the earlier
`storage-check` performance visit. The later popup attempt is inspected separately.
This work does not change the phone app or the baseline procedure.

From the project root in PowerShell:

```powershell
python -m jupyterlab notebooks/gaze_study_exploration.ipynb
```

Choose **Python 3 (ipykernel)**, then **Run → Run All Cells**. This computer already
has the required packages. For another Python environment, the tested versions
are in `requirements-gaze.txt`:

```powershell
python -m pip install -r notebooks/requirements-gaze.txt
```

The checked environment is Python3.10.6, pandas2.2.3, NumPy1.26.4,
Matplotlib3.10.3, JupyterLab4.3.2, ipykernel6.29.5, nbformat5.10.4 and NBClient0.10.1.
Package installation is unnecessary in the checked environment.

## Selecting data

The notebook's settings cell contains:

```python
SOURCE = REPO_ROOT / "output/study-live-check-04/packages"
SELECTED_SESSION_ID = "f22986df-be2f-494a-bc5b-2ef0ebc00261"
POPUP_SESSION_ID = "60bb4931-432b-4f27-85db-0718b23cb3d4"
```

`SOURCE` can be a parent directory containing package directories, one package
directory, or an exported ZIP. Select a **transport session ID** from the catalog.
The article-reading ID groups provider segments and is a different identifier.
Set `POPUP_SESSION_ID=None` when investigating another dataset without that second
visit. The main and optional popup visit stay separate throughout.

The catalog reads **declared manifest metadata**. The selected recording then
passes through the existing strict importer. A successful load checks stored
bytes/hashes, gzip decoding, identities, counts, sequences and loss metadata; it
does not establish tracking accuracy or reading behaviour.

For a received multi-package export, first verify that the entire declared
package set arrived:

```powershell
python tools/gaze/study_transfer_check.py path/to/received.zip --output output/received-check.json
```

This catches a missing whole package, which cannot be discovered by checking only
the packages that remain. Raw package directories copied over ADB have per-package
integrity but do not carry the ZIP's complete export inventory.

## What the notebook produces

| Working table | Contents |
| --- | --- |
| `gaze` | Raw and selected x/y; original reasons/flags; capture/delivery clocks; derived offsets/latency |
| `rdf` | Native inputs/boundaries/window ticks; R/D/F values; native MD/MD²/status |
| `scanpath` | Per-producer outputs and IDs; compact layout metadata; direct pattern events separated by type |
| `aids` | Support/evaluation/decision records; action IDs/types; offers, consultation and layout proxies |
| `boundaries` | Logical starts/ends and explicit context transitions |
| `health` | Provider/configuration/collector health and otherwise ungrouped compact facts |
| `references` | Frozen R/D/F reference/fit metadata and focused profile JSON |

These are compact projections, not replacements for the complete source archive.
Every row retains its package/session/clock/record identity. Ambiguous field
collisions reject rather than overwrite provenance. Native nulls, statuses and
MD=0 survive; large geometry/context/history blobs are omitted from working tables.
Use the original study importer when the complete payloads are required.

Plots cover recorded coordinate cadence/latency, raw versus smoothed position
distributions, native R/D/F features and score availability, direct pattern output
records, and the separate lookup-event timeline. Images and CSVs are saved under
a new `output/study-notebook/<run>/` directory with a provenance receipt. Set
`WRITE_EXPORTS=False` for in-memory inspection only. The tracked notebook has
cleared outputs; executed notebooks and exports remain under ignored `output/`.

On the supplied engineering recording, the notebook loads3,754gaze observations,
277native R/D/F ticks (186AVAILABLE), and3,481direct native pattern-output rows.
Of those output rows,2,495areP13boundaries and858areP12summaries. Word/line
resolutions and these output families are shown separately; these totals are not
counts of independent reading behaviours. The separate popup visit yields an
eight-row decision/offer/consultation/layout timeline.

The checked results are available without rerunning Python in
`output/study-notebook-check/preview.html`; the executed user copy is
`output/study-notebook-check/gaze_study_exploration.executed.ipynb`. Source paths
and export locations are in `output/study-notebook-check/checks.json`.

## Aid counts and alignment with reading

The recordings already retain aid decisions and presentation/interaction events
with scoped action IDs, reasons/rules, source elapsed timestamps and reading
session links. Counts are derived from these events; there is no single native
aggregate that means "number of helpful interventions."

| Question | Recorded source | Counting distinction |
| --- | --- | --- |
| When did the policy choose an action? | `aid_decision`, `decision_status=CHOSEN` | A new action may replace/update an already-present aid |
| What was suppressed, cleared or expired? | Other native decision statuses and reasons | Keep these separate from chosen actions |
| When was there evidence of layout/drawing? | `aid_runtime_render` / `aid_presentation` | These are two views of callbacks; do not sum them as separate triggers |
| When was a lookup offered or accepted? | `aid_runtime_definition_offer`, event `shown` / `accepted` | Offer, acceptance and panel are different stages |
| When was a definition being consulted? | `aid_runtime_definition_consultation` | Start/end, pause exclusions and dismissal reasons are retained |
| How long was a presentation observed? | Presentation ID, first/last draw proxies, end/censor state | Proxy intervals do not establish exact human-visible exposure |

Use the canonical `aid_decision` family for policy-action counts rather than
adding its separate runtime trace copy. Scope IDs by package/session and retain
generation/anchor identity where available. The current notebook's Section7
groups native decision records and shows the lookup timeline. It does **not yet**
produce deduced intervention-episode counts or a before/after correlation table.

For example, the actual later popup attempt has1chosenWORD_DEFINITION action,
1offer shown and1acceptance. The earlier performance visit has1,068CURRENT_LINE
and188FOCUS_REGION chosen actions; these are policy action selections including
updates/replacements, not1,256separate on-screen help episodes. Continuous line
guides and changing focus strength need a declared episode/duration rule before
being reported as intervention counts. Keep the original records intact while
deriving such a table.

For temporal alignment, match the same package/session/clock domain, use the
actual aid decision or render-proxy timestamp, and compare with gaze capture
times and each R/D/F window's recorded start/end. A10srolling window can straddle
an aid onset; its output timestamp alone does not make it a wholly post-aid
measurement. Aids respond to the gaze stream, so an association may reflect the
reason an aid triggered; that association alone does not establish benefit.

## Interpretation rules

- Capture, delivery, recorded, UTC-anchor and separate usage-log clocks are
  different fields. The plots use stated elapsed-time differences; they do not
  silently combine clock domains.
- Rates use actual recorded-bin spans, including startup and exclusions. The
  last partial bin has its actual duration. Coordinate output rate is not
  independently measured camera/screen FPS.
- Context eligibility is not gaze accuracy. Position scatter is not a validated
  word fixation or a reading scanpath, and out-of-screen points are retained.
- R/D/F's field `fixation_rate_per_minute` currently describes native line stays.
  The overlapping windows are not independent experimental observations.
- Direct pattern output rows are distinguished from revisions and other
  consumers. P12 includes residence/progress summaries and P13 includes boundaries.
  Word and line resolutions are separate. Repeated IDs must be scoped by session,
  producer, generation and resolution before event-level interpretation.
- A CHOSEN action, an offer, acceptance and a panel/layout proxy are different
  stages. `verified_visibility=false` does not demonstrate successful dictionary
  content, perception or benefit.
- Coordinate MD remains uncomputed without a compatible reference; unavailable
  is never replaced by zero. Completed shared reading baselines can now be fitted offline from their declared source link.
- These engineering visits do not establish reading benefit. Participant/article
  outcomes and planned comparisons still need a study design.

Loading retains the existing512MiB decoded-package import budget. Gzip's small
disk footprint does not imply equally small RAM use; select one recording at a
time. This notebook does not silently truncate long recordings.

## Verification and sources

The adapter has focused synthetic checks in `tools/gaze/test_study_notebook.py`.
The notebook is validated and executed using NBClient against the actual two
recordings; evidence is under `output/study-notebook-check/`. A separate executed
copy and figures are retained there or in the notebook's generated output folder.
No source recording is modified and no new phone capture is requested.

Technical APIs follow the primary [NBClient execution documentation](https://nbclient.readthedocs.io/en/latest/client.html)
and [Jupyter notebook format](https://nbformat.readthedocs.io/en/latest/format_description.html).
Exploratory-analysis framing used the statistical-analysis skill from Scientific
Agent Skills: Kassis, T., Agarwal, V., He, Y., Patel, D., & Brueckner, A. M. (2026),
[*Scientific Agent Skills: A Library of Procedural Knowledge for Research Agents*](https://doi.org/10.48550/arXiv.2609.00065).
This procedural citation does not validate NewsMead's gaze measurements.


## Shared reading baseline and coordinate MD

The coordinate section resolves only the `coordinate_reference` recorded at
reading startup. A sibling source package in the same parent directory or a
source session in the same export ZIP is strictly loaded and checked before
fitting. For a separately copied baseline, set `COORDINATE_REFERENCE_SOURCE`
(and `COORDINATE_REFERENCE_SESSION_ID` when needed). An explicit legacy
`coordinate_reference` session is also supported. No latest/closest baseline is
selected and an old `rdf_baseline` never becomes a coordinate reference.

The source must declare `shared_reading_baseline_v1`, the
`article_reading_surface_v1` contract and a completed `shared_baseline_result`.
The declared package/session, attempt, participant, installation, calibration,
clock and source bounds must match and precede evaluation. R/D/F fit failure
does not itself prevent coordinate fitting. Core context compatibility, recorder
loss, raw/selected separation and numerical/coverage gates remain unchanged.
The provider's recorded 650ms acquisition gate does not change the coordinate
core's 500ms analysis gate. Reference bounds may be overridden only as a paired
explicit subset; the original recorded link is still checked against the full
source period.

The notebook displays point MD, MD2 and statuses, exact native-window summaries,
separate R/D/F and coordinate availability, and fit/config/source provenance.
It exports `coordinate_points.csv`, `coordinate_native_windows.csv` and
`coordinate-provenance.json`. Missing sources or incompatible links stay explicit
and distances remain null; a valid center point stays zero with AVAILABLE.
Tracked notebook outputs remain cleared. Synthetic regression checks establish
software behavior only, not tracker accuracy or participant validity.
