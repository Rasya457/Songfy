/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * SettingsComponents.kt — Reusable Apple Music dark style components for Settings & Sub-settings.
 * Pure black background (#000000), grouped dark surface cards (#1C1C1E),
 * uppercase section headers (#8E8E93), 22dp monochrome icons, iOS-style switches,
 * and 140dp bottom padding for floating mini player + bottom navigation bar.
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R

// ─── Theme Colors ────────────────────────────────────────────────────────────
val SettingsBg = Color(0xFF000000)
val SettingsCardDark = Color(0xFF1C1C1E)
val SettingsDividerColor = Color(0xFF262629)
val SettingsTextPrimary = Color(0xFFFFFFFF)
val SettingsTextSecondary = Color(0xFF8E8E93)
val SettingsChevronColor = Color(0xFF636366)
val SettingsSwitchActive = Color(0xFF34C759)
val SettingsSwitchInactive = Color(0xFF39393D)
val SettingsBadgeBg = Color(0xFF2C2C2E)

// ─── iOS-style Switch Colors ─────────────────────────────────────────────────
@Composable
fun settingsSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = SettingsSwitchActive,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = SettingsSwitchInactive,
    checkedBorderColor = Color.Transparent,
    uncheckedBorderColor = Color.Transparent,
    disabledCheckedThumbColor = Color.White.copy(alpha = 0.5f),
    disabledUncheckedThumbColor = Color.White.copy(alpha = 0.5f),
    disabledCheckedTrackColor = SettingsSwitchActive.copy(alpha = 0.4f),
    disabledUncheckedTrackColor = SettingsSwitchInactive.copy(alpha = 0.4f),
)

// ─── Reusable Scaffold for Sub-Settings ──────────────────────────────────────
@Composable
fun SettingsSubScreenScaffold(
    title: String,
    navController: NavController,
    onBack: (() -> Unit)? = null,
    trailingHeaderContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBg),
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

            // Circular back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SettingsCardDark)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onBack?.invoke() ?: navController.navigateUp() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.back),
                        tint = SettingsTextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                if (trailingHeaderContent != null) {
                    Spacer(Modifier.weight(1f))
                    trailingHeaderContent()
                }
            }

            Spacer(Modifier.height(16.dp))

            // Large Bold Title
            Text(
                text = title,
                color = SettingsTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp,
            )

            Spacer(Modifier.height(20.dp))

            content()

            // Generous bottom inset for floating mini player + bottom nav
            Spacer(Modifier.height(140.dp))
        }
    }
}

// ─── Section Header (Uppercase Small Gray) ───────────────────────────────────
@Composable
fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = SettingsTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 20.dp),
    )
}

// ─── Grouped Card Container ──────────────────────────────────────────────────
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SettingsCardDark),
    ) {
        content()
    }
}

// ─── Settings Row Item (Navigation / Action) ─────────────────────────────────
@Composable
fun SettingsRowItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    badgeText: String? = null,
    trailingText: String? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = if (enabled) SettingsTextPrimary else SettingsTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = if (enabled) SettingsTextPrimary else SettingsTextSecondary.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (badgeText != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SettingsBadgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = badgeText.uppercase(),
                                color = SettingsTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = SettingsTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailingText != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = trailingText,
                    color = SettingsTextSecondary,
                    fontSize = 14.sp,
                )
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                trailingContent()
            } else if (onClick != null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = SettingsChevronColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (icon != null) 52.dp else 16.dp)
                    .height(0.5.dp)
                    .background(SettingsDividerColor),
            )
        }
    }
}

// ─── Settings Toggle Item (iOS Style Switch) ─────────────────────────────────
@Composable
fun SettingsToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    badgeText: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = { onCheckedChange(!checked) },
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = if (enabled) SettingsTextPrimary else SettingsTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = if (enabled) SettingsTextPrimary else SettingsTextSecondary.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (badgeText != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SettingsBadgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = badgeText.uppercase(),
                                color = SettingsTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = SettingsTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = settingsSwitchColors(),
                thumbContent = null,
            )
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (icon != null) 52.dp else 16.dp)
                    .height(0.5.dp)
                    .background(SettingsDividerColor),
            )
        }
    }
}
