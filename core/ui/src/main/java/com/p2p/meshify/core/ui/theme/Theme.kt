package com.p2p.meshify.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * MD3E Motion Configuration.
 * Central source for spring physics and motion presets.
 */
@Immutable
data class MeshifyMotion(
    val springSpec: androidx.compose.animation.core.SpringSpec<Float> = MotionSpecs.Standard,
    val scale: Float = 1.0f
)

val LocalMeshifyMotion = staticCompositionLocalOf { MeshifyMotion() }

/**
 * MD3E Theme Configuration.
 * Central Source of Truth for all design variables.
 * Includes seedColor for static theming and customFontUri for external fonts.
 */
@Immutable
data class MeshifyThemeConfig(
    val shapeStyle: com.p2p.meshify.domain.model.ShapeStyle = com.p2p.meshify.domain.model.ShapeStyle.CIRCLE,
    val motionPreset: com.p2p.meshify.domain.model.MotionPreset = com.p2p.meshify.domain.model.MotionPreset.STANDARD,
    val motionScale: Float = 1.0f,
    val fontFamily: FontFamily = MD3EFontFamilies.Roboto,
    val customFontUri: String? = null,
    val bubbleStyle: com.p2p.meshify.domain.model.BubbleStyle = com.p2p.meshify.domain.model.BubbleStyle.ROUNDED,
    val visualDensity: Float = 1.0f,
    val seedColor: Color = Color(0xFF006D68) // Default teal
)

val LocalMeshifyThemeConfig = staticCompositionLocalOf { MeshifyThemeConfig() }

/**
 * Shared Dimensions and Shapes for Consistency.
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

/**
 * Get chat bubble shape based on selected BubbleStyle from settings.
 */
fun getBubbleShape(
    bubbleStyle: com.p2p.meshify.domain.model.BubbleStyle,
    isFromMe: Boolean,
    isGroupedWithPrevious: Boolean,
    isGroupedWithNext: Boolean
): RoundedCornerShape {
    val radius = MeshifyThemeProperties.ChatBubbleRadius
    val smallRadius = MeshifyThemeProperties.ChatBubbleGroupedRadius

    return when (bubbleStyle) {
        com.p2p.meshify.domain.model.BubbleStyle.ROUNDED -> {
            // Classic rounded bubbles
            when {
                isFromMe -> {
                    when {
                        isGroupedWithPrevious && isGroupedWithNext -> ChatBubbleShapes.MeGroupedMiddle
                        isGroupedWithPrevious -> ChatBubbleShapes.MeGroupedTop
                        isGroupedWithNext -> ChatBubbleShapes.MeGroupedBottom
                        else -> ChatBubbleShapes.Ungrouped
                    }
                }
                else -> {
                    when {
                        isGroupedWithPrevious && isGroupedWithNext -> ChatBubbleShapes.PeerGroupedMiddle
                        isGroupedWithPrevious -> ChatBubbleShapes.PeerGroupedTop
                        else -> ChatBubbleShapes.Ungrouped
                    }
                }
            }
        }
        com.p2p.meshify.domain.model.BubbleStyle.TAILED -> {
            // More pronounced tail effect with asymmetric corners
            RoundedCornerShape(
                topStart = if (isFromMe) radius else smallRadius,
                topEnd = if (isFromMe) smallRadius else radius,
                bottomEnd = radius,
                bottomStart = radius
            )
        }
        com.p2p.meshify.domain.model.BubbleStyle.SQUARCLES -> {
            // Square-circles: minimal rounding
            RoundedCornerShape(8.dp)
        }
        com.p2p.meshify.domain.model.BubbleStyle.ORGANIC -> {
            // Extra rounded, almost pill-like
            RoundedCornerShape(32.dp)
        }
    }
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
 * MD3E Theme - Central Source of Truth.
 * Integrates with Settings Repository for dynamic theming.
 * Supports seedColor for static theming when dynamic color is disabled.
 */
@Composable
fun MeshifyTheme(
    themeMode: String = "SYSTEM",
    dynamicColor: Boolean = true,
    motionPreset: com.p2p.meshify.domain.model.MotionPreset = com.p2p.meshify.domain.model.MotionPreset.STANDARD,
    motionScale: Float = 1.0f,
    fontFamily: FontFamily = MD3EFontFamilies.Roboto,
    shapeStyle: com.p2p.meshify.domain.model.ShapeStyle = com.p2p.meshify.domain.model.ShapeStyle.CIRCLE,
    bubbleStyle: com.p2p.meshify.domain.model.BubbleStyle = com.p2p.meshify.domain.model.BubbleStyle.ROUNDED,
    visualDensity: Float = 1.0f,
    seedColor: Color = Color(0xFF006D68),
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

    val motion = MeshifyMotion(
        springSpec = MotionSpecs.getSpring(motionPreset, motionScale),
        scale = motionScale
    )

    CompositionLocalProvider(
        LocalMeshifyMotion provides motion,
        LocalMeshifyThemeConfig provides MeshifyThemeConfig(
            motionPreset = motionPreset,
            motionScale = motionScale,
            fontFamily = fontFamily,
            shapeStyle = shapeStyle,
            bubbleStyle = bubbleStyle,
            visualDensity = visualDensity,
            seedColor = seedColor
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = getTypography(fontFamily),
            content = content
        )
    }
}
