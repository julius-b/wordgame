package wtf.hotbling.wordgame

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WordGame",
    ) {
        App()
    }
}