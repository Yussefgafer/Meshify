package com.p2p.meshify.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun nextPage() {
        val currentState = _uiState.value
        if (currentState.currentPage < currentState.totalPages - 1 && !currentState.isAnimating) {
            _uiState.update { it.copy(currentPage = it.currentPage + 1, isAnimating = true) }
            viewModelScope.launch {
                delay(300)
                _uiState.update { it.copy(isAnimating = false) }
            }
        }
    }

    fun goToPage(pageIndex: Int) {
        val currentState = _uiState.value
        if (pageIndex in 0 until currentState.totalPages && pageIndex != currentState.currentPage && !currentState.isAnimating) {
            _uiState.update { it.copy(currentPage = pageIndex, isAnimating = true) }
            viewModelScope.launch {
                delay(300)
                _uiState.update { it.copy(isAnimating = false) }
            }
        }
    }

    fun toggleLangMenu() {
        _uiState.update { it.copy(isLangMenuOpen = !it.isLangMenuOpen) }
    }

    fun startPermissionFlow() {
        _uiState.update { it.copy(isPermissionFlowActive = true, isAnimating = true) }
        viewModelScope.launch {
            delay(200)
            _uiState.update { it.copy(isAnimating = false) }
        }
    }

    fun showSummary() {
        _uiState.update { it.copy(isPermissionFlowActive = false, isSummaryVisible = true) }
    }

    fun dismissSummary() {
        _uiState.update { it.copy(isSummaryVisible = false) }
    }
}
