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
        Logger.i("MeshifyApp -> Starting Application")
        container = AppContainer(this)
    }

    /**
     * Configures Coil with a robust caching strategy to prevent UI stutter.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
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
    }
}
