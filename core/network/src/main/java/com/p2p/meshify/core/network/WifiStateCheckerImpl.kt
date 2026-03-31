package com.p2p.meshify.core.network

import android.content.Context
import android.net.wifi.WifiManager
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker

/**
 * Android implementation of WifiStateChecker.
 * Uses WifiManager to check Wi-Fi state.
 */
class WifiStateCheckerImpl(
    private val context: Context
) : WifiStateChecker {
    
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    override val isWifiEnabled: Boolean
        get() = wifiManager.isWifiEnabled
    
    override fun checkWifiState(): Boolean = isWifiEnabled
}
