package com.p2p.meshify.feature.realdevicetesting.engine

import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.feature.realdevicetesting.engine.TestEngineConfig.testPeerIdFor

private const val TAG = "TestDataCleaner"

/**
 * Cleans up test data from the Room database after each test run.
 *
 * Test data is isolated using a special peer ID prefix (`test_target_<device_id>`).
 * This ensures test messages never mix with real user chats.
 *
 * Cleanup strategy:
 * 1. Delete all messages for the test peer (via `deleteAllMessagesForChat`)
 * 2. Delete the chat entry itself (via `deleteChatById`)
 *
 * This is called automatically after each test by the Test Engine,
 * and also manually via the "Clean Test Data" button in the UI.
 */
class TestDataCleaner(
    private val database: MeshifyDatabase
) {

    /**
     * Deletes all test data associated with the given target device.
     *
     * This method:
     * 1. Constructs the test peer ID using [testPeerIdFor]
     * 2. Deletes all messages for that chat
     * 3. Deletes the chat entry itself
     *
     * Idempotent: safe to call multiple times for the same peer.
     * If the chat doesn't exist, it silently succeeds.
     *
     * @param targetDeviceId The raw device ID of the test target (without prefix).
     * @return Result indicating success or failure with error details.
     */
    suspend fun cleanup(targetDeviceId: String): Result<Unit> = try {
        val testPeerId = testPeerIdFor(targetDeviceId)
        Logger.i(TAG, "Cleaning test data for peer: $testPeerId")

        // Step 1: Delete all messages for this test chat
        database.messageDao().deleteAllMessagesForChat(testPeerId)
        Logger.i(TAG, "Deleted messages for $testPeerId")

        // Step 2: Delete the chat entry
        database.chatDao().deleteChatById(testPeerId)
        Logger.i(TAG, "Deleted chat entry for $testPeerId")

        Logger.i(TAG, "Test data cleaned for $testPeerId")
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.e("Failed to clean test data for $targetDeviceId", e, tag = TAG)
        Result.failure(e)
    }
}
