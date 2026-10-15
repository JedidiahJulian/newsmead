package com.newsmead.gaze.aid

import android.view.View
import android.view.Gravity
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.data.StudyConfig
import com.newsmead.gaze.getVisibleRectOnScreen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Detached native geometry and fake clock/repository; no network or UI-thread sleeps. */
@RunWith(AndroidJUnit4::class)
class ReadingAidDefinitionOfferTest {
    private class Fixture(headroom: Boolean, scrollHeadroom: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val host = FrameLayout(context)
        private val articleTop = if (headroom) dp(80) else 0
        val body = object : TextView(context) {
            override fun getLocationOnScreen(location: IntArray) {
                super.getLocationOnScreen(location)
                // Detached fixtures have no window translation; model the content's known offset.
                if (scrollHeadroom) { location[0] = 0; location[1] = articleTop }
            }
        }.apply {
            text = "keyboard source word\nAnother article line\nThird article line"
            textSize = 16f
            setPadding(0, if (headroom && !scrollHeadroom) dp(80) else 0, 0, 0)
        }
        val scrollViewport = if (scrollHeadroom) NestedScrollView(context) else null
        var clock = 1000.0
        var due: Double? = null
        var task: Runnable? = null
        var requests = 0
        val events = mutableListOf<String>()
        val consultations = mutableListOf<Boolean>()
        val renders = mutableListOf<Pair<Boolean, String>>()
        val repository = object : WordDefinitionRepository {
            override fun lookup(word: String, callback: (WordDefinition?) -> Unit): DefinitionRequest {
                requests++
                callback(WordDefinition(word, "Fixture definition", "fixture"))
                return DefinitionRequest { }
            }
        }
        val popup: ReadingAidDefinitionPopup
        var action = ReadingAidAction("test", ReadingAidType.WORD_DEFINITION, "CONTEXT_WORD_LOOKUP_OFFER_V1", "TEST",
            ReadingAidAnchor(1, "g1", 0, 0, 8), 1.0, null, null, 1.0, Double.MAX_VALUE,
            emptyList(), emptyList(), 1.0, 1.0, 1.0, "TEST", emptyList(), "synthetic fixture", true, "TEST")

        init {
            if (scrollViewport == null) host.addView(body, FrameLayout.LayoutParams(dp(360), dp(600)))
            else {
                val content = FrameLayout(context)
                if (headroom) content.addView(TextView(context).apply { text = "Article headline" },
                    FrameLayout.LayoutParams(dp(360), dp(24)))
                content.addView(body, FrameLayout.LayoutParams(dp(360), dp(600)).apply { topMargin = articleTop })
                scrollViewport.addView(content, FrameLayout.LayoutParams(dp(360), FrameLayout.LayoutParams.WRAP_CONTENT))
                host.addView(scrollViewport, FrameLayout.LayoutParams(dp(360), dp(640)))
            }
            layout()
            popup = ReadingAidDefinitionPopup(context, host, body, repository,
                offerClock = { clock },
                offerSchedule = { runnable, delay -> task = runnable; due = clock + delay },
                offerCancel = { runnable -> if (task === runnable) { task = null; due = null } })
            popup.onOfferEvent = { _, event, _ -> events.add(event) }
            popup.onRender = { _, visible, reason, _ -> renders.add(visible to reason) }
            popup.onConsultationChanged = { active, _, _ -> consultations.add(active) }
        }
        fun layout() {
            host.measure(View.MeasureSpec.makeMeasureSpec(dp(360), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(640), View.MeasureSpec.EXACTLY))
            host.layout(0, 0, dp(360), dp(640))
            body.viewTreeObserver.dispatchOnPreDraw()
        }
        fun offer() { popup.offerAction(action); layout() }
        fun button(): View = host.findViewById(R.id.readingAidLookupOffer)
        fun advance(delta: Double) {
            clock += delta
            if (due?.let { clock >= it } == true) task!!.run()
        }
    }
    private fun fixture(headroom: Boolean = true, scrollHeadroom: Boolean = false, check: (Fixture) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val f = Fixture(headroom, scrollHeadroom)
            try { check(f) } finally { f.popup.close() }
        }
    }

    private fun assertBubbleTailAtUpperRight(f: Fixture, mirrored: Boolean, previewLabel: String? = null) {
        val button = f.button() as ImageView
        assertNull("Bubble must not have an enclosing button tile", button.background)
        assertEquals(0f, button.elevation, 0f)
        val drawable = button.drawable
        val expectedSide = 28 * f.context.resources.displayMetrics.density
        assertEquals(expectedSide, drawable.intrinsicWidth.toFloat(), 1f)
        val point = floatArrayOf(drawable.intrinsicWidth * (if (mirrored) 20f else 4f) / 24f,
            drawable.intrinsicHeight * 22f / 24f)
        button.imageMatrix.mapPoints(point)
        val visible = Rect()
        assertTrue(button.getVisibleRectOnScreen(visible))
        val tipX = visible.left + button.paddingLeft + point[0]
        val tipY = visible.top + button.paddingTop + point[1]
        val location = IntArray(2); f.body.getLocationOnScreen(location)
        val wordRight = location[0] + f.body.totalPaddingLeft - f.body.scrollX +
            f.body.layout.getPrimaryHorizontal(f.action.anchor.wordEndUtf16!!)
        val lineTop = location[1] + f.body.totalPaddingTop - f.body.scrollY +
            f.body.layout.getLineTop(f.action.anchor.line)
        val lineBottom = location[1] + f.body.totalPaddingTop - f.body.scrollY +
            f.body.layout.getLineBottom(f.action.anchor.line)
        val wordLeft = location[0] + f.body.totalPaddingLeft - f.body.scrollX +
            f.body.layout.getPrimaryHorizontal(f.action.anchor.wordStartUtf16!!)
        assertEquals("Tail must align with the source word's right edge", wordRight, tipX, 1f)
        assertTrue(visible.left >= 0 && visible.right <= f.host.width)
        // Render this synthetic fixture for visual inspection; no screen/camera capture.
        val preview = Bitmap.createBitmap(f.host.width,
            maxOf(f.dp(180), lineBottom + f.dp(16)).coerceAtMost(f.host.height), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(preview)
            canvas.drawColor(Color.WHITE)
            f.host.draw(canvas)
            // Measure actual painted letters instead of reproducing the placement calculation.
            val textTop = (lineTop.coerceAtLeast(0) until lineBottom.coerceAtMost(preview.height)).firstOrNull { y ->
                (wordLeft.toInt().coerceAtLeast(0) until kotlin.math.ceil(wordRight.toDouble()).toInt()
                    .coerceAtMost(preview.width)).any { x ->
                    val color = preview.getPixel(x, y)
                    val red = Color.red(color); val green = Color.green(color); val blue = Color.blue(color)
                    val bubbleBlue = blue > red + 3 && blue > green + 3
                    !bubbleBlue && (red < 245 || green < 245 || blue < 245)
                }
            }
            assertNotNull("Fixture must paint source letters", textTop)
            val paintedTop = textTop!!
            val gap = paintedTop - tipY
            assertTrue("Tail-to-letter gap must stay close to 3dp; actual=$gap px",
                gap in (f.dp(2) - 1).toFloat()..(f.dp(4) + 1).toFloat())
            assertTrue("Transparent touch target must not overlap source letters", visible.bottom <= paintedTop)
            val label = previewLabel ?: if (mirrored) "mirrored" else "normal"
            val name = "lookup_info_bubble_corner_$label.png"
            File(f.context.noBackupFilesDir, name).outputStream().use {
                check(preview.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { preview.recycle() }
    }

    @Test fun offerStaysAboveWordAndTapAloneStartsOneConsultation() = fixture { f ->
        f.offer()
        assertTrue(f.popup.isOfferShowing)
        assertFalse(f.popup.isShowing)
        assertEquals(0, f.requests)
        assertTrue(f.consultations.isEmpty())
        assertEquals(listOf("shown"), f.events)
        assertTrue(f.renders.contains(true to "definition_offer_layout_proxy"))
        val button = f.button()
        assertEquals(f.dp(48), button.width)
        assertEquals("Look up keyboard", button.contentDescription.toString())
        assertEquals(2, f.host.childCount) // Body and offer only; no word highlight yet.
        assertBubbleTailAtUpperRight(f, mirrored = false)
        button.performClick()
        assertFalse(f.popup.isOfferShowing)
        assertTrue(f.popup.isShowing)
        assertEquals(1, f.requests)
        assertEquals(listOf(true), f.consultations)
        assertEquals(listOf("shown", "accepted"), f.events)
        assertEquals(f.action, f.popup.action) // Original evidence/action clock retained.
        assertEquals(4, f.host.childCount) // Body, panel wrapper, pointer, source highlight.
        button.performClick()
        assertEquals(1, f.requests)
    }

    @Test fun rightEdgeMirrorsBubbleAndKeepsTailOnTheSameWordCorner() = fixture { f ->
        f.body.text = "keyboard\nAnother article line\nThird article line"
        f.body.gravity = Gravity.TOP or Gravity.RIGHT
        f.body.setPadding(0, f.dp(80), f.dp(16), 0)
        f.layout()
        f.offer()
        assertTrue(f.popup.isOfferShowing)
        assertBubbleTailAtUpperRight(f, mirrored = true)
        assertEquals(0, f.requests)
        assertTrue(f.consultations.isEmpty())
    }

    @Test fun articleFontAndLineSpacingKeepBubbleNearActualThirdLineLetters() = fixture { f ->
        f.body.text = "First article line\nSecond article line\nInnovative CFD\nAnother article line"
        f.body.typeface = ResourcesCompat.getFont(f.context, R.font.arial_regular)
        f.body.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP)
        f.body.setLineSpacing(f.context.resources.displayMetrics.scaledDensity, 1.6f)
        f.layout()
        val start = f.body.text.toString().indexOf("Innovative")
        f.action = f.action.copy(anchor = f.action.anchor.copy(line = 2, wordStartUtf16 = start,
            wordEndUtf16 = start + "Innovative".length))
        f.offer()
        assertTrue(f.popup.isOfferShowing)
        assertBubbleTailAtUpperRight(f, mirrored = false, previewLabel = "article_spacing")
        assertEquals(0, f.requests)
    }

    @Test fun refreshAndNullDoNotRenewOrMoveAndIgnoredOfferExpiresWithoutLookup() = fixture { f ->
        f.offer()
        val firstDue = f.due
        val button = f.button()
        f.advance(4000.0)
        f.popup.offerAction(null)
        f.popup.offerAction(f.action.copy(id = "refreshed"))
        f.popup.offerAction(f.action.copy(id = "other", anchor = f.action.anchor.copy(wordStartUtf16 = 9, wordEndUtf16 = 15)))
        assertSame(button, f.button())
        assertEquals(firstDue, f.due)
        f.advance(1000.0)
        assertFalse(f.popup.isOfferShowing)
        assertEquals(listOf("shown", "expired"), f.events)
        assertEquals(0, f.requests)
        assertTrue(f.consultations.isEmpty())
        f.offer()
        assertFalse(f.popup.isOfferShowing)
        f.popup.offerAction(f.action.copy(id = "new-clock", anchor = f.action.anchor.copy(geometrySnapshotId = "g2")))
        assertFalse(f.popup.isOfferShowing)
        f.popup.clear("explicit_boundary")
        f.offer()
        assertTrue(f.popup.isOfferShowing)
    }

    @Test fun textAndScrollInvalidateAndCancelDeadlineWithoutConsultation() = fixture { f ->
        f.offer()
        f.body.scrollTo(0, 1)
        f.body.viewTreeObserver.dispatchOnPreDraw()
        assertFalse(f.popup.isOfferShowing)
        assertNull(f.task)
        assertEquals(listOf("shown", "cleared:article_layout_changed"), f.events)
        f.popup.clear("fixture_reset")
        f.body.scrollTo(0, 0)
        f.offer()
        f.body.text = "changed source word"
        f.button().performClick()
        assertFalse(f.popup.isShowing)
        assertFalse(f.popup.isOfferShowing)
        assertEquals(0, f.requests)
        assertTrue(f.consultations.isEmpty())
    }

    @Test fun expiredWordCanReofferOnlyAfterValidDifferentObservedWord() = fixture { f ->
        f.offer()
        f.advance(5000.0)
        f.popup.observeReadingTarget(1, 0, 8)
        f.popup.observeReadingTarget(1, null, null)
        f.popup.observeReadingTarget(1, 8, 9) // Whitespace.
        f.popup.observeReadingTarget(1, 9, 12) // Partial token.
        f.offer()
        assertFalse(f.popup.isOfferShowing)
        f.popup.observeReadingTarget(1, 9, 15) // A valid "source" observation, without another offer.
        f.offer()
        assertTrue(f.popup.isOfferShowing)
        val current = f.button()
        f.popup.observeReadingTarget(1, 9, 15)
        f.popup.offerAction(f.action.copy(anchor = f.action.anchor.copy(wordStartUtf16 = 9, wordEndUtf16 = 15)))
        assertSame(current, f.button())
        assertEquals(0, f.requests)
    }

    @Test fun staleDeadlineCannotExpireNewOffer() = fixture { f ->
        f.offer()
        val stale = f.task!!
        val oldButton = f.button()
        f.advance(5000.0)
        f.popup.observeReadingTarget(1, 9, 15)
        f.offer()
        val current = f.button()
        val due = f.due
        stale.run() // Simulate a callback already dequeued before cancellation.
        oldButton.performClick()
        assertTrue(f.popup.isOfferShowing)
        assertSame(current, f.button())
        assertEquals(due, f.due)
        assertEquals(0, f.requests)
        f.advance(5000.0)
        assertFalse(f.popup.isOfferShowing)
        assertEquals(listOf("shown", "expired", "shown", "expired"), f.events)
    }

    @Test fun firstBodyRowUsesHeadlineHeadroomInsideNearestScrollViewport() = fixture(scrollHeadroom = true) { f ->
        assertEquals(0, f.body.totalPaddingTop)
        f.offer()
        assertTrue(f.popup.isOfferShowing)
        assertTrue(f.renders.contains(true to "definition_offer_layout_proxy"))
        val button = f.button()
        val wordLocation = IntArray(2); f.body.getLocationOnScreen(wordLocation)
        assertTrue(button.y >= f.scrollViewport!!.top)
        val ink = Rect()
        f.body.paint.getTextBounds("keyboard", 0, 8, ink)
        val inkTop = wordLocation[1] + f.body.layout.getLineBaseline(0) + ink.top
        assertTrue(button.y + button.height <= inkTop)
        assertEquals(0, f.requests)
    }

    @Test fun noHeadroomWithholdsRatherThanPlacingBelowWord() = fixture(headroom = false) { f ->
        f.offer()
        assertFalse(f.popup.isOfferShowing)
        assertEquals(listOf(false to "definition_offer_space_unavailable"), f.renders)
        assertTrue(f.events.isEmpty())
        assertEquals(0, f.requests)
    }

    @Test fun preTapAnchorFailuresRemainOfferPhaseReasons() = fixture { f ->
        f.popup.offerAction(f.action.copy(anchor = f.action.anchor.copy(wordEndUtf16 = 999)))
        assertEquals(listOf(false to "definition_offer_anchor_unavailable"), f.renders)
        f.body.scrollTo(0, f.dp(100))
        f.popup.offerAction(f.action)
        assertEquals(false to "definition_offer_word_not_wholly_visible", f.renders.last())
        assertTrue(f.renders.all { it.second.startsWith("definition_offer_") })
        assertTrue(f.consultations.isEmpty())
        assertEquals(0, f.requests)
    }

    @Test fun nearestScrollViewportWithoutHeadroomAlsoWithholds() = fixture(headroom = false, scrollHeadroom = true) { f ->
        f.offer()
        assertFalse(f.popup.isOfferShowing)
        assertEquals(listOf(false to "definition_offer_space_unavailable"), f.renders)
        assertEquals(0, f.requests)
    }

    @Test fun reflowClearsOfferAndOldControlCannotQuery() = fixture { f ->
        f.offer()
        val old = f.button()
        f.body.textSize = 22f
        f.layout()
        assertFalse(f.popup.isOfferShowing)
        old.performClick()
        assertEquals(0, f.requests)
        assertNull(f.task)
        assertTrue(f.consultations.isEmpty())
    }

    @Test fun physicalHitTestIncludesOnlyFortyEightDpControl() = fixture { f ->
        f.offer()
        // Detached native views have no window location; global visible bounds include translation.
        val visible = Rect()
        assertTrue(f.button().getVisibleRectOnScreen(visible))
        assertEquals(f.dp(48), visible.width())
        assertEquals(f.dp(48), visible.height())
        val x = visible.left.toFloat(); val y = visible.top.toFloat()
        assertTrue(f.popup.lookupOfferContains(x, y))
        assertTrue(f.popup.lookupOfferContains(x + f.dp(48) - 1, y + f.dp(48) - 1))
        assertFalse(f.popup.lookupOfferContains(x - 1, y))
        assertFalse(f.popup.lookupOfferContains(x, y + f.dp(48)))
        f.popup.clear("reader_left")
        f.popup.clear("reader_left")
        assertFalse(f.popup.lookupOfferContains(x, y))
        assertTrue(f.consultations.isEmpty())
        assertEquals(listOf("shown", "cleared:reader_left"), f.events)
    }
}
