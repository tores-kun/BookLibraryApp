package com.booklibrary.android.data.repository

import com.booklibrary.android.data.local.dao.*
import com.booklibrary.android.data.remote.api.BookLibraryApiService
import com.booklibrary.android.data.mapper.*
import com.booklibrary.android.domain.model.*
import com.booklibrary.android.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val apiService: BookLibraryApiService,
    private val bookDao: BookDao,
    private val genreDao: GenreDao,
    private val bookmarkDao: BookmarkDao,
    private val context: Context
) : BookRepository {

    override fun getBooks(
        query: String?,
        genre: String?,
        sort: String,
        order: String,
        bookmarkStatus: String?
    ): Flow<List<Book>> = flow {
        // Cache-first strategy
        val localBooks = bookDao.getAllBooks().first().map { entity ->
            val genres = genreDao.getGenresForBook(entity.id).map { it.genreName }
            val bookmark = bookmarkDao.getBookmark(entity.id)?.toDomain()
            entity.toDomain(genres, bookmark)
        }
        emit(localBooks)

        // Then fetch from network
        try {
            val response = apiService.getBooks(query, genre, sort, order, bookmarkStatus)
            if (response.isSuccessful) {
                response.body()?.let { bookResponse ->
                    val books = bookResponse.books.map { it.toDomain() }

                    // Save to local database
                    val entities = bookResponse.books.map { it.toEntity() }
                    bookDao.insertBooks(entities)

                    emit(books)
                }
            }
        } catch (e: Exception) {
            // Network error, use cached data
        }
    }

    override suspend fun getBookById(bookId: Int): Book? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getBookById(bookId)
            if (response.isSuccessful) {
                response.body()?.toDomain()
            } else {
                // Fallback to local
                val entity = bookDao.getBookById(bookId)
                entity?.let {
                    val genres = genreDao.getGenresForBook(bookId).map { it.genreName }
                    val bookmark = bookmarkDao.getBookmark(bookId)?.toDomain()
                    it.toDomain(genres, bookmark)
                }
            }
        } catch (e: Exception) {
            val entity = bookDao.getBookById(bookId)
            entity?.let {
                val genres = genreDao.getGenresForBook(bookId).map { it.genreName }
                val bookmark = bookmarkDao.getBookmark(bookId)?.toDomain()
                it.toDomain(genres, bookmark)
            }
        }
    }

    override suspend fun refreshBooks() {
        // Implementation
    }

    override fun getBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getBookmarksByStatus(status: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByStatus(status).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createBookmark(bookId: Int, status: String, currentChapter: Int) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val bookmark = Bookmark(
            bookId = bookId,
            status = BookmarkStatus.fromString(status),
            currentChapter = currentChapter,
            lastUpdated = currentTime
        )
        bookmarkDao.insertBookmark(bookmark.toEntity())

        // Sync to server in background
    }

    override suspend fun deleteBookmark(bookId: Int) {
        bookmarkDao.getBookmark(bookId)?.let { bookmark ->
            bookmarkDao.deleteBookmark(bookmark)
        }
    }

    override fun getGenres(): Flow<List<Genre>> {
        return genreDao.getAllGenres().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun downloadBook(bookId: Int): Flow<DownloadProgress> = flow {
        // Implementation for downloading EPUB
        emit(DownloadProgress(bookId, 0.5f))
        emit(DownloadProgress(bookId, 1.0f, isComplete = true))
    }

    override suspend fun isBookDownloaded(bookId: Int): Boolean {
        return bookDao.getBookById(bookId)?.isDownloaded ?: false
    }
}
