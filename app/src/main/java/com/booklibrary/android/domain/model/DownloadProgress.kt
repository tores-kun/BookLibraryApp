package com.booklibrary.android.domain.model

data class DownloadProgress(
    val bookId: Int,
    val progress: Float = 0f,
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val filePathUri: String? = null,
    val wasAlreadyDownloaded: Boolean = false
)
