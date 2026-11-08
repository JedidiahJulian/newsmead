package com.newsmead.research.study

import com.google.android.material.appbar.AppBarLayout
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.gaze.SharedReadingBaseline

/** Keep the real shared reading viewport fixed without disabling passage scrolling.
 * Only the toolbar's collapse flags change; aid docks and genuine layout changes remain visible.
 */
object SharedStudyToolbar {
    /** The caller resolves the active reference with participant and calibration matching. */
    fun forLinkedArticle(binding: FragmentArticleBinding, reference: SharedReadingBaseline.Entry?): Boolean =
        if (reference != null) holdExpanded(binding) else false

    /** Returns true when a layout must settle before starting capture. Apply on every new view,
     * before hierarchy restoration; the forced expansion overrides a restored collapsed offset.
     */
    fun holdExpanded(binding: FragmentArticleBinding): Boolean {
        val params = binding.clArticleTopBar.layoutParams as AppBarLayout.LayoutParams
        if (params.scrollFlags == 0) return false
        params.scrollFlags = 0
        binding.clArticleTopBar.layoutParams = params
        binding.articleHeaderAppBar.setExpanded(true, false)
        return true
    }
}
