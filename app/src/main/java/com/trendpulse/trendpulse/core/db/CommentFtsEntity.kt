package com.trendpulse.trendpulse.core.db

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 (Full-Text Search) entity for the comments table.
 * This table is optimized for fast text searching.
 */
@Fts4(contentEntity = CachedComment::class)
@Entity(tableName = "comments_fts")
data class CommentFtsEntity(
    val text: String,
    val user: String
)
