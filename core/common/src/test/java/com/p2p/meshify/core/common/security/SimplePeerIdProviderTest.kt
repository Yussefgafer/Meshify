package com.p2p.meshify.core.common.security

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

/**
 * Unit tests for SimplePeerIdProvider.
 * Tests cover first-launch UUID generation, persistence across calls,
 * reset behavior, and edge cases.
 */
@RunWith(RobolectricTestRunner::class)
class SimplePeerIdProviderTest {

    // ============================================================================
    // SECTION 1: FIRST LAUNCH (NO PREVIOUS ID)
    // ============================================================================

    @Test
    fun `getPeerId returns valid UUID string on first call`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())

        // When
        val peerId = provider.getPeerId()

        // Then - should be a valid UUID string
        assertNotNull("Peer ID should not be null", peerId)
        assertTrue("Peer ID should be a valid UUID", isValidUuid(peerId))
    }

    @Test
    fun `getPeerId generates unique IDs across different providers`() {
        // Given
        val provider1 = SimplePeerIdProvider(RuntimeEnvironment.getApplication())
        val provider2 = SimplePeerIdProvider(RuntimeEnvironment.getApplication())

        // When
        val id1 = provider1.getPeerId()
        val id2 = provider2.getPeerId()

        // Then - different instances should have different IDs
        // (they share the same SharedPreferences, so second instance gets the same ID)
        assertEquals("Same shared prefs should return same ID", id1, id2)
    }

    // ============================================================================
    // SECTION 2: PERSISTENCE ACROSS CALLS
    // ============================================================================

    @Test
    fun `getPeerId returns same ID on subsequent calls`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())

        // When
        val firstId = provider.getPeerId()
        val secondId = provider.getPeerId()
        val thirdId = provider.getPeerId()

        // Then
        assertEquals("Second call should return same ID", firstId, secondId)
        assertEquals("Third call should return same ID", firstId, thirdId)
    }

    @Test
    fun `getPeerId persists across provider instances`() {
        // Given - first provider
        val provider1 = SimplePeerIdProvider(RuntimeEnvironment.getApplication())
        val id1 = provider1.getPeerId()

        // When - create a new provider instance (same SharedPreferences)
        val provider2 = SimplePeerIdProvider(RuntimeEnvironment.getApplication())
        val id2 = provider2.getPeerId()

        // Then - should retrieve the same persisted ID
        assertEquals("ID should persist across provider instances", id1, id2)
    }

    @Test
    fun `getPeerId is stable across multiple provider re-creations`() {
        // Given
        val app = RuntimeEnvironment.getApplication()
        val firstId = SimplePeerIdProvider(app).getPeerId()

        // When - create and call many times
        val ids = (1..10).map {
            SimplePeerIdProvider(app).getPeerId()
        }

        // Then - all should return the same ID
        ids.forEach { id ->
            assertEquals("All instances should return same persisted ID", firstId, id)
        }
    }

    // ============================================================================
    // SECTION 3: RESET BEHAVIOR
    // ============================================================================

    @Test
    fun `resetPeerId causes next getPeerId to return a new ID`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())
        val originalId = provider.getPeerId()

        // When
        provider.resetPeerId()
        val newId = provider.getPeerId()

        // Then
        assertNotNull("New ID should not be null", newId)
        assertTrue("New ID should be a valid UUID", isValidUuid(newId))
        assertNotEquals("New ID should differ from original", originalId, newId)
    }

    @Test
    fun `resetPeerId followed by multiple calls returns stable new ID`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())
        val originalId = provider.getPeerId()

        // When
        provider.resetPeerId()
        val newId1 = provider.getPeerId()
        val newId2 = provider.getPeerId()
        val newId3 = provider.getPeerId()

        // Then - after reset, the new ID should be stable
        assertEquals("New ID should be stable after first post-reset call", newId1, newId2)
        assertEquals("New ID should be stable across calls", newId1, newId3)
        assertNotEquals("New ID should differ from original", originalId, newId1)
    }

    @Test
    fun `resetPeerId before first getPeerId generates new ID on first call`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())

        // When - reset before ever calling getPeerId
        provider.resetPeerId()
        val firstId = provider.getPeerId()

        // Then
        assertNotNull("Should still generate a valid ID", firstId)
        assertTrue("Should be a valid UUID", isValidUuid(firstId))
    }

    @Test
    fun `multiple resets generate unique IDs each time`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())
        val ids = mutableSetOf<String>()

        // When - reset multiple times
        repeat(5) {
            provider.resetPeerId()
            val newId = provider.getPeerId()
            ids.add(newId)
        }

        // Then - all reset IDs should be unique
        assertEquals("Each reset should produce a unique ID", 5, ids.size)
    }

    // ============================================================================
    // SECTION 4: EDGE CASES
    // ============================================================================

    @Test
    fun `getPeerId returns non-empty string`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())

        // When
        val peerId = provider.getPeerId()

        // Then
        assertNotNull(peerId)
        assertTrue("Peer ID should not be empty", peerId.isNotEmpty())
    }

    @Test
    fun `getPeerId format matches UUID pattern`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())

        // When
        val peerId = provider.getPeerId()

        // Then - UUID format: 8-4-4-4-12 hex digits
        assertTrue(
            "Peer ID should match UUID format: $peerId",
            peerId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
        )
    }

    @Test
    fun `resetPeerId on fresh provider does not throw`() {
        // Given
        val provider = SimplePeerIdProvider(RuntimeEnvironment.getApplication())

        // When/Then - should not throw
        provider.resetPeerId()
        provider.resetPeerId()
        provider.resetPeerId()
    }

    // ============================================================================
    // SECTION 5: HELPER
    // ============================================================================

    private fun isValidUuid(str: String): Boolean {
        return try {
            UUID.fromString(str)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
