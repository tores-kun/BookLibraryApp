package com.booklibrary.android.di

import android.content.Context
import androidx.room.Room
import com.booklibrary.android.data.local.BookDatabase
import com.booklibrary.android.data.local.dao.BookDao
import com.booklibrary.android.data.local.dao.ReadingPositionDao
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
    fun provideBookDatabase(@ApplicationContext context: Context): BookDatabase {
        return BookDatabase.getDatabase(context)
    }

    @Provides
    fun provideBookDao(database: BookDatabase): BookDao {
        return database.bookDao()
    }

    @Provides
    fun provideReadingPositionDao(database: BookDatabase): ReadingPositionDao {
        return database.readingPositionDao()
    }
}