# MGazeNet stationary pilot results — 2026-09-08 to 2026-09-09

Status: prospective descriptive pilot in progress. Both predeclared fresh
SM-G991B sessions are complete and retained; the two A56 sessions have not run.
No accuracy gate or promotion decision is assigned. Read after
`gaze-mgazenet-stationary-v2.md`.

## Session 1 declared conditions

The participant reported no glasses, a viewing distance around 30 cm with
ordinary somewhat-closer/somewhat-farther variation, a lighted room, and medium
screen brightness described as neither too bright nor too dark. The participant
later disclosed being very fatigued during both sessions, without good sleep for
approximately two weeks and without expecting that to resolve soon. This is a
self-reported condition, not a measured sleep or clinical assessment. Natural
small distance variation was not treated as a protocol failure or estimated
from face scale.

Fatigue could affect target fixation, blinks, eye movement, posture and task
compliance, but this harness cannot quantify those pathways or subtract them
from tracker error. Both runs are therefore fatigue-context feasibility evidence,
not clean rested-condition estimator benchmarks. The disclosure does not justify
discarding unfavorable samples or retroactively correcting either run.

## SM-G991B session 1 — forward then reverse

- Run label: `g991b_s1_forward_reverse`
- Session ID: `1788837575199_9f02f7ae-9d34-4ac2-8f7b-6beedde25cf3`
- Outcome: complete
- Report SHA-256: `d21aac066af1c3af0a4e88a790b7a9102ec09062590cd2e0b73c90eef956f39f`
- Offline summary SHA-256: `f39db85fdc8e15ff1729523c5d5acd025eeea4ed5047d97125df3ee7096afb2e`

The integrity-checked numeric record and derived summary are retained under
`diagnostics-local/2026-09-08/mgazenet-stationary-v2/sm-g991b/session-1-forward-reverse/`.
The scorer verified the v2 manifest, pipeline binding, exact forward/reverse
order, all twenty block identities/timings, ten repeated locations, fixed
50/100/200/500 ms sensitivity ages, and absence of fit/test coordinate overlap.

The 1080×2400 screen produced viewport `[33.75,383.75,1046.25,2153.75]`
and 118 px line height. The pinned 640×480/rotation-270 camera path produced
upright 480×640 input with the GPU localizer and four-thread default-precision
MNN estimator. APK SHA-256 was
`8ac48a8e51d3c9e3939e5c146b95c0ae06b1426fc3b2b65325b95adfb574a611`.

Calibration accepted exactly 45 rows at each of thirteen fit targets (585
rows), plus 45 practice rows. It recorded no rejected calibration result. The
fit digest is
`a15723517358482122799ba4a66b56b1fbb51ad8375cf544c190c60106b09b12`.
This complete calibration is not evidence that every fixation was compliant.

### Session and sweep summaries

All 20 planned blocks and all 10 locations contributed. Across the fixed 50,000
ms of measurement time, 375 results were received and all 375 contained an
admitted coordinate; there were zero explicit nulls and zero off-text
coordinates. Thirty-five results arrived after their capture window ended and
remain in the spatial summaries and timing record.

| Scope | Coordinates | mean dx px | mean dy px | 2-D median / P95 / max px | vertical median / P95 / max lines | exact line | within one line | age median / P95 / max ms |
| --- | ---: | ---: | ---: | --- | --- | ---: | ---: | --- |
| Whole session | 375 | +44.4 | -75.3 | 108.8 / 232.2 / 979.2 | 0.76 / 1.82 / 8.30 | 31.7% | 85.3% | 219.8 / 281.1 / 357.1 |
| Sweep 1, forward | 195 | +40.0 | -78.3 | 106.8 / 220.7 / 641.5 | 0.76 / 1.72 / 2.13 | 29.7% | 87.2% | 214.4 / 275.4 / 303.7 |
| Sweep 2, reverse | 180 | +49.2 | -72.1 | 113.0 / 232.4 / 979.2 | 0.77 / 1.88 / 8.30 | 33.9% | 83.3% | 225.1 / 287.2 / 357.1 |

