package com.trendpulse.trendpulse.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Represents the main local database for the TrendPulse application using Room persistence library.
 *
 * This database serves as a centralized storage mechanism for caching:
 * - User comments retrieved from external sources
 * - Sentiment analysis results associated with those comments
 *
 * It follows the singleton design pattern to ensure a single database instance
 * is maintained throughout the application lifecycle.
 */
@Database(
    entities = [CachedComment::class, CachedSentiment::class, CommentFtsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Provides access to comment-related database operations.
     */
    abstract fun commentDao(): CommentDao

    /**
     * Provides access to sentiment-related database operations.
     */
    abstract fun sentimentDao(): SentimentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Retrieves the singleton instance of the database.
         *
         * @param context Application context used to initialize the database.
         * @return A single instance of [AppDatabase].
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trendpulse_db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}