package com.booklibrary.android.presentation.ui.screens.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booklibrary.android.R
import com.booklibrary.android.presentation.ui.components.BookCard
import com.booklibrary.android.presentation.ui.components.GenreFilterChips
import com.booklibrary.android.presentation.ui.components.SearchBar
import com.booklibrary.android.presentation.viewmodel.CatalogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onBookClick: (Int) -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenreState.collectAsStateWithLifecycle() // Added
    val availableGenres by viewModel.availableGenres.collectAsStateWithLifecycle() // Added

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = onNavigateToBookmarks) {
                    Icon(Icons.Filled.Bookmark, contentDescription = stringResource(R.string.nav_bookmarks))
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                }
            }
        )

        // Search Bar
        SearchBar(
            query = searchQuery, 
            onQueryChange = viewModel::onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        // Genre Filters
        GenreFilterChips(
            genres = availableGenres, // Assuming this parameter name
            selectedGenre = selectedGenre, 
            onGenreSelected = viewModel::onGenreFilterChange,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Books List
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(books) { book ->
                    // Создаем полный URL для обложки каждой книги
                    val baseUrl = "http://10.93.2.6:8080"
                    val fullCoverUrl = if (book.coverUrl?.startsWith("http") == true) {
                        book.coverUrl
                    } else {
                        book.coverUrl?.let { baseUrl.trimEnd('/') + it } ?: "your_placeholder_image_url_or_empty_string"
                    }

                    BookCard(
                        book = book.copy(coverUrl = fullCoverUrl),
                        onClick = { onBookClick(book.id) },
                        onBookmarkToggle = { viewModel.toggleBookmark(book) },
                        onDownload = { viewModel.downloadBook(book) },
                        downloadProgress = uiState.downloadProgress[book.id]
                    )

                }
            }
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Consider showing a Snackbar or Toast for the error
            // For now, it just clears the error
            viewModel.clearError()
        }
    }
}