Target-balanced results across all ten contributing locations were: mean
location median 2-D error 114.9 px; mean location P95 227.9 px; mean location
maximum 349.3 px; mean location median/P95/maximum vertical error
0.81/1.39/2.38 lines; mean signed X/Y bias +45.2/-74.2 px; mean exact-line
fraction 32.2%; and mean within-one-line fraction 85.3%. An empty location would
have remained missing rather than contributing zero, but there were no empty
locations here.

### Per-target/sweep spatial results

`dx` and `dy` are coordinate-mean signed bias. `SDx`/`SDy` use sample standard
deviation (`n-1`) around that block's coordinate mean. Spatial statistics use
every admitted coordinate and do not change with freshness age.

| Block | n | dx / dy px | 2-D median / P95 / max px | vertical median / P95 / max lines | exact / within-one | SDx / SDy px |
| --- | ---: | --- | --- | --- | --- | --- |
| S1 grid 2 | 18 | +87.4 / +58.4 | 60.9 / 641.5 / 641.5 | 0.38 / 1.10 / 1.10 | 61.1% / 100% | 200.0 / 33.7 |
| S1 grid 8 | 21 | +47.3 / +28.9 | 63.0 / 100.7 / 102.3 | 0.23 / 0.36 / 0.37 | 100% / 100% | 37.8 / 6.3 |
| S1 grid 13 | 20 | +22.8 / -122.1 | 129.9 / 216.0 / 267.1 | 1.03 / 1.77 / 2.13 | 10.0% / 85.0% | 32.3 / 52.8 |
| S1 grid 15 | 21 | +53.0 / -108.1 | 137.5 / 209.5 / 220.7 | 0.81 / 1.72 / 1.77 | 23.8% / 81.0% | 48.5 / 60.3 |
| S1 grid 22 | 20 | +35.4 / -74.9 | 78.7 / 113.8 / 145.1 | 0.59 / 0.94 / 1.02 | 25.0% / 100% | 23.6 / 23.9 |
| S1 grid 24 | 16 | +70.7 / -32.6 | 85.6 / 133.6 / 133.6 | 0.25 / 0.62 / 0.62 | 68.8% / 100% | 33.1 / 26.6 |
| S1 grid 31 | 20 | +47.4 / -152.2 | 167.8 / 216.8 / 245.5 | 1.30 / 1.60 / 1.64 | 0% / 70.0% | 55.7 / 28.7 |
| S1 grid 33 | 20 | +48.0 / -180.0 | 183.5 / 234.6 / 245.1 | 1.54 / 1.90 / 1.93 | 0% / 40.0% | 31.7 / 33.3 |
| S1 grid 38 | 19 | -2.7 / -72.8 | 83.6 / 149.5 / 149.5 | 0.61 / 1.00 / 1.00 | 10.5% / 100% | 56.6 / 18.2 |
| S1 grid 44 | 20 | -1.9 / -108.2 | 114.0 / 173.9 / 195.0 | 0.94 / 1.33 / 1.47 | 5.0% / 100% | 45.8 / 30.6 |
| S2 grid 44 | 20 | +37.8 / -110.9 | 123.3 / 162.3 / 171.8 | 0.94 / 1.12 / 1.34 | 0% / 100% | 44.4 / 20.0 |
| S2 grid 38 | 19 | +81.2 / -106.3 | 142.1 / 204.4 / 204.4 | 0.81 / 1.51 / 1.51 | 10.5% / 94.7% | 42.4 / 33.9 |
| S2 grid 33 | 18 | +58.9 / -132.2 | 214.3 / 403.1 / 403.1 | 1.72 / 3.10 / 3.10 | 5.6% / 22.2% | 64.3 / 171.0 |
| S2 grid 31 | 16 | +83.9 / -202.0 | 223.0 / 259.6 / 259.6 | 1.72 / 2.03 / 2.03 | 0% / 18.8% | 30.1 / 23.9 |
| S2 grid 24 | 19 | +97.6 / -103.7 | 153.8 / 177.4 / 177.4 | 0.87 / 1.29 / 1.29 | 0% / 100% | 33.9 / 21.3 |
| S2 grid 22 | 18 | -30.3 / -67.4 | 70.2 / 130.9 / 130.9 | 0.54 / 1.10 / 1.10 | 38.9% / 100% | 22.7 / 30.8 |
| S2 grid 15 | 18 | +51.1 / -64.0 | 97.8 / 181.3 / 181.3 | 0.63 / 1.50 / 1.50 | 44.4% / 94.4% | 38.8 / 71.6 |
| S2 grid 13 | 19 | +60.4 / -47.9 | 85.0 / 151.6 / 151.6 | 0.34 / 1.21 / 1.21 | 63.2% / 100% | 27.4 / 47.8 |
| S2 grid 8 | 17 | -0.6 / +80.2 | 35.0 / 979.2 / 979.2 | 0.21 / 8.30 / 8.30 | 94.1% / 94.1% | 29.7 / 231.7 |
| S2 grid 2 | 16 | +49.6 / +46.8 | 64.5 / 113.7 / 113.7 | 0.37 / 0.57 / 0.57 | 93.8% / 100% | 32.1 / 9.7 |

