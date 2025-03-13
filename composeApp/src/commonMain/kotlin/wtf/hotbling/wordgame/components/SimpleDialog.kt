package wtf.hotbling.wordgame.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SimpleDialog(
    title: String,
    onDismiss: () -> Unit = {},
    canDismiss: Boolean = true,
    showDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                //.wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold
                    )
                    if (showDismiss) {
                        IconButton(onClick = { onDismiss() }, enabled = canDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "close"
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}
