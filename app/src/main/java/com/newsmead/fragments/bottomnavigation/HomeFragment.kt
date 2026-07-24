package com.newsmead.fragments.bottomnavigation

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.newsmead.R
import com.newsmead.activities.GazeCalibrationActivity
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.DataHelper
import com.newsmead.data.FirebaseHelper
import com.newsmead.databinding.FragmentHomeBinding
import com.newsmead.models.Article
import com.newsmead.recyclerviews.feed.ArticleAdapter
import com.newsmead.recyclerviews.feed.clickListener
class HomeFragment : Fragment(), clickListener {

    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var viewBinding: FragmentHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        this.viewBinding = FragmentHomeBinding.inflate(inflater, container, false)
        return this.viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start shimmer
        viewBinding.shimmerFeed.startShimmer()

        // Set up Feed RecyclerView
        this.articleAdapter = ArticleAdapter(arrayListOf(), this)

        // Load data and update the adapter when available
        DataHelper.loadArticleData(context) { articles ->
            if (FirebaseHelper.isNetworkAvailable(requireContext())) {
                // Stop the shimmer
                viewBinding.shimmerFeed.stopShimmer()
                viewBinding.shimmerFeed.visibility = View.GONE
                // Update the adapter with the retrieved articles
                articleAdapter.updateData(articles)
            }
        }

        this.viewBinding.rvFeed.adapter = articleAdapter
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.orientation = LinearLayoutManager.VERTICAL
        this.viewBinding.rvFeed.layoutManager = linearLayoutManager

        // Divider
        val customDivider = CustomDividerItemDecoration(context, R.drawable.line_divider)
        this.viewBinding.rvFeed.addItemDecoration(customDivider)

        // History Button
        this.viewBinding.btnHistory.setOnClickListener {
            // Action
            val action = HomeFragmentDirections.actionHomeFragmentToHistoryFragment()

            // Navigate
            Navigation.findNavController(requireView()).navigate(action)
        }

        // Gaze calibration button - launches the 16-point calibration screen
        this.viewBinding.btnGazeCalibration.setOnClickListener {
            startActivity(Intent(requireContext(), GazeCalibrationActivity::class.java))
        }
    }

    override fun onItemClicked(article: Article) {
        // Action
        val action = HomeFragmentDirections.actionHomeFragmentToArticleActivityStart(
            article
        )

        // Navigate
        Navigation.findNavController(requireView()).navigate(action)
    }
}