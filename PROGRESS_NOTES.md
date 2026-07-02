# NewsMead — Progress Notes

## 2026-07-01 — Bypass login gate for thesis research-instrument use

**Context:** NewsMead is being repurposed as a research instrument for a thesis
study. No real users, no personalization, no account system. The Firebase login
screen was blocking access to the home/article screens, and we don't want to
stand up the backend auth flow just to get past it.

### What the login gate was
- Enforced in `app/src/main/java/com/newsmead/activities/SplashActivity.kt`
  (the `LAUNCHER` activity, see `AndroidManifest.xml`).
- Check: `if (auth.currentUser != null)` — a **Firebase Authentication** check
  (`FirebaseAuth.getInstance().currentUser`). Not a local session token, not a
  `newsmead-api` call. If a Firebase user existed → `MainActivity` (home);
  otherwise → `AccountActivity` sign-up/login.
- `MainActivity` hosts `nav_graph.xml`, whose `startDestination` is already
  `homeFragment`. So the only thing gating the home screen was that one `if`.

### The change (smallest, reversible, non-destructive)
Added a feature flag in `SplashActivity` and short-circuited the auth check:

```kotlin
companion object {
    const val BYPASS_LOGIN = true   // false = restore normal login/sign-up flow
}
...
if (BYPASS_LOGIN || auth.currentUser != null) {
    navigateToMainActivity()
}
```

- All login code (LogInFragment, SignUpFragment, AccountActivity, Firebase) is
  left fully intact for later reference. Flip `BYPASS_LOGIN` to `false` to revert.
- Launch now goes straight to `MainActivity` → `homeFragment`.

### Known secondary consideration (feed content)
- Article/home content comes from a **public REST API** (Volley GETs to
  `newsmead.southeastasia.cloudapp.azure.com`) that does **not** require a
  Firebase token — see `DataHelper.loadArticleData()`. Browsing by
  category/source/search uses the `/articles/?…` endpoint and works with no login.
- BUT the **default home feed** hits `…/recommendations/{uid}`, where
  `uid = FirebaseHelper.getUid()`. With no Firebase user, `getUid()` returns the
  literal string `"null"`, so the request becomes `…/recommendations/null`.
  If the recommender rejects an unknown uid, the **default home feed may be
  empty** (screen still renders; list just comes back empty).
  - Server behavior for `recommendations/null` was not verifiable at edit time
    (the Azure host was unreachable from the dev sandbox).
  - If the feed comes up empty on-device, the smallest fix is to fall back to the
    generic `/articles/` endpoint when `uid == "null"` inside
    `DataHelper.loadArticleData()` (the code already knows how to parse that
    response). Not applied yet — pending confirmation of actual server behavior.
- Firebase-backed features (Saved lists, History, Profile) already guard on
  `uid == "null"` and degrade gracefully to a "Please login" toast — no crash.

---

## 2026-07-02 — Offline local article dataset (backend is gone)

**Context:** The content backend no longer exists — both API hosts
(`newsmead.southeastasia.cloudapp.azure.com` and `…-fil.…`) **no longer resolve
in DNS** (Azure VMs deprovisioned). So bypassing login alone leaves every feed
empty. Per the study plan (Option 1), article content is now served from a
bundled local JSON asset so the app runs **fully offline**.

### Switches
All study-mode flags now live in one place:
`app/src/main/java/com/newsmead/data/StudyConfig.kt`
- `BYPASS_LOGIN = true` — used by `SplashActivity` (replaces the local const
  from the previous entry).
- `OFFLINE_MODE = true` — serve articles from the local asset.
- `ARTICLES_ASSET = "study_articles.json"`.
Flip both to `false` to restore full production behaviour.

### What changed
1. **`DataHelper.loadArticleData()`** — when `OFFLINE_MODE`, delegates to a new
   private `loadLocalArticleData()` that reads `assets/study_articles.json`,
   parses the **same JSON schema the old API returned**, and applies the same
   filters server-side used to (source / category / language / search / pageSize).
   So home feed, search, by-source, by-category, and the recommended list all
   work unchanged against local data.
