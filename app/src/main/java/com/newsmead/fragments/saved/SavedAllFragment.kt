package com.newsmead.fragments.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.newsmead.R
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.FirebaseHelper
import com.newsmead.data.PreloadedData
import com.newsmead.databinding.FragmentSavedAllBinding
import com.newsmead.fragments.bottomnavigation.SavedFragmentDirections
import com.newsmead.models.Article
import com.newsmead.recyclerviews.feed.ArticleAdapter
import com.newsmead.recyclerviews.feed.clickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SavedAllFragment: Fragment(), clickListener {
    private lateinit var binding: FragmentSavedAllBinding
    private lateinit var data: ArrayList<Article>
    private lateinit var adapter: ArticleAdapter
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSavedAllBinding.inflate(inflater, container, false)

        // Data generation
        data = ArrayList()

        // use feed item recyclerview adapter
        adapter  = ArticleAdapter(data, this)
        binding.rvSavedAll.adapter = adapter
        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.rvSavedAll.layoutManager = layoutManager

        // Add divider
        val customDivider = CustomDividerItemDecoration(context, R.drawable.line_divider)
        binding.rvSavedAll.addItemDecoration(customDivider)

        // Grab preloaded data without mutating PreloadedData.savedArticles in-place
        val preloadedData = PreloadedData.savedArticles
        data.addAll(preloadedData.asReversed())

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        refreshSavedArticles()
    }

    private fun refreshSavedArticles() {
        val context = context ?: return
        if (com.newsmead.fragments.account.GuestLoginPrompt.isGuest()) {
            data.clear()
            adapter.notifyDataSetChanged()
            binding.tvLocalArticleNotice.setText(R.string.guest_saved_all_notice)
            binding.tvLocalArticleNotice.visibility = View.VISIBLE
            return
        }
        binding.tvLocalArticleNotice.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Update Articles Tab
                val allArticles: ArrayList<Article> = FirebaseHelper.getAllArticlesFromLists(context)

                // Update preloaded data
                PreloadedData.savedArticles = ArrayList(allArticles)

                // Reverse order (To get latest saved articles first)
                val latestFirst = allArticles.asReversed()

                withContext(Dispatchers.Main) {
                    // Update Articles Tab
                    data.clear()
                    data.addAll(latestFirst)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onItemClicked(article: Article) {
        // Action
        val action = SavedFragmentDirections.actionSavedFragmentToArticleActivityStart(
            article
        )

        // Navigate
        Navigation.findNavController(requireView()).navigate(action)
    }
}