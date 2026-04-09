package com.trendpulse.trendpulse.core.util

import kotlinx.coroutines.flow.*

/**
 * Implements the Network Bound Resource pattern using Kotlin Flow.
 *
 * This function coordinates data flow between a local database and a remote data source,
 * ensuring that the UI observes a single source of truth (the local database).
 *
 * Workflow:
 * 1. Emit cached data from the database immediately
 * 2. Determine whether fresh data should be fetched
 * 3. Fetch data from the network if necessary
 * 4. Persist the fetched data into the database
 * 5. Automatically re-emit updated data from the database
 *
 * This pattern provides:
 * - Offline-first capability
 * - Improved data consistency
 * - Reduced redundant network calls
 *
 * @param query Function to retrieve data from the local database.
 * @param fetch Function to retrieve data from the remote source.
 * @param saveFetchResult Function to persist remote data locally.
 * @param shouldFetch Determines whether a network request is required.
 *
 * @return A Flow emitting [Resource] objects representing the current state.
 */
fun <Local, Remote> networkBoundResource(
    // Load from local DB
    query: () -> Flow<Local>,
    // Fetch from network
    fetch: suspend () -> Remote,
    // Save network result into DB
    saveFetchResult: suspend (Remote) -> Unit,
    // Should we fetch from network? (e.g. cache expired or empty)
    shouldFetch: suspend (Local) -> Boolean = { true }
): Flow<Resource<Local>> = flow {

    // ✅ Step 1 — emit Loading with current DB data immediately
    emit(Resource.Loading(null))
    val localData = query().first()
    emit(Resource.Loading(localData))

    if (shouldFetch(localData)) {
        try {
            // ✅ Step 2 — fetch from network and save to DB
            val remote = fetch()
            saveFetchResult(remote)
            // ✅ Step 3 — DB update causes query() flow to re-emit automatically
            emitAll(query().map { Resource.Success(it) })
        } catch (e: Exception) {
            // ✅ On network failure — still show cached data with error
            emitAll(query().map { Resource.Error(e.message ?: "Unknown error", it) })
        }
    } else {
        // ✅ Cache is fresh — just emit from DB
        emitAll(query().map { Resource.Success(it) })
    }
}