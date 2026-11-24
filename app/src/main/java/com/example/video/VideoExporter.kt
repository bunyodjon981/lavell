package com.example.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.core.content.FileProvider
import com.example.audio.SynthMusicGenerator
import com.example.model.MontageProject
import com.example.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

class VideoExporter(private val context: Context) {

    data class Mp4ExportResult(
        val displayPath: String,
        val shareUriString: String,
        val fileName: String
    )

    suspend fun exportToMp4(
        project: MontageProject,
        requestedFileName: String,
        onProgress: (String, Float) -> Unit
    ): Mp4ExportResult = withContext(Dispatchers.IO) {
        val safeBase = requestedFileName
            .trim()
            .removeSuffix(".mp4")
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
            .trim()
            .ifBlank { "lavelle_video" }
        val finalFileName = "$safeBase.mp4"

        val width = project.aspectRatio.exportWidth
        val height = project.aspectRatio.exportHeight
        val fps = 30
        val totalDurationMs = project.totalDurationMs.coerceAtLeast(1000L)
        val totalFrames = ((totalDurationMs * fps) / 1000L).toInt().coerceAtLeast(fps)

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempMp4 = File(exportsDir, finalFileName)
        if (tempMp4.exists()) tempMp4.delete()

        onProgress("Video kadrlar tayyorlanmoqda…", 0.05f)

        val bitmapCache = MontageBitmapCache(context)
        val frameRenderer = MontageFrameRenderer(context, bitmapCache)

        val audioFile = resolveAudioFile(project.music)
        val pcmAudio = if (audioFile != null && project.music != null) {
            onProgress("Musiqa trek tayyorlanmoqda…", 0.12f)
            decodeAudioToMonoPcm(audioFile, totalDurationMs, project.music)
        } else null

        val hasAudio = pcmAudio != null && pcmAudio.isNotEmpty()

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = videoEncoder.createInputSurface()
        videoEncoder.start()

        val eglHelper = EglInputSurfaceRenderer(inputSurface, width, height)
        val muxer = MediaMuxer(tempMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        val audioEncoder: MediaCodec? = if (hasAudio) {
            try {
                val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            } catch (e: Exception) {
                Log.w("VideoExporter", "AAC encoder init failed: ${e.message}")
                null
            }
        } else null

        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)
        val videoBufferInfo = MediaCodec.BufferInfo()
        val audioBufferInfo = MediaCodec.BufferInfo()

        try {
            eglHelper.makeCurrent()

            for (frameIdx in 0 until totalFrames) {
                val timeMs = (frameIdx * 1000L) / fps
                frameRenderer.drawFrame(frameCanvas, width.toFloat(), height.toFloat(), project, timeMs, maxBitmapDim = maxOf(width, height))

                val ptsNs = (frameIdx * 1_000_000_000L) / fps
                eglHelper.drawBitmapFrame(frameBitmap, ptsNs)

                val drainResult = drainEncoder(
                    encoder = videoEncoder,
                    bufferInfo = videoBufferInfo,
                    muxer = muxer,
                    trackIndex = videoTrackIndex,
                    muxerStarted = muxerStarted,
                    endOfStream = false,
                    onFormatChanged = { newFormat ->
                        videoTrackIndex = muxer.addTrack(newFormat)
                        if (audioEncoder == null || audioTrackIndex >= 0) {
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                )
                muxerStarted = drainResult
                if (frameIdx % 6 == 0) {
                    val pct = 0.16f + 0.68f * (frameIdx.toFloat() / totalFrames)
                    onProgress("Video yozilmoqda (${frameIdx + 1}/$totalFrames kadr)…", pct)
                }
            }

            videoEncoder.signalEndOfInputStream()
            muxerStarted = drainEncoder(
                encoder = videoEncoder,
                bufferInfo = videoBufferInfo,
                muxer = muxer,
                trackIndex = videoTrackIndex,
                muxerStarted = muxerStarted,
                endOfStream = true,
                onFormatChanged = { newFormat ->
                    videoTrackIndex = muxer.addTrack(newFormat)
                    if (audioEncoder == null || audioTrackIndex >= 0) {
                        muxer.start()
                        muxerStarted = true
                    }
                }
            )

            // Feed and drain audio if available
            if (audioEncoder != null && pcmAudio != null) {
                onProgress("Audio qo'shilmoqda…", 0.88f)
                encodePcmToMuxer(
                    audioEncoder = audioEncoder,
                    pcm = pcmAudio,
                    bufferInfo = audioBufferInfo,
                    muxer = muxer,
                    getMuxerStarted = { muxerStarted },
                    onStartMuxer = { newFormat ->
                        audioTrackIndex = muxer.addTrack(newFormat)
                        if (videoTrackIndex >= 0 && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                        }
                    },
                    getAudioTrackIndex = { audioTrackIndex }
                )
            }
        } finally {
            frameBitmap.recycle()
            bitmapCache.clear()
            eglHelper.release()
            try {
                videoEncoder.stop()
                videoEncoder.release()
            } catch (_: Exception) {
            }
            try {
                audioEncoder?.stop()
                audioEncoder?.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {
            }
        }

        onProgress("Telefon xotirasiga saqlanmoqda…", 0.96f)
        val displayPath = saveMp4ToGalleryAndDownloads(tempMp4, finalFileName)
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempMp4
        ).toString()

        onProgress("Tayyor!", 1.0f)
        Mp4ExportResult(
            displayPath = displayPath,
            shareUriString = shareUri,
            fileName = finalFileName
        )
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean,
        onFormatChanged: (MediaFormat) -> Unit
    ): Boolean {
        var started = muxerStarted
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onFormatChanged(encoder.outputFormat)
                    if (trackIndex >= 0 || started) started = true
                }
                outIdx >= 0 -> {
                    val data = encoder.getOutputBuffer(outIdx)
                    if (data != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && started && trackIndex >= 0) {
                        data.position(bufferInfo.offset)
                        data.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, data, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
        return started
    }

    private fun encodePcmToMuxer(
        audioEncoder: MediaCodec,
        pcm: ShortArray,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        getMuxerStarted: () -> Boolean,
        onStartMuxer: (MediaFormat) -> Unit,
        getAudioTrackIndex: () -> Int
    ) {
        var sampleCursor = 0
        var eosSent = false
        var eosReceived = false
        val totalSamples = pcm.size

        while (!eosReceived) {
            if (!eosSent) {
                val inIdx = audioEncoder.dequeueInputBuffer(5_000L)
                if (inIdx >= 0) {
                    val buf = audioEncoder.getInputBuffer(inIdx)!!
                    buf.clear()
                    if (sampleCursor >= totalSamples) {
                        val ptsUs = (sampleCursor * 1_000_000L) / 44100L
                        audioEncoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosSent = true
                    } else {
                        val maxShorts = buf.capacity() / 2
                        val count = min(maxShorts, totalSamples - sampleCursor)
                        val ptsUs = (sampleCursor * 1_000_000L) / 44100L
                        val byteOrder = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until count) {
                            byteOrder.putShort(pcm[sampleCursor + i])
                        }
                        buf.put(byteOrder.array())
                        audioEncoder.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                        sampleCursor += count
                    }
                }
            }

            var drainLoop = true
            while (drainLoop) {
                val outIdx = audioEncoder.dequeueOutputBuffer(bufferInfo, 5_000L)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> drainLoop = false
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        onStartMuxer(audioEncoder.outputFormat)
                    }
                    outIdx >= 0 -> {
                        val data = audioEncoder.getOutputBuffer(outIdx)
                        val trackIdx = getAudioTrackIndex()
                        if (data != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && getMuxerStarted() && trackIdx >= 0) {
                            data.position(bufferInfo.offset)
                            data.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIdx, data, bufferInfo)
                        }
                        audioEncoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eosReceived = true
                            drainLoop = false
                        }
                    }
                }
            }
        }
    }

    private suspend fun resolveAudioFile(track: MusicTrack?): File? {
        if (track == null) return null
        track.audioFilePath?.let { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0L) return f
        }
        val preset = track.builtInPreset ?: return null
        return SynthMusicGenerator.ensureBuiltInTrackFile(context, preset, track.bpm, track.durationMs.coerceAtLeast(15_000L))
    }

    private fun decodeAudioToMonoPcm(file: File, targetDurationMs: Long, track: MusicTrack): ShortArray? {
        val targetSampleCount = ((targetDurationMs * 44100L) / 1000L).toInt().coerceAtLeast(4410)
        // Fast path for generated WAV files
        if (file.extension.equals("wav", ignoreCase = true)) {
            val raw = readWavMono44100(file)
            if (raw != null && raw.isNotEmpty()) {
                return fitAndApplyVolumeEnvelope(raw, targetSampleCount, track)
            }
        }
        // General MediaExtractor + MediaCodec decoder for MP3/M4A/AAC/OGG
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIdx = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIdx < 0 || inputFormat == null) return null
            extractor.selectTrack(audioTrackIdx)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val decodedShorts = ArrayList<Short>(targetSampleCount)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val maxSourceSamples = ((targetDurationMs + track.trimStartMs + 2000L) * sampleRate / 1000L).toInt()

            while (!outputDone && decodedShorts.size < maxSourceSamples) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 5_000L)
                if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frameShorts = ShortArray(shortBuf.remaining())
                        shortBuf.get(frameShorts)
                        if (channels <= 1) {
                            for (s in frameShorts) decodedShorts.add(s)
                        } else {
                            var idx = 0
                            while (idx + channels - 1 < frameShorts.size) {
                                var sum = 0
                                for (c in 0 until channels) sum += frameShorts[idx + c]
                                decodedShorts.add((sum / channels).toShort())
                                idx += channels
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
            decoder.stop()
            decoder.release()
            if (decodedShorts.isEmpty()) return null

            val rawArr = decodedShorts.toShortArray()
            val resampled = if (sampleRate == 44100) rawArr else resampleMono(rawArr, sampleRate, 44100)
            fitAndApplyVolumeEnvelope(resampled, targetSampleCount, track)
        } catch (e: Exception) {
            Log.w("VideoExporter", "Audio decode skipped: ${e.message}")
            null
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun readWavMono44100(file: File): ShortArray? {
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= 44) return null
            val pcmCount = (bytes.size - 44) / 2
            val out = ShortArray(pcmCount)
            val bb = ByteBuffer.wrap(bytes, 44, pcmCount * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until pcmCount) out[i] = bb.short
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun resampleMono(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLen = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val srcIdx = (i * ratio).toInt().coerceIn(0, input.lastIndex)
            out[i] = input[srcIdx]
        }
        return out
    }

    private fun fitAndApplyVolumeEnvelope(source: ShortArray, targetCount: Int, track: MusicTrack): ShortArray {
        val out = ShortArray(targetCount)
        val startOffset = ((track.trimStartMs * 44100L) / 1000L).toInt().coerceIn(0, source.lastIndex)
        val fadeInSamples = ((track.fadeInMs * 44100L) / 1000L).toInt().coerceAtLeast(1)
        val fadeOutSamples = ((track.fadeOutMs * 44100L) / 1000L).toInt().coerceAtLeast(1)

        for (i in 0 until targetCount) {
            val srcIdx = (startOffset + i) % source.size
            val inEnv = (i.toFloat() / fadeInSamples).coerceIn(0f, 1f)
            val outEnv = ((targetCount - 1 - i).toFloat() / fadeOutSamples).coerceIn(0f, 1f)
            val gain = track.volume.coerceIn(0f, 1f) * minOf(inEnv, outEnv)
            out[i] = (source[srcIdx] * gain).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun saveMp4ToGalleryAndDownloads(sourceFile: File, fileName: String): String {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Lavelle")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "Movies/Lavelle/$fileName"
            }
        }
        val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        fallbackDir.mkdirs()
        val dest = File(fallbackDir, fileName)
        sourceFile.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }
}

private class EglInputSurfaceRenderer(
    private val surface: Surface,
    private val width: Int,
    private val height: Int
) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private var posHandle = 0
    private var texHandle = 0

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(
                floatArrayOf(
                    // X, Y, U, V (flip V for Android Bitmap -> OpenGL texture coordinates)
                    -1f, -1f, 0f, 1f,
                    1f, -1f, 1f, 1f,
                    -1f, 1f, 0f, 0f,
                    1f, 1f, 1f, 0f
                )
            )
            position(0)
        }

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

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
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
    }

    fun makeCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        setupGl()
    }

    private fun setupGl() {
        val vs = compileShader(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec4 aPos;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vTex = aTex;
            }
            """.trimIndent()
        )
        val fs = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uTex;
            void main() {
                gl_FragColor = texture2D(uTex, vTex);
            }
            """.trimIndent()
        )
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        posHandle = GLES20.glGetAttribLocation(program, "aPos")
        texHandle = GLES20.glGetAttribLocation(program, "aTex")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun drawBitmapFrame(bitmap: Bitmap, ptsNs: Long) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        surface.release()
    }
}
