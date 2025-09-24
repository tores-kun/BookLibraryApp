package com.booklibrary.android.data.epub

import android.content.Context
import nl.siegmann.epublib.epub.EpubReader
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.SpineReference
import nl.siegmann.epublib.domain.Resource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import com.booklibrary.android.domain.model.1ChapterContent
import com.booklibrary.android.domain.model.EpubBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpubParser @Inject constructor(
    private val context: Context
) {
    
    /**
     * Парсит EPUB файл и извлекает содержимое
     */
    suspend fun parseEpubFile(
        bookId: Int,
        filePath: String
    ): Result<EpubBook> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(
                    Exception("EPUB файл не найден: $filePath")
                )
            }

            val epubReader = EpubReader()
            val book = epubReader.readEpub(FileInputStream(file))
            
            val chapters = extractChapters(book)
            val title = book.title ?: "Неизвестное название"
            val author = book.metadata?.authors?.firstOrNull()?.let { 
                "${it.firstname} ${it.lastname}".trim() 
            } ?: "Неизвестный автор"
            
            val coverImagePath = extractCoverImage(book, bookId)
            
            val epubBook = EpubBook(
                id = bookId,
                title = title,
                author = author,
                filePath = filePath,
                chapters = chapters,
                coverImagePath = coverImagePath
            )
            
            Result.success(epubBook)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Извлекает главы из EPUB книги
     */
    private suspend fun extractChapters(book: Book): List<ChapterContent> = 
        withContext(Dispatchers.IO) {
            val chapters = mutableListOf<ChapterContent>()
            
            book.spine.spineReferences.forEachIndexed { index, spineRef ->
                try {
                    val resource = spineRef.resource
                    val inputStream = resource.inputStream
                    val content = inputStream.bufferedReader().use { it.readText() }
                    
                    val cleanContent = cleanHtmlContent(content)
                    val title = extractChapterTitle(content, index)
                    val wordCount = countWords(cleanContent)
                    
                    if (cleanContent.isNotBlank()) {
                        chapters.add(
                            ChapterContent(
                                index = index,
                                title = title,
                                content = cleanContent,
                                wordCount = wordCount
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Пропускаем проблемные главы
                    e.printStackTrace()
                }
            }
            
            chapters
        }
    
    /**
     * Очищает HTML контент от тегов
     */
    private fun cleanHtmlContent(htmlContent: String): String {
        return try {
            val doc: Document = Jsoup.parse(htmlContent)
            
            // Удаляем ненужные элементы
            doc.select("style, script, meta, link").remove()
            
            // Извлекаем текст с сохранением структуры
            val text = doc.body()?.text() ?: doc.text()
            
            // Очищаем лишние пробелы и переносы
            text.replace(Regex("\\s+"), " ")
                .replace(Regex("\\n\\s*\\n"), "\n\n")
                .trim()
                
        } catch (e: Exception) {
            // Если парсинг HTML не удался, возвращаем исходный текст
            htmlContent.replace(Regex("<[^>]*>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
    
    /**
     * Извлекает заголовок главы
     */
    private fun extractChapterTitle(htmlContent: String, chapterIndex: Int): String {
        return try {
            val doc = Jsoup.parse(htmlContent)
            
            // Ищем заголовки в порядке приоритета
            val titleSelectors = listOf("h1", "h2", "h3", "title", ".chapter-title")
            
            for (selector in titleSelectors) {
                val element = doc.selectFirst(selector)
                if (element != null) {
                    val title = element.text().trim()
                    if (title.isNotBlank() && title.length < 200) {
                        return title
                    }
                }
            }
            
            "Глава ${chapterIndex + 1}"
        } catch (e: Exception) {
            "Глава ${chapterIndex + 1}"
        }
    }
    
    /**
     * Подсчитывает количество слов в тексте
     */
    private fun countWords(text: String): Int {
        return text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .size
    }
    
    /**
     * Извлекает обложку книги
     */
    private suspend fun extractCoverImage(
        book: Book, 
        bookId: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val coverImage = book.coverImage
            if (coverImage != null) {
                val coversDir = File(context.filesDir, "covers")
                if (!coversDir.exists()) {
                    coversDir.mkdirs()
                }
                
                val imageFile = File(coversDir, "cover_$bookId.jpg")
                imageFile.writeBytes(coverImage.data)
                
                imageFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Получает конкретную главу по индексу
     */
    suspend fun getChapterContent(
        filePath: String, 
        chapterIndex: Int
    ): Result<ChapterContent> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(
                    Exception("EPUB файл не найден")
                )
            }

            val epubReader = EpubReader()
            val book = epubReader.readEpub(FileInputStream(file))
            
            val spineReferences = book.spine.spineReferences
            if (chapterIndex >= spineReferences.size) {
                return@withContext Result.failure(
                    Exception("Глава не найдена")
                )
            }
            
            val spineRef = spineReferences[chapterIndex]
            val resource = spineRef.resource
            val content = resource.inputStream.bufferedReader().use { it.readText() }
            
            val cleanContent = cleanHtmlContent(content)
            val title = extractChapterTitle(content, chapterIndex)
            val wordCount = countWords(cleanContent)
            
            Result.success(
                ChapterContent(
                    index = chapterIndex,
                    title = title,
                    content = cleanContent,
                    wordCount = wordCount
                )
            )
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}