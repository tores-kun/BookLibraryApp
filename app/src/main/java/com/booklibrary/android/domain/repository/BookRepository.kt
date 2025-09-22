package com.booklibrary.android.domain.repository

import com.booklibrary.android.domain.model.*
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getBooks(
        query: String? = null,
        genre: String? = null,
        sort: String = "date_added",
        order: String = "desc",
        bookmarkStatus: String? = null
    ): Flow<List<Book>>

    suspend fun getBookById(bookId: Int): Book?
    suspend fun refreshBooks()
    suspend fun refreshGenres()

    fun getBookmarks(): Flow<List<Bookmark>>
    fun getBookmarksByStatus(status: String): Flow<List<Bookmark>>
    suspend fun createBookmark(bookId: Int, status: String, currentChapter: Int)
    suspend fun deleteBookmark(bookId: Int)

    fun getGenres(): Flow<List<Genre>>

    suspend fun downloadBook(bookId: Int): Flow<DownloadProgress>
    suspend fun isBookDownloaded(bookId: Int): Boolean
    suspend fun updateBookDownloadStatus(bookId: Int, isDownloaded: Boolean, localFilePath: String?) // <--- НОВЫЙ МЕТОД
}

interface NoteRepository {
    fun getNotesForBook(bookId: Int): Flow<List<Note>>
    fun getNotesForChapter(bookId: Int, chapter: Int): Flow<List<Note>>
    suspend fun createNote(bookId: Int, chapter: Int?, text: String): Result<Note>
    suspend fun updateNote(noteId: Int, text: String): Result<Note>
    suspend fun deleteNote(noteId: Int): Result<Unit>
}

interface ReaderRepository {
    suspend fun getReaderState(bookId: Int): ReaderState?
    suspend fun saveReaderState(state: ReaderState)
    suspend fun getEpubContent(bookId: Int): String?
}

interface SettingsRepository {
    suspend fun getBaseUrl(): String
    suspend fun setBaseUrl(url: String)
    suspend fun getNetworkProfile(): String
    suspend fun setNetworkProfile(profile: String)
    suspend fun getTtsSettings(): TtsSettings
    suspend fun saveTtsSettings(settings: TtsSettings)
}

data class TtsSettings(
    val language: String = "ru",
    val voice: String = "",
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f
)
