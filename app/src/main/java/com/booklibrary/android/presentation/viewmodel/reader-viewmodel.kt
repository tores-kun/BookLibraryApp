package com.booklibrary.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booklibrary.android.data.epub.EpubParser
import com.booklibrary.android.data.repository.ReadingPositionRepository
import com.booklibrary.android.domain.model.*
import com.booklibrary.android.util.EnhancedTtsManager
import com.booklibrary.android.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val epubParser: EpubParser,
    private val readingPositionRepository: ReadingPositionRepository,
    private val ttsManager: EnhancedTtsManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _readerSettings = MutableStateFlow(ReaderSettings())
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()

    private var currentBookId: Int? = null
    private var epubBook: EpubBook? = null

    init {
        observeTtsState()
        loadReaderSettings()
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            combine(
                ttsManager.isSpeaking,
                ttsManager.isPaused,
                ttsManager.isLoading,
                ttsManager.error
            ) { isSpeaking, isPaused, isLoading, error ->
                _uiState.value = _uiState.value.copy(
                    isTtsSpeaking = isSpeaking,
                    isTtsPaused = isPaused,
                    isTtsLoading = isLoading,
                    ttsError = error
                )
            }.collect()
        }
    }

    private fun loadReaderSettings() {
        viewModelScope.launch {
            try {
                // Загружаем настройки из SharedPreferences
                val settings = ReaderSettings(
                    fontSize = preferencesManager.getReaderFontSize(),
                    theme = preferencesManager.getReaderTheme(),
                    lineHeight = preferencesManager.getReaderLineHeight(),
                    fontFamily = preferencesManager.getReaderFont(),
                    textAlignment = preferencesManager.getReaderTextAlignment(),
                    ttsEnabled = preferencesManager.isTtsEnabled(),
                    ttsSpeed = preferencesManager.getTtsSpeed(),
                    ttsPitch = preferencesManager.getTtsPitch()
                )
                _readerSettings.value = settings
                
                // Применяем настройки TTS
                ttsManager.setSpeechRate(settings.ttsSpeed)
                ttsManager.setPitch(settings.ttsPitch)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка загрузки настроек: ${e.message}"
                )
            }
        }
    }

    fun loadBook(bookId: Int, filePath: String) {
        if (currentBookId == bookId && epubBook != null) {
            return // Книга уже загружена
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Парсим EPUB
                val result = epubParser.parseEpubFile(bookId, filePath)
                
                result.fold(
                    onSuccess = { book ->
                        epubBook = book
                        currentBookId = bookId
                        
                        // Загружаем позицию чтения
                        val readingPosition = readingPositionRepository.getReadingPosition(bookId)
                        val chapterIndex = readingPosition?.chapterIndex ?: 0
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            epubBook = book,
                            currentChapterIndex = chapterIndex,
                            currentChapter = book.chapters.getOrNull(chapterIndex),
                            totalProgress = readingPosition?.totalProgress ?: 0f,
                            scrollPosition = readingPosition?.scrollPosition ?: 0f,
                            error = null
                        )
                        
                        // Подписываемся на изменения позиции чтения
                        observeReadingPosition(bookId)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Ошибка загрузки книги: ${error.message}"
                        )
                    }
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Неожиданная ошибка: ${e.message}"
                )
            }
        }
    }

    private fun observeReadingPosition(bookId: Int) {
        viewModelScope.launch {
            readingPositionRepository.getReadingPositionFlow(bookId)
                .collect { position ->
                    if (position != null) {
                        _uiState.value = _uiState.value.copy(
                            totalProgress = position.totalProgress
                        )
                    }
                }
        }
    }

    fun navigateToChapter(chapterIndex: Int) {
        val book = epubBook ?: return
        
        if (chapterIndex < 0 || chapterIndex >= book.chapters.size) {
            _uiState.value = _uiState.value.copy(
                error = "Некорректный индекс главы"
            )
            return
        }

        val chapter = book.chapters[chapterIndex]
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = chapterIndex,
            currentChapter = chapter,
            scrollPosition = 0f
        )

        // Сохраняем позицию
        saveCurrentReadingPosition()
        
        // Останавливаем TTS при смене главы
        ttsManager.stop()
    }

    fun navigateToPreviousChapter() {
        val currentIndex = _uiState.value.currentChapterIndex
        if (currentIndex > 0) {
            navigateToChapter(currentIndex - 1)
        }
    }

    fun navigateToNextChapter() {
        val book = epubBook ?: return
        val currentIndex = _uiState.value.currentChapterIndex
        if (currentIndex < book.chapters.size - 1) {
            navigateToChapter(currentIndex + 1)
        }
    }

    fun updateScrollPosition(scrollPosition: Float) {
        _uiState.value = _uiState.value.copy(scrollPosition = scrollPosition)
        
        // Рассчитываем общий прогресс
        val book = epubBook ?: return
        val currentChapterIndex = _uiState.value.currentChapterIndex
        val chapterProgress = scrollPosition
        val totalProgress = (currentChapterIndex + chapterProgress) / book.chapters.size
        
        _uiState.value = _uiState.value.copy(totalProgress = totalProgress)
        
        // Сохраняем позицию с задержкой
        saveCurrentReadingPosition()
    }

    private fun saveCurrentReadingPosition() {
        val bookId = currentBookId ?: return
        val state = _uiState.value
        
        viewModelScope.launch {
            try {
                readingPositionRepository.saveReadingPosition(
                    bookId = bookId,
                    chapterIndex = state.currentChapterIndex,
                    textPosition = 0, // Пока не используем
                    scrollPosition = state.scrollPosition,
                    totalProgress = state.totalProgress
                )
            } catch (e: Exception) {
                // Логируем ошибку, но не показываем пользователю
                e.printStackTrace()
            }
        }
    }

    // Настройки читалки
    fun updateFontSize(fontSize: Int) {
        val clampedSize = fontSize.coerceIn(12, 24)
        val updatedSettings = _readerSettings.value.copy(fontSize = clampedSize)
        _readerSettings.value = updatedSettings
        preferencesManager.setReaderFontSize(clampedSize)
    }

    fun updateTheme(theme: ReaderTheme) {
        val updatedSettings = _readerSettings.value.copy(theme = theme)
        _readerSettings.value = updatedSettings
        preferencesManager.setReaderTheme(theme)
    }

    fun updateLineHeight(lineHeight: Float) {
        val clampedHeight = lineHeight.coerceIn(1.0f, 2.0f)
        val updatedSettings = _readerSettings.value.copy(lineHeight = clampedHeight)
        _readerSettings.value = updatedSettings
        preferencesManager.setReaderLineHeight(clampedHeight)
    }

    fun updateFontFamily(font: ReaderFont) {
        val updatedSettings = _readerSettings.value.copy(fontFamily = font)
        _readerSettings.value = updatedSettings
        preferencesManager.setReaderFont(font)
    }

    fun updateTextAlignment(alignment: ReaderTextAlignment) {
        val updatedSettings = _readerSettings.value.copy(textAlignment = alignment)
        _readerSettings.value = updatedSettings
        preferencesManager.setReaderTextAlignment(alignment)
    }

    // TTS функциональность
    fun startTts() {
        val currentChapter = _uiState.value.currentChapter
        if (currentChapter != null) {
            val utteranceId = "chapter_${currentChapter.index}"
            ttsManager.speak(currentChapter.content, utteranceId)
            
            val updatedSettings = _readerSettings.value.copy(ttsEnabled = true)
            _readerSettings.value = updatedSettings
            preferencesManager.setTtsEnabled(true)
        }
    }

    fun pauseTts() {
        ttsManager.pause()
    }

    fun stopTts() {
        ttsManager.stop()
    }

    fun updateTtsSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.1f, 3.0f)
        ttsManager.setSpeechRate(clampedSpeed)
        
        val updatedSettings = _readerSettings.value.copy(ttsSpeed = clampedSpeed)
        _readerSettings.value = updatedSettings
        preferencesManager.setTtsSpeed(clampedSpeed)
    }

    fun updateTtsPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        ttsManager.setPitch(clampedPitch)
        
        val updatedSettings = _readerSettings.value.copy(ttsPitch = clampedPitch)
        _readerSettings.value = updatedSettings
        preferencesManager.setTtsPitch(clampedPitch)
    }

    fun toggleSettingsPanel() {
        _uiState.value = _uiState.value.copy(
            showSettingsPanel = !_uiState.value.showSettingsPanel
        )
    }

    fun toggleChaptersList() {
        _uiState.value = _uiState.value.copy(
            showChaptersList = !_uiState.value.showChaptersList
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, ttsError = null)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}

