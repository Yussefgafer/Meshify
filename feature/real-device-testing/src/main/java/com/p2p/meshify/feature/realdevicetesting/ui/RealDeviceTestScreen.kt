package com.p2p.meshify.feature.realdevicetesting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import com.p2p.meshify.feature.realdevicetesting.R
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealDeviceTestScreen(
    viewModel: RealDeviceTestingViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.real_device_testing_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.screen_back)
                        )
                    }
                },
                actions = {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(MeshifyDesignSystem.Spacing.Md),
                    verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Lg)
                ) {
                    when (val state = uiState) {
                        is RealDeviceTestingUiState.Initial -> {
                            InitialStateContent(onRunPreflight = {
                                viewModel.onEvent(RealDeviceTestingUiEvent.RunPreflight)
                            })
                        }

                        is RealDeviceTestingUiState.RunningPreflight -> {
                            RunningPreflightContent(state)
                        }

                        is RealDeviceTestingUiState.PreFlightDone -> {
                            PreFlightDoneContent(
                                state = state,
                                onScanPeers = { viewModel.onEvent(RealDeviceTestingUiEvent.ScanPeers) },
                                onSelectPeer = { viewModel.onEvent(RealDeviceTestingUiEvent.SelectPeer(it)) },
                                onToggleScenario = { viewModel.onEvent(RealDeviceTestingUiEvent.ToggleScenario(it)) },
                                onSelectAll = { viewModel.onEvent(RealDeviceTestingUiEvent.SelectAllScenarios) },
                                onDeselectAll = { viewModel.onEvent(RealDeviceTestingUiEvent.DeselectAllScenarios) },
                                onRunAllTests = { viewModel.onEvent(RealDeviceTestingUiEvent.RunAllTests) },
                                onRunSelectedTests = { viewModel.onEvent(RealDeviceTestingUiEvent.RunSelectedTests) }
                            )
                        }

                        is RealDeviceTestingUiState.PreFlightFailed -> {
                            PreFlightFailedContent(
                                state = state,
                                onReRunPreflight = { viewModel.onEvent(RealDeviceTestingUiEvent.RunPreflight) }
                            )
                        }

                        is RealDeviceTestingUiState.NoPeersFound -> {
                            NoPeersFoundContent(
                                state = state,
                                onScanPeers = { viewModel.onEvent(RealDeviceTestingUiEvent.ScanPeers) },
                                onToggleScenario = { viewModel.onEvent(RealDeviceTestingUiEvent.ToggleScenario(it)) },
                                onSelectAll = { viewModel.onEvent(RealDeviceTestingUiEvent.SelectAllScenarios) },
                                onDeselectAll = { viewModel.onEvent(RealDeviceTestingUiEvent.DeselectAllScenarios) }
                            )
                        }

                        is RealDeviceTestingUiState.RunningTests -> {
                            RunningTestsContent(
                                state = state,
                                onCancel = { viewModel.onEvent(RealDeviceTestingUiEvent.CancelTests) }
                            )
                        }

                        is RealDeviceTestingUiState.TestsDone -> {
                            TestsDoneContent(
                                state = state,
                                onExportLog = { viewModel.onEvent(RealDeviceTestingUiEvent.ExportLog) },
                                onCleanupData = { viewModel.onEvent(RealDeviceTestingUiEvent.CleanupTestData) },
                                onRerunFailed = { viewModel.onEvent(RealDeviceTestingUiEvent.RerunFailedTests) },
                                onRunAllTests = { viewModel.onEvent(RealDeviceTestingUiEvent.RunAllTests) },
                                onRunSelectedTests = { viewModel.onEvent(RealDeviceTestingUiEvent.RunSelectedTests) },
                                onToggleScenario = { viewModel.onEvent(RealDeviceTestingUiEvent.ToggleScenario(it)) },
                                onSelectAll = { viewModel.onEvent(RealDeviceTestingUiEvent.SelectAllScenarios) },
                                onDeselectAll = { viewModel.onEvent(RealDeviceTestingUiEvent.DeselectAllScenarios) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InitialStateContent(onRunPreflight: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MeshifyDesignSystem.Spacing.Xxl + MeshifyDesignSystem.Spacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Science,
            contentDescription = null,
            modifier = Modifier.padding(bottom = MeshifyDesignSystem.Spacing.Lg),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.real_device_testing_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.real_device_testing_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MeshifyDesignSystem.Spacing.Sm)
        )
        Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Lg))
        Button(
            onClick = onRunPreflight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.padding(end = MeshifyDesignSystem.Spacing.Xs)
            )
            Text(text = stringResource(R.string.screen_start_testing))
        }
    }
}

