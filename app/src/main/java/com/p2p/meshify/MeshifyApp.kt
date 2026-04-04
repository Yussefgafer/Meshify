package com.p2p.meshify

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.receivers.ReplyReceiver

/**
 * Main Application class.
 * Initializes Dependencies and Global Configurations.
 */
class MeshifyApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        Logger.init(this) // ✅ SEC-03: Initialize logger with debug flag
        Logger.i("MeshifyApp -> Application onCreate START")
        Logger.d("MeshifyApp -> Process Name: ${packageName}")
        try {
            container = AppContainer(this)
            Logger.i("MeshifyApp -> AppContainer initialized SUCCESS")
        } catch (e: Exception) {
            Logger.e("MeshifyApp -> AppContainer initialization FAILED: ${e.message}")
            throw e
        }
        Logger.i("MeshifyApp -> Application onCreate COMPLETE")
    }

    /**
     * Called when the application is terminating.
     * Clean up global resources to prevent memory leaks.
     */
    override fun onTerminate() {
        super.onTerminate()
        Logger.d("MeshifyApp -> Application onTerminate, cleaning up resources")
        // Clean up ReplyReceiver resources (RateLimiter coroutine scope)
        ReplyReceiver.cleanup()
        // Clean up AppContainer resources (transport manager, chat repository, etc.)
        container.cleanup()
    }

    /**
     * Configures Coil 3 with a robust caching strategy to prevent UI stutter.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        Logger.d("MeshifyApp -> Creating Coil 3 ImageLoader")
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25) // 25% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // 2% of storage
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
