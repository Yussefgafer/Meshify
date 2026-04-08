package com.p2p.meshify.domain.usecase

/**
 * Validates message send requests before they reach the repository layer.
 *
 * Extracted from ChatInputViewModel.sendMessage() to enable pure unit testing
 * of validation rules without Android/Compose dependencies.
 *
 * Validation rules:
 * 1. Text must not be blank (whitespace-only counts as blank)
 * 2. Attachments may supplement empty text, but at least one content source required
 * 3. Debouncing prevents rapid duplicate sends (500ms window)
 * 4. Reply-to-self is invalid (message author cannot reply to own message)
 * 5. Message text must not exceed max size (4096 characters)
 */
class SendMessageValidation {

    companion object {
        private const val SEND_DEBOUNCE_MS = 500L
        const val MAX_MESSAGE_LENGTH = 4096
    }

    /**
     * Result of validation.
     * @property isValid whether the message can be sent
     * @property errorCode null if valid, otherwise a specific error code
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorCode: ErrorCode? = null
    )

    enum class ErrorCode {
        EMPTY_CONTENT,
        SEND_DEBOUNCED,
        REPLY_TO_SELF,
        MESSAGE_TOO_LONG
    }

    /**
     * Validates whether a message can be sent.
     *
     * @param text The message text (may be blank if attachments exist)
     * @param hasAttachments Whether the message includes file/image attachments
     * @param lastSendTimeMillis Timestamp of last successful send (0 if none)
     * @param currentTimeMillis Current timestamp
     * @param isReplyToSelf Whether the reply target is the sender's own message
     * @return ValidationResult with error code if invalid
     */
    fun validate(
        text: String,
        hasAttachments: Boolean = false,
        lastSendTimeMillis: Long = 0,
        currentTimeMillis: Long = System.currentTimeMillis(),
        isReplyToSelf: Boolean = false
    ): ValidationResult {
        // Rule 1: Content check — text or attachments required
        val hasText = text.isNotBlank()
        if (!hasText && !hasAttachments) {
            return ValidationResult(isValid = false, errorCode = ErrorCode.EMPTY_CONTENT)
        }

        // Rule 2: Message length check
        if (text.length > MAX_MESSAGE_LENGTH) {
            return ValidationResult(isValid = false, errorCode = ErrorCode.MESSAGE_TOO_LONG)
        }

        // Rule 3: Reply-to-self check
        if (isReplyToSelf) {
            return ValidationResult(isValid = false, errorCode = ErrorCode.REPLY_TO_SELF)
        }

        // Rule 4: Debounce check
        val elapsed = currentTimeMillis - lastSendTimeMillis
        if (lastSendTimeMillis > 0 && elapsed < SEND_DEBOUNCE_MS) {
            return ValidationResult(isValid = false, errorCode = ErrorCode.SEND_DEBOUNCED)
        }

        return ValidationResult(isValid = true)
    }
}
