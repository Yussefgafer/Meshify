package com.p2p.meshify.core.common.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Unit tests for [SimplePeerIdProvider].
 *
 * Verifies the persistent-identity contract under Robolectric (the project
 * compiles test bytecode to class-file v65 via jvmToolchain(21), which
 * Robolectric 4.16.1 instruments fine):
 * - `getPeerId()` is stable across repeated calls (same instance + across
 *   instances sharing the same application `SharedPreferences`).
 * - the id is generated once and persisted across restarts.
 * - `resetPeerId()` clears the stored id so the next `getPeerId()` regenerates
 *   a different one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SimplePeerIdProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `getPeerId is stable across repeated calls on the same instance`() {
        val provider = SimplePeerIdProvider(context)
        val first = provider.getPeerId()
        val second = provider.getPeerId()
        assertEquals(first, second)
    }

    @Test
    fun `peer id is persisted across provider instances sharing context`() {
        val id1 = SimplePeerIdProvider(context).getPeerId()
        // A fresh provider reading the same SharedPreferences must return the
        // already-persisted id (simulates an app restart).
        val id2 = SimplePeerIdProvider(context).getPeerId()
        assertEquals(id1, id2)
    }

    @Test
    fun `peer id has uuid shape`() {
        val id = SimplePeerIdProvider(context).getPeerId()
        assertEquals(36, id.length)
        assertTrue("expected UUID shape, got: $id", id.count { it == '-' } == 4)
        // Parsing must not throw for a valid UUID string.
        assertEquals(id, UUID.fromString(id).toString())
    }

    @Test
    fun `resetPeerId regenerates a different id on next call`() {
        val provider = SimplePeerIdProvider(context)
        val original = provider.getPeerId()

        provider.resetPeerId()
        val regenerated = provider.getPeerId()

        assertNotEquals(original, regenerated)
        assertTrue("regenerated id must still be a UUID", regenerated.count { it == '-' } == 4)
    }

    @Test
    fun `resetPeerId then persistence keeps the new id stable`() {
        val provider = SimplePeerIdProvider(context)
        provider.resetPeerId()
        val newId = provider.getPeerId()

        // A separate instance must observe the newly persisted id.
        assertEquals(newId, SimplePeerIdProvider(context).getPeerId())
    }
}
