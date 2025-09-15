package com.booklibrary.android.presentation.ui.screens.book_details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.booklibrary.android.R
import com.booklibrary.android.presentation.viewmodel.BookDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    bookId: Int,
    onBackClick: () -> Unit,
    onReadClick: (Int) -> Unit,
    onNotesClick: (Int) -> Unit,
    viewModel: BookDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.book_details)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            }
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            uiState.book?.let { book ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Создаем полный URL для обложки
                        val baseUrl = "http://10.93.2.6:8080"
                        val fullCoverUrl = if (book.coverUrl?.startsWith("http") == true) { // Явно проверяем на true
                            book.coverUrl
                        } else {
                            // Обрабатываем случай, когда book.coverUrl равен null или не начинается с "http"
                            if (book.coverUrl != null) {
                                baseUrl.trimEnd('/') + book.coverUrl
                            } else {
                                // Укажите URL по умолчанию или обработайте случай отсутствия URL обложки
                                "" // Или URL какого-нибудь изображения-заполнителя
                            }
                        }

                        AsyncImage(
                            model = fullCoverUrl,
                            contentDescription = "Обложка книги",
                            modifier = Modifier
                                .width(120.dp)
                                .height(180.dp),
                            contentScale = ContentScale.Crop
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Используем stringResource с параметрами вместо строковой интерполяции
                            Text(
                                text = stringResource(R.string.chapter_count, book.chapterCount),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = stringResource(R.string.language, book.language),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (book.translator.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.translator, book.translator),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.genres),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = book.genres.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.description),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = book.description,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onReadClick(book.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = book.isDownloaded
                        ) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_read))
                        }

                        if (!book.isDownloaded) {
                            Button(
                                onClick = { viewModel.downloadBook() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_download))
                            }
                        }

                        OutlinedButton(
                            onClick = { onNotesClick(book.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Note, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_notes))
                        }
                    }
                }
            }
        }
    }
}
