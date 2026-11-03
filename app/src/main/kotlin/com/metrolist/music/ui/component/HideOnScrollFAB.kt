/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
) {
    val liquidNav = LocalLiquidNavState.current
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }

    val isMinimized = liquidNav?.minimized == true
    val hasMiniPlayer = liquidNav?.hasMiniPlayer == true

    // Target bottom calculation:
    // Minimized mode: directly above Search circle (56dp circle + 8dp dock padding + 16dp gap)
    // Normal mode with mini player: directly above Mini Player (56dp + 8dp gap + 64dp bar + 8dp dock padding + 16dp gap)
    // Normal mode without mini player: directly above Tab Bar (64dp + 8dp dock padding + 16dp gap)
    val targetBottom = when {
        liquidNav == null -> with(density) { LocalPlayerAwareWindowInsets.current.getBottom(density).toDp() } + 16.dp
        isMinimized -> bottomInset + 56.dp + 8.dp + 16.dp
        hasMiniPlayer -> bottomInset + 128.dp + 8.dp + 16.dp
        else -> bottomInset + 64.dp + 8.dp + 16.dp
    }

    val animatedBottom by animateDpAsState(
        targetValue = targetBottom,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 260f),
        label = "fabBottomOffset",
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = animatedBottom),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onRecognitionClick != null) {
                SmallFloatingActionButton(
                    onClick = onRecognitionClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.mic),
                        contentDescription = stringResource(R.string.recognize_music),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            FloatingActionButton(
                onClick = onClick,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                )
            }
        }
    }
}
