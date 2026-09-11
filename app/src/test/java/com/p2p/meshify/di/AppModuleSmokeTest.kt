package com.p2p.meshify.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.crypto.CryptoModule
import com.p2p.meshify.core.data.local.MeshifyDatabase
import javax.inject.Singleton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke خفيف لـ [AppModule] — يتحقّق أن كل @Provides يوفّر instance غير-null
 * دون كسر رسم Hilt.
 *
 * ملاحظة: [dagger.hilt.InstallIn] retention هو CLASS (كما تم التحقّق بـ javap
 * على hilt-core-2.60.1.jar) لذا لا يمكن التحقّق منه بالـ reflection وقت التشغيل.
 * نكتفي بالتحقّق من [dagger.Module] (RUNTIME) + سلوك الـ providers؛ سلامة
 * الـ InstallIn يضمنها الـ build (Hilt codegen سيفشل لو كان خاطئاً).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AppModuleSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: MeshifyDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `AppModule has dagger Module annotation`() {
        val ann = AppModule::class.java.getAnnotation(dagger.Module::class.java)
        assertNotNull("AppModule must have @Module", ann)
    }

    @Test
    fun `provideDatabase returns non-null and is singleton scoped`() {
        val m = AppModule::class.java.getDeclaredMethod("provideDatabase", Context::class.java)
        assertNotNull(m.getAnnotation(Singleton::class.java))

        val db = AppModule.provideDatabase(context)
        database = db
        assertNotNull(db)
        assertNotNull(db.chatDao())
        assertNotNull(db.messageDao())
        assertNotNull(db.pendingMessageDao())
    }

    @Test
    fun `provideSettingsRepository is singleton and non-null`() {
        val m = AppModule::class.java.getDeclaredMethod("provideSettingsRepository", Context::class.java)
        assertNotNull(m.getAnnotation(Singleton::class.java))
        assertNotNull(AppModule.provideSettingsRepository(context))
    }

    @Test
    fun `provideFileManager is singleton and non-null`() {
        val m = AppModule::class.java.getDeclaredMethod("provideFileManager", Context::class.java)
        assertNotNull(m.getAnnotation(Singleton::class.java))
        assertNotNull(AppModule.provideFileManager(context))
    }

    @Test
    fun `provideNotificationHelper is singleton and non-null`() {
        val m = AppModule::class.java.getDeclaredMethod("provideNotificationHelper", Context::class.java)
        assertNotNull(m.getAnnotation(Singleton::class.java))
        assertNotNull(AppModule.provideNotificationHelper(context))
    }

    @Test
    fun `providePeerIdProvider is singleton and returns stable id`() {
        val m = AppModule::class.java.getDeclaredMethod("providePeerIdProvider", Context::class.java)
        assertNotNull(m.getAnnotation(Singleton::class.java))
        val a = AppModule.providePeerIdProvider(context)
        val b = AppModule.providePeerIdProvider(context)
        assertNotNull(a)
        // SharedPreferences backed — second call must return same persisted id
        assertEquals(a.getPeerId(), b.getPeerId())
    }

    @Test
    fun `provideStringResourceProvider is singleton and non-null`() {
        val m = AppModule::class.java.getDeclaredMethod("provideStringResourceProvider", Context::class.java)
        assertNotNull(m.getAnnotation(Singleton::class.java))
        assertNotNull(AppModule.provideStringResourceProvider(context))
    }

    @Test
    fun `provideWifiStateChecker is singleton and non-null`() {
        val m = AppModule::class.java.getDeclaredMethod("provideWifiStateChecker", Context::class.java)
        assertNotNull(m.getAnnotation(Singleton::class.java))
        assertNotNull(AppModule.provideWifiStateChecker(context))
    }

    @Test
    fun `provideTransportManager is singleton and wires LAN only`() {
        val m = AppModule::class.java.getDeclaredMethod(
            "provideTransportManager",
            Context::class.java,
            com.p2p.meshify.domain.repository.ISettingsRepository::class.java,
            com.p2p.meshify.core.common.security.SimplePeerIdProvider::class.java,
            com.p2p.meshify.core.crypto.MessageCipher::class.java,
            com.p2p.meshify.core.crypto.PeerPublicKeyStore::class.java
        )
        assertNotNull(m.getAnnotation(Singleton::class.java))

        val settings = AppModule.provideSettingsRepository(context)
        val peerIds = AppModule.providePeerIdProvider(context)
        val keys = CryptoModule.provideDeviceKeysetManager(context)
        val store = CryptoModule.providePeerPublicKeyStore()
        val cipher = CryptoModule.provideMessageCipher(keys, store)

        val tm = AppModule.provideTransportManager(context, settings, peerIds, cipher, store)
        assertNotNull(tm)
        assertNotNull("LAN transport must be registered by createDefault", tm.getTransport("lan"))
        assertEquals(null, tm.getTransport("ble"))
    }
}
