# Describing recorded gaze: Mahalanobis and related methods

Research checkpoint: 2026-11-02. This is a bounded primary-source/method review,
not a systematic review, implemented score, or validation study. The owner has
clarified that the new measurement describes estimated gaze itself; it is not
intended to operationalize reading instability.

## Main finding

Mahalanobis distance is a covariance-standardized distance whose meaning depends
on its input and reference. The inspected gaze papers use it for calibration
processing or gaze-zone classification. That establishes relevant methodological
precedent, but does not establish a standard descriptive phone-reading MD outcome.
Related gaze-distribution methods report spatial spread/covariance ellipses and
variability explicitly. We can build a descriptive coordinate distance without
claiming a cognitive or clinical interpretation, provided the reference is stated.

## Bounded evidence table

| Primary source | Actual method/context | Consequence for NewsMead |
| --- | --- | --- |
| Pezzoli et al., ETRA 2026, §§2–6, equations 6–7, [open author PDF](https://re.public.polimi.it/retrieve/220ccfe2-e836-482f-a80e-6cd16e9e98b6/3797246.3806216.pdf), [DOI](https://doi.org/10.1145/3797246.3806216) | Minimum squared MD to class-specific means/covariances of 32-dimensional binocular photosensor measurements; gaze-zone classification. Custom IR photodiode eyewear, 1 kHz, robot tests and 29 adults aged 21–37; EyeLink reference. | Concrete MD-in-gaze example, but its representation, labelled calibration classes and hardware differ from estimated phone x/y. No transfer of classification accuracy or reading validity. |
| Brilhault, Neuenschwander & Rios, 2022 online/2023 issue, [publisher record](https://link.springer.com/article/10.3758/s13428-022-01809-4), [abstract](https://pubmed.ncbi.nlm.nih.gov/35297014/) | Multivariate depth/mode estimation in contaminated calibration point clouds; includes Mahalanobis depth in the methods comparison. Simulated mixtures and Capuchin-monkey calibration. Positions use a 1024×768 pixel screen frame. | Calibration filtering/mode estimation differs from reporting natural-gaze deviation. Publisher preview/notes and indexed method excerpts were accessible; full evaluation details were not. Do not attribute BRIL's reported performance to MD alone. |
| Oh & Lee, JOK 2026, [journal record](https://jok.kiise.or.kr/digital-library/108123), [DOI](https://doi.org/10.5626/JOK.2026.53.9.850) | Accessible abstract describes MD-based training-data outlier removal for webcam gaze mapping. Exact MD vector/reference, population and sampling are not available in the inspected abstract. | A further filtering precedent only. Reported mapping gains are not an isolated MD effect. Insufficient detail to reproduce this as our descriptive score. |
| Friedman, Hanson & Komogortsev, 2021, [primary journal record](https://bop.unibe.ch/JEMR/article/view/7844), DOI 10.16910/jemr.14.3.4 | Examines position-distribution multimodality during fixation with EyeLink 1000. Reports that unimodal-normal spread interpretation often fails in their evaluated data. | Inspect coordinate clouds before treating a single ellipse as a density or probability model. Their fixation-specific result does not quantify multimodality in our phone data. Full journal PDF timed out; use the accessible abstract/method excerpts for this limited conclusion. |
| Asfaw et al., 2020, [Scientific Reports primary article](https://www.nature.com/articles/s41598-020-66196-2) | 55 young healthy participants viewed images/videos under simulated visual-field loss. BCEA described the spread of detected saccade endpoints; significant changes did not provide consistent clinical identification. | A natural-viewing distribution example, but uses derived event endpoints, not our raw-point contract. Neither its endpoint selection nor disease interpretation is adopted. |
| Niehorster et al., 2026, [original methods/resource article](https://link.springer.com/article/10.3758/s13428-026-03039-4), sections on RMS-S2S, STD and BCEA | Defines spread, intersample variability, effective sampling and missingness measures, with supplied code. BCEA is an ellipse-area summary. Raw RMS-S2S over substantial actual movement is not automatically tracker precision. | Report spatial extent separately from temporal variation and accuracy. In ordinary reading, distribution width combines actual viewing behavior and measurement error. Known-location data are needed for accuracy assessment. |
| Dunn et al., 2023 guideline/2024 issue, [consensus guideline](https://pmc.ncbi.nlm.nih.gov/articles/PMC11225961/), DOI 10.3758/s13428-023-02187-1 | Original consensus reporting guidance, rather than an empirical device-validation experiment. Specifies recording/coordinate/processing/quality information for reproducibility. | Keep units, coordinate frame, sampling, processing, missingness and validation procedure explicit. Its existence does not validate our hardware or metric. |

One bounded researcher extraction was reused for the three literal-MD precedents.
Lead checked the open PSOG paper, BRIL publisher material, spatial-distribution
sources and method/reporting resources. No private recordings were opened or sent.
Access limits above are retained rather than filled in from assumptions.

## How MD relates to a covariance ellipse

For a point g=(x,y), reference mean mu and covariance S:

`D_squared = (g-mu)^T S^-1 (g-mu)`

The contour `D_squared = c` is an ellipse with area `pi*c*sqrt(det(S))` for positive
definite S. Thus MD describes a point's standardized radius; an ellipse's area
describes spatial extent. They are related calculations, not interchangeable
outcomes. Choosing a nominal population coverage for the ellipse requires a
distribution model; a Gaussian formula does not guarantee that fraction of a
multimodal gaze cloud will lie inside it. Report observed inclusion separately.

BCEA-style area and axes can be descriptive co-reports without constructing the
previous H/V/movement MD vector. In our pixel frame they have pixel/pixel-squared
units (or explicitly normalized equivalents). Degrees of visual angle require
viewing geometry; do not silently substitute screen fractions or pixels for degrees.

## Critical reference-design result

Do not fit each window's mean/covariance from its own points and report mean
squared MD as a general measure of how dispersed gaze was. With p dimensions,
n samples, unweighted sample covariance (n-1 denominator), full rank and no ridge:

`sum(D_squared_i) = p*(n-1)`

Hence its mean is `p*(n-1)/n`, independently of uniform scale or translation.
This follows from the trace of the covariance-standardized centered cross-product;
it is a mathematical identity, not an empirical gaze finding. With weighted
population covariance fitted and summarized on the same points using the same
weights, the analogous weighted mean is exactly p, provided full rank and no
ridge. Regularization changes these identities but does not make self-normalization
a direct spread measurement.

A four-point synthetic numerical check in two dimensions gave:

| Evaluation cloud | Fixed original reference: mean MD-squared | Its own fitted reference: mean MD-squared |
| --- | ---: | ---: |
| Original | 1.5 | 1.5 |
| Twice the spatial scale | 6.0 | 1.5 |
| Shifted 0.2 screen fractions horizontally | 4.5 | 1.5 |

Receipt: `output/gaze-coordinate-md-research-2026-11-02/sanity.json`. Inputs are
four artificial screen-fraction points, not participant data. This verifies the
reference-design issue, not a production implementation or human validity.

## Recommended interpretation and prototype boundary

The most direct first prototype is **pointwise coordinate deviation relative to
a separately recorded, frozen reference distribution**, with raw coordinates,
mean/covariance and distance all retained. The reference is not a ground-truth
gaze location. It describes one declared viewing/acquisition context.

Keep this distinct from two other questions: within-segment spatial extent
(covariance/ellipse summary), and tracking accuracy (known-location validation).
An unusually distant point may be genuine looking behavior, a changed viewport,
calibration drift or tracking error; MD alone cannot identify the cause. Do not
delete a point merely because its MD is high: that would confound a descriptive
outcome with an automatic cleaning rule.

The next plan should specify reference identity, coordinate frame, weighting,
eligibility, regularization and window summaries before coding, while leaving
the existing R/D/F score and aids unchanged. There is no need to invent a
reading-instability label or a new engineered feature family for this objective.

Method provenance: planning used the installed scientific-critical-thinking and
statistical-analysis skills. Their procedural software citation is Kassis, T.,
Agarwal, V., He, Y., Patel, D., & Brueckner, A. M. (2026),
[Scientific Agent Skills](https://doi.org/10.48550/arXiv.2609.00065).
This acknowledges methodological tooling, not validation of a gaze measure.
