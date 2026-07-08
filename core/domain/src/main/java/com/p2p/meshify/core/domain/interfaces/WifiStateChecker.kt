package com.p2p.meshify.core.domain.interfaces

/**
 * Interface for checking Wi-Fi state.
 * Abstraction to avoid Android framework dependency in ViewModels.
 */
interface WifiStateChecker {
    /**
     * Check if Wi-Fi is currently enabled.
     * @return true if Wi-Fi is enabled, false otherwise
     */
    val isWifiEnabled: Boolean
}
