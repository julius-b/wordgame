package wtf.hotbling.wordgame

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    //MainScreen()
}

actual fun setPath(path: String) {}
actual fun getPath(): String? = null

actual suspend fun setClipboard(text: String) = false

// TODO try LocalActivity.current
@Composable
actual fun calculateWindowSizeClass() = calculateWindowSizeClass(LocalContext.current as Activity)
