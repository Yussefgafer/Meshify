package com.p2p.meshify.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class MeshifyMotion(
    val defaultSpatial: androidx.compose.animation.core.SpringSpec<Float> = androidx.compose.animation.core.spring(
        dampingRatio = 0.8f,
        stiffness = 600f
    )
)

val LocalMeshifyMotion = staticCompositionLocalOf { MeshifyMotion() }

/**
 * Shared Dimensions and Shapes for Consistency (Fixes P1 Hardcoded Values).
 */
object MeshifyThemeProperties {
    val ChatBubbleRadius = 24.dp
    val ChatBubbleGroupedRadius = 4.dp
    val CardRadius = 28.dp
    val AvatarRadius = 16.dp
}

object ChatBubbleShapes {
    val Ungrouped = RoundedCornerShape(MeshifyThemeProperties.ChatBubbleRadius)
    
    val MeGroupedTop = RoundedCornerShape(
        topStart = MeshifyThemeProperties.ChatBubbleRadius,
        topEnd = MeshifyThemeProperties.ChatBubbleGroupedRadius,
        bottomEnd = MeshifyThemeProperties.ChatBubbleRadius,
        bottomStart = MeshifyThemeProperties.ChatBubbleRadius
    )
    
    val MeGroupedMiddle = RoundedCornerShape(
        topStart = MeshifyThemeProperties.ChatBubbleRadius,
        topEnd = MeshifyThemeProperties.ChatBubbleGroupedRadius,
        bottomEnd = MeshifyThemeProperties.ChatBubbleGroupedRadius,
        bottomStart = MeshifyThemeProperties.ChatBubbleRadius
    )
    
    val MeGroupedBottom = RoundedCornerShape(
        topStart = MeshifyThemeProperties.ChatBubbleRadius,
        topEnd = MeshifyThemeProperties.ChatBubbleRadius,
        bottomEnd = MeshifyThemeProperties.ChatBubbleRadius,
        bottomStart = MeshifyThemeProperties.ChatBubbleRadius
    )

    val PeerGroupedTop = RoundedCornerShape(
        topStart = MeshifyThemeProperties.ChatBubbleGroupedRadius,
        topEnd = MeshifyThemeProperties.ChatBubbleRadius,
        bottomEnd = MeshifyThemeProperties.ChatBubbleRadius,
        bottomStart = MeshifyThemeProperties.ChatBubbleRadius
    )
    
    val PeerGroupedMiddle = RoundedCornerShape(
        topStart = MeshifyThemeProperties.ChatBubbleGroupedRadius,
        topEnd = MeshifyThemeProperties.ChatBubbleRadius,
        bottomEnd = MeshifyThemeProperties.ChatBubbleRadius,
        bottomStart = MeshifyThemeProperties.ChatBubbleGroupedRadius
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003737),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF004F4F),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF6FF6F6),
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
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFF7F2FA)
)

/**
 * Meshify Theme with Meshify Brand Identity.
 */
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

    CompositionLocalProvider(
        LocalMeshifyMotion provides MeshifyMotion()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
