package com.example.util

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ImageModelResolver {
    // 8 MB bounded LRU cache keyed by source string hash+length so Coil receives the exact
    // same ByteArray instance across recompositions and scrolling (100% Coil memory-cache hit).
    private val base64Cache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size.coerceAtLeast(1)
    }
    private val aspectRatioCache = LruCache<String, Float>(256)

    /**
     * Resolves any image model (base64 data URI, raw base64, local file path, file URI, http URL)
     * into a format that Coil natively supports out of the box (ByteArray, File, Uri, String).
     */
    fun resolve(source: Any?): Any? {
        if (source == null) return null
        if (source !is String) return source
        val str = source.trim()
        if (str.isEmpty()) return null

        // 1. Data URI with base64: "data:image/jpeg;base64,..."
        if (str.startsWith("data:image", ignoreCase = true) && str.contains("base64,", ignoreCase = true)) {
            return decodeBase64Bytes(str)
        }

        // 2. Raw base64 string check (long string without spaces/slashes at start)
        if (str.length > 200 && !str.startsWith("http") && !str.startsWith("/") && !str.startsWith("content:") && !str.startsWith("file:")) {
            return decodeBase64Bytes(str) ?: str
        }

        // 3. Local file path check
        if (str.startsWith("/")) {
            return try {
                val file = File(str)
                if (file.exists()) file else null
            } catch (_: Exception) {
                null
            }
        }

        // 4. File URI or Content URI
        if (str.startsWith("file://") || str.startsWith("content://")) {
            return try {
                Uri.parse(str)
            } catch (_: Exception) {
                str
            }
        }

        // 5. Remote URL or resource
        return str
    }

    /**
     * Non-blocking Coil model resolver for Jetpack Compose UI.
     * Avoids main-thread Base64 decoding and File.exists() disk I/O during LazyColumn scrolling;
     * Base64 payloads are wrapped in [Base64ImageModel] and decoded asynchronously by [Base64CoilFetcher].
     */
    fun resolveForCoil(source: Any?): Any? {
        if (source == null) return null
        if (source !is String) return source
        val str = source.trim()
        if (str.isEmpty()) return null

        if (str.startsWith("data:image", ignoreCase = true) && str.contains("base64,", ignoreCase = true)) {
            return Base64ImageModel(str)
        }
        if (str.length > 200 && !str.startsWith("http") && !str.startsWith("/") && !str.startsWith("content:") && !str.startsWith("file:")) {
            return Base64ImageModel(str)
        }
        if (str.startsWith("/")) {
            return File(str)
        }
        if (str.startsWith("file://") || str.startsWith("content://")) {
            return try {
                Uri.parse(str)
            } catch (_: Exception) {
                str
            }
        }
        return str
    }

    internal fun decodeBase64Bytes(raw: String): ByteArray? {
        val str = raw.trim()
        if (str.isEmpty()) return null

        if (str.startsWith("data:image", ignoreCase = true) && str.contains("base64,", ignoreCase = true)) {
            val cacheKey = "uri_${str.length}_${str.hashCode()}"
            base64Cache.get(cacheKey)?.let { return it }
            val base64Data = str.substringAfter("base64,")
            return try {
                val decoded = Base64.decode(base64Data, Base64.DEFAULT)
                if (decoded.isEmpty()) {
                    null
                } else {
                    if (decoded.size <= 1536 * 1024) {
                        base64Cache.put(cacheKey, decoded)
                    }
                    decoded
                }
            } catch (_: OutOfMemoryError) {
                base64Cache.evictAll()
                null
            } catch (_: Exception) {
                null
            }
        }

        val cacheKey = "raw_${str.length}_${str.hashCode()}"
        base64Cache.get(cacheKey)?.let { return it }
        return try {
            val decoded = Base64.decode(str, Base64.DEFAULT)
            if (decoded.isEmpty()) {
                null
            } else {
                if (decoded.size <= 1536 * 1024) {
                    base64Cache.put(cacheKey, decoded)
                }
                decoded
            }
        } catch (_: OutOfMemoryError) {
            base64Cache.evictAll()
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun aspectCacheKey(str: String): String = "${str.length}_${str.hashCode()}"

    fun getCachedAspectRatio(source: String?): Float? {
        val str = source?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return aspectRatioCache.get(aspectCacheKey(str))
    }

    fun cacheAspectRatio(source: String?, ratio: Float) {
        val str = source?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (ratio > 0.05f && ratio.isFinite()) {
            aspectRatioCache.put(aspectCacheKey(str), ratio)
        }
    }

    suspend fun resolveAspectRatio(source: String?): Float? {
        val str = source?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val key = aspectCacheKey(str)
        aspectRatioCache.get(key)?.let { return it }

        val ratio: Float? = when {
            (str.startsWith("data:image", ignoreCase = true) && str.contains("base64,", ignoreCase = true)) ||
                (str.length > 200 && !str.startsWith("http") && !str.startsWith("/") && !str.startsWith("content:") && !str.startsWith("file:")) -> {
                withContext(Dispatchers.Default) {
                    val bytes = decodeBase64Bytes(str) ?: return@withContext null
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        opts.outWidth.toFloat() / opts.outHeight.toFloat()
                    } else null
                }
            }
            str.startsWith("/") -> {
                withContext(Dispatchers.IO) {
                    try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(str, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            opts.outWidth.toFloat() / opts.outHeight.toFloat()
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            else -> null
        }

        if (ratio != null && ratio > 0.05f && ratio.isFinite()) {
            aspectRatioCache.put(key, ratio)
        }
        return ratio
    }
}
