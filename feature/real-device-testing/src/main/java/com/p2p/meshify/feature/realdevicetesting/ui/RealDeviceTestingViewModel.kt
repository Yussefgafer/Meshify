package com.p2p.meshify.feature.realdevicetesting.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.feature.realdevicetesting.R
import com.p2p.meshify.feature.realdevicetesting.adapter.TestRegistry
import com.p2p.meshify.feature.realdevicetesting.adapter.TransportTestAdapter
import com.p2p.meshify.feature.realdevicetesting.engine.TestDataCleaner
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngine
import com.p2p.meshify.feature.realdevicetesting.engine.TestResultLogger
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario
import com.p2p.meshify.feature.realdevicetesting.model.TestScenarioFactory
import com.p2p.meshify.feature.realdevicetesting.preflight.PreFlightChecker
import com.p2p.meshify.feature.realdevicetesting.preflight.PreFlightResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "RealDeviceTestVM"
private const val SCANNING_TIMEOUT_MS = 15_000L

/**
 * ViewModel for the Real Device Testing screen.
 *
 * Manages the full state machine:
 * INITIAL → RUNNING_PREFLIGHT → PREFLIGHT_DONE → SCANNING_PEERS → PEERS_FOUND →
 *   RUNNING_TESTS → TESTS_DONE
 *
 * Dependencies are provided via a manual factory (not Hilt) because the feature
 * module is not DI-bound. The factory receives:
 * - [context]: Android application context
 * - [chatRepository]: For message/file test runners
 * - [database]: Room database for cleanup
 *
 * The [TestRegistry] singleton provides transport adapters.
 */
