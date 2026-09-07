package com.p2p.meshify.core.crypto

open class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

class CryptoDecryptionException(message: String, cause: Throwable? = null) : CryptoException(message, cause)

/**
 * Thrown when a peer's handshake explicitly signaled it does not support
 * encryption. The crypto contract is that the caller MUST NOT send
 * automatically in this case — the user must explicitly opt in to send the
 * message unencrypted.
 */
class PeerUnsupportedEncryptionException(
    val peerId: String,
    message: String = "Peer $peerId does not support encryption"
) : CryptoException(message)

/**
 * Thrown when the peer's public key is not yet available (handshake hasn't
 * arrived or is in flight) and the cipher can't encrypt. The crypto contract
 * is that the message MUST NOT be silently queued as plaintext — the UI
 * surfaces a visible "Encryption key pending — Retry" affordance instead so
 * the user knows their message didn't leave the device.
 */
class PeerPublicKeyUnavailableException(
    val peerId: String,
    message: String = "Encryption key for peer $peerId is not yet available"
) : CryptoException(message)
