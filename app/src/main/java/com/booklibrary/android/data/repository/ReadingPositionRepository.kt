package com.booklibrary.android.data.repository

import com.booklibrary.android.data.local.dao.ReadingPositionDao
import com.booklibrary.android.domain.model.ReadingPosition
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingPositionRepository @Inject constructor(
    private val readingPositionDao: ReadingPositionDao
) {
    
    suspend fun getReadingPosition(bookId: Int): ReadingPosition? {
        return readingPositionDao.getReadingPosition(bookId)
    }
    
    fun getReadingPositionFlow(bookId: Int): Flow<ReadingPosition?> {
        return readingPositionDao.getReadingPositionFlow(bookId)
    }
    
    suspend fun saveReadingPosition(
        bookId: Int,
        chapterIndex: Int,
        textPosition: Int,
        scrollPosition: Float,
        totalProgress: Float
    ) {
        val position = ReadingPosition(
            bookId = bookId,
            chapterIndex = chapterIndex,
            textPosition = textPosition,
            scrollPosition = scrollPosition,
            totalProgress = totalProgress,
            lastReadTimestamp = System.currentTimeMillis()
        )
        readingPositionDao.insertOrUpdateReadingPosition(position)
    }
    
    suspend fun updateProgress(bookId: Int, progress: Float) {
        readingPositionDao.updateProgress(bookId, progress)
    }
    
    suspend fun deleteReadingPosition(bookId: Int) {
        readingPositionDao.deleteReadingPositionByBookId(bookId)
    }
    
    fun getAllReadingPositions(): Flow<List<ReadingPosition>> {
        return readingPositionDao.getAllReadingPositions()
    }
}