package com.metrolist.music.ui.component

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.utils.ComposeToImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private data class LyricsCardPalette(
    val glowColor: Color,
    val midColor: Color,
    val deepColor: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLyricsSheet(
    selectedLines: List<String>,
    songTitle: String,
    artistName: String,
    coverUrl: String?,
    accentColor: Color,
    secondaryColor: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isBusy by remember { mutableStateOf(false) }
    var isStoryRatio by remember { mutableStateOf(false) }

    var coverBitmap by remember(coverUrl) { mutableStateOf<Bitmap?>(null) }
    var cardPalette by remember(coverUrl) {
        mutableStateOf(
            LyricsCardPalette(
                glowColor = Color(ColorUtils.blendARGB(accentColor.toArgb(), android.graphics.Color.BLACK, 0.35f)),
                midColor = Color(ColorUtils.blendARGB(secondaryColor.toArgb(), android.graphics.Color.BLACK, 0.58f)),
                deepColor = Color(0xFF140D09),
            ),
        )
    }

    LaunchedEffect(coverUrl) {
        if (coverUrl.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .size(512)
                    .build()
                val bmp = loader.execute(req).image?.toBitmap() ?: return@runCatching
                coverBitmap = bmp
                val palette = Palette.from(bmp).generate()
                val baseRgb = palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: accentColor.toArgb()
                val darkRgb = palette.darkVibrantSwatch?.rgb
                    ?: palette.darkMutedSwatch?.rgb
                    ?: ColorUtils.blendARGB(baseRgb, android.graphics.Color.BLACK, 0.55f)
                val glow = Color(ColorUtils.blendARGB(baseRgb, android.graphics.Color.BLACK, 0.32f))
                val mid = Color(ColorUtils.blendARGB(darkRgb, android.graphics.Color.BLACK, 0.45f))
                val deep = Color(ColorUtils.blendARGB(darkRgb, android.graphics.Color.BLACK, 0.78f))
                withContext(Dispatchers.Main) {
                    cardPalette = LyricsCardPalette(glow, mid, deep)
                }
            }
        }
    }

    suspend fun shareBitmap(bitmap: Bitmap, asStory: Boolean) {
        val file = withContext(Dispatchers.IO) {
            val outFile = File(context.cacheDir, if (asStory) "songfy_lyrics_story.png" else "songfy_lyrics_share.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            outFile
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share lyrics"))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Text(
                text = "Share lyrics",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The lines you picked, laid out on a card with the cover.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Preview Card matching album cover colors (1:1 Square or 9:16 Phone Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isStoryRatio) {
                                Modifier
                                    .fillMaxWidth(0.56f)
                                    .aspectRatio(9f / 16f)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp)
                                    .aspectRatio(1f)
                            },
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(cardPalette.midColor, cardPalette.deepColor),
                            ),
                        )
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    cardPalette.glowColor.copy(alpha = 0.85f),
                                    Color.Transparent,
                                ),
                                center = Offset(120f, 360f),
                                radius = 750f,
                            ),
                        )
                        .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .padding(if (isStoryRatio) 14.dp else 20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Songfy watermark top-right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(if (isStoryRatio) 13.dp else 16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Songfy",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = if (isStoryRatio) 10.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Selected lyrics
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val baseSize = if (isStoryRatio) {
                                if (selectedLines.size > 4) 11.sp else 13.sp
                            } else {
                                if (selectedLines.size > 4) 15.sp else 18.sp
                            }
                            val lineH = if (isStoryRatio) {
                                if (selectedLines.size > 4) 14.sp else 17.sp
                            } else {
                                if (selectedLines.size > 4) 20.sp else 23.sp
                            }
                            selectedLines.forEachIndexed { idx, line ->
                                Text(
                                    text = line,
                                    color = Color.White,
                                    fontSize = baseSize,
                                    lineHeight = lineH,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                if (idx < selectedLines.lastIndex) {
                                    Spacer(
                                        modifier = Modifier.height(
                                            if (isStoryRatio) 5.dp else if (selectedLines.size > 4) 6.dp else 10.dp,
                                        ),
                                    )
                                }
                            }
                        }

                        // Cover + Title + Artist
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (coverUrl != null) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = songTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(if (isStoryRatio) 28.dp else 40.dp)
                                        .clip(RoundedCornerShape(if (isStoryRatio) 6.dp else 8.dp)),
                                )
                                Spacer(modifier = Modifier.width(if (isStoryRatio) 8.dp else 10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = songTitle,
                                    color = Color.White,
                                    fontSize = if (isStoryRatio) 10.sp else 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = artistName,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = if (isStoryRatio) 9.sp else 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Make it a story" -> toggle 9:16 phone ratio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isStoryRatio) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                    )
                    .then(
                        if (isStoryRatio) {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                        } else Modifier,
                    )
                    .clickable(enabled = !isBusy) {
                        isStoryRatio = !isStoryRatio
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 17.dp)
                        .border(1.5.dp, Color.White, RoundedCornerShape(3.dp))
                        .padding(bottom = 2.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 1.5.dp)
                            .background(Color.White, RoundedCornerShape(1.dp)),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStoryRatio) "Make it a story (9:16)" else "Make it a story",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Save & Share row -> exports 1:1 (1080x1080) or 9:16 (1080x1920)
            val exportWidth = 1080
            val exportHeight = if (isStoryRatio) 1920 else 1080
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = !isBusy) {
                            isBusy = true
                            coroutineScope.launch {
                                runCatching {
                                    val bmp = withContext(Dispatchers.Default) {
                                        renderLyricsCardBitmap(
                                            context = context,
                                            width = exportWidth,
                                            height = exportHeight,
                                            lines = selectedLines,
                                            songTitle = songTitle,
                                            artistName = artistName,
                                            coverBitmap = coverBitmap,
                                            palette = cardPalette,
                                        )
                                    }
                                    withContext(Dispatchers.IO) {
                                        ComposeToImage.saveBitmapAsFile(
                                            context,
                                            bmp,
                                            "Songfy_${System.currentTimeMillis()}",
                                        )
                                    }
                                }.onSuccess {
                                    Toast.makeText(context, "Tersimpan ke Galeri", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Gagal menyimpan gambar", Toast.LENGTH_SHORT).show()
                                }
                                isBusy = false
                            }
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable(enabled = !isBusy) {
                            isBusy = true
                            coroutineScope.launch {
                                runCatching {
                                    val bmp = withContext(Dispatchers.Default) {
                                        renderLyricsCardBitmap(
                                            context = context,
                                            width = exportWidth,
                                            height = exportHeight,
                                            lines = selectedLines,
                                            songTitle = songTitle,
                                            artistName = artistName,
                                            coverBitmap = coverBitmap,
                                            palette = cardPalette,
                                        )
                                    }
                                    shareBitmap(bmp, asStory = isStoryRatio)
                                }.onFailure {
                                    Toast.makeText(context, "Gagal membagikan lirik", Toast.LENGTH_SHORT).show()
                                }
                                isBusy = false
                            }
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun renderLyricsCardBitmap(
    context: Context,
    width: Int,
    height: Int,
    lines: List<String>,
    songTitle: String,
    artistName: String,
    coverBitmap: Bitmap?,
    palette: LyricsCardPalette,
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val isStory = height > width

    // 1. Full-bleed vertical gradient + radial album-color glow (no rounded corners)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    bgPaint.shader = LinearGradient(
        0f,
        0f,
        0f,
        height.toFloat(),
        intArrayOf(palette.midColor.toArgb(), palette.deepColor.toArgb()),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    glowPaint.shader = RadialGradient(
        width * 0.16f,
        height * 0.42f,
        width * 0.95f,
        intArrayOf(
            palette.glowColor.copy(alpha = 0.90f).toArgb(),
            android.graphics.Color.TRANSPARENT,
        ),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

    val padX = width * 0.072f
    val padTop = if (isStory) height * 0.048f else height * 0.075f
    val padBottom = if (isStory) height * 0.060f else height * 0.075f

    // 2. Top-right Songfy watermark
    val brandTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(235, 255, 255, 255)
        textSize = width * 0.040f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val brandText = "Songfy"
    val brandWidth = brandTextPaint.measureText(brandText)
    val logoSize = (width * 0.048f).toInt()
    val logoRight = width - padX - brandWidth - (width * 0.014f)
    val logoTop = padTop
    ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.let { drawable ->
        drawable.setBounds(logoRight.toInt(), logoTop.toInt(), (logoRight + logoSize).toInt(), (logoTop + logoSize).toInt())
        drawable.colorFilter = PorterDuffColorFilter(android.graphics.Color.argb(235, 255, 255, 255), PorterDuff.Mode.SRC_IN)
        drawable.draw(canvas)
    }
    val textBaselineY = logoTop + logoSize * 0.78f
    canvas.drawText(brandText, width - padX - brandWidth, textBaselineY, brandTextPaint)

    // 3. Bottom-left cover + title + artist
    val coverSize = (width * 0.135f).toInt()
    val coverTop = height - padBottom - coverSize
    if (coverBitmap != null) {
        val rect = RectF(padX, coverTop, padX + coverSize, coverTop + coverSize)
        val path = Path().apply { addRoundRect(rect, width * 0.022f, width * 0.022f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(coverBitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    }
    val metaX = if (coverBitmap != null) padX + coverSize + width * 0.032f else padX
    val metaMaxW = (width - metaX - padX).toInt().coerceAtLeast(100)
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = width * 0.038f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(185, 255, 255, 255)
        textSize = width * 0.031f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val ellipsizedTitle = TextUtils.ellipsize(songTitle, titlePaint, metaMaxW.toFloat(), TextUtils.TruncateAt.END).toString()
    val ellipsizedArtist = TextUtils.ellipsize(artistName, artistPaint, metaMaxW.toFloat(), TextUtils.TruncateAt.END).toString()
    canvas.drawText(ellipsizedTitle, metaX, coverTop + coverSize * 0.44f, titlePaint)
    canvas.drawText(ellipsizedArtist, metaX, coverTop + coverSize * 0.80f, artistPaint)

    // 4. Lyrics centered in the available vertical band
    val lyricsTopLimit = logoTop + logoSize + height * 0.04f
    val lyricsBottomLimit = coverTop - height * 0.04f
    val availableH = (lyricsBottomLimit - lyricsTopLimit).coerceAtLeast(100f)
    val maxTextW = (width - 2f * padX).toInt().coerceAtLeast(100)

    var fontSize = when {
        lines.size <= 2 -> width * 0.068f
        lines.size <= 4 -> width * 0.058f
        else -> width * 0.048f
    }
    val minFontSize = width * 0.032f
    var layouts: List<StaticLayout>
    var gap: Float
    var totalH: Float
    while (true) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        gap = fontSize * 0.55f
        layouts = lines.map { line ->
            StaticLayout.Builder.obtain(line, 0, line.length, tp, maxTextW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.08f)
                .build()
        }
        totalH = layouts.sumOf { it.height } + (layouts.size - 1).coerceAtLeast(0) * gap
        if (totalH <= availableH || fontSize <= minFontSize) break
        fontSize -= 3f
    }

    var cursorY = lyricsTopLimit + ((availableH - totalH).coerceAtLeast(0f) / 2f)
    for (layout in layouts) {
        canvas.save()
        canvas.translate(padX, cursorY)
        layout.draw(canvas)
        canvas.restore()
        cursorY += layout.height + gap
    }

    return bmp
}
