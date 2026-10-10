package com.lagu.songfy.presentation.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    Dark,
    Light
}

data class IOSColorScheme(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val iconTint: Color,
    val iconBackground: Color,
    val accentBlue: Color,
    val accentRed: Color,
    val switchTrackOff: Color,
    val inputBackground: Color
)

val IOSLightPalette = IOSColorScheme(
    isDark = false,
    background = Color(0xFF121212), // Deep neutral black matching Home
    cardBackground = Color(0xFF242426), // Elevated neutral dark gray card
    cardBorder = Color(0x1FFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    divider = Color(0xFF2C2C2E),
    iconTint = Color(0xFFFFFFFF),
    iconBackground = Color(0xFF2C2C2E),
    accentBlue = Color(0xFF0A84FF),
    accentRed = Color(0xFFFF453A),
    switchTrackOff = Color(0xFF39393D),
    inputBackground = Color(0xFF1C1C1E)
)

val IOSDarkPalette = IOSColorScheme(
    isDark = true,
    background = Color(0xFF121212), // Deep neutral dark black matching HomeScreen
    cardBackground = Color(0xFF242426), // Elevated neutral dark gray card
    cardBorder = Color(0x1FFFFFFF), // Subtle dark hairline border
    textPrimary = Color(0xFFFFFFFF), // Crisp white text
    textSecondary = Color(0xFF8E8E93), // iOS secondary gray
    divider = Color(0xFF2C2C2E), // iOS dark separator
    iconTint = Color(0xFFFFFFFF),
    iconBackground = Color(0xFF2C2C2E),
    accentBlue = Color(0xFF0A84FF), // iOS system blue (dark variant)
    accentRed = Color(0xFFFF453A), // iOS destructive red (dark variant)
    switchTrackOff = Color(0xFF39393D),
    inputBackground = Color(0xFF1C1C1E)
)

object AppSettings {
    private const val PREFS_NAME = "songfy_app_settings"
    private const val KEY_THEME = "app_theme_mode"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"

    var currentTheme by mutableStateOf(AppThemeMode.Dark)
        private set

    var notificationsEnabled by mutableStateOf(true)
        private set

    val colors: IOSColorScheme
        get() = if (currentTheme == AppThemeMode.Dark) IOSDarkPalette else IOSLightPalette

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_THEME, AppThemeMode.Dark.name) ?: AppThemeMode.Dark.name
        currentTheme = if (savedTheme == AppThemeMode.Light.name) AppThemeMode.Light else AppThemeMode.Dark
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true)
    }

    fun setTheme(context: Context, theme: AppThemeMode) {
        currentTheme = theme
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.name)
            .apply()
    }

    fun toggleTheme(context: Context) {
        val newTheme = if (currentTheme == AppThemeMode.Dark) AppThemeMode.Light else AppThemeMode.Dark
        setTheme(context, newTheme)
    }

    fun setNotifications(context: Context, enabled: Boolean) {
        notificationsEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATIONS, enabled)
            .apply()
    }
}
