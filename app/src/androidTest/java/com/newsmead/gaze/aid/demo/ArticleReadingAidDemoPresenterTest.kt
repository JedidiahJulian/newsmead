package com.newsmead.gaze.aid.demo

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.aid.ReadingAidAction
import com.newsmead.gaze.aid.ReadingAidOverlayView
import com.newsmead.gaze.aid.ReadingAidType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Loaded-text geometry fixtures only: no article navigation, camera, engine, or journal. */
@RunWith(AndroidJUnit4::class)
class ArticleReadingAidDemoPresenterTest {
    private fun fixture(
        text: String = "😀 Article source words\nSecond actual article line\nThird complete line\nFourth complete line\nFifth complete line\nSixth complete line\nSeventh complete line",
        check: (TextView, ArticleReadingAidDemoPresenter, () -> ReadingAidAction?) -> Unit,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val root = FrameLayout(context)
            val body = TextView(context).apply {
                this.text = text
                textSize = 16f
                layoutParams = FrameLayout.LayoutParams(1000, 1000)
            }
            val overlay = ReadingAidOverlayView(context)
            root.addView(body); root.addView(overlay, FrameLayout.LayoutParams(1000, 1000))
            root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1000, 1000)
            var wordAction: ReadingAidAction? = null
            val presenter = ArticleReadingAidDemoPresenter(body, overlay,
                { wordAction = it }, {}, {})
            try { check(body, presenter) { wordAction } }
            finally { presenter.stop("fixture_closed") }
        }
    }

    @Test fun actualArticleWordUsesUtf16AnchorAndNeverChangesSource() = fixture { body, presenter, word ->
        val original = body.text.toString()
        assertFalse(presenter.isPresenting)
        presenter.start()
        assertNull(presenter.activeAction)
        presenter.step(3)
        val action = word()!!
        assertEquals(ReadingAidType.WORD_DEFINITION, action.aidType)
        assertEquals("Article", original.substring(action.anchor.wordStartUtf16!!, action.anchor.wordEndUtf16!!))
        assertEquals(3, action.anchor.wordStartUtf16)
        assertTrue(action.eventIds.isEmpty())
        assertTrue(action.evidenceIds.isEmpty())
        assertEquals("DEMO_PRESENTATION_ONLY", action.conditionId)
        assertEquals(original, body.text.toString())
        presenter.stop("reader_stop")
        presenter.step(1)
        assertFalse(presenter.isPresenting)
        assertNull(word())
        assertNull(presenter.activeAction)
    }

    @Test fun bodyAndGeometryChangesClearBeforeAnotherStage() = fixture { body, presenter, word ->
        presenter.start(); presenter.step(3)
        body.text = "Changed article body"
        presenter.step(1)
        assertFalse(presenter.isPresenting)
        assertNull(word())
    }

    @Test fun markerPreviewUsesExpectedSuccessorWithFixedAppearance() = fixture { _, presenter, _ ->
        presenter.start(); presenter.step(1)
        val source = presenter.activeAction!!.anchor.line
        presenter.step(1)
        val marker = presenter.activeAction!!
        assertEquals(ReadingAidType.NEXT_LINE_START, marker.aidType)
        assertEquals(source + 1, marker.anchor.line)
        assertEquals("CONTINUATION_LINE_START_CUE_V1", marker.aidFormId)
        assertEquals(1.0, marker.strength, 0.0)
        assertTrue(marker.eventIds.isEmpty())
        assertTrue(marker.evidenceIds.isEmpty())
    }

    @Test fun offlinePreviewSelectsCoveredSourceWordAfterUnknownWord() = fixture(
        text = "😀 Qzxunknown keyboard\nQzxother qzxthird\nQzxfourth qzxfifth",
    ) { body, presenter, word ->
        val original = body.text.toString()
        presenter.start(); presenter.step(3)
        val anchor = word()!!.anchor
        assertEquals("keyboard", original.substring(anchor.wordStartUtf16!!, anchor.wordEndUtf16!!))
        assertEquals(original.indexOf("keyboard"), anchor.wordStartUtf16)
        assertEquals(original, body.text.toString())
    }

    @Test fun uncoveredArticleKeepsActualWordInsteadOfManufacturingOfflineText() = fixture(
        text = "Qzxunknown qzxother\nQzxthird qzxfourth\nQzxfifth qzxsixth",
    ) { body, presenter, word ->
        val original = body.text.toString()
        presenter.start(); presenter.step(3)
        val anchor = word()!!.anchor
        assertEquals("Qzxunknown", original.substring(anchor.wordStartUtf16!!, anchor.wordEndUtf16!!))
        assertEquals(original, body.text.toString())
    }

    @Test fun movementClearsAndDoesNotRestart() = fixture { body, presenter, word ->
        presenter.start(); presenter.step(3)
        body.scrollBy(0, 20)
        presenter.step(1)
        assertFalse(presenter.isPresenting)
        assertNull(word())
        presenter.step(-1)
        assertFalse(presenter.isPresenting)
    }

    @Test fun insufficientVisibleArticleAbstainsWithoutChangingText() = fixture { body, presenter, word ->
        body.text = "One line"
        body.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        body.layout(0, 0, 1000, 1000)
        try {
            presenter.start()
            fail("A one-line article must not manufacture a next-line target")
        } catch (_: IllegalStateException) {
            assertFalse(presenter.isPresenting)
            assertNull(word())
            assertEquals("One line", body.text.toString())
        }
    }
}
