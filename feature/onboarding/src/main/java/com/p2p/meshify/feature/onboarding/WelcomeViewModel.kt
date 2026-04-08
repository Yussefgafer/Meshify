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
 * Manages page navigation, state, and user interactions.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val pages = OnboardingPages.getPages()

    /**
     * Navigate to the next page.
     * Automatically handles the last page transition.
     */
    fun nextPage() {
        val currentState = _uiState.value
        if (currentState.currentPage < currentState.totalPages - 1 && !currentState.isAnimating) {
            _uiState.update {
                it.copy(
                    currentPage = it.currentPage + 1,
                    isLastPage = it.currentPage + 1 == it.totalPages - 1,
                    isAnimating = true
                )
            }
            
            // Reset animation flag after animation completes
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                _uiState.update { it.copy(isAnimating = false) }
            }
        }
    }

    /**
     * Navigate to the previous page.
     */
    fun previousPage() {
        val currentState = _uiState.value
        if (currentState.currentPage > 0 && !currentState.isAnimating) {
            _uiState.update {
                it.copy(
                    currentPage = it.currentPage - 1,
                    isLastPage = it.currentPage - 1 == it.totalPages - 1,
                    isAnimating = true
                )
            }
            
            // Reset animation flag after animation completes
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                _uiState.update { it.copy(isAnimating = false) }
            }
        }
    }

    /**
     * Navigate to a specific page.
     * Used by the page indicator dots.
     */
    fun goToPage(pageIndex: Int) {
        val currentState = _uiState.value
        if (pageIndex in 0 until currentState.totalPages && 
            pageIndex != currentState.currentPage && 
            !currentState.isAnimating) {
            _uiState.update {
                it.copy(
                    currentPage = pageIndex,
                    isLastPage = pageIndex == it.totalPages - 1,
                    isAnimating = true
                )
            }
            
            // Reset animation flag after animation completes
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                _uiState.update { it.copy(isAnimating = false) }
            }
        }
    }

    /**
     * Skip onboarding and go directly to the last page.
     */
    fun skipOnboarding() {
        val currentState = _uiState.value
        if (!currentState.isAnimating) {
            _uiState.update {
                it.copy(
                    currentPage = it.totalPages - 1,
                    isLastPage = true,
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
     * Get the current page info.
     */
    fun getCurrentPageInfo(): OnboardingPageInfo {
        return pages[_uiState.value.currentPage]
    }

    /**
     * Get all pages for reference.
     */
    fun getAllPages(): List<OnboardingPageInfo> = pages
}