2. **`FirebaseHelper.isNetworkAvailable()`** — returns `true` immediately when
   `OFFLINE_MODE`. The feed screens only render inside
   `if (isNetworkAvailable(...))` (8 sites), so without this the shimmer would
   spin forever on a device with no network.
3. **`app/src/main/assets/study_articles.json`** — the dataset. Now holds **2
   original dummy study articles** (`article_id` 1 = health/lifestyle,
   2 = drainage/news), both English, `source: "study"`. Source anonymized:
   `"study"` maps to the neutral in-app label **"NewsMead"** (via `sourceNameMap`
   / `reverseSourceNameMap` in `DataHelper`) so nothing looks like a real outlet.
   Add more articles (up to the 4 A–D conditions) by appending to the array.

### JSON schema (dictated by the parser in `DataHelper`)
Top-level object with an `articles` array. Each article:

| field        | type   | notes |
|--------------|--------|-------|
| `source`     | string | raw key: `gmanews`, `inquirer`, `philstar`, `manilabulletin`, `news5`, `abantenews`, or `study`. Maps to the bundled logo + display name; any other value → generic logo + blank name. `study` → display name **"NewsMead"** + generic logo (added for the anonymous thesis articles). |
| `title`      | string | headline |
| `image_url`  | string | hero image. `""` = none. For an offline image, put the file in `assets/study_images/` and use `file:///android_asset/study_images/<file>` (Glide loads it). A normal `http(s)` URL only shows if the device happens to be online. |
| `date`       | string | **must** be `yyyy-MM-dd HH:mm:ss` (falls back to the raw string if unparseable, logged as a warning) |
| `body`       | string | full article text, no length limit |
| `category`   | string | e.g. `news`, `opinion`, `sports`, `technology`, `lifestyle`, `business`, `entertainment` |
| `language`   | string | `English` or `Filipino` (non-English hides the Translate button, which needs the dead API) |
| `read_time`  | string | free text, e.g. `"5 min read"` |
| `url`        | string | original article URL (used by the Share button) |
| `article_id` | int    | unique integer; identity used for de-dup and Latin-square condition assignment |

Latin-square note: the 4 articles map cleanly to conditions A–D via
`article_id`. The feed shows them in array order; per-participant ordering is a
study-runtime concern, not yet wired into the app.

---

## 2026-07-02 — Stage 4 prep: article view confirmed native + font size locked

- **WebView vs native (Stage 4 open question):** RESOLVED — the article body is a
  native `TextView` (`tvArticleText`, set via `.text = article.body`). No WebView
  anywhere (only a boilerplate mention in `proguard-rules.pro`). So line boxes for
  AOI mapping come from the `TextView` `Layout` API, not injected JS.
- **Font size (Stage 4 blocker):** RESOLVED and locked.
  - The article view had **no "large" preset** — just a ± stepper (min 14 / max 60,
    `ArticleFragment.addBottomAppBarListeners`). Default body = **20sp**
    (`ts_body_large`). The stepper is buggy (mixes px/sp: "larger" +~0.7sp/tap,
    "smaller" −2sp/tap) and density-dependent — unusable for fixed AOIs.
  - **Decision: 22sp**, locked. Implemented in `StudyConfig`
    (`LOCK_ARTICLE_FONT_SIZE`, `ARTICLE_FONT_SIZE_DP = 22f`) and applied in
    `ArticleFragment` via `setTextSize(COMPLEX_UNIT_DIP, 22f)` — **dp, not sp**, so
    the OS accessibility font-scale can't change the pixel size. The ± controls
    (`llArticleBottomButtons`) are hidden in study mode → no runtime adjustment.
  - NOTE: body line spacing is `lineSpacingMultiplier 1.6` + `lineSpacingExtra 1sp`
    (from `ts_body_large`) — relevant when computing per-line vertical bands.
  - Color-mode buttons (light/dark/sepia) are left active; they don't change line
    geometry. Flag if you want them locked too for visual consistency.

---

## 2026-07-02 — Stage 4 slice 1: WiFi gaze stack ported into NewsMead

