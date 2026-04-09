package com.trendpulse.trendpulse.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing sentiment analysis records in the local database.
 *
 * This interface provides methods to retrieve, insert, and delete sentiment data
 * associated with cached comments. It supports reactive data observation using Kotlin Flow.
 */
@Dao
interface SentimentDao {

    /**
     * Retrieves all sentiment records associated with a specific post URL.
     *
     * @param url The post video URL used to filter sentiment data.
     * @return A Flow emitting a list of [CachedSentiment] objects.
     */
    @Query("SELECT * FROM sentiment WHERE postUrl = :url")
    fun getSentiment(url: String): Flow<List<CachedSentiment>>

    /**
     * Inserts a list of sentiment records into the database.
     *
     * Existing records are replaced in case of conflict.
     *
     * @param sentiments List of sentiment records to be inserted.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sentiments: List<CachedSentiment>)

    /**
     * Deletes all sentiment records associated with a specific post URL.
     *
     * @param url The post video URL whose sentiment data should be removed.
     */
    @Query("DELETE FROM sentiment WHERE postUrl = :url")
    suspend fun deleteForUrl(url: String)

    // Add to SentimentDao
    @Query("SELECT * FROM sentiment WHERE postUrl = :url")
    suspend fun getSentimentOnce(url: String): List<CachedSentiment>
}