data class ReaderUiState(
    val isLoading: Boolean = false,
    val epubBook: EpubBook? = null,
    val currentChapterIndex: Int = 0,
    val currentChapter: ChapterContent? = null,
    val scrollPosition: Float = 0f,
    val totalProgress: Float = 0f,
    val showSettingsPanel: Boolean = false,
    val showChaptersList: Boolean = false,
    val error: String? = null,
    
    // TTS состояние
    val isTtsSpeaking: Boolean = false,
    val isTtsPaused: Boolean = false,
    val isTtsLoading: Boolean = false,
    val ttsError: String? = null
)

// Расширения для PreferencesManager
private fun PreferencesManager.getReaderFontSize(): Int = 
    getInt("reader_font_size", 16)

private fun PreferencesManager.setReaderFontSize(size: Int) = 
    setInt("reader_font_size", size)

private fun PreferencesManager.getReaderTheme(): ReaderTheme =
    ReaderTheme.valueOf(getString("reader_theme", ReaderTheme.LIGHT.name))

private fun PreferencesManager.setReaderTheme(theme: ReaderTheme) = 
    setString("reader_theme", theme.name)

private fun PreferencesManager.getReaderLineHeight(): Float = 
    getFloat("reader_line_height", 1.5f)

private fun PreferencesManager.setReaderLineHeight(height: Float) = 
    setFloat("reader_line_height", height)