Ported the self-contained GazeFollower-over-WiFi tracker from
gaze-thesis-prototype into **`app/src/main/java/com/newsmead/gaze/`** (verbatim
except `package`): `GazeProvider` (the swappable boundary), `WiFiGazeProvider`,
`GazeStream` (UDP listener, port 5005), `GazeMapper` (affine laptop-px→phone-px),
`OneEuroFilter`, `MedianFilter`, `CalibrationStore` (+`CalibrationSample`).
- Added dep `org.apache.commons:commons-math3:3.6.1` (GazeMapper least-squares).
- Added `<uses-permission android:name="android.permission.INTERNET"/>` for the
  UDP socket (was only present transitively via Firebase/Volley).
- `:app:compileDebugKotlin` → BUILD SUCCESSFUL. Not yet wired to any screen.
- NOT ported (prototype-only, not needed here): the Stage-1/2/3 camera/menu
  activities, `WiFiListenerActivity`, `CalibrationActivity`/`CalibrationView`,
  `GazeDotView`. The calibration *capture* UI is still needed to produce
  `calibration_wifi.csv` before `WiFiGazeProvider` can map real gaze — see slice
  decision below.

## 2026-07-02 — Stage 4 slice 2: article-screen wiring (touch-validation)

Wired the gaze AOI layer into the article reading screen, driven by touch for
validation (no tracker/laptop needed yet). New files in `com.newsmead.gaze`:
- **`LineAoiMapper`** — `lineAt(screenY)` → body line index. Scroll-aware for free:
  uses `tvArticleText.getLocationOnScreen()` (which already moves with scroll), so
  `screenY − textViewTop − totalPaddingTop` indexes the `Layout` directly; returns
  −1 when the point is off the text. Stable because font size is locked (22dp).
- **`GazeOverlayView`** — translucent dot drawn from full-screen px; non-interactive
  (passes touch through), sits on top of the article.
- **`ArticleFragment.setupGazeDebug()`** — gated by `StudyConfig.GAZE_ENABLED`. Adds
  the overlay over `binding.root`, and routes all gaze through one
  `GazeProvider.OnGaze` entry point (so live `WiFiGazeProvider.setOnGaze` drops in
  later with no fragment changes). With `GAZE_TOUCH_VALIDATION`, an
  `nsvArticleText` touch listener feeds `event.rawX/rawY` and returns false so
  scrolling still works.
- Logs `GazeAOI: gaze=(x,y) line=N/total` per event.

**On-device test (James, A56 — no laptop needed):** open a study article, drag a
finger down the text, `adb logcat -s GazeAOI`. The logged `line=N` should match the
line under your finger, and stay correct after scrolling. This is the build-spec
touch-validation gate; passing it isolates any later error to the gaze tracker,
not the integration.

`:app:assembleDebug` → BUILD SUCCESSFUL (with JDK 17).

**On-device (A56):** VALIDATED — `GazeAOI` logs stream and `line=N` is responsive
to touch as the finger moves down the passage (touch-validation gate). Scroll-
awareness confirmation recommended as a final check.

### Stage 4 slice 3 (NEXT) — live gaze
Port the calibration *capture* flow (`CalibrationActivity`/`CalibrationView`) from
the prototype so it writes `calibration_wifi.csv`, then construct
`WiFiGazeProvider(GazeMapper(CalibrationStore.load(...)))` and set it as the
article screen's gaze source (`GAZE_TOUCH_VALIDATION = false`). Needs the laptop
rig + an on-device calibration run to validate.

### Build note
- The project's kapt is **incompatible with JDK 21** (fails with
  `module jdk.compiler does not export com.sun.tools.javac.main`). This is a
  pre-existing environment issue, unrelated to this change.
- Build with **JDK 17** instead, e.g. `C:\Program Files\Java\jdk-17`:
  `JAVA_HOME="C:\Program Files\Java\jdk-17"; ./gradlew :app:compileDebugKotlin`
- Verified: `:app:compileDebugKotlin` → **BUILD SUCCESSFUL** with all changes
  above (login bypass + offline dataset). `study_articles.json` validated as
  well-formed. Full on-device confirmation (home feed renders, article opens)
  still requires an emulator/device.
