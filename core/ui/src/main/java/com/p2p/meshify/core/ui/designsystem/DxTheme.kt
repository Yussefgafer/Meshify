package com.p2p.meshify.core.ui.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.p2p.meshify.core.ui.designsystem.foundation.DxShape
import com.p2p.meshify.core.ui.theme.BackgroundDark
import com.p2p.meshify.core.ui.theme.MeshifyError
import com.p2p.meshify.core.ui.theme.MeshifyOnError
import com.p2p.meshify.core.ui.theme.MeshifyOnPrimary
import com.p2p.meshify.core.ui.theme.MeshifyOnPrimaryContainer
import com.p2p.meshify.core.ui.theme.MeshifyOnSecondary
import com.p2p.meshify.core.ui.theme.MeshifyOnSecondaryContainer
import com.p2p.meshify.core.ui.theme.MeshifyOnTertiary
import com.p2p.meshify.core.ui.theme.MeshifyOnTertiaryContainer
import com.p2p.meshify.core.ui.theme.MeshifyPrimary
import com.p2p.meshify.core.ui.theme.MeshifyPrimaryContainer
import com.p2p.meshify.core.ui.theme.MeshifySecondary
import com.p2p.meshify.core.ui.theme.MeshifySecondaryContainer
import com.p2p.meshify.core.ui.theme.MeshifyTertiary
import com.p2p.meshify.core.ui.theme.MeshifyTertiaryContainer
import com.p2p.meshify.core.ui.theme.PrimaryDark
import com.p2p.meshify.core.ui.theme.SecondaryDark
import com.p2p.meshify.core.ui.theme.SurfaceContainerHighDark
import com.p2p.meshify.core.ui.theme.SurfaceDark
import com.p2p.meshify.core.ui.theme.TertiaryDark
import com.p2p.meshify.core.ui.theme.Typography

private val DxDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFF250061),
    primaryContainer = Color(0xFF3D00A1),
    onPrimaryContainer = Color(0xFFE3DBFF),
    secondary = SecondaryDark,
    onSecondary = Color(0xFF61003A),
    secondaryContainer = Color(0xFF890054),
    onSecondaryContainer = Color(0xFFF3D4FF),
    tertiary = TertiaryDark,
    onTertiary = Color(0xFF5E1900),
    tertiaryContainer = Color(0xFF7E2E00),
    onTertiaryContainer = Color(0xFFFFDBCF),
    error = Color(0xFFFF5252),
    onError = Color(0xFF690005),
    background = BackgroundDark,
    onBackground = Color(0xFFE6E1E5),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceContainerHigh = SurfaceContainerHighDark
)

private val DxLightColorScheme = lightColorScheme(
    primary = MeshifyPrimary,
    onPrimary = MeshifyOnPrimary,
    primaryContainer = MeshifyPrimaryContainer,
    onPrimaryContainer = MeshifyOnPrimaryContainer,
    secondary = MeshifySecondary,
    onSecondary = MeshifyOnSecondary,
    secondaryContainer = MeshifySecondaryContainer,
    onSecondaryContainer = MeshifyOnSecondaryContainer,
    tertiary = MeshifyTertiary,
    onTertiary = MeshifyOnTertiary,
    tertiaryContainer = MeshifyTertiaryContainer,
    onTertiaryContainer = MeshifyOnTertiaryContainer,
    error = MeshifyError,
    onError = MeshifyOnError,
    background = Color(0xFFF7F2FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFFF0EAFC)
)

/**
 * Expressive theme using [MaterialExpressiveTheme] with
 * [MotionScheme.expressive] and the Koda-inspired shape scale
 * (8/12/20/28/36).
 *
 * Shares the same palette (Purple/Pink/Orange) and Google Sans Rounded
 * typography as [MeshifyTheme] so both systems stay in sync.
 * Does NOT include status-bar styling — that is handled by the caller
 * or by [MeshifyTheme] at the root.
 */
@Composable
fun DxTheme(
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
        darkTheme -> DxDarkColorScheme
        else -> DxLightColorScheme
    }

    val expressiveShapes = Shapes(
        extraSmall = DxShape.ExtraSmall,
        small = DxShape.Small,
        medium = DxShape.Medium,
        large = DxShape.Large,
        extraLarge = DxShape.ExtraLarge
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = expressiveShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
