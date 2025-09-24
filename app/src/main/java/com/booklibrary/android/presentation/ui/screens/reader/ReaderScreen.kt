package com.booklibrary.android.presentation.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// import androidx.compose.foundation.lazy.rememberLazyListState // Неиспользуемый импорт удален
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booklibrary.android.domain.model.* // Убедитесь, что ReaderUiState и другие модели здесь
import com.booklibrary.android.presentation.viewmodel.ReaderViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Int,
    filePath: String,
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    
    // Загружаем книгу при первом входе
    LaunchedEffect(bookId, filePath) {
        viewModel.loadBook(bookId, filePath)
    }
    
    // Обработка ошибок
    LaunchedEffect(uiState.error, uiState.ttsError) {
        uiState.error?.let { error ->
            // Показать Snackbar с ошибкой (логика отображения Snackbar должна быть здесь)
            viewModel.clearError()
        }
        uiState.ttsError?.let { error ->
            // Показать TTS ошибку (логика отображения Snackbar должна быть здесь)
            viewModel.clearError()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(getThemeBackgroundColor(readerSettings.theme))
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Загрузка книги...",
                        color = getThemeTextColor(readerSettings.theme)
                    )
                }
            }
        } else {
            val currentChapter = uiState.currentChapter // Локальная переменная для стабильности
            if (currentChapter != null) {
                Column {
                    // Top App Bar
                    ReaderTopAppBar(
                        uiState = uiState,
                        readerSettings = readerSettings,
                        onBackClick = onBackClick,
                        onSettingsClick = { viewModel.toggleSettingsPanel() },
                        onChaptersClick = { viewModel.toggleChaptersList() },
                        onTtsClick = {
                            if (uiState.isTtsSpeaking) {
                                viewModel.pauseTts()
                            } else if (uiState.isTtsPaused) {
                                viewModel.startTts() // Возможно, здесь resumeTts(), если есть такая логика
                            } else {
                                viewModel.startTts()
                            }
                        }
                    )
                    
                    // Main content
                    Box(modifier = Modifier.weight(1f)) {
                        // Chapter content
                        ChapterContentView(
                            chapter = currentChapter, // Используем локальную переменную
                            readerSettings = readerSettings,
                            scrollPosition = uiState.scrollPosition,
                            onScrollPositionChange = { viewModel.updateScrollPosition(it) }
                        )
                        
                        // Settings panel overlay
                        AnimatedVisibility(
                            visible = uiState.showSettingsPanel,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut()
                        ) {
                            ReaderSettingsPanel(
                                settings = readerSettings,
                                // uiState = uiState, // Удалено, так как не используется
                                onFontSizeChange = { viewModel.updateFontSize(it) },
                                onThemeChange = { viewModel.updateTheme(it) },
                                onLineHeightChange = { viewModel.updateLineHeight(it) },
                                // onFontFamilyChange = { viewModel.updateFontFamily(it) }, // Удалено, так как не используется
                                // onTextAlignmentChange = { viewModel.updateTextAlignment(it) }, // Удалено, так как не используется
                                onTtsSpeedChange = { viewModel.updateTtsSpeed(it) },
                                onTtsPitchChange = { viewModel.updateTtsPitch(it) },
                                onClose = { viewModel.toggleSettingsPanel() }
                            )
                        }
                        
                        // Chapters list drawer
                        AnimatedVisibility(
                            visible = uiState.showChaptersList,
                            enter = slideInHorizontally { -it } + fadeIn(),
                            exit = slideOutHorizontally { -it } + fadeOut()
                        ) {
                            ChaptersListDrawer(
                                chapters = uiState.epubBook?.chapters ?: emptyList(),
                                currentChapterIndex = uiState.currentChapterIndex,
                                readerSettings = readerSettings,
                                onChapterSelect = { index ->
                                    viewModel.navigateToChapter(index)
                                    viewModel.toggleChaptersList()
                                },
                                onClose = { viewModel.toggleChaptersList() }
                            )
                        }
                    }
                    
                    // Bottom navigation
                    ReaderBottomNavigation(
                        uiState = uiState,
                        readerSettings = readerSettings,
                        onPreviousChapter = { viewModel.navigateToPreviousChapter() },
                        onNextChapter = { viewModel.navigateToNextChapter() }
                    )
                }
            } else if (uiState.error != null) {
                // Error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error ?: "Неизвестная ошибка",
                            color = getThemeTextColor(readerSettings.theme),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBackClick
                        ) {
                            Text("Назад")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopAppBar(
    uiState: ReaderUiState, // Убедитесь, что ReaderUiState здесь корректно определен и импортирован
    readerSettings: ReaderSettings,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onChaptersClick: () -> Unit,
    onTtsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = uiState.currentChapter?.title ?: "Глава", // Зависит от ReaderUiState
                    color = getThemeTextColor(readerSettings.theme),
                    maxLines = 1
                )
                LinearProgressIndicator(
                    progress = uiState.totalProgress, // Зависит от ReaderUiState
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = getThemeTextColor(readerSettings.theme).copy(alpha = 0.3f),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = getThemeTextColor(readerSettings.theme)
                )
            }
        },
        actions = {
            // TTS button
            IconButton(onClick = onTtsClick) {
                val icon = when {
                    uiState.isTtsLoading -> Icons.Filled.HourglassEmpty // Зависит от ReaderUiState
                    uiState.isTtsSpeaking -> Icons.Filled.Pause // Зависит от ReaderUiState
                    uiState.isTtsPaused -> Icons.Filled.PlayArrow // Зависит от ReaderUiState
                    else -> Icons.Filled.VolumeUp
                }
                Icon(
                    icon,
                    contentDescription = "Озвучка",
                    tint = if (uiState.isTtsSpeaking) MaterialTheme.colorScheme.primary // Зависит от ReaderUiState
                           else getThemeTextColor(readerSettings.theme)
                )
            }
            
            // Chapters button
            IconButton(onClick = onChaptersClick) {
                Icon(
                    Icons.Filled.List,
                    contentDescription = "Главы",
                    tint = getThemeTextColor(readerSettings.theme)
                )
            }
            
            // Settings button
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Настройки",
                    tint = getThemeTextColor(readerSettings.theme)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = getThemeBackgroundColor(readerSettings.theme)
        )
    )
}

