package com.booklibrary.android.data.mapper

import com.booklibrary.android.data.local.entity.GenreEntity
import com.booklibrary.android.data.remote.dto.GenreDto
import com.booklibrary.android.domain.model.Genre
import kotlin.jvm.JvmName // <--- ДОБАВЛЕН ЭТОТ ИМПОРТ

// GenreDto (из сети) -> Genre (доменная модель)
fun GenreDto.toDomain(): Genre {
    return Genre(
        name = this.name,
        count = this.count
    )
}

// GenreDto (из сети) -> GenreEntity (для БД)
fun GenreDto.toEntity(): GenreEntity {
    return GenreEntity(
        name = this.name,
        count = this.count
    )
}

// GenreEntity (из БД) -> Genre (доменная модель)
fun GenreEntity.toDomain(): Genre {
    return Genre(
        name = this.name,
        count = this.count
    )
}

// Если вам нужно преобразовать список:
@JvmName("genreDtoListToDomainList") // <--- ДОБАВЛЕНА АННОТАЦИЯ
fun List<GenreDto>.toDomainList(): List<Genre> {
    return this.map { it.toDomain() }
}

fun List<GenreDto>.toEntityList(): List<GenreEntity> {
    return this.map { it.toEntity() }
}

@JvmName("genreEntityListToDomainList") // <--- ДОБАВЛЕНА АННОТАЦИЯ
fun List<GenreEntity>.toDomainList(): List<Genre> {
    return this.map { it.toDomain() }
}
