package com.booklibrary.android.util

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

class PreferencesManager(context: Context) {

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
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
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
}
