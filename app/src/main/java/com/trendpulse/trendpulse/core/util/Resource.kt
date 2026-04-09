package com.trendpulse.trendpulse.core.util

/**
 * Represents a generic wrapper for data along with its loading state.
 *
 * This sealed class is commonly used in modern Android architectures
 * to model asynchronous data streams and communicate state between
 * the data layer and the UI layer.
 *
 * It encapsulates three primary states:
 * - [Loading]: Indicates that a data operation is in progress
 * - [Success]: Indicates that data has been successfully retrieved
 * - [Error]: Indicates that an error occurred during the operation
 *
 * This abstraction improves UI responsiveness and enables consistent
 * handling of network and database states.
 *
 * @param T The type of data being wrapped.
 */
sealed class Resource<T>(val data: T? = null, val message: String? = null) {

    /**
     * Represents a loading state.
     *
     * @param data Optional cached data that can be displayed while loading.
     */
    class Loading<T>(data: T? = null) : Resource<T>(data)

    /**
     * Represents a successful data state.
     *
     * @param data The successfully retrieved data.
     */
    class Success<T>(data: T) : Resource<T>(data)

    /**
     * Represents an error state.
     *
     * @param message Description of the error.
     * @param data Optional cached data available despite the error.
     */
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
}