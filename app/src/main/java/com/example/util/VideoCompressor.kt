package com.example.util

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Pure Android SDK lightweight MP4 H.264 compressor.
 * Downscales and lowers bitrate cleanly so uploaded videos remain small and fast.
 */
object VideoCompressor {

    private const val TARGET_MAX_DIMENSION = 480
    private const val TARGET_BITRATE_BPS = 320_000
    private const val TARGET_FPS = 24
    private const val TIMEOUT_US = 10_000L
    private const val MAX_COMPRESS_DURATION_MS = 18_000L
    private const val MAX_STALLED_ITERATIONS = 250

    fun computeLightweightVideoDimensions(
        srcWidth: Int,
        srcHeight: Int,
        maxDim: Int = TARGET_MAX_DIMENSION
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return Pair(480, 272)
        val scale = minOf(maxDim.toFloat() / srcWidth, maxDim.toFloat() / srcHeight, 1.0f)
        val rawW = (srcWidth * scale).roundToInt().coerceAtLeast(16)
        val rawH = (srcHeight * scale).roundToInt().coerceAtLeast(16)
        val alignedW = ((rawW + 8) / 16 * 16).coerceAtLeast(16)
        val alignedH = ((rawH + 8) / 16 * 16).coerceAtLeast(16)
        return Pair(alignedW, alignedH)
    }

    fun compressToLightweightMp4(inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists() || inputFile.length() == 0L) return false

        var rotation = 0
        var srcWidth = 640
        var srcHeight = 360
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(inputFile.absolutePath)
            srcWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 640
            srcHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 360
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val (outW, outH) = computeLightweightVideoDimensions(srcWidth, srcHeight, TARGET_MAX_DIMENSION)

        val videoExtractor = MediaExtractor()
        var audioExtractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: CodecInputSurface? = null
        var outputSurface: DecoderOutputSurface? = null

        return try {
            videoExtractor.setDataSource(inputFile.absolutePath)
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioTrackSourceIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    videoFormat = fmt
                } else if (mime.startsWith("audio/mp4a") && audioTrackSourceIndex == -1) {
                    audioTrackSourceIndex = i
                    audioFormat = fmt
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) return false
            videoExtractor.selectTrack(videoTrackIndex)

