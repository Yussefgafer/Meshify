package com.p2p.meshify.core.util

import android.content.Context
import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyStore
import javax.crypto.KeyGenerator

/**
 * NotificationHelperTest — JVM unit test (plain JVM). The project compiles test
 * bytecode to class-file v65 (via compileOptions VERSION_21 in this module's
 * build.gradle.kts). Robolectric is available in this module but adds no value
 * for a pure-HMAC sign/verify test that does not need Android framework
 * resources, so these tests deliberately avoid it to stay fast. We mirror the
 * established [ChatRepositoryImplTest] style:
 * - stub [android.util.Log] so Logger never throws;
 * - stub the [KeyStore]/[KeyGenerator] statics that point at "AndroidKeyStore"
 *   (which has no JCE provider on plain JVM), backing the secret key on a real
 *   SunJCE HmacSHA256 key so the HMAC sign/verify path genuinely runs.
 *
 * Covers:
 * - the reply-signature HMAC round-trips sign/verify and rejects tampering.
 *
 * Not covered: channel registration (`createNotificationChannels`) and
 * `showMessageNotification` both need Android resources / Notification.Builder;
 * Robolectric is on this module's classpath but no test here uses it.
 */
class NotificationHelperTest {

    private lateinit var context: Context
    private lateinit var helper: NotificationHelper

    @Before
    fun setUp() {
        // Logger delegates to android.util.Log; stub every overload.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0

        // android.util.Base64 is an Android stub (returns null on plain JVM).
        // Delegate encodeToString to the real java.util.Base64 so HMAC bytes
        // serialize correctly for the sign/verify round-trip.
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any<ByteArray>(), Base64.NO_WRAP) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), Base64.NO_WRAP) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }

        // AndroidKeyStore has no JCE provider on plain JVM. Back it with an
        // in-memory map holding a REAL HmacSHA256 key (generated via SunJCE) so
        // the HMAC sign/verify path executes for real. Mac is left unmocked.
        mockkStatic(KeyStore::class)
        val fakeStore = mockk<KeyStore>(relaxed = true)
        val storeMap = mutableMapOf<String, KeyStore.Entry>()
        every { KeyStore.getInstance("AndroidKeyStore") } returns fakeStore
        every { fakeStore.getEntry(any(), any()) } answers { storeMap[firstArg()] }
        every { fakeStore.setEntry(any(), any(), any()) } answers { storeMap[firstArg()] = secondArg() }
        every { fakeStore.deleteEntry(any()) } answers { storeMap.remove(firstArg()) }

        mockkStatic(KeyGenerator::class)
        val realKeyGen = KeyGenerator.getInstance("HmacSHA256") // SunJCE, real key
        every { KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore") } returns realKeyGen

        context = mockk(relaxed = true)
        // NotificationHelper persists the key-rotation timestamp in SharedPreferences.
        // A relaxed mock returns 0L, which makes every call think the key is 30+
        // days old and regenerate a fresh key — breaking sign/verify. Return "now"
        // so the rotation guard does NOT fire and the same key is reused.
        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences("notification_helper", Context.MODE_PRIVATE) } returns prefs
        every { prefs.getLong(any(), any()) } returns System.currentTimeMillis()

        helper = NotificationHelper(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        unmockkStatic(Base64::class)
        unmockkStatic(KeyStore::class)
        unmockkStatic(KeyGenerator::class)
    }

    @Test
    fun replySignature_roundTripsSignAndVerify() {
        val chatId = "peerSig"
        val ts = 1_700_000_000_000L
        val sig = helper.generateReplySignature(chatId, ts)
        assertTrue("signature non-empty", sig.isNotEmpty())
        assertTrue("valid signature verifies", helper.verifyReplySignature(chatId, sig, ts))
        assertFalse("tampered chatId fails", helper.verifyReplySignature("other", sig, ts))
        assertFalse("tampered timestamp fails", helper.verifyReplySignature(chatId, sig, ts + 1))
    }
}
