package com.p2p.meshify.domain.security.model

/**
 * Security events that should be surfaced to the user.
 *
 * After Phase 4 simplification, only message send failures remain.
 * Encryption-related events (DecryptionFailed, TofuViolation, SessionExpired)
 * have been removed since all messages are now sent as plaintext.
 */
data class SecurityEvent(
    val type: EventType,
    val messageId: String = "",
    val peerId: String = "",
    val reason: String = ""
) {
    enum class EventType {
        MESSAGE_SEND_FAILED
    }

    companion object {
        fun messageSendFailed(messageId: String, peerId: String, reason: String): SecurityEvent {
            return SecurityEvent(
                type = EventType.MESSAGE_SEND_FAILED,
                messageId = messageId,
                peerId = peerId,
                reason = reason
            )
        }
    }
}