            val outFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE_BPS)
                setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = CodecInputSurface(encoder.createInputSurface())
            encoder.start()
            inputSurface.makeCurrent()

            outputSurface = DecoderOutputSurface()
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: return false
            decoder = MediaCodec.createDecoderByType(videoMime)
            decoder.configure(videoFormat, outputSurface.surface, null, 0)
            decoder.start()

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation == 90 || rotation == 180 || rotation == 270) {
                muxer.setOrientationHint(rotation)
            }

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var muxerStarted = false

            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            val startedAtMs = System.currentTimeMillis()
            var stalledIterations = 0

            while (!encoderDone) {
                if (System.currentTimeMillis() - startedAtMs > MAX_COMPRESS_DURATION_MS ||
                    stalledIterations > MAX_STALLED_ITERATIONS
                ) {
                    try { outputFile.delete() } catch (_: Exception) {}
                    return false
                }
                var madeProgress = false

                // Feed video extractor into decoder
                if (!inputDone) {
                    val inBufIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inBufIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inBufIdx)
                        if (inBuf != null) {
                            val sampleSize = videoExtractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inBufIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inBufIdx, 0, sampleSize, videoExtractor.sampleTime, 0)
                                videoExtractor.advance()
                            }
                            madeProgress = true
                        }
                    }
                }

                // Drain encoder -> muxer AND drain decoder -> GL surface -> encoder
                var drainingEncoder = true
                while (drainingEncoder) {
                    val encStatus = encoder.dequeueOutputBuffer(encInfo, TIMEOUT_US)
                    when {
                        encStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            drainingEncoder = false
                        }
                        encStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            madeProgress = true
                            if (muxerStarted) {
                                drainingEncoder = false
                            } else {
                                muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                                if (audioFormat != null) {
                                    muxerAudioTrack = try {
                                        muxer.addTrack(audioFormat)
                                    } catch (_: Exception) {
                                        -1
                                    }
                                }
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        encStatus >= 0 -> {
                            madeProgress = true
                            val encodedData = encoder.getOutputBuffer(encStatus)
                            if ((encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                encInfo.size = 0
                            }
                            if (encInfo.size > 0 && muxerStarted && encodedData != null) {
                                encodedData.position(encInfo.offset)
                                encodedData.limit(encInfo.offset + encInfo.size)
                                muxer.writeSampleData(muxerVideoTrack, encodedData, encInfo)
                            }
                            encoder.releaseOutputBuffer(encStatus, false)
                            if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderDone = true
                                drainingEncoder = false
                            }
                        }
                        else -> {
                            drainingEncoder = false
                        }
                    }
                }

                if (!decoderDone && !encoderDone) {
                    val decStatus = decoder.dequeueOutputBuffer(decInfo, TIMEOUT_US)
                    if (decStatus >= 0) {
                        madeProgress = true
                        val doRender = decInfo.size != 0
                        decoder.releaseOutputBuffer(decStatus, doRender)
                        if (doRender) {
                            outputSurface.awaitNewImage()
                            outputSurface.drawImage()
                            inputSurface.setPresentationTime(decInfo.presentationTimeUs * 1000L)
                            inputSurface.swapBuffers()
                        }
                        if ((decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderDone = true
                            encoder.signalEndOfInputStream()
                        }
                    } else if (decStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        madeProgress = true
                    }
                }

                stalledIterations = if (madeProgress) 0 else stalledIterations + 1
            }

            // Copy lightweight AAC audio stream if muxer registered audio track
            if (muxerStarted && muxerAudioTrack >= 0 && audioTrackSourceIndex >= 0) {
                try {
                    audioExtractor = MediaExtractor().apply {
                        setDataSource(inputFile.absolutePath)
                        selectTrack(audioTrackSourceIndex)
                    }
                    val maxAudioBufSize = try {
                        val fmtSize = audioFormat?.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0
                        fmtSize.coerceAtLeast(256 * 1024)
                    } catch (_: Exception) {
                        256 * 1024
                    }
                    val audioBuffer = ByteBuffer.allocateDirect(maxAudioBufSize)
                    val audioInfo = MediaCodec.BufferInfo()
                    var lastAudioPts = Long.MIN_VALUE
                    while (true) {
                        audioInfo.offset = 0
                        val size = audioExtractor.readSampleData(audioBuffer, 0)
                        if (size < 0) break
                        val pts = audioExtractor.sampleTime
                        if (pts >= 0L && pts >= lastAudioPts) {
                            audioInfo.size = size
                            audioInfo.presentationTimeUs = pts
                            audioInfo.flags = audioExtractor.sampleFlags
                            muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioInfo)
                            lastAudioPts = pts
                        }
                        audioExtractor.advance()
                    }
                } catch (e: Throwable) {
                    android.util.Log.w("VideoCompressor", "Audio mux copy skipped cleanly: ${e.message}")
                }
            }

            if (muxerStarted) {
                muxer.stop()
            }
            outputFile.exists() && outputFile.length() > 1024L && outputFile.length() < inputFile.length()
        } catch (e: Throwable) {
            android.util.Log.w("VideoCompressor", "Video compression fallback to original: ${e.message}")
            try { outputFile.delete() } catch (_: Exception) {}
            false
        } finally {
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { videoExtractor.release() } catch (_: Exception) {}
            try { outputSurface?.release() } catch (_: Exception) {}
            try { inputSurface?.release() } catch (_: Exception) {}
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private class CodecInputSurface(private var surface: Surface?) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        init {
            eglSetup()
        }

        private fun eglSetup() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "Unable to initialize EGL14" }
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val chosen = EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            check(chosen && numConfigs[0] > 0 && configs[0] != null) { "Unable to find RGB888+recordable EGL config" }
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }
            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfAttribs, 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(eglDisplay, eglSurface)

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun release() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            surface?.release()
            surface = null
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    private class DecoderOutputSurface : SurfaceTexture.OnFrameAvailableListener {
        private var textureId = 0
        private var surfaceTexture: SurfaceTexture? = null
        var surface: Surface? = null
            private set

        private val frameSync = Object()
        private var frameAvailable = false

        private val triangleVerticesData = floatArrayOf(
            -1.0f, -1.0f, 0f, 0f, 0f,
            1.0f, -1.0f, 0f, 1f, 0f,
            -1.0f, 1.0f, 0f, 0f, 1f,
            1.0f, 1.0f, 0f, 1f, 1f
        )
        private val triangleVertices: FloatBuffer = ByteBuffer
            .allocateDirect(triangleVerticesData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(triangleVerticesData); position(0) }

        private val mvpMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
        private val stMatrix = FloatArray(16)
        private var program = 0
        private var muMVPMatrixHandle = 0
        private var muSTMatrixHandle = 0
        private var maPositionHandle = 0
        private var maTextureHandle = 0

        init {
            val vertexShader = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
            """.trimIndent()
            val fragmentShader = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
            """.trimIndent()

            val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader)
            val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vs)
            GLES20.glAttachShader(program, fs)
            GLES20.glLinkProgram(program)

            maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            maTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            muMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            muSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            surfaceTexture = SurfaceTexture(textureId).apply {
                setOnFrameAvailableListener(this@DecoderOutputSurface)
            }
            surface = Surface(surfaceTexture)
        }

        private fun loadShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            return shader
        }

        override fun onFrameAvailable(st: SurfaceTexture?) {
            synchronized(frameSync) {
                frameAvailable = true
                frameSync.notifyAll()
            }
        }

        fun awaitNewImage() {
            synchronized(frameSync) {
                var attempts = 0
                while (!frameAvailable && attempts < 20) {
                    frameSync.wait(100L)
                    attempts++
                }
                frameAvailable = false
            }
            surfaceTexture?.updateTexImage()
        }

        fun drawImage() {
            val st = surfaceTexture ?: return
            st.getTransformMatrix(stMatrix)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            triangleVertices.position(0)
            GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, 20, triangleVertices)
            GLES20.glEnableVertexAttribArray(maPositionHandle)

            triangleVertices.position(3)
            GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 20, triangleVertices)
            GLES20.glEnableVertexAttribArray(maTextureHandle)

            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, stMatrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glFinish()
        }

        fun release() {
            surface?.release()
            surface = null
            surfaceTexture?.release()
            surfaceTexture = null
        }
    }
}
