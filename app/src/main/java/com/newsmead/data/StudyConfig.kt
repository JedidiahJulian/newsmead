package com.newsmead.data

import com.newsmead.BuildConfig

/**
 * Study behaviour switches. The account-free build still permits live news;
 * fixed testing articles are available separately without account-backed lists.
 *
 * See docs/progress-notes.md for the full context and the study_articles.json schema.
 */
object StudyConfig {

    /**
     * Skip the login gate and account-backed saved/history/telemetry paths.
     * Configured by accountFreeStudyBuild in app/build.gradle.kts so the manifest
     * disables collection before SDK startup as well.
     */
    const val BYPASS_LOGIN = BuildConfig.ACCOUNT_FREE_STUDY

    /**
     * Serve article content from a bundled local asset instead of NewsData.io.
     * When true, DataHelper.loadArticleData()
     * reads from [ARTICLES_ASSET] and the app runs fully offline.
     */
    const val OFFLINE_MODE = false

    /** Name of the bundled article dataset in app/src/main/assets/. */
    const val ARTICLES_ASSET = "study_articles.json"

    /**
     * Optional normal-article lock. Live scanpath geometry is rebuilt after a
     * reader resizes text; dedicated validation/pilot views set their own size.
     */
    const val LOCK_ARTICLE_FONT_SIZE = false

    /**
     * Controlled-view/reference body size. Applied in **dp** (not sp) so it ignores the OS
     * accessibility font-scale setting - the on-screen pixel size, and therefore
     * the line boxes, stay fixed on a given device. 22dp == 22sp at 100% system
     * font. (App's original default was 20sp; ts_body_large.)
     */
    const val ARTICLE_FONT_SIZE_DP = 22f

    // ---- Stage 4: gaze AOI mapping (article reading screen)  ----

    /**
     * Attach the debug gaze layer to the article screen: the line-AOI mapper
     * (gaze-y -> text line index) plus the gaze-dot overlay.
     */
    const val GAZE_ENABLED = true

    /**
     * Validation mode: a finger touch on the article substitutes for the gaze
     * coordinate, so line mapping can be verified on-device without the tracker
     * (build-spec "validate the plumbing before trusting the gaze"). Set this
     * to false only after the local MediaPipe/iris raw source is wired.
     */
    const val GAZE_TOUCH_VALIDATION = false

    /** Independent scanpath aid prototype in the article; no change to frozen validation activities. */
    const val SCANPATH_READING_AIDS = true

    // ---- Adaptive graded visual scaffolding (manuscript Section 4.3.5) ----

    enum class ScaffoldMode {
        OFF,
        ADAPTIVE,
        FORCE_WORD,
        FORCE_LINE,
        FORCE_FOCUS,
        FORCE_REENTRY,
        DEMO_CYCLE,
    }

    /**
     * ADAPTIVE is the study condition. Forced modes are validation aids that
     * make one renderer visible without waiting for an instability trigger.
     * DEMO_CYCLE walks every level in turn for recording; it is not a study mode.
     */
    val SCAFFOLD_MODE = ScaffoldMode.DEMO_CYCLE

    /** How long DEMO_CYCLE holds each level before moving to the next one. */
    const val SCAFFOLD_DEMO_LEVEL_DURATION_MS = 12_000L

    /** Draw the active level's name on screen during DEMO_CYCLE recordings. */
    const val SCAFFOLD_DEMO_CAPTION = true

    /** Participant article reading hides researcher diagnostics; numerical capture stays active. */
    const val GAZE_DEBUG_VISUALS = false

    /**
     * Operational age limit for reading coordinates. MGazeNet inference can
     * exceed the provider's stricter display default on slower devices; the
     * baseline must use the same bounded reading allowance as the previously
     * verified Mahalanobis path. This is not an accuracy threshold.
     */
    const val READING_MAX_OUTPUT_AGE_MS = 650.0

    /** Recent behavior window used by adaptation; cumulative RSI remains logged. */
    const val SCAFFOLD_WINDOW_MS = 10_000L

    /**
     * Automatic per-article baseline warm-up. No scaffold appears during this
     * interval. Before formal collection this should be replaced or supplied by
     * the standardized baseline-reading phase described in the manuscript.
     */
    const val SCAFFOLD_BASELINE_DURATION_MS = 20_000L

    const val SCAFFOLD_WORD_THRESHOLD_Z = 1.0
    const val SCAFFOLD_LINE_THRESHOLD_Z = 1.5
    const val SCAFFOLD_FOCUS_THRESHOLD_Z = 2.0
    const val SCAFFOLD_REENTRY_THRESHOLD_Z = 2.5
    const val SCAFFOLD_MIN_GAZE_CONFIDENCE = 0.65
    const val SCAFFOLD_ESCALATION_PERSISTENCE_MS = 1_500L
    const val SCAFFOLD_RECOVERY_PERSISTENCE_MS = 3_500L

    /**
     * Blink filtering thresholds for the local MediaPipe gaze source. Openness is
     * the eye-aspect ratio: lower values mean the eyelids are closer together.
     * Hysteresis avoids flicker: enter blink below close, leave above open.
     */
    const val GAZE_BLINK_CLOSE_THRESHOLD = 0.04f
    const val GAZE_BLINK_OPEN_THRESHOLD = 0.055f

    /**
     * Development-only, non-authoritative BlazeFace + 64x64 iris comparison.
     * The current 478-point source remains the only producer of gaze coordinates.
     */
    const val GAZE_HYBRID_SHADOW_ENABLED = false
}
