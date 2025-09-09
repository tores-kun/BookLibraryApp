package com.booklibrary.android.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.booklibrary.android.presentation.ui.screens.catalog.CatalogScreen
import com.booklibrary.android.presentation.ui.screens.book_details.BookDetailsScreen
import com.booklibrary.android.presentation.ui.screens.reader.ReaderScreen
import com.booklibrary.android.presentation.ui.screens.bookmarks.BookmarksScreen
import com.booklibrary.android.presentation.ui.screens.notes.NotesScreen
import com.booklibrary.android.presentation.ui.screens.settings.SettingsScreen

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
                onReadClick = { bookId ->
                    navController.navigate("reader/$bookId")
                },
                onNotesClick = { bookId ->
                    navController.navigate("notes/$bookId")
                }
            )
        }

        composable("reader/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")?.toIntOrNull() ?: 0
            ReaderScreen(
                bookId = bookId,
                onBackClick = { navController.popBackStack() }
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
