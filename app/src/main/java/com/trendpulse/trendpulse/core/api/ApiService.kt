package com.trendpulse.trendpulse.core.api

import com.trendpulse.trendpulse.core.model.CommentResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query


/**
 * Defines the network API contract for interacting with the remote TrendPulse backend service.
 *
 * This interface uses Retrofit to declare HTTP endpoints responsible for:
 * - Initiating the scraping process for post comments
 * - Retrieving processed comments along with sentiment analysis results
 *
 * Each function represents a suspendable network operation executed asynchronously
 * using Kotlin coroutines.
 */
interface ApiService {

    /**
     * Sends a request to initiate the scraping of comments from a given post URL.
     *
     * @param body A key-value map containing request parameters, typically including
     *             the post video URL.
     */
    @POST("collect")
    suspend fun startScraping(@Body body: Map<String, String>)

    /**
     * Retrieves a paginated list of comments and associated processing metadata.
     *
     * @param postUrl The URL of the post video whose comments are being requested.
     * @param page The page number for pagination.
     * @param limit The maximum number of comments to return per request.
     * @return A [CommentResponse] object containing comments and processing status.
     */
    @GET("comments")
    suspend fun getComments(
        @Query("postUrl") postUrl: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): CommentResponse

}