@Composable
private fun RunningPreflightContent(state: RealDeviceTestingUiState.RunningPreflight) {
    TestingCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.CardMedium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Md))
            Text(
                text = stringResource(R.string.screen_running_preflight),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MeshifyDesignSystem.Spacing.Sm))
            Text(
                text = stringResource(R.string.screen_elapsed_time, state.elapsedMs / 1000),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreFlightDoneContent(
    state: RealDeviceTestingUiState.PreFlightDone,
    onScanPeers: () -> Unit,
    onSelectPeer: (DiscoveredPeer) -> Unit,
    onToggleScenario: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onRunAllTests: () -> Unit,
    onRunSelectedTests: () -> Unit
) {
    val hasSelectedPeer = state.selectedPeer != null
    val hasSelectedScenarios = state.selectedScenarioIds.isNotEmpty()

    PreFlightResultsCard(
        preFlightResult = state.preFlightResult,
        isRunning = false,
        elapsedMs = state.preFlightResult.totalDurationMs
    )

    DiscoveredPeerList(
        peers = state.peers,
        selectedPeer = state.selectedPeer,
        isScanning = state.isScanning,
        onSelectPeer = onSelectPeer
    )

    if (!state.isScanning) {
        OutlinedButton(
            onClick = onScanPeers,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xs))
            Text(text = stringResource(R.string.screen_rescan))
        }
    }

    TestTypeSelector(
        scenarios = state.scenarios,
        selectedScenarioIds = state.selectedScenarioIds,
        enabled = true,
        onToggleScenario = onToggleScenario,
        onSelectAll = onSelectAll,
        onDeselectAll = onDeselectAll
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
    ) {
        Button(
            onClick = onRunAllTests,
            enabled = hasSelectedPeer && state.scenarios.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.padding(end = MeshifyDesignSystem.Spacing.Xs)
            )
            Text(text = stringResource(R.string.screen_run_all_tests))
        }

        OutlinedButton(
            onClick = onRunSelectedTests,
            enabled = hasSelectedPeer && hasSelectedScenarios,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.screen_run_selected_tests, state.selectedScenarioIds.size))
        }

        if (!hasSelectedPeer) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Small)
                )
                Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xxs))
                Text(
                    text = stringResource(R.string.screen_select_peer_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PreFlightFailedContent(
    state: RealDeviceTestingUiState.PreFlightFailed,
    onReRunPreflight: () -> Unit
) {
    PreFlightResultsCard(
        preFlightResult = state.preFlightResult,
        isRunning = false,
        elapsedMs = state.preFlightResult.totalDurationMs
    )

    TestingCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MeshifyDesignSystem.Shapes.CardSmall,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(MeshifyDesignSystem.Spacing.Md),
            horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Large)
            )
            Text(
                text = stringResource(R.string.screen_preflight_blocked),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    Button(
        onClick = onReRunPreflight,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.padding(end = MeshifyDesignSystem.Spacing.Xs)
        )
        Text(text = stringResource(R.string.screen_rerun_preflight))
    }
}

@Composable
private fun NoPeersFoundContent(
    state: RealDeviceTestingUiState.NoPeersFound,
    onScanPeers: () -> Unit,
    onToggleScenario: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    PreFlightResultsCard(
        preFlightResult = state.preFlightResult,
        isRunning = false,
        elapsedMs = state.preFlightResult.totalDurationMs
    )

    DiscoveredPeerList(
        peers = emptyList(),
        selectedPeer = null,
        isScanning = false,
        onSelectPeer = {}
    )

    TestTypeSelector(
        scenarios = state.scenarios,
        selectedScenarioIds = state.selectedScenarioIds,
        enabled = true,
        onToggleScenario = onToggleScenario,
        onSelectAll = onSelectAll,
        onDeselectAll = onDeselectAll
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(MeshifyDesignSystem.IconSizes.Small)
        )
        Spacer(Modifier.padding(start = MeshifyDesignSystem.Spacing.Xxs))
        Text(
            text = stringResource(R.string.screen_select_peer_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }

    Button(
        onClick = onScanPeers,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.padding(end = MeshifyDesignSystem.Spacing.Xs)
        )
        Text(text = stringResource(R.string.screen_rescan))
    }
}

@Composable
private fun RunningTestsContent(
    state: RealDeviceTestingUiState.RunningTests,
    onCancel: () -> Unit
) {
    TestProgressPanel(
        scenarios = state.scenarios,
        currentIndex = state.currentIndex,
        results = state.results,
        targetPeerName = state.targetPeer.name,
        isCancelling = state.isCancelling,
        progressFraction = state.progressFraction,
        onCancel = onCancel
    )
}

@Composable
private fun TestsDoneContent(
    state: RealDeviceTestingUiState.TestsDone,
    onExportLog: () -> Unit,
    onCleanupData: () -> Unit,
    onRerunFailed: () -> Unit,
    onRunAllTests: () -> Unit,
    onRunSelectedTests: () -> Unit,
    onToggleScenario: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    TestResultsPanel(
        scenarios = state.scenarios,
        results = state.results,
        isExporting = state.isExporting,
        isCleaning = state.isCleaning,
        exportSuccess = state.exportSuccess,
        cleanupSuccess = state.cleanupSuccess,
        cleanupError = state.cleanupError,
        onExportLog = onExportLog,
        onCleanupData = onCleanupData,
        onRerunFailed = onRerunFailed
    )

    TestTypeSelector(
        scenarios = state.scenarios,
        selectedScenarioIds = state.scenarios.map { it.id }.toSet(),
        enabled = true,
        onToggleScenario = onToggleScenario,
        onSelectAll = onSelectAll,
        onDeselectAll = onDeselectAll
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm)
    ) {
        val failedCount = state.results.count { (_, r) ->
            r.status == TestStatus.FAILED || r.status == TestStatus.TIMEOUT
        }

        if (failedCount > 0) {
            Button(
                onClick = onRerunFailed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MeshifyDesignSystem.Spacing.Xs)
                )
                Text(text = stringResource(R.string.screen_rerun_failed, failedCount))
            }
        }

        OutlinedButton(
            onClick = onRunAllTests,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.padding(end = MeshifyDesignSystem.Spacing.Xs)
            )
            Text(text = stringResource(R.string.screen_run_all_tests_again))
        }
    }
}

@Composable
private fun TestingCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = MeshifyDesignSystem.Shapes.CardMedium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = MeshifyDesignSystem.Elevation.Level1
    ) {
        content()
    }
}
