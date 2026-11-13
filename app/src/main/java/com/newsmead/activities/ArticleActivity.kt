package com.newsmead.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navArgs
import com.newsmead.R

import com.newsmead.databinding.ActivityArticleBinding
import com.newsmead.models.Article

class ArticleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val experimentalThreads = com.newsmead.gaze.mgazenet.SameModelExecutionTrial.requested(this, intent.extras)
        val experimentalLocalizer = com.newsmead.gaze.mgazenet.SameModelExecutionTrial.requestedLocalizer(this, intent.extras)
        val experimentalBackend = com.newsmead.gaze.mgazenet.SameModelExecutionTrial.requestedBackend(this, intent.extras)
        super.onCreate(savedInstanceState)

        // Initialize viewBinding for ArticleActivity
        val binding = ActivityArticleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("ArticleActivity", "onCreate: ArticleActivity started")

        val article = intent.getParcelableExtra<Article>("articleItem")

        Log.d("ArticleActivity", "onCreate: articleId: ${article?.newsId}")

        // Update fragment with articleId
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.flMainArticleContainer) as NavHostFragment
        val navController = navHostFragment.navController

        // Clear fragment backstack (this is because ArticleActivity starts with a fragment)
        // due to nav_article_graph.xml having a startDestination.
        navController.popBackStack()

        // Create bundle to pass article parcelable to article fragment
        val bundle = Bundle()
        bundle.putParcelable("article", article)
        experimentalThreads?.let {
            bundle.putBoolean(com.newsmead.gaze.mgazenet.SameModelExecutionTrial.OPT_IN, true)
            bundle.putInt(com.newsmead.gaze.mgazenet.SameModelExecutionTrial.THREADS, it)
        }
        experimentalLocalizer?.let { bundle.putString(com.newsmead.gaze.mgazenet.SameModelExecutionTrial.LOCALIZER, it) }
        experimentalBackend?.let { bundle.putString(com.newsmead.gaze.mgazenet.SameModelExecutionTrial.BACKEND, it) }
        bundle.putBoolean(com.newsmead.gaze.SharedReadingBaseline.ARGUMENT,
            intent.getBooleanExtra(com.newsmead.gaze.SharedReadingBaseline.ARGUMENT, false))

        Log.d("ArticleActivity", "onCreate: Bundle Contents: $bundle")

        // Navigate to article fragment using bundle
        Log.d("ArticleActivity", "onCreate: Navigating to article fragment")
        navController.navigate(R.id.articleFragment, bundle)
    }
}
