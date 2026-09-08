# MGazeNet stationary pilot results — 2026-09-08

Status: prospective descriptive pilot in progress. SM-G991B session 1 is
complete and retained; its counterbalanced fresh session 2 has not yet run.
No accuracy gate or promotion decision is assigned. Read after
`gaze-mgazenet-stationary-v2.md`.

## Declared conditions

The participant reported no glasses, a viewing distance around 30 cm with
ordinary somewhat-closer/somewhat-farther variation, a lighted room, and medium
screen brightness described as neither too bright nor too dark. These are
participant descriptions, not instrument measurements. Natural small distance
variation was not treated as a protocol failure or estimated from face scale.

## SM-G991B session 1 — forward then reverse

Run label: `g991b_s1_forward_reverse`  
Session ID: `1788837575199_9f02f7ae-9d34-4ac2-8f7b-6beedde25cf3`  
Outcome: complete  
Report SHA-256: `d21aac066af1c3af0a4e88a790b7a9102ec09062590cd2e0b73c90eef956f39f`  
Offline summary SHA-256: `f39db85fdc8e15ff1729523c5d5acd025eeea4ed5047d97125df3ee7096afb2e`

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

## Interpretation and next boundary

This first session shows complete spatial coverage but substantial negative-Y
bias through much of the layout, weak exact-line assignment, large regional
variation, and output ages incompatible with the three lower sensitivity ages.
The 85.3% within-one-line fraction does not erase 31.7% exact-line accuracy,
the 1.82-line vertical P95, the 8.30-line maximum, or the availability result.
It establishes neither reliable reading events nor a pass.

Do not tune, correct, filter or repeat this session to obtain a favorable result.
The next predeclared observation is a fresh SM-G991B session 2 using reverse then
forward order, after rest and ordinary repositioning, with the same reported
conditions recorded again. That run requires another full calibration and
should be reported separately before any cross-session conclusion. CAMERA was
revoked and the isolated app was force-stopped after session 1 was preserved.
