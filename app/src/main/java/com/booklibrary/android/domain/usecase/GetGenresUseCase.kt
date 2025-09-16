package com.booklibrary.android.domain.usecase

import com.booklibrary.android.domain.model.Genre
import com.booklibrary.android.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGenresUseCase @Inject constructor(
    private val repository: BookRepository
) {
    operator fun invoke(): Flow<List<Genre>> {
        return repository.getGenres()
    }
}
