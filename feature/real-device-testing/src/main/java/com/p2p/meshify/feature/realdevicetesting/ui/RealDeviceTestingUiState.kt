package com.p2p.meshify.feature.realdevicetesting.ui

import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario
import com.p2p.meshify.feature.realdevicetesting.preflight.PreFlightResult

/**
 * Sealed UI state for the Real Device Testing screen.
 *
 * State machine transitions:
 * INITIAL → RUNNING_PREFLIGHT → PREFLIGHT_DONE → SCANNING_PEERS → PEERS_FOUND →
 *   RUNNING_TESTS → TESTS_DONE
 *   → PREFLIGHT_FAILED (from RUNNING_PREFLIGHT)
 *   → NO_PEERS_FOUND (from SCANNING_PEERS)
 *
 * From TESTS_DONE: → RUNNING_TESTS (re-run) or back to TESTS_DONE.
 */
sealed interface RealDeviceTestingUiState {
    data object Initial : RealDeviceTestingUiState

    data class RunningPreflight(
        val elapsedMs: Long = 0L
    ) : RealDeviceTestingUiState

    data class PreFlightDone(
        val preFlightResult: PreFlightResult,
        val peers: List<DiscoveredPeer> = emptyList(),
        val selectedPeer: DiscoveredPeer? = null,
        val isScanning: Boolean = false,
        val scenarios: List<TestScenario> = emptyList(),
        val selectedScenarioIds: Set<String> = scenarios.map { it.id }.toSet(),
        val results: Map<String, TestResult> = emptyMap()
    ) : RealDeviceTestingUiState

    data class PreFlightFailed(
        val preFlightResult: PreFlightResult
    ) : RealDeviceTestingUiState

    data class NoPeersFound(
        val preFlightResult: PreFlightResult,
        val scenarios: List<TestScenario> = emptyList(),
        val selectedScenarioIds: Set<String> = scenarios.map { it.id }.toSet()
    ) : RealDeviceTestingUiState

    data class RunningTests(
        val scenarios: List<TestScenario>,
        val currentIndex: Int = 0,
        val results: Map<String, TestResult> = emptyMap(),
        val targetPeer: DiscoveredPeer,
        val isCancelling: Boolean = false
    ) : RealDeviceTestingUiState

    data class TestsDone(
        val scenarios: List<TestScenario>,
        val results: Map<String, TestResult>,
        val targetPeer: DiscoveredPeer,
        val isExporting: Boolean = false,
        val isCleaning: Boolean = false,
        val exportSuccess: Boolean? = null,
        val cleanupSuccess: Boolean? = null,
        val cleanupError: String? = null
    ) : RealDeviceTestingUiState

    /**
     * Convenience: computed progress fraction for running tests state.
     */
    val progressFraction: Float
        get() = when (this) {
            is RunningTests -> if (scenarios.isEmpty()) 0f else currentIndex.toFloat() / scenarios.size
            else -> 0f
        }
}

/**
 * Screen-level event actions the ViewModel exposes to the UI.
 */
sealed interface RealDeviceTestingUiEvent {
    data object RunPreflight : RealDeviceTestingUiEvent
    data object ScanPeers : RealDeviceTestingUiEvent
    data class SelectPeer(val peer: DiscoveredPeer) : RealDeviceTestingUiEvent
    data object DeselectPeer : RealDeviceTestingUiEvent
    data class ToggleScenario(val scenarioId: String) : RealDeviceTestingUiEvent
    data object SelectAllScenarios : RealDeviceTestingUiEvent
    data object DeselectAllScenarios : RealDeviceTestingUiEvent
    data object RunAllTests : RealDeviceTestingUiEvent
    data object RunSelectedTests : RealDeviceTestingUiEvent
    data object CancelTests : RealDeviceTestingUiEvent
    data object ExportLog : RealDeviceTestingUiEvent
    data object CleanupTestData : RealDeviceTestingUiEvent
    data object RerunFailedTests : RealDeviceTestingUiEvent
    data object DismissExportStatus : RealDeviceTestingUiEvent
    data object DismissCleanupStatus : RealDeviceTestingUiEvent
}
