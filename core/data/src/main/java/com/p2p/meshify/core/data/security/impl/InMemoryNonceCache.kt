package com.p2p.meshify.core.data.security.impl

import com.p2p.meshify.core.common.util.HexUtil
import com.p2p.meshify.domain.security.interfaces.NonceCache
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory nonce cache with time-based cleanup.
 * Stores nonces for [windowMs] milliseconds to prevent replay attacks.
 * 
 * Thread-safe via ConcurrentHashMap.
 */
class InMemoryNonceCache(
    private val windowMs: Long = DEFAULT_WINDOW_MS
) : NonceCache {
    
    companion object {
        private const val DEFAULT_WINDOW_MS = 30_000L // 30 seconds
        
        // Maximum number of nonces to store before forcing cleanup
        private const val MAX_CACHE_SIZE = 10_000
    }
    
    /** Map of nonce (hex string) → timestamp when added */
    private val cache = ConcurrentHashMap<String, Long>()
    
    override fun addIfAbsent(nonce: ByteArray): Boolean {
        val key = HexUtil.toHex(nonce)
        val now = System.currentTimeMillis()
        
        // Cleanup if cache is getting too large
        if (cache.size >= MAX_CACHE_SIZE) {
            cleanup()
        }
        
        // Atomically add to cache — returns null if key was absent (first time seen)
        val previous = cache.putIfAbsent(key, now)
        return previous == null
    }
    
    override fun cleanup() {
        val cutoff = System.currentTimeMillis() - windowMs
        cache.entries.removeIf { it.value < cutoff }
    }
    
    override fun clear() {
        cache.clear()
    }
    
    /**
     * Get current cache size (for testing/monitoring).
     */
    fun size(): Int = cache.size
}
