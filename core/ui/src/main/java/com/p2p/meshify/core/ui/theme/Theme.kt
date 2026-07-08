package com.p2p.meshify.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Color(0xFF6FF6F6),
    secondary = SecondaryDark,
    tertiary = TertiaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceContainerHigh = SurfaceContainerHighDark
)

private val LightColorScheme = lightColorScheme(
    primary = MeshifyPrimary,
    onPrimary = MeshifyOnPrimary,
    primaryContainer = MeshifyPrimaryContainer,
    onPrimaryContainer = MeshifyOnPrimaryContainer,
    secondary = MeshifySecondary,
    tertiary = MeshifyTertiary,
    error = MeshifyError,
    onError = MeshifyOnError,
    surfaceContainerHigh = Color(0xFFF7F2FA)
)

@Composable
fun MeshifyTheme(
    themeMode: String = "SYSTEM",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
