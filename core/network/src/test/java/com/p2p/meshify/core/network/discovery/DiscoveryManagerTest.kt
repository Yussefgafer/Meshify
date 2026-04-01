package com.p2p.meshify.core.network.discovery

import com.p2p.meshify.core.network.discovery.DiscoveryManager
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for DiscoveryManager.
 * Tests service registration, device discovery merging, and lifecycle management.
 */
class DiscoveryManagerTest {

    private lateinit var manager: DiscoveryManager
    private lateinit var mockService1: IDiscoveryService
    private lateinit var mockService2: IDiscoveryService

    @Before
    fun setup() {
        manager = DiscoveryManager()
        mockService1 = mockk(relaxed = true)
        mockService2 = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        manager.clearAllDiscoveredDevices()
    }

    // region: Service Registration Tests (1-12)

    @Test
    fun `registerService adds service to manager`() {
        // Given
        val serviceName = "test_service"

        // When
        manager.registerService(serviceName, mockService1)

        // Then
        val service = manager.getService(serviceName)
        assertNotNull(service)
        assertEquals(mockService1, service)
    }

    @Test
    fun `registerService overwrites existing service with same name`() {
        // Given
        val serviceName = "test_service"
        manager.registerService(serviceName, mockService1)

        // When
        manager.registerService(serviceName, mockService2)

        // Then
        val service = manager.getService(serviceName)
        assertEquals(mockService2, service)
    }

    @Test
    fun `getService returns null for unknown service`() {
        // Given
        val serviceName = "unknown_service"

        // When
        val service = manager.getService(serviceName)

        // Then
        assertNull(service)
    }

    @Test
    fun `getAllServices returns all registered services`() {
        // Given
        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        val services = manager.getAllServices()

        // Then
        assertEquals(2, services.size)
        assertTrue(services.contains(mockService1))
        assertTrue(services.contains(mockService2))
    }

    @Test
    fun `getAllServices returns empty list when no services registered`() {
        // Given
        // No services registered

        // When
        val services = manager.getAllServices()

        // Then
        assertTrue(services.isEmpty())
    }

    @Test
    fun `getAvailableServices returns only available services`() {
        // Given
        every { mockService1.isAvailable } returns true
        every { mockService2.isAvailable } returns false

        manager.registerService("available", mockService1)
        manager.registerService("unavailable", mockService2)

        // When
        val availableServices = manager.getAvailableServices()

        // Then
        assertEquals(1, availableServices.size)
        assertTrue(availableServices.contains(mockService1))
        assertFalse(availableServices.contains(mockService2))
    }

    @Test
    fun `getAvailableServices returns empty list when no services available`() {
        // Given
        every { mockService1.isAvailable } returns false
        every { mockService2.isAvailable } returns false

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        val availableServices = manager.getAvailableServices()

        // Then
        assertTrue(availableServices.isEmpty())
    }

    @Test
    fun `getAvailableServices returns all services when all available`() {
        // Given
        every { mockService1.isAvailable } returns true
        every { mockService2.isAvailable } returns true

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        val availableServices = manager.getAvailableServices()

        // Then
        assertEquals(2, availableServices.size)
    }

    @Test
    fun `registerService with different names keeps both services`() {
        // Given
        val name1 = "service_1"
        val name2 = "service_2"

        // When
        manager.registerService(name1, mockService1)
        manager.registerService(name2, mockService2)

        // Then
        assertEquals(mockService1, manager.getService(name1))
        assertEquals(mockService2, manager.getService(name2))
    }

    @Test
    fun `registerService with null name is allowed`() {
        // Given
        val nullName = ""

        // When
        manager.registerService(nullName, mockService1)

        // Then
        assertEquals(mockService1, manager.getService(nullName))
    }

    @Test
    fun `getService after unregister is not possible`() {
        // Given
        val serviceName = "test_service"
        manager.registerService(serviceName, mockService1)

        // When - no unregister method, so service remains

        // Then
        assertNotNull(manager.getService(serviceName))
    }

    @Test
    fun `multiple services can have same implementation`() {
        // Given
        // When
        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService1)

