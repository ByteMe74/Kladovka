package ru.kladovka

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import ru.kladovka.data.*
import ru.kladovka.data.Repository
import ru.kladovka.ui.App

fun main() = runBlocking {
    createWindow()
}

@Composable
fun createWindow() {
    val repo = remember { Repository() }
    
    Window(
        onCloseRequest = ::System.exit(0),
        title = "Kladovka Desktop",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        var settings by remember { mutableStateOf(Settings(id = 0, theme = "dark", language = "ru", syncEnabled = false)) }
        
        App(
            initialSettings = settings
        )
    }
}
