package com.p2p.meshify.core.domain.model

/**
 * Sealed class representing upload progress states.
 * Used to track file upload progress in the UI.
 */
sealed class UploadProgress {
    /** Upload in progress with percentage (0-100) */
    data class Uploading(val percent: Int) : UploadProgress()
    
    /** Upload completed successfully */
    data class Success(val messageId: String) : UploadProgress()
    
    /** Upload failed with error message */
    data class Error(val messageId: String, val message: String) : UploadProgress()
}
