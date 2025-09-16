package com.booklibrary.android.data.repository

import android.content.Context
import android.util.Log
import com.booklibrary.android.data.local.dao.*
import com.booklibrary.android.data.local.entities.BookEntity
import com.booklibrary.android.data.local.entity.BookGenreEntity
import com.booklibrary.android.data.mapper.*
import com.booklibrary.android.data.remote.api.BookLibraryApiService
import com.booklibrary.android.domain.model.*
import com.booklibrary.android.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val apiService: BookLibraryApiService,
    private val bookDao: BookDao,
    private val genreDao: GenreDao,
    private val bookmarkDao: BookmarkDao,
    private val context: Context
) : BookRepository {

    companion object {
        private const val PAGE_SIZE = 20
    }

    override suspend fun refreshGenres() {
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getGenres()
                if (response.isSuccessful) {
                    response.body()?.let { genreDtos ->
                        val genreEntities = genreDtos.toEntityList()
                        genreDao.clearAndInsertAll(genreEntities)
                    }
                } else {
                    Log.e("BookRepositoryImpl", "Failed to fetch genres: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("BookRepositoryImpl", "Error fetching genres", e)
            }
        }
    }

    override fun getGenres(): Flow<List<Genre>> {
        return genreDao.getAllGenres().map { entities ->
            entities.toDomainList()
        }
    }

    override fun getBooks(
        query: String?,
        genre: String?,
        sort: String,
        order: String,
        bookmarkStatus: String?
    ): Flow<List<Book>> {
        Log.d("BookLoadDebug", "getBooks called. Query: '$query', Genre: '$genre'")

        val localBooksSource: Flow<List<BookEntity>> = when {
            !query.isNullOrBlank() && genre != null -> {
                Log.d("BookLoadDebug", "DAO: searchBooksByQueryAndGenre('%${query}%', '$genre')")
                bookDao.searchBooksByQueryAndGenre("%${query}%", genre)
            }
            !query.isNullOrBlank() -> {
                Log.d("BookLoadDebug", "DAO: searchBooks('%${query}%')")
                bookDao.searchBooks("%${query}%")
            }
            genre != null -> {
                Log.d("BookLoadDebug", "DAO: getBooksByGenre('$genre')")
                bookDao.getBooksByGenre(genre)
            }
            else -> {
                Log.d("BookLoadDebug", "DAO: getAllBooks()")
                bookDao.getAllBooks()
            }
        }

        return localBooksSource.map { bookEntities ->
            Log.d("BookLoadDebug", "Local Flow emitted ${bookEntities.size} bookEntities. Transforming to Domain.")
            val domainBooks = ArrayList<Book>(bookEntities.size)
            for (entity in bookEntities) {
                val genreNamesList = genreDao.getGenresForBook(entity.id).map { it.genreName }
                val bookmarkModel = bookmarkDao.getBookmark(entity.id)?.toDomain()
                domainBooks.add(entity.toDomain(genreNamesList, bookmarkModel))
            }
            Log.d("BookLoadDebug", "Finished transforming ${domainBooks.size} books to Domain.")
            domainBooks
        }
    }

    suspend fun refreshBooksWithParams(query: String?, genre: String?, sort: String, order: String, bookmarkStatus: String?) {
        withContext(Dispatchers.IO) {
            var currentPage = 1
            var totalBooksAvailable = 0
            var booksFetchedInThisRun = 0
            var continueFetching = true

            Log.d("BookLoadDebug", "Starting paginated book fetch. Initial Query: '$query', Genre: '$genre'")

            if (query == null && genre == null) {
                Log.d("BookLoadDebug", "Clearing all book-genre relations and all books before full refresh.")
                genreDao.clearAllBookGenreRelations()
                bookDao.deleteAllBooks()
            }

            do {
                Log.d("BookLoadDebug", "Fetching page $currentPage. Query: '$query', Genre: '$genre', Limit: $PAGE_SIZE")
                try {
                    val response = apiService.getBooks(
                        query = query,
                        genre = genre,
                        sort = sort,
                        order = order,
                        bookmarkStatus = bookmarkStatus,
                        page = currentPage,
                        limit = PAGE_SIZE
                    )

                    if (response.isSuccessful) {
                        val bookResponse = response.body()
                        if (bookResponse != null) {
                            val bookListFromApi = bookResponse.books
                            if (currentPage == 1) {
                                totalBooksAvailable = bookResponse.total
                                Log.d("BookLoadDebug", "Total books available from API: $totalBooksAvailable")
                            }

                            Log.d("BookLoadDebug", "API page $currentPage returned ${bookListFromApi.size} books. Expected limit: ${bookResponse.limit}")

                            if (bookListFromApi.isNotEmpty()) {
                                bookListFromApi.forEach { bookDto ->
                                    try {
                                        bookDao.insertBook(bookDto.toEntity())
                                        if (bookDto.genres.isNotEmpty()) {
                                            val bookGenreEntities = bookDto.genres.map {
                                                BookGenreEntity(bookId = bookDto.id, genreName = it.trim())
                                            }
                                            genreDao.insertBookGenres(bookGenreEntities)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("BookLoadDebug", "Error inserting book DTO ID ${bookDto.id} or its genres into Room", e)
                                    }
                                }
                                booksFetchedInThisRun += bookListFromApi.size
                                currentPage++
                                if (bookListFromApi.size < PAGE_SIZE || booksFetchedInThisRun >= totalBooksAvailable && totalBooksAvailable > 0) {
                                    continueFetching = false
                                    Log.d("BookLoadDebug", "Reached end of data. Fetched ${bookListFromApi.size}, PageSize $PAGE_SIZE, Total Fetched $booksFetchedInThisRun, Total Available $totalBooksAvailable")
                                }
                            } else {
                                continueFetching = false
                                Log.d("BookLoadDebug", "API returned an empty list of books on page $currentPage. Stopping.")
                            }
                        } else {
                            Log.e("BookLoadDebug", "API response body is null for page $currentPage.")
                            continueFetching = false
                        }
                    } else {
                        Log.e("BookLoadDebug", "Network error on page $currentPage: ${response.code()} - ${response.message()}. Body: ${response.errorBody()?.string()}")
                        continueFetching = false 
                    }
                } catch (e: Exception) {
                    Log.e("BookLoadDebug", "Exception during network fetch or processing for page $currentPage", e)
                    continueFetching = false 
                }
            } while (continueFetching && booksFetchedInThisRun < totalBooksAvailable && totalBooksAvailable > 0)
            
            Log.d("BookLoadDebug", "Finished paginated book fetch. Total books fetched in this run: $booksFetchedInThisRun. Current page reached: ${currentPage -1}")
        }
    }

    override suspend fun refreshBooks() {
        Log.d("BookLoadDebug", "refreshBooks (global) called, will trigger paginated refreshBooksWithParams")
        refreshBooksWithParams(null, null, "date_added", "desc", null)
    }

    override suspend fun getBookById(bookId: Int): Book? = withContext(Dispatchers.IO) {
        bookDao.getBookById(bookId)?.let { entity ->
            val genreNames = genreDao.getGenresForBook(entity.id).map { it.genreName }
            val bookmark = bookmarkDao.getBookmark(entity.id)?.toDomain()
            return@withContext entity.toDomain(genreNames, bookmark)
        }

        Log.d("BookLoadDebug", "Book $bookId not in cache, fetching from network.")
        try {
            val response = apiService.getBookById(bookId)
            if (response.isSuccessful) {
                response.body()?.let { bookDto ->
                    Log.d("BookLoadDebug", "Fetched book $bookId from network: ${bookDto.title}")
                    bookDao.insertBook(bookDto.toEntity())
                    if (bookDto.genres.isNotEmpty()) {
                        val bookGenreEntities = bookDto.genres.map { genreName ->
                            BookGenreEntity(bookId = bookDto.id, genreName = genreName.trim())
                        }
                        genreDao.insertBookGenres(bookGenreEntities)
                    }
                    val newEntity = bookDao.getBookById(bookId)
                    return@withContext newEntity?.let {
                        val genres = genreDao.getGenresForBook(it.id).map { g -> g.genreName }
                        val bm = bookmarkDao.getBookmark(it.id)?.toDomain()
                        it.toDomain(genres, bm)
                    }
                }
            } else {
                Log.e("BookLoadDebug", "Network error fetching book by id $bookId: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("BookLoadDebug", "Exception fetching book by id $bookId from network", e)
        }
        return@withContext null
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
    }

    override suspend fun deleteBookmark(bookId: Int) {
        bookmarkDao.getBookmark(bookId)?.let { bookmark ->
            bookmarkDao.deleteBookmark(bookmark)
        }
    }

    override suspend fun downloadBook(bookId: Int): Flow<DownloadProgress> = flow {
        emit(DownloadProgress(bookId, 0.0f))
        kotlinx.coroutines.delay(1000)
        emit(DownloadProgress(bookId, 0.5f))
        kotlinx.coroutines.delay(1000)
        emit(DownloadProgress(bookId, 1.0f, isComplete = true))
    }

    override suspend fun isBookDownloaded(bookId: Int): Boolean {
        return bookDao.getBookById(bookId)?.isDownloaded ?: false
    }
}
