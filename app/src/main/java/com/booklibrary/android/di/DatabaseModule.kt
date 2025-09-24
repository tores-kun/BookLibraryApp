package com.booklibrary.android.di

import android.content.Context
import androidx.room.Room
import com.booklibrary.android.data.local.database.BookLibraryDatabase
import com.booklibrary.android.data.local.dao.BookDao
import com.booklibrary.android.data.local.dao.GenreDao
import com.booklibrary.android.data.local.dao.BookmarkDao
import com.booklibrary.android.data.local.dao.NoteDao
import com.booklibrary.android.data.local.dao.ReaderStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBookLibraryDatabase(
        @ApplicationContext context: Context
    ): BookLibraryDatabase {
        return Room.databaseBuilder(
            context,
            BookLibraryDatabase::class.java,
            "book_library_database"
        )
            .addMigrations(BookLibraryDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideBookDao(db: BookLibraryDatabase): BookDao = db.bookDao()

    @Provides
    fun provideGenreDao(db: BookLibraryDatabase): GenreDao = db.genreDao()

    @Provides
    fun provideBookmarkDao(db: BookLibraryDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideNoteDao(db: BookLibraryDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideReaderStateDao(db: BookLibraryDatabase): ReaderStateDao = db.readerStateDao()
}
