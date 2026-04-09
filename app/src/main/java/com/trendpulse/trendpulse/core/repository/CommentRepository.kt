package com.trendpulse.trendpulse.core.repository

import com.trendpulse.trendpulse.core.api.ApiService
import com.trendpulse.trendpulse.core.db.AppDatabase
import com.trendpulse.trendpulse.core.db.CachedComment
import com.trendpulse.trendpulse.core.db.CachedSentiment
import com.trendpulse.trendpulse.core.ml.SentimentResult
import com.trendpulse.trendpulse.core.model.Comment
import com.trendpulse.trendpulse.core.util.Resource
import com.trendpulse.trendpulse.core.util.networkBoundResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.paging.*
import com.trendpulse.trendpulse.core.paging.CommentRemoteMediator

/**
 * Repository layer that mediates between the data sources (network and local database)
 * and the rest of the application.
 *
 * This class follows the Repository design pattern and implements a
 * "single source of truth" principle, where the local database serves as the
 * authoritative data source observed by the UI.
 *
 * It integrates:
 * - Remote data fetching via [ApiService]
 * - Local caching via Room database
 * - Data synchronization using a network-bound resource pattern
 *
 * This abstraction ensures separation of concerns and improves testability.
 */
class CommentRepository(
    private val apiService: ApiService,
    private val db: AppDatabase
) {

    private val commentDao = db.commentDao()
    private val sentimentDao = db.sentimentDao()

    /**
     * Retrieves comments for a given post URL.
     *
     * The method:
     * - Observes cached data from the database
     * - Fetches fresh data from the network when necessary
     * - Updates the local cache accordingly
     *
     * @param postUrl The post video URL.
     * @return A Flow emitting [Resource] containing a list of comments.
     */
    fun getComments(postUrl: String): Flow<Resource<List<Comment>>> =
        networkBoundResource(
            query = {
                // Map DB entities back to domain model
                commentDao.getComments(postUrl).map { cached ->
                    cached.map { Comment(text = it.text, user = it.user) }
                }
            },
            fetch = {
                // Fetch page 1 with large limit to warm cache
                apiService.getComments(postUrl, 1, 100)
            },
            saveFetchResult = { response ->
                // ✅ Clear stale data then insert fresh batch
                commentDao.deleteForUrl(postUrl)
                commentDao.insertAll(
                    response.comments.map {
                        CachedComment(
                            postUrl = postUrl,
                            text = it.text,
                            user = it.user
                        )
                    }
                )
            },
            shouldFetch = { cached ->
                // ✅ Fetch if cache is empty
                cached.isEmpty()
            }
        )

    /**
     * Persists sentiment analysis results in the local database.
     *
     * @param postUrl The associated post video URL.
     * @param comment The comment being analyzed.
     * @param result The sentiment analysis result.
     */
    suspend fun saveSentiment(postUrl: String, comment: Comment, result: SentimentResult) {
        sentimentDao.insertAll(listOf(
            CachedSentiment(
                commentText = comment.text,
                postUrl = postUrl,
                label = result.label,
                score = result.score
            )
        ))
    }

    /**
     * Retrieves cached sentiment analysis results.
     *
     * @param postUrl The post video URL used for filtering.
     * @return A Flow emitting a list of cached sentiment records.
     */
    fun getSentiment(postUrl: String): Flow<List<CachedSentiment>> =
        sentimentDao.getSentiment(postUrl)

    // Add to CommentRepository
    suspend fun getCachedSentimentOnce(postUrl: String): List<CachedSentiment> =
        sentimentDao.getSentimentOnce(postUrl)

    suspend fun getCachedCommentCount(postUrl: String): Int =
        commentDao.countForUrl(postUrl)

    suspend fun getLastAnalyzedUrl(): String? =
        commentDao.getLastAnalyzedUrl()

    /**
     * Searches for comments in the local database using FTS4.
     */
    fun searchComments(query: String): Flow<List<CachedComment>> =
        commentDao.searchComments(query)

    /**
     * Retrieves comments for a given post URL using Paging 3.
     * Room serves as the "Single Source of Truth".
     */
    @OptIn(ExperimentalPagingApi::class)
    fun getCommentsPaged(postUrl: String): Flow<PagingData<CachedComment>> =
        Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = false
            ),
            remoteMediator = CommentRemoteMediator(apiService, db, postUrl),
            pagingSourceFactory = { commentDao.getCommentsPaging(postUrl) }
        ).flow
}
