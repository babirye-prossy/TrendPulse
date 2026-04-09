package com.trendpulse.trendpulse.core.network

import com.trendpulse.trendpulse.core.model.Comment

/**
 * Represents the different types of events emitted during a WebSocket session.
 *
 * This sealed class defines a strongly-typed event system for handling real-time
 * communication between the client and the backend service.
 *
 * Events include:
 * - Connection lifecycle updates
 * - Scraping progress notifications
 * - Incremental delivery of comments
 * - Completion and error states
 *
 * This abstraction enables reactive handling of streaming data within the application.
 */
sealed class WebSocketEvent {

    /** Indicates that the WebSocket connection has been successfully established. */
    object Connected : WebSocketEvent()

    /** Indicates that the WebSocket connection has been terminated. */
    object Disconnected : WebSocketEvent()

    /** Signals the start of the scraping process on the server. */
    object ScrapeStarted : WebSocketEvent()

    /**
     * Represents a batch of newly received comments along with progress metadata.
     *
     * @property comments List of newly fetched comments.
     * @property progress Current progress percentage.
     * @property total Total number of comments expected.
     */
    data class NewComments(
        val comments: List<Comment>,
        val progress: Int,
        val total: Int
    ) : WebSocketEvent()

    /**
     * Indicates that the scraping process has completed.
     *
     * @property total Total number of comments retrieved.
     */
    data class Done(val total: Int) : WebSocketEvent()

    /**
     * Represents an error encountered during the WebSocket session.
     *
     * @property message Description of the error.
     */
    data class Error(val message: String) : WebSocketEvent()
}