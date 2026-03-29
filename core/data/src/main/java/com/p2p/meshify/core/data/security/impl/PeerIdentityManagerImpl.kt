package com.p2p.meshify.core.data.security.impl

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.p2p.meshify.core.common.util.HexUtil
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature

/**
 * Manages peer identity using Android Keystore.
 * Generates EC keypair (secp256r1) in hardware-backed Keystore (TEE or StrongBox).
 * 
 * peerId = Base64(SHA-256(DER-encoded public key))
 * 
 * This provides:
 * - Self-sovereign identity (no central authority)
 * - Stable peer ID across sessions
 * - Cryptographic proof of identity via ECDSA signatures
 */
class PeerIdentityManagerImpl : PeerIdentityRepository {
    
    companion object {
        private const val IDENTITY_KEY_ALIAS = "meshify_peer_identity"
        private const val PROVIDER = "AndroidKeyStore"
    }
    
    override suspend fun initializeIdentity(): String = withContext(Dispatchers.IO) {
        if (keyExists(IDENTITY_KEY_ALIAS)) {
            return@withContext derivePeerId(getPublicKeyBytesInternal())
        }

        // Generate EC keypair in Android Keystore
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        kpg.initialize(
            KeyGenParameterSpec.Builder(
                IDENTITY_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        kpg.generateKeyPair()

        derivePeerId(getPublicKeyBytesInternal())
    }
    
    override suspend fun getPeerId(): String = withContext(Dispatchers.IO) {
        derivePeerId(getPublicKeyBytesInternal())
    }
    
    override suspend fun getPublicKeyBytes(): ByteArray = withContext(Dispatchers.IO) {
        getPublicKeyBytesInternal()
    }
    
    override suspend fun getPublicKeyHex(): String = withContext(Dispatchers.IO) {
        HexUtil.toHex(getPublicKeyBytesInternal())
    }
    
    override suspend fun signChallenge(challenge: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val privateKey = ks.getKey(IDENTITY_KEY_ALIAS, null) as java.security.PrivateKey
        
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(challenge)
        signature.sign()
    }
    
    override suspend fun verifySignature(
        publicKeyBytes: ByteArray,
        challenge: ByteArray,
        signatureBytes: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pubKey = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))
            
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(pubKey)
            signature.update(challenge)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun resetIdentity() = withContext(Dispatchers.IO) {
        deleteKey(IDENTITY_KEY_ALIAS)
    }
    
    // ============ Private Helper Methods ============
    
    private fun derivePeerId(publicKeyBytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
    
    private fun getPublicKeyBytesInternal(): ByteArray {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return ks.getCertificate(IDENTITY_KEY_ALIAS).publicKey.encoded
    }
    
    private fun keyExists(alias: String): Boolean {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return ks.containsAlias(alias)
    }
    
    private fun deleteKey(alias: String) {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }
}
