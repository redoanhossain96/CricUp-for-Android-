package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PreparedPostVideo(
    val localFilePath: String,
    val thumbnailBase64: String?,
    val durationMs: Long,
    val inlineVideoBase64: String?,
    val chunkedBase64Parts: List<String>,
    val exceedsCloudClipLimit: Boolean
)

object MediaHelper {
    const val MAX_POST_IMAGE_BASE64_CHARS = 210_000
    const val MAX_AVATAR_BASE64_CHARS = 95_000
    val AVATAR_COMPRESSION_STEPS: List<Pair<Int, Int>> = listOf(480 to 84, 400 to 78, 340 to 72)
    val POST_IMAGE_COMPRESSION_STEPS: List<Pair<Int, Int>> = listOf(1080 to 82, 880 to 76, 720 to 70, 600 to 64)
    private const val LIGHTWEIGHT_VIDEO_SKIP_COMPRESS_BYTES = 350_000L
    private const val INLINE_VIDEO_MAX_BYTES = 450_000L
    private const val CHUNKED_VIDEO_MAX_BYTES = 3_200_000L
    private const val LOCAL_VIDEO_HARD_LIMIT_BYTES = 60 * 1024 * 1024L
    private const val VIDEO_CHUNK_CHAR_SIZE = 400_000

    suspend fun compressAvatarToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        for ((dim, quality) in AVATAR_COMPRESSION_STEPS) {
            val encoded = ImagePickerHelper.compressUriToBase64(context, uri, maxDimension = dim, quality = quality)
            if (encoded != null && encoded.length <= MAX_AVATAR_BASE64_CHARS) {
                return@withContext encoded
            }
        }
        ImagePickerHelper.compressUriToBase64(context, uri, maxDimension = 280, quality = 65)
    }

    suspend fun compressPostImageToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        for ((dim, quality) in POST_IMAGE_COMPRESSION_STEPS) {
            val encoded = ImagePickerHelper.compressUriToBase64(context, uri, maxDimension = dim, quality = quality)
            if (encoded != null && encoded.length <= MAX_POST_IMAGE_BASE64_CHARS) {
                return@withContext encoded
            }
        }
        ImagePickerHelper.compressUriToBase64(context, uri, maxDimension = 520, quality = 58)
    }

    suspend fun preparePostVideo(
        context: Context,
        uri: Uri,
        postId: String
    ): Result<PreparedPostVideo> = withContext(Dispatchers.IO) {
        try {
            val videoDir = File(context.filesDir, "post_videos").apply { mkdirs() }
            val destFile = File(videoDir, "$postId.mp4")

            var totalCopied = 0L
            val videoStream = try {
                context.contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                null
            } ?: try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
            } catch (_: Exception) {
                null
            }
            videoStream?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalCopied += read
                        if (totalCopied > LOCAL_VIDEO_HARD_LIMIT_BYTES) {
                            output.close()
                            destFile.delete()
                            return@withContext Result.failure(
                                IllegalArgumentException("ভিডিও সাইজ ৬০ MB-এর কম হতে হবে")
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(IllegalStateException("ভিডিও ফাইল পড়া যায়নি"))

            if (!destFile.exists() || destFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("খালি ভিডিও ফাইল"))
            }

            // Convert video to a very lightweight MP4 size before preparing upload
            if (destFile.length() > LIGHTWEIGHT_VIDEO_SKIP_COMPRESS_BYTES) {
                val compressedTemp = File(videoDir, "${postId}_slim.mp4")
                val compressedOk = VideoCompressor.compressToLightweightMp4(destFile, compressedTemp)
                if (compressedOk && compressedTemp.exists() && compressedTemp.length() in 1024L until destFile.length()) {
                    destFile.delete()
                    compressedTemp.renameTo(destFile)
                } else {
                    compressedTemp.delete()
                }
            }

            var durationMs = 0L
            var thumbnailBase64: String? = null
            val retriever = MediaMetadataRetriever()
            try {
                try {
                    retriever.setDataSource(destFile.absolutePath)
                } catch (_: Exception) {
                    retriever.setDataSource(context, uri)
                }
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    thumbnailBase64 = bitmapToBase64Thumbnail(frame, maxDim = 480, quality = 68)
                    frame.recycle()
                }
            } catch (e: Exception) {
                android.util.Log.w("MediaHelper", "Thumbnail/duration extraction skipped: ${e.message}")
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            val fileSize = destFile.length()
            var inlineBase64: String? = null
            var chunks: List<String> = emptyList()
            var exceedsCloud = false

            when {
                fileSize <= INLINE_VIDEO_MAX_BYTES -> {
                    val bytes = destFile.readBytes()
                    inlineBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                fileSize <= CHUNKED_VIDEO_MAX_BYTES -> {
                    val bytes = destFile.readBytes()
                    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    chunks = encoded.chunked(VIDEO_CHUNK_CHAR_SIZE)
                }
                else -> {
                    exceedsCloud = true
                }
            }

            Result.success(
                PreparedPostVideo(
                    localFilePath = destFile.absolutePath,
                    thumbnailBase64 = thumbnailBase64,
                    durationMs = durationMs,
                    inlineVideoBase64 = inlineBase64,
                    chunkedBase64Parts = chunks,
                    exceedsCloudClipLimit = exceedsCloud
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun materializeVideoFile(
        context: Context,
        postId: String,
        localPath: String?,
        inlineVideoBase64: String?,
        chunkFetcher: suspend (String) -> List<String>
    ): File? = withContext(Dispatchers.IO) {
        if (!localPath.isNullOrBlank()) {
            val local = File(localPath)
            if (local.exists() && local.length() > 0L) return@withContext local
        }

        val cacheDir = File(context.cacheDir, "post_videos").apply { mkdirs() }
        val cachedFile = File(cacheDir, "$postId.mp4")
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            return@withContext cachedFile
        }

        val fullBase64 = when {
            !inlineVideoBase64.isNullOrBlank() -> inlineVideoBase64.substringAfter("base64,", inlineVideoBase64)
            else -> {
                val parts = chunkFetcher(postId)
                if (parts.isEmpty()) null else parts.joinToString("")
            }
        } ?: return@withContext null

        return@withContext try {
            val bytes = Base64.decode(fullBase64, Base64.DEFAULT)
            FileOutputStream(cachedFile).use { it.write(bytes) }
            cachedFile
        } catch (_: Exception) {
            cachedFile.delete()
            null
        }
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun bitmapToBase64Thumbnail(source: Bitmap, maxDim: Int, quality: Int): String? {
        return try {
            val scale = minOf(maxDim.toFloat() / source.width, maxDim.toFloat() / source.height, 1.0f)
            val targetW = (source.width * scale).toInt().coerceAtLeast(1)
            val targetH = (source.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1.0f) Bitmap.createScaledBitmap(source, targetW, targetH, true) else source
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (scaled != source) scaled.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}
