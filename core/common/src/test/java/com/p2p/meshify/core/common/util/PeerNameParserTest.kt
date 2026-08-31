package com.p2p.meshify.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [PeerNameParser].
 *
 * The parser strips a trailing device identifier in parentheses
 * (`"name (device_id)"` -> `"name"`) and returns standard names verbatim.
 * All cases are plain-JVM (no Android dependency).
 */
class PeerNameParserTest {

    @Test
    fun `strips trailing device id in parentheses`() {
        assertEquals("Alice", PeerNameParser.parseName("Alice (device-1234)"))
        assertEquals("Bob", PeerNameParser.parseName("Bob (abc-def-999)"))
    }

    @Test
    fun `returns standard name without parentheses verbatim`() {
        assertEquals("Alice", PeerNameParser.parseName("Alice"))
        assertEquals("Bob Smith", PeerNameParser.parseName("Bob Smith"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("Alice", PeerNameParser.parseName("  Alice (device-1)  "))
        assertEquals("Alice", PeerNameParser.parseName("  Alice  "))
    }

    @Test
    fun `handles spaces inside the device id segment`() {
        // Only the part up to " (" is taken; parentheses content is dropped.
        assertEquals("Alice", PeerNameParser.parseName("Alice (My Phone)"))
    }

    @Test
    fun `keeps text before the first open paren only`() {
        // Everything from the first " (" onward is treated as the suffix.
        assertEquals("Alice", PeerNameParser.parseName("Alice (id) (extra)"))
    }

    @Test
    fun `empty and blank input returns empty after trim`() {
        assertEquals("", PeerNameParser.parseName(""))
        assertEquals("", PeerNameParser.parseName("   "))
    }

    @Test
    fun `name that is only device id pattern returns that portion`() {
        // If the whole string contains no " (", it is returned verbatim.
        assertEquals("(no-id-here)", PeerNameParser.parseName("(no-id-here)"))
    }

    @Test
    fun `open paren with no content still splits`() {
        assertEquals("Alice", PeerNameParser.parseName("Alice ()"))
    }

    @Test
    fun `unicode names are preserved`() {
        assertEquals("مرحبا", PeerNameParser.parseName("مرحبا (peer-7)"))
        assertEquals("你好", PeerNameParser.parseName("你好"))
    }

    @Test
    fun `a lone open paren not prefixed by a space is not split`() {
        // " (" (space+open-paren) is the delimiter; a lone "(" is preserved.
        val parsed = PeerNameParser.parseName("Alice(device)")
        assertEquals("Alice(device)", parsed)
    }
}
