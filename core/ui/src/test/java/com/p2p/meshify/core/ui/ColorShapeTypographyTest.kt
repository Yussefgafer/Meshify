package com.p2p.meshify.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p2p.meshify.core.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ColorShapeTypographyTest {

    @Test fun `Meshify primary colors match spec`() {
        assertEquals(Color(0xFF6C4FF5), MeshifyPrimary)
        assertEquals(Color(0xFFFFFFFF), MeshifyOnPrimary)
        assertEquals(Color(0xFFE3DBFF), MeshifyPrimaryContainer)
        assertEquals(Color(0xFF23005C), MeshifyOnPrimaryContainer)
    }

    @Test fun `Meshify secondary colors match spec`() {
        assertEquals(Color(0xFFAB47BC), MeshifySecondary)
        assertEquals(Color(0xFFF3D4FF), MeshifySecondaryContainer)
        assertEquals(Color(0xFF45005A), MeshifyOnSecondaryContainer)
    }

    @Test fun `Meshify tertiary and error match spec`() {
        assertEquals(Color(0xFFFF8A65), MeshifyTertiary)
        assertEquals(Color(0xFFFFDBCF), MeshifyTertiaryContainer)
        assertEquals(Color(0xFF3E0D00), MeshifyOnTertiaryContainer)
        assertEquals(Color(0xFFD32F2F), MeshifyError)
    }

    @Test fun `dark theme variants`() {
        assertEquals(Color(0xFFB394FF), PrimaryDark)
        assertEquals(Color(0xFFF06292), SecondaryDark)
        assertEquals(Color(0xFF1C1B1F), BackgroundDark)
    }

    @Test fun `StatusOnline and presets`() {
        assertEquals(Color(0xFF4CAF50), StatusOnline)
        assertEquals(Color(0xFF006D68), ColorPresetTeal)
        assertEquals(Color(0xFF6750A4), ColorPresetPurple)
        assertEquals(Color(0xFF006E1C), ColorPresetGreen)
        assertEquals(Color(0xFFB81D24), ColorPresetRed)
        assertEquals(Color(0xFFD4880C), ColorPresetAmber)
        assertEquals(Color(0xFF00649F), ColorPresetBlue)
        assertEquals(Color(0xFF984061), ColorPresetPink)
        assertEquals(Color(0xFF5C5D50), ColorPresetNeutral)
        // distinct
        val presets = listOf(ColorPresetTeal, ColorPresetPurple, ColorPresetGreen, ColorPresetRed, ColorPresetAmber, ColorPresetBlue, ColorPresetPink, ColorPresetNeutral)
        assertEquals(presets.size, presets.toSet().size)
    }

    @Test fun `Shapes have expected radii`() {
        assertEquals(RoundedCornerShape(8.dp), Shapes.small)
        assertEquals(RoundedCornerShape(16.dp), Shapes.medium)
        assertEquals(RoundedCornerShape(24.dp), Shapes.large)
    }

    @Test fun `Typography display sizes`() {
        assertEquals(48.sp, Typography.displayLarge.fontSize)
        assertEquals(36.sp, Typography.displayMedium.fontSize)
        assertEquals(30.sp, Typography.displaySmall.fontSize)
        assertEquals(32.sp, Typography.headlineLarge.fontSize)
        assertEquals(28.sp, Typography.headlineMedium.fontSize)
        assertEquals(24.sp, Typography.headlineSmall.fontSize)
    }

    @Test fun `Typography body and label sizes`() {
        assertEquals(16.sp, Typography.bodyLarge.fontSize)
        assertEquals(14.sp, Typography.bodyMedium.fontSize)
        assertEquals(12.sp, Typography.bodySmall.fontSize)
        assertEquals(14.sp, Typography.labelLarge.fontSize)
        assertEquals(11.sp, Typography.labelSmall.fontSize)
    }

    @Test fun `Typography scaled returns same when 1f`() {
        assertEquals(Typography, Typography.scaled(1f))
        // same instance check is not required, but values equal
        assertEquals(Typography.displayLarge.fontSize, Typography.scaled(1f).displayLarge.fontSize)
    }

    @Test fun `Typography scaled scales all styles`() {
        val scaled = Typography.scaled(1.5f)
        assertEquals(72.sp, scaled.displayLarge.fontSize) // 48*1.5
        assertEquals(54.sp, scaled.displayMedium.fontSize) // 36*1.5
        assertEquals(24.sp, scaled.bodyLarge.fontSize) // 16*1.5
        assertEquals(24.sp, scaled.titleMedium.fontSize) // 16*1.5
        assertEquals(33.sp, scaled.titleLarge.fontSize) // 22*1.5
        // lineHeight also scaled
        assertEquals(84.sp, scaled.displayLarge.lineHeight) // 56*1.5
        assertEquals(36.sp, scaled.bodyLarge.lineHeight) // 24*1.5
    }

    @Test fun `Typography scaled down`() {
        val scaled = Typography.scaled(0.5f)
        assertEquals(24.sp, scaled.displayLarge.fontSize)
        assertEquals(8.sp, scaled.bodyLarge.fontSize)
    }

    @Test fun `MeshifyDesignSystem shapes distinct`() {
        assertNotEquals(MeshifyDesignSystem.Shapes.Card, MeshifyDesignSystem.Shapes.CardSmall)
        assertEquals(RoundedCornerShape(12.dp), MeshifyDesignSystem.Shapes.Card)
        assertEquals(RoundedCornerShape(8.dp), MeshifyDesignSystem.Shapes.CardSmall)
        assertEquals(RoundedCornerShape(16.dp), MeshifyDesignSystem.Shapes.Dialog)
    }

    @Test fun `MeshifyDesignSystem spacing monotonic`() {
        assertTrue(MeshifyDesignSystem.Spacing.Xxs < MeshifyDesignSystem.Spacing.Xs)
        assertTrue(MeshifyDesignSystem.Spacing.Xs < MeshifyDesignSystem.Spacing.Sm)
        assertTrue(MeshifyDesignSystem.Spacing.Sm < MeshifyDesignSystem.Spacing.Md)
        assertTrue(MeshifyDesignSystem.Spacing.Md < MeshifyDesignSystem.Spacing.Lg)
        assertTrue(MeshifyDesignSystem.Spacing.Lg < MeshifyDesignSystem.Spacing.Xl)
    }

    @Test fun `MeshifyDesignSystem elevation levels`() {
        assertEquals(0.dp, MeshifyDesignSystem.Elevation.Level0)
        assertEquals(1.dp, MeshifyDesignSystem.Elevation.Level1)
        assertEquals(2.dp, MeshifyDesignSystem.Elevation.Level2)
        assertEquals(4.dp, MeshifyDesignSystem.Elevation.Level3)
    }
}
