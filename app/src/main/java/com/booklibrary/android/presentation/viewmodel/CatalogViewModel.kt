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
    val isListRefreshing: Boolean = true, // ИЗМЕНЕНО: isLoading -> isListRefreshing
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
    // private val refreshBooksUseCase: RefreshBooksUseCase, // Используем bookRepository.refreshBooks()
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
        // При изменении любого из этих параметров, booksFlow будет пересобираться
        // и инициировать новую загрузку данных, что должно установить isListRefreshing = true
        // Однако, прямое управление isListRefreshing здесь сложно, лучше делать это перед запросом
        getBooksUseCase(
            query = if (query.isBlank()) null else query,
            genre = genre,
            sort = sort,
            order = order
        )
    }.flatMapLatest { it }

    init {
        _uiState.update { it.copy(isListRefreshing = true) } // ИЗМЕНЕНО
        viewModelScope.launch {
            try {
                refreshGenresUseCase()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка обновления списка жанров: ${e.message}") }
            }
        }
        loadBooks()
    }
    
    private fun loadBooks() {
         // isListRefreshing должен быть true перед началом сбора booksFlow
        _uiState.update { it.copy(isListRefreshing = true, error = null) } // ИЗМЕНЕНО: Устанавливаем при каждой загрузке списка
        viewModelScope.launch {
            booksFlow
                .catch { exception ->
                    _uiState.update { currentState ->
                        currentState.copy(isListRefreshing = false, error = "Ошибка при загрузке книг: ${exception.message}") // ИЗМЕНЕНО
                    }
                }
                .collect { bookList ->
                    _uiState.update { currentState ->
                        currentState.copy(isListRefreshing = false, books = bookList) // ИЗМЕНЕНО
                    }
                }
        }
    }

    fun onRefreshTriggered() {
        // isListRefreshing = true устанавливается в loadBooks(), который будет вызван из-за обновления booksFlow
        // или напрямую, если refreshBooks() не триггерит booksFlow (что маловероятно с Room)
        _uiState.update { it.copy(isListRefreshing = true, error = null) } // ИЗМЕНЕНО: Явно ставим флаг обновления
        viewModelScope.launch {
            try {
                refreshGenresUseCase() 
            } catch (e: Exception) {
                // Ошибка жанров не должна останавливать обновление книг или сбрасывать isListRefreshing
                _uiState.update { it.copy(error = "Ошибка обновления списка жанров: ${e.message}") }
            }
            try {
                bookRepository.refreshBooks() // Это должно триггерить booksFlow, который установит isListRefreshing = false
            } catch (e: Exception) {
                _uiState.update { it.copy(isListRefreshing = false, error = "Ошибка обновления списка книг: ${e.message}") } // ИЗМЕНЕНО
            }
            // Если booksFlow не эмитит после bookRepository.refreshBooks() (например, нет изменений),
            // isListRefreshing может остаться true. Поэтому loadBooks() вызывается в onSearchQueryChange и onGenreFilterChange.
            // В данном случае, booksFlow должен эмитить и установить isListRefreshing = false.
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
                // Не меняем isListRefreshing здесь
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
                    _uiState.update { 
                        // Не меняем isListRefreshing здесь
                        it.copy(error = "Файл для ${book.title} не найден. Начинаю загрузку...") 
                    }
                    bookRepository.updateBookDownloadStatus(book.id, false, null) 
                    downloadBookInternal(book)
                }
            }
        } else {
             // _uiState.update { it.copy(error = null) } // Очистка общей ошибки здесь может быть нежелательна
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
                // НЕ меняем isListRefreshing
                currentState.copy(downloadProgress = newProgressMap) 
            }

            downloadBookUseCase(book.id).collect { progress ->
                _uiState.update { currentState ->
                    val newProgressMap = currentState.downloadProgress.toMutableMap()
                    newProgressMap[book.id] = progress
                    // НЕ меняем isListRefreshing
                    var currentGlobalError = currentState.error
                    if (progress.error != null && currentState.error == null) { // Показываем ошибку загрузки книги, если нет глобальной
                        currentGlobalError = "Ошибка загрузки ${book.title}: ${progress.error}"
                    }
                    currentState.copy(downloadProgress = newProgressMap, error = currentGlobalError)
                }
            }
        }
    }
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        // loadBooks() // Перезапускаем загрузку списка, это установит isListRefreshing
    }

    fun onGenreFilterChange(genre: String?) {
        _selectedGenre.value = genre
        // loadBooks() // Перезапускаем загрузку списка, это установит isListRefreshing
    }

    // При смене сортировки также нужно перезагрузить книги
    fun onSortChange(sort: String, order: String) {
        _sortOrder.value = sort
        _sortDirection.value = order
        // loadBooks() // Перезапускаем загрузку списка, это установит isListRefreshing
    }

    // Эффект от combine для searchQuery, selectedGenre, sortOrder, sortDirection должен теперь управлять isListRefreshing
    // Добавим отдельный StateFlow для индикации того, что параметры изменились и нужна перезагрузка
    private val _listParametersChanged = MutableStateFlow(0) // Просто для триггера

    // Пересмотренная логика для booksFlow и isListRefreshing
    // Вместо прямого вызова loadBooks(), используем Flow для управления состоянием загрузки списка
    @OptIn(ExperimentalCoroutinesApi::class)
    private val booksTriggerFlow = combine(_searchQuery, _selectedGenre, _sortOrder, _sortDirection) { q, g, s, o -> 
        Triple(q,g, Pair(s,o)) // Просто для того, чтобы combine эмитил при любом изменении
    }.onEach {
        _uiState.update { it.copy(isListRefreshing = true, error = null) } // Устанавливаем флаг перед загрузкой
    }

    private val booksDataFlow: Flow<List<Book>> = booksTriggerFlow.flatMapLatest { (query, genre, sortPair) ->
        getBooksUseCase(
            query = if (query.isBlank()) null else query,
            genre = genre,
            sort = sortPair.first,
            order = sortPair.second
        )
    }

    // Инициализация сбора основного потока данных
    // Этот init блок должен быть после определения booksDataFlow
    // Мы уже имеем init, нужно интегрировать туда.
    // Вместо предыдущего init -> loadBooks(), сделаем так:
    // (Старый init остается для refreshGenres, новый init для booksDataFlow)
    // Лучше объединить

    // Объединенный init:
    // init {
    //     _uiState.update { it.copy(isListRefreshing = true) } // Начальная установка
    //     viewModelScope.launch {
    //         try {
    //             refreshGenresUseCase()
    //         } catch (e: Exception) {
    //             _uiState.update { it.copy(error = "Ошибка обновления списка жанров: ${e.message}") }
    //         }
    //     }
    //     viewModelScope.launch { // Запуск сбора данных о книгах
    //         booksDataFlow
    //             .catch { exception ->
    //                 _uiState.update { currentState ->
    //                     currentState.copy(isListRefreshing = false, error = "Ошибка при загрузке книг: ${exception.message}")
    //                 }
    //             }
    //             .collect { bookList ->
    //                 _uiState.update { currentState ->
    //                     currentState.copy(isListRefreshing = false, books = bookList)
    //                 }
    //             }
    //     }
    // }
    // Переделанный init внизу, чтобы все Flow были определены.


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
                _uiState.update { it.copy(error = "Ошибка закладки: ${e.message}") }
            }
        }
    }

    fun clearError() {
        // Если ошибка была от загрузки книги, не очищаем downloadProgress.error
        // Только глобальную ошибку
        _uiState.update { it.copy(error = null) }
    }

    // Пересмотренный init, чтобы все было в одном месте и правильно настроено
    init {
        // 1. Начальная установка состояния
        _uiState.update { it.copy(isListRefreshing = true) } 

        // 2. Загрузка/обновление жанров (однократно или по необходимости)
        viewModelScope.launch {
            try {
                refreshGenresUseCase()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка обновления списка жанров: ${e.message}") } // isListRefreshing не меняем здесь
            }
        }

        // 3. Запуск сбора данных о книгах, который реагирует на изменения параметров
        viewModelScope.launch {
            booksDataFlow
                .catch { exception ->
                    _uiState.update { currentState ->
                        // isListRefreshing уже должен быть true из booksTriggerFlow.onEach
                        currentState.copy(isListRefreshing = false, error = "Ошибка при загрузке книг: ${exception.message}")
                    }
                }
                .collect { bookList ->
                    _uiState.update { currentState ->
                        // isListRefreshing уже должен быть true из booksTriggerFlow.onEach
                        currentState.copy(isListRefreshing = false, books = bookList, error = if (bookList.isEmpty() && currentState.error == null && !(_searchQuery.value.isBlank() && _selectedGenre.value == null)) "Книги не найдены по вашему запросу" else currentState.error)
                    }
                }
        }
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
                if (progress.isComplete && !progress.isLoading && progress.error == null && !progress.wasAlreadyDownloaded) {
                    loadBook(bookToDownload.id) 
                }
                 if (progress.error != null) {
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
