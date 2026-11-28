package com.metrolist.music.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.R
import com.metrolist.music.utils.AppReleaseInfo
import com.metrolist.music.utils.AppUpdater
import com.songfy.ui.songfyLiquidGlass
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SoftwareUpdateDialog(
    release: AppReleaseInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "updateDownloadProgress",
    )

    Dialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val cardShape = RoundedCornerShape(26.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!isDownloading) onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(220)) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                ),
                exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.95f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .shadow(24.dp, cardShape)
                        .clip(cardShape)
                        .background(Color(0xEB15151A))
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), cardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Software Update",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDownloading) {
                            "Downloading Songfy ${release.versionName}..."
                        } else {
                            "Songfy ${release.versionName} is available to download."
                        },
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )

                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress.coerceIn(0.03f, 1f))
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Scrollable "What's new" body with Songfy phone mockup & changelog
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp),
                    ) {
                        Text(
                            text = "What's new",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        UpdateLyricsMockupCard()

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = release.title,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (release.changelog.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Changelog",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = release.changelog,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        downloadError?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = err,
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    if (isDownloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    downloadJob?.cancel()
                                    downloadJob = null
                                    isDownloading = false
                                    downloadProgress = 0f
                                }
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Cancel",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    downloadError = null
                                    isDownloading = true
                                    downloadProgress = 0f
                                    downloadJob = coroutineScope.launch {
                                        val destFile = File(context.cacheDir, "songfy-update-${release.versionName}.apk")
                                        runCatching {
                                            AppUpdater.downloadApk(
                                                url = release.apkUrl,
                                                destFile = destFile,
                                                expectedSizeBytes = release.apkSizeBytes,
                                                onProgress = { p -> downloadProgress = p },
                                            )
                                        }.onSuccess { file ->
                                            isDownloading = false
                                            val started = AppUpdater.installApk(context, file)
                                            if (!started) {
                                                Toast.makeText(
                                                    context,
                                                    "Izinkan pemasangan dari sumber tidak dikenal, lalu ketuk Download Now lagi.",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            } else {
                                                onDismiss()
                                            }
                                        }.onFailure { err ->
                                            isDownloading = false
                                            if (err !is kotlinx.coroutines.CancellationException) {
                                                downloadError = err.message ?: "Gagal mengunduh update."
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Download Now",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Remind Me Later",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateLyricsMockupCard() {
    var activeLine by remember { mutableIntStateOf(0) }
    val previewLines = remember {
        listOf(
            "Look how they shine for you",
            "And everything you do",
            "Yeah, they were all yellow",
            "I came along",
        )
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1700L)
            activeLine = (activeLine + 1) % previewLines.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7A5210),
                        Color(0xFF3A2606),
                        Color(0xFF100C05),
                    ),
                    center = Offset(300f, 220f),
                    radius = 480f,
                ),
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
    ) {
        // Songfy watermark top-left
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Songfy",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Centered mini phone mockup with animated synced lyrics
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 24.dp)
                .width(126.dp)
                .height(145.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF281D0A), Color(0xFF6B490E), Color(0xFF3E2907)),
                    ),
                )
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Mini phone top pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFD4A036)),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                previewLines.forEachIndexed { idx, line ->
                    val alpha by animateFloatAsState(
                        targetValue = if (idx == activeLine) 1f else 0.42f,
                        animationSpec = tween(350),
                        label = "mockupLineAlpha",
                    )
                    Text(
                        text = line,
                        color = Color.White.copy(alpha = alpha),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = if (idx == activeLine) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
