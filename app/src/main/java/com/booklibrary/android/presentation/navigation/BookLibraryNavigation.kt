package com.booklibrary.android.presentation.navigation

import android.net.Uri // Добавлен импорт для Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavController // Добавлен импорт для NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType // Добавлен импорт для NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument // Добавлен импорт для navArgument
import com.booklibrary.android.presentation.ui.screens.catalog.CatalogScreen
import com.booklibrary.android.presentation.ui.screens.book_details.BookDetailsScreen
import com.booklibrary.android.presentation.ui.screens.reader.ReaderScreen
import com.booklibrary.android.presentation.ui.screens.bookmarks.BookmarksScreen
import com.booklibrary.android.presentation.ui.screens.notes.NotesScreen
import com.booklibrary.android.presentation.ui.screens.settings.SettingsScreen

// Функция для навигации к читалке
fun navigateToReader(navController: NavController, bookId: Int, filePath: String) {
    val encodedFilePath = Uri.encode(filePath)
    navController.navigate("reader/$bookId/$encodedFilePath")
}

@Composable
fun BookLibraryNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "catalog"
    ) {
        composable("catalog") {
            CatalogScreen(
                onBookClick = { bookId ->
                    navController.navigate("book_details/$bookId")
                },
                onNavigateToBookmarks = {
                    navController.navigate("bookmarks")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("book_details/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")?.toIntOrNull() ?: 0
            BookDetailsScreen(
                bookId = bookId,
                onBackClick = { navController.popBackStack() },
                // Важно: onReadClick здесь нужно будет обновить в BookDetailsScreen.kt,
                // чтобы он вызывал navigateToReader(navController, bookId, book.filePath)
                // Это потребует доступа к filePath книги на экране BookDetailsScreen.
                onReadClick = { currentBookId, currentBookFilePath -> // Предполагаем, что BookDetailsScreen сможет предоставить filePath
                    navigateToReader(navController, currentBookId, currentBookFilePath)
                },
                onNotesClick = { bookId ->
                    navController.navigate("notes/$bookId")
                }
            )
        }

        // Обновленный маршрут для ReaderScreen
        composable(
            route = "reader/{bookId}/{filePath}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.IntType },
                navArgument("filePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 0 // return@composable если ID критичен
            val filePath = backStackEntry.arguments?.getString("filePath") ?: "" // return@composable если filePath критичен
            
            // Предполагается, что ReaderScreen теперь принимает filePath
            ReaderScreen(
                bookId = bookId,
                filePath = Uri.decode(filePath), // Декодируем путь к файлу
                onBackClick = { 
                    navController.popBackStack() 
                }
            )
        }

        composable("bookmarks") {
            BookmarksScreen(
                onBackClick = { navController.popBackStack() },
                onBookClick = { bookId ->
                    navController.navigate("book_details/$bookId")
                }
            )
        }

        composable("notes/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")?.toIntOrNull() ?: 0
            NotesScreen(
                bookId = bookId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
