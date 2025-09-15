package com.booklibrary.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booklibrary.android.domain.model.*
import com.booklibrary.android.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi // Убедитесь, что этот импорт есть или добавьте его
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // <--- ДОБАВЛЕНА ЭТА АННОТАЦИЯ
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val createBookmarkUseCase: CreateBookmarkUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val downloadBookUseCase: DownloadBookUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedGenre = MutableStateFlow<String?>(null)
    private val _sortOrder = MutableStateFlow("date_added")
    private val _sortDirection = MutableStateFlow("desc")

    val books = combine(
        _searchQuery,
        _selectedGenre,
        _sortOrder,
        _sortDirection
    ) { query, genre, sort, order ->
        getBooksUseCase(
            query = if (query.isBlank()) null else query,
            genre = genre,
            sort = sort,
            order = order
        )
    }.flatMapLatest { it }
        .catch { exception ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = exception.message
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            books.collect { bookList ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    books = bookList
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onGenreFilterChange(genre: String?) {
        _selectedGenre.value = genre
    }

    fun onSortChange(sort: String, order: String) {
        _sortOrder.value = sort
        _sortDirection.value = order
    }

    fun toggleBookmark(book: Book) {
        viewModelScope.launch {
            try {
                if (book.bookmark != null) {
                    deleteBookmarkUseCase(book.id)
                } else {
                    createBookmarkUseCase(book.id, "reading", 1)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun downloadBook(book: Book) {
        viewModelScope.launch {
            downloadBookUseCase(book.id).collect { progress ->
                _uiState.value = _uiState.value.copy(
                    downloadProgress = _uiState.value.downloadProgress.toMutableMap().apply {
                        put(book.id, progress)
                    }
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class CatalogUiState(
    val isLoading: Boolean = false,
    val books: List<Book> = emptyList(),
    val error: String? = null,
    val downloadProgress: Map<Int, DownloadProgress> = emptyMap()
)

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val createBookmarkUseCase: CreateBookmarkUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val downloadBookUseCase: DownloadBookUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailsUiState())
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val book = getBookByIdUseCase(bookId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    book = book
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun updateBookmarkStatus(status: String, chapter: Int) {
        val book = _uiState.value.book ?: return

        viewModelScope.launch {
            try {
                createBookmarkUseCase(book.id, status, chapter)
                loadBook(book.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun removeBookmark() {
        val book = _uiState.value.book ?: return

        viewModelScope.launch {
            try {
                deleteBookmarkUseCase(book.id)
                loadBook(book.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun downloadBook() {
        val book = _uiState.value.book ?: return

        viewModelScope.launch {
            downloadBookUseCase(book.id).collect { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class BookDetailsUiState(
    val isLoading: Boolean = false,
    val book: Book? = null,
    val error: String? = null,
    val downloadProgress: DownloadProgress? = null
)
