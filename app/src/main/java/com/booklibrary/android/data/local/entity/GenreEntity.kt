package com.booklibrary.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey val name: String,
    val count: Int
)
