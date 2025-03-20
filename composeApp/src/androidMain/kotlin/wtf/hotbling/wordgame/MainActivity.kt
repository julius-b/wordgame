package wtf.hotbling.wordgame

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import wtf.hotbling.wordgame.api.ApiCharStatus
import wtf.hotbling.wordgame.di.AndroidApplicationComponent

class MainActivity : ComponentActivity() {

    private val applicationComponent: AndroidApplicationComponent by lazy {
        (applicationContext as ClipboardApplication).component
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            applicationComponent.wordGameApp()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    //MainScreen()
}

@Preview
@org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
fun KeyboardPreview() {
    KeyboardView(
        listOf(
            mapOf('A' to ApiCharStatus.Correct, 'T' to ApiCharStatus.Correct),
            mapOf('A' to ApiCharStatus.Correct, 'T' to ApiCharStatus.Kinda),
            mapOf('A' to ApiCharStatus.Correct, 'T' to ApiCharStatus.Wrong),
            mapOf('A' to ApiCharStatus.Correct)
        ), onKey = {})
}

actual fun setPath(path: String) {}
actual fun getPath(): String? = null

actual suspend fun setClipboard(text: String) = false

// TODO try LocalActivity.current
@Composable
actual fun calculateWindowSizeClass() = calculateWindowSizeClass(LocalContext.current as Activity)

actual suspend fun notify(title: String, text: String) {}
actual fun hasNotifyPermission() = false
actual suspend fun reqNotifyPermission() = false
actual fun isNotifySupported() = false
