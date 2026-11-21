package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImagePickerHelper {

    const val DEFAULT_AVATAR_OR_LOGO_MAX_DIM = 440
    const val DEFAULT_AVATAR_OR_LOGO_QUALITY = 82

    /**
     * Asynchronously decodes, EXIF-rotates, scales, and JPEG-compresses a Photo Picker Uri
     * on [Dispatchers.IO] so the UI thread stays completely smooth and drop-free.
     */
    suspend fun compressUriToBase64Async(
        context: Context,
        uri: Uri,
        maxDimension: Int = DEFAULT_AVATAR_OR_LOGO_MAX_DIM,
        quality: Int = DEFAULT_AVATAR_OR_LOGO_QUALITY
    ): String? = withContext(Dispatchers.IO) {
        compressUriToBase64(context, uri, maxDimension, quality)
    }

    private fun openUriStream(context: Context, uri: Uri): InputStream? {
        val cr = context.contentResolver
        return try {
            cr.openInputStream(uri)
        } catch (_: Exception) {
            null
        } ?: try {
            cr.openAssetFileDescriptor(uri, "r")?.createInputStream()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compresses an image Uri from the Photo Picker into a crisp normal-resolution Base64 JPEG
     * data URI (default 440px @ q82) with EXIF rotation applied and OOM-safe downsampling.
     */
    fun compressUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = DEFAULT_AVATAR_OR_LOGO_MAX_DIM,
        quality: Int = DEFAULT_AVATAR_OR_LOGO_QUALITY
    ): String? {
        val safeMaxDim = maxDimension.coerceIn(64, 1600)
        val safeQuality = quality.coerceIn(30, 95)
        return try {
            // 1. Read EXIF orientation safely (ignored on formats without EXIF)
            val exifOrientation = try {
                openUriStream(context, uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // 2. Decode bounds only to compute optimal power-of-2 inSampleSize.
            // Note: BitmapFactory.decodeStream always returns null when inJustDecodeBounds = true,
            // so we must NOT treat null return from decodeStream as an error!
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val boundsStreamOpened = openUriStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
                true
            } ?: false

            var usedImageDecoder = false
            var decodedBitmap: Bitmap? = null

            if (boundsStreamOpened && boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                val inSampleSize = calculateInSampleSize(
                    boundsOptions.outWidth,
                    boundsOptions.outHeight,
                    safeMaxDim
                )
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                decodedBitmap = openUriStream(context, uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            }

            // Fallback: try direct full stream decode (handles some virtual/provider streams)
            if (decodedBitmap == null) {
                decodedBitmap = openUriStream(context, uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }

            // Fallback on Android 9+ (P+): ImageDecoder handles HEIC, AVIF, WebP, and OEM Photo Picker URIs
            if (decodedBitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodedBitmap = try {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val w = info.size.width
                        val h = info.size.height
                        if (w > 0 && h > 0) {
                            val scale = minOf(
                                safeMaxDim.toFloat() / w,
                                safeMaxDim.toFloat() / h,
                                1.0f
                            )
                            val targetW = (w * scale).toInt().coerceAtLeast(1)
                            val targetH = (h * scale).toInt().coerceAtLeast(1)
                            decoder.setTargetSize(targetW, targetH)
                        }
                    }.also { usedImageDecoder = true }
                } catch (_: Throwable) {
                    null
                }
            }

            val rawBitmap = decodedBitmap ?: return null

            // 4. Apply EXIF rotation/flip if decoded via BitmapFactory (ImageDecoder auto-orients)
            val orientedBitmap = if (usedImageDecoder) {
                rawBitmap
            } else {
                applyExifOrientation(rawBitmap, exifOrientation)
            }
            if (orientedBitmap != rawBitmap) {
                rawBitmap.recycle()
            }

            // 5. Resize precisely to fit within safeMaxDim and flatten transparency onto clean white
            // if transparent PNG logos are converted to JPEG (prevents black background artifacts).
            val result = encodeBitmapToDataUri(orientedBitmap, safeMaxDim, safeQuality)
            orientedBitmap.recycle()
            result
        } catch (e: OutOfMemoryError) {
            android.util.Log.w("ImagePickerHelper", "OOM during image compression", e)
            null
        } catch (e: Throwable) {
            android.util.Log.w("ImagePickerHelper", "Failed to compress image: ${e.message}")
            null
        }
    }

    internal fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, targetMaxDim: Int): Int {
        var inSampleSize = 1
        while ((srcWidth / (inSampleSize * 2)) >= targetMaxDim &&
            (srcHeight / (inSampleSize * 2)) >= targetMaxDim
        ) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    internal fun encodeBitmapToDataUri(
        source: Bitmap,
        maxDimension: Int,
        quality: Int
    ): String {
        val scale = minOf(
            maxDimension.toFloat() / source.width.coerceAtLeast(1),
            maxDimension.toFloat() / source.height.coerceAtLeast(1),
            1.0f
        )
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)

        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        } else {
            source
        }

        val jpegReadyBitmap = if (scaledBitmap.hasAlpha()) {
            Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888).also { canvasBmp ->
                val canvas = Canvas(canvasBmp)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
            }
        } else {
            scaledBitmap
        }

        val outputStream = ByteArrayOutputStream()
        jpegReadyBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 95), outputStream)
        val byteArray = outputStream.toByteArray()

        if (jpegReadyBitmap != scaledBitmap) jpegReadyBitmap.recycle()
        if (scaledBitmap != source) scaledBitmap.recycle()

        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64String"
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }
}
