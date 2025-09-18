package com.booklibrary.android.domain.usecase

import com.booklibrary.android.domain.model.DownloadProgress
import com.booklibrary.android.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DownloadBookUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(bookId: Int): Flow<DownloadProgress> {
        return bookRepository.downloadBook(bookId)
    }
}
