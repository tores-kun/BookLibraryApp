package com.booklibrary.android.domain.usecase

import com.booklibrary.android.domain.repository.BookRepository
import javax.inject.Inject

class RefreshGenresUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke() {
        repository.refreshGenres()
    }
}
