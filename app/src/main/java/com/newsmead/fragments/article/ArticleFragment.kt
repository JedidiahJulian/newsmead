package com.newsmead.fragments.article

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Toast
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.newsmead.R
import com.newsmead.activities.GazeTestActivity
import com.newsmead.activities.ReadingValidationActivity
import com.newsmead.activities.ScanpathReplayActivity
import com.newsmead.activities.ScanpathPilotActivity
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.DataHelper
import com.newsmead.data.DatabaseHelper
import com.newsmead.data.FirebaseHelper
import com.newsmead.data.StudyConfig
import com.newsmead.gaze.AdaptiveScaffoldController
import com.newsmead.gaze.GazeOverlayView
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.GazeTargetStabilizer
import com.newsmead.gaze.LineAoiMapper
import com.newsmead.gaze.ReadingStateInferencer
import com.newsmead.gaze.ReadingMeasurementLog
import com.newsmead.gaze.ScaffoldLevel
import com.newsmead.gaze.ScaffoldUpdate
import com.newsmead.gaze.TextTarget
import com.newsmead.gaze.WindowedStabilityEstimator
import com.newsmead.gaze.scanpath.ArticleScanpathSession
import com.newsmead.gaze.scanpath.ScanpathEngine
import com.newsmead.gaze.aid.ArticleReadingAidRuntime
import com.newsmead.gaze.aid.ReadingAidOverlayView
import com.newsmead.gaze.aid.ReaderAidPreferences
import com.newsmead.gaze.aid.ReadingAidDefinitionPopup
import com.newsmead.gaze.aid.ReadingAidPolicyConfig
import com.newsmead.gaze.aid.demo.ArticleReadingAidDemoPresenter
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.fragments.layouts.BottomSheetDialogSaveFragment
import com.newsmead.models.Article
import com.newsmead.models.SavedList
import com.newsmead.recyclerviews.feed.ArticleSimplifiedAdapter
import com.newsmead.recyclerviews.feed.clickListener
import kotlinx.coroutines.launch
import java.util.Locale


class ArticleFragment() : Fragment(), clickListener, TextToSpeech.OnInitListener {
    private lateinit var binding: FragmentArticleBinding
    private lateinit var adapter: ArticleSimplifiedAdapter
    private lateinit var savedLists: ArrayList<SavedList>
    private lateinit var textToSpeech: TextToSpeech
    private var isTranslated = false
    private var language = "english"
    private var gazeProvider: GazeProvider? = null
    private var articleGazeSink: GazeProvider.OnGaze? = null
    private var gazeOverlay: GazeOverlayView? = null
    private var launchedCalibration = false
    private var resumeGazeAfterStop = false
    private var rsiInferencer: ReadingStateInferencer? = null
    private var targetStabilizer: GazeTargetStabilizer? = null
    private var stabilityEstimator: WindowedStabilityEstimator? = null
    private var scaffoldController: AdaptiveScaffoldController? = null
    private var currentTextTarget = TextTarget.INVALID
    private var lastStabilityLogMs = 0L
    private var lastAoiLogMs = 0L
    private var readingMeasurement: ReadingMeasurementLog? = null
    private var scanpathSession: ArticleScanpathSession? = null
    private var readingAidRuntime: ArticleReadingAidRuntime? = null
    private var readingAidOverlay: ReadingAidOverlayView? = null
    private var definitionPopup: ReadingAidDefinitionPopup? = null
    private var articleDemo: ArticleReadingAidDemoPresenter? = null
    private var demoCameraSuppressed = false
    private var articleBodyReady = false
    private var demoStartToken = 0
    private var aidPreferences = ReaderAidPreferences(wordMagnifier = false)
    private val scanpathAidsEnabled: Boolean
        get() = StudyConfig.SCANPATH_READING_AIDS && !StudyConfig.GAZE_TOUCH_VALIDATION
    private val scanpathCounts = mutableMapOf<String, Int>()
    private val scanpathGeometryListener = ViewTreeObserver.OnPreDrawListener {
        scanpathSession?.checkGeometry()
        readingAidRuntime?.tick()
        true
    }
    private val measurementGeometryListener = ViewTreeObserver.OnPreDrawListener {
        readingMeasurement?.checkGeometry()
        true
    }
    private val scaffoldDemoHandler = Handler(Looper.getMainLooper())
    private var scaffoldDemoStep = 0
    private enum class ColorMode { LIGHT, DARK, SEPIA }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ArticleFragment", "onCreateView: ArticleFragment started")
        binding = FragmentArticleBinding.inflate(inflater, container, false)
        definitionPopup = ReadingAidDefinitionPopup(requireContext(), binding.root as ViewGroup, binding.tvArticleText)
        binding.articleAidDemoPrevious.setOnClickListener { articleDemo?.step(-1) }
        binding.articleAidDemoNext.setOnClickListener { articleDemo?.step(1) }
        binding.articleAidDemoStop.setOnClickListener { stopArticleDemo("reader_stop") }

        // Disable the Read Aloud button until the TextToSpeech engine is initialized
        binding.btnReadAloudArticle.isEnabled = false
        binding.btnReadAloudArticle.isClickable = false

        // Initialize TextToSpeech with Filipino language
        textToSpeech = TextToSpeech(requireContext(), this, "com.google.android.tts")

        // Initialize RecyclerView
        adapter = ArticleSimplifiedAdapter(arrayListOf(), this)
        binding.rvArticleRecommended.adapter = adapter

        // Set layout manager
        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.rvArticleRecommended.layoutManager = layoutManager

        // Add divider
        val customDivider = CustomDividerItemDecoration(context, R.drawable.line_divider)
        binding.rvArticleRecommended.addItemDecoration(customDivider)

        // Receive parcelable from ArticleActivity
        var article = arguments?.getParcelable<Article>("article") ?: Article(
            "Article Source",
            "Article Source Image",
            "Article Title",
            "",
            "Article Date",
            "Article Body",
            "Article Category",
            "Article Language",
            "Article Read Time",
            "url",
            "0"
        )

        // If article is null from ArticleActivity, then receive from it's own arguments
        if (article.title == "Article Title") {
            val args = ArticleFragmentArgs.fromBundle(requireArguments())
            article = args.articleItem
        }

        // Safeargs bundle
        Log.d("ArticleFragment", "onCreateView: newsId: ${article.newsId}")
        Log.d("ArticleFragment", "onCreateView: title: ${article.title}")
        Log.d("ArticleFragment", "onCreateView: date: ${article.date}")
        Log.d("ArticleFragment", "onCreateView: source: ${article.source}")
        Log.d("ArticleFragment", "onCreateView: sourceImage: ${article.sourceImage}")
        Log.d("ArticleFragment", "onCreateView: imageURL: ${article.imageURL}")
        Log.d("ArticleFragment", "onCreateView: readTime: ${article.readTime}")
        Log.d("ArticleFragment", "onCreateView: url: ${article.url}")


