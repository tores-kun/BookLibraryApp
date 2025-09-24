package com.booklibrary.android.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.booklibrary.android.data.local.dao.BookDao
import com.booklibrary.android.data.local.dao.ReadingPositionDao
// Импортируем правильные Entities
import com.booklibrary.android.data.local.entities.BookEntity
import com.booklibrary.android.data.local.entities.GenreEntity
import com.booklibrary.android.data.local.entities.BookGenreEntity
import com.booklibrary.android.data.local.entities.BookmarkEntity
import com.booklibrary.android.data.local.entities.NoteEntity
import com.booklibrary.android.data.local.entities.ReaderStateEntity
import com.booklibrary.android.domain.model.ReadingPosition // ADDED IMPORT

@Database(
    entities = [
        BookEntity::class,         // ИСПРАВЛЕНО
        GenreEntity::class,        // ДОБАВЛЕНО
        BookGenreEntity::class,    // ДОБАВЛЕНО
        BookmarkEntity::class,     // ДОБАВЛЕНО
        NoteEntity::class,         // ДОБАВЛЕНО
        ReaderStateEntity::class,   // ДОБАВЛЕНО
        ReadingPosition::class    // ADDED ENTITY
    ],
    version = 2, 
    exportSchema = false
)
abstract class BookDatabase : RoomDatabase() {
    
    abstract fun bookDao(): BookDao
    abstract fun readingPositionDao(): ReadingPositionDao
    
    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reading_positions (
                        bookId INTEGER NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        textPosition INTEGER NOT NULL,
                        scrollPosition REAL NOT NULL,
                        totalProgress REAL NOT NULL,
                        lastReadTimestamp INTEGER NOT NULL,
                        PRIMARY KEY(bookId)
                    )
                """.trimIndent())
                // Если у вас были другие изменения между версиями, их тоже нужно здесь отразить
                // Например, создание таблиц для GenreEntity, BookGenreEntity и т.д., если они не были в версии 1
            }
        }
        
        fun getDatabase(context: Context): BookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "book_database"
                )
                .addMigrations(MIGRATION_1_2)
                // .fallbackToDestructiveMigration() // Рассмотрите это на время разработки, если миграции сложные
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}