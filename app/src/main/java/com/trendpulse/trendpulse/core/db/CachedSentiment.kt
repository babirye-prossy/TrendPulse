package com.trendpulse.trendpulse.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a cached sentiment analysis result for a specific comment.
 *
 * This entity stores the sentiment classification and score derived from
 * processing a comment, allowing reuse of results without repeated computation.
 *
 * The comment text serves as the primary key to ensure uniqueness and
 * consistency with in-memory sentiment mappings.
 */
@Entity(tableName = "sentiment")
data class CachedSentiment(

    @PrimaryKey
    val commentText: String,   // ✅ keyed by comment text, same as sentimentMap

    val postUrl: String,       // The post video URL associated with the comment
    val label: String,         // The sentiment label, typically "POSITIVE" or "NEGATIVE"
    val score: Float,          // The sentiment score, ranging from 0.0 to 1.0
    val cachedAt: Long = System.currentTimeMillis() // timestamp when the sentiment was cached

)
