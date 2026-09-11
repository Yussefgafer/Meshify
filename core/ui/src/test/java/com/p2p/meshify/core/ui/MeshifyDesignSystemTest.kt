package com.p2p.meshify.core.ui

import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class MeshifyDesignSystemTest {
    @Test fun `meshify spacing tokens`() {
        assertEquals(4.dp, MeshifyDesignSystem.Spacing.Xxs)
        assertEquals(8.dp, MeshifyDesignSystem.Spacing.Xs)
        assertEquals(12.dp, MeshifyDesignSystem.Spacing.Sm)
        assertEquals(16.dp, MeshifyDesignSystem.Spacing.Md)
        assertEquals(24.dp, MeshifyDesignSystem.Spacing.Lg)
        assertEquals(32.dp, MeshifyDesignSystem.Spacing.Xl)
        assertEquals(48.dp, MeshifyDesignSystem.Spacing.Xxl)
    }
    @Test fun shapesHaveExpectedRadiiInOutline() {
        val card = MeshifyDesignSystem.Shapes.Card.toString()
        val cardSmall = MeshifyDesignSystem.Shapes.CardSmall.toString()
        assert(card.contains("12.0")) { "Card expected 12.dp, got $card" }
        assert(cardSmall.contains("8.0")) { "CardSmall expected 8.dp, got $cardSmall" }
        assert(card != cardSmall) { "Card and CardSmall should have distinct radii" }
    }
}
