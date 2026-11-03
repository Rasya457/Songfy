/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Typography.kt — typography SF Pro untuk semua Text (judul, lirik, bottom nav, mini player, dst)
 */

package com.metrolist.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.metrolist.music.R

/** Bobot asli: Regular = W400, Medium = W500, Semibold = W600, Bold = W700, Heavy = W800 */
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
    Font(R.font.sf_pro_display_heavy, FontWeight.W800),
)

private val DefaultTypography = Typography()

// Dasar = typography Material default supaya lineHeight tetap terisi (dipakai di HomeScreen: lineHeight.toDp()).
private val BaseTypography = DefaultTypography.copy(
    displayLarge = DefaultTypography.displayLarge.copy(fontWeight = FontWeight.W800, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontWeight = FontWeight.W800, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontWeight = FontWeight.W700, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.25).sp),
    titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.W700, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.05.sp),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
    labelMedium = DefaultTypography.labelMedium.copy(fontWeight = FontWeight.W600, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp),
    labelSmall = DefaultTypography.labelSmall.copy(fontWeight = FontWeight.W600, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)

/** Tempel family ke SEMUA style supaya nggak ada teks yang ketinggalan Roboto */
fun Typography.withFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

val SongfyTypography = BaseTypography.withFamily(SFProDisplay)

