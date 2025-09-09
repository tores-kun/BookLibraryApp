package com.booklibrary.android.presentation.ui.screens.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Int,
    onBackClick: () -> Unit
) {
    var currentChapter by remember { mutableStateOf(1) }
    var fontSize by remember { mutableStateOf(16.sp) }
    var showSettings by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Глава $currentChapter") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                }
            }
        )

        if (showSettings) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Размер шрифта")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (fontSize.value > 12) fontSize = (fontSize.value - 2).sp }
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Уменьшить")
                        }
                        Text("${fontSize.value.toInt()}sp", modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(
                            onClick = { if (fontSize.value < 24) fontSize = (fontSize.value + 2).sp }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Увеличить")
                        }
                    }
                }
            }
        }

        Text(
            text = getSampleChapterContent(currentChapter),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            fontSize = fontSize,
            lineHeight = fontSize * 1.5f,
            textAlign = TextAlign.Start
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (currentChapter > 1) currentChapter-- },
                enabled = currentChapter > 1
            ) {
                Icon(Icons.Filled.NavigateBefore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Назад")
            }

            Text(
                text = "Глава $currentChapter",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(onClick = { currentChapter++ }) {
                Text("Вперед")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.NavigateNext, contentDescription = null)
            }
        }
    }
}

private fun getSampleChapterContent(chapter: Int): String {
    return """
        Глава $chapter

        Это пример текста книги для демонстрации функциональности читалки.
        В реальном приложении здесь будет отображаться содержимое EPUB файла.

        Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod
        tempor incididunt ut labore et dolore magna aliqua.
    """.trimIndent()
}
