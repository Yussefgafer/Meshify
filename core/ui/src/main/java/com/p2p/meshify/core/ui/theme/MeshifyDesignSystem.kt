package com.p2p.meshify.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
        val Card = RoundedCornerShape(12.dp)
        val CardLarge = RoundedCornerShape(16.dp)
        val CardSmall = RoundedCornerShape(8.dp)
        val Button = RoundedCornerShape(10.dp)
        val Input = RoundedCornerShape(8.dp)
        val Pill = RoundedCornerShape(8.dp)
        val Avatar = RoundedCornerShape(8.dp)
        val IconContainer = RoundedCornerShape(12.dp)
        val Dialog = RoundedCornerShape(16.dp)
    }

    object IconSizes {
        val Small = 18.dp
        val Medium = 22.dp
        val Large = 24.dp
        val XL = 32.dp
        val XXL = 40.dp
    }

    object AvatarSizes {
        val Small = 40.dp
        val Medium = 48.dp
        val Large = 56.dp
        val XL = 80.dp
        val XXL = 120.dp
    }

    object SeedColorPresets {
        val Teal = Color(0xFF006D68)
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
}
