package com.trendpulse.trendpulse.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for performing database operations on cached comments.
 *
 * This interface defines methods for querying, inserting, and deleting comment data
 * from the local Room database. It leverages Kotlin Flow to provide reactive data streams.
 */
@Dao
interface CommentDao {

    /**
     * Retrieves all cached comments associated with a specific post URL.
     *
     * The result is returned as a Flow, allowing observers to react to changes
     * in the underlying database table i.e. re-emits whenever the data changes.
     *
     * @param url The post video URL used to filter comments.
     * @return A Flow emitting a list of [CachedComment] objects.
     */
    @Query("SELECT * FROM comments WHERE postUrl = :url ORDER BY id ASC")
    fun getComments(url: String): Flow<List<CachedComment>>

    /**
     * Inserts a list of comments into the database.
     *
     * Existing entries are replaced in case of conflict.
     *
     * @param comments List of comments to be inserted.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<CachedComment>)

    /**
     * Deletes all comments associated with a specific post URL.
     *
     * @param url The post video URL whose comments should be removed.
     */
    @Query("DELETE FROM comments WHERE postUrl = :url")
    suspend fun deleteForUrl(url: String)

    /**
     * Counts the number of cached comments for a given post URL.
     *
     * @param url The post video URL used for filtering.
     * @return The total number of matching comments.
     */
    @Query("SELECT COUNT(*) FROM comments WHERE postUrl = :url")
    suspend fun countForUrl(url: String): Int

    @Query("SELECT * FROM comments WHERE postUrl = :url ORDER BY id ASC")
    fun getCommentsPaging(url: String): androidx.paging.PagingSource<Int, CachedComment>

    /**
     * Efficiently searches through cached comments using FTS4.
     * Uses the MATCH operator for high-performance text searching.
     *
     * @param query The search query string.
     * @return A Flow emitting a list of matching comments.
     */
    @Query("""
        SELECT comments.* FROM comments 
        JOIN comments_fts ON comments.text = comments_fts.text
        WHERE comments_fts MATCH :query || '*'
    """)
    fun searchComments(query: String): Flow<List<CachedComment>>

    /**
     * Retrieves the URL of the most recently analyzed post.
     */
    @Query("SELECT postUrl FROM comments ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getLastAnalyzedUrl(): String?
}
