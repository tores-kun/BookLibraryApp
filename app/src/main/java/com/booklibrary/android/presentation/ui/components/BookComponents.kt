package com.booklibrary.android.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle 
import androidx.compose.material.icons.filled.Download 
import androidx.compose.material.icons.filled.ErrorOutline 
import androidx.compose.material.icons.filled.Launch 
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.booklibrary.android.domain.model.Book
import com.booklibrary.android.domain.model.DownloadProgress

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Поиск книг...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        modifier = modifier,
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreFilterChips(
    genres: List<String>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selectedGenre == null,
                onClick = { onGenreSelected(null) },
                label = { Text("Все") }
            )
        }
        items(genres) { genre ->
            FilterChip(
                selected = selectedGenre == genre,
                onClick = { onGenreSelected(genre) },
                label = { Text(genre) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onActionClick: () -> Unit, // <--- ИЗМЕНЕНО: Добавлен onActionClick
    downloadProgress: DownloadProgress?,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top 
        ) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = "Обложка книги",
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = book.genres.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                book.bookmark?.let {
                    Text(
                        text = "Статус: ${it.status.value}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.SpaceBetween, 
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.height(90.dp) 
            ) {
                IconButton(onClick = onBookmarkToggle) {
                    Icon(
                        imageVector = if (book.bookmark != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Закладка",
                        tint = if (book.bookmark != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                // Логика отображения кнопки Скачать/Прогресс/Открыть/Ошибка
                when {
                    downloadProgress?.error != null -> {
                        IconButton(onClick = onActionClick) { // <--- ИЗМЕНЕНО: используется onActionClick
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = "Ошибка загрузки",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    downloadProgress?.isLoading == true && !downloadProgress.isComplete -> {
                        CircularProgressIndicator(
                            progress = downloadProgress.progress,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp 
                        )
                    }
                    book.isDownloaded && !book.localFilePath.isNullOrBlank() -> {
                        IconButton(onClick = onActionClick) { // <--- ИЗМЕНЕНО: используется onActionClick
                            Icon(
                                Icons.Filled.Launch, 
                                contentDescription = "Открыть книгу",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    else -> {
                        IconButton(onClick = onActionClick) { // <--- ИЗМЕНЕНО: используется onActionClick
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Скачать",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