        // Add to history
        FirebaseHelper.addArticleToHistory(requireContext(), article)

        // Set article title
        binding.tvArticleHeadline.text = article.title

        // Set article category in title case
        binding.tvCategory.text = article.category.replaceFirstChar { it.uppercase() }

        // Set article source
        binding.tvSource.text = article.source
        binding.btnArticleRecommendations.text = "Show more from " + article.source
        val context = binding.root.context
        val resourceId = context.resources.getIdentifier(article.sourceImage, "drawable", context.packageName)
        binding.ivSourceImage.setImageResource(if (resourceId != 0) resourceId else R.drawable.sample_source_image)

        // Set article read time
        val readTime = article.readTime //+ " min read"
        binding.tvArticleMinRead.text = readTime

        // Set language
        language = article.language
        updateDefinitionLanguage()
        if(language.lowercase() == "english"){
            binding.btnTranslateArticle.text = "Filipino"
        }
        else{
            binding.btnTranslateArticle.text = "English"
        }

        // Read Aloud button
        binding.btnReadAloudArticle.setOnClickListener {
            if (readingMeasurement != null) return@setOnClickListener
            binding.btnReadAloudArticle.isEnabled = false
            binding.btnReadAloudArticle.isClickable = false
            if (textToSpeech.isSpeaking && binding.btnReadAloudArticle.text == "Stop") {
                binding.btnReadAloudArticle.text = "Read Aloud"
                textToSpeech.stop()
            } else {
                var body = binding.tvArticleText.text.toString()
                Log.d("ArticleFragment", "tts-body: ${body.substring(0, 100)}")
                if (body.isNotEmpty()) speak(body)
                else Toast.makeText(context, "No article to read", Toast.LENGTH_SHORT).show()
            }
            binding.btnReadAloudArticle.isEnabled = true
            binding.btnReadAloudArticle.isClickable = true
        }

        // If language is not english, hide the translate button
        if(language.lowercase() != "english"){
            binding.btnTranslateArticle.visibility = View.GONE
        }

        // Translate button
        binding.btnTranslateArticle.setOnClickListener {
            if (textToSpeech.isSpeaking) {
                binding.btnReadAloudArticle.isEnabled = false
                binding.btnReadAloudArticle.isClickable = false
                binding.btnReadAloudArticle.text = "Read Aloud"
                textToSpeech.stop()
                binding.btnReadAloudArticle.isEnabled = true
                binding.btnReadAloudArticle.isClickable = true
            }

            binding.btnTranslateArticle.isEnabled = false
            binding.btnTranslateArticle.isClickable = false
            binding.btnTranslateArticle.text = "Translating..."
            if (!isTranslated) {
                // Translate article
                DataHelper.translateArticle(article.newsId, binding.switchUseGoogle.isChecked, requireContext()) { title, body ->
                    if(title.isNotEmpty() && body.isNotEmpty()) {
                        stopArticleDemo("article_translation_changed")
                        binding.tvArticleHeadline.text = title
                        binding.tvArticleText.text = body
                        binding.btnTranslateArticle.text = "English"
                        isTranslated = true
                        updateDefinitionLanguage()
                    }
                    else{
                        Toast.makeText(context, "Translation unavailable", Toast.LENGTH_SHORT).show()
                        binding.btnTranslateArticle.text = "Filipino"
                    }
                    binding.btnTranslateArticle.isEnabled = true
                    binding.btnTranslateArticle.isClickable = true
                }
            } else {
                // Revert to original language
                stopArticleDemo("article_translation_changed")
                binding.tvArticleHeadline.text = article.title
                binding.tvArticleText.text = article.body
                binding.btnTranslateArticle.text = "Filipino"
                isTranslated = false
                updateDefinitionLanguage()
                binding.btnTranslateArticle.isEnabled = true
                binding.btnTranslateArticle.isClickable = true
            }
        }

        // When the user zooms in on the ZoomImageView, disable the NestedScrollView scrolling
        binding.ivArticleFullImage.setOnTouchListener { _, event ->
            when {
                event.action == MotionEvent.ACTION_UP -> {
                    // Enable scrolling on the NestedScrollView when the user lifts their finger
                    binding.nsvArticleText.setScrollingEnabled(true)
                }
                event.pointerCount > 1 -> {
                    // Disable scrolling on the NestedScrollView if there are at least two fingers touching the image
                    binding.nsvArticleText.setScrollingEnabled(false)
                }
            }
            false // Return false so the event is not consumed and continues to be propagated
        }

        // Bottom sheet dialog for saving articles
        binding.btnSaveList.setOnClickListener {
            binding.btnSaveList.isEnabled = false
            binding.btnSaveList.isClickable = false
            val bodyContent = binding.tvArticleText.text.toString()
            val bottomSheetDialogFragment = BottomSheetDialogSaveFragment(article, bodyContent)
            bottomSheetDialogFragment.show(requireActivity().supportFragmentManager, "save")
            // Re-enable the button after a 1 second to prevent multiple rapid clicks
            Handler(Looper.getMainLooper()).postDelayed({
                binding.btnSaveList.isEnabled = true
                binding.btnSaveList.isClickable = true
            }, 1000)
        }

        // Back button to go back to previous fragment
        binding.btnArticleBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnRunGazeTest.setOnClickListener {
            showGazeDiagnosticsMenu()
        }

        // Show more button to show more articles from source
        binding.btnArticleRecommendations.setOnClickListener {
            // Navigate to ArticleSourceFragment
            val action = ArticleFragmentDirections.actionArticleFragmentToArticleSourceFragment(
                article.source
            )

            Navigation.findNavController(binding.root).navigate(action)
        }

        // Share button to share article
        binding.btnArticleShare.setOnClickListener {
            binding.btnArticleShare.isEnabled = false
            binding.btnArticleShare.isClickable = false
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TITLE, "NewsMead")

