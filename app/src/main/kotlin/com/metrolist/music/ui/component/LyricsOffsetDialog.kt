package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog pengaturan Lyrics Offset (ms) untuk sinkronisasi lirik dengan tempo lagu.
 */
@Composable
fun LyricsOffsetDialog(
    initialOffsetMs: Int,
    onDismiss: () -> Unit,
    onSaveOffset: (Int) -> Unit,
) {
    var offset by remember { mutableIntStateOf(initialOffsetMs) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1C1C1E))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Lyrics Sync Offset",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sesuaikan keterlambatan atau percepatan lirik:",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "${if (offset > 0) "+" else ""}$offset ms",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilledTonalButton(
                        onClick = { offset -= 500 },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text("-500ms", color = Color.White, fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = { offset -= 100 },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text("-100ms", color = Color.White, fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = { offset = 0 },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text("Reset", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilledTonalButton(
                        onClick = { offset += 100 },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text("+100ms", color = Color.White, fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = { offset += 500 },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text("+500ms", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color.White.copy(alpha = 0.70f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        onSaveOffset(offset)
                        onDismiss()
                    }) {
                        Text("Simpan", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

