package com.booklibrary.android.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val description: String,
    val chapterCount: Int,
    val coverUrl: String?,
    val epubUrl: String?,
    val language: String,
    val translator: String,
    val status: String,
    val dateAdded: String,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false
)

@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)

@Entity(
    tableName = "book_genres",
    primaryKeys = ["bookId", "genreName"]
)
data class BookGenreEntity(
    val bookId: Int,
    val genreName: String
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey
    val bookId: Int,
    val status: String,
    val currentChapter: Int,
    val lastUpdated: String
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val bookId: Int,
    val chapter: Int?,
    val text: String,
    val createdAt: String,
    val updatedAt: String
)

@Entity(tableName = "reader_state")
data class ReaderStateEntity(
    @PrimaryKey
    val bookId: Int,
    val lastLocation: String,
    val fontSize: Float,
    val theme: String,
    val brightness: Float
)