                val articleTitle = article.title
                val articleURL = article.url
                val shareText = "$articleTitle\n$articleURL"

                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, "Share this article")
            startActivity(shareIntent)

            // Re-enable the button after a 2 seconds to prevent multiple rapid clicks
            Handler(Looper.getMainLooper()).postDelayed({
                binding.btnArticleShare.isEnabled = true
                binding.btnArticleShare.isClickable = true
            }, 2000)
        }

        addBottomAppBarListeners()

        // Study mode: lock the body font size and remove the +/- text-size controls
        // so line bounding boxes stay stable for gaze AOI mapping (Stage 4).
        // Applied in dp so the OS font-scale setting can't change the pixel size.
        if (StudyConfig.LOCK_ARTICLE_FONT_SIZE) {
            binding.tvArticleText.setTextSize(
                TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP
            )
            binding.llArticleBottomButtons.visibility = View.GONE
        }

        // Loading article content from url
        lifecycleScope.launch {
            // Check if offline article
            val articleId = article.newsId
            val offlineArticle = DatabaseHelper.getNewsArticleDao().getNewsArticle(articleId)

            Log.d("ArticleFragment", "onCreateView: offlineArticle: $offlineArticle")

            if (offlineArticle?.articleBody?.isNotEmpty() == true) {
                // Offline article
                binding.tvArticleText.text = offlineArticle.articleBody
            } else {
                // Online article
                binding.tvArticleText.text = article.body

                // Set article image from link
                if (article.imageURL != "") {
                    Glide.with(this@ArticleFragment).load(article.imageURL).error(R.drawable.sample_article_image).into(binding.ivArticleFullImage)
                } else {
                    binding.ivSourceImage.setImageResource(R.drawable.sample_source_image)
                }
            }

            // Article text must be final before TextView line/word geometry and
            // the participant-relative adaptation baseline begin.
            articleBodyReady = true
            setupGazeDebug()

            // Load Recommended Articles
            DataHelper.loadArticleData(context, pageSize = 7, language = language) { it ->
                // Check if the fragment is still attached to a context
                val ctx = context ?: return@loadArticleData

                if (FirebaseHelper.isNetworkAvailable(ctx)) {
                    // remove article with the same newsId
                    val recommendedArticles = it.filter { it.newsId != article.newsId }
                    adapter.updateData(recommendedArticles)

                    // If no articles, hide recommendations
                    if (it.isEmpty()) {
                        binding.divider.visibility = View.GONE
                        binding.btnArticleRecommendations.visibility = View.GONE
                        binding.tvArticleRecommended.visibility = View.GONE
                    }

                } else {
                    binding.divider.visibility = View.GONE
                    binding.btnArticleRecommendations.visibility = View.GONE
                    binding.tvArticleRecommended.visibility = View.GONE
                }
            }

            // Load Saved Lists
            savedLists = ArrayList()

            val checkedLists = FirebaseHelper.checkIfArticleSavedInLists(requireContext(), article.newsId)
            for (listId in checkedLists) {
                val savedList = SavedList(listId, "List Name")
                savedLists.add(savedList)
            }

            Log.d("ArticleFragment", "onCreateView: savedLists: $savedLists")

            if (savedLists.isNotEmpty()) {
                Log.d("ArticleFragment", "onCreateView: savedLists is not empty")
                // Change save button icon to filled
                binding.btnSaveList.icon = ContextCompat.getDrawable(requireContext(), R.drawable.bookmark_filled_weight400)

                // Change text to "Saved to list"
                val savedText = "Saved to list"
                binding.btnSaveList.text = savedText
            }
        }

        return binding.root
    }
    // Implement TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to Filipino (Tagalog)
            val result = textToSpeech.setLanguage(Locale("fil", "PH"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                installVoiceData()
            }
            for (voice in textToSpeech.voices) {
                // Log the voice name
                Log.d("ArticleFragment", "${voice.name} ${voice.quality} ${voice.isNetworkConnectionRequired} ${voice.features} ${voice.latency}")
                if(voice.name.contains("fil-ph-x-fie-local")) {
                    textToSpeech.setVoice(voice)
                    break
                }
            }

            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(p0: String?) {
                    // Change the Read Aloud button to "Stop" when TTS starts speaking
                    Handler(Looper.getMainLooper()).post {
                        binding.btnReadAloudArticle.text = "Stop"
                        Log.d("ArticleFragment", "TTS started")
                    }
                }

                override fun onDone(utteranceId: String) {
                    // Enable the Read Aloud button after the TTS is done speaking
                    Handler(Looper.getMainLooper()).post {
                        binding.btnReadAloudArticle.text = "Read Aloud"
                        binding.btnReadAloudArticle.isEnabled = true
                        binding.btnReadAloudArticle.isClickable = true
                        Log.d("ArticleFragment", "TTS done")
                    }
                }

                @Deprecated("Deprecated in API level 21")
                override fun onError(p0: String?){
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    // Show toast message when TTS encounters an error
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Read Aloud failed (${errorCode})", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            binding.btnReadAloudArticle.isEnabled = true
            binding.btnReadAloudArticle.isClickable = true
        } else {
            // Upon TextToSpeech initialization failure, disable the Read Aloud button
            Toast.makeText(context, "Read Aloud is not available", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Ask the current default engine to launch the matching INSTALL_TTS_DATA activity
     * so the required TTS files are properly installed.
     */
    private fun installVoiceData() {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setPackage("com.google.android.tts")
        try {
            Log.v("TTS", "Installing voice data: " + intent.toUri(0))
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            Log.e("TTS", "Failed to install TTS data, no activity found for $intent)")
        }
    }

    // Don't forget to release TextToSpeech when your activity is destroyed
    override fun onDestroy() {
        stopArticleDemo("fragment_destroyed")
        definitionPopup?.close()
        definitionPopup = null
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        scaffoldDemoHandler.removeCallbacksAndMessages(null)
        rsiInferencer?.flush()
        stopScanpathSession("fragment_destroyed")
        gazeProvider?.stop()
        gazeOverlay?.clearScaffold()
        targetStabilizer?.reset()
        scaffoldController?.reset()
        targetStabilizer = null
        stabilityEstimator = null
        scaffoldController = null
        currentTextTarget = TextTarget.INVALID
        gazeOverlay = null
        super.onDestroy()
    }


    override fun onResume() {
        super.onResume()
        scanpathSession?.resume()
        readingAidRuntime?.resume()
        if (!demoCameraSuppressed && !scanpathAidsEnabled && StudyConfig.SCAFFOLD_MODE == StudyConfig.ScaffoldMode.DEMO_CYCLE && scaffoldController != null) {
            startScaffoldDemoCycle()
        }
        if (resumeGazeAfterStop && !launchedCalibration && !demoCameraSuppressed) {
            resumeGazeAfterStop = false
            binding.root.post {
                if (isResumed && gazeProvider == null && !demoCameraSuppressed) articleGazeSink?.let { attachLiveGaze(it) }
            }
        }
        if (launchedCalibration) {
            launchedCalibration = false
            // Allow the finishing activity's worker to drain its native resources.
            Handler(Looper.getMainLooper()).postDelayed({
                if (isResumed && !demoCameraSuppressed) restartLiveGazeAfterCalibration()
            }, GAZE_RESTART_DELAY_MS)
        }
    }

    override fun onPause() {
        stopArticleDemo("screen_paused")
        definitionPopup?.clear("screen_paused")
        finishReadingMeasurement("screen_paused", restore = false)
        readingAidRuntime?.suspend("screen_paused")
        scanpathSession?.pause("screen_paused")
        super.onPause()
    }

    override fun onDestroyView() {
        stopArticleDemo("article_view_destroyed")
        stopScanpathSession("article_view_destroyed")
        gazeProvider?.stop()
        gazeProvider = null
        definitionPopup?.close()
        definitionPopup = null
        articleDemo = null
        readingAidOverlay = null
        articleBodyReady = false
        super.onDestroyView()
    }

    override fun onStop() {
        finishReadingMeasurement("screen_left", restore = false)
        scaffoldDemoHandler.removeCallbacksAndMessages(null)
        resumeGazeAfterStop = gazeProvider != null && !StudyConfig.GAZE_TOUCH_VALIDATION
        stopScanpathSession("screen_left")
        gazeProvider?.stop(); gazeProvider = null
        gazeOverlay?.clearGaze()
        super.onStop()
    }

    /** Stop live gaze (frees the camera), open the accuracy test, resume on return. */
    private fun launchGazeTest() {
        stopArticleDemo("accuracy_test_opened")
        launchedCalibration = true
        rsiInferencer?.flush()
        stopScanpathSession("calibration_opened")
        gazeProvider?.stop()
        gazeProvider = null
        startActivity(Intent(requireContext(), GazeTestActivity::class.java))
    }

    private fun showGazeDiagnosticsMenu() {
        if (readingMeasurement != null) {
            finishReadingMeasurement("manual_stop")
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reading_validation_menu)
            .setItems(
                arrayOf(
                    getString(R.string.reading_validation_accuracy_option),
                    getString(R.string.reading_validation_reading_option),
                    getString(R.string.reading_measurement_option),
                    getString(R.string.reading_aids_option),
                    getString(R.string.reading_aids_save_trace),
                    getString(R.string.scanpath_replay_title),
                    getString(R.string.scanpath_pilot_title),
                    getString(R.string.article_aid_demo_option),
                ),
            ) { _, which ->
                when (which) {
                    0 -> launchGazeTest()
                    1 -> launchReadingValidation()
                    2 -> showReadingMeasurementInstructions()
                    3 -> showReadingAidControls()
                    4 -> saveReadingAidTrace()
                    5 -> launchScanpathReplay()
                    6 -> launchScanpathPilot()
                    7 -> startArticleDemo()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showReadingMeasurementInstructions() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reading_measurement_option)
            .setMessage(R.string.reading_measurement_instructions)
            .setPositiveButton(R.string.reading_measurement_start) { _, _ -> startReadingMeasurement() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startReadingMeasurement() {
        if (gazeProvider == null || StudyConfig.GAZE_TOUCH_VALIDATION || binding.tvArticleText.layout == null) {
            Toast.makeText(context, R.string.reading_measurement_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val fingerprint = MgazeNetCalibrationStore(requireContext()).fingerprint() ?: return
        readingAidRuntime?.suspend("diagnostic_recording")
        lateinit var session: ReadingMeasurementLog
        try {
            session = ReadingMeasurementLog(requireContext(), binding.tvArticleText, fingerprint) { file, complete ->
                if (readingMeasurement === session) finishReadingMeasurement("writer_closed")
                Log.i("ReadingMeasurement", "${file.name}: complete=$complete")
                if (isAdded && isResumed) Toast.makeText(context,
                    if (complete) R.string.reading_measurement_saved else R.string.reading_measurement_failed,
                    Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            readingAidRuntime?.resume()
            Log.e("ReadingMeasurement", "Cannot open recording", e)
            Toast.makeText(context, R.string.reading_measurement_failed, Toast.LENGTH_LONG).show()
            return
        }
        readingMeasurement = session
        scanpathSession?.boundary("diagnostic_mode_changed")
        textToSpeech.stop()
        binding.btnReadAloudArticle.isEnabled = false
        scaffoldDemoHandler.removeCallbacksAndMessages(null)
        gazeOverlay?.setDotOnly(true)
        binding.root.viewTreeObserver.addOnPreDrawListener(measurementGeometryListener)
    }

    private fun finishReadingMeasurement(reason: String, restore: Boolean = true) {
        val session = readingMeasurement ?: return
        readingMeasurement = null
        scanpathSession?.boundary("diagnostic_mode_changed")
        binding.root.viewTreeObserver.removeOnPreDrawListener(measurementGeometryListener)
        session.finish(reason)
        binding.btnReadAloudArticle.isEnabled = true
        resetAdaptiveReadingSession()
        gazeOverlay?.setDotOnly(false)
        if (restore && isResumed) readingAidRuntime?.resume()
        if (!scanpathAidsEnabled && restore && isResumed && StudyConfig.SCAFFOLD_MODE == StudyConfig.ScaffoldMode.DEMO_CYCLE) {
            startScaffoldDemoCycle()
        }
    }

    /** Stop live gaze, run the controlled reading protocol, then rebind on return. */
    private fun launchReadingValidation() {
        stopArticleDemo("reading_validation_opened")
        launchedCalibration = true
        rsiInferencer?.flush()
        stopScanpathSession("reading_validation_opened")
        gazeProvider?.stop()
        gazeProvider = null
        startActivity(Intent(requireContext(), ReadingValidationActivity::class.java))
    }

    private fun restartLiveGazeAfterCalibration() {
        if (StudyConfig.GAZE_TOUCH_VALIDATION || demoCameraSuppressed) return
        val onGaze = articleGazeSink ?: return
        stopScanpathSession("provider_restarted")
        gazeProvider?.stop()
        gazeProvider = null
        resetAdaptiveReadingSession()
        attachLiveGaze(onGaze)
    }

    private fun resetAdaptiveReadingSession() {
        targetStabilizer?.reset()
        stabilityEstimator?.reset()
        scaffoldController?.reset()?.let(::applyScaffoldUpdate)
        currentTextTarget = TextTarget.INVALID
        lastStabilityLogMs = 0L
        lastAoiLogMs = 0L
        rsiInferencer = createRsiInferencer()
        Log.i("GazeScaffold", "Adaptive reading session reset after calibration/test interruption")
    }
    // Function to convert text to speech
    private fun speak(text: String) {
        if(!isTranslated && language.lowercase() == "english"){
            textToSpeech.setLanguage(Locale.ENGLISH)
            textToSpeech.voice = textToSpeech.voices.find { it.name.contains("en-us-x-iom-local") }
        }
        else{
            textToSpeech.setLanguage(Locale("fil", "PH"))
            textToSpeech.voice = textToSpeech.voices.find { it.name.contains("fil-ph-x-fie-local") }
        }
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED)
    }

    fun convertPixelsToSp(px: Float): Float {
        return px / (requireContext().resources.displayMetrics.scaledDensity)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Add bottom app bar logic (it must stick and then only disappear
        // once it reaches recommendations
        // binding.btnArticleRecommendations.post {
        //     val btnLocation = IntArray(2)
        //     binding.btnArticleRecommendations.getLocationOnScreen(btnLocation)
        //     val btnY = btnLocation[1]
        //     val nestedScrollView = binding.nsvArticleText
        //     val bottomAppBar = binding.bottomAppBar
        //     nestedScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
        //         Log.d("Y_COORDINATE", "btnY: $btnY scrollY: $scrollY")
        //         // Check if the scroll position is past the Y-coordinate of btnArticleRecommendations
        //         if (scrollY > btnY -1000) {
        //             Log.d("SCROLL", "Hiding Bottom App Bar")
        //             bottomAppBar.performHide()
        //         } else {
        //             bottomAppBar.performShow()
        //         }
        //     })
        // }
    }

    private fun addBottomAppBarListeners() {
        val size = 2
        val minSizeLimit = 14
        val maxSizeLimit = 60

        // Increase font size when btnArticleTextLarger is clicked
        binding.btnArticleTextLarger.setOnClickListener {
            val newSize = convertPixelsToSp(binding.tvArticleText.textSize + size)
            if (newSize <= maxSizeLimit) {
                binding.tvArticleText.textSize = newSize
            }
            else{
                Toast.makeText(context, "Reached maximum text size", Toast.LENGTH_SHORT).show()
            }
        }

        // Decrease font size when btnArticleTextSmaller is clicked
        binding.btnArticleTextSmaller.setOnClickListener {
            val newSize = convertPixelsToSp(binding.tvArticleText.textSize) - size
            if (newSize > minSizeLimit) {
                binding.tvArticleText.textSize = newSize
            }
            else{
                Toast.makeText(context, "Reached minimum text size", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnArticleClrLight.setOnClickListener{ updateColors(ColorMode.LIGHT) }
        binding.btnArticleClrDark.setOnClickListener{ updateColors(ColorMode.DARK) }
        binding.btnArticleClrSepia.setOnClickListener{ updateColors(ColorMode.SEPIA) }
    }

    /**
     * Stage 4 (touch-validation slice): attach the debug gaze layer to the
     * article view: the line-AOI mapper + a gaze-dot overlay. All gaze flows
     * through the single [GazeProvider.OnGaze] entry point below, so the live
     * local calibrated provider can drive it via `setOnGaze` with no changes here.
     * Touch validation proves line mapping before trusting gaze accuracy.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGazeDebug() {
        if (!StudyConfig.GAZE_ENABLED || demoCameraSuppressed) return

        val overlay = GazeOverlayView(requireContext())
        gazeOverlay = overlay
        (binding.root as ViewGroup).addView(
            overlay,
            ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
        )
        overlay.bringToFront()
        overlay.translationZ = 1000f
        overlay.setDebugVisualsEnabled(StudyConfig.GAZE_DEBUG_VISUALS)
        if (scanpathAidsEnabled) {
            val aidOverlay = ReadingAidOverlayView(requireContext(), lineStartMarkerInGutter = true)
            (binding.root as ViewGroup).addView(aidOverlay, ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            })
            aidOverlay.translationZ = 900f
            readingAidOverlay = aidOverlay
        }
        val mapper = LineAoiMapper(binding.tvArticleText)
        val stabilizer = GazeTargetStabilizer()
        val estimator = WindowedStabilityEstimator(
            windowMs = StudyConfig.SCAFFOLD_WINDOW_MS,
            baselineDurationMs = StudyConfig.SCAFFOLD_BASELINE_DURATION_MS,
        )
        val controller = AdaptiveScaffoldController(
            wordThreshold = StudyConfig.SCAFFOLD_WORD_THRESHOLD_Z,
            lineThreshold = StudyConfig.SCAFFOLD_LINE_THRESHOLD_Z,
            focusThreshold = StudyConfig.SCAFFOLD_FOCUS_THRESHOLD_Z,
            reentryThreshold = StudyConfig.SCAFFOLD_REENTRY_THRESHOLD_Z,
            minimumConfidence = StudyConfig.SCAFFOLD_MIN_GAZE_CONFIDENCE,
            escalationPersistenceMs = StudyConfig.SCAFFOLD_ESCALATION_PERSISTENCE_MS,
            recoveryPersistenceMs = StudyConfig.SCAFFOLD_RECOVERY_PERSISTENCE_MS,
            forcedLevel = forcedScaffoldLevel(),
        )
        targetStabilizer = stabilizer
        stabilityEstimator = estimator
        scaffoldController = controller
        rsiInferencer = createRsiInferencer()

        // Single gaze entry point (full-screen px). Both the touch-validation
        // source and the local calibrated gaze provider feed through here.
        val onGaze = GazeProvider.OnGaze { x, y ->
            if (demoCameraSuppressed) return@OnGaze
            overlay.setGazeScreen(x, y)
            if (readingMeasurement != null) return@OnGaze
            if (scanpathAidsEnabled) return@OnGaze
            val timestampMs = System.currentTimeMillis()
            val rawTarget = mapper.targetAt(x, y)
            val target = stabilizer.update(rawTarget, timestampMs)
            currentTextTarget = target
            estimator.recordGazeSample(rawTarget.isValid, timestampMs)
            controller.onGazeTarget(target, timestampMs)

            if (timestampMs - lastAoiLogMs >= ARTICLE_DIAGNOSTIC_LOG_INTERVAL_MS) {
                lastAoiLogMs = timestampMs
                Log.d(
                    "GazeAOI",
                    "gaze=(" + x.toInt() + "," + y.toInt() + ") rawLine=" + rawTarget.lineIndex +
                        " stableLine=" + target.lineIndex + "/" + target.lineCount +
                        " word=" + target.wordStart + ":" + target.wordEnd
                )
            }

            if (target.isValid) {
                rsiInferencer?.onLine(target.lineIndex, target.lineCount, timestampMs)
            } else {
                applyScaffoldUpdate(
                    controller.update(estimator.currentSnapshot(timestampMs), target, timestampMs)
                )
            }
        }

        articleGazeSink = onGaze

        if (StudyConfig.GAZE_TOUCH_VALIDATION) {
            attachTouchValidation(onGaze)
        } else {
            attachLiveGaze(onGaze)
        }

        if (!scanpathAidsEnabled && StudyConfig.SCAFFOLD_MODE == StudyConfig.ScaffoldMode.DEMO_CYCLE) startScaffoldDemoCycle()
    }

    /**
     * Recording aid, not a study mode: hold each level for
     * [StudyConfig.SCAFFOLD_DEMO_LEVEL_DURATION_MS] and then move to the next one,
     * so all four appear in a single continuous take. Real gaze still decides where
     * each level is drawn - only the adaptive *trigger* is bypassed, so nothing here
     * demonstrates instability detection.
     */
    private fun startScaffoldDemoCycle() {
        if (demoCameraSuppressed) return
        if (scanpathAidsEnabled) return
        if (readingMeasurement != null) return
        scaffoldDemoHandler.removeCallbacksAndMessages(null)
        scaffoldDemoStep = 0
        val advance = object : Runnable {
            override fun run() {
                val controller = scaffoldController ?: return
                val level = SCAFFOLD_DEMO_SEQUENCE[scaffoldDemoStep % SCAFFOLD_DEMO_SEQUENCE.size]
                controller.forcedLevel = level
                if (StudyConfig.SCAFFOLD_DEMO_CAPTION) {
                    gazeOverlay?.setScaffoldCaption(SCAFFOLD_DEMO_CAPTIONS[level])
                }
                Log.i("GazeScaffold", "demo cycle -> $level")
                scaffoldDemoStep++
                scaffoldDemoHandler.postDelayed(this, StudyConfig.SCAFFOLD_DEMO_LEVEL_DURATION_MS)
            }
        }
        scaffoldDemoHandler.post(advance)
    }

    private fun createRsiInferencer(): ReadingStateInferencer =
        ReadingStateInferencer(object : ReadingStateInferencer.Listener {
            override fun onLineSample(sample: ReadingStateInferencer.LineSample) {
                // Per-sample diagnostics are intentionally omitted from the reading hot path.
            }

            override fun onFixation(event: ReadingStateInferencer.FixationEvent) {
                Log.d("GazeRSI", "fixation line=${event.lineIndex}/${event.lineCount} duration=${event.durationMs}ms")
            }

            override fun onDwell(event: ReadingStateInferencer.DwellEvent) {
                Log.d("GazeRSI", "dwell line=${event.lineIndex}/${event.lineCount} duration=${event.durationMs}ms")
            }

            override fun onRegression(event: ReadingStateInferencer.RegressionEvent) {
                Log.d("GazeRSI", "regression ${event.fromLine}->${event.toLine} t=${event.timestampMs}")
            }

            override fun onScore(score: ReadingStateInferencer.RsiScore) {
                val estimator = stabilityEstimator ?: return
                val controller = scaffoldController ?: return
                val snapshot = estimator.onCumulativeScore(score)
                applyScaffoldUpdate(controller.update(snapshot, currentTextTarget, score.timestampMs))
                if (score.timestampMs - lastStabilityLogMs >= 2_000L) {
                    lastStabilityLogMs = score.timestampMs
                    Log.d(
                        "GazeScaffold",
                        String.format(
                            Locale.US,
                            "index=%.2f raw=%.2f confidence=%.2f baselineReady=%s " +
                                "metrics=reg%.1f/min,dwell%.2f,fix%.1f/min",
                            snapshot.smoothedIndex,
                            snapshot.rawIndex,
                            snapshot.confidence,
                            snapshot.baselineReady,
                            snapshot.metrics.regressionRatePerMinute,
                            snapshot.metrics.dwellRatio,
                            snapshot.metrics.fixationRatePerMinute,
                        )
                    )
                }
            }
        })

    private fun applyScaffoldUpdate(update: ScaffoldUpdate) {
        if (scanpathAidsEnabled) return
        gazeOverlay?.setScaffoldState(binding.tvArticleText, update.state)
        update.transition?.let { transition ->
            Log.i(
                "GazeScaffold",
                String.format(
                    Locale.US,
                    "transition=%s->%s reason=%s index=%.2f confidence=%.2f " +
                        "line=%d target=%d recoveryLatencyMs=%d",
                    transition.from,
                    transition.to,
                    transition.reason,
                    transition.stabilityIndex,
                    transition.confidence,
                    transition.currentLine,
                    transition.reentryTargetLine,
                    transition.recoveryLatencyMs ?: -1L,
                )
            )
        }
    }

    private fun forcedScaffoldLevel(): ScaffoldLevel? = when (StudyConfig.SCAFFOLD_MODE) {
        StudyConfig.ScaffoldMode.OFF -> ScaffoldLevel.NONE
        StudyConfig.ScaffoldMode.ADAPTIVE -> null
        StudyConfig.ScaffoldMode.FORCE_WORD -> ScaffoldLevel.WORD
        StudyConfig.ScaffoldMode.FORCE_LINE -> ScaffoldLevel.LINE
        StudyConfig.ScaffoldMode.FORCE_FOCUS -> ScaffoldLevel.FOCUS
        StudyConfig.ScaffoldMode.FORCE_REENTRY -> ScaffoldLevel.REENTRY
        StudyConfig.ScaffoldMode.DEMO_CYCLE -> SCAFFOLD_DEMO_SEQUENCE.first()
    }

    /** Finger substitutes for gaze; returns false so the article still scrolls. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchValidation(onGaze: GazeProvider.OnGaze) {
        binding.nsvArticleText.setOnTouchListener { _, event ->
            onGaze.onGaze(event.rawX, event.rawY)
            false
        }
    }

    /**
     * Live gaze from the local phone tracker, mapped to the phone screen with
     * the saved 13-point calibration. In live mode, do not silently substitute
     * touch input; missing/invalid calibration must be fixed before reading.
     */
    private fun attachLiveGaze(onGaze: GazeProvider.OnGaze) {
        if (demoCameraSuppressed) return
        val issue = MgazeNetCalibrationStore(requireContext()).compatibilityIssue(binding.root)
        if (issue != null) {
            Toast.makeText(context, issue, Toast.LENGTH_LONG).show()
            return
        }
        val provider = MgazeNetGazeProvider(requireContext(),binding.root,smoothForReading = true)
        stopScanpathSession("provider_replaced")
        scanpathCounts.clear()
        val aids = readingAidOverlay?.let { ArticleReadingAidRuntime(binding.tvArticleText,
            binding.readingAidWordDock, it, config = ReadingAidPolicyConfig.definitionPrototype(),
            preferences = aidPreferences, definitionPopup = definitionPopup,
            onConsultationChanged = { active, at, reason -> scanpathSession?.setConsultation(active, at, reason) }) }
        readingAidRuntime = aids
        val liveScanpath = ArticleScanpathSession(binding.tvArticleText,
            config = aids?.scanpathConfig ?: com.newsmead.gaze.scanpath.ScanpathConfig(),
            onEvent = { generation, event ->
            scanpathCounts[event.patternId] = (scanpathCounts[event.patternId] ?: 0) + 1
            aids?.event(generation, event)
        }, onGeometry = { generation, geometry -> aids?.geometry(generation, geometry) },
            onInput = { generation, sample -> aids?.input(generation, sample) },
            onCandidate = { generation, candidate -> aids?.candidate(generation, candidate) },
            onFixation = { generation, fixation -> aids?.fixation(generation, fixation) })
        scanpathSession = liveScanpath
        binding.root.viewTreeObserver.addOnPreDrawListener(scanpathGeometryListener)
        liveScanpath.checkGeometry()
        Log.i("Scanpath", "live raw engine=${ScanpathEngine.EXTRACTOR_VERSION} " +
            "classifier=${ScanpathEngine.CLASSIFIER_VERSION}; independent experimental aids=${aids != null}")
        provider.onFps = { fps -> gazeOverlay?.setFps(fps) }
        provider.onFailure = { message ->
            if (scanpathSession === liveScanpath) stopScanpathSession("provider_failed")
            Toast.makeText(context,message,Toast.LENGTH_LONG).show()
        }
        provider.onObservation = { observation ->
            // An old provider's terminal callback must not close a newer article stream.
            if (scanpathSession === liveScanpath) liveScanpath.record(observation)
            readingMeasurement?.record(observation)
            if (observation.reason != "coordinate") gazeOverlay?.clearGaze()
        }
        provider.setOnGaze { x,y -> onGaze.onGaze(x,y) }
        gazeProvider = provider
        provider.start(viewLifecycleOwner)
    }

    private fun launchScanpathReplay() {
        stopArticleDemo("replay_opened")
        launchedCalibration = true
        stopScanpathSession("replay_opened")
        gazeProvider?.stop()
        gazeProvider = null
        startActivity(Intent(requireContext(), ScanpathReplayActivity::class.java))
    }

    private fun launchScanpathPilot() {
        stopArticleDemo("pilot_opened")
        launchedCalibration = true
        stopScanpathSession("pilot_opened")
        val current = gazeProvider
        gazeProvider = null
        val open = {
            if (isAdded && viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                startActivity(Intent(requireContext(), ScanpathPilotActivity::class.java))
        }
        if (current is MgazeNetGazeProvider) current.stopAndThen(open) else { current?.stop(); open() }
    }

    private fun stopScanpathSession(reason: String) {
        val session = scanpathSession ?: return
        scanpathSession = null
        binding.root.viewTreeObserver.removeOnPreDrawListener(scanpathGeometryListener)
        readingAidRuntime?.close(reason)
        readingAidRuntime = null
        definitionPopup?.onConsultationChanged = { _, _, _ -> }
        definitionPopup?.onRender = { _, _, _, _ -> }
        session.close(reason)
        Log.i("Scanpath", "closed reason=$reason patternRecords=${scanpathCounts.toSortedMap()}")
    }

    /** Explicit render-only inspection of the loaded article; camera stays off afterward. */
    private fun startArticleDemo() {
        if (!articleBodyReady || !isResumed || readingMeasurement != null || launchedCalibration) {
            Toast.makeText(context, R.string.reading_measurement_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        stopArticleDemo("replaced")
        demoCameraSuppressed = true
        resumeGazeAfterStop = false
        scaffoldDemoHandler.removeCallbacksAndMessages(null)
        stopScanpathSession("article_demo_started")
        gazeOverlay?.clearGaze()
        gazeOverlay?.clearScaffold()
        val current = gazeProvider
        gazeProvider = null
        val startToken = ++demoStartToken
        binding.articleAidDemoPanel.visibility = View.VISIBLE
        binding.articleAidDemoPanel.bringToFront()
        binding.articleAidDemoPanel.translationZ = 1100f
        binding.articleAidDemoStatus.text = getString(R.string.article_aid_demo_preparing)
        binding.articleAidDemoPrevious.isEnabled = false
        binding.articleAidDemoNext.isEnabled = false
        val prepare = {
            if (isAdded && isResumed && startToken == demoStartToken) {
                // Settle panel inset first, then expose the actual loaded body before snapshotting.
                binding.root.doOnPreDraw {
                    if (isResumed && startToken == demoStartToken) {
                        binding.nsvArticleText.scrollTo(0, binding.tvArticleText.top)
                        binding.root.doOnPreDraw {
                            if (isResumed && startToken == demoStartToken) prepareArticleDemo()
                        }
                        binding.root.invalidate()
                    }
                }
                binding.root.requestLayout()
            }
        }
        if (current is MgazeNetGazeProvider) current.stopAndThen(prepare)
        else { current?.stop(); prepare() }
    }

    private fun prepareArticleDemo() {
        val aidOverlay = readingAidOverlay ?: ReadingAidOverlayView(requireContext(), lineStartMarkerInGutter = true).also { overlay ->
            (binding.root as ViewGroup).addView(overlay, ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            })
            overlay.translationZ = 900f
            readingAidOverlay = overlay
        }
        // Presentation draw receipts never enter live runtime traces.
        aidOverlay.onRender = { _, _, _, _ -> }
        definitionPopup?.onConsultationChanged = { _, _, _ -> }
        articleDemo = ArticleReadingAidDemoPresenter(binding.tvArticleText, aidOverlay,
            renderWord = { definitionPopup?.showAction(it) },
            onStatus = { binding.articleAidDemoStatus.text = it },
            onStopped = { stopArticleDemo(it) })
        try {
            articleDemo!!.start()
            binding.articleAidDemoPrevious.isEnabled = true
            binding.articleAidDemoNext.isEnabled = true
        } catch (e: IllegalStateException) {
            stopArticleDemo("article_layout_unavailable")
            Toast.makeText(context, getString(R.string.article_aid_demo_unavailable,
                e.message ?: "Article layout unavailable"), Toast.LENGTH_LONG).show()
        }
    }

    private fun stopArticleDemo(reason: String) {
        demoStartToken++
        articleDemo?.stop(reason, notify = false)
        definitionPopup?.clear(reason)
        if (::binding.isInitialized) binding.articleAidDemoPanel.visibility = View.GONE
    }

    private fun updateDefinitionLanguage() {
        // This translation route changes an English original into Filipino.
        // Unknown or non-English originals never use English homograph definitions.
        definitionPopup?.clear("article_language_changed")
        definitionPopup?.languageSupported = language.equals("english", ignoreCase = true) && !isTranslated
    }

    private fun showReadingAidControls() {
        val runtime = readingAidRuntime ?: run {
            Toast.makeText(context, R.string.reading_measurement_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun check(label: Int, selected: Boolean) = CheckBox(requireContext()).apply {
            setText(label); isChecked = selected; panel.addView(this)
        }
        val prefs = runtime.preferences
        val enabled = check(R.string.reading_aids_enabled, prefs.enabled)
        val line = check(R.string.reading_aids_line, prefs.lineGuide)
        val start = check(R.string.reading_aids_start, prefs.lineStartCue)
        val word = check(R.string.reading_aids_word_definition, prefs.wordDefinition)
        val focus = check(R.string.reading_aids_focus, prefs.focusRegion)
        val adaptive = check(R.string.reading_aids_adaptive, prefs.adaptiveStrength)
        val label = TextView(requireContext()).apply { panel.addView(this) }
        val cap = SeekBar(requireContext()).apply {
            max = 100; progress = (prefs.maxStrength * 100).toInt(); panel.addView(this)
            label.text = getString(R.string.reading_aids_strength, progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = getString(R.string.reading_aids_strength, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        // Interaction ends the reading episode; fresh gaze reacquires after the dialog.
        runtime.suspend("reader_controls")
        scanpathSession?.boundary("reader_controls")
        AlertDialog.Builder(requireContext()).setTitle(R.string.reading_aids_option).setView(panel)
            .setPositiveButton(R.string.reading_aids_apply) { _, _ ->
                aidPreferences = prefs.copy(enabled = enabled.isChecked, lineGuide = line.isChecked,
                    lineStartCue = start.isChecked, wordMagnifier = false, wordDefinition = word.isChecked,
                    focusRegion = focus.isChecked, adaptiveStrength = adaptive.isChecked,
                    maxStrength = cap.progress / 100.0, manualStrength = cap.progress / 100.0)
                runtime.updatePreferences(aidPreferences)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                scanpathSession?.boundary("reader_controls_closed")
                if (isResumed && readingAidRuntime === runtime) runtime.resume()
            }.show()
    }

    private fun saveReadingAidTrace() {
        val runtime = readingAidRuntime ?: return
        try {
            val file = runtime.export(requireContext())
            Log.i("ReadingAidTrace", "Saved ${file.name}")
            Toast.makeText(context, R.string.reading_aids_trace_saved, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("ReadingAidTrace", "Cannot save trace", e)
            Toast.makeText(context, R.string.reading_aids_trace_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Updates the colors of the article fragment
     * @param color The color mode to update to
     */
    private fun updateColors(color: ColorMode) {
        val activity = requireActivity()
        val window = activity.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        when (color) {
            ColorMode.LIGHT -> {
                window.statusBarColor = ContextCompat.getColor(activity,R.color.light)
                window.navigationBarColor = ContextCompat.getColor(activity,R.color.light)

                changeColorOfBackgrounds(ContextCompat.getColor(requireContext(), R.color.light))
                changeColorOfButtons(ContextCompat.getColor(requireContext(), R.color.light_btn))
                binding.btnArticleTextLarger.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_btn_emphasis))
                changeColorOfTexts(ContextCompat.getColor(requireContext(), R.color.light_text))
                changeColorOfSubTexts(ContextCompat.getColor(requireContext(), R.color.light_subtext))
            }
            ColorMode.DARK -> {
                window.statusBarColor = ContextCompat.getColor(activity,R.color.dark)
                window.navigationBarColor = ContextCompat.getColor(activity,R.color.dark)

                changeColorOfBackgrounds(ContextCompat.getColor(requireContext(), R.color.dark))
                changeColorOfButtons(ContextCompat.getColor(requireContext(), R.color.dark_btn))
                binding.btnArticleTextLarger.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_btn_emphasis))
                changeColorOfTexts(ContextCompat.getColor(requireContext(), R.color.dark_text))
                changeColorOfSubTexts(ContextCompat.getColor(requireContext(), R.color.dark_subtext))
            }
            ColorMode.SEPIA -> {
                window.statusBarColor = ContextCompat.getColor(activity,R.color.sepia)
                window.navigationBarColor = ContextCompat.getColor(activity,R.color.sepia)

                changeColorOfBackgrounds(ContextCompat.getColor(requireContext(), R.color.sepia))
                changeColorOfButtons(ContextCompat.getColor(requireContext(), R.color.sepia_btn))
                binding.btnArticleTextLarger.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.sepia_btn_emphasis))
                changeColorOfTexts(ContextCompat.getColor(requireContext(), R.color.sepia_text))
                changeColorOfSubTexts(ContextCompat.getColor(requireContext(), R.color.sepia_subtext))
            }
        }
    }

    private fun changeColorOfBackgrounds(color: Int){
        binding.root.setBackgroundColor(color)
        binding.nsvArticleText.setBackgroundColor(color)
        binding.bottomAppBar.setBackgroundColor(color)
        binding.clArticleTopBar.setBackgroundColor(color)
        // Change the color of the Recommended Articles RecyclerView (background)
        adapter.changeBackgroundColor(color)
    }
    
    private fun changeColorOfButtons(color: Int) {
        binding.btnArticleTextSmaller.setBackgroundColor(color)
        binding.btnSaveList.setBackgroundColor(color)
        binding.btnTranslateArticle.setBackgroundColor(color)
        binding.btnReadAloudArticle.setBackgroundColor(color)
    }

    private fun changeColorOfSubTexts(color: Int){
        binding.tvArticleAuthor.setTextColor(color)
        binding.tvArticleMinRead.setTextColor(color)
        binding.tvArticleBy.setTextColor(color)
        binding.tvByDot.setTextColor(color)
        binding.tvBullet.setTextColor(color)
        // Change the color of the Recommended Articles RecyclerView (read time)
        adapter.changeSubTextColor(color)
    }

    private fun changeColorOfTexts(color: Int){
        binding.tvArticleText.setTextColor(color)
        binding.tvArticleHeadline.setTextColor(color)
        binding.tvArticleRecommended.setTextColor(color)
        binding.tvSource.setTextColor(color)
        binding.tvCategory.setTextColor(color)

        binding.btnArticleTextLarger.setTextColor(color)
        binding.btnArticleTextSmaller.setTextColor(color)
        binding.btnSaveList.setTextColor(color)
        binding.btnTranslateArticle.setTextColor(color)
        binding.btnReadAloudArticle.setTextColor(color)
        binding.btnArticleClrLight.setTextColor(color)
        binding.btnArticleClrDark.setTextColor(color)
        binding.btnArticleClrSepia.setTextColor(color)

        // Change icon colors
        binding.btnArticleBack.setColorFilter(color)
        binding.btnArticleShare.setColorFilter(color)

        binding.btnSaveList.iconTint = ColorStateList.valueOf(color)
        binding.btnTranslateArticle.iconTint = ColorStateList.valueOf(color)
        binding.btnReadAloudArticle.iconTint = ColorStateList.valueOf(color)

        binding.btnArticleTextLarger.setCompoundDrawableTintList(ColorStateList.valueOf(color))
        binding.btnArticleTextSmaller.setCompoundDrawableTintList(ColorStateList.valueOf(color))

        // Change the color of the Recommended Articles RecyclerView (source, title)
        adapter.changeTextColor(color)
    }

    override fun onItemClicked(article: Article) {
        // Action
        val action = ArticleFragmentDirections.actionArticleFragmentSelf(article)
        Navigation.findNavController(requireView()).navigate(action)
    }

    companion object {
        /**
         * Delay before restarting live gaze after returning from the gaze
         * test/calibration activity, giving that activity time to finish its
         * onDestroy camera release before we rebind the shared CameraX camera.
         */
        private const val GAZE_RESTART_DELAY_MS = 800L
        private const val ARTICLE_DIAGNOSTIC_LOG_INTERVAL_MS = 1_000L

        /** Order DEMO_CYCLE walks through; it loops, so a retake needs no restart. */
        private val SCAFFOLD_DEMO_SEQUENCE = listOf(
            ScaffoldLevel.NONE,
            ScaffoldLevel.WORD,
            ScaffoldLevel.LINE,
            ScaffoldLevel.FOCUS,
            ScaffoldLevel.REENTRY,
        )

        private val SCAFFOLD_DEMO_CAPTIONS = mapOf(
            ScaffoldLevel.NONE to "No scaffold (stable reading)",
            ScaffoldLevel.WORD to "Level 1 - Word highlighting",
            ScaffoldLevel.LINE to "Level 2 - Line emphasis",
            ScaffoldLevel.FOCUS to "Level 3 - Focus window",
            ScaffoldLevel.REENTRY to "Level 4 - Re-entry support",
        )
    }
}
