@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.retained.rememberRetained
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val ColorGrey = Color(0xFF94A3B8) // a4aec4
val ColorGrey2 = Color(0xFF6A7A80)
val ColorYellow = Color(0xFFEAB308) // f3c237
val ColorGreen = Color(0xFF22C55E) // 79b851
val ColorRed = Color(0xcFF94600)
val ColorKeyMiss = Color(0xFF3A3A3A)
val ColorKeyHover = Color(0xFFBBC0C6)

typealias WordGameApp = @Composable () -> Unit

@Inject
@Composable
@Preview
fun WordGameApp(circuit: Circuit) {
    val log = Logger.withTag("WordGameApp")
    MaterialTheme {
        val backStack = rememberSaveableBackStack(MainScreen)
        val navigator = rememberCircuitNavigator(backStack) { }

        val path = rememberRetained { getPath() ?: "" }

        rememberRetained(Unit) {
            log.i { "path: $path" }
            val segments = path.split("/")
            when (segments[0]) {
                "room" -> {
                    if (segments.size != 2) return@rememberRetained
                    try {
                        val sessionId = Uuid.parse(segments[1])
                        navigator.goTo(GameScreen(sessionId))
                    } catch (e: Throwable) {
                        log.w(e) { "failed to parse #room segment: ${segments[1]}" }
                    }
                }
            }
            Unit
        }

        CircuitCompositionLocals(circuit) {
            NavigableCircuitContent(
                navigator = navigator,
                backStack = backStack,
                // TODO android
                /*decoration = GestureNavigationDecoration(
                    onBackInvoked = navigator::pop
                )*/
            )
        }
    }
}

object KeyPressObserver {
    private val _keyPressed = MutableStateFlow<KeyPress?>(null)
    val keyPressed: StateFlow<KeyPress?> get() = _keyPressed

    fun updateKey(key: KeyPress) {
        _keyPressed.value = key
    }

    fun clear() {
        _keyPressed.value = null
    }
}


@Composable
fun SectionBoundary(
    modifier: Modifier = Modifier,
    background: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    Column(
        modifier.fillMaxWidth().background(background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 12.dp)
                .width(1280.dp)
        ) {
            content()
        }
    }
}

@Composable
fun Header(modifier: Modifier = Modifier) {
    // wrap in white box for dark mode
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            buildAnnotatedString {
                append("hotbling")
                withStyle(style = SpanStyle(color = ColorRed)) {
                    append(".")
                }
                // TODO rainbow
                append("wtf")
            },
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

expect fun setPath(path: String)
expect fun getPath(): String?

expect suspend fun setClipboard(text: String): Boolean

@Composable
expect fun calculateWindowSizeClass(): WindowSizeClass
