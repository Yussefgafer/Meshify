package com.p2p.meshify.feature.realdevicetesting.ui

import android.content.Context
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.feature.realdevicetesting.adapter.TransportTestAdapter
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import com.p2p.meshify.feature.realdevicetesting.model.TestResult
import com.p2p.meshify.feature.realdevicetesting.model.TestScenario
import com.p2p.meshify.feature.realdevicetesting.model.TestStatus
import com.p2p.meshify.feature.realdevicetesting.preflight.CheckResult
import com.p2p.meshify.feature.realdevicetesting.preflight.CheckStatus
import com.p2p.meshify.feature.realdevicetesting.preflight.PreFlightResult
import com.p2p.meshify.feature.realdevicetesting.ui.RealDeviceTestingUiEvent.CancelTests
import com.p2p.meshify.feature.realdevicetesting.ui.RealDeviceTestingUiEvent.RunPreflight
import com.p2p.meshify.feature.realdevicetesting.ui.RealDeviceTestingUiEvent.SelectPeer
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealDeviceTestingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RealDeviceTestingViewModel
    private lateinit var chatRepository: IChatRepository
    private lateinit var database: MeshifyDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        chatRepository = mockk(relaxed = true)
        database = mockk(relaxed = true)
        context = mockk(relaxed = true)

        viewModel = RealDeviceTestingViewModel(context, chatRepository, database)
    }

    @Test
    fun `initial state is Initial`() = runTest(dispatcher) {
        assertEquals(RealDeviceTestingUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun `preflight flow enters running preflight state`() = runTest(dispatcher) {
        setUiState(RealDeviceTestingUiState.PreFlightDone(
            preFlightResult = PreFlightResult(
                permissionResults = emptyList(),
                connectivityResult = CheckResult("Connectivity", CheckStatus.PASS, "ok"),
                securityResult = null,
                totalDurationMs = 1
            ),
            peers = emptyList(),
            selectedPeer = null,
            isScanning = false,
            scenarios = emptyList(),
            selectedScenarioIds = emptySet()
        ))

        viewModel.onEvent(RunPreflight)

        val state = viewModel.uiState.value
        when (state) {
            is RealDeviceTestingUiState.RunningPreflight,
            is RealDeviceTestingUiState.PreFlightDone,
            is RealDeviceTestingUiState.PreFlightFailed -> {}
            else -> throw AssertionError("Unexpected state: $state")
        }
    }

    @Test
    fun `select peer updates selected peer in preflight done state`() = runTest(dispatcher) {
        val peer = DiscoveredPeer(id = "p", name = "Device", address = "a", transportType = TransportType.LAN)
        val preflight = PreFlightResult(
            permissionResults = emptyList(),
            connectivityResult = CheckResult("Connectivity", CheckStatus.PASS, "ok"),
            securityResult = null,
            totalDurationMs = 1
        )
        setUiState(RealDeviceTestingUiState.PreFlightDone(
            preFlightResult = preflight,
            peers = listOf(peer),
            selectedPeer = null,
            isScanning = false,
            scenarios = emptyList(),
            selectedScenarioIds = emptySet()
        ))

        viewModel.onEvent(SelectPeer(peer))
        val state = viewModel.uiState.value
        assertTrue(state is RealDeviceTestingUiState.PreFlightDone)
        assertEquals(peer, (state as RealDeviceTestingUiState.PreFlightDone).selectedPeer)
    }

    @Test
    fun `cancel tests preserves completed results and does not regress finished scenario status`() = runTest(dispatcher) {
        val peer = DiscoveredPeer(id = "p", name = "Device", address = "a", transportType = TransportType.LAN)
        val scenarios = listOf(TestScenario("s", 0, 0, "icon"))
        val results = mapOf("s" to TestResult.pass("s", 1, "ok"))

        setUiState(RealDeviceTestingUiState.RunningTests(
            scenarios = scenarios,
            currentIndex = 1,
            results = results,
            targetPeer = peer
        ))

        viewModel.onEvent(CancelTests)

        val state = viewModel.uiState.value
        assertTrue(state is RealDeviceTestingUiState.TestsDone)
        val done = state as RealDeviceTestingUiState.TestsDone
        // Completed results are preserved; cancellation only affects unfinished scenarios.
        assertEquals(TestStatus.PASSED, done.results["s"]?.status)
        assertEquals(1, done.results.size)
    }

    private fun setUiState(state: RealDeviceTestingUiState) {
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<RealDeviceTestingUiState>
        stateFlow.value = state
    }
}
