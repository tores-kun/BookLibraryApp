package com.booklibrary.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booklibrary.android.domain.model.*
import com.booklibrary.android.domain.usecase.*
import com.booklibrary.android.domain.usecase.RefreshGenresUseCase 
import com.booklibrary.android.domain.usecase.RefreshBooksUseCase // Импорт для нового UseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val createBookmarkUseCase: CreateBookmarkUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val downloadBookUseCase: DownloadBookUseCase,
    private val getGenresUseCase: GetGenresUseCase,
    private val refreshGenresUseCase: RefreshGenresUseCase, 
    private val refreshBooksUseCase: RefreshBooksUseCase // Внедряем RefreshBooksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenreState: StateFlow<String?> = _selectedGenre.asStateFlow()

    val availableGenres: StateFlow<List<String>> = getGenresUseCase()
        .map { genreList ->
            genreList.map { it.name } 
        }
        .catch { exception ->
            _uiState.update { currentState ->
                currentState.copy(error = "Не удалось загрузить жанры: ${exception.message}")
            }
            emit(emptyList()) 
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList() 
        )

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
            _uiState.update { currentState ->
                currentState.copy(isLoading = false, error = exception.message)
            }
            // emit(emptyList()) // Не эмитируем здесь пустой список, чтобы не сбрасывать существующие данные при ошибке Flow
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    init {
        // Устанавливаем isLoading в true перед началом загрузки данных
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                refreshGenresUseCase()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка обновления списка жанров: ${e.message}") }
            }
        }

        viewModelScope.launch {
            try {
                refreshBooksUseCase() // Вызываем обновление/загрузку книг с сервера
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, error = "Ошибка обновления списка книг: ${e.message}") }
            }
            // isLoading станет false, когда Flow книг эмитирует данные
        }

        // Существующая логика для сбора книг
        // Этот collect должен быть запущен ПОСЛЕ того, как refreshBooksUseCase() потенциально обновит базу
        viewModelScope.launch {
            books.collect { bookList -> // Removed .onStart as isLoading is managed above
                _uiState.update { currentState ->
                    currentState.copy(isLoading = false, books = bookList)
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        // Можно добавить вызов refreshBooksUseCase() здесь, если нужна перезагрузка с сервера при поиске
        // viewModelScope.launch { refreshBooksUseCase() } 
    }

    fun onGenreFilterChange(genre: String?) {
        _selectedGenre.value = genre
        // Можно добавить вызов refreshBooksUseCase() здесь, если нужна перезагрузка с сервера при смене жанра
        // viewModelScope.launch { refreshBooksUseCase() } 
    }

    fun onSortChange(sort: String, order: String) {
        _sortOrder.value = sort
        _sortDirection.value = order
        // Можно добавить вызов refreshBooksUseCase() здесь, если нужна перезагрузка с сервера при смене сортировки
        // viewModelScope.launch { refreshBooksUseCase() } 
    }

    fun toggleBookmark(book: Book) {
        viewModelScope.launch {
            try {
                if (book.bookmark != null) {
                    deleteBookmarkUseCase(book.id)
                } else {
                    createBookmarkUseCase(book.id, "reading", 1)
                }
                // refreshBooksUseCase() // Обновляем список, чтобы отразить изменения закладки
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun downloadBook(book: Book) {
        viewModelScope.launch {
            downloadBookUseCase(book.id).collect { progress ->
                _uiState.update { currentState ->
                    currentState.copy(
                        downloadProgress = currentState.downloadProgress.toMutableMap().apply {
                            put(book.id, progress)
                        }
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class CatalogUiState(
    val isLoading: Boolean = true, // Начальное состояние isLoading = true
    val books: List<Book> = emptyList(),
    val error: String? = null,
    val downloadProgress: Map<Int, DownloadProgress> = emptyMap()
)

// BookDetailsViewModel и BookDetailsUiState остаются без изменений
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
