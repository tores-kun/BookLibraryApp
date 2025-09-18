package com.booklibrary.android.domain.model

data class Book(
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
    val genres: List<String>,
    val bookmark: Bookmark?,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false
)

data class Bookmark(
    val bookId: Int,
    val status: BookmarkStatus,
    val currentChapter: Int,
    val lastUpdated: String
)

enum class BookmarkStatus(val value: String) {
    READING("reading"),
    PAUSED("paused"),
    FINISHED("finished");

    companion object {
        fun fromString(value: String): BookmarkStatus {
            return entries.find { it.value == value } ?: READING
        }
    }
}

data class Note(
    val id: Int,
    val bookId: Int,
    val chapter: Int?,
    val text: String,
    val createdAt: String,
    val updatedAt: String
)

data class ReaderState(
    val bookId: Int,
    val lastLocation: String,
    val fontSize: Float = 16f,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val brightness: Float = 0.5f
)

enum class ReaderTheme(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    SEPIA("sepia")
}

// Старое определение DownloadProgress было здесь и удалено
