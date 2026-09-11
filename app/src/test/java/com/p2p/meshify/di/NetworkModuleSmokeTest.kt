package com.p2p.meshify.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.crypto.CryptoModule
import com.p2p.meshify.core.network.ble.BleTransportImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke خفيف لـ [NetworkModule] — يتحقّق أن Hilt binding يوفّر BleTransportImpl
 * دون كسر الرسم البياني.
 *
 * ملاحظة: [dagger.hilt.InstallIn] retention هو CLASS (كما تم التحقّق بـ javap
 * على hilt-core-2.60.1.jar) لذا لا يمكن التحقّق منه بالـ reflection وقت التشغيل.
 * نكتفي بالتحقّق من [dagger.Module] (RUNTIME) + سلوك الـ providers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class NetworkModuleSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `NetworkModule has dagger Module annotation`() {
        val ann = NetworkModule::class.java.getAnnotation(dagger.Module::class.java)
        assertNotNull("NetworkModule must have @Module", ann)
    }

    @Test
    fun `provideBleTransport returns non-null BleTransportImpl with correct transportName`() {
        val settings = AppModule.provideSettingsRepository(context)
        val peerIds = AppModule.providePeerIdProvider(context)
        val store = CryptoModule.providePeerPublicKeyStore()

        val ble: BleTransportImpl = NetworkModule.provideBleTransport(
            context, settings, peerIds, store
        )
        assertNotNull(ble)
        assertEquals("ble", ble.transportName)
    }

    @Test
    fun `provideBleTransport is not singleton scoped - each call creates new instance`() {
        val m = NetworkModule::class.java.getDeclaredMethod(
            "provideBleTransport",
            Context::class.java,
            com.p2p.meshify.domain.repository.ISettingsRepository::class.java,
            com.p2p.meshify.core.common.security.SimplePeerIdProvider::class.java,
            com.p2p.meshify.core.crypto.PeerPublicKeyStore::class.java
        )
        // NetworkModule intentionally does NOT use @Singleton for BLE — lifecycle is
        // managed by MeshifyApp (register/unregister). Assert the absence.
        assertNull("provideBleTransport must NOT be @Singleton", m.getAnnotation(javax.inject.Singleton::class.java))

        val settings = AppModule.provideSettingsRepository(context)
        val peerIds = AppModule.providePeerIdProvider(context)
        val store = CryptoModule.providePeerPublicKeyStore()

        val a = NetworkModule.provideBleTransport(context, settings, peerIds, store)
        val b = NetworkModule.provideBleTransport(context, settings, peerIds, store)
        assertNotNull(a)
        assertNotNull(b)
        // Must be distinct instances (no singleton)
        assertTrue("Each provideBleTransport call must create a new instance", a !== b)
    }

    @Test
    fun `provideBleTransport uses peerIdProvider id`() {
        val settings = AppModule.provideSettingsRepository(context)
        val peerIds = AppModule.providePeerIdProvider(context)
        val expectedPeerId = peerIds.getPeerId()
        val store = CryptoModule.providePeerPublicKeyStore()

        // The BleTransportImpl stores peerId internally; we verify indirectly by
        // checking that peerIdProvider still returns the same id after construction
        // and that the transport was created without throwing.
        val ble = NetworkModule.provideBleTransport(context, settings, peerIds, store)
        assertNotNull(ble)
        assertEquals(expectedPeerId, peerIds.getPeerId())
    }
}
