import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ru.kladovka.KladovkaDesktopApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        KladovkaDesktopApp()
    }
}
