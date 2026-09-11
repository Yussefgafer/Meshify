package com.p2p.meshify.feature.realdevicetesting.engine

import android.util.Log
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestDataCleanerTest {

    private lateinit var database: MeshifyDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var chatDao: ChatDao
    private lateinit var cleaner: TestDataCleaner

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        messageDao = mockk(relaxed = true)
        chatDao = mockk(relaxed = true)
        database = mockk()
        coEvery { database.messageDao() } returns messageDao
        coEvery { database.chatDao() } returns chatDao

        cleaner = TestDataCleaner(database)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `cleanup constructs prefixed peer id and deletes messages and chat`() = runTest {
        val targetDeviceId = "device-123"
        val result = cleaner.cleanup(targetDeviceId)

        assertTrue(result.isSuccess)
        coVerify { messageDao.deleteAllMessagesForChat("test_target_device-123") }
        coVerify { chatDao.deleteChatById("test_target_device-123") }
    }

    @Test
    fun `cleanup is idempotent for same target`() = runTest {
        val result1 = cleaner.cleanup("device-123")
        val result2 = cleaner.cleanup("device-123")

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        coVerify(exactly = 2) { messageDao.deleteAllMessagesForChat("test_target_device-123") }
        coVerify(exactly = 2) { chatDao.deleteChatById("test_target_device-123") }
    }

    @Test
    fun `cleanup does not delete other peer chat data`() = runTest {
        val result = cleaner.cleanup("device-123")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { chatDao.deleteChatById("test_target_device-other") }
        coVerify(exactly = 0) { messageDao.deleteAllMessagesForChat("test_target_device-other") }
    }

    @Test
    fun `cleanup returns failure on DAO exception`() = runTest {
        coEvery { messageDao.deleteAllMessagesForChat(any()) } throws IllegalStateException("db failure")

        val result = cleaner.cleanup("device-123")

        assertTrue(result.isFailure)
    }
}
