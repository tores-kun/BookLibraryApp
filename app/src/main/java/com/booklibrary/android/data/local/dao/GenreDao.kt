package com.booklibrary.android.data.local.dao

import androidx.room.*
import com.booklibrary.android.data.local.entity.BookGenreEntity // Убедимся, что импорт есть
import com.booklibrary.android.data.local.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {

    // --- Методы для работы со списком всех жанров (для фильтров и кэширования) ---

    @Query("SELECT * FROM genres ORDER BY name ASC")
    fun getAllGenres(): Flow<List<GenreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(genres: List<GenreEntity>) // Используется для обновления кэша жанров

    @Query("DELETE FROM genres")
    suspend fun clearAll()

    @Transaction
    suspend fun clearAndInsertAll(genres: List<GenreEntity>) {
        clearAll()
        insertAll(genres)
    }

    // --- Методы для работы со связями книга-жанр (многие-ко-многим) ---

    // Получает все записи связей (BookGenreEntity) для конкретной книги.
    // BookRepositoryImpl использует это и затем .map { it.genreName }
    @Query("SELECT * FROM book_genres WHERE bookId = :bookId")
    suspend fun getGenresForBook(bookId: Int): List<BookGenreEntity>

    // Вставляет список связей книга-жанр.
    // Используется при сохранении книги с ее жанрами.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookGenres(bookGenreRelations: List<BookGenreEntity>)

    // Опционально: Метод для удаления связей для конкретной книги,
    // может быть полезен, если жанры книги могут полностью меняться.
    @Query("DELETE FROM book_genres WHERE bookId = :bookId")
    suspend fun deleteBookGenresForBook(bookId: Int)
    
    @Query("DELETE FROM book_genres") // Added this method
    suspend fun clearAllBookGenreRelations() // Added this method

    // Опционально: Транзакционный метод для обновления жанров книги
    @Transaction
    suspend fun updateBookGenresForBook(bookId: Int, newBookGenres: List<BookGenreEntity>) {
        deleteBookGenresForBook(bookId)
        if (newBookGenres.isNotEmpty()) { // Проверяем, что список не пустой перед вставкой
            insertBookGenres(newBookGenres)
        }
    }
}
