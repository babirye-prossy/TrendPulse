package com.trendpulse.trendpulse.core.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trendpulse.trendpulse.core.api.RetrofitClient
import com.trendpulse.trendpulse.core.db.AppDatabase
import com.trendpulse.trendpulse.core.ml.SentimentAnalyzer
import com.trendpulse.trendpulse.core.model.Comment
import com.trendpulse.trendpulse.core.repository.CommentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A background worker that periodically fetches new comments, performs sentiment analysis,
 * and updates the local database using the existing application modules.
 *
 * Implements CoroutineWorker to ensure all operations are off-loaded from the main thread.
 */
class CommentSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context)
    private val apiService = RetrofitClient.apiService
    private val repository = CommentRepository(apiService, db)
    private val sentimentAnalyzer = SentimentAnalyzer.getInstance(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val postUrl = inputData.getString("POST_URL") ?: return@withContext Result.failure()

        return@withContext try {
            Log.d("CommentSyncWorker", "Starting background sync for $postUrl")

            // 1. Fetch new comments using existing ApiService
            val response = apiService.getComments(postUrl, 1, 50)
            val comments = response.comments ?: emptyList()

            if (comments.isEmpty()) {
                Log.d("CommentSyncWorker", "No new comments found")
                return@withContext Result.success()
            }

            // 2. Perform Sentiment Analysis on Dispatchers.Default (sequential processing)
            comments.forEach { comment ->
                val result = withContext(Dispatchers.Default) {
                    sentimentAnalyzer.analyze(comment.text)
                }
                
                // 3. Save results using the Repository (Single Source of Truth)
                repository.saveSentiment(
                    postUrl = postUrl,
                    comment = Comment(text = comment.text, user = comment.user),
                    result = result
                )
            }

            Log.d("CommentSyncWorker", "Background sync completed successfully for $postUrl")
            Result.success()
        } catch (e: Exception) {
            Log.e("CommentSyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
