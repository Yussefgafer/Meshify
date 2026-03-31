package com.p2p.meshify.core.data.security.impl

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.common.util.HexUtil
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EncryptedSessionKeyStoreTest {

    private lateinit var context: Context
    private lateinit var store: EncryptedSessionKeyStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = EncryptedSessionKeyStore(context)
    }

    private fun generateSessionKey(): ByteArray = ByteArray(32) { 0x42 }
    private fun generatePublicKeyHex(): String = ByteArray(64) { 0x55 }.joinToString("") { "%02x".format(it) }

    @Test
    fun `session key persists across app restart`() {
        val peerId = "peer-123"
        val sessionKey = generateSessionKey()
        val publicKeyHex = generatePublicKeyHex()

        store.putSessionKey(peerId, sessionKey, publicKeyHex)

        store.destroy()
        val newStore = EncryptedSessionKeyStore(context)

        val retrievedInfo = newStore.getSessionKey(peerId)
        Assert.assertNotNull("Session should persist across restart", retrievedInfo)
        Assert.assertArrayEquals(
            "Session key must match after restart",
            sessionKey,
            retrievedInfo?.sessionKey
        )
        Assert.assertEquals(
            "Public key must match after restart",
            publicKeyHex,
            retrievedInfo?.peerPublicKeyHex
        )
    }

    @Test
    fun `same key is accepted without TOFU violation`() {
        val peerId = "peer-456"
        val sessionKey = generateSessionKey()
        val publicKeyHex = generatePublicKeyHex()

        store.putSessionKey(peerId, sessionKey, publicKeyHex)

        val tofuResult = store.validatePeerPublicKey(peerId, publicKeyHex)

        Assert.assertEquals(
            "Same key should be accepted (no TOFU violation)",
            true,
            tofuResult
        )
    }

    @Test
    fun `hasSession returns true for valid session`() {
        val peerId = "peer-789"
        store.putSessionKey(peerId, generateSessionKey(), generatePublicKeyHex())

        val hasSession = store.hasSession(peerId)

        Assert.assertTrue("Session should exist", hasSession)
    }

    @Test
    fun `TOFU violation aborts session establishment`() {
        val peerId = "attacker-peer"
        val originalPublicKeyHex = generatePublicKeyHex()
        store.putSessionKey(peerId, generateSessionKey(), originalPublicKeyHex)

        val attackerPublicKeyHex = ByteArray(64) { 0x66 }.joinToString("") { "%02x".format(it) }
        val tofuResult = store.validatePeerPublicKey(peerId, attackerPublicKeyHex)

        Assert.assertEquals(
            "Different key should trigger TOFU violation",
            false,
            tofuResult
        )
    }

    @Test
    fun `TOFU violation returns false not null`() {
        val peerId = "tofu-test-peer"
        store.putSessionKey(peerId, generateSessionKey(), generatePublicKeyHex())

        val result = store.validatePeerPublicKey(peerId, "wrong-key-hex")

        Assert.assertEquals(
            "TOFU violation must return false",
            false,
            result
        )
    }

    @Test
    fun `first handshake returns null for TOFU check`() {
        val newPeerId = "brand-new-peer"

        val tofuResult = store.validatePeerPublicKey(newPeerId, generatePublicKeyHex())

        Assert.assertNull(
            "First handshake should return null (TOFU trust)",
            tofuResult
        )
    }

    @Test
    fun `session older than 24 hours is expired`() {
        val peerId = "old-peer"
        val sessionKey = generateSessionKey()
        val publicKeyHex = generatePublicKeyHex()

        val now = System.currentTimeMillis()
        val oldTimestamp = now - (25 * 60 * 60 * 1000L)

        val prefs = EncryptedSharedPreferences.create(
            context,
            "encrypted_session_keys",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        prefs.edit().apply {
            putString("session_key:$peerId", HexUtil.toHex(sessionKey))
            putString("peer_pubkey:$peerId", publicKeyHex)
            putLong("session_timestamp:$peerId", oldTimestamp)
            apply()
        }

        store.destroy()
        val newStore = EncryptedSessionKeyStore(context)

        val sessionInfo = newStore.getSessionKey(peerId)

        Assert.assertNull(
            "Session older than 24 hours should be expired",
            sessionInfo
        )
    }

    @Test
    fun `session younger than 24 hours is valid`() {
        val peerId = "fresh-peer"
        val sessionKey = generateSessionKey()
        val publicKeyHex = generatePublicKeyHex()

        store.putSessionKey(peerId, sessionKey, publicKeyHex)

        val sessionInfo = store.getSessionKey(peerId)

        Assert.assertNotNull(
            "Fresh session should be valid",
            sessionInfo
        )
    }

    @Test
    fun `cleanup removes expired sessions`() {
        val freshPeerId = "fresh-peer"
        val oldPeerId = "old-peer"

        store.putSessionKey(freshPeerId, generateSessionKey(), generatePublicKeyHex())

        val oldTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
        val prefs = EncryptedSharedPreferences.create(
            context,
            "encrypted_session_keys",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit().apply {
            putString("session_key:$oldPeerId", HexUtil.toHex(generateSessionKey()))
            putString("peer_pubkey:$oldPeerId", generatePublicKeyHex())
            putLong("session_timestamp:$oldPeerId", oldTimestamp)
            apply()
        }

        store.destroy()
        val newStore = EncryptedSessionKeyStore(context)

        newStore.cleanup()

        Assert.assertNull("Old session should be removed", newStore.getSessionKey(oldPeerId))
        Assert.assertNotNull("Fresh session should remain", newStore.getSessionKey(freshPeerId))
    }

    @Test
    fun `getSessionKey loads from persistent storage if not in cache`() {
        val peerId = "cache-test-peer"
        val sessionKey = generateSessionKey()
        val publicKeyHex = generatePublicKeyHex()

        store.putSessionKey(peerId, sessionKey, publicKeyHex)
        store.destroy()

        val newStore = EncryptedSessionKeyStore(context)
        val sessionInfo = newStore.getSessionKey(peerId)

        Assert.assertNotNull("Session should load from storage", sessionInfo)
        Assert.assertArrayEquals(
            "Session key should match",
            sessionKey,
            sessionInfo?.sessionKey
        )
    }

    @Test
    fun `clearSession removes from both cache and storage`() {
        val peerId = "clear-test-peer"
        store.putSessionKey(peerId, generateSessionKey(), generatePublicKeyHex())

        store.clearSession(peerId)

        Assert.assertFalse("Session should not exist in cache", store.hasSession(peerId))

        store.destroy()
        val newStore = EncryptedSessionKeyStore(context)
        Assert.assertFalse("Session should not exist after restart", newStore.hasSession(peerId))
    }

    @Test
    fun `clearAll removes all sessions`() {
        store.putSessionKey("peer1", generateSessionKey(), generatePublicKeyHex())
        store.putSessionKey("peer2", generateSessionKey(), generatePublicKeyHex())
        store.putSessionKey("peer3", generateSessionKey(), generatePublicKeyHex())

        store.clearAll()

        Assert.assertEquals("Cache should be empty", 0, store.size())
        Assert.assertFalse("Peer1 should be cleared", store.hasSession("peer1"))
        Assert.assertFalse("Peer2 should be cleared", store.hasSession("peer2"))
        Assert.assertFalse("Peer3 should be cleared", store.hasSession("peer3"))
    }

    @Test
    fun `empty session key is stored correctly`() {
        val peerId = "empty-key-peer"
        val emptyKey = ByteArray(0)
        val publicKeyHex = generatePublicKeyHex()

        store.putSessionKey(peerId, emptyKey, publicKeyHex)

        val retrieved = store.getSessionKey(peerId)
        Assert.assertNotNull("Empty key should be stored", retrieved)
        Assert.assertArrayEquals(
            "Empty key should match",
            emptyKey,
            retrieved?.sessionKey
        )
    }

    @Test
    fun `empty peerId is handled correctly`() {
        val emptyPeerId = ""
        val sessionKey = generateSessionKey()
        val publicKeyHex = generatePublicKeyHex()

        store.putSessionKey(emptyPeerId, sessionKey, publicKeyHex)

        val retrieved = store.getSessionKey(emptyPeerId)
        Assert.assertNotNull("Session with empty peerId should be stored", retrieved)
    }

    @Test
    fun `special characters in peerId are handled correctly`() {
        val specialPeerId = "peer|with:special@chars#123"
        val sessionKey = generateSessionKey()
        val publicKeyHex = generatePublicKeyHex()

        store.putSessionKey(specialPeerId, sessionKey, publicKeyHex)

        val retrieved = store.getSessionKey(specialPeerId)
        Assert.assertNotNull("Session with special chars should be stored", retrieved)
        Assert.assertArrayEquals(
            "Session key should match",
            sessionKey,
            retrieved?.sessionKey
        )
    }

    @Test
    fun `very long peerId is handled correctly`() {
        val longPeerId = "peer-" + "a".repeat(1000)
        val sessionKey = generateSessionKey()
        val publicKeyHex = generatePublicKeyHex()

        store.putSessionKey(longPeerId, sessionKey, publicKeyHex)

        val retrieved = store.getSessionKey(longPeerId)
        Assert.assertNotNull("Session with long peerId should be stored", retrieved)
    }

    @Test
    fun `invalid hex in storage returns null`() {
        val peerId = "invalid-hex-peer"
        val prefs = EncryptedSharedPreferences.create(
            context,
            "encrypted_session_keys",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit().apply {
            putString("session_key:$peerId", "not-valid-hex!!!")
            putString("peer_pubkey:$peerId", generatePublicKeyHex())
            putLong("session_timestamp:$peerId", System.currentTimeMillis())
            apply()
        }

        store.destroy()
        val newStore = EncryptedSessionKeyStore(context)
        val sessionInfo = newStore.getSessionKey(peerId)

        Assert.assertNull("Invalid hex should return null", sessionInfo)
    }

    @Test
    fun `missing timestamp returns null`() {
        val peerId = "no-timestamp-peer"
        val prefs = EncryptedSharedPreferences.create(
            context,
            "encrypted_session_keys",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit().apply {
            putString("session_key:$peerId", HexUtil.toHex(generateSessionKey()))
            putString("peer_pubkey:$peerId", generatePublicKeyHex())
            apply()
        }

        store.destroy()
        val newStore = EncryptedSessionKeyStore(context)
        val sessionInfo = newStore.getSessionKey(peerId)

        Assert.assertNull("Missing timestamp should return null", sessionInfo)
    }

    @Test
    fun `size returns correct count`() {
        store.putSessionKey("peer1", generateSessionKey(), generatePublicKeyHex())
        store.putSessionKey("peer2", generateSessionKey(), generatePublicKeyHex())
        store.putSessionKey("peer3", generateSessionKey(), generatePublicKeyHex())

        val size = store.size()

        Assert.assertEquals("Size should be 3", 3, size)
    }

    @Test
    fun `updating session overwrites previous value`() {
        val peerId = "update-peer"
        val originalKey = generateSessionKey()
        val originalPubKey = generatePublicKeyHex()

        store.putSessionKey(peerId, originalKey, originalPubKey)

        val newKey = generateSessionKey()
        val newPubKey = generatePublicKeyHex()
        store.putSessionKey(peerId, newKey, newPubKey)

        val retrieved = store.getSessionKey(peerId)
        Assert.assertNotNull("Session should exist", retrieved)
        Assert.assertArrayEquals(
            "New session key should be stored",
            newKey,
            retrieved?.sessionKey
        )
        Assert.assertEquals(
            "New public key should be stored",
            newPubKey,
            retrieved?.peerPublicKeyHex
        )
    }
}
