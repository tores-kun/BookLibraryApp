package com.booklibrary.android.domain.usecase

import com.booklibrary.android.domain.model.*
import com.booklibrary.android.domain.repository.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBooksUseCase @Inject constructor(
    private val repository: BookRepository
) {
    operator fun invoke(
        query: String? = null,
        genre: String? = null,
        sort: String = "date_added",
        order: String = "desc",
        bookmarkStatus: String? = null
    ): Flow<List<Book>> {
        return repository.getBooks(query, genre, sort, order, bookmarkStatus)
    }
}

class GetBookByIdUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(bookId: Int): Book? {
        return repository.getBookById(bookId)
    }
}

class GetBookmarksUseCase @Inject constructor(
    private val repository: BookRepository
) {
    operator fun invoke(): Flow<List<Bookmark>> {
        return repository.getBookmarks()
    }
}

class CreateBookmarkUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(bookId: Int, status: String, currentChapter: Int) {
        repository.createBookmark(bookId, status, currentChapter)
    }
}

class DeleteBookmarkUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(bookId: Int) {
        repository.deleteBookmark(bookId)
    }
}


class GetNotesUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    operator fun invoke(bookId: Int): Flow<List<Note>> {
        return repository.getNotesForBook(bookId)
    }
}

class CreateNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(bookId: Int, chapter: Int?, text: String): Result<Note> {
        return repository.createNote(bookId, chapter, text)
    }
}

class UpdateNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(noteId: Int, text: String): Result<Note> {
        return repository.updateNote(noteId, text)
    }
}

class DeleteNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(noteId: Int): Result<Unit> {
        return repository.deleteNote(noteId)
    }
}
