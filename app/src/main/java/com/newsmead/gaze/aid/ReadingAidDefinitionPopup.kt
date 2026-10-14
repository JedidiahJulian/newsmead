package com.newsmead.gaze.aid

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.newsmead.R
import com.newsmead.gaze.scanpath.ScanpathTextDirection
import com.newsmead.gaze.scanpath.ScanpathTextGeometry
import com.newsmead.gaze.scanpath.ScanpathTextGeometryReader
import kotlin.math.max
import kotlin.math.min

/** Near-source overlay; no source spans, font changes, or paragraph reflow. */
class ReadingAidDefinitionPopup(
    private val context: Context,
    private val host: ViewGroup,
    private val body: TextView,
    private val repository: WordDefinitionRepository = DictionaryDefinitionRepository(context),
    private val unavailableMessageRes: Int = R.string.definition_unavailable,
) {
    var onConsultationChanged: (Boolean, Double, String) -> Unit = { _, _, _ -> }
    var onRender: (ReadingAidAction, Boolean, String, Double) -> Unit = { _, _, _, _ -> }
    var languageSupported = true
    val isShowing get() = card != null
    var action: ReadingAidAction? = null; private set
    private var card: LinearLayout? = null
    private var sourceHighlight: View? = null
    private var pointer: View? = null
    private var pending: DefinitionRequest? = null
    private var token = 0L
    private var closed = false
    private var sourceText = ""
    private var frame: ScanpathTextGeometry? = null
    private val reader = ScanpathTextGeometryReader()
    private val geometryListener = ViewTreeObserver.OnPreDrawListener {
        if (isShowing && (body.text.toString() != sourceText || reader.read(body) != frame)) clear("article_layout_changed")
        true
    }

    fun showAction(value: ReadingAidAction?, source: TextView = body) {
        if (value == null) { clear("action_cleared"); return }
        if (closed || value.aidType != ReadingAidType.WORD_DEFINITION || source !== body) return
        if (isShowing && action?.anchor == value.anchor) return
        clear("replaced")
        val start = value.anchor.wordStartUtf16
        val end = value.anchor.wordEndUtf16
        sourceText = body.text.toString()
        val snapshot = reader.read(body)
        val layout = body.layout
        if (snapshot == null || layout == null || snapshot.direction != ScanpathTextDirection.LTR ||
            start == null || end == null || start < 0 || end <= start || end > sourceText.length ||
            layout.getLineForOffset(start) != value.anchor.line || layout.getLineForOffset(end - 1) != value.anchor.line) {
            onRender(value, false, "definition_anchor_unavailable", now()); return
        }
        val line = value.anchor.line
        val left = snapshot.originX + min(layout.getPrimaryHorizontal(start), layout.getPrimaryHorizontal(end))
        val right = snapshot.originX + max(layout.getPrimaryHorizontal(start), layout.getPrimaryHorizontal(end))
        val top = snapshot.originY + layout.getLineTop(line)
        val bottom = snapshot.originY + layout.getLineBottom(line)
        val visible = snapshot.visible
        if (left < visible.left || right > visible.right || top < visible.top || bottom > visible.bottom) {
            onRender(value, false, "definition_word_not_wholly_visible", now()); return
        }
        val origin = IntArray(2); host.getLocationOnScreen(origin)
        val margin = dp(8)
        val width = min(dp(310), (visible.right - visible.left).toInt() - 2 * margin)
        // Reserve a bounded reading area on either side of the source line; never cover it.
        val above = (top - visible.top).toInt() - margin
        val below = (visible.bottom - bottom).toInt() - margin
        val height = min(dp(230), max(above, below))
        if (width < dp(160) || height < dp(132)) {
            onRender(value, false, "definition_space_unavailable", now()); return
        }
        val aboveWord = above >= height
        val x = ((left + right - width) / 2).coerceIn(visible.left + margin, visible.right - margin - width) - origin[0]
        val y = (if (aboveWord) top - margin - height else bottom + margin) - origin[1]
        val templateContext = ContextThemeWrapper(context, R.style.Theme_NewsMead)
        val panel = (LayoutInflater.from(templateContext)
            .inflate(R.layout.view_word_definition_popover, null, false) as LinearLayout).apply {
            elevation = dp(8).toFloat()
            contentDescription = context.getString(R.string.definition_card_description, sourceText.substring(start, end))
        }
        panel.findViewById<TextView>(R.id.definitionWord).text = sourceText.substring(start, end)
        panel.findViewById<View>(R.id.definitionClose).setOnClickListener { clear("reader_dismissed") }
        val meaning = panel.findViewById<TextView>(R.id.definitionMeaning)
        val sourceLabel = panel.findViewById<TextView>(R.id.definitionSource)
        // A positioned FrameLayout has stable size and does not participate in article constraints.
        val wrapper = FrameLayout(context).apply { addView(panel, FrameLayout.LayoutParams(width, height)) }
        val params = ViewGroup.LayoutParams(width, height)
        host.addView(wrapper, params)
        wrapper.x = x.toFloat(); wrapper.y = y.toFloat(); wrapper.translationZ = 1100f
        pointer = View(templateContext).apply {
            val colorAttr = if (aboveWord) R.attr.primaryBackgroundColor else R.attr.cardBackgroundColor
            fun color(attr: Int): Int {
                val value = TypedValue()
                check(templateContext.theme.resolveAttribute(attr, value, true))
                return value.data
            }
            background = GradientDrawable().apply {
                setColor(color(colorAttr)); setStroke(dp(1), color(R.attr.dividerColor))
            }
            isClickable = false; isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            host.addView(this, ViewGroup.LayoutParams(dp(8), dp(8)))
            // The rotated 8 dp square occupies the existing gap, pointing toward the word.
            val center = ((left + right) / 2 - origin[0]).coerceIn(x + dp(12), x + width - dp(12))
            this.x = (center - dp(4)).toFloat()
            this.y = ((if (aboveWord) y + height else y) - dp(4)).toFloat()
            rotation = 45f; translationZ = 1099f
        }
        sourceHighlight = View(context).apply {
            // Highlight the original word without altering TextView spans or text geometry.
            background = GradientDrawable().apply {
                setColor(Color.argb(105, 255, 214, 64)); cornerRadius = dp(2).toFloat()
            }
            isClickable = false; isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            host.addView(this, ViewGroup.LayoutParams(kotlin.math.ceil(right - left).toInt(),
                kotlin.math.ceil(bottom - top).toInt()))
            this.x = (left - origin[0]).toFloat(); this.y = (top - origin[1]).toFloat()
            translationZ = 1050f
        }
        panel.tag = wrapper
        card = panel; action = value; frame = snapshot
        body.viewTreeObserver.addOnPreDrawListener(geometryListener)
        val requestToken = ++token
        onConsultationChanged(true, now(), "definition_loading")
        wrapper.post {
            if (requestToken == token && card === panel) onRender(value, true, "definition_layout_proxy", now())
        }
        if (!languageSupported) {
            meaning.text = context.getString(R.string.definition_language_unavailable)
            return
        }
        pending = repository.lookupResult(sourceText.substring(start, end)) { result ->
            if (requestToken != token || card !== panel || closed) return@lookupResult
            if (body.text.toString() != sourceText || reader.read(body) != frame) { clear("stale_dictionary_response"); return@lookupResult }
            val definition = result.definition
            val messageRes = when (result.failure) {
                DefinitionFailure.INVALID_TOKEN -> R.string.definition_token_unavailable
                DefinitionFailure.ENTRY_NOT_FOUND -> R.string.definition_entry_not_found
                DefinitionFailure.SERVICE_UNAVAILABLE -> R.string.definition_service_unavailable
                DefinitionFailure.OFFLINE_UNAVAILABLE -> R.string.definition_offline_unavailable
                else -> if (unavailableMessageRes == R.string.definition_unavailable)
                    R.string.definition_unknown_unavailable else unavailableMessageRes
            }
            meaning.text = definition?.meanings ?: context.getString(messageRes)
            sourceLabel.text = definition?.let { context.getString(R.string.definition_common_meanings, it.attribution) } ?: ""
            sourceLabel.visibility = if (definition == null) View.GONE else View.VISIBLE
        }
    }

    fun clear(reason: String) {
        token++
        pending?.cancel(); pending = null
        val previous = card ?: return
        card = null; action = null; frame = null
        sourceHighlight?.let(host::removeView)
        sourceHighlight = null
        pointer?.let(host::removeView)
        pointer = null
        if (body.viewTreeObserver.isAlive) body.viewTreeObserver.removeOnPreDrawListener(geometryListener)
        host.removeView(previous.tag as View)
        onConsultationChanged(false, now(), reason)
    }

    fun close() { clear("popup_closed"); closed = true }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e6
}
