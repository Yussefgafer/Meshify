package com.p2p.meshify.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ThemeConfig enums: ShapeStyle, MotionPreset, FontFamilyPreset, BubbleStyle.
 */
class ThemeConfigTest {

    // --- ShapeStyle ---

    @Test
    fun `ShapeStyle contains all expected values`() {
        val expected = listOf(
            ShapeStyle.SUNNY,
            ShapeStyle.BREEZY,
            ShapeStyle.PENTAGON,
            ShapeStyle.BLOB,
            ShapeStyle.BURST,
            ShapeStyle.CLOVER,
            ShapeStyle.CIRCLE
        )

        assertEquals(expected.size, ShapeStyle.values().size)
        assertTrue(ShapeStyle.values().toList().containsAll(expected))
    }

    @Test
    fun `ShapeStyle values have unique names`() {
        val names = ShapeStyle.values().map { it.name }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `ShapeStyle valueOf returns correct enum for each style`() {
        assertEquals(ShapeStyle.SUNNY, ShapeStyle.valueOf("SUNNY"))
        assertEquals(ShapeStyle.BREEZY, ShapeStyle.valueOf("BREEZY"))
        assertEquals(ShapeStyle.PENTAGON, ShapeStyle.valueOf("PENTAGON"))
        assertEquals(ShapeStyle.BLOB, ShapeStyle.valueOf("BLOB"))
        assertEquals(ShapeStyle.BURST, ShapeStyle.valueOf("BURST"))
        assertEquals(ShapeStyle.CLOVER, ShapeStyle.valueOf("CLOVER"))
        assertEquals(ShapeStyle.CIRCLE, ShapeStyle.valueOf("CIRCLE"))
    }

    @Test
    fun `ShapeStyle ordinal order is as declared`() {
        assertEquals(0, ShapeStyle.SUNNY.ordinal)
        assertEquals(1, ShapeStyle.BREEZY.ordinal)
        assertEquals(2, ShapeStyle.PENTAGON.ordinal)
        assertEquals(3, ShapeStyle.BLOB.ordinal)
        assertEquals(4, ShapeStyle.BURST.ordinal)
        assertEquals(5, ShapeStyle.CLOVER.ordinal)
        assertEquals(6, ShapeStyle.CIRCLE.ordinal)
    }

    // --- MotionPreset ---

    @Test
    fun `MotionPreset contains all expected values`() {
        val expected = listOf(
            MotionPreset.GENTLE,
            MotionPreset.STANDARD,
            MotionPreset.SNAPPY,
            MotionPreset.BOUNCY
        )

        assertEquals(expected.size, MotionPreset.values().size)
        assertTrue(MotionPreset.values().toList().containsAll(expected))
    }

    @Test
    fun `MotionPreset values have unique names`() {
        val names = MotionPreset.values().map { it.name }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `MotionPreset valueOf returns correct enum for each preset`() {
        assertEquals(MotionPreset.GENTLE, MotionPreset.valueOf("GENTLE"))
        assertEquals(MotionPreset.STANDARD, MotionPreset.valueOf("STANDARD"))
        assertEquals(MotionPreset.SNAPPY, MotionPreset.valueOf("SNAPPY"))
        assertEquals(MotionPreset.BOUNCY, MotionPreset.valueOf("BOUNCY"))
    }

    @Test
    fun `MotionPreset ordinal order is as declared`() {
        assertEquals(0, MotionPreset.GENTLE.ordinal)
        assertEquals(1, MotionPreset.STANDARD.ordinal)
        assertEquals(2, MotionPreset.SNAPPY.ordinal)
        assertEquals(3, MotionPreset.BOUNCY.ordinal)
    }

    // --- FontFamilyPreset ---

    @Test
    fun `FontFamilyPreset contains all expected values`() {
        val expected = listOf(
            FontFamilyPreset.ROBOTO,
            FontFamilyPreset.POPPINS,
            FontFamilyPreset.LORA,
            FontFamilyPreset.MONTSERRAT,
            FontFamilyPreset.PLAYFAIR,
            FontFamilyPreset.INTER
        )

        assertEquals(expected.size, FontFamilyPreset.values().size)
        assertTrue(FontFamilyPreset.values().toList().containsAll(expected))
    }

    @Test
    fun `FontFamilyPreset values have unique names`() {
        val names = FontFamilyPreset.values().map { it.name }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `FontFamilyPreset valueOf returns correct enum for each preset`() {
        assertEquals(FontFamilyPreset.ROBOTO, FontFamilyPreset.valueOf("ROBOTO"))
        assertEquals(FontFamilyPreset.POPPINS, FontFamilyPreset.valueOf("POPPINS"))
        assertEquals(FontFamilyPreset.LORA, FontFamilyPreset.valueOf("LORA"))
        assertEquals(FontFamilyPreset.MONTSERRAT, FontFamilyPreset.valueOf("MONTSERRAT"))
        assertEquals(FontFamilyPreset.PLAYFAIR, FontFamilyPreset.valueOf("PLAYFAIR"))
        assertEquals(FontFamilyPreset.INTER, FontFamilyPreset.valueOf("INTER"))
    }

    @Test
    fun `FontFamilyPreset ordinal order is as declared`() {
        assertEquals(0, FontFamilyPreset.ROBOTO.ordinal)
        assertEquals(1, FontFamilyPreset.POPPINS.ordinal)
        assertEquals(2, FontFamilyPreset.LORA.ordinal)
        assertEquals(3, FontFamilyPreset.MONTSERRAT.ordinal)
        assertEquals(4, FontFamilyPreset.PLAYFAIR.ordinal)
        assertEquals(5, FontFamilyPreset.INTER.ordinal)
    }

    // --- BubbleStyle ---

    @Test
    fun `BubbleStyle contains all expected values`() {
        val expected = listOf(
            BubbleStyle.ROUNDED,
            BubbleStyle.TAILED,
            BubbleStyle.SQUARCLES,
            BubbleStyle.ORGANIC
        )

        assertEquals(expected.size, BubbleStyle.values().size)
        assertTrue(BubbleStyle.values().toList().containsAll(expected))
    }

    @Test
    fun `BubbleStyle values have unique names`() {
        val names = BubbleStyle.values().map { it.name }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `BubbleStyle valueOf returns correct enum for each style`() {
        assertEquals(BubbleStyle.ROUNDED, BubbleStyle.valueOf("ROUNDED"))
        assertEquals(BubbleStyle.TAILED, BubbleStyle.valueOf("TAILED"))
        assertEquals(BubbleStyle.SQUARCLES, BubbleStyle.valueOf("SQUARCLES"))
        assertEquals(BubbleStyle.ORGANIC, BubbleStyle.valueOf("ORGANIC"))
    }

    @Test
    fun `BubbleStyle ordinal order is as declared`() {
        assertEquals(0, BubbleStyle.ROUNDED.ordinal)
        assertEquals(1, BubbleStyle.TAILED.ordinal)
        assertEquals(2, BubbleStyle.SQUARCLES.ordinal)
        assertEquals(3, BubbleStyle.ORGANIC.ordinal)
    }
}
