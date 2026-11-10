package com.newsmead.fragments.saved

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.newsmead.R
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.StudyArticleCatalog
import com.newsmead.data.StudyConfig
import com.newsmead.databinding.FragmentSavedAllBinding
import com.newsmead.fragments.bottomnavigation.SavedFragmentDirections
import com.newsmead.models.Article
import com.newsmead.recyclerviews.feed.ArticleAdapter
import com.newsmead.recyclerviews.feed.clickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Packaged reading material, independent of login, Firestore and the live feed. */
class StudyArticlesFragment : Fragment(), clickListener {
    private var binding: FragmentSavedAllBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val viewBinding = FragmentSavedAllBinding.inflate(inflater, container, false)
        binding = viewBinding
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = requireNotNull(binding)
        val adapter = ArticleAdapter(arrayListOf(), this)
        viewBinding.rvSavedAll.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.rvSavedAll.adapter = adapter
        viewBinding.rvSavedAll.addItemDecoration(
            CustomDividerItemDecoration(requireContext(), R.drawable.line_divider))
        viewBinding.tvLocalArticleNotice.visibility = View.VISIBLE
        viewBinding.tvLocalArticleNotice.setText(R.string.saved_testing_samples_notice)

        val assets = requireContext().applicationContext.assets
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    assets.open(StudyConfig.ARTICLES_ASSET).bufferedReader().use {
                        StudyArticleCatalog.parse(it.readText())
                    }
                }
            }
            if (binding !== viewBinding) return@launch
            result.onSuccess { articles ->
                adapter.updateData(articles)
                if (articles.isEmpty()) viewBinding.tvLocalArticleNotice.setText(R.string.saved_testing_empty)
            }.onFailure { error ->
                Log.w("StudyArticlesFragment", "Unable to load packaged articles", error)
                viewBinding.tvLocalArticleNotice.setText(R.string.saved_testing_load_error)
            }
        }
    }

    override fun onItemClicked(article: Article) {
        requireView().findNavController().navigate(
            SavedFragmentDirections.actionSavedFragmentToArticleActivityStart(article))
    }

    override fun onDestroyView() {
        binding?.rvSavedAll?.adapter = null
        binding = null
        super.onDestroyView()
    }
}
