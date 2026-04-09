package com.trendpulse.trendpulse.core.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.trendpulse.trendpulse.core.api.ApiService
import com.trendpulse.trendpulse.core.model.Comment
import kotlinx.coroutines.delay

/**
 * PagingSource implementation for loading comments in a paginated manner
 * from the remote API.
 *
 * This class integrates with the Android Paging 3 library to support
 * efficient, incremental data loading and smooth scrolling in the UI.
 *
 * It handles:
 * - Page-based data retrieval
 * - Error handling during network requests
 * - Determination of previous and next page keys
 *
 * The paging mechanism reduces memory usage and improves performance
 * when dealing with large datasets such as social media comments.
 */
class CommentPagingSource(
    private val apiService: ApiService,
    private val postUrl: String
) : PagingSource<Int, Comment>() {

    /**
     * Loads a page of comments from the API.
     *
     * @param params Parameters defining the requested page and load size.
     * @return A [LoadResult] containing the loaded data or an error.
     */
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Comment> {
        val page = params.key ?: 1
        return try {
            val response = apiService.getComments(postUrl, page, params.loadSize)
            val comments = response.comments ?: emptyList()

            LoadResult.Page(
                data = comments,
                prevKey = if (page == 1) null else page - 1,
                // ✅ Stop only when server returns fewer items than requested
                // stage is always "done" now so it can't be used as a signal
                nextKey = if (comments.size < params.loadSize) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /**
     * Determines the key for reloading data closest to the current viewport position.
     *
     * @param state The current paging state.
     * @return The key for the page to be reloaded.
     */
    override fun getRefreshKey(state: PagingState<Int, Comment>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}