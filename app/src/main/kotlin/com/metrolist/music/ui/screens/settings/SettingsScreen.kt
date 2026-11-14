/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * SettingsScreen.kt — Redesigned Apple Music dark style settings page.
 * - Pure black background
 * - Pill search bar
 * - Grouped dark cards with large rounded corners
 * - Section headers in uppercase gray
 * - Circular back button
 * - Reuses all existing navigation routes
 */

package com.metrolist.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R

// ─── Colors ──────────────────────────────────────────────────────────────────
private val BgBlack = Color(0xFF000000)
private val CardDark = Color(0xFF1C1C1E)
private val DividerColor = Color(0xFF38383A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8E8E93)
private val ChevronColor = Color(0xFF636366)
private val SearchBarBg = Color(0xFF1C1C1E)
private val BackButtonBg = Color(0xFF1C1C1E)
private val SectionLabelColor = Color(0xFF8E8E93)

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo("com.google.android.projection.gearhead", 0)
            true
        } catch (_: Exception) { false }
    }

    var searchQuery by remember { mutableStateOf("") }

    // All items flattened for search filtering
    data class SettingsEntry(val title: String, val route: String? = null, val onClick: (() -> Unit)? = null)

    val allItems = remember {
        buildList {
            add(SettingsEntry(context.getString(R.string.integrations), "settings/integrations"))
            add(SettingsEntry(context.getString(R.string.listen_together), "settings/integrations/listen_together"))
            add(SettingsEntry(context.getString(R.string.appearance), "settings/appearance"))
            add(SettingsEntry(context.getString(R.string.player_and_audio), "settings/player"))
            add(SettingsEntry(context.getString(R.string.content), "settings/content"))
            add(SettingsEntry(context.getString(R.string.storage), "settings/storage"))
            add(SettingsEntry(context.getString(R.string.backup_restore), "settings/backup_restore"))
            add(SettingsEntry(context.getString(R.string.privacy), "settings/privacy"))
            add(SettingsEntry(context.getString(R.string.about), "settings/about"))
        }
    }

    val filteredItems = if (searchQuery.isBlank()) emptyList()
    else allItems.filter { it.title.contains(searchQuery, ignoreCase = true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Circular back button ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BackButtonBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { navController.navigateUp() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back),
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.settings),
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp,
            )

            Spacer(Modifier.height(16.dp))

            // ── Search Bar ────────────────────────────────────────────────────
            SettingsSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )

            Spacer(Modifier.height(24.dp))

            // ── Search Results ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = filteredItems.isNotEmpty(),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSectionLabel("Results")
                    Spacer(Modifier.height(8.dp))
                    SettingsGroupCard {
                        filteredItems.forEachIndexed { i, item ->
                            SettingsRow(
                                title = item.title,
                                showDivider = i < filteredItems.lastIndex,
                                onClick = {
                                    if (item.route != null) navController.navigate(item.route)
                                    else item.onClick?.invoke()
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            AnimatedVisibility(
                visible = searchQuery.isBlank(),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                    SettingsGroupCard {
                        SettingsRow(
                            icon = painterResource(R.drawable.integration),
                            title = stringResource(R.string.integrations),
                            showDivider = true,
                            onClick = { navController.navigate("settings/integrations") },
                        )
                        SettingsRow(
                            icon = painterResource(R.drawable.group),
                            title = stringResource(R.string.listen_together),
                            subtitle = stringResource(R.string.listen_together_description),
                            showDivider = false,
                            onClick = { navController.navigate("settings/integrations/listen_together") },
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Section: Audio Quality ────────────────────────────────
                    SettingsSectionLabel(stringResource(R.string.settings_section_player_content).uppercase())
                    Spacer(Modifier.height(8.dp))

                    SettingsGroupCard {
                        SettingsRow(
                            icon = painterResource(R.drawable.play),
                            title = stringResource(R.string.player_and_audio),
                            showDivider = true,
                            onClick = { navController.navigate("settings/player") },
                        )
                        SettingsRow(
                            icon = painterResource(R.drawable.language),
                            title = stringResource(R.string.content),
                            showDivider = true,
                            onClick = { navController.navigate("settings/content") },
                        )
                        SettingsRow(
                            icon = painterResource(R.drawable.translate),
                            title = stringResource(R.string.ai_lyrics_translation),
                            showDivider = false,
                            onClick = { navController.navigate("settings/ai") },
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Section: Interface ─────────────────────────────────────
                    SettingsSectionLabel(stringResource(R.string.settings_section_ui).uppercase())
                    Spacer(Modifier.height(8.dp))

                    SettingsGroupCard {
                        SettingsRow(
                            icon = painterResource(R.drawable.palette),
                            title = stringResource(R.string.appearance),
                            showDivider = false,
                            onClick = { navController.navigate("settings/appearance") },
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Section: Storage & Data ───────────────────────────────
                    SettingsSectionLabel(stringResource(R.string.settings_section_storage).uppercase())
                    Spacer(Modifier.height(8.dp))

                    SettingsGroupCard {
                        SettingsRow(
                            icon = painterResource(R.drawable.storage),
                            title = stringResource(R.string.storage),
                            showDivider = true,
                            onClick = { navController.navigate("settings/storage") },
                        )
                        SettingsRow(
                            icon = painterResource(R.drawable.restore),
                            title = stringResource(R.string.backup_restore),
                            showDivider = false,
                            onClick = { navController.navigate("settings/backup_restore") },
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Section: Privacy & System ─────────────────────────────
                    SettingsSectionLabel(stringResource(R.string.settings_section_privacy).uppercase())
                    Spacer(Modifier.height(8.dp))

                    SettingsGroupCard {
                        SettingsRow(
                            icon = painterResource(R.drawable.security),
                            title = stringResource(R.string.privacy),
                            showDivider = hasAndroidAuto || isAndroid12OrLater,
                            onClick = { navController.navigate("settings/privacy") },
                        )
                        if (isAndroid12OrLater) {
                            SettingsRow(
                                icon = painterResource(R.drawable.link),
                                title = stringResource(R.string.default_links),
                                showDivider = hasAndroidAuto,
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                                "package:${context.packageName}".toUri(),
                                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        )
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, R.string.open_app_settings_error, Toast.LENGTH_LONG).show()
                                    }
                                },
                            )
                        }
                        if (hasAndroidAuto) {
                            SettingsRow(
                                icon = painterResource(R.drawable.ic_android_auto),
                                title = stringResource(R.string.android_auto),
                                showDivider = false,
                                onClick = { navController.navigate("settings/android_auto") },
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Section: About ────────────────────────────────────────
                    SettingsSectionLabel(stringResource(R.string.settings_section_system).uppercase())
                    Spacer(Modifier.height(8.dp))

                    SettingsGroupCard {
                        SettingsRow(
                            icon = painterResource(R.drawable.info),
                            title = stringResource(R.string.about),
                            showDivider = false,
                            onClick = { navController.navigate("settings/about") },
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SearchBarBg),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_settings),
                    color = TextSecondary,
                    fontSize = 15.sp,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TextPrimary),
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = SectionLabelColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    )
}

@Composable
private fun SettingsGroupCard(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark),
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.painter.Painter? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = ChevronColor,
                modifier = Modifier.size(20.dp),
            )
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (icon != null) 52.dp else 16.dp)
                    .height(0.5.dp)
                    .background(DividerColor),
            )
        }
    }
}
