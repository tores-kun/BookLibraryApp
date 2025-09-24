package com.booklibrary.android.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnhancedTtsManager @Inject constructor(
    private val context: Context
) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    private val _currentSpeed = MutableStateFlow(1.0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()
    
    private val _currentPitch = MutableStateFlow(1.0f)
    val currentPitch: StateFlow<Float> = _currentPitch.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Callback для отслеживания прогресса чтения
    var onTextStarted: ((String) -> Unit)? = null
    var onTextCompleted: ((String) -> Unit)? = null
    var onTextError: ((String) -> Unit)? = null
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            _error.value = "Ошибка инициализации TTS: ${e.message}"
            _isLoading.value = false
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { textToSpeech ->
                // Пробуем установить русский язык
                val russianResult = textToSpeech.setLanguage(Locale("ru", "RU"))
                
                if (russianResult == TextToSpeech.LANG_MISSING_DATA || 
                    russianResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Если русский не поддерживается, используем английский
                    val englishResult = textToSpeech.setLanguage(Locale.ENGLISH)
                    
                    if (englishResult == TextToSpeech.LANG_MISSING_DATA || 
                        englishResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        _error.value = "TTS языки не поддерживаются"
                        _isLoading.value = false
                        return
                    }
                }
                
                // Настраиваем слушатель прогресса
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                        _isPaused.value = false
                        utteranceId?.let { onTextStarted?.invoke(it) }
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        _isPaused.value = false
                        utteranceId?.let { onTextCompleted?.invoke(it) }
                    }
                    
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        _isPaused.value = false
                        utteranceId?.let { 
                            onTextError?.invoke(it)
                            _error.value = "Ошибка озвучивания"
                        }
                    }
                    
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        _isSpeaking.value = false
                        if (!interrupted) {
                            _isPaused.value = true
                        }
                    }
                })
                
                isInitialized = true
                _isLoading.value = false
                _error.value = null
            }
        } else {
            _error.value = "Ошибка инициализации TTS"
            _isLoading.value = false
        }
    }
    
    /**
     * Начинает озвучивание текста
     */
    fun speak(text: String, utteranceId: String = "tts_${System.currentTimeMillis()}") {
        if (!isInitialized || tts == null) {
            _error.value = "TTS не инициализирован"
            return
        }
        
        if (text.isBlank()) {
            _error.value = "Текст для озвучивания пустой"
            return
        }
        
        try {
            // Разбиваем длинный текст на части
            val maxLength = TextToSpeech.getMaxSpeechInputLength()
            
            if (text.length <= maxLength) {
                val params = hashMapOf<String, String>().apply {
                    put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            } else {
                // Разбиваем на предложения для лучшего качества
                val sentences = splitTextIntoSentences(text, maxLength)
                sentences.forEachIndexed { index, sentence ->
                    val chunkId = "${utteranceId}_chunk_$index"
                    val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    
                    val params = hashMapOf<String, String>().apply {
                        put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunkId)
                    }
                    tts?.speak(sentence, queueMode, params)
                }
            }
        } catch (e: Exception) {
            _error.value = "Ошибка при озвучивании: ${e.message}"
        }
    }
    
    /**
     * Приостанавливает озвучивание
     */
    fun pause() {
        if (isInitialized && _isSpeaking.value) {
            tts?.stop()
            _isPaused.value = true
            _isSpeaking.value = false
        }
    }
    
    /**
     * Останавливает озвучивание
     */
    fun stop() {
        if (isInitialized) {
            tts?.stop()
            _isSpeaking.value = false
            _isPaused.value = false
        }
    }
    
    /**
     * Устанавливает скорость речи
     */
    fun setSpeechRate(rate: Float) {
        if (isInitialized) {
            val clampedRate = rate.coerceIn(0.1f, 3.0f)
            tts?.setSpeechRate(clampedRate)
            _currentSpeed.value = clampedRate
        }
    }
    
    /**
     * Устанавливает высоту тона
     */
    fun setPitch(pitch: Float) {
        if (isInitialized) {
            val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
            tts?.setPitch(clampedPitch)
            _currentPitch.value = clampedPitch
        }
    }
    
    /**
     * Проверяет доступность языка
     */
    fun isLanguageAvailable(locale: Locale): Boolean {
        return if (isInitialized) {
            val result = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            result >= TextToSpeech.LANG_AVAILABLE
        } else {
            false
        }
    }
    
    /**
     * Получает доступные языки
     */
    fun getAvailableLanguages(): Set<Locale> {
        return if (isInitialized && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tts?.availableLanguages ?: emptySet()
        } else {
            emptySet()
        }
    }
    
    /**
     * Разбивает текст на предложения с учетом максимальной длины
     */
    private fun splitTextIntoSentences(text: String, maxLength: Int): List<String> {
        val sentences = mutableListOf<String>()
        val sentenceDelimiters = Regex("[.!?]+\\s+")
        
        val rawSentences = text.split(sentenceDelimiters)
        var currentChunk = ""
        
        for (sentence in rawSentences) {
            val trimmedSentence = sentence.trim()
            if (trimmedSentence.isEmpty()) continue
            
            if (currentChunk.length + trimmedSentence.length + 1 <= maxLength) {
                currentChunk = if (currentChunk.isEmpty()) {
                    trimmedSentence
                } else {
                    "$currentChunk. $trimmedSentence"
                }
            } else {
                if (currentChunk.isNotEmpty()) {
                    sentences.add(currentChunk)
                }
                
                if (trimmedSentence.length <= maxLength) {
                    currentChunk = trimmedSentence
                } else {
                    // Если предложение слишком длинное, разбиваем по словам
                    val words = trimmedSentence.split(" ")
                    var wordChunk = ""
                    
                    for (word in words) {
                        if (wordChunk.length + word.length + 1 <= maxLength) {
                            wordChunk = if (wordChunk.isEmpty()) word else "$wordChunk $word"
                        } else {
                            if (wordChunk.isNotEmpty()) {
                                sentences.add(wordChunk)
                            }
                            wordChunk = word
                        }
                    }
                    currentChunk = wordChunk
                }
            }
        }
        
        if (currentChunk.isNotEmpty()) {
            sentences.add(currentChunk)
        }
        
        return sentences
    }
    
    /**
     * Освобождает ресурсы
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isLoading.value = true
        _isSpeaking.value = false
        _isPaused.value = false
    }
}