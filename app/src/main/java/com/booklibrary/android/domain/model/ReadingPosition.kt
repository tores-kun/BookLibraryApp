package com.booklibrary.android.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_positions")
data class ReadingPosition(
    @PrimaryKey
    val bookId: Int,
    val chapterIndex: Int,
    val textPosition: Int,
    val scrollPosition: Float,
    val totalProgress: Float,
    val lastReadTimestamp: Long
)