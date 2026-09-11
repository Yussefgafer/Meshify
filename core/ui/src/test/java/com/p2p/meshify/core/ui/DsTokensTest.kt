package com.p2p.meshify.core.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.designsystem.foundation.DxElevation
import com.p2p.meshify.core.ui.designsystem.foundation.DxExpressiveShapes
import com.p2p.meshify.core.ui.designsystem.foundation.DxShape
import com.p2p.meshify.core.ui.designsystem.foundation.DxSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DsTokensTest {

    @Test fun `spacing tokens match spec`() {
        assertEquals(2.dp, DxSpacing.Xxs)
        assertEquals(4.dp, DxSpacing.Xs)
        assertEquals(8.dp, DxSpacing.Sm)
        assertEquals(12.dp, DxSpacing.Md)
        assertEquals(16.dp, DxSpacing.Lg)
        assertEquals(24.dp, DxSpacing.Xl)
        assertEquals(32.dp, DxSpacing.Xxl)
        assertEquals(48.dp, DxSpacing.Xxxl)
    }

    @Test fun `shape tokens are RoundedCornerShape`() {
        assertNotNull(DxShape.ExtraSmall)
        assertNotNull(DxShape.Medium)
        assertNotNull(DxShape.Dialog)
        assertNotNull(DxShape.Full)
        assertEquals(DxShape.Chip, DxShape.ExtraSmall)
        assertEquals(DxShape.IconBox, DxShape.Small)
        assertEquals(DxShape.Card, DxShape.Medium)
        assertEquals(DxShape.Pill, DxShape.Large)
        assertEquals(DxShape.Sheet, DxShape.ExtraLarge)
    }

    @Test fun `elevation tokens monotonic`() {
        assert(DxElevation.Level0 <= DxElevation.Level1)
        assert(DxElevation.Level1 <= DxElevation.Level2)
        assert(DxElevation.Level2 <= DxElevation.Level5)
        assertEquals(0.dp, DxElevation.Level0)
        assertEquals(12.dp, DxElevation.Level5)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test fun `expressive shapes expose MaterialShapes`() {
        assertNotNull(DxExpressiveShapes.Cookie9Sided)
        assertNotNull(DxExpressiveShapes.Sunny)
        assertNotNull(DxExpressiveShapes.Burst)
        assertNotNull(DxExpressiveShapes.Pill)
        assertNotNull(DxExpressiveShapes.Clover4Leaf)
        // existence of getters validates alias mapping
        assert(MaterialShapes.Cookie9Sided == DxExpressiveShapes.Cookie9Sided)
    }
}
