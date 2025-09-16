package com.booklibrary.android.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.booklibrary.android.data.local.entities.*

@Dao
interface BookDao {
    @Query("SELECT * FROM books")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("""
        SELECT books.* FROM books
        INNER JOIN book_genres ON books.id = book_genres.bookId
        WHERE book_genres.genreName = :genreName
    """)
    fun getBooksByGenre(genreName: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Int): BookEntity?

    @Query("SELECT * FROM books WHERE title LIKE :query OR description LIKE :query")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Query("""
        SELECT books.* FROM books
        INNER JOIN book_genres ON books.id = book_genres.bookId
        WHERE book_genres.genreName = :genreName AND (books.title LIKE :query OR books.description LIKE :query)
    """)
    fun searchBooksByQueryAndGenre(query: String, genreName: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books") // Added this method
    suspend fun deleteAllBooks() // Added this method
}

// GenreDao interface was here and has been removed

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
