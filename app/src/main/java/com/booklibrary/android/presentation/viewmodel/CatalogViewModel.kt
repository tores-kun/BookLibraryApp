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

// Определение UiState для CatalogScreen
data class CatalogUiState(
    val isLoading: Boolean = true,
    val books: List<Book> = emptyList(),
    val error: String? = null,
    val downloadProgress: Map<Int, DownloadProgress> = emptyMap() // Int это bookId
)

// Определение UiState для BookDetailsScreen (остается здесь же, если не было вынесено)
data class BookDetailsUiState(
    val isLoading: Boolean = false,
    val book: Book? = null,
    val error: String? = null,
    val downloadProgress: DownloadProgress? = null // Только для текущей открытой книги
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val createBookmarkUseCase: CreateBookmarkUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val downloadBookUseCase: DownloadBookUseCase,
    private val getGenresUseCase: GetGenresUseCase,
    private val refreshGenresUseCase: RefreshGenresUseCase,
    private val refreshBooksUseCase: RefreshBooksUseCase,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState()) // Используем восстановленный CatalogUiState
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

    private val booksFlow: Flow<List<Book>> = combine(
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

    init {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                refreshGenresUseCase()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка обновления списка жанров: ${e.message}") }
            }
        }
        viewModelScope.launch {
            booksFlow
                .catch { exception ->
                    _uiState.update { currentState ->
                        currentState.copy(isLoading = false, error = "Ошибка при загрузке книг: ${exception.message}")
                    }
                    // Не эмитируем пустой список, чтобы не сбрасывать данные при временной ошибке
                }
                .collect { bookList ->
                    _uiState.update { currentState ->
                        currentState.copy(isLoading = false, books = bookList)
                    }
                }
        }
    }
    
    fun onRefreshTriggered() {
        _uiState.update { it.copy(isLoading = true, error = null) } 
        viewModelScope.launch {
            try {
                refreshGenresUseCase() 
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка обновления списка жанров: ${e.message}") }
            }
            try {
                // refreshBooksUseCase() // Этот use case должен обновить книги в БД, что вызовет обновление Flow `books`
                // Вместо прямого вызова refreshBooksUseCase(), который возвращает Unit,
                // лучше вызвать метод репозитория, который может генерировать исключения, если есть.
                // Однако, refreshBooksUseCase уже должен использовать refreshBooksWithParams из репозитория.
                // Основная задача - чтобы booksFlow был пересобран или получил новые данные.
                // Если refreshBooksUseCase обновляет БД, booksFlow автоматически это подхватит.
                bookRepository.refreshBooks() // Вызываем метод репозитория напрямую для ясности или используем UseCase
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Ошибка обновления списка книг: ${e.message}") }
            }
            // isLoading станет false, когда Flow `booksFlow` эмитирует новые данные
        }
    }

    fun handleOpenOrDownloadClick(book: Book) {
        val currentBookProgress = _uiState.value.downloadProgress[book.id]

        if (currentBookProgress?.isLoading == true) {
            return // Уже грузится, ничего не делаем
        }

        // Если была ошибка для этой книги, пробуем снова
        if (currentBookProgress?.error != null) {
             _uiState.update { currentState ->
                val newProgressMap = currentState.downloadProgress.toMutableMap()
                newProgressMap.remove(book.id) // Удаляем старую ошибку перед повторной попыткой
                currentState.copy(downloadProgress = newProgressMap, error = "Повторная попытка загрузки для ${book.title}...")
            }
            downloadBookInternal(book)
            return
        }

        if (book.isDownloaded && !book.localFilePath.isNullOrBlank()) {
            viewModelScope.launch {
                val isFileStillValid = bookRepository.isBookDownloaded(book.id)
                if (isFileStillValid) {
                    _openFileEvent.emit(book.localFilePath!!)
                } else {
                    _uiState.update { it.copy(error = "Файл для ${book.title} не найден. Начинаю загрузку...") }
                    // Важно: обновить статус в БД, чтобы UI не показывал "Открыть" невалидный файл
                    bookRepository.updateBookDownloadStatus(book.id, false, null) 
                    downloadBookInternal(book)
                }
            }
        } else {
            _uiState.update { it.copy(error = null) } // Очищаем общую ошибку, если начинаем новую загрузку
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
                currentState.copy(downloadProgress = newProgressMap, error = null) 
            }

            downloadBookUseCase(book.id).collect { progress ->
                _uiState.update { currentState ->
                    val newProgressMap = currentState.downloadProgress.toMutableMap()
                    newProgressMap[book.id] = progress
                    currentState.copy(downloadProgress = newProgressMap)
                }
                if (progress.error != null) {
                    _uiState.update { it.copy(error = "Ошибка загрузки ${book.title}: ${progress.error}") }
                }
                // Обновление списка books произойдет автоматически через booksFlow, если BookRepository корректно обновляет DAO
            }
        }
    }
    
    fun requestDownload(book: Book) { // Оставим на случай, если где-то еще используется
        downloadBookInternal(book)
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
                // Используем uiState.books, так как это наиболее актуальный список на UI
                val currentBookInUi = _uiState.value.books.find { it.id == book.id } ?: book

                if (currentBookInUi.bookmark != null) {
                    deleteBookmarkUseCase(book.id)
                } else {
                    createBookmarkUseCase(book.id, "reading", 1) 
                }
                // booksFlow должен автоматически обновиться после изменения в BookmarkDao
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка закладки: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
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

    private val _uiState = MutableStateFlow(BookDetailsUiState()) // Используем BookDetailsUiState
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    private val _openFileEvent = MutableSharedFlow<String>() 
    val openFileEvent: SharedFlow<String> = _openFileEvent.asSharedFlow()

    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, book = null, downloadProgress = null) } // Сброс перед загрузкой
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
                    _openFileEvent.emit(currentBook.localFilePath!!) 
                } else {
                    _uiState.update { it.copy(error = "Файл не найден. Начинаю загрузку...") }
                    bookRepository.updateBookDownloadStatus(currentBook.id, false, null) // Обновляем в БД
                    downloadBookInternal()
                }
            }
        } else {
            _uiState.update { it.copy(error = null) } 
            downloadBookInternal()
        }
    }
    
    // Переименовано для консистентности и чтобы не было путаницы с downloadBookUseCase
    private fun downloadBookInternal() {
        val bookToDownload = _uiState.value.book ?: return
        // Проверка, не идет ли уже загрузка для этой книги
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
                // Если загрузка успешно завершена (и это не была "уже загруженная" книга),
                // BookRepositoryImpl.downloadBook уже обновил БД.
                // Мы должны перезагрузить book в uiState, чтобы получить обновленный isDownloaded и localFilePath.
                if (progress.isComplete && !progress.isLoading && progress.error == null && !progress.wasAlreadyDownloaded) {
                    loadBook(bookToDownload.id) 
                }
                 if (progress.error != null) {
                    // Ошибка уже будет в uiState.downloadProgress.error, также можно показать Toast
                     _uiState.update { it.copy(error = "Ошибка загрузки: ${progress.error}") }
                }
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
                _uiState.update { it.copy(error = e.message) }
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
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, downloadProgress = _uiState.value.downloadProgress?.copy(error = null)) }
    }
}
