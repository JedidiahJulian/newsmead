package com.newsmead.gaze.aid

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.newsmead.R

/** Immediate session preferences; strength is a normalized setting, not paint opacity. */
object ReadingAidControlsSheet {
    fun show(context: Context, preferences: ReaderAidPreferences,
        onChanged: (ReaderAidPreferences) -> Unit, onDismiss: () -> Unit = {}): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        var prefs = preferences
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        fun text(label: Int) = TextView(context).apply { setText(label); panel.addView(this) }
        text(R.string.reading_aids_option).textSize = 20f
        val children = mutableListOf<View>()
        var refresh: () -> Unit = {}
        fun change(next: ReaderAidPreferences) { prefs = next; refresh(); onChanged(next) }
        fun toggle(id: Int, label: Int, checked: Boolean, child: Boolean = true,
            update: (Boolean) -> ReaderAidPreferences): SwitchMaterial = SwitchMaterial(context).apply {
            this.id = id; setText(label); minHeight = dp(48); isChecked = checked
            panel.addView(this)
            if (child) children.add(this)
            setOnCheckedChangeListener { _, value -> change(update(value)) }
        }
        toggle(R.id.readingAidMenuEnabled, R.string.reading_aids_enabled, prefs.enabled, false) { prefs.copy(enabled = it) }
        toggle(R.id.readingAidMenuGuide, R.string.reading_aid_menu_guide, prefs.lineGuide) { prefs.copy(lineGuide = it) }
        val styles = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL; panel.addView(this) }
        fun radio(id: Int, label: Int) = MaterialRadioButton(context).apply {
            this.id = id; setText(label); minHeight = dp(48); styles.addView(this); children.add(this)
        }
        val line = radio(R.id.readingAidMenuLine, R.string.reading_aid_menu_line)
        val sentence = radio(R.id.readingAidMenuSentence, R.string.reading_aid_menu_sentence)
        styles.check(if (prefs.guideStyle == ReadingGuideStyle.LINE) line.id else sentence.id)
        styles.setOnCheckedChangeListener { _, id ->
            change(prefs.copy(guideStyle = if (id == line.id) ReadingGuideStyle.LINE else ReadingGuideStyle.SENTENCE))
        }
        text(R.string.reading_aid_menu_sentence_help)
        val opacity = text(R.string.reading_aid_menu_opacity)
        toggle(R.id.readingAidMenuBookmark, R.string.reading_aid_menu_bookmark, prefs.lineStartCue) { prefs.copy(lineStartCue = it) }
        toggle(R.id.readingAidMenuDefinitions, R.string.reading_aid_menu_definitions, prefs.wordDefinition) { prefs.copy(wordDefinition = it) }
        toggle(R.id.readingAidMenuFocus, R.string.reading_aid_menu_focus, prefs.focusRegion) { prefs.copy(focusRegion = it) }
        text(R.string.reading_aid_menu_focus_appearance)
        val focusStyles = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL; panel.addView(this) }
        fun focusRadio(id: Int, label: Int) = MaterialRadioButton(context).apply {
            this.id = id; setText(label); minHeight = dp(48); focusStyles.addView(this)
        }
        val fadeText = focusRadio(R.id.readingAidMenuFocusFade, R.string.reading_aid_menu_focus_fade)
        val darkSurroundings = focusRadio(R.id.readingAidMenuFocusDark, R.string.reading_aid_menu_focus_dark)
        focusStyles.check(if (prefs.focusStyle == ReadingFocusStyle.FADE_TEXT) fadeText.id else darkSurroundings.id)
        focusStyles.setOnCheckedChangeListener { _, id ->
            change(prefs.copy(focusStyle = if (id == fadeText.id) ReadingFocusStyle.FADE_TEXT else ReadingFocusStyle.DARK_SURROUNDINGS))
        }
        val advanced = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        MaterialButton(context).apply {
            id = R.id.readingAidMenuAdvanced; setText(R.string.reading_aid_menu_advanced); minHeight = dp(48)
            panel.addView(this)
            setOnClickListener { advanced.visibility = if (advanced.visibility == View.GONE) View.VISIBLE else View.GONE }
        }
        // Add advanced controls with the same construction helpers, then move them together.
        val advancedStart = panel.childCount
        val adaptive = toggle(R.id.readingAidMenuAdaptive, R.string.reading_aids_adaptive, prefs.adaptiveStrength) { prefs.copy(adaptiveStrength = it) }
        text(R.string.reading_aid_menu_strength_help)
        fun slider(id: Int, label: Int, initial: Double, update: (Double) -> ReaderAidPreferences): SeekBar {
            val title = text(label)
            title.labelFor = id
            return SeekBar(context).apply {
                this.id = id; max = 100; progress = (initial * 100).toInt(); minimumHeight = dp(48)
                panel.addView(this); children.add(this)
                fun caption(value: Int) {
                    title.text = context.getString(label, value)
                    contentDescription = title.text
                }
                caption(progress)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        caption(value); if (fromUser) change(update(value / 100.0))
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            }
        }
        slider(R.id.readingAidMenuCap, R.string.reading_aid_menu_cap, prefs.maxStrength) { prefs.copy(maxStrength = it) }
        val manual = slider(R.id.readingAidMenuStrength, R.string.reading_aid_menu_strength, prefs.manualStrength) { prefs.copy(manualStrength = it) }
        while (panel.childCount > advancedStart) {
            val child = panel.getChildAt(advancedStart); panel.removeView(child); advanced.addView(child)
        }
        panel.addView(advanced)
        refresh = {
            val strength = minOf(prefs.maxStrength, if (prefs.adaptiveStrength) 0.25 else prefs.manualStrength)
            val alpha = if (prefs.enabled && prefs.lineGuide) (80 * strength).toInt() else 0
            opacity.text = context.getString(R.string.reading_aid_menu_opacity, alpha / 255.0 * 100)
            children.forEach { it.isEnabled = prefs.enabled }
            line.isEnabled = prefs.enabled && prefs.lineGuide
            sentence.isEnabled = prefs.enabled && prefs.lineGuide
            focusStyles.isEnabled = prefs.enabled && prefs.focusRegion
            fadeText.isEnabled = prefs.enabled && prefs.focusRegion
            darkSurroundings.isEnabled = prefs.enabled && prefs.focusRegion
            manual.isEnabled = prefs.enabled && !prefs.adaptiveStrength
            adaptive.isEnabled = prefs.enabled
        }
        refresh()
        MaterialButton(context).apply {
            id = R.id.readingAidMenuDone; setText(R.string.reading_aid_menu_done); minHeight = dp(48)
            panel.addView(this); setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(ScrollView(context).apply { addView(panel) })
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        return dialog
    }
}
