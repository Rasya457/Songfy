package com.lagu.songfy

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class SongfyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        configureCoil()
        installCrashGuard()
    }

    /**
     * Global crash guard:
     * 1. Log crash cause ke Logcat agar mudah di-debug.
     * 2. Bersihkan media_cache sebelum proses mati → mencegah SimpleCache
     *    IllegalStateException pada launch berikutnya akibat lock file dari crash sebelumnya.
     * Ini memperbaiki pola crash berulang "app tidak bisa dibuka setelah crash".
     */
    private fun installCrashGuard() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e(
                    "SongfyCrash",
                    "UNCAUGHT EXCEPTION on thread '${thread.name}': ${throwable.message}",
                    throwable
                )
                // Hapus media_cache agar SimpleCache tidak stuck di launch berikutnya
                val mediaCacheDir = java.io.File(cacheDir, "media_cache")
                if (mediaCacheDir.exists()) {
                    mediaCacheDir.deleteRecursively()
                    android.util.Log.w("SongfyCrash", "media_cache cleared to prevent next-launch SimpleCache lock")
                }
            } catch (_: Exception) {
                // Jangan throw di dalam crash handler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Configure Coil image loader globally:
     * - 25% of available memory for in-memory cache
     * - 100MB disk cache for album art thumbnails
     * - Shorter OkHttp timeouts for image requests (15s)
     * - Respect Cache-Control headers from CDN
     */
    private fun configureCoil() {
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}