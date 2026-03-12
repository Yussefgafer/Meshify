package com.p2p.meshify.core.network.service

import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportEvent
import kotlinx.coroutines.*

/**
 * Service that monitors transport events and retries pending messages
 * when new peers are discovered.
 */
class MessageQueueService(
    private val repository: IChatRepository,
    private val transport: IMeshTransport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    /**
     * Starts listening for device discovery events to retry pending messages.
     */
    fun start() {
        scope.launch {
            transport.events.collect { event ->
                if (event is TransportEvent.DeviceDiscovered) {
                    repository.retryPendingMessages(event.deviceId)
                }
            }
        }
    }

    /**
     * Stops the message queue service and cancels all coroutines.
     */
    fun stop() {
        scope.cancel()
    }
}
