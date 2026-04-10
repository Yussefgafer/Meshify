package com.p2p.meshify.feature.realdevicetesting.adapter

import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.feature.realdevicetesting.model.DiscoveredPeer
import kotlinx.coroutines.flow.Flow

/**
 * Test-safe abstraction over IMeshTransport for the Real Device Testing feature.
 *
 * This interface exposes a simplified, test-oriented subset of the transport layer.
 * The Test Engine (Session 4) depends ONLY on this interface — never on concrete
 * transport implementations like LanTransportImpl or BleTransportImpl.
 *
 * Lifecycle contract:
 * 1. Call [initialize] once before any test operations.
 * 2. Call [discoverPeers] with a timeout to find nearby devices.
 * 3. Call [sendTestPayload] to send a test payload to a discovered peer.
 * 4. Call [shutdown] when done to release all resources.
 *
 * Thread safety: All methods are suspend functions — callers must use a coroutine scope.
 * Implementations MUST be thread-safe for concurrent discovery and send operations.
 */
interface TransportTestAdapter {

    /**
     * Unique transport identifier (e.g., "lan", "ble").
     * Maps directly to IMeshTransport.transportName.
     */
    val transportType: TransportType

    /**
     * Human-readable name for UI display (e.g., "LAN (Wi-Fi)", "Bluetooth LE").
     */
    val displayName: String

    /**
     * Whether this transport is available on the current device.
     * Checks hardware support, permissions, and runtime state.
     */
    val isAvailable: Boolean

    /**
     * Stream of transport events from the underlying implementation.
     * Emits DeviceDiscovered, DeviceLost, ConnectionEstablished, ConnectionLost, Error, etc.
     */
    val events: Flow<TransportEvent>

    /**
     * Initializes the transport and starts peer discovery.
     *
     * This method:
     * - Starts the underlying transport (sockets, mDNS, BLE advertising, etc.)
     * - Begins peer discovery
     * - Begins collecting transport events
     *
     * Must be called before [discoverPeers] or [sendTestPayload].
     * Idempotent: calling it multiple times has no effect after the first successful call.
     *
     * @throws IllegalStateException if the transport cannot be started (e.g., missing permissions)
     */
    suspend fun initialize()

    /**
     * Discovers nearby peers with a timeout.
     *
     * Blocks until either:
     * - [timeoutMs] milliseconds have elapsed, OR
     * - At least one peer is discovered (returns immediately on first discovery)
     *
     * The returned list contains all peers discovered within the timeout window.
     * An empty list means no peers were found — not necessarily a failure.
     *
     * @param timeoutMs Maximum time to wait for discoveries (default: 10 seconds).
     *                  Must be positive. Values under 1000ms are clamped to 1000ms.
     * @return List of discovered peers. May be empty.
     */
    suspend fun discoverPeers(timeoutMs: Long = 10_000L): List<DiscoveredPeer>

    /**
     * Sends a test payload to a specific peer.
     *
     * This method:
     * - Validates the peer is known (discovered previously)
     * - Constructs a Payload of the specified type
     * - Sends it through the underlying transport
     * - Measures send duration
     * - Returns a structured result
     *
     * @param peerId The target peer's ID (must have been returned by [discoverPeers]).
     * @param payloadType The type of payload to send (TEXT, SYSTEM_CONTROL, etc.).
     * @param testData Raw bytes for the payload data.
     * @return Result of the send operation with timing and error details.
     */
    suspend fun sendTestPayload(
        peerId: String,
        payloadType: com.p2p.meshify.domain.model.Payload.PayloadType,
        testData: ByteArray
    ): TestSendResult

    /**
     * Stops the transport and releases all resources.
     *
     * This method:
     * - Stops peer discovery
     * - Stops the underlying transport (closes sockets, stops BLE advertising)
     * - Clears internal peer state
     * - Cancels all coroutine jobs
     *
     * Must be called after testing is complete. Idempotent: safe to call multiple times.
     * After calling [shutdown], the adapter must be re-initialized via [initialize] to use again.
     */
    suspend fun shutdown()
}

/**
 * Result of a test payload send operation.
 *
 * @property success Whether the payload was sent successfully.
 * @property durationMs Time taken to send the payload in milliseconds.
 * @property bytesSent Number of bytes in the payload data.
 * @property error Human-readable error message if the send failed.
 * @property exception The underlying exception if the send failed (null on success).
 */
data class TestSendResult(
    val success: Boolean,
    val durationMs: Long,
    val bytesSent: Int,
    val error: String? = null,
    val exception: Throwable? = null
) {
    companion object {
        fun success(durationMs: Long, bytesSent: Int) = TestSendResult(
            success = true,
            durationMs = durationMs,
            bytesSent = bytesSent
        )

        fun failure(error: String, durationMs: Long, bytesSent: Int = 0, exception: Throwable? = null) =
            TestSendResult(
                success = false,
                durationMs = durationMs,
                bytesSent = bytesSent,
                error = error,
                exception = exception
            )
    }
}
