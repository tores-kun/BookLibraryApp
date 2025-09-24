package com.booklibrary.android.di

import android.content.Context
import androidx.room.Room
import com.booklibrary.android.data.local.BookDatabase
import com.booklibrary.android.data.local.dao.BookDao
import com.booklibrary.android.data.local.dao.ReadingPositionDao
import com.booklibrary.android.data.epub.EpubParser
import com.booklibrary.android.data.repository.ReadingPositionRepository
import com.booklibrary.android.util.EnhancedTtsManager
import com.booklibrary.android.util.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {
    
    @Provides
    @Singleton
    fun provideEpubParser(@ApplicationContext context: Context): EpubParser {
        return EpubParser(context)
    }
    
    @Provides
    @Singleton
    fun provideReadingPositionRepository(
        readingPositionDao: ReadingPositionDao
    ): ReadingPositionRepository {
        return ReadingPositionRepository(readingPositionDao)
    }
    
    @Provides
    @Singleton
    fun provideEnhancedTtsManager(@ApplicationContext context: Context): EnhancedTtsManager {
        return EnhancedTtsManager(context)
    }
    
    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }
}