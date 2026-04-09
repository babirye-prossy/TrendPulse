// core/model/SentimentTrendPoint.kt
package com.trendpulse.trendpulse.core.model


/**
 * Represents a single data point in a sentiment trend analysis.
 *
 * Each point corresponds to a comment's position in a sequence and its
 * associated sentiment score.
 *
 * - `index` indicates the position of the comment (used as the x-axis in charts)
 * - `score` represents the sentiment value, typically normalized between:
 *      0.0 (negative sentiment) and 1.0 (positive sentiment)
 *
 * This model is primarily used for visualizing sentiment trends over time
 * in graphs or charts.
 */
data class SentimentTrendPoint(
    val index: Int,        // comment number (x axis)
    val score: Float       // 0.0 negative → 1.0 positive (y axis)
)