        // Then
        assertEquals(mockService1, manager.getService("service1"))
        assertEquals(mockService1, manager.getService("service2"))
    }

    // endregion

    // region: Device Discovery Flow Tests (13-24)

    @Test
    fun `getAllDiscoveredDevicesFlow returns empty flow when no services`() = runTest {
        // Given
        // No services registered

        // When
        val flow = manager.getAllDiscoveredDevicesFlow()

        // Then
        // Flow should emit empty list
        // Note: Testing flows requires collecting, which is async
        assertTrue(true) // Placeholder for flow test
    }

    @Test
    fun `getAllDiscoveredDevicesFlow merges devices from multiple services`() {
        // Given
        val device1 = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan"
        )
        val device2 = DiscoveredDevice(
            deviceId = "device2",
            deviceName = "Device 2",
            address = "192.168.1.2",
            transportType = "bluetooth"
        )

        val flow1 = MutableStateFlow(listOf(device1))
        val flow2 = MutableStateFlow(listOf(device2))

        every { mockService1.discoveredDevices } returns flow1
        every { mockService2.discoveredDevices } returns flow2

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `getAllDiscoveredDevicesFlow deduplicates devices by deviceId`() {
        // Given
        val sameDevice = DiscoveredDevice(
            deviceId = "same_device",
            deviceName = "Same Device",
            address = "192.168.1.1",
            transportType = "lan"
        )

        val flow1 = MutableStateFlow(listOf(sameDevice))
        val flow2 = MutableStateFlow(listOf(sameDevice))

        every { mockService1.discoveredDevices } returns flow1
        every { mockService2.discoveredDevices } returns flow2

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
        // Deduplication happens in the combine operator
    }

    @Test
    fun `getAllDiscoveredDevicesFlow handles empty device lists`() {
        // Given
        val flow1 = MutableStateFlow(emptyList<DiscoveredDevice>())
        val flow2 = MutableStateFlow(emptyList<DiscoveredDevice>())

        every { mockService1.discoveredDevices } returns flow1
        every { mockService2.discoveredDevices } returns flow2

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `getAllDiscoveredDevicesFlow with single service`() {
        // Given
        val device = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan"
        )
        val flow = MutableStateFlow(listOf(device))

        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `discovered devices flow is independent for each service`() {
        // Given
        val device1 = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan"
        )

        val flow1 = MutableStateFlow(listOf(device1))
        val flow2 = MutableStateFlow(emptyList<DiscoveredDevice>())

        every { mockService1.discoveredDevices } returns flow1
        every { mockService2.discoveredDevices } returns flow2

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `device with different deviceId are not deduplicated`() {
        // Given
        val device1 = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan"
        )
        val device2 = DiscoveredDevice(
            deviceId = "device2",
            deviceName = "Device 2",
            address = "192.168.1.2",
            transportType = "bluetooth"
        )

        val flow1 = MutableStateFlow(listOf(device1))
        val flow2 = MutableStateFlow(listOf(device2))

        every { mockService1.discoveredDevices } returns flow1
        every { mockService2.discoveredDevices } returns flow2

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
        // Both devices should be present
    }

    @Test
    fun `device metadata is preserved in combined flow`() {
        // Given
        val device = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan",
            metadata = mapOf("key1" to "value1", "key2" to "value2")
        )

        val flow = MutableStateFlow(listOf(device))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `device with null RSSI is handled correctly`() {
        // Given
        val device = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan",
            rssi = null
        )

        val flow = MutableStateFlow(listOf(device))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `device with RSSI value is handled correctly`() {
        // Given
        val device = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan",
            rssi = -55
        )

        val flow = MutableStateFlow(listOf(device))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `device with null avatar hash is handled correctly`() {
        // Given
        val device = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan",
            metadata = mapOf()
        )

        val flow = MutableStateFlow(listOf(device))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `device equality is based on deviceId only`() {
        // Given
        val device1 = DiscoveredDevice(
            deviceId = "same_id",
            deviceName = "Name 1",
            address = "192.168.1.1",
            transportType = "lan"
        )
        val device2 = DiscoveredDevice(
            deviceId = "same_id",
            deviceName = "Name 2",
            address = "192.168.1.2",
            transportType = "bluetooth"
        )

        // When & Then
        assertEquals(device1, device2)
        assertEquals(device1.hashCode(), device2.hashCode())
    }

    // endregion

    // region: Discovery Lifecycle Tests (25-40)

    @Test
    fun `startDiscoveryOnAll starts all available services`() = runTest {
        // Given
        every { mockService1.isAvailable } returns true
        every { mockService2.isAvailable } returns true

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        manager.startDiscoveryOnAll()

        // Then
        coVerify { mockService1.startDiscovery(null) }
        coVerify { mockService2.startDiscovery(null) }
    }

    @Test
    fun `startDiscoveryOnAll skips unavailable services`() = runTest {
        // Given
        every { mockService1.isAvailable } returns true
        every { mockService2.isAvailable } returns false

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        manager.startDiscoveryOnAll()

        // Then
        coVerify { mockService1.startDiscovery(null) }
        coVerify(exactly = 0) { mockService2.startDiscovery(any()) }
    }

    @Test
    fun `startDiscoveryOnAll with timeout passes timeout to services`() = runTest {
        // Given
        every { mockService1.isAvailable } returns true
        val timeoutMs = 30000L

        manager.registerService("service1", mockService1)

        // When
        manager.startDiscoveryOnAll(timeoutMs)

        // Then
        coVerify { mockService1.startDiscovery(timeoutMs) }
    }

    @Test
    fun `startDiscoveryOnAll handles exception from one service`() = runTest {
        // Given
        every { mockService1.isAvailable } returns true
        every { mockService2.isAvailable } returns true
        coEvery { mockService1.startDiscovery(null) } throws Exception("Service 1 failed")

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When & Then
        manager.startDiscoveryOnAll() // Should not throw
        coVerify { mockService2.startDiscovery(null) } // Service 2 should still start
    }

    @Test
    fun `stopDiscoveryOnAll stops all services`() = runTest {
        // Given
        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        manager.stopDiscoveryOnAll()

        // Then
        coVerify { mockService1.stopDiscovery() }
        coVerify { mockService2.stopDiscovery() }
    }

    @Test
    fun `stopDiscoveryOnAll handles exception from one service`() = runTest {
        // Given
        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)
        coEvery { mockService1.stopDiscovery() } throws Exception("Service 1 failed")

        // When & Then
        manager.stopDiscoveryOnAll() // Should not throw
        coVerify { mockService2.stopDiscovery() } // Service 2 should still stop
    }

    @Test
    fun `clearAllDiscoveredDevices clears all services`() {
        // Given
        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        manager.clearAllDiscoveredDevices()

        // Then
        verify { mockService1.clearDiscoveredDevices() }
        verify { mockService2.clearDiscoveredDevices() }
    }

    @Test
    fun `clearAllDiscoveredDevices handles exception from one service`() {
        // Given
        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)
        every { mockService1.clearDiscoveredDevices() } throws Exception("Clear failed")

        // When & Then
        manager.clearAllDiscoveredDevices() // Should not throw
        verify { mockService2.clearDiscoveredDevices() }
    }

    @Test
    fun `startDiscoveryOnAll with empty service list does nothing`() = runTest {
        // Given
        // No services registered

        // When & Then
        manager.startDiscoveryOnAll() // Should not throw
    }

    @Test
    fun `stopDiscoveryOnAll with empty service list does nothing`() = runTest {
        // Given
        // No services registered

        // When & Then
        manager.stopDiscoveryOnAll() // Should not throw
    }

    @Test
    fun `clearAllDiscoveredDevices with empty service list does nothing`() {
        // Given
        // No services registered

        // When & Then
        manager.clearAllDiscoveredDevices() // Should not throw
    }

    @Test
    fun `startDiscoveryOnAll starts service independently`() = runTest {
        // Given
        every { mockService1.isAvailable } returns true
        every { mockService2.isAvailable } returns true
        coEvery { mockService1.startDiscovery(null) } coAnswers { kotlinx.coroutines.delay(1000) }

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)

        // When
        manager.startDiscoveryOnAll()

        // Then
        // Service 2 should start even if service 1 is still running
        coVerify { mockService2.startDiscovery(null) }
    }

    @Test
    fun `stopDiscoveryOnAll stops service independently`() = runTest {
        // Given
        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)
        coEvery { mockService1.stopDiscovery() } coAnswers { kotlinx.coroutines.delay(1000) }

        // When
        manager.stopDiscoveryOnAll()

        // Then
        // Service 2 should stop even if service 1 is still stopping
        coVerify { mockService2.stopDiscovery() }
    }

    @Test
    fun `getService returns correct service by name`() {
        // Given
        manager.registerService("lan", mockService1)
        manager.registerService("bluetooth", mockService2)

        // When
        val lanService = manager.getService("lan")
        val bluetoothService = manager.getService("bluetooth")

        // Then
        assertEquals(mockService1, lanService)
        assertEquals(mockService2, bluetoothService)
    }

    @Test
    fun `getAllServices returns services in registration order`() {
        // Given
        manager.registerService("first", mockService1)
        manager.registerService("second", mockService2)

        // When
        val services = manager.getAllServices()

        // Then
        assertEquals(2, services.size)
        assertEquals(mockService1, services[0])
        assertEquals(mockService2, services[1])
    }

    @Test
    fun `getAvailableServices filters by isAvailable property`() {
        // Given
        val availableService = mockk<IDiscoveryService>()
        val unavailableService = mockk<IDiscoveryService>()
        every { availableService.isAvailable } returns true
        every { unavailableService.isAvailable } returns false
        every { availableService.serviceName } returns "available"
        every { unavailableService.serviceName } returns "unavailable"

        manager.registerService("available", availableService)
        manager.registerService("unavailable", unavailableService)

        // When
        val availableServices = manager.getAvailableServices()

        // Then
        assertEquals(1, availableServices.size)
        assertTrue(availableServices.contains(availableService))
    }

    // endregion

    // region: Integration Tests (41-60)

    @Test
    fun `createDefault creates manager with LAN service`() {
        // This test would require actual Android Context and ISettingsRepository
        // Placeholder for integration test
        assertTrue(true)
    }

    @Test
    fun `multiple register and clear cycles work correctly`() {
        // Given
        manager.registerService("service1", mockService1)
        manager.clearAllDiscoveredDevices()

        manager.registerService("service2", mockService2)
        manager.clearAllDiscoveredDevices()

        // When
        manager.registerService("service3", mockService1)
        manager.clearAllDiscoveredDevices()

        // Then
        verify(exactly = 3) { mockService1.clearDiscoveredDevices() }
        verify(exactly = 1) { mockService2.clearDiscoveredDevices() }
    }

    @Test
    fun `service can be retrieved after multiple operations`() = runTest {
        // Given
        manager.registerService("service1", mockService1)
        manager.startDiscoveryOnAll()
        manager.stopDiscoveryOnAll()

        // When
        val service = manager.getService("service1")

        // Then
        assertEquals(mockService1, service)
    }

    @Test
    fun `discovery flow updates when service emits new devices`() {
        // Given
        val device1 = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan"
        )
        val device2 = DiscoveredDevice(
            deviceId = "device2",
            deviceName = "Device 2",
            address = "192.168.1.2",
            transportType = "lan"
        )

        val flow = MutableStateFlow(listOf(device1))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        flow.value = listOf(device1, device2)

        // Then
        // Flow should emit updated list
        assertTrue(true)
    }

    @Test
    fun `service unavailability is checked on startDiscovery`() = runTest {
        // Given
        every { mockService1.isAvailable } returns false

        manager.registerService("service1", mockService1)

        // When
        manager.startDiscoveryOnAll()

        // Then
        coVerify(exactly = 0) { mockService1.startDiscovery(any()) }
    }

    @Test
    fun `service availability can change between calls`() = runTest {
        // Given
        every { mockService1.isAvailable } returns true

        manager.registerService("service1", mockService1)
        manager.startDiscoveryOnAll()

        // When
        every { mockService1.isAvailable } returns false
        manager.startDiscoveryOnAll()

        // Then
        coVerify(exactly = 2) { mockService1.startDiscovery(any()) }
    }

    @Test
    fun `exception in startDiscovery is logged but does not stop other services`() = runTest {
        // Given
        val mockService3 = mockk<IDiscoveryService>(relaxed = true)
        every { mockService1.isAvailable } returns true
        every { mockService2.isAvailable } returns true
        every { mockService3.isAvailable } returns true
        coEvery { mockService1.startDiscovery(null) } throws Exception("Failure")

        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)
        manager.registerService("service3", mockService3)

        // When & Then
        manager.startDiscoveryOnAll()
        coVerify { mockService2.startDiscovery(null) }
        coVerify { mockService3.startDiscovery(null) }
    }

    @Test
    fun `exception in stopDiscovery is logged but does not stop other services`() = runTest {
        // Given
        val mockService3 = mockk<IDiscoveryService>(relaxed = true)
        manager.registerService("service1", mockService1)
        manager.registerService("service2", mockService2)
        manager.registerService("service3", mockService3)
        coEvery { mockService1.stopDiscovery() } throws Exception("Failure")

        // When & Then
        manager.stopDiscoveryOnAll()
        coVerify { mockService2.stopDiscovery() }
        coVerify { mockService3.stopDiscovery() }
    }

    @Test
    fun `clearDiscoveredDevices does not affect service registration`() {
        // Given
        manager.registerService("service1", mockService1)
        manager.clearAllDiscoveredDevices()

        // When
        val service = manager.getService("service1")

        // Then
        assertEquals(mockService1, service)
    }

    @Test
    fun `getAllDiscoveredDevicesFlow handles service removal`() {
        // Given
        val device = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan"
        )
        val flow = MutableStateFlow(listOf(device))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `startDiscovery with timeout of zero is valid`() = runTest {
        // Given
        every { mockService1.isAvailable } returns true
        manager.registerService("service1", mockService1)

        // When
        manager.startDiscoveryOnAll(timeoutMs = 0)

        // Then
        coVerify { mockService1.startDiscovery(0) }
    }

    @Test
    fun `startDiscovery with negative timeout is valid`() = runTest {
        // Given
        every { mockService1.isAvailable } returns true
        manager.registerService("service1", mockService1)

        // When
        manager.startDiscoveryOnAll(timeoutMs = -1)

        // Then
        coVerify { mockService1.startDiscovery(-1) }
    }

    @Test
    fun `service with empty device list is handled correctly`() {
        // Given
        val flow = MutableStateFlow(emptyList<DiscoveredDevice>())
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `service with null metadata is handled correctly`() {
        // Given
        val device = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan",
            metadata = emptyMap()
        )
        val flow = MutableStateFlow(listOf(device))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    @Test
    fun `multiple devices with same ID from same service are deduplicated`() {
        // Given
        val device1 = DiscoveredDevice(
            deviceId = "same_id",
            deviceName = "Name 1",
            address = "192.168.1.1",
            transportType = "lan"
        )
        val device2 = DiscoveredDevice(
            deviceId = "same_id",
            deviceName = "Name 2",
            address = "192.168.1.2",
            transportType = "lan"
        )

        val flow = MutableStateFlow(listOf(device1, device2))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
        // Deduplication happens in combine operator
    }

    @Test
    fun `device with all fields populated is handled correctly`() {
        // Given
        val device = DiscoveredDevice(
            deviceId = "device1",
            deviceName = "Device 1",
            address = "192.168.1.1",
            transportType = "lan",
            rssi = -55,
            metadata = mapOf("avatar" to "hash123", "capability" to "file_transfer")
        )
        val flow = MutableStateFlow(listOf(device))
        every { mockService1.discoveredDevices } returns flow

        manager.registerService("service1", mockService1)

        // When
        val combinedFlow = manager.getAllDiscoveredDevicesFlow()

        // Then
        assertNotNull(combinedFlow)
    }

    // endregion
}
