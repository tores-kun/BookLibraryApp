package com.booklibrary.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booklibrary.android.domain.model.*
import com.booklibrary.android.domain.repository.BookRepository
import com.booklibrary.android.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject


data class CatalogUiState(
    val isListRefreshing: Boolean = true,
    val books: List<Book> = emptyList(),
    val error: String? = null,
    val downloadProgress: Map<Int, DownloadProgress> = emptyMap(),
    val showLoadFromApiButton: Boolean = false
)

data class BookDetailsUiState(
    val isLoading: Boolean = false,
    val book: Book? = null,
    val error: String? = null,
    val downloadProgress: DownloadProgress? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val createBookmarkUseCase: CreateBookmarkUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val downloadBookUseCase: DownloadBookUseCase,
    private val getGenresUseCase: GetGenresUseCase,
    private val refreshGenresUseCase: RefreshGenresUseCase,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenreState: StateFlow<String?> = _selectedGenre.asStateFlow()

    private val _openFileEvent = MutableSharedFlow<String>()
    val openFileEvent: SharedFlow<String> = _openFileEvent.asSharedFlow()

    val availableGenres: StateFlow<List<String>> = getGenresUseCase()
        .map { genreList ->
            genreList.map { it.name }
        }
        .catch {
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _sortOrder = MutableStateFlow("date_added")
    private val _sortDirection = MutableStateFlow("desc")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val booksTriggerFlow = combine(_searchQuery, _selectedGenre, _sortOrder, _sortDirection) { q, g, s, o ->
        Triple(q,g, Pair(s,o))
    }.onEach {
        _uiState.update { currentState ->
            currentState.copy(isListRefreshing = true, error = null, showLoadFromApiButton = false)
        }
    }

    private val booksDataFlow: Flow<List<Book>> = booksTriggerFlow.flatMapLatest { (query, genre, sortPair) ->
        getBooksUseCase(
            query = if (query.isBlank()) null else query,
            genre = genre,
            sort = sortPair.first,
            order = sortPair.second
        )
    }

    init {
        viewModelScope.launch {
            try {
                refreshGenresUseCase()
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(error = appendError(currentState.error, "Ошибка обновления списка жанров: ${e.message}"))
                }
            }
        }

        viewModelScope.launch {
            booksDataFlow
                .catch { exception ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            isListRefreshing = false,
                            error = appendError(currentState.error, "Критическая ошибка при загрузке книг: ${exception.message}"),
                            showLoadFromApiButton = false
                        )
                    }
                }
                .collect { bookList ->
                    val isSearchOrFilterActive = !_searchQuery.value.isBlank() || _selectedGenre.value != null
                    val shouldShowButton = bookList.isEmpty() && !isSearchOrFilterActive
                    val currentError = _uiState.value.error

                    _uiState.update {
                        it.copy(
                            isListRefreshing = false,
                            books = bookList,
                            error = currentError,
                            showLoadFromApiButton = shouldShowButton
                        )
                    }
                }
        }
    }

    fun onLoadFromApiClick() {
        _uiState.update {
            it.copy(isListRefreshing = true, error = null, showLoadFromApiButton = false)
        }
        viewModelScope.launch {
            try {
                bookRepository.refreshBooks()
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isListRefreshing = false,
                        error = appendError(currentState.error, "Ошибка принудительной загрузки книг с API: ${e.message}"),
                        showLoadFromApiButton = currentState.books.isEmpty() && _searchQuery.value.isBlank() && _selectedGenre.value == null
                    )
                }
            }
        }
    }

    fun onRefreshTriggered() {
        _uiState.update { it.copy(isListRefreshing = true, error = null, showLoadFromApiButton = false) }
        viewModelScope.launch {
            try {
                refreshGenresUseCase()
            } catch (e: Exception) {
                _uiState.update { currentState ->
                     currentState.copy(error = appendError(currentState.error, "Ошибка обновления списка жанров: ${e.message}"))
                }
            }
            try {
                bookRepository.refreshBooks()
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isListRefreshing = false,
                        error = appendError(currentState.error, "Ошибка обновления списка книг: ${e.message}"),
                        showLoadFromApiButton = currentState.books.isEmpty() && _searchQuery.value.isBlank() && _selectedGenre.value == null
                    )
                }
            }
        }
    }

    fun handleOpenOrDownloadClick(book: Book) {
        val currentBookProgress = _uiState.value.downloadProgress[book.id]

        if (currentBookProgress?.isLoading == true) {
            return
        }

        if (currentBookProgress?.error != null) {
             _uiState.update { currentState ->
                val newProgressMap = currentState.downloadProgress.toMutableMap()
                newProgressMap.remove(book.id)
                var updatedError = currentState.error
                val bookSpecificError = "Ошибка загрузки ${book.title}: ${currentBookProgress.error}"
                if (updatedError?.contains(bookSpecificError) == true) {
                    updatedError = updatedError.replace(bookSpecificError, "").lines().joinToString("\n").trim().ifBlank { null }
                }
                currentState.copy(
                    downloadProgress = newProgressMap,
                    error = appendError(updatedError, "Повторная попытка загрузки для ${book.title}...")
                 )
            }
            downloadBookInternal(book)
            return
        }

        if (book.isDownloaded && !book.localFilePath.isNullOrBlank()) {
            viewModelScope.launch {
                val isFileStillValid = bookRepository.isBookDownloaded(book.id)
                if (isFileStillValid) {
                    _openFileEvent.emit(book.localFilePath)
                } else {
                    _uiState.update { currentState ->
                        currentState.copy(error = appendError(currentState.error, "Файл для ${book.title} не найден. Начинаю загрузку..."))
                    }
                    bookRepository.updateBookDownloadStatus(book.id, false, null)
                    downloadBookInternal(book)
                }
            }
        } else {
            downloadBookInternal(book)
        }
    }

    private fun downloadBookInternal(book: Book) {
        if (_uiState.value.downloadProgress[book.id]?.isLoading == true) {
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                val newProgressMap = currentState.downloadProgress.toMutableMap()
                newProgressMap[book.id] = DownloadProgress(bookId = book.id, progress = 0f, isLoading = true, error = null, filePathUri = null, wasAlreadyDownloaded = false)
                currentState.copy(downloadProgress = newProgressMap)
            }

            downloadBookUseCase(book.id).collect { progress ->
                _uiState.update { currentState ->
                    val newProgressMap = currentState.downloadProgress.toMutableMap()
                    newProgressMap[book.id] = progress
                    var currentGlobalError = currentState.error
                    val bookSpecificErrorPrefix = "Ошибка загрузки ${book.title}"
                    if (currentGlobalError?.contains(bookSpecificErrorPrefix) == true) {
                        currentGlobalError = currentGlobalError.lines().filterNot { it.startsWith(bookSpecificErrorPrefix) }.joinToString("\n").trim().ifBlank { null }
                    }
                    if (progress.error != null) {
                        currentGlobalError = appendError(currentGlobalError, "$bookSpecificErrorPrefix: ${progress.error}")
                    }
                    currentState.copy(downloadProgress = newProgressMap, error = currentGlobalError)
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onGenreFilterChange(genre: String?) {
        _selectedGenre.value = genre
    }

    fun toggleBookmark(book: Book) {
        viewModelScope.launch {
            try {
                val currentBookInUi = _uiState.value.books.find { it.id == book.id } ?: book
                if (currentBookInUi.bookmark != null) {
                    deleteBookmarkUseCase(book.id)
                } else {
                    createBookmarkUseCase(book.id, "reading", 1)
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                     currentState.copy(error = appendError(currentState.error, "Ошибка закладки: ${e.message}"))
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun appendError(existingError: String?, newError: String?): String? {
        if (newError.isNullOrBlank()) return existingError
        if (existingError.isNullOrBlank()) return newError
        return "$existingError\n$newError"
    }
}


@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val createBookmarkUseCase: CreateBookmarkUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val downloadBookUseCase: DownloadBookUseCase,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailsUiState())
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    private val _openFileEvent = MutableSharedFlow<String>()
    val openFileEvent: SharedFlow<String> = _openFileEvent.asSharedFlow()

    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, book = null, downloadProgress = null) }
            try {
                val book = getBookByIdUseCase(bookId)
                _uiState.update { currentState ->
                    currentState.copy(isLoading = false, book = book)
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun handleOpenBookClick() {
        val currentBook = _uiState.value.book ?: return
        val currentDownloadProgress = _uiState.value.downloadProgress

        if (currentDownloadProgress?.isLoading == true) {
            return
        }

        if (currentDownloadProgress?.error != null) {
            _uiState.update { it.copy(downloadProgress = null, error = "Повторная попытка загрузки...") }
            downloadBookInternal()
            return
        }

        if (currentBook.isDownloaded && !currentBook.localFilePath.isNullOrBlank()) {
            viewModelScope.launch {
                val isFileStillValid = bookRepository.isBookDownloaded(currentBook.id)
                if (isFileStillValid) {
                    _openFileEvent.emit(currentBook.localFilePath)
                } else {
                    _uiState.update { it.copy(error = "Файл не найден. Начинаю загрузку...") }
                    bookRepository.updateBookDownloadStatus(currentBook.id, false, null)
                    val bookAfterStatusUpdate = bookRepository.getBookById(currentBook.id)
                    _uiState.update { it.copy(book = bookAfterStatusUpdate) }
                    downloadBookInternal()
                }
            }
        } else {
            _uiState.update { it.copy(error = null) }
            downloadBookInternal()
        }
    }

    private fun downloadBookInternal() {
        val bookToDownload = _uiState.value.book ?: return
        if (_uiState.value.downloadProgress?.isLoading == true && _uiState.value.downloadProgress?.bookId == bookToDownload.id) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(downloadProgress = DownloadProgress(bookId = bookToDownload.id, progress = 0f, isLoading = true, error = null, filePathUri = null, wasAlreadyDownloaded = false))
            }
            downloadBookUseCase(bookToDownload.id).collect { progress ->
                _uiState.update { currentState ->
                    currentState.copy(downloadProgress = progress)
                }
                if (!progress.isLoading) {
                    loadBook(bookToDownload.id)
                }
                 if (progress.error != null) {
                     _uiState.update { it.copy(error = "Ошибка загрузки: ${progress.error}") }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                error = null,
                downloadProgress = _uiState.value.downloadProgress?.copy(error = null)
            )
        }
    }
}
