package com.booklibrary.android.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.booklibrary.android.data.local.entities.*
import com.booklibrary.android.data.local.dao.*

@Database(
    entities = [
        BookEntity::class,
        GenreEntity::class, 
        BookGenreEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        ReaderStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BookLibraryDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun genreDao(): GenreDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun readerStateDao(): ReaderStateDao
}
