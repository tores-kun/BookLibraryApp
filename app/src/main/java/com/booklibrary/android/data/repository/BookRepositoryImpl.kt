package com.booklibrary.android.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.booklibrary.android.data.local.dao.*
import com.booklibrary.android.data.local.entities.BookEntity
import com.booklibrary.android.data.local.entity.BookGenreEntity
import com.booklibrary.android.data.mapper.*
import com.booklibrary.android.data.remote.api.BookLibraryApiService
import com.booklibrary.android.domain.model.*
import com.booklibrary.android.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
        private const val EPUB_MIME_TYPE = "application/epub+zip"
        private const val TAG = "BookRepositoryImpl"
        private const val DOWNLOAD_SUB_DIR = "BookLibraryApp"
    }

    private fun getFileNameFromHeaders(headers: okhttp3.Headers): String? {
        val contentDisposition = headers["Content-Disposition"]
        if (contentDisposition != null) {
            val patterns = listOf(
                Regex("""filename\*="?'?UTF-8''([^";]+)"?'?""", RegexOption.IGNORE_CASE),
                Regex("""filename\s*=\s*\"([^\"]+)\""""", RegexOption.IGNORE_CASE)
            )
            for (pattern in patterns) {
                val match = pattern.find(contentDisposition)
                if (match != null && match.groupValues.size > 1) {
                    var filenameSource = match.groupValues[1]
                    var decodedFilename = filenameSource
                    val isFilenameStar = pattern.pattern.contains("filename*")
                    try {
                        if (isFilenameStar) {
                            decodedFilename = java.net.URLDecoder.decode(filenameSource, "UTF-8")
                        } else if (filenameSource.contains("%")) {
                            decodedFilename = java.net.URLDecoder.decode(filenameSource, "UTF-8")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getFileNameFromHeaders: Error decoding '$filenameSource', using raw.", e)
                    }
                    return File(decodedFilename).name
                }
            }
        }
        return null
    }

    override suspend fun refreshGenres() {
        withContext(Dispatchers.IO) {
            try {
                apiService.getGenres().body()?.let { genreDao.clearAndInsertAll(it.toEntityList()) }
            } catch (e: Exception) { Log.e(TAG, "Error refreshing genres", e) }
        }
    }

    override fun getGenres(): Flow<List<Genre>> = genreDao.getAllGenres().map { it.toDomainList() }

    override fun getBooks(
        query: String?, genre: String?, sort: String, order: String, bookmarkStatus: String?
    ): Flow<List<Book>> {
        val source = when {
            !query.isNullOrBlank() && genre != null -> bookDao.searchBooksByQueryAndGenre("%${query}%", genre)
            !query.isNullOrBlank() -> bookDao.searchBooks("%${query}%")
            genre != null -> bookDao.getBooksByGenre(genre)
            else -> bookDao.getAllBooks()
        }
        return source.map { entities ->
            entities.map {
                val genres = genreDao.getGenresForBook(it.id).map { g -> g.genreName }
                val bookmark = bookmarkDao.getBookmark(it.id)?.toDomain()
                it.toDomain(genres, bookmark)
            }
        }
    }

    suspend fun refreshBooksWithParams(query: String?, genre: String?, sort: String, order: String, bookmarkStatus: String?) {
        withContext(Dispatchers.IO) {
            var currentPage = 1
            var totalBooksAvailable = 0
            var booksFetchedThisRun = 0
            var continueFetching = true
            Log.d(TAG, "Refreshing books. query=$query, genre=$genre. Local data will be updated/augmented.")
            do {
                try {
                    val response = apiService.getBooks(query, genre, sort, order, bookmarkStatus, currentPage, PAGE_SIZE)
                    if (response.isSuccessful) {
                        val bookResponse = response.body()
                        if (bookResponse != null) {
                            val apiBooks = bookResponse.books
                            if (currentPage == 1) {
                                totalBooksAvailable = bookResponse.total
                                if (query == null && genre == null && bookmarkStatus == null) {
                                    genreDao.clearAllBookGenreRelations()
                                }
                            }
                            if (apiBooks.isNotEmpty()) {
                                apiBooks.forEach { dto ->
                                    val existing = bookDao.getBookById(dto.id)
                                    val entity = dto.toEntity().copy(
                                        isDownloaded = existing?.isDownloaded ?: false,
                                        localFilePath = existing?.localFilePath
                                    )
                                    bookDao.insertBook(entity)
                                    genreDao.deleteBookGenresForBook(dto.id)
                                    if (dto.genres.isNotEmpty()) {
                                        genreDao.insertBookGenres(dto.genres.map { BookGenreEntity(dto.id, it.trim()) })
                                    }
                                }
                                booksFetchedThisRun += apiBooks.size
                                currentPage++
                                if (apiBooks.size < PAGE_SIZE || (totalBooksAvailable > 0 && booksFetchedThisRun >= totalBooksAvailable)) {
                                    continueFetching = false
                                }
                            } else { continueFetching = false }
                        } else { continueFetching = false }
                    } else {
                        Log.e(TAG, "API error: ${response.code()} ${response.message()}")
                        continueFetching = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during refreshBooks page $currentPage", e)
                    continueFetching = false
                }
            } while (continueFetching)
        }
    }

    override suspend fun refreshBooks() {
        refreshBooksWithParams(null, null, "date_added", "desc", null)
    }

    override suspend fun getBookById(bookId: Int): Book? = withContext(Dispatchers.IO) {
        var entity = bookDao.getBookById(bookId)
        if (entity == null) { // Not in DB, try fetching from network
            try {
                apiService.getBookById(bookId).body()?.let { dto ->
                    val newEntity = dto.toEntity().copy(isDownloaded = false, localFilePath = null)
                    bookDao.insertBook(newEntity)
                    genreDao.deleteBookGenresForBook(dto.id)
                    if (dto.genres.isNotEmpty()) {
                        genreDao.insertBookGenres(dto.genres.map { BookGenreEntity(dto.id, it.trim()) })
                    }
                    entity = newEntity
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching book by id $bookId from network", e)
                return@withContext null
            }
        }
        entity?.let {
            val currentEntity = it // effectively final for lambda
            val genres = genreDao.getGenresForBook(currentEntity.id).map { g -> g.genreName }
            val bookmark = bookmarkDao.getBookmark(currentEntity.id)?.toDomain()
            // Ensure download status is accurate by checking filesystem, potentially updating DB
            if (isBookDownloaded(currentEntity.id)) {
                // isBookDownloaded might have updated the DB entry, so re-fetch for accurate localFilePath
                val updatedEntity = bookDao.getBookById(currentEntity.id)!! 
                return@withContext updatedEntity.toDomain(genres, bookmark)
            }
            return@withContext currentEntity.toDomain(genres, bookmark) // Return with potentially outdated download status if fs check failed to find it
        }
        return@withContext null
    }

    override fun getBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks().map { it.map { bm -> bm.toDomain() } }
    override fun getBookmarksByStatus(status: String): Flow<List<Bookmark>> = bookmarkDao.getBookmarksByStatus(status).map { it.map { bm -> bm.toDomain() } }

    override suspend fun createBookmark(bookId: Int, status: String, currentChapter: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val bm = Bookmark(bookId, BookmarkStatus.fromString(status), currentChapter, sdf.format(Date()))
        bookmarkDao.insertBookmark(bm.toEntity())
    }

    override suspend fun deleteBookmark(bookId: Int) {
        bookmarkDao.getBookmark(bookId)?.let { bookmarkDao.deleteBookmark(it) }
    }

    private fun verifyFilePath(filePath: String, bookIdForLog: Int): Boolean {
        if (filePath.isBlank()) return false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && filePath.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(filePath), "r")?.use { pfd ->
                    return if (pfd.statSize > 0) true else {
                        Log.w(TAG, "verifyFilePath (Q+): File $filePath for book $bookIdForLog empty.")
                        false
                    }
                }
                Log.w(TAG, "verifyFilePath (Q+): Could not get PFD for $filePath (book $bookIdForLog).")
                return false
            } else {
                val file = File(filePath)
                return if (file.exists() && file.isFile && file.length() > 0) true else {
                    Log.w(TAG, "verifyFilePath (<Q or non-content URI): File $filePath for $bookIdForLog invalid.")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "verifyFilePath: Exception for $filePath (book $bookIdForLog)", e)
            return false
        }
    }
    
    private fun generateFallbackBookFileName(title: String, bookId: Int): String {
        val cleanTitle = title.trim().replace(Regex("[^a-zA-Z0-9.-]+"), "_").take(50)
        return "${cleanTitle}_${bookId}.epub"
    }

    private fun getCurrentFormattedDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override suspend fun isBookDownloaded(bookId: Int): Boolean = withContext(Dispatchers.IO) {
        var bookEntity = bookDao.getBookById(bookId)

        // Scenario 1: DB has a valid path, verify it.
        if (bookEntity?.isDownloaded == true && !bookEntity.localFilePath.isNullOrBlank()) {
            if (verifyFilePath(bookEntity.localFilePath!!, bookId)) {
                Log.d(TAG, "isBookDownloaded: Book $bookId confirmed via DB path ${bookEntity.localFilePath}.")
                return@withContext true
            } else {
                Log.w(TAG, "isBookDownloaded: DB path ${bookEntity.localFilePath} for $bookId invalid. Correcting DB.")
                bookDao.updateBook(bookEntity.copy(isDownloaded = false, localFilePath = null))
                bookEntity = bookEntity.copy(isDownloaded = false, localFilePath = null) // Update local var for fallback
            }
        }

        // Scenario 2: Fallback - DB doesn't confirm, or path was invalidated. Try to find file by expected name.
        val titleForFallback: String?
        if (bookEntity == null) { // Book not in DB at all, try to fetch its title from server for filename generation
            Log.d(TAG, "isBookDownloaded: Book $bookId not in DB. Fetching title from server for fallback filename.")
            titleForFallback = try { apiService.getBookById(bookId).body()?.title } catch (e:Exception) { null }
            if (titleForFallback.isNullOrBlank()) {
                Log.w(TAG, "isBookDownloaded: Could not get title for $bookId from server for fallback. Cannot check.")
                return@withContext false
            }
        } else { // Book is in DB, use its title (or fetch from server for freshness)
            Log.d(TAG, "isBookDownloaded: Book $bookId in DB, using its title or fetching fresh server title for fallback.")
            val serverTitle = try { apiService.getBookById(bookId).body()?.title } catch (e:Exception) { null }
            titleForFallback = serverTitle ?: bookEntity.title // Prioritize server title
        }
        
        if (titleForFallback.isNullOrBlank()) {
             Log.w(TAG, "isBookDownloaded: No title available for book $bookId (local or server). Cannot generate fallback filename.")
            return@withContext false
        }

        val fallbackFileName = generateFallbackBookFileName(titleForFallback, bookId)
        Log.d(TAG, "isBookDownloaded: Trying fallback check for $bookId with filename: $fallbackFileName")

        var foundValidFallbackPath: String? = null
        val targetDirName = DOWNLOAD_SUB_DIR

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
            val relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + targetDirName + File.separator
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(relativePath, fallbackFileName)
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            try {
                resolver.query(collection, projection, selection, selectionArgs, null)?.use {
                    if (it.moveToFirst()) {
                        if (it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)) > 0) {
                            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                            foundValidFallbackPath = ContentUris.withAppendedId(collection, id).toString()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "isBookDownloaded (Q+ fallback): Error querying MediaStore for $fallbackFileName", e)
            }
        } else {
            @Suppress("DEPRECATION")
            val appDownloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), targetDirName)
            if (appDownloadsDir.exists() && appDownloadsDir.isDirectory) {
                val file = File(appDownloadsDir, fallbackFileName)
                if (file.exists() && file.isFile && file.length() > 0) {
                    foundValidFallbackPath = file.absolutePath
                }
            }
        }

        if (foundValidFallbackPath != null) {
            Log.i(TAG, "isBookDownloaded: Fallback file found for $bookId at $foundValidFallbackPath. Updating DB.")
            // If bookEntity was null (because book wasn't in DB), create a minimal one.
            val entityToUpsert = bookEntity ?: BookEntity(
                id = bookId, 
                title = titleForFallback, 
                description = "", // Default value
                chapterCount = 0, // Default value
                coverUrl = null, // Default value
                epubUrl = null, // Default value
                language = "", // Default value
                translator = "", // Default value
                status = "", // Default value
                dateAdded = getCurrentFormattedDateString(),
                localFilePath = null, // Will be overridden by copy
                isDownloaded = false // Will be overridden by copy
            )
            bookDao.updateBook(entityToUpsert.copy(isDownloaded = true, localFilePath = foundValidFallbackPath))
            return@withContext true
        }

        Log.d(TAG, "isBookDownloaded: Book $bookId not downloaded (DB and fallback check failed).")
        return@withContext false
    }

    override suspend fun downloadBook(bookId: Int): Flow<DownloadProgress> = flow {
        var outputStream: OutputStream? = null
        var inputStream: InputStream? = null
        var fileUri: Uri? = null
        var finalFilePath: String? = null
        var bytesCopied = 0L
        var contentLength = -1L

        Log.d(TAG, "downloadBook: Starting for $bookId")
        if (isBookDownloaded(bookId)) {
            val confirmedBookEntity = withContext(Dispatchers.IO) { bookDao.getBookById(bookId)!! } 
            Log.i(TAG, "downloadBook: Book $bookId already downloaded and valid at ${confirmedBookEntity.localFilePath}.")
            emit(DownloadProgress(bookId, 1f, true, false, confirmedBookEntity.localFilePath, null, true))
            return@flow
        }
        emit(DownloadProgress(bookId, 0f, false, true, null, null, false))
        var bookTitleForFileName: String? = null

        try {
            val localBook = withContext(Dispatchers.IO) { bookDao.getBookById(bookId) }
            bookTitleForFileName = localBook?.title
            if (bookTitleForFileName.isNullOrBlank()) {
                bookTitleForFileName = try { apiService.getBookById(bookId).body()?.title } catch (e: Exception) { null }
            }
            if (bookTitleForFileName.isNullOrBlank()) bookTitleForFileName = "book_$bookId" 
            
            val netResponse = apiService.downloadEpub(bookId)
            if (netResponse.isSuccessful) {
                val responseBody = netResponse.body()
                if (responseBody != null) {
                    contentLength = responseBody.contentLength()
                    inputStream = responseBody.byteStream()
                    var fileName = getFileNameFromHeaders(netResponse.raw().headers)
                    if (fileName.isNullOrBlank()) {
                        fileName = generateFallbackBookFileName(bookTitleForFileName, bookId)
                    }
                    Log.d(TAG, "downloadBook: Determined filename for $bookId: $fileName")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val cv = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, EPUB_MIME_TYPE)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + DOWNLOAD_SUB_DIR)
                        }
                        fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                        outputStream = fileUri?.let { resolver.openOutputStream(it) }
                        finalFilePath = fileUri?.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        val downloadsAppDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_SUB_DIR)
                        if (!downloadsAppDir.exists()) downloadsAppDir.mkdirs()
                        val actualFile = File(downloadsAppDir, fileName)
                        outputStream = FileOutputStream(actualFile)
                        finalFilePath = actualFile.absolutePath
                    }

                    if (outputStream == null) throw IOException("Failed to create output stream for $fileName")

                    val buffer = ByteArray(8 * 1024)
                    var bytes = inputStream.read(buffer)
                    while (bytes >= 0) {
                        outputStream.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        val progress = if (contentLength > 0) bytesCopied.toFloat() / contentLength.toFloat() else -1f
                        emit(DownloadProgress(bookId, progress, false, true, finalFilePath, null, false))
                        bytes = inputStream.read(buffer)
                    }
                    outputStream.flush()

                    if (finalFilePath == null) throw IOException("Saved path is null for $fileName")
                    
                    withContext(Dispatchers.IO) {
                        var entityToUpdate = bookDao.getBookById(bookId)
                        if (entityToUpdate == null) { 
                           val serverBook = try { apiService.getBookById(bookId).body() } catch (e: Exception) { null }
                           if (serverBook != null) {
                               entityToUpdate = serverBook.toEntity().copy(isDownloaded = true, localFilePath = finalFilePath)
                               bookDao.insertBook(entityToUpdate) // serverBook.toEntity() should have all required fields
                           } else { 
                                Log.e(TAG, "Could not fetch book details for $bookId after download. Creating minimal entity.")
                                bookDao.insertBook(BookEntity(
                                    id = bookId, 
                                    title = bookTitleForFileName, // Use the one determined for filename
                                    description = "", 
                                    chapterCount = 0, 
                                    coverUrl = null, 
                                    epubUrl = null, 
                                    language = "", 
                                    translator = "", 
                                    status = "", 
                                    dateAdded = getCurrentFormattedDateString(),
                                    isDownloaded = true, 
                                    localFilePath = finalFilePath
                                ))
                           }
                        } else {
                             bookDao.updateBook(entityToUpdate.copy(isDownloaded = true, localFilePath = finalFilePath))
                        }
                    }
                    emit(DownloadProgress(bookId, 1f, true, false, finalFilePath, null, false))

                } else throw IOException("Response body is null for $bookId")
            } else throw IOException("Network error ${netResponse.code()} for $bookId: ${netResponse.errorBody()?.string()}")
        } catch (e: Exception) {
            Log.e(TAG, "downloadBook: Error for $bookId: ${e.message}", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fileUri != null) {
                try { context.contentResolver.delete(fileUri, null, null) } catch (rd: Exception) { Log.e(TAG, "Error deleting MediaStore $fileUri", rd)}
            } else if (finalFilePath != null && !finalFilePath.startsWith("content://")) { 
                try { File(finalFilePath).delete() } catch (fd: Exception) { Log.e(TAG, "Error deleting file $finalFilePath", fd) }
            }
            val prog = if(contentLength > 0 && bytesCopied > 0) (bytesCopied.toFloat() / contentLength.toFloat()) else 0f
            emit(DownloadProgress(bookId, prog, false, false, null, e.localizedMessage ?: "Download failed", false))
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun updateBookDownloadStatus(bookId: Int, isDownloaded: Boolean, localFilePath: String?) {
        withContext(Dispatchers.IO) {
            bookDao.getBookById(bookId)?.let {
                bookDao.updateBook(it.copy(isDownloaded = isDownloaded, localFilePath = localFilePath))
            }
        }
    }
}