The extreme tails are retained. Sweep-1 grid 2 included two >400 px 2-D
samples 615-790 ms into its measurement window. Sweep-2 grid 33 included one
403 px sample, and sweep-2 grid 8 included one 979 px/8.30-line sample about
1,165 ms into its window. Their capture times are inside the stable measurement
windows, so the scorer does not discard them. They may mix tracker error with
participant movement/compliance; this session cannot separate those causes.

### Same-location bias change

Values are sweep 2 minus sweep 1 coordinate-mean signed bias.

| Grid | ΔX px | ΔY px |
| ---: | ---: | ---: |
| 2 | -37.8 | -11.6 |
| 8 | -47.9 | +51.3 |
| 13 | +37.5 | +74.1 |
| 15 | -1.9 | +44.1 |
| 22 | -65.7 | +7.5 |
| 24 | +26.9 | -71.2 |
| 31 | +36.5 | -49.8 |
| 33 | +11.0 | +47.8 |
| 38 | +83.9 | -33.5 |
| 44 | +39.7 | -2.6 |

These changes vary in sign and magnitude by location. They are observed
within-session bias changes, not a causal drift estimate.

### Fixed freshness sensitivity

| Maximum capture age | coordinate availability | exact-line availability | within-one-line availability |
| ---: | ---: | ---: | ---: |
| 50 ms | 0% | 0% | 0% |
| 100 ms | 0% | 0% | 0% |
| 200 ms | 0.31% | 0.03% | 0.18% |
| 500 ms | 88.63% | 28.21% | 76.42% |

These are target-balanced fractions of planned measurement time, not sample
accuracy and not acceptance thresholds. Median output age was 219.8 ms, so the
50 and 100 ms columns necessarily have no available coordinate time. Even the
200 ms column has almost none. At 500 ms, the worst within-block unavailable
interval was 419.8 ms. The full table is retained; the 500 ms column is not
selected as a favorable replacement threshold.

Across the whole run, CameraX delivered 3,877 analyzer arrivals and the
single-owner analyzer observed 2,127 busy drops (54.9%). CameraX-undelivered
frames are not observable. No resource-close error occurred. These counters
span practice, calibration, settling and validation, so they are operational
context rather than a test-window sample denominator.

## SM-G991B session 2 — reverse then forward

- Run label: `g991b_s2_reverse_forward`
- Session ID: `1788888496569_3a72ea87-d398-48b8-b6cb-6012dcbae791`
- Outcome: complete
- Report SHA-256: `449f98623549c27479f60d82959468efc89a8f515a696a8d5764bdd13184169a`
- Offline summary SHA-256: `523988b9dbeab891bbf10da6889fad769d7648c514868772384685b8765fd565`

