package com.example.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.ConcurrentHashMap

object JsonHelper {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val adapterCache = ConcurrentHashMap<Class<*>, JsonAdapter<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> adapterFor(clazz: Class<T>): JsonAdapter<T> {
        return adapterCache.computeIfAbsent(clazz) {
            moshi.adapter(clazz)
        } as JsonAdapter<T>
    }

    inline fun <reified T> toJson(value: T): String {
        return try {
            adapterFor(T::class.java).toJson(value)
        } catch (e: Exception) {
            ""
        }
    }

    inline fun <reified T> fromJson(json: String): T? {
        return try {
            if (json.isBlank()) return null
            adapterFor(T::class.java).fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
