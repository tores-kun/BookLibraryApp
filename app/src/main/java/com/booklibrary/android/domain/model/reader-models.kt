package com.booklibrary.android.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность для сохранения позиции чтения
 */
@Entity(tableName = "reading_positions")
data class ReadingPosition(
    @PrimaryKey
    val bookId: Int,
    val chapterIndex: Int = 0,
    val textPosition: Int = 0,
    val scrollPosition: Float = 0f,
    val totalProgress: Float = 0f,
    val lastReadTimestamp: Long = System.currentTimeMillis()
)

/**
 * Модель главы книги
 */
data class ChapterContent(
    val index: Int,
    val title: String,
    val content: String,
    val wordCount: Int
) {
    val estimatedReadingTime: Int
        get() = (wordCount / 200).coerceAtLeast(1) // примерно 200 слов в минуту
}

/**
 * Настройки читалки
 */
data class ReaderSettings(
    val fontSize: Int = 16, // sp
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val lineHeight: Float = 1.5f,
    val fontFamily: ReaderFont = ReaderFont.DEFAULT,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.LEFT,
    val brightness: Float = 1.0f,
    val ttsEnabled: Boolean = false,
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f
)

enum class ReaderTheme(val displayName: String) {
    LIGHT("Светлая"),
    DARK("Тёмная"),
    SEPIA("Сепия")
}

enum class ReaderFont(val displayName: String) {
    DEFAULT("По умолчанию"),
    SERIF("Serif"),
    SANS_SERIF("Sans Serif"),
    MONOSPACE("Monospace")
}

enum class ReaderTextAlignment(val displayName: String) {
    LEFT("По левому краю"),
    CENTER("По центру"),
    JUSTIFY("По ширине")
}

/**
 * Состояние EPUB книги
 */
data class EpubBook(
    val id: Int,
    val title: String,
    val author: String,
    val filePath: String,
    val chapters: List<ChapterContent>,
    val coverImagePath: String? = null,
    val totalWordCount: Int = chapters.sumOf { it.wordCount }
) {
    val estimatedReadingTime: Int
        get() = (totalWordCount / 200).coerceAtLeast(1)
}