package com.newsmead.gaze.aid

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.widget.NestedScrollView
import com.newsmead.R
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.scanpath.ScanpathTextDirection
import com.newsmead.gaze.scanpath.ScanpathTextGeometry
import com.newsmead.gaze.scanpath.ScanpathTextGeometryReader
import com.newsmead.gaze.scanpath.ScanpathWordMapper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A reader-confirmed entry point. Geometry and hit tests use physical screen pixels. */
internal class ReadingAidDefinitionOffer(
    private val context: Context,
    private val host: ViewGroup,
    private val body: TextView,
    private val clock: () -> Double,
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val accept: (ReadingAidAction) -> Unit,
    private val event: (ReadingAidAction, String, Double) -> Unit,
    private val render: (ReadingAidAction, Boolean, String, Double) -> Unit,
) {
    companion object {
        const val STYLE_ID = "WORD_LOOKUP_INFO_BUBBLE_INK_CORNER_V4"
        const val TIP_GAP_DP = 3
    }
    private val reader = ScanpathTextGeometryReader()
    private var button: AppCompatImageButton? = null
    private var action: ReadingAidAction? = null
    private var text = ""
    private var frame: ScanpathTextGeometry? = null
    private var viewport: Rect? = null
    private var suppressed: ReadingAidAnchor? = null
    private var shownAt: Double? = null
    private var bounds = Rect()
    val isShowing get() = button != null
    private var deadline: Runnable? = null
    private val listener = ViewTreeObserver.OnPreDrawListener {
        if (button != null) {
            if (!valid()) clear("article_layout_changed")
            else if (shownAt == null) {
                val view = button!!
                val actual = Rect()
                if (view.width > 0 && view.height > 0 && view.getVisibleRectOnScreen(actual) &&
                    actual == bounds) {
                    val value = action!!
                    shownAt = clock()
                    val timeout = Runnable {
                        if (button === view && action === value) clear("expired")
                    }
                    deadline = timeout
                    schedule(timeout, ReadingAidDefinitionPopup.OFFER_TTL_MS)
                    event(value, "shown", shownAt!!)
                    if (action === value) render(value, true, "definition_offer_layout_proxy", clock())
                } else if (view.width > 0 && view.height > 0) {
                    val value = action!!
                    clear("offer_not_wholly_visible")
                    render(value, false, "definition_offer_space_unavailable", clock())
                }
            }
        }
        true
    }

    fun contains(x: Float, y: Float): Boolean = button != null && shownAt != null &&
        x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom

    fun observeReadingTarget(generation: Long, start: Int?, end: Int?) {
        val previous = suppressed ?: return
        if (button != null || start == null || end == null || start < 0 || end <= start ||
            end > body.text.length) return
        if (previous.generation == generation && previous.wordStartUtf16 == start &&
            previous.wordEndUtf16 == end) return
        // Missing, whitespace and partial-token ranges do not establish leaving for another word.
        if (ScanpathWordMapper(body.text.toString(), body).ranges.any {
                it.startUtf16 == start && it.endUtf16 == end }) suppressed = null
    }

    fun offer(value: ReadingAidAction?) {
        if (value == null || value.aidType != ReadingAidType.WORD_DEFINITION) return
        if (button != null) return // Keep the first visible target steady, including across null actions.
        if (suppressed?.let { it.generation == value.anchor.generation &&
                it.wordStartUtf16 == value.anchor.wordStartUtf16 &&
                it.wordEndUtf16 == value.anchor.wordEndUtf16 } == true) return
        val source = body.text.toString()
        val snapshot = reader.read(body)
        val layout = body.layout
        val start = value.anchor.wordStartUtf16
        val end = value.anchor.wordEndUtf16
        val line = value.anchor.line
        if (snapshot == null || layout == null || snapshot.direction != ScanpathTextDirection.LTR ||
            start == null || end == null || start < 0 || end <= start || end > source.length ||
            line !in 0 until layout.lineCount || layout.getLineForOffset(start) != line ||
            layout.getLineForOffset(end - 1) != line) {
            render(value, false, "definition_offer_anchor_unavailable", clock()); return
        }
        val left = snapshot.originX + min(layout.getPrimaryHorizontal(start), layout.getPrimaryHorizontal(end))
        val right = snapshot.originX + max(layout.getPrimaryHorizontal(start), layout.getPrimaryHorizontal(end))
        val top = snapshot.originY + layout.getLineTop(line)
        val bottom = snapshot.originY + layout.getLineBottom(line)
        if (right <= left || left < snapshot.visible.left || right > snapshot.visible.right ||
            top < snapshot.visible.top || bottom > snapshot.visible.bottom) {
            render(value, false, "definition_offer_word_not_wholly_visible", clock()); return
        }
        val visible = articleViewport(snapshot)
        val size = dp(48)
        // The visible bubble sits at the bottom of its transparent touch target.
        // Anchor the vector's tail tip (4,22 in a 24px viewport) to the word's right edge.
        val paddingLeft = dp(10)
        val paddingRight = dp(10)
        val glyphSize = min(dp(28), size - paddingLeft - paddingRight)
        val glyphLeft = paddingLeft + (size - paddingLeft - paddingRight - glyphSize) / 2f
        val ink = Rect()
        layout.paint.getTextBounds(source, start, end, ink)
        if (ink.isEmpty) {
            render(value, false, "definition_offer_word_ink_unavailable", clock()); return
        }
        // Line tops include font padding/leading. Place the actual vector tip near the letters.
        val inkTop = snapshot.originY + layout.getLineBaseline(line) + ink.top
        val tipOffsetY = size - glyphSize + glyphSize * 22.0 / 24.0
        val y = (inkTop - dp(TIP_GAP_DP) - tipOffsetY).roundToInt()
        if (visible.width() < size || y < visible.top || y + size > visible.bottom || y + size > inkTop) {
            render(value, false, "definition_offer_space_unavailable", clock()); return
        }
        fun xForTip(tip: Int) = (right - glyphLeft - glyphSize * tip / 24.0).roundToInt()
        fun fits(x: Int) = x >= visible.left && x + size <= visible.right
        val normalX = xForTip(4)
        val mirrored = !fits(normalX)
        val x = if (mirrored) xForTip(20) else normalX
        if (!fits(x)) {
            render(value, false, "definition_offer_space_unavailable", clock()); return
        }
        val themed = ContextThemeWrapper(context, R.style.Theme_NewsMead)
        val view = AppCompatImageButton(themed).apply {
            id = R.id.readingAidLookupOffer
            setImageResource(if (mirrored) R.drawable.ic_word_lookup_offer_mirrored
                else R.drawable.ic_word_lookup_offer)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(paddingLeft, size - glyphSize, paddingRight, 0)
            // The bubble itself is the visible affordance; no surrounding tile or shadow.
            background = null
            imageTintList = null
            contentDescription = context.getString(R.string.definition_lookup_offer, source.substring(start, end))
            elevation = 0f
            translationZ = 1100f
            setOnClickListener { clicked ->
                if (button !== clicked) return@setOnClickListener
                val original = action ?: return@setOnClickListener
                if (!valid()) { clear("article_layout_changed"); return@setOnClickListener }
                if (shownAt == null) return@setOnClickListener
                if (clock() - shownAt!! >= ReadingAidDefinitionPopup.OFFER_TTL_MS) {
                    clear("expired"); return@setOnClickListener
                }
                // Remove first; callbacks from consultation may synchronously clear the runtime action.
                clear("accepted")
                accept(original)
            }
        }
        val origin = IntArray(2); host.getLocationOnScreen(origin)
        text = source; frame = snapshot; viewport = visible; action = value; button = view
        suppressed = null
        bounds = Rect(x, y, x + size, y + size)
        host.addView(view, ViewGroup.LayoutParams(size, size))
        view.x = (x - origin[0]).toFloat(); view.y = (y - origin[1]).toFloat()
        body.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun articleViewport(snapshot: ScanpathTextGeometry): Rect {
        var ancestor = body.parent as? View
        while (ancestor != null) {
            val parent: View = ancestor
            if (parent is ScrollView || parent is NestedScrollView) {
                val rect = Rect()
                if (parent.getVisibleRectOnScreen(rect)) return rect
                return Rect()
            }
            ancestor = parent.parent as? View
        }
        return Rect(snapshot.visible.left.toInt(), snapshot.visible.top.toInt(),
            snapshot.visible.right.toInt(), snapshot.visible.bottom.toInt())
    }

    private fun valid(): Boolean = body.text.toString() == text && reader.read(body) == frame &&
        frame?.let { articleViewport(it) == viewport } == true

    fun clear(reason: String, resetSuppression: Boolean = false) {
        deadline?.let(cancel)
        deadline = null
        val previous = action
        val old = button
        action = null; button = null; shownAt = null; frame = null; viewport = null
        if (body.viewTreeObserver.isAlive) body.viewTreeObserver.removeOnPreDrawListener(listener)
        old?.let(host::removeView)
        if (resetSuppression) suppressed = null else if (previous != null) suppressed = previous.anchor
        if (previous != null) event(previous, when (reason) {
            "accepted", "expired" -> reason
            else -> "cleared:$reason"
        }, clock())
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
