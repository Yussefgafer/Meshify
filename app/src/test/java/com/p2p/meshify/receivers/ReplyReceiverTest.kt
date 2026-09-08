package com.p2p.meshify.receivers

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.MeshifyApp
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.core.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ReplyReceiver unit tests under Robolectric.
 *
 * Robolectric cannot provide `AndroidKeyStore`, which `NotificationHelper`
 * uses for the HMAC secret key. We fake the verifier through the test seam
 * on `ReplyReceiver` so the orchestration paths past the signature gate are
 * fully exercised; the real HMAC math is covered by
 * `core/data:NotificationHelperTest`.
 *
 * Coverage here:
 * - wrong action, missing chat_id/signature, invalid Base64,
 *   future/expired timestamp, bad signature, empty/too-long body → error notification.
 * - valid path: `IChatRepository.sendMessage` success → success notification.
 * - repository failure → error notification.
 * - chat not found → localized error notification.
 *
 * Deliberately excluded:
 * - rate limiting: the companion singleton `replyRateLimiter` has no seam,
 *   and its in-memory state would leak between tests.
 * - retry timing: `scheduleRetry` schedules background jobs; we verify the
 *   immediate notification path only, to keep the suite fast and deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestReplyApp::class, sdk = [33])
class ReplyReceiverTest {

