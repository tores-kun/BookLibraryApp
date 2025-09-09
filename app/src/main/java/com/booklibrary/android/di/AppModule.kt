package com.booklibrary.android.di

import android.content.Context
import androidx.room.Room
import com.booklibrary.android.data.local.database.BookLibraryDatabase
import com.booklibrary.android.data.local.dao.*
import com.booklibrary.android.data.remote.api.BookLibraryApiService
import com.booklibrary.android.data.repository.BookRepositoryImpl
import com.booklibrary.android.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBookLibraryDatabase(@ApplicationContext context: Context): BookLibraryDatabase {
        return Room.databaseBuilder(
            context,
            BookLibraryDatabase::class.java,
            "book_library_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideBookDao(database: BookLibraryDatabase): BookDao = database.bookDao()

    @Provides
    fun provideGenreDao(database: BookLibraryDatabase): GenreDao = database.genreDao()

    @Provides
    fun provideBookmarkDao(database: BookLibraryDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideNoteDao(database: BookLibraryDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideReaderStateDao(database: BookLibraryDatabase): ReaderStateDao = database.readerStateDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://10.93.2.6:8080/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideBookLibraryApiService(retrofit: Retrofit): BookLibraryApiService {
        return retrofit.create(BookLibraryApiService::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideBookRepository(
        apiService: BookLibraryApiService,
        bookDao: BookDao,
        genreDao: GenreDao,
        bookmarkDao: BookmarkDao,
        @ApplicationContext context: Context
    ): BookRepository {
        return BookRepositoryImpl(apiService, bookDao, genreDao, bookmarkDao, context)
    }
}
