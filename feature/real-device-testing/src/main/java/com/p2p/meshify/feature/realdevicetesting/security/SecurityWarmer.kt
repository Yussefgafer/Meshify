package com.p2p.meshify.feature.realdevicetesting.security

import com.p2p.meshify.core.common.util.HexUtil
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.security.util.EcdhSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair

/**
 * Result of the ECDH security self-test.
 * @property success Whether the self-test passed
 * @property sessionKeyMatch Whether both derived session keys match
 * @property aliceSessionKeyHex Hex representation of Alice's derived session key
 * @property bobSessionKeyHex Hex representation of Bob's derived session key
 * @property error Error message if test failed
 * @property durationMs Time taken to complete the test
 */
data class SecurityWarmupResult(
    val success: Boolean,
    val sessionKeyMatch: Boolean,
    val aliceSessionKeyHex: String,
    val bobSessionKeyHex: String,
    val error: String?,
    val durationMs: Long
) {
    companion object {
        fun success(
            sessionKeyMatch: Boolean,
            aliceKeyHex: String,
            bobKeyHex: String,
            durationMs: Long
        ) = SecurityWarmupResult(
            success = true,
            sessionKeyMatch = sessionKeyMatch,
            aliceSessionKeyHex = aliceKeyHex,
            bobSessionKeyHex = bobKeyHex,
            error = null,
            durationMs = durationMs
        )

        fun failure(error: String, durationMs: Long) = SecurityWarmupResult(
            success = false,
            sessionKeyMatch = false,
            aliceSessionKeyHex = "",
            bobSessionKeyHex = "",
            error = error,
            durationMs = durationMs
        )
    }
}

/**
 * Warms up the cryptographic subsystem by performing a full ECDH key exchange self-test.
 *
 * This simulates a complete handshake between two virtual parties (Alice and Bob)
 * on the same device to verify:
 * 1. ECDH keypair generation works on this device
 * 2. HKDF-SHA256 key derivation produces valid 32-byte keys
 * 3. Both parties derive the SAME session key (critical for encryption/decryption)
 * 4. Android crypto provider is functional
 *
 * This test uses pure Kotlin (no Android dependencies) via EcdhSessionManager from :core:domain.
 * It does NOT modify any persistent state or touch the real session key store.
 */
class SecurityWarmer {

    private val ecdhSessionManager = EcdhSessionManager()

    /**
     * Run the ECDH self-test.
     *
     * Simulates a full V2 handshake:
     * 1. Alice generates ephemeral keypair + nonce
     * 2. Bob generates ephemeral keypair + nonce
     * 3. Bob derives session key using Alice's ephemeral pub key + nonce
     * 4. Alice finalizes session key using Bob's ephemeral pub key + nonce
     * 5. Verify both session keys match
     *
     * @return SecurityWarmupResult with test outcome
     */
    suspend fun warmUpCrypto(): SecurityWarmupResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            Logger.i("Starting ECDH security self-test...", tag = "SecurityWarmer")

            // Step 1: Alice creates ephemeral session (keypair + nonce)
            val aliceSession = ecdhSessionManager.createEphemeralSession()
            Logger.d("Alice: ephemeral pub key generated (${aliceSession.ephemeralPublicKey.size} bytes)", tag = "SecurityWarmer")

            // Step 2: Bob generates his own ephemeral keypair
            val bobKeyPair: KeyPair = ecdhSessionManager.generateEphemeralKeypair()
            val bobNonce = ecdhSessionManager.generateNonce()
            Logger.d("Bob: ephemeral pub key generated (${bobKeyPair.public.encoded.size} bytes)", tag = "SecurityWarmer")

            // Step 3: Bob derives session key from Alice's ephemeral pub key
            val bobSessionKey = ecdhSessionManager.deriveSessionKeyFromPeer(
                peerEphemeralPubKeyBytes = aliceSession.ephemeralPublicKey,
                peerNonce = aliceSession.sessionNonce,
                myEphemeralKeyPair = bobKeyPair,
                myNonce = bobNonce
            )
            Logger.d("Bob: session key derived (${bobSessionKey.size} bytes)", tag = "SecurityWarmer")

            // Step 4: Alice finalizes session key from Bob's ephemeral pub key
            val aliceSessionKey = ecdhSessionManager.finalizeSessionKey(
                peerEphemeralPubKeyBytes = bobKeyPair.public.encoded,
                peerNonce = bobNonce,
                myEphemeralPrivateKey = aliceSession.ephemeralPrivateKey,
                myNonce = aliceSession.sessionNonce
            )
            Logger.d("Alice: session key finalized (${aliceSessionKey.size} bytes)", tag = "SecurityWarmer")

            // Step 5: Verify both session keys match
            val sessionKeyMatch = aliceSessionKey.contentEquals(bobSessionKey)
            val aliceKeyHex = HexUtil.toHex(aliceSessionKey)
            val bobKeyHex = HexUtil.toHex(bobSessionKey)

            val duration = System.currentTimeMillis() - startTime

            if (sessionKeyMatch) {
                Logger.i(
                    "ECDH self-test PASSED (${duration}ms) — " +
                        "both parties derived same session key",
                    tag = "SecurityWarmer"
                )
                SecurityWarmupResult.success(
                    sessionKeyMatch = true,
                    aliceKeyHex = aliceKeyHex,
                    bobKeyHex = bobKeyHex,
                    durationMs = duration
                )
            } else {
                val errorMsg = "Session key MISMATCH: Alice=$aliceKeyHex, Bob=$bobKeyHex"
                Logger.e("ECDH self-test FAILED: $errorMsg", tag = "SecurityWarmer")
                SecurityWarmupResult.failure(
                    error = errorMsg,
                    durationMs = duration
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val errorMsg = "ECDH self-test exception: ${e.message}"
            Logger.e("ECDH self-test FAILED with exception: ${e.message}", e, tag = "SecurityWarmer")
            SecurityWarmupResult.failure(
                error = errorMsg,
                durationMs = duration
            )
        }
    }
}
