package com.p2p.meshify.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for PeerNameParser.
 * Covers standard parsing, edge cases, and boundary conditions.
 */
class PeerNameParserTest {

    // ============================================================================
    // SECTION 1: HAPPY PATH TESTS
    // ============================================================================

    @Test
    fun `parseName extracts name before device id in parentheses`() {
        // Given
        val raw = "Alice (abc123)"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("Alice", result)
    }

    @Test
    fun `parseName returns plain name as-is when no parentheses`() {
        // Given
        val raw = "justname"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("justname", result)
    }

    @Test
    fun `parseName handles empty string`() {
        // Given
        val raw = ""

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("", result)
    }

    // ============================================================================
    // SECTION 2: WHITESPACE AND FORMATTING
    // ============================================================================

    @Test
    fun `parseName trims whitespace around name`() {
        // Given
        val raw = "  Bob  (device456)  "

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("Bob", result)
    }

    @Test
    fun `parseName returns trimmed plain name with surrounding whitespace`() {
        // Given
        val raw = "  spaced name  "

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("spaced name", result)
    }

    // ============================================================================
    // SECTION 3: EDGE CASES AND BOUNDARIES
    // ============================================================================

    @Test
    fun `parseName with multiple parentheses returns only first segment`() {
        // Given
        val raw = "name (first) (second)"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("name", result)
    }

    @Test
    fun `parseName with no space before parenthesis returns full string`() {
        // Given - no " (" delimiter present
        val raw = "name(id)"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("name(id)", result)
    }

    @Test
    fun `parseName with only device id in parentheses`() {
        // Given
        val raw = " (onlyid)"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("", result)
    }

    @Test
    fun `parseName name with special characters`() {
        // Given
        val raw = "user@domain.com (abc123)"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("user@domain.com", result)
    }

    @Test
    fun `parseName name with unicode characters`() {
        // Given
        val raw = "用户 (device_id)"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("用户", result)
    }

    @Test
    fun `parseName name with very long device id`() {
        // Given
        val longId = "a".repeat(1000)
        val raw = "name ($longId)"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("name", result)
    }

    @Test
    fun `parseName with just parentheses and no content`() {
        // Given
        val raw = "name ()"

        // When
        val result = PeerNameParser.parseName(raw)

        // Then
        assertEquals("name", result)
    }
}
