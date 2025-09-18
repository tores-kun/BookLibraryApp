package com.booklibrary.android.data.remote.api

import com.booklibrary.android.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface BookLibraryApiService {

    @GET("api/books")
    suspend fun getBooks(
        @Query("q") query: String? = null,
        @Query("genre") genre: String? = null,
        @Query("sort") sort: String = "date_added",
        @Query("order") order: String = "desc",
        @Query("bookmark_status") bookmarkStatus: String? = null,
        @Query("page") page: Int = 1, // Оставляем пагинацию, если она используется или понадобится
        @Query("limit") limit: Int? = null // Изменено на Int? = null
    ): Response<BookResponse>

    @GET("api/books/{id}")
    suspend fun getBookById(@Path("id") bookId: Int): Response<BookDto>

    @GET("api/genres")
    suspend fun getGenres(): Response<List<GenreDto>>

    @POST("api/bookmarks")
    suspend fun createBookmark(@Body request: CreateBookmarkRequest): Response<ApiResponse<BookmarkDto>>

    @HTTP(method = "DELETE", path = "api/bookmarks", hasBody = true)
    suspend fun deleteBookmark(@Body request: DeleteBookmarkRequest): Response<ApiResponse<String>>

    @GET("api/notes")
    suspend fun getNotes(
        @Query("book_id") bookId: Int? = null,
        @Query("chapter") chapter: Int? = null
    ): Response<List<NoteDto>>

    @POST("api/notes")
    suspend fun createNote(@Body request: CreateNoteRequest): Response<NoteDto>

    @PUT("api/notes/{id}")
    suspend fun updateNote(
        @Path("id") noteId: Int,
        @Body request: UpdateNoteRequest
    ): Response<NoteDto>

    @DELETE("api/notes/{id}")
    suspend fun deleteNote(@Path("id") noteId: Int): Response<ApiResponse<String>>

    @GET("api/books/{id}/download")
    @Streaming
    suspend fun downloadEpub(@Path("id") bookId: Int): Response<ResponseBody>

    @GET("api/debug/books")
    suspend fun getDebugInfo(): Response<Any>
}
