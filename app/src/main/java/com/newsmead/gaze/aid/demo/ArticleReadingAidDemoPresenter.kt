package com.newsmead.gaze.aid.demo

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.widget.TextView
import com.newsmead.R
import com.newsmead.gaze.aid.OfflineWordDefinitions
import com.newsmead.gaze.aid.LineStartMarkerAppearance
import com.newsmead.gaze.aid.ReadingAidAction
import com.newsmead.gaze.aid.ReadingAidAnchor
import com.newsmead.gaze.aid.ReadingAidOverlayView
import com.newsmead.gaze.aid.ReadingAidType
import com.newsmead.gaze.aid.ReadingGuideResolver
import com.newsmead.gaze.aid.ReadingGuideStyle
import com.newsmead.gaze.aid.ReadingFocusAppearance
import com.newsmead.gaze.aid.ReadingFocusStyle
import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathTextDirection
import com.newsmead.gaze.scanpath.ScanpathTextGeometryReader
import com.newsmead.gaze.scanpath.ScanpathWordMapper

/** Article presentation only. No provider, detector, policy, or measurement journal. */
internal class ArticleReadingAidDemoPresenter(
    private val body: TextView,
    private val overlay: ReadingAidOverlayView,
    private val renderWord: (ReadingAidAction?) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onStopped: (String) -> Unit,
    private val guideStyle: ReadingGuideStyle = ReadingGuideStyle.LINE,
    private val focusStyle: ReadingFocusStyle = ReadingFocusStyle.FADE_TEXT,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = ScanpathTextGeometryReader()
    private var geometry: ScanpathGeometry? = null
    private var originalBody = ""
    private var generation = 0L
    private var serial = 0L
    private var line = 0
    private var wordStart = 0
    private var wordEnd = 0
    private var focusAvailable = false
    var isPresenting = false; private set
    var stageIndex = 0; private set
    var activeAction: ReadingAidAction? = null; private set
    private val advance = Runnable { step(1) }
    private val geometryListener = ViewTreeObserver.OnPreDrawListener {
        if (isPresenting && !geometryMatches()) stop("article_layout_changed")
        true
    }

    /** Caller settles panel layout and scrolling before taking this snapshot. */
    fun start() {
        stop("replaced", notify = false)
        reader.invalidate()
        val frame = checkNotNull(reader.read(body)) { "Article layout is unavailable." }
        check(frame.direction == ScanpathTextDirection.LTR) { "Demo requires left-to-right article text." }
        val visibleLines = frame.lines.indices.filter { i ->
            val row = frame.lines[i]
            frame.originY + row.top >= frame.visible.top && frame.originY + row.bottom <= frame.visible.bottom &&
                frame.originX + row.left >= frame.visible.left && frame.originX + row.right <= frame.visible.right && row.right > row.left
        }
        check(visibleLines.size >= 2) { "Scroll to expose at least two complete article lines." }
        originalBody = body.text.toString()
        val words = ScanpathWordMapper(originalBody, body).ranges
        val eligibleWords = words.filter { word ->
            val row = body.layout.getLineForOffset(word.startUtf16)
            row in visibleLines &&
                body.layout.getLineForOffset(word.endUtf16 - 1) == row
        }
        // Prefer a real article word with a bundled entry so the preview also works offline.
        // Longer words take priority over function words; no article text is substituted.
        // Reserve above-word headroom for the lookup control inside the visible article.
        val offerHeadroom = 52 * body.resources.displayMetrics.density
        val wordsWithHeadroom = eligibleWords.filter {
            val row = body.layout.getLineForOffset(it.startUtf16)
            frame.originY + body.layout.getLineTop(row) - offerHeadroom >= frame.visible.top
        }.ifEmpty { eligibleWords }
        val coveredWords = wordsWithHeadroom.filter {
            OfflineWordDefinitions.lookup(originalBody.substring(it.startUtf16, it.endUtf16)) != null
        }
        val word = coveredWords.firstOrNull { it.endUtf16 - it.startUtf16 >= 4 }
            ?: coveredWords.firstOrNull() ?: wordsWithHeadroom.firstOrNull()
            ?: error("No complete word is visible.")
        line = body.layout.getLineForOffset(word.startUtf16)
        wordStart = word.startUtf16; wordEnd = word.endUtf16
        generation++
        geometry = frame.forEngine("ARTICLE_DEMO:$generation")
        focusAvailable = ReadingFocusAppearance.resolve(geometry!!, geometry!!.visibleRectScreen,
            line, 2, 1.0, limitContextHeight = false, keepLineCount = true,
            occludeSurroundings = true).appearance != null
        stageIndex = 0; isPresenting = true
        body.viewTreeObserver.addOnPreDrawListener(geometryListener)
        render()
    }

    fun step(delta: Int) {
        if (!isPresenting) return
        handler.removeCallbacks(advance)
        if (!geometryMatches()) { stop("article_layout_changed"); return }
        stageIndex = ReadingAidDemoPlan.move(stageIndex, delta)
        render()
    }

    private fun geometryMatches() = originalBody == body.text.toString() &&
        reader.read(body)?.forEngine(geometry?.snapshotId ?: "") == geometry

    private fun render() {
        val stage = ReadingAidDemoPlan.stages[stageIndex]
        val at = SystemClock.elapsedRealtimeNanos() / 1e6
        val selectedType = if (stage.type == ReadingAidType.CURRENT_LINE && guideStyle == ReadingGuideStyle.SENTENCE)
            ReadingAidType.CURRENT_SENTENCE else stage.type
        val guideRange = if (selectedType == ReadingAidType.CURRENT_SENTENCE)
            ReadingGuideResolver().resolve(originalBody, geometry, line) else null
        val type = selectedType?.takeUnless {
            (it == ReadingAidType.FOCUS_REGION && !focusAvailable) ||
                (it == ReadingAidType.CURRENT_SENTENCE && guideRange == null)
        }
        val targetLine = if (type == ReadingAidType.NEXT_LINE_START) line + 1 else line
        activeAction = type?.let {
            ReadingAidAction("ARTICLE_DEMO:${++serial}", it, when (it) {
                ReadingAidType.CURRENT_LINE -> "CURRENT_LINE_HIGHLIGHT_GRAY_V1"
                ReadingAidType.CURRENT_SENTENCE -> "CURRENT_SENTENCE_CONTEXT_HIGHLIGHT_V1"
                ReadingAidType.NEXT_LINE_START -> LineStartMarkerAppearance.FORM_ID
                ReadingAidType.READING_BOOKMARK -> LineStartMarkerAppearance.BOOKMARK_FORM_ID
                ReadingAidType.WORD_DEFINITION -> "CONTEXT_WORD_LOOKUP_OFFER_V1"
                ReadingAidType.FOCUS_REGION -> "CONTEXT_FOCUS_REGION_V4"
                ReadingAidType.WORD_MAGNIFIER -> "CONTEXT_WORD_MAGNIFIER_V1"
            }, "DEMO_PRESENTATION_ONLY", ReadingAidAnchor(generation, geometry!!.snapshotId,
                targetLine,
                wordStart.takeIf { it >= 0 && (type == ReadingAidType.WORD_DEFINITION || type == ReadingAidType.WORD_MAGNIFIER) },
                wordEnd.takeIf { it >= 0 && (type == ReadingAidType.WORD_DEFINITION || type == ReadingAidType.WORD_MAGNIFIER) }), stage.strength, null,
                if (it == ReadingAidType.FOCUS_REGION) 2 else null,
                at, at + ReadingAidDemoPlan.STAGE_MS, emptyList(), emptyList(), at, at, at,
                "DEMO_BUTTON_OR_TIMER", emptyList(), "Manufactured presentation; no detector evidence", true, "DEMO_GUIDE_V2",
                guideRange = guideRange,
                focusStyle = focusStyle.takeIf { type == ReadingAidType.FOCUS_REGION })
        }
        overlay.setActions(body, geometry, generation, listOfNotNull(activeAction))
        renderWord(activeAction?.takeIf { it.aidType == ReadingAidType.WORD_DEFINITION })
        val label = body.context.getString(when (selectedType) {
            null -> R.string.scanpath_demo_baseline
            ReadingAidType.CURRENT_LINE -> R.string.scanpath_demo_line
            ReadingAidType.CURRENT_SENTENCE -> R.string.reading_aid_menu_sentence
            ReadingAidType.NEXT_LINE_START -> R.string.scanpath_demo_start
            ReadingAidType.READING_BOOKMARK -> R.string.scanpath_demo_start
            ReadingAidType.WORD_DEFINITION -> R.string.article_aid_demo_definition
            ReadingAidType.WORD_MAGNIFIER -> R.string.scanpath_demo_word
            ReadingAidType.FOCUS_REGION -> R.string.scanpath_demo_focus
        })
        onStatus(if (stage.type == ReadingAidType.FOCUS_REGION && !focusAvailable)
            body.context.getString(R.string.article_aid_demo_unavailable, "Focus needs a visible reading line and surrounding text.")
        else if (selectedType == ReadingAidType.CURRENT_SENTENCE && guideRange == null)
            body.context.getString(R.string.article_aid_demo_unavailable, "Sentence context is unavailable for this line.")
        else body.context.getString(R.string.article_aid_demo_status, stageIndex + 1, ReadingAidDemoPlan.stages.size, label))
        handler.postDelayed(advance, ReadingAidDemoPlan.STAGE_MS)
    }

    fun stop(reason: String, notify: Boolean = true) {
        val wasPresenting = isPresenting
        isPresenting = false
        handler.removeCallbacks(advance)
        if (body.viewTreeObserver.isAlive) body.viewTreeObserver.removeOnPreDrawListener(geometryListener)
        activeAction = null
        overlay.setActions(body, null, generation, emptyList())
        renderWord(null)
        geometry = null
        if (wasPresenting && notify) onStopped(reason)
    }
}