The integrity-checked record and summary are retained under
`diagnostics-local/2026-09-09/mgazenet-stationary-v2/sm-g991b/session-2-reverse-forward/`.
The record independently verifies `reverse_then_forward`, all twenty blocks,
the same v2 pipeline and layout contract, and a fresh 585-row calibration. Its
fit digest,
`a09923f85f3ecc0e78b642709b05b93baf7654e753e483ddc7fd44a14cb52580`,
differs from session 1. All thirteen fit targets and the practice target accepted
45 rows without an eligibility rejection.

The participant retrospectively confirmed the same no-glasses, approximate
distance, room lighting and medium-brightness conditions for session 2. It was
nighttime and the room was slightly dimmer, though still lighted and not reported
as a large change. The same severe-fatigue disclosure applies to this run.

### Session and sweep summaries

All 20 blocks and 10 locations contributed. Across 50,000 ms of measurement,
349 results were received and all had admitted coordinates. Five coordinates
were outside every text band; they remain in spatial error but are not counted
as correct line assignments. There were no explicit null results. Thirty-three
outputs arrived after their capture window ended.

| Scope | Coordinates | mean dx px | mean dy px | 2-D median / P95 / max px | vertical median / P95 / max lines | exact line | within one line | age median / P95 / max ms |
| --- | ---: | ---: | ---: | --- | --- | ---: | ---: | --- |
| Whole session | 349 | -59.5 | +101.1 | 154.1 / 276.5 / 1443.7 | 0.87 / 1.90 / 12.12 | 33.5% | 83.1% | 237.9 / 307.0 / 332.8 |
| Sweep 1, reverse | 180 | -6.8 | +80.1 | 125.8 / 229.1 / 304.0 | 0.76 / 1.69 / 2.18 | 37.2% | 86.7% | 228.3 / 270.5 / 322.3 |
| Sweep 2, forward | 169 | -115.6 | +123.4 | 183.2 / 308.9 / 1443.7 | 1.00 / 2.11 / 12.12 | 29.6% | 79.3% | 245.4 / 311.5 / 332.8 |

Target-balanced results across ten contributing locations were: mean location
median/P95/maximum 2-D error 147.9/331.2/412.7 px; mean location
median/P95/maximum vertical error 0.81/2.18/2.82 lines; mean signed X/Y bias
-59.6/+99.9 px; mean exact-line fraction 33.6%; and mean within-one-line
fraction 83.5%.

### Per-target/sweep spatial results

