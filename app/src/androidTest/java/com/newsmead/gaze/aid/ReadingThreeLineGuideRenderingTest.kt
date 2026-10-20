package com.newsmead.gaze.aid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathTextGeometryReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Synthetic native rendering only; no camera, capture files or human validity claim. */
@RunWith(AndroidJUnit4::class)
class ReadingThreeLineGuideRenderingTest {
    private class CountingBackground(backgroundColor: Int) : Drawable() {
        var draws = 0
        private val paint = android.graphics.Paint().apply { color = backgroundColor }
        override fun draw(canvas: Canvas) { draws++; canvas.drawRect(bounds, paint) }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Suppress("DEPRECATION") override fun getOpacity() = PixelFormat.OPAQUE
    }

    @Test fun backgroundInvalidationUsesDrawableCallbackAndCancelsPendingAnimation() {
        val base = CountingBackground(Color.WHITE)
        val wrapper = ReadingLineGuideBackground(base) {}
        var invalidations = 0
        val pending = mutableSetOf<Runnable>()
        wrapper.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) { invalidations++ }
            override fun scheduleDrawable(who: Drawable, what: Runnable, whenMs: Long) { pending.add(what) }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) { pending.remove(what) }
        }
        wrapper.attach()
        repeat(3) { wrapper.requestNextDraw(16) }
        assertEquals(1, pending.size)
        pending.single().run()
        assertEquals(1, invalidations)
        base.invalidateSelf()
        assertEquals(2, invalidations)
        wrapper.release()
        assertTrue(pending.isEmpty())
        assertNull(base.callback)
    }

    @Test fun longBodyReusesTextLayoutAndBackgroundAcrossCapturesAndRestoresThemeAndBody() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.replayRoot)
                val host = FrameLayout(activity)
                val scroll = NestedScrollView(activity)
                val body = TextView(activity).apply {
                    text = (0 until 150).joinToString("\n") { "Row $it: readable article text with enough words to wrap." }
                    setTextColor(Color.BLACK)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f)
                    setPadding(19, 23, 17, 29)
                }
                val base = CountingBackground(Color.WHITE)
                body.background = base
                // Explicit article padding must survive wrapping a background without padding.
                body.setPadding(19, 23, 17, 29)
                scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
                host.addView(scroll, FrameLayout.LayoutParams(-1, -1))
                var now = 500.0
                val overlay = ReadingAidOverlayView(activity, presentationClockMs = { now })
                host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
                val container = root.parent as ViewGroup
                container.addView(host, FrameLayout.LayoutParams(root.width, root.height))
                fun layout() {
                    host.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                    host.layout(0, 0, root.width, root.height)
                }
                layout()
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                val reader = ScanpathTextGeometryReader()
                try {
                    var g = reader.read(body)!!.forEngine("long-body")
                    val text = body.text
                    val textLayout = body.layout
                    overlay.setActions(body, g, 0, listOf(action(g)))
                    val wrapper = body.background as ReadingLineGuideBackground
                    assertSame(base, wrapper.base)
                    assertSame(wrapper, base.callback)
                    host.draw(Canvas(bitmap))
                    val validationCount = overlay.lineGuideLayoutValidationCount
                    assertEquals(1, validationCount)
                    repeat(12) { capture ->
                        now += 1
                        g = reader.read(body)!!.forEngine("capture-$capture")
                        overlay.setActions(body, g, 0, listOf(action(g).copy(id = "capture-$capture")))
                        val before = base.draws
                        host.draw(Canvas(bitmap))
                        assertEquals("One base draw per body draw", before + 1, base.draws)
                        assertSame(text, body.text)
                        assertSame(textLayout, body.layout)
                        assertSame(wrapper, body.background)
                        assertEquals(validationCount, overlay.lineGuideLayoutValidationCount)
                        assertEquals(19, body.paddingLeft)
                        assertEquals(23, body.paddingTop)
                    }
                    // Both scroll frames are exercised while keeping snapshot origins current.
                    scroll.scrollTo(0, 90)
                    body.scrollTo(3, 11)
                    g = reader.read(body)!!.forEngine("scrolled")
                    val receipts = mutableListOf<Boolean>()
                    overlay.onRender = { _, drawn, _, _ -> receipts.add(drawn) }
                    val line = g.lines.indexOfFirst { g.textOriginY + it.bottom > g.visibleRectScreen.top + 25 }
                    overlay.setActions(body, g, 0, emptyList())
                    val baseline = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    host.draw(Canvas(baseline))
                    overlay.setActions(body, g, 0, listOf(action(g, line)))
                    now += 250
                    host.draw(Canvas(bitmap))
                    assertTrue(receipts.any { it })
                    val hostOrigin = IntArray(2); host.getLocationOnScreen(hostOrigin)
                    var changed = 0
                    val bandTop = g.textOriginY + g.lines[(line - 1).coerceAtLeast(0)].top
                    val bandBottom = g.textOriginY + g.lines[(line + 1).coerceAtMost(g.lines.lastIndex)].bottom
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                        if (baseline.getPixel(x, y) != bitmap.getPixel(x, y)) {
                            changed++
                            val screenY = y + hostOrigin[1]
                            val screenX = x + hostOrigin[0]
                            assertTrue("Guide stays in body-coordinate rows after both scrolls",
                                screenY >= bandTop && screenY < bandBottom)
                            assertTrue("Parent clipping bounds the drawable",
                                screenY >= g.visibleRectScreen.top && screenY < g.visibleRectScreen.bottom &&
                                    screenX >= g.visibleRectScreen.left && screenX < g.visibleRectScreen.right)
                        }
                    }
                    assertTrue(changed > 0)
                    baseline.recycle()
                    val theme = CountingBackground(Color.rgb(240, 245, 250))
                    body.background = theme
                    body.setPadding(19, 23, 17, 29)
                    overlay.setActions(body, g, 0, listOf(action(g, line).copy(id = "theme")))
                    assertSame(theme, (body.background as ReadingLineGuideBackground).base)
                    assertNull(base.callback)
                    val replacement = TextView(activity).apply { this.text = body.text }
                    overlay.setActions(replacement, null, 1, emptyList())
                    assertSame(theme, body.background)
                    assertSame(body, theme.callback)
                    assertSame(text, body.text)
                    overlay.setActions(body, g, 0, listOf(action(g, line)))
                    host.removeView(overlay)
                    assertSame(theme, body.background)
                } finally {
                    overlay.setActions(body, null, 0, emptyList())
                    container.removeView(host)
                    bitmap.recycle()
                }
            }
        }
    }

    private fun action(g: ScanpathGeometry, line: Int = 1, opacity: Double = .09,
                       direction: ReadingGuideDirection = ReadingGuideDirection.BALANCED): ReadingAidAction {
        val range = ReadingLineGuideAppearance.rows(line, g.lines.lastIndex, direction)
        val first = g.lines[range.first]
        val last = g.lines[range.last]
        return ReadingAidAction("three-line", ReadingAidType.CURRENT_LINE, "CURRENT_THREE_LINE_GUIDE_GRAY_V1",
            "fixture", ReadingAidAnchor(0, g.snapshotId, line), .25, null, null,
            0.0, 10_000.0, emptyList(), emptyList(), 0.0, 0.0, 0.0,
            "fixture", emptyList(), "Synthetic rendering fixture", true, "fixture",
            guideRange = ReadingGuideRange(first.startUtf16, last.endUtf16, ReadingGuideUnit.READING_BAND),
            guideEvidence = ReadingAidGuideEvidence(line, line, "fixture", false, 1000.0, 0.0, opacity,
                threeLine = ReadingLineGuideAppearance.threeLine(direction, opacity)))
    }

    @Test fun replayPixelsMatchRequestedOpacityForSingleAndAllThreeRows() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.replayRoot)
                val body = activity.findViewById<TextView>(R.id.replayBody)
                val viewport = activity.findViewById<FrameLayout>(R.id.replayViewport)
                var now = 500.0
                val overlay = ReadingAidOverlayView(activity, presentationClockMs = { now })
                viewport.addView(overlay, FrameLayout.LayoutParams(-1, -1))
                body.background = null
                root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                root.layout(root.left, root.top, root.right, root.bottom)
                val g = ScanpathTextGeometryReader().read(body)!!.forEngine("opacity-replay")
                assertTrue(g.lines.size >= 5)
                val origin = IntArray(2); root.getLocationOnScreen(origin)
                val baseline = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                val painted = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                val examples = java.io.File(activity.cacheDir, "guide-directional-band").apply { mkdirs() }
                val samples = org.json.JSONArray()
                var checks = 0
                try {
                    for ((theme, background) in listOf("light" to Color.WHITE,
                        "dark" to Color.rgb(32, 32, 32), "sepia" to Color.rgb(247, 244, 224))) {
                        root.setBackgroundColor(background)
                        body.setTextColor(if (theme == "dark") Color.WHITE else Color.BLACK)
                        fun assertPlateau(line: Int, expectedOpacity: Double, label: String, sampleY: Int? = null) {
                            val row = g.lines[line]
                            val y = sampleY ?: (g.textOriginY + (row.top + row.bottom) / 2 - origin[1]).toInt()
                            assertTrue("$label row must be visible", y in 0 until root.height)
                            val left = (g.textOriginX - origin[0]).roundToInt().coerceAtLeast(0)
                            val right = (g.textOriginX + body.layout.width - origin[0]).toInt().coerceAtMost(root.width)
                            val x = (left until right).firstOrNull { baseline.getPixel(it, y) == background }
                            assertNotNull("$label needs a background pixel outside glyphs", x)
                            val actual = painted.getPixel(x!!, y)
                            val alpha = (255 * expectedOpacity).roundToInt() / 255.0
                            val components: List<Pair<String, (Int) -> Int>> = listOf(
                                "r" to { value -> Color.red(value) }, "g" to { value -> Color.green(value) },
                                "b" to { value -> Color.blue(value) })
                            for ((channel, component) in components) {
                                val expected = (component(background) * (1 - alpha) + 128 * alpha).roundToInt()
                                assertEquals("$theme $label row=$line channel=$channel opacity=$expectedOpacity",
                                    expected.toDouble(), component(actual).toDouble(), 2.0)
                            }
                            samples.put(org.json.JSONObject().put("theme", theme).put("case", label)
                                .put("line", line).put("requested_opacity", expectedOpacity)
                                .put("sample_y", y)
                                .put("background_rgb", background and 0xffffff)
                                .put("drawn_rgb", actual and 0xffffff))
                            checks++
                        }
                        fun render(value: ReadingAidAction, time: Double = 500.0) {
                            overlay.setActions(body, g, 0, emptyList())
                            root.draw(Canvas(baseline))
                            now = time
                            overlay.setActions(body, g, 0, listOf(value))
                            root.draw(Canvas(painted))
                        }
                        fun save(name: String) {
                            java.io.File(examples, "$theme-$name.png").outputStream().use {
                                painted.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        }
                        for (opacity in listOf(.08, .13, .19)) {
                            val single = action(g, opacity = opacity).let { value -> value.copy(
                                id = "single-$opacity", aidFormId = "CURRENT_LINE_HIGHLIGHT_GRAY_V1",
                                guideEvidence = value.guideEvidence!!.copy(threeLine = null)) }
                            render(single)
                            assertPlateau(1, opacity, "single")
                            assertPlateau(0, 0.0, "single upper outside")
                            assertPlateau(2, 0.0, "single lower outside")
                            if (opacity == .08) save("single-8pct")
                            render(single, 75.0)
                            assertPlateau(1, opacity * .5, "single half-entry")
                            render(single, 1900.0)
                            assertPlateau(1, opacity * .5, "single half-exit")
                        }
                        for (direction in ReadingGuideDirection.values()) {
                            val expectedRows = when (direction) {
                                ReadingGuideDirection.DOWN -> listOf(2, 3, 4)
                                ReadingGuideDirection.UP -> listOf(0, 1, 2)
                                ReadingGuideDirection.BALANCED -> listOf(1, 2, 3)
                            }
                            val three = action(g, line = 2, direction = direction).copy(id = "three-$direction")
                            render(three)
                            expectedRows.forEach { assertPlateau(it, .09, "three-$direction uniform") }
                            save("three-${direction.name.lowercase()}-uniform-9pct")
                            if (direction == ReadingGuideDirection.BALANCED) {
                                // Check where the former row fills met, including fractional
                                // boundaries during travel: every interior pixel stays at 9%.
                                fun assertNoSeams(offset: Double, label: String) {
                                    for (boundary in listOf(g.lines[2].top, g.lines[2].bottom)) {
                                        val y = (g.textOriginY + boundary + offset - origin[1]).roundToInt()
                                        for (delta in -1..1) assertPlateau(2, .09, label, y + delta)
                                    }
                                }
                                assertNoSeams(0.0, "stationary interior")
                                overlay.setActions(body, g, 0, listOf(action(g, line = 3)))
                                now += 83.0
                                root.draw(Canvas(painted))
                                val t = 83.0 / 250.0
                                val offset = (g.lines[3].top - g.lines[2].top) * t * t * (3 - 2 * t)
                                assertNoSeams(offset, "moving interior")
                            }
                            render(action(g, line = 2, opacity = .045, direction = direction))
                            expectedRows.forEach { assertPlateau(it, .045, "capped three-$direction uniform") }
                        }
                    }
                    assertEquals(135, checks)
                    java.io.File(examples, "samples.json").writeText(samples.toString(2))
                } finally {
                    overlay.setActions(body, null, 0, emptyList())
                    viewport.removeView(overlay)
                    baseline.recycle(); painted.recycle()
                }
            }
        }
    }

    @Test fun threeRowsPaintBehindGlyphsAndReceiptsRequireActualNonzeroBackgroundDraw() {
        ActivityScenario.launch(ScanpathReplayActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.replayRoot)
                val body = activity.findViewById<TextView>(R.id.replayBody)
                val viewport = activity.findViewById<FrameLayout>(R.id.replayViewport)
                var now = 500.0
                val overlay = ReadingAidOverlayView(activity, presentationClockMs = { now })
                viewport.addView(overlay, FrameLayout.LayoutParams(-1, -1))
                fun layout() {
                    root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                    root.layout(root.left, root.top, root.right, root.bottom)
                }
                layout()
                val g = ScanpathTextGeometryReader().read(body)!!.forEngine("three-render")
                assertTrue(g.lines.size > 3)
                val originalText = body.text.toString()
                val foreground = body.currentTextColor
                val baseline = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                val painted = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                val receipts = mutableListOf<Pair<Boolean, String>>()
                overlay.onRender = { _, drawn, reason, _ -> receipts.add(drawn to reason) }
                try {
                    root.draw(Canvas(baseline))
                    overlay.setActions(body, g, 0, listOf(action(g)))
                    layout()
                    overlay.draw(Canvas(painted))
                    assertFalse("Overlay eligibility cannot stand in for a text draw", receipts.any { it.first })
                    root.draw(Canvas(painted))
                    assertTrue(receipts.any { it.first && it.second == "three_line_text_background_draw" })
                    assertEquals(originalText, body.text.toString())
                    assertEquals(foreground, body.currentTextColor)
                    assertEquals(g.lines, ScanpathTextGeometryReader().read(body)!!.lines)
                    val rootOrigin = IntArray(2); root.getLocationOnScreen(rootOrigin)
                    var glyphs = 0
                    for (line in 0..3) {
                        val row = g.lines[line]
                        val top = (g.textOriginY + row.top - rootOrigin[1]).toInt().coerceAtLeast(0)
                        val bottom = (g.textOriginY + row.bottom - rootOrigin[1]).toInt().coerceAtMost(root.height)
                        var changed = 0
                        val changedScanlines = mutableSetOf<Int>()
                        for (y in top until bottom) for (x in 0 until root.width) {
                            if (baseline.getPixel(x, y) != painted.getPixel(x, y)) {
                                changed++; changedScanlines.add(y)
                            }
                            if (baseline.getPixel(x, y) == foreground) {
                                glyphs++
                                assertEquals("Solid glyphs retain foreground", foreground, painted.getPixel(x, y))
                            }
                        }
                        if (line <= 2) {
                            assertTrue("Each available guide row must be painted", changed > 0)
                            assertTrue("Outer fade must retain almost the full side row",
                                changedScanlines.size >= (bottom - top) * .8)
                        }
                        else assertEquals("Fourth row stays outside the guide", 0, changed)
                    }
                    assertTrue(glyphs > 0)
                    // Half a row of travel has only partial extra boundary rows.
                    overlay.setActions(body, g, 0, listOf(action(g, line = 2).copy(id = "moving-three")))
                    now += 125.0
                    root.draw(Canvas(painted))
                    for (line in 0..3) {
                        val row = g.lines[line]
                        val top = (g.textOriginY + row.top - rootOrigin[1]).toInt().coerceAtLeast(0)
                        val bottom = (g.textOriginY + row.bottom - rootOrigin[1]).toInt().coerceAtMost(root.height)
                        val changedHeight = (top until bottom).count { y -> (0 until root.width).any { x ->
                            baseline.getPixel(x, y) != painted.getPixel(x, y)
                        } }
                        if (line == 0 || line == 3) {
                            assertTrue("Moving outer boundaries cover part of a row", changedHeight in 1 until bottom - top)
                        } else assertTrue(changedHeight >= (bottom - top) * .8)
                    }
                    now += 125.0
                    root.draw(Canvas(painted))
                    fun single(line: Int, opacity: Double = .13): ReadingAidAction {
                        val row = g.lines[line]
                        return action(g, line, opacity).copy(id = "single-$line-$opacity",
                            aidFormId = "CURRENT_LINE_HIGHLIGHT_GRAY_V1",
                            guideRange = ReadingGuideRange(row.startUtf16, row.endUtf16, ReadingGuideUnit.READING_BAND),
                            guideEvidence = action(g, line, opacity).guideEvidence!!.copy(threeLine = null))
                    }
                    fun changedRows(): Set<Int> = g.lines.indices.filter { line ->
                        val row = g.lines[line]
                        val y = (g.textOriginY + (row.top + row.bottom) / 2 - rootOrigin[1]).toInt()
                        y in 0 until root.height && (0 until root.width).any { x ->
                            baseline.getPixel(x, y) != painted.getPixel(x, y)
                        }
                    }.toSet()
                    // Style changes discard the previous three-row width immediately.
                    receipts.clear()
                    overlay.setActions(body, g, 0, listOf(single(1)))
                    layout(); root.draw(Canvas(painted))
                    assertEquals(setOf(1), changedRows())
                    assertTrue(receipts.any { it.first && it.second == "single_line_text_background_draw" })
                    overlay.setActions(body, g, 0, listOf(single(3, .19)))
                    now += 125.0
                    overlay.setActions(body, g, 0, listOf(single(3, .15)))
                    root.draw(Canvas(painted))
                    assertEquals("Midpoint follows the shared eased body-coordinate motion", setOf(2), changedRows())
                    now += 125.0
                    overlay.setActions(body, g, 0, listOf(single(3, .19)))
                    root.draw(Canvas(painted))
                    assertEquals("Opacity refreshes cannot delay position arrival", setOf(3), changedRows())
                    // Reduced reader caps bound actual pixels on the next draw.
                    overlay.setActions(body, g, 0, listOf(single(3, .01)))
                    root.draw(Canvas(painted))
                    for (y in 0 until root.height) for (x in 0 until root.width) {
                        val before = baseline.getPixel(x, y)
                        val after = painted.getPixel(x, y)
                        assertTrue(kotlin.math.abs(android.graphics.Color.red(before) - android.graphics.Color.red(after)) <= 3)
                    }
                    receipts.clear()
                    overlay.setActions(body, g, 0, listOf(action(g).copy(id = "reflow")))
                    val oldTextSize = body.textSize
                    body.setTextSize(TypedValue.COMPLEX_UNIT_PX, oldTextSize + 3f)
                    layout()
                    root.draw(Canvas(painted))
                    assertFalse("Font reflow must withhold the stale snapshot", receipts.any { it.first })
                    body.setTextSize(TypedValue.COMPLEX_UNIT_PX, oldTextSize)
                    layout()
                    receipts.clear()
                    overlay.setActions(body, g, 0, listOf(action(g, opacity = 0.0).copy(id = "zero")))
                    root.draw(Canvas(painted))
                    assertFalse(receipts.any { it.first })
                    assertTrue(baseline.sameAs(painted))
                    // The same snapshot can switch guide styles; remove the full-body span.
                    overlay.setActions(body, g, 0, emptyList())
                    root.draw(Canvas(painted))
                    assertTrue(baseline.sameAs(painted))
                    // Entry/loss use wall presentation time, without any capture mutation.
                    now = 2100.0
                    overlay.setActions(body, g, 0, listOf(action(g).copy(id = "faded")))
                    root.draw(Canvas(painted))
                    assertTrue(baseline.sameAs(painted))
                } finally {
                    overlay.setActions(body, null, 0, emptyList())
                    viewport.removeView(overlay)
                    baseline.recycle(); painted.recycle()
                }
            }
        }
    }
}
