package com.trendpulse.trendpulse.core.model

/**
 * Represents a single user-generated comment within the TrendPulse system.
 *
 * This model is used to store basic comment information, including the
 * textual content of the comment and the username of the person who posted it.
 *
 * It is typically used as part of API responses or data processing pipelines
 * for sentiment analysis.
 */
data class Comment(
    val text: String,
    val user: String
)