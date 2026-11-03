package com.metrolist.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import kotlin.math.roundToInt

/**
 * PlayerSleepTimerDialog — Dialog cepat untuk menyetel sleep timer pemutaran musik.
 */
@Composable
fun PlayerSleepTimerDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    var minutes by remember { mutableFloatStateOf(30f) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.bedtime),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${minutes.roundToInt()} menit",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    valueRange = 5f..120f,
                    steps = (120 - 5) / 5 - 1,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    playerConnection.service.sleepTimer?.start(
                        minute = minutes.roundToInt(),
                    )
                    Toast.makeText(context, "Sleep timer disetel ${minutes.roundToInt()} menit", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

