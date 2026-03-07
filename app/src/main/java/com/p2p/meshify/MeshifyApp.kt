package com.p2p.meshify

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.p2p.meshify.core.util.Logger

/**
 * Main Application class.
 * Initializes Dependencies and Global Configurations.
 */
class MeshifyApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
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
     * Configures Coil with a robust caching strategy to prevent UI stutter.
     */
    override fun newImageLoader(): ImageLoader {
        Logger.d("MeshifyApp -> Creating Coil ImageLoader")
        val loader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // 2% of storage
                    .build()
            }
            .respectCacheHeaders(false) // Better performance for local mesh files
            .build()
        Logger.d("MeshifyApp -> Coil ImageLoader created SUCCESS")
        return loader
    }
}
