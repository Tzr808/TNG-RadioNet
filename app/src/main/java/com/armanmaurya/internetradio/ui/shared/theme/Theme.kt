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
 * Restituisce la palette Pure Black (solo #000000 e #FFFFFF) quando il tema è scuro.
 * Sostituisce completamente qualsiasi ColorScheme di base (dynamic o preset).
 */
private fun ColorScheme.applyPureBlack(): ColorScheme = pureBlackDarkScheme

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