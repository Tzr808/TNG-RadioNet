package com.armanmaurya.internetradio.ui.shared.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.armanmaurya.internetradio.domain.model.AppPreferences

val LocalAppPreferences = androidx.compose.runtime.compositionLocalOf { AppPreferences() }

/**
 * Applica uno sfondo nero puro e una palette monocromatica al tema corrente.
 * Funziona come overlay su qualsiasi ColorScheme (dynamic o preset).
 */
private fun ColorScheme.applyPureBlack(): ColorScheme = copy(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color.White,
    inversePrimary = Color.Black,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFE7E9EA),
    surface = Color.Black,
    onSurface = Color(0xFFE7E9EA),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFF8B8F93),
    surfaceTint = Color.Black,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF2F3336),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424)
)

@Composable
fun InternetRadioTheme(
    appPreferences: AppPreferences = AppPreferences(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (appPreferences.themeMode) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val dynamicColor = appPreferences.useDynamicColor

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && appPreferences.isPureBlackEnabled) baseScheme.applyPureBlack() else baseScheme
        }
        else -> {
            val baseScheme = appPreferences.appColor.getColorScheme(
                darkTheme = darkTheme,
                customColorArgb = appPreferences.customColorArgb
            )
            if (darkTheme && appPreferences.isPureBlackEnabled) baseScheme.applyPureBlack() else baseScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}