class RealDeviceTestingViewModel(
    private val context: Context,
    private val chatRepository: IChatRepository,
    private val database: MeshifyDatabase
) : ViewModel() {

    // ─── State ─────────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<RealDeviceTestingUiState>(
        RealDeviceTestingUiState.Initial
    )
    val uiState = _uiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    private var testRunJob: Job? = null
    private var scanJob: Job? = null

    // ─── Public Actions ────────────────────────────────────────────────────────────

    fun onEvent(event: RealDeviceTestingUiEvent) {
        when (event) {
            is RealDeviceTestingUiEvent.RunPreflight -> runPreflight()
            is RealDeviceTestingUiEvent.ScanPeers -> scanPeers()
            is RealDeviceTestingUiEvent.SelectPeer -> selectPeer(event.peer)
            is RealDeviceTestingUiEvent.DeselectPeer -> deselectPeer()
            is RealDeviceTestingUiEvent.ToggleScenario -> toggleScenario(event.scenarioId)
            is RealDeviceTestingUiEvent.SelectAllScenarios -> selectAllScenarios()
            is RealDeviceTestingUiEvent.DeselectAllScenarios -> deselectAllScenarios()
            is RealDeviceTestingUiEvent.RunAllTests -> runAllTests()
            is RealDeviceTestingUiEvent.RunSelectedTests -> runSelectedTests()
            is RealDeviceTestingUiEvent.CancelTests -> cancelTests()
            is RealDeviceTestingUiEvent.ExportLog -> exportLog()
            is RealDeviceTestingUiEvent.CleanupTestData -> cleanupTestData()
            is RealDeviceTestingUiEvent.RerunFailedTests -> rerunFailedTests()
            is RealDeviceTestingUiEvent.DismissExportStatus -> dismissExportStatus()
            is RealDeviceTestingUiEvent.DismissCleanupStatus -> dismissCleanupStatus()
        }
    }

    // ─── Pre-Flight ────────────────────────────────────────────────────────────────

    private fun runPreflight() {
        viewModelScope.launch {
            _uiState.value = RealDeviceTestingUiState.RunningPreflight()

            val checker = PreFlightChecker(context)
            val result = checker.runAllChecks()

            if (result.allPassed) {
                Logger.i(TAG, "Pre-flight passed — ready to test")
                _uiState.value = RealDeviceTestingUiState.PreFlightDone(
                    preFlightResult = result,
                    scenarios = TestScenarioFactory.createDefaults()
                )
            } else {
                Logger.w(TAG, "Pre-flight failed: ${result.failedChecks.size} check(s)")
                _uiState.value = RealDeviceTestingUiState.PreFlightFailed(result)
            }
        }
    }

    // ─── Peer Discovery ────────────────────────────────────────────────────────────

    private fun scanPeers() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val currentState = _uiState.value
            val scenarios = when (currentState) {
                is RealDeviceTestingUiState.PreFlightDone -> currentState.scenarios
                is RealDeviceTestingUiState.NoPeersFound -> currentState.scenarios
                else -> TestScenarioFactory.createDefaults()
            }
            val preFlightResult = when (currentState) {
                is RealDeviceTestingUiState.PreFlightDone -> currentState.preFlightResult
                is RealDeviceTestingUiState.NoPeersFound -> currentState.preFlightResult
                is RealDeviceTestingUiState.PreFlightFailed -> currentState.preFlightResult
                else -> return@launch
            }

            _uiState.value = RealDeviceTestingUiState.PreFlightDone(
                preFlightResult = preFlightResult,
                isScanning = true,
                scenarios = scenarios
            )

            val allPeers = discoverPeersWithTimeout(SCANNING_TIMEOUT_MS)

            if (allPeers.isEmpty()) {
                _uiState.value = RealDeviceTestingUiState.NoPeersFound(
                    preFlightResult = preFlightResult,
                    scenarios = scenarios
                )
                _snackbarMessage.value = context.getString(R.string.peer_selection_none)
            } else {
                Logger.i(TAG, "Discovered ${allPeers.size} peer(s)")
                val autoSelected = if (allPeers.size == 1) allPeers.first() else null
                _uiState.value = RealDeviceTestingUiState.PreFlightDone(
                    preFlightResult = preFlightResult,
                    peers = allPeers,
                    selectedPeer = autoSelected,
                    isScanning = false,
                    scenarios = scenarios
                )
            }
        }
    }

    private suspend fun discoverPeersWithTimeout(timeoutMs: Long): List<DiscoveredPeer> {
        val availableTransports = TestRegistry.INSTANCE.getAvailableTransports()
        if (availableTransports.isEmpty()) {
            Logger.w(TAG, "No transports available for discovery")
            return emptyList()
        }

        val allPeers = mutableListOf<DiscoveredPeer>()

        for (transport in availableTransports) {
            try {
                transport.initialize()
                val peers = transport.discoverPeers(timeoutMs)
                allPeers.addAll(peers)
                transport.shutdown()
            } catch (e: Exception) {
                Logger.e("Discovery failed on ${transport.transportType.name}", e, TAG)
                try { transport.shutdown() } catch (e: Exception) {
                    Logger.e("Transport shutdown failed", e, TAG)
                }
            }
        }

        return allPeers.distinctBy { it.id }
    }

    // ─── Peer Selection ────────────────────────────────────────────────────────────

    private fun selectPeer(peer: DiscoveredPeer) {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.PreFlightDone) return

        _uiState.value = currentState.copy(
            selectedPeer = if (currentState.selectedPeer == peer) null else peer
        )
    }

    private fun deselectPeer() {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.PreFlightDone) return

        _uiState.value = currentState.copy(selectedPeer = null)
    }

    // ─── Scenario Selection ────────────────────────────────────────────────────────

    private fun toggleScenario(scenarioId: String) {
        val currentState = _uiState.value
        val currentSelectedIds = when (currentState) {
            is RealDeviceTestingUiState.PreFlightDone -> currentState.selectedScenarioIds
            is RealDeviceTestingUiState.NoPeersFound -> currentState.selectedScenarioIds
            else -> return
        }

        val newIds = if (currentSelectedIds.contains(scenarioId)) {
            currentSelectedIds - scenarioId
        } else {
            currentSelectedIds + scenarioId
        }

        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (currentState) {
            is RealDeviceTestingUiState.PreFlightDone ->
                _uiState.value = currentState.copy(selectedScenarioIds = newIds)
            is RealDeviceTestingUiState.NoPeersFound ->
                _uiState.value = currentState.copy(selectedScenarioIds = newIds)
            else -> Unit
        }
    }

    private fun selectAllScenarios() {
        val currentState = _uiState.value
        val scenarios = when (currentState) {
            is RealDeviceTestingUiState.PreFlightDone -> currentState.scenarios
            is RealDeviceTestingUiState.NoPeersFound -> currentState.scenarios
            else -> return
        }

        val allIds = scenarios.map { it.id }.toSet()
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (currentState) {
            is RealDeviceTestingUiState.PreFlightDone ->
                _uiState.value = currentState.copy(selectedScenarioIds = allIds)
            is RealDeviceTestingUiState.NoPeersFound ->
                _uiState.value = currentState.copy(selectedScenarioIds = allIds)
            else -> Unit
        }
    }

    private fun deselectAllScenarios() {
        val currentState = _uiState.value
        when (currentState) {
            is RealDeviceTestingUiState.PreFlightDone ->
                _uiState.value = currentState.copy(selectedScenarioIds = emptySet())
            is RealDeviceTestingUiState.NoPeersFound ->
                _uiState.value = currentState.copy(selectedScenarioIds = emptySet())
            else -> Unit
        }
    }

    // ─── Test Execution ────────────────────────────────────────────────────────────

    private fun runAllTests() {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.PreFlightDone) return

        val targetPeer = currentState.selectedPeer
        if (targetPeer == null) {
            _snackbarMessage.value = context.getString(R.string.error_no_peer_selected)
            return
        }

        val transport = TestRegistry.INSTANCE.getTransport(targetPeer.transportType)
        if (transport == null) {
            _snackbarMessage.value = context.getString(R.string.error_no_transport)
            return
        }

        executeTests(currentState.scenarios, targetPeer, transport)
    }

    private fun runSelectedTests() {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.PreFlightDone) return

        val targetPeer = currentState.selectedPeer
        if (targetPeer == null) {
            _snackbarMessage.value = context.getString(R.string.error_no_peer_selected)
            return
        }

        if (currentState.selectedScenarioIds.isEmpty()) {
            _snackbarMessage.value = context.getString(R.string.peer_selection_select)
            return
        }

        val transport = TestRegistry.INSTANCE.getTransport(targetPeer.transportType)
        if (transport == null) {
            _snackbarMessage.value = context.getString(R.string.error_transport_unavailable, targetPeer.transportType.name)
            return
        }

        val scenariosToRun = currentState.scenarios.filter { it.id in currentState.selectedScenarioIds }
        executeTests(scenariosToRun, targetPeer, transport)
    }

    private fun rerunFailedTests() {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.TestsDone) return

        val failedIds = currentState.results
            .filter { (_, result) ->
                result.status == TestStatus.FAILED ||
                    result.status == TestStatus.TIMEOUT
            }
            .keys

        if (failedIds.isEmpty()) return

        val transport = TestRegistry.INSTANCE.getTransport(currentState.targetPeer.transportType)
        if (transport == null) {
            _snackbarMessage.value = context.getString(R.string.error_no_transport)
            return
        }

        val scenariosToRerun = currentState.scenarios.filter { it.id in failedIds }
        executeTests(scenariosToRerun, currentState.targetPeer, transport)
    }

    private fun executeTests(
        scenarios: List<TestScenario>,
        targetPeer: DiscoveredPeer,
        transport: TransportTestAdapter
    ) {
        testRunJob?.cancel()
        testRunJob = viewModelScope.launch {
            val logger = TestResultLogger(context)
            val dataCleaner = TestDataCleaner(database)
            val engine = TestEngine(chatRepository, database, logger, dataCleaner)

            _uiState.value = RealDeviceTestingUiState.RunningTests(
                scenarios = scenarios,
                currentIndex = 0,
                results = emptyMap(),
                targetPeer = targetPeer
            )

            val results = mutableMapOf<String, TestResult>()

            try {
                transport.initialize()

                engine.runTests(
                    scenarios = scenarios,
                    targetPeer = targetPeer,
                    transport = transport,
                    onResultUpdate = { result ->
                        results[result.scenarioId] = result
                        _uiState.value = RealDeviceTestingUiState.RunningTests(
                            scenarios = scenarios,
                            currentIndex = results.size,
                            results = results.toMap(),
                            targetPeer = targetPeer
                        )
                    }
                )
            } catch (e: Exception) {
                Logger.e("Test execution failed", e, TAG)
                _snackbarMessage.value = context.getString(R.string.cleanup_failure, e.message ?: "Unknown")
            } finally {
                try { transport.shutdown() } catch (e: Exception) {
                    Logger.e("Transport shutdown failed", e, TAG)
                }
            }

            _uiState.value = RealDeviceTestingUiState.TestsDone(
                scenarios = scenarios,
                results = results,
                targetPeer = targetPeer
            )
        }
    }

    private fun cancelTests() {
        testRunJob?.cancel()
        testRunJob = null

        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.RunningTests) return

        // Mark remaining scenarios as timeout
        val completedIds = currentState.results.keys
        val remainingScenarios = currentState.scenarios.filter { it.id !in completedIds }
        val timeoutResults = remainingScenarios.associate { scenario ->
            scenario.id to TestResult(
                scenarioId = scenario.id,
                status = TestStatus.TIMEOUT,
                error = context.getString(R.string.test_cancelled_by_user)
            )
        }

        _uiState.value = RealDeviceTestingUiState.TestsDone(
            scenarios = currentState.scenarios,
            results = currentState.results + timeoutResults,
            targetPeer = currentState.targetPeer
        )

        Logger.i(TAG, "Test run cancelled by user")
    }

    // ─── Export & Cleanup ─────────────────────────────────────────────────────────

    private fun exportLog() {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.TestsDone) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isExporting = true, exportSuccess = null)

            val logger = TestResultLogger(context)
            logger.startSession(
                targetPeer = currentState.targetPeer,
                transportName = currentState.targetPeer.transportType.name
            )
            currentState.results.values.forEach { logger.appendResult(it) }
            val allResults = currentState.scenarios.mapNotNull { scenario ->
                currentState.results[scenario.id]
            }
            logger.appendSummary(allResults, totalDurationMs = 0)

            logger.exportLogFile().onSuccess { file ->
                _uiState.value = _uiState.value.let {
                    if (it is RealDeviceTestingUiState.TestsDone) {
                        it.copy(isExporting = false, exportSuccess = true)
                    } else it
                }
                _snackbarMessage.value = context.getString(R.string.screen_log_exported)
            }.onFailure { e ->
                Logger.e("Export failed", e, TAG)
                _uiState.value = _uiState.value.let {
                    if (it is RealDeviceTestingUiState.TestsDone) {
                        it.copy(isExporting = false, exportSuccess = false)
                    } else it
                }
                _snackbarMessage.value = context.getString(R.string.screen_export_failed)
            }
        }
    }

    private fun cleanupTestData() {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.TestsDone) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isCleaning = true, cleanupSuccess = null)

            val dataCleaner = TestDataCleaner(database)
            dataCleaner.cleanup(currentState.targetPeer.id)
                .onSuccess {
                    _uiState.value = _uiState.value.let {
                        if (it is RealDeviceTestingUiState.TestsDone) {
                            it.copy(isCleaning = false, cleanupSuccess = true)
                        } else it
                    }
                    _snackbarMessage.value = context.getString(R.string.cleanup_success)
                }
                .onFailure { e ->
                    Logger.e("Cleanup failed", e, TAG)
                    _uiState.value = _uiState.value.let {
                        if (it is RealDeviceTestingUiState.TestsDone) {
                            it.copy(isCleaning = false, cleanupSuccess = false, cleanupError = e.message)
                        } else it
                    }
                    _snackbarMessage.value = context.getString(R.string.cleanup_failure, e.message ?: "Unknown")
                }
        }
    }

    private fun dismissExportStatus() {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.TestsDone) return
        _uiState.value = currentState.copy(exportSuccess = null)
    }

    private fun dismissCleanupStatus() {
        val currentState = _uiState.value
        if (currentState !is RealDeviceTestingUiState.TestsDone) return
        _uiState.value = currentState.copy(cleanupSuccess = null)
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        testRunJob?.cancel()
        scanJob?.cancel()

        // Cleanup test data from DB when ViewModel is destroyed
        val currentState = _uiState.value
        if (currentState is RealDeviceTestingUiState.TestsDone) {
            viewModelScope.launch {
                val dataCleaner = TestDataCleaner(database)
                dataCleaner.cleanup(currentState.targetPeer.id)
                    .onSuccess { Logger.i(TAG, "Auto-cleaned test data on ViewModel cleared") }
                    .onFailure { e -> Logger.e("Auto-cleanup failed", e, TAG) }
            }
        }
    }

    // ─── Factory ───────────────────────────────────────────────────────────────────

    companion object {
        fun factory(
            context: Context,
            chatRepository: IChatRepository,
            database: MeshifyDatabase
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RealDeviceTestingViewModel(
                    context = context,
                    chatRepository = chatRepository,
                    database = database
                )
            }
        }
    }
}
