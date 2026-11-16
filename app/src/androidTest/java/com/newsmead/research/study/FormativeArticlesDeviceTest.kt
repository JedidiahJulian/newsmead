package com.newsmead.research.study

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.tabs.TabLayout
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.google.android.material.appbar.AppBarLayout
import com.newsmead.R
import com.newsmead.activities.ArticleActivity
import com.newsmead.activities.MainActivity
import com.newsmead.data.StudyArticleCatalog
import com.newsmead.data.StudyConfig
import com.newsmead.fragments.saved.StudyArticlesFragment
import com.newsmead.gaze.getVisibleRectOnScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real list -> reader navigation. Run only with the app's camera app-op temporarily ignored. */
@RunWith(AndroidJUnit4::class)
class FormativeArticlesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val evidenceRun get() = InstrumentationRegistry.getArguments().getString("evidence_run", "article-images-v3")
    private val output get() = File(context.getExternalFilesDir(null), "formative-articles-2026-11-16/$evidenceRun")

    @Test fun selectedArticlesOpenWithExactBodiesAndScrollWithoutCamera() {
        val ops = context.getSystemService(AppOpsManager::class.java)
        assertEquals("Block the camera app-op before this content-only test",
            AppOpsManager.MODE_IGNORED,
            ops.checkOpNoThrow(AppOpsManager.OPSTR_CAMERA, Process.myUid(), context.packageName))
        assertFalse("Unlock the test phone before this run",
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        main { assertNull("Stop the current study run before UI checks", StudyCollection.run) }
        val articles = context.assets.open(StudyConfig.ARTICLES_ASSET).bufferedReader().use {
            StudyArticleCatalog.parse(it.readText())
        }
        assertEquals(2, articles.size)
        output.mkdirs()
        val results = JSONArray()
        writeResults(results)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.savedFragment).performClick() }
            await {
                scenario.onActivity { activity ->
                    if (!StudyConfig.ACCOUNT_FREE_STUDY) {
                        val tabs = activity.findViewById<TabLayout>(R.id.tlListTabs)
                        if (tabs?.tabCount == 3) tabs.getTabAt(2)?.select()
                    }
                }
                var ready = false
                scenario.onActivity { activity ->
                    val catalog = list(activity)
                    val firstImage = catalog?.findViewHolderForAdapterPosition(0)?.itemView
                        ?.findViewById<ImageView>(R.id.ivArticleImage)
                    ready = catalog?.adapter?.itemCount == articles.size && imageLoaded(firstImage)
                }
                ready
            }
            capture("testing-list")
            for ((index, article) in articles.withIndex()) {
                val imageUri = requireNotNull(article.imageURL)
                scenario.onActivity { list(it)!!.scrollToPosition(index) }
                await {
                    var opened = false
                    scenario.onActivity { activity ->
                        list(activity)?.findViewHolderForAdapterPosition(index)?.let { row ->
                            assertEquals(article.title, row.itemView.findViewById<TextView>(R.id.tvArticleTitle).text.toString())
                            assertEquals(article.source, row.itemView.findViewById<TextView>(R.id.tvSource).text.toString())
                            assertEquals(article.date, row.itemView.findViewById<TextView>(R.id.tvArticleDate).text.toString())
                            if (imageLoaded(row.itemView.findViewById(R.id.ivArticleImage))) {
                                assertTrue(row.itemView.findViewById<View>(R.id.cvArticleCard).performClick())
                                opened = true
                            }
                        }
                    }
                    opened
                }
                var reader: ArticleActivity? = null
                try {
                    await {
                        main {
                            reader = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                                .filterIsInstance<ArticleActivity>().singleOrNull()
                        }
                        reader != null
                    }
                    val activity = checkNotNull(reader)
                    await {
                        var ready = false
                        main {
                            val body = activity.findViewById<TextView>(R.id.tvArticleText)
                            ready = body?.text?.toString() == article.body && body.isShown && body.layout != null &&
                                imageLoaded(activity.findViewById(R.id.ivArticleFullImage))
                        }
                        ready
                    }
                    val result = JSONObject().put("article_id", article.newsId).put("title", article.title)
                    results.put(result)
                    main {
                        val body = activity.findViewById<TextView>(R.id.tvArticleText)
                        val headline = activity.findViewById<TextView>(R.id.tvArticleHeadline)
                        val scroll = activity.findViewById<NestedScrollView>(R.id.nsvArticleText)
                        assertEquals(article.title, headline.text.toString())
                        assertEquals(article.source, activity.findViewById<TextView>(R.id.tvSource).text.toString())
                        assertEquals(article.readTime, activity.findViewById<TextView>(R.id.tvArticleMinRead).text.toString())
                        assertEquals(article.body.length, body.layout.getLineEnd(body.layout.lineCount - 1))
                        assertEquals(article.title.length, headline.layout.getLineEnd(headline.layout.lineCount - 1))
                        assertEquals(0, headline.layout.getEllipsisCount(headline.layout.lineCount - 1))
                        val image = activity.findViewById<ImageView>(R.id.ivArticleFullImage)
                        assertTrue(imageLoaded(image))
                        val expected = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.assets.open(imageUri.removePrefix("file:///android_asset/")).use {
                            BitmapFactory.decodeStream(it, null, expected)
                        }
                        val actual = (image.drawable as BitmapDrawable).bitmap
                        assertEquals(expected.outWidth, actual.width)
                        assertEquals(expected.outHeight, actual.height)
                        result.put("list_thumbnail_loaded", true).put("reader_photo_loaded", true)
                            .put("image_asset_uri", imageUri)
                            .put("image_width_px", actual.width).put("image_height_px", actual.height)
                        assertTrue("The complete article must extend below the initial viewport", scroll.canScrollVertically(1))
                        result.put("body_exact", true).put("body_utf16_length", body.length())
                            .put("body_lines", body.layout.lineCount).put("text_size_px", body.textSize)
                            .put("viewport_height_px", scroll.height).put("headline_untruncated", true)
                        assertNull(StudyCollection.run)
                    }
                    writeResults(results)
                    capture("article-${index + 1}-top")
                    main {
                        // Follow the same nested pre-scroll path as a user's upward gesture.
                        // A plain fullScroll() bypasses the CoordinatorLayout's collapsing header.
                        val scroll = activity.findViewById<NestedScrollView>(R.id.nsvArticleText)
                        val header = activity.findViewById<AppBarLayout>(R.id.articleHeaderAppBar)
                        assertTrue(scroll.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
                        scroll.dispatchNestedPreScroll(0, header.totalScrollRange, IntArray(2), null, ViewCompat.TYPE_TOUCH)
                        scroll.stopNestedScroll(ViewCompat.TYPE_TOUCH)
                    }
                    await {
                        var collapsed = false
                        main { collapsed = !activity.findViewById<View>(R.id.clArticleTopBar).getGlobalVisibleRect(Rect()) }
                        collapsed
                    }
                    main {
                        val body = activity.findViewById<TextView>(R.id.tvArticleText)
                        val lastLine = body.layout.lineCount - 1
                        // The page may continue into links/recommendations after the article.
                        // Ask the real scroll parent to reveal the final text line, not the page end.
                        body.requestRectangleOnScreen(Rect(0,
                            body.totalPaddingTop + body.layout.getLineTop(lastLine), body.width,
                            body.totalPaddingTop + body.layout.getLineBottom(lastLine)), false)
                    }
                    var previousGeometry = ""
                    var stableSince = SystemClock.uptimeMillis()
                    try {
                        await {
                            var visible = false
                            var geometry = ""
                            main {
                                val body = activity.findViewById<TextView>(R.id.tvArticleText)
                                val scroll = activity.findViewById<NestedScrollView>(R.id.nsvArticleText)
                                val viewport = Rect()
                                val footer = Rect()
                                val position = IntArray(2).also(body::getLocationOnScreen)
                                val lastLine = body.layout.lineCount - 1
                                val top = position[1] + body.totalPaddingTop + body.layout.getLineTop(lastLine)
                                val bottom = position[1] + body.totalPaddingTop + body.layout.getLineBottom(lastLine)
                                val hasViewport = scroll.getVisibleRectOnScreen(viewport)
                                val hasFooter = activity.findViewById<View>(R.id.bottomAppBar).getVisibleRectOnScreen(footer)
                                val unobscuredBottom = if (hasFooter) minOf(viewport.bottom, footer.top) else viewport.bottom
                                visible = hasViewport && !scroll.isLayoutRequested && !body.isLayoutRequested &&
                                    top >= viewport.top && bottom <= unobscuredBottom && scroll.scrollY > 0
                                geometry = "${scroll.scrollY}:$top:$bottom:${viewport.top}:$unobscuredBottom"
                                result.put("last_line_top_px", top).put("last_line_bottom_px", bottom)
                                    .put("viewport_top_px", viewport.top).put("unobscured_viewport_bottom_px", unobscuredBottom)
                                    .put("article_end_scroll_y_px", scroll.scrollY)
                                    .put("page_continues_below_article", scroll.canScrollVertically(1))
                            }
                            if (!visible || geometry != previousGeometry) stableSince = SystemClock.uptimeMillis()
                            previousGeometry = geometry
                            visible && SystemClock.uptimeMillis() - stableSince >= 300
                        }
                    } finally { writeResults(results) }
                    main {
                        val body = activity.findViewById<TextView>(R.id.tvArticleText)
                        assertEquals(article.body, body.text.toString())
                        assertNull(StudyCollection.run)
                        result.put("last_line_visible", true)
                    }
                    writeResults(results)
                    capture("article-${index + 1}-bottom")
                } finally {
                    main { reader?.finish() }
                }
                instrumentation.waitForIdleSync()
            }
            main { assertNull(StudyCollection.run) }
        }
        writeResults(results)
    }

    private fun list(activity: MainActivity): RecyclerView? {
        val host = activity.supportFragmentManager.findFragmentById(R.id.mainContainer) as NavHostFragment
        val saved = host.childFragmentManager.primaryNavigationFragment
        return saved?.childFragmentManager?.fragments
            ?.firstOrNull { it is StudyArticlesFragment && it.isResumed }
            ?.view?.findViewById(R.id.rvSavedAll)
    }

    private fun capture(name: String) {
        val bitmap = settledScreen()
        try { File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    private fun imageLoaded(image: ImageView?): Boolean = image != null && image.isShown &&
        image.drawable is BitmapDrawable && DrawableImageViewTarget(image).request?.isComplete == true

    /** Main-thread idleness alone does not wait for WindowManager's opening animation. */
    private fun settledScreen(): Bitmap {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var previous: Bitmap? = null
        var unchangedSince = SystemClock.uptimeMillis()
        try {
            do {
                instrumentation.waitForIdleSync()
                val current = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val unchanged = previous?.sameAs(current) == true
                previous?.recycle()
                previous = current
                if (!unchanged) unchangedSince = SystemClock.uptimeMillis()
                if (unchanged && SystemClock.uptimeMillis() - unchangedSince >= 350) {
                    previous = null
                    return current
                }
                SystemClock.sleep(80)
            } while (SystemClock.uptimeMillis() < deadline)
            throw AssertionError("Screen pixels did not settle before the screenshot")
        } finally { previous?.recycle() }
    }

    private fun writeResults(results: JSONArray) {
        File(output, "render-results.json").writeText(JSONObject()
            .put("model", android.os.Build.MODEL).put("camera_appop_ignored", true)
            .put("study_run_started", false).put("evidence_run", evidenceRun)
            .put("articles", results).toString(2))
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Article navigation/rendering did not become ready within 5 seconds")
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
