package com.p2p.meshify.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.domain.security.model.OobVerificationData
import com.p2p.meshify.domain.security.model.OobVerificationMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for OOB verification flow.
 *
 * @property isLoading Whether verification data is being generated
 * @property myFingerprint SHA-256 hash of local public key (hex-encoded)
 * @property mySas Local Short Authentication String for comparison
 * @property peerSas Peer's SAS code (null until received)
 * @property isVerified Whether the peer identity has been confirmed
 * @property verificationError Error message if verification failed
 * @property selectedMethod Currently selected verification method
 */
data class OobVerificationUiState(
    val isLoading: Boolean = false,
    val myFingerprint: String = "",
    val mySas: String = "",
    val peerSas: String? = null,
    val isVerified: Boolean = false,
    val verificationError: String? = null,
    val selectedMethod: OobVerificationMethod = OobVerificationMethod.QR
)

/**
 * ViewModel for Out-Of-Band (OOB) identity verification.
 *
 * Manages the OOB verification flow where users verify each other's identity
 * through QR code scanning or SAS code comparison to prevent MITM attacks.
 *
 * Flow:
 * 1. Generate local verification data (fingerprint, SAS, QR data)
 * 2. Display fingerprint as QR code or SAS for manual comparison
 * 3. Receive peer's verification data
 * 4. User confirms match or reports mismatch
 * 5. If verified, the session is trusted; if not, connection may be compromised
 *
 * NOTE: Currently uses mock fingerprint data. Will integrate with EcdhSessionManager
 * when the ECDH key exchange module is ready.
 */
@HiltViewModel
class OobVerificationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(OobVerificationUiState())
    val uiState: StateFlow<OobVerificationUiState> = _uiState.asStateFlow()

    init {
        generateVerificationData()
    }

    /**
     * Generate local verification data (fingerprint, SAS).
     *
     * Note: Currently uses a mock fingerprint for UI demonstration.
     * When EcdhSessionManager integration is complete, this method
     * will derive the fingerprint from the actual ECDH public key.
     */
    private fun generateVerificationData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, verificationError = null) }

            try {
                val mockFingerprint = generateMockFingerprint()
                val sas = OobVerificationData.generateSas(mockFingerprint)

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        myFingerprint = mockFingerprint,
                        mySas = sas
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        verificationError = "Failed to generate verification data: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Select a verification method (QR, SAS, or NFC).
     */
    fun selectMethod(method: OobVerificationMethod) {
        _uiState.update { it.copy(selectedMethod = method) }
    }

    /**
     * Receive peer's SAS code for comparison.
     */
    fun receivePeerSas(peerSas: String) {
        _uiState.update { it.copy(peerSas = peerSas, verificationError = null) }
    }

    /**
     * Verify that SAS codes match and update verification state.
     *
     * @return true if codes match, false if they differ (possible MITM)
     */
    fun verifySasMatch(): Boolean {
        val state = _uiState.value
        val isMatch = OobVerificationData.verifySas(state.mySas, state.peerSas ?: "")

        if (isMatch) {
            _uiState.update { state ->
                state.copy(
                    isVerified = true,
                    verificationError = null
                )
            }
        } else {
            _uiState.update { state ->
                state.copy(
                    verificationError = "SAS codes don't match! Possible MITM attack."
                )
            }
        }

        return isMatch
    }

    /**
     * Report SAS mismatch — possible MITM attack.
     */
    fun reportSasMismatch() {
        _uiState.update { state ->
            state.copy(
                isVerified = false,
                verificationError = "SAS codes differ. Do NOT trust this connection."
            )
        }
    }

    /**
     * Reset verification state to start fresh.
     */
    fun reset() {
        _uiState.update { OobVerificationUiState() }
        generateVerificationData()
    }

    /**
     * Clear any error messages without resetting verification state.
     */
    fun clearError() {
        _uiState.update { it.copy(verificationError = null) }
    }

    /**
     * Generate a mock fingerprint for UI demonstration.
     *
     * Produces a random 64-character hex string (SHA-256 length).
     * In production, replace with actual ECDH public key hash.
     */
    private fun generateMockFingerprint(): String {
        val hexChars = "0123456789abcdef"
        return (1..64).map { hexChars.random() }.joinToString("")
    }
}
