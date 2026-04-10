package com.p2p.meshify.feature.realdevicetesting.engine

/**
 * Configuration constants for the Test Engine.
 *
 * All timeouts, message counts, and payload sizes are centralized here
 * so they can be tuned without touching test logic.
 */
object TestEngineConfig {

    // --- Timeouts ---

    /** Discovery test: maximum time to scan for peers. */
    const val DISCOVERY_TIMEOUT_MS = 15_000L

    /** Ping test: maximum wait for a PONG response. */
    const val PING_TIMEOUT_MS = 10_000L

    /** Message test: maximum time for full pipeline (encrypt → send → decrypt → save). */
    const val MESSAGE_TIMEOUT_MS = 30_000L

    /** File test: maximum time for file transfer with progress. */
    const val FILE_TIMEOUT_MS = 60_000L

    /** Latency test: per-message timeout within the 10-message burst. */
    const val LATENCY_PER_MESSAGE_TIMEOUT_MS = 5_000L

    /** Latency test: delay between individual messages to avoid overwhelming transport. */
    const val LATENCY_INTER_MESSAGE_DELAY_MS = 100L

    /** Round-trip test: maximum wait for ECHO_REPLY. */
    const val ROUNDTRIP_TIMEOUT_MS = 15_000L

    // --- Counts ---

    /** Latency test: number of messages to send for statistical analysis. */
    const val LATENCY_MESSAGE_COUNT = 10

    // --- Payload Sizes ---

    /** Ping payload: the exact string sent in a PING test. */
    const val PING_PAYLOAD = "PING"

    /** Round-trip echo prefix — used to construct ECHO:<nonce> payloads. */
    const val ECHO_PREFIX = "ECHO:"

    /** Message test: the text sent through the full encrypted pipeline. */
    const val TEST_MESSAGE_TEXT = "Meshify real-device test message — verify decryption and storage."

    /** File test: size of synthetic test file in bytes (1 KB of repeated pattern). */
    const val TEST_FILE_SIZE_BYTES = 1024

    // --- Database ---

    /** Peer ID prefix for test data — used to isolate test messages from real chats. */
    const val TEST_PEER_ID_PREFIX = "test_target_"

    /**
     * Constructs the test peer ID for a given target device.
     * Example: "test_target_abc123"
     */
    fun testPeerIdFor(targetDeviceId: String): String = "$TEST_PEER_ID_PREFIX$targetDeviceId"
}
