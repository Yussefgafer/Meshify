package com.p2p.meshify.core.crypto

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt

class TinkMessageCipher(
    private val deviceKeysetManager: DeviceKeysetManager,
    private val peerPublicKeyStore: PeerPublicKeyStore
) : MessageCipher {

    private val emptyContextInfo = ByteArray(0)

    override suspend fun encryptFor(peerId: String, plaintext: ByteArray): EncryptResult {
        if (peerPublicKeyStore.isKnownUnsupported(peerId)) {
            return EncryptResult.PeerDoesNotSupportEncryption
        }

        val recipientPublicKey = peerPublicKeyStore.awaitKey(peerId)
            ?: return if (peerPublicKeyStore.isKnownUnsupported(peerId)) {
                EncryptResult.PeerDoesNotSupportEncryption
            } else {
                EncryptResult.PublicKeyUnavailable
            }

        return try {
            val hybridEncrypt = recipientPublicKey.getPrimitive(HybridEncrypt::class.java)
            val ciphertext = hybridEncrypt.encrypt(plaintext, emptyContextInfo)
            EncryptResult.Success(ciphertext)
        } catch (e: Throwable) {
            throw CryptoException("Failed to encrypt message for peer $peerId", e)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        return try {
            val myPrivateKey = deviceKeysetManager.getPrivateKeysetHandle()
            val hybridDecrypt = myPrivateKey.getPrimitive(HybridDecrypt::class.java)
            hybridDecrypt.decrypt(ciphertext, emptyContextInfo)
        } catch (e: Throwable) {
            throw CryptoDecryptionException("Failed to decrypt message", e)
        }
    }

    override fun getPublicKeyBase64(): String = deviceKeysetManager.getPublicKeyBase64()
}
