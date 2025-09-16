package com.booklibrary.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.booklibrary.android.data.local.entities.BookEntity
import com.booklibrary.android.data.local.entity.GenreEntity


@Entity(
    tableName = "book_genres",
    primaryKeys = ["bookId", "genreName"],
    indices = [Index(value = ["genreName"])],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GenreEntity::class, 
            parentColumns = ["name"],
            childColumns = ["genreName"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookGenreEntity(
    val bookId: Int,
    val genreName: String
)