    private lateinit var app: TestReplyApp
    private lateinit var context: Context
    private lateinit var receiver: ReplyReceiver
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var chatDao: com.p2p.meshify.core.data.local.dao.ChatDao
    private lateinit var repository: com.p2p.meshify.core.data.repository.ChatRepositoryImpl
    private lateinit var mainDispatcher: TestDispatcher
    private lateinit var testScheduler: TestCoroutineScheduler

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app.applicationContext

        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any()) } returns 0

        notificationHelper = mockk(relaxed = true)
        chatDao = mockk(relaxed = true)
        repository = mockk<com.p2p.meshify.core.data.repository.ChatRepositoryImpl>(relaxed = true)

        mainDispatcher = StandardTestDispatcher()
        testScheduler = mainDispatcher.scheduler

        Dispatchers.setMain(mainDispatcher)

        // Assign directly into the Application fields so onReceive can use them
        // without Hilt/transport startup.
        app.chatRepository = repository
        app.database = mockk(relaxed = true)
        every { app.database.chatDao() } returns chatDao

        receiver = ReplyReceiver()
        receiver.notificationHelperFactory = { notificationHelper }
        receiver.replyScopeFactory = {
            CoroutineScope(mainDispatcher + kotlinx.coroutines.SupervisorJob())
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun buildValidReplyIntent(
        chatId: String = "chat-1",
        signature: String = "valid-sig",
        timestamp: Long = System.currentTimeMillis(),
        text: String = "hello p2p"
    ): Intent {
        val intent = Intent("com.p2p.meshify.REPLY_ACTION").apply {
            putExtra("chat_id", chatId)
            putExtra("signature", signature)
            putExtra("timestamp", timestamp)
        }
        val remoteInput = RemoteInput.Builder(NotificationHelper.KEY_TEXT_REPLY).apply { setLabel("reply") }.build()
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, android.os.Bundle().apply {
            putCharSequence(NotificationHelper.KEY_TEXT_REPLY, text)
        })
        return intent
    }

    @Test
    fun `wrong action returns immediately`() {
        val intent = Intent("other.action")
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        verify(exactly = 0) { notificationHelper.verifyReplySignature(any(), any(), any()) }
    }

    @Test
    fun `missing chat_id shows invalid chat error`() {
        val intent = Intent("com.p2p.meshify.REPLY_ACTION").apply {
            putExtra("signature", "sig")
            putExtra("timestamp", System.currentTimeMillis())
        }
        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_invalid_chat), last.last())
    }

    @Test
    fun `missing signature shows invalid message error`() {
        val intent = Intent("com.p2p.meshify.REPLY_ACTION").apply {
            putExtra("chat_id", "c1")
            putExtra("timestamp", System.currentTimeMillis())
        }
        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_invalid_message), last.last())
    }

    @Test
    fun `invalid base64 signature shows unauthorized error`() {
        val intent = Intent("com.p2p.meshify.REPLY_ACTION").apply {
            putExtra("chat_id", "c1")
            putExtra("signature", "!!!not base64!!!")
            putExtra("timestamp", System.currentTimeMillis())
        }
        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_unauthorized), last.last())
    }

    @Test
    fun `future timestamp is rejected`() {
        val intent = Intent("com.p2p.meshify.REPLY_ACTION").apply {
            putExtra("chat_id", "c1")
            putExtra("signature", "sig")
            putExtra("timestamp", System.currentTimeMillis() + 1000L)
        }
        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_invalid_timestamp), last.last())
    }

    @Test
    fun `expired timestamp is rejected`() {
        val intent = Intent("com.p2p.meshify.REPLY_ACTION").apply {
            putExtra("chat_id", "c1")
            putExtra("signature", "sig")
            putExtra("timestamp", System.currentTimeMillis() - 16 * 60 * 1000L)
        }
        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_expired), last.last())
    }

    @Test
    fun `invalid signature shows not authorized error`() {
        every { notificationHelper.verifyReplySignature(any(), any(), any()) } returns false
        val intent = Intent("com.p2p.meshify.REPLY_ACTION").apply {
            putExtra("chat_id", "c1")
            putExtra("signature", "bad-sig")
            putExtra("timestamp", System.currentTimeMillis())
        }
        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_not_authorized), last.last())
        verify(exactly = 1) { notificationHelper.verifyReplySignature("c1", "bad-sig", any()) }
    }

    @Test
    fun `empty reply text shows empty error`() {
        every { notificationHelper.verifyReplySignature(any(), any(), any()) } returns true
        val intent = buildValidReplyIntent(text = "   ")
        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_empty), last.last())
    }

    @Test
    fun `long reply text shows too long error`() {
        every { notificationHelper.verifyReplySignature(any(), any(), any()) } returns true
        val intent = buildValidReplyIntent(text = "x".repeat(10001))
        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_too_long), last.last())
    }

    @Test
    fun `valid path shows success notification`() {
        every { notificationHelper.verifyReplySignature(any(), any(), any()) } returns true
        coEvery { chatDao.getChatById("c1") } returns ChatEntity(
            peerId = "c1",
            peerName = "peer-1",
            lastMessage = null,
            lastTimestamp = 0L
        )
        coEvery { repository.sendMessage("c1", "peer-1", "hello p2p", null) } returns kotlin.Result.success(Unit)

        val intent = buildValidReplyIntent(chatId = "c1", text = "hello p2p")
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()

        val shadow = org.robolectric.Shadows.shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
        val last = shadow.allNotifications.last()
        assertEquals(context.getString(R.string.notification_reply_sent_title), last.extras.getString(Notification.EXTRA_TITLE))
        coVerify(exactly = 1) { repository.sendMessage("c1", "peer-1", "hello p2p", null) }
    }

    @Test
    fun `repository failure shows error notification`() {
        every { notificationHelper.verifyReplySignature(any(), any(), any()) } returns true
        coEvery { chatDao.getChatById("c1") } returns ChatEntity(
            peerId = "c1",
            peerName = "peer-1",
            lastMessage = null,
            lastTimestamp = 0L
        )
        coEvery { repository.sendMessage("c1", "peer-1", "hello p2p", null) } returns kotlin.Result.failure(
            java.io.IOException("send failed")
        )

        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        val intent = buildValidReplyIntent(chatId = "c1", text = "hello p2p")
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_network), last.last())
    }

    @Test
    fun `chat not found shows chat not found error`() {
        every { notificationHelper.verifyReplySignature(any(), any(), any()) } returns true
        coEvery { chatDao.getChatById("missing") } returns null

        val last = mutableListOf<String?>()
        receiver.errorNotificationSink = { _, text -> last += text }
        val intent = buildValidReplyIntent(chatId = "missing", text = "hello")
        receiver.onReceive(context, intent)
        testScheduler.advanceUntilIdle()
        assertEquals(context.getString(R.string.error_reply_chat_not_found), last.last())
    }
}
