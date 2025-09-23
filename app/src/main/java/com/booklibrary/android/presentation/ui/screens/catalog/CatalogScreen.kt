package com.booklibrary.android.presentation.ui.screens.catalog

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booklibrary.android.R
import com.booklibrary.android.presentation.ui.components.BookCard
import com.booklibrary.android.presentation.ui.components.GenreFilterChips
import com.booklibrary.android.presentation.ui.components.SearchBar
import com.booklibrary.android.presentation.viewmodel.CatalogViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private fun openBookFile(context: Context, filePath: String?) {
    filePath?.let {
        try {
            val fileUri = it.toUri()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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
    val isListCurrentlyRefreshing = uiState.isListRefreshing
    val isBookListEmpty = books.isEmpty()
    val shouldShowPullRefreshIndicator = isListCurrentlyRefreshing && !isBookListEmpty
    val pullRefreshState = rememberPullRefreshState(
        refreshing = shouldShowPullRefreshIndicator,
        onRefresh = { viewModel.onRefreshTriggered() }
    )

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTopButton by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 5 } // Порог для отображения кнопки
    }

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            if (isListCurrentlyRefreshing && isBookListEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (isBookListEmpty) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (uiState.showLoadFromApiButton) {
                        Button(onClick = { viewModel.onLoadFromApiClick() }) {
                            Text("Загрузить книги с сервера")
                        }
                    } else {
                        Text("Книги не найдены")
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
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

            if (shouldShowPullRefreshIndicator) {
                PullRefreshIndicator(
                    refreshing = true,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // Кнопка "Вверх"
            if (showScrollToTopButton) {
                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    onClick = {
                        scope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    },
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Прокрутить вверх")
                }
            }
        }
    }
}