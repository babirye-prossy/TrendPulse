package com.trendpulse.trendpulse.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a cached comment entity stored in the local database.
 *
 * This entity is used to persist comments retrieved from the remote API,
 * enabling offline access and reducing redundant network requests.
 *
 * Each comment is associated with a specific post URL to support
 * partitioned caching and efficient querying.
 */
@Entity(tableName = "comments")
data class CachedComment(

    /**
     * Unique identifier for the cached comment.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val postUrl: String,     // ✅ partition cache by video URL
    val text: String,        //The textual content of the comment
    val user: String,        // the username of the comment author
    val cachedAt: Long = System.currentTimeMillis() // timestamp when the comment was cached
)
