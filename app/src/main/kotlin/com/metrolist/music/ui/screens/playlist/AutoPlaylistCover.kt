/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.menu.CustomThumbnailMenu
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Custom cover support for auto-generated playlists (Top, Liked, Downloaded, Cache) that have no
 * database row. The chosen image is cropped into `filesDir/playlist_covers` and its URI is kept in
 * DataStore under a per-playlist key.
 */
@Stable
class AutoPlaylistCoverState(
    val customCoverUrl: String?,
    val onEditClick: () -> Unit,
)

@Composable
fun rememberAutoPlaylistCover(playlistKey: String): AutoPlaylistCoverState {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()

    val safeKey = remember(playlistKey) { playlistKey.replace(Regex("[^A-Za-z0-9_]"), "_") }
    val prefKey = remember(safeKey) { stringPreferencesKey("autoPlaylistCover_$safeKey") }
    val (stored, setStored) = rememberPreference(prefKey, defaultValue = "")
    val currentSetStored by rememberUpdatedState(setStored)
    var pendingDestUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val coverPrefix = "cover_auto_${safeKey}_"
    val cropTitle = stringResource(R.string.edit_playlist_cover)
    val colors = MaterialTheme.colorScheme
    val (darkMode, _) = rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val darkTheme = darkMode == DarkMode.ON || (darkMode == DarkMode.AUTO && isSystemInDarkTheme())

    val cropLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val output =
                if (res.resultCode == android.app.Activity.RESULT_OK) {
                    res.data?.let { UCrop.getOutput(it) } ?: pendingDestUri
                } else {
                    null
                }
            pendingDestUri = null
            if (output != null) {
                val newName = output.lastPathSegment
                currentSetStored(output.toString())
                scope.launch(Dispatchers.IO) {
                    File(context.filesDir, "playlist_covers")
                        .listFiles { f -> f.name.startsWith(coverPrefix) && f.name != newName }
                        ?.forEach { runCatching { it.delete() } }
                }
            }
        }

    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val coverDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
            val destFile = File(coverDir, "$coverPrefix${System.currentTimeMillis()}.jpg")
            val destUri = android.net.Uri.fromFile(destFile)
            pendingDestUri = destUri

            val options =
                UCrop.Options().apply {
                    setCompressionFormat(Bitmap.CompressFormat.JPEG)
                    setCompressionQuality(90)
                    setHideBottomControls(true)
                    setToolbarTitle(cropTitle)
                    setStatusBarLight(!darkTheme)
                    setToolbarColor(colors.surface.toArgb())
                    setToolbarWidgetColor(colors.inverseSurface.toArgb())
                    setRootViewBackgroundColor(colors.surface.toArgb())
                    setLogoColor(colors.surface.toArgb())
                }
            val intent =
                UCrop
                    .of(uri, destUri)
                    .withAspectRatio(1f, 1f)
                    .withOptions(options)
                    .getIntent(context)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            cropLauncher.launch(intent)
        }

    val customUrl = stored.takeIf { it.isNotBlank() }

    val onEditClick: () -> Unit = {
        val pick = {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (customUrl != null) {
            menuState.show {
                CustomThumbnailMenu(
                    onEdit = pick,
                    onRemove = {
                        currentSetStored("")
                        scope.launch(Dispatchers.IO) {
                            File(context.filesDir, "playlist_covers")
                                .listFiles { f -> f.name.startsWith(coverPrefix) }
                                ?.forEach { runCatching { it.delete() } }
                        }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        } else {
            pick()
        }
    }

    return remember(customUrl, onEditClick) { AutoPlaylistCoverState(customUrl, onEditClick) }
}
