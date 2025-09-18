package com.booklibrary.android.presentation.ui.screens.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi // Required for pullRefresh
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator // Added
import androidx.compose.material.pullrefresh.pullRefresh // Added
import androidx.compose.material.pullrefresh.rememberPullRefreshState // Added
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booklibrary.android.R
import com.booklibrary.android.presentation.ui.components.BookCard 
import com.booklibrary.android.presentation.ui.components.GenreFilterChips
import com.booklibrary.android.presentation.ui.components.SearchBar
import com.booklibrary.android.presentation.viewmodel.CatalogViewModel
import kotlinx.coroutines.flow.collectLatest

// Helper function to open book file (can be moved to a util file if needed)
private fun openBookFile(context: Context, filePath: String?) {
    filePath?.let {
        try {
            val fileUri = Uri.parse(it)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/epub+zip")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) 
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                val chooser = Intent.createChooser(intent, "Открыть книгу с помощью...")
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "Не найдено приложение для открытия EPUB файлов", Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
             Log.e("OpenFileError", "SecurityException opening file URI: $it", e)
             Toast.makeText(context, "Ошибка безопасности при открытии файла.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("OpenFileError", "Error opening file URI: $it", e)
            Toast.makeText(context, "Ошибка при открытии файла: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    } ?: run {
        Toast.makeText(context, "Путь к файлу не найден", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class) // Added ExperimentalMaterialApi
@Composable
fun CatalogScreen(
    onBookClick: (Int) -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val books = uiState.books 
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenreState.collectAsStateWithLifecycle()
    val availableGenres by viewModel.availableGenres.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // State for pull-to-refresh
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading, // uiState.isLoading будет управлять состоянием индикатора
        onRefresh = { viewModel.onRefreshTriggered() }
    )

    LaunchedEffect(Unit) {
        viewModel.openFileEvent.collectLatest { filePath ->
            openBookFile(context, filePath)
        }
    }

    uiState.error?.let { errorMsg ->
        LaunchedEffect(errorMsg) { 
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            viewModel.clearError() 
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
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

        SearchBar(
            query = searchQuery,
            onQueryChange = viewModel::onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        GenreFilterChips(
            genres = availableGenres,
            selectedGenre = selectedGenre,
            onGenreSelected = viewModel::onGenreFilterChange,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        // Box to enable pull-to-refresh over the list area
        Box(modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState) // Apply pullRefresh modifier here
        ) {
            if (uiState.isLoading && books.isEmpty()) { // Показываем только если список пуст и идет начальная загрузка
                Box(
                    modifier = Modifier.fillMaxSize(), // Этот Box не должен быть pullable, только его контент
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator() // Этот индикатор для начальной загрузки, не для pull-refresh
                }
            } else if (books.isEmpty() && !uiState.isLoading) {
                // Сообщение "Книги не найдены" также должно быть частью области pull-refresh
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Книги не найдены") // Используем временную строку или R.string.no_books_found
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(), // LazyColumn сам по себе заполняет Box
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), // Добавил bottom padding
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        val baseUrl = "http://10.93.2.6:8080"
                        val fullCoverUrl = if (book.coverUrl?.startsWith("http") == true) {
                            book.coverUrl
                        } else {
                            book.coverUrl?.let { baseUrl.trimEnd('/') + it } ?: ""
                        }
                        
                        BookCard(
                            book = book.copy(coverUrl = fullCoverUrl),
                            onClick = { onBookClick(book.id) },
                            onBookmarkToggle = { viewModel.toggleBookmark(book) },
                            onActionClick = { viewModel.handleOpenOrDownloadClick(book) }, 
                            downloadProgress = uiState.downloadProgress[book.id]
                        )
                    }
                }
            }

            // PullRefreshIndicator должен быть поверх контента
            PullRefreshIndicator(
                refreshing = uiState.isLoading, // Используем isLoading из uiState для управления видимостью
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
