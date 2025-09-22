package com.booklibrary.android.data.local.dao

import androidx.room.*
import com.booklibrary.android.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("""
        SELECT * FROM books
        ORDER BY
            CASE WHEN :sortColumn = 'dateAdded' AND :sortOrder = 'asc' THEN dateAdded END ASC,
            CASE WHEN :sortColumn = 'dateAdded' AND :sortOrder = 'desc' THEN dateAdded END DESC,
            CASE WHEN :sortColumn = 'title' AND :sortOrder = 'asc' THEN title END ASC,
            CASE WHEN :sortColumn = 'title' AND :sortOrder = 'desc' THEN title END DESC
    """)
    fun getAllBooksSorted(sortColumn: String, sortOrder: String): Flow<List<BookEntity>>

    // Используем dateAdded для сортировки по умолчанию
    fun getAllBooks(): Flow<List<BookEntity>> = getAllBooksSorted("dateAdded", "desc")

    @Query("""
        SELECT * FROM books
        WHERE title LIKE :query OR description LIKE :query
        ORDER BY
            CASE WHEN :sortColumn = 'dateAdded' AND :sortOrder = 'asc' THEN dateAdded END ASC,
            CASE WHEN :sortColumn = 'dateAdded' AND :sortOrder = 'desc' THEN dateAdded END DESC,
            CASE WHEN :sortColumn = 'title' AND :sortOrder = 'asc' THEN title END ASC,
            CASE WHEN :sortColumn = 'title' AND :sortOrder = 'desc' THEN title END DESC
    """)
    fun searchBooks(query: String, sortColumn: String, sortOrder: String): Flow<List<BookEntity>>

    @Query("""
        SELECT b.* FROM books b JOIN book_genres bg ON b.id = bg.bookId
        WHERE bg.genreName = :genreName
        ORDER BY
            CASE WHEN :sortColumn = 'b.dateAdded' AND :sortOrder = 'asc' THEN b.dateAdded END ASC,
            CASE WHEN :sortColumn = 'b.dateAdded' AND :sortOrder = 'desc' THEN b.dateAdded END DESC,
            CASE WHEN :sortColumn = 'b.title' AND :sortOrder = 'asc' THEN b.title END ASC,
            CASE WHEN :sortColumn = 'b.title' AND :sortOrder = 'desc' THEN b.title END DESC
    """)
    fun getBooksByGenre(genreName: String, sortColumn: String, sortOrder: String): Flow<List<BookEntity>>

    @Query("""
        SELECT b.* FROM books b JOIN book_genres bg ON b.id = bg.bookId
        WHERE (b.title LIKE :query OR b.description LIKE :query) AND bg.genreName = :genreName
        ORDER BY
            CASE WHEN :sortColumn = 'b.dateAdded' AND :sortOrder = 'asc' THEN b.dateAdded END ASC,
            CASE WHEN :sortColumn = 'b.dateAdded' AND :sortOrder = 'desc' THEN b.dateAdded END DESC,
            CASE WHEN :sortColumn = 'b.title' AND :sortOrder = 'asc' THEN b.title END ASC,
            CASE WHEN :sortColumn = 'b.title' AND :sortOrder = 'desc' THEN b.title END DESC
    """)
    fun searchBooksByQueryAndGenre(query: String, genreName: String, sortColumn: String, sortOrder: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Int): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Int)

    @Query("DELETE FROM books")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId")
    suspend fun getBookmark(bookId: Int): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE status = :status")
    fun getBookmarksByStatus(status: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getNotesForBook(bookId: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE bookId = :bookId AND chapter = :chapter")
    fun getNotesForChapter(bookId: Int, chapter: Int): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}

@Dao
interface ReaderStateDao {
    @Query("SELECT * FROM reader_state WHERE bookId = :bookId")
    suspend fun getReaderState(bookId: Int): ReaderStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaderState(state: ReaderStateEntity)

    @Update
    suspend fun updateReaderState(state: ReaderStateEntity)
}
