package com.booklibrary.android.domain.usecase

import com.booklibrary.android.domain.repository.BookRepository
import javax.inject.Inject

class RefreshBooksUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke() {
        bookRepository.refreshBooks()
    }
}