@Composable
private fun ChapterContentView(
    chapter: ChapterContent, // Убедитесь, что ChapterContent здесь корректно определен и импортирован
    readerSettings: ReaderSettings,
    scrollPosition: Float,
    onScrollPositionChange: (Float) -> Unit
) {
    val scrollState = rememberScrollState()
    
    // Синхронизируем позицию скролла
    LaunchedEffect(scrollPosition, scrollState.maxValue) { // Добавлено scrollState.maxValue для корректной работы при изменении контента
        if (scrollPosition >= 0f && scrollState.maxValue > 0) { // >= 0f для начальной установки
            scrollState.scrollTo((scrollState.maxValue * scrollPosition).toInt())
        }
    }
    
    // Отслеживаем изменения скролла
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val position = scrollState.value.toFloat() / scrollState.maxValue
            onScrollPositionChange(position)
        } else if (scrollState.value == 0 && scrollState.maxValue == 0) { // Если контент пуст или очень мал
            onScrollPositionChange(0f)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = chapter.content,
            fontSize = readerSettings.fontSize.sp,
            lineHeight = (readerSettings.fontSize * readerSettings.lineHeight).sp,
            color = getThemeTextColor(readerSettings.theme),
            fontFamily = getFontFamily(readerSettings.fontFamily),
            textAlign = getTextAlignment(readerSettings.textAlignment),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Chapter info
        Divider(
            color = getThemeTextColor(readerSettings.theme).copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        Text(
            text = "Примерное время чтения: ${chapter.estimatedReadingTime} мин",
            fontSize = 12.sp,
            color = getThemeTextColor(readerSettings.theme).copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(100.dp)) // Отступ для нижней навигации
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsPanel(
    settings: ReaderSettings,
    // uiState: ReaderUiState, // Удалено
    onFontSizeChange: (Int) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    // onFontFamilyChange: (ReaderFont) -> Unit, // Удалено
    // onTextAlignmentChange: (ReaderTextAlignment) -> Unit, // Удалено
    onTtsSpeedChange: (Float) -> Unit,
    onTtsPitchChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = getThemeBackgroundColor(settings.theme)
        ),
        border = BorderStroke(
            1.dp,
            getThemeTextColor(settings.theme).copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Настройки читалки",
                    style = MaterialTheme.typography.headlineSmall,
                    color = getThemeTextColor(settings.theme)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = getThemeTextColor(settings.theme)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Font Size
            Text(
                text = "Размер шрифта: ${settings.fontSize}sp",
                color = getThemeTextColor(settings.theme)
            )
            Slider(
                value = settings.fontSize.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 12f..24f,
                steps = 11, // 24-12 = 12 intervals, so 11 steps + start/end
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Theme Selection
            Text(
                text = "Тема оформления",
                color = getThemeTextColor(settings.theme)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReaderTheme.entries.forEach { theme -> // Используем .entries
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { onThemeChange(theme) },
                        label = { Text(theme.name) }, // Используем .name вместо .displayName
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Line Height
            Text(
                text = "Межстрочный интервал: ${String.format(Locale.getDefault(), "%.1f", settings.lineHeight)}",
                color = getThemeTextColor(settings.theme)
            )
            Slider(
                value = settings.lineHeight,
                onValueChange = onLineHeightChange,
                valueRange = 1.0f..2.0f,
                steps = 9, // (2.0-1.0)/0.1 = 10 intervals, so 9 steps + start/end
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // TTS Settings
            if (settings.ttsEnabled) {
                Text(
                    text = "Настройки озвучки",
                    style = MaterialTheme.typography.titleMedium,
                    color = getThemeTextColor(settings.theme)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // TTS Speed
                Text(
                    text = "Скорость речи: ${String.format(Locale.getDefault(), "%.1f", settings.ttsSpeed)}",
                    color = getThemeTextColor(settings.theme)
                )
                Slider(
                    value = settings.ttsSpeed,
                    onValueChange = onTtsSpeedChange,
                    valueRange = 0.1f..3.0f, // Уточните шаги, если нужно
                    modifier = Modifier.fillMaxWidth()
                )
                
                // TTS Pitch
                Text(
                    text = "Высота тона: ${String.format(Locale.getDefault(), "%.1f", settings.ttsPitch)}",
                    color = getThemeTextColor(settings.theme)
                )
                Slider(
                    value = settings.ttsPitch,
                    onValueChange = onTtsPitchChange,
                    valueRange = 0.5f..2.0f, // Уточните шаги, если нужно
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersListDrawer(
    chapters: List<ChapterContent>, // Убедитесь, что ChapterContent здесь корректно определен и импортирован
    currentChapterIndex: Int,
    readerSettings: ReaderSettings,
    onChapterSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = getThemeBackgroundColor(readerSettings.theme)
        )
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Главы",
                    style = MaterialTheme.typography.headlineSmall,
                    color = getThemeTextColor(readerSettings.theme)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = getThemeTextColor(readerSettings.theme)
                    )
                }
            }
            
            Divider(color = getThemeTextColor(readerSettings.theme).copy(alpha = 0.3f))
            
            // Chapters list
            LazyColumn {
                items(chapters) { chapter ->
                    val isCurrentChapter = chapter.index == currentChapterIndex
                    
                    Card( // Эта карта является кликабельной, что требует OptIn
                        onClick = { onChapterSelect(chapter.index) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentChapter) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else 
                                Color.Transparent // Или getThemeBackgroundColor(readerSettings.theme).copy(alpha = 0.5f) для другого эффекта
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrentChapter)
                                    MaterialTheme.colorScheme.primary
                                else
                                    getThemeTextColor(readerSettings.theme),
                                fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2
                            )
                            Text(
                                text = "${chapter.estimatedReadingTime} мин чтения",
                                style = MaterialTheme.typography.bodySmall,
                                color = getThemeTextColor(readerSettings.theme).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomNavigation(
    uiState: ReaderUiState, // Убедитесь, что ReaderUiState здесь корректно определен и импортирован
    readerSettings: ReaderSettings,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    val book = uiState.epubBook ?: return // Зависит от ReaderUiState
    val hasPrevious = uiState.currentChapterIndex > 0 // Зависит от ReaderUiState
    val hasNext = uiState.currentChapterIndex < book.chapters.size - 1 // Зависит от ReaderUiState
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = getThemeBackgroundColor(readerSettings.theme),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPreviousChapter,
                enabled = hasPrevious,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.NavigateBefore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Назад")
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${uiState.currentChapterIndex + 1} из ${book.chapters.size}", // Зависит от ReaderUiState
                    style = MaterialTheme.typography.bodySmall,
                    color = getThemeTextColor(readerSettings.theme)
                )
                Text(
                    text = "${(uiState.totalProgress * 100).toInt()}%", // Зависит от ReaderUiState
                    style = MaterialTheme.typography.bodyMedium,
                    color = getThemeTextColor(readerSettings.theme),
                    fontWeight = FontWeight.Bold
                )
            }
            
            Button(
                onClick = onNextChapter,
                enabled = hasNext,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Вперед")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Filled.NavigateNext,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// Utility functions for theming
@Composable
private fun getThemeBackgroundColor(theme: ReaderTheme): Color {
    return when (theme) {
        ReaderTheme.LIGHT -> Color.White
        ReaderTheme.DARK -> Color(0xFF121212)
        ReaderTheme.SEPIA -> Color(0xFFF4F1E8)
    }
}

@Composable
private fun getThemeTextColor(theme: ReaderTheme): Color {
    return when (theme) {
        ReaderTheme.LIGHT -> Color.Black
        ReaderTheme.DARK -> Color.White
        ReaderTheme.SEPIA -> Color(0xFF5D4037)
    }
}

private fun getFontFamily(font: ReaderFont): FontFamily {
    return when (font) {
        ReaderFont.DEFAULT -> FontFamily.Default
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.SANS_SERIF -> FontFamily.SansSerif
        ReaderFont.MONOSPACE -> FontFamily.Monospace
    }
}

private fun getTextAlignment(alignment: ReaderTextAlignment): TextAlign {
    return when (alignment) {
        ReaderTextAlignment.LEFT -> TextAlign.Start
        ReaderTextAlignment.CENTER -> TextAlign.Center
        ReaderTextAlignment.JUSTIFY -> TextAlign.Justify
    }
}
