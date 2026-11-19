package com.pocketvm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = DarkBackground,
    primaryContainer = CyanDark,
    onPrimaryContainer = OnDarkBackground,
    secondary = Magenta,
    onSecondary = DarkBackground,
    secondaryContainer = MagentaDark,
    onSecondaryContainer = OnDarkBackground,
    tertiary = Lime,
    onTertiary = DarkBackground,
    tertiaryContainer = LimeDark,
    onTertiaryContainer = OnDarkBackground,
    background = DarkBackground,
    onBackground = OnDarkBackground,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    error = ErrorRed,
    onError = DarkBackground,
    outline = DarkSurfaceHigh,
)

private val LightColorScheme = lightColorScheme(
    primary = CyanDark,
    secondary = MagentaDark,
    tertiary = LimeDark,
)

@Composable
fun PocketVmTheme(
    darkTheme: Boolean = true, // Always dark by default
    dynamicColor: Boolean = false, // Off — keep our brand colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicDarkColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketVmTypography,
        content = content
    )
}
