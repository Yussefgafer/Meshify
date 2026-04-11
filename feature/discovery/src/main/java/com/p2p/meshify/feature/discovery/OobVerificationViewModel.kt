package com.p2p.meshify.feature.discovery

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.util.Logger
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
 * @property myPeerId Local device peer ID for out-of-band comparison
 * @property peerPeerId Peer's device ID (null until received)
 * @property isVerified Whether the peer identity has been confirmed
 * @property verificationError String resource ID if verification failed
 * @property selectedMethod Currently selected verification method
 */
data class OobVerificationUiState(
    val isLoading: Boolean = false,
    val myPeerId: String = "",
    val peerPeerId: String? = null,
    val isVerified: Boolean = false,
    @field:StringRes val verificationError: Int? = null,
    val selectedMethod: OobVerificationMethod = OobVerificationMethod.QR
)

/**
 * ViewModel for Out-Of-Band (OOB) identity verification.
 *
 * Manages the OOB verification flow where users verify each other's identity
 * by comparing peer IDs to ensure they are connecting to the intended device.
 *
 * Flow:
 * 1. Display local peer ID as QR code or text for manual comparison
 * 2. Receive peer's verification data (peer ID)
 * 3. User confirms match or reports mismatch
 * 4. If verified, the peer is trusted; if not, connection may be compromised
 */
@HiltViewModel
class OobVerificationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(OobVerificationUiState())
    val uiState: StateFlow<OobVerificationUiState> = _uiState.asStateFlow()

    init {
        generateVerificationData()
    }

    /**
     * Generate local verification data (peer ID).
     */
    private fun generateVerificationData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, verificationError = null) }

            try {
                // Generate a deterministic peer ID for demonstration.
                // In production, inject SimplePeerIdProvider and use getDeviceId().
                val peerId = "PEER-${generateShortId()}"

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        myPeerId = peerId
                    )
                }
            } catch (e: Exception) {
                Logger.e("OobVerificationViewModel -> Failed to generate verification data", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        verificationError = R.string.oob_verification_error_generic
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
     * Receive peer's peer ID for comparison.
     */
    fun receivePeerId(peerId: String) {
        _uiState.update { it.copy(peerPeerId = peerId, verificationError = null) }
    }

    /**
     * Verify that peer IDs match and update verification state.
     *
     * @return true if IDs match, false if they differ (possible MITM)
     */
    fun verifyIdsMatch(): Boolean {
        val state = _uiState.value
        val isMatch = state.myPeerId.equals(state.peerPeerId ?: "", ignoreCase = true)

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
                    verificationError = R.string.oob_peer_ids_mismatch_mitm
                )
            }
        }

        return isMatch
    }

    /**
     * Report peer ID mismatch — possible MITM attack.
     */
    fun reportMismatch() {
        _uiState.update { state ->
            state.copy(
                isVerified = false,
                verificationError = R.string.oob_peer_ids_differ_distrust
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
     * Generate a short random ID for peer identification.
     */
    private fun generateShortId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}
