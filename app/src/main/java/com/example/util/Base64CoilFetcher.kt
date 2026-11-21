package com.example.util

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer

/**
 * Lightweight immutable wrapper passed to Coil so Base64 image strings bypass
 * Coil's String->Uri mapper, participate in Coil's memory/disk cache via [Base64Keyer],
 * and decode off the main thread in [Base64CoilFetcher].
 */
data class Base64ImageModel(val raw: String)

class Base64ImageKeyer : Keyer<Base64ImageModel> {
    override fun key(data: Base64ImageModel, options: Options): String {
        val len = data.raw.length
        val hash = data.raw.hashCode()
        return "b64_${len}_${hash}_${options.size}"
    }
}

class Base64CoilFetcher(
    private val data: Base64ImageModel,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.Default) {
        val str = data.raw
        val mimeType = if (str.startsWith("data:image/", ignoreCase = true)) {
            str.substringAfter("data:").substringBefore(";").takeIf { it.startsWith("image/") }
        } else {
            null
        }
        val bytes = ImageModelResolver.decodeBase64Bytes(str) ?: return@withContext null
        val buffer = Buffer().write(bytes)
        SourceResult(
            source = ImageSource(source = buffer, context = options.context),
            mimeType = mimeType,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<Base64ImageModel> {
        override fun create(
            data: Base64ImageModel,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = Base64CoilFetcher(data, options)
    }
}
