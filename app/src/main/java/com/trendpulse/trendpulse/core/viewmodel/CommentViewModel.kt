package com.trendpulse.trendpulse.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.trendpulse.trendpulse.core.api.RetrofitClient
import com.trendpulse.trendpulse.core.db.AppDatabase
import com.trendpulse.trendpulse.core.ml.SentimentAnalyzer
import com.trendpulse.trendpulse.core.ml.SentimentResult
import com.trendpulse.trendpulse.core.model.Comment
import com.trendpulse.trendpulse.core.model.SentimentTrendPoint
import com.trendpulse.trendpulse.core.network.WebSocketEvent
import com.trendpulse.trendpulse.core.network.WebSocketService
import com.trendpulse.trendpulse.core.repository.CommentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.work.*
import com.trendpulse.trendpulse.core.worker.CommentSyncWorker
import java.util.concurrent.TimeUnit

/**
 * ViewModel for managing comment scraping and sentiment analysis state.
 *
 * This implementation uses a reactive architecture:
 * - [comments]: Paged data stream from Room (SSOT).
 * - [sentimentMap]: Reactive map of analysis results.
 * - [sentimentTrend] & [overallSentiment]: Derived states that update automatically.
 *
 * It employs a single-consumer queue ([sentimentQueue]) to ensure that ML inference
 * happens sequentially on a background thread, preventing UI jank and model crashes.
 */
class CommentViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = RetrofitClient.apiService
    private val sentimentAnalyzer = SentimentAnalyzer.getInstance(application)
    private val repository = CommentRepository(apiService, AppDatabase.getInstance(application))

    private val _activeUrl = MutableStateFlow<String?>(null)
    private val _scrapingState = MutableStateFlow<ScrapingState>(ScrapingState.Idle)
    val scrapingState: StateFlow<ScrapingState> = _scrapingState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Optimized: Debounced search results from Room FTS4.
     * Prevents excessive database hits while the user is typing.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Comment>> = _searchQuery
        .debounce(300L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList<Comment>())
            } else {
                repository.searchComments(query).map { list ->
                    list.map { Comment(text = it.text, user = it.user) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    private val _sentimentMap = MutableStateFlow<Map<String, SentimentResult>>(emptyMap())
    val sentimentMap: StateFlow<Map<String, SentimentResult>> = _sentimentMap.asStateFlow()

    /**
     * Optimized: Derived StateFlow for sentiment trends.
     * Automatically updates whenever [_sentimentMap] changes, eliminating redundant manual updates.
     */
    val sentimentTrend: StateFlow<List<SentimentTrendPoint>> = _sentimentMap
        .map { map ->
            map.values.mapIndexed { index, result ->
                SentimentTrendPoint(index = index, score = result.toNormalizedScore())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Optimized: Derived StateFlow for overall sentiment summary.
     */
    val overallSentiment: StateFlow<String> = _sentimentMap
        .map { map ->
            if (map.isEmpty()) return@map "Analyzing..."
            val positiveCount = map.values.count { it.label == "POSITIVE" }
            val pct = if (map.isEmpty()) 0 else (positiveCount * 100) / map.size
            formatSentimentLabel(pct)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Analyzing...")

    /**
     * Paged comments from the single source of truth (Room).
     * Reacts to changes in [_activeUrl].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val comments: Flow<PagingData<Comment>> = _activeUrl
        .filterNotNull()
        .flatMapLatest { url ->
            repository.getCommentsPaged(url).map { pagingData ->
                pagingData.map { cached -> Comment(text = cached.text, user = cached.user) }
            }
        }
        .cachedIn(viewModelScope)

    private val sentimentQueue = Channel<Comment>(Channel.UNLIMITED)

    init {
        // Single-consumer loop for sequential sentiment analysis
        viewModelScope.launch(Dispatchers.Default) {
            sentimentQueue.consumeEach { analyzeSentimentSync(it) }
        }

        // Reactive subscription to WebSocket events
        viewModelScope.launch {
            WebSocketService.eventFlow.collect { handleWebSocketEvent(it) }
        }
    }

    private fun handleWebSocketEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.NewComments -> {
                val incoming = event.comments.map { Comment(it.text, it.user) }
                incoming.forEach { sentimentQueue.trySend(it) }

                if (event.progress < 100) {
                    _scrapingState.value = ScrapingState.Loading("Receiving comments... (${event.progress}%)")
                }
            }

            is WebSocketEvent.Done -> {
                _scrapingState.value = ScrapingState.Done
                triggerPagerRefresh()
                _activeUrl.value?.let { analyzeAllComments(it) }
            }

            is WebSocketEvent.Error -> {
                _scrapingState.value = ScrapingState.Error(event.message)
            }
            else -> {}
        }
    }

    /**
     * Loads the last analyzed post from the database.
     */
    fun loadLastPost() {
        viewModelScope.launch {
            repository.getLastAnalyzedUrl()?.let { url ->
                _activeUrl.value = url
                _scrapingState.value = ScrapingState.Done
                loadCached(url)
            }
        }
    }

    /**
     * Initiates the scraping process for a given URL.
     */
    fun startScraping(url: String) {
        viewModelScope.launch {
            _sentimentMap.value = emptyMap()
            _scrapingState.value = ScrapingState.Loading("Starting scrape...")

            loadCached(url)

            try {
                apiService.startScraping(mapOf("postUrl" to url))
                pollScrapingStatus(url)

                _scrapingState.value = ScrapingState.Done
                _activeUrl.value = url
                analyzeAllComments(url)
                scheduleBackgroundSync(url)

            } catch (e: Exception) {
                _scrapingState.value = ScrapingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Sequential status polling until scraping completes or times out.
     */
    private suspend fun pollScrapingStatus(url: String) {
        var stage = "collecting"
        var attempt = 0
        while (stage == "collecting" && attempt < 30) {
            delay(4_000)
            attempt++
            stage = apiService.getComments(url, 1, 1).stage ?: "collecting"
            _scrapingState.value = ScrapingState.Loading("Scraping comments... (${attempt * 4}s)")
        }
        if (stage == "collecting") throw Exception("Scrape timed out, please retry")
    }

    /**
     * Batch analyzes all comments for a given URL.
     */
    private fun analyzeAllComments(url: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                var page = 1
                while (true) {
                    val response = apiService.getComments(url, page, 20)
                    val comments = response.comments ?: break
                    if (comments.isEmpty()) break
                    comments.forEach { sentimentQueue.trySend(Comment(it.text, it.user)) }
                    if (comments.size < 20) break
                    page++
                }
            } catch (_: Exception) { /* non-fatal */ }
        }
    }

    /**
     * Performs synchronized on-device sentiment analysis.
     */
    private suspend fun analyzeSentimentSync(comment: Comment) {
        if (_sentimentMap.value.containsKey(comment.text)) return
        try {
            val result = sentimentAnalyzer.analyze(comment.text)
            _sentimentMap.update { it + (comment.text to result) }
            _activeUrl.value?.let { repository.saveSentiment(it, comment, result) }
        } catch (_: Exception) { /* non-fatal */ }
    }

    /**
     * Loads previously analyzed sentiment results from the local cache.
     */
    fun loadCached(url: String) {
        viewModelScope.launch {
            repository.getSentiment(url).take(1).collect { cached ->
                val map = cached.associate { it.commentText to SentimentResult(it.label, it.score) }
                if (map.isNotEmpty()) {
                    _sentimentMap.value = map
                }
            }
        }
    }

    /**
     * Forces a refresh of the PagingSource by briefly nulling out the active URL.
     */
    private fun triggerPagerRefresh() {
        val current = _activeUrl.value ?: return
        _activeUrl.value = null
        _activeUrl.value = current
    }

    private fun scheduleBackgroundSync(url: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<CommentSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(workDataOf("POST_URL" to url))
            .build()

        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "CommentSyncWork",
            ExistingPeriodicWorkPolicy.REPLACE, // REPLACE ensures we only sync the latest requested URL
            syncRequest
        )
    }

    private fun SentimentResult.toNormalizedScore() =
        if (label == "POSITIVE") score else 1f - score

    private fun formatSentimentLabel(pct: Int) = when {
        pct >= 70 -> "😊 Mostly Positive ($pct%)"
        pct <= 30 -> "😠 Mostly Negative ($pct%)"
        else -> "😐 Mixed ($pct%)"
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup resources
        viewModelScope.launch(Dispatchers.Default) {
            sentimentAnalyzer.close()
        }
    }

    sealed class ScrapingState {
        object Idle : ScrapingState()
        data class Loading(val message: String) : ScrapingState()
        object Done : ScrapingState()
        data class Error(val message: String) : ScrapingState()
    }
}
