/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R

/**
 * Clean Dark Minimalist Apple Music-style settings group component.
 * Renders an uppercase section header and wraps items in a single grouped card with thin dividers.
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>,
    useLowContrast: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        title?.let {
            SettingsSectionLabel(text = it)
        }

        SettingsGroupCard {
            items.forEachIndexed { index, item ->
                Material3SettingsItemRow(item = item)
                if (index < items.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (item.icon != null || item.leadingContent != null) 52.dp else 16.dp)
                            .height(0.5.dp)
                            .background(SettingsDividerColor),
                    )
                }
            }
        }
    }
}

/**
 * Individual settings item row with Apple Music dark styling
 */
@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.enabled && item.onClick != null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { item.onClick?.invoke() },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(modifier = Modifier.width(14.dp))
        } else if (item.icon != null) {
            Icon(
                painter = item.icon,
                contentDescription = null,
                tint = if (!item.enabled) SettingsTextSecondary.copy(alpha = 0.5f) else SettingsTextPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
        ) {
            ProvideTextStyle(
                TextStyle(
                    color = if (!item.enabled) SettingsTextSecondary.copy(alpha = 0.5f) else SettingsTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) {
                item.title()
            }

            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(3.dp))
                ProvideTextStyle(
                    TextStyle(
                        color = SettingsTextSecondary,
                        fontSize = 13.sp,
                    ),
                ) {
                    desc()
                }
            }
        }

        if (item.trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            item.trailingContent.invoke()
        } else if (item.onClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = SettingsChevronColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Data class for settings item
 */
data class Material3SettingsItem(
    val icon: Painter? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)
