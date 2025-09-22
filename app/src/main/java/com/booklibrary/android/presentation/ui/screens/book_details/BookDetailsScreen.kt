package com.booklibrary.android.presentation.ui.screens.book_details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.booklibrary.android.R
import com.booklibrary.android.domain.model.DownloadProgress 
import com.booklibrary.android.presentation.viewmodel.BookDetailsViewModel
import kotlinx.coroutines.flow.collectLatest

private fun openBookFile(context: Context, filePath: String?) {
    filePath?.let {
        try {
            val fileUri = Uri.parse(it) 
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/epub+zip")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Verify that an app exists to receive the intent
            if (intent.resolveActivity(context.packageManager) != null) {
                val chooser = Intent.createChooser(intent, "Открыть книгу с помощью...")
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "Не найдено приложение для открытия EPUB файлов", Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
             Log.e("OpenFileError", "SecurityException opening file URI: $it", e)
             Toast.makeText(context, "Ошибка безопасности при открытии файла. Проверьте права доступа.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("OpenFileError", "Error opening file URI: $it", e)
            Toast.makeText(context, "Ошибка при открытии файла: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    } ?: run {
        Toast.makeText(context, "Путь к файлу не указан", Toast.LENGTH_LONG).show()
    }
}

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
    val context = LocalContext.current

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
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
            title = { Text(stringResource(R.string.book_details)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            }
        )

        if (uiState.isLoading && uiState.book == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.book != null) {
            val book = uiState.book!!
            val downloadProgressForThisBook = if (uiState.downloadProgress?.bookId == book.id) {
                uiState.downloadProgress
            } else {
                null 
            }

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
                    val baseUrl = "http://10.93.2.6:8080"
                    val fullCoverUrl = if (book.coverUrl?.startsWith("http") == true) {
                        book.coverUrl
                    } else {
                        if (book.coverUrl != null) {
                            baseUrl.trimEnd('/') + book.coverUrl
                        } else {
                            ""
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onReadClick(book.id) }, 
                        modifier = Modifier.fillMaxWidth(),
                        enabled = book.isDownloaded && !book.localFilePath.isNullOrBlank() 
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_read))
                    }

                    Button(
                        onClick = { viewModel.handleOpenBookClick() }, 
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !(downloadProgressForThisBook?.isLoading == true && downloadProgressForThisBook.progress > 0f && !downloadProgressForThisBook.isComplete)
                    ) {
                        when {
                            downloadProgressForThisBook?.error != null -> {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ошибка. Повторить?") 
                            }
                            downloadProgressForThisBook?.isLoading == true && !downloadProgressForThisBook.isComplete -> {
                                CircularProgressIndicator(
                                    progress = downloadProgressForThisBook.progress,
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(String.format("Загрузка %.0f%%", downloadProgressForThisBook.progress * 100))
                            }
                            book.isDownloaded && !book.localFilePath.isNullOrBlank() && downloadProgressForThisBook == null -> {
                                Icon(Icons.Filled.Launch, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Открыть книгу") 
                            }
                            else -> {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_download))
                            }
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
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Книга не найдена", // TODO: Замените на stringResource(R.string.book_not_found_error) после добавления ресурса
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