private fun PreferencesManager.getReaderFont(): ReaderFont =
    ReaderFont.valueOf(getString("reader_font", ReaderFont.DEFAULT.name))

private fun PreferencesManager.setReaderFont(font: ReaderFont) = 
    setString("reader_font", font.name)

private fun PreferencesManager.getReaderTextAlignment(): ReaderTextAlignment =
    ReaderTextAlignment.valueOf(getString("reader_text_alignment", ReaderTextAlignment.LEFT.name))

private fun PreferencesManager.setReaderTextAlignment(alignment: ReaderTextAlignment) = 
    setString("reader_text_alignment", alignment.name)

private fun PreferencesManager.isTtsEnabled(): Boolean = 
    getBoolean("tts_enabled", false)

private fun PreferencesManager.setTtsEnabled(enabled: Boolean) = 
    setBoolean("tts_enabled", enabled)

private fun PreferencesManager.getTtsSpeed(): Float = 
    getFloat("tts_speed", 1.0f)

private fun PreferencesManager.setTtsSpeed(speed: Float) = 
    setFloat("tts_speed", speed)

private fun PreferencesManager.getTtsPitch(): Float = 
    getFloat("tts_pitch", 1.0f)

private fun PreferencesManager.setTtsPitch(pitch: Float) = 
    setFloat("tts_pitch", pitch)

// Placeholder методы для PreferencesManager (добавьте их в существующий класс)
private fun PreferencesManager.getInt(key: String, defaultValue: Int): Int = defaultValue
private fun PreferencesManager.setInt(key: String, value: Int) {}
private fun PreferencesManager.getString(key: String, defaultValue: String): String = defaultValue
private fun PreferencesManager.setString(key: String, value: String) {}
private fun PreferencesManager.getFloat(key: String, defaultValue: Float): Float = defaultValue
private fun PreferencesManager.setFloat(key: String, value: Float) {}
private fun PreferencesManager.getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
private fun PreferencesManager.setBoolean(key: String, value: Boolean) {}