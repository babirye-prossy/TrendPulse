package com.trendpulse.trendpulse.core.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.trendpulse.trendpulse.core.api.ApiService
import com.trendpulse.trendpulse.core.db.AppDatabase
import com.trendpulse.trendpulse.core.db.CachedComment
import retrofit2.HttpException
import java.io.IOException

/**
 * RemoteMediator implementation that coordinates between the remote API and local Room database.
 *
 * This class follows the "Single Source of Truth" pattern where the UI always observes
 * the local database, and the RemoteMediator fetches fresh data when the database is
 * exhausted or empty.
 */
@OptIn(ExperimentalPagingApi::class)
class CommentRemoteMediator(
    private val apiService: ApiService,
    private val db: AppDatabase,
    private val postUrl: String
) : RemoteMediator<Int, CachedComment>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, CachedComment>
    ): MediatorResult {
        return try {
            // ✅ Determine which page to load based on the LoadType
            val loadKey = when (loadType) {
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val count = db.commentDao().countForUrl(postUrl)
                    if (count == 0) return MediatorResult.Success(endOfPaginationReached = false)
                    (count / state.config.pageSize) + 1
                }
            }

            // ✅ Fetch from network
            val response = apiService.getComments(
                postUrl = postUrl,
                page = loadKey,
                limit = state.config.pageSize
            )

            val comments = response.comments ?: emptyList()
            val endOfPaginationReached = comments.size < state.config.pageSize

            // ✅ Save to local database within a transaction
            db.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    db.commentDao().deleteForUrl(postUrl)
                }

                db.commentDao().insertAll(
                    comments.map {
                        CachedComment(
                            postUrl = postUrl,
                            text = it.text,
                            user = it.user
                        )
                    }
                )
            }

            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }

    /**
     * Determines whether we should trigger a refresh on startup.
     * For TrendPulse, we refresh if the cache is empty.
     */
    override suspend fun initialize(): InitializeAction {
        val cacheCount = db.commentDao().countForUrl(postUrl)
        return if (cacheCount == 0) {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            InitializeAction.SKIP_INITIAL_REFRESH
        }
    }
}