| Block | n | dx / dy px | 2-D median / P95 / max px | vertical median / P95 / max lines | exact / within-one | SDx / SDy px |
| --- | ---: | --- | --- | --- | --- | --- |
| S1 grid 44 | 18 | -38.5 / -0.2 | 33.7 / 130.5 / 130.5 | 0.08 / 0.25 / 0.25 | 100% / 100% | 34.8 / 15.9 |
| S1 grid 38 | 18 | -52.6 / +13.2 | 53.5 / 124.4 / 124.4 | 0.18 / 0.50 / 0.50 | 94.4% / 100% | 31.4 / 23.4 |
| S1 grid 33 | 18 | +164.9 / +41.0 | 165.4 / 304.0 / 304.0 | 0.40 / 1.38 / 1.38 | 66.7% / 100% | 39.9 / 51.8 |
| S1 grid 31 | 18 | +42.5 / -41.0 | 78.5 / 134.6 / 134.6 | 0.34 / 0.79 / 0.79 | 77.8% / 100% | 55.0 / 25.7 |
| S1 grid 24 | 17 | +84.1 / +100.9 | 130.2 / 210.6 / 210.6 | 0.86 / 1.17 / 1.17 | 0% / 100% | 52.9 / 19.0 |
| S1 grid 22 | 18 | +1.0 / +103.6 | 111.6 / 171.4 / 171.4 | 0.78 / 1.43 / 1.43 | 0% / 100% | 47.6 / 35.4 |
| S1 grid 15 | 18 | -22.1 / +107.8 | 117.6 / 186.2 / 186.2 | 0.91 / 1.57 / 1.57 | 11.1% / 94.4% | 40.5 / 33.1 |
| S1 grid 13 | 18 | -53.4 / +128.9 | 147.5 / 230.7 / 230.7 | 1.06 / 1.77 / 1.77 | 22.2% / 72.2% | 47.8 / 60.8 |
| S1 grid 8 | 19 | -104.8 / +185.0 | 205.9 / 281.3 / 281.3 | 1.53 / 1.95 / 1.95 | 0% / 31.6% | 25.2 / 22.9 |
| S1 grid 2 | 18 | -78.9 / +157.0 | 174.9 / 267.5 / 267.5 | 1.28 / 2.18 / 2.18 | 0% / 72.2% | 16.8 / 48.1 |
| S2 grid 2 | 18 | -96.6 / +153.8 | 181.8 / 244.9 / 244.9 | 1.31 / 1.85 / 1.85 | 0% / 77.8% | 15.1 / 30.7 |
| S2 grid 8 | 18 | -17.9 / +344.2 | 195.3 / 1443.7 / 1443.7 | 1.63 / 12.12 / 12.12 | 0% / 38.9% | 73.7 / 362.8 |
| S2 grid 13 | 17 | -90.4 / +74.5 | 133.2 / 164.6 / 164.6 | 0.49 / 1.20 / 1.20 | 52.9% / 100% | 34.6 / 43.9 |
| S2 grid 15 | 18 | -33.2 / +176.9 | 172.3 / 297.0 / 297.0 | 1.44 / 2.43 / 2.43 | 0% / 55.6% | 58.1 / 48.5 |
| S2 grid 22 | 15 | -164.3 / +135.9 | 200.9 / 317.8 / 317.8 | 1.08 / 2.00 / 2.00 | 0% / 86.7% | 45.3 / 46.9 |
| S2 grid 24 | 16 | -197.6 / +165.9 | 265.2 / 362.8 / 362.8 | 1.35 / 2.19 / 2.19 | 0% / 75.0% | 65.9 / 40.2 |
| S2 grid 31 | 17 | -110.9 / +85.4 | 143.2 / 377.0 / 377.0 | 0.61 / 2.63 / 2.63 | 41.2% / 88.2% | 71.2 / 81.9 |
| S2 grid 33 | 17 | -137.4 / +79.7 | 172.9 / 269.4 / 269.4 | 0.75 / 1.40 / 1.40 | 35.3% / 100% | 69.5 / 40.8 |
| S2 grid 38 | 16 | -107.1 / -1.8 | 136.6 / 182.1 / 182.1 | 0.26 / 1.20 / 1.20 | 68.8% / 75.0% | 51.0 / 46.2 |
| S2 grid 44 | 17 | -222.6 / -1.8 | 226.7 / 344.6 / 344.6 | 0.12 / 0.32 / 0.32 | 100% / 100% | 42.2 / 19.4 |

Sweep-2 grid 8 contains three >400 px samples 280-547 ms into its stable
measurement window; the largest is 1,443.7 px/12.12 lines. All have finite
coordinates and capture/output clocks and are retained. Five session coordinates
are off the rendered text bands. As in session 1, participant movement/compliance
and tracker error cannot be separated post hoc.

### Same-location bias change

Values are sweep 2 minus sweep 1 coordinate-mean signed bias within session 2.

| Grid | ΔX px | ΔY px |
| ---: | ---: | ---: |
| 2 | -17.8 | -3.1 |
| 8 | +86.9 | +159.2 |
| 13 | -36.9 | -54.4 |
| 15 | -11.1 | +69.1 |
| 22 | -165.3 | +32.2 |
| 24 | -281.6 | +65.1 |
| 31 | -153.4 | +126.4 |
| 33 | -302.3 | +38.7 |
| 38 | -54.5 | -15.0 |
| 44 | -184.1 | -1.6 |

