package com.p2p.meshify.core.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Meshify Unified Design System.
 * Single Source of Truth for Spacing, Shapes, and Elevation.
 */
object MeshifyDesignSystem {

    object Spacing {
        val Xxs = 4.dp
        val Xs = 8.dp
        val Sm = 12.dp
        val Md = 16.dp
        val Lg = 24.dp
        val Xl = 32.dp
        val Xxl = 48.dp
    }

    object Shapes {
        val CardLarge = RoundedCornerShape(28.dp)
        val CardMedium = RoundedCornerShape(20.dp)
        val CardSmall = RoundedCornerShape(16.dp)
        val BubbleMe = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        val BubblePeer = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        val Button = RoundedCornerShape(20.dp)
        val Input = RoundedCornerShape(24.dp)
        val Pill = RoundedCornerShape(50)
    }

    // ✅ FIX: Icon Sizes - use this instead of hardcoded values
    object IconSizes {
        val Small = 18.dp
        val Medium = 22.dp
        val Large = 24.dp
        val XL = 32.dp
        val XXL = 40.dp
    }

    // ✅ FIX: Avatar Sizes - use this instead of hardcoded values
    object AvatarSizes {
        val Small = 40.dp
        val Medium = 48.dp
        val Large = 56.dp
        val XL = 80.dp
        val XXL = 120.dp
    }

    // ✅ FIX: Dialog Shapes - use this instead of hardcoded values
    object DialogShapes {
        val Default = RoundedCornerShape(28.dp)
        val Small = RoundedCornerShape(16.dp)
        val Medium = RoundedCornerShape(20.dp)
    }

    // ✅ FIX: Seed Color Presets - use this instead of hardcoded values
    object SeedColorPresets {
        val Teal = Color(0xFF008080)
        val Blue = Color(0xFF0000FF)
        val Purple = Color(0xFF800080)
        val Pink = Color(0xFFFFC0CB)
        val Red = Color(0xFFFF0000)
        val Orange = Color(0xFFFFA500)
        val Green = Color(0xFF008000)
        val Cyan = Color(0xFF00FFFF)
        val Indigo = Color(0xFF4B0082)
        val Lime = Color(0xFF32CD32)
    }

    object Elevation {
        val Level0 = 0.dp
        val Level1 = 1.dp
        val Level2 = 2.dp
        val Level3 = 4.dp
        val Level4 = 6.dp
        val Level5 = 8.dp
    }

    object Motion {
        fun <T> expressiveSpring() = spring<T>(
            dampingRatio = 0.75f,
            stiffness = 350f
        )
    }
}
