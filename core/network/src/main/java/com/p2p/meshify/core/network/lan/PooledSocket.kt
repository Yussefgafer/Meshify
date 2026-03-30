package com.p2p.meshify.core.network.lan

import java.net.Socket

/**
 * Pooled socket wrapper with metadata for connection management.
 * Internal data class — not exposed outside this package.
 */
internal data class PooledSocket(
    val socket: Socket,
    val createdAt: Long = System.currentTimeMillis(),
    @Volatile var lastUsedAt: Long = System.currentTimeMillis(),
    @Volatile var isInUse: Boolean = false
)
