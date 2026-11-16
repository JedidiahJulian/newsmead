package com.newsmead.fragments.article

import android.view.View
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.button.MaterialButton
import com.newsmead.R

/** Fixed icon-only control: state changes the action icon and accessible label. */
internal fun MaterialButton.bindAutoScrollState(enabled: Boolean, paused: Boolean) {
    visibility = if (enabled) View.VISIBLE else View.GONE
    text = null
    contentDescription = context.getString(
        if (paused) R.string.article_auto_scroll_resume else R.string.article_auto_scroll_pause)
    setIconResource(if (paused) R.drawable.ic_auto_scroll_play else R.drawable.ic_auto_scroll_pause)
    TooltipCompat.setTooltipText(this, contentDescription)
}