### Fixed freshness sensitivity

| Maximum capture age | coordinate availability | exact-line availability | within-one-line availability |
| ---: | ---: | ---: | ---: |
| 50 ms | 0% | 0% | 0% |
| 100 ms | 0% | 0% | 0% |
| 200 ms | 0.0005% | 0% | 0.0005% |
| 500 ms | 87.45% | 29.09% | 72.68% |

The worst 500 ms within-block unavailable interval was 392.2 ms. CameraX
delivered 3,495 analyzer arrivals and 1,834 were observed busy drops (52.5%).
No resource-close error occurred.

## Between-session comparison

| Metric | Session 1 F→R | Session 2 R→F |
| --- | ---: | ---: |
| Coordinates / off-text / null | 375 / 0 / 0 | 349 / 5 / 0 |
| Mean signed X bias | +44.4 px | -59.5 px |
| Mean signed Y bias | -75.3 px | +101.1 px |
| 2-D median / P95 / max | 108.8 / 232.2 / 979.2 px | 154.1 / 276.5 / 1443.7 px |
| Vertical median / P95 / max | 0.76 / 1.82 / 8.30 lines | 0.87 / 1.90 / 12.12 lines |
| Exact line | 31.7% | 33.5% |
| Within one line | 85.3% | 83.1% |
| Output-age median / P95 | 219.8 / 281.1 ms | 237.9 / 307.0 ms |
| 500 ms coordinate availability | 88.63% | 87.45% |
| 500 ms exact-line availability | 28.21% | 29.09% |
| 500 ms within-one-line availability | 76.42% | 72.68% |
| Analyzer busy-drop fraction | 54.9% | 52.5% |

The fresh calibration and reversed order did not reproduce a stable signed bias:
both mean axes changed sign, while exact-line performance remained close to one
third and within-one-line remained near 83-85%. Session 2 worsened the 2-D
median/tail and output age. These two sessions therefore show material
calibration/session variability; pooling their frames would hide that result.

### Relation to the historical tracker evidence

The 0.76 and 0.87-line session medians are within the better range previously
recorded for other NewsMead gaze configurations. The retained eye-local work
reported 0.62/2.07 and 0.96/1.51-line live median/maximum pairs (E-045); the
stronger E-054 A56 reading run reported 1/2-line median/P95, 36.0% exact-line and
85.4% within-one-line; and the stronger SM compact-candidate replication reported
0.83/2.09-line held-out median/maximum (E-071). In that limited sense, the
MGazeNet typical vertical errors are encouraging and broadly comparable.

They are not a matched superiority result. The MGazeNet task uses stationary red
points centered inside line bands, while E-054 used reading behavior and the
historical target protocols, sample aggregation and runtime paths differ. The
MGazeNet sessions also retain 8.30/12.12-line sample maxima, approximately
31.7/33.5% exact-line assignment, opposite session-level signed bias and weak
sub-200 ms availability. Therefore neither the median nor the historical range
is enough to label the full signal accurate for reading events.

## Interpretation and next boundary

The paired SM-G991B observations establish neither reliable reading events nor
a pass. Within-one-line results do not erase roughly one-third exact-line
accuracy, multi-line P95/max tails, weak sub-200 ms availability, regional
variation, or the reversal of signed bias between fresh calibrations. No filter,
correction, threshold or target was tuned after either run, and neither session
will be repeated to obtain a favorable result.

The predeclared next observations remain two fresh A56 sessions, first forward
then reverse and then reverse then forward after rest/repositioning. They are
paused: continuing immediately while the participant reports severe ongoing
fatigue would add an uncontrolled validity limitation and may add discomfort.
Do not solicit or launch another personal run merely to complete the table.
Camera use and personal calibration on that phone require a separate explicit
authorization and refreshed condition/comfort confirmation. Keep the SM-G991B
sessions separate in all later tables. CAMERA was revoked and the isolated app
was force-stopped after each preserved session.
