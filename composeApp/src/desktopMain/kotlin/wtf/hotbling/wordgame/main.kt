package wtf.hotbling.wordgame

import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import wtf.hotbling.wordgame.di.DesktopApplicationComponent
import wtf.hotbling.wordgame.di.create

fun main() = application {
    val component = remember { DesktopApplicationComponent.create() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "WordGame",
    ) {
        component.wordGameApp()
    }
}

actual fun setPath(path: String) {}
actual fun getPath(): String? = null

actual suspend fun setClipboard(text: String) = false

@Composable
actual fun calculateWindowSizeClass() = calculateWindowSizeClass()
