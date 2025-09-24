package com.booklibrary.android.util

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "book_library_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Existing methods
    fun getBaseUrl(): String {
        return encryptedPrefs.getString("base_url", Constants.DEFAULT_HTTP_BASE_URL)
            ?: Constants.DEFAULT_HTTP_BASE_URL
    }

    fun setBaseUrl(url: String) {
        encryptedPrefs.edit().putString("base_url", url).apply()
    }

    fun getNetworkProfile(): String {
        return encryptedPrefs.getString("network_profile", "http") ?: "http"
    }

    fun setNetworkProfile(profile: String) {
        encryptedPrefs.edit().putString("network_profile", profile).apply()
    }

    // New reader settings methods
    fun getInt(key: String, defaultValue: Int): Int {
        return encryptedPrefs.getInt(key, defaultValue)
    }

    fun setInt(key: String, value: Int) {
        encryptedPrefs.edit().putInt(key, value).apply()
    }

    fun getString(key: String, defaultValue: String): String {
        return encryptedPrefs.getString(key, defaultValue) ?: defaultValue
    }

    fun setString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    fun getFloat(key: String, defaultValue: Float): Float {
        return encryptedPrefs.getFloat(key, defaultValue)
    }

    fun setFloat(key: String, value: Float) {
        encryptedPrefs.edit().putFloat(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return encryptedPrefs.getBoolean(key, defaultValue)
    }

    fun setBoolean(key: String, value: Boolean) {
        encryptedPrefs.edit().putBoolean(key, value).apply()
    }

    // Reader-specific convenience methods
    fun getReaderFontSize(): Int = getInt("reader_font_size", 16)
    fun setReaderFontSize(size: Int) = setInt("reader_font_size", size)

    fun getReaderTheme(): String = getString("reader_theme", "LIGHT")
    fun setReaderTheme(theme: String) = setString("reader_theme", theme)

    fun getReaderLineHeight(): Float = getFloat("reader_line_height", 1.5f)
    fun setReaderLineHeight(height: Float) = setFloat("reader_line_height", height)

    fun getReaderFont(): String = getString("reader_font", "DEFAULT")
    fun setReaderFont(font: String) = setString("reader_font", font)

    fun getReaderTextAlignment(): String = getString("reader_text_alignment", "LEFT")
    fun setReaderTextAlignment(alignment: String) = setString("reader_text_alignment", alignment)

    fun isTtsEnabled(): Boolean = getBoolean("tts_enabled", false)
    fun setTtsEnabled(enabled: Boolean) = setBoolean("tts_enabled", enabled)

    fun getTtsSpeed(): Float = getFloat("tts_speed", 1.0f)
    fun setTtsSpeed(speed: Float) = setFloat("tts_speed", speed)

    fun getTtsPitch(): Float = getFloat("tts_pitch", 1.0f)
    fun setTtsPitch(pitch: Float) = setFloat("tts_pitch", pitch)

    // Theme brightness
    fun getReaderBrightness(): Float = getFloat("reader_brightness", 1.0f)
    fun setReaderBrightness(brightness: Float) = setFloat("reader_brightness", brightness)

    // Recently read books
    fun addRecentlyReadBook(bookId: Int) {
        val recentBooks = getRecentlyReadBooks().toMutableSet()
        recentBooks.add(bookId)
        
        // Keep only last 10 books
        if (recentBooks.size > 10) {
            val sortedBooks = recentBooks.toList()
            recentBooks.clear()
            recentBooks.addAll(sortedBooks.takeLast(10))
        }
        
        setString("recently_read_books", recentBooks.joinToString(","))
    }

    fun getRecentlyReadBooks(): List<Int> {
        val booksString = getString("recently_read_books", "")
        return if (booksString.isNotEmpty()) {
            booksString.split(",").mapNotNull { it.toIntOrNull() }
        } else {
            emptyList()
        }
    }

    // Reading statistics
    fun getTotalReadingTime(): Long = encryptedPrefs.getLong("total_reading_time", 0L)
    fun setTotalReadingTime(time: Long) = encryptedPrefs.edit().putLong("total_reading_time", time).apply()
    
    fun addReadingTime(additionalTime: Long) {
        val currentTime = getTotalReadingTime()
        setTotalReadingTime(currentTime + additionalTime)
    }

    fun getBooksCompleted(): Int = getInt("books_completed", 0)
    fun setBooksCompleted(count: Int) = setInt("books_completed", count)
    
    fun incrementBooksCompleted() {
        setBooksCompleted(getBooksCompleted() + 1)
    }

    // Auto-scroll settings
    fun getAutoScrollSpeed(): Int = getInt("auto_scroll_speed", 50) // pixels per second
    fun setAutoScrollSpeed(speed: Int) = setInt("auto_scroll_speed", speed)

    fun isAutoScrollEnabled(): Boolean = getBoolean("auto_scroll_enabled", false)
    fun setAutoScrollEnabled(enabled: Boolean) = setBoolean("auto_scroll_enabled", enabled)

    // Keep screen on during reading
    fun isKeepScreenOn(): Boolean = getBoolean("keep_screen_on", true)
    fun setKeepScreenOn(keepOn: Boolean) = setBoolean("keep_screen_on", keepOn)

    // Volume buttons for page turning
    fun isVolumeButtonsPageTurn(): Boolean = getBoolean("volume_buttons_page_turn", false)
    fun setVolumeButtonsPageTurn(enabled: Boolean) = setBoolean("volume_buttons_page_turn", enabled)

    // Clear all reader settings
    fun clearReaderSettings() {
        encryptedPrefs.edit()
            .remove("reader_font_size")
            .remove("reader_theme")
            .remove("reader_line_height")
            .remove("reader_font")
            .remove("reader_text_alignment")
            .remove("reader_brightness")
            .remove("tts_enabled")
            .remove("tts_speed")
            .remove("tts_pitch")
            .remove("auto_scroll_speed")
            .remove("auto_scroll_enabled")
            .remove("keep_screen_on")
            .remove("volume_buttons_page_turn")
            .apply()
    }
}

class TtsManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { textToSpeech ->
                val result = textToSpeech.setLanguage(Locale("ru", "RU"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(Locale.getDefault())
                }
                isInitialized = true
            }
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (isInitialized) {
            tts?.speak(text, queueMode, null, "tts_id")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

object Constants {
    const val DEFAULT_HTTP_BASE_URL = "http://10.93.2.6:8080/"
    const val DEFAULT_HTTPS_BASE_URL = "https://10.93.2.6:8888/"
    const val MIN_FONT_SIZE = 12f
    const val MAX_FONT_SIZE = 24f
    const val DEFAULT_FONT_SIZE = 16f
    
    // Reader constants
    const val MIN_LINE_HEIGHT = 1.0f
    const val MAX_LINE_HEIGHT = 2.0f
    const val DEFAULT_LINE_HEIGHT = 1.5f
    
    const val MIN_TTS_SPEED = 0.1f
    const val MAX_TTS_SPEED = 3.0f
    const val DEFAULT_TTS_SPEED = 1.0f
    
    const val MIN_TTS_PITCH = 0.5f
    const val MAX_TTS_PITCH = 2.0f
    const val DEFAULT_TTS_PITCH = 1.0f
}