package ru.kladovka

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ru.kladovka.data.AppDatabase
import ru.kladovka.data.Repository
import ru.kladovka.ui.*

fun main() = application {
    val databasePath = java.nio.file.Paths.get(System.getProperty("user.home"))
        .resolve(".kladovka-desktop").resolve("kladovka.db")
    
    AppDatabase.setDatabasePath(databasePath)
    val db = AppDatabase.create(databasePath.toString())
    val repo = Repository(db)

    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var pendingPick by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    KladovkaTheme(themeMode = themeMode) {
        var screenIndex by remember { mutableStateOf(0) }
        var screenArg by remember { mutableStateOf(0L) }

        fun navigate(newIndex: Int, arg: Long = 0L) {
            screenIndex = newIndex
            screenArg = arg
        }

        when (screenIndex) {
            1 -> {
                BackHandler { navigate(0) }
                ItemEditScreen(itemId = screenArg, repo = repo, onDone = { navigate(0) })
            }
            2 -> {
                BackHandler { navigate(0) }
                ShelfEditScreen(shelfId = screenArg, repo = repo, onDone = { navigate(0) })
            }
            3 -> {
                BackHandler { navigate(0) }
                ContainerEditScreen(containerId = screenArg, repo = repo, onDone = { navigate(0) })
            }
            5 -> {
                BackHandler { navigate(0) }
                PolkaEditScreen(polkaId = screenArg, repo = repo, onDone = { navigate(0) })
            }
            4 -> {
                BackHandler { navigate(0) }
                PlaceEditScreen(
                    placeId = screenArg,
                    repo = repo,
                    onDone = { navigate(0) },
                    pendingLat = pendingPick?.first,
                    pendingLon = pendingPick?.second,
                    onPendingConsumed = { pendingPick = null }
                )
            }
            else -> {
                MainScreen(
                    repo = repo,
                    onOpenItem = { navigate(1, it) },
                    onAddItem = { navigate(1, 0L) },
                    onEditShelf = { navigate(2, it) },
                    onAddShelf = { navigate(2, 0L) },
                    onEditPolka = { navigate(5, it) },
                    onAddPolka = { navigate(5, 0L) },
                    onEditContainer = { navigate(3, it) },
                    onAddContainer = { navigate(3, 0L) },
                    onEditPlace = { navigate(4, it) },
                    onAddPlace = { navigate(4, 0L) }
                )
            }
        }
    }
}

@Composable
private fun BackHandler(onBack: () -> Unit) {
    // Упрощённая реализация для Desktop
    // В полной версии используется androidx.compose.ui.input.key.BackHandler
}
