@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class)

package wtf.hotbling.wordgame

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import co.touchlab.kermit.consoleError
import co.touchlab.kermit.consoleLog
import com.slack.circuit.retained.rememberRetained
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.events.KeyboardEvent
import wtf.hotbling.wordgame.di.WasmJsApplicationComponent
import wtf.hotbling.wordgame.di.create
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    setupKeyListener()
    ComposeViewport(document.body!!) {
        val component = rememberRetained { WasmJsApplicationComponent.create(window) }

        component.wordGameApp()
    }
}

fun setupKeyListener() {
    window.addEventListener("keydown") { event ->
        event as KeyboardEvent
        MainScope().launch {
            val key = when {
                event.key == "Backspace" -> KeyPress.Backspace()
                event.key == "Enter" -> KeyPress.Enter()
                event.key.length != 1 -> {
                    println("[ERR] pressed key unexpected: ${event.key}")
                    null
                }

                else -> {
                    val char = event.keyCode.toChar()
                    if (!char.isLetter()) {
                        println("[WRN] pressed key not a letter: $char")
                        return@launch
                    }
                    KeyPress.Letter(char.lowercaseChar())
                }
            }
            if (key != null) KeyPressObserver.updateKey(key)
        }
    }
}

actual fun setPath(path: String) {
    window.location.hash = path.lowercase()
    // alt: doesn't handle "" correctly
    //val url = if (path == "") "" else "#$path"
    //window.history.pushState(null, path, url)
}

// empty string if there is no
actual fun getPath(): String? = window.location.hash.substringAfter('#').lowercase()

actual suspend fun setClipboard(text: String): Boolean {
    return suspendCoroutine { cont ->
        window.navigator.clipboard.writeText(text)
            .then {
                consoleLog("saved to clipboard: $text")
                cont.resume(true)
                null
            }
            .catch { err ->
                consoleError("failed to save to clipboard: $err")
                cont.resume(false)
                null
            }
        //.toThrowableOrNull()
    }
}

@Composable
actual fun calculateWindowSizeClass() = calculateWindowSizeClass()
