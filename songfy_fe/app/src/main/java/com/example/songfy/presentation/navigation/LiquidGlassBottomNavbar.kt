package com.lagu.songfy.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState

// Aliased to keep backwards compatibility with existing references
typealias NavTabItem = LiquidNavBarItem

/**
 * LiquidGlassBottomNavbar forwards to [LiquidGlassNavBar] to provide
 * the complete iOS 26 Liquid Glass floating navigation bar with liquid lens,
 * horizontal drag interaction, squash & stretch physics, AGSL refraction shader,
 * and Haze backdrop blur.
 */
@Composable
fun LiquidGlassBottomNavbar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glassState: GlassState? = null,
    hazeState: HazeState? = null,
    pagerPosition: (() -> Float)? = null,
    isFrozen: (() -> Boolean)? = null,
    onTabReselected: ((Int) -> Unit)? = null
) {
    LiquidGlassNavBar(
        selectedIndex = selectedIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        hazeState = hazeState,
        onTabReselected = onTabReselected,
        pagerPosition = pagerPosition,
        isFrozen = isFrozen,
        includeBottomPadding = false
    )
}