package com.p2p.meshify.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the onboarding flow.
 * Manages page navigation, language state, and permission tracking.
 *
 * Note: This ViewModel does NOT request Android permissions directly.
 * Permission requests are handled by the Activity via callbacks.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    /**
     * Navigate to the next page.
     */
    fun nextPage() {
        val currentState = _uiState.value
        if (currentState.currentPage < currentState.totalPages - 1 && !currentState.isAnimating) {
            _uiState.update {
                it.copy(
                    currentPage = it.currentPage + 1,
                    isAnimating = true
                )
            }
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                _uiState.update { it.copy(isAnimating = false) }
            }
        }
    }

    /**
     * Navigate to a specific page.
     */
    fun goToPage(pageIndex: Int) {
        val currentState = _uiState.value
        if (pageIndex in 0 until currentState.totalPages &&
            pageIndex != currentState.currentPage &&
            !currentState.isAnimating) {
            _uiState.update {
                it.copy(
                    currentPage = pageIndex,
                    isAnimating = true
                )
            }
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                _uiState.update { it.copy(isAnimating = false) }
            }
        }
    }

    /**
     * Toggle the language menu.
     */
    fun toggleLangMenu() {
        _uiState.update { it.copy(isLangMenuOpen = !it.isLangMenuOpen) }
    }

    /**
     * Start the permission flow (transition to showing permission cards).
     */
    fun startPermissionFlow() {
        _uiState.update {
            it.copy(
                isPermissionFlowActive = true,
                isAnimating = true
            )
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            _uiState.update { it.copy(isAnimating = false) }
        }
    }

    /**
     * Show the summary dialog after permissions are processed.
     */
    fun showSummary() {
        _uiState.update {
            it.copy(
                isPermissionFlowActive = false,
                isSummaryVisible = true
            )
        }
    }

    /**
     * Dismiss the summary dialog.
     */
    fun dismissSummary() {
        _uiState.update { it.copy(isSummaryVisible = false) }
    }
}

/**
 * Sealed class for illustration types used in onboarding.
 */
sealed class IllustrationType {
    object MeshNetwork : IllustrationType()
    object DiscoveryScreen : IllustrationType()
    object ConnectScreen : IllustrationType()
    object ChatScreen : IllustrationType()
    object ShieldCheck : IllustrationType()
}
