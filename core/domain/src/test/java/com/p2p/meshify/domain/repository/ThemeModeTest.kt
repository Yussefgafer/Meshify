package com.p2p.meshify.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `ThemeMode has exactly three values in expected order`() {
        val values = ThemeMode.values()
        assertEquals(3, values.size)
        assertEquals(ThemeMode.LIGHT, values[0])
        assertEquals(ThemeMode.DARK, values[1])
        assertEquals(ThemeMode.SYSTEM, values[2])
    }

    @Test
    fun `ThemeMode names are stable`() {
        assertEquals("LIGHT", ThemeMode.LIGHT.name)
        assertEquals("DARK", ThemeMode.DARK.name)
        assertEquals("SYSTEM", ThemeMode.SYSTEM.name)
    }

    @Test
    fun `ThemeMode valueOf round-trip`() {
        for (mode in ThemeMode.values()) {
            assertEquals(mode, ThemeMode.valueOf(mode.name))
        }
    }

    @Test
    fun `ThemeMode exhaustive when covers all cases`() {
        for (mode in ThemeMode.values()) {
            val label = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
            assertNotNull(label)
            assertTrue(label.isNotEmpty())
        }
    }

    @Test
    fun `ThemeMode ordinal is stable`() {
        assertEquals(0, ThemeMode.LIGHT.ordinal)
        assertEquals(1, ThemeMode.DARK.ordinal)
        assertEquals(2, ThemeMode.SYSTEM.ordinal)
    }
}
