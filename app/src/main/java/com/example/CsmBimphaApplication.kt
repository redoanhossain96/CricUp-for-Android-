package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.di.AppContainer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class CsmBimphaApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Install Global Uncaught Exception Handler with anti-loop guard
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val prefs = getSharedPreferences("cricup_crash_guard", android.content.Context.MODE_PRIVATE)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("CricUpCrash", "Caught uncaught exception in ${thread.name}: ${throwable.message}", throwable)
            val now = System.currentTimeMillis()
            val lastRestart = prefs.getLong("last_restart_time", 0L)

            if (thread == android.os.Looper.getMainLooper().thread && (now - lastRestart > 20_000L)) {
                try {
                    prefs.edit().putLong("last_restart_time", now).apply()
                    val intent = android.content.Intent(this, MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra("EXTRA_CRASH_RECOVERED", true)
                        putExtra("EXTRA_CRASH_ERROR", throwable.localizedMessage ?: "Unexpected error")
                    }
                    startActivity(intent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                } catch (_: Exception) {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            } else {
                android.util.Log.w("CricUpCrash", "Suppressed loop restart for exception in ${thread.name}")
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // 2. Safe Firestore offline persistence initialization
        try {
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            firestore.firestoreSettings = settings
        } catch (e: Exception) {
            android.util.Log.w("CsmBimphaApplication", "Firestore offline persistence fallback", e)
        }
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(com.example.util.Base64ImageKeyer())
                add(com.example.util.Base64CoilFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(48L * 1024L * 1024L)
                    .build()
            }
            .crossfade(80)
            .respectCacheHeaders(false)
            .build()
    }
}
