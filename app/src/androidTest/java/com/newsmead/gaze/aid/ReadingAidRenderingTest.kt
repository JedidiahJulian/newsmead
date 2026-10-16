package com.newsmead.gaze.aid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.TextView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.scanpath.ScanpathTextGeometryReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Idle synthetic view checks only; never creates a provider/camera or recording. */
@RunWith(AndroidJUnit4::class)
class ReadingAidRenderingTest {
    @Test fun v4UsesArticleColorKeepsScrollWindowAndUncoversFreshAnchor() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val width = 800
            val margin = 40
            val root = FrameLayout(context).apply { setBackgroundColor(android.graphics.Color.rgb(35, 55, 75)) }
            val source = TextView(context).apply {
                text = (0..11).joinToString("\n") { "Article row $it" }
                textSize = 20f
                includeFontPadding = false
                setTextColor(android.graphics.Color.WHITE)
            }
            var presentationMs = SystemClock.elapsedRealtimeNanos() / 1e6
            val overlay = ReadingAidOverlayView(context, presentationClockMs = { presentationMs }).apply {
                allowFocusScrollHold = true
            }
            root.addView(source, FrameLayout.LayoutParams(width - 2 * margin,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = margin; rightMargin = margin })
            root.addView(overlay, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val height = source.lineHeight * 9
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val reader = ScanpathTextGeometryReader()
            val initial = reader.read(source)!!.forEngine("g1")
            val focus = action(presentationMs + 5000).copy(
                aidType = ReadingAidType.FOCUS_REGION, aidFormId = "CONTEXT_FOCUS_REGION_V4",
                anchor = ReadingAidAnchor(0, "g1", 4), strength = 0.5,
                magnificationScale = null, contextRadiusLines = 2)
            val held = mutableListOf<String>()
            overlay.onFocusScrollRender = { original, snapshot, clearTop, clearBottom, _ ->
                assertEquals(focus, original)
                assertEquals(initial.textOriginY + initial.lines[2].top, clearTop, 0.0)
                assertEquals(initial.textOriginY + initial.lines[6].bottom, clearBottom, 0.0)
                held.add(snapshot)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                overlay.setActions(source, initial, 0, listOf(focus))
                overlay.draw(Canvas(bitmap))
                val pixel = bitmap.getPixel(0, 0)
                assertEquals(153, android.graphics.Color.alpha(pixel))
                for (x in listOf(0, margin - 1, margin, width / 2, width - margin, width - 1)) {
                    assertEquals("V4 top veil spans viewport including side gutters", 153,
                        android.graphics.Color.alpha(bitmap.getPixel(x, 0)))
                    assertEquals("V4 bottom veil spans viewport including side gutters", 153,
                        android.graphics.Color.alpha(bitmap.getPixel(x, height - 1)))
                    for (line in 2..6) {
                        val row = initial.lines[line]
                        val y = (initial.textOriginY + (row.top + row.bottom) / 2).toInt()
                        assertEquals("V4 keeps five physical rows clear across viewport", 0,
                            android.graphics.Color.alpha(bitmap.getPixel(x, y)))
                    }
                }
                // Premultiplied bitmap roundoff can alter channels by one at partial alpha.
                assertTrue(kotlin.math.abs(35 - android.graphics.Color.red(pixel)) <= 1)
                assertTrue(kotlin.math.abs(55 - android.graphics.Color.green(pixel)) <= 1)
                assertTrue(kotlin.math.abs(75 - android.graphics.Color.blue(pixel)) <= 1)
                val clearY = ((initial.lines[4].top + initial.lines[4].bottom) / 2).toInt()
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(width / 2, clearY)))
                overlay.setActions(source, initial, 0, listOf(focus.copy(strength = 1.0)))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals("Full-strength surroundings retain about 15% contrast", 217,
                    android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(width / 2, clearY)))
                overlay.setActions(source, initial, 0, listOf(focus))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                source.scrollTo(0, source.lineHeight / 2)
                val scrolled = reader.read(source)!!.forEngine("g2")
                overlay.setActions(source, scrolled, 0, emptyList())
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(153, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(width / 2, clearY)))
                assertEquals(listOf("g2"), held)
                overlay.draw(Canvas(bitmap))
                assertEquals("One receipt per held snapshot", listOf("g2"), held)

                // Dark onset changes only painted opacity, retaining the clear rows and width.
                val dark = focus.copy(id = "dark", anchor = ReadingAidAnchor(0, "g2", 4),
                    focusStyle = ReadingFocusStyle.DARK_SURROUNDINGS)
                val darkReceipts = mutableListOf<Boolean>()
                overlay.onRender = { original, drawn, _, _ ->
                    if (original.focusStyle == ReadingFocusStyle.DARK_SURROUNDINGS) darkReceipts.add(drawn)
                }
                overlay.setActions(source, scrolled, 0, listOf(dark))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals("Zero onset is not a successful render", listOf(false), darkReceipts)
                presentationMs += 200
                overlay.setActions(source, scrolled, 0, listOf(dark.copy(id = "dark-refresh")))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(android.graphics.Color.argb(51, 0, 0, 0), bitmap.getPixel(0, 0))
                assertEquals(listOf(false, true), darkReceipts)
                presentationMs += 200
                overlay.setActions(source, scrolled, 0, listOf(dark))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(android.graphics.Color.argb(102, 0, 0, 0), bitmap.getPixel(0, 0))

                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(width / 2, clearY)))

                // Its held scrolling presentation must retain the chosen dark style too.
                source.scrollTo(0, source.scrollY + source.lineHeight)
                val darkScrolled = reader.read(source)!!.forEngine("dark-scroll")
                overlay.onFocusScrollRender = { original, _, _, _, _ -> assertEquals(dark, original) }
                overlay.setActions(source, darkScrolled, 0, emptyList())
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(android.graphics.Color.argb(102, 0, 0, 0), bitmap.getPixel(0, 0))

                val darkFresh = dark.copy(id = "dark-fresh", anchor = ReadingAidAnchor(0, "dark-scroll", 4))
                overlay.setActions(source, darkScrolled, 0, listOf(darkFresh))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals("Fresh gaze takes over a hold without restarting onset",
                    android.graphics.Color.argb(102, 0, 0, 0), bitmap.getPixel(0, 0))

                // Restore the preceding geometry for the independent fresh-anchor assertion.
                source.scrollTo(0, source.scrollY - source.lineHeight)

                val fresh = focus.copy(id = "fresh", anchor = ReadingAidAnchor(0, "g2", 8))
                overlay.setActions(source, scrolled, 0, listOf(fresh))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                val row = scrolled.lines[8]
                for (y in (scrolled.textOriginY + row.top).toInt() until
                    (scrolled.textOriginY + row.bottom).toInt()) {
                    assertEquals("Fresh anchor is clear during transition", 0,
                        android.graphics.Color.alpha(bitmap.getPixel(width / 2, y)))
                }
                overlay.clearFocusScrollHold()
                overlay.setActions(source, scrolled, 0, emptyList())
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))

                // A new presentation after removal starts at zero again.
                overlay.setActions(source, scrolled, 0, listOf(dark))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                presentationMs += 400
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(102, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))

                // An undrawn style change must not turn the previous Fade frame into a Dark hold.
                overlay.setActions(source, scrolled, 0, listOf(focus.copy(anchor = dark.anchor)))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(153, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                overlay.setActions(source, scrolled, 0, listOf(dark))
                source.scrollTo(0, source.scrollY + source.lineHeight)
                val undrawnStyleScroll = reader.read(source)!!.forEngine("undrawn-style-scroll")
                var undrawnHeld = false
                overlay.onFocusScrollRender = { _, _, _, _, _ -> undrawnHeld = true }
                overlay.setActions(source, undrawnStyleScroll, 0, emptyList())
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                assertFalse("No held receipt before a positive Dark frame", undrawnHeld)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun scrollHoldExpiresAfterLastTranslationDespiteSameGeometryTicksAndUndrawnRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var source: TextView
        lateinit var overlay: ReadingAidOverlayView
        lateinit var root: FrameLayout
        lateinit var snapshot: com.newsmead.gaze.scanpath.ScanpathGeometry
        lateinit var bitmap: Bitmap
        var translatedAtMs = 0L
        val held = mutableListOf<String>()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            root = FrameLayout(context)
            source = TextView(context).apply {
                text = (0..11).joinToString("\n") { "Article row $it" }
                textSize = 20f; includeFontPadding = false
            }
            overlay = ReadingAidOverlayView(context).apply { allowFocusScrollHold = true }
            root.addView(source, FrameLayout.LayoutParams(800, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(overlay, FrameLayout.LayoutParams(800, ViewGroup.LayoutParams.MATCH_PARENT))
            root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val height = source.lineHeight * 9
            root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 800, height)
            bitmap = Bitmap.createBitmap(800, height, Bitmap.Config.ARGB_8888)
            val reader = ScanpathTextGeometryReader()
            val first = reader.read(source)!!.forEngine("initial")
            val drawn = action(SystemClock.elapsedRealtimeNanos() / 1e6 + 5000).copy(
                id = "drawn", aidType = ReadingAidType.FOCUS_REGION,
                aidFormId = "CONTEXT_FOCUS_REGION_V4", anchor = ReadingAidAnchor(0, "initial", 4),
                strength = 0.5, magnificationScale = null, contextRadiusLines = 2)
            overlay.onFocusScrollRender = { original, id, _, _, _ ->
                assertEquals("Preserve the last actually drawn source", drawn, original)
                held.add(id)
            }
            overlay.setActions(source, first, 0, listOf(drawn))
            overlay.draw(Canvas(bitmap))
            // Refresh ID/strength before a frame: the still-visible source remains 'drawn'.
            overlay.setActions(source, first, 0, listOf(drawn.copy(id = "undrawn", strength = 1.0)))
            source.scrollTo(0, source.lineHeight)
            snapshot = reader.read(source)!!.forEngine("scroll")
            translatedAtMs = SystemClock.elapsedRealtime()
            overlay.setActions(source, snapshot, 0, emptyList())
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            overlay.draw(Canvas(bitmap))
            assertEquals(153, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(listOf("scroll"), held)
        }
        try {
            // Wait on the instrumentation thread; never block the UI thread.
            SystemClock.sleep((translatedAtMs + 850 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            instrumentation.runOnMainSync {
                snapshot = snapshot.copy(snapshotId = "tick")
                overlay.setActions(source, snapshot, 0, emptyList())
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals("Hold survives briefly after scroll at original strength", 153,
                    android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals(listOf("scroll", "tick"), held)
            }
            SystemClock.sleep((translatedAtMs + 1600 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            instrumentation.runOnMainSync {
                snapshot = snapshot.copy(snapshotId = "late_tick")
                overlay.setActions(source, snapshot, 0, emptyList())
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                overlay.draw(Canvas(bitmap))
                assertEquals("Same-geometry ticks cannot renew the 1500ms hold", 0,
                    android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals("Expired hold emits no receipt", listOf("scroll", "tick"), held)
            }
        } finally { instrumentation.runOnMainSync { bitmap.recycle() } }
    }

    @Test fun fiveLineFocusCoversBothViewportEdgesBeyondTextMargins() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val density = context.resources.displayMetrics.density
            val width = (360 * density).toInt()
            val margin = (24 * density).toInt()
            val root = FrameLayout(context)
            val source = TextView(context).apply {
                text = "First row\nSecond row\nThird row\nFourth row\nAnchor row\nSixth row\nSeventh row\nEighth row\nNinth row"
                textSize = 20f
                includeFontPadding = false
            }
            root.addView(source, FrameLayout.LayoutParams(width - 2 * margin,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = margin; rightMargin = margin })
            val overlay = ReadingAidOverlayView(context)
            root.addView(overlay, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val height = source.measuredHeight
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val geometry = ScanpathTextGeometryReader().read(source)!!.forEngine("g1")
            assertEquals(9, geometry.lines.size)
            assertTrue(geometry.visibleRectScreen.left > 0)
            assertTrue(geometry.visibleRectScreen.right < width)
            val focus = action(SystemClock.elapsedRealtimeNanos() / 1e6 + 5000).copy(
                aidType = ReadingAidType.FOCUS_REGION, aidFormId = "CONTEXT_FOCUS_REGION_V3",
                anchor = ReadingAidAnchor(0, "g1", 4), strength = 0.5,
                magnificationScale = null, contextRadiusLines = 2)
            val receipts = mutableListOf<Pair<Boolean, String>>()
            overlay.onRender = { _, drawn, reason, _ -> receipts.add(drawn to reason) }
            overlay.setActions(source, geometry, 0, listOf(focus))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                overlay.draw(Canvas(bitmap))
                val xs = listOf(0, margin - 1, margin, width / 2, width - margin, width - 1)
                for (x in xs) {
                    assertEquals("Top veil reaches both edges and text margins", 20,
                        android.graphics.Color.alpha(bitmap.getPixel(x, 0)))
                    assertEquals("Bottom veil reaches both edges and text margins", 20,
                        android.graphics.Color.alpha(bitmap.getPixel(x, height - 1)))
                    for (line in 2..6) {
                        val row = geometry.lines[line]
                        val y = (geometry.textOriginY + (row.top + row.bottom) / 2).toInt()
                        assertEquals("All five rows remain clear across the viewport", 0,
                            android.graphics.Color.alpha(bitmap.getPixel(x, y)))
                    }
                }
                assertTrue(receipts.contains(true to "canvas_draw_proxy"))
            } finally { bitmap.recycle() }
        }
    }

    private fun action(expires: Double) = ReadingAidAction("a1", ReadingAidType.WORD_MAGNIFIER,
        "CONTEXT_WORD_MAGNIFIER_V1", "synthetic", ReadingAidAnchor(0, "g1", 0, 0, 7),
        1.0, 1.5, null, 0.0, expires, emptyList(), listOf("candidate:1"), 0.0, 100.0, 110.0,
        "synthetic", listOf("W24"), "software check", true, "synthetic")

    @Test fun shortFocusViewportHasVisibleShadeAndKeepsTheAnchorClear() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val width = (320 * context.resources.displayMetrics.density).toInt()
            val root = FrameLayout(context)
            val source = TextView(context).apply {
                text = "First row\nSecond row\nAnchor row\nFourth row\nFifth row"
                textSize = 20f
                includeFontPadding = false
            }
            val overlay = ReadingAidOverlayView(context)
            root.addView(source, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(overlay, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val height = source.measuredHeight
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val geometry = ScanpathTextGeometryReader().read(source)!!.forEngine("g1")
            assertEquals(5, geometry.lines.size)
            val focus = action(SystemClock.elapsedRealtimeNanos() / 1e6 + 5000).copy(
                aidType = ReadingAidType.FOCUS_REGION, aidFormId = "CONTEXT_FOCUS_REGION_V2",
                anchor = ReadingAidAnchor(0, "g1", 2, null, null),
                strength = 0.5, magnificationScale = null, contextRadiusLines = 2)
            val receipts = mutableListOf<Pair<Boolean, String>>()
            overlay.onRender = { _, drawn, reason, _ -> receipts.add(drawn to reason) }
            overlay.setActions(source, geometry, 0, listOf(focus))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                overlay.draw(Canvas(bitmap))
                assertEquals(20, android.graphics.Color.alpha(bitmap.getPixel(width / 2, 0)))
                assertEquals(20, android.graphics.Color.alpha(bitmap.getPixel(width / 2, height - 1)))
                val anchor = geometry.lines[2]
                for (y in anchor.top.toInt() until anchor.bottom.toInt()) {
                    assertEquals("The entire anchor row must remain clear", 0,
                        android.graphics.Color.alpha(bitmap.getPixel(width / 2, y)))
                }
                assertTrue(receipts.contains(true to "canvas_draw_proxy"))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                receipts.clear()
                overlay.setActions(source, geometry, 0, listOf(focus.copy(
                    id = "legacy", aidFormId = "CONTEXT_FOCUS_REGION_V1")))
                overlay.draw(Canvas(bitmap))
                assertTrue(receipts.contains(false to "focus_no_dimmed_area"))
                assertFalse(receipts.any { it.first })
                receipts.clear()
                overlay.setActions(source, geometry, 0, listOf(focus.copy(id = "zero", strength = 0.0)))
                overlay.draw(Canvas(bitmap))
                assertTrue(receipts.contains(false to "focus_alpha_zero"))
                assertFalse(receipts.any { it.first })
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(width / 2, 0)))

                // One oversized visible anchor consumes the viewport: no successful draw proxy.
                val fullAnchor = geometry.copy(lines = geometry.lines.mapIndexed { index, line ->
                    if (index == 2) line.copy(top = 0.0, bottom = height.toDouble()) else line
                })
                receipts.clear()
                overlay.setActions(source, fullAnchor, 0, listOf(focus.copy(id = "full")))
                overlay.draw(Canvas(bitmap))
                assertTrue(receipts.contains(false to "focus_no_dimmed_area"))
                assertFalse(receipts.any { it.first })
                assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(width / 2, 0)))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun articleLineStartMarkerDrawsInPaddingWithoutCoveringTheText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val density = context.resources.displayMetrics.density
            val width = (320 * density).toInt()
            val height = (120 * density).toInt()
            val root = FrameLayout(context)
            val source = TextView(context).apply {
                text = "example article line"
                textSize = 20f
                setPadding((16 * density).toInt(), 0, 0, 0)
            }
            val overlay = ReadingAidOverlayView(context, lineStartMarkerInGutter = true)
            root.addView(source, FrameLayout.LayoutParams(width, height))
            root.addView(overlay, FrameLayout.LayoutParams(width, height))
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val geometry = ScanpathTextGeometryReader().read(source)!!.forEngine("g1")
            val marker = action(SystemClock.elapsedRealtimeNanos() / 1e6 + 5000).copy(
                aidType = ReadingAidType.READING_BOOKMARK,
                aidFormId = LineStartMarkerAppearance.BOOKMARK_FORM_ID,
                magnificationScale = null,
            )
            val receipts = mutableListOf<Pair<Boolean, String>>()
            overlay.onRender = { _, drawn, reason, _ -> receipts.add(drawn to reason) }
            overlay.setActions(source, geometry, 0, listOf(marker))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                overlay.draw(Canvas(bitmap))
                var lastPaintedX = -1
                for (y in 0 until height) for (x in 0 until width) {
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) != 0) lastPaintedX = maxOf(lastPaintedX, x)
                }
                assertTrue("The marker must actually draw", lastPaintedX >= 0)
                assertTrue("Marker pixels must clear the first letter", lastPaintedX < source.totalPaddingLeft - 3 * density)
                assertTrue(receipts.contains(true to "canvas_draw_proxy"))
                assertEquals("example article line", source.text.toString())
                val alternate = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    overlay.setActions(source, geometry, 0, listOf(marker.copy(id = "low_strength", strength = 0.1)))
                    overlay.draw(Canvas(alternate))
                    assertTrue("Marker appearance must be independent of strength", bitmap.sameAs(alternate))
                } finally { alternate.recycle() }
            } finally { bitmap.recycle() }
        }
    }

    @Test fun centeredStripFitsLowThenHighStrengthButWithholdsOversizedWords() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val density = context.resources.displayMetrics.density
            val width = (320 * density).toInt()
            val height = (68 * density).toInt()
            val source = TextView(context).apply {
                text = "example paragraph"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 22f)
            }
            val dock = ReadingAidWordView(context).apply {
                layoutParams = ViewGroup.LayoutParams(width, height)
                gravity = Gravity.CENTER
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            }
            fun layoutDock() {
                dock.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                dock.layout(0, 0, width, height)
            }
            layoutDock()
            val receipts = mutableListOf<Pair<Boolean, String>>()
            dock.onRender = { _, drawn, reason, _ -> receipts.add(drawn to reason) }
            val base = action(SystemClock.elapsedRealtimeNanos() / 1e6 + 5000)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                dock.showAction(base.copy(id = "low", strength = 0.25, magnificationScale = 1.125), source)
                layoutDock()
                assertEquals("example", dock.text.toString())
                dock.draw(Canvas(bitmap))
                assertTrue(receipts.contains(true to "text_draw_proxy"))
                receipts.clear()
                dock.showAction(base.copy(id = "high"), source)
                layoutDock()
                assertEquals("example", dock.text.toString())
                assertEquals(source.textSize * 1.5f, dock.textSize, 0.01f)
                assertEquals(height, dock.height)
                dock.draw(Canvas(bitmap))
                assertTrue(receipts.contains(true to "text_draw_proxy"))
                receipts.clear()
                val oversized = "W".repeat(100)
                source.text = oversized
                dock.showAction(base.copy(id = "too_wide", anchor = base.anchor.copy(wordEndUtf16 = oversized.length)), source)
                assertTrue(dock.text.isEmpty())
                assertTrue(receipts.contains(false to "magnified_word_does_not_fit"))
                receipts.clear()
                source.text = "example"
                source.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 100f)
                dock.showAction(base.copy(id = "too_tall"), source)
                assertTrue(dock.text.isEmpty())
                assertTrue(receipts.contains(false to "magnified_word_does_not_fit"))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun magnificationUsesItsReservedViewAndReportsADrawProxy() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = TextView(context).apply { text = "example paragraph"; textSize = 20f }
            val dock = ReadingAidWordView(context).apply {
                layoutParams = ViewGroup.LayoutParams(1000, 300)
            }
            dock.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
            dock.layout(0, 0, 1000, 300)
            val receipt = mutableListOf<Pair<Boolean, String>>()
            dock.onRender = { _, drawn, reason, _ -> receipt.add(drawn to reason) }
            dock.showAction(action(SystemClock.elapsedRealtimeNanos() / 1e6 + 1500), source)
            assertEquals("example", dock.text.toString())
            assertEquals(source.textSize * 1.5f, dock.textSize, 0.01f)
            assertEquals("example paragraph", source.text.toString())
            val bitmap = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
            try { dock.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
            assertTrue(receipt.contains(true to "text_draw_proxy"))
        }
    }

    @Test fun anExpiredMagnifierNeverReportsADrawnFrame() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = TextView(context).apply { text = "example paragraph"; textSize = 20f }
            val dock = ReadingAidWordView(context).apply {
                layoutParams = ViewGroup.LayoutParams(1000, 300)
            }
            dock.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
            dock.layout(0, 0, 1000, 300)
            val receipt = mutableListOf<Pair<Boolean, String>>()
            dock.onRender = { _, drawn, reason, _ -> receipt.add(drawn to reason) }
            dock.showAction(action(0.0), source)
            val bitmap = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
            try { dock.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
            assertTrue(receipt.contains(false to "expired_before_draw"))
            assertFalse(receipt.any { it.first })
        }
    }
}
