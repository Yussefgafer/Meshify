package com.p2p.meshify.core.network.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeAdvertiser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleAdvertiserTest {

    private fun mockAdapter(
        enabled: Boolean = true,
        advertiser: BluetoothLeAdvertiser? = mockk(relaxed = true)
    ): BluetoothAdapter {
        val adapter = mockk<BluetoothAdapter>(relaxed = true)
        every { adapter.isEnabled } returns enabled
        every { adapter.bluetoothLeAdvertiser } returns advertiser
        return adapter
    }

    @Test
    fun startAdvertising_withNullAdvertiser_doesNotThrow() {
        val adapter = mockAdapter(advertiser = null)
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "Device", bluetoothAdapter = adapter)
        adv.startAdvertising()
        assertFalse(adv.isCurrentlyAdvertising())
    }

    @Test
    fun startAdvertising_withNullAdapter_doesNotThrow() {
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "Device", bluetoothAdapter = null)
        adv.startAdvertising()
        assertFalse(adv.isCurrentlyAdvertising())
    }

    @Test
    fun startAdvertising_withDisabledAdapter_doesNotCallStartAdvertising() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        val adapter = mockAdapter(enabled = false, advertiser = mockAdv)
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "Device", bluetoothAdapter = adapter)
        adv.startAdvertising()
        verify(exactly = 0) { mockAdv.startAdvertising(any(), any(), any(), any()) }
        assertFalse(adv.isCurrentlyAdvertising())
    }

    @Test
    fun startAdvertising_withValidAdapter_callsStartAdvertising() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        val adapter = mockAdapter(enabled = true, advertiser = mockAdv)
        val peerId = UUID.randomUUID().toString()
        val adv = BleAdvertiser(peerId = peerId, deviceName = "MeshifyDevice", bluetoothAdapter = adapter)
        adv.startAdvertising()
        verify(exactly = 1) { mockAdv.startAdvertising(any(), any(), any(), any()) }
    }

    @Test
    fun startAdvertising_withNonUuidPeerId_truncatesAndCalls() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        val adapter = mockAdapter(advertiser = mockAdv)
        val adv = BleAdvertiser(peerId = "not-a-uuid-but-a-very-long-peer-id-that-exceeds-limit", deviceName = "D", bluetoothAdapter = adapter)
        adv.startAdvertising()
        verify(exactly = 1) { mockAdv.startAdvertising(any(), any(), any(), any()) }
    }

    @Test
    fun startAdvertising_withShortNonUuidPeerId_calls() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        val adapter = mockAdapter(advertiser = mockAdv)
        val adv = BleAdvertiser(peerId = "short", deviceName = "D", bluetoothAdapter = adapter)
        adv.startAdvertising()
        verify(exactly = 1) { mockAdv.startAdvertising(any(), any(), any(), any()) }
    }

    @Test
    fun startAdvertising_whenSecurityException_doesNotThrow() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        every { mockAdv.startAdvertising(any(), any(), any(), any()) } throws SecurityException("missing perm")
        val adapter = mockAdapter(advertiser = mockAdv)
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "D", bluetoothAdapter = adapter)
        adv.startAdvertising()
        assertFalse(adv.isCurrentlyAdvertising())
    }

    @Test
    fun startAdvertising_whenGenericException_doesNotThrow() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        every { mockAdv.startAdvertising(any(), any(), any(), any()) } throws RuntimeException("boom")
        val adapter = mockAdapter(advertiser = mockAdv)
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "D", bluetoothAdapter = adapter)
        adv.startAdvertising()
        assertFalse(adv.isCurrentlyAdvertising())
    }

    @Test
    fun stopAdvertising_whenNotAdvertising_isNoOp() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        val adapter = mockAdapter(advertiser = mockAdv)
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "D", bluetoothAdapter = adapter)
        adv.stopAdvertising()
        verify(exactly = 0) { mockAdv.stopAdvertising(any()) }
        assertFalse(adv.isCurrentlyAdvertising())
    }

    @Test
    fun stopAdvertising_whenAdvertiserNull_doesNotThrow() {
        val adapter = mockAdapter(advertiser = null)
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "D", bluetoothAdapter = adapter)
        adv.stopAdvertising()
        assertFalse(adv.isCurrentlyAdvertising())
    }

    @Test
    fun isCurrentlyAdvertising_initiallyFalse() {
        val adapter = mockAdapter(advertiser = null)
        val adv = BleAdvertiser(peerId = "peer", deviceName = "D", bluetoothAdapter = adapter)
        assertFalse(adv.isCurrentlyAdvertising())
    }

    @Test
    fun stopAdvertising_whenCallbackThrows_doesNotThrow() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        every { mockAdv.stopAdvertising(any()) } throws RuntimeException("stop fail")
        val adapter = mockAdapter(advertiser = mockAdv)
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "D", bluetoothAdapter = adapter)
        val field = BleAdvertiser::class.java.getDeclaredField("isAdvertising")
        field.isAccessible = true
        field.set(adv, true)
        assertTrue(adv.isCurrentlyAdvertising())
        adv.stopAdvertising()
    }

    @Test
    fun startAdvertising_idempotentWhenAlreadyAdvertising_doesNotCallAgain() {
        val mockAdv = mockk<BluetoothLeAdvertiser>(relaxed = true)
        val adapter = mockAdapter(advertiser = mockAdv)
        val adv = BleAdvertiser(peerId = UUID.randomUUID().toString(), deviceName = "D", bluetoothAdapter = adapter)
        val field = BleAdvertiser::class.java.getDeclaredField("isAdvertising")
        field.isAccessible = true
        field.set(adv, true)
        adv.startAdvertising()
        verify(exactly = 0) { mockAdv.startAdvertising(any(), any(), any(), any()) }
    }
}
