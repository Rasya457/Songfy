package com.metrolist.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.utils.SpotifyImporter
import kotlinx.coroutines.launch

@Composable
fun SpotifyImportDialog(
    onDismiss: () -> Unit,
    onImported: (playlistId: String) -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var linkInput by rememberSaveable { mutableStateOf("") }
    var isImporting by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var completedCount by rememberSaveable { mutableIntStateOf(0) }
    var totalCount by rememberSaveable { mutableIntStateOf(0) }
    var currentTrack by rememberSaveable { mutableStateOf("") }

    LiquidGlassAlertDialog(
        onDismissRequest = {
            if (!isImporting) onDismiss()
        },
        icon = {
            Icon(
                painter = painterResource(R.drawable.playlist_add),
                contentDescription = null,
            )
        },
        title = {
            Text(text = "Impor dari Spotify")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tempel link playlist Spotify publik untuk mengimpor lagunya ke Pustaka.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = {
                        linkInput = it
                        errorMessage = null
                    },
                    label = { Text("Link playlist Spotify") },
                    placeholder = { Text("https://open.spotify.com/playlist/...") },
                    singleLine = true,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isImporting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (totalCount > 0) {
                        LinearProgressIndicator(
                            progress = { (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Mengimpor $completedCount / $totalCount: $currentTrack",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Mengambil daftar lagu dari Spotify...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isImporting && linkInput.isNotBlank(),
                onClick = {
                    isImporting = true
                    errorMessage = null
                    completedCount = 0
                    totalCount = 0
                    currentTrack = ""
                    coroutineScope.launch {
                        runCatching {
                            SpotifyImporter.importPlaylist(
                                urlInput = linkInput,
                                database = database,
                            ) { done, total, trackTitle ->
                                completedCount = done
                                totalCount = total
                                currentTrack = trackTitle
                            }
                        }.onSuccess { summary ->
                            isImporting = false
                            Toast.makeText(
                                context,
                                "Berhasil mengimpor ${summary.importedCount}/${summary.totalTracks} lagu ke \"${summary.playlistTitle}\"",
                                Toast.LENGTH_LONG,
                            ).show()
                            onImported(summary.playlistId)
                        }.onFailure { error ->
                            isImporting = false
                            errorMessage = error.message ?: "Gagal mengimpor playlist Spotify."
                        }
                    }
                },
            ) {
                Text(if (isImporting) "Mengimpor..." else "Impor")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isImporting,
                onClick = onDismiss,
            ) {
                Text("Batal")
            }
        },
    )
}
