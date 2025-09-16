package com.booklibrary.android.data.mapper

import com.booklibrary.android.data.local.entities.*
import com.booklibrary.android.data.remote.dto.*
import com.booklibrary.android.domain.model.*

fun BookDto.toEntity(): BookEntity {
    return BookEntity(
        id = id,
        title = title,
        description = description,
        chapterCount = chapterCount,
        coverUrl = coverUrl,
        epubUrl = epubUrl,
        language = language,
        translator = translator,
        status = status,
        dateAdded = dateAdded
    )
}

fun BookEntity.toDomain(genres: List<String> = emptyList(), bookmark: Bookmark? = null): Book {
    return Book(
        id = id,
        title = title,
        description = description,
        chapterCount = chapterCount,
        coverUrl = coverUrl,
        epubUrl = epubUrl,
        language = language,
        translator = translator,
        status = status,
        dateAdded = dateAdded,
        genres = genres,
        bookmark = bookmark,
        localFilePath = localFilePath,
        isDownloaded = isDownloaded
    )
}

fun BookDto.toDomain(): Book {
    return Book(
        id = id,
        title = title,
        description = description,
        chapterCount = chapterCount,
        coverUrl = coverUrl,
        epubUrl = epubUrl,
        language = language,
        translator = translator,
        status = status,
        dateAdded = dateAdded,
        genres = genres,
        bookmark = bookmark?.toDomain()
    )
}

fun BookmarkDto.toDomain(): Bookmark {
    return Bookmark(
        bookId = 0, // Will be set separately
        status = BookmarkStatus.fromString(status),
        currentChapter = currentChapter,
        lastUpdated = lastUpdated
    )
}

fun BookmarkEntity.toDomain(): Bookmark {
    return Bookmark(
        bookId = bookId,
        status = BookmarkStatus.fromString(status),
        currentChapter = currentChapter,
        lastUpdated = lastUpdated
    )
}

fun Bookmark.toEntity(): BookmarkEntity {
    return BookmarkEntity(
        bookId = bookId,
        status = status.value,
        currentChapter = currentChapter,
        lastUpdated = lastUpdated
    )
}

fun GenreEntity.toDomain(count: Int = 0): Genre {
    return Genre(name = name, count = count)
}

fun NoteDto.toEntity(): NoteEntity {
    return NoteEntity(
        id = id ?: 0,
        bookId = bookId,
        chapter = chapter,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun NoteEntity.toDomain(): Note {
    return Note(
        id = id,
        bookId = bookId,
        chapter = chapter,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Note.toEntity(): NoteEntity {
    return NoteEntity(
        id = id,
        bookId = bookId,
        chapter = chapter,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ReaderStateEntity.toDomain(): ReaderState {
    return ReaderState(
        bookId = bookId,
        lastLocation = lastLocation,
        fontSize = fontSize,
        theme = ReaderTheme.entries.find { it.value == theme } ?: ReaderTheme.LIGHT,
        brightness = brightness
    )
}

fun ReaderState.toEntity(): ReaderStateEntity {
    return ReaderStateEntity(
        bookId = bookId,
        lastLocation = lastLocation,
        fontSize = fontSize,
        theme = theme.value,
        brightness = brightness
    )
}
