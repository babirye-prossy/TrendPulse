package com.trendpulse.trendpulse.core.model


/**
 * Represents the response returned from the comment processing or sentiment analysis API.
 *
 * This class encapsulates the current processing state along with metadata such as:
 * - The current stage of processing (e.g., fetching, analyzing, completed)
 * - Progress percentage of the operation
 * - Estimated time remaining (ETA) in seconds
 * - The list of processed comments
 *
 * It is commonly used to track long-running operations and provide feedback
 * to the UI layer in real-time.
 */
data class CommentResponse(
    val stage: String,
    val progress: Int,
    val eta: Int,
    val comments: List<Comment>
)