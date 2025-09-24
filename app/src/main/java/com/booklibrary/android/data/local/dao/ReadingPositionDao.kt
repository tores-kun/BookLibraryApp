package com.booklibrary.android.data.local.dao

import androidx.room.*
import com.booklibrary.android.domain.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPositionDao {
    
    @Query("SELECT * FROM reading_positions WHERE bookId = :bookId")
    suspend fun getReadingPosition(bookId: Int): ReadingPosition?
    
    @Query("SELECT * FROM reading_positions WHERE bookId = :bookId")
    fun getReadingPositionFlow(bookId: Int): Flow<ReadingPosition?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReadingPosition(position: ReadingPosition)
    
    @Delete
    suspend fun deleteReadingPosition(position: ReadingPosition)
    
    @Query("DELETE FROM reading_positions WHERE bookId = :bookId")
    suspend fun deleteReadingPositionByBookId(bookId: Int)
    
    @Query("SELECT * FROM reading_positions ORDER BY lastReadTimestamp DESC")
    fun getAllReadingPositions(): Flow<List<ReadingPosition>>
    
    @Query("UPDATE reading_positions SET totalProgress = :progress WHERE bookId = :bookId")
    suspend fun updateProgress(bookId: Int, progress: Float)
}