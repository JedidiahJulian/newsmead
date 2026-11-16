package com.newsmead.gaze.aid

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.newsmead.R

/** Immediate session preferences; strength is a normalized setting, not paint opacity. */
object ReadingAidControlsSheet {
    fun show(
        context: Context,
        preferences: ReaderAidPreferences,
        onChanged: (ReaderAidPreferences) -> Unit,
        onDismiss: () -> Unit = {},
        supportsAutoScroll: Boolean = false,
        autoScrollStatusLabel: String? = null,
        supportsHideGazeCursor: Boolean = false,
    ): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        val root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_dialog_reading_aids, null, false)
        var prefs = preferences

        val regularFont = ResourcesCompat.getFont(context, R.font.arial_regular)
        val boldFont = ResourcesCompat.getFont(context, R.font.arial_bold)

        val modeGroup = root.findViewById<RadioGroup>(R.id.readingAidModeGroup)
        val modeOff = root.findViewById<MaterialRadioButton>(R.id.readingAidModeOff)
        val modeAuto = root.findViewById<MaterialRadioButton>(R.id.readingAidModeAuto)
        val modeOn = root.findViewById<MaterialRadioButton>(R.id.readingAidModeOn)

        val guideRow = root.findViewById<LinearLayout>(R.id.readingAidGuideRow)
        val guideSubtitle = root.findViewById<TextView>(R.id.readingAidGuideSubtitle)
        val guideSwitch = root.findViewById<SwitchMaterial>(R.id.readingAidMenuGuide)
        val guideOptions = root.findViewById<LinearLayout>(R.id.readingAidGuideOptions)
        val guideStyleGroup = root.findViewById<RadioGroup>(R.id.readingAidGuideStyleGroup)
        val threeLine = root.findViewById<MaterialRadioButton>(R.id.readingAidMenuThreeLine)
        val line = root.findViewById<MaterialRadioButton>(R.id.readingAidMenuLine)
        val sentence = root.findViewById<MaterialRadioButton>(R.id.readingAidMenuSentence)
        val guideHelp = root.findViewById<TextView>(R.id.readingAidGuideHelp)

        val bookmarkSwitch = root.findViewById<SwitchMaterial>(R.id.readingAidMenuBookmark)

        val focusRow = root.findViewById<LinearLayout>(R.id.readingAidFocusRow)
        val focusSubtitle = root.findViewById<TextView>(R.id.readingAidFocusSubtitle)
        val focusSwitch = root.findViewById<SwitchMaterial>(R.id.readingAidMenuFocus)
        val focusOptions = root.findViewById<LinearLayout>(R.id.readingAidFocusOptions)
        val focusStyleGroup = root.findViewById<RadioGroup>(R.id.readingAidFocusStyleGroup)
        val fadeText = root.findViewById<MaterialRadioButton>(R.id.readingAidMenuFocusFade)
        val darkSurroundings = root.findViewById<MaterialRadioButton>(R.id.readingAidMenuFocusDark)

        val definitionsSwitch = root.findViewById<SwitchMaterial>(R.id.readingAidMenuDefinitions)

        val controlsHeader = root.findViewById<TextView>(R.id.readingAidControlsHeader)
        val controlsCard = root.findViewById<LinearLayout>(R.id.readingAidControlsCard)
        val autoScrollRow = root.findViewById<LinearLayout>(R.id.readingAidAutoScrollRow)
        val autoScrollSubtitle = root.findViewById<TextView>(R.id.readingAidAutoScrollSubtitle)
        val autoScrollSwitch = root.findViewById<SwitchMaterial>(R.id.readingAidMenuAutoScroll)
        val controlsDivider = root.findViewById<View>(R.id.readingAidControlsDivider)
        val cursorRow = root.findViewById<LinearLayout>(R.id.readingAidCursorRow)
        val cursorSwitch = root.findViewById<SwitchMaterial>(R.id.readingAidMenuHideGazeCursor)

        val advancedRow = root.findViewById<LinearLayout>(R.id.readingAidMenuAdvanced)
        val advancedChevron = root.findViewById<ImageView>(R.id.readingAidAdvancedChevron)
        val advancedSection = root.findViewById<LinearLayout>(R.id.readingAidAdvancedSection)
        val adaptiveSwitch = root.findViewById<SwitchMaterial>(R.id.readingAidMenuAdaptive)
        val opacityInfo = root.findViewById<TextView>(R.id.readingAidOpacityInfo)
        val capLabel = root.findViewById<TextView>(R.id.readingAidCapLabel)
        val capSeek = root.findViewById<SeekBar>(R.id.readingAidMenuCap)
        val strengthLabel = root.findViewById<TextView>(R.id.readingAidStrengthLabel)
        val strengthSeek = root.findViewById<SeekBar>(R.id.readingAidMenuStrength)

        if (!supportsAutoScroll) controlsCard.removeView(autoScrollRow)
        if (!supportsHideGazeCursor) controlsCard.removeView(cursorRow)
        if (!supportsAutoScroll || !supportsHideGazeCursor) controlsCard.removeView(controlsDivider)
        if (!supportsAutoScroll && !supportsHideGazeCursor) {
            controlsHeader.visibility = View.GONE
            controlsCard.visibility = View.GONE
        }

        val aidDependentViews = buildList<View> {
            addAll(listOf(guideSwitch, line, threeLine, sentence, bookmarkSwitch, definitionsSwitch, focusSwitch,
                adaptiveSwitch, capSeek, strengthSeek))
            if (supportsAutoScroll) add(autoScrollSwitch)
        }

        fun updateSliderCaptions() {
            capLabel.text = context.getString(R.string.reading_aid_menu_cap, capSeek.progress)
            capSeek.contentDescription = capLabel.text
            strengthLabel.text = context.getString(R.string.reading_aid_menu_strength, strengthSeek.progress)
            strengthSeek.contentDescription = strengthLabel.text
        }

        fun refresh() {
            val modeAllowsAids = prefs.mode != ReadingAidsMode.OFF
            modeOff.typeface = if (prefs.mode == ReadingAidsMode.OFF) boldFont else regularFont
            modeAuto.typeface = if (prefs.mode == ReadingAidsMode.AUTO) boldFont else regularFont
            modeOn.typeface = if (prefs.mode == ReadingAidsMode.ON) boldFont else regularFont

            val guideStyleLabel = context.getString(when (prefs.guideStyle) {
                ReadingGuideStyle.THREE_LINE -> R.string.reading_aid_menu_three_line
                ReadingGuideStyle.LINE -> R.string.reading_aid_menu_line
                ReadingGuideStyle.SENTENCE -> R.string.reading_aid_menu_sentence
            })
            guideSubtitle.text = context.getString(R.string.reading_aid_menu_customize_format, guideStyleLabel)
            guideHelp.setText(when (prefs.guideStyle) {
                ReadingGuideStyle.THREE_LINE -> R.string.reading_aid_menu_three_line_help
                ReadingGuideStyle.SENTENCE -> R.string.reading_aid_menu_sentence_help
                ReadingGuideStyle.LINE -> R.string.reading_aid_menu_line_help
            })

            val focusStyleLabel = context.getString(if (prefs.focusStyle == ReadingFocusStyle.FADE_TEXT)
                R.string.reading_aid_menu_focus_fade else R.string.reading_aid_menu_focus_dark)
            focusSubtitle.text = context.getString(R.string.reading_aid_menu_customize_format, focusStyleLabel)

            val limit = if (modeAllowsAids && prefs.lineGuide)
                .19 * minOf(prefs.maxStrength, if (prefs.adaptiveStrength) 1.0 else prefs.manualStrength) else 0.0
            opacityInfo.text = if (prefs.guideStyle == ReadingGuideStyle.THREE_LINE) {
                val center = minOf(ReadingLineGuideAppearance.THREE_CENTER_OPACITY, limit)
                context.getString(R.string.reading_aid_menu_three_line_opacity, center * 100)
            } else {
                context.getString(R.string.reading_aid_menu_guide_targets,
                    minOf(.08, limit) * 100, minOf(.13, limit) * 100, minOf(.19, limit) * 100)
            }

            if (supportsAutoScroll) {
                if (prefs.autoScroll && !autoScrollStatusLabel.isNullOrBlank()) {
                    autoScrollSubtitle.text = autoScrollStatusLabel
                    autoScrollSubtitle.visibility = View.VISIBLE
                } else {
                    autoScrollSubtitle.visibility = View.GONE
                }
            }

            aidDependentViews.forEach { it.isEnabled = modeAllowsAids }
            guideRow.alpha = if (modeAllowsAids) 1f else 0.5f
            focusRow.alpha = if (modeAllowsAids) 1f else 0.5f
            val guideStylesEnabled = modeAllowsAids && prefs.lineGuide
            line.isEnabled = guideStylesEnabled
            threeLine.isEnabled = guideStylesEnabled
            sentence.isEnabled = guideStylesEnabled
            guideStyleGroup.alpha = if (guideStylesEnabled) 1f else 0.5f

            val focusStylesEnabled = modeAllowsAids && prefs.focusRegion
            focusStyleGroup.isEnabled = focusStylesEnabled
            fadeText.isEnabled = focusStylesEnabled
            darkSurroundings.isEnabled = focusStylesEnabled
            focusStyleGroup.alpha = if (focusStylesEnabled) 1f else 0.5f

            adaptiveSwitch.isEnabled = modeAllowsAids
            strengthSeek.isEnabled = modeAllowsAids && !prefs.adaptiveStrength
        }

        fun change(next: ReaderAidPreferences) {
            prefs = next
            refresh()
            onChanged(next)
        }

        modeGroup.check(when (prefs.mode) {
            ReadingAidsMode.OFF -> modeOff.id
            ReadingAidsMode.AUTO -> modeAuto.id
            ReadingAidsMode.ON -> modeOn.id
        })
        modeGroup.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                modeOff.id -> ReadingAidsMode.OFF
                modeAuto.id -> ReadingAidsMode.AUTO
                modeOn.id -> ReadingAidsMode.ON
                else -> return@setOnCheckedChangeListener
            }
            change(prefs.copy(mode = mode))
        }

        guideSwitch.isChecked = prefs.lineGuide
        guideSwitch.setOnCheckedChangeListener { _, checked -> change(prefs.copy(lineGuide = checked)) }
        guideRow.setOnClickListener {
            guideOptions.visibility = if (guideOptions.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        guideStyleGroup.check(when (prefs.guideStyle) {
            ReadingGuideStyle.LINE -> line.id
            ReadingGuideStyle.THREE_LINE -> threeLine.id
            ReadingGuideStyle.SENTENCE -> sentence.id
        })
        guideStyleGroup.setOnCheckedChangeListener { _, id ->
            val style = when (id) {
                line.id -> ReadingGuideStyle.LINE
                threeLine.id -> ReadingGuideStyle.THREE_LINE
                sentence.id -> ReadingGuideStyle.SENTENCE
                else -> return@setOnCheckedChangeListener
            }
            change(prefs.copy(guideStyle = style))
        }

        bookmarkSwitch.isChecked = prefs.lineStartCue
        bookmarkSwitch.setOnCheckedChangeListener { _, checked -> change(prefs.copy(lineStartCue = checked)) }

        focusSwitch.isChecked = prefs.focusRegion
        focusSwitch.setOnCheckedChangeListener { _, checked -> change(prefs.copy(focusRegion = checked)) }
        focusRow.setOnClickListener {
            focusOptions.visibility = if (focusOptions.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        focusStyleGroup.check(if (prefs.focusStyle == ReadingFocusStyle.FADE_TEXT) fadeText.id else darkSurroundings.id)
        focusStyleGroup.setOnCheckedChangeListener { _, id ->
            val style = if (id == fadeText.id) ReadingFocusStyle.FADE_TEXT else ReadingFocusStyle.DARK_SURROUNDINGS
            change(prefs.copy(focusStyle = style))
        }

        definitionsSwitch.isChecked = prefs.wordDefinition
        definitionsSwitch.setOnCheckedChangeListener { _, checked -> change(prefs.copy(wordDefinition = checked)) }

        if (supportsAutoScroll) {
            autoScrollSwitch.isChecked = prefs.autoScroll
            autoScrollSwitch.setOnCheckedChangeListener { _, checked -> change(prefs.copy(autoScroll = checked)) }
        }

        if (supportsHideGazeCursor) {
            cursorSwitch.isChecked = !prefs.hideGazeCursor
            cursorSwitch.setOnCheckedChangeListener { _, checked -> change(prefs.copy(hideGazeCursor = !checked)) }
        }

        advancedRow.setOnClickListener {
            val expand = advancedSection.visibility == View.GONE
            advancedSection.visibility = if (expand) View.VISIBLE else View.GONE
            advancedChevron.rotation = if (expand) 90f else 0f
        }

        adaptiveSwitch.isChecked = prefs.adaptiveStrength
        adaptiveSwitch.setOnCheckedChangeListener { _, checked -> change(prefs.copy(adaptiveStrength = checked)) }

        capSeek.progress = (prefs.maxStrength * 100).toInt()
        strengthSeek.progress = (prefs.manualStrength * 100).toInt()
        updateSliderCaptions()
        capSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                updateSliderCaptions()
                if (fromUser) change(prefs.copy(maxStrength = value / 100.0))
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        strengthSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                updateSliderCaptions()
                if (fromUser) change(prefs.copy(manualStrength = value / 100.0))
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        root.findViewById<View>(R.id.readingAidMenuClose).setOnClickListener { dialog.dismiss() }
        root.findViewById<MaterialButton>(R.id.readingAidMenuDone).setOnClickListener { dialog.dismiss() }

        refresh()
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                BottomSheetBehavior.from(sheet).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        return dialog
    }
}
