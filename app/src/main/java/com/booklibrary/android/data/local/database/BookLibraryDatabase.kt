package com.booklibrary.android.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.booklibrary.android.data.local.entities.BookEntity
// Эти импорты должны указывать на ЕДИНСТВЕННЫЕ правильные определения ваших сущностей
import com.booklibrary.android.data.local.entity.GenreEntity 
import com.booklibrary.android.data.local.entity.BookGenreEntity 
import com.booklibrary.android.data.local.dao.*
import com.booklibrary.android.data.local.entities.BookmarkEntity
import com.booklibrary.android.data.local.entities.NoteEntity
import com.booklibrary.android.data.local.entities.ReaderStateEntity

@Database(
    entities = [
        BookEntity::class, // Предполагается, что это из ...entities.BookEntity
        GenreEntity::class, // Предполагается, что это из ...entity.GenreEntity (с PK name, count)
        BookGenreEntity::class, // Предполагается, что это из ...entity.BookGenreEntity
        BookmarkEntity::class, // Из ...entities.
        NoteEntity::class, // Из ...entities.
        ReaderStateEntity::class // Из ...entities.
    ],
    version = 2, 
    exportSchema = false
)
abstract class BookLibraryDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun genreDao(): GenreDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun readerStateDao(): ReaderStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Удаляем старую таблицу genres, если она существовала
                database.execSQL("DROP TABLE IF EXISTS genres")
                
                // Явно создаем новую таблицу genres согласно GenreEntity (с PK name, count)
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `genres` (" +
                    "`name` TEXT NOT NULL PRIMARY KEY, " +
                    "`count` INTEGER NOT NULL)"
                )

                // Удаляем старую таблицу book_genres, если она существовала
                database.execSQL("DROP TABLE IF EXISTS book_genres")

                // Явно создаем таблицу book_genres
                // Внешний ключ для bookId теперь ссылается на таблицу 'books'
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_genres` (" +
                    "`bookId` INTEGER NOT NULL, " +
                    "`genreName` TEXT NOT NULL, " +
                    "PRIMARY KEY(`bookId`, `genreName`), " +
                    "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`genreName`) REFERENCES `genres`(`name`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_book_genres_genreName` ON `book_genres` (`genreName`)")
            }
        }
    }
}
