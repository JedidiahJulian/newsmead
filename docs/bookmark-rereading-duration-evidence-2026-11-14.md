# Bookmark persistence: observed rereading durations

Research checkpoint,14November2026. The owner rejects arbitrary5/10/30s deletion deadlines, asks for gradual dimming, and specifies that rereading earlier material must not itself dismiss the saved frontier. This is a bounded primary-source check, not a systematic review or a validated phone policy.

## Findings

**[Cane, Cauchard & Weger2012](https://www.interruptions.net/literature/Cane-QJExpPsychol12.pdf), Experiment2, Table2, p.1408.**28analyzed university participants; mean age19.54; desktop EyeLink1000. After imposed interruptions, fixation time spent revisiting pre-interruption text averaged5.90s(SD2.94) without the location highlight and3.71s(SD2.82) with it. This sums rereading within a defined text region; it is not an upper bound on a natural rereading episode. Supports testing location cues after interruptions, not a5s/10s expiry or a fade curve.

**[Moreno, León, Kaakinen & Hyönä2021](https://journals.copmadrid.org/psed/archivos/1135_755X_psed_27_1_0051.pdf), pp.54–55/Table1; [publisher methods](https://journals.copmadrid.org/psed/art/psed2020a20).**132Spanish-speaking students,20–23; EyeTechVT2,80Hz; six200–250-word expository texts. Look-back duration sums fixations returning to a paragraph after visiting another paragraph. One question-relevant condition averaged4.333s(SD7.041); condition means ranged0.541–4.333s. These are paragraph aggregates, not continuous episodes. No individual ranges or percentiles establish how often episodes exceed10s. Task/instruction dependence argues against treating one aggregate mean as a universal lifetime.

**[Wang et al.2018](https://pmc.ncbi.nlm.nih.gov/articles/PMC6019021/), Table1/Methods; [publication record](https://pubmed.ncbi.nlm.nih.gov/27032427/).**36young and36older Chinese readers; mean ages23.5/71.6; EyeLink1000,1000Hz. Sentence rereading—summed fixations after first pass, including initially skipped words—averaged0.934s(SE0.108) and1.619s(SE0.126), respectively. SE is not participant SD. Single-sentence aggregates are not whole-passage episode lengths or maxima. Age-group association in Chinese lab reading cannot set an English-phone expiry.

**[Jo, Kim & Seo2015, EyeBookmark](https://hcil.snu.ac.kr/cms/uploads/Eye_Bookmark_Assisting_Recovery_from_Interruption_during_Reading_5d09d092a0.pdf), pp.2964–2966/Table1.**Main study:13Korean university participants,20–28; desktop TobiiX60, chin rest. Block/sentence location cues reduced interruption-resumption lag. That outcome ends when reading restarts, not when rereading finishes. Comprehension differences between cue conditions were nonsignificant. Neither gradual dimming nor a general bookmark-expiry duration was tested. It supports a location-cue hypothesis in that task, not a phone fade schedule.

The cited [von der Malsburg & Vasishth2011](https://tmalsburg.github.io/MalsburgVasishth2011.pdf) scanpath study does not supply the episode-duration distribution needed to select an expiry. Do not infer a rereading maximum from a plotted time axis, group mean, mean-plus-SD calculation, resumption lag, or a fixation duration.

## Design interpretation, not an experimental finding

- Keep the saved frontier available while the reader revisits earlier material. Elapsed time alone does not demonstrate completion.
- Treat prominence separately from retention: gradually dim to a still-visible level, retaining the location until supported forward progression passes it or the reader dismisses it. Invalid layout/article or an explicit navigation boundary can still invalidate an anchor.
- A progress-based dimming curve is an alternative to an elapsed-time curve. Neither the curve nor a minimum visible opacity is validated by these studies; select explicitly as an exploratory presentation setting, not by relabelling a mean rereading duration as a deadline.
- Reduced prominence does not establish that a trigger was correct. The unresolved live-versus-Replay activation problem remains separate.

## Actual implementation status

After this evidence review, the owner explicitly authorized implementation. Current ruleV6/formV3 removes timed deletion and same/earlier-line residence dismissal, clears on supported adjacent forward passage beyond the saved row, and uses supported reread-line progress for75%→40% dimming. The40% floor and250ms UI interpolation are presentation choices; no reading-duration estimate or empirical benefit is claimed. Lookaway arming is retained across brief tracking breaks; a stable broad backward visit after supported forward progression can preserve the departure line before full rereading completes. See [implementation and boundaries](bookmark-rereading-and-recovery-2026-11-14.md).

Software/build and installation evidence: `output/bookmark-progress-2026-11-14/`. Device validation uses synthetic observations through the actual motion-enabled article adapter and rendered text, with the camera off. It does not establish natural-reading accuracy or benefit. Both phones now have this ruleV6 build; the earlier ruleV5 build is historical and superseded. The10s proposal was never implemented.
