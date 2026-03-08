package com.p2p.meshify.network.service

import com.p2p.meshify.data.local.dao.PendingMessageDao
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.network.base.IMeshTransport
import com.p2p.meshify.network.base.TransportEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class MessageQueueService(
    private val repository: IChatRepository,
    private val transport: IMeshTransport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    fun start() {
        scope.launch {
            transport.events.collect { event ->
                if (event is TransportEvent.DeviceDiscovered) {
                    repository.retryPendingMessages(event.deviceId)
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
