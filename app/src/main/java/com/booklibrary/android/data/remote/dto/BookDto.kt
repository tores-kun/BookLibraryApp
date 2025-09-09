package com.booklibrary.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class BookResponse(
    val books: List<BookDto>,
    val total: Int,
    val page: Int,
    val limit: Int
)

data class BookDto(
    val id: Int,
    val title: String,
    val description: String,
    @SerializedName("chapter_count")
    val chapterCount: Int,
    @SerializedName("cover_url")
    val coverUrl: String?,
    @SerializedName("epub_url")
    val epubUrl: String?,
    val language: String,
    val translator: String,
    val status: String,
    @SerializedName("date_added")
    val dateAdded: String,
    val genres: List<String>,
    val bookmark: BookmarkDto?
)

data class BookmarkDto(
    val status: String,
    @SerializedName("current_chapter")
    val currentChapter: Int,
    @SerializedName("last_updated")
    val lastUpdated: String
)

data class GenreDto(
    val name: String,
    val count: Int
)

data class NoteDto(
    val id: Int? = null,
    @SerializedName("book_id")
    val bookId: Int,
    val chapter: Int?,
    val text: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

data class CreateBookmarkRequest(
    @SerializedName("book_id")
    val bookId: Int,
    val status: String,
    @SerializedName("current_chapter")
    val currentChapter: Int
)

data class DeleteBookmarkRequest(
    @SerializedName("book_id")
    val bookId: Int
)

data class CreateNoteRequest(
    @SerializedName("book_id")
    val bookId: Int,
    val chapter: Int?,
    val text: String
)

data class UpdateNoteRequest(
    val text: String
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)
